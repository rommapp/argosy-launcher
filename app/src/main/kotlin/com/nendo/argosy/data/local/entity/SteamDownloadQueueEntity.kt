package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

enum class SteamDownloadDbState {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(
    tableName = "steam_download_queue",
    indices = [
        Index(value = ["appId"], unique = true),
        Index(value = ["state"])
    ]
)
data class SteamDownloadQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appId: Long,
    val gameName: String,
    val coverPath: String?,
    val installDir: String?,
    val installPath: String?,
    val totalBytes: Long,
    val bytesDownloaded: Long,
    val state: String,
    val errorReason: String?,
    val createdAt: Instant = Instant.now()
)
