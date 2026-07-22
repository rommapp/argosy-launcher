package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.common.color
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.common.savechannel.StateSlotRow
import com.nendo.argosy.ui.common.savechannel.SaveHistoryItem
import com.nendo.argosy.ui.common.savechannel.SaveSlotItem
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import com.nendo.argosy.ui.screens.gamedetail.components.icon
import com.nendo.argosy.util.formatSaveSize
import com.nendo.argosy.util.formatSaveTimestamp
import com.nendo.argosy.ui.util.touchOnly

@Composable
fun DualGameDetailLowerScreen(
    state: DualGameDetailUiState,
    slots: List<SaveSlotItem>,
    history: List<SaveHistoryItem>,
    saveFocusColumn: SaveFocusColumn,
    selectedSlotIndex: Int,
    selectedHistoryIndex: Int,
    stateEntries: List<UnifiedStateEntry> = emptyList(),
    selectedStateIndex: Int = 0,
    visibleOptions: List<GameDetailOption>,
    selectedScreenshotIndex: Int,
    selectedOptionIndex: Int,
    savesLoading: Boolean = false,
    savesApplying: Boolean = false,
    savesSyncing: Boolean = false,
    isDimmed: Boolean = false,
    onDimTapped: () -> Unit = {},
    onTabChanged: (DualGameDetailTab) -> Unit,
    onSlotTapped: (Int) -> Unit,
    onHistoryTapped: (Int) -> Unit,
    onStateTapped: (Int) -> Unit = {},
    onScreenshotSelected: (Int) -> Unit,
    onScreenshotView: (Int) -> Unit,
    onOptionSelected: (GameDetailOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabHeader(
                currentTab = state.currentTab,
                availableTabs = state.availableTabs,
                onTabChanged = onTabChanged
            )

            HorizontalDivider(color = theme.hairlineHigh)

            Box(modifier = Modifier.weight(1f)) {
                when (state.currentTab) {
                    DualGameDetailTab.SAVES -> SavesTabContent(
                        slots = slots,
                        history = history,
                        focusColumn = saveFocusColumn,
                        selectedSlotIndex = selectedSlotIndex,
                        selectedHistoryIndex = selectedHistoryIndex,
                        isLoading = savesLoading,
                        isApplying = savesApplying,
                        isSyncing = savesSyncing,
                        onSlotTapped = onSlotTapped,
                        onHistoryTapped = onHistoryTapped
                    )
                    DualGameDetailTab.STATES -> StatesTabContent(
                        entries = stateEntries,
                        selectedIndex = selectedStateIndex,
                        onStateTapped = onStateTapped
                    )
                    DualGameDetailTab.MEDIA -> MediaTabContent(
                        screenshots = state.screenshots,
                        selectedIndex = selectedScreenshotIndex,
                        onScreenshotSelected = onScreenshotSelected
                    )
                    DualGameDetailTab.OPTIONS -> OptionsTabContent(
                        visibleOptions = visibleOptions,
                        isPlayable = state.isPlayable,
                        downloadProgress = state.downloadProgress,
                        downloadState = state.downloadState,
                        isFavorite = state.isFavorite,
                        userRating = state.rating,
                        userDifficulty = state.userDifficulty,
                        status = state.status,
                        emulatorName = state.emulatorName,
                        coreName = state.selectedCoreName,
                        savePathOverride = state.savePathOverride,
                        displayTargetName = state.displayTargetName,
                        platformDisplayTargetName = state.platformDisplayTargetName,
                        variantName = state.selectedVariantName,
                        activeChannel = state.activeChannel,
                        activeSaveTimestamp = state.activeSaveTimestamp,
                        saveSyncStatusName = state.saveSyncStatusName,
                        isHidden = state.isHidden,
                        selectedIndex = selectedOptionIndex,
                        onOptionSelected = onOptionSelected
                    )
                }
            }
        }

        if (isDimmed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .touchOnly { onDimTapped() }
            )
        }
    }
}

@Composable
private fun TabHeader(
    currentTab: DualGameDetailTab,
    availableTabs: List<DualGameDetailTab>,
    onTabChanged: (DualGameDetailTab) -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val focusAccent = theme.focusAccent
        availableTabs.forEach { tab ->
            val isSelected = tab == currentTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .background(
                        if (isSelected) {
                            focusAccent.copy(alpha = 0.15f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .touchOnly { onTabChanged(tab) }
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
            ) {
                Text(
                    text = tab.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        lerp(focusAccent, Color.White, 0.45f)
                    } else {
                        theme.textDim
                    }
                )
            }
        }
    }
}

@Composable
private fun SavesTabContent(
    slots: List<SaveSlotItem>,
    history: List<SaveHistoryItem>,
    focusColumn: SaveFocusColumn,
    selectedSlotIndex: Int,
    selectedHistoryIndex: Int,
    isLoading: Boolean,
    isApplying: Boolean,
    isSyncing: Boolean = false,
    onSlotTapped: (Int) -> Unit,
    onHistoryTapped: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading saves...",
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSyncing) {
                ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SaveSlotsColumn(
                    slots = slots,
                    selectedIndex = selectedSlotIndex,
                    isFocused = focusColumn == SaveFocusColumn.SLOTS,
                    onSlotTapped = onSlotTapped,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                )

                VerticalDivider(color = theme.hairlineLow)

                SaveHistoryColumn(
                    history = history,
                    selectedIndex = selectedHistoryIndex,
                    isFocused = focusColumn == SaveFocusColumn.HISTORY,
                    slotName = slots.getOrNull(selectedSlotIndex)
                        ?.let { if (it.isCreateAction) null else it.displayName },
                    onHistoryTapped = onHistoryTapped,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                )
            }
        }

        if (isApplying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .touchOnly { },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Applying save...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    ArgosyProgressBar(
                        progress = null,
                        style = ProgressBarStyle.Working,
                        modifier = Modifier.fillMaxWidth(0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveSlotsColumn(
    slots: List<SaveSlotItem>,
    selectedIndex: Int,
    isFocused: Boolean,
    onSlotTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && isFocused) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(
            text = "Save Slots",
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(slots, key = { _, slot -> slot.slotKey }) { index, slot ->
                val isSelected = index == selectedIndex && isFocused
                if (slot.isCreateAction) {
                    NewSlotRow(
                        isSelected = isSelected,
                        onClick = { onSlotTapped(index) }
                    )
                } else {
                    SlotRow(
                        slot = slot,
                        isSelected = isSelected,
                        onClick = { onSlotTapped(index) }
                    )
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
    val theme = LocalArgosyTheme.current
    val accentColor = theme.focusAccent
    val textColor = if (slot.isActive) accentColor else theme.textPrimary
    val shape = RoundedCornerShape(Dimens.radiusControl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isSelected,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clip(shape)
            .touchOnly { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (slot.isActive) {
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(Dimens.dotSm)
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
                color = theme.textDim
            )
        }
    }
}

@Composable
private fun NewSlotRow(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isSelected,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clip(shape)
            .touchOnly { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = theme.focusAccent,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = "New Slot",
            style = MaterialTheme.typography.bodyMedium,
            color = theme.focusAccent
        )
    }
}

@Composable
private fun SaveHistoryColumn(
    history: List<SaveHistoryItem>,
    selectedIndex: Int,
    isFocused: Boolean,
    slotName: String?,
    onHistoryTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && isFocused) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(
            text = if (slotName != null) "History ($slotName)" else "History",
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
        )

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.spacingMd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saves yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.textDim
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(history) { index, item ->
                    HistoryRow(
                        item = item,
                        isSelected = index == selectedIndex && isFocused,
                        onClick = { onHistoryTapped(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: SaveHistoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isSelected,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clip(shape)
            .touchOnly { onClick() }
            .padding(horizontal = 12.dp, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatSaveTimestamp(item.timestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = theme.textPrimary
                )
                if (item.isActiveRestorePoint) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Active restore point",
                        tint = theme.focusAccent,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                }
                if (item.isLatest) {
                    Text(
                        text = "Latest",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.focusAccent
                    )
                }
            }
            Text(
                text = formatSaveSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim
            )
        }

        val syncTag = if (item.isSynced) "Synced" else "Local"
        val syncColor = if (item.isSynced) Color(0xFF4CAF50) else theme.textDim
        Text(
            text = "[$syncTag]",
            style = MaterialTheme.typography.labelSmall,
            color = syncColor
        )
    }
}

@Composable
private fun StatesTabContent(
    entries: List<UnifiedStateEntry>,
    selectedIndex: Int,
    onStateTapped: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No state slots",
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(entries) { index, entry ->
            StateSlotRow(
                entry = entry,
                isSelected = index == selectedIndex,
                onClick = { onStateTapped(index) },
                clickModifier = Modifier.touchOnly { onStateTapped(index) }
            )
        }
    }
}

@Composable
private fun MediaTabContent(
    screenshots: List<String>,
    selectedIndex: Int,
    onScreenshotSelected: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    if (screenshots.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No screenshots",
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textDim
            )
        }
    } else {
        val gridState = rememberLazyGridState()

        LaunchedEffect(selectedIndex) {
            if (selectedIndex >= 0) {
                val info = gridState.layoutInfo
                val viewport = info.viewportEndOffset - info.viewportStartOffset
                val itemH = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                val offset = -(viewport - itemH) / 2
                gridState.animateScrollToItem(selectedIndex, offset)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(MEDIA_GRID_COLUMNS),
            state = gridState,
            contentPadding = PaddingValues(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(screenshots) { index, path ->
                ScreenshotThumbnail(
                    path = path,
                    isSelected = index == selectedIndex,
                    onClick = { onScreenshotSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotThumbnail(
    path: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .argosyFocusIndicators(
                focused = isSelected,
                indicators = FocusIndicators.Tile,
                shape = shape
            )
            .clip(shape)
            .touchOnly { onClick() }
    ) {
        AsyncImage(
            model = rememberFileImageModel(path),
            contentDescription = "Screenshot",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private data class OptionEntry(
    val option: GameDetailOption,
    val icon: ImageVector,
    val label: String,
    val value: String? = null,
    val tint: Color? = null,
    val subLabel: String? = null,
    val subLabelSecondary: String? = null,
    val visualContent: (@Composable () -> Unit)? = null
)

@Composable
private fun OptionsTabContent(
    visibleOptions: List<GameDetailOption>,
    isPlayable: Boolean,
    downloadProgress: Float?,
    downloadState: String?,
    isFavorite: Boolean,
    userRating: Int?,
    userDifficulty: Int,
    status: String?,
    emulatorName: String?,
    coreName: String?,
    savePathOverride: String?,
    displayTargetName: String?,
    platformDisplayTargetName: String?,
    variantName: String?,
    activeChannel: String?,
    activeSaveTimestamp: Long?,
    saveSyncStatusName: String?,
    isHidden: Boolean,
    selectedIndex: Int,
    onOptionSelected: (GameDetailOption) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val emulatorText = emulatorName ?: "Platform Default"
    val coreText = coreName ?: "Default"
    val savePathText = if (savePathOverride != null) "Custom" else "Default"
    val displayTargetText = EmulatorDisplayTarget
        .fromString(displayTargetName ?: platformDisplayTargetName).displayName
    val variantText = variantName ?: "Default"
    val completionStatus = CompletionStatus.fromApiValue(status)

    val dlState = downloadState

    fun entryFor(option: GameDetailOption): OptionEntry = when (option) {
        GameDetailOption.PLAY -> {
            val label = when (dlState) {
                "EXTRACTING" -> "Extracting..."
                "DOWNLOADING" -> "Downloading ${((downloadProgress ?: 0f) * 100).toInt()}%"
                "QUEUED" -> "Queued..."
                "PAUSED" -> "Paused ${((downloadProgress ?: 0f) * 100).toInt()}%"
                "WAITING_FOR_STORAGE" -> "No Space"
                null -> if (isPlayable) "Play" else "Download"
                else -> if (isPlayable) "Play" else "Download"
            }
            val icon = when (dlState) {
                "EXTRACTING" -> Icons.Filled.FolderZip
                "DOWNLOADING", "QUEUED", "PAUSED", "WAITING_FOR_STORAGE" -> Icons.Filled.Download
                else -> if (isPlayable) Icons.Filled.PlayArrow else Icons.Filled.Download
            }
            val progressVisual: (@Composable () -> Unit)? = if (dlState == "DOWNLOADING" || dlState == "EXTRACTING") {
                {
                    ArgosyProgressBar(
                        progress = downloadProgress ?: 0f,
                        modifier = Modifier.width(80.dp)
                    )
                }
            } else null

            val showSaveInfo = isPlayable && dlState == null && activeSaveTimestamp != null
            val slotLabel = if (showSaveInfo) activeChannel ?: "Auto-save" else null
            val dateLabel = if (showSaveInfo) formatSaveTimestamp(activeSaveTimestamp!!) else null
            val statusVisual: (@Composable () -> Unit)? = if (showSaveInfo && saveSyncStatusName != null) {
                val status = runCatching { SaveSyncStatus.valueOf(saveSyncStatusName) }.getOrNull()
                if (status != null) {
                    {
                        Icon(
                            imageVector = status.icon,
                            contentDescription = null,
                            tint = theme.textDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null
            } else null

            OptionEntry(
                option, icon, label,
                subLabel = slotLabel,
                subLabelSecondary = dateLabel,
                visualContent = progressVisual ?: statusVisual
            )
        }
        GameDetailOption.RATING -> OptionEntry(
            option, Icons.Filled.Star, "Rating",
            visualContent = {
                PipDisplay(
                    filled = userRating ?: 0,
                    max = 10,
                    filledIcon = Icons.Filled.Star,
                    emptyIcon = Icons.Outlined.Star,
                    activeColor = ALauncherColors.TrophyAmber
                )
            }
        )
        GameDetailOption.DIFFICULTY -> OptionEntry(
            option, Icons.Filled.Whatshot, "Difficulty",
            visualContent = {
                PipDisplay(
                    filled = userDifficulty,
                    max = 10,
                    filledIcon = Icons.Filled.Whatshot,
                    emptyIcon = Icons.Outlined.Whatshot,
                    activeColor = ALauncherColors.Orange
                )
            }
        )
        GameDetailOption.STATUS -> OptionEntry(
            option,
            completionStatus?.icon ?: Icons.Filled.CheckCircle,
            "Status",
            visualContent = {
                if (completionStatus != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = completionStatus.icon,
                            contentDescription = null,
                            tint = completionStatus.color,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = completionStatus.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = completionStatus.color
                        )
                    }
                } else {
                    Text(
                        text = "Not set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim
                    )
                }
            }
        )
        GameDetailOption.TOGGLE_FAVORITE -> OptionEntry(
            option,
            if (isFavorite) Icons.Filled.Favorite
            else Icons.Filled.FavoriteBorder,
            if (isFavorite) "Remove from Favorites"
            else "Add to Favorites"
        )
        GameDetailOption.CHANGE_EMULATOR -> OptionEntry(
            option, Icons.Filled.Settings, "Change Emulator", emulatorText
        )
        GameDetailOption.CHANGE_CORE -> OptionEntry(
            option, Icons.Filled.Settings, "Change Core", coreText
        )
        GameDetailOption.SAVE_PATH -> OptionEntry(
            option, Icons.Filled.Folder, "Save Path", savePathText,
            subLabel = savePathOverride
        )
        GameDetailOption.DISPLAY_TARGET -> OptionEntry(
            option, Icons.Filled.Tv, "Display Target", displayTargetText
        )
        GameDetailOption.SELECT_VARIANT -> OptionEntry(
            option, Icons.Filled.Settings, "Select Variant", variantText
        )
        GameDetailOption.SELECT_DISC -> OptionEntry(
            option, Icons.Filled.Album, "Select Disc"
        )
        GameDetailOption.FILES -> OptionEntry(
            option, Icons.Filled.Checklist, "Files"
        )
        GameDetailOption.ADD_TO_COLLECTION -> OptionEntry(
            option, Icons.Filled.FolderSpecial, "Add to Collection"
        )
        GameDetailOption.REFRESH_METADATA -> OptionEntry(
            option, Icons.Filled.Refresh, "Refresh Metadata"
        )
        GameDetailOption.DELETE -> OptionEntry(
            option, Icons.Filled.Delete, "Delete from Library",
            tint = theme.destructive
        )
        GameDetailOption.HIDE -> if (isHidden) {
            OptionEntry(option, Icons.Filled.Visibility, "Unhide Game")
        } else {
            OptionEntry(option, Icons.Filled.VisibilityOff, "Hide Game", tint = theme.destructive)
        }
    }

    val actionGroup = setOf(GameDetailOption.PLAY)
    val userDataGroup = setOf(
        GameDetailOption.RATING, GameDetailOption.DIFFICULTY,
        GameDetailOption.STATUS, GameDetailOption.TOGGLE_FAVORITE
    )
    val managementGroup = setOf(
        GameDetailOption.CHANGE_EMULATOR,
        GameDetailOption.CHANGE_CORE,
        GameDetailOption.SAVE_PATH,
        GameDetailOption.DISPLAY_TARGET,
        GameDetailOption.SELECT_VARIANT,
        GameDetailOption.SELECT_DISC,
        GameDetailOption.FILES,
        GameDetailOption.ADD_TO_COLLECTION,
        GameDetailOption.REFRESH_METADATA
    )
    val dangerGroup = setOf(GameDetailOption.DELETE, GameDetailOption.HIDE)

    val groupOrder = listOf(actionGroup, userDataGroup, managementGroup, dangerGroup)
    val groups = groupOrder.map { groupSet ->
        visibleOptions.filter { it in groupSet }.map { entryFor(it) }
    }.filter { it.isNotEmpty() }

    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) {
            var lazyIndex = selectedIndex
            var cumulative = 0
            for (group in groups) {
                if (selectedIndex < cumulative + group.size) break
                cumulative += group.size
                lazyIndex++
            }
            val info = listState.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val itemH = info.visibleItemsInfo.firstOrNull()?.size ?: 0
            val offset = -(viewport - itemH) / 2
            listState.animateScrollToItem(lazyIndex, offset)
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        groups.forEachIndexed { groupIdx, group ->
            items(group.size, key = { group[it].option }) { i ->
                val entry = group[i]
                val itemIndex = groups.take(groupIdx)
                    .sumOf { it.size } + i
                OptionItem(
                    icon = entry.icon,
                    label = entry.label,
                    value = entry.value,
                    isSelected = itemIndex == selectedIndex,
                    tint = entry.tint,
                    subLabel = entry.subLabel,
                    subLabelSecondary = entry.subLabelSecondary,
                    visualContent = entry.visualContent,
                    onClick = { onOptionSelected(entry.option) }
                )
            }
            if (groupIdx < groups.lastIndex) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimens.spacingXs),
                        color = theme.hairlineLow
                    )
                }
            }
        }
    }
}

@Composable
private fun PipDisplay(
    filled: Int,
    max: Int,
    filledIcon: ImageVector,
    emptyIcon: ImageVector,
    activeColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..max) {
            Icon(
                imageVector = if (i <= filled) filledIcon else emptyIcon,
                contentDescription = null,
                tint = if (i <= filled) activeColor
                    else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(Dimens.iconXs)
            )
        }
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    value: String? = null,
    isSelected: Boolean,
    tint: Color? = null,
    subLabel: String? = null,
    subLabelSecondary: String? = null,
    visualContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isSelected,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clip(shape)
            .touchOnly { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: if (isSelected) {
                theme.focusAccent
            } else {
                theme.textDim
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint ?: theme.textPrimary
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (subLabelSecondary != null) {
                Text(
                    text = subLabelSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (visualContent != null) {
            visualContent()
        } else if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim
            )
        }
    }
}
