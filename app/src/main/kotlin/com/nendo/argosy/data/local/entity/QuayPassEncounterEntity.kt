package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

/**
 * One peer this device has met, recorded against the local account that met them.
 *
 * [accountId] is the PEER's QuayPass account. [localOwnerUserId] is ours, and it is half the
 * primary key: without it the same peer collapses to one row device-wide, so the second local
 * account to meet them is refused as a duplicate and never receives the ticket credit.
 */
@Entity(
    tableName = "quaypass_encounters",
    primaryKeys = ["credentialFingerprint", "localOwnerUserId"],
    indices = [
        Index("encounteredAt"),
        Index("seenByUser"),
        Index("localOwnerUserId")
    ]
)
data class QuayPassEncounterEntity(
    val credentialFingerprint: String,
    val username: String,
    val displayName: String?,
    val avatarColor: String?,
    val avatarBlobBase64: String?,
    val greeting: String?,
    val lastGameTitle: String?,
    val lastGamePlatform: String?,
    val lastGamePlaytimeMinutes: Int?,
    val lastGameIgdbId: Long?,
    val encounteredAt: Instant,
    val seenByUser: Boolean = false,
    val accountId: String? = null,
    val reported: Boolean = false,
    val meetCount: Int = 1,
    @ColumnInfo(defaultValue = "0")
    val localOwnerUserId: Long = NO_OWNER
) {
    companion object {
        /**
         * Owner stamp for rows recorded before accounts existed, and for an unpaired device.
         */
        const val NO_OWNER = 0L
    }
}
