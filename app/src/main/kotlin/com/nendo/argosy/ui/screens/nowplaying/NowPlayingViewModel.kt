package com.nendo.argosy.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.audio.AmbientMusicSwitch
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.ui.components.music.MusicPlaylistPickerState
import com.nendo.argosy.ui.screens.music.musicGuarded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import com.nendo.argosy.ui.components.musicplayer.stepFrom
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

private const val TAG = "NowPlayingVM"

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val ambientMusicSwitch: AmbientMusicSwitch,
    private val nowPlayingPreferences: NowPlayingPreferencesRepository,
    controlsPreferences: ControlsPreferencesRepository,
    private val musicLibrary: MusicLibrary,
    private val notifications: NotificationManager,
    analysisStore: TrackAnalysisStore
) : ViewModel() {

    private val position = MutableStateFlow<PlaybackPosition?>(null)
    private val navigation = MutableStateFlow(NowPlayingNavigation())

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

    fun samplePosition() {
        position.value = ambientAudioManager.positionSnapshot()
    }

    fun moveVertical(delta: Int): Boolean {
        val state = uiState.value
        if (!state.showsQueue) return false
        val lastTrack = state.playback.tracks.lastIndex
        val next = when (state.focus) {
            NowPlayingFocus.TRANSPORT -> if (delta > 0) {
                state.navigation.copy(focus = NowPlayingFocus.QUEUE, queueFocus = state.playback.index.coerceIn(0, lastTrack))
            } else {
                null
            }
            NowPlayingFocus.QUEUE -> {
                val target = state.queueFocus + delta
                when {
                    target < 0 -> state.navigation.copy(focus = NowPlayingFocus.TRANSPORT)
                    target > lastTrack -> null
                    else -> state.navigation.copy(queueFocus = target)
                }
            }
        } ?: return false
        navigation.update { it.copy(focus = next.focus, queueFocus = next.queueFocus) }
        return true
    }

    fun moveTransport(delta: Int): Boolean {
        val state = uiState.value
        if (!state.hasQueue || state.prefs.layout != NowPlayingLayout.SHELF) return false
        if (state.focus != NowPlayingFocus.TRANSPORT) return false
        val buttons = transportButtons(withPlaylist = true)
        navigation.update { it.copy(transport = buttons.stepFrom(it.transport, delta)) }
        return true
    }

    fun focusQueue(): Boolean {
        val state = uiState.value
        if (!state.showsQueue) return false
        navigation.update {
            it.copy(
                focus = NowPlayingFocus.QUEUE,
                queueFocus = state.playback.index.coerceIn(0, state.playback.tracks.lastIndex)
            )
        }
        return true
    }

    fun leaveQueue(): Boolean {
        if (uiState.value.focus != NowPlayingFocus.QUEUE) return false
        navigation.update { it.copy(focus = NowPlayingFocus.TRANSPORT) }
        return true
    }

    fun confirm() {
        val state = uiState.value
        when {
            state.prefs.layout == NowPlayingLayout.SIMPLE -> togglePlayPause()
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

    fun openPlaylistPicker() {
        val track = uiState.value.playback.let { it.tracks.getOrNull(it.index) }
        val source = track?.identity?.source?.let(MusicSourceKind::fromToken)
        navigation.update { it.copy(playlistPicker = MusicPlaylistPickerState(), addingToPlaylist = false) }
        viewModelScope.launch {
            val playlists = source?.let { kind ->
                musicGuarded(TAG, "playlists") {
                    withContext(Dispatchers.IO) { musicLibrary.playlists(MusicSourceFilter.ALL) }
                }.orEmpty().filter { it.writable && it.source == kind }
            }.orEmpty()
            navigation.update { nav ->
                if (nav.playlistPicker == null) {
                    nav
                } else {
                    nav.copy(playlistPicker = MusicPlaylistPickerState(playlists = playlists, isLoading = false))
                }
            }
        }
    }

    fun movePlaylistFocus(delta: Int) {
        navigation.update { nav -> nav.copy(playlistPicker = nav.playlistPicker?.moved(delta)) }
    }

    fun focusPlaylist(index: Int) {
        navigation.update { nav -> nav.copy(playlistPicker = nav.playlistPicker?.copy(focusIndex = index)) }
    }

    fun closePlaylistPicker() {
        navigation.update { it.copy(playlistPicker = null, addingToPlaylist = false) }
    }

    fun confirmPlaylist() {
        val nav = navigation.value
        if (nav.addingToPlaylist) return
        val playlist = nav.playlistPicker?.focused ?: return
        val track = uiState.value.playback.let { it.tracks.getOrNull(it.index) } ?: return
        navigation.update { it.copy(addingToPlaylist = true) }
        viewModelScope.launch {
            val result = musicGuarded(TAG, "add to playlist") {
                withContext(Dispatchers.IO) { musicLibrary.addToPlaylist(playlist, track) }
            }
            closePlaylistPicker()
            if (result?.isSuccess == true) {
                notifications.showSuccess(NotificationText.Res(R.string.music_library_notice_added, listOf(playlist.name)))
            } else {
                notifications.showError(NotificationText.Res(R.string.music_library_notice_add_failed))
            }
        }
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
