package com.nendo.argosy.data.titledb

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TitleDbRepository"

@Singleton
class TitleDbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: TitleDbService,
    private val gameDao: GameDao,
    private val moshi: Moshi
) {
    private val deviceToken: String by lazy {
        getOrCreateDeviceToken()
    }

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter by lazy { moshi.adapter<List<String>>(stringListType) }

    fun isConfigured(): Boolean = service.isConfigured()

    suspend fun resolveTitleId(
        gameId: Long,
        gameName: String,
        platform: String
    ): String? {
        if (!isConfigured()) {
            Logger.debug(TAG, "TitleDB not configured, skipping lookup for game=$gameId")
            return null
        }

        val mappedPlatform = mapPlatformSlug(platform)
        if (mappedPlatform == null) {
            Logger.debug(TAG, "Platform $platform not supported for TitleDB lookup")
            return null
        }

        val cached = gameDao.getTitleId(gameId)
        if (cached != null) {
            Logger.debug(TAG, "Using cached titleId=$cached for game=$gameId")
            return cached
        }

        Logger.debug(TAG, "Looking up titleId for game=$gameId, name='$gameName', platform=$mappedPlatform")
        var result = service.lookupByName(gameName, mappedPlatform, deviceToken)

        if (result == null) {
            val normalizedName = normalizeGameName(gameName)
            if (normalizedName != gameName) {
                Logger.debug(TAG, "Retrying with normalized name='$normalizedName' for game=$gameId")
                result = service.lookupByName(normalizedName, mappedPlatform, deviceToken)
            }
        }

        if (result != null) {
            Logger.info(TAG, "Found titleId=${result.titleId} for game=$gameId (score=${result.score})")
            gameDao.updateTitleId(gameId, result.titleId)
            return result.titleId
        }

        Logger.debug(TAG, "No titleId found for game=$gameId")
        return null
    }

    private fun normalizeGameName(name: String): String {
        var normalized = name
        normalized = normalized.replace(Regex("""\s*\([^)]*\)\s*"""), " ")
        normalized = normalized.replace(Regex("""\s*\[[^\]]*\]\s*"""), " ")
        normalized = normalized.replace(Regex("""^\d+\s*-\s*"""), "")
        normalized = normalized.replace(Regex("""\.(3ds|cia|nds|xci|nsp|wua|rpx)$""", RegexOption.IGNORE_CASE), "")
        normalized = normalized.trim().replace(Regex("""\s+"""), " ")
        return normalized
    }

    suspend fun getCachedCandidates(gameId: Long): List<String> {
        val cached = gameDao.getTitleIdCandidates(gameId) ?: return emptyList()
        return try {
            stringListAdapter.fromJson(cached) ?: emptyList()
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to parse cached candidates: ${e.message}")
            emptyList()
        }
    }

    suspend fun resolveTitleIdCandidates(
        gameId: Long,
        gameName: String,
        platform: String
    ): List<String> {
        if (!isConfigured()) {
            Logger.debug(TAG, "TitleDB not configured, skipping candidates lookup")
            return emptyList()
        }

        val cached = getCachedCandidates(gameId)
        if (cached.isNotEmpty()) {
            Logger.debug(TAG, "Using cached candidates for game=$gameId: $cached")
            return cached
        }

        val mappedPlatform = mapPlatformSlug(platform)
        if (mappedPlatform == null) {
            Logger.debug(TAG, "Platform $platform not supported for TitleDB lookup")
            return emptyList()
        }

        Logger.debug(TAG, "Looking up candidates for game=$gameId, name='$gameName', platform=$mappedPlatform")
        var result = service.lookupVariants(gameName, mappedPlatform, deviceToken)

        if (result == null || result.candidates.isEmpty()) {
            val normalizedName = normalizeGameName(gameName)
            if (normalizedName != gameName) {
                Logger.debug(TAG, "Retrying candidates with normalized name='$normalizedName' for game=$gameId")
                result = service.lookupVariants(normalizedName, mappedPlatform, deviceToken)
            }
        }

        if (result != null && result.candidates.isNotEmpty()) {
            val titleIds = result.candidates.map { it.titleId }
            Logger.info(TAG, "Found ${titleIds.size} candidates for game=$gameId: $titleIds")

            val json = stringListAdapter.toJson(titleIds)
            gameDao.updateTitleIdCandidates(gameId, json)

            return titleIds
        }

        Logger.debug(TAG, "No candidates found for game=$gameId")
        return emptyList()
    }

    suspend fun clearTitleIdCache(gameId: Long) {
        Logger.debug(TAG, "Clearing titleId cache for game=$gameId")
        gameDao.updateTitleId(gameId, null)
        gameDao.updateTitleIdCandidates(gameId, null)
    }

    suspend fun lookupName(titleId: String, platform: String): String? {
        if (!isConfigured()) return null

        val mappedPlatform = mapPlatformSlug(platform) ?: return null
        val result = service.lookupByTitleId(titleId, mappedPlatform, deviceToken)
        return result?.name
    }

    private fun mapPlatformSlug(platform: String): String? {
        return when (platform.lowercase()) {
            "switch", "nintendo_switch", "ns" -> "switch"
            "wiiu", "wii_u", "wup" -> "wiiu"
            "3ds", "nintendo_3ds", "n3ds" -> "3ds"
            else -> null
        }
    }

    private fun getOrCreateDeviceToken(): String {
        val prefs = context.getSharedPreferences("titledb_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_token", null)
        if (existing != null) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }

        val token = if (androidId.length >= 8) {
            androidId
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }

        prefs.edit().putString("device_token", token).apply()
        return token
    }
}
