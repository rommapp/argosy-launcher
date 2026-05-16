package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.remote.romm.RomMClientSaveState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMSyncCompletePayload
import com.nendo.argosy.data.remote.romm.RomMSyncNegotiatePayload
import com.nendo.argosy.data.remote.romm.RomMSyncOperation
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NegotiatorSaveSyncStrategy @Inject constructor(
    private val connectionManager: RomMConnectionManager
) : SaveSyncStrategy {

    override suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan {
        val api = connectionManager.getApi() ?: run {
            Logger.debug(TAG, "planReconcile: no api, returning empty plan")
            return ReconcilePlan.EMPTY
        }
        val deviceId = connectionManager.getDeviceId() ?: run {
            Logger.debug(TAG, "planReconcile: no deviceId, returning empty plan")
            return ReconcilePlan.EMPTY
        }

        val payload = RomMSyncNegotiatePayload(
            deviceId = deviceId,
            saves = localInventory.map {
                RomMClientSaveState(
                    romId = it.romId,
                    fileName = it.fileName,
                    slot = it.slot,
                    emulator = it.emulator,
                    contentHash = it.contentHash,
                    updatedAt = it.updatedAt,
                    fileSizeBytes = it.fileSizeBytes
                )
            }
        )

        val response = try {
            api.negotiateSync(payload)
        } catch (e: Exception) {
            Logger.error(TAG, "planReconcile: negotiate failed", e)
            return ReconcilePlan.EMPTY
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "planReconcile: server returned ${response.code()}")
            return ReconcilePlan.EMPTY
        }
        val body = response.body() ?: return ReconcilePlan.EMPTY

        Logger.info(
            TAG,
            "planReconcile: sessionId=${body.sessionId} upload=${body.totalUpload} download=${body.totalDownload} conflict=${body.totalConflict} no_op=${body.totalNoOp}"
        )

        return ReconcilePlan(
            sessionId = body.sessionId,
            operations = body.operations.map { it.toReconcileOperation() }
        )
    }

    override suspend fun completeSession(
        sessionId: Long,
        operationsCompleted: Int,
        operationsFailed: Int
    ): Result<Unit> {
        val api = connectionManager.getApi() ?: return Result.success(Unit)
        return try {
            val response = api.completeSyncSession(
                sessionId,
                RomMSyncCompletePayload(
                    operationsCompleted = operationsCompleted,
                    operationsFailed = operationsFailed
                )
            )
            if (response.isSuccessful) {
                Logger.info(TAG, "completeSession: $sessionId done | ok=$operationsCompleted fail=$operationsFailed")
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Complete returned ${response.code()}"))
            }
        } catch (e: Exception) {
            Logger.error(TAG, "completeSession: $sessionId failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "NegotiatorSaveSyncStrategy"
    }
}

private fun RomMSyncOperation.toReconcileOperation(): ReconcileOperation = ReconcileOperation(
    action = when (action) {
        "upload" -> ReconcileAction.UPLOAD
        "download" -> ReconcileAction.DOWNLOAD
        "conflict" -> ReconcileAction.CONFLICT
        else -> ReconcileAction.NO_OP
    },
    romId = romId,
    saveId = saveId,
    fileName = fileName,
    slot = slot,
    emulator = emulator,
    reason = reason,
    serverUpdatedAt = serverUpdatedAt,
    serverContentHash = serverContentHash
)
