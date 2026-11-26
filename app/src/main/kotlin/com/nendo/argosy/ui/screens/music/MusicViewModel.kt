package com.nendo.argosy.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.data.preferences.MusicScreenPreferencesRepository
import com.nendo.argosy.data.preferences.QuickNavigationSource
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.ui.audio.AmbientAudioManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "MusicViewModel"
private const val JUMP_LABEL_HOLD_MS = 600L

private data class MusicScreenSelection(val grouping: MusicGrouping, val filter: MusicSourceFilter)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val library: MusicLibrary,
    playlists: MusicPlaylists,
    private val preferences: MusicScreenPreferencesRepository,
    audio: AmbientAudioManager,
    playback: MusicPlayback,
    notificationManager: NotificationManager,
    quickNavigation: QuickNavigationSource
) : ViewModel() {

    val quickNavigationEnabled: StateFlow<Boolean> = quickNavigation.enabled

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _playbackStarted = Channel<Unit>(Channel.CONFLATED)
    val playbackStarted: Flow<Unit> = _playbackStarted.receiveAsFlow()

    private val resolver = MusicTrackResolver(library)
    private val selection = MutableStateFlow<MusicScreenSelection?>(null)
    private val reloads = MutableStateFlow(0)
    private var contentsJob: Job? = null
    private var albumTracksJob: Job? = null
    private var stackJob: Job? = null
    private var jumpLabelJob: Job? = null
    private var playingTrackId: String? = null

    val playlistActions = MusicPlaylistActionsDelegate(
        playlists = playlists,
        notifications = notificationManager,
        resolver = resolver,
        scope = viewModelScope,
        state = _uiState,
        onOpenForReorder = { column, group ->
            if (_uiState.value.focusedColumn?.key == column.key) openContents(moveGroup = group)
        },
        onReloadContents = ::reloadContents
    )

    val actions = MusicActionsDelegate(
        library = library,
        playback = playback,
        notifications = notificationManager,
        resolver = resolver,
        scope = viewModelScope,
        state = _uiState,
        playlistActions = playlistActions,
        onPlaybackStarted = { _playbackStarted.trySend(Unit) }
    )

    init {
        viewModelScope.launch {
            val stored = preferences.preferences.first()
            selection.compareAndSet(null, MusicScreenSelection(stored.grouping, stored.sourceFilter))
        }
        observeLibrary(playlists)
        viewModelScope.launch {
            playlists.changes.collect { playlistActions.onPlaylistsChanged() }
        }
        viewModelScope.launch {
            audio.playback
                .map { playback ->
                    val hasQueue = playback.tracks.isNotEmpty()
                    hasQueue to MusicPlayingUi(
                        sourceId = playback.sourceId.takeIf { hasQueue },
                        trackId = playback.tracks.getOrNull(playback.index)?.id,
                        isPlaying = playback.isPlaying
                    )
                }
                .distinctUntilChanged()
                .collect { (hasQueue, playing) -> onPlaying(hasQueue, playing) }
        }
    }

    private fun onPlaying(hasQueue: Boolean, playing: MusicPlayingUi) {
        val previous = playingTrackId
        playingTrackId = playing.trackId
        _uiState.update { state ->
            state.copy(
                hasQueue = hasQueue,
                playing = playing,
                contents = state.contents?.followingPlaying(
                    previousId = previous,
                    playingId = playing.trackId,
                    listFocused = state.options == null && state.playlistDialog == null
                )
            )
        }
    }

    private fun observeLibrary(playlists: MusicPlaylists) {
        viewModelScope.launch {
            combine(
                selection.filterNotNull(),
                library.configuredSources(),
                library.changes,
                playlists.changes,
                reloads
            ) { chosen, sources, _, _, _ -> Triple(chosen.grouping, chosen.filter, sources) }
                .collectLatest { (grouping, filter, sources) ->
                    resolver.clear()
                    _uiState.update { it.copy(sources = sources, grouping = grouping, filter = filter) }
                    if (sources.isEmpty()) {
                        _uiState.update { it.copy(page = null, contents = null, isLoading = false, loadFailed = false) }
                    } else {
                        loadColumns(grouping, filter)
                    }
                }
        }
    }

    private suspend fun loadColumns(grouping: MusicGrouping, filter: MusicSourceFilter) {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val page = musicGuarded(TAG, "columns") {
            withContext(Dispatchers.IO) { library.columns(grouping, filter) }
        }
        _uiState.update { state ->
            if (page == null) {
                state.copy(isLoading = false, loadFailed = state.page == null)
            } else {
                val ordered = MusicColumnsPage(columns = recentFirst(page.columns), offline = page.offline)
                val keptKey = state.focusedColumn?.key
                val index = ordered.columns.indexOfFirst { it.key == keptKey }.coerceAtLeast(0)
                state.copy(page = ordered, position = index, isLoading = false, loadFailed = false)
            }
        }
    }

    private fun recentFirst(columns: List<MusicColumn>): List<MusicColumn> {
        val (recent, rest) = columns.partition { it.kind == MusicColumnKind.RECENT }
        return recent + rest
    }

    fun retry() {
        reloads.update { it + 1 }
    }

    fun setSlotCount(slots: Int) {
        _uiState.update { state ->
            if (state.slotCount == slots) return@update state
            state.copy(slotCount = slots, position = state.focusIndex)
        }
    }

    fun moveColumnFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.columns.size < 2) return false
        if (state.wraps) {
            _uiState.update { it.copy(position = it.position + delta) }
            return true
        }
        val target = state.focusIndex + delta
        if (target !in state.columns.indices) return false
        _uiState.update { it.copy(position = target) }
        return true
    }

    fun jumpSection(delta: Int): Boolean {
        val state = _uiState.value
        if (state.contents != null) return false
        val columns = state.columns
        val target = MusicSections.jumpTarget(columns, state.focusIndex, delta) ?: return false
        var moveBy = target - state.focusIndex
        if (state.wraps) {
            if (delta > 0 && moveBy < 0) moveBy += columns.size
            if (delta < 0 && moveBy > 0) moveBy -= columns.size
        }
        val label = MusicSections.sectionOf(columns[target])
        _uiState.update {
            it.copy(
                position = if (it.wraps) it.position + moveBy else target,
                jumpLabel = label,
                showsJumpLabel = label != null
            )
        }
        jumpLabelJob?.cancel()
        jumpLabelJob = viewModelScope.launch {
            delay(JUMP_LABEL_HOLD_MS)
            _uiState.update { it.copy(showsJumpLabel = false) }
        }
        return true
    }

    fun moveStack(delta: Int): Boolean {
        val state = _uiState.value
        val column = state.focusedColumn ?: return false
        if (!state.canFlip) return false
        val loaded = state.focusedStack
        if (loaded != null) {
            _uiState.update { it.copy(stack = loaded.copy(index = (loaded.index + delta).mod(loaded.albums.size))) }
            return true
        }
        val grouping = state.grouping
        val filter = state.filter
        stackJob?.cancel()
        stackJob = viewModelScope.launch {
            val contents = musicGuarded(TAG, "stack") { resolver.contents(column, grouping, filter) } ?: return@launch
            val albums = stackOrder(column, contents.albums)
            if (albums.size < 2) return@launch
            _uiState.update { s ->
                if (s.focusedColumn?.key != column.key) s
                else s.copy(stack = MusicStackUi(column.key, albums, delta.mod(albums.size)))
            }
        }
        return true
    }

    private fun stackOrder(column: MusicColumn, albums: List<MusicAlbum>): List<MusicAlbum> =
        albums.sortedBy { album ->
            column.sleeveTitles.indexOf(album.title).let { if (it < 0) Int.MAX_VALUE else it }
        }

    fun tapColumn(fromFocus: Int) {
        if (fromFocus == 0) {
            openContents()
            return
        }
        _uiState.update { state ->
            if (state.wraps) {
                state.copy(position = state.position + fromFocus)
            } else {
                state.copy(position = (state.focusIndex + fromFocus).coerceIn(0, state.columns.lastIndex))
            }
        }
    }

    fun cycleGrouping(delta: Int) {
        val current = selection.value ?: return
        val entries = MusicGrouping.entries
        val next = entries[(current.grouping.ordinal + delta).mod(entries.size)]
        _uiState.update {
            it.copy(
                grouping = next,
                page = null,
                position = 0,
                contents = null,
                stack = null,
                isLoading = true,
                loadFailed = false
            )
        }
        selection.value = current.copy(grouping = next)
        viewModelScope.launch { preferences.setGrouping(next) }
    }

    fun confirmFilter() {
        val chosen = actions.dismissFilterWithChoice() ?: return
        val current = selection.value ?: return
        if (current.filter == chosen) return
        _uiState.update { it.copy(filter = chosen) }
        selection.value = current.copy(filter = chosen)
        viewModelScope.launch { preferences.setSourceFilter(chosen) }
    }

    fun playFocusedColumn() {
        val column = _uiState.value.focusedColumn ?: return
        actions.playColumn(column, shuffle = false)
    }

    fun openFocusedColumnOptions() {
        val column = _uiState.value.focusedColumn ?: return
        actions.openColumnOptions(column)
    }

    fun openContents(moveGroup: MusicPlaylistGroup? = null) {
        val state = _uiState.value
        val column = state.focusedColumn ?: return
        val grouping = state.grouping
        val filter = state.filter
        val frontKey = state.focusedStack?.front?.key
        _uiState.update { it.copy(contents = MusicContentsUiState(column = column, moveGroup = moveGroup)) }
        contentsJob?.cancel()
        contentsJob = viewModelScope.launch {
            val contents = musicGuarded(TAG, "contents") { resolver.contents(column, grouping, filter) }
            _uiState.update { s ->
                val current = s.contents?.takeIf { it.column.key == column.key } ?: return@update s
                if (contents == null) {
                    s.copy(contents = current.copy(isLoading = false, loadFailed = true))
                } else {
                    val startAlbum = contents.albums.indexOfFirst { it.key == frontKey }.coerceAtLeast(0)
                    val loaded = current.copy(
                        albums = contents.albums,
                        albumIndex = startAlbum,
                        isLoading = false,
                        loadFailed = false
                    )
                    s.copy(contents = if (loaded.isFlat) loaded.copy(tracks = contents.tracks) else loaded)
                }
            }
            val opened = _uiState.value.contents ?: return@launch
            opened.focusedAlbum?.let { loadAlbumTracks(column, it, contents?.tracks.orEmpty()) }
        }
    }

    private fun loadAlbumTracks(column: MusicColumn, album: MusicAlbum, columnTracks: List<MusicTrack>) {
        _uiState.update { s -> s.copy(contents = s.contents?.copy(tracks = emptyList(), trackIndex = 0, tracksLoading = true)) }
        albumTracksJob?.cancel()
        albumTracksJob = viewModelScope.launch {
            val tracks = musicGuarded(TAG, "album tracks") { resolver.albumTracks(album, columnTracks) }
            _uiState.update { s ->
                val current = s.contents ?: return@update s
                if (current.column.key != column.key || current.focusedAlbum?.key != album.key) return@update s
                s.copy(contents = current.copy(tracks = tracks.orEmpty(), tracksLoading = false))
            }
        }
    }

    fun moveAlbum(delta: Int): Boolean {
        val contents = _uiState.value.contents ?: return false
        if (contents.isFlat || contents.albums.size < 2) return false
        selectAlbum((contents.albumIndex + delta).mod(contents.albums.size))
        return true
    }

    fun selectAlbum(index: Int) {
        val state = _uiState.value
        val contents = state.contents ?: return
        val album = contents.albums.getOrNull(index) ?: return
        if (index == contents.albumIndex && contents.tracks.isNotEmpty()) return
        _uiState.update { s -> s.copy(contents = s.contents?.copy(albumIndex = index)) }
        viewModelScope.launch {
            val columnTracks = musicGuarded(TAG, "contents") {
                resolver.contents(contents.column, state.grouping, state.filter)
            }?.tracks.orEmpty()
            loadAlbumTracks(contents.column, album, columnTracks)
        }
    }

    fun moveTrack(delta: Int): Boolean {
        val contents = _uiState.value.contents ?: return false
        if (contents.tracks.size < 2) return false
        _uiState.update { s ->
            val current = s.contents ?: return@update s
            s.copy(contents = current.copy(trackIndex = (current.trackIndex + delta).mod(current.tracks.size)))
        }
        return true
    }

    fun playTrackAt(index: Int) {
        val contents = _uiState.value.contents ?: return
        if (index !in contents.tracks.indices) return
        _uiState.update { s -> s.copy(contents = s.contents?.copy(trackIndex = index)) }
        val album = contents.focusedAlbum
        actions.playTracks(
            sourceId = album?.key ?: contents.column.key,
            label = album?.title ?: contents.column.title,
            tracks = contents.tracks,
            startIndex = index
        )
    }

    fun playFocusedTrack() {
        val contents = _uiState.value.contents ?: return
        playTrackAt(contents.trackIndex)
    }

    fun playContentsAlbum() {
        val contents = _uiState.value.contents ?: return
        val album = contents.focusedAlbum
        if (album == null) {
            actions.playTracks(contents.column.key, contents.column.title, contents.tracks, 0)
        } else {
            actions.playTracks(album.key, album.title, contents.tracks, 0)
        }
    }

    fun playContentsColumn(): Boolean {
        val contents = _uiState.value.contents ?: return false
        if (!contents.canPlayColumn) return false
        actions.playColumn(contents.column, shuffle = false)
        return true
    }

    fun openFocusedTrackOptions() {
        val contents = _uiState.value.contents ?: return
        if (contents.isMoving) return
        val track = contents.focusedTrack ?: return
        actions.openTrackOptions(track, contents.column, contents.trackIndex)
    }

    fun openTrackOptionsAt(index: Int) {
        _uiState.update { s -> s.copy(contents = s.contents?.copy(trackIndex = index)) }
        openFocusedTrackOptions()
    }

    fun closeContents() {
        contentsJob?.cancel()
        albumTracksJob?.cancel()
        _uiState.update { it.copy(contents = null) }
    }

    fun retryContents() {
        if (_uiState.value.contents == null) return
        openContents()
    }

    private fun reloadContents() {
        viewModelScope.launch {
            resolver.clear()
            retryContents()
        }
    }
}
