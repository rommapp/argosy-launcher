package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "pending_achievements",
    indices = [
        Index("gameId"),
        Index("createdAt")
    ]
)
data class PendingAchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val achievementRaId: Long,
    val forHardcoreMode: Boolean,
    val earnedAt: Instant,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant = Instant.now()
)
