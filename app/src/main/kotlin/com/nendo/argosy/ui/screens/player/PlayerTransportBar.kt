package com.nendo.argosy.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val SCRUB_TRACK_UNPLAYED_ALPHA = 0.35f
private const val CHAPTER_MARK_ALPHA = 0.7f
private const val CHROME_SCRIM_ALPHA = 0.75f

/**
 * The bar that carries position and transport.
 *
 * Two bands rather than one row of controls: left and right mean "move through the film" on the
 * scrubber and "move between buttons" underneath it, and a single focus index cannot hold both
 * meanings without one of them being wrong.
 */
@Composable
fun PlayerTransportBar(
    state: PlayerUiState,
    trickplayTile: TrickplayTile?,
    onSeekToFraction: (Float) -> Unit,
    onFocusScrubber: () -> Unit,
    onControlClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.playerTransportHeight)
            .background(Color.Black.copy(alpha = CHROME_SCRIM_ALPHA))
            .padding(Dimens.playerChromePadding)
    ) {
        ScrubBand(
            state = state,
            trickplayTile = trickplayTile,
            onSeekToFraction = onSeekToFraction,
            onFocusScrubber = onFocusScrubber
        )
        Spacer(Modifier.height(Dimens.spacingMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            state.controls.forEachIndexed { index, control ->
                TransportButton(
                    control = control,
                    isPlaying = state.isPlaying,
                    skipLabel = state.activeSkip?.kind?.label,
                    focused = state.focusRow == PlayerRow.CONTROLS && state.controlIndex == index,
                    onClick = { onControlClick(index) }
                )
            }
        }
    }
}

@Composable
private fun ScrubBand(
    state: PlayerUiState,
    trickplayTile: TrickplayTile?,
    onSeekToFraction: (Float) -> Unit,
    onFocusScrubber: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val focused = state.focusRow == PlayerRow.SCRUBBER
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = formatPlaybackTime(state.previewPositionMs),
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) theme.focusAccent else theme.textDim
        )
        ScrubTrack(
            state = state,
            trickplayTile = trickplayTile,
            focused = focused,
            onSeekToFraction = onSeekToFraction,
            onFocusScrubber = onFocusScrubber,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatPlaybackTime(state.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim
        )
    }
}

/**
 * The track itself, with the chapter boundaries marked on it. The marks are what make a chapter
 * jump legible: without them the shoulder buttons move the position to places the bar gives no
 * account of.
 */
@Composable
private fun ScrubTrack(
    state: PlayerUiState,
    trickplayTile: TrickplayTile?,
    focused: Boolean,
    onSeekToFraction: (Float) -> Unit,
    onFocusScrubber: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val thumbSize = Dimens.playerScrubThumbSize
    val trackHeight = Dimens.playerScrubTrackHeight
    val trickplayWidth = Dimens.playerTrickplayWidth
    val trickplayHeight = Dimens.playerTrickplayHeight

    BoxWithConstraints(
        modifier = modifier
            .height(thumbSize + trackHeight)
            .pointerInput(state.durationMs) {
                detectTapGestures { offset ->
                    onFocusScrubber()
                    onSeekToFraction(offset.x / size.width.toFloat())
                }
            }
            .pointerInput(state.durationMs) {
                detectHorizontalDragGestures { change, _ ->
                    onFocusScrubber()
                    onSeekToFraction(change.position.x / size.width.toFloat())
                }
            }
    ) {
        val trackWidth = maxWidth
        val fraction = state.progressFraction
        val thumbCenter = trackWidth * fraction

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(theme.textMute.copy(alpha = SCRUB_TRACK_UNPLAYED_ALPHA))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(thumbCenter)
                .height(trackHeight)
                .clip(CircleShape)
                .background(theme.focusAccent)
        )
        state.chapters.forEach { chapter ->
            if (state.durationMs <= 0) return@forEach
            val markFraction = (chapter.startMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = trackWidth * markFraction)
                    .width(Dimens.borderMedium)
                    .height(trackHeight)
                    .background(theme.textPrimary.copy(alpha = CHAPTER_MARK_ALPHA))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbCenter - thumbSize / 2)
                .size(thumbSize)
                .argosyFocusIndicators(
                    focused = focused,
                    indicators = FocusIndicators.Ring,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .background(if (focused) theme.focusAccent else theme.textPrimary)
        )

        if (state.isScrubbing && trickplayTile != null) {
            TrickplayThumbnail(
                tile = trickplayTile,
                authorizationHeader = state.trickplayAuthHeader,
                width = trickplayWidth,
                height = trickplayHeight,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = clampPreviewOffset(thumbCenter, trickplayWidth, trackWidth),
                        y = -(trickplayHeight + Dimens.spacingSm)
                    )
                    .clip(RoundedCornerShape(Dimens.radiusPanel))
            )
        }
    }
}

/**
 * Keeps the preview inside the track it belongs to, so a thumbnail near either end is not half off
 * the screen where the bar it explains cannot be seen next to it.
 */
private fun clampPreviewOffset(center: Dp, previewWidth: Dp, trackWidth: Dp): Dp {
    val half = previewWidth / 2
    val maxOffset = (trackWidth - previewWidth).coerceAtLeast(Dp.Hairline)
    return (center - half).coerceIn(Dp.Hairline, maxOffset)
}

@Composable
private fun TransportButton(
    control: PlayerControl,
    isPlaying: Boolean,
    skipLabel: String?,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val tint = if (focused) theme.focusAccent else theme.textPrimary
    val label = if (control == PlayerControl.SKIP) skipLabel else null

    Row(
        modifier = Modifier
            .height(Dimens.buttonHeight)
            .then(
                if (label != null) Modifier.width(Dimens.playerSkipButtonWidth)
                else Modifier.width(Dimens.buttonHeight)
            )
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.Button, shape = shape)
            .clip(shape)
            .background(theme.surfaceElevated.copy(alpha = CHROME_SCRIM_ALPHA))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = control.icon(isPlaying),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconSm)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun PlayerControl.icon(isPlaying: Boolean): ImageVector = when (this) {
    PlayerControl.PLAY_PAUSE -> if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
    PlayerControl.AUDIO -> Icons.Default.Audiotrack
    PlayerControl.SUBTITLES -> Icons.Default.Subtitles
    PlayerControl.CHAPTERS -> Icons.Default.FormatListBulleted
    PlayerControl.SKIP -> Icons.Default.FastForward
}
