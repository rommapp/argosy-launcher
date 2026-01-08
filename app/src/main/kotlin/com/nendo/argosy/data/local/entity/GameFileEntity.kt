package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "game_files",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("gameId"),
        Index(value = ["rommFileId"], unique = true)
    ]
)
data class GameFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommFileId: Long,
    val romId: Long,
    val fileName: String,
    val filePath: String,
    val category: String,
    val fileSize: Long,
    val localPath: String? = null,
    val downloadedAt: Instant? = null
)
