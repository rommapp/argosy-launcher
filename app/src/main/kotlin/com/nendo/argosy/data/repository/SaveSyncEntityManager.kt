package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncEntityManager @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val syncQueueManager: SyncQueueManager
) {
    val syncQueueState: StateFlow<SyncQueueState> = syncQueueManager.state

    fun clearCompletedOperations() = syncQueueManager.clearCompletedOperations()

    fun observeNewSavesCount(): Flow<Int> = saveSyncDao.observeNewSavesCount()

    fun observePendingCount(): Flow<Int> = saveCacheDao.observeNeedingRemoteSyncCount()

    suspend fun clearDirtyFlags(gameId: Long) = saveCacheDao.clearAllDirtyFlags(gameId)

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? {
        return saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
    }

    suspend fun updateSyncEntity(
        gameId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ) {
        val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        if (existing != null) {
            saveSyncDao.upsert(
                existing.copy(
                    localSavePath = localPath ?: existing.localSavePath,
                    localUpdatedAt = localUpdatedAt ?: existing.localUpdatedAt
                )
            )
        }
    }

    suspend fun markRestored(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String?,
        localPath: String,
        rommSaveId: Long?,
        serverTimestamp: Instant?
    ) {
        val existing = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
                ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }
        val now = Instant.now()
        saveSyncDao.upsert(
            SaveSyncEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                rommId = rommId,
                emulatorId = emulatorId,
                channelName = channelName,
                rommSaveId = rommSaveId ?: existing?.rommSaveId,
                localSavePath = localPath,
                localUpdatedAt = now,
                serverUpdatedAt = serverTimestamp ?: existing?.serverUpdatedAt,
                lastSyncedAt = now,
                lastUploadedHash = existing?.lastUploadedHash,
                syncStatus = SaveSyncEntity.STATUS_SYNCED,
                userSelectedRestorePoint = existing?.userSelectedRestorePoint ?: false,
                userSelectedRestorePointAt = existing?.userSelectedRestorePointAt
            )
        )
    }

    suspend fun markUserSelectedRestorePoint(gameId: Long, emulatorId: String, channelName: String?) {
        val row = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
                ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }
        if (row != null) {
            saveSyncDao.setUserSelectedRestorePoint(row.id, Instant.now().toEpochMilli())
        }
    }

    suspend fun clearUserSelectedRestorePointForGame(gameId: Long) {
        saveSyncDao.clearUserSelectedRestorePointForGame(gameId)
    }

    suspend fun createOrUpdateSyncEntity(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?,
        channelName: String? = null
    ): SaveSyncEntity {
        val existing = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        }
        val entity = SaveSyncEntity(
            id = existing?.id ?: 0,
            gameId = gameId,
            rommId = rommId,
            emulatorId = emulatorId,
            channelName = channelName,
            rommSaveId = existing?.rommSaveId,
            localSavePath = localPath ?: existing?.localSavePath,
            localUpdatedAt = localUpdatedAt ?: existing?.localUpdatedAt,
            serverUpdatedAt = existing?.serverUpdatedAt,
            lastSyncedAt = existing?.lastSyncedAt,
            lastUploadedHash = existing?.lastUploadedHash,
            syncStatus = existing?.syncStatus ?: SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastSyncDeviceId = existing?.lastSyncDeviceId,
            lastSyncDeviceName = existing?.lastSyncDeviceName,
            userSelectedRestorePoint = existing?.userSelectedRestorePoint ?: false,
            userSelectedRestorePointAt = existing?.userSelectedRestorePointAt
        )
        saveSyncDao.upsert(entity)
        return entity
    }
}
