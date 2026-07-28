package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A save conflict parked for a human decision, attributed to the account that hit it.
 *
 * [ownerUserId] is part of the unique index on purpose: the insert strategy is REPLACE, so
 * without the owner column one account parking a conflict would delete the other account's
 * parked conflict for the same game and server save. It is not null because SQLite treats NULLs
 * as distinct inside a unique index, which would stop unattributed rows deduping at all;
 * [UNATTRIBUTED] stands for a conflict found before any RomM user id was known.
 */
@Entity(
    tableName = "pending_conflicts",
    indices = [
        Index(value = ["gameId", "rommSaveId", "ownerUserId"], unique = true),
        Index("dismissed")
    ]
)
data class PendingConflictEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommSaveId: Long?,
    val fileName: String,
    val slot: String? = null,
    val emulator: String? = null,
    val localUpdatedAt: Instant?,
    val serverUpdatedAt: Instant?,
    val localHash: String? = null,
    val serverHash: String? = null,
    val reason: String = "",
    val discoveredAt: Instant = Instant.now(),
    val dismissed: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val ownerUserId: Long = UNATTRIBUTED
) {
    companion object {
        const val UNATTRIBUTED = 0L

        fun ownerScope(rommUserId: Long?): List<Long> =
            if (rommUserId == null) listOf(UNATTRIBUTED) else listOf(rommUserId, UNATTRIBUTED)
    }
}
