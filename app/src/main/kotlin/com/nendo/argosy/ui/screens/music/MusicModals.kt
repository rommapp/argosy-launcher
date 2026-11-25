package com.nendo.argosy.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.music.PlaylistPickerList
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal class MusicModalInput(
    private val move: (Int) -> Unit,
    private val confirm: () -> Unit,
    private val back: () -> Unit
) : InputHandler {
    override fun onUp(): InputResult {
        move(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        move(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        confirm()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        back()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onSelect(): InputResult {
        back()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }

    override fun onLeft(): InputResult = InputResult.HANDLED
    override fun onRight(): InputResult = InputResult.HANDLED
    override fun onMenu(): InputResult = InputResult.HANDLED
    override fun onSecondaryAction(): InputResult = InputResult.HANDLED
    override fun onContextMenu(): InputResult = InputResult.HANDLED
    override fun onPrevSection(): InputResult = InputResult.HANDLED
    override fun onNextSection(): InputResult = InputResult.HANDLED
    override fun onPrevTrigger(): InputResult = InputResult.HANDLED
    override fun onNextTrigger(): InputResult = InputResult.HANDLED
    override fun onLeftStickClick(): InputResult = InputResult.HANDLED
    override fun onRightStickClick(): InputResult = InputResult.HANDLED
    override fun onLongConfirm(): InputResult = InputResult.HANDLED
}

@Composable
fun MusicOptionsModalHost(
    options: MusicOptionsState?,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentMove by rememberUpdatedState(onMove)
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentBack by rememberUpdatedState(onBack)
    val handler = remember {
        MusicModalInput(move = { currentMove(it) }, confirm = { currentConfirm() }, back = { currentBack() })
    }
    ModalInputEffect(active = options != null, handler = handler)

    val content = options ?: return
    val theme = LocalArgosyTheme.current
    val picker = content.picker
    ModalScaffold(visible = true, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = if (picker != null) stringResource(R.string.music_library_playlists_title) else content.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (picker != null) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            if (picker == null) {
                OptionList(
                    count = content.actions.size,
                    focusedIndex = content.focusIndex,
                    key = { content.actions[it].name },
                    label = { optionLabel(content.actions[it]) },
                    icon = { optionIcon(content.actions[it]) },
                    enabled = !content.isBusy,
                    onClick = { index ->
                        onFocus(index)
                        onConfirm()
                    }
                )
            } else {
                PlaylistPickerList(picker = picker, enabled = !content.isBusy) { index ->
                    onFocus(index)
                    onConfirm()
                }
            }
        }
    }
}

@Composable
fun MusicFilterModalHost(
    filter: MusicFilterState?,
    selected: MusicSourceFilter,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentMove by rememberUpdatedState(onMove)
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        MusicModalInput(move = { currentMove(it) }, confirm = { currentConfirm() }, back = { currentDismiss() })
    }
    ModalInputEffect(active = filter != null, handler = handler)

    val content = filter ?: return
    val theme = LocalArgosyTheme.current
    ModalScaffold(visible = true, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = stringResource(R.string.music_library_filter_title),
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Spacer(Modifier.height(Dimens.spacingLg))
            val listState = rememberLazyListState()
            FocusedScroll(listState = listState, focusedIndex = content.focusIndex)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                itemsIndexed(content.options, key = { _, option -> option.token }) { index, option ->
                    OptionItem(
                        label = stringResource(option.labelRes),
                        isFocused = index == content.focusIndex,
                        isSelected = option == selected,
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

@Composable
private fun ColumnScope.OptionList(
    count: Int,
    focusedIndex: Int,
    key: (Int) -> Any,
    label: @Composable (Int) -> String,
    icon: (Int) -> ImageVector,
    enabled: Boolean,
    onClick: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusedIndex)
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        items(count = count, key = key) { index ->
            OptionItem(
                icon = icon(index),
                label = label(index),
                isFocused = index == focusedIndex,
                isEnabled = enabled,
                onClick = { onClick(index) }
            )
        }
    }
}


@Composable
private fun optionLabel(action: MusicOptionAction): String = stringResource(
    when (action) {
        MusicOptionAction.PLAY -> R.string.music_library_options_play
        MusicOptionAction.SHUFFLE -> R.string.music_library_options_shuffle
        MusicOptionAction.DOWNLOAD -> R.string.music_library_options_download
        MusicOptionAction.ADD_TO_PLAYLIST -> R.string.music_library_options_add_to_playlist
        MusicOptionAction.NOW_PLAYING -> R.string.music_library_options_now_playing
        MusicOptionAction.LISTENING -> R.string.music_stats_entry_option
    }
)

private fun optionIcon(action: MusicOptionAction): ImageVector = when (action) {
    MusicOptionAction.PLAY -> Icons.Default.PlayArrow
    MusicOptionAction.SHUFFLE -> Icons.Default.Shuffle
    MusicOptionAction.DOWNLOAD -> Icons.Default.Download
    MusicOptionAction.ADD_TO_PLAYLIST -> Icons.AutoMirrored.Filled.PlaylistAdd
    MusicOptionAction.NOW_PLAYING -> Icons.Default.GraphicEq
    MusicOptionAction.LISTENING -> Icons.Default.Insights
}
