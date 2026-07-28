package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_social_sync",
    indices = [
        Index("status"),
        Index("syncType"),
        Index("ownerUserId")
    ]
)
data class PendingSocialSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val syncType: SocialSyncType,
    val payloadJson: String,
    val occurredAt: Instant,
    val status: SocialSyncStatus = SocialSyncStatus.PENDING,
    val retryCount: Int = 0,
    val maxRetries: Int = 5,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * RomM user id of the account that enqueued the row. The social user id was only ever inside
     * [payloadJson], so a drain sent whatever the live socket happened to be signed in as. Null
     * means the row predates account binding.
     */
    val ownerUserId: Long? = null
)

enum class SocialSyncType {
    PLAY_SESSION,
    FEED_EVENT
}

enum class SocialSyncStatus {
    PENDING,
    IN_PROGRESS,
    FAILED
}
