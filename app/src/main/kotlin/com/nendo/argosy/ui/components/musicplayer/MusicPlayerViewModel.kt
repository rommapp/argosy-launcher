package com.nendo.argosy.ui.components.musicplayer

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.ui.audio.AmbientMusicSwitch
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.domain.model.MusicPlaylistCatalogEntry
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.domain.model.MusicServerStatus
import com.nendo.argosy.domain.usecase.music.GetMusicPlaylistsUseCase
import com.nendo.argosy.domain.usecase.music.ResolveMusicQueueUseCase
import com.nendo.argosy.domain.usecase.music.SearchSoundtrackGamesUseCase
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.ui.components.nowplaying.currentTrackAnalysis
import com.nendo.argosy.ui.components.nowplaying.resolvedDurationMs
import com.nendo.argosy.ui.screens.settings.delegates.VolumeLevels
import com.nendo.argosy.ui.screens.settings.delegates.adjustInList
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

private const val TAG = "MusicPlayerVM"
private const val SOUNDTRACK_PAGE_SIZE = 50
private const val PREFETCH_THRESHOLD = 8

@OptIn(FlowPreview::class)
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val controlsPreferences: ControlsPreferencesRepository,
    private val nowPlayingPreferences: NowPlayingPreferencesRepository,
    private val ambientMusicSwitch: AmbientMusicSwitch,
    private val getMusicPlaylists: GetMusicPlaylistsUseCase,
    private val searchSoundtrackGames: SearchSoundtrackGamesUseCase,
    private val resolveMusicQueue: ResolveMusicQueueUseCase,
    private val analysisStore: TrackAnalysisStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

    private val searchInput = MutableStateFlow("")
    private var playlistCatalog: List<MusicPlaylistCatalogEntry> = emptyList()
    private var loadJob: Job? = null
    private var selectJob: Job? = null

    init {
        viewModelScope.launch {
            ambientAudioManager.playback.collect { playback ->
                _uiState.update { state ->
                    val lastTrack = (playback.tracks.size - 1).coerceAtLeast(0)
                    val sourceChanged = playback.sourceId != state.playback.sourceId
                    val followed = followPlayingFocus(
                        focus = state.trackFocus,
                        previousPlaying = state.playback.index,
                        playing = playback.index,
                        listFocused = state.focusedRow == MusicPlayerRow.TRACKS
                    )
                    val next = state.copy(
                        playback = playback,
                        trackFocus = if (sourceChanged) playback.index else followed.coerceIn(0, lastTrack),
                        trackCursor = if (sourceChanged) null else state.trackCursor
                    )
                    val rowGone = when (state.focusedRow) {
                        MusicPlayerRow.TRANSPORT, MusicPlayerRow.NOW_PLAYING, MusicPlayerRow.VOLUME -> !next.hasQueue
                        MusicPlayerRow.TRACKS -> playback.tracks.isEmpty()
                        MusicPlayerRow.SOURCES -> false
                    }
                    if (rowGone) next.copy(focusedRow = MusicPlayerRow.SOURCES) else next
                }
            }
        }
        viewModelScope.launch {
            controlsPreferences.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        launcherEnabled = prefs.ambientAudioEnabled,
                        volumeLevel = prefs.ambientAudioVolume,
                        playInBackground = prefs.ambientAudioPlayInBackground
                    )
                }
            }
        }
        viewModelScope.launch {
            ambientAudioManager.playback.currentTrackAnalysis(analysisStore).collect { analysis ->
                _uiState.update { it.copy(analysis = analysis) }
            }
        }
        viewModelScope.launch {
            nowPlayingPreferences.preferences.collect { prefs ->
                _uiState.update { it.copy(nowPlaying = prefs) }
            }
        }
        viewModelScope.launch {
            searchInput
                .drop(1)
                .debounce(ComponentDefaults.MusicPlayer.searchDebounceMs.toLong())
                .distinctUntilChanged()
                .collectLatest { onQueryChanged() }
        }
    }

    fun samplePosition() {
        val position = ambientAudioManager.positionSnapshot()
        _uiState.update {
            it.copy(
                positionMs = position?.positionMs ?: 0L,
                durationMs = it.playback.resolvedDurationMs(position)
            )
        }
    }

    fun onPageHidden() {
        if (_uiState.value.browse != null) closeBrowse()
    }

    fun moveRow(delta: Int): Boolean {
        val state = _uiState.value
        val trackCount = state.playback.tracks.size
        val next: MusicPlayerUiState = when (state.focusedRow) {
            MusicPlayerRow.TRANSPORT -> if (delta > 0) state.copy(focusedRow = MusicPlayerRow.NOW_PLAYING) else state
            MusicPlayerRow.NOW_PLAYING -> state.copy(
                focusedRow = if (delta > 0) MusicPlayerRow.VOLUME else MusicPlayerRow.TRANSPORT
            )
            MusicPlayerRow.VOLUME -> state.copy(
                focusedRow = if (delta > 0) MusicPlayerRow.SOURCES else MusicPlayerRow.NOW_PLAYING
            )
            MusicPlayerRow.SOURCES -> when {
                delta < 0 -> if (state.hasQueue) state.copy(focusedRow = MusicPlayerRow.VOLUME) else state
                trackCount > 0 -> state.copy(
                    focusedRow = MusicPlayerRow.TRACKS,
                    trackFocus = trackListEntryFocus(
                        cursor = state.trackCursor,
                        playingIndex = state.playback.index,
                        sourceId = state.playback.sourceId,
                        trackCount = trackCount
                    )
                )
                else -> state
            }
            MusicPlayerRow.TRACKS -> {
                val target = state.trackFocus + delta
                when {
                    target < 0 -> state.copy(focusedRow = MusicPlayerRow.SOURCES, trackCursor = state.leavingCursor())
                    target >= trackCount -> state
                    else -> state.copy(trackFocus = target)
                }
            }
        }
        if (next == state) return false
        _uiState.update { current ->
            current.copy(focusedRow = next.focusedRow, trackFocus = next.trackFocus, trackCursor = next.trackCursor)
        }
        return true
    }

    private fun MusicPlayerUiState.leavingCursor(): TrackListCursor =
        TrackListCursor(position = trackFocus, playingIndex = playback.index, sourceId = playback.sourceId)

    fun leaveTrackList(): Boolean {
        val state = _uiState.value
        if (state.focusedRow != MusicPlayerRow.TRACKS) return false
        _uiState.update {
            it.copy(
                focusedRow = if (it.hasQueue) MusicPlayerRow.TRANSPORT else MusicPlayerRow.SOURCES,
                trackCursor = it.leavingCursor()
            )
        }
        return true
    }

    fun moveHorizontal(delta: Int): Boolean {
        val state = _uiState.value
        return when (state.focusedRow) {
            MusicPlayerRow.TRANSPORT -> {
                val buttons = transportButtons(withPlaylist = false)
                _uiState.update { it.copy(transportButton = buttons.stepFrom(it.transportButton, delta)) }
                true
            }
            MusicPlayerRow.SOURCES -> {
                val kinds = MusicBrowseKind.entries
                _uiState.update {
                    it.copy(sourceButton = kinds[(it.sourceButton.ordinal + delta).mod(kinds.size)])
                }
                true
            }
            MusicPlayerRow.VOLUME -> stepVolume(delta)
            MusicPlayerRow.TRACKS -> leaveTrackList()
            MusicPlayerRow.NOW_PLAYING -> false
        }
    }

    fun focusNowPlayingLink() {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.NOW_PLAYING) }
    }

    fun focusTransport(button: MusicTransportButton) {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.TRANSPORT, transportButton = button) }
    }

    fun focusVolume() {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.VOLUME) }
    }

    fun toggleMusicEnabled(): Boolean {
        val enabled = !_uiState.value.launcherEnabled
        setMusicEnabled(enabled)
        return enabled
    }

    fun setMusicEnabled(enabled: Boolean) {
        _uiState.update { it.copy(launcherEnabled = enabled) }
        viewModelScope.launch {
            controlsPreferences.setAmbientAudioEnabled(enabled)
            ambientAudioManager.setEnabled(enabled)
            if (enabled) ambientAudioManager.fadeIn() else ambientAudioManager.fadeOut()
        }
    }

    fun stepVolume(delta: Int): Boolean {
        val state = _uiState.value
        if (!state.launcherEnabled) return false
        val next = adjustInList(state.volumeLevel, VolumeLevels.AMBIENT_AUDIO, delta) ?: return false
        applyVolumeLevel(next)
        return true
    }

    fun setVolumeFraction(fraction: Float) {
        if (!_uiState.value.launcherEnabled) return
        val levels = VolumeLevels.AMBIENT_AUDIO
        val index = (ceil(fraction.coerceIn(0f, 1f) * levels.size).toInt() - 1).coerceIn(0, levels.lastIndex)
        applyVolumeLevel(levels[index])
    }

    private fun applyVolumeLevel(level: Int) {
        _uiState.update { it.copy(volumeLevel = level) }
        viewModelScope.launch {
            controlsPreferences.setAmbientAudioVolume(level)
            ambientAudioManager.setVolume(level)
        }
    }

    fun focusSource(kind: MusicBrowseKind) {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.SOURCES, sourceButton = kind) }
    }

    fun confirmRow(): Boolean {
        val state = _uiState.value
        when (state.focusedRow) {
            MusicPlayerRow.TRANSPORT -> return activateTransport(state.transportButton)
            MusicPlayerRow.VOLUME -> toggleMusicEnabled()
            MusicPlayerRow.SOURCES -> openBrowse(state.sourceButton)
            MusicPlayerRow.TRACKS -> playTrack(state.trackFocus)
            MusicPlayerRow.NOW_PLAYING -> Unit
        }
        return true
    }

    fun playTrack(position: Int) {
        _uiState.update { it.copy(focusedRow = MusicPlayerRow.TRACKS, trackFocus = position) }
        viewModelScope.launch {
            ensureEnabled()
            ambientAudioManager.playAt(position)
        }
    }

    fun activateTransport(button: MusicTransportButton): Boolean {
        when (button) {
            MusicTransportButton.PREVIOUS -> skipPrevious()
            MusicTransportButton.PLAY_PAUSE -> togglePlayPause()
            MusicTransportButton.NEXT -> return skipNext()
            MusicTransportButton.SHUFFLE -> toggleShuffle()
            MusicTransportButton.ADD_TO_PLAYLIST -> return false
            MusicTransportButton.REPEAT -> {
                val next = _uiState.value.playback.repeat.next
                viewModelScope.launch { ambientMusicSwitch.setRepeat(next) }
            }
            MusicTransportButton.BACKGROUND -> togglePlayInBackground()
        }
        return true
    }

    fun togglePlayInBackground() {
        val next = !_uiState.value.playInBackground
        _uiState.update { it.copy(playInBackground = next) }
        viewModelScope.launch { ambientMusicSwitch.setPlayInBackground(next) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isAudible) {
            ambientAudioManager.pause()
            return
        }
        viewModelScope.launch {
            ensureEnabled()
            ambientAudioManager.play()
        }
    }

    fun skipNext(): Boolean {
        if (!_uiState.value.launcherEnabled) return false
        return ambientAudioManager.skipNext()
    }

    fun skipPrevious() {
        if (!_uiState.value.launcherEnabled) return
        ambientAudioManager.skipPrevious()
    }

    fun toggleShuffle() {
        val next = !_uiState.value.playback.shuffle
        viewModelScope.launch { ambientMusicSwitch.setShuffle(next) }
    }

    private suspend fun ensureEnabled() = ambientMusicSwitch.ensureEnabled()

    fun openBrowse(kind: MusicBrowseKind) {
        loadJob?.cancel()
        selectJob?.cancel()
        searchInput.value = ""
        _uiState.update { it.copy(browse = MusicBrowseUi(kind = kind)) }
        loadBrowse(reset = true)
    }

    fun closeBrowse() {
        selectJob?.cancel()
        clearBrowse()
    }

    private fun clearBrowse() {
        loadJob?.cancel()
        searchInput.value = ""
        _uiState.update { it.copy(browse = null) }
    }

    private suspend fun <T> attempt(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$what failed: ${e.message}")
        null
    }

    fun updateQuery(query: String) {
        _uiState.update { state -> state.copy(browse = state.browse?.copy(query = query)) }
        searchInput.value = query
    }

    fun focusSearch() {
        _uiState.update { state ->
            state.copy(
                browse = state.browse?.copy(
                    focusIndex = MusicBrowseUi.SEARCH_FOCUS_INDEX,
                    showKeyboard = true
                )
            )
        }
    }

    fun setBrowseFocus(index: Int) {
        _uiState.update { state ->
            val browse = state.browse ?: return@update state
            state.copy(
                browse = browse.copy(
                    focusIndex = index.coerceIn(MusicBrowseUi.SEARCH_FOCUS_INDEX, browse.itemCount - 1),
                    showKeyboard = false
                )
            )
        }
    }

    fun moveBrowseFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            val browse = state.browse ?: return@update state
            val next = (browse.focusIndex + delta)
                .coerceIn(MusicBrowseUi.SEARCH_FOCUS_INDEX, browse.itemCount - 1)
            moved = next != browse.focusIndex
            state.copy(browse = browse.copy(focusIndex = next, showKeyboard = false))
        }
        maybeLoadMore()
        return moved
    }

    fun confirmBrowse(index: Int? = null) {
        val browse = _uiState.value.browse ?: return
        val target = index ?: browse.focusIndex
        when {
            target == MusicBrowseUi.SEARCH_FOCUS_INDEX -> focusSearch()
            browse.status == MusicBrowseStatus.FAILED -> loadBrowse(reset = true)
            else -> browse.rows.getOrNull(target)?.let { select(it.selection) }
        }
    }

    fun onListEndApproached() = maybeLoadMore(force = true)

    val needsRommSignIn: Boolean
        get() = _uiState.value.browse?.notice == MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS

    private fun maybeLoadMore(force: Boolean = false) {
        val browse = _uiState.value.browse ?: return
        if (browse.kind != MusicBrowseKind.SOUNDTRACKS || !browse.hasMore || browse.isLoadingMore) return
        if (!force && browse.focusIndex < browse.rows.size - PREFETCH_THRESHOLD) return
        loadBrowse(reset = false)
    }

    private fun select(selection: MusicSelection) {
        if (selection is MusicSelection.Launcher) {
            selectJob?.cancel()
            selectJob = viewModelScope.launch {
                ensureEnabled()
                ambientAudioManager.activateLauncher()
                clearBrowse()
            }
            return
        }
        if (_uiState.value.browse?.pendingSelectionId != null) return
        selectJob = viewModelScope.launch {
            updateBrowse { it.copy(pendingSelectionId = selection.id, notice = null) }
            val tracks = attempt("Queue resolve for ${selection.id}") { resolveMusicQueue(selection) }
                .orEmpty()
            if (tracks.isEmpty()) {
                updateBrowse {
                    it.copy(pendingSelectionId = null, notice = MusicBrowseNotice.NO_PLAYABLE_TRACKS)
                }
                return@launch
            }
            ensureEnabled()
            ambientAudioManager.playSelection(selection.id, selection.label(), tracks)
            clearBrowse()
        }
    }

    private fun onQueryChanged() {
        val browse = _uiState.value.browse ?: return
        when (browse.kind) {
            MusicBrowseKind.PLAYLISTS -> applyPlaylistFilter()
            MusicBrowseKind.SOUNDTRACKS -> loadBrowse(reset = true)
        }
    }

    private fun loadBrowse(reset: Boolean) {
        val browse = _uiState.value.browse ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            updateBrowse {
                if (reset) {
                    it.copy(status = MusicBrowseStatus.LOADING, rows = emptyList(), hasMore = false, notice = null)
                } else {
                    it.copy(isLoadingMore = true)
                }
            }
            when (browse.kind) {
                MusicBrowseKind.PLAYLISTS -> loadPlaylists()
                MusicBrowseKind.SOUNDTRACKS -> loadSoundtracks(reset)
            }
        }
    }

    private suspend fun loadPlaylists() {
        val catalog = attempt("Playlist load") { getMusicPlaylists() }
        if (catalog == null) {
            updateBrowse { it.copy(status = MusicBrowseStatus.FAILED, focusIndex = 0) }
            return
        }
        playlistCatalog = catalog.entries
        updateBrowse { it.copy(notice = playlistNotice(catalog.serverStatus)) }
        applyPlaylistFilter()
    }

    private fun applyPlaylistFilter() {
        val query = _uiState.value.browse?.query?.trim().orEmpty()
        val rows = playlistCatalog
            .filter { entry ->
                query.isEmpty() || entry.selection is MusicSelection.Launcher ||
                    (entry.selection as? MusicSelection.ServerPlaylist)?.name
                        ?.contains(query, ignoreCase = true) == true
            }
            .map { it.toRow() }
        updateBrowse { browse ->
            browse.copy(
                rows = rows,
                status = MusicBrowseStatus.READY,
                focusIndex = settledFocus(browse, rows.size)
            )
        }
    }

    private suspend fun loadSoundtracks(reset: Boolean) {
        val current = _uiState.value.browse ?: return
        val offset = if (reset) 0 else current.rows.size
        val page = attempt("Soundtrack load") {
            searchSoundtrackGames(current.query, offset, SOUNDTRACK_PAGE_SIZE)
        }
        if (page == null) {
            updateBrowse {
                if (reset) {
                    it.copy(status = MusicBrowseStatus.FAILED, focusIndex = 0, isLoadingMore = false)
                } else {
                    it.copy(isLoadingMore = false, hasMore = false)
                }
            }
            return
        }
        val newRows = page.entries.map { entry ->
            MusicBrowseRowUi(
                selection = entry.selection,
                title = entry.selection.title,
                ownerName = null,
                platformName = entry.platformName,
                coverPath = entry.coverPath,
                trackCount = entry.trackCount
            )
        }
        updateBrowse { browse ->
            val merged = if (reset) newRows else (browse.rows + newRows).distinctBy { it.key }
            browse.copy(
                rows = merged,
                status = MusicBrowseStatus.READY,
                hasMore = page.hasMore,
                isLoadingMore = false,
                notice = if (page.fromServer) null else soundtrackNotice(page.serverStatus),
                focusIndex = if (reset) settledFocus(browse, merged.size) else browse.focusIndex
            )
        }
    }

    private fun settledFocus(browse: MusicBrowseUi, count: Int): Int = when {
        browse.isSearchFocused -> MusicBrowseUi.SEARCH_FOCUS_INDEX
        count == 0 -> MusicBrowseUi.SEARCH_FOCUS_INDEX
        else -> browse.focusIndex.coerceIn(0, count - 1)
    }

    private fun updateBrowse(transform: (MusicBrowseUi) -> MusicBrowseUi) {
        _uiState.update { state -> state.copy(browse = state.browse?.let(transform)) }
    }

    private fun playlistNotice(status: MusicServerStatus): MusicBrowseNotice? = when (status) {
        MusicServerStatus.OFFLINE -> MusicBrowseNotice.OFFLINE
        MusicServerStatus.FAILED -> MusicBrowseNotice.SERVER_FAILED
        MusicServerStatus.UNAUTHORIZED -> MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS
        MusicServerStatus.AVAILABLE,
        MusicServerStatus.UNSUPPORTED -> null
    }

    private fun soundtrackNotice(status: MusicServerStatus): MusicBrowseNotice? = when (status) {
        MusicServerStatus.OFFLINE -> MusicBrowseNotice.OFFLINE
        MusicServerStatus.FAILED -> MusicBrowseNotice.SERVER_FAILED
        MusicServerStatus.UNSUPPORTED,
        MusicServerStatus.UNAUTHORIZED -> MusicBrowseNotice.LOCAL_ONLY
        MusicServerStatus.AVAILABLE -> null
    }

    private fun MusicPlaylistCatalogEntry.toRow(): MusicBrowseRowUi = MusicBrowseRowUi(
        selection = selection,
        title = (selection as? MusicSelection.ServerPlaylist)?.name,
        ownerName = ownerName,
        platformName = null,
        coverPath = null,
        trackCount = trackCount
    )

    private fun MusicSelection.label(): String? = when (this) {
        MusicSelection.Launcher -> null
        is MusicSelection.ServerPlaylist -> name
        is MusicSelection.GameSoundtrack -> title
    }
}
