package com.nendo.argosy.domain.usecase.savechannel

import com.nendo.argosy.data.local.dao.PendingConflictDao
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncEngine
import com.nendo.argosy.data.sync.snapshot.SnapshotSyncResult
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.util.Logger
import javax.inject.Inject

/**
 * Settles a snapshot conflict so the save on disk, the snapshot this device holds and the active
 * cached save all name the same snapshot. A branch keeps the live states where they are: they
 * belong to the session the new channel now carries. A stored conflict row is dismissed once the
 * choice lands, and stays open when it fails.
 */
class ResolveSnapshotConflictUseCase @Inject constructor(
    private val engine: SnapshotSyncEngine,
    private val activeSaveRepository: ActiveSaveRepository,
    private val gameRepository: GameRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val pendingConflictDao: PendingConflictDao
) {
    suspend fun appliesTo(gameId: Long): Boolean = engine.isEligible(gameId)

    suspend fun isParkedDowngrade(pendingConflictId: Long?): Boolean =
        pendingConflictId?.let { pendingConflictDao.getById(it) }?.isHardcoreDowngrade == true

    suspend operator fun invoke(
        gameId: Long,
        emulatorId: String?,
        channelName: String?,
        choice: SnapshotConflictChoice,
        pendingConflictId: Long? = null
    ): SnapshotSyncResult {
        val emulator = emulatorId ?: emulatorFor(gameId, pendingConflictId)
            ?: return SnapshotSyncResult.Failed("No emulator resolves for game $gameId")
        val isHardcore = activeSaveRepository.getActiveRow(gameId)?.isHardcore == true
        val result = when (choice) {
            SnapshotConflictChoice.MINE -> engine.keepLocal(gameId, emulator, channelName, isHardcore)
            SnapshotConflictChoice.THEIRS -> engine.keepServer(gameId, emulator, channelName)
            SnapshotConflictChoice.REVERT -> engine.revert(gameId, emulator, channelName)
            SnapshotConflictChoice.BRANCH -> engine.branch(gameId, emulator, channelName, isHardcore).also { branched ->
                if (branched is SnapshotSyncResult.Branched) {
                    activeSaveRepository.registerChannel(gameId, branched.channelLabel)
                    activeSaveRepository.activateChannel(gameId, branched.channelLabel)
                }
            }
        }
        if (pendingConflictId != null && result.settles) pendingConflictDao.dismiss(pendingConflictId)
        Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | conflict on ${channelName ?: "default"} resolved as $choice -> $result")
        return result
    }

    private suspend fun emulatorFor(gameId: Long, pendingConflictId: Long?): String? {
        pendingConflictId?.let { pendingConflictDao.getById(it)?.emulator }?.let { return it }
        val game = gameRepository.getById(gameId) ?: return null
        return saveSyncRepository.resolveEmulatorForGame(game)
    }

    private val SnapshotSyncResult.settles: Boolean
        get() = this is SnapshotSyncResult.Pushed || this is SnapshotSyncResult.Applied ||
            this is SnapshotSyncResult.Branched || this == SnapshotSyncResult.UpToDate ||
            this == SnapshotSyncResult.Empty

    private companion object {
        const val TAG = "ResolveSnapshotConflict"
    }
}
