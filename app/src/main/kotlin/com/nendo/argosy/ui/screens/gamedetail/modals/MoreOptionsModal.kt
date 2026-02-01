package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean,
    updateCount: Int = 0,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    val canTrackProgress = game.isRommGame || game.isAndroidApp
    val isEmulatedGame = !game.isSteamGame && !game.isAndroidApp
    val hasUpdates = updateCount > 0
    var currentIndex = 0

    Modal(
        title = game.title,
        titleContent = {
            GameTitle(
                title = game.title,
                titleStyle = MaterialTheme.typography.titleMedium,
                titleId = game.titleId,
                titleIdColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
            )
        },
        onDismiss = onDismiss
    ) {
        if (game.canManageSaves) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Save,
                label = "Manage Cached Saves",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.ManageSaves) }
            )
        }
        if (canTrackProgress) {
            val ratingsIdx = currentIndex++
            OptionItem(
                icon = Icons.Default.Star,
                label = "Ratings & Status",
                isFocused = focusIndex == ratingsIdx,
                onClick = { onAction(MoreOptionAction.RatingsStatus) }
            )
        }
        if (game.isSteamGame) {
            val idx = currentIndex++
            OptionItem(
                label = "Change Launcher",
                value = game.steamLauncherName ?: "Auto",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.ChangeSteamLauncher) }
            )
        } else if (isEmulatedGame) {
            val idx = currentIndex++
            OptionItem(
                label = "Change Emulator",
                value = game.emulatorName ?: "Default",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.ChangeEmulator) }
            )
        }
        if (game.isRetroArchEmulator && isEmulatedGame) {
            val idx = currentIndex++
            OptionItem(
                label = "Change Core",
                value = game.selectedCoreName ?: "Default",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.ChangeCore) }
            )
        }
        if (game.isMultiDisc) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Album,
                label = "Select Disc",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.SelectDisc) }
            )
        }
        if (hasUpdates) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.SystemUpdate,
                label = "Updates/DLC",
                value = "$updateCount",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.UpdatesDlc) }
            )
        }
        if (canTrackProgress) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.Refresh,
                label = "Refresh Game Data",
                isFocused = focusIndex == idx,
                onClick = { onAction(MoreOptionAction.RefreshData) }
            )
        }

        val addToCollectionIdx = currentIndex++
        OptionItem(
            icon = Icons.Default.FolderSpecial,
            label = "Add to Collection",
            isFocused = focusIndex == addToCollectionIdx,
            onClick = { onAction(MoreOptionAction.AddToCollection) }
        )

        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingSm),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (isDownloaded || game.isAndroidApp) {
            val idx = currentIndex++
            OptionItem(
                icon = Icons.Default.DeleteOutline,
                label = if (game.isAndroidApp) "Uninstall" else "Delete Download",
                isFocused = focusIndex == idx,
                isDangerous = true,
                onClick = { onAction(MoreOptionAction.Delete) }
            )
        }
        val hideIdx = currentIndex
        OptionItem(
            icon = if (game.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            label = if (game.isHidden) "Show" else "Hide",
            isFocused = focusIndex == hideIdx,
            isDangerous = !game.isHidden,
            onClick = { onAction(MoreOptionAction.ToggleHide) }
        )
    }
}
