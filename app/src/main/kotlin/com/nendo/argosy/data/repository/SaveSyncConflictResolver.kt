package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
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
import kotlinx.coroutines.flow.first
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
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val switchSaveHandler: SwitchSaveHandler
) {
    enum class HardcoreResolutionChoice {
        KEEP_HARDCORE,
        DOWNGRADE_TO_CASUAL,
        KEEP_LOCAL
    }

    sealed class PreLaunchSyncResult {
        data object NoConnection : PreLaunchSyncResult()
        data object NoServerSave : PreLaunchSyncResult()
        data object LocalIsNewer : PreLaunchSyncResult()
        data class ServerIsNewer(val serverTimestamp: Instant, val channelName: String?) : PreLaunchSyncResult()
        data class LocalModified(
            val localSavePath: String,
            val serverTimestamp: Instant,
            val channelName: String?
        ) : PreLaunchSyncResult()
    }

    suspend fun preLaunchSync(gameId: Long, rommId: Long, emulatorId: String): PreLaunchSyncResult =
        withContext(Dispatchers.IO) {
            val client = apiClient.get()
            val activeChannel = gameDao.getActiveSaveChannel(gameId)
            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId rommId=$rommId emulator=$emulatorId channel=$activeChannel | Checking server saves")
            val api = client.getApi()
            if (api == null) {
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No API connection")
                return@withContext PreLaunchSyncResult.NoConnection
            }

            client.flushPendingDeviceSync(gameId)

            val deviceId = client.getDeviceId()

            if (deviceId == null || activeChannel == null) {
                val activeSaveApplied = gameDao.getActiveSaveApplied(gameId)
                if (activeSaveApplied == true) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Skipping - user chose to keep local (activeSaveApplied=true)")
                    return@withContext PreLaunchSyncResult.LocalIsNewer
                }
            }

            try {
                val serverSaves = client.checkSavesForGame(gameId, rommId)
                val matchingSaves = serverSaves.filter { it.emulator == emulatorId || it.emulator == null }
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Found ${serverSaves.size} server saves | matching=$emulatorId: ${matchingSaves.size}, channels=${matchingSaves.map { it.fileNameNoExt }}")

                val game = gameDao.getById(gameId)
                val romBaseName = game?.localPath?.let { File(it).nameWithoutExtension }

                val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
                val serverSave = if (activeChannel != null) {
                    val channelSave = matchingSaves
                        .filter { it.slot == activeChannel }
                        .maxByOrNull { client.parseTimestamp(it.updatedAt) }
                        ?: matchingSaves.find { it.fileNameNoExt == activeChannel }
                    if (channelSave != null) {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Using active channel save | channel=$activeChannel, slot=${channelSave.slot}")
                        channelSave
                    } else {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Active channel '$activeChannel' not found on server, no fallback")
                        null
                    }
                } else {
                    val candidates = matchingSaves.filter { client.isLatestSaveFileName(it.fileName, romBaseName) }
                    if (config?.usesGciFormat == true && candidates.size > 1) {
                        val preferred = candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                            ?: candidates.firstOrNull()
                        if (preferred != null && preferred != candidates.firstOrNull()) {
                            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | GCI format: preferring ZIP bundle over single file")
                        }
                        preferred
                    } else {
                        candidates.firstOrNull()
                    }
                }
                if (serverSave == null) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No server save found for emulator=$emulatorId")
                    return@withContext PreLaunchSyncResult.NoServerSave
                }

                val serverTime = client.parseTimestamp(serverSave.updatedAt)
                val selectedChannel = serverSave.slot ?: serverSave.fileNameNoExt
                val existing = if (selectedChannel != null) {
                    saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, selectedChannel)
                        ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, selectedChannel)
                } else {
                    saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
                }

                val validatedPath = existing?.localSavePath?.takeIf { path ->
                    if (game?.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
                }
                if (existing?.localSavePath != null && validatedPath == null) {
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Rejecting invalid cached Switch path=${existing.localSavePath}")
                }
                val localFile = validatedPath?.let { File(it) }?.takeIf { it.exists() }
                val localFileTime = localFile?.let { Instant.ofEpochMilli(it.lastModified()) }
                val localDbTime = if (validatedPath != null) existing?.localUpdatedAt else null
                val localTime = when {
                    localDbTime == null -> localFileTime
                    localFileTime == null -> localDbTime
                    localFileTime.isAfter(localDbTime) -> localFileTime
                    else -> localDbTime
                }

                val deltaMs = if (localTime != null) serverTime.toEpochMilli() - localTime.toEpochMilli() else null
                val deltaStr = deltaMs?.let { if (it >= 0) "+${it/1000}s" else "${it/1000}s" } ?: "N/A"
                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Timestamp compare | server=$serverTime, local=$localTime (db=$localDbTime, file=$localFileTime), delta=$deltaStr")

                if (deviceId == null && localFile != null && validatedPath != null) {
                    val activeSaveTimestamp = gameDao.getActiveSaveTimestamp(gameId)
                    val cachedSave = if (activeSaveTimestamp != null) {
                        saveCacheManager.get().getByTimestamp(gameId, activeSaveTimestamp)
                    } else {
                        selectedChannel?.let { saveCacheManager.get().getMostRecentInChannel(gameId, it) }
                    }
                    val cachedHash = cachedSave?.contentHash
                    if (cachedHash != null) {
                        val localHash = saveCacheManager.get().calculateLocalSaveHash(validatedPath)
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Hash compare | local=$localHash, cached=$cachedHash")
                        if (localHash != null && localHash != cachedHash) {
                            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_MODIFIED | Local save differs from cached, prompting user")
                            return@withContext PreLaunchSyncResult.LocalModified(
                                localSavePath = validatedPath,
                                serverTimestamp = serverTime,
                                channelName = selectedChannel
                            )
                        }
                    } else {
                        val localHash = saveCacheManager.get().calculateLocalSaveHash(validatedPath)
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | No cached hash to compare, checking against all caches (localHash=$localHash)")
                        if (localHash != null) {
                            val matchingCache = saveCacheManager.get().getByGameAndHash(gameId, localHash)
                            if (matchingCache == null) {
                                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_MODIFIED | Local hash not found in any cache, prompting user")
                                return@withContext PreLaunchSyncResult.LocalModified(
                                    localSavePath = validatedPath,
                                    serverTimestamp = serverTime,
                                    channelName = selectedChannel
                                )
                            } else {
                                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Local hash matches cache id=${matchingCache.id}")
                            }
                        }
                    }
                }

                val deviceSyncEntry = deviceId?.let { devId ->
                    serverSave.deviceSyncs?.find { it.deviceId == devId }
                }
                val isDeviceCurrent = deviceSyncEntry?.isCurrent

                val isLocalDiverged = if (deviceId != null && validatedPath != null) {
                    val lastUploadedHash = existing?.lastUploadedHash
                    val localHash = saveCacheManager.get()
                        .calculateLocalSaveHash(validatedPath)
                    if (lastUploadedHash != null && localHash != null) {
                        (localHash != lastUploadedHash).also { diverged ->
                            Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Hash divergence check | local=$localHash, lastUploaded=$lastUploadedHash, diverged=$diverged")
                        }
                    } else if (localHash != null && activeChannel != null) {
                        val latestCache = saveCacheDao
                            .getLatestCasualSaveInChannel(gameId, activeChannel)
                        val diverged = latestCache?.contentHash != null &&
                            localHash != latestCache.contentHash
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Hash fallback check | local=$localHash, cacheHash=${latestCache?.contentHash}, diverged=$diverged")
                        diverged
                    } else false
                } else false

                if (isDeviceCurrent != null) {
                    if (isDeviceCurrent) {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_CURRENT | Device is_current=true, skipping download")
                        return@withContext PreLaunchSyncResult.LocalIsNewer
                    }
                    if (isLocalDiverged) {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_MODIFIED | Device is_current=false AND local save diverged from server timeline")
                        return@withContext PreLaunchSyncResult.LocalModified(
                            localSavePath = validatedPath ?: "",
                            serverTimestamp = serverTime,
                            channelName = selectedChannel
                        )
                    }
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=SERVER_NEWER | Device is_current=false")
                } else {
                    if (localFile != null && localTime != null && !serverTime.isAfter(localTime)) {
                        Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=LOCAL_NEWER | Timestamp fallback: local is newer or equal, skipping download")
                        return@withContext PreLaunchSyncResult.LocalIsNewer
                    }
                    Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Decision=SERVER_NEWER | Timestamp fallback: server is newer")
                }

                Logger.debug(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Will download channel=$selectedChannel")
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = selectedChannel,
                        rommSaveId = serverSave.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                    )
                )

                PreLaunchSyncResult.ServerIsNewer(serverTime, selectedChannel)
            } catch (e: Exception) {
                Logger.warn(TAG, "[SaveSync] PRE_LAUNCH gameId=$gameId | Error checking server saves", e)
                PreLaunchSyncResult.NoConnection
            }
        }

    suspend fun checkForConflict(
        gameId: Long,
        emulatorId: String,
        channelName: String?
    ): ConflictInfo? = withContext(Dispatchers.IO) {
        val client = apiClient.get()
        client.getApi() ?: return@withContext null

        val game = gameDao.getById(gameId) ?: return@withContext null
        val rommId = game.rommId ?: return@withContext null

        val localPath = findLocalSavePath(gameId, emulatorId, channelName)
        if (localPath == null) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No local save path found")
            return@withContext null
        }
        val localFile = File(localPath)
        if (!localFile.exists()) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Local file does not exist")
            return@withContext null
        }
        val localModified = Instant.ofEpochMilli(localFile.lastModified())

        val serverSaves = try {
            client.checkSavesForGame(gameId, rommId)
        } catch (e: Exception) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Failed to check server saves: ${e.message}")
            return@withContext null
        }

        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
        val matchingSaves = serverSaves.filter { it.emulator == emulatorId || it.emulator == null }
        val serverSave = if (channelName != null) {
            matchingSaves
                .filter { it.slot == channelName }
                .maxByOrNull { client.parseTimestamp(it.updatedAt) }
                ?: matchingSaves.find { it.fileNameNoExt == channelName }
        } else {
            matchingSaves.filter { client.isLatestSaveFileName(it.fileName, romBaseName) }.firstOrNull()
        }

        if (serverSave == null) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No matching server save")
            return@withContext null
        }

        val serverTime = client.parseTimestamp(serverSave.updatedAt)

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        }

        val localHash = saveCacheManager.get().calculateLocalSaveHash(localPath)
        val hashMatchesLastUpload = syncEntity?.lastUploadedHash != null
            && localHash != null
            && localHash == syncEntity.lastUploadedHash

        if (hashMatchesLastUpload) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Local hash matches last upload, no real conflict")
            return@withContext null
        }

        val isHashConflict = syncEntity?.lastUploadedHash != null
            && localHash != null
            && localHash != syncEntity.lastUploadedHash

        val deviceId = client.getDeviceId()
        val deviceSyncEntry = deviceId?.let { devId ->
            serverSave.deviceSyncs?.find { it.deviceId == devId }
        }
        val isServerNewer = if (deviceSyncEntry != null) {
            !deviceSyncEntry.isCurrent
        } else {
            serverTime.isAfter(localModified)
        }

        if (isServerNewer || isHashConflict) {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | Conflict detected: serverNewer=$isServerNewer (is_current=${deviceSyncEntry?.isCurrent}), hash=$isHashConflict")
            ConflictInfo(
                gameId = gameId,
                gameName = game.title,
                channelName = channelName,
                localTimestamp = localModified,
                serverTimestamp = serverTime,
                isHashConflict = isHashConflict
            )
        } else {
            Logger.debug(TAG, "[SaveSync] checkForConflict gameId=$gameId | No conflict")
            null
        }
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
            if (game.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
        }
        if (cachedPath != null) return cachedPath

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val folderSyncEnabled = isFolderSaveSyncEnabled()

        return savePathResolver.discoverSavePath(
            emulatorId = resolvedEmulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedTitleId = game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = gameId,
            isFolderSaveSyncEnabled = folderSyncEnabled
        )
    }

    private suspend fun isFolderSaveSyncEnabled(): Boolean {
        val prefs = userPreferencesRepository.preferences.first()
        return prefs.saveSyncEnabled && prefs.experimentalFolderSaveSync
    }

    companion object {
        private const val TAG = "SaveSyncConflictResolver"
    }
}
