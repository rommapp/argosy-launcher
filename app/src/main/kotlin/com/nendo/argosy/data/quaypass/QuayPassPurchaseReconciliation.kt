package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.entity.QuayPassOwnedPartEntity

/** Pure reconciliation helpers for offline part purchases. */
object QuayPassPurchaseReconciliation {

    /**
     * Orders unsynced purchases for replay so equipped parts claim tickets first
     * and, within each group, the oldest purchase wins. This realizes the revert
     * priority (keep equipped, then by purchase time) since whatever the server
     * cannot cover is replayed last and reverted.
     */
    fun orderForReplay(
        unsynced: List<QuayPassOwnedPartEntity>,
        equipped: Set<String>
    ): List<QuayPassOwnedPartEntity> =
        unsynced.sortedWith(
            compareByDescending<QuayPassOwnedPartEntity> { it.partKey in equipped }
                .thenBy { it.acquiredAt }
        )
}
