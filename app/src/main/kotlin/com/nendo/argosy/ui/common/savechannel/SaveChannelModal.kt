package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal
@Composable
fun SaveChannelModal(
    state: SaveChannelState,
    savePath: String? = null,
    onRenameTextChange: (String) -> Unit,
    onSlotClick: (Int) -> Unit = {},
    onHistoryClick: (Int) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    if (!state.isVisible) return

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.7f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .width(Dimens.modalWidthXl)
                .clickableNoFocus {}
                .padding(Dimens.spacingMd)
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
                        val displayPath = formatTruncatedPath(
                            savePath, maxSegments = 5
                        )
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

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            val itemHeight = Dimens.settingsItemMinHeight
            val maxVisibleItems = 4

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(
                            min = itemHeight * 2,
                            max = itemHeight * maxVisibleItems
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                SavesTabContent(
                    state = state,
                    maxHeight = itemHeight * maxVisibleItems,
                    onSlotClick = onSlotClick,
                    onHistoryClick = onHistoryClick
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            val hints = buildFooterHints(state)
            FooterBarWithState(hints = hints)
        }

        if (state.showRestoreConfirmation &&
            state.restoreSelectedEntry != null) {
            RestoreConfirmationOverlay()
        }

        if (state.showRenameDialog) {
            RenameChannelOverlay(
                isCreate = state.renameEntry == null ||
                    !state.renameEntry.isChannel,
                text = state.renameText,
                onTextChange = onRenameTextChange
            )
        }

        if (state.showDeleteConfirmation &&
            state.deleteSelectedEntry != null) {
            DeleteConfirmationOverlay(
                channelName = state.deleteSelectedEntry.channelName ?: ""
            )
        }

        if (state.showVersionMismatchDialog &&
            state.versionMismatchState != null) {
            VersionMismatchOverlay(
                savedCoreId = state.versionMismatchState.coreId,
                savedVersion = state.versionMismatchState.coreVersion,
                currentCoreId = state.currentCoreId,
                currentVersion = state.currentCoreVersion
            )
        }

        if (state.showStateDeleteConfirmation &&
            state.stateDeleteTarget != null) {
            StateDeleteConfirmationOverlay(
                slotNumber = state.stateDeleteTarget.slotNumber
            )
        }

        if (state.showStateReplaceAutoConfirmation &&
            state.stateReplaceAutoTarget != null) {
            StateReplaceAutoConfirmationOverlay(
                slotNumber = state.stateReplaceAutoTarget.slotNumber
            )
        }

        if (state.showMigrateConfirmation &&
            state.migrateChannelName != null) {
            MigrateConfirmationOverlay(
                channelName = state.migrateChannelName
            )
        }

        if (state.showDeleteLegacyConfirmation &&
            state.deleteLegacyChannelName != null) {
            DeleteLegacyConfirmationOverlay(
                channelName = state.deleteLegacyChannelName,
                saveCount = state.saveSlots.firstOrNull {
                    it.channelName == state.deleteLegacyChannelName
                }?.saveCount ?: 0
            )
        }
    }
}

@Composable
private fun SavesTabContent(
    state: SaveChannelState,
    maxHeight: androidx.compose.ui.unit.Dp,
    onSlotClick: (Int) -> Unit,
    onHistoryClick: (Int) -> Unit
) {
    val slotListState = rememberLazyListState()
    val historyListState = rememberLazyListState()

    LaunchedEffect(state.selectedSlotIndex) {
        if (state.saveFocusColumn == SaveFocusColumn.SLOTS &&
            state.selectedSlotIndex >= 0) {
            slotListState.animateScrollToItem(state.selectedSlotIndex)
        }
    }

    LaunchedEffect(state.selectedHistoryIndex) {
        if (state.saveFocusColumn == SaveFocusColumn.HISTORY &&
            state.selectedHistoryIndex >= 0) {
            historyListState.animateScrollToItem(state.selectedHistoryIndex)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            Text(
                text = "Save Slots",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Dimens.spacingMd, vertical = 4.dp
                )
            )

            LazyColumn(
                state = slotListState,
                contentPadding = PaddingValues(
                    horizontal = Dimens.spacingSm, vertical = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(state.saveSlots, key = { _, slot -> slot.displayName }) { index, slot ->
                    val isSelected = index == state.selectedSlotIndex &&
                        state.saveFocusColumn == SaveFocusColumn.SLOTS
                    when {
                        slot.isCreateAction -> NewSlotRow(
                            isSelected = isSelected,
                            onClick = { onSlotClick(index) }
                        )
                        slot.isMigrationCandidate -> MigrationSlotRow(
                            slot = slot,
                            isSelected = isSelected,
                            onClick = { onSlotClick(index) }
                        )
                        else -> SlotRow(
                            slot = slot,
                            isSelected = isSelected,
                            onClick = { onSlotClick(index) }
                        )
                    }
                }
            }
        }

        VerticalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
        )

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            val slotName = state.saveSlots.getOrNull(state.selectedSlotIndex)
                ?.let {
                    if (it.isCreateAction) null else it.displayName
                }
            Text(
                text = if (slotName != null) "History ($slotName)"
                    else "History",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = Dimens.spacingMd, vertical = 4.dp
                )
            )

            if (state.saveHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimens.spacingLg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saves yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = historyListState,
                    contentPadding = PaddingValues(
                        horizontal = Dimens.spacingSm, vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(state.saveHistory, key = { _, item -> "${item.cacheId}_${item.timestamp}" }) { index, item ->
                        HistoryRow(
                            item = item,
                            isSelected = index == state.selectedHistoryIndex &&
                                state.saveFocusColumn == SaveFocusColumn.HISTORY,
                            onClick = { onHistoryClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(
    slot: SaveSlotItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val textColor = if (slot.isActive) accentColor
        else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    .copy(alpha = 0.6f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (slot.isActive) {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(8.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = slot.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (slot.isActive) FontWeight.Bold
                    else FontWeight.Normal,
                color = textColor
            )
        }
        if (slot.saveCount > 0) {
            Text(
                text = "${slot.saveCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NewSlotRow(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    .copy(alpha = 0.6f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "New Slot",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun MigrationSlotRow(
    slot: SaveSlotItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dimmedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    .copy(alpha = 0.4f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary
                        .copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                tint = dimmedColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = slot.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = dimmedColor
            )
        }
        Text(
            text = "[Legacy]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun HistoryRow(
    item: SaveHistoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    .copy(alpha = 0.6f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.isActiveRestorePoint) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Active restore point",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (item.isLatest) {
                    Text(
                        text = "Latest",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = formatSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val syncTag = if (item.isSynced) "Synced" else "Local"
        val syncColor = if (item.isSynced) Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = "[$syncTag]",
            style = MaterialTheme.typography.labelSmall,
            color = syncColor
        )
    }
}


@Composable
private fun ActiveSaveIndicator(activeChannel: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
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

    when (state.saveFocusColumn) {
        SaveFocusColumn.SLOTS -> {
            val focused = state.focusedSlot
            if (focused?.isMigrationCandidate == true) {
                hints.add(FooterHintItem(InputButton.A, "Migrate"))
                hints.add(FooterHintItem(InputButton.Y, "Delete"))
            } else {
                hints.add(FooterHintItem(InputButton.A, "Activate"))
                if (state.canRenameSlot) {
                    hints.add(FooterHintItem(InputButton.X, "Rename"))
                }
                if (state.canDeleteSlot) {
                    hints.add(FooterHintItem(InputButton.Y, "Delete"))
                }
            }
        }
        SaveFocusColumn.HISTORY -> {
            hints.add(FooterHintItem(InputButton.A, "Restore"))
            if (state.canLockAsSlot) {
                hints.add(FooterHintItem(InputButton.Y, "Lock"))
            }
        }
    }

    hints.add(FooterHintItem(InputButton.RB, "Sync"))

    return hints
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val now = java.util.Date()
    val diffMs = now.time - timestamp
    val diffDays = diffMs / (1000 * 60 * 60 * 24)

    return when {
        diffDays == 0L -> {
            val format = java.text.SimpleDateFormat(
                "h:mm a", java.util.Locale.getDefault()
            )
            "Today ${format.format(date)}"
        }
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> {
            val format = java.text.SimpleDateFormat(
                "MMM d", java.util.Locale.getDefault()
            )
            format.format(date)
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}


@Composable
private fun RestoreConfirmationOverlay() {
    NestedModal(
        title = "RESTORE SAVE",
        footerHints = listOf(
            InputButton.A to "Restore",
            InputButton.B to "Cancel"
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
            InputButton.A to "Confirm",
            InputButton.B to "Cancel"
        )
    ) {
        Text(
            text = if (isCreate) "Enter a name for this save slot"
                else "Enter a new name",
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
private fun MigrateConfirmationOverlay(channelName: String) {
    NestedModal(
        title = "MIGRATE SAVE",
        footerHints = listOf(
            InputButton.A to "Migrate",
            InputButton.B to "Cancel"
        )
    ) {
        Text(
            text = "Migrate \"$channelName\" to new save system?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "This will register it as a named save slot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DeleteLegacyConfirmationOverlay(
    channelName: String,
    saveCount: Int
) {
    val countLabel = if (saveCount == 1) "1 backup" else "$saveCount backups"
    NestedModal(
        title = "DELETE LEGACY SAVE",
        footerHints = listOf(
            InputButton.A to "Delete",
            InputButton.B to "Cancel"
        )
    ) {
        Text(
            text = "Delete \"$channelName\" and $countLabel?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "This will remove it from the server and local storage. This cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DeleteConfirmationOverlay(channelName: String) {
    NestedModal(
        title = "DELETE SAVE SLOT",
        footerHints = listOf(
            InputButton.A to "Delete",
            InputButton.B to "Cancel"
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
fun VersionMismatchOverlay(
    savedCoreId: String?,
    savedVersion: String?,
    currentCoreId: String?,
    currentVersion: String?
) {
    NestedModal(
        title = "CORE VERSION MISMATCH",
        footerHints = listOf(
            InputButton.A to "Load Anyway",
            InputButton.B to "Cancel"
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

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

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

        Spacer(modifier = Modifier.height(Dimens.radiusLg))

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
            InputButton.A to "Delete",
            InputButton.B to "Cancel"
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
            InputButton.A to "Replace",
            InputButton.B to "Cancel"
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
