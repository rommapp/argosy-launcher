package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHintsWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.common.StateScreenshotViewer
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotChannelView
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotEntryOverlays
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotOverlays
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewActions
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewState
import com.nendo.argosy.ui.common.displayName
import com.nendo.argosy.util.formatSaveSize
import com.nendo.argosy.util.formatSaveTimestamp

private val SLOT_PICKER_LIST_MAX_HEIGHT =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.slotPickerListMaxHeight.dp
private const val MIN_VISIBLE_ITEMS = 4
private const val LIST_HEIGHT_FRACTION = 0.6f
private const val HEADER_PATH_SEGMENTS = 2
private const val ACTIVE_PILL_FILL_ALPHA = 0.18f

@Composable
fun SaveChannelModal(
    state: SaveChannelState,
    savePath: String? = null,
    onRenameTextChange: (String) -> Unit,
    onRenameConfirm: () -> Unit = {},
    onRenameCancel: () -> Unit = {},
    onSlotClick: (Int) -> Unit = {},
    onHistoryClick: (Int) -> Unit = {},
    onTabSwitch: (SaveTab) -> Unit = {},
    onStateClick: (Int) -> Unit = {},
    onDismissScreenshotPreview: () -> Unit = {},
    onSlotPickerClick: (Int) -> Unit = {},
    onHintClick: (InputButton) -> Unit = {},
    onDismiss: () -> Unit = {},
    coverPath: String? = null,
    snapshotActions: SnapshotViewActions? = null
) {
    if (!state.isVisible) return
    val snapshotView = state.snapshot?.takeIf { snapshotActions != null }

    ModalPresenceEffect()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.7f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val theme = LocalArgosyTheme.current
        val panelShape = RoundedCornerShape(Dimens.radiusXl)
        Column(
            modifier = Modifier
                .padding(horizontal = Dimens.spacingLg)
                .widthIn(max = Dimens.saveChannelModalWidth)
                .fillMaxWidth()
                .clip(panelShape)
                .background(theme.surfaceBase)
                .border(Dimens.borderThin, theme.hairlineLow, panelShape)
                .clickableNoFocus {}
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            ModalHeader(
                savePath = savePath,
                activeChannel = state.activeChannel,
                showActiveChannel = state.snapshot == null
            )

            if (state.showsStatesTab) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                TabRow(
                    selectedTab = state.selectedTab,
                    onTabSwitch = onTabSwitch
                )
            } else {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
            }

            HorizontalDivider(color = theme.hairlineLow)

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            if (state.isLoadingServer) {
                ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
            }

            val itemHeight = Dimens.settingsItemMinHeight
            val listHeight = maxOf(
                itemHeight * MIN_VISIBLE_ITEMS,
                androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * LIST_HEIGHT_FRACTION
            )

            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(
                            min = itemHeight * 2,
                            max = listHeight
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (state.selectedTab) {
                    SaveTab.SAVES -> if (snapshotView != null && snapshotActions != null) {
                        SnapshotChannelView(
                            state = snapshotView,
                            coverPath = coverPath,
                            maxHeight = listHeight,
                            actions = snapshotActions
                        )
                    } else {
                        SavesTabContent(
                            state = state,
                            maxHeight = listHeight,
                            onSlotClick = onSlotClick,
                            onHistoryClick = onHistoryClick
                        )
                    }
                    SaveTab.STATES -> StatesTabContent(
                        state = state,
                        maxHeight = listHeight,
                        onStateClick = onStateClick
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            val hints = if (snapshotView != null && state.selectedTab == SaveTab.SAVES) {
                buildSnapshotFooterHints(state, snapshotView)
            } else {
                buildFooterHints(state)
            }
            FooterHintsWithState(hints = hints, onHintClick = onHintClick)
        }

        if (snapshotView != null && snapshotActions != null && state.selectedTab == SaveTab.SAVES) {
            SnapshotOverlays(state = snapshotView, coverPath = coverPath, actions = snapshotActions)
            SnapshotEntryOverlays(state = snapshotView, actions = snapshotActions)
        }

        if (state.showRestoreConfirmation &&
            state.restoreSelectedEntry != null) {
            RestoreConfirmationOverlay(onHintClick = onHintClick)
        }

        if (state.showRenameDialog) {
            RenameChannelOverlay(
                mode = state.renameMode,
                text = state.renameText,
                onTextChange = onRenameTextChange,
                onConfirm = onRenameConfirm,
                onCancel = onRenameCancel
            )
        }

        if (state.showDeleteConfirmation && state.deleteTarget != null) {
            DeleteConfirmationOverlay(
                target = state.deleteTarget,
                onHintClick = onHintClick
            )
        }

        if (state.showVersionMismatchDialog &&
            state.versionMismatchState != null) {
            VersionMismatchOverlay(
                savedCoreId = state.versionMismatchState.coreId,
                savedVersion = state.versionMismatchState.coreVersion,
                currentCoreId = state.currentCoreId,
                currentVersion = state.currentCoreVersion,
                onHintClick = onHintClick
            )
        }

        if (state.showStateDeleteConfirmation &&
            state.stateDeleteTarget != null) {
            StateDeleteConfirmationOverlay(
                slotNumber = state.stateDeleteTarget.slotNumber,
                onHintClick = onHintClick
            )
        }

        if (state.showStateReplaceAutoConfirmation &&
            state.stateReplaceAutoTarget != null) {
            StateReplaceAutoConfirmationOverlay(
                slotNumber = state.stateReplaceAutoTarget.slotNumber,
                onHintClick = onHintClick
            )
        }

        if (state.showMigrateConfirmation &&
            state.migrateChannelName != null) {
            MigrateConfirmationOverlay(
                channelName = state.migrateChannelName,
                onHintClick = onHintClick
            )
        }

        if (state.showSlotPicker) {
            SlotPickerOverlay(
                items = state.slotPickerItems,
                selectedIndex = state.slotPickerIndex,
                onItemClick = onSlotPickerClick,
                onHintClick = onHintClick
            )
        }

        if (state.showScreenshotPreview &&
            state.screenshotPreviewEntry != null) {
            StateScreenshotViewer(
                screenshotPath = state.screenshotPreviewEntry.screenshotPath ?: "",
                slotLabel = state.screenshotPreviewEntry.displayName(LocalContext.current),
                timestampFormatted = state.screenshotPreviewEntry.timestampFormatted,
                onDismiss = onDismissScreenshotPreview
            )
        }

        if (state.showDeleteLegacyConfirmation &&
            state.deleteLegacyChannelName != null) {
            DeleteLegacyConfirmationOverlay(
                channelName = state.deleteLegacyChannelName,
                saveCount = state.saveSlots.firstOrNull {
                    it.channelName == state.deleteLegacyChannelName
                }?.saveCount ?: 0,
                onHintClick = onHintClick
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

    if (state.saveFocusColumn == SaveFocusColumn.SLOTS) {
        FocusedScroll(listState = slotListState, focusedIndex = state.selectedSlotIndex)
    }

    if (state.saveFocusColumn == SaveFocusColumn.HISTORY) {
        FocusedScroll(listState = historyListState, focusedIndex = state.selectedHistoryIndex)
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
                text = stringResource(R.string.ui_save_channel_slots_heading),
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
                itemsIndexed(state.saveSlots, key = { _, slot -> slot.slotKey }) { index, slot ->
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
                text = if (slotName != null) {
                    stringResource(R.string.ui_save_channel_history_heading_named, slotName)
                } else {
                    stringResource(R.string.ui_save_channel_history_heading)
                },
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
                        text = stringResource(R.string.ui_save_channel_history_empty),
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
                    itemsIndexed(state.saveHistory, key = { _, item -> item.historyKey }) { index, item ->
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
private fun ModalHeader(
    savePath: String?,
    activeChannel: String?,
    showActiveChannel: Boolean
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Text(
                text = stringResource(R.string.ui_save_channel_title),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary,
                maxLines = 1
            )
            if (savePath != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = theme.textMute,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = formatTruncatedPath(savePath, maxSegments = HEADER_PATH_SEGMENTS),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMute,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (showActiveChannel) ActiveSaveIndicator(activeChannel = activeChannel)
    }
}

@Composable
private fun TabRow(
    selectedTab: SaveTab,
    onTabSwitch: (SaveTab) -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        SaveTab.entries.forEach { tab ->
            val isActive = tab == selectedTab
            val label = when (tab) {
                SaveTab.SAVES -> stringResource(R.string.ui_save_channel_tab_saves)
                SaveTab.STATES -> stringResource(R.string.ui_save_channel_tab_states)
            }
            Column(
                modifier = Modifier.width(IntrinsicSize.Max).clickableNoFocus { onTabSwitch(tab) },
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) theme.textPrimary else theme.textDim,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.borderMedium)
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .background(if (isActive) theme.focusAccent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun StatesTabContent(
    state: SaveChannelState,
    maxHeight: androidx.compose.ui.unit.Dp,
    onStateClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    FocusedScroll(listState = listState, focusedIndex = state.focusIndex)

    if (state.statesEntries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(Dimens.spacingLg),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.ui_save_channel_states_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Text(
            text = stringResource(
                R.string.ui_save_channel_states_heading_named,
                SaveSyncApiClient.syncKeyOf(state.activeChannel)
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
            contentPadding = PaddingValues(
                horizontal = Dimens.spacingSm, vertical = 4.dp
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(
                state.statesEntries,
                key = { _, entry -> "state_${entry.slotNumber}" }
            ) { index, entry ->
                StateSlotRow(
                    entry = entry,
                    isSelected = index == state.focusIndex,
                    onClick = { onStateClick(index) },
                    clickModifier = Modifier.clickableNoFocus {
                        onStateClick(index)
                    }
                )
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
            .saveRowFocus(isSelected)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (slot.isActive) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(8.dp)
            )
        }
        Text(
            text = slot.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (slot.isActive) FontWeight.Bold
                else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
            .saveRowFocus(isSelected)
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
            text = stringResource(R.string.ui_save_channel_slot_new),
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
            .saveRowFocus(isSelected)
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
            text = stringResource(R.string.ui_save_channel_slot_legacy_tag),
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
            .saveRowFocus(isSelected)
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
                    text = formatSaveTimestamp(LocalContext.current, item.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.isActiveRestorePoint) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(
                            R.string.ui_save_channel_history_active_point
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (item.isLatest) {
                    Text(
                        text = stringResource(R.string.ui_save_channel_history_latest),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = formatSaveSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val syncTag = if (item.isSynced) {
            stringResource(R.string.ui_save_channel_history_tag_synced)
        } else {
            stringResource(R.string.ui_save_channel_history_tag_local)
        }
        val syncColor = if (item.isSynced) Color(0xFF4CAF50)
            else MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = syncTag,
            style = MaterialTheme.typography.labelSmall,
            color = syncColor
        )
    }
}


@Composable
private fun ActiveSaveIndicator(activeChannel: String?) {
    val accent = LocalArgosyTheme.current.focusAccent
    val shape = RoundedCornerShape(Dimens.radiusPill)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = ACTIVE_PILL_FILL_ALPHA))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.dotSm)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = SaveSyncApiClient.syncKeyOf(activeChannel),
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

@Composable
private fun buildSnapshotFooterHints(
    state: SaveChannelState,
    snapshot: SnapshotViewState
): List<FooterHintItem> = buildList {
    if (snapshot.hasOverlay) return@buildList
    if (snapshot.focusedTile != null) {
        add(FooterHintItem(InputButton.X, stringResource(R.string.save_channels_footer_channel_actions)))
    }
    add(FooterHintItem(InputButton.Y, stringResource(R.string.save_channels_footer_timeline)))
}

@Composable
private fun buildFooterHints(state: SaveChannelState): List<FooterHintItem> {
    val hints = mutableListOf<FooterHintItem>()

    if (state.showSlotPicker) return emptyList()

    when (state.selectedTab) {
        SaveTab.SAVES -> {
            when (state.saveFocusColumn) {
                SaveFocusColumn.SLOTS -> {
                    val focused = state.focusedSlot
                    if (focused?.isMigrationCandidate == true) {
                        hints.add(
                            FooterHintItem(
                                InputButton.A,
                                stringResource(R.string.ui_save_channel_footer_migrate)
                            )
                        )
                        hints.add(
                            FooterHintItem(
                                InputButton.Y,
                                stringResource(R.string.ui_save_channel_footer_delete_legacy_slot)
                            )
                        )
                    } else {
                        hints.add(
                            FooterHintItem(
                                InputButton.A,
                                stringResource(R.string.ui_save_channel_footer_activate)
                            )
                        )
                        if (state.canRenameSlot) {
                            hints.add(
                                FooterHintItem(
                                    InputButton.X,
                                    stringResource(R.string.ui_save_channel_footer_rename)
                                )
                            )
                        }
                        if (state.canDeleteSlot) {
                            hints.add(
                                FooterHintItem(
                                    InputButton.Y,
                                    stringResource(R.string.ui_save_channel_footer_delete_slot)
                                )
                            )
                        }
                        if (state.canDeleteArchived) {
                            hints.add(
                                FooterHintItem(
                                    InputButton.Y,
                                    stringResource(R.string.ui_save_channel_footer_delete_archived)
                                )
                            )
                        }
                    }
                }
                SaveFocusColumn.HISTORY -> {
                    hints.add(
                        FooterHintItem(
                            InputButton.A,
                            stringResource(R.string.ui_save_channel_footer_restore_save)
                        )
                    )
                    if (state.canDeleteVersion) {
                        hints.add(
                            FooterHintItem(
                                InputButton.Y,
                                stringResource(R.string.ui_save_channel_footer_delete_version)
                            )
                        )
                    }
                    if (state.canLockAsSlot) {
                        hints.add(
                            FooterHintItem(
                                InputButton.X,
                                stringResource(R.string.ui_save_channel_footer_save_as)
                            )
                        )
                    }
                }
            }
        }
        SaveTab.STATES -> {
            val focused = state.focusedStateEntry
            if (focused != null && focused.localCacheId != null) {
                hints.add(
                    FooterHintItem(
                        InputButton.A,
                        stringResource(R.string.ui_save_channel_footer_restore_state)
                    )
                )
                if (focused.screenshotPath != null) {
                    hints.add(
                        FooterHintItem(
                            InputButton.X,
                            stringResource(R.string.ui_save_channel_footer_preview_state)
                        )
                    )
                }
                hints.add(
                    FooterHintItem(
                        InputButton.Y,
                        stringResource(R.string.ui_save_channel_footer_delete_state)
                    )
                )
            } else if (focused?.serverStateId != null) {
                hints.add(
                    FooterHintItem(
                        InputButton.A,
                        stringResource(R.string.ui_save_channel_footer_download_state)
                    )
                )
            }
        }
    }

    if (state.supportsStates) {
        val tabLabel = if (state.selectedTab == SaveTab.SAVES) {
            stringResource(R.string.ui_save_channel_footer_tab_states)
        } else {
            stringResource(R.string.ui_save_channel_footer_tab_saves)
        }
        hints.add(FooterHintItem(InputButton.RB, tabLabel))
    } else {
        hints.add(
            FooterHintItem(InputButton.RB, stringResource(R.string.ui_save_channel_footer_sync))
        )
    }

    return hints
}



@Composable
private fun RestoreConfirmationOverlay(onHintClick: (InputButton) -> Unit) {
    NestedModal(
        title = stringResource(R.string.ui_save_channel_restore_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_restore_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_restore_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = stringResource(R.string.ui_save_channel_restore_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
internal fun RenameChannelOverlay(
    mode: RenameMode,
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val title = when (mode) {
        RenameMode.SAVE_AS -> stringResource(R.string.ui_save_channel_rename_title_save_as)
        RenameMode.NEW_SLOT -> stringResource(R.string.ui_save_channel_rename_title_new_slot)
        RenameMode.RENAME -> stringResource(R.string.ui_save_channel_rename_title_rename)
    }
    val confirmLabel = when (mode) {
        RenameMode.SAVE_AS -> stringResource(R.string.ui_save_channel_rename_confirm_save_as)
        RenameMode.NEW_SLOT -> stringResource(R.string.ui_save_channel_rename_confirm_new_slot)
        RenameMode.RENAME -> stringResource(R.string.ui_save_channel_rename_confirm_rename)
    }
    val prompt = when (mode) {
        RenameMode.SAVE_AS -> stringResource(R.string.ui_save_channel_rename_prompt_save_as)
        RenameMode.NEW_SLOT -> stringResource(R.string.ui_save_channel_rename_prompt_new_slot)
        RenameMode.RENAME -> stringResource(R.string.ui_save_channel_rename_prompt_rename)
    }
    val theme = LocalArgosyTheme.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    NestedModal(
        title = title,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_rename_footer_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_rename_footer_cancel)
        )
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = {
                Text(stringResource(R.string.ui_save_channel_rename_placeholder))
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            ModalActionButton(
                label = stringResource(R.string.ui_save_channel_rename_cancel_button),
                tint = theme.textDim,
                restLabelColor = theme.textPrimary,
                focused = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
            ModalActionButton(
                label = confirmLabel,
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = false,
                onClick = onConfirm,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SlotPickerOverlay(
    items: List<SlotPickerItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    onHintClick: (InputButton) -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = selectedIndex)

    NestedModal(
        title = stringResource(R.string.ui_save_channel_slot_picker_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_slot_picker_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_slot_picker_cancel)
        ),
        onHintClick = onHintClick
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = SLOT_PICKER_LIST_MAX_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(items, key = { _, item -> item.pickerKey }) { index, item ->
                SlotPickerRow(
                    item = item,
                    isSelected = index == selectedIndex,
                    onClick = { onItemClick(index) }
                )
            }
        }
    }
}

@Composable
private fun SlotPickerRow(
    item: SlotPickerItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val labelColor = when {
        item.isBlocked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        item.isNewSave -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .saveRowFocus(isSelected)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = if (item.isNewSave) Icons.Filled.Add else Icons.Filled.Save,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(if (item.isNewSave) Dimens.iconSm else Dimens.iconXs)
            )
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor
            )
        }
        if (item.isBlocked) {
            Text(
                text = stringResource(R.string.ui_save_channel_slot_picker_has_save_tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun MigrateConfirmationOverlay(channelName: String, onHintClick: (InputButton) -> Unit) {
    NestedModal(
        title = stringResource(R.string.ui_save_channel_migrate_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_migrate_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_migrate_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = stringResource(R.string.ui_save_channel_migrate_message, channelName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(R.string.ui_save_channel_migrate_note),
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
    saveCount: Int,
    onHintClick: (InputButton) -> Unit
) {
    NestedModal(
        title = stringResource(R.string.ui_save_channel_delete_legacy_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_delete_legacy_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_delete_legacy_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.ui_save_channel_delete_legacy_message,
                saveCount,
                channelName,
                saveCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(R.string.ui_save_channel_delete_legacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun localDeleteNote(onServer: Boolean): String = stringResource(
    if (onServer) R.string.ui_save_channel_delete_local_note else R.string.ui_save_channel_delete_only_copy_note
)

@Composable
private fun DeleteConfirmationOverlay(target: SaveDeleteTarget, onHintClick: (InputButton) -> Unit) {
    val title = when (target) {
        is SaveDeleteTarget.Channel -> stringResource(R.string.ui_save_channel_delete_slot_title)
        is SaveDeleteTarget.Version -> stringResource(R.string.ui_save_channel_delete_version_title)
        is SaveDeleteTarget.Archived -> stringResource(R.string.ui_save_channel_delete_archived_title)
    }
    val message = when (target) {
        is SaveDeleteTarget.Channel ->
            stringResource(R.string.ui_save_channel_delete_slot_message, target.channelName)
        is SaveDeleteTarget.Version -> stringResource(
            R.string.ui_save_channel_delete_version_message,
            formatSaveTimestamp(LocalContext.current, target.timestamp)
        )
        is SaveDeleteTarget.Archived -> pluralStringResource(
            R.plurals.ui_save_channel_delete_archived_message,
            target.cacheIds.size,
            target.cacheIds.size
        )
    }
    val note = when (target) {
        is SaveDeleteTarget.Channel -> stringResource(R.string.ui_save_channel_delete_slot_note)
        is SaveDeleteTarget.Version -> localDeleteNote(target.onServer)
        is SaveDeleteTarget.Archived -> localDeleteNote(target.allOnServer)
    }
    NestedModal(
        title = title,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_delete_slot_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_delete_slot_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = note,
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
    currentVersion: String?,
    onHintClick: (InputButton) -> Unit
) {
    NestedModal(
        title = stringResource(R.string.ui_save_channel_version_mismatch_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_version_mismatch_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_version_mismatch_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = stringResource(R.string.ui_save_channel_version_mismatch_saved_with),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "${
                savedCoreId ?: stringResource(R.string.ui_save_channel_version_mismatch_saved_core_unknown)
            } ${savedVersion ?: ""}".trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_save_channel_version_mismatch_current),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = "${
                currentCoreId ?: stringResource(R.string.ui_save_channel_version_mismatch_current_core_unknown)
            } ${currentVersion ?: ""}".trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        Text(
            text = stringResource(R.string.ui_save_channel_version_mismatch_warning),
            style = MaterialTheme.typography.bodySmall,
            color = LocalLauncherTheme.current.semanticColors.warning,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StateDeleteConfirmationOverlay(slotNumber: Int, onHintClick: (InputButton) -> Unit) {
    val slotLabel = if (slotNumber == -1) {
        stringResource(R.string.ui_save_channel_delete_state_target_auto)
    } else {
        stringResource(R.string.ui_save_channel_delete_state_target_slot, slotNumber)
    }
    NestedModal(
        title = stringResource(R.string.ui_save_channel_delete_state_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_delete_state_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_delete_state_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = stringResource(R.string.ui_save_channel_delete_state_message, slotLabel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(R.string.ui_save_channel_delete_state_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StateReplaceAutoConfirmationOverlay(slotNumber: Int, onHintClick: (InputButton) -> Unit) {
    NestedModal(
        title = stringResource(R.string.ui_save_channel_replace_auto_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.ui_save_channel_replace_auto_confirm),
            InputButton.B to stringResource(R.string.ui_save_channel_replace_auto_cancel)
        ),
        onHintClick = onHintClick
    ) {
        Text(
            text = stringResource(R.string.ui_save_channel_replace_auto_message, slotNumber),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(R.string.ui_save_channel_replace_auto_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
