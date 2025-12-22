package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean,
    onOptionSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isRommGame = game.isRommGame
    var currentIndex = 0

    Modal(title = "MORE OPTIONS", onDismiss = onDismiss) {
        if (game.canManageSaves) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Save,
                label = "Manage Cached Saves",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        }
        if (isRommGame) {
            val rateIdx = currentIndex++
            OptionItem(
                icon = Icons.Default.Star,
                label = "Rate Game",
                value = if (game.userRating > 0) "${game.userRating}/10" else "Not rated",
                isFocused = focusIndex == rateIdx,
                onClick = { onOptionSelect(rateIdx) }
            )
            val diffIdx = currentIndex++
            OptionItem(
                icon = Icons.Default.Whatshot,
                label = "Set Difficulty",
                value = if (game.userDifficulty > 0) "${game.userDifficulty}/10" else "Not set",
                isFocused = focusIndex == diffIdx,
                onClick = { onOptionSelect(diffIdx) }
            )
            val statusIdx = currentIndex++
            OptionItem(
                icon = Icons.Default.CheckCircle,
                label = "Set Status",
                value = CompletionStatus.fromApiValue(game.status)?.label ?: "Not set",
                isFocused = focusIndex == statusIdx,
                onClick = { onOptionSelect(statusIdx) }
            )
        }
        if (game.isSteamGame) {
            val idx = currentIndex++
            OptionItem(
                label = "Change Launcher",
                value = game.steamLauncherName ?: "Auto",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        } else {
            val idx = currentIndex++
            OptionItem(
                label = "Change Emulator",
                value = game.emulatorName ?: "Default",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        }
        if (game.isRetroArchEmulator && !game.isSteamGame) {
            val idx = currentIndex++
            OptionItem(
                label = "Change Core",
                value = game.selectedCoreName ?: "Default",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        }
        if (game.isMultiDisc) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Album,
                label = "Select Disc",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        }
        if (isRommGame) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Refresh,
                label = "Refresh Game Data",
                isFocused = focusIndex == idx,
                onClick = { onOptionSelect(idx) }
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (isDownloaded) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.DeleteOutline,
                label = "Delete Download",
                isFocused = focusIndex == idx,
                isDangerous = true,
                onClick = { onOptionSelect(idx) }
            )
        }
        val hideIdx = currentIndex
        OptionItem(
            icon = Icons.Default.VisibilityOff,
            label = "Hide",
            isFocused = focusIndex == hideIdx,
            isDangerous = true,
            onClick = { onOptionSelect(hideIdx) }
        )
    }
}
