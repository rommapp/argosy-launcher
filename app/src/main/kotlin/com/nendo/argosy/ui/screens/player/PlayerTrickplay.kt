package com.nendo.argosy.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * The scrub thumbnails one item actually has, taken from that item's own trickplay manifest.
 *
 * There is no default worth keeping here. The geometry is per library and per item - an
 * administrator sets the interval and the tile grid - and the manifest is also the only thing that
 * says whether any thumbnails were generated at all, which the server version cannot answer.
 */
data class PlayerTrickplay(
    val mediaSourceId: String,
    val thumbnailWidth: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Long,
    val thumbnailCount: Int
)

/**
 * One thumbnail: which sheet it is on, where in that sheet it sits, and how that sheet is packed.
 */
data class TrickplayTile(
    val url: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int
)

/**
 * Locates the thumbnail covering a position. Thumbnails are laid out in reading order within a
 * sheet, and sheets follow one another in time.
 *
 * A position past the last thumbnail the server generated has no tile rather than a tile that
 * resolves to nothing: a sheet that does not exist answers 404, and a preview box waiting on one
 * stays empty for as long as the user holds the scrubber there.
 */
fun trickplayTileFor(
    trickplay: PlayerTrickplay,
    url: (Int) -> String,
    positionMs: Long
): TrickplayTile? {
    val perSheet = trickplay.columns * trickplay.rows
    if (perSheet <= 0 || trickplay.intervalMs <= 0) return null
    val thumbnailIndex = (positionMs / trickplay.intervalMs).toInt().coerceAtLeast(0)
    if (trickplay.thumbnailCount > 0 && thumbnailIndex >= trickplay.thumbnailCount) return null
    val withinSheet = thumbnailIndex % perSheet
    return TrickplayTile(
        url = url(thumbnailIndex / perSheet),
        column = withinSheet % trickplay.columns,
        row = withinSheet / trickplay.columns,
        columns = trickplay.columns,
        rows = trickplay.rows
    )
}

/**
 * Draws one thumbnail out of a sheet by laying the whole sheet down at thumbnail scale and showing
 * only the cell that matters. There is no crop-on-load in the image pipeline, and the sheet is
 * fetched once and reused for the hundred thumbnails around it, which is the point of sheets.
 */
@Composable
fun TrickplayThumbnail(
    tile: TrickplayTile,
    authorizationHeader: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(tile.url)
        .apply { authorizationHeader?.let { addHeader("Authorization", it) } }
        .crossfade(false)
        .build()

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clipToBounds()
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(width = width * tile.columns, height = height * tile.rows)
                .offset(x = -width * tile.column, y = -height * tile.row)
        )
    }
}
