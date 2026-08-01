package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
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
    AutoGridFocusSync(gridState, focusedIndex, items.size, config.scrollAxis)
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
        val cells = GridCells.Fixed(config.laneCount.coerceAtLeast(1))
        val spacing = Arrangement.spacedBy(Dimens.spacingSm)
        val padding = PaddingValues(
            horizontal = Dimens.spacingMd,
            vertical = Dimens.spacingSm
        )
        val cell: @Composable (Int, CarouselItem) -> Unit = { index, item ->
            AutoGridCell(
                item = item,
                isFocused = index == focusedIndex,
                showTitle = config.showTitles,
                showPlatformBadge = showPlatformBadge,
                downloadIndicator = downloadIndicatorFor(item),
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

/**
 * Keeps the focused cell on screen without moving the grid under it. Only a cell that is off the
 * viewport or clipped by its edge causes a scroll, and then by exactly the amount that brings it
 * fully into view: the d-pad has to read as a cursor travelling over a still grid, which it stops
 * doing the moment every press re-anchors the list.
 */
@Composable
private fun AutoGridFocusSync(
    gridState: LazyGridState,
    focusedIndex: Int,
    itemCount: Int,
    scrollAxis: HomeScrollAxis
) {
    LaunchedEffect(focusedIndex, itemCount, scrollAxis) {
        if (itemCount <= 0) return@LaunchedEffect
        val target = focusedIndex.coerceIn(0, itemCount - 1)
        val info = gridState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target }
        if (item == null) {
            gridState.animateScrollToItem(target)
            return@LaunchedEffect
        }
        val vertical = scrollAxis == HomeScrollAxis.VERTICAL
        val start = if (vertical) item.offset.y else item.offset.x
        val end = start + if (vertical) item.size.height else item.size.width
        val delta = when {
            start < info.viewportStartOffset -> start - info.viewportStartOffset
            end > info.viewportEndOffset -> end - info.viewportEndOffset
            else -> 0
        }
        if (delta != 0) gridState.animateScrollBy(delta.toFloat())
    }
}

@Composable
private fun AutoGridCell(
    item: CarouselItem,
    isFocused: Boolean,
    showTitle: Boolean,
    showPlatformBadge: Boolean,
    downloadIndicator: GameDownloadIndicator,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCoverLoadFailed: ((Long, String) -> Unit)?,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)?
) {
    val coverAspectRatio = LocalBoxArtStyle.current.aspectRatio
    Column(modifier = Modifier.fillMaxWidth()) {
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
