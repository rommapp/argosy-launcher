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
        Index(value = ["packageName"], unique = true),
        Index("regions"),
        Index("gameModes"),
        Index("franchises")
    ]
)
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val platformId: Long,
    val platformSlug: String = "",
    val title: String,
    val sortTitle: String,

    val localPath: String?,
    val rommId: Long?,
    val igdbId: Long?,
    val steamAppId: Long? = null,
    val steamLauncher: String? = null,
    val packageName: String? = null,
    val launcherSetManually: Boolean = false,
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
    val status: String? = null,
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
    val earnedAchievementCount: Int = 0,

    val activeSaveChannel: String? = null,
    val activeSaveTimestamp: Long? = null,

    val titleId: String? = null,
    val youtubeVideoId: String? = null
)

data class GameListItem(
    val id: Long,
    val platformId: Long,
    val platformSlug: String,
    val title: String,
    val sortTitle: String,
    val localPath: String?,
    val source: GameSource,
    val coverPath: String?,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val isMultiDisc: Boolean,
    val rommId: Long?,
    val steamAppId: Long?,
    val packageName: String?,
    val playCount: Int,
    val playTimeMinutes: Int,
    val lastPlayed: Instant?,
    val genre: String?,
    val gameModes: String?
) {
    val isDownloaded: Boolean get() = localPath != null
}

data class GameCategoryInfo(
    val id: Long,
    val genre: String?,
    val gameModes: String?
)
