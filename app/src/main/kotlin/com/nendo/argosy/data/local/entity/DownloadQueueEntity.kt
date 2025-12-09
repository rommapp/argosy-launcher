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
        Index(value = ["discId"])
    ]
)
data class DownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommId: Long,
    val discId: Long? = null,
    val fileName: String,
    val gameTitle: String,
    val platformSlug: String,
    val coverPath: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: String,
    val errorReason: String?,
    val tempFilePath: String?,
    val createdAt: Instant = Instant.now()
)
