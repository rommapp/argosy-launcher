package com.nendo.argosy.ui.screens.nowplaying

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.nowplaying.QueueTrackRow
import com.nendo.argosy.ui.components.nowplaying.labelRes
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.flow.collectLatest

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
                QueueTrackRow(
                    number = position + 1,
                    track = track,
                    isCurrent = position == state.playback.index,
                    isFocused = queueFocused && position == state.queueFocus,
                    currentDescription = stringResource(R.string.now_playing_current_track),
                    onClick = { onPlay(position) }
                )
            }
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
                    subtitle = if (state.split) stringResource(R.string.now_playing_option_layout_single_screen) else null,
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
