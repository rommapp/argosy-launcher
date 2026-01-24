package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "core_versions")
data class CoreVersionEntity(
    @PrimaryKey
    val coreId: String,
    val installedVersion: String? = null,
    val latestVersion: String? = null,
    val installedAt: Instant? = null,
    val lastCheckedAt: Instant? = null,
    val updateAvailable: Boolean = false
)
