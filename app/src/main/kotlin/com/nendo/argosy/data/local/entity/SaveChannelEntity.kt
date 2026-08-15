package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A save slot this device knows about, whether or not anything has been saved into it yet.
 *
 * Slots were previously only ever inferred from the saves that existed, which meant a slot created
 * and not yet used had no way to exist: it was announced, then gone the next time the list was
 * built, and the next save went somewhere else. This table is what an empty slot is.
 *
 * Deliberately local. An empty slot holds no data worth carrying to another device, and the server
 * has no concept to map it onto; slots become visible elsewhere when a save is written into them,
 * which is the point at which there is something to carry.
 *
 * [isActive] records which slot a game will save into, and is the answer for a slot with no save to
 * hang that fact on. At most one row per game and owner carries it.
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
