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
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.model.VersionGroups
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
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
    private val pendingSyncQueueDao: com.nendo.argosy.data.local.dao.PendingSyncQueueDao
) {
    private val api: RomMApi? get() = connectionManager.getApi()
    private val syncMutex = Mutex()
    private var boxArtCacheEnabledForSync = true
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
            val hasGenres = gameDao.getDistinctGenres(owner).isNotEmpty()
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
            return@withContext SyncResult(0, 0, 0, 0, listOf("Sync already in progress"))
        }

        try {
            return@withContext doSyncLibrary(onProgress)
        } finally {
            flushDecodedImageCache()
            syncMutex.unlock()
        }
    }

    suspend fun syncPlatform(platformId: Long): SyncResult = withContext(NonCancellable + Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult(0, 0, 0, 0, listOf("Sync already in progress"))
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

    private suspend fun doSyncPlatform(platformId: Long): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))

        platformDao.getById(platformId)
            ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found locally"))

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
        val scope = resolveSyncScope(currentApi)

        _syncProgress.value = SyncProgress(isSyncing = true, platformsTotal = 1)

        try {
            val remoteQueryId = if (platformId == LocalPlatformIds.ANDROID) {
                resolveRemoteAndroidPlatformId(currentApi)
                    ?: return SyncResult(0, 0, 0, 0, listOf("Android platform not found on server"))
            } else {
                platformId
            }
            val platformResponse = currentApi.getPlatform(remoteQueryId)
            if (!platformResponse.isSuccessful) {
                return SyncResult(0, 0, 0, 0, listOf("Failed to fetch platform: ${platformResponse.code()}"))
            }

            val platform = platformResponse.body()
                ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found"))

            syncPlatformMetadata(platform)

            _syncProgress.value = _syncProgress.value.copy(
                currentPlatform = platform.name,
                currentPlatformSlug = platform.slug
            )

            val storageId = storagePlatformId(platform)
            gameDao.markSyncDirtyForOwner(storageId, ROMM_SOURCES, scope.ownerUserId)

            val result = syncPlatformRoms(currentApi, platform, filters, scope)

            val gamesDeleted = processPostPlatformSync(currentApi, storageId, result, filters, scope)

            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)

            androidGameScanner.get().relinkInstalledRommAndroidApps()

            syncVirtualCollectionsUseCase.get()()

            return SyncResult(1, result.added, result.updated, gamesDeleted, result.error?.let { listOf(it) } ?: emptyList())
        } catch (e: Exception) {
            return SyncResult(0, 0, 0, 0, listOf(e.message ?: "Platform sync failed"))
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
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

        absorbConsolidatedGames(result.absorptionPairs, scope)
        consolidateMultiDiscGames(api, result.multiDiscGroups, scope)

        if (result.error == null) {
            realignDirtyGames(platformId, scope)
        }

        cleanupInvalidExtensionGames(platformId, scope)
        gamesDeleted += cleanupDuplicateGames(platformId, scope)

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

    private suspend fun doSyncLibrary(
        onProgress: ((current: Int, total: Int, platformName: String) -> Unit)?
    ): SyncResult {
        val currentApi = api ?: return SyncResult(0, 0, 0, 0, listOf("Not connected"))
        val errors = mutableListOf<String>()
        var platformsSynced = 0
        var gamesAdded = 0
        var gamesUpdated = 0
        var gamesDeleted = 0

        val prefs = userPreferencesRepository.preferences.first()
        val filters = prefs.syncFilters
        boxArtCacheEnabledForSync = prefs.boxArtCacheEnabled
        val scope = resolveSyncScope(currentApi)

        _syncProgress.value = SyncProgress(isSyncing = true)

        try {
            val platformsResponse = retryOnThrow(PLATFORM_FETCH_ATTEMPTS, PLATFORM_FETCH_BACKOFF_MS) {
                currentApi.getPlatforms()
            }

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

            for (platform in platforms) {
                syncPlatformMetadata(platform)
            }

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

            _syncProgress.value = _syncProgress.value.copy(platformsTotal = enabledPlatforms.size)

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
                    continue
                }

                try {
                    gameDao.markSyncDirtyForOwner(storageId, ROMM_SOURCES, scope.ownerUserId)

                    val result = syncPlatformRoms(currentApi, platform, filters, scope)
                    gamesAdded += result.added
                    gamesUpdated += result.updated
                    result.error?.let { errors.add(it) }

                    gamesDeleted += processPostPlatformSync(currentApi, storageId, result, filters, scope)

                    platformsSynced++
                    if (result.error == null) {
                        userPreferencesRepository.addSyncResumeCompletedPlatform(storageId)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.warn(
                        TAG,
                        "doSyncLibrary: platform ${platform.name} failed, continuing with the rest: ${e.message}"
                    )
                    errors.add("${platform.name}: ${e.message ?: "sync failed"}")
                    gameDao.clearSyncDirty(storageId, ROMM_SOURCES)
                }
            }

            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)

            if (filters.deleteOrphans && errors.isEmpty()) {
                gamesDeleted += reconcileDeletedRoms(scope)
            }

            userPreferencesRepository.clearSyncResume()

            cleanupLegacyPlatforms(platforms)

            androidGameScanner.get().relinkInstalledRommAndroidApps()

            userPreferencesRepository.setLastRommSyncTime(Instant.now())

            syncVirtualCollectionsUseCase.get()()

            attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)

        } catch (e: Exception) {
            errors.add(e.message ?: "Sync failed")
            gameDao.clearAllSyncDirtyForOwner(scope.ownerUserId)
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
        }

        gameRepository.get().cleanupEmptyNumericFolders()

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
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
     * [serverRomIds] is the whole library's id set as the server sees it for this account, or null
     * when the call failed. Null means the pass cannot prove any rom is gone and every deletion
     * path falls back to the evidence it had before the set existed.
     */
    private data class SyncScope(
        val ownerUserId: Long?,
        val visibility: RomMVisibility,
        val serverRomIds: Set<Long>?
    )

    private suspend fun resolveSyncScope(api: RomMApi): SyncScope {
        val ownerUserId = overlayWriter.activeOwnerId()
        if (ownerUserId != null) overlayWriter.adoptLibraryIfUnclaimed(ownerUserId)
        val identifiers = when (val result = apiClient.getRomIdentifiers()) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> {
                Logger.info(
                    TAG,
                    "resolveSyncScope: rom identifiers unavailable (${result.message}); deletions fall back to pass evidence"
                )
                null
            }
        }
        return SyncScope(ownerUserId, visibilityService.fetch(api), identifiers)
    }

    /**
     * Turns "the server did not return this rom" into either a mask change or a deletion.
     *
     * Not-returned is only evidence of deletion when the server has also said, explicitly, that
     * the rom is not hidden from this account. Without that statement - an older server, a failed
     * call - nothing is deleted, because for a restricted account absence and invisibility look
     * identical and one of the two outcomes is unrecoverable.
     *
     * [decidedRomIds] separates the two reasons a row can still be dirty. A rom the pages returned
     * and the pass then set aside - a filter excluded it, a dedup key was already taken, a folder
     * multi-disc parent owns its discs - was decided against, and removing it is the point. A rom
     * the pages never mentioned is only removable once `GET /api/roms/identifiers` agrees it is
     * gone; while the server still lists it, absence here means it moved platform or the pass
     * failed on it, and the row stays.
     */
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
        val serverRomIds = scope.serverRomIds
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

            if (rommId != null &&
                rommId !in decidedRomIds &&
                serverRomIds != null &&
                rommId in serverRomIds
            ) {
                stillListed++
                continue
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

        val logoUrl = remote.logoUrl?.let { apiClient.buildMediaUrl(it) }
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
        val logoUrl = remote.logoUrl?.let { apiClient.buildMediaUrl(it) }
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

    private suspend fun syncRom(rom: RomMRom, scope: SyncScope, syncFiles: Boolean = true): Pair<Boolean, GameEntity> {
        val platformSlug = platformDao.getById(rom.platformId)?.slug
            ?: PlatformDefinitions.resolveImportSlug(rom.platformSlug, rom.platformName)
        val platformId = if (platformSlug == ANDROID_SLUG) LocalPlatformIds.ANDROID else rom.platformId
        val existing = gameDao.getByRommId(rom.id)

        val migrationSources = if (existing == null && rom.igdbId != null) {
            gameDao.getAllByIgdbIdAndPlatform(rom.igdbId, platformId)
                .filter { it.rommId != null && it.rommId != rom.id }
        } else emptyList()

        if (migrationSources.isNotEmpty()) {
            Logger.info(TAG, "syncRom: detected migration for ${rom.name} (igdbId=${rom.igdbId}): ${migrationSources.size} old entries -> new rommId=${rom.id}")
        }

        val validatedExisting = existing?.let { game ->
            val path = game.localPath
            if (path != null && !fileAccessLayer.exists(path)) {
                Logger.warn(TAG, "syncRom: existing localPath no longer exists: $path, clearing for ${rom.name}")
                game.copy(localPath = null)
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
            rom.screenshotPaths?.map { apiClient.buildMediaUrl(it) } ?: emptyList()
        }

        val contentChanged = existing != null && existing.title != rom.name
        if (contentChanged) {
            imageCacheManager.deleteGameImages(rom.id, clearDecoded = false)
            decodedImageCacheDirty = true
        }

        val backgroundUrl = rom.backgroundUrls.firstOrNull()
            ?: screenshotUrls.getOrNull(1)
            ?: screenshotUrls.getOrNull(0)
        val cachedBackground = when {
            !contentChanged && existing?.backgroundPath?.startsWith("/") == true -> existing.backgroundPath
            backgroundUrl != null -> {
                imageCacheManager.queueBackgroundCache(backgroundUrl, rom.id, rom.name)
                backgroundUrl
            }
            else -> null
        }

        val coverUrl = rom.coverLarge?.let { apiClient.buildMediaUrl(it) }
        val cachedCover = when {
            existing?.coverSetManually == true -> existing.coverPath
            !contentChanged && existing?.coverPath?.startsWith("/") == true -> existing.coverPath
            coverUrl != null -> {
                imageCacheManager.queueCoverCache(coverUrl, rom.id, rom.name)
                coverUrl
            }
            else -> null
        }
        val syncedOriginalCover = when {
            existing?.coverSetManually == true -> existing.originalCoverPath
            else -> null
        }

        val boxBackUrl = if (boxArtCacheEnabledForSync) {
            rom.ssMetadata?.box2dBackPath?.let { apiClient.buildResourceUrl(it) }
        } else null
        val cachedBoxBack = when {
            !contentChanged && existing?.boxBackPath?.startsWith("/") == true -> existing.boxBackPath
            boxBackUrl != null -> {
                imageCacheManager.queueBoxFaceCache(boxBackUrl, rom.id, rom.name, ImageCacheManager.BoxFace.BACK)
                boxBackUrl
            }
            else -> null
        }
        val boxSpineUrl = if (boxArtCacheEnabledForSync) {
            rom.ssMetadata?.box2dSidePath?.let { apiClient.buildResourceUrl(it) }
        } else null
        val cachedBoxSpine = when {
            !contentChanged && existing?.boxSpinePath?.startsWith("/") == true -> existing.boxSpinePath
            boxSpineUrl != null -> {
                imageCacheManager.queueBoxFaceCache(boxSpineUrl, rom.id, rom.name, ImageCacheManager.BoxFace.SPINE)
                boxSpineUrl
            }
            else -> null
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
            coverPath = cachedCover,
            originalCoverPath = syncedOriginalCover,
            coverSetManually = existing?.coverSetManually ?: false,
            backgroundPath = cachedBackground,
            boxBackPath = cachedBoxBack,
            boxSpinePath = cachedBoxSpine,
            screenshotPaths = screenshotUrls.joinToString(","),
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
            earnedAchievementCount = localDataSource?.earnedAchievementCount ?: 0
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
            }
            migrationSources.forEach { source ->
                gameDao.delete(source.id)
            }
            Logger.info(TAG, "syncRom: deleted ${migrationSources.size} old game entries after migration")
        }

        val savedGame = gameDao.getByRommId(rom.id)
        if (savedGame != null) {
            applyRomUserProperties(savedGame.id, rom, scope)
            if (syncFiles) syncGameFiles(savedGame.id, rom, platformSlug)
        }

        return isNew to game
    }

    /**
     * Records the server's rom_user block against the account that fetched it, and re-grants that
     * account's membership. The block is per user, so writing it onto the library row alone would
     * publish one account's rating and status to every other account on the device.
     *
     * `rom_user.hidden` lands in `user_roms_hidden` rather than on the overlay row: it is the
     * user's own choice and the peer of the local toggle, not the admin-imposed `serverHidden`.
     * A local hide that has not been pushed yet wins, or the server would undo it on the next
     * pass before the queue ever drained.
     */
    private suspend fun applyRomUserProperties(gameId: Long, rom: RomMRom, scope: SyncScope) {
        val owner = scope.ownerUserId
        if (owner != null) overlayWriter.grantMembership(owner, gameId)

        val romUser = rom.romUser ?: return
        overlayDao.setUserRating(owner, gameId, romUser.rating)
        overlayDao.setUserDifficulty(owner, gameId, romUser.difficulty)
        overlayDao.setCompletion(owner, gameId, romUser.completion)
        overlayDao.setStatus(owner, gameId, romUser.status)
        overlayDao.setBacklogged(owner, gameId, romUser.backlogged)
        overlayDao.setNowPlaying(owner, gameId, romUser.nowPlaying)

        if (pendingSyncQueueDao.hasPending(gameId, com.nendo.argosy.data.local.entity.SyncType.HIDDEN)) return
        if (romUser.hidden) {
            userRomsHiddenDao.hide(owner, gameId)
        } else {
            userRomsHiddenDao.unhide(owner, gameId)
        }
    }

    private suspend fun syncGameFiles(gameId: Long, rom: RomMRom, platformSlug: String) {
        gameFileSync.sync(gameId, rom, platformSlug, fileListIsAuthoritative = false)
    }

    private data class PlatformSyncResult(
        val added: Int,
        val updated: Int,
        val multiDiscGroups: List<MultiDiscGroup>,
        val error: String? = null,
        val absorptionPairs: List<Pair<Long, Long>> = emptyList(),
        val decidedRomIds: Set<Long> = emptySet()
    )

    private suspend fun syncPlatformRoms(
        api: RomMApi,
        platform: RomMPlatform,
        filters: SyncFilterPreferences,
        scope: SyncScope
    ): PlatformSyncResult {
        var added = 0
        var updated = 0
        val seenDedupKeys = mutableSetOf<String>()
        val multiDiscGroups = mutableListOf<MultiDiscGroup>()
        val processedDiscIds = mutableSetOf<Long>()
        val skipIndividualDiscIds = mutableSetOf<Long>()
        val siblingGroups = mutableMapOf<Long, SiblingGroup>()
        val absorptionPairs = mutableListOf<Pair<Long, Long>>()
        val decidedRomIds = mutableSetOf<Long>()
        var offset = 0
        var totalFetched = 0
        var platformTotal: Int? = null

        fun groupFor(rom: RomMRom): SiblingGroup {
            val ids = listOf(rom.id) +
                rom.effectiveSiblings.filter { !it.isDiscVariant }.map { it.id }
            val existingGroups = ids.mapNotNull { siblingGroups[it] }.distinct()
            val group = existingGroups.firstOrNull() ?: SiblingGroup()
            existingGroups.drop(1).forEach { other ->
                group.members.addAll(other.members)
                group.expectedIds.addAll(other.expectedIds)
                if (group.mainSiblingId == null) group.mainSiblingId = other.mainSiblingId
                if (group.winner == null) group.winner = other.winner
            }
            group.expectedIds.addAll(ids)
            ids.forEach { siblingGroups[it] = group }
            return group
        }

        fun trackSiblingMultiDisc(rom: RomMRom) {
            val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
            if (isSiblingBasedMultiDisc && rom.id !in processedDiscIds) {
                val discSiblings = rom.effectiveSiblings.filter { it.isDiscVariant }
                val siblingIds = discSiblings.map { it.id }

                processedDiscIds.add(rom.id)
                processedDiscIds.addAll(siblingIds)

                multiDiscGroups.add(MultiDiscGroup(
                    primaryRommId = rom.id,
                    siblingRommIds = siblingIds,
                    platformSlug = platform.slug
                ))
            }
        }

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
                    added, updated, multiDiscGroups,
                    error = "Failed to fetch ROMs for ${platform.name}: ${romsResponse.code()}",
                    decidedRomIds = decidedRomIds
                )
            }

            val romsPage = romsResponse.body()
            if (romsPage == null || romsPage.items.isEmpty()) break

            val pageCount = romsPage.items.size
            totalFetched += pageCount
            romsPage.total?.let { platformTotal = it }
            _syncProgress.value = _syncProgress.value.copy(
                gamesTotal = platformTotal ?: totalFetched,
                gamesDone = totalFetched
            )

            for (rom in romsPage.items) {
                decidedRomIds.add(rom.id)

                if (!RomMSyncFilter.shouldSyncRom(rom, filters)) continue

                if (rom.id in skipIndividualDiscIds) {
                    Logger.debug(TAG, "syncPlatformRoms: skipping individual disc ${rom.name} - folder-based version preferred")
                    continue
                }

                if (rom.isFolderMultiDisc) {
                    val discSiblings = rom.effectiveSiblings.filter { it.isDiscVariant }
                    if (discSiblings.isNotEmpty()) {
                        val siblingIds = discSiblings.map { it.id }
                        skipIndividualDiscIds.addAll(siblingIds)
                        Logger.info(TAG, "syncPlatformRoms: ${rom.name} is folder-based multi-disc, marking ${siblingIds.size} individual disc siblings to skip")
                        for (siblingId in siblingIds) {
                            val existingGame = gameDao.getByRommId(siblingId) ?: continue
                            if (hasLocalContent(existingGame)) {
                                Logger.info(TAG, "syncPlatformRoms: keeping redundant individual disc game ${existingGame.title}, it holds local content")
                                continue
                            }
                            Logger.info(TAG, "syncPlatformRoms: deleting redundant individual disc game: ${existingGame.title}")
                            gameDao.delete(existingGame.id)
                        }
                    }
                }

                if (rom.hasNonDiscSiblings) {
                    val group = groupFor(rom)
                    group.members.add(SiblingMember(rom.id, rom.regions, rom.files))
                    rom.effectiveSiblings
                        .firstOrNull { !it.isDiscVariant && it.isMainSibling == true }
                        ?.let { group.mainSiblingId = it.id }
                    group.winner = chooseWinner(group, rom, filters)

                    if (group.isComplete) {
                        val outcome = consolidateSiblingGroup(
                            api, group, platform, absorptionPairs, scope, ::trackSiblingMultiDisc
                        )
                        added += outcome.added
                        updated += outcome.updated
                    }
                    continue
                }

                val dedupKey = RomMUtils.getDedupKey(rom)
                if (dedupKey != null && !seenDedupKeys.add(dedupKey)) continue

                try {
                    val (isNew, _) = syncRom(rom, scope)
                    if (isNew) added++ else updated++
                    trackSiblingMultiDisc(rom)
                } catch (e: Exception) {
                    decidedRomIds.remove(rom.id)
                    Logger.warn(TAG, "syncPlatformRoms: failed to sync ROM ${rom.id} (${rom.name}): ${e.message}")
                }
            }

            if (pageCount < SYNC_PAGE_SIZE) break
            val knownTotal = platformTotal
            if (knownTotal != null && totalFetched >= knownTotal) break
            offset += SYNC_PAGE_SIZE
        }

        for (group in siblingGroups.values.distinct()) {
            val outcome = consolidateSiblingGroup(
                api, group, platform, absorptionPairs, scope, ::trackSiblingMultiDisc
            )
            added += outcome.added
            updated += outcome.updated
        }

        return PlatformSyncResult(
            added, updated, multiDiscGroups,
            absorptionPairs = absorptionPairs,
            decidedRomIds = decidedRomIds
        )
    }

    private class ConsolidationOutcome(val added: Int, val updated: Int)

    /**
     * Consolidates one sibling group and drops its retained rom. Runs as soon as a group
     * has seen every sibling it expects, so only in-flight groups stay resident; the pass
     * after the last page picks up groups whose siblings were filtered out and never
     * completed.
     */
    private suspend fun consolidateSiblingGroup(
        api: RomMApi,
        group: SiblingGroup,
        platform: RomMPlatform,
        absorptionPairs: MutableList<Pair<Long, Long>>,
        scope: SyncScope,
        trackMultiDisc: (RomMRom) -> Unit
    ): ConsolidationOutcome {
        if (group.consolidated) return ConsolidationOutcome(0, 0)
        val members = group.members
        if (members.isEmpty()) return ConsolidationOutcome(0, 0)
        val winner = resolveGroupWinner(api, group) ?: return ConsolidationOutcome(0, 0)

        group.consolidated = true
        var added = 0
        var updated = 0
        try {
            val (isNew, _) = syncRom(winner, scope, syncFiles = false)
            if (isNew) added++ else updated++
            trackMultiDisc(winner)
            val gameId = gameDao.getByRommId(winner.id)?.id
                ?: return ConsolidationOutcome(added, updated)
            val validFileIds = mutableListOf<Long>()
            for (member in members) {
                validFileIds += syncVersionFiles(gameId, member, platform.slug)
                if (member.id != winner.id) {
                    gameDao.getByRommId(member.id)?.let { loser ->
                        absorptionPairs.add(loser.id to gameId)
                    }
                }
            }
            if (validFileIds.isNotEmpty()) {
                gameFileDao.deleteInvalidFiles(gameId, validFileIds)
            }
            Logger.info(TAG, "syncPlatformRoms: consolidated ${members.size} sibling versions under ${winner.name} (${winner.regions})")
        } catch (e: Exception) {
            Logger.warn(TAG, "syncPlatformRoms: failed to consolidate sibling group for ${winner.name}: ${e.message}")
        } finally {
            group.winner = null
            group.members.clear()
        }
        return ConsolidationOutcome(added, updated)
    }

    private suspend fun absorbConsolidatedGames(pairs: List<Pair<Long, Long>>, scope: SyncScope) {
        for ((loserId, winnerId) in pairs) {
            if (loserId == winnerId) continue
            val loser = gameDao.getById(loserId) ?: continue
            val versionGroup = loser.rommId?.let { VersionGroups.groupKey(it) } ?: continue
            val channelPrefix = loser.regions
                ?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                ?: "Version ${loser.rommId}"
            try {
                database.gameAbsorptionDao().absorb(
                    loserId = loserId,
                    winnerId = winnerId,
                    channelPrefix = channelPrefix,
                    versionGroup = versionGroup,
                    loserLocalPath = loser.localPath,
                    loserDownloadedAtEpoch = loser.addedAt.toEpochMilli(),
                    playTimeMinutes = loser.playTimeMinutes,
                    playCount = loser.playCount,
                    isFavorite = loser.isFavorite,
                    userRating = loser.userRating,
                    userDifficulty = loser.userDifficulty,
                    status = loser.status,
                    ownerUserId = scope.ownerUserId
                )
                Logger.info(TAG, "absorbConsolidatedGames: absorbed ${loser.title} ($loserId) into game $winnerId as '$channelPrefix'")
            } catch (e: Exception) {
                Logger.warn(TAG, "absorbConsolidatedGames: failed for $loserId -> $winnerId: ${e.message}")
            }
        }
    }

    /**
     * A group can learn its declared main sibling from a member fetched after that sibling
     * was already seen and reduced to a projection. Re-fetch it in that case rather than
     * consolidating under the wrong ROM.
     */
    private suspend fun resolveGroupWinner(api: RomMApi, group: SiblingGroup): RomMRom? {
        val running = group.winner ?: return null
        val mainId = group.mainSiblingId
        if (mainId == null || running.id == mainId) return running
        if (group.members.none { it.id == mainId }) return running

        return try {
            val response = api.getRom(mainId)
            response.body()?.takeIf { response.isSuccessful } ?: running
        } catch (e: Exception) {
            Logger.warn(TAG, "resolveGroupWinner: failed to fetch main sibling $mainId: ${e.message}")
            running
        }
    }

    private suspend fun syncVersionFiles(
        gameId: Long,
        member: SiblingMember,
        platformSlug: String
    ): List<Long> {
        val files = member.files
            ?.filter { !it.fileName.startsWith(".") }
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()
        val rootPathLength = files.minOf { it.filePath.length }
        val groupKey = VersionGroups.groupKey(member.id)
        val regions = member.regions?.joinToString(",")?.takeIf { it.isNotBlank() }

        val entities = files.map { file ->
            val existing = gameFileDao.getByRommFileId(file.id)
            val isNested = file.filePath.length > rootPathLength
            val category = when {
                file.category != null -> VariantCategory.fromKey(file.category)
                isNested -> VariantCategory.UNKNOWN
                else -> VariantCategory.GAME
            }
            GameFileEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                rommFileId = file.id,
                romId = file.romId,
                fileName = file.fileName,
                filePath = file.filePath,
                category = category.key,
                fileSize = file.fileSizeBytes,
                localPath = existing?.localPath,
                downloadedAt = existing?.downloadedAt,
                isLaunchTarget = category.isLaunchTarget && !(isNested && file.category == null),
                isMultiDisc = file.isDiscVariant || (existing?.isMultiDisc ?: false),
                m3uPath = existing?.m3uPath,
                regions = regions,
                versionGroup = groupKey,
                trackTitle = file.trackMeta?.title ?: existing?.trackTitle,
                trackNumber = file.trackMeta?.track ?: existing?.trackNumber,
                durationSeconds = file.trackMeta?.durationSeconds ?: existing?.durationSeconds
            )
        }
        gameFileDao.insertAll(entities)
        promoteFolderDiscsToDiscModel(gameId, member.id, files)
        return files.mapNotNull { if (it.id > 0) it.id else null }
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

    private suspend fun cleanupDuplicateGames(platformId: Long, scope: SyncScope): Int {
        val platformGames = gameDao.getBySourcesForOwner(ROMM_SOURCES, platformId, scope.ownerUserId)
        val deletedIds = mutableSetOf<Long>()

        val gamesByIgdb = platformGames
            .filter { it.igdbId != null }
            .groupBy { it.igdbId to it.regions?.lowercase() }

        for ((_, duplicates) in gamesByIgdb) {
            deletedIds.addAll(deleteRedundantDuplicates(duplicates))
        }

        val remainingGames = platformGames.filter { it.id !in deletedIds }
        val gamesByTitle = remainingGames
            .groupBy { it.title.lowercase() to it.regions?.lowercase() }

        for ((_, duplicates) in gamesByTitle) {
            deletedIds.addAll(deleteRedundantDuplicates(duplicates))
        }

        return deletedIds.size
    }

    private suspend fun deleteRedundantDuplicates(duplicates: List<GameEntity>): List<Long> {
        if (duplicates.size <= 1) return emptyList()

        val sorted = duplicates.sortedWith(
            compareByDescending<GameEntity> { it.achievementCount > 0 }
                .thenByDescending { it.localPath != null }
                .thenBy { it.id }
        )

        val deleted = mutableListOf<Long>()
        for (game in sorted.drop(1)) {
            if (hasLocalContent(game)) {
                Logger.info(TAG, "cleanupDuplicateGames: preserving local-content duplicate ${game.title} (${game.localPath})")
                continue
            }
            gameDao.delete(game.id)
            deleted.add(game.id)
        }
        return deleted
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

/**
 * What consolidation needs from a sibling: its id to re-attribute the losing game, its
 * regions to rank candidates, and its files to register as versions of the winner.
 * Retaining the whole [RomMRom] instead holds every metadata blob plus each member's own
 * sibling list for the entire platform, which is quadratic in group size.
 */
internal class SiblingMember(
    val id: Long,
    val regions: List<String>?,
    val files: List<RomMRomFile>?
)

internal class SiblingGroup {
    val members = mutableListOf<SiblingMember>()
    val expectedIds = mutableSetOf<Long>()
    var mainSiblingId: Long? = null
    var winner: RomMRom? = null
    var consolidated = false

    /**
     * Every rom carries the ids of its whole sibling set, so a group knows its final size
     * from its first member and can be consolidated and released as soon as the last one
     * arrives rather than waiting out the platform.
     */
    val isComplete: Boolean get() = expectedIds.isNotEmpty() &&
        members.mapTo(mutableSetOf()) { it.id }.containsAll(expectedIds)
}

/**
 * Running pick of the sibling a group consolidates under, so only one full [RomMRom] per
 * group stays resident. Mirrors the deferred rule it replaced: a declared main sibling
 * wins outright, otherwise the best region rank wins, ties going to the member seen first.
 */
internal fun chooseWinner(
    group: SiblingGroup,
    candidate: RomMRom,
    filters: SyncFilterPreferences
): RomMRom {
    val current = group.winner ?: return candidate
    if (candidate.id == group.mainSiblingId) return candidate
    if (current.id == group.mainSiblingId) return current
    return if (filters.regionRank(candidate.regions) < filters.regionRank(current.regions)) {
        candidate
    } else {
        current
    }
}
