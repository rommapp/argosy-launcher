package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "play_sessions",
    indices = [
        Index("gameId"),
        Index("igdbId"),
        Index("startTime"),
        Index("deviceId"),
        Index("userId")
    ]
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String?,
    val gameId: Long,
    val igdbId: Long?,
    val gameTitle: String,
    val platformSlug: String,
    val startTime: Instant,
    val endTime: Instant,
    val continued: Boolean = false,
    val deviceId: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val activePlayMs: Long = 0,
    val standbyMs: Long = 0
)
