package com.nendo.argosy.data.scanner

import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AppCategoryDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.AppCategoryEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.playstore.PlayStoreAppDetails
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AndroidGameScanner"
private const val ANDROID_PLATFORM_ID = "android"

data class AndroidScanProgress(
    val isScanning: Boolean = false,
    val currentApp: String = "",
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val gamesFound: Int = 0
) {
    val progressPercent: Int
        get() = if (totalCount > 0) scannedCount * 100 / totalCount else 0
}

data class AndroidScanResult(
    val gamesAdded: Int,
    val gamesUpdated: Int,
    val gamesSkipped: Int,
    val errors: List<String>
) {
    val totalGames: Int get() = gamesAdded + gamesUpdated + gamesSkipped
}

@Singleton
class AndroidGameScanner @Inject constructor(
    private val appsRepository: AppsRepository,
    private val appCategoryDao: AppCategoryDao,
    private val playStoreService: PlayStoreService,
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val imageCacheManager: ImageCacheManager
) {
    private val _progress = MutableStateFlow(AndroidScanProgress())
    val progress: StateFlow<AndroidScanProgress> = _progress.asStateFlow()

    suspend fun scan(): AndroidScanResult = withContext(Dispatchers.IO) {
        var gamesAdded = 0
        var gamesUpdated = 0
        var gamesSkipped = 0
        val errors = mutableListOf<String>()

        try {
            _progress.value = AndroidScanProgress(isScanning = true)

            ensureAndroidPlatformExists()

            val removedEmulators = removeEmulatorApps()
            if (removedEmulators > 0) {
                Log.d(TAG, "Removed $removedEmulators emulator apps from games")
            }

            val installedApps = appsRepository.getInstalledApps(includeSystemApps = false)
            Log.d(TAG, "Found ${installedApps.size} installed apps to check")

            _progress.value = _progress.value.copy(totalCount = installedApps.size)

            for ((index, app) in installedApps.withIndex()) {
                _progress.value = _progress.value.copy(
                    currentApp = app.label,
                    scannedCount = index
                )

                try {
                    val result = processApp(app)
                    when (result) {
                        ProcessResult.ADDED -> gamesAdded++
                        ProcessResult.UPDATED -> gamesUpdated++
                        ProcessResult.SKIPPED -> gamesSkipped++
                        ProcessResult.NOT_A_GAME -> { }
                    }

                    if (result != ProcessResult.NOT_A_GAME) {
                        _progress.value = _progress.value.copy(
                            gamesFound = _progress.value.gamesFound + 1
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing ${app.packageName}: ${e.message}")
                    errors.add("${app.label}: ${e.message}")
                }
            }

            updatePlatformGameCount()

            _progress.value = _progress.value.copy(
                scannedCount = installedApps.size,
                isScanning = false
            )

            Log.d(TAG, "Scan complete: added=$gamesAdded, updated=$gamesUpdated, skipped=$gamesSkipped")
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed: ${e.message}", e)
            errors.add("Scan failed: ${e.message}")
            _progress.value = AndroidScanProgress()
        }

        AndroidScanResult(gamesAdded, gamesUpdated, gamesSkipped, errors)
    }

    private suspend fun processApp(app: InstalledApp): ProcessResult {
        if (isEmulatorPackage(app.packageName)) {
            return ProcessResult.NOT_A_GAME
        }

        val cached = appCategoryDao.getByPackageName(app.packageName)
        val existing = gameDao.getByPackageName(app.packageName)
        val now = System.currentTimeMillis()

        val cacheExpired = cached == null ||
            !cached.isManualOverride && now - cached.fetchedAt >= PlayStoreService.CACHE_TTL_MS
        val existingNeedsMetadata = existing != null && existing.description == null
        val isNewGame = existing == null
        val needsFetch = cacheExpired || existingNeedsMetadata || isNewGame

        val details: PlayStoreAppDetails? = if (needsFetch) {
            playStoreService.getAppDetails(app.packageName).getOrNull()
        } else null

        val isGame = when {
            cached != null && cached.isManualOverride -> cached.isGame
            cached != null && !cacheExpired -> cached.isGame
            else -> {
                val detected = details?.isGame == true

                appCategoryDao.insert(
                    AppCategoryEntity(
                        id = cached?.id ?: 0,
                        packageName = app.packageName,
                        category = details?.category,
                        isGame = detected,
                        isManualOverride = false,
                        fetchedAt = now
                    )
                )

                detected
            }
        }

        if (!isGame) return ProcessResult.NOT_A_GAME

        if (existing != null) {
            val needsMetadataUpdate = existing.description == null && details != null
            if (existing.title != app.label || needsMetadataUpdate) {
                val updated = existing.copy(
                    title = app.label,
                    sortTitle = createSortTitle(app.label),
                    description = details?.description ?: existing.description,
                    developer = details?.developer ?: existing.developer,
                    genre = details?.genre ?: existing.genre,
                    rating = details?.ratingPercent ?: existing.rating,
                    screenshotPaths = details?.screenshotUrls?.joinToString(",") ?: existing.screenshotPaths,
                    backgroundPath = details?.screenshotUrls?.firstOrNull() ?: existing.backgroundPath
                )
                gameDao.update(updated)

                if (details != null) {
                    queueImageCaching(existing.id, details)
                }
                return ProcessResult.UPDATED
            }
            return ProcessResult.SKIPPED
        }

        val sortTitle = createSortTitle(app.label)
        val game = GameEntity(
            platformId = ANDROID_PLATFORM_ID,
            platformSlug = ANDROID_PLATFORM_ID,
            title = app.label,
            sortTitle = sortTitle,
            localPath = null,
            rommId = null,
            igdbId = null,
            packageName = app.packageName,
            source = GameSource.ANDROID_APP,
            description = details?.description,
            developer = details?.developer,
            genre = details?.genre,
            rating = details?.ratingPercent,
            screenshotPaths = details?.screenshotUrls?.joinToString(","),
            backgroundPath = details?.screenshotUrls?.firstOrNull()
        )

        val gameId = gameDao.insert(game)
        imageCacheManager.queueAppIconCache(gameId, app.packageName)

        if (details != null) {
            queueImageCaching(gameId, details)
        }

        return ProcessResult.ADDED
    }

    private fun queueImageCaching(gameId: Long, details: PlayStoreAppDetails) {
        details.coverUrl?.let { url ->
            imageCacheManager.queueCoverCacheByGameId(url, gameId)
        }
        if (details.screenshotUrls.isNotEmpty()) {
            imageCacheManager.queueScreenshotCacheByGameId(gameId, details.screenshotUrls)
        }
    }

    suspend fun markAsGame(packageName: String, isGame: Boolean) {
        appCategoryDao.setManualGameFlag(packageName, isGame)

        if (isGame) {
            val app = appsRepository.getInstalledApps().find { it.packageName == packageName }
            if (app != null) {
                processApp(app)
            }
        } else {
            gameDao.getByPackageName(packageName)?.let { game ->
                gameDao.delete(game)
            }
        }
    }

    suspend fun syncInstalledStatus() = withContext(Dispatchers.IO) {
        val androidGames = gameDao.getBySource(GameSource.ANDROID_APP)
        val installedPackages = appsRepository.getInstalledApps().map { it.packageName }.toSet()

        for (game in androidGames) {
            val packageName = game.packageName ?: continue
            if (packageName !in installedPackages) {
                Log.d(TAG, "Removing uninstalled game: ${game.title}")
                gameDao.delete(game)
            }
        }

        updatePlatformGameCount()
    }

    suspend fun refreshAllMetadata(): Int = withContext(Dispatchers.IO) {
        val androidGames = gameDao.getBySource(GameSource.ANDROID_APP)
        var refreshedCount = 0

        Log.d(TAG, "Refreshing metadata for ${androidGames.size} Android games")

        for (game in androidGames) {
            val packageName = game.packageName ?: continue
            try {
                val details = playStoreService.getAppDetails(packageName).getOrNull()
                if (details != null) {
                    val updated = game.copy(
                        description = details.description ?: game.description,
                        developer = details.developer ?: game.developer,
                        genre = details.genre ?: game.genre,
                        rating = details.ratingPercent ?: game.rating,
                        screenshotPaths = details.screenshotUrls.takeIf { it.isNotEmpty() }
                            ?.joinToString(",") ?: game.screenshotPaths,
                        backgroundPath = details.screenshotUrls.firstOrNull() ?: game.backgroundPath
                    )
                    gameDao.update(updated)
                    queueImageCaching(game.id, details)
                    refreshedCount++
                    Log.d(TAG, "Refreshed metadata for: ${game.title}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh ${game.title}", e)
            }
        }

        Log.d(TAG, "Refreshed $refreshedCount Android games")
        refreshedCount
    }

    suspend fun removeEmulatorApps(): Int = withContext(Dispatchers.IO) {
        val androidGames = gameDao.getBySource(GameSource.ANDROID_APP)
        var removed = 0

        for (game in androidGames) {
            val packageName = game.packageName ?: continue
            if (isEmulatorPackage(packageName)) {
                Log.d(TAG, "Removing emulator from games: ${game.title} ($packageName)")
                gameDao.delete(game)
                removed++
            }
        }

        if (removed > 0) {
            updatePlatformGameCount()
        }

        removed
    }

    private suspend fun ensureAndroidPlatformExists() {
        val existing = platformDao.getById(ANDROID_PLATFORM_ID)
        if (existing == null) {
            val def = com.nendo.argosy.data.platform.PlatformDefinitions.getById(ANDROID_PLATFORM_ID)
            if (def != null) {
                platformDao.insert(com.nendo.argosy.data.platform.PlatformDefinitions.toEntity(def))
            }
        }
    }

    private suspend fun updatePlatformGameCount() {
        val count = gameDao.countByPlatform(ANDROID_PLATFORM_ID)
        platformDao.updateGameCount(ANDROID_PLATFORM_ID, count)
    }

    private fun createSortTitle(title: String): String {
        return title.lowercase()
            .removePrefix("the ")
            .removePrefix("a ")
            .removePrefix("an ")
            .trim()
    }

    private fun isEmulatorPackage(packageName: String): Boolean {
        if (EmulatorRegistry.getByPackage(packageName) != null) return true
        if (EmulatorRegistry.findFamilyForPackage(packageName) != null) return true
        return false
    }

    private enum class ProcessResult {
        ADDED, UPDATED, SKIPPED, NOT_A_GAME
    }
}
