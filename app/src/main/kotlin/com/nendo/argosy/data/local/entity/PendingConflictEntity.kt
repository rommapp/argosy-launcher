package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_conflicts",
    indices = [
        Index(value = ["gameId", "rommSaveId"], unique = true),
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
    val dismissed: Boolean = false
)
