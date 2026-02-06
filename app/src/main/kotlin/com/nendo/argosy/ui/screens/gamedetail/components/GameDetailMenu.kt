package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

enum class MenuItemType {
    PLAY,
    FAVORITE,
    OPTIONS,
    DETAILS,
    DESCRIPTION,
    SCREENSHOTS,
    ACHIEVEMENTS
}

data class GameDetailMenuState(
    val focusedIndex: Int = 0,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val isExtracting: Boolean = false,
    val downloadProgress: Float = 0f,
    val isFavorite: Boolean = false,
    val hasDescription: Boolean = true,
    val hasScreenshots: Boolean = true,
    val hasAchievements: Boolean = false,
    val saveStatus: SaveStatusInfo? = null,
    val downloadSizeBytes: Long? = null
) {
    val menuItems: List<MenuItemType>
        get() = buildList {
            add(MenuItemType.PLAY)
            add(MenuItemType.FAVORITE)
            add(MenuItemType.OPTIONS)
            add(MenuItemType.DETAILS)
            if (hasDescription) add(MenuItemType.DESCRIPTION)
            if (hasScreenshots) add(MenuItemType.SCREENSHOTS)
            if (hasAchievements) add(MenuItemType.ACHIEVEMENTS)
        }

    val focusedItem: MenuItemType?
        get() = menuItems.getOrNull(focusedIndex)

    fun indexOfItem(item: MenuItemType): Int = menuItems.indexOf(item)
}

@Composable
fun GameDetailMenu(
    state: GameDetailMenuState,
    onItemClick: (MenuItemType) -> Unit,
    onFocusChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val menuItems = state.menuItems

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = if (isCompact) Dimens.spacingXs else Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        horizontalAlignment = if (isCompact) Alignment.CenterHorizontally else Alignment.Start
    ) {

        menuItems.forEachIndexed { index, item ->
            val isFocused = index == state.focusedIndex

            when (item) {
                MenuItemType.PLAY -> {
                    PlayMenuItem(
                        isDownloaded = state.isDownloaded,
                        isDownloading = state.isDownloading,
                        isExtracting = state.isExtracting,
                        downloadProgress = state.downloadProgress,
                        isFocused = isFocused,
                        saveStatus = state.saveStatus,
                        downloadSizeBytes = state.downloadSizeBytes,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }

                MenuItemType.FAVORITE -> {
                    FavoriteMenuItem(
                        isFavorite = state.isFavorite,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }

                MenuItemType.OPTIONS -> {
                    OptionsMenuItem(
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                }

                MenuItemType.DETAILS -> {
                    IconTextMenuItem(
                        label = "Details",
                        icon = Icons.Default.Info,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }

                MenuItemType.DESCRIPTION -> {
                    IconTextMenuItem(
                        label = "Description",
                        icon = Icons.Default.Description,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }

                MenuItemType.SCREENSHOTS -> {
                    IconTextMenuItem(
                        label = "Screenshots",
                        icon = Icons.Default.Image,
                        isFocused = isFocused,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }

                MenuItemType.ACHIEVEMENTS -> {
                    IconTextMenuItem(
                        label = "Achievements",
                        icon = Icons.Default.EmojiEvents,
                        isFocused = isFocused,
                        isEnabled = state.hasAchievements,
                        isCompact = isCompact,
                        onClick = { onFocusChange(index); onItemClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayMenuItem(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isExtracting: Boolean,
    downloadProgress: Float,
    isFocused: Boolean,
    saveStatus: SaveStatusInfo?,
    downloadSizeBytes: Long?,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        isExtracting -> "Extracting..."
        isDownloading -> "${(downloadProgress * 100).toInt()}%"
        isDownloaded -> "Play"
        else -> "Download"
    }

    val containerColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    }

    val icon = when {
        isExtracting || isDownloading -> Icons.Default.Download
        isDownloaded -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }

    val isInProgress = isDownloading || isExtracting
    val showDownloadSize = !isDownloaded && !isInProgress && downloadSizeBytes != null

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = !isInProgress,
            shape = RoundedCornerShape(Dimens.radiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = if (isCompact) {
                androidx.compose.foundation.layout.PaddingValues(Dimens.spacingSm)
            } else {
                ButtonDefaults.ContentPadding
            },
            modifier = if (isCompact) Modifier else Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(if (isCompact) Dimens.iconMd else Dimens.iconSm)
            )
            if (!isCompact) {
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                if (showDownloadSize) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = formatFileSize(downloadSizeBytes!!),
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (saveStatus != null && isDownloaded && !isCompact) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            SaveStatusRow(
                status = saveStatus,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun FavoriteMenuItem(
    isFavorite: Boolean,
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val favoriteColor = MaterialTheme.colorScheme.error
    val softRed = favoriteColor.copy(alpha = 0.6f)

    val iconTint = when {
        isFavorite -> favoriteColor
        isFocused -> softRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder

    val textColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isFocused) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Favorite",
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun OptionsMenuItem(
    isFocused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val iconTint = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val textStyle = if (isFocused) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Options",
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Options",
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Options",
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun IconTextMenuItem(
    label: String,
    icon: ImageVector,
    isFocused: Boolean,
    isCompact: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val textStyle = if (isFocused && isEnabled) {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    } else {
        MaterialTheme.typography.bodyMedium
    }

    MenuItemWithLeftBorder(
        isFocused = isFocused && isEnabled,
        isEnabled = isEnabled,
        isCompact = isCompact,
        onClick = onClick
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = label,
                    style = textStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun MenuItemWithLeftBorder(
    isFocused: Boolean,
    isEnabled: Boolean = true,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }

    Box(
        modifier = Modifier
            .then(if (isCompact) Modifier else Modifier.fillMaxWidth())
            .then(
                if (isFocused) {
                    Modifier.drawBehind {
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset.Zero,
                            size = size.copy(width = borderWidthPx)
                        )
                    }
                } else Modifier
            )
            .then(
                if (isEnabled) Modifier.clickableNoFocus(onClick = onClick)
                else Modifier
            )
            .padding(
                start = Dimens.spacingMd,
                end = if (isCompact) Dimens.spacingSm else 0.dp,
                top = Dimens.spacingSm,
                bottom = Dimens.spacingSm
            ),
        contentAlignment = if (isCompact) Alignment.Center else Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
internal fun EndWeightedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val processed = preprocessPlatformName(text)
    val words = processed.split(" ")
    if (words.size <= 2) {
        Text(text = processed, style = style, color = color, modifier = modifier)
        return
    }

    val lines = buildEndWeightedLines(words)
    Column(modifier = modifier) {
        lines.forEach { line ->
            Text(
                text = line,
                style = style,
                color = color,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private val KEEP_TOGETHER_FRAGMENTS = listOf(
    "Game Boy",
    "Neo Geo",
    "PC Engine",
    "Master System",
    "Mega Drive",
    "Game Gear",
    "Sega CD",
    "Sega Saturn",
    "Virtual Boy",
    "Wii U",
    "Xbox 360",
    "Xbox One",
    "Series X"
)

private fun preprocessPlatformName(text: String): String {
    var result = text
    for (fragment in KEEP_TOGETHER_FRAGMENTS) {
        result = result.replace(fragment, fragment.replace(" ", "\u00A0"))
    }
    return result
}

private fun buildEndWeightedLines(words: List<String>): List<String> {
    if (words.size <= 1) return words

    var bestSplit = 1
    var bestDiff = Int.MAX_VALUE

    for (splitAt in 1 until words.size) {
        val firstLen = words.subList(0, splitAt).sumOf { it.length } + splitAt - 1
        val lastLen = words.subList(splitAt, words.size).sumOf { it.length } + (words.size - splitAt - 1)

        if (lastLen >= firstLen) {
            val diff = lastLen - firstLen
            if (diff < bestDiff) {
                bestDiff = diff
                bestSplit = splitAt
            }
        }
    }

    val firstPart = words.subList(0, bestSplit).joinToString("\u00A0")
    val lastPart = words.subList(bestSplit, words.size).joinToString("\u00A0")

    return listOf(firstPart, lastPart)
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
