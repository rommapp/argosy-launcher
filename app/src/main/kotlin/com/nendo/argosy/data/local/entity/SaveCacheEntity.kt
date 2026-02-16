package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "save_cache",
    indices = [
        Index("gameId"),
        Index("cachedAt"),
        Index("needsRemoteSync")
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
    val rommSaveId: Long? = null
) {
    companion object {
        @Deprecated("Hardcore saves now use isHardcore flag instead of special slot name")
        const val SLOT_HARDCORE = "HARDCORE"
    }
}
