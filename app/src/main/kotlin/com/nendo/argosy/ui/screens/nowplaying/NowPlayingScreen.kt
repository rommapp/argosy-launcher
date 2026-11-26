package com.nendo.argosy.ui.screens.nowplaying

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.ui.dualscreen.PresentOnCompanionLive
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.presentationTakesSlotsState
import com.nendo.argosy.ui.dualscreen.selectSwapModeState
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.music.MusicPlaylistPermissionModal
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerDialogs
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.ui.components.music.PlaylistPickerList
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.music.MusicModalInput
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.nowplaying.DiscArt
import com.nendo.argosy.ui.components.nowplaying.NowPlayingProgress
import com.nendo.argosy.ui.components.nowplaying.NowPlayingVisualizer
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.nowplaying.PlaybackTransportRow
import com.nendo.argosy.ui.components.nowplaying.displayArt
import com.nendo.argosy.ui.screens.music.BlurredHero
import com.nendo.argosy.ui.components.nowplaying.displayArtist
import com.nendo.argosy.ui.components.nowplaying.displayTitle
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private val Defaults = ComponentDefaults.NowPlaying

private const val NOW_PLAYING_SLOT_OWNER = "now_playing"

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenRommSettings: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) { NowPlayingInputHandler(viewModel, onBack) }
    val optionsHandler = remember(viewModel) { NowPlayingOptionsInputHandler(viewModel) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_NOW_PLAYING)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_NOW_PLAYING)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        while (isActive) {
            viewModel.samplePosition()
            delay(ComponentDefaults.MusicPlayer.positionPollMs.toLong())
        }
    }

    val split = presentationTakesSlotsState()
    LaunchedEffect(split) { viewModel.setSplit(split) }
    val quickNavigation by viewModel.quickNavigationEnabled.collectAsState()
    val selectSwaps = selectSwapModeState() == SelectSwapMode.TAP
    val slotOwner = remember(viewModel) { SlotOwner.of(NOW_PLAYING_SLOT_OWNER, viewModel) }
    state.stageSlot(bumpersSeek = !quickNavigation, selectSwaps = selectSwaps)?.let { slot ->
        PresentOnCompanionLive(owner = slotOwner, slot = slot)
    }

    ModalInputEffect(active = state.navigation.options != null, handler = optionsHandler)
    val playlistHandler = remember(viewModel) {
        MusicModalInput(
            move = viewModel.playlistPicker::move,
            confirm = viewModel.playlistPicker::confirm,
            back = viewModel::closePlaylistPicker
        )
    }
    val picker = state.navigation.playlistPicker
    ModalInputEffect(active = picker != null && picker.ownsInput, handler = playlistHandler)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (state.split) Modifier.surfaceBackdrop(BackdropRole.CONTENT) else Modifier)
    ) {
        if (state.hasQueue && !state.split) {
            state.playback.displayArt?.let { BlurredHero(it) }
            NowPlayingVisualizer(
                analysis = state.analysis,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playing = state.isAudible
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !state.hasQueue -> EmptyNowPlaying()
                    state.split -> ControlLayout(state, viewModel)
                    state.prefs.layout == NowPlayingLayout.SIMPLE -> SimpleLayout(state, viewModel)
                    else -> ShelfLayout(state, viewModel)
                }
            }
            NowPlayingFooter(
                state = state,
                bumpersSeek = !quickNavigation,
                selectSwaps = selectSwaps,
                viewModel = viewModel
            )
            FooterSpacer()
        }
        state.navigation.options?.let { options ->
            NowPlayingOptionsModal(state = state, options = options, viewModel = viewModel)
        }
        if (picker != null && picker.ownsInput) {
            AddToPlaylistModal(state = state, picker = picker, viewModel = viewModel)
        }
        MusicPlaylistPickerDialogs(
            picker = picker,
            onSubmitName = viewModel.playlistPicker::submitName,
            onCancelName = viewModel.playlistPicker::cancelNaming,
            onConfirmConflict = viewModel.playlistPicker::confirmConflict,
            onDismissConflict = viewModel.playlistPicker::dismissConflict
        )
        MusicPlaylistPermissionModal(
            visible = state.navigation.showsPlaylistPermission,
            onOpenSettings = {
                viewModel.dismissPlaylistPermission()
                onOpenRommSettings()
            },
            onDismiss = viewModel::dismissPlaylistPermission
        )
    }
}

@Composable
private fun AddToPlaylistModal(
    state: NowPlayingUiState,
    picker: MusicPlaylistPickerState,
    viewModel: NowPlayingViewModel
) {
    val theme = LocalArgosyTheme.current
    ModalScaffold(visible = true, onDismiss = viewModel::closePlaylistPicker) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = stringResource(R.string.music_library_playlists_title),
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            state.playback.trackTitle?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            PlaylistPickerList(picker = picker, enabled = !picker.isBusy) { index ->
                viewModel.playlistPicker.focus(index)
                viewModel.playlistPicker.confirm()
            }
        }
    }
}

@Composable
private fun NowPlayingFooter(
    state: NowPlayingUiState,
    bumpersSeek: Boolean,
    selectSwaps: Boolean,
    viewModel: NowPlayingViewModel
) {
    val previousHint = stringResource(R.string.now_playing_hint_previous)
    val nextHint = stringResource(R.string.now_playing_hint_next)
    val skipHint = stringResource(R.string.now_playing_hint_skip)
    val queueHint = stringResource(R.string.now_playing_hint_queue)
    val optionsHint = stringResource(R.string.now_playing_hint_options)
    val swapHint = stringResource(R.string.now_playing_hint_swap_screens)
    val hints = buildList {
        if (state.hasQueue) {
            if (bumpersSeek) add(InputButton.LB_RB to skipHint)
            add(InputButton.LT to previousHint)
            add(InputButton.RT to nextHint)
        }
        if (state.showsQueue) add(InputButton.X to queueHint)
        add(InputButton.SELECT to if (selectSwaps) swapHint else optionsHint)
    }
    FooterHints(
        hints = hints,
        onHintClick = { button ->
            when (button) {
                InputButton.LB_RB -> viewModel.seekBy(1)
                InputButton.LT -> viewModel.skipPrevious()
                InputButton.RT -> viewModel.skipNext()
                InputButton.X -> viewModel.focusQueue()
                InputButton.SELECT -> if (selectSwaps) {
                    DualScreenManagerHolder.instance?.swapRoles()
                } else {
                    viewModel.openOptions()
                }
                else -> Unit
            }
        }
    )
}

@Composable
private fun ControlLayout(state: NowPlayingUiState, viewModel: NowPlayingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, top = Dimens.spacingLg)
    ) {
        SeekWave(
            state = state,
            focused = state.focus == NowPlayingFocus.WAVE,
            onSeek = viewModel::seekToFraction
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        ShelfTransport(state = state, onButton = viewModel::activateTransport)
        RunningOrder(
            state = state,
            onPlay = viewModel::playTrack,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SeekWave(state: NowPlayingUiState, focused: Boolean, onSeek: (Float) -> Unit) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val currentOnSeek by rememberUpdatedState(onSeek)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(focused = focused, indicators = FocusIndicators.Ring, shape = shape)
            .padding(Dimens.spacingSm)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) currentOnSeek(offset.x / size.width)
                }
            }
    ) {
        NowPlayingProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.isAudible,
            analysis = state.analysis
        )
    }
}

@Composable
private fun EmptyNowPlaying() {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = stringResource(R.string.now_playing_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary
        )
        Text(
            text = stringResource(R.string.now_playing_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
    }
}

@Composable
private fun ShelfLayout(state: NowPlayingUiState, viewModel: NowPlayingViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth
        val disc: @Composable (Modifier) -> Unit = { boxModifier ->
            Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
                ShelfDisc(state = state, onToggle = { viewModel.togglePlayPause() })
            }
        }
        if (portrait) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingLg)) {
                disc(Modifier.fillMaxWidth().weight(1f - Defaults.queueWidthRatio))
                ShelfDetails(state = state, viewModel = viewModel, modifier = Modifier.weight(Defaults.queueWidthRatio))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingLg)) {
                disc(Modifier.fillMaxHeight().weight(1f - Defaults.queueWidthRatio))
                Spacer(modifier = Modifier.width(Dimens.spacingLg))
                ShelfDetails(state = state, viewModel = viewModel, modifier = Modifier.weight(Defaults.queueWidthRatio))
            }
        }
    }
}

@Composable
private fun ShelfDisc(state: NowPlayingUiState, onToggle: () -> Unit) {
    val discSize = Defaults.discSizeDp.dp
    DiscArt(
        art = state.playback.displayArt,
        style = state.prefs.style,
        spinning = state.prefs.spin,
        playing = state.isAudible,
        modifier = Modifier
            .padding(Dimens.spacingLg)
            .sizeIn(maxWidth = discSize, maxHeight = discSize)
            .clickableNoFocus(onClick = onToggle)
    )
}

@Composable
private fun ShelfDetails(state: NowPlayingUiState, viewModel: NowPlayingViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxHeight().padding(top = Dimens.spacingLg)) {
        NowPlayingKicker(state)
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        TrackTitle(state = state, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
        TrackArtist(state = state, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        NowPlayingProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.isAudible,
            analysis = state.analysis
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        ShelfTransport(state = state, onButton = viewModel::activateTransport)
        RunningOrder(
            state = state,
            onPlay = viewModel::playTrack,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShelfTransport(state: NowPlayingUiState, onButton: (MusicTransportButton) -> Unit) {
    PlaybackTransportRow(
        isAudible = state.isAudible,
        shuffle = state.playback.shuffle,
        repeat = state.playback.repeat,
        playInBackground = state.playInBackground,
        focusedButton = state.navigation.transport.takeIf { state.focus == NowPlayingFocus.TRANSPORT },
        buttonSize = Defaults.transportButtonDp.dp,
        playButtonSize = Defaults.playButtonDp.dp,
        onButton = onButton,
        withPlaylist = true,
        playIconSize = Dimens.iconLg
    )
}

@Composable
private fun SimpleLayout(state: NowPlayingUiState, viewModel: NowPlayingViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingLg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiscArt(
                art = state.playback.displayArt,
                style = state.prefs.style,
                spinning = state.prefs.spin,
                playing = state.isAudible,
                modifier = Modifier
                    .size(Defaults.simpleArtDp.dp)
                    .clickableNoFocus(onClick = { viewModel.togglePlayPause() })
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            NowPlayingKicker(state)
        }
        Spacer(modifier = Modifier.weight(1f))
        TrackTitle(state = state, style = MaterialTheme.typography.displayMedium, maxLines = 2)
        TrackArtist(state = state, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        NowPlayingProgress(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.isAudible,
            analysis = state.analysis
        )
    }
}

@Composable
private fun NowPlayingKicker(state: NowPlayingUiState) {
    val theme = LocalArgosyTheme.current
    val playback = state.playback
    val source = if (playback.sourceId == MusicSelection.LAUNCHER_ID) {
        stringResource(R.string.now_playing_source_launcher)
    } else {
        playback.sourceLabel
    }
    val album = (playback.album ?: playback.gameTitle).takeIf { playback.overrideTitle == null && it != source }
    val style = MaterialTheme.typography.labelMedium
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        source?.let {
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = style,
                color = theme.focusAccent,
                maxLines = 1
            )
        }
        album?.let {
            Text(
                text = it.uppercase(Locale.getDefault()),
                style = style,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TrackTitle(state: NowPlayingUiState, style: TextStyle, maxLines: Int) {
    Text(
        text = state.playback.displayTitle ?: stringResource(R.string.now_playing_unknown_title),
        style = style,
        color = LocalArgosyTheme.current.textPrimary,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TrackArtist(state: NowPlayingUiState, style: TextStyle) {
    val artist = state.playback.displayArtist ?: return
    Text(
        text = artist,
        style = style,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
