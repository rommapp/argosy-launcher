package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.ResolvedGridShape
import com.nendo.argosy.domain.model.SCROLL_AXIS_BOUND
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens

/**
 * A scrolling custom grid: one canvas along [CustomGridState.scrollAxis], drawn as a lazy list of
 * [CustomGridState.scrollBands]. The list follows [CustomGridState.cell], centring the band that
 * holds it; the cursor itself stays with the caller's state.
 */
@Composable
fun CustomGridScrollCanvas(
    state: CustomGridState,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    columns: GridAxis,
    rows: GridAxis,
    onCellTap: (GridCell) -> Unit,
    onShapeResolved: (ResolvedGridShape) -> Unit,
    modifier: Modifier = Modifier,
    onTileLongPress: ((GridCell) -> Unit)? = null,
    onBadgeTap: ((Int) -> Unit)? = null,
    onBandTap: (() -> Unit)? = null,
    onTileDrag: ((GridCell) -> Unit)? = null,
    onTileResize: ((GridCell) -> Unit)? = null,
    onToggleEditMode: (() -> Unit)? = null,
    onCommitEdit: (() -> Unit)? = null,
    onPlaybackPosition: (String, Long) -> Unit = { _, _ -> },
    onTakeAudio: () -> Unit = {},
    onReleaseAudio: () -> Unit = {},
    showEmptySlots: Boolean = true,
    showCursor: Boolean = true,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null,
    onPosterLoaded: ((String, android.graphics.Bitmap) -> Unit)? = null,
    useBoxArt: Boolean = false
) {
    val axis = state.scrollAxis ?: return
    val vertical = axis == HomeScrollAxis.VERTICAL
    val density = LocalDensity.current
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val gap = Dimens.spacingSm
    val gapPx = with(density) { gap.toPx() }
    val reportShape by rememberUpdatedState(onShapeResolved)
    val listState = rememberLazyListState()
    val bands = remember(state) { state.scrollBands }
    val focusedBand = remember(state) { state.focusedBandIndex }
    val tiles = remember(state) { state.tilesOnPage(state.page) }
    val editModeLabel = state.editLabelRes?.let { stringResource(it) }

    LaunchedEffect(focusedBand, bands.size) {
        if (bands.isNotEmpty()) listState.animateScrollToItemCentered(focusedBand)
    }

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
        val cellWidth = with(density) { metrics.cellWidthPx.toDp() }
        val cellHeight = with(density) { metrics.cellHeightPx.toDp() }
        val stride = (if (vertical) cellHeight else cellWidth) + gap
        val stridePx = (if (vertical) metrics.cellHeightPx else metrics.cellWidthPx) + gapPx
        val lead = Dimens.spacingLg
        val leadPx = with(density) { lead.toPx() }
        val cross = with(density) { (if (vertical) metrics.offsetXPx else metrics.offsetYPx).toDp() }

        val band: @Composable (Int, IntRange) -> Unit = { index, lines ->
            val bandTiles = remember(tiles, lines, vertical) {
                tiles.filter { (if (vertical) it.rect.rowIndex else it.rect.columnIndex) in lines }
            }
            val length = stride * lines.count()
            val shift = -(stride * lines.first)
            Box(
                modifier = Modifier
                    .zIndex(if (index == focusedBand) FOCUSED_BAND_Z else 0f)
                    .then(
                        if (vertical) {
                            Modifier.fillMaxWidth().height(length)
                        } else {
                            Modifier.fillMaxHeight().width(length)
                        }
                    )
            ) {
                CustomGridCells(
                    tiles = bandTiles,
                    columnRange = if (vertical) 0 until metrics.columns else lines,
                    rowRange = if (vertical) lines else 0 until metrics.rows,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    gap = gap,
                    originX = if (vertical) cross else shift,
                    originY = if (vertical) shift else cross,
                    contentFor = contentFor,
                    focusedCell = state.cell,
                    onCellTap = onCellTap,
                    onTileLongPress = onTileLongPress,
                    showEmptyCells = showEmptySlots,
                    showCursor = showCursor,
                    editModeLabel = editModeLabel,
                    downloadIndicatorFor = downloadIndicatorFor,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    onPosterLoaded = onPosterLoaded,
                    useBoxArt = useBoxArt,
                    overlappedTileIds = state.overlappedTileIds,
                    editingTileId = state.editingTileId,
                    dragOffset = dragOffset,
                    tilePlayback = state.tilePlayback,
                    engagedTileId = state.engagedTileId,
                    engagedPaused = state.engagedPaused,
                    engagedSeekTicks = state.engagedSeekTicks,
                    engagedIndex = state.engagedIndex,
                    onBadgeTap = onBadgeTap,
                    onBandTap = onBandTap,
                    playbackPositions = state.playbackPositions,
                    onPlaybackPosition = onPlaybackPosition,
                    onTakeAudio = onTakeAudio,
                    onReleaseAudio = onReleaseAudio
                )
            }
        }

        if (vertical) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = lead),
                userScrollEnabled = !state.isEditing,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(bands, key = { _, lines -> lines.first }) { index, lines -> band(index, lines) }
            }
        } else {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = lead),
                userScrollEnabled = !state.isEditing,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(bands, key = { _, lines -> lines.first }) { index, lines -> band(index, lines) }
            }
        }

        if (state.editingTileId != null && onTileDrag != null) {
            TileDragSurface(
                metrics = if (vertical) {
                    metrics.copy(rows = SCROLL_AXIS_BOUND, offsetYPx = leadPx)
                } else {
                    metrics.copy(columns = SCROLL_AXIS_BOUND, offsetXPx = leadPx)
                },
                anchor = state.editingTile?.rect,
                isResizing = state.editMode == TileEditMode.RESIZE,
                onOffsetChange = { dragOffset = it },
                onTileDrag = onTileDrag,
                onTileResize = onTileResize,
                onCommit = { onCommitEdit?.invoke() },
                onTap = { onToggleEditMode?.invoke() },
                scrolled = {
                    val travelled = scrolledPx(listState, bands, stridePx)
                    if (vertical) Offset(0f, travelled) else Offset(travelled, 0f)
                }
            )
        }
    }
}

private fun scrolledPx(listState: LazyListState, bands: List<IntRange>, stridePx: Float): Float {
    val firstLine = bands.getOrNull(listState.firstVisibleItemIndex)?.first ?: 0
    return firstLine * stridePx + listState.firstVisibleItemScrollOffset
}

private const val FOCUSED_BAND_Z = 1f
