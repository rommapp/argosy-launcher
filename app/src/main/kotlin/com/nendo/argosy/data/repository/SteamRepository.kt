package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameScreenshotDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.remote.steam.SteamAppData
import com.nendo.argosy.data.remote.steam.SteamStoreApi
import com.nendo.argosy.util.AppPaths
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
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
    private val steamDownloadQueueDao: com.nendo.argosy.data.local.dao.SteamDownloadQueueDao,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val gameArtDao: GameArtDao,
    private val gameScreenshotDao: GameScreenshotDao
) {
    private val api: SteamStoreApi by lazy { createApi() }

    private val cacheDir: File by lazy {
        File(context.filesDir, "steam").also { it.mkdirs() }
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

            writeArtSources(insertedId, steamAppId, appData.name, appData, backgroundUrl)
            gameScreenshotDao.replaceSources(insertedId, screenshotUrls)

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

            val updatedGame = game.copy(
                description = appData.shortDescription ?: game.description,
                genre = if (!storeGenres.isNullOrBlank()) storeGenres else game.genre,
                rating = appData.metacritic?.score?.toFloat() ?: game.rating
            )

            gameDao.update(updatedGame)
            writeArtSources(game.id, steamAppId, game.title, appData, backgroundUrl)
            if (screenshotUrls.isNotEmpty()) gameScreenshotDao.replaceSources(game.id, screenshotUrls)

            Log.d(TAG, "Enriched ${game.title} with store data")
            SteamResult.Success(updatedGame)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enrich Steam game", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Frees the installed files and leaves the library row, matching what deleting a
     * download does for every other source. The game stays listed as owned and not
     * installed, which is the state it was added in.
     */
    suspend fun uninstallGame(steamAppId: Long): SteamResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(steamAppId)
            if (game != null) {
                deleteInstalledFiles(game.localPath, steamAppId)
                gameDao.clearLocalPath(game.id)
                steamDownloadQueueDao.deleteByAppId(steamAppId)
            }
            SteamResult.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall Steam game", e)
            SteamResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Drops the game from the library entirely, undoing the add. A Steam row exists only
     * because the user asked for it, so this is the one path that may discard it.
     */
    suspend fun removeGame(steamAppId: Long): SteamResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val game = gameDao.getBySteamAppId(steamAppId)
            if (game != null) {
                deleteInstalledFiles(game.localPath, steamAppId)
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

    private fun deleteInstalledFiles(localPath: String?, steamAppId: Long) {
        if (localPath != null) {
            val dir = File(localPath)
            if (dir.exists()) {
                val deleted = dir.deleteRecursively()
                Log.d(TAG, "Deleted game files at $localPath: $deleted")
            }
        }

        val stagingDir = AppPaths.steamStagingDir(context.filesDir, steamAppId)
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
            Log.d(TAG, "Deleted staging dir for $steamAppId")
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

    fun observeDownloadByAppId(
        steamAppId: Long
    ): kotlinx.coroutines.flow.Flow<com.nendo.argosy.data.local.entity.SteamDownloadQueueEntity?> =
        steamDownloadQueueDao.observeByAppId(steamAppId)

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

                    gameDao.update(
                        game.copy(
                            title = appData.name,
                            sortTitle = createSortTitle(appData.name),
                            developer = appData.developers?.firstOrNull() ?: game.developer,
                            publisher = appData.publishers?.firstOrNull() ?: game.publisher,
                            releaseYear = parseReleaseYear(appData.releaseDate?.date) ?: game.releaseYear,
                            genre = appData.genres?.mapNotNull { it.description }?.joinToString(", ") ?: game.genre,
                            description = appData.shortDescription ?: game.description,
                            rating = appData.metacritic?.score?.toFloat() ?: game.rating
                        )
                    )
                    writeArtSources(game.id, steamAppId, appData.name, appData, backgroundUrl)
                    gameScreenshotDao.replaceSources(game.id, screenshotUrls)

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

    private suspend fun writeArtSources(
        gameId: Long,
        steamAppId: Long,
        title: String,
        appData: SteamAppData,
        backgroundUrl: String?
    ) {
        val covers = listOfNotNull(
            "https://steamcdn-a.akamaihd.net/steam/apps/$steamAppId/library_600x900.jpg",
            appData.headerImage,
            appData.capsuleImage
        ).distinct()
        recordArtSource(gameArtDao, imageCacheManager, gameId, ArtSlot.COVER, covers, title, steamAppId = steamAppId)
        if (backgroundUrl != null) {
            recordArtSource(
                gameArtDao, imageCacheManager, gameId, ArtSlot.BACKGROUND, listOf(backgroundUrl), title,
                steamAppId = steamAppId
            )
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
        val count = gameDao.countByPlatform(
            LocalPlatformIds.STEAM,
            syncPreferencesRepository.getRommUserId()
        )
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
                    sortOrder = 2,
                    isVisible = true,
                    romExtensions = "",
                    gameCount = 0
                )
            )
        }
    }
}
