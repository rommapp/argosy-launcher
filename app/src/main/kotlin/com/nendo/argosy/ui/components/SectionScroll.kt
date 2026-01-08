package com.nendo.argosy.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

data class ListSection(
    val listStartIndex: Int,
    val listEndIndex: Int,
    val focusStartIndex: Int,
    val focusEndIndex: Int
)

@Composable
fun SectionFocusedScroll(
    listState: LazyListState,
    focusedIndex: Int,
    focusToListIndex: (Int) -> Int,
    sections: List<ListSection>
) {
    val previousSection = remember { mutableIntStateOf(-1) }
    val sectionItemCounts = remember { mutableMapOf<Int, Int>() }

    LaunchedEffect(focusedIndex, sections) {
        val currentSectionIndex = sections.indexOfFirst { section ->
            focusedIndex in section.focusStartIndex..section.focusEndIndex
        }
        if (currentSectionIndex == -1) return@LaunchedEffect

        val currentSection = sections[currentSectionIndex]
        val sectionItemCount = currentSection.listEndIndex - currentSection.listStartIndex + 1
        val sectionChanged = currentSectionIndex != previousSection.intValue
        val previousItemCount = sectionItemCounts[currentSectionIndex] ?: -1
        val sectionSizeChanged = sectionItemCount != previousItemCount

        previousSection.intValue = currentSectionIndex
        sectionItemCounts[currentSectionIndex] = sectionItemCount

        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleItems = layoutInfo.visibleItemsInfo

        val sectionItems = visibleItems.filter { it.index in currentSection.listStartIndex..currentSection.listEndIndex }
        val sectionHeight = if (sectionItems.isNotEmpty()) {
            val avgHeight = sectionItems.sumOf { it.size } / sectionItems.size
            avgHeight * sectionItemCount
        } else {
            val avgHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 60
            avgHeight * sectionItemCount
        }

        val listIndex = focusToListIndex(focusedIndex)

        if (sectionHeight <= viewportHeight) {
            if (sectionChanged || sectionSizeChanged) {
                listState.animateScrollToItem(currentSection.listStartIndex)
            }
        } else {
            if (sectionChanged || sectionSizeChanged) {
                listState.animateScrollToItem(currentSection.listStartIndex)
            } else {
                val targetItem = visibleItems.find { it.index == listIndex }
                val itemHeight = targetItem?.size ?: visibleItems.firstOrNull()?.size ?: 60
                val centerOffset = (viewportHeight - itemHeight) / 2
                listState.animateScrollToItem(listIndex, -centerOffset)
            }
        }
    }
}
