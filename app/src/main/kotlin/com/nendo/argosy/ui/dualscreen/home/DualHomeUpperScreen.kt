/**
 * DUAL-SCREEN COMPONENT - Upper display game showcase.
 * Runs in main process (MainActivity).
 * Receives selection from lower display via broadcasts.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.theme.ALauncherColors
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

private val PLATFORM_KEEP_TOGETHER = listOf(
    "Game Boy",
    "Nintendo 64",
    "Nintendo DS",
    "Nintendo 3DS",
    "Super Nintendo",
    "Neo Geo",
    "Master System",
    "Mega Drive",
    "PC Engine",
    "PlayStation Portable",
    "PlayStation Vita"
)

private fun splitPlatformName(name: String): List<String> {
    var processed = name
    for (phrase in PLATFORM_KEEP_TOGETHER) {
        processed = processed.replace(phrase, phrase.replace(" ", "\u00A0"))
    }

    val words = processed.split(" ")
    if (words.size <= 1) return listOf(name)

    // End-weighted: make bottom row heavier
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

    val firstPart = words.subList(0, bestSplit).joinToString(" ").replace("\u00A0", " ")
    val lastPart = words.subList(bestSplit, words.size).joinToString(" ").replace("\u00A0", " ")

    return listOf(firstPart, lastPart)
}

data class DualHomeShowcaseState(
    val gameId: Long = -1,
    val title: String = "",
    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val platformName: String = "",
    val platformSlug: String = "",
    val playTimeMinutes: Int = 0,
    val lastPlayedAt: Long = 0,
    val status: String? = null,
    val communityRating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val description: String? = null,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val titleId: String? = null,
    val isFavorite: Boolean = false
)

@Composable
fun DualHomeUpperScreen(
    state: DualHomeShowcaseState,
    footerHints: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Full-bleed background image (no blur)
        if (state.backgroundPath != null || state.coverPath != null) {
            AsyncImage(
                model = File(state.backgroundPath ?: state.coverPath!!),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header bar with semi-transparent background
            ShowcaseHeader(
                title = state.title,
                platformName = state.platformName,
                developer = state.developer,
                releaseYear = state.releaseYear,
                titleId = state.titleId,
                communityRating = state.communityRating,
                userRating = state.userRating,
                userDifficulty = state.userDifficulty
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )

            // Spacer to push content down
            Spacer(modifier = Modifier.weight(1f))

            // Stats card (floating in corner)
            if (state.gameId > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    StatsCard(
                        playTimeMinutes = state.playTimeMinutes,
                        lastPlayedAt = state.lastPlayedAt,
                        status = state.status
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )

            // Footer hints
            footerHints()
        }
    }
}

@Composable
private fun ShowcaseHeader(
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
        // Title (left)
        Column(modifier = Modifier.weight(1f)) {
            if (title.isEmpty()) {
                Text(
                    text = "Select a game",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                GameTitle(
                    title = title,
                    titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    titleColor = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
            if (titleId != null) {
                Text(
                    text = "TitleID: $titleId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Ratings cluster (center)
        RatingsCluster(
            communityRating = communityRating,
            userRating = userRating,
            userDifficulty = userDifficulty
        )

        // Platform and developer/year (right)
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
                    text = listOfNotNull(developer, releaseYear?.toString()).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RatingsCluster(
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
            RatingItem(
                icon = Icons.Default.People,
                value = "${rating.toInt()}",
                iconColor = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(24.dp))
        }

        if (userRating > 0) {
            RatingItem(
                icon = Icons.Default.Star,
                value = "$userRating",
                iconColor = ALauncherColors.StarGold
            )
            if (userDifficulty > 0) {
                Spacer(modifier = Modifier.width(24.dp))
            }
        }

        if (userDifficulty > 0) {
            RatingItem(
                icon = Icons.Default.Whatshot,
                value = "$userDifficulty",
                iconColor = ALauncherColors.DifficultyRed
            )
        }
    }
}

@Composable
private fun RatingItem(
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
private fun StatsCard(
    playTimeMinutes: Int,
    lastPlayedAt: Long,
    status: String?
) {
    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Play time
        Text(
            text = formatPlayTime(playTimeMinutes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Last played
        if (lastPlayedAt > 0) {
            Text(
                text = formatLastPlayed(lastPlayedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status
        if (status != null) {
            Text(
                text = status.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
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
