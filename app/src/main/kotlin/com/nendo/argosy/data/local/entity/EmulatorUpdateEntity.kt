package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "emulator_updates")
data class EmulatorUpdateEntity(
    @PrimaryKey
    val emulatorId: String,

    val latestVersion: String,
    val installedVersion: String?,
    val downloadUrl: String,
    val assetName: String,
    val assetSize: Long,
    val checkedAt: Instant,

    val installedVariant: String? = null,
    val hasUpdate: Boolean = false
)
