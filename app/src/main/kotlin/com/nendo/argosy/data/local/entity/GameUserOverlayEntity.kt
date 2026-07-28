package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One RomM account's view of one library row.
 *
 * `games` is library truth: one row per rom, never duplicated per account. Everything a
 * particular account thinks about that rom lives here instead, keyed on the account's RomM
 * user id. The matching columns still exist on `games` as a materialised copy of whichever
 * account is active, so the ~125 existing `FROM games` queries keep working unchanged; those
 * columns are a cache and this table is the record.
 *
 * [isMember] is the visibility mask. A rom the server stops returning for this account is a
 * membership drop, not a deletion: the row stays for every other account that can still see
 * it. Absence of a row is read as membership, so a library that predates the account keeps
 * working until the first write seeds it.
 */
@Entity(
    tableName = "game_user_overlay",
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
data class GameUserOverlayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long,
    val gameId: Long,
    val isMember: Boolean = true,
    val serverHidden: Boolean = false,
    val isFavorite: Boolean = false,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val completion: Int = 0,
    val status: String? = null,
    val backlogged: Boolean = false,
    val nowPlaying: Boolean = false,
    val playCount: Int = 0,
    val playTimeMinutes: Int = 0,
    val lastPlayed: Instant? = null,
    val earnedAchievementCount: Int = 0
)
