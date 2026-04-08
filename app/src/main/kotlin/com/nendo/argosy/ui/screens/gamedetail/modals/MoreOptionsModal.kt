package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

private sealed interface MoreMenuEntry {
    data class Option(
        val icon: ImageVector? = null,
        val label: String,
        val value: String? = null,
        val isDangerous: Boolean = false,
        val action: MoreOptionAction
    ) : MoreMenuEntry

    data object Divider : MoreMenuEntry
}

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean,
    hasVariants: Boolean = false,
    updateCount: Int = 0,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    val canTrackProgress = game.isRommGame || game.isAndroidApp
    val isEmulatedGame = !game.isSteamGame && !game.isAndroidApp
    val hasUpdates = updateCount > 0
    val usesTitleId = game.platformSlug in setOf("switch", "wiiu", "3ds", "vita", "psvita", "psp", "wii", "ps2")

    val entries = buildList<MoreMenuEntry> {
        if (game.canManageSaves) {
            add(MoreMenuEntry.Option(Icons.Default.Save, "Manage Cached Saves", action = MoreOptionAction.ManageSaves))
        }
        if (canTrackProgress) {
            add(MoreMenuEntry.Option(Icons.Default.Star, "Ratings & Status", action = MoreOptionAction.RatingsStatus))
        }
        if (game.isSteamGame) {
            add(MoreMenuEntry.Option(label = "Change Launcher", value = game.steamLauncherName ?: "Auto", action = MoreOptionAction.ChangeSteamLauncher))
        } else if (isEmulatedGame) {
            add(MoreMenuEntry.Option(label = "Change Emulator", value = game.emulatorName ?: "Default", action = MoreOptionAction.ChangeEmulator))
        }
        if (game.hasMultipleCores && isEmulatedGame) {
            add(MoreMenuEntry.Option(label = "Change Core", value = game.selectedCoreName ?: "Default", action = MoreOptionAction.ChangeCore))
        }
        if (usesTitleId && isEmulatedGame) {
            add(MoreMenuEntry.Option(Icons.Default.Tag, "Title ID", value = game.titleId ?: "Not detected", action = MoreOptionAction.RefreshTitleId))
        }
        if (game.isMultiDisc) {
            add(MoreMenuEntry.Option(Icons.Default.Album, "Select Disc", action = MoreOptionAction.SelectDisc))
        }
        if (hasVariants && isEmulatedGame) {
            add(MoreMenuEntry.Option(Icons.Default.SwapHoriz, "Select Variant", action = MoreOptionAction.SelectVariant))
        }
        if (hasUpdates) {
            add(MoreMenuEntry.Option(Icons.Default.SystemUpdate, "Updates/DLC", value = "$updateCount", action = MoreOptionAction.UpdatesDlc))
        }
        if (canTrackProgress) {
            add(MoreMenuEntry.Option(Icons.Default.Refresh, "Refresh Game Data", action = MoreOptionAction.RefreshData))
        }
        add(MoreMenuEntry.Option(Icons.Default.FolderSpecial, "Add to Collection", action = MoreOptionAction.AddToCollection))

        add(MoreMenuEntry.Divider)

        if (isDownloaded || game.isAndroidApp) {
            add(MoreMenuEntry.Option(
                icon = Icons.Default.DeleteOutline,
                label = if (game.isAndroidApp) "Uninstall" else "Delete Download",
                isDangerous = true,
                action = MoreOptionAction.Delete
            ))
        }
        add(MoreMenuEntry.Option(
            icon = if (game.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            label = if (game.isHidden) "Show" else "Hide",
            isDangerous = !game.isHidden,
            action = MoreOptionAction.ToggleHide
        ))
    }

    val listState = rememberLazyListState()

    val listIndex = run {
        var focus = 0
        entries.indexOfFirst { entry ->
            if (entry is MoreMenuEntry.Option) {
                if (focus == focusIndex) return@indexOfFirst true
                focus++
            }
            false
        }.coerceAtLeast(0)
    }

    FocusedScroll(listState = listState, focusedIndex = listIndex)

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
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            itemsIndexed(entries) { _, entry ->
                when (entry) {
                    is MoreMenuEntry.Divider -> {
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.spacingSm),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    is MoreMenuEntry.Option -> {
                        val optionFocusIndex = run {
                            var count = 0
                            for (e in entries) {
                                if (e === entry) break
                                if (e is MoreMenuEntry.Option) count++
                            }
                            count
                        }
                        OptionItem(
                            icon = entry.icon,
                            label = entry.label,
                            value = entry.value,
                            isFocused = focusIndex == optionFocusIndex,
                            isDangerous = entry.isDangerous,
                            onClick = { onAction(entry.action) }
                        )
                    }
                }
            }
        }
    }
}
