package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.data.sync.SyncQueueState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncEntityManager @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val saveCacheDao: SaveCacheDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    val syncQueueState: StateFlow<SyncQueueState> = syncQueueManager.state

    fun clearCompletedOperations() = syncQueueManager.clearCompletedOperations()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNewSavesCount(): Flow<Int> = syncPreferencesRepository.preferences
        .map { it.rommUserId }
        .distinctUntilChanged()
        .flatMapLatest { saveSyncDao.observeNewSavesCount(it) }

    fun observePendingCount(): Flow<Int> = saveCacheDao.observeNeedingRemoteSyncCount()

    suspend fun clearDirtyFlags(gameId: Long) =
        saveCacheDao.clearAllDirtyFlags(gameId, syncPreferencesRepository.getRommUserId())

    suspend fun getSyncStatus(gameId: Long, emulatorId: String): SaveSyncEntity? {
        return saveSyncDao.getByGameAndEmulator(gameId, emulatorId, syncPreferencesRepository.getRommUserId())
    }

    suspend fun updateSyncEntity(
        gameId: Long,
        emulatorId: String,
        localPath: String?,
        localUpdatedAt: Instant?
    ) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val existing = saveSyncDao.getByGameAndEmulator(gameId, emulatorId, ownerUserId)
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
        serverTimestamp: Instant?,
        contentHash: String? = null
    ) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val existing = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName, ownerUserId)
                ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, channelName, ownerUserId)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId, ownerUserId)
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
                localContentHash = contentHash ?: existing?.localContentHash,
                syncStatus = SaveSyncEntity.STATUS_SYNCED,
                userSelectedRestorePoint = existing?.userSelectedRestorePoint ?: false,
                userSelectedRestorePointAt = existing?.userSelectedRestorePointAt,
                ownerUserId = existing?.ownerUserId ?: ownerUserId
            )
        )
    }

    suspend fun markUserSelectedRestorePoint(gameId: Long, emulatorId: String, channelName: String?) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val row = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName, ownerUserId)
                ?: saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, channelName, ownerUserId)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId, ownerUserId)
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
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val existing = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName, ownerUserId)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(
                gameId,
                emulatorId,
                SaveSyncApiClient.DEFAULT_SAVE_NAME,
                ownerUserId
            )
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
            localContentHash = existing?.localContentHash,
            syncStatus = existing?.syncStatus ?: SaveSyncEntity.STATUS_PENDING_UPLOAD,
            lastSyncDeviceId = existing?.lastSyncDeviceId,
            lastSyncDeviceName = existing?.lastSyncDeviceName,
            userSelectedRestorePoint = existing?.userSelectedRestorePoint ?: false,
            userSelectedRestorePointAt = existing?.userSelectedRestorePointAt,
            ownerUserId = existing?.ownerUserId ?: ownerUserId
        )
        saveSyncDao.upsert(entity)
        return entity
    }
}
