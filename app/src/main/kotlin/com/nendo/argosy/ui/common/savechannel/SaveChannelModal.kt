package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaveChannelModal(
    state: SaveChannelState,
    onRenameTextChange: (String) -> Unit,
    onTabSelect: (SaveTab) -> Unit = {},
    onEntryClick: (Int) -> Unit = {},
    onEntryLongClick: (Int) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    if (!state.isVisible) return

    val listState = rememberLazyListState()
    val itemHeight = 56.dp
    val maxVisibleItems = 5
    val entries = state.currentTabEntries

    LaunchedEffect(state.focusIndex, state.selectedTab, entries.size) {
        if (entries.isNotEmpty()) {
            val centerOffset = maxVisibleItems / 2
            val maxScrollIndex = (entries.size - maxVisibleItems).coerceAtLeast(0)
            val targetScrollIndex = (state.focusIndex - centerOffset).coerceIn(0, maxScrollIndex)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .width(450.dp)
                .clickable(enabled = false, onClick = {})
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SAVE CHANNELS",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (state.selectedTab == SaveTab.SLOTS) "Select a save slot" else "Recent save history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ActiveSaveIndicator(activeChannel = state.activeChannel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabBar(
                selectedTab = state.selectedTab,
                hasSaveSlots = state.hasSaveSlots,
                onTabSelect = onTabSelect
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight * 3),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight * 2),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.selectedTab == SaveTab.SLOTS) "No save slots" else "No cached saves",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .heightIn(max = itemHeight * maxVisibleItems)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    items(
                        count = entries.size,
                        key = { index ->
                            val entry = entries[index]
                            "${entry.localCacheId ?: "null"}-${entry.serverSaveId ?: "null"}"
                        }
                    ) { index ->
                        val entry = entries[index]
                        val isActiveChannel = entry.isChannel &&
                            entry.channelName != null &&
                            entry.channelName == state.activeChannel
                        SaveCacheEntryRow(
                            entry = entry,
                            isFocused = state.focusIndex == index,
                            isActiveChannel = isActiveChannel,
                            onClick = { onEntryClick(index) },
                            onLongClick = { onEntryLongClick(index) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hints = buildFooterHints(state)
            FooterBar(hints = hints)
        }

        if (state.showRestoreConfirmation && state.restoreSelectedEntry != null) {
            RestoreConfirmationOverlay()
        }

        if (state.showRenameDialog) {
            RenameChannelOverlay(
                isCreate = state.selectedTab == SaveTab.TIMELINE,
                text = state.renameText,
                onTextChange = onRenameTextChange
            )
        }

        if (state.showDeleteConfirmation && state.deleteSelectedEntry != null) {
            DeleteConfirmationOverlay(channelName = state.deleteSelectedEntry.channelName ?: "")
        }

        if (state.showResetConfirmation) {
            ResetConfirmationOverlay()
        }
    }
}

@Composable
private fun ActiveSaveIndicator(activeChannel: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "\u25C6",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = activeChannel ?: "Latest",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TabBar(
    selectedTab: SaveTab,
    hasSaveSlots: Boolean,
    onTabSelect: (SaveTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            label = "Save Slots",
            isSelected = selectedTab == SaveTab.SLOTS,
            isEnabled = hasSaveSlots,
            onClick = { onTabSelect(SaveTab.SLOTS) }
        )
        TabButton(
            label = "Recent Saves",
            isSelected = selectedTab == SaveTab.TIMELINE,
            isEnabled = true,
            onClick = { onTabSelect(SaveTab.TIMELINE) }
        )
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        !isEnabled -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(enabled = isEnabled && !isSelected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

private fun buildFooterHints(state: SaveChannelState): List<Pair<InputButton, String>> {
    val hints = mutableListOf<Pair<InputButton, String>>()

    when (state.selectedTab) {
        SaveTab.SLOTS -> {
            hints.add(InputButton.SOUTH to "Activate")
            if (state.canDeleteChannel) {
                hints.add(InputButton.NORTH to "Delete")
            }
            if (state.canRenameChannel) {
                hints.add(InputButton.WEST to "Rename")
            }
        }
        SaveTab.TIMELINE -> {
            hints.add(InputButton.SOUTH to "Restore")
            if (state.canCreateChannel) {
                hints.add(InputButton.NORTH to "Lock")
            }
            hints.add(InputButton.SELECT to "Reset")
        }
    }

    return hints
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SaveCacheEntryRow(
    entry: UnifiedSaveEntry,
    isFocused: Boolean,
    isActiveChannel: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
    }
    val formattedDate = remember(entry.timestamp) {
        dateFormatter.format(entry.timestamp)
    }
    val formattedSize = remember(entry.size) {
        when {
            entry.size < 1024 -> "${entry.size} B"
            entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
            else -> String.format("%.1f MB", entry.size / (1024.0 * 1024.0))
        }
    }

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val sourceText = when (entry.source) {
        UnifiedSaveEntry.Source.LOCAL -> "Local"
        UnifiedSaveEntry.Source.SERVER -> "Server"
        UnifiedSaveEntry.Source.BOTH -> "Synced"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            entry.isChannel -> {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Channel",
                    tint = if (isActiveChannel) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            entry.isActive -> {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                Spacer(modifier = Modifier.width(20.dp))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.isActive) {
                    Text(
                        text = "[ACTIVE]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }

        Text(
            text = "[$sourceText]",
            style = MaterialTheme.typography.bodySmall,
            color = when (entry.source) {
                UnifiedSaveEntry.Source.LOCAL -> MaterialTheme.colorScheme.tertiary
                UnifiedSaveEntry.Source.SERVER -> MaterialTheme.colorScheme.secondary
                UnifiedSaveEntry.Source.BOTH -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun RestoreConfirmationOverlay() {
    NestedModal(
        title = "RESTORE SAVE",
        footerHints = listOf(
            InputButton.SOUTH to "Restore",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "Restore this save to your current game state?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun RenameChannelOverlay(
    isCreate: Boolean,
    text: String,
    onTextChange: (String) -> Unit
) {
    NestedModal(
        title = if (isCreate) "CREATE SAVE SLOT" else "RENAME SAVE SLOT",
        footerHints = listOf(
            InputButton.SOUTH to "Confirm",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = if (isCreate) "Enter a name for this save slot" else "Enter a new name",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Slot name")
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun DeleteConfirmationOverlay(channelName: String) {
    NestedModal(
        title = "DELETE SAVE SLOT",
        footerHints = listOf(
            InputButton.SOUTH to "Delete",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "Delete \"$channelName\"?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "This will remove it from local storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ResetConfirmationOverlay() {
    NestedModal(
        title = "RESET SAVE",
        footerHints = listOf(
            InputButton.SOUTH to "Reset",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "Start fresh?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "This will delete your local save file. Your saved slots and server saves will not be affected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
