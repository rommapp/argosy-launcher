package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveOwnershipDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.util.Logger
import javax.inject.Inject

private const val TAG = "DeleteSaveChannel"

/**
 * Removes a save slot wherever it exists: saves and states, on this device and on the server,
 * plus the sync tracking row, queued uploads, parked conflicts and ownership attribution that
 * name the channel, since any one of those is enough for the next reconcile to advertise the slot
 * again and upload the live save under its name.
 *
 * States are tombstoned rather than only deleted, so a server delete that never lands cannot
 * download them back. The purge is not behind the emulator's state-support gate: that gate decides
 * whether states may SYNC, and says nothing about whether the user asked for them to be gone.
 */
class DeleteSaveChannelUseCase @Inject constructor(
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager,
    private val activeSaveRepository: ActiveSaveRepository,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val pendingConflictDao: PendingConflictDao,
    private val saveOwnershipDao: SaveOwnershipDao,
    private val payloadCodec: SyncPayloadCodec
) {
    /**
     * [ServerDeleteFailed] means the local slot is gone but the server still holds its saves, so
     * the next sync lists them again and the user has to delete once more when connected.
     */
    sealed interface Result {
        data object Deleted : Result
        data object ServerDeleteFailed : Result
    }

    suspend operator fun invoke(gameId: Long, channelName: String): Result {
        val ownerUserId = activeSaveRepository.activeOwnerId()
        val wasActive = activeSaveRepository.getActiveChannel(gameId) == channelName
        val entries = getUnifiedSavesUseCase(gameId, expandHistory = true)
            .filter { it.channelName == channelName }

        val serverSaveIds = entries.mapNotNull { it.serverSaveId }.distinct()
        val serverDeleted = serverSaveIds.isEmpty() || saveSyncRepository.deleteServerSaves(serverSaveIds)
        if (!serverDeleted) {
            Logger.warn(TAG, "Server delete failed for gameId=$gameId channel=$channelName saveIds=$serverSaveIds; removing local records anyway")
        }

        val cacheIds = entries.mapNotNull { it.localCacheId }.toSet()
        cacheIds.forEach { saveCacheManager.deleteSave(it) }

        stateCacheManager.getStatesForChannel(gameId, channelName).forEach { state ->
            stateCacheManager.purgeState(
                gameId = gameId,
                cacheId = state.id,
                serverStateId = state.rommSaveId
            )
        }

        forgetQueuedUploads(gameId, channelName, ownerUserId, cacheIds)
        saveSyncDao.deleteByGameAndChannel(gameId, channelName, ownerUserId)
        pendingConflictDao.deleteByGameAndSlot(gameId, channelName, PendingConflictEntity.ownerScope(ownerUserId))
        if (ownerUserId != null) {
            saveOwnershipDao.detachChannel(gameId, channelName, ownerUserId)
        }

        activeSaveRepository.forgetChannel(gameId, channelName)
        if (wasActive) {
            activeSaveRepository.clearActive(gameId)
        }
        return if (serverDeleted) Result.Deleted else Result.ServerDeleteFailed
    }

    private suspend fun forgetQueuedUploads(
        gameId: Long,
        channelName: String,
        ownerUserId: Long?,
        deletedCacheIds: Set<Long>
    ) {
        pendingSyncQueueDao.getByGameId(gameId)
            .filter { row -> row.syncType == SyncType.SAVE_FILE }
            .filter { row ->
                val pinsDeletedCache = row.cacheId?.let { it in deletedCacheIds } == true
                val namesChannel = row.ownerUserId == ownerUserId &&
                    payloadCodec.decodeSaveFile(row.payloadJson)?.channelName == channelName
                pinsDeletedCache || namesChannel
            }
            .forEach { row ->
                Logger.debug(TAG, "Dropping queued upload id=${row.id} for deleted channel gameId=$gameId channel=$channelName")
                pendingSyncQueueDao.deleteById(row.id)
            }
    }
}
