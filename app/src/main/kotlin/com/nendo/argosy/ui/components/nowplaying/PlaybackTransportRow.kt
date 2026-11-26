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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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

/**
 * Spreads the buttons edge to edge with gaps between [minGap] and [maxGap], and centres the row
 * once every gap reaches [maxGap]. Buttons never shrink; below [minGap] the row overflows its end.
 */
@Composable
fun rememberTransportArrangement(
    minGap: Dp = Dimens.spacingSm,
    maxGap: Dp = Dimens.spacingLg
): Arrangement.Horizontal = remember(minGap, maxGap) { BoundedSpaceBetween(minGap, maxGap) }

private class BoundedSpaceBetween(private val minGap: Dp, private val maxGap: Dp) : Arrangement.Horizontal {
    override val spacing: Dp = minGap

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray
    ) {
        val content = sizes.sum()
        val gap = transportGapPx(totalSize, content, sizes.size, minGap.roundToPx(), maxGap.roundToPx())
        val used = content + gap * (sizes.size - 1).coerceAtLeast(0)
        var x = ((totalSize - used) / 2).coerceAtLeast(0)
        sizes.forEachIndexed { index, size ->
            outPositions[index] = if (layoutDirection == LayoutDirection.Rtl) totalSize - x - size else x
            x += size + gap
        }
    }
}

internal fun transportGapPx(totalPx: Int, contentPx: Int, count: Int, minGapPx: Int, maxGapPx: Int): Int {
    if (count < 2) return 0
    return ((totalPx - contentPx) / (count - 1)).coerceIn(minGapPx, maxGapPx)
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
    onButton: (MusicTransportButton) -> Unit,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = rememberTransportArrangement(),
    withPlaylist: Boolean = false,
    buttons: List<MusicTransportButton> = transportButtons(withPlaylist),
    playIconSize: Dp = Dimens.iconMd
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        buttons.forEach { button ->
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
