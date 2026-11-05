package com.nendo.argosy.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.DEFAULT_LANE_COUNT
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.ResolvedGridShape
import com.nendo.argosy.domain.model.isFixed
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.components.MediaDownloadBadge
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlinx.coroutines.launch
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * A page's geometry in pixels, the block centred with [offsetXPx] and [offsetYPx] of margin on each
 * side. Cells are square unless both axes are fixed.
 */
data class CustomGridMetrics(
    val columns: Int,
    val rows: Int,
    val cellWidthPx: Float,
    val cellHeightPx: Float,
    val gapPx: Float,
    val offsetXPx: Float,
    val offsetYPx: Float
) {
    val shape: CustomGridShape get() = CustomGridShape(columns, rows)
}

/**
 * Widest a cell may be against its height, and the reverse, when both axes are fixed.
 */
const val MAX_CELL_STRETCH = 4f / 3f

/**
 * The single page geometry for the home grid and the settings preview.
 *
 * A non-fixed axis takes as many square cells as fit. Two fixed axes fit the whole block on the page,
 * cells stretching up to [MAX_CELL_STRETCH] either way, with the rest left as centred margin. Each
 * axis reserves [overhangLanes] of a cell beyond the block for a focused tile's growth.
 */
fun customGridMetrics(
    width: Float,
    height: Float,
    columns: GridAxis,
    rows: GridAxis,
    gapPx: Float,
    overhangLanes: Float = (ComponentDefaults.Focus.scaleFocused - 1f).coerceAtLeast(0f)
): CustomGridMetrics {
    val bothOpen = !columns.isFixed && !rows.isFixed
    val fixedColumns = (columns as? GridAxis.Fixed)?.count?.coerceAtLeast(1)
    val fixedRows = ((if (bothOpen) GridAxis.Fixed(DEFAULT_LANE_COUNT) else rows) as? GridAxis.Fixed)
        ?.count?.coerceAtLeast(1)
    if (width <= 0f || height <= 0f) {
        return CustomGridMetrics(
            columns = fixedColumns ?: fixedRows ?: 1,
            rows = fixedRows ?: fixedColumns ?: 1,
            cellWidthPx = 0f,
            cellHeightPx = 0f,
            gapPx = gapPx,
            offsetXPx = 0f,
            offsetYPx = 0f
        )
    }
    val overhang = overhangLanes.coerceAtLeast(0f)
    val extent = { edge: Float, count: Int ->
        ((edge - gapPx * (count - 1)) / (count + overhang)).coerceAtLeast(1f)
    }
    val fitting = { edge: Float, cell: Float ->
        ((edge - cell * overhang + gapPx) / (cell + gapPx)).toInt().coerceAtLeast(1)
    }
    val columnCount: Int
    val rowCount: Int
    val cellWidth: Float
    val cellHeight: Float
    when {
        fixedColumns != null && fixedRows != null -> {
            val across = extent(width, fixedColumns)
            val down = extent(height, fixedRows)
            columnCount = fixedColumns
            rowCount = fixedRows
            cellWidth = minOf(across, down * MAX_CELL_STRETCH)
            cellHeight = minOf(down, across * MAX_CELL_STRETCH)
        }
        fixedColumns != null -> {
            val cell = minOf(extent(width, fixedColumns), extent(height, 1))
            columnCount = fixedColumns
            rowCount = fitting(height, cell)
            cellWidth = cell
            cellHeight = cell
        }
        else -> {
            val lanes = fixedRows ?: DEFAULT_LANE_COUNT
            val cell = minOf(extent(height, lanes), extent(width, 1))
            columnCount = fitting(width, cell)
            rowCount = lanes
            cellWidth = cell
            cellHeight = cell
        }
    }
    val gridWidth = columnCount * cellWidth + gapPx * (columnCount - 1)
    val gridHeight = rowCount * cellHeight + gapPx * (rowCount - 1)
    return CustomGridMetrics(
        columns = columnCount,
        rows = rowCount,
        cellWidthPx = cellWidth,
        cellHeightPx = cellHeight,
        gapPx = gapPx,
        offsetXPx = ((width - gridWidth) / 2f).coerceAtLeast(0f),
        offsetYPx = ((height - gridHeight) / 2f).coerceAtLeast(0f)
    )
}

/**
 * What a tile shows once resolved. The grid does not know how to load a cover, so the host supplies
 * one of these per tile and an unresolvable target is simply absent from the map.
 */
data class CustomGridTileContent(
    val game: com.nendo.argosy.ui.screens.home.HomeGameUi?,
    val label: String,
    val media: com.nendo.argosy.ui.screens.home.HomeMediaUi? = null,
    val isMissing: Boolean = false,
    val packageName: String? = null,
    val coverPath: String? = null,
    val posterUrl: String? = null,
    val subtitle: String? = null,
    val stats: List<TileStat> = emptyList(),
    /**
     * Marks a tile that plays one game out of a collection, so a queue is distinguishable from the
     * plain game tile it otherwise looks exactly like.
     */
    val isCollectionQueue: Boolean = false,
    /**
     * Marks a random game tile, which otherwise looks exactly like the game it happens to be
     * showing. The die badge and the re-roll animation hang off this.
     */
    val isRandom: Boolean = false,
    /**
     * Marks the continue tile, which likewise looks exactly like the game it last showed.
     */
    val isContinue: Boolean = false,
    val isLibraryLink: Boolean = false,
    /**
     * What a RetroAchievements tile draws, in place of every other field here. Present only on
     * that tile, which owns its own layout in each cell shape rather than borrowing one.
     */
    val ra: RaTileUi? = null
)

/**
 * One fact worth reading at a glance on a tile with room for it. Kept as a label and value pair so
 * the tile decides how many fit rather than each caller guessing.
 */
data class TileStat(val label: String, val value: String)


/**
 * A collection a tile points at, resolved for drawing: the name it shows and one cover from inside
 * it, since a collection has no art of its own.
 */
data class TileCollectionUi(
    val name: String,
    val coverPaths: List<String> = emptyList(),
    val gameCount: Int = 0
) {
    val coverPath: String? get() = coverPaths.firstOrNull()
}

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
    columns: GridAxis,
    rows: GridAxis,
    focusedCell: GridCell,
    onCellTap: (GridCell) -> Unit,
    onShapeResolved: (ResolvedGridShape) -> Unit,
    modifier: Modifier = Modifier,
    onTileLongPress: ((GridCell) -> Unit)? = null,
    showEmptyCells: Boolean = true,
    showCursor: Boolean = true,
    editModeLabel: String? = null,
    downloadIndicatorFor: (Long) -> com.nendo.argosy.ui.screens.home.GameDownloadIndicator = {
        com.nendo.argosy.ui.screens.home.GameDownloadIndicator.NONE
    },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null,
    onPosterLoaded: ((String, android.graphics.Bitmap) -> Unit)? = null,
    useBoxArt: Boolean = false,
    overlappedTileIds: Set<Long> = emptySet(),
    editingTileId: Long? = null,
    onTileDrag: ((GridCell) -> Unit)? = null,
    onTileResize: ((GridCell) -> Unit)? = null,
    onToggleEditMode: (() -> Unit)? = null,
    onCommitEdit: (() -> Unit)? = null,
    isResizing: Boolean = false,
    tilePlayback: Map<Long, String> = emptyMap(),
    engagedTileId: Long? = null,
    engagedPaused: Boolean = false,
    engagedSeekTicks: Int = 0,
    engagedIndex: Int = 0,
    onBadgeTap: ((Int) -> Unit)? = null,
    onBandTap: (() -> Unit)? = null,
    playbackPositions: Map<String, Long> = emptyMap(),
    onPlaybackPosition: (String, Long) -> Unit = { _, _ -> },
    onTakeAudio: () -> Unit = {},
    onReleaseAudio: () -> Unit = {}
) {
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val gap = Dimens.spacingSm
    val gapPx = with(density) { gap.toPx() }
    val reportShape by androidx.compose.runtime.rememberUpdatedState(onShapeResolved)
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { measured = it }
    ) {
        if (measured.width <= 0 || measured.height <= 0) return@Box
        val metrics = customGridMetrics(
            measured.width.toFloat(),
            measured.height.toFloat(),
            columns,
            rows,
            gapPx
        )
        LaunchedEffect(columns, rows, metrics.columns, metrics.rows) {
            reportShape(
                ResolvedGridShape(columns, rows, metrics.shape, portrait = measured.height > measured.width)
            )
        }
        CustomGridCells(
            tiles = tiles,
            columnRange = 0 until metrics.columns,
            rowRange = 0 until metrics.rows,
            cellWidth = with(density) { metrics.cellWidthPx.toDp() },
            cellHeight = with(density) { metrics.cellHeightPx.toDp() },
            gap = gap,
            originX = with(density) { metrics.offsetXPx.toDp() },
            originY = with(density) { metrics.offsetYPx.toDp() },
            contentFor = contentFor,
            focusedCell = focusedCell,
            onCellTap = onCellTap,
            onTileLongPress = onTileLongPress,
            showEmptyCells = showEmptyCells,
            showCursor = showCursor,
            editModeLabel = editModeLabel,
            downloadIndicatorFor = downloadIndicatorFor,
            onCoverLoadFailed = onCoverLoadFailed,
            onCoverLoaded = onCoverLoaded,
            onPosterLoaded = onPosterLoaded,
            useBoxArt = useBoxArt,
            overlappedTileIds = overlappedTileIds,
            editingTileId = editingTileId,
            dragOffset = dragOffset,
            tilePlayback = tilePlayback,
            engagedTileId = engagedTileId,
            engagedPaused = engagedPaused,
            engagedSeekTicks = engagedSeekTicks,
            engagedIndex = engagedIndex,
            onBadgeTap = onBadgeTap,
            onBandTap = onBandTap,
            playbackPositions = playbackPositions,
            onPlaybackPosition = onPlaybackPosition,
            onTakeAudio = onTakeAudio,
            onReleaseAudio = onReleaseAudio
        )

        if (editingTileId != null && onTileDrag != null) {
            TileDragSurface(
                metrics = metrics,
                anchor = tiles.firstOrNull { it.id == editingTileId }?.rect,
                isResizing = isResizing,
                onOffsetChange = { dragOffset = it },
                onTileDrag = onTileDrag,
                onTileResize = onTileResize,
                onCommit = { onCommitEdit?.invoke() },
                onTap = { onToggleEditMode?.invoke() }
            )
        }
    }
}

@Composable
internal fun CustomGridCells(
    tiles: List<HomeTile>,
    columnRange: IntRange,
    rowRange: IntRange,
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    originX: Dp,
    originY: Dp,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    focusedCell: GridCell,
    onCellTap: (GridCell) -> Unit,
    onTileLongPress: ((GridCell) -> Unit)?,
    showEmptyCells: Boolean,
    showCursor: Boolean,
    editModeLabel: String?,
    downloadIndicatorFor: (Long) -> com.nendo.argosy.ui.screens.home.GameDownloadIndicator,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?,
    onPosterLoaded: ((String, android.graphics.Bitmap) -> Unit)?,
    useBoxArt: Boolean,
    overlappedTileIds: Set<Long>,
    editingTileId: Long?,
    dragOffset: androidx.compose.ui.geometry.Offset,
    tilePlayback: Map<Long, String>,
    engagedTileId: Long?,
    engagedPaused: Boolean,
    engagedSeekTicks: Int,
    engagedIndex: Int,
    onBadgeTap: ((Int) -> Unit)?,
    onBandTap: (() -> Unit)?,
    playbackPositions: Map<String, Long>,
    onPlaybackPosition: (String, Long) -> Unit,
    onTakeAudio: () -> Unit,
    onReleaseAudio: () -> Unit
) {
    val occupied = remember(tiles) {
        tiles.flatMap { tile ->
            (tile.rect.columnIndex..tile.rect.lastColumn).flatMap { column ->
                (tile.rect.rowIndex..tile.rect.lastRow).map { row -> column to row }
            }
        }.toSet()
    }

    for (column in columnRange) {
        for (row in rowRange) {
            if (column to row in occupied) continue
            val isCursor = focusedCell.columnIndex == column && focusedCell.rowIndex == row
            if (!showEmptyCells && !isCursor) continue
            CustomGridCellBox(
                rect = TileRect(column, row),
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                gap = gap,
                originX = originX,
                originY = originY,
                isFocused = isCursor && showCursor,
                onClick = { onCellTap(GridCell(column, row)) },
                onLongClick = null,
                content = null,
                outlineEmpty = showEmptyCells,
                editModeLabel = null,
                isOverlapped = false,
                downloadIndicatorFor = downloadIndicatorFor,
                onCoverLoadFailed = null,
                onCoverLoaded = null,
                onPosterLoaded = null,
                useBoxArt = false
            )
        }
    }

    tiles.sortedBy { it.id == editingTileId }.forEach { tile ->
        key(tile.id) {
            CustomGridCellBox(
                rect = tile.rect,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
                gap = gap,
                originX = originX,
                originY = originY,
                isFocused = if (editingTileId != null) {
                    tile.id == editingTileId
                } else {
                    showCursor && tile.rect.covers(focusedCell.columnIndex, focusedCell.rowIndex)
                },
                onClick = { onCellTap(GridCell(tile.rect.columnIndex, tile.rect.rowIndex)) },
                onLongClick = onTileLongPress?.let { handler ->
                    { handler(GridCell(tile.rect.columnIndex, tile.rect.rowIndex)) }
                },
                content = contentFor(tile),
                coverScale = tile.coverScale,
                editModeLabel = editModeLabel.takeIf { tile.id == editingTileId },
                isOverlapped = tile.id in overlappedTileIds,
                dragOffset = if (tile.id == editingTileId) {
                    dragOffset
                } else {
                    androidx.compose.ui.geometry.Offset.Zero
                },
                downloadIndicatorFor = downloadIndicatorFor,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                onPosterLoaded = onPosterLoaded,
                useBoxArt = useBoxArt,
                playbackPath = tilePlayback[tile.id],
                isEngaged = tile.id == engagedTileId,
                isPaused = tile.id == engagedTileId && engagedPaused,
                seekTicks = if (tile.id == engagedTileId) engagedSeekTicks else 0,
                engagedIndex = if (tile.id == engagedTileId) engagedIndex else 0,
                onBadgeTap = onBadgeTap,
                onBandTap = onBandTap,
                startPositionMs = tilePlayback[tile.id]?.let { playbackPositions[it] } ?: 0L,
                onPlaybackPosition = onPlaybackPosition,
                onTakeAudio = onTakeAudio,
                onReleaseAudio = onReleaseAudio
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
    cellWidth: Dp,
    cellHeight: Dp,
    gap: Dp,
    originX: Dp,
    originY: Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: CustomGridTileContent?,
    outlineEmpty: Boolean = true,
    coverScale: TileCoverScale = TileCoverScale.CROP,
    dragOffset: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero,
    editModeLabel: String?,
    isOverlapped: Boolean,
    downloadIndicatorFor: (Long) -> com.nendo.argosy.ui.screens.home.GameDownloadIndicator,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?,
    onPosterLoaded: ((String, android.graphics.Bitmap) -> Unit)?,
    useBoxArt: Boolean,
    playbackPath: String? = null,
    isEngaged: Boolean = false,
    isPaused: Boolean = false,
    seekTicks: Int = 0,
    engagedIndex: Int = 0,
    onBadgeTap: ((Int) -> Unit)? = null,
    onBandTap: (() -> Unit)? = null,
    startPositionMs: Long = 0L,
    onPlaybackPosition: (String, Long) -> Unit = { _, _ -> },
    onTakeAudio: () -> Unit = {},
    onReleaseAudio: () -> Unit = {}
) {
    val theme = LocalArgosyTheme.current
    val boxArtStyle = com.nendo.argosy.ui.theme.LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)
    val width = cellWidth * rect.columnSpan + gap * (rect.columnSpan - 1)
    val height = cellHeight * rect.rowSpan + gap * (rect.rowSpan - 1)
    val placement = Modifier
        .zIndex(
            when {
                editModeLabel != null -> EDITING_TILE_Z
                isFocused -> FOCUSED_TILE_Z
                else -> 0f
            }
        )
        .offset(
            x = originX + (cellWidth + gap) * rect.columnIndex,
            y = originY + (cellHeight + gap) * rect.rowIndex
        )
        .graphicsLayer {
            translationX = dragOffset.x
            translationY = dragOffset.y
        }
        .size(width, height)

    val ra = content?.ra
    if (ra != null) {
        RaTileBox(
            placement = placement,
            rect = rect,
            ra = ra,
            isFocused = isFocused,
            isOverlapped = isOverlapped,
            isEngaged = isEngaged,
            engagedIndex = engagedIndex,
            editModeLabel = editModeLabel,
            onClick = onClick,
            onLongClick = onLongClick,
            onBadgeTap = onBadgeTap,
            onBandTap = onBandTap,
            onCoverLoaded = onCoverLoaded
        )
        return
    }

    if (playbackPath != null && content?.media != null) {
        Box(modifier = placement, contentAlignment = Alignment.Center) {
            InlineTilePlayer(
                filePath = playbackPath,
                isPlaying = true,
                isEngaged = isEngaged,
                isPaused = isPaused,
                seekTicks = seekTicks,
                startPositionMs = startPositionMs,
                onPositionChanged = { onPlaybackPosition(playbackPath, it) },
                onTakeAudio = onTakeAudio,
                onReleaseAudio = onReleaseAudio,
                modifier = Modifier
                    .fillMaxSize()
                    .boxArtFrame(
                        isFocused = isFocused,
                        focusScale = focusScaleForSpan(rect),
                        alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null
                    )
                    .then(
                        if (onLongClick == null) {
                            Modifier.clickableNoFocus(onClick = onClick)
                        } else {
                            Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                        }
                    )
            )
            if (editModeLabel != null) {
                TileModeTab(
                    label = editModeLabel,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        return
    }

    if (rect.columnSpan > rect.rowSpan && content != null && !content.isMissing) {
        WideTileBox(
            placement = placement,
            content = content,
            coverScale = coverScale,
            isFocused = isFocused,
            isOverlapped = isOverlapped,
            focusScale = focusScaleForSpan(rect),
            editModeLabel = editModeLabel,
            onCoverLoaded = onCoverLoaded,
            onClick = onClick,
            onLongClick = onLongClick
        )
        return
    }

    val media = content?.media
    if (media != null) {
        val tileRatio = width / height
        val fitByHeight = mediaPosterAspectRatio <= tileRatio
        Box(modifier = placement, contentAlignment = Alignment.Center) {
            MediaCard(
                media = media,
                isFocused = isFocused,
                focusScale = focusScaleForSpan(rect),
                alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                onPosterLoaded = onPosterLoaded,
                modifier = Modifier
                    .then(
                        if (fitByHeight) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()
                    )
                    .aspectRatio(mediaPosterAspectRatio, matchHeightConstraintsFirst = fitByHeight)
                    .then(
                        if (onLongClick == null) {
                            Modifier.clickableNoFocus(onClick = onClick)
                        } else {
                            Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                        }
                    )
            )
            if (editModeLabel != null && isFocused) {
                TileModeTab(
                    label = editModeLabel,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        return
    }

    val game = content?.game
    if (game != null) {
        val boxArtStyle = com.nendo.argosy.ui.theme.LocalBoxArtStyle.current
        val tileRatio = width / height
        val keepsWholeCover = boxArtStyle.nativeAspectRatio || coverScale == TileCoverScale.FIT
        val artRatio = if (keepsWholeCover) {
            com.nendo.argosy.ui.common.rememberGameArtAspectRatio(
                game = game,
                useBoxArt = useBoxArt,
                coverPath = game.coverPath,
                fallback = boxArtStyle.aspectRatio
            )
        } else {
            tileRatio
        }
        val fitByHeight = artRatio <= tileRatio
        val reroll = rememberRerollAnimation(enabled = content.isRandom, pickKey = game.id)
        Box(modifier = placement, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .then(
                        if (fitByHeight) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()
                    )
                    .aspectRatio(artRatio, matchHeightConstraintsFirst = fitByHeight)
                    .graphicsLayer {
                        scaleX = reroll.scale
                        scaleY = reroll.scale
                    }
            ) {
                val indicatorFor by androidx.compose.runtime.rememberUpdatedState(downloadIndicatorFor)
                val indicator by androidx.compose.runtime.remember(game.id) {
                    androidx.compose.runtime.derivedStateOf { indicatorFor(game.id) }
                }
                GameCard(
                    game = game,
                    isFocused = isFocused,
                    focusScale = focusScaleForSpan(rect),
                    downloadIndicator = indicator,
                    saturationOverride = if (isOverlapped) OVERLAPPED_SATURATION else null,
                    alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    useBoxArt = useBoxArt,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (onLongClick == null) {
                                Modifier.clickableNoFocus(onClick = onClick)
                            } else {
                                Modifier.clickableNoFocus(
                                    onClick = onClick,
                                    onLongClick = onLongClick
                                )
                            }
                        )
                )
                if (content.isCollectionQueue) {
                    CollectionQueueBadge(modifier = Modifier.align(Alignment.TopStart))
                }
                if (content.isRandom) {
                    TileKindBadge(
                        icon = Icons.Filled.Casino,
                        rotation = reroll.rotation,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                if (content.isContinue) {
                    TileKindBadge(
                        icon = Icons.Filled.History,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                if (content.isLibraryLink) {
                    TileKindBadge(
                        icon = Icons.Filled.FilterAlt,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
            if (editModeLabel != null && isFocused) {
                TileModeTab(
                    label = editModeLabel,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        return
    }

    Box(modifier = placement) {
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
        Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .then(
                if (content == null) {
                    if (outlineEmpty) {
                        Modifier.border(boxArtStyle.borderThicknessDp, theme.surfaceRaised, shape)
                    } else {
                        Modifier
                    }
                } else {
                    Modifier.background(theme.surfaceRaised)
                }
            )
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            )
            .then(
                if (onLongClick == null) {
                    Modifier.clickableNoFocus(onClick = onClick)
                } else {
                    Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                }
            ),
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
            else -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.padding(Dimens.spacingSm)
            ) {
                val appIcon = content.packageName?.let {
                    com.nendo.argosy.ui.coil.AppIconData(it)
                }
                val cover = com.nendo.argosy.ui.common.rememberFileImageModel(content.coverPath)
                when {
                    appIcon != null -> coil.compose.AsyncImage(
                        model = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconXl)
                    )
                    cover != null -> coil.compose.AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(Dimens.iconXl)
                            .clip(RoundedCornerShape(Dimens.radiusSm))
                    )
                    content.isRandom -> Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconXl)
                    )
                    content.isContinue -> Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconXl)
                    )
                    content.isLibraryLink -> Icon(
                        imageVector = Icons.Filled.FilterAlt,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconXl)
                    )
                }
                Text(
                    text = content.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textDim,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (appIcon == null && cover == null) {
                    content.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMute,
                            maxLines = 2,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    content.stats.forEach { stat ->
                        Text(
                            text = "${stat.value} ${stat.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim,
                            maxLines = 1
                        )
                    }
                }
            }
        }
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
                text = stringResource(R.string.ui_custom_grid_add_page),
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
 * Overlapped tiles are dimmed and drained of colour. Both are handed to the card's own overrides
 * rather than applied as a parent layer: a cover composites with blend modes internally, and
 * wrapping that in an alpha layer flattens it to black instead of fading it.
 */
private const val COLLECTION_BADGE_SCRIM_ALPHA = 0.7f
internal const val OVERLAPPED_ALPHA = 0.6f
private const val OVERLAPPED_SATURATION = 0.15f
private const val FOCUSED_TILE_Z = 1f
private const val EDITING_TILE_Z = 2f

/**
 * Says a tile showing one game is really a collection being played through, so it is not mistaken
 * for a plain game tile that happens to sit next to it.
 */
@Composable
private fun CollectionQueueBadge(modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .padding(Dimens.spacingXs)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceBase.copy(alpha = COLLECTION_BADGE_SCRIM_ALPHA))
            .padding(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Outlined.Layers,
            contentDescription = null,
            tint = theme.textPrimary,
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

/**
 * The badge in the corner of a feature tile that otherwise looks like a plain game: a die for a
 * random pick, a clock for the continue tile. [rotation] is the re-roll spin, so on a random tile
 * the die is what visibly rolls when the pick changes.
 */
@Composable
private fun TileKindBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    rotation: Float = 0f
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .padding(Dimens.spacingXs)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceBase.copy(alpha = COLLECTION_BADGE_SCRIM_ALPHA))
            .padding(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.textPrimary,
            modifier = Modifier
                .size(Dimens.iconSm)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

private data class RerollAnimation(val rotation: Float, val scale: Float)

/**
 * Spins the die and gives the tile a short squeeze whenever [pickKey] changes, which on a random
 * tile is exactly a re-roll. The first pick a tile shows does not animate: the page arriving is
 * not a roll.
 */
@Composable
private fun rememberRerollAnimation(enabled: Boolean, pickKey: Long): RerollAnimation {
    val rotation = remember { androidx.compose.animation.core.Animatable(0f) }
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    var lastKey by remember { mutableStateOf(pickKey) }
    LaunchedEffect(pickKey) {
        if (!enabled || lastKey == pickKey) return@LaunchedEffect
        lastKey = pickKey
        rotation.snapTo(0f)
        scale.snapTo(REROLL_SQUEEZE_SCALE)
        launch { rotation.animateTo(REROLL_SPIN_DEGREES, MotionTokens.Tween.medium) }
        scale.animateTo(1f, MotionTokens.Spring.focusSnappy)
    }
    return RerollAnimation(rotation = rotation.value, scale = if (enabled) scale.value else 1f)
}

private const val REROLL_SPIN_DEGREES = 360f
private const val REROLL_SQUEEZE_SCALE = 0.9f

/**
 * A tab hanging off the tile's lower edge naming the mode the d-pad is currently in. It is the
 * inverse of the platform tab, which sits inset within the cover: this one belongs to the cursor
 * rather than to the game, so it reads as attached from outside.
 */
@Composable
internal fun TileModeTab(label: String, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = theme.surfaceBase,
        modifier = modifier
            .graphicsLayer { translationY = -size.height }
            .clip(shape)
            .background(theme.focusAccent)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

/**
 * Focus scale for a tile covering [rect]. A scale is a proportion, so applying the same one to a
 * tile spanning three cells moves its edge three times as far as a single cell's. Dividing by the
 * longest span keeps the growth a constant distance whatever the tile's size, which is both what a
 * cursor should look like and what the page reserved room for.
 */
internal fun focusScaleForSpan(rect: TileRect): Float {
    val span = maxOf(rect.columnSpan, rect.rowSpan).coerceAtLeast(1)
    return 1f + (ComponentDefaults.Focus.scaleFocused - 1f) / span
}

/**
 * The focused border a wide tile draws, matching what [GameCard] draws around a cover of the same
 * game: the cover's own gradient for the gradient and glass styles, the accent pair when the cover
 * has none. Solid returns null because [boxArtFrame] already draws that one.
 */
internal fun wideTileBorderBrush(
    style: BoxArtBorderStyle,
    gradientColors: Pair<Color, Color>?,
    accent: Color,
    secondary: Color
): Brush? = when (style) {
    BoxArtBorderStyle.GRADIENT -> {
        val (a, b) = gradientColors ?: (accent to secondary)
        Brush.linearGradient(listOf(a, b))
    }
    BoxArtBorderStyle.GLASS -> {
        val base = gradientColors?.first ?: accent
        val highlight = gradientColors?.second ?: base
        Brush.linearGradient(listOf(highlight.copy(alpha = 0.85f), base.copy(alpha = 0.6f)))
    }
    BoxArtBorderStyle.SOLID -> null
}

/**
 * A tile wider than it is tall. The extra width is spent on what the cover cannot say - how long
 * this has been played, how many achievements are left, how much is in a collection - rather than
 * on stretching the art, which is the one thing a wide box cannot do to a portrait cover.
 */
@Composable
private fun WideTileBox(
    placement: Modifier,
    content: CustomGridTileContent,
    coverScale: TileCoverScale,
    isFocused: Boolean,
    isOverlapped: Boolean,
    focusScale: Float,
    editModeLabel: String?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val boxArtStyle = com.nendo.argosy.ui.theme.LocalBoxArtStyle.current
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)
    val reroll = rememberRerollAnimation(enabled = content.isRandom, pickKey = content.game?.id ?: 0L)
    val gradientColors = content.game?.gradientColors
    val accent = boxArtStyle.accentColor ?: theme.focusAccent
    val borderBrush = wideTileBorderBrush(
        style = boxArtStyle.borderStyle,
        gradientColors = gradientColors,
        accent = accent,
        secondary = boxArtStyle.secondaryColor ?: accent
    ).takeIf { isFocused && boxArtStyle.borderThicknessDp.value > 0f }

    Box(modifier = placement) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = reroll.scale
                    scaleY = reroll.scale
                }
                .boxArtFrame(
                    isFocused = isFocused,
                    focusScale = focusScale,
                    alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                    artworkGradient = gradientColors,
                    background = SolidColor(theme.surfaceRaised)
                )
                .then(
                    if (borderBrush != null) {
                        Modifier.border(boxArtStyle.borderThicknessDp, borderBrush, shape)
                    } else Modifier
                )
                .then(
                    if (onLongClick == null) {
                        Modifier.clickableNoFocus(onClick = onClick)
                    } else {
                        Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
                    }
                )
        ) {
        Row(modifier = Modifier.fillMaxSize()) {
            val gameCover = content.game?.let {
                com.nendo.argosy.ui.common.rememberResolvedCoverPath(it.id, it.coverPath)
            }
            val coverPath = gameCover ?: content.coverPath
            val cover = com.nendo.argosy.ui.common.rememberFileImageModel(coverPath)
            val appIcon = content.packageName?.let { com.nendo.argosy.ui.coil.AppIconData(it) }
            val poster = content.posterUrl?.takeIf { it.isNotBlank() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(WIDE_TILE_COVER_ASPECT)
                    .background(theme.surfaceBase)
            ) {
                when {
                    appIcon != null -> coil.compose.AsyncImage(
                        model = appIcon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(Dimens.spacingSm)
                    )
                    poster != null -> coil.compose.AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    cover != null -> coil.compose.AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = if (coverScale == TileCoverScale.FIT) {
                            androidx.compose.ui.layout.ContentScale.Fit
                        } else {
                            androidx.compose.ui.layout.ContentScale.Crop
                        },
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            val gameId = content.game?.id ?: return@AsyncImage
                            val bitmap = (state.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) onCoverLoaded?.invoke(gameId, bitmap)
                        }
                    )
                }
                if (content.isRandom) {
                    TileKindBadge(
                        icon = Icons.Filled.Casino,
                        rotation = reroll.rotation,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                if (content.isContinue) {
                    TileKindBadge(
                        icon = Icons.Filled.History,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                if (content.isLibraryLink) {
                    TileKindBadge(
                        icon = Icons.Filled.FilterAlt,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                content.media?.let { media ->
                    MediaDownloadBadge(
                        availability = media.availability,
                        size = Dimens.iconSm,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Dimens.spacingXs)
                    )
                    if (media.progressFraction > 0f) {
                        MediaProgressBar(
                            fraction = media.progressFraction,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.spacingSm),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Text(
                    text = content.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                val subtitle = content.subtitle
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                content.stats.forEach { stat ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMute,
                            maxLines = 1
                        )
                        Text(
                            text = stat.value,
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        }
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

private const val WIDE_TILE_COVER_ASPECT = 0.7f

/**
 * What a wide game tile says beside its cover. Only facts that exist are offered: a game with no
 * achievements and no play time shows nothing rather than a row of zeroes, which reads as broken.
 */
fun tileStatsFor(game: com.nendo.argosy.ui.screens.home.HomeGameUi, context: Context): List<TileStat> = buildList {
    if (game.playTimeMinutes > 0) {
        add(TileStat(context.getString(R.string.home_grid_tile_stat_played), formatPlayTime(context, game.playTimeMinutes)))
    }
    if (game.achievementCount > 0) {
        add(
            TileStat(
                context.getString(R.string.home_grid_tile_stat_achievements),
                "${game.earnedAchievementCount}/${game.achievementCount}"
            )
        )
    }
    if (game.releaseYear != null) {
        add(TileStat(context.getString(R.string.home_grid_tile_stat_released), game.releaseYear.toString()))
    }
}

private fun formatPlayTime(context: Context, minutes: Int): String {
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) {
        context.getString(R.string.common_homegrid_playtime_hours_minutes, hours, remainder)
    } else {
        context.getString(R.string.common_homegrid_playtime_minutes, remainder)
    }
}

@Composable
internal fun BoxScope.TileDragSurface(
    metrics: CustomGridMetrics,
    anchor: TileRect?,
    isResizing: Boolean,
    onOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    onTileDrag: (GridCell) -> Unit,
    onTileResize: ((GridCell) -> Unit)?,
    onCommit: () -> Unit,
    onTap: () -> Unit,
    scrolled: () -> androidx.compose.ui.geometry.Offset = { androidx.compose.ui.geometry.Offset.Zero }
) {
    val currentAnchor by androidx.compose.runtime.rememberUpdatedState(anchor)
    val resizing by androidx.compose.runtime.rememberUpdatedState(isResizing)
    val currentScrolled by androidx.compose.runtime.rememberUpdatedState(scrolled)
    val cellAt = { offset: androidx.compose.ui.geometry.Offset ->
        cellAtOffset(offset + currentScrolled(), metrics)
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(metrics) {
                var grabColumn = 0
                var grabRow = 0
                var carrying = false
                var travelled = androidx.compose.ui.geometry.Offset.Zero
                var landing: GridCell? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        val touched = cellAt(offset)
                        val grabbed = currentAnchor
                        carrying = grabbed != null &&
                            grabbed.covers(touched.columnIndex, touched.rowIndex)
                        travelled = androidx.compose.ui.geometry.Offset.Zero
                        landing = null
                        if (carrying && grabbed != null) {
                            grabColumn = touched.columnIndex - grabbed.columnIndex
                            grabRow = touched.rowIndex - grabbed.rowIndex
                        }
                    },
                    onDragEnd = {
                        if (carrying && !resizing) landing?.let(onTileDrag)
                        carrying = false
                        travelled = androidx.compose.ui.geometry.Offset.Zero
                        onOffsetChange(androidx.compose.ui.geometry.Offset.Zero)
                    },
                    onDragCancel = {
                        carrying = false
                        travelled = androidx.compose.ui.geometry.Offset.Zero
                        onOffsetChange(androidx.compose.ui.geometry.Offset.Zero)
                    },
                    onDrag = { change, amount ->
                        if (!carrying) return@detectDragGestures
                        change.consume()
                        val touched = cellAt(change.position)
                        if (resizing) {
                            onTileResize?.invoke(touched)
                            return@detectDragGestures
                        }
                        travelled += amount
                        onOffsetChange(travelled)
                        landing = GridCell(
                            touched.columnIndex - grabColumn,
                            touched.rowIndex - grabRow
                        )
                    }
                )
            }
            .pointerInput(metrics) {
                detectTapGestures(
                    onTap = { offset ->
                        val touched = cellAt(offset)
                        val grabbed = currentAnchor ?: return@detectTapGestures
                        if (grabbed.covers(touched.columnIndex, touched.rowIndex)) onTap()
                    },
                    onDoubleTap = { onCommit() }
                )
            }
    )
}

/**
 * The cell a point falls in. Points in the margin around the grid clamp to the nearest cell rather
 * than resolving to nothing, so a drag that strays past the edge keeps tracking the finger.
 */
private fun cellAtOffset(
    offset: androidx.compose.ui.geometry.Offset,
    metrics: CustomGridMetrics
): GridCell {
    val columnStride = metrics.cellWidthPx + metrics.gapPx
    val rowStride = metrics.cellHeightPx + metrics.gapPx
    if (columnStride <= 0f || rowStride <= 0f) return GridCell(0, 0)
    val column = ((offset.x - metrics.offsetXPx) / columnStride).toInt()
    val row = ((offset.y - metrics.offsetYPx) / rowStride).toInt()
    return GridCell(
        column.coerceIn(0, (metrics.columns - 1).coerceAtLeast(0)),
        row.coerceIn(0, (metrics.rows - 1).coerceAtLeast(0))
    )
}
