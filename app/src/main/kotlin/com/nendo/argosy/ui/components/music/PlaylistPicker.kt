package com.nendo.argosy.ui.components.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The add-to-playlist picker. Row 0 is "New playlist", then one row per owned playlist in [groups].
 * [canAdd] is false when the item has no library source to add from, and the picker lists nothing.
 * [isNaming] and [conflict] are the dialogs opened over the list; while either is up the list
 * gives up gamepad input ([ownsInput]).
 */
data class MusicPlaylistPickerState(
    val groups: List<MusicPlaylistGroup> = emptyList(),
    val focusIndex: Int = NEW_PLAYLIST_ROW,
    val isLoading: Boolean = true,
    val canAdd: Boolean = true,
    val isBusy: Boolean = false,
    val isNaming: Boolean = false,
    val conflict: MusicPlaylistGroup? = null
) {
    val rowCount: Int get() = if (canAdd) groups.size + 1 else 0
    val isNewPlaylistFocused: Boolean get() = canAdd && focusIndex == NEW_PLAYLIST_ROW
    val focused: MusicPlaylistGroup? get() = if (canAdd) groups.getOrNull(focusIndex - 1) else null
    val ownsInput: Boolean get() = !isNaming && conflict == null

    fun moved(delta: Int): MusicPlaylistPickerState =
        if (rowCount == 0) this else copy(focusIndex = (focusIndex + delta).mod(rowCount))

    fun focusedAt(index: Int): MusicPlaylistPickerState =
        if (rowCount == 0) this else copy(focusIndex = index.coerceIn(0, rowCount - 1))

    companion object {
        const val NEW_PLAYLIST_ROW = 0
    }
}

@Composable
fun ColumnScope.PlaylistPickerList(
    picker: MusicPlaylistPickerState,
    enabled: Boolean,
    onPick: (Int) -> Unit
) {
    if (!picker.canAdd) {
        PickerNote(stringResource(R.string.music_library_playlists_empty))
        return
    }
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = picker.focusIndex)
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        item(key = NEW_PLAYLIST_KEY) {
            OptionItem(
                icon = Icons.Default.Add,
                label = stringResource(R.string.music_library_playlists_new),
                isFocused = picker.isNewPlaylistFocused,
                isEnabled = enabled,
                onClick = { onPick(MusicPlaylistPickerState.NEW_PLAYLIST_ROW) }
            )
        }
        items(count = picker.groups.size, key = { picker.groups[it].key }) { index ->
            OptionItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = picker.groups[index].name,
                isFocused = index + 1 == picker.focusIndex,
                isEnabled = enabled,
                onClick = { onPick(index + 1) }
            )
        }
        if (picker.isLoading) {
            item(key = LOADING_KEY) { PickerNote(stringResource(R.string.music_library_playlists_loading)) }
        }
    }
}

@Composable
fun MusicPlaylistPickerDialogs(
    picker: MusicPlaylistPickerState?,
    onSubmitName: (String) -> Unit,
    onCancelName: () -> Unit,
    onConfirmConflict: () -> Unit,
    onDismissConflict: () -> Unit
) {
    if (picker?.isNaming == true) {
        PlaylistNameModal(
            title = stringResource(R.string.music_library_playlists_new_title),
            confirmLabel = stringResource(R.string.music_library_playlists_new_confirm),
            initialName = "",
            busy = picker.isBusy,
            onSubmit = onSubmitName,
            onDismiss = onCancelName
        )
    }
    val conflict = picker?.conflict
    ArgosyConfirmModalHost(
        visible = conflict != null,
        title = stringResource(R.string.music_library_playlists_exists_title),
        message = stringResource(R.string.music_library_playlists_exists_message, conflict?.name.orEmpty()),
        confirmLabel = stringResource(R.string.music_library_playlists_exists_confirm),
        onConfirm = onConfirmConflict,
        onDismiss = onDismissConflict,
        working = if (picker?.isBusy == true) stringResource(R.string.music_library_playlists_working) else null
    )
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(vertical = Dimens.spacingMd)
    )
}

private const val NEW_PLAYLIST_KEY = "new-playlist"
private const val LOADING_KEY = "loading"
