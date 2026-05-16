package com.nendo.argosy.data.sync.strategy

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacySaveSyncStrategy @Inject constructor() : SaveSyncStrategy {
    override suspend fun planReconcile(localInventory: List<LocalSaveState>): ReconcilePlan {
        return ReconcilePlan.EMPTY
    }
}
