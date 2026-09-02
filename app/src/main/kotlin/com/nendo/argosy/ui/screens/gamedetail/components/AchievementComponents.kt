package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.ui.common.achievementTypeColor
import com.nendo.argosy.ui.common.achievementTypeLabelRes
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.util.clickableNoFocus

private val hardcoreColor = ColorTokens.Domain.AchievementTier.hardcore
private val softcoreColor = ColorTokens.Domain.AchievementTier.softcore

@Composable
fun AchievementRow(
    achievement: AchievementUi,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val lockedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

    val accentColor = when {
        achievement.isUnlockedHardcore -> hardcoreColor
        achievement.isUnlocked -> softcoreColor
        else -> lockedColor
    }
    val rowShape = RoundedCornerShape(Dimens.radiusMd)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = rowShape
            )
            .then(if (onClick != null) Modifier.clickableNoFocus(onClick) else Modifier)
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
                            .shadow(Dimens.elevationMd, badgeShape, spotColor = hardcoreColor.copy(alpha = 0.5f))
                            .border(
                                width = Dimens.borderMedium,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        hardcoreColor,
                                        Color(0xFFFFF8DC),
                                        hardcoreColor
                                    )
                                ),
                                shape = badgeShape
                            )
                    } else if (achievement.isUnlocked) {
                        Modifier.border(Dimens.borderThin, softcoreColor.copy(alpha = 0.6f), badgeShape)
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
                text = pluralStringResource(
                    R.plurals.gamedetail_achievement_strip_points,
                    achievement.points,
                    achievement.points
                ),
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = Dimens.borderMedium)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                AchievementTypeBadge(type = achievement.type)
            }
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

/**
 * Small pill naming the RetroAchievements type (missable, progression, win condition). Draws
 * nothing for the untyped majority, so callers place it unconditionally.
 */
@Composable
fun AchievementTypeBadge(
    type: String?,
    modifier: Modifier = Modifier
) {
    val labelRes = achievementTypeLabelRes(type) ?: return
    val color = achievementTypeColor(type)
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.spacingXs, vertical = Dimens.borderMedium)
    )
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

/**
 * Unlocked achievements first, then locked, each half under a counted heading. [focusIndex]
 * indexes that combined order, which is what the caller's wrap arithmetic must size against;
 * -1 draws no focus. Rows are the shared [AchievementRow] so the in-game page, the companion
 * panel and the game page all read the same.
 */
@Composable
fun AchievementList(
    achievements: List<AchievementUi>,
    focusIndex: Int,
    @StringRes unlockedHeadingRes: Int,
    @StringRes lockedHeadingRes: Int,
    @StringRes emptyTextRes: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.spacingLg),
    onRowTapped: (Int) -> Unit = {}
) {
    val ordered = remember(achievements) { achievements.unlockedFirst() }
    val unlockedCount = remember(achievements) { achievements.count { it.isUnlocked } }
    val lockedCount = ordered.size - unlockedCount

    if (ordered.isEmpty()) {
        Box(
            modifier = modifier.padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(emptyTextRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val headerOffset = if (unlockedCount > 0) 1 else 0

    LaunchedEffect(focusIndex, ordered.size) {
        if (focusIndex in ordered.indices) {
            val lockedHeaderOffset = if (lockedCount > 0 && focusIndex >= unlockedCount) 1 else 0
            listState.animateScrollToItem(focusIndex + headerOffset + lockedHeaderOffset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (unlockedCount > 0) {
            item(key = "unlocked_heading") {
                AchievementSectionLabel(
                    text = stringResource(unlockedHeadingRes, unlockedCount),
                    color = ColorTokens.Domain.trophyAmber
                )
            }
        }
        itemsIndexed(
            items = ordered.take(unlockedCount),
            key = { _, achievement -> achievement.raId }
        ) { index, achievement ->
            AchievementRow(
                achievement = achievement,
                isFocused = index == focusIndex,
                onClick = { onRowTapped(index) }
            )
        }
        if (lockedCount > 0) {
            item(key = "locked_heading") {
                AchievementSectionLabel(
                    text = stringResource(lockedHeadingRes, lockedCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(
            items = ordered.drop(unlockedCount),
            key = { _, achievement -> achievement.raId }
        ) { index, achievement ->
            val orderedIndex = index + unlockedCount
            AchievementRow(
                achievement = achievement,
                isFocused = orderedIndex == focusIndex,
                onClick = { onRowTapped(orderedIndex) }
            )
        }
        item(key = "list_tail") { Spacer(modifier = Modifier.height(Dimens.spacingLg)) }
    }
}

fun List<AchievementUi>.unlockedFirst(): List<AchievementUi> =
    filter { it.isUnlocked } + filter { !it.isUnlocked }

@Composable
private fun AchievementSectionLabel(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(vertical = Dimens.spacingSm)
    )
}
