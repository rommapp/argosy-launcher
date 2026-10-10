package com.nendo.argosy.data.remote.romm

import androidx.room.withTransaction
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.ControllerMappingDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.cache.recordArtSource
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.data.sync.UnsentUserPropsByGame
import com.nendo.argosy.data.sync.unsentUserPropsByGame
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_SIZE = 100
private const val SYNC_RESUME_TTL_HOURS = 24L
private const val PLATFORM_FETCH_ATTEMPTS = 3
private const val PLATFORM_FETCH_BACKOFF_MS = 2000L
private const val TAG = "RomMLibrarySyncService"
private const val ANDROID_SLUG = "android"
private val CHANGES_OVERLAP: Duration = Duration.ofHours(1)

@Singleton
class RomMLibrarySyncService @Inject constructor(
    private val apiClient: RomMApiClient,
    private val connectionManager: RomMConnectionManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: ALauncherDatabase,
    private val gameDao: GameDao,
    private val homeTileDao: com.nendo.argosy.data.local.dao.HomeTileDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val saveSyncDao: com.nendo.argosy.data.local.dao.SaveSyncDao,
    private val saveCacheDao: com.nendo.argosy.data.local.dao.SaveCacheDao,
    private val stateCacheDao: com.nendo.argosy.data.local.dao.StateCacheDao,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val playSessionDao: PlaySessionDao,
    private val firmwareDao: FirmwareDao,
    private val controllerMappingDao: ControllerMappingDao,
    private val collectionDao: CollectionDao,
    private val imageCacheManager: ImageCacheManager,
    private val musicDirectoryManager: com.nendo.argosy.data.music.MusicDirectoryManager,
    private val gameFileSync: RomMGameFileSync,
    private val biosRepository: BiosRepository,
    private val installedAppResolver: InstalledAppResolver,
    private val gameRepository: dagger.Lazy<com.nendo.argosy.data.repository.GameRepository>,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val overlayDao: com.nendo.argosy.data.local.dao.GameUserOverlayDao,
    private val visibilityService: RomMVisibilityService,
    private val syncVirtualCollectionsUseCase: dagger.Lazy<com.nendo.argosy.domain.usecase.collection.SyncVirtualCollectionsUseCase>,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer,
    private val androidGameScanner: dagger.Lazy<com.nendo.argosy.data.scanner.AndroidGameScanner>,
    private val attributionRepository: StorageAttributionRepository,
    private val userRomsHiddenDao: com.nendo.argosy.data.local.dao.UserRomsHiddenDao,
    private val pendingSyncQueueDao: com.nendo.argosy.data.local.dao.PendingSyncQueueDao,
    private val siblingSplitRepair: SiblingSplitRepair,
    private val siblingConfigCarryOver: SiblingConfigCarryOver,
    private val siblingGroupRepository: com.nendo.argosy.data.repository.SiblingGroupRepository,
    private val variantFileCleanup: com.nendo.argosy.data.emulator.VariantFileCleanup,
    private val gameArtDao: com.nendo.argosy.data.local.dao.GameArtDao,
    private val gameScreenshotDao: com.nendo.argosy.data.local.dao.GameScreenshotDao
) {
    private val api: RomMApi? get() = connectionManager.getApi()
    private val syncMutex = Mutex()
    private var boxArtCacheEnabledForSync = true
    private var screenshotCacheEnabledForSync = false
    private var decodedImageCacheDirty = false

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    companion object {
        private val ROMM_SOURCES = listOf(GameSource.ROMM_REMOTE, GameSource.ROMM_SYNCED)
    }

    suspend fun populateVirtualCollectionsIfNeeded() {
        val genreCount = collectionDao.countByType(CollectionType.GENRE)
        val gameModeCount = collectionDao.countByType(CollectionType.GAME_MODE)

        if (genreCount == 0 && gameModeCount == 0) {
            val owner = overlayWriter.activeOwnerId()
            val hasGenres = gameDao.getDistinctGenres(owner, oneEntryPerGroup = false).isNotEmpty()
            val hasGameModes = gameDao.getDistinctGameModes(owner).isNotEmpty()

            if (hasGenres || hasGameModes) {
                Logger.info(TAG, "Populating virtual collections for existing games")
                syncVirtualCollectionsUseCase.get()()
            }
        }
    }

    suspend fun syncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)? = null
    ): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(0, 0, 0, 0, emptyList(), alreadyInProgress = true)
        }

        try {
            return@withContext doSyncLibrary(onProgress)
        } finally {
            flushDecodedImageCache()
            syncMutex.unlock()
        }
    }

    suspend fun syncLibraryChanges(since: Instant): SyncResult =
        withContext(NonCancellable + Dispatchers.IO) {
            if (!syncMutex.tryLock()) {
                return@withContext SyncResult(0, 0, 0, 0, emptyList(), alreadyInProgress = true)
            }
            try {
                return@withContext doSyncLibraryChanges(since)
            } finally {
                flushDecodedImageCache()
                syncMutex.unlock()
            }
        }

    private suspend fun doSyncLibraryChanges(since: Instant): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))
        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
        screenshotCacheEnabledForSync = prefs.syncScreenshotsEnabled
        val scope = resolveSyncScope(currentApi)
        val syncStartedAt = Instant.now()
        val queryFrom = since.minus(CHANGES_OVERLAP)

        _syncProgress.value = SyncProgress(isSyncing = true)
        try {
            val platformsResponse = retryOnThrow(PLATFORM_FETCH_ATTEMPTS, PLATFORM_FETCH_BACKOFF_MS) {
                currentApi.getPlatforms()
            }
            val platforms = platformsResponse.body()
            if (!platformsResponse.isSuccessful || platforms.isNullOrEmpty()) {
                return SyncResult(0, 0, 0, 0, listOf("Failed to fetch platforms: ${platformsResponse.code()}"))
            }
            platforms.forEach { syncPlatformMetadata(it) }
            val enabledRemoteIds = platforms
                .filter { platformDao.getById(storagePlatformId(it))?.syncEnabled != false }
                .associateBy { it.id }

            val batch = RomBatch()
            val touchedStorageIds = mutableSetOf<Long>()
            var offset = 0
            var fetched = 0
            while (true) {
                val response = currentApi.getRoms(
                    apiClient.buildRomsQueryParams(
                        limit = SYNC_PAGE_SIZE,
                        offset = offset,
                        includeFiles = true,
                        updatedAfter = queryFrom
                    )
                )
                if (!response.isSuccessful) {
                    return SyncResult(0, batch.added, batch.updated, 0, listOf("Failed to fetch changed ROMs: ${response.code()}"))
                }
                val page = response.body()
                if (page == null || page.items.isEmpty()) break
                fetched += page.items.size
                for (rom in page.items) {
                    val platform = enabledRemoteIds[rom.platformId] ?: continue
                    _syncProgress.update {
                        it.copy(
                            currentPlatform = platform.name,
                            currentPlatformSlug = platform.slug,
                            gamesDone = batch.added + batch.updated,
                            gamesTotal = page.total ?: fetched
                        )
                    }
                    touchedStorageIds += storagePlatformId(platform)
                    syncRomInBatch(rom, filters, scope, batch)
                }
                if (page.items.size < SYNC_PAGE_SIZE) break
                val total = page.total
                if (total != null && fetched >= total) break
                offset += SYNC_PAGE_SIZE
            }

            consolidateMultiDiscGames(currentApi, batch.multiDiscGroups, scope)

            val gamesDeleted = if (filters.deleteOrphans) reconcileDeletedRoms(scope) else 0

            androidGameScanner.get().relinkInstalledRommAndroidApps()
            playSessionDao.relinkOrphans(scope.ownerUserId)
            userPreferencesRepository.setLastRommSyncTime(syncStartedAt)
            syncVirtualCollectionsUseCase.get()()
            recomputeSiblingGroups(completesFullPass = false)
            writeGameCounts(enabledRemoteIds.values.map { storagePlatformId(it) }, scope.ownerUserId)
            attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)

            Logger.info(
                TAG,
                "syncLibraryChanges: since=$queryFrom fetched=$fetched added=${batch.added} " +
                    "updated=${batch.updated} deleted=$gamesDeleted platforms=${touchedStorageIds.size}"
            )
            return SyncResult(touchedStorageIds.size, batch.added, batch.updated, gamesDeleted, emptyList())
        } catch (e: Exception) {
            return SyncResult(0, 0, 0, 0, listOf(e.message ?: "Sync failed"))
        } finally {
            _syncProgress.update { it.copy(isSyncing = false) }
        }
    }

    suspend fun syncPlatform(platformId: Long): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(0, 0, 0, 0, emptyList(), alreadyInProgress = true)
        }

        try {
            return@withContext doSyncPlatform(platformId)
        } finally {
            flushDecodedImageCache()
            syncMutex.unlock()
        }
    }

    private suspend fun flushDecodedImageCache() {
        if (!decodedImageCacheDirty) return
        decodedImageCacheDirty = false
        imageCacheManager.clearDecodedImageCache()
    }

    suspend fun syncPlatformsOnly(): Result<Int> = withContext(Dispatchers.IO) {
        val currentApi = api ?: return@withContext Result.failure(Exception("Not connected to server"))
        try {
            val response = currentApi.getPlatforms()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch platforms: ${response.code()}"))
            }
            val platforms = response.body() ?: emptyList()
            for (platform in platforms) {
                syncPlatformMetadata(platform)
            }
            androidGameScanner.get().relinkInstalledRommAndroidApps()
            Result.success(platforms.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Stores one rom from a single `GET api/roms/{id}`, creating its platform row first when the
     * device has none. Waits for a running library pass to finish. Never relinks an older rom id
     * onto this one.
     */
    suspend fun syncSingleRom(romId: Long): RomMResult<GameEntity> =
        withContext(NonCancellable + Dispatchers.IO) {
            val currentApi = api ?: return@withContext RomMResult.Error("Not connected")
            val rom = when (val fetched = apiClient.getRom(romId)) {
                is RomMResult.Success -> fetched.data
                is RomMResult.Error -> return@withContext fetched
            }
            syncMutex.withLock {
                val prefs = userPreferencesRepository.preferences.first()
                boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
                screenshotCacheEnabledForSync = prefs.syncScreenshotsEnabled
                try {
                    if (!ensurePlatformRow(currentApi, rom.platformId)) {
                        return@withLock RomMResult.Error("Platform ${rom.platformId} not found on the server")
                    }
                    syncRom(rom, singleRomScope())
                    recomputeSiblingGroups(completesFullPass = false)
                    gameDao.getByRommId(rom.id)
                        ?.let { RomMResult.Success(it) }
                        ?: RomMResult.Error("Rom ${rom.id} was not stored")
                } finally {
                    flushDecodedImageCache()
                }
            }
        }

    private suspend fun ensurePlatformRow(api: RomMApi, remotePlatformId: Long): Boolean {
        if (platformDao.getById(remotePlatformId) != null) return true
        val remote = api.getPlatform(remotePlatformId).takeIf { it.isSuccessful }?.body() ?: return false
        syncPlatformMetadata(remote)
        return true
    }

    private suspend fun singleRomScope(): SyncScope {
        val ownerUserId = overlayWriter.activeOwnerId()
        if (ownerUserId != null) overlayWriter.adoptLibraryIfUnclaimed(ownerUserId)
        return SyncScope(
            ownerUserId = ownerUserId,
            visibility = RomMVisibility.Unavailable,
            serverRomIds = ServerRomIds { null },
            unsentUserProps = pendingSyncQueueDao.unsentUserPropsByGame(ownerUserId)
        )
    }

    private suspend fun doSyncPlatform(platformId: Long): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))

        platformDao.getById(platformId)
            ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found locally"))

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
        screenshotCacheEnabledForSync = prefs.syncScreenshotsEnabled
        val scope = resolveSyncScope(currentApi)

        _syncProgress.update {
            it.copy(
                isSyncing = true,
                platformsTotal = 1,
                platformsDone = 0,
                gamesTotal = 0,
                gamesDone = 0
            )
        }

        try {
            val remoteQueryId = if (platformId == LocalPlatformIds.ANDROID) {
                resolveRemoteAndroidPlatformId(currentApi)
                    ?: return SyncResult(0, 0, 0, 0, listOf("Android platform not found on server"))
            } else {
                platformId
            }
            val phaseClock = PhaseClock("platform $platformId")
            val platformResponse = currentApi.getPlatform(remoteQueryId)
            phaseClock.mark("getPlatform")
            if (!platformResponse.isSuccessful) {
                return SyncResult(0, 0, 0, 0, listOf("Failed to fetch platform: ${platformResponse.code()}"))
            }

            val platform = platformResponse.body()
                ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found"))

            syncPlatformMetadata(platform)
            phaseClock.mark("syncPlatformMetadata")

            val storageId = storagePlatformId(platform)
            val row = PlatformSyncRow(
                platformId = storageId,
                name = platform.name,
                slug = platform.slug,
                state = PlatformSyncState.SYNCING
            )
            _syncProgress.update { progress ->
                progress.copy(
                    currentPlatform = platform.name,
                    currentPlatformSlug = platform.slug,
                    platforms = progress.platforms.filterNot { it.platformId == storageId } + row
                )
            }

            gameDao.markSyncDirtyForOwner(storageId, ROMM_SOURCES, scope.ownerUserId)

            val result = syncPlatformRoms(currentApi, platform, filters, scope)
            phaseClock.mark("syncPlatformRoms(${result.added + result.updated} roms)")

            val gamesDeleted = processPostPlatformSync(currentApi, storageId, result, filters, scope)
            phaseClock.mark("processPostPlatformSync")

            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)
            phaseClock.mark("clearAllSyncDirty")

            androidGameScanner.get().relinkInstalledRommAndroidApps()
            phaseClock.mark("relinkAndroidApps")

            syncVirtualCollectionsUseCase.get()()
            phaseClock.mark("syncVirtualCollections")

            recomputeSiblingGroups(completesFullPass = false)
            writeGameCounts(listOf(storageId), scope.ownerUserId)
            phaseClock.mark("recomputeSiblingGroups")

            updateRow(storageId) {
                it.copy(
                    state = if (result.error == null) {
                        PlatformSyncState.DONE
                    } else {
                        PlatformSyncState.FAILED
                    },
                    added = result.added,
                    updated = result.updated,
                    removed = gamesDeleted,
                    error = result.error
                )
            }
            return SyncResult(1, result.added, result.updated, gamesDeleted, result.error?.let { listOf(it) } ?: emptyList())
        } catch (e: Exception) {
            return SyncResult(0, 0, 0, 0, listOf(e.message ?: "Platform sync failed"))
        } finally {
            _syncProgress.update { it.copy(isSyncing = false) }
        }
    }

    private suspend fun processPostPlatformSync(
        api: RomMApi,
        platformId: Long,
        result: PlatformSyncResult,
        filters: SyncFilterPreferences,
        scope: SyncScope
    ): Int {
        var gamesDeleted = 0

        consolidateMultiDiscGames(api, result.multiDiscGroups, scope)

        if (result.error == null) {
            realignDirtyGames(platformId, scope)
        }

        cleanupInvalidExtensionGames(platformId, scope)

        if (filters.deleteOrphans && result.error == null) {
            gamesDeleted += reconcileOrphans(platformId, scope, result.decidedRomIds)
        }

        gameRepository.get().validateLocalFilesForPlatform(platformId)
        gameRepository.get().discoverLocalFilesForPlatform(platformId)
        gameRepository.get().validateDiscLocalFiles(platformId)
        gameRepository.get().validateFileLocalFiles(platformId)

        val count = gameDao.countByPlatform(platformId, scope.ownerUserId)
        platformDao.updateGameCount(platformId, count)

        return gamesDeleted
    }

    /**
     * Logs how long each phase of a sync took. A platform holding one rom still pays for every
     * library-wide step that runs after it, and only a per-phase split says which one.
     */
    private class PhaseClock(private val label: String) {
        private val started = System.currentTimeMillis()
        private var last = started

        fun mark(phase: String) {
            val now = System.currentTimeMillis()
            Logger.info(TAG, "[Timing] $label | $phase took ${now - last}ms (total ${now - started}ms)")
            last = now
        }
    }

    private data class PlatformPassOutcome(
        val added: Int = 0,
        val updated: Int = 0,
        val removed: Int = 0,
        val error: String? = null,
        val counted: Boolean = false
    )

    /**
     * One platform's share of a library pass: mark dirty, sync its roms, run the post-platform
     * work, and record it against the resume generation so a later run can skip it. A platform
     * that throws is reported and left behind rather than ending the pass.
     */
    private suspend fun syncOnePlatformOfPass(
        currentApi: RomMApi,
        platform: RomMPlatform,
        storageId: Long,
        filters: SyncFilterPreferences,
        scope: SyncScope
    ): PlatformPassOutcome {
        updateRow(storageId) { it.copy(state = PlatformSyncState.SYNCING) }
        return try {
            gameDao.markSyncDirtyForOwner(storageId, ROMM_SOURCES, scope.ownerUserId)

            val result = syncPlatformRoms(currentApi, platform, filters, scope)
            val removed = processPostPlatformSync(currentApi, storageId, result, filters, scope)

            if (result.error == null) {
                userPreferencesRepository.addSyncResumeCompletedPlatform(storageId)
            }
            updateRow(storageId) {
                it.copy(
                    state = if (result.error == null) {
                        PlatformSyncState.DONE
                    } else {
                        PlatformSyncState.FAILED
                    },
                    added = result.added,
                    updated = result.updated,
                    removed = removed,
                    error = result.error
                )
            }
            PlatformPassOutcome(result.added, result.updated, removed, result.error, counted = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(
                TAG,
                "doSyncLibrary: platform ${platform.name} failed, continuing with the rest: ${e.message}"
            )
            gameDao.clearSyncDirty(storageId, ROMM_SOURCES)
            val message = "${platform.name}: ${e.message ?: "sync failed"}"
            updateRow(storageId) {
                it.copy(state = PlatformSyncState.FAILED, error = message)
            }
            PlatformPassOutcome(error = message)
        }
    }

    /**
     * Advances the game counters once per rom rather than once per page, so a consumer drawing a
     * bar moves by one game at a time instead of jumping a page width when a response lands.
     */
    private fun publishGameProgress(platformId: Long, done: Int, total: Int) {
        val bounded = maxOf(done, total)
        _syncProgress.update { progress ->
            progress.copy(
                gamesDone = done,
                gamesTotal = bounded,
                platforms = progress.platforms.map { row ->
                    if (row.platformId == platformId) {
                        row.copy(gamesDone = done, gamesTotal = bounded)
                    } else {
                        row
                    }
                }
            )
        }
    }

    private fun updateRow(platformId: Long, transform: (PlatformSyncRow) -> PlatformSyncRow) {
        _syncProgress.update { progress ->
            progress.copy(
                platforms = progress.platforms.map { row ->
                    if (row.platformId == platformId) transform(row) else row
                }
            )
        }
    }

    private suspend fun doSyncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)?
    ): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))
        val errors = mutableListOf<String>()
        var platformsSynced = 0
        var gamesAdded = 0
        var gamesUpdated = 0
        var gamesDeleted = 0
        var platformsResumed = 0

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
        screenshotCacheEnabledForSync = prefs.syncScreenshotsEnabled
        val scope = resolveSyncScope(currentApi)

        _syncProgress.value = SyncProgress(isSyncing = true)

        try {
            val passClock = PhaseClock("library pass")
            val platformsResponse = retryOnThrow(PLATFORM_FETCH_ATTEMPTS, PLATFORM_FETCH_BACKOFF_MS) {
                currentApi.getPlatforms()
            }
            passClock.mark("getPlatforms")

            if (!platformsResponse.isSuccessful) {
                val errorMsg = when (platformsResponse.code()) {
                    401, 403 -> "Authentication failed - token may be invalid or missing permissions"
                    else -> "Failed to fetch platforms: ${platformsResponse.code()}"
                }
                return SyncResult(0, 0, 0, 0, listOf(errorMsg))
            }

            val platforms = platformsResponse.body()
            if (platforms.isNullOrEmpty()) {
                return SyncResult(0, 0, 0, 0, listOf("No platforms returned from server"))
            }

            _syncProgress.update { progress ->
                progress.copy(
                    platforms = platforms.map { platform ->
                        PlatformSyncRow(
                            platformId = storagePlatformId(platform),
                            name = platform.name,
                            slug = platform.slug
                        )
                    }
                )
            }

            for (platform in platforms) {
                syncPlatformMetadata(platform)
            }
            passClock.mark("syncPlatformMetadata x${platforms.size}")

            val enabledPlatforms = platforms.filter { platform ->
                val local = platformDao.getById(storagePlatformId(platform))
                local?.syncEnabled != false
            }

            val syncStartedAt = Instant.now()
            val resumeGeneration = userPreferencesRepository.getSyncResumeGeneration()
            val resuming = resumeGeneration != null &&
                Duration.between(resumeGeneration, syncStartedAt) < Duration.ofHours(SYNC_RESUME_TTL_HOURS)
            val completedPlatformIds = if (resuming) {
                userPreferencesRepository.getSyncResumeCompletedPlatformIds()
            } else {
                userPreferencesRepository.startSyncGeneration(syncStartedAt)
                emptySet()
            }

            _syncProgress.value = _syncProgress.value.copy(
                platformsTotal = enabledPlatforms.size,
                platforms = enabledPlatforms.map { platform ->
                    PlatformSyncRow(
                        platformId = storagePlatformId(platform),
                        name = platform.name,
                        slug = platform.slug
                    )
                }
            )

            for ((index, platform) in enabledPlatforms.withIndex()) {
                onProgress?.invoke(index + 1, enabledPlatforms.size, platform.name)

                _syncProgress.value = _syncProgress.value.copy(
                    currentPlatform = platform.name,
                    currentPlatformSlug = platform.slug,
                    platformsDone = index
                )

                val storageId = storagePlatformId(platform)
                if (storageId in completedPlatformIds) {
                    platformsSynced++
                    platformsResumed++
                    updateRow(storageId) { it.copy(state = PlatformSyncState.ALREADY_SYNCED) }
                    continue
                }

                val outcome = syncOnePlatformOfPass(currentApi, platform, storageId, filters, scope)
                gamesAdded += outcome.added
                gamesUpdated += outcome.updated
                gamesDeleted += outcome.removed
                outcome.error?.let { errors.add(it) }
                if (outcome.counted) platformsSynced++
            }

            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)

            if (filters.deleteOrphans && errors.isEmpty()) {
                gamesDeleted += reconcileDeletedRoms(scope)
            }

            userPreferencesRepository.clearSyncResume()

            val completeUnresumedPass = errors.isEmpty() && platformsResumed == 0
            if (completeUnresumedPass) {
                siblingSplitRepair.retryBlockedSaveSyncMoves()
                siblingSplitRepair.runOnce()
                siblingConfigCarryOver.runOnce()
            }
            variantFileCleanup.runOnce()

            cleanupLegacyPlatforms(platforms)

            androidGameScanner.get().relinkInstalledRommAndroidApps()

            val relinkedSessions = playSessionDao.relinkOrphans(scope.ownerUserId)
            if (relinkedSessions > 0) {
                Logger.info(TAG, "doSyncLibrary: relinked $relinkedSessions play sessions to current game rows")
            }

            userPreferencesRepository.setLastRommSyncTime(Instant.now())
            if (errors.isEmpty()) {
                userPreferencesRepository.setLastRommFullSyncTime(
                    resumeGeneration.takeIf { resuming } ?: syncStartedAt
                )
            }

            syncVirtualCollectionsUseCase.get()()

            recomputeSiblingGroups(completesFullPass = completeUnresumedPass)
            writeGameCounts(enabledPlatforms.map { storagePlatformId(it) }, scope.ownerUserId)

            attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)

        } catch (e: Exception) {
            errors.add(e.message ?: "Sync failed")
            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)
        } finally {
            _syncProgress.update { it.copy(isSyncing = false) }
        }

        gameRepository.get().cleanupEmptyNumericFolders()

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
    }

    private suspend fun recomputeSiblingGroups(completesFullPass: Boolean) {
        try {
            if (completesFullPass) {
                siblingGroupRepository.completeFullPass()
            } else {
                siblingGroupRepository.recomputeAll()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "recomputeSiblingGroups: failed, visibility kept from the last pass: ${e.message}")
        }
    }

    private suspend fun writeGameCounts(platformIds: Collection<Long>, ownerUserId: Long?) {
        for (platformId in platformIds.distinct()) {
            platformDao.updateGameCount(platformId, gameDao.countByPlatform(platformId, ownerUserId))
        }
    }

    private suspend fun <T> retryOnThrow(attempts: Int, backoffMs: Long, block: suspend () -> T): T {
        var lastError: Exception? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                Logger.warn(TAG, "retryOnThrow: attempt ${attempt + 1}/$attempts failed: ${e.message}")
                if (attempt < attempts - 1) delay(backoffMs * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("retryOnThrow: no attempts made")
    }

    /**
     * The account a sync pass runs as, plus what the server withholds from it. Both are read
     * once when the pass starts and carried down; re-reading mid-pass would let a switch land
     * halfway through and attribute the remainder to the wrong account.
     */
    /**
     * Fetched at most once per pass and only when a deletion is about to happen.
     * `GET /api/roms/identifiers` costs tens of seconds on a large library.
     */
    private class ServerRomIds(private val fetch: suspend () -> Set<Long>?) {
        private val mutex = Mutex()
        private var fetched = false
        private var ids: Set<Long>? = null

        suspend fun get(): Set<Long>? = mutex.withLock {
            if (!fetched) {
                ids = fetch()
                fetched = true
            }
            ids
        }
    }

    private data class SyncScope(
        val ownerUserId: Long?,
        val visibility: RomMVisibility,
        val serverRomIds: ServerRomIds,
        val unsentUserProps: UnsentUserPropsByGame
    )

    private suspend fun SyncScope.withFreshUnsentUserProps(): SyncScope =
        copy(unsentUserProps = pendingSyncQueueDao.unsentUserPropsByGame(ownerUserId))

    private suspend fun resolveSyncScope(api: RomMApi): SyncScope {
        val ownerUserId = overlayWriter.activeOwnerId()
        if (ownerUserId != null) overlayWriter.adoptLibraryIfUnclaimed(ownerUserId)
        val identifiers = ServerRomIds {
            when (val result = apiClient.getRomIdentifiers()) {
                is RomMResult.Success -> result.data
                is RomMResult.Error -> {
                    Logger.info(
                        TAG,
                        "resolveSyncScope: rom identifiers unavailable (${result.message}); deletions fall back to pass evidence"
                    )
                    null
                }
            }
        }
        return SyncScope(
            ownerUserId = ownerUserId,
            visibility = visibilityService.fetch(api),
            serverRomIds = identifiers,
            unsentUserProps = pendingSyncQueueDao.unsentUserPropsByGame(ownerUserId)
        )
    }

    private suspend fun reconcileOrphans(
        platformId: Long,
        scope: SyncScope,
        decidedRomIds: Set<Long>
    ): Int {
        val dirtyGames = gameDao.getSyncDirtyGames(platformId, ROMM_SOURCES)
        if (dirtyGames.isEmpty()) return 0

        val romVolumesReadable = gameRepository.get().romStorageVolumesReadable()
        val visibility = scope.visibility
        val ownerUserId = scope.ownerUserId
        var deleted = 0
        var masked = 0
        var stillListed = 0
        var volumeWithheld = 0

        for (game in dirtyGames) {
            val rommId = game.rommId
            if (rommId != null && rommId < 0) continue

            val provenDeleted = visibility is RomMVisibility.Known &&
                rommId != null &&
                !visibility.hides(rommId, game.platformId)

            if (!provenDeleted) {
                if (ownerUserId != null) {
                    val serverHidden = visibility is RomMVisibility.Known
                    overlayWriter.dropMembership(ownerUserId, game.id, serverHidden)
                    masked++
                }
                continue
            }

            if (rommId != null && rommId !in decidedRomIds) {
                val serverRomIds = scope.serverRomIds.get()
                if (serverRomIds != null && rommId in serverRomIds) {
                    stillListed++
                    continue
                }
            }

            if (hasLocalContent(game)) {
                preserveOrphanedGame(game, ownerUserId)
                continue
            }
            if (!romVolumesReadable) {
                volumeWithheld++
                continue
            }
            gameDao.delete(game.id)
            deleted++
        }

        if (deleted > 0) {
            homeTileDao.deleteTilesForMissingGames()
        }

        if (volumeWithheld > 0) {
            Logger.warn(
                TAG,
                "reconcileOrphans: kept $volumeWithheld rows on platform $platformId because a rom " +
                    "storage volume could not be read, so local content could not be ruled out"
            )
        }

        if (masked > 0) {
            Logger.info(
                TAG,
                "reconcileOrphans: kept $masked rows on platform $platformId as a visibility mask " +
                    "(visibility=${if (visibility is RomMVisibility.Known) "known" else "unavailable"})"
            )
        }

        if (stillListed > 0) {
            Logger.info(
                TAG,
                "reconcileOrphans: kept $stillListed rows on platform $platformId that this pass " +
                    "never reached but the server still lists"
            )
        }
        return deleted
    }

    /**
     * Removes rows for roms the server no longer lists anywhere, which the per-platform sweep
     * structurally cannot reach: it only ever examines platforms this pass walked, so a rom on a
     * platform since disabled, unshared, or dropped from the server keeps its row forever.
     *
     * Runs only after a whole-library pass that finished clean, and re-reads the id set rather
     * than reusing the one the pass opened with: a rom added while the pass was walking platforms
     * is missing from that older set and present in the library, which is exactly the shape this
     * sweep deletes. An unavailable set or visibility answer means it withholds entirely.
     */
    private suspend fun reconcileDeletedRoms(scope: SyncScope): Int {
        val serverRomIds = when (val result = apiClient.getRomIdentifiers()) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> {
                Logger.info(
                    TAG,
                    "reconcileDeletedRoms: rom identifiers unavailable (${result.message}); withholding"
                )
                return 0
            }
        }
        if (serverRomIds.isEmpty()) {
            Logger.info(TAG, "reconcileDeletedRoms: server listed no roms at all; withholding")
            return 0
        }

        val visibility = scope.visibility as? RomMVisibility.Known ?: return 0
        unbindDeletedAndroidApps(serverRomIds, visibility, scope.ownerUserId)
        val missing = gameDao.getServerBackedIdsForOwner(ROMM_SOURCES, scope.ownerUserId)
            .filter { it.rommId !in serverRomIds && !visibility.hides(it.rommId, it.platformId) }
        if (missing.isEmpty()) return 0

        if (!gameRepository.get().romStorageVolumesReadable()) {
            Logger.warn(
                TAG,
                "reconcileDeletedRoms: a rom storage volume could not be read; withholding"
            )
            return 0
        }

        var deleted = 0
        var preserved = 0

        for (ref in missing) {
            val game = gameDao.getById(ref.id) ?: continue

            if (hasLocalContent(game)) {
                preserveOrphanedGame(game, scope.ownerUserId)
                preserved++
                continue
            }
            gameDao.delete(game.id)
            deleted++
        }

        if (deleted > 0) {
            homeTileDao.deleteTilesForMissingGames()
        }

        if (deleted > 0 || preserved > 0) {
            Logger.info(
                TAG,
                "reconcileDeletedRoms: $deleted rows removed, $preserved preserved for local content"
            )
        }
        return deleted
    }

    private suspend fun unbindDeletedAndroidApps(
        serverRomIds: Collection<Long>,
        visibility: RomMVisibility.Known,
        ownerUserId: Long?
    ) {
        if (!visibility.isAdmin && visibility.hiddenPlatformIds.isNotEmpty()) return
        val gone = gameDao.getServerBackedIdsForOwner(listOf(GameSource.ANDROID_APP), ownerUserId)
            .filter { it.rommId !in serverRomIds && !visibility.hides(it.rommId, it.platformId) }
        for (ref in gone) {
            val game = gameDao.getById(ref.id) ?: continue
            preserveOrphanedGame(game, ownerUserId)
        }
        if (gone.isNotEmpty()) {
            Logger.info(TAG, "reconcileDeletedRoms: ${gone.size} installed Android apps unbound from RomM")
        }
    }

    private fun storagePlatformId(platform: RomMPlatform): Long {
        val slug = PlatformDefinitions.resolveImportSlug(platform.slug, platform.displayName ?: platform.name, platform.fsSlug)
        return if (slug == ANDROID_SLUG) LocalPlatformIds.ANDROID else platform.id
    }

    private suspend fun resolveRemoteAndroidPlatformId(api: RomMApi): Long? {
        val platforms = api.getPlatforms().takeIf { it.isSuccessful }?.body() ?: return null
        return platforms.firstOrNull { storagePlatformId(it) == LocalPlatformIds.ANDROID }?.id
    }

    private suspend fun syncPlatformMetadata(remote: RomMPlatform) {
        val platformId = remote.id
        val effectiveSlug = PlatformDefinitions.resolveImportSlug(remote.slug, remote.displayName ?: remote.name, remote.fsSlug)
        if (effectiveSlug == ANDROID_SLUG) {
            syncAndroidPlatformMetadata(remote)
            return
        }
        val existing = platformDao.getById(platformId)
        val platformDef = PlatformDefinitions.getBySlug(effectiveSlug)
        val isSubPlatform = !effectiveSlug.equals(remote.slug, ignoreCase = true)

        val logoUrl = apiClient.buildMediaUrl(remote.logoUrl)
        val derivedNames = if (isSubPlatform) {
            PlatformDefinitions.getAliasDisplayName(effectiveSlug)
                ?: PlatformDefinitions.deriveDisplayName(effectiveSlug)
        } else {
            PlatformDefinitions.getAliasDisplayName(remote.slug)
                ?: PlatformDefinitions.deriveDisplayName(remote.slug)
                ?: PlatformDefinitions.deriveDisplayName(remote.fsSlug)
        }
        val normalizedName = if (isSubPlatform) {
            remote.customName?.takeIf { it.isNotBlank() }
                ?: derivedNames?.first ?: platformDef?.name ?: remote.name
        } else {
            remote.customName?.takeIf { it.isNotBlank() }
                ?: remote.displayName ?: derivedNames?.first ?: remote.name
        }
        val resolvedShortName = derivedNames?.second ?: platformDef?.shortName ?: normalizedName
        val entity = PlatformEntity(
            id = platformId,
            slug = effectiveSlug,
            fsSlug = remote.fsSlug,
            name = normalizedName,
            shortName = resolvedShortName,
            romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
            gameCount = remote.romCount,
            isVisible = existing?.isVisible ?: true,
            logoPath = logoUrl ?: existing?.logoPath,
            sortOrder = existing?.sortOrder ?: platformDef?.sortOrder ?: 999,
            lastScanned = existing?.lastScanned,
            syncEnabled = existing?.syncEnabled ?: true,
            customRomPath = existing?.customRomPath,
            combineContent = existing?.combineContent ?: false
        )

        if (existing == null) {
            platformDao.insert(entity)
        } else {
            platformDao.update(entity)
        }

        if (logoUrl != null && logoUrl.startsWith("http")) {
            imageCacheManager.queuePlatformLogoCache(platformId, logoUrl)
        }

        remote.firmware?.let { firmware ->
            if (firmware.isNotEmpty()) {
                biosRepository.syncPlatformFirmware(platformId, effectiveSlug, firmware)
            }
        }
    }

    private suspend fun syncAndroidPlatformMetadata(remote: RomMPlatform) {
        if (platformDao.getById(LocalPlatformIds.ANDROID) == null) {
            PlatformDefinitions.getBySlug(ANDROID_SLUG)?.let { def ->
                PlatformDefinitions.toLocalPlatformEntity(def)?.let { platformDao.insert(it) }
            }
        }

        val legacy = platformDao.getById(remote.id)
        if (legacy != null && legacy.slug == ANDROID_SLUG) {
            migrateLegacyAndroidPlatform(legacy)
        }

        val local = platformDao.getById(LocalPlatformIds.ANDROID) ?: return
        val logoUrl = apiClient.buildMediaUrl(remote.logoUrl)
        if (local.logoPath == null && logoUrl != null) {
            platformDao.updateLogoPath(LocalPlatformIds.ANDROID, logoUrl)
            if (logoUrl.startsWith("http")) {
                imageCacheManager.queuePlatformLogoCache(LocalPlatformIds.ANDROID, logoUrl)
            }
        }
    }

    private suspend fun migrateLegacyAndroidPlatform(legacy: PlatformEntity) {
        val owner = overlayWriter.activeOwnerId()
        database.withTransaction {
            val moved = gameDao.countByPlatform(legacy.id, owner)
            gameDao.migratePlatform(legacy.id, LocalPlatformIds.ANDROID, ANDROID_SLUG)
            emulatorConfigDao.migratePlatform(legacy.id, LocalPlatformIds.ANDROID)
            platformDao.getById(LocalPlatformIds.ANDROID)?.let { local ->
                platformDao.update(local.copy(
                    isVisible = legacy.isVisible,
                    syncEnabled = legacy.syncEnabled,
                    customRomPath = legacy.customRomPath ?: local.customRomPath
                ))
            }
            platformDao.deleteById(legacy.id)
            platformDao.updateGameCount(
                LocalPlatformIds.ANDROID,
                gameDao.countByPlatform(LocalPlatformIds.ANDROID, owner)
            )
            Logger.info(TAG, "migrateLegacyAndroidPlatform: moved $moved games from platform ${legacy.id} to local android platform")
        }
    }

    private suspend fun syncRom(rom: RomMRom, scope: SyncScope): Pair<Boolean, GameEntity> {
        val platformSlug = platformDao.getById(rom.platformId)?.slug
            ?: PlatformDefinitions.resolveImportSlug(rom.platformSlug, rom.platformName)
        val platformId = if (platformSlug == ANDROID_SLUG) LocalPlatformIds.ANDROID else rom.platformId
        val existing = gameDao.getByRommId(rom.id)

        val migrationSources = if (existing == null) {
            findUnlistedMigrationSources(rom, platformId, scope)
        } else emptyList()

        if (migrationSources.isNotEmpty()) {
            Logger.info(TAG, "syncRom: detected migration for ${rom.name} (igdbId=${rom.igdbId}): ${migrationSources.size} old entries -> new rommId=${rom.id}")
        }

        val validatedExisting = existing?.let { game ->
            val path = game.localPath
            if (path != null && !fileAccessLayer.exists(path)) {
                Logger.warn(TAG, "syncRom: existing localPath no longer exists: $path, clearing for ${rom.name}")
                game.copy(localPath = null, fileOrigin = FileOrigin.ADOPTED)
            } else {
                game
            }
        }

        val localDataSource = validatedExisting ?: GameMigrationHelper.aggregateMultiDiscData(migrationSources) { path ->
            val exists = fileAccessLayer.exists(path)
            if (!exists) {
                Logger.warn(TAG, "syncRom: migrated localPath no longer exists: $path")
            }
            exists
        }

        val screenshotUrls = rom.screenshotUrls.ifEmpty {
            rom.screenshotPaths?.mapNotNull { apiClient.buildMediaUrl(it) } ?: emptyList()
        }

        val contentChanged = existing != null && existing.title != rom.name
        if (contentChanged) {
            imageCacheManager.deleteGameImages(rom.id, clearDecoded = false)
            decodedImageCacheDirty = true
        }

        val artSources = mapOf(
            ArtSlot.COVER to apiClient.buildCoverUrls(rom),
            ArtSlot.BACKGROUND to apiClient.buildBackgroundUrls(rom),
            ArtSlot.LOGO to apiClient.buildLogoUrls(rom),
            ArtSlot.BOX_3D to apiClient.buildBox3dUrls(rom)
        ) + if (boxArtCacheEnabledForSync) {
            mapOf(
                ArtSlot.BOX_SPINE to apiClient.buildBoxSpineUrls(rom),
                ArtSlot.BOX_BACK to apiClient.buildBoxBackUrls(rom)
            )
        } else {
            emptyMap()
        }

        val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
        val shouldBeMultiDisc = isSiblingBasedMultiDisc

        if (existing?.isMultiDisc == true && !shouldBeMultiDisc && !rom.isFolderMultiDisc) {
            val existingDiscs = gameDiscDao.getDiscsForGame(existing.id)
            val wasSiblingBased = existingDiscs.any { it.parentRommId == null }
            if (wasSiblingBased) {
                gameDiscDao.deleteByGameId(existing.id)
            }
        }

        Logger.debug(TAG, "syncRom: ${rom.name} - rom.raId=${rom.raId}, existing.raId=${existing?.raId}")

        val installedPackageName = existing?.packageName
            ?.takeIf { platformId == LocalPlatformIds.ANDROID && installedAppResolver.isAppInstalled(it) }

        val game = GameEntity(
            id = existing?.id ?: 0,
            platformId = platformId,
            platformSlug = platformSlug,
            title = rom.name,
            sortTitle = RomMUtils.createSortTitle(rom.name),
            localPath = localDataSource?.localPath,
            fileOrigin = localDataSource?.fileOrigin ?: FileOrigin.ADOPTED,
            packageName = installedPackageName,
            rommId = rom.id,
            rommFileName = rom.fileName,
            igdbId = rom.igdbId,
            raId = rom.raId,
            titleId = existing?.titleId,
            source = when {
                installedPackageName != null -> GameSource.ANDROID_APP
                localDataSource?.localPath != null -> GameSource.ROMM_SYNCED
                else -> GameSource.ROMM_REMOTE
            },
            userRating = localDataSource?.userRating ?: 0,
            userDifficulty = localDataSource?.userDifficulty ?: 0,
            completion = localDataSource?.completion ?: 0,
            status = localDataSource?.status,
            backlogged = localDataSource?.backlogged ?: false,
            nowPlaying = localDataSource?.nowPlaying ?: false,
            isFavorite = localDataSource?.isFavorite ?: false,
            isMultiDisc = when {
                rom.isFolderMultiDisc -> localDataSource?.isMultiDisc == true && localDataSource.localPath != null
                shouldBeMultiDisc -> localDataSource?.isMultiDisc ?: false
                else -> false
            },
            playCount = localDataSource?.playCount ?: 0,
            playTimeMinutes = localDataSource?.playTimeMinutes ?: 0,
            lastPlayed = localDataSource?.lastPlayed,
            addedAt = localDataSource?.addedAt ?: java.time.Instant.now(),
            achievementCount = localDataSource?.achievementCount ?: 0,
            earnedAchievementCount = localDataSource?.earnedAchievementCount ?: 0,
            isGroupVisible = existing?.isGroupVisible ?: true
        ).withRomMetadata(rom)

        val isNew = existing == null
        gameDao.insert(game)

        if (migrationSources.isNotEmpty()) {
            val mergedId = gameDao.getByRommId(rom.id)?.id
            if (mergedId != null) {
                userRomsHiddenDao.inheritWhenAllHidden(
                    mergedId,
                    migrationSources.map { it.id },
                    migrationSources.size
                )
                carryArtOverrides(mergedId, migrationSources.map { it.id })
            }
            migrationSources.forEach { source ->
                gameDao.delete(source.id)
            }
            Logger.info(TAG, "syncRom: deleted ${migrationSources.size} old game entries after migration")
        }

        val savedGame = gameDao.getByRommId(rom.id)
        if (savedGame != null) {
            if (contentChanged) imageCacheManager.forgetCachedArt(savedGame.id)
            writeArtSources(savedGame.id, rom, artSources)
            val screenshots = gameScreenshotDao.replaceSources(savedGame.id, screenshotUrls)
            if (screenshotCacheEnabledForSync && screenshots.any { !it.isCachedFromSource }) {
                imageCacheManager.queueScreenshotCache(savedGame.id, rom.id, rom.name)
            }
            applyRomUserProperties(savedGame.id, rom, scope)
            syncGameFiles(savedGame.id, rom, platformSlug)
            if (rom.isFolderMultiDisc) {
                promoteFolderDiscsToDiscModel(
                    savedGame.id,
                    rom.id,
                    rom.files.orEmpty().filter { it.isGameContent }
                )
            }
        }

        return isNew to game
    }

    private suspend fun writeArtSources(gameId: Long, rom: RomMRom, sources: Map<ArtSlot, List<String>>) {
        sources.forEach { (slot, urls) ->
            if (urls.isEmpty()) imageCacheManager.forgetCachedArt(gameId, slot)
            recordArtSource(gameArtDao, imageCacheManager, gameId, slot, urls, rom.name, rommId = rom.id)
        }
    }

    private suspend fun carryArtOverrides(targetGameId: Long, sourceGameIds: List<Long>) {
        val current = gameArtDao.getForGame(targetGameId).associateBy { it.slot }
        val sourceRows = gameArtDao.getForGames(sourceGameIds)
        ArtSlot.entries.forEach { slot ->
            if (current[slot.name]?.overridePath != null) return@forEach
            val override = sourceGameIds.firstNotNullOfOrNull { id ->
                sourceRows.firstOrNull { it.gameId == id && it.slot == slot.name }?.overridePath
            } ?: return@forEach
            gameArtDao.setOverride(targetGameId, slot, override)
        }
    }

    private suspend fun findUnlistedMigrationSources(
        rom: RomMRom,
        platformId: Long,
        scope: SyncScope
    ): List<GameEntity> {
        val igdbId = rom.igdbId ?: return emptyList()
        val candidates = gameDao.getAllByIgdbIdAndPlatform(igdbId, platformId)
            .filter { it.rommId != null && it.rommId != rom.id }
        if (candidates.isEmpty()) return emptyList()
        val serverRomIds = scope.serverRomIds.get() ?: return emptyList()
        return candidates.filter { it.rommId !in serverRomIds }
    }

    private suspend fun applyRomUserProperties(gameId: Long, rom: RomMRom, scope: SyncScope) {
        val owner = scope.ownerUserId
        if (owner != null) overlayWriter.grantMembership(owner, gameId)

        val romUser = rom.romUser ?: return
        val unsent = scope.unsentUserProps.forGame(gameId)
        if (!unsent.keepsLocal(SyncType.RATING)) overlayDao.setUserRating(owner, gameId, romUser.rating)
        if (!unsent.keepsLocal(SyncType.DIFFICULTY)) overlayDao.setUserDifficulty(owner, gameId, romUser.difficulty)
        if (!unsent.keepsLocal(SyncType.COMPLETION)) overlayDao.setCompletion(owner, gameId, romUser.completion)
        if (!unsent.keepsLocal(SyncType.STATUS)) overlayDao.setStatus(owner, gameId, romUser.status)
        overlayDao.setBacklogged(owner, gameId, romUser.backlogged)
        overlayDao.setNowPlaying(owner, gameId, romUser.nowPlaying)
        overlayWriter.setRommMainSibling(owner, gameId, romUser.isMainSibling)
    }

    private suspend fun syncGameFiles(gameId: Long, rom: RomMRom, platformSlug: String) {
        gameFileSync.sync(gameId, rom, platformSlug, fileListIsAuthoritative = false)
    }

    private data class PlatformSyncResult(
        val added: Int,
        val updated: Int,
        val multiDiscGroups: List<MultiDiscGroup>,
        val error: String? = null,
        val decidedRomIds: Set<Long> = emptySet()
    )

    private class RomBatch {
        var added = 0
        var updated = 0
        val multiDiscGroups = mutableListOf<MultiDiscGroup>()
        val processedDiscIds = mutableSetOf<Long>()
        val skipIndividualDiscIds = mutableSetOf<Long>()
        val decidedRomIds = mutableSetOf<Long>()
    }

    private fun trackSiblingMultiDisc(rom: RomMRom, batch: RomBatch) {
        val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
        if (isSiblingBasedMultiDisc && rom.id !in batch.processedDiscIds) {
            val siblingIds = rom.sameGameSiblings.filter { it.isDiscVariant }.map { it.id }
            batch.processedDiscIds.add(rom.id)
            batch.processedDiscIds.addAll(siblingIds)
            batch.multiDiscGroups.add(
                MultiDiscGroup(
                    primaryRommId = rom.id,
                    siblingRommIds = siblingIds,
                    platformSlug = rom.platformSlug
                )
            )
        }
    }

    private suspend fun syncRomInBatch(
        rom: RomMRom,
        filters: SyncFilterPreferences,
        scope: SyncScope,
        batch: RomBatch
    ) {
        batch.decidedRomIds.add(rom.id)

        if (!RomMSyncFilter.shouldSyncRom(rom, filters)) return

        if (rom.id in batch.skipIndividualDiscIds) {
            Logger.debug(TAG, "syncRomInBatch: skipping individual disc ${rom.name} - folder-based version preferred")
            return
        }

        if (rom.id in batch.processedDiscIds) {
            Logger.debug(TAG, "syncRomInBatch: skipping disc ${rom.name} - registered on its multi-disc game")
            return
        }

        if (rom.isFolderMultiDisc) {
            val discSiblings = rom.sameGameSiblings.filter { it.isDiscVariant }
            if (discSiblings.isNotEmpty()) {
                val siblingIds = discSiblings.map { it.id }
                batch.skipIndividualDiscIds.addAll(siblingIds)
                Logger.info(TAG, "syncRomInBatch: ${rom.name} is folder-based multi-disc, marking ${siblingIds.size} individual disc siblings to skip")
                for (siblingId in siblingIds) {
                    val existingGame = gameDao.getByRommId(siblingId) ?: continue
                    if (hasLocalContent(existingGame)) {
                        Logger.info(TAG, "syncRomInBatch: keeping redundant individual disc game ${existingGame.title}, it holds local content")
                        continue
                    }
                    Logger.info(TAG, "syncRomInBatch: deleting redundant individual disc game: ${existingGame.title}")
                    gameDao.delete(existingGame.id)
                }
            }
        }

        try {
            val (isNew, _) = syncRom(rom, scope)
            if (isNew) batch.added++ else batch.updated++
            trackSiblingMultiDisc(rom, batch)
        } catch (e: Exception) {
            batch.decidedRomIds.remove(rom.id)
            Logger.warn(TAG, "syncRomInBatch: failed to sync ROM ${rom.id} (${rom.name}): ${e.message}")
        }
    }

    private suspend fun syncPlatformRoms(
        api: RomMApi,
        platform: RomMPlatform,
        filters: SyncFilterPreferences,
        scope: SyncScope
    ): PlatformSyncResult {
        val platformScope = scope.withFreshUnsentUserProps()
        val batch = RomBatch()
        var offset = 0
        var totalFetched = 0
        var processedRoms = 0
        var platformTotal: Int? = platform.romCount.takeIf { it > 0 }
        val storageId = storagePlatformId(platform)

        while (true) {
            val romsResponse = api.getRoms(
                apiClient.buildRomsQueryParams(
                    platformId = platform.id,
                    limit = SYNC_PAGE_SIZE,
                    offset = offset,
                    includeFiles = true
                )
            )

            if (!romsResponse.isSuccessful) {
                return PlatformSyncResult(
                    batch.added, batch.updated, batch.multiDiscGroups,
                    error = "Failed to fetch ROMs for ${platform.name}: ${romsResponse.code()}",
                    decidedRomIds = batch.decidedRomIds
                )
            }

            val romsPage = romsResponse.body()
            if (romsPage == null || romsPage.items.isEmpty()) break

            val pageCount = romsPage.items.size
            totalFetched += pageCount
            romsPage.total?.let { platformTotal = it }
            publishGameProgress(storageId, processedRoms, platformTotal ?: totalFetched)

            for (rom in romsPage.items) {
                processedRoms++
                publishGameProgress(storageId, processedRoms, platformTotal ?: totalFetched)
                syncRomInBatch(rom, filters, platformScope, batch)
            }

            if (pageCount < SYNC_PAGE_SIZE) break
            val knownTotal = platformTotal
            if (knownTotal != null && totalFetched >= knownTotal) break
            offset += SYNC_PAGE_SIZE
        }

        return PlatformSyncResult(
            batch.added, batch.updated, batch.multiDiscGroups,
            decidedRomIds = batch.decidedRomIds
        )
    }

    /**
     * Registers the discs of a folder-based multi-disc rom.
     *
     * RomM has two multi-disc shapes: separate roms per disc, handled by
     * [consolidateMultiDiscGroup], and one rom whose folder holds every disc, which reached this
     * far as ordinary files. Without a disc model those files are indistinguishable from version
     * variants, so the launcher treats a disc as a variant, isolates its saves under
     * `saves/variants/<fileId>` and offers neither the disc picker nor the in-game disc menu, and
     * no m3u is written because the generator requires the game to already be multi-disc.
     *
     * Disc numbers come from the file's own `(Disc N)` tag, the same source
     * [consolidateMultiDiscGroup] uses. Files with no disc tag are extras that happen to share the
     * folder and are left alone.
     */
    private suspend fun promoteFolderDiscsToDiscModel(
        gameId: Long,
        rommId: Long,
        files: List<RomMRomFile>
    ) {
        val discFiles = files.filter { it.isDiscVariant }
        if (discFiles.size < 2) return

        val game = gameDao.getById(gameId) ?: return
        val existingDiscs = gameDiscDao.getDiscsForGame(gameId)
        if (existingDiscs.isNotEmpty() && game.isMultiDisc) return

        val rows = discFiles.mapNotNull { file ->
            val number = file.discNumber ?: return@mapNotNull null
            val stored = gameFileDao.getByRommFileId(file.id)
            GameDiscEntity(
                id = existingDiscs.firstOrNull { it.discNumber == number }?.id ?: 0,
                gameId = gameId,
                discNumber = number,
                rommId = rommId,
                fileName = file.fileName,
                localPath = stored?.localPath,
                fileSize = file.fileSizeBytes,
                parentRommId = rommId
            )
        }.sortedBy { it.discNumber }
        if (rows.isEmpty()) return

        gameDiscDao.insertAll(rows)
        if (!game.isMultiDisc) {
            gameDao.update(game.copy(isMultiDisc = true))
        }
        Logger.info(
            TAG,
            "promoteFolderDiscs: gameId=$gameId rommId=$rommId registered ${rows.size} discs " +
                "(${rows.joinToString { "#${it.discNumber}" }}) from a folder-based multi-disc rom"
        )
    }

    private suspend fun consolidateMultiDiscGames(
        api: RomMApi,
        groups: List<MultiDiscGroup>,
        scope: SyncScope
    ) {
        for (group in groups) {
            try {
                consolidateMultiDiscGroup(api, group, scope)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun consolidateMultiDiscGroup(
        api: RomMApi,
        group: MultiDiscGroup,
        scope: SyncScope
    ) {
        val allRommIds = listOf(group.primaryRommId) + group.siblingRommIds
        val existingGames = allRommIds.mapNotNull { rommId ->
            gameDao.getByRommId(rommId)
        }.distinctBy { it.id }

        if (existingGames.isEmpty()) return

        val primaryGame = existingGames.find { it.isMultiDisc }
            ?: existingGames.minByOrNull { game ->
                allRommIds.indexOf(game.rommId).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            ?: existingGames.first()

        val redundantGames = existingGames.filter { it.id != primaryGame.id }

        if (primaryGame.isMultiDisc && redundantGames.isEmpty()) {
            gameDiscDao.deleteInvalidDiscs(primaryGame.id, allRommIds)
            return
        }

        val mergedIsFavorite = existingGames.any { it.isFavorite }
        val mergedPlayCount = existingGames.sumOf { it.playCount }
        val mergedPlayTime = existingGames.sumOf { it.playTimeMinutes }
        val mergedLastPlayed = existingGames.mapNotNull { it.lastPlayed }.maxOrNull()
        val mergedUserRating = existingGames.maxOf { it.userRating }
        val mergedUserDifficulty = existingGames.maxOf { it.userDifficulty }
        val mergedCompletion = existingGames.maxOf { it.completion }
        val mergedBacklogged = existingGames.any { it.backlogged }
        val mergedNowPlaying = existingGames.any { it.nowPlaying }
        val earliestAddedAt = existingGames.minOf { it.addedAt }

        gameDao.update(primaryGame.copy(addedAt = earliestAddedAt, isMultiDisc = true))

        val owner = scope.ownerUserId
        overlayDao.setFavorite(owner, primaryGame.id, mergedIsFavorite)
        overlayDao.setUserRating(owner, primaryGame.id, mergedUserRating)
        overlayDao.setUserDifficulty(owner, primaryGame.id, mergedUserDifficulty)
        overlayDao.setCompletion(owner, primaryGame.id, mergedCompletion)
        overlayDao.setBacklogged(owner, primaryGame.id, mergedBacklogged)
        overlayDao.setNowPlaying(owner, primaryGame.id, mergedNowPlaying)
        overlayDao.setMergedPlayTotals(
            owner,
            primaryGame.id,
            mergedPlayCount,
            mergedPlayTime,
            mergedLastPlayed ?: primaryGame.lastPlayed
        )

        val localPathsByRommId = existingGames
            .filter { it.localPath != null && it.rommId != null }
            .associate { it.rommId!! to it.localPath!! }

        val existingDiscs = gameDiscDao.getDiscsForGame(primaryGame.id)
        val existingDiscRommIds = existingDiscs.map { it.rommId }.toSet()

        val discsToInsert = mutableListOf<GameDiscEntity>()

        for (rommId in allRommIds) {
            if (rommId in existingDiscRommIds) continue

            val existingDisc = gameDiscDao.getByRommId(rommId)
            val localPath = localPathsByRommId[rommId] ?: existingDisc?.localPath

            val romData = try {
                val response = api.getRom(rommId)
                if (response.isSuccessful) response.body() else null
            } catch (e: Exception) {
                Logger.warn(TAG, "consolidateMultiDiscGroup: failed to fetch ROM $rommId: ${e.message}")
                null
            }

            if (romData == null && existingDisc == null) {
                Logger.warn(TAG, "consolidateMultiDiscGroup: skipping disc $rommId - no data available")
                continue
            }

            discsToInsert.add(GameDiscEntity(
                id = existingDisc?.id ?: 0,
                gameId = primaryGame.id,
                discNumber = romData?.discNumber ?: existingDisc?.discNumber ?: (discsToInsert.size + existingDiscs.size + 1),
                rommId = rommId,
                fileName = romData?.fileName ?: existingDisc?.fileName ?: "Disc",
                localPath = localPath,
                fileSize = romData?.fileSize ?: existingDisc?.fileSize ?: 0
            ))
        }

        if (discsToInsert.isNotEmpty()) {
            gameDiscDao.insertAll(discsToInsert)
        }

        gameDiscDao.deleteInvalidDiscs(primaryGame.id, allRommIds)

        for (redundantGame in redundantGames) {
            gameDao.delete(redundantGame.id)
        }
    }

    private suspend fun realignDirtyGames(platformId: Long, scope: SyncScope) {
        val dirtyGames = gameDao.getSyncDirtyGames(platformId, ROMM_SOURCES)
        if (dirtyGames.isEmpty()) return

        var realigned = 0
        for (game in dirtyGames) {
            val fileName = game.rommFileName?.takeIf { it.isNotBlank() } ?: continue
            val successor = gameDao
                .getCleanSyncedByFileNameAndPlatformForOwner(fileName, platformId, scope.ownerUserId)
                .filter { it.id != game.id && it.rommId != game.rommId && it.localPath == null }
                .singleOrNull() ?: continue

            gameDao.delete(successor.id)
            gameDao.insert(game.copy(rommId = successor.rommId, syncDirty = false))
            successor.rommId?.let { newRommId ->
                saveSyncDao.realignToRommId(game.id, scope.ownerUserId, newRommId)
                saveCacheDao.clearRemoteLinkage(game.id, scope.ownerUserId)
                game.rommId?.let { pendingSyncQueueDao.realignRommId(game.id, it, newRommId) }
            }
            realigned++
            Logger.info(
                TAG,
                "realignDirtyGames: ${game.title} rommId ${game.rommId} -> ${successor.rommId} matched by fileName"
            )
        }
        if (realigned > 0) {
            Logger.info(TAG, "realignDirtyGames: $realigned games realigned on platform $platformId")
        }
    }

    /**
     * Whether removing this row would take user content with it. Cached saves and states count:
     * they outlive the rom on the server, they are addressed by game id, and no foreign key
     * brings them back once the row is gone.
     */
    private suspend fun hasLocalContent(game: GameEntity): Boolean =
        game.localPath != null ||
            gameFileDao.getDownloadedCount(game.id) > 0 ||
            gameDiscDao.getDiscsForGame(game.id).any { it.localPath != null } ||
            saveCacheDao.countByGameAllOwners(game.id) > 0 ||
            stateCacheDao.countByGameAllOwners(game.id) > 0

    /**
     * Keeps a game whose rom left the server, under a synthetic id so nothing downstream
     * mistakes it for a synced one. Its sync rows go: they address a rom that no longer
     * answers, and leaving them is what makes a device retry an upload against a dead id
     * for as long as the game exists. The cached saves themselves are never touched.
     */
    private suspend fun preserveOrphanedGame(game: GameEntity, ownerUserId: Long?) {
        val syntheticId = game.rommId?.takeIf { it < 0 } ?: -game.id
        gameDao.insert(game.copy(rommId = syntheticId, syncDirty = false))
        if (syntheticId != game.rommId) {
            saveSyncDao.deleteByGameForOwner(game.id, ownerUserId)
            saveCacheDao.clearRemoteLinkage(game.id, ownerUserId)
            Logger.info(
                TAG,
                "preserveOrphanedGame: ${game.title} has local content, rommId ${game.rommId} -> $syntheticId, dropped remote sync state"
            )
        }
    }

    private suspend fun cleanupInvalidExtensionGames(platformId: Long, scope: SyncScope): Int {
        var cleared = 0
        val platformGames = gameDao.getBySourcesForOwner(ROMM_SOURCES, platformId, scope.ownerUserId)

        for (game in platformGames) {
            val localPath = game.localPath ?: continue
            val extension = localPath.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) continue

            if (RomMSyncFilter.isNonGameExtension(extension)) {
                gameDao.clearLocalPath(game.id)
                cleared++
                Logger.info(TAG, "cleanupInvalidExtensionGames: cleared invalid pointer for ${game.title}: $localPath")
            }
        }
        return cleared
    }

    private suspend fun cleanupLegacyPlatforms(remotePlatforms: List<RomMPlatform>) {
        val remoteIds = remotePlatforms.map { it.id }.toSet()
        val remoteByComposite = remotePlatforms.associateBy { it.slug to it.fsSlug }
        val remoteBySlug = remotePlatforms
            .filter { it.slug.isNotBlank() }
            .groupBy { it.slug }
        val remoteByFsSlug = remotePlatforms
            .filter { !it.fsSlug.isNullOrBlank() }
            .groupBy { it.fsSlug!! }
        val allLocal = platformDao.getAllPlatforms()

        for (local in allLocal) {
            if (local.id < 0) continue
            if (local.id in remoteIds) continue

            val matchingRemote = remoteByComposite[local.slug to local.fsSlug]
                ?: if (local.slug.isNotBlank()) {
                    val slugCandidates = remoteBySlug[local.slug] ?: emptyList()
                    slugCandidates.singleOrNull()
                } else null
                ?: if (local.slug.isBlank() && !local.fsSlug.isNullOrBlank()) {
                    val fsCandidates = remoteByFsSlug[local.fsSlug] ?: emptyList()
                    fsCandidates.singleOrNull()
                } else null

            if (matchingRemote == null) {
                Logger.warn(TAG, "cleanupLegacyPlatforms: no confident match for " +
                    "platform ${local.id} (slug=${local.slug}, fsSlug=${local.fsSlug}), skipping")
                continue
            }

            migratePlatformData(local, matchingRemote)
            Logger.info(TAG, "cleanupLegacyPlatforms: migrated platform " +
                "${local.id} (${local.slug}) -> ${matchingRemote.id} (${matchingRemote.slug})")
        }
    }

    private suspend fun migratePlatformData(old: PlatformEntity, remote: RomMPlatform) {
        val label = "${old.name} (${old.id} -> ${remote.id})"
        val owner = overlayWriter.activeOwnerId()
        database.withTransaction {
            val gameCount = gameDao.countByPlatform(old.id, owner)
            gameDao.migratePlatform(old.id, remote.id, remote.slug)
            Logger.info(TAG, "migratePlatformData [$label]: moved $gameCount games")

            emulatorConfigDao.migratePlatform(old.id, remote.id)

            val oldHasOverrides = platformLibretroSettingsDao.hasOverrides(old.id)
            val newHasOverrides = platformLibretroSettingsDao.hasOverrides(remote.id)
            if (oldHasOverrides && !newHasOverrides) {
                platformLibretroSettingsDao.migratePlatform(old.id, remote.id)
                Logger.info(TAG, "migratePlatformData [$label]: transferred libretro overrides")
            } else if (oldHasOverrides) {
                Logger.info(TAG, "migratePlatformData [$label]: kept newer libretro overrides on target")
            }

            val firmwareMigrated = migrateFirmware(old.id, remote.id)
            if (firmwareMigrated > 0) {
                Logger.info(TAG, "migratePlatformData [$label]: migrated $firmwareMigrated firmware entries")
            }

            if (old.slug.isNotBlank() && old.slug != remote.slug) {
                controllerMappingDao.migratePlatformSlug(old.slug, remote.slug)
                Logger.info(TAG, "migratePlatformData [$label]: remapped controller bindings ${old.slug} -> ${remote.slug}")
            }

            val newPlatform = platformDao.getById(remote.id)
            if (newPlatform != null) {
                platformDao.update(newPlatform.copy(
                    isVisible = old.isVisible,
                    syncEnabled = old.syncEnabled,
                    customRomPath = old.customRomPath ?: newPlatform.customRomPath,
                    sortOrder = old.sortOrder
                ))
            }

            platformDao.deleteById(old.id)
        }
    }

    private suspend fun migrateFirmware(oldPlatformId: Long, newPlatformId: Long): Int {
        val oldEntries = firmwareDao.getByPlatform(oldPlatformId)
        if (oldEntries.isEmpty()) return 0

        val newEntries = firmwareDao.getByPlatform(newPlatformId)
        val newByFileName = newEntries.associateBy { it.fileName }
        var migrated = 0

        for (oldEntry in oldEntries) {
            val newEntry = newByFileName[oldEntry.fileName]
            if (newEntry != null) {
                if (oldEntry.localPath != null && newEntry.localPath == null) {
                    firmwareDao.updateLocalPath(newEntry.id, oldEntry.localPath, oldEntry.downloadedAt)
                    migrated++
                }
            } else {
                firmwareDao.upsert(oldEntry.copy(
                    id = 0,
                    platformId = newPlatformId
                ))
                migrated++
            }
        }
        return migrated
    }

}
