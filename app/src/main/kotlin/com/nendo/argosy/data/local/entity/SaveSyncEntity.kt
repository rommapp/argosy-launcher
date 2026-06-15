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
    val lastSyncError: String? = null,
    /** Server-verified content hash (RomM >= 4.9 only) of the held file, set from upload/download/fetch reconcile; null = none or pre-4.9. Never store a locally-computed hash here. */
    val lastUploadedHash: String? = null,
    /** Client-computed hash of the last-synced local file (this device's hash space). Used only for local change detection; never sent to the server. */
    val localContentHash: String? = null,
    /** Server file timestamp at the moment we detected a corrupt download.
     * Subsequent sync attempts skip the download while the server's
     * timestamp matches; cleared automatically when it changes. */
    val corruptZipTimestamp: String? = null,
    val lastSyncDeviceId: String? = null,
    val lastSyncDeviceName: String? = null,
    val userSelectedRestorePoint: Boolean = false,
    val userSelectedRestorePointAt: Instant? = null
) {
    companion object {
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_LOCAL_NEWER = "LOCAL_NEWER"
        const val STATUS_SERVER_NEWER = "SERVER_NEWER"
        const val STATUS_CONFLICT = "CONFLICT"
        const val STATUS_PENDING_UPLOAD = "PENDING_UPLOAD"
        const val STATUS_NEEDS_HARDCORE_RESOLUTION = "NEEDS_HARDCORE_RESOLUTION"
    }
}
