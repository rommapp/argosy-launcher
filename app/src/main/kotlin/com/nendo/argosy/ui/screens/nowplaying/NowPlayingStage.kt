package com.nendo.argosy.ui.screens.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.MusicRepeatMode
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.nowplaying.DiscArt
import com.nendo.argosy.ui.components.nowplaying.NowPlayingProgress
import com.nendo.argosy.ui.components.nowplaying.NowPlayingVisualizer
import com.nendo.argosy.ui.components.nowplaying.displayArt
import com.nendo.argosy.ui.components.nowplaying.displayArtist
import com.nendo.argosy.ui.components.nowplaying.displayTitle
import com.nendo.argosy.ui.components.nowplaying.isAudible
import com.nendo.argosy.ui.screens.music.BlurredHero
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The Now Playing stage on the presentation screen: the Simple layout over the blurred art and
 * the visualizer, with its own hint line. Display only; nothing here takes input.
 */
@Composable
fun NowPlayingStage(state: NowPlayingStageState) {
    val playback = state.playback
    val playing = playback.isAudible
    Box(modifier = Modifier.fillMaxSize()) {
        playback.displayArt?.let { BlurredHero(it) }
        NowPlayingVisualizer(
            analysis = state.analysis,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = playing
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = Dimens.spacingXl,
                    end = Dimens.spacingXl,
                    top = Dimens.headerHeight,
                    bottom = Dimens.spacingLg
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DiscArt(
                    art = playback.displayArt,
                    style = state.style,
                    spinning = state.spin,
                    playing = playing,
                    modifier = Modifier.size(ComponentDefaults.NowPlaying.stageDiscDp.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                StageStateIcons(state)
            }
            Spacer(modifier = Modifier.weight(1f))
            StagePositionLine(playback)
            Text(
                text = playback.displayTitle ?: stringResource(R.string.now_playing_unknown_title),
                style = MaterialTheme.typography.displayMedium,
                color = LocalArgosyTheme.current.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            playback.displayArtist?.let { artist ->
                Text(
                    text = artist,
                    style = MaterialTheme.typography.headlineSmall,
                    color = LocalArgosyTheme.current.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            NowPlayingProgress(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playing = playing,
                analysis = state.analysis
            )
            if (state.hints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                StageHintLine(state.hints)
            }
        }
    }
}

@Composable
private fun StageStateIcons(state: NowPlayingStageState) {
    val playback = state.playback
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StateIcon(
            icon = if (playback.isAudible) Icons.Default.PlayArrow else Icons.Default.Pause,
            active = playback.isAudible
        )
        StateIcon(icon = Icons.Default.Shuffle, active = playback.shuffle)
        StateIcon(
            icon = if (playback.repeat == MusicRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            active = playback.repeat != MusicRepeatMode.OFF
        )
        StateIcon(icon = Icons.Default.Bedtime, active = state.playInBackground)
    }
}

@Composable
private fun StateIcon(icon: ImageVector, active: Boolean) {
    val theme = LocalArgosyTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) theme.textPrimary else theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Box(
            modifier = Modifier
                .padding(top = Dimens.spacingXs)
                .size(Dimens.spacingXs)
                .clip(CircleShape)
                .background(if (active) theme.focusAccent else Color.Transparent)
        )
    }
}

@Composable
private fun StagePositionLine(playback: AmbientPlaybackState) {
    val tracks = playback.tracks
    if (playback.overrideTitle != null || tracks.isEmpty()) return
    val position = stringResource(R.string.now_playing_stage_position, playback.index + 1, tracks.size)
    val upNext = tracks.getOrNull(playback.index + 1)?.title
        ?.let { stringResource(R.string.now_playing_stage_up_next, it) }
    Text(
        text = listOfNotNull(position, upNext).joinToString(stringResource(R.string.now_playing_stage_separator)),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = Dimens.spacingXs)
    )
}

@Composable
private fun StageHintLine(hints: List<NowPlayingStageHint>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        hints.forEach { hint ->
            val (button, label) = when (hint) {
                NowPlayingStageHint.SKIP_SECONDS -> InputButton.LB_RB to R.string.now_playing_stage_hint_skip
                NowPlayingStageHint.TRACK -> InputButton.LT_RT to R.string.now_playing_stage_hint_track
                NowPlayingStageHint.QUEUE -> InputButton.X to R.string.now_playing_stage_hint_queue
                NowPlayingStageHint.STYLE -> InputButton.SELECT to R.string.now_playing_stage_hint_style
                NowPlayingStageHint.SWAP_SCREENS -> InputButton.SELECT to R.string.now_playing_stage_hint_swap
            }
            FooterHint(button = button, action = stringResource(label))
        }
    }
}
