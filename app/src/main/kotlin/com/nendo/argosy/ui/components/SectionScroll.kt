package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

internal enum class FocusedScrollBehavior {
    KEEP,
    SNAP,
    ANIMATE
}

internal fun focusedScrollBehavior(
    isInitialPass: Boolean,
    isTargetFullyVisible: Boolean,
    jumped: Boolean = false
): FocusedScrollBehavior = when {
    isInitialPass && isTargetFullyVisible -> FocusedScrollBehavior.KEEP
    isInitialPass || jumped -> FocusedScrollBehavior.SNAP
    else -> FocusedScrollBehavior.ANIMATE
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
        val initialPass = isInitialPass
        isInitialPass = false
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            listState.scrollToItem(focusedIndex)
            return@LaunchedEffect
        }
        if (!listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetItem = visibleItems.find { it.index == focusedIndex }
        val isTargetFullyVisible = targetItem != null &&
            targetItem.offset >= layoutInfo.viewportStartOffset &&
            targetItem.offset + targetItem.size <= layoutInfo.viewportEndOffset
        val behavior = focusedScrollBehavior(initialPass, isTargetFullyVisible)
        if (behavior == FocusedScrollBehavior.KEEP) {
            return@LaunchedEffect
        }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val lastListIndex = layoutInfo.totalItemsCount - 1

        suspend fun scroll(index: Int, offset: Int) {
            if (behavior == FocusedScrollBehavior.SNAP) {
                listState.scrollToItem(index, offset)
            } else {
                listState.animateScrollToItem(index, offset)
            }
        }

        if (focusedIndex >= lastListIndex) {
            val bottomAlignOffset = if (targetItem != null) itemHeight - viewportHeight else 0
            scroll(lastListIndex, bottomAlignOffset)
            return@LaunchedEffect
        }

        val centerOffset = (viewportHeight - itemHeight) / 2
        scroll(focusedIndex, -centerOffset)
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
        val initialPass = isInitialPass
        isInitialPass = false
        val jumped = abs(focusedIndex - previousFocusIndex) > 1
        previousFocusIndex = focusedIndex

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isNotEmpty() && !listState.canScrollForward && !listState.canScrollBackward) {
            return@LaunchedEffect
        }

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val listIndex = focusToListIndex(focusedIndex)
        val targetItem = visibleItems.find { it.index == listIndex }
        val isTargetFullyVisible = targetItem != null &&
            targetItem.offset >= layoutInfo.viewportStartOffset &&
            targetItem.offset + targetItem.size <= layoutInfo.viewportEndOffset
        val behavior = focusedScrollBehavior(initialPass, isTargetFullyVisible, jumped)
        if (behavior == FocusedScrollBehavior.KEEP) {
            return@LaunchedEffect
        }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val lastListIndex = layoutInfo.totalItemsCount - 1

        suspend fun scroll(index: Int, offset: Int) {
            if (behavior == FocusedScrollBehavior.SNAP) {
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
