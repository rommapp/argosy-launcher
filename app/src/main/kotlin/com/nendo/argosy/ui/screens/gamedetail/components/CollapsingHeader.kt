package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import java.io.File

private val EXPANDED_COVER_WIDTH = 200.dp
private val EXPANDED_COVER_HEIGHT = 280.dp
private val COLLAPSED_THUMBNAIL_SIZE = 48.dp
private val COLLAPSED_BAR_HEIGHT = 64.dp

data class CollapsingHeaderState(
    val isCollapsed: Boolean = false
)

@Composable
fun CollapsingHeader(
    game: GameDetailUi,
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier
) {
    val collapseProgress by animateFloatAsState(
        targetValue = if (state.isCollapsed) 1f else 0f,
        label = "collapse_progress"
    )

    Box(modifier = modifier) {
        ExpandedHeader(
            game = game,
            modifier = Modifier
                .alpha(1f - collapseProgress)
                .graphicsLayer {
                    scaleX = 1f - (collapseProgress * 0.1f)
                    scaleY = 1f - (collapseProgress * 0.1f)
                }
        )

        if (collapseProgress > 0f) {
            CollapsedHeader(
                game = game,
                modifier = Modifier
                    .alpha(collapseProgress)
            )
        }
    }
}

@Composable
fun StickyCollapsedHeader(
    game: GameDetailUi,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        CollapsedHeader(game = game)
    }
}

@Composable
fun ExpandedHeader(
    game: GameDetailUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
    ) {
        val imageData = game.coverPath?.let { path ->
            if (path.startsWith("/")) File(path) else path
        }
        AsyncImage(
            model = imageData,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(EXPANDED_COVER_WIDTH)
                .height(EXPANDED_COVER_HEIGHT)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(modifier = Modifier.weight(1f)) {
            GameTitle(
                title = game.title,
                titleStyle = MaterialTheme.typography.displaySmall,
                titleColor = MaterialTheme.colorScheme.onSurface,
                adaptiveSize = true
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.Bottom
            ) {
                EndWeightedText(
                    text = game.platformName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                game.releaseYear?.let { year ->
                    Text(text = "|", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXs))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                game.developer?.let { dev ->
                    Text(
                        text = dev,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                game.genre?.let { genre ->
                    if (game.developer != null) {
                        Text(text = "|", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)) {
                game.players?.let { players ->
                    MetadataChip(label = "Players", value = players)
                }
                game.rating?.let { rating ->
                    CommunityRatingChip(rating = rating)
                }
                if (game.userRating > 0) {
                    RatingChip(
                        label = "My Rating",
                        value = game.userRating,
                        icon = Icons.Default.Star,
                        iconColor = ALauncherColors.StarGold
                    )
                }
                if (game.userDifficulty > 0) {
                    RatingChip(
                        label = "Difficulty",
                        value = game.userDifficulty,
                        icon = Icons.Default.Whatshot,
                        iconColor = ALauncherColors.DifficultyRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            if (game.playTimeMinutes > 0 || game.status != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)) {
                    if (game.playTimeMinutes > 0) {
                        PlayTimeChip(minutes = game.playTimeMinutes)
                    }
                    game.status?.let { status ->
                        StatusChip(statusValue = status)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CollapsedHeader(
    game: GameDetailUi,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(COLLAPSED_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        val imageData = game.coverPath?.let { path ->
            if (path.startsWith("/")) File(path) else path
        }
        AsyncImage(
            model = imageData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(COLLAPSED_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = game.platformName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (game.rating != null || game.userRating > 0 || game.userDifficulty > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    game.rating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${rating.toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (game.userRating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = ALauncherColors.StarGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${game.userRating}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (game.userDifficulty > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                tint = ALauncherColors.DifficultyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${game.userDifficulty}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (game.playTimeMinutes > 0 || game.playCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (game.playTimeMinutes > 0) {
                        Text(
                            text = formatPlayTime(game.playTimeMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (game.playCount > 0) {
                        Text(
                            text = if (game.playCount == 1) "1 play" else "${game.playCount} plays",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
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
