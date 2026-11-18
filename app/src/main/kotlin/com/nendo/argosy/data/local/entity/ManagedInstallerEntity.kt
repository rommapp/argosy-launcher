package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "managed_installers",
    indices = [
        Index(value = ["repoOwner", "repoName"], unique = true)
    ]
)
data class ManagedInstallerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val repoOwner: String,
    val repoName: String,
    val displayName: String,
    val packageName: String? = null,
    val assetVariant: String? = null,
    val tagAtInstall: String? = null,
    val latestSeenTag: String? = null,
    val lastCheckedAt: Instant? = null,
    val locked: Boolean = false,
    val sortOrder: Int = 0,
    val includePrereleases: Boolean = false
)
