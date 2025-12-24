package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "state_cache",
    indices = [
        Index("gameId"),
        Index("cachedAt"),
        Index(
            value = ["gameId", "emulatorId", "slotNumber", "channelName"],
            unique = true
        )
    ]
)
data class StateCacheEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val platformSlug: String,
    val emulatorId: String,
    val slotNumber: Int,
    val channelName: String? = null,
    val cachedAt: Instant,
    val stateSize: Long,
    val cachePath: String,
    val screenshotPath: String? = null,
    val coreId: String? = null,
    val coreVersion: String? = null,
    val isLocked: Boolean = false,
    val note: String? = null
)
