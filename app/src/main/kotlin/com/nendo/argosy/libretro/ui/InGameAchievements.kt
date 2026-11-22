package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.common.achievements.AchievementCommentsController
import com.nendo.argosy.ui.common.achievements.AchievementCommentsPane
import com.nendo.argosy.ui.common.achievements.CommentsKeyEntry
import com.nendo.argosy.ui.common.achievements.rememberAchievementCommentsInput
import com.nendo.argosy.ui.screens.gamedetail.components.AchievementList
import com.nendo.argosy.ui.screens.gamedetail.components.unlockedFirst
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * List of the running game's RetroAchievements, opened inside the in-game menu panel. Up
 * and down walk the unlocked-first order the shared list draws, wrapping at both ends; A or a tap
 * opens the row's community comments; back returns to the menu.
 */
@Composable
fun InGameAchievements(
    gameName: String,
    achievements: List<AchievementUi>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    comments: AchievementCommentsController,
    onDismiss: () -> Unit
): InputHandler {
    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentCount = rememberUpdatedState(achievements.size)
    val currentAchievements = rememberUpdatedState(achievements)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val openComments: (Int) -> Unit = { index ->
        currentAchievements.value.unlockedFirst().getOrNull(index)?.let { achievement ->
            currentOnFocusChange.value(index)
            comments.open(achievement)
        }
    }
    val currentOpenComments = rememberUpdatedState(openComments)

    val commentsState by comments.state.collectAsState()
    val commentsInput = rememberAchievementCommentsInput(comments, CommentsKeyEntry.INLINE, onOpenKeySettings = {})
    commentsState?.let { state ->
        AchievementCommentsPane(
            state = state,
            controller = comments,
            keyEntry = CommentsKeyEntry.INLINE,
            onOpenKeySettings = {},
            modifier = Modifier.focusProperties { canFocus = false },
            contentPadding = PaddingValues(horizontal = Dimens.spacingMd)
        )
        return commentsInput
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                val count = currentCount.value
                if (count > 0) currentOnFocusChange.value((currentFocusedIndex.value - 1).mod(count))
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                val count = currentCount.value
                if (count > 0) currentOnFocusChange.value((currentFocusedIndex.value + 1).mod(count))
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOpenComments.value(currentFocusedIndex.value)
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    val unlockedCount = remember(achievements) { achievements.count { it.isUnlocked } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = ColorTokens.Domain.trophyAmber,
                    modifier = Modifier.size(Dimens.iconMd)
                )
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ingame_achievements_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = gameName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = stringResource(
                        R.string.ingame_achievements_progress,
                        unlockedCount,
                        achievements.size
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AchievementList(
                achievements = achievements,
                focusIndex = focusedIndex,
                unlockedHeadingRes = R.string.ingame_achievements_unlocked_heading,
                lockedHeadingRes = R.string.ingame_achievements_locked_heading,
                emptyTextRes = R.string.ingame_achievements_empty,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.spacingMd),
                onRowTapped = openComments
            )
        }
    }

    return inputHandler
}
