package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A save slot this device knows about, whether or not anything has been saved into it yet.
 *
 * Local only; the server has no concept of an empty slot. [isActive] records which slot a game
 * saves into, for slots with no save row to carry it. At most one row per game and owner.
 */
@Entity(
    tableName = "save_channels",
    indices = [
        Index(value = ["ownerUserId", "gameId", "channelName"], unique = true),
        Index(value = ["ownerUserId", "gameId"])
    ]
)
data class SaveChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long?,
    val gameId: Long,
    val channelName: String,
    val isActive: Boolean = false,
    val createdAt: Instant = Instant.now()
)
