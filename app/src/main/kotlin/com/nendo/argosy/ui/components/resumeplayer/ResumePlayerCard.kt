package com.nendo.argosy.ui.components.resumeplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.ui.components.frostedSurface
import com.nendo.argosy.ui.components.nowplaying.DiscArt
import com.nendo.argosy.ui.components.nowplaying.PinchedWaveProgress
import com.nendo.argosy.ui.components.nowplaying.PlaybackTransportRow
import com.nendo.argosy.ui.components.nowplaying.TransportIconButton
import com.nendo.argosy.ui.components.nowplaying.displayArt
import com.nendo.argosy.ui.components.nowplaying.displayArtist
import com.nendo.argosy.ui.components.nowplaying.displayTitle
import com.nendo.argosy.ui.components.nowplaying.isAudible
import com.nendo.argosy.ui.components.nowplaying.progressFraction
import com.nendo.argosy.ui.dualscreen.PresentOnCompanionLive
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.screens.nowplaying.NowPlayingStageHint
import com.nendo.argosy.ui.screens.nowplaying.NowPlayingStageState
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

private val Defaults = ComponentDefaults.ResumePlayer
private const val SLOT_OWNER_NAME = "resume_player"

/**
 * The Resume page's player card. [onPlaced] reports the card's bounds in root coordinates, the
 * anchor [ResumePlayerPanelLayer] drops the running order from.
 */
@Composable
fun ResumePlayerCard(
    controller: ResumePlayerController,
    state: ResumePlayerUiState,
    presentsOnCompanion: Boolean,
    onPlaced: (boundsInRoot: Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val playback = state.playback
    val playing = playback.isAudible
    if (presentsOnCompanion && state.holdsFocus) PresentNowPlaying(controller, state)
    Row(
        modifier = modifier
            .height(Defaults.cardHeightDp.dp)
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
            .frostedSurface(RoundedCornerShape(Dimens.radiusLg))
            .padding(Defaults.cardPaddingDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiscArt(
            art = playback.displayArt,
            style = NowPlayingStyle.COVER,
            spinning = false,
            playing = playing,
            modifier = Modifier
                .size(ComponentDefaults.MusicPlayer.rowCoverDp.dp)
                .clickableNoFocus(enabled = state.canOpenNowPlaying, onClick = controller::openNowPlaying)
        )
        Spacer(modifier = Modifier.width(Defaults.cardGapDp.dp))
        CardText(
            state = state,
            playing = playing,
            onClick = controller::togglePanelFromTouch,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        CardTransport(controller = controller, state = state, playing = playing)
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        TransportIconButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            description = stringResource(R.string.running_player_show_queue),
            size = ComponentDefaults.MusicPlayer.transportButtonDp.dp,
            isFocused = state.cardFocused && state.button == ResumePlayerButton.QUEUE,
            showDot = state.panelOpen,
            onClick = controller::togglePanelFromTouch
        )
    }
}

@Composable
private fun CardText(
    state: ResumePlayerUiState,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val title = state.playback.displayTitle.orEmpty()
    val artist = state.playback.displayArtist
    BoxWithConstraints(modifier = modifier.clickableNoFocus(onClick = onClick)) {
        val showsWave = maxWidth >= Defaults.waveMinTextDp.dp
        val showsArtist = maxWidth >= Defaults.artistMinTextDp.dp
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Text(
                text = if (showsArtist && artist != null) {
                    stringResource(R.string.running_player_title_artist, title, artist)
                } else {
                    title
                },
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showsWave) {
                PinchedWaveProgress(
                    progress = progressFraction(state.positionMs, state.durationMs),
                    playing = playing,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                    analysis = state.analysis,
                    durationMs = state.durationMs
                )
            }
        }
    }
}

@Composable
private fun CardTransport(controller: ResumePlayerController, state: ResumePlayerUiState, playing: Boolean) {
    val buttonSize = ComponentDefaults.MusicPlayer.transportButtonDp.dp
    val playSize = ComponentDefaults.MusicPlayer.playButtonDp.dp
    val gap = Dimens.spacingSm
    PlaybackTransportRow(
        isAudible = playing,
        shuffle = state.playback.shuffle,
        repeat = state.playback.repeat,
        playInBackground = false,
        focusedButton = state.button.transport.takeIf { state.cardFocused },
        buttonSize = buttonSize,
        playButtonSize = playSize,
        horizontalArrangement = Arrangement.spacedBy(gap),
        onButton = { pressed ->
            ResumePlayerButton.entries.firstOrNull { it.transport == pressed }?.let(controller::tapButton)
        },
        modifier = Modifier.width(buttonSize * 2 + playSize + gap * 2),
        buttons = ResumePlayerTransportButtons
    )
}

@Composable
private fun PresentNowPlaying(controller: ResumePlayerController, state: ResumePlayerUiState) {
    val owner = remember(controller) { SlotOwner.of(SLOT_OWNER_NAME, controller) }
    val slot = PresentationSlot.NowPlayingStage(
        NowPlayingStageState(
            playback = state.playback,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            analysis = state.analysis,
            style = state.stage.nowPlaying.style,
            spin = state.stage.nowPlaying.spin,
            playInBackground = state.stage.playInBackground,
            hints = listOf(NowPlayingStageHint.TRACK, NowPlayingStageHint.QUEUE)
        )
    )
    PresentOnCompanionLive(owner, slot)
}
