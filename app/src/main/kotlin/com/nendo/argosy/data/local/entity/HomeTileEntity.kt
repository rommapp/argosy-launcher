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
 *
 * Anchors are indexed but not unique. Two tiles may share a cell while one is being dragged over
 * another, and that state is written as it happens so a move survives the app being killed mid
 * arrangement; the overlap is resolved when the arrangement is committed, not forbidden by the
 * schema.
 */
/**
 * What a media tile plays when its target is a series. [SEQUENTIAL] stores no pointer; it derives
 * position from the library's watch state.
 */
enum class MediaTilePlayMode {
    SINGLE,
    RANDOM,
    SEQUENTIAL,
    SEASON,
    PLAYLIST;

    companion object {
        fun fromStored(value: String?): MediaTilePlayMode? =
            entries.find { it.name == value }
    }
}

@Entity(
    tableName = "home_tiles",
    indices = [
        Index(value = ["ownerUserId", "pageIndex"]),
        Index(value = ["ownerUserId", "pageIndex", "columnIndex", "rowIndex"])
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
    val mediaItemId: String? = null,
    val mediaPlayMode: String? = null,
    val mediaScopeId: String? = null,
    val mediaFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * What a tile points at. Stored as a string so an unknown value from a newer build reads back as
 * unresolvable instead of throwing.
 */
enum class HomeTileTarget { GAME, COLLECTION, VIRTUAL_COLLECTION, APP, MEDIA }
