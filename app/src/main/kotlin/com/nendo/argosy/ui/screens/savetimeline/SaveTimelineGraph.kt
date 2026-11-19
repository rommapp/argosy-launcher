package com.nendo.argosy.ui.screens.savetimeline

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.scrollToItemCentered
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val RAIL_ALPHA = 0.45f
private const val SKELETON_ALPHA = 0.5f
private const val SKELETON_DOTS = 3
private val LabelFocus = FocusIndicators(stripe = true)

internal class SaveTimelineGraphActions(
    val onTapNode: (lane: Int, position: Int) -> Unit,
    val onLongPressNode: (lane: Int, position: Int) -> Unit,
    val onTapLane: (lane: Int) -> Unit,
    val onLongPressLane: (lane: Int) -> Unit,
    val onTapFloater: () -> Unit
)

@Composable
internal fun saveTimelineLaneColors(): List<Color> {
    val accent = LocalArgosyTheme.current.focusAccent
    return remember(accent) {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(accent.toArgb(), hsl)
        LANE_HUE_STEPS.map { step ->
            Color(
                androidx.core.graphics.ColorUtils.HSLToColor(
                    floatArrayOf((hsl[0] + step) % 360f, hsl[1].coerceAtLeast(LANE_MIN_SATURATION), LANE_LIGHTNESS)
                )
            )
        }
    }
}

@Composable
internal fun saveTimelinePanelColor(): Color = LocalArgosyTheme.current.surfaceBase.copy(alpha = PANEL_ALPHA)

private const val PANEL_ALPHA = 0.55f

private val LANE_HUE_STEPS = listOf(0f, 180f, 90f, 270f, 45f, 225f)
private const val LANE_MIN_SATURATION = 0.55f
private const val LANE_LIGHTNESS = 0.62f

@Composable
internal fun SaveTimelineGraph(
    state: SaveTimelineUiState,
    overlayOpen: Boolean,
    coverPath: String?,
    actions: SaveTimelineGraphActions
) {
    val laneColors = saveTimelineLaneColors()
    val listState = rememberLazyListState()
    val verticalScroll = rememberScrollState()
    val laneHeight = Dimens.saveTimelineLaneHeight
    val laneHeightPx = with(LocalDensity.current) { laneHeight.toPx() }
    val isDraggingRow by listState.interactionSource.collectIsDraggedAsState()
    val isDraggingColumn by verticalScroll.interactionSource.collectIsDraggedAsState()

    val animate = !reducedMotion()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeightPx = constraints.maxHeight
        LaunchedEffect(state.recenterTick) {
            val node = state.focusedNode ?: return@LaunchedEffect
            launch { listState.scrollToItemCentered(node.column, animate) }
            val laneCenter = state.focusLane * laneHeightPx + laneHeightPx / 2f
            val target = (laneCenter - viewportHeightPx / 2f).roundToInt().coerceIn(0, verticalScroll.maxValue)
            if (animate) verticalScroll.animateScrollTo(target) else verticalScroll.scrollTo(target)
        }
        Box(
            modifier = Modifier
                .width(Dimens.saveTimelineLabelWidth)
                .fillMaxHeight()
                .background(saveTimelinePanelColor())
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(verticalScroll)
        ) {
            Column(modifier = Modifier.width(Dimens.saveTimelineLabelWidth)) {
                state.lanes.forEachIndexed { index, lane ->
                    LaneLabel(
                        lane = lane,
                        color = laneColors[index % laneColors.size],
                        isFocused = !overlayOpen && index == state.focusLane,
                        onClick = { actions.onTapLane(index) },
                        onLongClick = { actions.onLongPressLane(index) }
                    )
                }
            }
            LaneArea(
                state = state,
                listState = listState,
                laneColors = laneColors,
                overlayOpen = overlayOpen,
                actions = actions,
                modifier = Modifier
                    .weight(1f)
                    .height(laneHeight * state.lanes.size)
            )
        }
        val floaterHidden = overlayOpen || isDraggingRow || isDraggingColumn
        SaveTimelineFloaterHost(
            state = state,
            listState = listState,
            verticalScroll = verticalScroll,
            laneColors = laneColors,
            coverPath = coverPath,
            hidden = floaterHidden,
            viewportWidthPx = constraints.maxWidth,
            viewportHeightPx = viewportHeightPx,
            onClick = actions.onTapFloater
        )
    }
}

@Composable
private fun LaneArea(
    state: SaveTimelineUiState,
    listState: LazyListState,
    laneColors: List<Color>,
    overlayOpen: Boolean,
    actions: SaveTimelineGraphActions,
    modifier: Modifier
) {
    val density = LocalDensity.current
    val theme = LocalArgosyTheme.current
    val columnWidth = Dimens.saveTimelineColumnWidth
    val laneHeight = Dimens.saveTimelineLaneHeight
    val metrics = with(density) {
        GraphMetrics(
            columnWidth = columnWidth.toPx(),
            laneHeight = laneHeight.toPx(),
            stroke = Dimens.borderMedium.toPx(),
            dash = Dimens.spacingXs.toPx(),
            skeletonRadius = Dimens.saveTimelineDot.toPx() / 2f,
            edgeInset = Dimens.spacingXl.toPx()
        )
    }
    val skeletonColor = theme.hairlineHigh.copy(alpha = SKELETON_ALPHA)
    Box(
        modifier = modifier
            .clipToBounds()
            .drawBehind {
                drawGraph(state, listState, laneColors, skeletonColor, metrics)
            }
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Dimens.spacingXl)
        ) {
            items(state.columns, key = { it.key }) { column ->
                val lane = state.lanes.getOrNull(column.lane)
                val node = lane?.nodes?.getOrNull(column.position)
                Box(modifier = Modifier.width(columnWidth).fillMaxHeight()) {
                    if (node != null) {
                        NodeCell(
                            node = node,
                            color = laneColors[column.lane % laneColors.size],
                            isFocused = !overlayOpen && state.isFocused(column.lane, column.position),
                            onClick = { actions.onTapNode(column.lane, column.position) },
                            onLongClick = { actions.onLongPressNode(column.lane, column.position) },
                            modifier = Modifier.offset(y = laneHeight * column.lane)
                        )
                    }
                }
            }
        }
    }
}

private class GraphMetrics(
    val columnWidth: Float,
    val laneHeight: Float,
    val stroke: Float,
    val dash: Float,
    val skeletonRadius: Float,
    val edgeInset: Float
)

private fun DrawScope.drawGraph(
    state: SaveTimelineUiState,
    listState: LazyListState,
    laneColors: List<Color>,
    skeletonColor: Color,
    metrics: GraphMetrics
) {
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    fun xOf(column: Int): Float {
        val anchorIndex = first?.index ?: 0
        val anchorOffset = (first?.offset ?: 0) - info.viewportStartOffset
        return anchorOffset + (column - anchorIndex) * metrics.columnWidth + metrics.columnWidth / 2f
    }
    fun yOf(lane: Int): Float = lane * metrics.laneHeight + metrics.laneHeight / 2f
    val dashed = PathEffect.dashPathEffect(floatArrayOf(metrics.dash, metrics.dash))
    state.lanes.forEachIndexed { index, lane ->
        val color = laneColors[index % laneColors.size]
        val y = yOf(index)
        when {
            lane.nodes.isNotEmpty() -> {
                val start = if (lane.hasMore) 0f else xOf(lane.nodes.first().column)
                drawLine(
                    color = color.copy(alpha = RAIL_ALPHA),
                    start = Offset(start, y),
                    end = Offset(xOf(lane.nodes.last().column), y),
                    strokeWidth = metrics.stroke
                )
            }
            lane.isLoading -> repeat(SKELETON_DOTS) { step ->
                drawCircle(
                    color = skeletonColor,
                    radius = metrics.skeletonRadius,
                    center = Offset(size.width - metrics.edgeInset - step * metrics.columnWidth, y)
                )
            }
            !lane.failed -> drawLine(
                color = color.copy(alpha = RAIL_ALPHA),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = metrics.stroke,
                pathEffect = dashed
            )
        }
    }
    state.forks.forEach { fork ->
        val start = Offset(xOf(fork.parentColumn), yOf(fork.parentLane))
        val end = Offset(xOf(fork.childColumn), yOf(fork.childLane))
        val midX = (start.x + end.x) / 2f
        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(midX, start.y, midX, end.y, end.x, end.y)
        }
        drawPath(
            path = path,
            color = laneColors[fork.childLane % laneColors.size],
            style = Stroke(width = metrics.stroke, pathEffect = if (fork.isBranch) dashed else null)
        )
    }
}

@Composable
private fun NodeCell(
    node: SaveTimelineNodeUi,
    color: Color,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier
) {
    val theme = LocalArgosyTheme.current
    val card = node.card
    val dotSize = if (card.isCurrent) Dimens.saveTimelineDotCurrent else Dimens.saveTimelineDot
    val hollow = card.isBranch || card.isOlderClient
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.saveTimelineLaneHeight)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = FocusIndicators.Ring,
                    ringColor = theme.textPrimary,
                    shape = CircleShape
                )
                .padding(Dimens.spacingXs),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(if (hollow) theme.surfaceBase else color)
                    .border(Dimens.borderMedium, color, CircleShape)
            )
        }
        if (card.isPinned) {
            PinMark(
                offset = dotSize / 2,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun PinMark(offset: Dp, modifier: Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .offset(x = offset, y = -offset)
            .size(Dimens.saveTimelinePinMark)
            .clip(CircleShape)
            .background(theme.textPrimary)
    )
}

@Composable
private fun LaneLabel(
    lane: SaveTimelineLaneUi,
    color: Color,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val subtitle = when {
        lane.failed -> stringResource(R.string.save_channels_timeline_lane_failed)
        lane.nodes.isEmpty() && !lane.isLoading -> stringResource(R.string.save_channels_timeline_lane_empty)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.saveTimelineLaneHeight)
            .argosyFocusIndicators(focused = isFocused, indicators = LabelFocus, stripeColor = color)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.dotSm)
                .clip(CircleShape)
                .background(color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lane.tile.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (isFocused) theme.textPrimary else theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun SaveTimelineSkeleton() {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(SKELETON_DOTS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.saveTimelineLaneHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(Dimens.saveTimelineLabelWidth)
                        .padding(horizontal = Dimens.spacingMd)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Dimens.spacingMd)
                            .clip(CircleShape)
                            .background(theme.surfaceRaised)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.saveTimelineColumnWidth - Dimens.saveTimelineDot)) {
                    repeat(SKELETON_DOTS) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.saveTimelineDot)
                                .clip(CircleShape)
                                .background(theme.surfaceRaised)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveTimelineFloaterHost(
    state: SaveTimelineUiState,
    listState: LazyListState,
    verticalScroll: ScrollState,
    laneColors: List<Color>,
    coverPath: String?,
    hidden: Boolean,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    onClick: () -> Unit
) {
    val lane = state.focusedLane
    val node = state.focusedNode
    if (lane == null || node == null) return
    val density = LocalDensity.current
    val labelWidthPx = with(density) { Dimens.saveTimelineLabelWidth.toPx() }
    val columnWidthPx = with(density) { Dimens.saveTimelineColumnWidth.toPx() }
    val laneHeightPx = with(density) { Dimens.saveTimelineLaneHeight.toPx() }
    val marginPx = with(density) { Dimens.spacingMd.toPx() }
    val gapPx = laneHeightPx / 2f + with(density) { Dimens.spacingXs.toPx() }
    val info = listState.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == node.column }
    val anchor = item?.let {
        IntOffset(
            x = (labelWidthPx + it.offset - info.viewportStartOffset + columnWidthPx / 2f).roundToInt(),
            y = (state.focusLane * laneHeightPx + laneHeightPx / 2f - verticalScroll.value).roundToInt()
        )
    }
    SaveTimelineFloaterPlacement(
        anchor = anchor.takeUnless { hidden },
        gapPx = gapPx,
        marginPx = marginPx,
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx
    ) {
        SaveTimelineFloater(
            lane = lane,
            node = node,
            laneColor = laneColors[state.focusLane % laneColors.size],
            coverPath = coverPath,
            onClick = onClick
        )
    }
}
