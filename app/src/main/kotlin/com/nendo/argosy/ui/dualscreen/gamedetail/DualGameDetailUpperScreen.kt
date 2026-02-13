/**
 * DUAL-SCREEN COMPONENT - Upper display game detail view.
 * Shows rich info: boxart, description, achievements.
 * When screenshot is selected on lower, becomes full viewer.
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.GameTitle
import androidx.compose.material3.OutlinedTextField
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.screens.gamedetail.RatingType
import com.nendo.argosy.ui.screens.gamedetail.modals.RatingPickerModal
import com.nendo.argosy.ui.screens.gamedetail.modals.StatusPickerModal
import com.nendo.argosy.ui.util.touchOnly
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun DualGameDetailUpperScreen(
    state: DualGameDetailUpperState,
    onModalRatingSelect: (Int) -> Unit = {},
    onModalStatusSelect: (String) -> Unit = {},
    onModalEmulatorSelect: (Int) -> Unit = {},
    onModalCollectionToggle: (Long) -> Unit = {},
    onModalCollectionShowCreate: () -> Unit = {},
    onModalCollectionCreate: (String) -> Unit = {},
    onModalCollectionCreateDismiss: () -> Unit = {},
    onSaveNameTextChange: (String) -> Unit = {},
    onSaveNameConfirm: () -> Unit = {},
    onModalDismiss: () -> Unit = {},
    footerHints: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewerPath = state.viewerScreenshotIndex?.let { idx ->
        state.screenshots.getOrNull(idx)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (viewerPath != null) {
            ScreenshotViewer(
                imagePath = viewerPath,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            GameInfoDisplay(
                state = state,
                footerHints = footerHints
            )
        }

        when (state.modalType) {
            ActiveModal.RATING -> RatingPickerModal(
                type = RatingType.OPINION,
                value = state.modalRatingValue,
                onValueChange = onModalRatingSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.DIFFICULTY -> RatingPickerModal(
                type = RatingType.DIFFICULTY,
                value = state.modalRatingValue,
                onValueChange = onModalRatingSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.STATUS -> StatusPickerModal(
                selectedValue = state.modalStatusSelected,
                currentValue = state.modalStatusCurrent,
                onSelect = onModalStatusSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.EMULATOR -> DualEmulatorPickerContent(
                emulatorNames = state.emulatorNames,
                emulatorVersions = state.emulatorVersions,
                currentEmulatorName = state.emulatorCurrentName,
                focusIndex = state.emulatorFocusIndex,
                onSelect = onModalEmulatorSelect,
                onDismiss = onModalDismiss
            )
            ActiveModal.COLLECTION -> {
                DualCollectionModalContent(
                    items = state.collectionItems,
                    focusIndex = state.collectionFocusIndex,
                    showCreateDialog = state.showCreateDialog,
                    onToggle = onModalCollectionToggle,
                    onShowCreate = onModalCollectionShowCreate,
                    onCreate = onModalCollectionCreate,
                    onCreateDismiss = onModalCollectionCreateDismiss,
                    onDismiss = onModalDismiss
                )
            }
            ActiveModal.SAVE_NAME -> DualSaveNamePrompt(
                text = state.saveNameText,
                onTextChange = onSaveNameTextChange,
                onConfirm = onSaveNameConfirm,
                onDismiss = onModalDismiss
            )
            ActiveModal.NONE -> {}
        }
    }
}

@Composable
private fun ScreenshotViewer(
    imagePath: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = if (imagePath.startsWith("/")) File(imagePath)
                else imagePath,
            contentDescription = "Screenshot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun GameInfoDisplay(
    state: DualGameDetailUpperState,
    footerHints: @Composable () -> Unit
) {
    // Background image (blurred)
    if (state.backgroundPath != null || state.coverPath != null) {
        AsyncImage(
            model = File(state.backgroundPath ?: state.coverPath!!),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header bar
        DetailHeader(
            title = state.title,
            platformName = state.platformName,
            developer = state.developer,
            releaseYear = state.releaseYear,
            titleId = state.titleId,
            communityRating = state.communityRating,
            userRating = state.rating ?: 0,
            userDifficulty = state.userDifficulty
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )

        // Main content area
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Cover art
            if (state.coverPath != null) {
                AsyncImage(
                    model = File(state.coverPath),
                    contentDescription = state.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Info column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                if (state.description != null) {
                    Text(
                        text = state.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Play time
                    Column {
                        Text(
                            text = formatPlayTime(state.playTimeMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Play Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Last played
                    if (state.lastPlayedAt > 0) {
                        Column {
                            Text(
                                text = formatLastPlayed(state.lastPlayedAt),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Last Played",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Status
                    if (state.status != null) {
                        Column {
                            Text(
                                text = state.status.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Achievements
                if (state.achievementCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${state.earnedAchievementCount}/${state.achievementCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Achievements",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )

        // Footer hints
        footerHints()
    }
}

private val PLATFORM_KEEP_TOGETHER = listOf(
    "Game Boy", "Nintendo 64", "Nintendo DS", "Nintendo 3DS",
    "Super Nintendo", "Neo Geo", "Master System", "Mega Drive",
    "PC Engine", "PlayStation Portable", "PlayStation Vita"
)

private fun splitPlatformName(name: String): List<String> {
    var processed = name
    for (phrase in PLATFORM_KEEP_TOGETHER) {
        processed = processed.replace(phrase, phrase.replace(" ", "\u00A0"))
    }
    val words = processed.split(" ")
    if (words.size <= 1) return listOf(name)

    var bestSplit = 1
    var bestDiff = Int.MAX_VALUE
    for (splitAt in 1 until words.size) {
        val firstLen = words.subList(0, splitAt).sumOf { it.length } + splitAt - 1
        val lastLen = words.subList(splitAt, words.size).sumOf { it.length } + (words.size - splitAt - 1)
        if (lastLen >= firstLen) {
            val diff = lastLen - firstLen
            if (diff < bestDiff) { bestDiff = diff; bestSplit = splitAt }
        }
    }
    val firstPart = words.subList(0, bestSplit).joinToString(" ").replace("\u00A0", " ")
    val lastPart = words.subList(bestSplit, words.size).joinToString(" ").replace("\u00A0", " ")
    return listOf(firstPart, lastPart)
}

@Composable
private fun DetailHeader(
    title: String,
    platformName: String,
    developer: String?,
    releaseYear: Int?,
    titleId: String?,
    communityRating: Float?,
    userRating: Int,
    userDifficulty: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (title.isEmpty()) {
                Text(
                    text = "Game Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                GameTitle(
                    title = title,
                    titleStyle = MaterialTheme.typography.titleLarge
                        .copy(fontWeight = FontWeight.Bold),
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
            if (titleId != null) {
                Text(
                    text = "TitleID: $titleId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.7f)
                )
            }
        }

        DetailRatingsCluster(
            communityRating = communityRating,
            userRating = userRating,
            userDifficulty = userDifficulty
        )

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            val platformLines = splitPlatformName(platformName)
            platformLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (developer != null || releaseYear != null) {
                Text(
                    text = listOfNotNull(
                        developer, releaseYear?.toString()
                    ).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRatingsCluster(
    communityRating: Float?,
    userRating: Int,
    userDifficulty: Int
) {
    val hasAnyRating = communityRating != null || userRating > 0 || userDifficulty > 0
    if (!hasAnyRating) return

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        communityRating?.let { rating ->
            DetailRatingItem(
                icon = Icons.Default.People,
                value = "${rating.toInt()}",
                iconColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(24.dp))
        }
        if (userRating > 0) {
            DetailRatingItem(
                icon = Icons.Default.Star,
                value = "$userRating",
                iconColor = ALauncherColors.StarGold
            )
            if (userDifficulty > 0) {
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
        if (userDifficulty > 0) {
            DetailRatingItem(
                icon = Icons.Default.Whatshot,
                value = "$userDifficulty",
                iconColor = ALauncherColors.DifficultyRed
            )
        }
    }
}

@Composable
private fun DetailRatingItem(
    icon: ImageVector,
    value: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DualEmulatorPickerContent(
    emulatorNames: List<String>,
    emulatorVersions: List<String>,
    currentEmulatorName: String?,
    focusIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .touchOnly { }
                .padding(24.dp)
        ) {
            Text(
                text = "SELECT EMULATOR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item {
                    val isSelected = focusIndex == 0
                    val isCurrent = currentEmulatorName == null
                    EmulatorPickerItem(
                        name = "Use Platform Default",
                        version = null,
                        isSelected = isSelected,
                        isCurrent = isCurrent,
                        onClick = { onSelect(0) }
                    )
                }
                itemsIndexed(emulatorNames) { index, name ->
                    val itemIndex = index + 1
                    val isSelected = focusIndex == itemIndex
                    val isCurrent = name == currentEmulatorName
                    EmulatorPickerItem(
                        name = name,
                        version = emulatorVersions.getOrNull(index)
                            ?.takeIf { it.isNotBlank() },
                        isSelected = isSelected,
                        isCurrent = isCurrent,
                        onClick = { onSelect(itemIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmulatorPickerItem(
    name: String,
    version: String?,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    .copy(alpha = 0.5f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .touchOnly { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold
                    else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (version != null) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isCurrent) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DualCollectionModalContent(
    items: List<DualCollectionItem>,
    focusIndex: Int,
    showCreateDialog: Boolean,
    onToggle: (Long) -> Unit,
    onShowCreate: () -> Unit,
    onCreate: (String) -> Unit,
    onCreateDismiss: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .touchOnly { }
                .padding(24.dp)
        ) {
            Text(
                text = "ADD TO COLLECTION",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(items) { index, item ->
                    val isSelected = focusIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                        .copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .touchOnly { onToggle(item.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.isInCollection) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                item(key = "create") {
                    val isSelected = focusIndex == items.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                        .copy(alpha = 0.5f)
                                else Color.Transparent
                            )
                            .then(
                                if (isSelected) Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .touchOnly { onShowCreate() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Create New Collection",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            CreateCollectionDialog(
                onDismiss = onCreateDismiss,
                onCreate = onCreate
            )
        }
    }
}

private fun formatPlayTime(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes / 60}h"
    }
}

private fun formatLastPlayed(timestamp: Long): String {
    if (timestamp <= 0) return ""

    val now = Instant.now()
    val lastPlayed = Instant.ofEpochMilli(timestamp)
    val daysBetween = ChronoUnit.DAYS.between(lastPlayed, now)

    return when {
        daysBetween == 0L -> "Today"
        daysBetween == 1L -> "Yesterday"
        daysBetween < 7 -> "$daysBetween days ago"
        daysBetween < 30 -> "${daysBetween / 7} weeks ago"
        else -> "${daysBetween / 30} months ago"
    }
}

@Composable
private fun DualSaveNamePrompt(
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .touchOnly { }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "CREATE SAVE SLOT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Slot name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .touchOnly { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .touchOnly { onConfirm() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
