package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.AchievementPayload
import com.nendo.argosy.data.remote.ra.RAAchievementPatch
import com.nendo.argosy.data.remote.ra.RAApi
import com.nendo.argosy.data.remote.ra.RACredentials
import com.nendo.argosy.data.remote.ra.RAPatchData
import com.nendo.argosy.data.remote.romm.RomMAchievement
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.parseTimestamp
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetroAchievementsRepository"
private const val UNLOCKS_COOLDOWN_MS = 5 * 60 * 1000L
private const val WARNING_UNLOCK_ID = 101000001L
const val RA_BADGE_BASE_URL = "https://media.retroachievements.org/Badge/"

sealed class RALoginResult {
    data class Success(val username: String) : RALoginResult()
    data class Error(val message: String) : RALoginResult()
}

sealed class RAAwardResult {
    data object Success : RAAwardResult()
    data object AlreadyAwarded : RAAwardResult()
    data class Error(val message: String) : RAAwardResult()
    data object Queued : RAAwardResult()
}

@Singleton
class RetroAchievementsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val achievementDao: com.nendo.argosy.data.local.dao.AchievementDao,
    private val gameDao: com.nendo.argosy.data.local.dao.GameDao,
    private val prefsRepository: UserPreferencesRepository,
    private val payloadCodec: com.nendo.argosy.data.sync.SyncPayloadCodec
) {
    @Volatile private var cachedApi: RAApi? = null
    @Volatile private var cachedBaseUrl: String? = null

    private suspend fun api(): RAApi {
        val baseUrl = resolveBaseUrl()
        cachedApi?.let { if (cachedBaseUrl == baseUrl) return it }
        return createApi(baseUrl).also {
            cachedApi = it
            cachedBaseUrl = baseUrl
        }
    }

    private suspend fun resolveBaseUrl(): String {
        val prefs = prefsRepository.userPreferences.first()
        if (!prefs.raProxyEnabled) return RAApi.BASE_URL
        return normalizeProxyBaseUrl(prefs.raProxyAddress) ?: RAApi.BASE_URL
    }

    private fun normalizeProxyBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
        return withScheme.toHttpUrlOrNull()?.toString()
    }

    private fun createApi(baseUrl: String): RAApi {
        val moshi = Moshi.Builder().build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Argosy/${BuildConfig.VERSION_NAME} (Android ${android.os.Build.VERSION.RELEASE}) rcheevos/12.2.1")
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RAApi::class.java)
    }

    suspend fun isLoggedIn(): Boolean {
        val prefs = prefsRepository.userPreferences.first()
        return !prefs.raUsername.isNullOrBlank() && !prefs.raToken.isNullOrBlank()
    }

    suspend fun getCredentials(): RACredentials? {
        val prefs = prefsRepository.userPreferences.first()
        val username = prefs.raUsername ?: return null
        val token = prefs.raToken ?: return null
        return RACredentials(username, token)
    }

    // RA Connect tokens are long-lived but not eternal. RA can rotate them
    // server-side (password change, manual revoke, IP-binding triggered).
    // The client only learns about it when a request comes back HTTP 401 or
    // Success=false with an "invalid"/"expired" error. Stateless auth on
    // every endpoint means there is no other signal.
    //
    // When that happens, clear the stored creds so isLoggedIn() reflects
    // RA's real state instead of locally stale prefs. Otherwise every
    // subsequent call retries forever and the user sees nothing happening
    // -- no logs, no UI surface, no obvious action to take.
    @Suppress("UNUSED_PARAMETER")
    private suspend fun handleAuthFailure(httpCode: Int, errorBody: String?, context: String) {
        // No-op. The previous implementation cleared stored credentials on any
        // response whose error string contained "invalid"/"expired"/"credentials"
        // — too broad: RA Connect routinely returns those words for non-auth
        // errors ("Invalid game ID", "Invalid hash"), which silently wiped users'
        // tokens mid-session and broke RA from that point until manual re-login.
        // RA users can re-login from Settings if the token actually rots.
    }

    suspend fun resolveGameId(hash: String): Long? {
        val response = api().resolveGameId(hash = hash)
        if (!response.isSuccessful) {
            throw Exception("resolveGameId failed: HTTP ${response.code()}")
        }
        val body = response.body()?.string()
            ?: throw Exception("resolveGameId: empty response body")
        val gameId = body.trim().toLongOrNull() ?: 0
        return if (gameId > 0) gameId else null
    }

    suspend fun login(username: String, password: String): RALoginResult {
        Logger.debug(TAG, "Logging in to RetroAchievements")
        return try {
            val response = api().login(username = username, password = password)

            if (!response.isSuccessful) {
                Logger.error(TAG, "Login failed: HTTP ${response.code()}")
                return RALoginResult.Error("HTTP ${response.code()}")
            }

            val body = response.body()
            if (body == null || !body.success) {
                val error = body?.error ?: "Unknown error"
                Logger.error(TAG, "Login failed: $error")
                return RALoginResult.Error(error)
            }

            val token = body.token
            if (token.isNullOrBlank()) {
                Logger.error(TAG, "Login succeeded but no token received")
                return RALoginResult.Error("No token received")
            }

            prefsRepository.setRACredentials(username, token)
            gameDao.clearAllAchievementsFetchedAt()
            Logger.info(TAG, "Login successful; cleared achievement-fetch timestamps")
            RALoginResult.Success(username)
        } catch (e: Exception) {
            Logger.error(TAG, "Login exception: ${e.message}")
            RALoginResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun logout() {
        Logger.info(TAG, "Logging out")
        prefsRepository.clearRACredentials()
        gameDao.clearAllAchievementsFetchedAt()
        unlocksCache.clear()
    }

    suspend fun awardAchievement(
        gameId: Long,
        achievementRaId: Long,
        forHardcoreMode: Boolean
    ): RAAwardResult {
        val credentials = getCredentials()
        if (credentials == null) {
            Logger.error(TAG, "Cannot award achievement - not logged in")
            return RAAwardResult.Error("Not logged in")
        }

        val validation = generateValidation(achievementRaId, credentials.username, forHardcoreMode)
        val hardcoreInt = if (forHardcoreMode) 1 else 0

        return try {
            val response = api().awardAchievement(
                username = credentials.username,
                token = credentials.token,
                achievementId = achievementRaId,
                hardcore = hardcoreInt,
                validation = validation
            )

            if (!response.isSuccessful) {
                if (forHardcoreMode) {
                    Logger.error(TAG, "Hardcore achievement award failed: HTTP ${response.code()}")
                    return RAAwardResult.Error("HTTP ${response.code()}")
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            val body = response.body()
            if (body == null) {
                if (forHardcoreMode) {
                    return RAAwardResult.Error("Empty response")
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            if (!body.warning.isNullOrBlank()) {
                Logger.warn(TAG, "Award response warning: ${body.warning}")
            }

            if (!body.success) {
                val error = body.error ?: "Unknown error"
                if (error.contains("already has", ignoreCase = true)) {
                    Logger.debug(TAG, "Achievement $achievementRaId already awarded")
                    return RAAwardResult.AlreadyAwarded
                }
                if (forHardcoreMode) {
                    Logger.error(TAG, "Hardcore achievement award failed: $error")
                    return RAAwardResult.Error(error)
                } else {
                    queueAchievement(gameId, achievementRaId, forHardcoreMode)
                    return RAAwardResult.Queued
                }
            }

            Logger.info(TAG, "Achievement $achievementRaId awarded (hardcore=$forHardcoreMode)")
            RAAwardResult.Success
        } catch (e: Exception) {
            Logger.error(TAG, "Award exception: ${e.message}")
            if (forHardcoreMode) {
                RAAwardResult.Error(e.message ?: "Network error")
            } else {
                queueAchievement(gameId, achievementRaId, forHardcoreMode)
                RAAwardResult.Queued
            }
        }
    }

    private suspend fun queueAchievement(gameId: Long, achievementRaId: Long, forHardcoreMode: Boolean) {
        val payload = AchievementPayload(
            achievementRaId = achievementRaId,
            forHardcoreMode = forHardcoreMode,
            earnedAt = Instant.now().toEpochMilli()
        )
        val entity = PendingSyncQueueEntity(
            gameId = gameId,
            rommId = 0,
            syncType = SyncType.ACHIEVEMENT,
            priority = SyncPriority.PROPERTY,
            payloadJson = payloadCodec.encode(payload)
        )
        pendingSyncQueueDao.insert(entity)
        Logger.info(TAG, "Achievement $achievementRaId queued for later submission")
    }

    suspend fun submitPendingAchievements(): Int {
        val credentials = getCredentials() ?: return 0
        val pending = pendingSyncQueueDao.getRetryableBySyncType(SyncType.ACHIEVEMENT)
        if (pending.isEmpty()) return 0

        Logger.info(TAG, "Submitting ${pending.size} pending achievements")
        var successCount = 0

        for (entity in pending) {
            val payload = payloadCodec.decodeAchievement(entity.payloadJson) ?: continue
            val validation = generateValidation(payload.achievementRaId, credentials.username, payload.forHardcoreMode)
            val hardcoreInt = if (payload.forHardcoreMode) 1 else 0

            try {
                val response = api().awardAchievement(
                    username = credentials.username,
                    token = credentials.token,
                    achievementId = payload.achievementRaId,
                    hardcore = hardcoreInt,
                    validation = validation
                )

                val body = response.body()
                val success = response.isSuccessful && body?.success == true
                val alreadyHas = body?.error?.contains("already has", ignoreCase = true) == true

                if (success || alreadyHas) {
                    pendingSyncQueueDao.deleteById(entity.id)
                    successCount++
                    Logger.debug(TAG, "Pending achievement ${payload.achievementRaId} submitted")
                } else {
                    val error = body?.error ?: "HTTP ${response.code()}"
                    pendingSyncQueueDao.markFailed(entity.id, error)
                    Logger.warn(TAG, "Pending achievement ${payload.achievementRaId} retry: $error")
                }
            } catch (e: Exception) {
                pendingSyncQueueDao.markFailed(entity.id, e.message ?: "Network error")
                Logger.error(TAG, "Pending achievement ${payload.achievementRaId} exception: ${e.message}")
            }
        }

        Logger.info(TAG, "Submitted $successCount/${pending.size} pending achievements")
        return successCount
    }

    data class RASessionResult(
        val success: Boolean,
        val unlockedAchievements: Set<Long> = emptySet()
    )

    suspend fun startSession(gameRaId: Long, hardcore: Boolean = false): RASessionResult {
        val credentials = getCredentials()
        if (credentials == null) {
            Logger.warn(TAG, "Cannot start RA session - not logged in to RetroAchievements")
            return RASessionResult(false)
        }
        Logger.debug(TAG, "Starting RA session for game $gameRaId")

        return try {
            val response = api().startSession(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId,
                hardcore = if (hardcore) 1 else 0
            )

            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                Logger.info(TAG, "Session started for game $gameRaId (hardcore=$hardcore)")
                if (!body.warning.isNullOrBlank()) {
                    Logger.warn(TAG, "RA session warning: ${body.warning}")
                }
                val unlocked = mutableSetOf<Long>()
                if (hardcore) {
                    // Hardcore mode: only count hardcore unlocks
                    body.hardcoreUnlocks?.mapTo(unlocked) { it.id }
                } else {
                    // Casual mode: count all unlocks (hardcore unlocks count too)
                    body.hardcoreUnlocks?.mapTo(unlocked) { it.id }
                    body.unlocks?.mapTo(unlocked) { it.id }
                }
                Logger.debug(TAG, "Pre-unlocked achievements: ${unlocked.size}")
                RASessionResult(true, unlocked)
            } else {
                Logger.error(TAG, "Failed to start session: ${body?.error}")
                handleAuthFailure(response.code(), body?.error, "startSession")
                RASessionResult(false)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Session start exception: ${e.message}")
            RASessionResult(false)
        }
    }

    suspend fun sendHeartbeat(gameRaId: Long, richPresence: String? = null): Boolean {
        val credentials = getCredentials() ?: return false

        return try {
            val response = api().ping(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId,
                richPresence = richPresence
            )

            response.isSuccessful && response.body()?.success == true
        } catch (e: Exception) {
            Logger.error(TAG, "Heartbeat exception: ${e.message}")
            false
        }
    }

    suspend fun getGamePatchData(gameRaId: Long): RAPatchData? {
        val credentials = getCredentials() ?: return null

        return try {
            val response = api().getGameInfo(
                username = credentials.username,
                token = credentials.token,
                gameId = gameRaId
            )

            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                body.patchData
            } else {
                handleAuthFailure(response.code(), body?.error, "getGamePatchData")
                null
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Get game info exception: ${e.message}")
            null
        }
    }

    fun observePendingCount(): Flow<Int> = pendingSyncQueueDao.observePendingCountBySyncType(SyncType.ACHIEVEMENT)

    suspend fun getPendingCount(): Int = pendingSyncQueueDao.countPendingBySyncType(SyncType.ACHIEVEMENT)

    data class GameUnlocks(
        val unlockedIds: Set<Long>,
        val hardcoreUnlockedIds: Set<Long>
    )

    data class AchievementCounts(
        val total: Int,
        val earned: Int
    )

    /**
     * Build/persist achievement entities for [gameId] using [definitions] (the
     * achievement list from RomM's `rom.raMetadata.achievements`) merged with
     * unlock state from two complementary sources:
     *
     * 1. RA Connect `r=unlocks` (read-only, 5-min cooldown) when the user is
     *    logged in to RA. Returns IDs only -- no timestamps; stamps `now` for
     *    newly-known unlocks.
     * 2. [rommEarnedById] (from RomM's progression cache, keyed by badge id).
     *    Carries actual unlock timestamps.
     *
     * An achievement is unlocked if either source reports it. When both report
     * the same achievement, the RomM timestamp is preferred (it's a real date,
     * not `now`). [AchievementDao.replaceForGame] then merges with any existing
     * local timestamp via [minOf], keeping the earliest known unlock time.
     * Returns the post-merge counts read back from the DB.
     */
    suspend fun syncAchievementsForGame(
        gameId: Long,
        gameRaId: Long?,
        definitions: List<AchievementDefinition>,
        rommEarnedById: Map<String?, RommEarnedAchievement> = emptyMap()
    ): AchievementCounts? {
        if (definitions.isEmpty()) return null

        val freshUnlocks = gameRaId?.let { fetchUnlocksFresh(it) }
        val now = System.currentTimeMillis()

        Logger.debug(
            TAG,
            "syncAchievementsForGame gameId=$gameId raId=$gameRaId defs=${definitions.size} " +
                "freshUnlocks=${freshUnlocks?.let { "casual=${it.unlockedIds.size},hc=${it.hardcoreUnlockedIds.size}" } ?: "null"} " +
                "rommEarned=${rommEarnedById.size}"
        )

        val entities = definitions.map { def ->
            val rommEarned = rommEarnedById[def.badgeId]
            val raCasualUnlocked = freshUnlocks != null && def.raId in freshUnlocks.unlockedIds
            val raHardcoreUnlocked = freshUnlocks != null && def.raId in freshUnlocks.hardcoreUnlockedIds

            val unlockedAt = rommEarned?.unlockedAt ?: if (raCasualUnlocked) now else null
            val unlockedHardcoreAt = rommEarned?.unlockedHardcoreAt ?: if (raHardcoreUnlocked) now else null

            AchievementEntity(
                gameId = gameId,
                raId = def.raId,
                title = def.title,
                description = def.description,
                points = def.points,
                type = def.type,
                badgeUrl = def.badgeUrl,
                badgeUrlLock = def.badgeUrlLock,
                unlockedAt = unlockedAt,
                unlockedHardcoreAt = unlockedHardcoreAt
            )
        }

        achievementDao.replaceForGame(gameId, entities)

        val saved = achievementDao.getByGameId(gameId)
        val earned = saved.count { it.isUnlocked }
        return AchievementCounts(total = saved.size, earned = earned)
    }

    /**
     * Source-agnostic achievement definition. Both RomM's
     * `rom.raMetadata.achievements` and any other achievement-list provider
     * map onto this DTO before reaching [syncAchievementsForGame].
     */
    data class AchievementDefinition(
        val raId: Long,
        val title: String,
        val description: String?,
        val points: Int,
        val type: String?,
        val badgeId: String?,
        val badgeUrl: String?,
        val badgeUrlLock: String?
    )

    /**
     * Source-agnostic earned-achievement record with optional unlock timestamps.
     */
    data class RommEarnedAchievement(
        val unlockedAt: Long?,
        val unlockedHardcoreAt: Long?
    )

    companion object {
        fun RomMAchievement.toAchievementDefinition(): AchievementDefinition =
            AchievementDefinition(
                raId = raId,
                title = title,
                description = description,
                points = points,
                type = type,
                badgeId = badgeId,
                badgeUrl = badgeUrl,
                badgeUrlLock = badgeUrlLock
            )

        fun List<RomMEarnedAchievement>.toRommEarnedByBadgeId(): Map<String?, RommEarnedAchievement> =
            associate { earned ->
                earned.id to RommEarnedAchievement(
                    unlockedAt = earned.date?.let { parseTimestamp(it) },
                    unlockedHardcoreAt = earned.dateHardcore?.let { parseTimestamp(it) }
                )
            }
    }

    private val unlocksCache = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, GameUnlocks>>()

    // Per-game mutex so concurrent fetchUnlocksFresh callers share one network
    // call. Without this, multiple Home prefetches for the same game launch in
    // parallel, each sees an empty cache before any response writes, and all
    // hit RA. The cooldown only protects the second cycle, not within a cycle.
    private val unlocksFetchMutexes = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()

    /**
     * Fetches the user's unlocked achievement IDs for a single game directly
     * from RA's Connect API (`r=unlocks`). Read-only, unlike `startsession`
     * which would register a play session. Per-game results are cached for
     * 5 minutes to bound RA traffic during UI browsing.
     *
     * Returns null when the user is not logged in to RA, when the cooldown
     * is active and no cached result exists, or when the request fails.
     * The synthetic `101000001` "unsupported emulator" warning unlock that
     * RA attaches to every unlock query is filtered out.
     */
    suspend fun fetchUnlocksFresh(gameRaId: Long): GameUnlocks? {
        val credentials = getCredentials()
        if (credentials == null) {
            Logger.debug(TAG, "fetchUnlocksFresh: skipped (not logged in) game=$gameRaId")
            return null
        }

        val mutex = unlocksFetchMutexes.computeIfAbsent(gameRaId) { kotlinx.coroutines.sync.Mutex() }
        return mutex.withLock {
            val now = System.currentTimeMillis()
            unlocksCache[gameRaId]?.let { (fetchedAt, cached) ->
                val ageSec = (now - fetchedAt) / 1000
                if (now - fetchedAt < UNLOCKS_COOLDOWN_MS) {
                    Logger.debug(
                        TAG,
                        "fetchUnlocksFresh: cooldown hit game=$gameRaId age=${ageSec}s " +
                            "casual=${cached.unlockedIds.size} hc=${cached.hardcoreUnlockedIds.size}"
                    )
                    return@withLock cached
                }
            }

            Logger.debug(TAG, "fetchUnlocksFresh: calling RA Connect r=unlocks game=$gameRaId")
            try {
                val casualResponse = api().getUnlocks(
                    username = credentials.username,
                    token = credentials.token,
                    gameId = gameRaId,
                    hardcore = 0
                )
                val hardcoreResponse = api().getUnlocks(
                    username = credentials.username,
                    token = credentials.token,
                    gameId = gameRaId,
                    hardcore = 1
                )

                if (!casualResponse.isSuccessful) {
                    Logger.warn(TAG, "fetchUnlocksFresh: HTTP ${casualResponse.code()} for game $gameRaId")
                    handleAuthFailure(casualResponse.code(), casualResponse.body()?.error, "fetchUnlocksFresh")
                    return@withLock unlocksCache[gameRaId]?.second
                }

                val casual = (casualResponse.body()?.userUnlocks ?: emptyList())
                    .filter { it != WARNING_UNLOCK_ID }
                    .toSet()
                val hardcore = (hardcoreResponse.body()?.userUnlocks ?: emptyList())
                    .filter { it != WARNING_UNLOCK_ID }
                    .toSet()

                val result = GameUnlocks(unlockedIds = casual, hardcoreUnlockedIds = hardcore)
                unlocksCache[gameRaId] = now to result
                Logger.info(
                    TAG,
                    "fetchUnlocksFresh: game=$gameRaId casual=${casual.size} hardcore=${hardcore.size}"
                )
                result
            } catch (e: Exception) {
                Logger.warn(TAG, "fetchUnlocksFresh exception for game $gameRaId: ${e.message}")
                unlocksCache[gameRaId]?.second
            }
        }
    }

    private fun generateValidation(achievementId: Long, username: String, hardcore: Boolean): String {
        val hardcoreFlag = if (hardcore) "1" else "0"
        val input = "$achievementId$username$hardcoreFlag"
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
