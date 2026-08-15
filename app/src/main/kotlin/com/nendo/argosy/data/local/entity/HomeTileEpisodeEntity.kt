package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One episode a media tile was told to play, and where it sits in that tile's run.
 *
 * [orderIndex] is the order the viewer picked, which is not always broadcast order. [tileId] is not
 * a foreign key, matching the tile's own target; rows are cleared with the tile instead.
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
