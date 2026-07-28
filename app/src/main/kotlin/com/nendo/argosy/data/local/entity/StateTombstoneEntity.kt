package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "state_tombstones",
    indices = [
        Index(value = ["rommSaveId", "ownerUserId"], unique = true),
        Index("gameId"),
        Index("ownerUserId")
    ]
)
data class StateTombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    val rommSaveId: Long,
    val createdAt: Instant,
    /**
     * RomM user who deleted the server state. The owner is part of the unique index because a
     * tombstone unique on the server save id alone lets one account's delete suppress another's
     * state, which then resurrects on their next sync.
     */
    val ownerUserId: Long? = null
)
