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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
private const val GROUP_DIVIDER_ALPHA = 0.5f

/**
 * The bar that carries position and transport.
 *
 * Two bands rather than one row of controls: left and right mean "move through the film" on the
 * scrubber and "move between buttons" underneath it, and a single focus index cannot hold both
 * meanings without one of them being wrong.
 *
 * Between them runs one line that names whatever the highlight is on. That line is what lets the
 * buttons be icons and lets the player carry no legend of its own: a viewer reads what the thing
 * under their thumb does, rather than a table of which button opens what.
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
        Spacer(Modifier.height(Dimens.spacingSm))
        FocusCaption(state = state)
        Spacer(Modifier.height(Dimens.spacingXs))
        ControlRow(state = state, onControlClick = onControlClick)
    }
}

/**
 * The one line that describes the current highlight: on the transport it names the focused button,
 * and on the scrubber it says which chapter the thumb is standing in - or, on an item with no
 * chapters, how much of it is left.
 */
@Composable
private fun FocusCaption(state: PlayerUiState) {
    val theme = LocalArgosyTheme.current
    Text(
        text = state.focusCaption(),
        style = MaterialTheme.typography.labelMedium,
        color = theme.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * The transport, as one flat walk from left to right. It scrolls rather than wrapping onto a second
 * line, because a second line would mean up and down had to choose between rows of buttons and the
 * scrubber, and it follows the highlight so a button reached on a narrow screen is a button that can
 * be seen.
 */
@Composable
private fun ControlRow(state: PlayerUiState, onControlClick: (Int) -> Unit) {
    val controls = remember(
        state.audioTracks,
        state.subtitleTracks,
        state.chapters,
        state.nextEpisode,
        state.activeSkip
    ) { state.controls }
    val listState = rememberLazyListState()
    val focusedIndex = state.focusedControlIndex
    val onControls = state.focusRow == PlayerRow.CONTROLS

    LaunchedEffect(focusedIndex, onControls, controls.size) {
        if (onControls && controls.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, controls.lastIndex))
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(controls, key = { _, control -> control.name }) { index, control ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (index > 0 && controls[index - 1].group != control.group) GroupDivider()
                TransportButton(
                    control = control,
                    isPlaying = state.isPlaying,
                    isWatched = state.isWatched,
                    skipLabel = state.activeSkip?.kind?.label,
                    focused = onControls && focusedIndex == index,
                    onClick = { onControlClick(index) }
                )
            }
        }
    }
}

/**
 * Marks where the row stops being about time and starts being about the item. It takes no focus
 * slot; it is there so a row walked in one dimension still reads as three groups.
 */
@Composable
private fun GroupDivider() {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .padding(horizontal = Dimens.spacingXs)
            .width(Dimens.borderThin)
            .height(Dimens.iconSm)
            .background(theme.textMute.copy(alpha = GROUP_DIVIDER_ALPHA))
    )
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

/**
 * A button in the transport row. Only the skip prompt carries its name inline: it is the one control
 * that appears part-way through a film and has to be recognised without being walked to, while every
 * other button is named by the caption line as soon as the highlight reaches it.
 */
@Composable
private fun TransportButton(
    control: PlayerControl,
    isPlaying: Boolean,
    isWatched: Boolean,
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
            imageVector = control.icon(isPlaying, isWatched),
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

private fun PlayerControl.icon(isPlaying: Boolean, isWatched: Boolean): ImageVector = when (this) {
    PlayerControl.SKIP_BACK -> Icons.Default.Replay10
    PlayerControl.PLAY_PAUSE -> if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
    PlayerControl.SKIP_FORWARD -> Icons.Default.Forward10
    PlayerControl.AUDIO -> Icons.Default.Audiotrack
    PlayerControl.SUBTITLES -> Icons.Default.Subtitles
    PlayerControl.CHAPTERS -> Icons.Default.FormatListBulleted
    PlayerControl.NEXT_EPISODE -> Icons.Default.SkipNext
    PlayerControl.MARK_WATCHED ->
        if (isWatched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline
    PlayerControl.CLOSE -> Icons.Default.Close
    PlayerControl.SKIP -> Icons.Default.FastForward
}

/**
 * What the caption says. Copy lives here rather than in the state so a rename never has to travel
 * through the model the input handler reads.
 */
private fun PlayerUiState.focusCaption(): String = when (focusRow) {
    PlayerRow.CONTROLS -> focusedControl?.let { controlLabel(it) }.orEmpty()
    PlayerRow.SCRUBBER -> scrubberCaption()
}

private fun PlayerUiState.scrubberCaption(): String {
    val chapter = chapters.lastOrNull { it.startMs <= previewPositionMs }
    if (chapter != null) return chapter.name
    if (durationMs <= 0) return ""
    return "${formatPlaybackTime(durationMs - previewPositionMs)} Left"
}

private fun PlayerUiState.controlLabel(control: PlayerControl): String = when (control) {
    PlayerControl.SKIP_BACK -> "Back ${PLAYER_SEEK_STEP_SECONDS}s"
    PlayerControl.PLAY_PAUSE -> if (isPlaying) "Pause" else "Play"
    PlayerControl.SKIP_FORWARD -> "Forward ${PLAYER_SEEK_STEP_SECONDS}s"
    PlayerControl.AUDIO -> "Audio Track"
    PlayerControl.SUBTITLES -> "Subtitles"
    PlayerControl.CHAPTERS -> "Chapters"
    PlayerControl.NEXT_EPISODE -> nextEpisode?.label
        ?.takeIf { it.isNotBlank() }
        ?.let { "Next Episode  $it" }
        ?: "Next Episode"
    PlayerControl.MARK_WATCHED -> if (isWatched) "Watched" else "Mark Watched"
    PlayerControl.CLOSE -> "Close"
    PlayerControl.SKIP -> activeSkip?.kind?.label.orEmpty()
}
