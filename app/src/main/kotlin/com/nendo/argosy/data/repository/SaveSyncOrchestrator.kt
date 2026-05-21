package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SaveFilePayload
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.data.sync.SyncOperation
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncStatus
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncOrchestrator @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val gameDao: GameDao,
    private val emulatorResolver: EmulatorResolver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncQueueManager: SyncQueueManager,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val payloadCodec: SyncPayloadCodec
) {
    suspend fun queueUpload(gameId: Long, emulatorId: String, localPath: String) {
        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return

        val payload = SaveFilePayload(emulatorId)
        pendingSyncQueueDao.deleteByGameAndType(gameId, SyncType.SAVE_FILE)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_FILE,
                priority = SyncPriority.SAVE_FILE,
                payloadJson = payloadCodec.encode(payload)
            )
        )
    }

    suspend fun scanAndQueueLocalChanges(): Int = withContext(Dispatchers.IO) {
        val downloadedGames = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())
        var queued = 0
        val client = apiClient.get()

        for (game in downloadedGames) {
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
            val coreName = client.resolveCoreForGame(game)

            val savePath = savePathResolver.discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                coreName = coreName,
                emulatorPackage = emulatorPackage,
                gameId = game.id
            ) ?: continue

            val localFile = File(savePath)
            if (!localFile.exists()) continue

            val localModified = if (localFile.isDirectory) {
                Instant.ofEpochMilli(savePathResolver.findNewestFileTime(savePath))
            } else {
                Instant.ofEpochMilli(localFile.lastModified())
            }

            val syncEntity = saveSyncDao.getByGameAndEmulator(game.id, emulatorId)
            val lastSynced = syncEntity?.lastSyncedAt

            if (lastSynced == null || localModified.isAfter(lastSynced)) {
                Logger.debug(TAG, "[SaveSync] SCAN gameId=${game.id} | Local newer than sync | local=$localModified, lastSync=$lastSynced")
                queueUpload(game.id, emulatorId, savePath)
                queued++
            }
        }

        Logger.info(TAG, "[SaveSync] SCAN | Queued $queued local saves for upload")
        queued
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        val pendingDownloads = saveSyncDao.getPendingDownloads()
        if (pendingDownloads.isEmpty()) {
            return@withContext 0
        }

        val actionable = pendingDownloads.mapNotNull { entity ->
            val game = gameDao.getById(entity.gameId) ?: return@mapNotNull null
            if (game.localPath == null) {
                Logger.debug(TAG, "downloadPendingServerSaves: skipping non-installed gameId=${entity.gameId} silently")
                return@mapNotNull null
            }
            entity to game
        }
        if (actionable.isEmpty()) {
            return@withContext 0
        }

        for ((entity, game) in actionable) {
            syncQueueManager.addOperation(
                SyncOperation(
                    gameId = entity.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.DOWNLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var downloaded = 0
        val client = apiClient.get()

        for ((syncEntity, _) in actionable) {
            syncQueueManager.updateOperation(syncEntity.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = client.downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName, knownServerSaveId = syncEntity.rommSaveId)) {
                is SaveSyncResult.Success -> {
                    syncQueueManager.completeOperation(syncEntity.gameId)
                    downloaded++
                }
                is SaveSyncResult.NoSaveFound,
                is SaveSyncResult.NotConfigured -> {
                    syncQueueManager.removeOperation(syncEntity.gameId)
                    Logger.debug(TAG, "downloadPendingServerSaves: skipping gameId=${syncEntity.gameId} | result=$result")
                }
                is SaveSyncResult.NeedsHardcoreResolution -> {
                    saveSyncDao.upsert(syncEntity.copy(syncStatus = SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION))
                    syncQueueManager.removeOperation(syncEntity.gameId)
                    Logger.info(TAG, "downloadPendingServerSaves: gameId=${syncEntity.gameId} needs hardcore resolution; parked row as ${SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION} and dropped from bulk queue")
                }
                is SaveSyncResult.Error -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, result.message)
                }
                else -> {
                    syncQueueManager.completeOperation(syncEntity.gameId, "Download failed")
                }
            }
        }

        downloaded
    }

    suspend fun syncSavesForNewDownload(gameId: Long, rommId: Long, emulatorId: String) = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext

        val client = apiClient.get()
        val serverSaves = client.checkSavesForGame(gameId, rommId)
        if (serverSaves.isEmpty()) return@withContext

        val game = gameDao.getById(gameId) ?: return@withContext
        val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }

        val canonicalEmulatorId = if (emulatorId.isBlank() || emulatorId == "default") {
            client.resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "syncSavesForNewDownload: cannot resolve canonical emulator for gameId=$gameId; skipping")
                return@withContext
            }
        } else emulatorId

        for (serverSave in serverSaves) {
            val channelName = SaveSyncApiClient.parseServerChannelNameForSync(serverSave.fileName, romBaseName)
            val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)

            val existing = if (channelName != null) {
                saveSyncDao.getByGameEmulatorAndChannel(gameId, canonicalEmulatorId, channelName)
            } else {
                saveSyncDao.getByGameEmulatorAndNullChannel(gameId, canonicalEmulatorId)
            }

            saveSyncDao.upsert(
                SaveSyncEntity(
                    id = existing?.id ?: 0,
                    gameId = gameId,
                    rommId = rommId,
                    emulatorId = canonicalEmulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = existing?.localSavePath,
                    localUpdatedAt = existing?.localUpdatedAt,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = existing?.lastSyncedAt,
                    syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
                    lastUploadedHash = existing?.lastUploadedHash,
                    lastSyncDeviceId = existing?.lastSyncDeviceId,
                    lastSyncDeviceName = existing?.lastSyncDeviceName
                )
            )

            val result = client.downloadSave(gameId, canonicalEmulatorId, channelName, skipBackup = true, knownServerSaveId = serverSave.id)
            if (result is SaveSyncResult.Error) {
                Logger.error(TAG, "syncSavesForNewDownload: failed '${serverSave.fileName}': ${result.message}")
            }
        }
    }

    suspend fun forceSaveCheck(): ForceSaveCheckResult = withContext(Dispatchers.IO) {
        val prefs = userPreferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return@withContext ForceSaveCheckResult(0, 0, "Save sync disabled")

        val client = apiClient.get()
        val downloadedIds = gameDao.getDownloadedRommGameIds()
        val downloadedGames = gameDao.getByIdsChunked(downloadedIds)
        var inspected = 0
        var queued = 0

        for (game in downloadedGames) {
            val rommId = game.rommId ?: continue
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val serverSaves = client.checkSavesForGame(game.id, rommId)
            if (serverSaves.isEmpty()) continue
            inspected++

            val firstTimeForGame = saveSyncDao.getByGame(game.id).isEmpty()

            val latestPerChannel = serverSaves
                .filter { !SaveSyncApiClient.isStateShapedSave(it) }
                .groupBy { save ->
                    save.slot ?: SaveSyncApiClient.parseServerChannelNameForSync(save.fileName, romBaseName)
                }
                .mapValues { (_, saves) ->
                    saves.maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                }
                .values
                .filterNotNull()

            for (latest in latestPerChannel) {
                val channelName = latest.slot ?: SaveSyncApiClient.parseServerChannelNameForSync(latest.fileName, romBaseName)
                val existing = if (channelName != null) {
                    saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channelName)
                } else {
                    saveSyncDao.getByGameEmulatorAndNullChannel(game.id, emulatorId)
                }
                val serverTime = SaveSyncApiClient.parseTimestamp(latest.updatedAt)
                if (existing != null && existing.rommSaveId == latest.id && existing.serverUpdatedAt == serverTime) continue

                val isActiveChannel = channelName == null ||
                    channelName.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true) ||
                    channelName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true)
                val shouldDownload = firstTimeForGame || isActiveChannel
                val status = if (shouldDownload) SaveSyncEntity.STATUS_SERVER_NEWER else SaveSyncEntity.STATUS_SYNCED

                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = existing?.id ?: 0,
                        gameId = game.id,
                        rommId = rommId,
                        emulatorId = emulatorId,
                        channelName = channelName,
                        rommSaveId = latest.id,
                        localSavePath = existing?.localSavePath,
                        localUpdatedAt = existing?.localUpdatedAt,
                        serverUpdatedAt = serverTime,
                        lastSyncedAt = existing?.lastSyncedAt,
                        syncStatus = status,
                        lastUploadedHash = existing?.lastUploadedHash,
                        lastSyncDeviceId = existing?.lastSyncDeviceId,
                        lastSyncDeviceName = existing?.lastSyncDeviceName
                    )
                )
                if (shouldDownload) queued++
            }
        }
        val downloaded = downloadPendingServerSaves()
        Logger.info(TAG, "forceSaveCheck: inspected=$inspected queued=$queued downloaded=$downloaded")
        ForceSaveCheckResult(inspected = inspected, queued = queued, message = null, downloaded = downloaded)
    }

    data class ForceSaveCheckResult(
        val inspected: Int,
        val queued: Int,
        val message: String?,
        val downloaded: Int = 0
    )

    companion object {
        private const val TAG = "SaveSyncOrchestrator"
    }
}
