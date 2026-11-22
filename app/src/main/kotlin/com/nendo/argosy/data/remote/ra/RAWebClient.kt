package com.nendo.argosy.data.remote.ra

import com.nendo.argosy.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A RetroAchievements username and the web API key saved for it. [toString] leaves the key out.
 */
data class RAWebCredentials(val username: String, val apiKey: String) {
    override fun toString(): String = "RAWebCredentials(username=$username)"
}

sealed interface RAWebResult<out T> {
    data class Success<T>(val value: T) : RAWebResult<T>
    data object Rejected : RAWebResult<Nothing>
    data class Failed(val reason: String) : RAWebResult<Nothing>
}

/**
 * The RetroAchievements web API client. Game progress answers are cached per user and game for
 * [PROGRESS_CACHE_TTL].
 */
@Singleton
class RAWebClient internal constructor(
    apiProvider: () -> RAWebApi,
    clock: () -> Instant
) {
    @Inject constructor() : this({ createApi() }, { Instant.now() })

    private val api: RAWebApi by lazy(apiProvider)
    private val progressCache = RAWebProgressCache(PROGRESS_CACHE_TTL, clock)
    private val commentsCache = ConcurrentHashMap<Long, List<RAWebComment>>()

    suspend fun userSummary(credentials: RAWebCredentials): RAWebResult<RAWebUserSummary> =
        call { api.getUserSummary(credentials.apiKey, credentials.username) }

    /**
     * The user's progress in [raGameId], from the cache when an entry fetched at or after
     * [freshAfter] is still held, else from RetroAchievements.
     */
    suspend fun gameProgress(
        credentials: RAWebCredentials,
        raGameId: Long,
        freshAfter: Instant
    ): RAWebResult<RAWebGameProgress> {
        progressCache.get(credentials.username, raGameId, freshAfter)?.let { return RAWebResult.Success(it) }
        val result = call { api.getGameInfoAndUserProgress(credentials.apiKey, credentials.username, raGameId) }
        if (result is RAWebResult.Success) progressCache.put(credentials.username, raGameId, result.value)
        return result
    }

    /**
     * Every game in the user's completion progress, read page by page up to
     * [MAX_COMPLETION_PAGES] pages.
     */
    suspend fun completionProgress(credentials: RAWebCredentials): RAWebResult<List<RAWebCompletionEntry>> {
        val entries = mutableListOf<RAWebCompletionEntry>()
        repeat(MAX_COMPLETION_PAGES) {
            val page = call {
                api.getUserCompletionProgress(credentials.apiKey, credentials.username, COMPLETION_PAGE_SIZE, entries.size)
            }
            when (page) {
                is RAWebResult.Success -> {
                    entries += page.value.results
                    if (page.value.results.isEmpty() || entries.size >= page.value.total) {
                        return RAWebResult.Success(entries.toList())
                    }
                }
                is RAWebResult.Rejected -> return page
                is RAWebResult.Failed -> return page
            }
        }
        return RAWebResult.Success(entries.toList())
    }

    /**
     * Every unlock the user earned in [from, to], both ends in whole epoch seconds.
     */
    suspend fun earnedBetween(
        credentials: RAWebCredentials,
        from: Instant,
        to: Instant
    ): RAWebResult<List<RAWebEarnedAchievement>> =
        call { api.getAchievementsEarnedBetween(credentials.apiKey, credentials.username, from.epochSecond, to.epochSecond) }

    /**
     * The newest [COMMENTS_PAGE_SIZE] comments on achievement [achievementId], newest first. A
     * successful answer is kept until [clearCache].
     */
    suspend fun achievementComments(
        credentials: RAWebCredentials,
        achievementId: Long
    ): RAWebResult<List<RAWebComment>> {
        commentsCache[achievementId]?.let { return RAWebResult.Success(it) }
        val result = call {
            api.getComments(
                credentials.apiKey,
                achievementId,
                RAWebApi.COMMENT_KIND_ACHIEVEMENT,
                COMMENTS_PAGE_SIZE,
                RAWebApi.COMMENT_SORT_NEWEST_FIRST
            )
        }
        return when (result) {
            is RAWebResult.Success -> RAWebResult.Success(result.value.results).also {
                commentsCache[achievementId] = it.value
            }
            is RAWebResult.Rejected -> result
            is RAWebResult.Failed -> result
        }
    }

    fun clearCache() {
        progressCache.clear()
        commentsCache.clear()
    }

    private suspend fun <T> call(request: suspend () -> Response<T>): RAWebResult<T> = try {
        val response = request()
        val code = response.code()
        when {
            code == HTTP_UNAUTHORIZED || code == HTTP_FORBIDDEN -> RAWebResult.Rejected
            !response.isSuccessful -> RAWebResult.Failed("HTTP $code")
            else -> response.body()?.let { RAWebResult.Success(it) } ?: RAWebResult.Failed("empty body")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RAWebResult.Failed(e::class.java.simpleName)
    }

    companion object {
        val PROGRESS_CACHE_TTL: Duration = Duration.ofMinutes(15)
        const val COMPLETION_PAGE_SIZE = 500
        const val MAX_COMPLETION_PAGES = 10
        const val COMMENTS_PAGE_SIZE = 100
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val TIMEOUT_SECONDS = 15L

        fun moshi(): Moshi = Moshi.Builder().add(RAWebAchievementMapAdapter).build()

        private fun createApi(): RAWebApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", "Argosy/${BuildConfig.VERSION_NAME}")
                            .build()
                    )
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(RAWebApi.BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi()))
                .build()
                .create(RAWebApi::class.java)
        }
    }
}

internal class RAWebProgressCache(
    private val ttl: Duration,
    private val clock: () -> Instant
) {
    private data class Entry(val fetchedAt: Instant, val progress: RAWebGameProgress)

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(username: String, raGameId: Long, freshAfter: Instant): RAWebGameProgress? {
        val key = keyOf(username, raGameId)
        val entry = entries[key] ?: return null
        if (Duration.between(entry.fetchedAt, clock()) > ttl) {
            entries.remove(key, entry)
            return null
        }
        if (entry.fetchedAt.isBefore(freshAfter)) return null
        return entry.progress
    }

    fun put(username: String, raGameId: Long, progress: RAWebGameProgress) {
        entries[keyOf(username, raGameId)] = Entry(clock(), progress)
    }

    fun clear() {
        entries.clear()
    }

    private fun keyOf(username: String, raGameId: Long): String = "${username.lowercase()}:$raGameId"
}
