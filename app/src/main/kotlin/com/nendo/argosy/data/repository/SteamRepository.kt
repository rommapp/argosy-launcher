package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.remote.steam.SteamAppData
import com.nendo.argosy.data.remote.steam.SteamStoreApi
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SteamRepository"
private const val STEAM_PLATFORM_SLUG = "steam"

sealed class SteamResult<out T> {
    data class Success<T>(val data: T) : SteamResult<T>()
    data class Error(val message: String) : SteamResult<Nothing>()
}

@Singleton
class SteamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val imageCacheManager: ImageCacheManager,
    private val steamDownloadQueueDao: com.nendo.argosy.data.local.dao.SteamDownloadQueueDao
) {
    private val api: SteamStoreApi by lazy { createApi() }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "steam").also { it.mkdirs() }
    }

    private fun createApi(): SteamStoreApi {
        val moshi = Moshi.Builder().build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://store.steampowered.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SteamStoreApi::class.java)
    }

    suspend fun addGame(
        steamAppId: Long,
        launcherPackage: String
    ): SteamResult<GameEntity> = withContext(Dispatchers.IO) {
        try {
            ensureSteamPlatformExists()

            val existing = gameDao.getBySteamAppId(steamAppId)
            if (existing != null) {
                Log.d(TAG, "Game already exists: ${existing.title}")
                updatePlatformGameCount()
                return@withContext SteamResult.Success(existing)
            }

            Log.d(TAG, "Fetching Steam app details for $steamAppId")
            val response = api.getAppDetails(steamAppId)

            if (!response.isSuccessful) {
                return@withContext SteamResult.Error(
                    "Steam API error: ${response.code()}"
                )
            }

            val body = response.body()
            val appResponse = body?.get(steamAppId.toString())

            if (appResponse == null || !appResponse.success || appResponse.data == null) {
                return@withContext SteamResult.Error(
                    "App not found or invalid response"
                )
            }

            val appData = appResponse.data
            val libraryCapsuleUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$steamAppId/library_600x900.jpg"
            val coverPath = cacheCoverImage(steamAppId, libraryCapsuleUrl)

            val screenshotUrls = appData.screenshots?.mapNotNull { it.pathFull } ?: emptyList()
            val firstScreenshot = screenshotUrls.firstOrNull()
            val backgroundUrl = firstScreenshot
                ?: appData.background
                ?: appData.backgroundRaw

            val game = GameEntity(
                platformId = LocalPlatformIds.STEAM,
                platformSlug = STEAM_PLATFORM_SLUG,
                title = appData.name,
                sortTitle = createSortTitle(appData.name),
                localPath = null,
                rommId = null,
                igdbId = null,
                steamAppId = steamAppId,
                steamLauncher = launcherPackage,
                source = GameSource.STEAM,
                coverPath = coverPath,
                backgroundPath = backgroundUrl,
                screenshotPaths = screenshotUrls.joinToString(","),
                developer = appData.developers?.firstOrNull(),
                publisher = appData.publishers?.firstOrNull(),
                releaseYear = parseReleaseYear(appData.releaseDate?.date),
                genre = appData.genres?.mapNotNull { it.description }?.joinToString(", "),
                description = appData.shortDescription,
                rating = appData.metacritic?.score?.toFloat(),
                addedAt = Instant.now()
            )

            val insertedId = gameDao.insert(game)
            val savedGame = gameDao.getById(insertedId)

            if (backgroundUrl != null) {
                imageCacheManager.queueSteamBackgroundCache(backgroundUrl, steamAppId, appData.name)
            }

            updatePlatformGameCount()

            Log.d(TAG, "Added Steam game: ${appData.name}")
            SteamResult.Success(savedGame!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add Steam game", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun enrichWithStoreData(steamAppId: Long): SteamResult<GameEntity> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(steamAppId)
                ?: return@withContext SteamResult.Error("Game not found")

            Log.d(TAG, "Enriching Steam game with store data: ${game.title}")
            val response = api.getAppDetails(steamAppId)

            if (!response.isSuccessful) {
                return@withContext SteamResult.Error("Steam API error: ${response.code()}")
            }

            val appResponse = response.body()?.get(steamAppId.toString())
            if (appResponse?.success != true || appResponse.data == null) {
                return@withContext SteamResult.Error("App not found in Steam Store")
            }

            val appData = appResponse.data
            val screenshotUrls = appData.screenshots?.mapNotNull { it.pathFull } ?: emptyList()
            val firstScreenshot = screenshotUrls.firstOrNull()
            val backgroundUrl = firstScreenshot ?: appData.background ?: appData.backgroundRaw

            val storeGenres = appData.genres
                ?.mapNotNull { it.description }
                ?.joinToString(", ")

            val coverPath = repairCoverIfNeeded(game, steamAppId, appData)

            val updatedGame = game.copy(
                description = appData.shortDescription ?: game.description,
                genre = if (!storeGenres.isNullOrBlank()) storeGenres else game.genre,
                coverPath = coverPath ?: game.coverPath,
                screenshotPaths = if (screenshotUrls.isNotEmpty()) screenshotUrls.joinToString(",") else game.screenshotPaths,
                backgroundPath = backgroundUrl ?: game.backgroundPath,
                rating = appData.metacritic?.score?.toFloat() ?: game.rating
            )

            gameDao.update(updatedGame)

            if (backgroundUrl != null && game.backgroundPath != backgroundUrl) {
                imageCacheManager.queueSteamBackgroundCache(backgroundUrl, steamAppId, game.title)
            }

            Log.d(TAG, "Enriched ${game.title} with store data")
            SteamResult.Success(updatedGame)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich Steam game", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun removeGame(steamAppId: Long): SteamResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(steamAppId)
            if (game != null) {
                // Delete installed files
                val localPath = game.localPath
                if (localPath != null) {
                    val dir = File(localPath)
                    if (dir.exists()) {
                        val deleted = dir.deleteRecursively()
                        Log.d(TAG, "Deleted game files at $localPath: $deleted")
                    }
                }

                // Delete staging dir if it exists
                val stagingDir = File(context.filesDir, "steam_staging/$steamAppId")
                if (stagingDir.exists()) {
                    stagingDir.deleteRecursively()
                    Log.d(TAG, "Deleted staging dir for $steamAppId")
                }

                gameDao.delete(game.id)
                steamDownloadQueueDao.deleteByAppId(steamAppId)
                deleteCachedImage(steamAppId)
                updatePlatformGameCount()
            }
            SteamResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove Steam game", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateLauncher(
        steamAppId: Long,
        launcherPackage: String
    ): SteamResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(steamAppId)
            if (game != null) {
                gameDao.update(game.copy(steamLauncher = launcherPackage))
            }
            SteamResult.Success(Unit)
        } catch (e: Exception) {
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun refreshAllMetadata(): SteamResult<Int> = withContext(Dispatchers.IO) {
        try {
            val steamGames = gameDao.getBySource(GameSource.STEAM)
            var refreshedCount = 0

            for (game in steamGames) {
                val steamAppId = game.steamAppId ?: continue
                try {
                    val response = api.getAppDetails(steamAppId)
                    if (!response.isSuccessful) continue

                    val appResponse = response.body()?.get(steamAppId.toString())
                    if (appResponse?.success != true || appResponse.data == null) continue

                    val appData = appResponse.data
                    val screenshotUrls = appData.screenshots?.mapNotNull { it.pathFull } ?: emptyList()
                    val firstScreenshot = screenshotUrls.firstOrNull()
                    val backgroundUrl = firstScreenshot
                        ?: appData.background
                        ?: appData.backgroundRaw

                    val libraryCapsuleUrl = "https://steamcdn-a.akamaihd.net/steam/apps/$steamAppId/library_600x900.jpg"
                    val coverPath = cacheCoverImage(steamAppId, libraryCapsuleUrl)

                    gameDao.update(
                        game.copy(
                            title = appData.name,
                            sortTitle = createSortTitle(appData.name),
                            coverPath = coverPath ?: game.coverPath,
                            backgroundPath = backgroundUrl,
                            screenshotPaths = screenshotUrls.joinToString(","),
                            developer = appData.developers?.firstOrNull() ?: game.developer,
                            publisher = appData.publishers?.firstOrNull() ?: game.publisher,
                            releaseYear = parseReleaseYear(appData.releaseDate?.date) ?: game.releaseYear,
                            genre = appData.genres?.mapNotNull { it.description }?.joinToString(", ") ?: game.genre,
                            description = appData.shortDescription ?: game.description,
                            rating = appData.metacritic?.score?.toFloat() ?: game.rating
                        )
                    )

                    if (backgroundUrl != null) {
                        imageCacheManager.queueSteamBackgroundCache(backgroundUrl, steamAppId, appData.name)
                    }

                    refreshedCount++
                    Log.d(TAG, "Refreshed metadata for: ${appData.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to refresh ${game.title}", e)
                }
            }

            Log.d(TAG, "Refreshed $refreshedCount Steam games")
            SteamResult.Success(refreshedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh Steam metadata", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun repairCoverIfNeeded(
        game: GameEntity,
        steamAppId: Long,
        appData: SteamAppData
    ): String? {
        val cover = game.coverPath ?: return null
        if (!cover.startsWith("https://")) return null

        val is404 = try {
            val conn = URL(cover).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            conn.disconnect()
            code == 404
        } catch (e: Exception) {
            true
        }

        if (!is404) return null

        val fallbackUrl = appData.headerImage ?: appData.capsuleImage ?: return null
        Log.d(TAG, "Cover 404 for ${game.title}, replacing with Store API fallback")
        return try {
            val file = File(cacheDir, "cover_$steamAppId.jpg")
            URL(fallbackUrl).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download fallback cover for ${game.title}", e)
            null
        }
    }

    private suspend fun cacheCoverImage(steamAppId: Long, imageUrl: String?): String? {
        if (imageUrl.isNullOrBlank()) return null

        return try {
            val file = File(cacheDir, "cover_$steamAppId.jpg")
            if (file.exists()) {
                return file.absolutePath
            }

            URL(imageUrl).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache cover image", e)
            imageUrl
        }
    }

    private fun deleteCachedImage(steamAppId: Long) {
        try {
            File(cacheDir, "cover_$steamAppId.jpg").delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete cached image", e)
        }
    }

    private suspend fun updatePlatformGameCount() {
        val count = gameDao.countByPlatform(LocalPlatformIds.STEAM)
        platformDao.updateGameCount(LocalPlatformIds.STEAM, count)
    }

    private fun createSortTitle(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.startsWith("the ") -> title.drop(4)
            lower.startsWith("a ") -> title.drop(2)
            lower.startsWith("an ") -> title.drop(3)
            else -> title
        }.lowercase()
    }

    private fun parseReleaseYear(dateString: String?): Int? {
        if (dateString.isNullOrBlank()) return null
        val yearRegex = Regex("""\b(19|20)\d{2}\b""")
        return yearRegex.find(dateString)?.value?.toIntOrNull()
    }

    private suspend fun ensureSteamPlatformExists() {
        val existing = platformDao.getById(LocalPlatformIds.STEAM)
        if (existing == null) {
            Log.d(TAG, "Creating Steam platform")
            platformDao.insert(
                PlatformEntity(
                    id = LocalPlatformIds.STEAM,
                    slug = STEAM_PLATFORM_SLUG,
                    name = "Steam",
                    shortName = "Steam",
                    sortOrder = 130,
                    isVisible = true,
                    romExtensions = "",
                    gameCount = 0
                )
            )
        }
    }
}
