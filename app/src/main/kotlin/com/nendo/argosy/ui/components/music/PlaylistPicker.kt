package com.nendo.argosy.ui.components.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

data class MusicPlaylistPickerState(
    val playlists: List<MusicPlaylist> = emptyList(),
    val focusIndex: Int = 0,
    val isLoading: Boolean = true
) {
    val focused: MusicPlaylist? get() = playlists.getOrNull(focusIndex)

    fun moved(delta: Int): MusicPlaylistPickerState =
        if (playlists.isEmpty()) this else copy(focusIndex = (focusIndex + delta).mod(playlists.size))
}

@Composable
fun ColumnScope.PlaylistPickerList(
    picker: MusicPlaylistPickerState,
    enabled: Boolean,
    onPick: (Int) -> Unit
) {
    when {
        picker.isLoading -> PickerNote(stringResource(R.string.music_library_playlists_loading))
        picker.playlists.isEmpty() -> PickerNote(stringResource(R.string.music_library_playlists_empty))
        else -> {
            val listState = rememberLazyListState()
            FocusedScroll(listState = listState, focusedIndex = picker.focusIndex)
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                items(count = picker.playlists.size, key = { picker.playlists[it].key }) { index ->
                    OptionItem(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = picker.playlists[index].name,
                        isFocused = index == picker.focusIndex,
                        isEnabled = enabled,
                        onClick = { onPick(index) }
                    )
                }
            }
        }
    }
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
