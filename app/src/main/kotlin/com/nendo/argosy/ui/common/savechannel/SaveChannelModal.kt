package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaveChannelModal(
    state: SaveChannelState,
    savePath: String? = null,
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val activeEntries = if (state.selectedTab == SaveTab.STATES) state.statesEntries else entries
    LaunchedEffect(state.focusIndex, state.selectedTab, activeEntries.size) {
        if (activeEntries.isNotEmpty()) {
            val centerOffset = maxVisibleItems / 2
            val maxScrollIndex = (activeEntries.size - maxVisibleItems).coerceAtLeast(0)
            val targetScrollIndex = (state.focusIndex - centerOffset).coerceIn(0, maxScrollIndex)
            listState.animateScrollToItem(targetScrollIndex)
        }
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .background(overlayColor)
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
                        text = "Save Management",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (savePath != null) {
                        val displayPath = formatTruncatedPath(savePath, maxSegments = 5)
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ActiveSaveIndicator(activeChannel = state.activeChannel)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TabBar(
                selectedTab = state.selectedTab,
                hasSaveSlots = state.hasSaveSlots,
                hasStates = state.hasStates,
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
            } else if (state.selectedTab == SaveTab.STATES) {
                if (state.statesEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight * 2),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No save states",
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
                            count = state.statesEntries.size,
                            key = { index ->
                                val entry = state.statesEntries[index]
                                "state-${entry.slotNumber}-${entry.localCacheId ?: "empty"}"
                            }
                        ) { index ->
                            val entry = state.statesEntries[index]
                            StateSlotRow(
                                entry = entry,
                                isFocused = state.focusIndex == index,
                                onClick = { onEntryClick(index) },
                                onLongClick = { onEntryLongClick(index) }
                            )
                        }
                    }
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
            FooterBarWithState(hints = hints)
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

        if (state.showVersionMismatchDialog && state.versionMismatchState != null) {
            VersionMismatchOverlay(
                savedCoreId = state.versionMismatchState.coreId,
                savedVersion = state.versionMismatchState.coreVersion,
                currentCoreId = state.currentCoreId,
                currentVersion = state.currentCoreVersion
            )
        }

        if (state.showStateDeleteConfirmation && state.stateDeleteTarget != null) {
            StateDeleteConfirmationOverlay(
                slotNumber = state.stateDeleteTarget.slotNumber
            )
        }

        if (state.showStateReplaceAutoConfirmation && state.stateReplaceAutoTarget != null) {
            StateReplaceAutoConfirmationOverlay(
                slotNumber = state.stateReplaceAutoTarget.slotNumber
            )
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
    hasStates: Boolean,
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
        TabButton(
            label = "States",
            isSelected = selectedTab == SaveTab.STATES,
            isEnabled = hasStates,
            onClick = { onTabSelect(SaveTab.STATES) }
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
            .clickable(
                enabled = isEnabled && !isSelected,
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

private fun formatTruncatedPath(path: String, maxSegments: Int = 3): String {
    val segments = path.split("/").filter { it.isNotEmpty() }
    return if (segments.size <= maxSegments) {
        segments.joinToString("/")
    } else {
        "../" + segments.takeLast(maxSegments).joinToString("/")
    }
}

private fun buildFooterHints(state: SaveChannelState): List<FooterHintItem> {
    val hints = mutableListOf<FooterHintItem>()

    when (state.selectedTab) {
        SaveTab.SLOTS -> {
            hints.add(FooterHintItem(InputButton.SOUTH, "Activate"))
            if (state.canDeleteChannel) {
                hints.add(FooterHintItem(InputButton.NORTH, "Delete"))
            }
            if (state.canRenameChannel) {
                hints.add(FooterHintItem(InputButton.WEST, "Rename"))
            }
        }
        SaveTab.TIMELINE -> {
            hints.add(FooterHintItem(InputButton.SOUTH, "Restore"))
            if (state.canCreateChannel) {
                hints.add(FooterHintItem(InputButton.NORTH, "Lock"))
            }
            hints.add(FooterHintItem(InputButton.SELECT, "Reset"))
        }
        SaveTab.STATES -> {
            val focusedState = state.focusedStateEntry
            val canRestore = focusedState != null && focusedState.localCacheId != null
            hints.add(FooterHintItem(InputButton.SOUTH, "Restore", enabled = canRestore))
            if (state.canDeleteState) {
                hints.add(FooterHintItem(InputButton.NORTH, "Delete"))
            }
            if (state.canReplaceAutoWithSlot) {
                hints.add(FooterHintItem(InputButton.WEST, "Set as Auto"))
            }
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
            else -> String.format(java.util.Locale.US, "%.1f MB", entry.size / (1024.0 * 1024.0))
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
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
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
                UnifiedSaveEntry.Source.LOCAL -> LocalLauncherTheme.current.semanticColors.warning
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StateSlotRow(
    entry: UnifiedStateEntry,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isEmpty = entry.localCacheId == null
    val hasScreenshot = entry.screenshotPath != null
    val rowHeight = if (hasScreenshot) 72.dp else 56.dp

    val dateFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
            .withZone(java.time.ZoneId.systemDefault())
    }
    val formattedDate = remember(entry.timestamp) {
        if (isEmpty) "--" else dateFormatter.format(entry.timestamp)
    }
    val formattedSize = remember(entry.size) {
        if (isEmpty) "" else when {
            entry.size < 1024 -> "${entry.size} B"
            entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
            else -> String.format(java.util.Locale.US, "%.1f MB", entry.size / (1024.0 * 1024.0))
        }
    }

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else if (isEmpty) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val slotLabel = if (entry.slotNumber == -1) "Auto" else "Slot ${entry.slotNumber}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasScreenshot) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(java.io.File(entry.screenshotPath!!))
                    .memoryCacheKey(entry.screenshotPath)
                    .diskCacheKey(entry.screenshotPath)
                    .build(),
                contentDescription = "State screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 80.dp, height = 56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        when (entry.versionStatus) {
            UnifiedStateEntry.VersionStatus.MISMATCH -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Version mismatch",
                    tint = LocalLauncherTheme.current.semanticColors.warning,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                if (!hasScreenshot) {
                    Spacer(modifier = Modifier.width(20.dp))
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.coreId != null) {
                    Text(
                        text = entry.coreId,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
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
                if (formattedSize.isNotEmpty()) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (!isEmpty) {
            val sourceText = when (entry.source) {
                UnifiedStateEntry.Source.LOCAL -> "Local"
                UnifiedStateEntry.Source.SERVER -> "Server"
                UnifiedStateEntry.Source.BOTH -> "Synced"
            }
            Text(
                text = "[$sourceText]",
                style = MaterialTheme.typography.bodySmall,
                color = when (entry.source) {
                    UnifiedStateEntry.Source.LOCAL -> LocalLauncherTheme.current.semanticColors.warning
                    UnifiedStateEntry.Source.SERVER -> MaterialTheme.colorScheme.secondary
                    UnifiedStateEntry.Source.BOTH -> MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
fun VersionMismatchOverlay(
    savedCoreId: String?,
    savedVersion: String?,
    currentCoreId: String?,
    currentVersion: String?
) {
    NestedModal(
        title = "CORE VERSION MISMATCH",
        footerHints = listOf(
            InputButton.SOUTH to "Load Anyway",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "This state was saved with:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "${savedCoreId ?: "Unknown"} ${savedVersion ?: ""}".trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Current core version:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "${currentCoreId ?: "Unknown"} ${currentVersion ?: ""}".trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Loading may cause crashes or corruption.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalLauncherTheme.current.semanticColors.warning,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StateDeleteConfirmationOverlay(slotNumber: Int) {
    val slotLabel = if (slotNumber == -1) "auto state" else "slot $slotNumber"
    NestedModal(
        title = "DELETE STATE",
        footerHints = listOf(
            InputButton.SOUTH to "Delete",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "Delete $slotLabel?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "This will remove it from the cache.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StateReplaceAutoConfirmationOverlay(slotNumber: Int) {
    NestedModal(
        title = "REPLACE AUTO STATE",
        footerHints = listOf(
            InputButton.SOUTH to "Replace",
            InputButton.EAST to "Cancel"
        )
    ) {
        Text(
            text = "Replace auto state with slot $slotNumber?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "The current auto state will be overwritten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
