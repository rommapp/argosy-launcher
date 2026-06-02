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
import com.nendo.argosy.data.local.dao.OrphanedFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.OrphanedFileEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.InstalledAppResolver
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_PAGE_SIZE = 100
private const val TAG = "RomMLibrarySyncService"

@Singleton
class RomMLibrarySyncService @Inject constructor(
    private val apiClient: RomMApiClient,
    private val connectionManager: RomMConnectionManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: ALauncherDatabase,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val firmwareDao: FirmwareDao,
    private val controllerMappingDao: ControllerMappingDao,
    private val orphanedFileDao: OrphanedFileDao,
    private val collectionDao: CollectionDao,
    private val imageCacheManager: ImageCacheManager,
    private val biosRepository: BiosRepository,
    private val installedAppResolver: InstalledAppResolver,
    private val gameRepository: dagger.Lazy<com.nendo.argosy.data.repository.GameRepository>,
    private val syncVirtualCollectionsUseCase: dagger.Lazy<com.nendo.argosy.domain.usecase.collection.SyncVirtualCollectionsUseCase>,
    private val fileAccessLayer: com.nendo.argosy.data.storage.FileAccessLayer
) {
    private val api: RomMApi? get() = connectionManager.getApi()
    private val syncMutex = Mutex()

    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()

    companion object {
        private val ROMM_SOURCES = listOf(GameSource.ROMM_REMOTE, GameSource.ROMM_SYNCED)
    }

    suspend fun populateVirtualCollectionsIfNeeded() {
        val genreCount = collectionDao.countByType(CollectionType.GENRE)
        val gameModeCount = collectionDao.countByType(CollectionType.GAME_MODE)

        if (genreCount == 0 && gameModeCount == 0) {
            val hasGenres = gameDao.getDistinctGenres().isNotEmpty()
            val hasGameModes = gameDao.getDistinctGameModes().isNotEmpty()

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
            syncMutex.unlock()
        }
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

        _syncProgress.value = SyncProgress(isSyncing = true, platformsTotal = 1)

        try {
            val platformResponse = currentApi.getPlatform(platformId)
            if (!platformResponse.isSuccessful) {
                return SyncResult(0, 0, 0, 0, listOf("Failed to fetch platform: ${platformResponse.code()}"))
            }

            val platform = platformResponse.body()
                ?: return SyncResult(0, 0, 0, 0, listOf("Platform not found"))

            syncPlatformMetadata(platform)

            _syncProgress.value = _syncProgress.value.copy(currentPlatform = platform.name)

            gameDao.markSyncDirty(platform.id, ROMM_SOURCES)

            val result = syncPlatformRoms(currentApi, platform, filters)

            val gamesDeleted = processPostPlatformSync(currentApi, platform.id, result, filters)

            gameDao.clearAllSyncDirty()

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
        filters: SyncFilterPreferences
    ): Int {
        var gamesDeleted = 0

        consolidateMultiDiscGames(api, result.multiDiscGroups)

        gamesDeleted += cleanupInvalidExtensionGames(platformId)
        gamesDeleted += cleanupDuplicateGames(platformId)

        if (filters.deleteOrphans && result.error == null) {
            val dirtyGames = gameDao.getSyncDirtyGames(platformId, ROMM_SOURCES)
            for (game in dirtyGames) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                gamesDeleted++
            }
        }

        gameRepository.get().validateLocalFilesForPlatform(platformId)
        gameRepository.get().discoverLocalFilesForPlatform(platformId)
        gameRepository.get().validateDiscLocalFiles(platformId)
        gameRepository.get().validateFileLocalFiles(platformId)

        val count = gameDao.countByPlatform(platformId)
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

        _syncProgress.value = SyncProgress(isSyncing = true)

        try {
            val platformsResponse = currentApi.getPlatforms()

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
                val local = platformDao.getById(platform.id)
                local?.syncEnabled != false
            }

            _syncProgress.value = _syncProgress.value.copy(platformsTotal = enabledPlatforms.size)

            for ((index, platform) in enabledPlatforms.withIndex()) {
                onProgress?.invoke(index + 1, enabledPlatforms.size, platform.name)

                _syncProgress.value = _syncProgress.value.copy(
                    currentPlatform = platform.name,
                    platformsDone = index
                )

                gameDao.markSyncDirty(platform.id, ROMM_SOURCES)

                val result = syncPlatformRoms(currentApi, platform, filters)
                gamesAdded += result.added
                gamesUpdated += result.updated
                result.error?.let { errors.add(it) }

                gamesDeleted += processPostPlatformSync(currentApi, platform.id, result, filters)

                platformsSynced++
            }

            gameDao.clearAllSyncDirty()

            cleanupLegacyPlatforms(platforms)

            userPreferencesRepository.setLastRommSyncTime(Instant.now())

            syncVirtualCollectionsUseCase.get()()

        } catch (e: Exception) {
            errors.add(e.message ?: "Sync failed")
        } finally {
            _syncProgress.value = SyncProgress(isSyncing = false)
        }

        gameRepository.get().cleanupEmptyNumericFolders()

        return SyncResult(platformsSynced, gamesAdded, gamesUpdated, gamesDeleted, errors)
    }

    private suspend fun syncPlatformMetadata(remote: RomMPlatform) {
        val platformId = remote.id
        val existing = platformDao.getById(platformId)
        val platformDef = PlatformDefinitions.getBySlug(remote.slug)

        val logoUrl = remote.logoUrl?.let { apiClient.buildMediaUrl(it) }
        val derivedNames = PlatformDefinitions.getAliasDisplayName(remote.slug)
            ?: PlatformDefinitions.deriveDisplayName(remote.slug)
            ?: PlatformDefinitions.deriveDisplayName(remote.fsSlug)
        val normalizedName = remote.displayName ?: derivedNames?.first ?: remote.name
        val resolvedShortName = derivedNames?.second ?: platformDef?.shortName ?: normalizedName
        val entity = PlatformEntity(
            id = platformId,
            slug = remote.slug,
            fsSlug = remote.fsSlug,
            name = normalizedName,
            shortName = resolvedShortName,
            romExtensions = platformDef?.extensions?.joinToString(",") ?: "",
            gameCount = remote.romCount,
            isVisible = existing?.isVisible ?: true,
            logoPath = logoUrl ?: existing?.logoPath,
            sortOrder = platformDef?.sortOrder ?: existing?.sortOrder ?: 999,
            lastScanned = existing?.lastScanned,
            syncEnabled = existing?.syncEnabled ?: true,
            customRomPath = existing?.customRomPath
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
                biosRepository.syncPlatformFirmware(platformId, remote.slug, firmware)
            }
        }
    }

    private suspend fun syncRom(rom: RomMRom): Pair<Boolean, GameEntity> {
        val platformId = rom.platformId
        val platformSlug = rom.platformSlug
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
            imageCacheManager.deleteGameImages(rom.id)
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
            !contentChanged && existing?.coverPath?.startsWith("/") == true -> existing.coverPath
            coverUrl != null -> {
                imageCacheManager.queueCoverCache(coverUrl, rom.id, rom.name)
                coverUrl
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
            source = when {
                installedPackageName != null -> GameSource.ANDROID_APP
                localDataSource?.localPath != null -> GameSource.ROMM_SYNCED
                else -> GameSource.ROMM_REMOTE
            },
            coverPath = cachedCover,
            backgroundPath = cachedBackground,
            screenshotPaths = screenshotUrls.joinToString(","),
            description = rom.summary,
            releaseYear = rom.firstReleaseDateMillis?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).year
            },
            genre = rom.genres?.firstOrNull(),
            developer = rom.companies?.firstOrNull(),
            rating = rom.metadatum?.averageRating?.takeIf { rom.igdbId != null && it < 98f },
            regions = rom.regions?.joinToString(","),
            languages = rom.languages?.joinToString(","),
            gameModes = rom.metadatum?.gameModes?.joinToString(","),
            franchises = rom.metadatum?.franchises?.joinToString(","),
            userRating = rom.romUser?.rating ?: localDataSource?.userRating ?: 0,
            userDifficulty = rom.romUser?.difficulty ?: localDataSource?.userDifficulty ?: 0,
            completion = rom.romUser?.completion ?: localDataSource?.completion ?: 0,
            status = rom.romUser?.status ?: localDataSource?.status,
            backlogged = rom.romUser?.backlogged ?: localDataSource?.backlogged ?: false,
            nowPlaying = rom.romUser?.nowPlaying ?: localDataSource?.nowPlaying ?: false,
            isFavorite = localDataSource?.isFavorite ?: false,
            isHidden = localDataSource?.isHidden ?: false,
            isMultiDisc = when {
                rom.isFolderMultiDisc -> localDataSource?.isMultiDisc == true && localDataSource.localPath != null
                shouldBeMultiDisc -> localDataSource?.isMultiDisc ?: false
                else -> false
            },
            playCount = localDataSource?.playCount ?: 0,
            playTimeMinutes = localDataSource?.playTimeMinutes ?: 0,
            lastPlayed = localDataSource?.lastPlayed,
            addedAt = localDataSource?.addedAt ?: java.time.Instant.now(),
            achievementCount = rom.raMetadata?.achievements?.size ?: localDataSource?.achievementCount ?: 0,
            youtubeVideoId = rom.youtubeVideoId,
            fileSizeBytes = rom.files
                ?.filter { it.category == null && !it.fileName.startsWith(".") }
                ?.maxByOrNull { it.fileSizeBytes }
                ?.fileSizeBytes
                ?: rom.fileSize.takeIf { it > 0 }
        )

        val isNew = existing == null
        gameDao.insert(game)

        if (migrationSources.isNotEmpty()) {
            migrationSources.forEach { source ->
                gameDao.delete(source.id)
            }
            Logger.info(TAG, "syncRom: deleted ${migrationSources.size} old game entries after migration")
        }

        val savedGame = gameDao.getByRommId(rom.id)
        if (savedGame != null) {
            syncGameFiles(savedGame.id, rom, platformSlug)
        }

        return isNew to game
    }

    private suspend fun syncGameFiles(gameId: Long, rom: RomMRom, platformSlug: String) {
        val isVariantExcluded = platformSlug in com.nendo.argosy.data.model.VariantCategory.VARIANT_EXCLUDED_PLATFORMS
        val files = rom.files?.filter { file ->
            val cat = file.category
            if (cat == null || file.fileName.startsWith(".")) return@filter false
            if (isVariantExcluded) cat in setOf("update", "dlc") else true
        } ?: return

        if (files.isEmpty()) {
            gameFileDao.deleteByGameId(gameId)
            return
        }

        val validIds = files.mapNotNull { if (it.id > 0) it.id else null }
        if (validIds.isNotEmpty()) {
            gameFileDao.deleteInvalidFiles(gameId, validIds)
        }

        val entities = files.map { file ->
            val existing = gameFileDao.getByRommFileId(file.id)
            val category = com.nendo.argosy.data.model.VariantCategory.fromKey(file.category)
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
                isLaunchTarget = category.isLaunchTarget,
                isMultiDisc = existing?.isMultiDisc ?: false,
                m3uPath = existing?.m3uPath
            )
        }
        gameFileDao.insertAll(entities)
    }

    private data class PlatformSyncResult(
        val added: Int,
        val updated: Int,
        val multiDiscGroups: List<MultiDiscGroup>,
        val error: String? = null
    )

    private suspend fun syncPlatformRoms(
        api: RomMApi,
        platform: RomMPlatform,
        filters: SyncFilterPreferences
    ): PlatformSyncResult {
        var added = 0
        var updated = 0
        val seenDedupKeys = mutableSetOf<String>()
        val multiDiscGroups = mutableListOf<MultiDiscGroup>()
        val processedDiscIds = mutableSetOf<Long>()
        val skipIndividualDiscIds = mutableSetOf<Long>()
        var offset = 0
        var totalFetched = 0

        while (true) {
            val romsResponse = api.getRoms(
                apiClient.buildRomsQueryParams(
                    platformId = platform.id,
                    limit = SYNC_PAGE_SIZE,
                    offset = offset
                )
            )

            if (!romsResponse.isSuccessful) {
                return PlatformSyncResult(added, updated, multiDiscGroups,
                    "Failed to fetch ROMs for ${platform.name}: ${romsResponse.code()}")
            }

            val romsPage = romsResponse.body()
            if (romsPage == null || romsPage.items.isEmpty()) break

            totalFetched += romsPage.items.size
            _syncProgress.value = _syncProgress.value.copy(
                gamesTotal = romsPage.total,
                gamesDone = totalFetched
            )

            for (rom in romsPage.items) {
                if (!RomMSyncFilter.shouldSyncRom(rom, filters)) continue

                if (rom.id in skipIndividualDiscIds) {
                    Logger.debug(TAG, "syncPlatformRoms: skipping individual disc ${rom.name} - folder-based version preferred")
                    continue
                }

                if (rom.isFolderMultiDisc) {
                    val discSiblings = rom.siblings?.filter { it.isDiscVariant } ?: emptyList()
                    if (discSiblings.isNotEmpty()) {
                        val siblingIds = discSiblings.map { it.id }
                        skipIndividualDiscIds.addAll(siblingIds)
                        Logger.info(TAG, "syncPlatformRoms: ${rom.name} is folder-based multi-disc, marking ${siblingIds.size} individual disc siblings to skip")
                        for (siblingId in siblingIds) {
                            val existingGame = gameDao.getByRommId(siblingId)
                            if (existingGame != null) {
                                Logger.info(TAG, "syncPlatformRoms: deleting redundant individual disc game: ${existingGame.title}")
                                gameDao.delete(existingGame.id)
                            }
                        }
                    }
                }

                val dedupKey = RomMUtils.getDedupKey(rom)
                if (dedupKey != null) {
                    if (!seenDedupKeys.add(dedupKey)) continue
                }

                try {
                    val (isNew, _) = syncRom(rom)
                    if (isNew) added++ else updated++

                    val isSiblingBasedMultiDisc = rom.hasDiscSiblings && !rom.isFolderMultiDisc
                    if (isSiblingBasedMultiDisc && rom.id !in processedDiscIds) {
                        val discSiblings = rom.siblings?.filter { it.isDiscVariant } ?: emptyList()
                        val siblingIds = discSiblings.map { it.id }

                        processedDiscIds.add(rom.id)
                        processedDiscIds.addAll(siblingIds)

                        multiDiscGroups.add(MultiDiscGroup(
                            primaryRommId = rom.id,
                            siblingRommIds = siblingIds,
                            platformSlug = platform.slug
                        ))
                    }
                } catch (e: Exception) {
                    Logger.warn(TAG, "syncPlatformRoms: failed to sync ROM ${rom.id} (${rom.name}): ${e.message}")
                }
            }

            if (totalFetched >= romsPage.total) break
            offset += SYNC_PAGE_SIZE
        }

        return PlatformSyncResult(added, updated, multiDiscGroups)
    }

    private suspend fun consolidateMultiDiscGames(
        api: RomMApi,
        groups: List<MultiDiscGroup>
    ) {
        for (group in groups) {
            try {
                consolidateMultiDiscGroup(api, group)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun consolidateMultiDiscGroup(
        api: RomMApi,
        group: MultiDiscGroup
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

        val updatedGame = primaryGame.copy(
            isFavorite = mergedIsFavorite,
            playCount = mergedPlayCount,
            playTimeMinutes = mergedPlayTime,
            lastPlayed = mergedLastPlayed ?: primaryGame.lastPlayed,
            userRating = mergedUserRating,
            userDifficulty = mergedUserDifficulty,
            completion = mergedCompletion,
            backlogged = mergedBacklogged,
            nowPlaying = mergedNowPlaying,
            addedAt = earliestAddedAt,
            isMultiDisc = true
        )

        gameDao.update(updatedGame)

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

    private suspend fun cleanupInvalidExtensionGames(platformId: Long): Int {
        var deleted = 0
        val platformGames = gameDao.getBySources(ROMM_SOURCES, platformId)

        for (game in platformGames) {
            val localPath = game.localPath ?: continue
            val extension = localPath.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) continue

            val platformDef = PlatformDefinitions.getBySlug(game.platformSlug) ?: continue
            if (platformDef.extensions.isEmpty()) continue

            if (extension !in platformDef.extensions) {
                safeDeleteFile(localPath)
                gameDao.delete(game.id)
                deleted++
            }
        }
        return deleted
    }

    private suspend fun cleanupDuplicateGames(platformId: Long): Int {
        var deleted = 0
        val platformGames = gameDao.getBySources(ROMM_SOURCES, platformId)
        val deletedIds = mutableSetOf<Long>()

        val gamesByIgdb = platformGames
            .filter { it.igdbId != null }
            .groupBy { it.igdbId }

        for ((_, duplicates) in gamesByIgdb) {
            if (duplicates.size <= 1) continue

            val sorted = duplicates.sortedWith(
                compareByDescending<GameEntity> { it.achievementCount > 0 }
                    .thenByDescending { it.localPath != null }
                    .thenBy { it.id }
            )

            for (game in sorted.drop(1)) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deletedIds.add(game.id)
                deleted++
            }
        }

        val remainingGames = platformGames.filter { it.id !in deletedIds }
        val gamesByTitle = remainingGames
            .groupBy { it.title.lowercase() }

        for ((_, duplicates) in gamesByTitle) {
            if (duplicates.size <= 1) continue

            val sorted = duplicates.sortedWith(
                compareByDescending<GameEntity> { it.achievementCount > 0 }
                    .thenByDescending { it.localPath != null }
                    .thenBy { it.id }
            )

            for (game in sorted.drop(1)) {
                game.localPath?.let { safeDeleteFile(it) }
                gameDao.delete(game.id)
                deleted++
            }
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
        database.withTransaction {
            val gameCount = gameDao.countByPlatform(old.id)
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

    private suspend fun safeDeleteFile(path: String) {
        try {
            val file = java.io.File(path)
            if (file.exists() && !file.delete()) {
                Logger.warn(TAG, "safeDeleteFile: failed to delete $path, adding to orphan index")
                orphanedFileDao.insert(OrphanedFileEntity(path = path))
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "safeDeleteFile: error deleting $path: ${e.message}")
            orphanedFileDao.insert(OrphanedFileEntity(path = path))
        }
    }
}
