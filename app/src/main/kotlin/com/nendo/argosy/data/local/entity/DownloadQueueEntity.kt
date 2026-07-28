package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "download_queue",
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["state"]),
        Index(value = ["discId"]),
        Index(value = ["gameFileId"]),
        Index(value = ["ownerUserId"])
    ]
)
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val discId: Long? = null,
    val discNumber: Int? = null,
    val gameFileId: Long? = null,
    val fileCategory: String? = null,
    val fileName: String,
    val gameTitle: String,
    val gameFolderName: String? = null,
    val platformSlug: String,
    val coverPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: String,
    val errorReason: String?,
    val tempFilePath: String?,
    val createdAt: Instant = Instant.now(),
    val isMultiFileRom: Boolean = false,
    val selectedFileIds: String? = null,
    /**
     * RomM user who queued the download. The rom file itself is device-global and one copy serves
     * every account, so this is attribution for removal and pending-work accounting, not a
     * visibility filter on the queue.
     */
    val ownerUserId: Long? = null
)
