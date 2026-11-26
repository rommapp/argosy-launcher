package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "music_downloads",
    primaryKeys = ["serverKey", "trackKey"],
    indices = [Index("source", "albumId"), Index("ownerKey")]
)
data class MusicDownloadEntity(
    val trackKey: String,
    val serverKey: String,
    val ownerKey: Long,
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
) {
    companion object {
        const val UNSCOPED_SERVER_KEY = ""

        /**
         * `<source>:<instance>` for a source signed in at [baseUrl], with the instance normalised by
         * [serverInstanceKey]. Null when the source has no address.
         */
        fun serverKeyOf(source: String, baseUrl: String?): String? =
            baseUrl?.takeIf { it.isNotBlank() }?.let { "$source:${serverInstanceKey(it)}" }
    }
}
