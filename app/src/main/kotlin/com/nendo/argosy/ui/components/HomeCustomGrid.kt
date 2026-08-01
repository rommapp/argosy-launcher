package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * How a page divides into cells. The lane count runs across the page's short edge, so the same
 * curated page keeps its proportions on a tall handheld and a wide television rather than being
 * authored for one and stretched on the other.
 */
data class CustomGridShape(val columns: Int, val rows: Int) {
    companion object {
        fun forSize(size: IntSize, laneCount: Int): CustomGridShape {
            val lanes = laneCount.coerceAtLeast(1)
            if (size.width <= 0 || size.height <= 0) return CustomGridShape(lanes, lanes)
            return if (size.height >= size.width) {
                val cell = size.width.toFloat() / lanes
                CustomGridShape(lanes, ((size.height / cell).toInt()).coerceAtLeast(1))
            } else {
                val cell = size.height.toFloat() / lanes
                CustomGridShape(((size.width / cell).toInt()).coerceAtLeast(1), lanes)
            }
        }
    }
}

/**
 * What a tile shows once resolved. The grid does not know how to load a cover, so the host supplies
 * one of these per tile and an unresolvable target is simply absent from the map.
 */
data class CustomGridTileContent(
    val coverPath: String?,
    val label: String,
    val isMissing: Boolean = false
)

/**
 * One page of the custom grid. Tiles are placed absolutely from their anchor and span, because a
 * lazy grid can only lay out uniform cells and a curated page is the opposite of uniform.
 *
 * Owns no focus; [focusedCell] is the caller's and every empty cell is a legal place for it.
 */
@Composable
fun HomeCustomGridPage(
    tiles: List<HomeTile>,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    shape: CustomGridShape,
    focusedCell: GridCell,
    onCellTap: (GridCell) -> Unit,
    onSizeResolved: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyCells: Boolean = true
) {
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                measured = it
                onSizeResolved(it)
            }
    ) {
        if (measured.width <= 0 || measured.height <= 0) return@Box
        val gap = Dimens.spacingSm
        val cellWidth = with(density) { (measured.width / shape.columns).toDp() }
        val cellHeight = with(density) { (measured.height / shape.rows).toDp() }
        val occupied = tiles.flatMap { tile ->
            (tile.rect.columnIndex..tile.rect.lastColumn).flatMap { column ->
                (tile.rect.rowIndex..tile.rect.lastRow).map { row -> column to row }
            }
        }.toSet()

        if (showEmptyCells) {
            for (column in 0 until shape.columns) {
                for (row in 0 until shape.rows) {
                    if (column to row in occupied) continue
                    CustomGridSlot(
                        rect = TileRect(column, row),
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        gap = gap,
                        isFocused = focusedCell.columnIndex == column && focusedCell.rowIndex == row,
                        onClick = { onCellTap(GridCell(column, row)) }
                    )
                }
            }
        }

        tiles.forEach { tile ->
            val content = contentFor(tile)
            CustomGridTile(
                tile = tile,
                content = content,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                gap = gap,
                isFocused = tile.rect.covers(focusedCell.columnIndex, focusedCell.rowIndex),
                onClick = {
                    onCellTap(GridCell(tile.rect.columnIndex, tile.rect.rowIndex))
                }
            )
        }
    }
}

@Composable
private fun CustomGridSlot(
    rect: TileRect,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = Modifier
            .offset(x = cellWidth * rect.columnIndex, y = cellHeight * rect.rowIndex)
            .size(cellWidth - gap, cellHeight - gap)
            .clip(shape)
            .border(Dimens.borderThin, theme.surfaceRaised, shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isFocused) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}

@Composable
private fun CustomGridTile(
    tile: HomeTile,
    content: CustomGridTileContent?,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = Modifier
            .offset(x = cellWidth * tile.rect.columnIndex, y = cellHeight * tile.rect.rowIndex)
            .size(cellWidth * tile.rect.columnSpan - gap, cellHeight * tile.rect.rowSpan - gap)
            .clip(shape)
            .background(theme.surfaceRaised)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Tile,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            content == null || content.isMissing -> Text(
                text = "Missing",
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim
            )
            else -> CustomGridTileArt(content = content)
        }
    }
}

@Composable
private fun CustomGridTileArt(content: CustomGridTileContent) {
    val theme = LocalArgosyTheme.current
    val cover = com.nendo.argosy.ui.common.rememberFileImageModel(content.coverPath)
    if (cover != null) {
        coil.compose.AsyncImage(
            model = cover,
            contentDescription = content.label,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(
            text = content.label,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary,
            modifier = Modifier.padding(Dimens.spacingSm)
        )
    }
}
