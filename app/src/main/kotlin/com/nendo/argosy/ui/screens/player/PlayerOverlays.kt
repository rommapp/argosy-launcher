package com.nendo.argosy.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val SUBTITLE_OFF_ROW = 0

/**
 * The lists the player raises over the picture.
 *
 * They render only; the player's own handler owns the keys while one is open, so nothing here
 * subscribes input of its own.
 */
@Composable
fun PlayerOverlayHost(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    when (state.overlay) {
        PlayerOverlay.NONE -> Unit
        PlayerOverlay.AUDIO_TRACKS -> AudioTrackOverlay(state, onSelect, onDismiss)
        PlayerOverlay.SUBTITLE_TRACKS -> SubtitleTrackOverlay(state, onSelect, onDismiss)
        PlayerOverlay.CHAPTERS -> ChapterOverlay(state, onSelect, onDismiss)
        PlayerOverlay.QUALITY -> QualityOverlay(state, onSelect, onDismiss)
    }
}

/**
 * Picks the ceiling this viewing streams at.
 *
 * The choice lasts for this viewing only, so the row marked as current is the one in force now
 * rather than the saved preference; changing it re-negotiates and picks the picture back up where
 * it was.
 */
@Composable
private fun QualityOverlay(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val tiers = com.nendo.argosy.data.preferences.MediaStreamingQuality.entries
    Modal(
        title = "Quality",
        baseWidth = Dimens.modalWidth,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.A to "Use", InputButton.B to "Close")
    ) {
        OverlayList(selectedIndex = state.overlayIndex, itemCount = tiers.size) { index ->
            val tier = tiers[index]
            OverlayRow(
                label = tier.displayName,
                supporting = null,
                selected = tier == state.streamingQuality,
                focused = index == state.overlayIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun AudioTrackOverlay(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "Audio",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.A to "Select", InputButton.B to "Close")
    ) {
        OverlayList(selectedIndex = state.overlayIndex, itemCount = state.audioTracks.size) { index ->
            val track = state.audioTracks[index]
            OverlayRow(
                label = track.label,
                supporting = track.language,
                selected = track.streamIndex == state.selectedAudioStreamIndex,
                focused = index == state.overlayIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

/**
 * Subtitles, with "Off" above the tracks and burn-in below them.
 *
 * Burn-in sits in this list rather than in settings because it is a decision about this film: a
 * picture-based track can only be shown by having the server draw it into the video, which means a
 * re-encode, and that is a cost worth paying for one title and not for every title.
 */
@Composable
private fun SubtitleTrackOverlay(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Modal(
        title = "Subtitles",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.A to "Select", InputButton.B to "Close")
    ) {
        OverlayList(selectedIndex = state.overlayIndex, itemCount = state.overlayItemCount) { index ->
            when {
                index == SUBTITLE_OFF_ROW -> OverlayRow(
                    label = "Off",
                    supporting = null,
                    selected = state.selectedSubtitleStreamIndex == null,
                    focused = index == state.overlayIndex,
                    onClick = { onSelect(index) }
                )
                index == state.burnInRowIndex -> OverlayRow(
                    label = "Burn In Picture Subtitles",
                    supporting = if (state.burnInImageSubtitles) {
                        "On for this playback - the server re-encodes the video"
                    } else {
                        "Off - picture subtitles cannot be shown"
                    },
                    selected = state.burnInImageSubtitles,
                    focused = index == state.overlayIndex,
                    onClick = { onSelect(index) }
                )
                else -> {
                    val track = state.subtitleTracks[index - 1]
                    OverlayRow(
                        label = track.label,
                        supporting = if (track.isTextSubtitle) track.language else "Picture subtitle",
                        selected = track.streamIndex == state.selectedSubtitleStreamIndex,
                        focused = index == state.overlayIndex,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
        if (state.subtitleNotice != null) {
            Text(
                text = state.subtitleNotice,
                style = MaterialTheme.typography.bodySmall,
                color = theme.destructive,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun ChapterOverlay(
    state: PlayerUiState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "Chapters",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss,
        footerHints = listOf(InputButton.A to "Play From Here", InputButton.B to "Close")
    ) {
        OverlayList(selectedIndex = state.overlayIndex, itemCount = state.chapters.size) { index ->
            val chapter = state.chapters[index]
            OverlayRow(
                label = chapter.name,
                supporting = formatPlaybackTime(chapter.startMs),
                selected = false,
                focused = index == state.overlayIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun OverlayList(
    selectedIndex: Int,
    itemCount: Int,
    row: @Composable (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        if (itemCount > 0) listState.animateScrollToItem(selectedIndex.coerceIn(0, itemCount - 1))
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        modifier = Modifier.heightIn(max = Dimens.menuRowHeightLg * OVERLAY_MAX_ROWS)
    ) {
        items(count = itemCount, key = { index -> index }) { index ->
            row(index)
        }
    }
}

@Composable
private fun OverlayRow(
    label: String,
    supporting: String?,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val tint = if (focused) theme.focusAccent else theme.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators.ListRow,
                selected = selected,
                shape = shape
            )
            .clip(shape)
            .background(theme.surfaceElevated)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

private const val OVERLAY_MAX_ROWS = 6
