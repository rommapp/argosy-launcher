/**
 * DUAL-SCREEN COMPONENT - Lower display game detail with tabs.
 * Runs in :companion process (SecondaryHomeActivity).
 * Tabs: SAVES | MEDIA | OPTIONS
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.util.touchOnly
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DualGameDetailLowerScreen(
    state: DualGameDetailUiState,
    saves: List<SaveTimelineEntry>,
    channels: List<SaveChannelUi>,
    visibleOptions: List<GameDetailOption>,
    selectedSaveIndex: Int,
    selectedScreenshotIndex: Int,
    selectedOptionIndex: Int,
    isDimmed: Boolean = false,
    onTabChanged: (DualGameDetailTab) -> Unit,
    onSaveSelected: (String) -> Unit,
    onSaveRestore: (String) -> Unit,
    onSaveLock: (String) -> Unit,
    onSaveDelete: (String) -> Unit,
    onScreenshotSelected: (Int) -> Unit,
    onScreenshotView: (Int) -> Unit,
    onOptionSelected: (GameDetailOption) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabHeader(
                currentTab = state.currentTab,
                onTabChanged = onTabChanged
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = 0.2f)
            )

            Box(modifier = Modifier.weight(1f)) {
                when (state.currentTab) {
                    DualGameDetailTab.SAVES -> SavesTabContent(
                        saves = saves,
                        channels = channels,
                        selectedIndex = selectedSaveIndex,
                        onSaveSelected = onSaveSelected
                    )
                    DualGameDetailTab.MEDIA -> MediaTabContent(
                        screenshots = state.screenshots,
                        selectedIndex = selectedScreenshotIndex,
                        onScreenshotSelected = onScreenshotSelected
                    )
                    DualGameDetailTab.OPTIONS -> OptionsTabContent(
                        visibleOptions = visibleOptions,
                        isPlayable = state.isPlayable,
                        isFavorite = state.isFavorite,
                        userRating = state.rating,
                        userDifficulty = state.userDifficulty,
                        status = state.status,
                        emulatorName = state.emulatorName,
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
                    .touchOnly { }
            )
        }
    }
}

@Composable
private fun TabHeader(
    currentTab: DualGameDetailTab,
    onTabChanged: (DualGameDetailTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DualGameDetailTab.entries.forEach { tab ->
            val isSelected = tab == currentTab
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .touchOnly { onTabChanged(tab) }
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = tab.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SavesTabContent(
    saves: List<SaveTimelineEntry>,
    channels: List<SaveChannelUi>,
    selectedIndex: Int,
    onSaveSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Channel selector (simplified)
        if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CHANNEL:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channels.firstOrNull { it.isActive }?.name ?: "Main",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (saves.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No saves yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(saves) { index, save ->
                    SaveTimelineItem(
                        entry = save,
                        isSelected = index == selectedIndex,
                        onClick = { onSaveSelected(save.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveTimelineItem(
    entry: SaveTimelineEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            )
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .touchOnly { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = entry.label ?: formatTimestamp(entry.timestamp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (entry.label != null) {
                Text(
                    text = formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Locked",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = when {
                    entry.isSynced -> Icons.Filled.CheckCircle
                    entry.isRemoteOnly -> Icons.Filled.Cloud
                    entry.isLocal -> Icons.Filled.CloudOff
                    else -> Icons.Filled.CloudOff
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = when {
                    entry.isSynced -> Color(0xFF4CAF50)
                    entry.isRemoteOnly -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
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
    if (screenshots.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No screenshots",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .touchOnly { onClick() }
    ) {
        AsyncImage(
            model = if (path.startsWith("/")) File(path) else path,
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
    val visualContent: (@Composable () -> Unit)? = null
)

@Composable
private fun OptionsTabContent(
    visibleOptions: List<GameDetailOption>,
    isPlayable: Boolean,
    isFavorite: Boolean,
    userRating: Int?,
    userDifficulty: Int,
    status: String?,
    emulatorName: String?,
    selectedIndex: Int,
    onOptionSelected: (GameDetailOption) -> Unit
) {
    val emulatorText = emulatorName ?: "Platform Default"
    val completionStatus = CompletionStatus.fromApiValue(status)

    fun entryFor(option: GameDetailOption): OptionEntry = when (option) {
        GameDetailOption.PLAY -> OptionEntry(
            option,
            if (isPlayable) Icons.Filled.PlayArrow
            else Icons.Filled.Download,
            if (isPlayable) "Play" else "Download"
        )
        GameDetailOption.RATING -> OptionEntry(
            option, Icons.Filled.Star, "Rating",
            visualContent = {
                PipDisplay(
                    filled = userRating ?: 0,
                    max = 10,
                    filledIcon = Icons.Filled.Star,
                    emptyIcon = Icons.Outlined.Star,
                    activeColor = Color(0xFFFFB300)
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
                    activeColor = Color(0xFFFF7043)
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        GameDetailOption.ADD_TO_COLLECTION -> OptionEntry(
            option, Icons.Filled.FolderSpecial, "Add to Collection"
        )
        GameDetailOption.REFRESH_METADATA -> OptionEntry(
            option, Icons.Filled.Refresh, "Refresh Metadata"
        )
        GameDetailOption.DELETE -> OptionEntry(
            option, Icons.Filled.Delete, "Delete from Library",
            tint = Color(0xFFE57373)
        )
        GameDetailOption.HIDE -> OptionEntry(
            option, Icons.Filled.VisibilityOff, "Hide Game",
            tint = Color(0xFFE57373)
        )
    }

    val actionGroup = setOf(GameDetailOption.PLAY)
    val userDataGroup = setOf(
        GameDetailOption.RATING, GameDetailOption.DIFFICULTY,
        GameDetailOption.STATUS, GameDetailOption.TOGGLE_FAVORITE
    )
    val managementGroup = setOf(
        GameDetailOption.CHANGE_EMULATOR,
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groups.forEachIndexed { groupIdx, group ->
            items(group.size) { i ->
                val entry = group[i]
                val itemIndex = groups.take(groupIdx)
                    .sumOf { it.size } + i
                OptionItem(
                    icon = entry.icon,
                    label = entry.label,
                    value = entry.value,
                    isSelected = itemIndex == selectedIndex,
                    tint = entry.tint,
                    visualContent = entry.visualContent,
                    onClick = { onOptionSelected(entry.option) }
                )
            }
            if (groupIdx < groups.lastIndex) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onBackground
                            .copy(alpha = 0.12f)
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
                modifier = Modifier.size(14.dp)
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
    visualContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
            )
            .touchOnly { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (visualContent != null) {
            visualContent()
        } else if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diffMs = now.time - timestamp
    val diffDays = diffMs / (1000 * 60 * 60 * 24)

    return when {
        diffDays == 0L -> {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Today ${format.format(date)}"
        }
        diffDays == 1L -> "Yesterday"
        diffDays < 7 -> "$diffDays days ago"
        else -> {
            val format = SimpleDateFormat("MMM d", Locale.getDefault())
            format.format(date)
        }
    }
}
