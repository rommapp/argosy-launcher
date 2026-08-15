package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class MediaDownloadDbState {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED;

    companion object {
        /**
         * Whether a stored state means a copy is on its way right now. Paused is deliberately not
         * one: nothing is moving, and an indicator that keeps animating over a stopped download
         * says the opposite of what is true.
         */
        fun isActive(stored: String): Boolean =
            stored == QUEUED.name || stored == PREPARING.name || stored == DOWNLOADING.name
    }
}

/**
 * One queued or in-flight media download.
 *
 * Separate from the rom download queue rather than sharing it: that queue is keyed on a game id
 * end to end. This mirrors the Steam queue's shape instead, and its progress is merged into the
 * shared downloads presentation the same way.
 *
 * One row per item per account. Re-downloading the same item at another quality replaces the row,
 * so [quality] is the queue's own record of what is being fetched and the item keeps the record of
 * what it already has. It holds a `MediaDownloadQuality` name: anything but the original file is a
 * transcode the server has to produce first, which is what PREPARING covers before any bytes move.
 */
@Entity(
    tableName = "media_download_queue",
    indices = [
        Index(value = ["ownerUserId", "itemId"], unique = true),
        Index(value = ["state"])
    ]
)
data class MediaDownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val seriesId: String? = null,
    val itemName: String,
    val seriesName: String? = null,
    val itemType: String,
    val quality: String,
    val mediaSourceId: String? = null,
    val playSessionId: String? = null,
    val destinationPath: String? = null,
    val tempFilePath: String? = null,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: String,
    val errorReason: String? = null,
    val createdAt: Instant = Instant.now()
)
