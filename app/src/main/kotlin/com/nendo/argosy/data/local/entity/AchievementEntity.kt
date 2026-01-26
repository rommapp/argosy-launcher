package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "achievements",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("gameId")]
)
data class AchievementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val type: String?,
    val badgeUrl: String?,
    val badgeUrlLock: String?,
    val cachedBadgeUrl: String? = null,
    val cachedBadgeUrlLock: String? = null,
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val unlockedHardcoreAt: Long? = null
)
