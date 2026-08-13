package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One video, audio or subtitle track belonging to one media source of one item.
 *
 * Cached so the track picker can be drawn before playback starts. It records what the tracks ARE,
 * never how to fetch them: a playable url is negotiated per playback and expires, so no stream
 * address is stored here.
 *
 * A stream is identified by its source and its index within that source; an item with alternate
 * versions carries a set per [mediaSourceId].
 */
@Entity(
    tableName = "media_streams",
    indices = [
        Index(value = ["ownerUserId", "itemId", "mediaSourceId", "streamIndex"], unique = true)
    ]
)
data class MediaStreamEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val mediaSourceId: String,
    val streamIndex: Int,
    val streamType: String,
    val codec: String? = null,
    val language: String? = null,
    val displayTitle: String? = null,
    val channels: Int? = null,
    val bitRate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false
)

/**
 * What a track carries. Stored as the server's own token so a stream type from a newer server reads
 * back as unresolved instead of throwing.
 */
enum class MediaStreamType(val wireValue: String) {
    VIDEO("Video"),
    AUDIO("Audio"),
    SUBTITLE("Subtitle");

    companion object {
        fun fromWire(value: String?): MediaStreamType? =
            entries.firstOrNull { it.wireValue == value }
    }
}
