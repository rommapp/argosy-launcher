package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One account's choice to hide one rom. Row existence is the fact; there is no boolean.
 *
 * The peer of RomM's `rom_user.hidden`, and deliberately not the same thing as
 * [GameUserOverlayEntity.serverHidden], which is the peer of RomM's `hidden_entities` and is
 * imposed by an admin. A rom is invisible when either says so, but they are set, cleared and
 * synced by different things and never merge.
 *
 * The table is sparse by design: most roms are not hidden, so most games have no row here.
 * [ownerUserId] is null for a hide made before any account existed; those rows count for whoever
 * is signed in, and are cleared when that rom is unhidden.
 */
@Entity(
    tableName = "user_roms_hidden",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ownerUserId", "gameId"], unique = true),
        Index("gameId"),
        Index("ownerUserId")
    ]
)
data class UserRomHiddenEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long?,
    val gameId: Long
)
