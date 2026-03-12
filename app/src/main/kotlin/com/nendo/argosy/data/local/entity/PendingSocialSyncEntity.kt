package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_social_sync",
    indices = [
        Index("status"),
        Index("syncType")
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
    val updatedAt: Instant = Instant.now()
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
