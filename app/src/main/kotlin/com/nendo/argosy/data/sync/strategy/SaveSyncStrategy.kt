package com.nendo.argosy.data.sync.strategy

interface SaveSyncStrategy {
    suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan

    suspend fun completeSession(sessionId: Long, operationsCompleted: Int, operationsFailed: Int): CompleteOutcome {
        return CompleteOutcome.ACCEPTED
    }
}

/** Outcome of a server-side session-complete call. Drives whether local queue rows can be dropped. */
enum class CompleteOutcome {
    /** Server acknowledged (2xx). Safe to delete local rows. */
    ACCEPTED,

    /** Server reports the session no longer exists or is already finalized (404/410/409). Drop rows. */
    ALREADY_FINALIZED,

    /** Transient/server failure (5xx, network). Retain rows and retry on next cycle. */
    RETRY_LATER,
}
