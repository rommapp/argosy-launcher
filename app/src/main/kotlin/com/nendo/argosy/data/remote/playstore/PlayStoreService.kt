package com.nendo.argosy.data.remote.playstore

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayStoreService"
private const val PLAY_STORE_URL = "https://play.google.com/store/apps/details"
private const val THROTTLE_DELAY_MS = 500L

data class PlayStoreAppDetails(
    val category: String?,
    val description: String?,
    val developer: String?,
    val genre: String?,
    val rating: Float?,
    val iconUrl: String?,
    val coverUrl: String?,
    val screenshotUrls: List<String>
) {
    val isGame: Boolean get() = category?.startsWith("GAME_", ignoreCase = true) == true
    val ratingPercent: Float? get() = rating?.let { it / 5f * 100f }
}

@Singleton
class PlayStoreService @Inject constructor() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val throttleMutex = Mutex()
    private var lastRequestTime = 0L

    suspend fun getAppDetails(packageName: String): Result<PlayStoreAppDetails?> = withContext(Dispatchers.IO) {
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < THROTTLE_DELAY_MS) {
                delay(THROTTLE_DELAY_MS - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }

        try {
            val url = "$PLAY_STORE_URL?id=$packageName&hl=en&gl=US"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Play Store request failed for $packageName: ${response.code}")
                return@withContext Result.success(null)
            }

            val body = response.body?.string() ?: return@withContext Result.success(null)
            val details = parseAppDetails(body)

            Log.d(TAG, "Details for $packageName: category=${details.category}, rating=${details.rating}, screenshots=${details.screenshotUrls.size}")
            Result.success(details)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch details for $packageName: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseAppDetails(html: String): PlayStoreAppDetails {
        val category = extractPattern(html, """"applicationCategory":"([^"]+)"""")
        val description = extractDescription(html)
        val developer = extractPattern(html, """"author":\{"@type":"Person","name":"([^"]+)"""")
            ?: extractPattern(html, """"developer":\s*"([^"]+)"""")
        val genre = extractPattern(html, """"genre":\s*"([^"]+)"""")
            ?: category?.removePrefix("GAME_")?.replace("_", " ")?.lowercase()?.replaceFirstChar { it.uppercase() }
        val rating = extractPattern(html, """"ratingValue":\s*"?([0-9.]+)"?""")?.toFloatOrNull()
        val iconUrl = extractPattern(html, """<img[^>]*itemprop="image"[^>]*src="([^"]+)"""")
        val coverUrl = extractFeatureGraphic(html)
        val screenshotUrls = extractScreenshots(html)

        return PlayStoreAppDetails(
            category = category,
            description = description,
            developer = developer,
            genre = genre,
            rating = rating,
            iconUrl = iconUrl,
            coverUrl = coverUrl,
            screenshotUrls = screenshotUrls
        )
    }

    private fun extractPattern(html: String, pattern: String): String? {
        return Regex(pattern).find(html)?.groupValues?.getOrNull(1)
    }

    private fun extractDescription(html: String): String? {
        val jsonLdMatch = Regex("""<script type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.getOrNull(1) ?: return null

        return try {
            val json = JSONObject(jsonLdMatch)
            json.optString("description").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            extractPattern(html, """"description":\s*"([^"]{10,})"""")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
        }
    }

    private fun extractFeatureGraphic(html: String): String? {
        val patterns = listOf(
            """<meta name="twitter:image" content="([^"]+)"""",
            """<meta property="og:image" content="([^"]+)"""",
            """data-screenshot-item-src-large="([^"]+)"""",
            """srcset="([^"]*=w\d+-h\d+[^"]*)"[^>]*class="[^"]*feature"""",
            """"featureGraphic":\s*\{"url":\s*"([^"]+)""""
        )
        for (pattern in patterns) {
            extractPattern(html, pattern)?.let { url ->
                return cleanImageUrl(url)
            }
        }
        return null
    }

    private fun extractScreenshots(html: String): List<String> {
        val screenshots = mutableListOf<String>()

        // Pattern 1: img tags with data-screenshot-index attribute
        val screenshotIndexMatches = Regex("""<img[^>]*src="([^"]+)"[^>]*data-screenshot-index=""").findAll(html)
        for (match in screenshotIndexMatches) {
            match.groupValues.getOrNull(1)?.let { screenshots.add(cleanImageUrl(it)) }
        }

        // Pattern 2: img tags with alt="Screenshot image"
        if (screenshots.isEmpty()) {
            val altMatches = Regex("""<img[^>]*src="([^"]+)"[^>]*alt="Screenshot image"""").findAll(html)
            for (match in altMatches) {
                match.groupValues.getOrNull(1)?.let { screenshots.add(cleanImageUrl(it)) }
            }
        }

        // Pattern 3: srcset with screenshot class (legacy)
        if (screenshots.isEmpty()) {
            val srcsetMatches = Regex("""srcset="([^"]+)"[^>]*class="[^"]*screenshot""").findAll(html)
            for (match in srcsetMatches) {
                val srcset = match.groupValues.getOrNull(1) ?: continue
                val bestUrl = srcset.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull()
                if (bestUrl != null) {
                    screenshots.add(cleanImageUrl(bestUrl))
                }
            }
        }

        // Pattern 4: JSON screenshotUrls array
        if (screenshots.isEmpty()) {
            val urlMatches = Regex(""""screenshotUrls":\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL).find(html)
            urlMatches?.groupValues?.getOrNull(1)?.let { arrayStr ->
                Regex(""""([^"]+)"""").findAll(arrayStr).forEach { match ->
                    match.groupValues.getOrNull(1)?.let { screenshots.add(cleanImageUrl(it)) }
                }
            }
        }

        return screenshots.take(10)
    }

    private fun cleanImageUrl(url: String): String {
        return url
            .replace("=w\\d+-h\\d+".toRegex(), "=w720")
            .replace("=s\\d+".toRegex(), "=s720")
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    suspend fun getAppCategory(packageName: String): Result<String?> = withContext(Dispatchers.IO) {
        getAppDetails(packageName).map { it?.category }
    }

    fun isGameCategory(category: String?): Boolean {
        return category?.startsWith("GAME_", ignoreCase = true) == true
    }

    companion object {
        const val CACHE_TTL_DAYS = 7
        val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(CACHE_TTL_DAYS.toLong())
    }
}
