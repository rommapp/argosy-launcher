package com.nendo.argosy.ui.screens.media.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.media.MediaMenuAction
import com.nendo.argosy.ui.screens.media.MediaMenuState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The detail screen's side menu: everything the screen can do to a title that is not one of the few
 * actions worth a permanent button.
 *
 * It renders whatever the menu state carries and reports moves back out, so the rows the modal draws
 * and the rows the view model acts on are the one list. The focused index lives in the view model
 * like every other selection in the app.
 */
@Composable
fun MediaDetailMenuModalHost(
    menu: MediaMenuState?,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                currentOnMove(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                currentOnMove(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onContextMenu(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = menu != null, handler = inputHandler)

    val content = menu ?: return
    val theme = LocalArgosyTheme.current

    ModalScaffold(visible = true, onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content.subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = theme.textMute)
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            val listState = rememberLazyListState()
            FocusedScroll(listState = listState, focusedIndex = content.focusedIndex)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(
                    items = content.actions,
                    key = { _, action -> action.key }
                ) { index, action ->
                    OptionItem(
                        icon = action.iconFor(content),
                        label = action.labelFor(content),
                        isFocused = index == content.focusedIndex,
                        isDangerous = action == MediaMenuAction.RemoveDownloads,
                        isEnabled = !content.isBusy,
                        onClick = {
                            onFocus(index)
                            onConfirm()
                        }
                    )
                }
            }
        }
    }
}

private val MediaMenuAction.key: String
    get() = when (this) {
        MediaMenuAction.ToggleWatched -> "watched"
        MediaMenuAction.ToggleFavorite -> "favorite"
        MediaMenuAction.Download -> "download"
        MediaMenuAction.RemoveDownloads -> "remove_downloads"
        MediaMenuAction.RefreshSeries -> "refresh"
        MediaMenuAction.GoToLibrary -> "library"
    }

private fun MediaMenuAction.labelFor(menu: MediaMenuState): String = when (this) {
    MediaMenuAction.ToggleWatched -> if (menu.targetPlayed) "Mark Unwatched" else "Mark Watched"
    MediaMenuAction.ToggleFavorite -> if (menu.targetIsFavorite) "Remove Favorite" else "Favorite"
    MediaMenuAction.Download -> "Download"
    MediaMenuAction.RemoveDownloads -> "Remove Downloads"
    MediaMenuAction.RefreshSeries -> if (menu.isBusy) "Refreshing" else "Refresh From Server"
    MediaMenuAction.GoToLibrary -> "Go to Library"
}

private fun MediaMenuAction.iconFor(menu: MediaMenuState): ImageVector = when (this) {
    MediaMenuAction.ToggleWatched ->
        if (menu.targetPlayed) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked
    MediaMenuAction.ToggleFavorite ->
        if (menu.targetIsFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
    MediaMenuAction.Download -> Icons.Default.Download
    MediaMenuAction.RemoveDownloads -> Icons.Default.DeleteOutline
    MediaMenuAction.RefreshSeries -> Icons.Default.Refresh
    MediaMenuAction.GoToLibrary -> Icons.Default.VideoLibrary
}
