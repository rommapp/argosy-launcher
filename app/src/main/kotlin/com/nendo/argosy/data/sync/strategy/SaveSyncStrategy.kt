package com.nendo.argosy.data.sync.strategy

interface SaveSyncStrategy {
    suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan

    suspend fun completeSession(sessionId: Long, operationsCompleted: Int, operationsFailed: Int): Result<Unit> {
        return Result.success(Unit)
    }
}
