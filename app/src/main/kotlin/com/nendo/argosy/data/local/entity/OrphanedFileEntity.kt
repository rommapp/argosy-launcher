package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "orphaned_files",
    indices = [Index(value = ["path"], unique = true)]
)
data class OrphanedFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val createdAt: Instant = Instant.now()
)
