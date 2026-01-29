package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

data class ListSection(
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
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleItems = layoutInfo.visibleItemsInfo
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
    LaunchedEffect(focusedIndex, sections) {
        val isInSection = sections.any { section ->
            focusedIndex in section.focusStartIndex..section.focusEndIndex
        }
        if (!isInSection) return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleItems = layoutInfo.visibleItemsInfo
        val listIndex = focusToListIndex(focusedIndex)

        val targetItem = visibleItems.find { it.index == listIndex }
        val itemHeight = targetItem?.size ?: visibleItems.maxOfOrNull { it.size } ?: 80
        val centerOffset = (viewportHeight - itemHeight) / 2
        listState.animateScrollToItem(listIndex, -centerOffset)
    }
}
