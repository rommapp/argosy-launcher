package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "music_downloads",
    indices = [Index("source", "albumId")]
)
data class MusicDownloadEntity(
    @PrimaryKey val trackKey: String,
    val source: String,
    val trackId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val trackNumber: Int?,
    val durationSec: Int?,
    val year: Int?,
    val genre: String?,
    val artCacheKey: String?,
    val artLocalPath: String?,
    val filePath: String?,
    val status: String,
    val sizeBytes: Long?,
    val downloadedAt: Instant?
)
