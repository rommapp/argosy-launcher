package com.nendo.argosy.ui.screens.collections.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import java.io.File
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

@Composable
fun WideGameCard(
    title: String,
    platformShortName: String,
    coverPath: String?,
    developer: String?,
    releaseYear: Int?,
    genre: String?,
    userRating: Int,
    userDifficulty: Int,
    achievementCount: Int,
    playTimeMinutes: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val cornerRadius = boxArtStyle.cornerRadiusDp
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier.padding(Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (coverPath != null) {
                        val imageData = if (coverPath.startsWith("/")) {
                            File(coverPath)
                        } else {
                            coverPath
                        }
                        AsyncImage(
                            model = imageData,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxSize(0.5f)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (developer != null || releaseYear != null || genre != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            developer?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (developer != null && (releaseYear != null || genre != null)) {
                                Text(
                                    text = "|",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            releaseYear?.let {
                                Text(
                                    text = it.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            if (releaseYear != null && genre != null) {
                                Text(
                                    text = "|",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            genre?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            WideGameCardFooter(
                platformShortName = platformShortName,
                cornerRadius = cornerRadius,
                isFocused = isFocused,
                userRating = userRating,
                userDifficulty = userDifficulty,
                achievementCount = achievementCount,
                playTimeMinutes = playTimeMinutes
            )
        }
    }
}

@Composable
private fun WideGameCardFooter(
    platformShortName: String,
    cornerRadius: Dp,
    isFocused: Boolean,
    userRating: Int,
    userDifficulty: Int,
    achievementCount: Int,
    playTimeMinutes: Int
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val platformShape = RoundedCornerShape(
        bottomStart = cornerRadius,
        topEnd = cornerRadius
    )

    val borderOffset = if (isFocused) boxArtStyle.borderThicknessDp else 0.dp
    val earSize = cornerRadius

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Top ear row (above the main footer content)
        Box(
            modifier = Modifier
                .offset(x = borderOffset, y = 1.dp)
                .size(earSize)
                .clip(remember(cornerRadius) { BottomLeftTopEarShape(cornerRadius) })
                .background(primaryColor)
        )

        // Main footer row with slug and metadata
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Metadata background (full width, flows behind slug)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(end = Dimens.spacingMd),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))

                if (userRating > 0) {
                    FooterStat(icon = Icons.Default.Star, value = "$userRating/10")
                }

                if (userDifficulty > 0) {
                    FooterStat(icon = Icons.Default.Whatshot, value = "$userDifficulty/10")
                }

                if (achievementCount > 0) {
                    FooterStat(icon = Icons.Default.EmojiEvents, value = "$achievementCount")
                }

                if (playTimeMinutes > 0) {
                    FooterStat(icon = Icons.Default.Schedule, value = formatPlayTime(playTimeMinutes))
                }
            }

            // Platform slug with right ear (on top)
            Row(
                modifier = Modifier.zIndex(1f),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .clip(platformShape)
                        .background(primaryColor)
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = platformShortName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .offset(x = (-1).dp, y = -borderOffset)
                        .size(earSize)
                        .clip(remember(cornerRadius) { BottomLeftRightEarShape(cornerRadius) })
                        .background(primaryColor)
                )
            }
        }
    }
}

private class BottomLeftTopEarShape(
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, r)
            lineTo(r, r)
            arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

private class BottomLeftRightEarShape(
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, r)
            lineTo(r, r)
            arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun FooterStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor
        )
    }
}

private fun formatPlayTime(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes < 600 -> {
        val hours = minutes / 60
        val mins = minutes % 60
        if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
    }
    else -> "${minutes / 60}h"
}

