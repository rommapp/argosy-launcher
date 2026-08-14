package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.util.verticalEdgeFade
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.AutoGridConfig
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What a d-pad press resolves to inside the grid. Callers act on the verdict rather than deriving
 * one from a direction, so the boundary policy lives here and cannot drift between the surfaces
 * that host the grid.
 */
sealed interface AutoGridMove {
    data class Focus(val index: Int) : AutoGridMove
    data object PreviousSection : AutoGridMove
    data object NextSection : AutoGridMove
    data object None : AutoGridMove
}

/**
 * Resolves moving [direction] from [currentIndex] over [itemCount] items.
 *
 * Movement across the lanes is confined to the lane group it starts in, so the edge of a visual row
 * is a real boundary rather than a wrap onto the next one, and pressing into it leaves the section.
 * That is the axis the lanes run across, which flips with the scroll direction: left and right for a
 * vertical grid, up and down for a horizontal one. Travel along the scroll axis just runs out at the
 * ends, because that is the direction the section itself extends in.
 */
fun autoGridMove(
    itemCount: Int,
    config: AutoGridConfig,
    currentIndex: Int,
    direction: GridDirection
): AutoGridMove {
    if (itemCount <= 0) return AutoGridMove.None
    val lanes = config.laneCount.coerceAtLeast(1)
    val last = itemCount - 1
    val index = currentIndex.coerceIn(0, last)
    val forward = direction == GridDirection.RIGHT || direction == GridDirection.DOWN
    val acrossLanes = when (config.scrollAxis) {
        HomeScrollAxis.VERTICAL -> direction == GridDirection.LEFT || direction == GridDirection.RIGHT
        HomeScrollAxis.HORIZONTAL -> direction == GridDirection.UP || direction == GridDirection.DOWN
    }
    val leaving = if (forward) AutoGridMove.NextSection else AutoGridMove.PreviousSection
    if (acrossLanes) {
        val lane = index % lanes
        if (if (forward) lane == lanes - 1 else lane == 0) return leaving
        val target = if (forward) index + 1 else index - 1
        return if (target in 0..last) AutoGridMove.Focus(target) else leaving
    }
    val target = if (forward) index + lanes else index - lanes
    return when {
        target in 0..last -> AutoGridMove.Focus(target)
        forward && index / lanes < last / lanes -> AutoGridMove.Focus(last)
        else -> AutoGridMove.None
    }
}

/**
 * The auto grid: one home section's covers flowing in a fixed number of lanes, scrolling on the
 * configured axis. Lanes read as columns when scrolling vertically and as rows when scrolling
 * horizontally, so the same number means the same thing the picker previewed.
 *
 * Owns no focus state; [focusedIndex] indexes [items] and the caller drives it.
 */
@Composable
fun HomeAutoGrid(
    items: List<CarouselItem>,
    focusedIndex: Int,
    config: AutoGridConfig,
    gridState: LazyGridState,
    onItemTap: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPlatformBadge: Boolean = true,
    downloadIndicatorFor: (CarouselItem) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null
) {
    AutoGridFocusSync(gridState, focusedIndex, items.size, config)
    Column(modifier = modifier.fillMaxSize()) {
        if (config.showTitles) {
            AutoGridTitle(item = items.getOrNull(focusedIndex))
        }
        val lanes = config.laneCount.coerceAtLeast(1)
        val cells = GridCells.Fixed(lanes)
        val spacing = Arrangement.spacedBy(Dimens.spacingMd)
        var measured by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { measured = it }
        ) {
            val across = if (config.scrollAxis == HomeScrollAxis.VERTICAL) measured.width else measured.height
            val metrics = rememberAutoGridMetrics(
                available = with(density) { across.toDp() },
                lanes = lanes,
                scrollAxis = config.scrollAxis,
                coverAspectRatio = LocalBoxArtStyle.current.aspectRatio,
            )
            val padding = metrics.padding
            val cell: @Composable (Int, CarouselItem) -> Unit = { index, item ->
                AutoGridCell(
                    item = item,
                    isFocused = index == focusedIndex,
                    showTitle = false,
                    showPlatformBadge = showPlatformBadge,
                    downloadIndicator = downloadIndicatorFor(item),
                    cellWidth = metrics.cellWidth,
                    onTap = { onItemTap(index) },
                    onLongPress = { onItemLongPress(index) },
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded
                )
            }
            when (config.scrollAxis) {
                HomeScrollAxis.VERTICAL -> LazyVerticalGrid(
                    columns = cells,
                    state = gridState,
                    contentPadding = padding,
                    horizontalArrangement = spacing,
                    verticalArrangement = spacing,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalEdgeFade(gridState, fadeHeight = Dimens.spacingXl)
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item -> cell(index, item) }
                }
                HomeScrollAxis.HORIZONTAL -> LazyHorizontalGrid(
                    rows = cells,
                    state = gridState,
                    contentPadding = padding,
                    horizontalArrangement = spacing,
                    verticalArrangement = spacing,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalEdgeFade(gridState, fadeHeight = Dimens.spacingXl)
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item -> cell(index, item) }
                }
            }
        }
    }
}

/**
 * Sizing for one grid: the content padding, and the width a cell must be told to take.
 *
 * @param cellWidth non-null only for a horizontal grid, where the scroll axis leaves a cell's width
 *   unbounded. Left to itself a cell there is as wide as its untruncated title and a cover sized off
 *   the full lane height, so lanes gape and covers push their titles out of the lane.
 */
private data class AutoGridMetrics(val padding: PaddingValues, val cellWidth: Dp?)

/**
 * Padding is widened past the edge inset so a focused cover, which grows about its own centre, still
 * lands inside the viewport the grid clips to; the growth is measured off the cover rather than the
 * cell because the title beneath it does not scale.
 *
 * [available] is the measured extent across the lanes rather than one read from BoxWithConstraints:
 * subcomposing the grid would defer its cells to the layout pass, where a focus change alone
 * invalidates nothing, and the highlight would sit still while the list scrolled under it.
 */
@Composable
private fun rememberAutoGridMetrics(
    available: Dp,
    lanes: Int,
    scrollAxis: HomeScrollAxis,
    coverAspectRatio: Float
): AutoGridMetrics {
    val edge = Dimens.spacingMd
    val gap = Dimens.spacingMd
    val overhang = (ComponentDefaults.Focus.scaleFocused - 1f) / 2f
    val gaps = gap * (lanes - 1)
    val roughLane = ((available - edge * 2 - gaps) / lanes).coerceAtLeast(0.dp)
    val vertical = scrollAxis == HomeScrollAxis.VERTICAL
    val roughCross = roughLane
    val crossPad = edge + roughCross * overhang
    val lane = ((available - crossPad * 2 - gaps) / lanes).coerceAtLeast(0.dp)
    return if (vertical) {
        val coverHeight = lane / coverAspectRatio
        AutoGridMetrics(
            padding = PaddingValues(
                horizontal = crossPad,
                vertical = edge + coverHeight * overhang
            ),
            cellWidth = null
        )
    } else {
        val coverHeight = lane
        val coverWidth = coverHeight * coverAspectRatio
        AutoGridMetrics(
            padding = PaddingValues(
                horizontal = edge + coverWidth * overhang,
                vertical = crossPad
            ),
            cellWidth = coverWidth
        )
    }
}

/**
 * Anchors the focused row to one of three slots: the first row sits flush against the start of the
 * content, the last row flush against the end, and everything between is centred.
 *
 * Scrolling by the smallest amount that reveals a row is what produces the clipping. A row here is
 * close to a full viewport tall, so "just barely visible" and "cut off by the header" are the same
 * position, and the amount scrolled differs every time. Three fixed slots make the travel
 * predictable and leave the focused row whole. Moving within a row resolves to the slot it already
 * occupies, so the grid stays still until focus actually changes rows.
 */
@Composable
private fun AutoGridFocusSync(
    gridState: LazyGridState,
    focusedIndex: Int,
    itemCount: Int,
    config: AutoGridConfig
) {
    LaunchedEffect(focusedIndex, itemCount, config) {
        if (itemCount <= 0) return@LaunchedEffect
        val lanes = config.laneCount.coerceAtLeast(1)
        val target = focusedIndex.coerceIn(0, itemCount - 1)
        val info = gridState.layoutInfo
        val gauge = info.visibleItemsInfo.firstOrNull() ?: return@LaunchedEffect
        val vertical = config.scrollAxis == HomeScrollAxis.VERTICAL
        val itemExtent = if (vertical) gauge.size.height else gauge.size.width
        val viewportExtent = if (vertical) info.viewportSize.height else info.viewportSize.width
        val contentExtent = viewportExtent - info.beforeContentPadding - info.afterContentPadding
        val slack = (contentExtent - itemExtent).coerceAtLeast(0)
        val row = target / lanes
        val lastRow = (itemCount - 1) / lanes
        val offsetFromContentStart = when (row) {
            0 -> 0
            lastRow -> slack
            else -> slack / 2
        }
        gridState.animateScrollToItem(target, -offsetFromContentStart)
    }
}

/**
 * The line above the grid, naming what the cursor is on. Media answers it in two parts because the
 * tile is the show and the press starts one episode of it, and a grid that named only the show would
 * leave which episode to be guessed at.
 */
@Composable
private fun AutoGridTitle(item: CarouselItem?) {
    val heading = when (item) {
        is CarouselItem.Game -> item.game.title
        is CarouselItem.Media -> item.media.title
        else -> ""
    }
    val subheading = (item as? CarouselItem.Media)?.media?.subtitle
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
        )
        if (subheading != null) {
            Text(
                text = subheading,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingMd)
                    .padding(bottom = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun AutoGridCell(
    item: CarouselItem,
    isFocused: Boolean,
    showTitle: Boolean,
    showPlatformBadge: Boolean,
    downloadIndicator: GameDownloadIndicator,
    cellWidth: Dp?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?
) {
    val coverAspectRatio = LocalBoxArtStyle.current.aspectRatio
    Column(modifier = if (cellWidth != null) Modifier.width(cellWidth) else Modifier.fillMaxWidth()) {
        when (item) {
            is CarouselItem.Game -> GameCard(
                game = item.game,
                isFocused = isFocused,
                focusScale = ComponentDefaults.Focus.scaleFocused,
                downloadIndicator = downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                coverPathOverride = item.coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
                    .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
            )
            is CarouselItem.Media -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio),
                contentAlignment = Alignment.Center
            ) {
                val posterRatio = mediaPosterAspectRatio
                val fitByHeight = posterRatio <= coverAspectRatio
                MediaCard(
                    media = item.media,
                    isFocused = isFocused,
                    modifier = Modifier
                        .then(if (fitByHeight) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                        .aspectRatio(posterRatio, matchHeightConstraintsFirst = fitByHeight)
                        .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
                )
            }
            is CarouselItem.ViewAll -> ViewAllCard(
                isFocused = isFocused,
                onClick = onTap,
                remainingCount = item.remainingCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(coverAspectRatio)
            )
        }
        if (showTitle && item is CarouselItem.Game) {
            Text(
                text = item.game.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}
