package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.MusicRepeatMode
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.musicplayer.transportButtons
import com.nendo.argosy.ui.theme.Dimens

private val MusicRepeatMode.descriptionRes: Int
    get() = when (this) {
        MusicRepeatMode.ALL -> R.string.ui_quick_settings_music_repeat_all
        MusicRepeatMode.ONE -> R.string.ui_quick_settings_music_repeat_one
        MusicRepeatMode.OFF -> R.string.ui_quick_settings_music_repeat_off
    }

@Composable
fun PlaybackTransportRow(
    isAudible: Boolean,
    shuffle: Boolean,
    repeat: MusicRepeatMode,
    playInBackground: Boolean,
    focusedButton: MusicTransportButton?,
    buttonSize: Dp,
    playButtonSize: Dp,
    horizontalArrangement: Arrangement.Horizontal,
    onButton: (MusicTransportButton) -> Unit,
    modifier: Modifier = Modifier,
    withPlaylist: Boolean = false,
    playIconSize: Dp = Dimens.iconMd
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        transportButtons(withPlaylist).forEach { button ->
            val focused = focusedButton == button
            val click = { onButton(button) }
            when (button) {
                MusicTransportButton.PLAY_PAUSE -> PlayPauseButton(
                    isPlaying = isAudible,
                    playDescription = stringResource(R.string.ui_quick_settings_music_play),
                    pauseDescription = stringResource(R.string.ui_quick_settings_music_pause),
                    size = playButtonSize,
                    isFocused = focused,
                    onClick = click,
                    iconSize = playIconSize
                )
                MusicTransportButton.ADD_TO_PLAYLIST -> TransportIconButton(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    description = stringResource(R.string.now_playing_add_to_playlist),
                    size = buttonSize,
                    isFocused = focused,
                    onClick = click
                )
                MusicTransportButton.SHUFFLE -> TransportIconButton(
                    icon = Icons.Default.Shuffle,
                    description = stringResource(R.string.ui_quick_settings_music_shuffle),
                    size = buttonSize,
                    isFocused = focused,
                    showDot = shuffle,
                    onClick = click
                )
                MusicTransportButton.PREVIOUS -> TransportIconButton(
                    icon = Icons.Default.SkipPrevious,
                    description = stringResource(R.string.ui_quick_settings_music_previous),
                    size = buttonSize,
                    isFocused = focused,
                    onClick = click
                )
                MusicTransportButton.NEXT -> TransportIconButton(
                    icon = Icons.Default.SkipNext,
                    description = stringResource(R.string.ui_quick_settings_music_next),
                    size = buttonSize,
                    isFocused = focused,
                    onClick = click
                )
                MusicTransportButton.REPEAT -> TransportIconButton(
                    icon = if (repeat == MusicRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    description = stringResource(repeat.descriptionRes),
                    size = buttonSize,
                    isFocused = focused,
                    showDot = repeat != MusicRepeatMode.OFF,
                    onClick = click
                )
                MusicTransportButton.BACKGROUND -> TransportIconButton(
                    icon = Icons.Default.Bedtime,
                    description = stringResource(R.string.ui_quick_settings_music_play_in_background),
                    size = buttonSize,
                    isFocused = focused,
                    showDot = playInBackground,
                    onClick = click
                )
            }
        }
    }
}
