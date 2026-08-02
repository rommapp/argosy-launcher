package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * How a page divides into cells. The lane count runs across the page's short edge, so the same
 * curated page keeps its proportions on a tall handheld and a wide television rather than being
 * authored for one and stretched on the other.
 */
data class CustomGridShape(val columns: Int, val rows: Int) {
    companion object {
        fun forSize(size: IntSize, laneCount: Int, gapPx: Float): CustomGridShape {
            val metrics = customGridMetrics(size, laneCount, gapPx)
            return CustomGridShape(metrics.columns, metrics.rows)
        }
    }
}

/**
 * A page's geometry in pixels. Cells are square, so a tile reads the same whichever way it spans,
 * and whatever the square grid cannot fill becomes margin: the block is centred rather than stretched
 * to the edges, which keeps the spacing between cells equal to the spacing around them.
 *
 * The two margins are computed separately because a page rarely divides evenly on both axes; forcing
 * them equal would push the grid off centre on one of them.
 *
 * The cell is sized as though there were one extra lane's worth of focus growth to fit, because a
 * focused tile scales about its centre and the outermost lane would otherwise grow over whatever
 * sits beyond the grid. Reserving the room rather than clipping it keeps the cursor whole: clipping
 * cuts the one cell that most has to read clearly.
 */
data class CustomGridMetrics(
    val columns: Int,
    val rows: Int,
    val cellPx: Float,
    val gapPx: Float,
    val offsetXPx: Float,
    val offsetYPx: Float
)

fun customGridMetrics(size: IntSize, laneCount: Int, gapPx: Float): CustomGridMetrics {
    val lanes = laneCount.coerceAtLeast(1)
    if (size.width <= 0 || size.height <= 0) {
        return CustomGridMetrics(lanes, lanes, 0f, gapPx, 0f, 0f)
    }
    val widthIsShort = size.width <= size.height
    val shortEdge = if (widthIsShort) size.width else size.height
    val longEdge = if (widthIsShort) size.height else size.width
    val overhangLanes = (ComponentDefaults.Focus.scaleFocused - 1f).coerceAtLeast(0f)
    val cell = ((shortEdge - gapPx * (lanes - 1)) / (lanes + overhangLanes)).coerceAtLeast(1f)
    val longOverhang = cell * overhangLanes
    val alongLanes = (((longEdge - longOverhang + gapPx) / (cell + gapPx)).toInt()).coerceAtLeast(1)
    val columns = if (widthIsShort) lanes else alongLanes
    val rows = if (widthIsShort) alongLanes else lanes
    val gridWidth = columns * cell + gapPx * (columns - 1)
    val gridHeight = rows * cell + gapPx * (rows - 1)
    return CustomGridMetrics(
        columns = columns,
        rows = rows,
        cellPx = cell,
        gapPx = gapPx,
        offsetXPx = ((size.width - gridWidth) / 2f).coerceAtLeast(0f),
        offsetYPx = ((size.height - gridHeight) / 2f).coerceAtLeast(0f)
    )
}

/**
 * What a tile shows once resolved. The grid does not know how to load a cover, so the host supplies
 * one of these per tile and an unresolvable target is simply absent from the map.
 */
data class CustomGridTileContent(
    val game: com.nendo.argosy.ui.screens.home.HomeGameUi?,
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
    laneCount: Int,
    focusedCell: GridCell,
    onCellTap: (GridCell) -> Unit,
    onShapeResolved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    showEmptyCells: Boolean = true
) {
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val gap = Dimens.spacingSm
    val gapPx = with(density) { gap.toPx() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { measured = it }
    ) {
        if (measured.width <= 0 || measured.height <= 0) return@Box
        val metrics = customGridMetrics(measured, laneCount, gapPx)
        LaunchedEffect(metrics.columns, metrics.rows) {
            onShapeResolved(metrics.columns, metrics.rows)
        }
        val cellSize = with(density) { metrics.cellPx.toDp() }
        val originX = with(density) { metrics.offsetXPx.toDp() }
        val originY = with(density) { metrics.offsetYPx.toDp() }
        val occupied = tiles.flatMap { tile ->
            (tile.rect.columnIndex..tile.rect.lastColumn).flatMap { column ->
                (tile.rect.rowIndex..tile.rect.lastRow).map { row -> column to row }
            }
        }.toSet()

        if (showEmptyCells) {
            for (column in 0 until metrics.columns) {
                for (row in 0 until metrics.rows) {
                    if (column to row in occupied) continue
                    CustomGridCellBox(
                        rect = TileRect(column, row),
                        cellSize = cellSize,
                        gap = gap,
                        originX = originX,
                        originY = originY,
                        isFocused = focusedCell.columnIndex == column && focusedCell.rowIndex == row,
                        onClick = { onCellTap(GridCell(column, row)) },
                        content = null
                    )
                }
            }
        }

        tiles.forEach { tile ->
            CustomGridCellBox(
                rect = tile.rect,
                cellSize = cellSize,
                gap = gap,
                originX = originX,
                originY = originY,
                isFocused = tile.rect.covers(focusedCell.columnIndex, focusedCell.rowIndex),
                onClick = { onCellTap(GridCell(tile.rect.columnIndex, tile.rect.rowIndex)) },
                content = contentFor(tile)
            )
        }
    }
}

/**
 * One cell of the grid. A tile that resolves to a game renders as a [GameCard], so the cursor,
 * corner radius, border style and glow are the box art container's rather than a second look that
 * has to be kept in step with it. An empty cell borrows the same corner radius so the two read as
 * the same family.
 */
@Composable
private fun CustomGridCellBox(
    rect: TileRect,
    cellSize: Dp,
    gap: Dp,
    originX: Dp,
    originY: Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    content: CustomGridTileContent?
) {
    val theme = LocalArgosyTheme.current
    val boxArtStyle = com.nendo.argosy.ui.theme.LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)
    val stride = cellSize + gap
    val width = cellSize * rect.columnSpan + gap * (rect.columnSpan - 1)
    val height = cellSize * rect.rowSpan + gap * (rect.rowSpan - 1)
    val placement = Modifier
        .offset(
            x = originX + stride * rect.columnIndex,
            y = originY + stride * rect.rowIndex
        )
        .size(width, height)

    val game = content?.game
    if (game != null) {
        GameCard(
            game = game,
            isFocused = isFocused,
            focusScale = focusScaleForSpan(rect),
            showPlatformBadge = false,
            modifier = placement.clickableNoFocus(onClick = onClick)
        )
        return
    }

    Box(
        modifier = placement
            .clip(shape)
            .then(
                if (content == null) {
                    Modifier.border(boxArtStyle.borderThicknessDp, theme.surfaceRaised, shape)
                } else {
                    Modifier.background(theme.surfaceRaised)
                }
            )
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            content == null -> if (isFocused) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
            else -> Text(
                text = content.label,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                modifier = Modifier.padding(Dimens.spacingSm)
            )
        }
    }
}

/**
 * The page past the last one. It is a single tile filling the grid rather than an empty page of
 * cells, because an empty page and the offer to make one look identical otherwise, and only one of
 * them does anything when you press A.
 */
@Composable
fun CustomGridAddPage(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spacingLg)
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = if (isFocused) theme.focusAccent else theme.textDim,
                modifier = Modifier.size(Dimens.iconXl)
            )
            Text(
                text = "Add a page",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) theme.textPrimary else theme.textDim
            )
        }
    }
}

/**
 * Page position for a curated grid: one dot per page plus a distinct stub for the page that does not
 * exist yet. Sections have names worth listing; pages are just positions, so a count and a cursor is
 * the whole story.
 */
@Composable
fun CustomGridPageDots(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(Dimens.spacingSm)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage) theme.focusAccent else theme.textDim.copy(alpha = DOT_IDLE_ALPHA)
                    )
            )
        }
        Box(
            modifier = Modifier
                .size(Dimens.spacingSm)
                .clip(CircleShape)
                .border(
                    Dimens.borderThin,
                    if (currentPage == pageCount) theme.focusAccent else theme.textDim.copy(alpha = DOT_IDLE_ALPHA),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = if (currentPage == pageCount) theme.focusAccent else theme.textDim,
                modifier = Modifier.size(Dimens.spacingSm)
            )
        }
    }
}

private const val DOT_IDLE_ALPHA = 0.35f

/**
 * Focus scale for a tile covering [rect]. A scale is a proportion, so applying the same one to a
 * tile spanning three cells moves its edge three times as far as a single cell's. Dividing by the
 * longest span keeps the growth a constant distance whatever the tile's size, which is both what a
 * cursor should look like and what the page reserved room for.
 */
private fun focusScaleForSpan(rect: TileRect): Float {
    val span = maxOf(rect.columnSpan, rect.rowSpan).coerceAtLeast(1)
    return 1f + (ComponentDefaults.Focus.scaleFocused - 1f) / span
}
