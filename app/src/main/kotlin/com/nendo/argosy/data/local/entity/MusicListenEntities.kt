package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "music_listens",
    foreignKeys = [
        ForeignKey(
            entity = MusicGameContextEntity::class,
            parentColumns = ["id"],
            childColumns = ["contextId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["ownerKey", "startedAt"]),
        Index(value = ["ownerKey", "trackKey"]),
        Index(value = ["ownerKey", "albumKey"]),
        Index(value = ["ownerKey", "localDay"]),
        Index(value = ["ownerKey", "scrobbleState"]),
        Index(value = ["contextId"])
    ]
)
data class MusicListenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerKey: Long,
    val startedAt: Long,
    val endedAt: Long,
    val localDay: Int,
    val listenedMs: Long,
    val endPositionMs: Long,
    val durationMs: Long,
    val counted: Boolean,
    val endReason: String?,
    val source: String,
    val sourceAccountKey: String,
    val sourceTrackId: String,
    val trackKey: String,
    val recordingKey: String?,
    val albumKey: String?,
    val selectionId: String?,
    val romId: Long?,
    val romFileId: Long?,
    val isrc: String?,
    val title: String,
    val artistCredit: String?,
    val albumTitle: String?,
    val trackGameTitle: String?,
    val contextId: Long?,
    val scrobbleState: String,
    val scrobbledAt: Long?
) {
    companion object {
        const val SCROBBLE_NONE = "none"
        const val SCROBBLE_PENDING = "pending"
        const val SCROBBLE_SENT = "sent"
    }
}

@Entity(
    tableName = "music_listen_artists",
    primaryKeys = ["listenId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = MusicListenEntity::class,
            parentColumns = ["id"],
            childColumns = ["listenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ownerKey", "artistKey"])]
)
data class MusicListenArtistEntity(
    val listenId: Long,
    val position: Int,
    val ownerKey: Long,
    val artistKey: String,
    val name: String
)

@Entity(
    tableName = "music_game_contexts",
    indices = [Index(value = ["ownerKey", "gameId", "sessionStartTime"], unique = true)]
)
data class MusicGameContextEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerKey: Long,
    val gameId: Long,
    val sessionStartTime: Long,
    val gameKey: String,
    val rommId: Long?,
    val igdbId: Long?,
    val platformSlug: String,
    val gameTitle: String,
    val emulatorPackage: String,
    val isBuiltIn: Boolean
)

@Entity(
    tableName = "music_game_context_tags",
    primaryKeys = ["contextId", "kind", "tagKey"],
    foreignKeys = [
        ForeignKey(
            entity = MusicGameContextEntity::class,
            parentColumns = ["id"],
            childColumns = ["contextId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["ownerKey", "kind", "tagKey"])]
)
data class MusicGameContextTagEntity(
    val contextId: Long,
    val kind: String,
    val tagKey: String,
    val ownerKey: Long,
    val label: String
)
