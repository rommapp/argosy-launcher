package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "firmware",
    indices = [
        Index(value = ["platformId", "fileName"], unique = true),
        Index("platformSlug"),
        Index("rommId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FirmwareEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val platformId: Long,
    val platformSlug: String,
    val rommId: Long,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val md5Hash: String?,
    val sha1Hash: String?,
    val localPath: String? = null,
    val downloadedAt: Instant? = null,
    val lastVerifiedAt: Instant? = null
)
