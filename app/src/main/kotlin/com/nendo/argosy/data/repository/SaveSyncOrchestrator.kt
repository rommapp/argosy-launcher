package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.sync.SaveFilePayload
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
    private val apiClient: dagger.Lazy<SaveSyncApiClient>
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
                payloadJson = payload.toJson()
            )
        )
    }

    suspend fun scanAndQueueLocalChanges(): Int = withContext(Dispatchers.IO) {
        val downloadedGames = gameDao.getGamesWithLocalPath().filter { it.rommId != null }
        var queued = 0
        val client = apiClient.get()

        for (game in downloadedGames) {
            val emulatorId = client.resolveEmulatorForGame(game) ?: continue
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug)
            val folderSyncEnabled = isFolderSaveSyncEnabled()

            val savePath = savePathResolver.discoverSavePath(
                emulatorId = emulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                emulatorPackage = emulatorPackage,
                gameId = game.id,
                isFolderSaveSyncEnabled = folderSyncEnabled
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

    suspend fun processPendingUploads(): Int = withContext(Dispatchers.IO) {
        val pending = pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_FILE)
        if (pending.isEmpty()) {
            return@withContext 0
        }
        Logger.info(TAG, "Processing ${pending.size} pending save uploads")

        for (item in pending) {
            val game = gameDao.getById(item.gameId) ?: continue
            syncQueueManager.addOperation(
                SyncOperation(
                    gameId = item.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.UPLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var processed = 0
        val client = apiClient.get()

        for (item in pending) {
            val payload = SaveFilePayload.fromJson(item.payloadJson) ?: continue
            Logger.debug(TAG, "Processing pending upload: gameId=${item.gameId}, emulator=${payload.emulatorId}")
            syncQueueManager.updateOperation(item.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = client.uploadSave(item.gameId, payload.emulatorId)) {
                is SaveSyncResult.Success -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    syncQueueManager.completeOperation(item.gameId)
                    processed++
                }
                is SaveSyncResult.Conflict -> {
                    Logger.debug(TAG, "Pending upload conflict for gameId=${item.gameId}, leaving in queue")
                    syncQueueManager.completeOperation(item.gameId, "Server has newer save")
                }
                is SaveSyncResult.Error -> {
                    Logger.debug(TAG, "Pending upload failed for gameId=${item.gameId}: ${result.message}")
                    pendingSyncQueueDao.markFailed(item.id, result.message)
                    syncQueueManager.completeOperation(item.gameId, result.message)
                }
                else -> {
                    syncQueueManager.completeOperation(item.gameId, "Sync not available")
                }
            }
        }

        Logger.info(TAG, "Processed $processed/${pending.size} pending uploads")
        processed
    }

    suspend fun downloadPendingServerSaves(): Int = withContext(Dispatchers.IO) {
        val pendingDownloads = saveSyncDao.getPendingDownloads()
        if (pendingDownloads.isEmpty()) {
            return@withContext 0
        }

        for (syncEntity in pendingDownloads) {
            val game = gameDao.getById(syncEntity.gameId) ?: continue
            syncQueueManager.addOperation(
                SyncOperation(
                    gameId = syncEntity.gameId,
                    gameName = game.title,
                    coverPath = game.coverPath,
                    direction = SyncDirection.DOWNLOAD,
                    status = SyncStatus.PENDING
                )
            )
        }

        var downloaded = 0
        val client = apiClient.get()

        for (syncEntity in pendingDownloads) {
            syncQueueManager.updateOperation(syncEntity.gameId) { it.copy(status = SyncStatus.IN_PROGRESS) }

            when (val result = client.downloadSave(syncEntity.gameId, syncEntity.emulatorId, syncEntity.channelName)) {
                is SaveSyncResult.Success -> {
                    syncQueueManager.completeOperation(syncEntity.gameId)
                    downloaded++
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

        for (serverSave in serverSaves) {
            val channelName = client.parseServerChannelNameForSync(serverSave.fileName, romBaseName)
            val serverTime = client.parseTimestamp(serverSave.updatedAt)

            saveSyncDao.upsert(
                SaveSyncEntity(
                    id = 0,
                    gameId = gameId,
                    rommId = rommId,
                    emulatorId = emulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = null,
                    localUpdatedAt = null,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = null,
                    syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
                )
            )

            val result = client.downloadSave(gameId, emulatorId, channelName, skipBackup = true)
            if (result is SaveSyncResult.Error) {
                Logger.error(TAG, "syncSavesForNewDownload: failed '${serverSave.fileName}': ${result.message}")
            }
        }
    }

    private suspend fun isFolderSaveSyncEnabled(): Boolean {
        val prefs = userPreferencesRepository.preferences.first()
        return prefs.saveSyncEnabled && prefs.experimentalFolderSaveSync
    }

    companion object {
        private const val TAG = "SaveSyncOrchestrator"
    }
}
