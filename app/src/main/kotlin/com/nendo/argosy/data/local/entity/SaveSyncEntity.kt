package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "save_sync",
    indices = [
        Index(value = ["gameId", "emulatorId", "channelName"], unique = true),
        Index("rommSaveId"),
        Index("lastSyncedAt")
    ]
)
data class SaveSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val emulatorId: String,
    val channelName: String? = null,
    val rommSaveId: Long? = null,
    val localSavePath: String? = null,
    val localUpdatedAt: Instant? = null,
    val serverUpdatedAt: Instant? = null,
    val lastSyncedAt: Instant? = null,
    val syncStatus: String,
    val lastSyncError: String? = null
) {
    companion object {
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_LOCAL_NEWER = "LOCAL_NEWER"
        const val STATUS_SERVER_NEWER = "SERVER_NEWER"
        const val STATUS_CONFLICT = "CONFLICT"
        const val STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
    }
}
