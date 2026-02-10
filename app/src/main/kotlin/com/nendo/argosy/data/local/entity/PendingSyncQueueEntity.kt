package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_sync_queue",
    indices = [
        Index("priority", "createdAt"),
        Index("gameId"),
        Index("status")
    ]
)
data class PendingSyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val syncType: SyncType,
    val priority: Int,
    val payloadJson: String,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class SyncType {
    SAVE_FILE,
    SAVE_STATE,
    RATING,
    DIFFICULTY,
    STATUS,
    FAVORITE,
    ACHIEVEMENT
}

enum class SyncStatus {
    PENDING,
    IN_PROGRESS,
    FAILED,
    COMPLETED
}

object SyncPriority {
    const val SAVE_FILE = 0
    const val SAVE_STATE = 1
    const val PROPERTY = 2
}
