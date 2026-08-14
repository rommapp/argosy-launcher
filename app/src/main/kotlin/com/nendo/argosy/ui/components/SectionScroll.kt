package com.nendo.argosy.ui.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val FAR_JUMP_VIEWPORTS = 2

/**
 * Scrolls to [index] with animation, but pre-snaps to within a couple of
 * viewports of the target when it is far outside the visible window, so
 * long jumps (letter wrap, section skip) stay short instead of animating
 * across the whole list.
 */
suspend fun LazyListState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    val visible = layoutInfo.visibleItemsInfo
    val near = visible.size.coerceAtLeast(1) * FAR_JUMP_VIEWPORTS
    val first = firstVisibleItemIndex
    val last = visible.lastOrNull()?.index ?: first
    if (index > last + near) {
        scrollToItem((index - near).coerceAtLeast(0))
    } else if (index < first - near) {
        scrollToItem(index + near)
    }
    animateScrollToItem(index, scrollOffset)
}

suspend fun LazyGridState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    val visible = layoutInfo.visibleItemsInfo
    val near = visible.size.coerceAtLeast(1) * FAR_JUMP_VIEWPORTS
    val first = firstVisibleItemIndex
    val last = visible.lastOrNull()?.index ?: first
    if (index > last + near) {
        scrollToItem((index - near).coerceAtLeast(0))
    } else if (index < first - near) {
        scrollToItem(index + near)
    }
    animateScrollToItem(index, scrollOffset)
}

suspend fun LazyStaggeredGridState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    val visible = layoutInfo.visibleItemsInfo
    val near = visible.size.coerceAtLeast(1) * FAR_JUMP_VIEWPORTS
    val first = firstVisibleItemIndex
    val last = visible.lastOrNull()?.index ?: first
    if (index > last + near) {
        scrollToItem((index - near).coerceAtLeast(0))
    } else if (index < first - near) {
        scrollToItem(index + near)
    }
    animateScrollToItem(index, scrollOffset)
}

data class ListSection(
    val name: String? = null,
    val listStartIndex: Int,
    val listEndIndex: Int,
    val focusStartIndex: Int,
    val focusEndIndex: Int
)

@Composable
fun FocusedScroll(
    listState: LazyListState,
    focusedIndex: Int
) {
    var isInitialPass by remember(listState) { mutableStateOf(true) }

    LaunchedEffect(focusedIndex) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            listState.scrollToItem(focusedIndex)
            return@LaunchedEffect
        }
        if (!listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val instant = isInitialPass
        isInitialPass = false

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetItem = visibleItems.find { it.index == focusedIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val lastListIndex = layoutInfo.totalItemsCount - 1

        if (focusedIndex >= lastListIndex) {
            val bottomAlignOffset = if (targetItem != null) itemHeight - viewportHeight else 0
            if (instant) {
                listState.scrollToItem(lastListIndex, bottomAlignOffset)
            } else {
                listState.animateScrollToItem(lastListIndex, bottomAlignOffset)
            }
            return@LaunchedEffect
        }

        val centerOffset = (viewportHeight - itemHeight) / 2
        if (instant) {
            listState.scrollToItem(focusedIndex, -centerOffset)
        } else {
            listState.animateScrollToItem(focusedIndex, -centerOffset)
        }
    }
}

private fun LazyGridState.focusedItem(index: Int): LazyGridItemInfo? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

/**
 * Keeps the focused cell of a grid inside the band the user can actually see.
 *
 * [topInset] and [bottomInset] describe the chrome drawn OVER the grid - a header, a footer - which
 * a lazy grid knows nothing about: its own content padding only holds the ends of the list clear,
 * and scrolling to an item parks that item against the container edge, underneath whatever overlaps
 * it. The correction is measured from the laid-out item rather than requested as a scroll offset, so
 * it lands the same way whether the grid is at the top of the list, the bottom, or anywhere between,
 * and it moves the grid no further than it takes to clear the chrome.
 *
 * The caller is expected to reserve the same insets as content padding; the ends of the list scroll
 * under the chrome otherwise.
 */
@Composable
fun GridFocusedScroll(
    gridState: LazyGridState,
    focusedIndex: Int,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp
) {
    val density = LocalDensity.current
    val topInsetPx = with(density) { topInset.roundToPx() }
    val bottomInsetPx = with(density) { bottomInset.roundToPx() }
    var isInitialPass by remember(gridState) { mutableStateOf(true) }

    LaunchedEffect(focusedIndex, topInsetPx, bottomInsetPx) {
        if (focusedIndex < 0) return@LaunchedEffect

        val instant = isInitialPass
        isInitialPass = false

        if (gridState.layoutInfo.totalItemsCount == 0) {
            snapshotFlow { gridState.layoutInfo.totalItemsCount }.first { it > 0 }
        }
        if (focusedIndex >= gridState.layoutInfo.totalItemsCount) return@LaunchedEffect

        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == focusedIndex }) {
            gridState.scrollToItem(focusedIndex)
        }

        val item = gridState.focusedItem(focusedIndex)
            ?: snapshotFlow { gridState.focusedItem(focusedIndex) }.filterNotNull().first()
        val layoutInfo = gridState.layoutInfo

        val safeTop = layoutInfo.viewportStartOffset + topInsetPx
        val safeBottom = layoutInfo.viewportEndOffset - bottomInsetPx
        val itemTop = item.offset.y
        val itemBottom = itemTop + item.size.height

        val delta = when {
            itemTop < safeTop -> itemTop - safeTop
            itemBottom > safeBottom -> minOf(itemBottom - safeBottom, itemTop - safeTop)
            else -> 0
        }
        if (delta == 0) return@LaunchedEffect

        if (instant) {
            gridState.scrollBy(delta.toFloat())
        } else {
            gridState.animateScrollBy(delta.toFloat())
        }
    }
}

@Composable
fun SectionFocusedScroll(
    listState: LazyListState,
    focusedIndex: Int,
    focusToListIndex: (Int) -> Int,
    sections: List<ListSection>
) {
    var previousFocusIndex by remember { mutableIntStateOf(focusedIndex) }
    var isInitialPass by remember(listState) { mutableStateOf(true) }

    LaunchedEffect(focusedIndex) {
        val jumped = abs(focusedIndex - previousFocusIndex) > 1
        previousFocusIndex = focusedIndex

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty() && !listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val instant = jumped || isInitialPass
        isInitialPass = false

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val listIndex = focusToListIndex(focusedIndex)
        val targetItem = visibleItems.find { it.index == listIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val lastListIndex = layoutInfo.totalItemsCount - 1

        suspend fun scroll(index: Int, offset: Int) {
            if (instant) {
                listState.scrollToItem(index, offset)
            } else {
                listState.animateScrollToItem(index, offset)
            }
        }

        val firstFocusable = sections.firstOrNull()?.focusStartIndex
        if (firstFocusable != null && focusedIndex <= firstFocusable) {
            scroll(0, 0)
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == listIndex }) {
                scroll(listIndex, itemHeight - viewportHeight)
            }
            return@LaunchedEffect
        }

        val lastFocusable = sections.lastOrNull()?.focusEndIndex
        if (lastFocusable != null && focusedIndex >= lastFocusable && lastListIndex >= 0) {
            val lastItem = visibleItems.find { it.index == lastListIndex }
            val bottomAlignOffset = if (lastItem != null) lastItem.size - viewportHeight else 0
            scroll(lastListIndex, bottomAlignOffset)
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == listIndex }) {
                scroll(listIndex, 0)
            }
            return@LaunchedEffect
        }

        val centerOffset = (viewportHeight - itemHeight) / 2
        scroll(listIndex, -centerOffset)
    }
}
