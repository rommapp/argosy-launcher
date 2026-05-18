package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.local.entity.PendingConflictEntity
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConflictResolutionService"

sealed class ConflictResolutionOutcome {
    data object Dismissed : ConflictResolutionOutcome()
    data class Resolved(val syncResult: SaveSyncResult) : ConflictResolutionOutcome()
    data class Failed(val message: String) : ConflictResolutionOutcome()
}

@Singleton
class ConflictResolutionService @Inject constructor(
    private val pendingConflictDao: PendingConflictDao,
    private val saveSyncRepository: SaveSyncRepository
) {
    suspend fun resolve(
        conflict: PendingConflictEntity,
        resolution: ConflictResolution
    ): ConflictResolutionOutcome = withContext(Dispatchers.IO) {
        when (resolution) {
            ConflictResolution.SKIP -> {
                pendingConflictDao.dismiss(conflict.id)
                Logger.info(TAG, "[Resolve] SKIP gameId=${conflict.gameId} channel=${conflict.slot} conflictId=${conflict.id} -> dismissed")
                ConflictResolutionOutcome.Dismissed
            }
            ConflictResolution.KEEP_LOCAL -> {
                val emulatorId = conflict.emulator
                    ?: return@withContext ConflictResolutionOutcome.Failed("Missing emulator for conflict ${conflict.id}")
                pendingConflictDao.dismiss(conflict.id)
                val result = saveSyncRepository.uploadSave(
                    gameId = conflict.gameId,
                    emulatorId = emulatorId,
                    channelName = conflict.slot,
                    forceOverwrite = true
                )
                Logger.info(TAG, "[Resolve] KEEP_LOCAL gameId=${conflict.gameId} channel=${conflict.slot} -> $result")
                ConflictResolutionOutcome.Resolved(result)
            }
            ConflictResolution.KEEP_SERVER -> {
                val emulatorId = conflict.emulator
                    ?: return@withContext ConflictResolutionOutcome.Failed("Missing emulator for conflict ${conflict.id}")
                pendingConflictDao.dismiss(conflict.id)
                val result = saveSyncRepository.downloadSave(
                    gameId = conflict.gameId,
                    emulatorId = emulatorId,
                    channelName = conflict.slot,
                    knownServerSaveId = conflict.rommSaveId
                )
                Logger.info(TAG, "[Resolve] KEEP_SERVER gameId=${conflict.gameId} channel=${conflict.slot} -> $result")
                ConflictResolutionOutcome.Resolved(result)
            }
        }
    }
}
