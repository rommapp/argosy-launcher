package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.ui.components.MediaTileOption

/**
 * How many titles a batch may be missing before its size is worth stating. Below this the count on
 * its own is the whole story; above it the reader is choosing to fill a card and deserves a figure.
 */
const val MEDIA_TILE_SIZE_WARNING_THRESHOLD = 5

/**
 * What a batch chosen for a tile would cost.
 *
 * [approximateSize] is a phrase rather than a number of bytes because it is never exact and the
 * caller must not be able to present it as though it were: a queued download starts with no size at
 * all, and until the server has been asked what it will hand over the figure is worked back from a
 * bitrate. A batch nothing can be said about answers null.
 */
data class MediaTileDownloadPlan(
    val missingIds: List<String> = emptyList(),
    val approximateSize: String? = null
)

/**
 * The library reads the tile picker needs, kept behind an interface so the grid coordinator stays
 * free of the media stack and a surface with no media server can simply not supply one.
 */
interface MediaTileCatalog {
    suspend fun seasons(seriesId: String): List<MediaTileOption>

    /**
     * The episodes of one season, or of the whole series when [seasonId] is null.
     */
    suspend fun episodes(seriesId: String, seasonId: String?): List<MediaTileOption>

    suspend fun planDownloads(itemIds: List<String>): MediaTileDownloadPlan

    suspend fun enqueue(itemIds: List<String>)
}
