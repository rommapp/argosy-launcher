package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * One account's watch state for one item: where it was left, whether it is finished, whether it is
 * a favourite.
 *
 * Kept apart from [MediaItemEntity] because the two have opposite lifetimes. Item metadata is
 * server-owned and replaced wholesale by a sync; watch state is written locally, possibly offline,
 * and must survive a sync that rewrites or drops the item row -- which is also why there is no
 * foreign key to media_items. A position recorded while offline is the only copy that exists.
 *
 * [needsSync] marks a local write the server has not been told about; the queue drains on
 * reconnect and the last write wins. It is one position and three flags and is deliberately not
 * reconciled.
 *
 * [playbackPositionTicks] is in the server's own 100ns tick unit, not milliseconds.
 */
@Entity(
    tableName = "media_user_data",
    indices = [
        Index(value = ["ownerUserId", "itemId"], unique = true),
        Index(value = ["ownerUserId", "needsSync"])
    ]
)
data class MediaUserDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: String,
    val itemId: String,
    val playbackPositionTicks: Long = 0,
    val playedPercentage: Double? = null,
    val played: Boolean = false,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Instant? = null,
    val needsSync: Boolean = false,
    val updatedAt: Instant = Instant.now()
)
