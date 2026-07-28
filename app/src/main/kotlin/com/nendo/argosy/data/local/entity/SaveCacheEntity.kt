package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "save_cache",
    indices = [
        Index("gameId"),
        Index("cachedAt"),
        Index("needsRemoteSync"),
        Index("ownerUserId")
    ]
)
data class SaveCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val emulatorId: String,
    val cachedAt: Instant,
    val saveSize: Long,
    val cachePath: String,
    val isLocked: Boolean = false,
    val note: String? = null,
    val contentHash: String? = null,
    val cheatsUsed: Boolean = false,
    val isHardcore: Boolean = false,
    val slotName: String? = null,
    val isRollback: Boolean = false,
    val channelName: String? = null,
    val needsRemoteSync: Boolean = false,
    val lastSyncedAt: Instant? = null,
    val remoteSyncError: String? = null,
    val rommSaveId: Long? = null,
    val ownerUserId: Long? = null,
    /**
     * True when this row was, at its last reconciliation with the server, the newest copy the
     * server held for its channel. An account switch places a save offline from the incoming
     * account's cache, and this is the only durable record of whether that cache is the current
     * copy or a known-stale one the server has since moved past.
     */
    @ColumnInfo(defaultValue = "0")
    val serverCurrentAtSync: Boolean = false
) {
    companion object {
        @Deprecated("Hardcore saves now use isHardcore flag instead of special slot name")
        const val SLOT_HARDCORE = "HARDCORE"
    }
}
