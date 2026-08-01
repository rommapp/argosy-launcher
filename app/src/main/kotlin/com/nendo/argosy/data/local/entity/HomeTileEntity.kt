package com.nendo.argosy.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One placed tile on one page of the custom home grid.
 *
 * [columnIndex] and [rowIndex] are the tile's top-left cell, and it covers [columnSpan] by
 * [rowSpan] cells from there. Anchoring at the corner rather than storing every cell keeps a resize
 * to one row change and leaves the page's occupancy derivable rather than duplicated.
 *
 * The target is deliberately not a foreign key. A tile outlives the thing it points at: a game
 * erased by a sync, a collection deleted on the server or an app uninstalled would all cascade the
 * tile away and silently rearrange a page the user arranged by hand. Resolution happens on read
 * instead, so an unresolvable tile can be reported rather than having already vanished.
 *
 * [ownerUserId] is null for a tile placed before any account existed; those count for whoever is
 * signed in, matching how the other user-scoped tables treat pre-account rows.
 */
@Entity(
    tableName = "home_tiles",
    indices = [
        Index(value = ["ownerUserId", "pageIndex"]),
        Index(value = ["ownerUserId", "pageIndex", "columnIndex", "rowIndex"], unique = true)
    ]
)
data class HomeTileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerUserId: Long?,
    val pageIndex: Int,
    val columnIndex: Int,
    val rowIndex: Int,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val targetType: String,
    val gameId: Long? = null,
    val collectionId: Long? = null,
    val virtualType: String? = null,
    val virtualName: String? = null,
    val packageName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * What a tile points at. Stored as a string so an unknown value from a newer build reads back as
 * unresolvable instead of throwing.
 */
enum class HomeTileTarget { GAME, COLLECTION, VIRTUAL_COLLECTION, APP }
