package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.AutoGridConfig
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeSectionStyle
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Where focus lands moving [direction] from [currentIndex] over [itemCount] items, or null when
 * the move runs off the grid.
 *
 * Movement across the lanes is confined to the lane group it starts in, so the edge of a visual row
 * is a real boundary rather than a wrap onto the next one. LEFT and RIGHT always end up being the
 * boundary that matters, whichever way the grid scrolls: with a vertical grid they step between
 * columns of one row, with a horizontal grid they step between columns outright. A caller can
 * therefore treat a null from LEFT or RIGHT as "leave this section" without inspecting the axis.
 */
fun autoGridStep(
    itemCount: Int,
    config: AutoGridConfig,
    currentIndex: Int,
    direction: GridDirection
): Int? {
    if (itemCount <= 0) return null
    val lanes = config.laneCount.coerceAtLeast(1)
    val last = itemCount - 1
    val index = currentIndex.coerceIn(0, last)
    val forward = direction == GridDirection.RIGHT || direction == GridDirection.DOWN
    val acrossLanes = when (config.scrollAxis) {
        HomeScrollAxis.VERTICAL -> direction == GridDirection.LEFT || direction == GridDirection.RIGHT
        HomeScrollAxis.HORIZONTAL -> direction == GridDirection.UP || direction == GridDirection.DOWN
    }
    if (acrossLanes) {
        val lane = index % lanes
        if (if (forward) lane == lanes - 1 else lane == 0) return null
        return (if (forward) index + 1 else index - 1).takeIf { it in 0..last }
    }
    val target = if (forward) index + lanes else index - lanes
    return when {
        target in 0..last -> target
        forward && index / lanes < last / lanes -> last
        else -> null
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
    sectionTitle: String,
    onItemTap: (Int) -> Unit,
    onItemLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showSectionTitle: Boolean = true,
    showPlatformBadge: Boolean = true,
    downloadIndicatorFor: (CarouselItem) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null
) {
    AutoGridFocusSync(gridState, focusedIndex, items.size, config)
    Column(modifier = modifier.fillMaxSize()) {
        if (showSectionTitle && config.sectionStyle == HomeSectionStyle.HEADINGS) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = Dimens.spacingMd,
                    bottom = Dimens.spacingSm
                )
            )
        }
        val lanes = config.laneCount.coerceAtLeast(1)
        val cells = GridCells.Fixed(lanes)
        val spacing = Arrangement.spacedBy(Dimens.spacingSm)
        var measured by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val titleStyle = MaterialTheme.typography.labelSmall
        val titleAllowance = if (config.showTitles) {
            with(density) { titleStyle.lineHeight.toDp() } + Dimens.spacingXs
        } else {
            0.dp
        }
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
                titleAllowance = titleAllowance
            )
            val padding = metrics.padding
            val cell: @Composable (Int, CarouselItem) -> Unit = { index, item ->
                AutoGridCell(
                    item = item,
                    isFocused = index == focusedIndex,
                    showTitle = config.showTitles,
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(items, key = { _, item -> item.key }) { index, item -> cell(index, item) }
                }
                HomeScrollAxis.HORIZONTAL -> LazyHorizontalGrid(
                    rows = cells,
                    state = gridState,
                    contentPadding = padding,
                    horizontalArrangement = spacing,
                    verticalArrangement = spacing,
                    modifier = Modifier.fillMaxSize()
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
    coverAspectRatio: Float,
    titleAllowance: Dp
): AutoGridMetrics {
    val edge = Dimens.spacingMd
    val gap = Dimens.spacingSm
    val overhang = (ComponentDefaults.Focus.scaleFocused - 1f) / 2f
    val gaps = gap * (lanes - 1)
    val roughLane = ((available - edge * 2 - gaps) / lanes).coerceAtLeast(0.dp)
    val vertical = scrollAxis == HomeScrollAxis.VERTICAL
    val roughCross = if (vertical) roughLane else (roughLane - titleAllowance).coerceAtLeast(0.dp)
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
        val coverHeight = (lane - titleAllowance).coerceAtLeast(0.dp)
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
