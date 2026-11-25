package com.nendo.argosy.ui.screens.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.nowplaying.formatPlaybackTime
import com.nendo.argosy.ui.components.nowplaying.labelRes
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.flow.collectLatest

private const val MS_PER_SECOND = 1000L

@Composable
internal fun RunningOrder(
    state: NowPlayingUiState,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks = state.playback.tracks
    if (tracks.isEmpty()) return
    val listState = rememberLazyListState()
    val queueFocused = state.focus == NowPlayingFocus.QUEUE
    val anchor = if (queueFocused) state.queueFocus else state.playback.index
    val reduced = reducedMotion()
    LaunchedEffect(anchor, tracks.size) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collectLatest { if (anchor in tracks.indices) listState.animateScrollToItemCentered(anchor, reduced) }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        PanelSectionHeader(
            title = stringResource(R.string.now_playing_running_order),
            modifier = Modifier.padding(top = Dimens.spacingMd, bottom = Dimens.spacingXs)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingLg)
        ) {
            itemsIndexed(tracks, key = { position, track -> "$position:${track.id}" }) { position, track ->
                RunningOrderRow(
                    number = position + 1,
                    track = track,
                    isCurrent = position == state.playback.index,
                    isFocused = queueFocused && position == state.queueFocus,
                    onClick = { onPlay(position) }
                )
            }
        }
    }
}

@Composable
private fun RunningOrderRow(
    number: Int,
    track: MusicQueueTrack,
    isCurrent: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val detail = track.artist ?: track.gameTitle
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(Dimens.iconLg), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.now_playing_current_track),
                    tint = theme.focusAccent,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) theme.focusAccent else theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        track.durationSec?.let { seconds ->
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = formatPlaybackTime(seconds * MS_PER_SECOND),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun NowPlayingOptionsModal(
    state: NowPlayingUiState,
    options: NowPlayingOptionsUi,
    viewModel: NowPlayingViewModel
) {
    val styleLabels = NowPlayingStyle.entries.map { stringResource(it.labelRes) }
    val layoutLabels = NowPlayingLayout.entries.map { stringResource(it.labelRes) }
    fun pickerToken(option: NowPlayingOption) = if (options.pickerOption == option) options.pickerToken else 0

    Modal(
        title = stringResource(R.string.now_playing_options_title),
        onDismiss = viewModel::closeOptions
    ) {
        state.visibleOptions.forEach { option ->
            val focused = options.focused == option
            when (option) {
                NowPlayingOption.STYLE -> CyclePreference(
                    title = stringResource(R.string.now_playing_option_style),
                    value = styleLabels[state.prefs.style.ordinal],
                    isFocused = focused,
                    onClick = {
                        viewModel.focusOption(option)
                        viewModel.cycleStyle(1)
                    },
                    onPrev = {
                        viewModel.focusOption(option)
                        viewModel.cycleStyle(-1)
                    },
                    options = styleLabels,
                    onSelect = { index -> viewModel.setStyle(NowPlayingStyle.entries[index]) },
                    pickerRequestToken = pickerToken(option),
                    selectedIndex = state.prefs.style.ordinal
                )
                NowPlayingOption.SPIN -> SwitchPreference(
                    title = stringResource(R.string.now_playing_option_spin),
                    isEnabled = state.prefs.spin,
                    isFocused = focused,
                    onToggle = { enabled ->
                        viewModel.focusOption(option)
                        viewModel.setSpin(enabled)
                    }
                )
                NowPlayingOption.LAYOUT -> CyclePreference(
                    title = stringResource(R.string.now_playing_option_layout),
                    value = layoutLabels[state.prefs.layout.ordinal],
                    isFocused = focused,
                    onClick = {
                        viewModel.focusOption(option)
                        viewModel.cycleLayout(1)
                    },
                    onPrev = {
                        viewModel.focusOption(option)
                        viewModel.cycleLayout(-1)
                    },
                    options = layoutLabels,
                    onSelect = { index -> viewModel.setLayout(NowPlayingLayout.entries[index]) },
                    pickerRequestToken = pickerToken(option),
                    selectedIndex = state.prefs.layout.ordinal
                )
            }
        }
    }
}
