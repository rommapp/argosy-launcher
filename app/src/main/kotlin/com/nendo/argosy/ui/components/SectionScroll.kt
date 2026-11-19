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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.reducedMotion
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs

private const val FAR_JUMP_VIEWPORTS = 2

@Composable
private fun footerOverlapPx(): Int {
    val footerVisible = LocalFooterHostVisible.current && LocalFooterHost.current.isBarVisible
    val density = LocalDensity.current
    val footerPx = with(density) { Dimens.footerClearance.roundToPx() }
    val navBarPx = with(density) { LocalNavBarClearance.current.roundToPx() }
    return (if (footerVisible) footerPx else 0) + navBarPx
}

private fun farJumpSnapIndex(index: Int, visibleCount: Int, first: Int, last: Int): Int? {
    val near = visibleCount.coerceAtLeast(1) * FAR_JUMP_VIEWPORTS
    return when {
        index > last + near -> (index - near).coerceAtLeast(0)
        index < first - near -> index + near
        else -> null
    }
}

private suspend fun LazyListState.snapNearFarItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    val first = firstVisibleItemIndex
    farJumpSnapIndex(index, visible.size, first, visible.lastOrNull()?.index ?: first)
        ?.let { scrollToItem(it) }
}

private suspend fun LazyGridState.snapNearFarItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    val first = firstVisibleItemIndex
    farJumpSnapIndex(index, visible.size, first, visible.lastOrNull()?.index ?: first)
        ?.let { scrollToItem(it) }
}

private suspend fun LazyStaggeredGridState.snapNearFarItem(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    val first = firstVisibleItemIndex
    farJumpSnapIndex(index, visible.size, first, visible.lastOrNull()?.index ?: first)
        ?.let { scrollToItem(it) }
}

/**
 * Scrolls to [index] with animation, but pre-snaps to within a couple of
 * viewports of the target when it is far outside the visible window, so
 * long jumps (letter wrap, section skip) stay short instead of animating
 * across the whole list.
 */
suspend fun LazyListState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    snapNearFarItem(index)
    animateScrollToItem(index, scrollOffset)
}

suspend fun LazyGridState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    snapNearFarItem(index)
    animateScrollToItem(index, scrollOffset)
}

suspend fun LazyStaggeredGridState.fastAnimateScrollToItem(index: Int, scrollOffset: Int = 0) {
    snapNearFarItem(index)
    animateScrollToItem(index, scrollOffset)
}

private suspend fun centerOnItem(
    index: Int,
    animate: Boolean,
    snapFarJumps: Boolean,
    topInsetPx: Int,
    bottomInsetPx: Int,
    totalItems: () -> Int,
    canScroll: () -> Boolean,
    viewportHeight: () -> Int,
    visibleHeights: () -> List<Pair<Int, Int>>,
    snapNear: suspend () -> Unit,
    scrollTo: suspend (offset: Int, animate: Boolean) -> Unit
) {
    if (index < 0) return
    if (totalItems() == 0) snapshotFlow { totalItems() }.first { it > 0 }
    if (index >= totalItems()) return
    if (!canScroll()) return
    if (snapFarJumps && animate) snapNear()
    val visible = visibleHeights()
    if (visible.isEmpty()) {
        scrollTo(0, animate)
        return
    }
    val itemHeight = visible.firstOrNull { it.first == index }?.second
        ?: (visible.sumOf { it.second } / visible.size)
    val band = viewportHeight() - topInsetPx - bottomInsetPx
    scrollTo(-((band - itemHeight) / 2), animate)
}

/**
 * Animates until the item at [index] sits centred in the band left after [topInsetPx] and
 * [bottomInsetPx] of chrome; a top inset expects matching top content padding. An unmeasured
 * list waits for its first layout, one that fits its viewport is left alone, and [snapFarJumps]
 * pre-snaps near a far target as [fastAnimateScrollToItem] does. [reduced] jumps straight there.
 */
suspend fun LazyListState.animateScrollToItemCentered(
    index: Int,
    reduced: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = scrollToItemCentered(index, animate = !reduced, topInsetPx, bottomInsetPx, snapFarJumps)

/**
 * [animateScrollToItemCentered] with the motion optional: [animate] false jumps straight to the
 * centred position, for reduced motion and for restoring a position on first composition.
 */
suspend fun LazyListState.scrollToItemCentered(
    index: Int,
    animate: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = centerOnItem(
    index = index,
    animate = animate,
    snapFarJumps = snapFarJumps,
    topInsetPx = topInsetPx,
    bottomInsetPx = bottomInsetPx,
    totalItems = { layoutInfo.totalItemsCount },
    canScroll = { canScrollForward || canScrollBackward },
    viewportHeight = { layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset },
    visibleHeights = { layoutInfo.visibleItemsInfo.map { it.index to it.size } },
    snapNear = { snapNearFarItem(index) },
    scrollTo = { offset, animated ->
        if (animated) animateScrollToItem(index, offset) else scrollToItem(index, offset)
    }
)

/**
 * The grid form of [LazyListState.animateScrollToItemCentered]. A grid scrolls by rows, so this
 * centres the row the item at [index] sits in.
 */
suspend fun LazyGridState.animateScrollToItemCentered(
    index: Int,
    reduced: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = scrollToItemCentered(index, animate = !reduced, topInsetPx, bottomInsetPx, snapFarJumps)

suspend fun LazyGridState.scrollToItemCentered(
    index: Int,
    animate: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = centerOnItem(
    index = index,
    animate = animate,
    snapFarJumps = snapFarJumps,
    topInsetPx = topInsetPx,
    bottomInsetPx = bottomInsetPx,
    totalItems = { layoutInfo.totalItemsCount },
    canScroll = { canScrollForward || canScrollBackward },
    viewportHeight = { layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset },
    visibleHeights = { layoutInfo.visibleItemsInfo.map { it.index to it.size.height } },
    snapNear = { snapNearFarItem(index) },
    scrollTo = { offset, animated ->
        if (animated) animateScrollToItem(index, offset) else scrollToItem(index, offset)
    }
)

/**
 * The staggered-grid form of [LazyListState.animateScrollToItemCentered]; lanes of differing
 * heights centre on the item's own height.
 */
suspend fun LazyStaggeredGridState.animateScrollToItemCentered(
    index: Int,
    reduced: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = scrollToItemCentered(index, animate = !reduced, topInsetPx, bottomInsetPx, snapFarJumps)

suspend fun LazyStaggeredGridState.scrollToItemCentered(
    index: Int,
    animate: Boolean,
    topInsetPx: Int = 0,
    bottomInsetPx: Int = 0,
    snapFarJumps: Boolean = false
) = centerOnItem(
    index = index,
    animate = animate,
    snapFarJumps = snapFarJumps,
    topInsetPx = topInsetPx,
    bottomInsetPx = bottomInsetPx,
    totalItems = { layoutInfo.totalItemsCount },
    canScroll = { canScrollForward || canScrollBackward },
    viewportHeight = { layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset },
    visibleHeights = { layoutInfo.visibleItemsInfo.map { it.index to it.size.height } },
    snapNear = { snapNearFarItem(index) },
    scrollTo = { offset, animated ->
        if (animated) animateScrollToItem(index, offset) else scrollToItem(index, offset)
    }
)

/**
 * Whether a focus-follow scroll moves the list. The first pass always places the list; after
 * that, a focus change made while [isTouchMode] holds leaves the list where the finger put it.
 */
fun shouldFollowFocus(isInitialPass: Boolean, isTouchMode: Boolean): Boolean =
    isInitialPass || !isTouchMode

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
    focusedIndex: Int,
    isTouchMode: Boolean = false
) {
    var isInitialPass by remember(listState) { mutableStateOf(true) }
    val touchMode by rememberUpdatedState(isTouchMode)
    val reduced by rememberUpdatedState(reducedMotion())
    val footerPx = footerOverlapPx()

    LaunchedEffect(focusedIndex, footerPx) {
        if (focusedIndex < 0) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            listState.scrollToItem(focusedIndex)
            return@LaunchedEffect
        }
        if (!listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val firstPass = isInitialPass
        isInitialPass = false
        if (!shouldFollowFocus(firstPass, touchMode)) return@LaunchedEffect
        val instant = firstPass || reduced

        val visibleEnd = layoutInfo.viewportEndOffset - footerPx
        val viewportHeight = visibleEnd - layoutInfo.viewportStartOffset
        val targetItem = visibleItems.find { it.index == focusedIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val lastListIndex = layoutInfo.totalItemsCount - 1

        if (focusedIndex >= lastListIndex) {
            val alreadyWhole = targetItem != null &&
                targetItem.offset >= layoutInfo.viewportStartOffset &&
                targetItem.offset + targetItem.size <= visibleEnd
            if (alreadyWhole) return@LaunchedEffect
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

/**
 * Header-lock scrolling for a sectioned pane. When focus enters a different section, the list
 * scrolls so that section's header (its list index from [headerListIndexOf]) snaps to the top of
 * the viewport. While focus moves inside a section the focused item is kept fully in view below
 * any pinned sticky header ([headerListIndices]) with the smallest scroll that does so, so the
 * header stays put until the next section pushes it off.
 */
@Composable
fun SectionHeaderLockScroll(
    listState: LazyListState,
    focusedIndex: Int,
    focusToListIndex: (Int) -> Int,
    sections: List<ListSection>,
    headerListIndexOf: (ListSection) -> Int,
    headerListIndices: Set<Int>,
    isTouchMode: Boolean = false
) {
    var previousSection by remember(listState) { mutableIntStateOf(Int.MIN_VALUE) }
    var isInitialPass by remember(listState) { mutableStateOf(true) }
    val touchMode by rememberUpdatedState(isTouchMode)
    val reduced by rememberUpdatedState(reducedMotion())
    val footerPx = footerOverlapPx()

    LaunchedEffect(focusedIndex, sections, footerPx) {
        val sectionIndex = sections.indexOfLast { focusedIndex >= it.focusStartIndex }
        val sectionChanged = sectionIndex != previousSection
        previousSection = sectionIndex
        val firstPass = isInitialPass
        isInitialPass = false
        if (!shouldFollowFocus(firstPass, touchMode)) return@LaunchedEffect
        val instant = firstPass || reduced

        if (listState.layoutInfo.totalItemsCount == 0) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        }
        val listIndex = focusToListIndex(focusedIndex)
        if (sectionChanged && sectionIndex >= 0) {
            val header = headerListIndexOf(sections[sectionIndex])
                .coerceIn(0, listState.layoutInfo.totalItemsCount - 1)
            if (instant) listState.scrollToItem(header, 0) else listState.fastAnimateScrollToItem(header, 0)
            listState.keepFocusedVisible(listIndex, headerListIndices, instant = true, footerPx)
            return@LaunchedEffect
        }
        listState.keepFocusedVisible(listIndex, headerListIndices, instant, footerPx)
    }
}

private suspend fun LazyListState.keepFocusedVisible(
    listIndex: Int,
    headerListIndices: Set<Int>,
    instant: Boolean,
    footerPx: Int
) {
    if (listIndex < 0 || listIndex >= layoutInfo.totalItemsCount) return
    if (!canScrollForward && !canScrollBackward) return
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == listIndex }
    if (item == null) {
        if (instant) scrollToItem(listIndex) else fastAnimateScrollToItem(listIndex)
        return
    }
    val info = layoutInfo
    val pinnedHeader = info.visibleItemsInfo
        .filter { it.index in headerListIndices && it.index < listIndex && it.offset <= info.viewportStartOffset }
        .maxOfOrNull { it.offset + it.size - info.viewportStartOffset }
        ?.coerceAtLeast(0)
        ?: 0
    val safeTop = info.viewportStartOffset + pinnedHeader
    val safeBottom = info.viewportEndOffset - footerPx
    val itemBottom = item.offset + item.size
    val delta = when {
        item.offset < safeTop -> item.offset - safeTop
        itemBottom > safeBottom -> minOf(itemBottom - safeBottom, item.offset - safeTop)
        else -> 0
    }
    if (delta == 0) return
    if (instant) scrollBy(delta.toFloat()) else animateScrollBy(delta.toFloat())
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
    val bottomInsetPx = with(density) { bottomInset.roundToPx() } + footerOverlapPx()
    var isInitialPass by remember(gridState) { mutableStateOf(true) }
    val reduced by rememberUpdatedState(reducedMotion())

    LaunchedEffect(focusedIndex, topInsetPx, bottomInsetPx) {
        if (focusedIndex < 0) return@LaunchedEffect

        val instant = isInitialPass || reduced
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
    sections: List<ListSection>,
    isTouchMode: Boolean = false
) {
    var previousFocusIndex by remember { mutableIntStateOf(focusedIndex) }
    var isInitialPass by remember(listState) { mutableStateOf(true) }
    val touchMode by rememberUpdatedState(isTouchMode)
    val reduced by rememberUpdatedState(reducedMotion())
    val footerPx = footerOverlapPx()

    LaunchedEffect(focusedIndex, footerPx) {
        val jumped = abs(focusedIndex - previousFocusIndex) > 1
        previousFocusIndex = focusedIndex

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty() && !listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val firstPass = isInitialPass
        isInitialPass = false
        if (!shouldFollowFocus(firstPass, touchMode)) return@LaunchedEffect
        val instant = jumped || firstPass || reduced

        val viewportHeight = layoutInfo.viewportEndOffset - footerPx - layoutInfo.viewportStartOffset
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
