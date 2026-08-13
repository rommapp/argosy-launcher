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
 * The sheet width the server renders trickplay images at, and how the thumbnails are packed into
 * one sheet.
 *
 * These are the server's own defaults. The per-item geometry travels in the item's `Trickplay`
 * field, which this client does not request and its item model does not carry, so a library whose
 * administrator changed the trickplay interval or tile grid will preview at the wrong offsets until
 * that field is read. Getting it wrong costs a scrub preview that is off by a few seconds; it
 * cannot affect playback.
 */
const val TRICKPLAY_TILE_WIDTH = 320
const val TRICKPLAY_COLUMNS = 10
const val TRICKPLAY_ROWS = 10
const val TRICKPLAY_INTERVAL_MS = 10_000L

/**
 * One thumbnail: which sheet it is on and where in that sheet it sits.
 */
data class TrickplayTile(
    val url: String,
    val column: Int,
    val row: Int
)

/**
 * Locates the thumbnail covering a position. Thumbnails are laid out in reading order within a
 * sheet, and sheets follow one another in time.
 */
fun trickplayTileFor(url: (Int) -> String, positionMs: Long): TrickplayTile {
    val perSheet = TRICKPLAY_COLUMNS * TRICKPLAY_ROWS
    val thumbnailIndex = (positionMs / TRICKPLAY_INTERVAL_MS).toInt().coerceAtLeast(0)
    val sheetIndex = thumbnailIndex / perSheet
    val withinSheet = thumbnailIndex % perSheet
    return TrickplayTile(
        url = url(sheetIndex),
        column = withinSheet % TRICKPLAY_COLUMNS,
        row = withinSheet / TRICKPLAY_COLUMNS
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
                .size(width = width * TRICKPLAY_COLUMNS, height = height * TRICKPLAY_ROWS)
                .offset(x = -width * tile.column, y = -height * tile.row)
        )
    }
}
