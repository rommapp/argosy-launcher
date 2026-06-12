package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictAutoResolver @Inject constructor(
    private val gameDao: GameDao,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao
) {
    suspend fun classify(
        operation: ReconcileOperation,
        clientHash: String? = null
    ): Resolution {
        if (operation.action != ReconcileAction.CONFLICT) {
            return Resolution.AsIs
        }

        val gameRow = gameDao.getByRommId(operation.romId)
        val gameId = gameRow?.id

        if (gameId != null && gameRow.activeSaveApplied) {
            Logger.debug(TAG, "rule 1: activeSaveApplied=true for romId=${operation.romId} -> KEEP_LOCAL")
            return Resolution.KeepLocal("user-restored")
        }

        if (gameId != null) {
            val queued = pendingSyncQueueDao.getByGameId(gameId)
                .any { it.syncType == SyncType.SAVE_FILE }
            if (queued) {
                Logger.debug(TAG, "rule 2: queued upload exists for romId=${operation.romId} -> KEEP_LOCAL")
                return Resolution.KeepLocal("queued-upload")
            }
        }

        val syncRow = gameId?.let { gid ->
            operation.emulator?.takeIf { it.isNotBlank() }?.let { emu ->
                saveSyncDao.getByGameAndEmulator(gid, emu)
            }
        }
        val lastUploadedHash = syncRow?.lastUploadedHash

        if (lastUploadedHash != null && clientHash != null) {
            if (clientHash == lastUploadedHash && clientHash != operation.serverContentHash) {
                Logger.debug(TAG, "rule 3: local unchanged since last upload -> KEEP_SERVER")
                return Resolution.KeepServer("local-unchanged")
            }
            if (operation.serverContentHash == lastUploadedHash && clientHash != lastUploadedHash) {
                Logger.debug(TAG, "rule 4: server unchanged since last upload -> KEEP_LOCAL")
                return Resolution.KeepLocal("server-unchanged")
            }
        }

        return Resolution.AsIs
    }

    sealed class Resolution {
        data object AsIs : Resolution()
        data class KeepLocal(val ruleId: String) : Resolution()
        data class KeepServer(val ruleId: String) : Resolution()
    }

    companion object {
        private const val TAG = "ConflictAutoResolver"
    }
}
