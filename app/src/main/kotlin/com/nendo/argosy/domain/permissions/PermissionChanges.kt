package com.nendo.argosy.domain.permissions

import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSnapshot

/**
 * The last permission state the person acknowledged, and the revocations already reported by a
 * notice. A null [acknowledged] means nothing has been recorded on this install yet.
 */
data class PermissionRecord(
    val acknowledged: Set<ArgosyPermission>? = null,
    val noticed: Set<ArgosyPermission> = emptySet()
)

data class PermissionReview(
    val record: PermissionRecord,
    val revoked: List<ArgosyPermission>,
    val unnoticed: List<ArgosyPermission>,
    val turnSaveSyncOff: Boolean
) {
    val noticedRecord: PermissionRecord
        get() = record.copy(noticed = record.noticed + unnoticed)
}

object PermissionChanges {

    /**
     * Compares [current] with [record]. Grants since the record are absorbed silently; a tracked
     * permission granted in the record and missing now is revoked until acknowledged. [expected]
     * names permissions the person changed from Argosy's own page, whose current state is taken
     * as acknowledged.
     */
    fun review(
        record: PermissionRecord,
        current: PermissionSnapshot,
        saveSyncEnabled: Boolean,
        expected: Set<ArgosyPermission> = emptySet()
    ): PermissionReview {
        val acknowledged = record.acknowledged
            ?: return PermissionReview(acknowledge(current), emptyList(), emptyList(), turnSaveSyncOff = false)
        val absorbed = (acknowledged - expected) + current.grantedTracked
        val revoked = ArgosyPermission.entries.filter { permission ->
            permission.tracksChanges && permission in absorbed &&
                current.of(permission) == PermissionAvailability.MISSING
        }
        return PermissionReview(
            record = PermissionRecord(absorbed, record.noticed.intersect(revoked.toSet())),
            revoked = revoked,
            unnoticed = revoked.filter { it !in record.noticed },
            turnSaveSyncOff = saveSyncEnabled && revoked.any { it.syncRequired }
        )
    }

    fun acknowledge(current: PermissionSnapshot): PermissionRecord =
        PermissionRecord(acknowledged = current.grantedTracked, noticed = emptySet())
}
