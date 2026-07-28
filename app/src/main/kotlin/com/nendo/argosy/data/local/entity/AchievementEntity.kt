package com.nendo.argosy.data.local.entity

import androidx.room.ColumnInfo
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
    indices = [
        Index("gameId"),
        Index("ownerUserId"),
        Index(value = ["gameId", "raId", "ownerUserId"], unique = true)
    ]
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
    val unlockedAt: Long? = null,
    val unlockedHardcoreAt: Long? = null,
    val socialSharedAt: Long? = null,
    /**
     * RomM user id whose RA login earned these unlocks, or [NO_OWNER] for rows written before
     * accounts existed. It is part of the unique index because the insert strategy is REPLACE:
     * keyed on `(gameId, raId)` alone, one account's unlock row deletes the other's.
     */
    @ColumnInfo(defaultValue = "0")
    val ownerUserId: Long = NO_OWNER
) {
    val isUnlocked: Boolean
        get() = unlockedAt != null || unlockedHardcoreAt != null

    companion object {
        /**
         * Owner stamp for rows that predate accounts, and for a device with no RomM account.
         */
        const val NO_OWNER = 0L
    }
}
