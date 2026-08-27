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
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionsContext
import com.nendo.argosy.ui.screens.gamedetail.buildMoreOptions
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

private fun MoreOptionAction.toMenuEntry(
    game: GameDetailUi,
    context: Context
): MoreMenuEntry.Option = when (this) {
    MoreOptionAction.ManageSaves -> MoreMenuEntry.Option(
        Icons.Default.Save,
        context.getString(R.string.gamedetail_more_options_manage_saves),
        action = this
    )
    MoreOptionAction.RatingsStatus -> MoreMenuEntry.Option(
        Icons.Default.Star,
        context.getString(R.string.gamedetail_more_options_ratings_status),
        action = this
    )
    MoreOptionAction.ChangeSteamLauncher -> MoreMenuEntry.Option(
        label = context.getString(R.string.gamedetail_more_options_change_launcher),
        value = game.steamLauncherName ?: context.getString(R.string.gamedetail_steam_launcher_auto),
        action = this
    )
    MoreOptionAction.SpeedrunSplits -> MoreMenuEntry.Option(
        Icons.Default.Timer,
        context.getString(R.string.gamedetail_more_options_speedrun_splits),
        action = this
    )
    MoreOptionAction.RefreshTitleId -> MoreMenuEntry.Option(
        Icons.Default.Tag,
        context.getString(R.string.gamedetail_more_options_title_id),
        value = game.titleId
            ?: context.getString(R.string.gamedetail_more_options_title_id_missing),
        action = this
    )
    MoreOptionAction.SelectDisc -> MoreMenuEntry.Option(
        Icons.Default.Album,
        context.getString(R.string.gamedetail_more_options_select_disc),
        action = this
    )
    MoreOptionAction.SelectVariant -> MoreMenuEntry.Option(
        Icons.Default.SwapHoriz,
        context.getString(R.string.gamedetail_more_options_select_variant),
        action = this
    )
    MoreOptionAction.Files -> MoreMenuEntry.Option(
        Icons.Default.Checklist,
        context.getString(R.string.gamedetail_more_options_files),
        action = this
    )
    MoreOptionAction.RefreshData -> MoreMenuEntry.Option(
        Icons.Default.Refresh,
        context.getString(R.string.gamedetail_more_options_refresh_data),
        action = this
    )
    MoreOptionAction.AddToCollection -> MoreMenuEntry.Option(
        Icons.Default.FolderSpecial,
        context.getString(R.string.gamedetail_more_options_add_to_collection),
        action = this
    )
    MoreOptionAction.ChangeCover -> MoreMenuEntry.Option(
        Icons.Default.Image,
        context.getString(R.string.gamedetail_more_options_change_cover),
        action = this
    )
    MoreOptionAction.ResetCover -> MoreMenuEntry.Option(
        Icons.Default.Restore,
        context.getString(R.string.gamedetail_more_options_reset_cover),
        action = this
    )
    MoreOptionAction.Delete -> MoreMenuEntry.Option(
        icon = Icons.Default.DeleteOutline,
        label = when {
            game.isAndroidApp -> context.getString(R.string.gamedetail_more_options_uninstall)
            game.isExternallyManaged -> context.getString(
                R.string.gamedetail_more_options_unlink_from_launcher,
                game.managingLauncherDisplayName ?: context.getString(
                    R.string.gamedetail_more_options_unlink_launcher_fallback
                )
            )
            else -> context.getString(R.string.gamedetail_more_options_delete_download)
        },
        isDangerous = !game.isExternallyManaged,
        action = this
    )
    MoreOptionAction.RemoveFromLibrary -> MoreMenuEntry.Option(
        icon = Icons.Default.RemoveCircleOutline,
        label = context.getString(R.string.gamedetail_more_options_remove_from_library),
        isDangerous = true,
        action = this
    )
    MoreOptionAction.ToggleHide -> MoreMenuEntry.Option(
        icon = if (game.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
        label = if (game.isHidden) {
            context.getString(R.string.gamedetail_more_options_show)
        } else {
            context.getString(R.string.gamedetail_more_options_hide)
        },
        isDangerous = !game.isHidden,
        action = this
    )
    else -> MoreMenuEntry.Option(label = "", action = this)
}

@Composable
fun MoreOptionsModal(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean,
    hasVariants: Boolean = false,
    updateCount: Int = 0,
    hasManageableFiles: Boolean = false,
    canSearchCovers: Boolean = false,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    val actions = buildMoreOptions(
        MoreOptionsContext(
            isDownloaded = isDownloaded,
            isRommGame = game.isRommGame,
            isAndroidApp = game.isAndroidApp,
            isSteamGame = game.isSteamGame,
            canManageSaves = game.canManageSaves,
            canManageStates = game.canManageStates,
            isMultiDisc = game.isMultiDisc,
            hasVariants = hasVariants,
            hasUpdates = updateCount > 0,
            hasManageableFiles = hasManageableFiles,
            platformSlug = game.platformSlug,
            canSearchCovers = canSearchCovers,
            coverSetManually = game.coverSetManually
        )
    )

    val context = LocalContext.current
    val entries = buildList<MoreMenuEntry> {
        var dividerAdded = false
        actions.forEach { action ->
            val isTailAction = action is MoreOptionAction.Delete ||
                action is MoreOptionAction.RemoveFromLibrary ||
                action is MoreOptionAction.ToggleHide
            if (isTailAction && !dividerAdded) {
                add(MoreMenuEntry.Divider)
                dividerAdded = true
            }
            add(action.toMenuEntry(game, context))
        }
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
