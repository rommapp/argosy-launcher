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
        Index("userId"),
        Index("ownerUserId")
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
    val standbyMs: Long = 0,
    /**
     * RomM user id the session belongs to. Separate from [userId], which is the Argosy Social id:
     * the two identities are independent, a device can be linked to one and not the other, and
     * the RomM ingest must not upload another account's sessions under whoever is connected.
     */
    val ownerUserId: Long? = null
)
