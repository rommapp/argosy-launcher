package com.nendo.argosy.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.audio.AmbientMusicSwitch
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.QuickNavigationSource
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.ui.components.music.MusicPlaylistAddition
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerFlow
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.musicplayer.TrackListCursor
import com.nendo.argosy.ui.components.musicplayer.followPlayingFocus
import com.nendo.argosy.ui.components.musicplayer.stepFrom
import com.nendo.argosy.ui.components.musicplayer.trackListEntryFocus
import com.nendo.argosy.ui.components.musicplayer.transportButtons
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.PlaybackPosition
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.ui.components.nowplaying.currentTrackAnalysis
import com.nendo.argosy.ui.components.nowplaying.resolvedDurationMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEEK_STEP_MS = 10_000L

private class QueueTrackAddition(private val track: MusicQueueTrack) : MusicPlaylistAddition {
    override suspend fun addTo(playlists: MusicPlaylists, group: MusicPlaylistGroup): MusicPlaylistResult =
        playlists.addTrack(group, track)

    override suspend fun createNamed(playlists: MusicPlaylists, name: String): MusicPlaylistResult =
        playlists.create(name, track)
}

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val ambientMusicSwitch: AmbientMusicSwitch,
    private val nowPlayingPreferences: NowPlayingPreferencesRepository,
    controlsPreferences: ControlsPreferencesRepository,
    musicPlaylists: MusicPlaylists,
    notifications: NotificationManager,
    analysisStore: TrackAnalysisStore,
    quickNavigation: QuickNavigationSource
) : ViewModel() {

    val quickNavigationEnabled: StateFlow<Boolean> = quickNavigation.enabled

    private val position = MutableStateFlow<PlaybackPosition?>(null)
    private val navigation = MutableStateFlow(NowPlayingNavigation())
    private var playingIndex: Int? = null

    val uiState: StateFlow<NowPlayingUiState> = combine(
        ambientAudioManager.playback,
        position,
        nowPlayingPreferences.preferences,
        navigation,
        ambientAudioManager.playback.currentTrackAnalysis(analysisStore)
    ) { playback, sampled, prefs, nav, analysis ->
        NowPlayingUiState(
            playback = playback,
            positionMs = sampled?.positionMs ?: 0L,
            durationMs = playback.resolvedDurationMs(sampled),
            prefs = prefs,
            navigation = nav,
            analysis = analysis
        )
    }.combine(controlsPreferences.preferences.map { it.ambientAudioPlayInBackground }.distinctUntilChanged()) {
        state, playInBackground -> state.copy(playInBackground = playInBackground)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NowPlayingUiState(playback = ambientAudioManager.playback.value))

    val playlistPicker = MusicPlaylistPickerFlow(
        playlists = musicPlaylists,
        notifications = notifications,
        scope = viewModelScope,
        picker = { navigation.value.playlistPicker },
        updatePicker = { transform -> navigation.update { it.copy(playlistPicker = transform(it.playlistPicker)) } },
        addition = ::currentAddition,
        onFinished = {},
        onNeedsPermission = { navigation.update { it.copy(showsPlaylistPermission = true) } }
    )

    init {
        viewModelScope.launch {
            ambientAudioManager.playback
                .map { it.index }
                .distinctUntilChanged()
                .collect(::onPlayingIndex)
        }
        viewModelScope.launch {
            musicPlaylists.changes.collect { playlistPicker.refresh() }
        }
    }

    private fun onPlayingIndex(index: Int) {
        val previous = playingIndex
        playingIndex = index
        navigation.update { nav ->
            nav.copy(
                queueFocus = followPlayingFocus(
                    focus = nav.queueFocus,
                    previousPlaying = previous,
                    playing = index,
                    listFocused = nav.focus == NowPlayingFocus.QUEUE
                )
            )
        }
    }

    private fun currentAddition(): MusicPlaylistAddition? {
        val track = ambientAudioManager.playback.value.let { it.tracks.getOrNull(it.index) } ?: return null
        if (track.identity?.source?.let(MusicSourceKind::fromToken) == null) return null
        return QueueTrackAddition(track)
    }

    fun samplePosition() {
        position.value = ambientAudioManager.positionSnapshot()
    }

    private fun queueEntryFocus(): Int {
        val playback = ambientAudioManager.playback.value
        return trackListEntryFocus(
            cursor = navigation.value.queueCursor,
            playingIndex = playback.index,
            sourceId = playback.sourceId,
            trackCount = playback.tracks.size
        )
    }

    private fun leavingCursor(queueFocus: Int): TrackListCursor {
        val playback = ambientAudioManager.playback.value
        return TrackListCursor(
            position = queueFocus.coerceIn(0, (playback.tracks.size - 1).coerceAtLeast(0)),
            playingIndex = playback.index,
            sourceId = playback.sourceId
        )
    }

    fun setSplit(split: Boolean) {
        navigation.update { nav -> if (nav.split == split) nav else nav.copy(split = split) }
    }

    fun moveVertical(delta: Int): Boolean {
        val state = uiState.value
        if (!state.showsTransport) return false
        val lastTrack = state.playback.tracks.lastIndex
        when (state.focus) {
            NowPlayingFocus.WAVE -> {
                if (delta <= 0) return false
                navigation.update { it.copy(focus = NowPlayingFocus.TRANSPORT) }
            }
            NowPlayingFocus.TRANSPORT -> {
                if (delta < 0) return focusWave()
                if (!state.showsQueue) return false
                val entry = queueEntryFocus()
                navigation.update { it.copy(focus = NowPlayingFocus.QUEUE, queueFocus = entry) }
            }
            NowPlayingFocus.QUEUE -> {
                val target = state.queueFocus + delta
                when {
                    target < 0 -> return leaveQueue()
                    target > lastTrack -> return false
                    else -> navigation.update { it.copy(queueFocus = target) }
                }
            }
        }
        return true
    }

    fun moveTransport(delta: Int): Boolean {
        val state = uiState.value
        if (!state.showsTransport) return false
        when (state.focus) {
            NowPlayingFocus.QUEUE -> return leaveQueue()
            NowPlayingFocus.WAVE -> return seekBy(delta)
            NowPlayingFocus.TRANSPORT -> {
                val buttons = transportButtons(withPlaylist = true)
                navigation.update { it.copy(transport = buttons.stepFrom(it.transport, delta)) }
            }
        }
        return true
    }

    fun focusWave(): Boolean {
        val state = uiState.value
        if (!state.split || !state.hasQueue) return false
        if (state.focus == NowPlayingFocus.QUEUE) {
            navigation.update { it.copy(focus = NowPlayingFocus.WAVE, queueCursor = leavingCursor(it.queueFocus)) }
        } else {
            navigation.update { it.copy(focus = NowPlayingFocus.WAVE) }
        }
        return true
    }

    fun seekBy(direction: Int): Boolean {
        if (!uiState.value.hasQueue || direction == 0) return false
        ambientAudioManager.seekBy(direction * SEEK_STEP_MS)
        samplePosition()
        return true
    }

    fun seekToFraction(fraction: Float) {
        val state = uiState.value
        if (!state.hasQueue || state.durationMs <= 0L) return
        focusWave()
        ambientAudioManager.seekTo((state.durationMs * fraction.coerceIn(0f, 1f)).toLong())
        samplePosition()
    }

    fun focusQueue(): Boolean {
        val state = uiState.value
        if (!state.showsQueue) return false
        if (state.focus == NowPlayingFocus.QUEUE) return true
        val entry = queueEntryFocus()
        navigation.update { it.copy(focus = NowPlayingFocus.QUEUE, queueFocus = entry) }
        return true
    }

    fun leaveQueue(): Boolean {
        if (uiState.value.focus != NowPlayingFocus.QUEUE) return false
        navigation.update { it.copy(focus = NowPlayingFocus.TRANSPORT, queueCursor = leavingCursor(it.queueFocus)) }
        return true
    }

    fun confirm() {
        val state = uiState.value
        when {
            !state.showsTransport -> togglePlayPause()
            state.focus == NowPlayingFocus.WAVE -> togglePlayPause()
            state.focus == NowPlayingFocus.QUEUE -> playTrack(state.queueFocus)
            else -> activateTransport(state.navigation.transport)
        }
    }

    fun activateTransport(button: MusicTransportButton) {
        navigation.update { it.copy(focus = NowPlayingFocus.TRANSPORT, transport = button) }
        when (button) {
            MusicTransportButton.PREVIOUS -> skipPrevious()
            MusicTransportButton.PLAY_PAUSE -> togglePlayPause()
            MusicTransportButton.NEXT -> skipNext()
            MusicTransportButton.SHUFFLE -> {
                val next = !uiState.value.playback.shuffle
                viewModelScope.launch { ambientMusicSwitch.setShuffle(next) }
            }
            MusicTransportButton.ADD_TO_PLAYLIST -> openPlaylistPicker()
            MusicTransportButton.REPEAT -> {
                val next = uiState.value.playback.repeat.next
                viewModelScope.launch { ambientMusicSwitch.setRepeat(next) }
            }
            MusicTransportButton.BACKGROUND -> {
                val next = !uiState.value.playInBackground
                viewModelScope.launch { ambientMusicSwitch.setPlayInBackground(next) }
            }
        }
    }

    fun openPlaylistPicker() = playlistPicker.open()

    fun closePlaylistPicker() {
        navigation.update { nav ->
            if (nav.playlistPicker?.isBusy == true) nav else nav.copy(playlistPicker = null)
        }
    }

    fun dismissPlaylistPermission() {
        navigation.update { it.copy(showsPlaylistPermission = false) }
    }

    fun playTrack(queueIndex: Int) {
        navigation.update { it.copy(focus = NowPlayingFocus.QUEUE, queueFocus = queueIndex) }
        viewModelScope.launch {
            ensureEnabled()
            ambientAudioManager.playAt(queueIndex)
        }
    }

    fun togglePlayPause(): Boolean {
        val state = uiState.value
        if (!state.hasQueue) return false
        if (state.isAudible) {
            ambientAudioManager.pause()
            return true
        }
        viewModelScope.launch {
            ensureEnabled()
            ambientAudioManager.play()
        }
        return true
    }

    fun skipNext(): Boolean {
        if (!uiState.value.hasQueue) return false
        return ambientAudioManager.skipNext()
    }

    fun skipPrevious(): Boolean {
        if (!uiState.value.hasQueue) return false
        ambientAudioManager.skipPrevious()
        return true
    }

    private suspend fun ensureEnabled() = ambientMusicSwitch.ensureEnabled()

    fun openOptions() {
        navigation.update { it.copy(options = NowPlayingOptionsUi()) }
    }

    fun closeOptions() {
        navigation.update { it.copy(options = null) }
    }

    fun focusOption(option: NowPlayingOption) {
        updateOptions { it.copy(focused = option) }
    }

    fun moveOptionFocus(delta: Int) {
        val visible = uiState.value.visibleOptions
        updateOptions { options ->
            val current = visible.indexOf(options.focused).coerceAtLeast(0)
            options.copy(focused = visible[(current + delta).mod(visible.size)])
        }
    }

    fun requestPicker(option: NowPlayingOption) {
        updateOptions { it.copy(focused = option, pickerOption = option, pickerToken = it.pickerToken + 1) }
    }

    fun cycleStyle(delta: Int) {
        val styles = NowPlayingStyle.entries
        setStyle(styles[(currentPrefs().style.ordinal + delta).mod(styles.size)])
    }

    fun cycleLayout(delta: Int) {
        val layouts = NowPlayingLayout.entries
        setLayout(layouts[(currentPrefs().layout.ordinal + delta).mod(layouts.size)])
    }

    fun setStyle(style: NowPlayingStyle) {
        viewModelScope.launch { nowPlayingPreferences.setStyle(style) }
    }

    fun setLayout(layout: NowPlayingLayout) {
        viewModelScope.launch { nowPlayingPreferences.setLayout(layout) }
    }

    fun setSpin(spin: Boolean) {
        viewModelScope.launch { nowPlayingPreferences.setSpin(spin) }
    }

    fun toggleSpin(): Boolean {
        val next = !currentPrefs().spin
        setSpin(next)
        return next
    }

    private fun currentPrefs(): NowPlayingPreferences = uiState.value.prefs

    private fun updateOptions(transform: (NowPlayingOptionsUi) -> NowPlayingOptionsUi) {
        navigation.update { nav -> nav.copy(options = nav.options?.let(transform)) }
    }
}
