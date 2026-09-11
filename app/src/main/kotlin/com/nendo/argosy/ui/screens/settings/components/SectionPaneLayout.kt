package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SectionHeaderLockScroll
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.verticalEdgeFade
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.util.clickableNoFocus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <Item> SectionPaneLayout(
    items: List<Item>,
    sections: List<ListSection>,
    focusedIndex: Int,
    focusToListIndex: (Int) -> Int,
    itemKey: (Item) -> Any,
    isNavItem: (Item) -> Boolean,
    onSectionTap: (ListSection) -> Unit,
    modifier: Modifier = Modifier,
    isHeader: (Item) -> Boolean = { false },
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    headerLock: Boolean = false,
    itemContent: @Composable (Item) -> Unit
) {
    val aspectRatioClass = LocalUiScale.current.aspectRatioClass
    val isWide = aspectRatioClass == AspectRatioClass.ULTRA_WIDE ||
        aspectRatioClass == AspectRatioClass.WIDE
    val namedSections = remember(sections) { sections.filter { it.name != null } }
    val useSplitPane = isWide && namedSections.size >= 2
    val headerListIndices = remember(items) { items.indices.filter { isHeader(items[it]) }.toSet() }

    fun headerListIndexOf(section: ListSection): Int =
        (section.listStartIndex..section.listEndIndex).firstOrNull { it in headerListIndices } ?: section.listStartIndex

    if (useSplitPane) {
        val activeSectionIndex = namedSections.indexOfLast {
            focusedIndex >= it.focusStartIndex
        }.coerceAtLeast(0)

        val filteredItems = remember(items) { items.filter { !isNavItem(it) } }
        val indexMapping = remember(items) {
            val mapping = mutableMapOf<Int, Int>()
            var filteredIdx = 0
            for (i in items.indices) {
                if (!isNavItem(items[i])) {
                    mapping[i] = filteredIdx
                    filteredIdx++
                }
            }
            mapping
        }
        val filteredHeaderIndices = remember(headerListIndices, indexMapping) {
            headerListIndices.mapNotNull { indexMapping[it] }.toSet()
        }

        val contentListState = rememberLazyListState()

        PaneScroll(
            listState = contentListState,
            focusedIndex = focusedIndex,
            focusToListIndex = { focusIdx ->
                val originalListIndex = focusToListIndex(focusIdx)
                indexMapping[originalListIndex] ?: originalListIndex
            },
            sections = sections,
            headerLock = headerLock,
            headerListIndexOf = { section ->
                val original = headerListIndexOf(section)
                indexMapping[original] ?: original
            },
            headerListIndices = filteredHeaderIndices
        )

        Row(modifier = modifier) {
            LazyColumn(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .padding(end = Dimens.spacingSm)
            ) {
                items(namedSections.size) { index ->
                    val section = namedSections[index]
                    val isActive = index == activeSectionIndex
                    NavItem(
                        title = section.name ?: "",
                        isActive = isActive,
                        onClick = { onSectionTap(section) }
                    )
                }
            }

            VerticalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            LazyColumn(
                state = contentListState,
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxHeight()
                    .padding(start = Dimens.spacingMd)
                    .verticalEdgeFade(contentListState, fadeHeight = Dimens.spacingMd, top = !headerLock),
                verticalArrangement = verticalArrangement
            ) {
                paneItems(filteredItems, itemKey, isHeader, itemContent)
            }
        }
    } else {
        val listState = rememberLazyListState()

        PaneScroll(
            listState = listState,
            focusedIndex = focusedIndex,
            focusToListIndex = focusToListIndex,
            sections = sections,
            headerLock = headerLock,
            headerListIndexOf = ::headerListIndexOf,
            headerListIndices = headerListIndices
        )

        LazyColumn(
            state = listState,
            modifier = modifier.verticalEdgeFade(listState, fadeHeight = Dimens.spacingMd, top = false),
            verticalArrangement = verticalArrangement
        ) {
            paneItems(items, itemKey, isHeader, itemContent)
        }
    }
}

@Composable
private fun PaneScroll(
    listState: LazyListState,
    focusedIndex: Int,
    focusToListIndex: (Int) -> Int,
    sections: List<ListSection>,
    headerLock: Boolean,
    headerListIndexOf: (ListSection) -> Int,
    headerListIndices: Set<Int>
) {
    if (headerLock) {
        SectionHeaderLockScroll(
            listState = listState,
            focusedIndex = focusedIndex,
            focusToListIndex = focusToListIndex,
            sections = sections,
            headerListIndexOf = headerListIndexOf,
            headerListIndices = headerListIndices
        )
    } else {
        SectionFocusedScroll(
            listState = listState,
            focusedIndex = focusedIndex,
            focusToListIndex = focusToListIndex,
            sections = sections
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun <Item> LazyListScope.paneItems(
    items: List<Item>,
    itemKey: (Item) -> Any,
    isHeader: (Item) -> Boolean,
    itemContent: @Composable (Item) -> Unit
) {
    items.forEach { item ->
        if (isHeader(item)) {
            stickyHeader(key = itemKey(item)) {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    itemContent(item)
                }
            }
        } else {
            item(key = itemKey(item)) {
                itemContent(item)
            }
        }
    }
}

@Composable
private fun NavItem(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(
                horizontal = Dimens.spacingSm,
                vertical = Dimens.spacingSm
            )
    )
}
