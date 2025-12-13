package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nendo.argosy.data.model.GameSource
import java.time.Instant

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("platformId"),
        Index("title"),
        Index("lastPlayed"),
        Index("source"),
        Index(value = ["rommId"], unique = true),
        Index(value = ["steamAppId"], unique = true),
        Index("regions"),
        Index("gameModes"),
        Index("franchises")
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val platformId: String,
    val title: String,
    val sortTitle: String,

    val localPath: String?,
    val rommId: Long?,
    val igdbId: Long?,
    val steamAppId: Long? = null,
    val steamLauncher: String? = null,
    val source: GameSource,

    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val screenshotPaths: String? = null,
    val cachedScreenshotPaths: String? = null,

    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val description: String? = null,
    val players: String? = null,
    val rating: Float? = null,

    val regions: String? = null,
    val languages: String? = null,
    val gameModes: String? = null,
    val franchises: String? = null,

    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val completion: Int = 0,
    val backlogged: Boolean = false,
    val nowPlaying: Boolean = false,

    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val playCount: Int = 0,
    val playTimeMinutes: Int = 0,
    val lastPlayed: Instant? = null,
    val addedAt: Instant = Instant.now(),

    val isMultiDisc: Boolean = false,
    val lastPlayedDiscId: Long? = null,
    val m3uPath: String? = null,

    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0
)
