package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
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
    private val saveCacheDao: SaveCacheDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val switchSaveHandler: SwitchSaveHandler,
    private val fal: com.nendo.argosy.data.storage.FileAccessLayer,
    private val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
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

                    val targetFile = File(resolution.targetPath)
                    if (resolution.isFolderBased) {
                        val unzipSuccess = saveArchiver.unzipSingleFolder(tempFile, targetFile)
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

                    saveCacheManager.get().cacheCurrentSave(
                        gameId = resolution.gameId,
                        emulatorId = resolution.emulatorId,
                        savePath = resolution.targetPath,
                        channelName = resolution.channelName,
                        isHardcore = false
                    )

                    val syncEntity = saveSyncDao.getByGameAndEmulator(resolution.gameId, resolution.emulatorId)
                    if (syncEntity != null) {
                        saveSyncDao.upsert(
                            syncEntity.copy(
                                localSavePath = resolution.targetPath,
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

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, resolvedEmulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, resolvedEmulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        }

        val cachedPath = syncEntity?.localSavePath?.takeIf { path ->
            val switchOk = if (game.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
            switchOk && fal.exists(path)
        }
        if (cachedPath != null) return cachedPath
        if (syncEntity?.localSavePath != null) {
            Logger.debug(TAG, "[SaveSync] findLocalSavePath gameId=$gameId | Cached path stale on disk, re-discovering")
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val coreName = apiClient.get().resolveCoreForGame(game)

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

    private fun selectServerSaveForChannel(
        matchingSaves: List<com.nendo.argosy.data.remote.romm.RomMSave>,
        channelName: String?,
        romBaseName: String?,
        preferGciZipBundle: Boolean = false,
        gameIdForLogging: Long? = null
    ): com.nendo.argosy.data.remote.romm.RomMSave? {
        if (channelName != null) {
            return matchingSaves
                .filter { it.slot != null && SaveSyncApiClient.equalsNormalized(it.slot, channelName) }
                .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                ?: matchingSaves.find {
                    it.fileNameNoExt != null && SaveSyncApiClient.equalsNormalized(it.fileNameNoExt, channelName)
                }
        }

        val candidates = matchingSaves.filter {
            SaveSyncApiClient.isLatestSaveFileName(it.fileName, romBaseName) ||
                (it.slot != null && romBaseName != null && SaveSyncApiClient.equalsNormalized(it.slot, romBaseName))
        }
        val picked = if (preferGciZipBundle && candidates.size > 1) {
            candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                ?: candidates.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
        } else {
            candidates.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
        }
        if (picked != null) return picked

        val lone = matchingSaves.singleOrNull()
        if (lone != null) {
            Logger.warn(
                TAG,
                "[SaveSync] selectServerSave gameId=${gameIdForLogging ?: -1} | " +
                    "No filename match for romBaseName='$romBaseName'; accepting lone server save fileName='${lone.fileName}'"
            )
        }
        return lone
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
        val serverSave = selectServerSaveForChannel(
            matchingSaves = matchingSaves,
            channelName = channelName,
            romBaseName = romBaseName,
            gameIdForLogging = gameId
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

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        }

        val localHash = saveCacheManager.get().calculateLocalSaveHash(localPath)
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

    /** Copies the latest local cache row's bytes onto the active emulator's save path when the user has switched emulators since that cache was written. Restores cross-emulator portability (PPSSPP <-> built-in PSP) without round-tripping through the server. */
    suspend fun crossEmulatorMigrateIfNeeded(gameId: Long, currentEmulatorId: String) = withContext(Dispatchers.IO) {
        val game = gameDao.getById(gameId) ?: return@withContext
        val latestCache = saveCacheDao.getByGame(gameId)
            .filter { !it.contentHash.isNullOrBlank() }
            .maxByOrNull { it.cachedAt }
            ?: return@withContext
        if (latestCache.emulatorId == currentEmulatorId) return@withContext

        val targetPath = resolveTargetPathForEmulator(gameId, game, currentEmulatorId) ?: run {
            Logger.warn(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Cross-emulator sync skipped | could not resolve save path for emulator=$currentEmulatorId")
            return@withContext
        }

        val existingHash = saveCacheManager.get().calculateLocalSaveHash(targetPath)
        if (existingHash != null && existingHash == latestCache.contentHash) return@withContext
        if (existingHash != null) {
            val existingMtime = savePathResolver.findNewestFileTime(targetPath).takeIf { it > 0 }
                ?: File(targetPath).lastModified()
            if (latestCache.cachedAt.toEpochMilli() <= existingMtime) return@withContext
        }

        Logger.info(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Cross-emulator sync | from=${latestCache.emulatorId} to=$currentEmulatorId cacheId=${latestCache.id} target=$targetPath diskExists=${existingHash != null}")
        com.nendo.argosy.util.SaveDebugLogger.logCustom(
            event = "CROSS_EMULATOR_SYNC",
            gameId = gameId,
            gameName = game.title,
            channel = latestCache.channelName,
            details = "from=${latestCache.emulatorId} to=$currentEmulatorId cacheId=${latestCache.id} target=${File(targetPath).name} diskExists=${existingHash != null}"
        )
        val restored = saveCacheManager.get().restoreSave(latestCache.id, targetPath)
        if (!restored) {
            Logger.warn(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Cross-emulator restore FAILED | cacheId=${latestCache.id} target=$targetPath")
        }
    }

    private suspend fun resolveTargetPathForEmulator(
        gameId: Long,
        game: com.nendo.argosy.data.local.entity.GameEntity,
        currentEmulatorId: String
    ): String? {
        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val coreName = apiClient.get().resolveCoreForGame(game)

        val discovered = savePathResolver.discoverSavePath(
            emulatorId = currentEmulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        )
        if (discovered != null) return discovered

        val userOverride = emulatorSaveConfigDao.getByEmulator(currentEmulatorId)
            ?.takeIf { it.isUserOverride }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }
        if (userOverride != null) return userOverride

        val config = com.nendo.argosy.data.emulator.SavePathRegistry.getConfigForPlatform(currentEmulatorId, game.platformSlug)
            ?: com.nendo.argosy.data.emulator.SavePathRegistry.getConfig(currentEmulatorId)
            ?: return null
        val candidates = com.nendo.argosy.data.emulator.SavePathRegistry.resolvePathWithPackage(
            config, emulatorPackage, appContext.filesDir.absolutePath, fal.externalStorageRoots()
        )
        return candidates.firstOrNull { fal.exists(it) && fal.isDirectory(it) }
            ?: candidates.firstOrNull()
    }

    companion object {
        private const val TAG = "SaveSyncConflictResolver"
    }
}
