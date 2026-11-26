package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.LiveSavePath
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncConflictResolver @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val fal: com.nendo.argosy.data.storage.FileAccessLayer,
    private val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry,
    private val gciSaveHandler: com.nendo.argosy.data.sync.platform.GciSaveHandler
) {
    suspend fun checkForConflict(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ConflictInfo? = when (val analysis = analyzeChannel(gameId, emulatorId, channelName)) {
        is SyncAnalysis.Conflict -> analysis.info
        else -> null
    }

    suspend fun resolveHardcoreConflict(
        resolution: SaveSyncResult.NeedsHardcoreResolution,
        choice: HardcoreResolutionChoice
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        val tempFile = File(resolution.tempFilePath)
        val client = apiClient.get()

        try {
            when (choice) {
                HardcoreResolutionChoice.KEEP_HARDCORE -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | KEEP_HARDCORE | Uploading local save")
                    tempFile.delete()
                    client.uploadSave(
                        gameId = resolution.gameId,
                        emulatorId = resolution.emulatorId,
                        channelName = resolution.channelName,
                        forceOverwrite = true,
                        isHardcore = true
                    )
                }

                HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | DOWNGRADE_TO_CASUAL | Applying server save")

                    fal.prepareSaveAccess(resolution.targetPath)
                    if (!saveCacheManager.get().protectBeforeOverwrite(resolution.gameId, resolution.emulatorId, resolution.targetPath)) {
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                    }
                    val targetFile = fal.getTransformedFile(resolution.targetPath)
                    val game = gameDao.getById(resolution.gameId)
                    val gciConfig = game?.let { SavePathRegistry.getConfigForPlatform(resolution.emulatorId, it.platformSlug) }
                        ?.takeIf { it.usesGciFormat }
                    var placedPath = resolution.targetPath
                    val sigilHandler = saveHandlerRegistry.sigil.takeIf { sigil ->
                        game != null && sigil.route(game.id, resolution.emulatorId) != null
                    }
                    if (game != null && sigilHandler != null) {
                        val restored = sigilHandler.restore(game.id, tempFile, resolution.emulatorId)
                        if (restored !is SigilRestore.Restored) {
                            val reason = (restored as? SigilRestore.Refused)?.reason ?: "no Sigil layout"
                            return@withContext SaveSyncResult.Error("Failed to place save: $reason")
                        }
                    } else if (game != null && gciConfig != null) {
                        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
                        val placed = gciSaveHandler.extractDownload(
                            tempFile,
                            SaveContext(
                                config = gciConfig,
                                romPath = game.localPath,
                                saveId = game.saveId ?: game.titleId,
                                emulatorPackage = emulatorPackage,
                                gameId = game.id,
                                gameTitle = game.title,
                                platformSlug = game.platformSlug,
                                emulatorId = resolution.emulatorId,
                                basePathOverride = savePathResolver.gciBaseOverride(gciConfig, game.id, game.platformSlug, emulatorPackage)
                            )
                        )
                        placedPath = placed.targetPath?.takeIf { placed.success }
                            ?: return@withContext SaveSyncResult.Error(placed.error ?: "Failed to place GameCube save")
                    } else if (resolution.isFolderBased) {
                        val folderHandler = game?.platformSlug?.let { saveHandlerRegistry.getFolderHandler(it) }
                        val unzipSuccess = if (folderHandler != null && game != null) {
                            folderHandler.placeArchive(tempFile, targetFile, game.saveId ?: game.titleId)
                        } else {
                            saveArchiver.unzipSingleFolder(tempFile, targetFile)
                        }
                        if (!unzipSuccess) {
                            return@withContext SaveSyncResult.Error("Failed to unzip save")
                        }
                    } else {
                        val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(tempFile)
                        val written = if (bytesWithoutTrailer != null) {
                            saveArchiver.writeBytesToPath(resolution.targetPath, bytesWithoutTrailer)
                        } else {
                            saveArchiver.copyFileToPath(tempFile, resolution.targetPath)
                        }
                        if (!written) {
                            return@withContext SaveSyncResult.Error("Failed to write save file")
                        }
                    }

                    if (!fal.commitSaveAccess(resolution.targetPath, placedPath)) {
                        return@withContext SaveSyncResult.Error("Failed to write save")
                    }

                    saveCacheManager.get().cacheCurrentSave(
                        gameId = resolution.gameId,
                        emulatorId = resolution.emulatorId,
                        savePath = placedPath,
                        channelName = resolution.channelName,
                        isHardcore = false
                    )

                    val syncEntity = saveSyncDao.getByGameEmulatorAndChannel(
                        resolution.gameId,
                        resolution.emulatorId,
                        SaveSyncApiClient.syncKeyOf(resolution.channelName),
                        syncPreferencesRepository.getRommUserId()
                    )
                    if (syncEntity != null) {
                        saveSyncDao.upsert(
                            syncEntity.copy(
                                localSavePath = placedPath,
                                localUpdatedAt = Instant.now(),
                                lastSyncedAt = Instant.now(),
                                syncStatus = SaveSyncEntity.STATUS_SYNCED
                            )
                        )
                    }

                    SaveSyncResult.Success()
                }

                HardcoreResolutionChoice.KEEP_LOCAL -> {
                    Logger.info(TAG, "[SaveSync] RESOLVE gameId=${resolution.gameId} | KEEP_LOCAL | Skipping sync")
                    SaveSyncResult.Success()
                }
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun findLocalSavePath(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): String? {
        val game = gameDao.getById(gameId) ?: return null
        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            apiClient.get().resolveEmulatorForGame(game) ?: return null
        } else emulatorId

        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val syncEntity = saveSyncDao.getByGameEmulatorAndChannel(
            gameId, resolvedEmulatorId, SaveSyncApiClient.syncKeyOf(channelName), ownerUserId
        )

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val cachedPath = LiveSavePath.forPackage(syncEntity, emulatorPackage)
            ?.takeIf { path -> saveHandlerRegistry.isValidCachedSavePath(game.platformSlug, path) && fal.exists(path) }
        if (cachedPath != null) return cachedPath
        if (syncEntity?.localSavePath != null) {
            Logger.debug(TAG, "[SaveSync] findLocalSavePath gameId=$gameId | Cached path stale on disk, re-discovering")
        }

        val coreName = apiClient.get().resolveCoreForGame(game, resolvedEmulatorId)

        return savePathResolver.discoverSavePath(
            emulatorId = resolvedEmulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        )
    }


    suspend fun analyzeChannel(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): SyncAnalysis = withContext(Dispatchers.IO) {
        val client = apiClient.get()
        if (client.getApi() == null) {
            return@withContext SyncAnalysis.NoConnection
        }

        val game = gameDao.getById(gameId) ?: return@withContext SyncAnalysis.NoLocalSave
        val rommId = game.rommId ?: return@withContext SyncAnalysis.NoConnection

        val localPath = findLocalSavePath(gameId, emulatorId, channelName)
        val localFile = localPath?.let { File(it) }?.takeIf { it.exists() }

        val serverSaves = try {
            client.checkSavesForGame(gameId, rommId)
                .filterNot { SaveSyncApiClient.isStateShapedSave(it) }
        } catch (e: Exception) {
            Logger.debug(TAG, "[SaveSync] analyzeChannel gameId=$gameId | server check failed: ${e.message}")
            return@withContext SyncAnalysis.NoConnection
        }

        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
        val matchingSaves = serverSaves
        val serverSave = SaveSyncApiClient.selectLatestServerSave(
            serverSaves = matchingSaves,
            channelName = channelName,
            romBaseName = romBaseName,
            preferGciZipBundle = false
        )

        if (serverSave == null) {
            return@withContext if (localPath != null && localFile != null) {
                SyncAnalysis.LocalNewer(localPath, channelName)
            } else {
                SyncAnalysis.NoServerSave
            }
        }

        val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
        if (localFile == null) {
            return@withContext SyncAnalysis.ServerNewer(
                serverSaveId = serverSave.id,
                serverTimestamp = serverTime,
                channelName = channelName
            )
        }

        val localModified = Instant.ofEpochMilli(localFile.lastModified())

        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val syncEntity = saveSyncDao.getByGameEmulatorAndChannel(
            gameId, emulatorId, SaveSyncApiClient.syncKeyOf(channelName), ownerUserId
        )

        val localHash = saveCacheManager.get().calculateLocalSaveHash(localPath, gameId, emulatorId)
        val localMatchesAnchor = syncEntity?.localContentHash != null
            && localHash != null
            && localHash == syncEntity.localContentHash

        if (localMatchesAnchor) {
            return@withContext SyncAnalysis.InSync
        }

        val serverHash = serverSave.contentHash?.takeIf { client.getCapabilities().trustsServerHash }
        if (serverHash != null && localHash != null && localHash == serverHash) {
            Logger.debug(TAG, "[SaveSync] analyzeChannel gameId=$gameId | Local content matches server hash, in sync despite stale sync row")
            return@withContext SyncAnalysis.InSync
        }

        val localChangedSinceUpload = syncEntity?.localContentHash != null
            && localHash != null
            && localHash != syncEntity.localContentHash
        val serverChangedSinceUpload = serverHash != null
            && syncEntity?.lastUploadedHash != null
            && serverHash != syncEntity.lastUploadedHash
        val isHashConflict = localChangedSinceUpload && serverChangedSinceUpload

        val deviceId = client.getDeviceId()
        val deviceSyncEntry = deviceId?.let { devId ->
            serverSave.deviceSyncs?.find { it.deviceId == devId }
        }
        val isServerNewer = if (deviceSyncEntry != null) {
            !deviceSyncEntry.isCurrent
        } else {
            serverTime.isAfter(localModified)
        }

        val uploaderDeviceName = serverSave.deviceSyncs
            ?.filter { it.deviceId != deviceId }
            ?.maxByOrNull { it.lastSyncedAt ?: "" }
            ?.deviceName

        val haveTrustedHashes = serverHash != null &&
            syncEntity?.lastUploadedHash != null &&
            syncEntity.localContentHash != null
        val isConflict = if (haveTrustedHashes) isHashConflict else isServerNewer

        when {
            isConflict -> SyncAnalysis.Conflict(
                ConflictInfo(
                    gameId = gameId,
                    gameName = game.title,
                    channelName = channelName,
                    localTimestamp = localModified,
                    serverTimestamp = serverTime,
                    isHashConflict = isHashConflict,
                    serverDeviceName = uploaderDeviceName,
                    serverSaveId = serverSave.id
                )
            )
            else -> SyncAnalysis.LocalNewer(localPath, channelName)
        }
    }

    companion object {
        private const val TAG = "SaveSyncConflictResolver"
    }
}
