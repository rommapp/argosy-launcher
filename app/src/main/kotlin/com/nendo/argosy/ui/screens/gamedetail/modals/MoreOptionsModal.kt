package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean
) {
    val isRommGame = game.isRommGame
    var currentIndex = 0

    Modal(title = "MORE OPTIONS") {
        if (game.canManageSaves) {
            OptionItem(
                icon = Icons.Default.Save,
                label = "Manage Cached Saves",
                isFocused = focusIndex == currentIndex++
            )
        }
        if (isRommGame) {
            OptionItem(
                icon = Icons.Default.Star,
                label = "Rate Game",
                value = if (game.userRating > 0) "${game.userRating}/10" else "Not rated",
                isFocused = focusIndex == currentIndex++
            )
            OptionItem(
                icon = Icons.Default.Whatshot,
                label = "Set Difficulty",
                value = if (game.userDifficulty > 0) "${game.userDifficulty}/10" else "Not set",
                isFocused = focusIndex == currentIndex++
            )
        }
        OptionItem(
            label = "Change Emulator",
            value = game.emulatorName ?: "Default",
            isFocused = focusIndex == currentIndex++
        )
        if (game.isRetroArchEmulator) {
            OptionItem(
                label = "Change Core",
                value = game.selectedCoreName ?: "Default",
                isFocused = focusIndex == currentIndex++
            )
        }
        if (game.isMultiDisc) {
            OptionItem(
                icon = Icons.Default.Album,
                label = "Select Disc",
                isFocused = focusIndex == currentIndex++
            )
        }
        if (isRommGame) {
            OptionItem(
                icon = Icons.Default.Refresh,
                label = "Refresh Game Data",
                isFocused = focusIndex == currentIndex++
            )
        }

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (isDownloaded) {
            OptionItem(
                icon = Icons.Default.DeleteOutline,
                label = "Delete Download",
                isFocused = focusIndex == currentIndex++,
                isDangerous = true
            )
        }
        OptionItem(
            icon = Icons.Default.VisibilityOff,
            label = "Hide",
            isFocused = focusIndex == currentIndex,
            isDangerous = true
        )
    }
}
