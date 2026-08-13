package com.nendo.argosy.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val CHROME_SCRIM_ALPHA = 0.75f

/**
 * The player window: picture underneath, chrome on top, lists over both.
 *
 * The picture is a plain surface with the stock controls switched off. The launcher decides
 * selection itself everywhere else and this screen is no exception; a second set of controls with
 * its own focus behaviour underneath ours is how a d-pad ends up moving two things at once.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.uiState.collectAsState()
    val player by viewModel.player.collectAsState()
    val theme = LocalArgosyTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickableNoFocus { viewModel.chrome.toggle() }
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        if (state.isLoading || state.isBuffering) {
            CircularProgressIndicator(
                color = theme.focusAccent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(Dimens.iconXl)
            )
        }

        AnimatedVisibility(
            visible = state.isChromeVisible && state.errorMessage == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PlayerChrome(state = state, viewModel = viewModel)
        }

        state.errorMessage?.let { message ->
            PlayerErrorPanel(
                message = message,
                onRetry = { viewModel.retry() },
                onClose = { viewModel.requestExit() },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        PlayerOverlayHost(
            state = state,
            onSelect = { index ->
                viewModel.chrome.setOverlayIndex(index)
                viewModel.confirmOverlaySelection()
            },
            onDismiss = { viewModel.chrome.closeOverlay() }
        )
    }
}

@Composable
private fun PlayerChrome(state: PlayerUiState, viewModel: PlayerViewModel) {
    val theme = LocalArgosyTheme.current
    val trickplayTile = remember(state.previewPositionMs, state.trickplayEnabled, state.itemId) {
        if (state.isScrubbing) viewModel.trickplayTile(state.previewPositionMs) else null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = CHROME_SCRIM_ALPHA))
                .padding(Dimens.playerChromePadding)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.subtitle.isNotBlank()) {
                Text(
                    text = state.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(modifier = Modifier.weight(1f))

        PlayerTransportBar(
            state = state,
            trickplayTile = trickplayTile,
            onSeekToFraction = { viewModel.scrubToFraction(it) },
            onFocusScrubber = { viewModel.chrome.setFocusRow(PlayerRow.SCRUBBER) },
            onControlClick = { index ->
                viewModel.chrome.setControlIndex(index)
                when (state.controls.getOrNull(index)) {
                    PlayerControl.PLAY_PAUSE -> viewModel.togglePlayPause()
                    PlayerControl.AUDIO -> viewModel.chrome.openOverlay(PlayerOverlay.AUDIO_TRACKS)
                    PlayerControl.SUBTITLES -> viewModel.chrome.openOverlay(PlayerOverlay.SUBTITLE_TRACKS)
                    PlayerControl.CHAPTERS -> viewModel.chrome.openOverlay(PlayerOverlay.CHAPTERS)
                    PlayerControl.SKIP -> viewModel.skipActiveSegment()
                    null -> Unit
                }
            }
        )

        FooterBar(hints = state.footerHints())
    }
}

/**
 * Only what the focused control cannot say for itself. Play, pause and seeking are drawn on screen
 * and need no caption; the shoulder buttons and the two face buttons that open lists are bound to
 * nothing visible, so they are the ones that earn the bar.
 */
private fun PlayerUiState.footerHints(): List<Pair<InputButton, String>> = buildList {
    if (chapters.isNotEmpty()) {
        add(InputButton.LB_RB to "Chapter")
        add(InputButton.X to "Chapters")
    }
    if (subtitleTracks.isNotEmpty()) add(InputButton.Y to "Subtitles")
}

@Composable
private fun PlayerErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    Column(
        modifier = modifier
            .width(Dimens.modalWidth)
            .clip(shape)
            .background(theme.surfaceElevated)
            .padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            PanelButton(label = "Try Again", focused = true, onClick = onRetry)
            PanelButton(label = "Close", focused = false, onClick = onClose)
        }
    }
}

@Composable
private fun PanelButton(label: String, focused: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (focused) theme.focusAccent else theme.textDim,
        modifier = Modifier
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.Button, shape = shape)
            .clip(shape)
            .background(theme.surfaceRaised)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.buttonPaddingH, vertical = Dimens.buttonPaddingV)
    )
}
