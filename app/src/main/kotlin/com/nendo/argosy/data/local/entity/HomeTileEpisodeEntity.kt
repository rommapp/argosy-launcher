package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One episode a media tile was told to play, and where it sits in that tile's run.
 *
 * A chosen set is a list rather than a value, so it lives beside the tile instead of in a column on
 * it. [orderIndex] is stored because the run is the order the viewer picked, which is not always
 * broadcast order and cannot be recovered from the episodes themselves.
 *
 * [tileId] is not a foreign key, for the reason the tile's own target is not one: a cascade would
 * quietly rewrite a page the user arranged by hand. Rows orphaned by a deleted tile are cleared
 * when the tile is deleted, in the same transaction.
 */
@Entity(
    tableName = "home_tile_episodes",
    indices = [
        Index(value = ["tileId"]),
        Index(value = ["tileId", "itemId"], unique = true)
    ]
)
data class HomeTileEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tileId: Long,
    val itemId: String,
    val orderIndex: Int
)
