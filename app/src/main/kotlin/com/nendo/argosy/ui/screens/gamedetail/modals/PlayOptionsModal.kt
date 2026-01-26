package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.libretro.LaunchMode
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

private val goldColor = Color(0xFFFFD700)

sealed class PlayOptionAction {
    data object Resume : PlayOptionAction()
    data object NewCasual : PlayOptionAction()
    data object NewHardcore : PlayOptionAction()
    data object ResumeHardcore : PlayOptionAction()
}

@Composable
fun PlayOptionsModal(
    focusIndex: Int,
    hasSaves: Boolean,
    hasHardcoreSave: Boolean,
    isRALoggedIn: Boolean,
    isOnline: Boolean,
    onAction: (PlayOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex = 0

    Modal(title = "PLAY OPTIONS", onDismiss = onDismiss) {
        if (hasSaves) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.PlayArrow,
                label = "Resume",
                value = "Continue from last save",
                isFocused = focusIndex == idx,
                onClick = { onAction(PlayOptionAction.Resume) }
            )
        }

        if (hasSaves || hasHardcoreSave) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spacingSm),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        val casualIdx = currentIndex++
        OptionItem(
            icon = Icons.Default.RestartAlt,
            label = "New Game (Casual)",
            value = "Start fresh, keep existing saves",
            isFocused = focusIndex == casualIdx,
            onClick = { onAction(PlayOptionAction.NewCasual) }
        )

        if (isRALoggedIn) {
            val hardcoreIdx = currentIndex++
            OptionItem(
                icon = Icons.Default.EmojiEvents,
                label = "New Game (Hardcore)",
                value = if (isOnline) "Start fresh hardcore run" else "Requires internet",
                isFocused = focusIndex == hardcoreIdx,
                isEnabled = isOnline,
                iconTint = if (isOnline) goldColor else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { onAction(PlayOptionAction.NewHardcore) }
            )
        }

        if (hasHardcoreSave && isRALoggedIn) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spacingSm),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            val resumeHardcoreIdx = currentIndex
            OptionItem(
                icon = Icons.Default.EmojiEvents,
                label = "Resume Hardcore",
                value = "Continue hardcore save",
                isFocused = focusIndex == resumeHardcoreIdx,
                iconTint = goldColor,
                onClick = { onAction(PlayOptionAction.ResumeHardcore) }
            )
        }
    }
}
