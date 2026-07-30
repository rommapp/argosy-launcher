package com.nendo.argosy.libretro.ui.cheats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.touchOnly

sealed class CheatListItem {
    data class Header(val title: String) : CheatListItem()
    data class Cheat(val item: CheatDisplayItem, val globalIndex: Int) : CheatListItem()
}

data class CheatOrderKey(
    val recent: Boolean,
    val custom: Boolean,
    val lastUsedAt: Long,
    val enabled: Boolean,
    val description: String
)

private const val WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L

fun buildSectionedList(
    cheats: List<CheatDisplayItem>,
    now: Long,
    orderKeys: MutableMap<Long, CheatOrderKey>
): List<CheatListItem> {
    val weekAgo = now - WEEK_MILLIS
    cheats.forEach { cheat ->
        orderKeys.getOrPut(cheat.id) {
            CheatOrderKey(
                recent = cheat.lastUsedAt != null && cheat.lastUsedAt >= weekAgo,
                custom = cheat.isUserCreated,
                lastUsedAt = cheat.lastUsedAt ?: 0L,
                enabled = cheat.enabled,
                description = cheat.description
            )
        }
    }
    fun keyOf(cheat: CheatDisplayItem) = orderKeys.getValue(cheat.id)

    val recent = cheats.filter { keyOf(it).recent }
        .sortedByDescending { keyOf(it).lastUsedAt }
    val custom = cheats.filter { keyOf(it).let { k -> !k.recent && k.custom } }
        .sortedWith(
            compareByDescending<CheatDisplayItem> { keyOf(it).lastUsedAt }
                .thenBy { keyOf(it).description }
        )
    val available = cheats.filter { keyOf(it).let { k -> !k.recent && !k.custom } }
        .sortedWith(
            compareByDescending<CheatDisplayItem> { keyOf(it).enabled }
                .thenBy { keyOf(it).description }
        )

    val result = mutableListOf<CheatListItem>()
    var globalIndex = 0
    if (recent.isNotEmpty()) {
        result.add(CheatListItem.Header("RECENT"))
        recent.forEach { result.add(CheatListItem.Cheat(it, globalIndex++)) }
    }
    if (custom.isNotEmpty()) {
        result.add(CheatListItem.Header("CUSTOM"))
        custom.forEach { result.add(CheatListItem.Cheat(it, globalIndex++)) }
    }
    if (available.isNotEmpty()) {
        result.add(CheatListItem.Header("AVAILABLE"))
        available.forEach { result.add(CheatListItem.Cheat(it, globalIndex++)) }
    }
    return result
}

@Composable
fun AvailableTab(
    listItems: List<CheatListItem>,
    cheats: List<CheatDisplayItem>,
    allCheats: List<CheatDisplayItem>,
    searchQuery: String,
    focusedIndex: Int,
    onSearchClick: () -> Unit,
    onToggleCheat: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val enabledCount = allCheats.count { it.enabled }
    val searchFieldFocused = focusedIndex == 0

    val cheatIndexToListIndex = remember(listItems) {
        listItems.mapIndexedNotNull { listIdx, item ->
            if (item is CheatListItem.Cheat) item.globalIndex to listIdx else null
        }.toMap()
    }

    LaunchedEffect(focusedIndex) {
        if (focusedIndex > 0) {
            val cheatIndex = focusedIndex - 1
            val listIndex = cheatIndexToListIndex[cheatIndex]
            if (listIndex != null) {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
                val centerOffset = (viewportHeight - itemHeight) / 2
                listState.animateScrollToItem(listIndex, -centerOffset)
            }
        }
    }

    Column(modifier = modifier.padding(Dimens.spacingSm)) {
        SearchBar(
            query = searchQuery,
            isFocused = searchFieldFocused,
            onClick = onSearchClick
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingSm, horizontal = Dimens.spacingXs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${cheats.size} cheats",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$enabledCount/${allCheats.size} enabled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (cheats.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No matching cheats" else "No cheats available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).focusProperties { canFocus = false },
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                items(listItems.size, key = { index ->
                    when (val item = listItems[index]) {
                        is CheatListItem.Header -> "header_${item.title}"
                        is CheatListItem.Cheat -> item.item.id
                    }
                }) { index ->
                    when (val item = listItems[index]) {
                        is CheatListItem.Header -> {
                            SectionHeader(title = item.title)
                        }
                        is CheatListItem.Cheat -> {
                            SwitchPreference(
                                title = item.item.description,
                                isEnabled = item.item.enabled,
                                isFocused = item.globalIndex == focusedIndex - 1,
                                onToggle = { onToggleCheat(item.item.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor, shape)
            .touchOnly(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm + Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = query.ifEmpty { "Search cheats..." },
            style = MaterialTheme.typography.bodyLarge,
            color = if (query.isEmpty()) contentColor.copy(alpha = 0.6f) else contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (query.isNotEmpty()) {
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
