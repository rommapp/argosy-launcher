package com.nendo.argosy.ui.common

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls to ensure the item at [targetIndex] is fully visible with padding.
 * Only scrolls if necessary - doesn't scroll if item is already fully visible.
 *
 * @param targetIndex The LazyColumn item index (not focus index)
 * @param paddingPx Extra padding in pixels to keep around the item
 */
/**
 * Alias for scrollToItemIfNeeded for backward compatibility.
 */
suspend fun LazyListState.scrollToFocusedItem(targetIndex: Int) {
    scrollToItemIfNeeded(targetIndex)
}

suspend fun LazyListState.scrollToItemIfNeeded(targetIndex: Int, paddingPx: Int = 48) {
    val layoutInfo = layoutInfo
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeight <= 0) return

    val targetItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }

    if (targetItem != null) {
        // Item is visible - check if it's fully visible with padding
        val itemTop = targetItem.offset
        val itemBottom = itemTop + targetItem.size

        when {
            // Item extends below viewport - scroll down
            itemBottom + paddingPx > viewportHeight -> {
                val scrollAmount = itemBottom + paddingPx - viewportHeight
                animateScrollBy(scrollAmount.toFloat())
            }
            // Item extends above viewport - scroll up
            itemTop - paddingPx < 0 -> {
                val scrollAmount = itemTop - paddingPx
                animateScrollBy(scrollAmount.toFloat())
            }
            // Item is fully visible with padding - no scroll needed
        }
    } else {
        // Item not visible at all - scroll to it
        animateScrollToItem(targetIndex)
    }
}
