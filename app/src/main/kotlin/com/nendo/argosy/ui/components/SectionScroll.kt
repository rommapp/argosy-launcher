package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs

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
    LaunchedEffect(focusedIndex) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) {
            listState.scrollToItem(focusedIndex)
            return@LaunchedEffect
        }

        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetItem = visibleItems.find { it.index == focusedIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val centerOffset = (viewportHeight - itemHeight) / 2
        listState.animateScrollToItem(focusedIndex, -centerOffset)
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

    LaunchedEffect(focusedIndex) {
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleItems = layoutInfo.visibleItemsInfo
        val listIndex = focusToListIndex(focusedIndex)

        val targetItem = visibleItems.find { it.index == listIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val centerOffset = (viewportHeight - itemHeight) / 2

        val jumped = abs(focusedIndex - previousFocusIndex) > 1
        previousFocusIndex = focusedIndex
        if (jumped) {
            listState.scrollToItem(listIndex, -centerOffset)
        } else {
            listState.animateScrollToItem(listIndex, -centerOffset)
        }
    }
}
