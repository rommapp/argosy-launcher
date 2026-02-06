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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
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
    val isFavorite: Boolean = false,
    val hasDescription: Boolean = true,
    val hasScreenshots: Boolean = true,
    val hasAchievements: Boolean = false,
    val saveStatus: SaveStatusInfo? = null,
    val platformName: String = "",
    val playCount: Int = 0,
    val playTimeMinutes: Int = 0
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
    modifier: Modifier = Modifier
) {
    val menuItems = state.menuItems
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderWidth = 4.dp

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(end = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        // Header with platform and play stats
        MenuHeader(
            platformName = state.platformName,
            playCount = state.playCount,
            playTimeMinutes = state.playTimeMinutes
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        menuItems.forEachIndexed { index, item ->
            val isFocused = index == state.focusedIndex

            when (item) {
                MenuItemType.PLAY -> {
                    PlayMenuItem(
                        isDownloaded = state.isDownloaded,
                        isDownloading = state.isDownloading,
                        isFocused = isFocused,
                        saveStatus = state.saveStatus,
                        onClick = { onItemClick(item) }
                    )
                }

                MenuItemType.FAVORITE -> {
                    FavoriteMenuItem(
                        isFavorite = state.isFavorite,
                        isFocused = isFocused,
                        onClick = { onItemClick(item) }
                    )
                }

                MenuItemType.OPTIONS -> {
                    TextMenuItem(
                        label = "Options",
                        isFocused = isFocused,
                        onClick = { onItemClick(item) }
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                }

                MenuItemType.DETAILS -> {
                    TextMenuItem(
                        label = "Details",
                        isFocused = isFocused,
                        onClick = { onItemClick(item) }
                    )
                }

                MenuItemType.DESCRIPTION -> {
                    TextMenuItem(
                        label = "Description",
                        isFocused = isFocused,
                        onClick = { onItemClick(item) }
                    )
                }

                MenuItemType.SCREENSHOTS -> {
                    TextMenuItem(
                        label = "Screenshots",
                        isFocused = isFocused,
                        onClick = { onItemClick(item) }
                    )
                }

                MenuItemType.ACHIEVEMENTS -> {
                    TextMenuItem(
                        label = "Achievements",
                        isFocused = isFocused,
                        isEnabled = state.hasAchievements,
                        onClick = { onItemClick(item) }
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
    isFocused: Boolean,
    saveStatus: SaveStatusInfo?,
    onClick: () -> Unit
) {
    val label = when {
        isDownloading -> "Downloading..."
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

    Column {
        Button(
            onClick = onClick,
            enabled = !isDownloading,
            shape = RoundedCornerShape(Dimens.radiusMd),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (saveStatus != null && isDownloaded) {
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
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = iconTint,
                modifier = Modifier.size(Dimens.iconSm)
            )
            Text(
                text = "Favorite",
                style = textStyle,
                color = textColor
            )
        }
    }
}

@Composable
private fun TextMenuItem(
    label: String,
    isFocused: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val textColor = when {
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
        onClick = onClick
    ) {
        Text(
            text = label,
            style = textStyle,
            color = textColor
        )
    }
}

@Composable
private fun MenuItemWithLeftBorder(
    isFocused: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
            .padding(start = Dimens.spacingMd, top = Dimens.spacingSm, bottom = Dimens.spacingSm),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

@Composable
private fun MenuHeader(
    platformName: String,
    playCount: Int,
    playTimeMinutes: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        EndWeightedText(
            text = platformName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (playCount > 0 || playTimeMinutes > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playCount > 0) {
                    Text(
                        text = if (playCount == 1) "Played once" else "Played $playCount times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (playCount > 0 && playTimeMinutes > 0) {
                    Text(
                        text = "|",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                if (playTimeMinutes > 0) {
                    Text(
                        text = formatPlayTime(playTimeMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatPlayTime(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m"
        else -> {
            val hours = minutes / 60
            "${hours}h"
        }
    }
}

@Composable
private fun EndWeightedText(
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
