package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One deferred sync operation, bound to the account that created it.
 *
 * [ownerUserId] is the RomM user id of the account that enqueued the row, not a local account
 * row id; a drain resolves the client for it rather than using whoever is signed in. Null means
 * the row predates account binding and drains under the live connection.
 *
 * [cacheId] pins a SAVE_FILE row to a save_cache row so the deferred upload sends the bytes that
 * were captured at enqueue time instead of whatever is on the live save path at drain time.
 */
@Entity(
    tableName = "pending_sync_queue",
    indices = [
        Index("priority", "createdAt"),
        Index("gameId"),
        Index("status"),
        Index("sessionId"),
        Index("ownerUserId")
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
    val updatedAt: Instant = Instant.now(),
    val sessionId: Long? = null,
    val ownerUserId: Long? = null,
    val cacheId: Long? = null
)

enum class SyncType {
    SAVE_FILE,
    SAVE_STATE,
    RATING,
    DIFFICULTY,
    STATUS,
    FAVORITE,
    ACHIEVEMENT,
    SCREENSHOT
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
