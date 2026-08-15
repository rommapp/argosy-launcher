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
 * What a media tile plays when its target is a series.
 *
 * [SEQUENTIAL] deliberately stores no pointer. Where the viewer is up to is already known from the
 * library's own watch state, so deriving it means finishing an episode anywhere - fullscreen, a
 * home rail, another client entirely - moves the tile on, and there is no second record of progress
 * that can disagree with the first.
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
    /**
     * What a media tile plays, when the thing it points at is a series rather than one title.
     * Stored as a string so a mode written by a newer build reads back as unresolvable rather than
     * crashing, the same way [targetType] and [virtualType] are treated.
     */
    val mediaPlayMode: String? = null,
    /**
     * The season a [MediaTilePlayMode.SEASON] tile is confined to. Null for every other mode.
     */
    val mediaScopeId: String? = null,
    /**
     * A video or animation on this device that the tile plays instead of a library title. A sibling
     * of [packageName]: it names something outside the media library entirely.
     */
    val mediaFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * What a tile points at. Stored as a string so an unknown value from a newer build reads back as
 * unresolvable instead of throwing.
 */
enum class HomeTileTarget { GAME, COLLECTION, VIRTUAL_COLLECTION, APP, MEDIA }
