package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi
import com.nendo.argosy.ui.theme.Dimens

private val goldColor = Color(0xFFFFD700)
private val bronzeColor = Color(0xFFCD7F32)

@Composable
fun AchievementRow(achievement: AchievementUi) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val lockedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    val accentColor = when {
        achievement.isUnlockedHardcore -> goldColor
        achievement.isUnlocked -> bronzeColor
        else -> lockedColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.radiusSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(Dimens.settingsItemMinHeight)
        ) {
            val badgeShape = RoundedCornerShape(Dimens.radiusSm)
            val badgeModifier = Modifier
                .size(Dimens.iconXl)
                .clip(badgeShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (achievement.isUnlockedHardcore) {
                        Modifier
                            .shadow(4.dp, badgeShape, spotColor = goldColor.copy(alpha = 0.5f))
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        goldColor,
                                        Color(0xFFFFF8DC),
                                        goldColor
                                    )
                                ),
                                shape = badgeShape
                            )
                    } else if (achievement.isUnlocked) {
                        Modifier.border(1.dp, bronzeColor.copy(alpha = 0.6f), badgeShape)
                    } else {
                        Modifier
                    }
                )

            Box(
                modifier = badgeModifier,
                contentAlignment = Alignment.Center
            ) {
                if (achievement.badgeUrl != null) {
                    AsyncImage(
                        model = achievement.badgeUrl,
                        contentDescription = achievement.title,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (!achievement.isUnlocked) {
                            ColorFilter.colorMatrix(grayscaleMatrix)
                        } else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (achievement.isUnlocked) 1f else 0.7f)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(Dimens.iconLg)
                    )
                }
            }
            Text(
                text = "${achievement.points} pts",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = Dimens.borderMedium)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!achievement.description.isNullOrBlank()) {
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AchievementColumn(
    achievements: List<AchievementUi>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        achievements.forEach { achievement ->
            AchievementRow(achievement)
        }
    }
}
