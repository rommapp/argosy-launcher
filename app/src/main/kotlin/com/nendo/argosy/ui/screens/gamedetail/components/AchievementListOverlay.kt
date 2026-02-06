package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun AchievementListOverlay(
    visible: Boolean,
    gameTitle: String,
    achievements: List<AchievementUi>,
    focusIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                AchievementListHeader(
                    gameTitle = gameTitle,
                    achievements = achievements
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val unlocked = achievements.filter { it.isUnlocked }
                val locked = achievements.filter { !it.isUnlocked }
                val allItems = unlocked + locked

                val listState = rememberLazyListState()

                LaunchedEffect(focusIndex) {
                    if (allItems.isNotEmpty() && focusIndex in allItems.indices) {
                        listState.animateScrollToItem(
                            index = focusIndex,
                            scrollOffset = -200
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Dimens.spacingXl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    if (unlocked.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(Dimens.spacingMd))
                            SectionLabel(
                                text = "UNLOCKED (${unlocked.size})",
                                color = ALauncherColors.TrophyAmber
                            )
                        }
                    }

                    itemsIndexed(allItems) { index, achievement ->
                        val isUnlockedItem = index < unlocked.size
                        val isFocused = index == focusIndex

                        if (!isUnlockedItem && index == unlocked.size) {
                            Spacer(modifier = Modifier.height(Dimens.spacingMd))
                            SectionLabel(
                                text = "LOCKED (${locked.size})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AchievementRow(
                            achievement = achievement,
                            isLocked = !isUnlockedItem,
                            isFocused = isFocused
                        )
                    }

                    item { Spacer(modifier = Modifier.height(Dimens.spacingLg)) }
                }

                FooterBar(
                    hints = listOf(
                        InputButton.DPAD_VERTICAL to "Navigate",
                        InputButton.B to "Back"
                    )
                )
            }
        }
    }
}

@Composable
private fun AchievementListHeader(
    gameTitle: String,
    achievements: List<AchievementUi>
) {
    val unlocked = achievements.count { it.isUnlocked }
    val total = achievements.size
    val percentage = if (total > 0) (unlocked * 100 / total) else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.EmojiEvents,
            contentDescription = null,
            tint = ALauncherColors.TrophyAmber,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "$unlocked/$total ($percentage%)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(vertical = Dimens.spacingSm)
    )
}

@Composable
private fun AchievementRow(
    achievement: AchievementUi,
    isLocked: Boolean,
    isFocused: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "achievement_scale"
    )

    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isLocked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (achievement.badgeUrl != null && !isLocked) {
                AsyncImage(
                    model = achievement.badgeUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant
                    else ALauncherColors.TrophyAmber,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.onSurface
            )
            achievement.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        if (!isLocked) {
            val tierColor = if (achievement.isUnlockedHardcore) ALauncherColors.StarGold
            else ALauncherColors.Orange
            val tierLabel = if (achievement.isUnlockedHardcore) "Hardcore" else "Casual"

            Text(
                text = tierLabel,
                style = MaterialTheme.typography.labelSmall,
                color = tierColor
            )
        } else {
            Text(
                text = "${achievement.points} pts",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
