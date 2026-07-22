package com.nendo.argosy.ui.screens.musicbrowser

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.BgmPlaylistEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMMusicFacet
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.music.DownloadMusicTrackUseCase
import com.nendo.argosy.domain.usecase.music.GetLocalGameCoversUseCase
import com.nendo.argosy.domain.usecase.music.GetLocalMusicTrackStateUseCase
import com.nendo.argosy.domain.usecase.music.LocalMusicTrackState
import com.nendo.argosy.domain.usecase.music.MusicTrackLookup
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.BgmPlaylistCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject
import kotlin.math.roundToInt

private const val PAGE_SIZE = 50
private const val FACET_LIMIT = 200
private const val PREFETCH_THRESHOLD = 8
private const val SEARCH_DEBOUNCE_MS = 400L
private const val NOTICE_DURATION_MS = 3000L
private const val BGM_MIN_DURATION_SECONDS = 30.0

@OptIn(FlowPreview::class)
@HiltViewModel
class MusicBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romMRepository: RomMRepository,
    private val downloadMusicTrack: DownloadMusicTrackUseCase,
    private val getLocalMusicTrackState: GetLocalMusicTrackStateUseCase,
    private val getLocalGameCovers: GetLocalGameCoversUseCase,
    private val playlistCoordinator: BgmPlaylistCoordinator,
    private val ambientAudioManager: AmbientAudioManager,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicBrowserState())
    val uiState: StateFlow<MusicBrowserState> = _uiState.asStateFlow()

    private val _sfxAssignedEvent = MutableSharedFlow<String>()
    val sfxAssignedEvent: SharedFlow<String> = _sfxAssignedEvent.asSharedFlow()

    private val searchInput = MutableStateFlow("")
    private val playerTeardownScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var previewPlayer: MediaPlayer? = null

    @Volatile
    private var previewToken = 0

    private var loadJob: Job? = null
    private var noticeJob: Job? = null
    private var collectorsStarted = false
    private var silenceHeld = false

    fun open(mode: MusicBrowserMode) {
        stopPreview()
        acquireSilence()
        _uiState.update { st ->
            MusicBrowserState(
                mode = mode,
                playlistPaths = st.playlistPaths,
                playlistFileIds = st.playlistFileIds,
                playlistPathByFileId = st.playlistPathByFileId
            )
        }
        searchInput.value = ""
        startCollectors()
        loadPage(reset = true)
    }

    private fun startCollectors() {
        if (collectorsStarted) return
        collectorsStarted = true
        viewModelScope.launch {
            searchInput
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { loadPage(reset = true) }
        }
        viewModelScope.launch {
            playlistCoordinator.entries.collect { rows ->
                val fileRows = rows.filter { it.entryType != BgmPlaylistEntity.TYPE_FOLDER && it.enabled }
                _uiState.update { st ->
                    st.copy(
                        playlistPaths = fileRows.map { it.filePath }.toSet(),
                        playlistFileIds = fileRows.mapNotNull { it.gameFileId }.toSet(),
                        playlistPathByFileId = fileRows.mapNotNull { row ->
                            row.gameFileId?.let { it to row.filePath }
                        }.toMap()
                    )
                }
            }
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchInput.value = query
    }

    fun focusSearch() {
        _uiState.update { it.copy(focusedIndex = -1, showKeyboard = true) }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { st ->
            st.copy(
                focusedIndex = index.coerceIn(-1, (st.tracks.size - 1).coerceAtLeast(-1)),
                showKeyboard = false
            )
        }
    }

    fun moveFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { st ->
            val next = (st.focusedIndex + delta).coerceIn(-1, st.tracks.size - 1)
            moved = next != st.focusedIndex
            st.copy(focusedIndex = next, showKeyboard = next == -1)
        }
        maybePrefetch()
        return moved
    }

    fun jumpGroup(delta: Int): Boolean {
        var moved = false
        _uiState.update { st ->
            if (st.groups.isEmpty()) return@update st
            val target = (st.groupIndexOf(st.focusedIndex) + delta).coerceIn(0, st.groups.size - 1)
            moved = st.groups[target].startIndex != st.focusedIndex
            st.copy(focusedIndex = st.groups[target].startIndex, showKeyboard = false)
        }
        maybePrefetch()
        return moved
    }

    private fun maybePrefetch() {
        val st = _uiState.value
        if (st.focusedIndex >= st.tracks.size - PREFETCH_THRESHOLD) loadPage(reset = false)
    }

    fun onListEndApproached() = loadPage(reset = false)

    fun retry() = loadPage(reset = true)

    private fun loadPage(reset: Boolean) {
        val st = _uiState.value
        if (!reset && (st.isLoading || st.isLoadingMore || !st.hasMore || st.isUnsupported || st.isOffline)) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            if (!romMRepository.isConnected()) {
                _uiState.update {
                    it.copy(
                        isOffline = true,
                        isLoading = false,
                        isLoadingMore = false,
                        tracks = if (reset) emptyList() else it.tracks,
                        groups = if (reset) emptyList() else it.groups
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    isOffline = false,
                    errorMessage = null,
                    isLoading = reset,
                    isLoadingMore = !reset,
                    tracks = if (reset) emptyList() else it.tracks,
                    groups = if (reset) emptyList() else it.groups,
                    total = if (reset) 0 else it.total,
                    focusedIndex = if (reset) -1 else it.focusedIndex
                )
            }
            val current = _uiState.value
            val (minDuration, maxDuration) = durationParams(current)
            val params = romMRepository.buildMusicQueryParams(
                search = current.searchQuery.trim().takeIf { it.isNotEmpty() },
                artist = current.artistFilter,
                album = current.albumFilter,
                genre = current.genreFilter,
                minDuration = minDuration,
                maxDuration = maxDuration,
                orderBy = "album",
                limit = PAGE_SIZE,
                offset = if (reset) 0 else current.tracks.size
            )
            when (val result = romMRepository.getMusicTracks(params)) {
                is RomMResult.Success -> {
                    val newTracks = result.data.items.map { it.toUi() }
                    val localStates = getLocalMusicTrackState(newTracks.map { it.toLookup() })
                    val knownCovers = if (reset) emptyMap() else _uiState.value.coversByRomId
                    val unknownRomIds = newTracks.map { it.romId }.toSet() - knownCovers.keys
                    val newCovers = if (unknownRomIds.isEmpty()) emptyMap() else getLocalGameCovers(unknownRomIds)
                    _uiState.update { state ->
                        val merged = if (reset) newTracks else state.tracks + newTracks
                        val covers = knownCovers + newCovers
                        val (flat, groups) = regroup(merged, covers)
                        val focusedId = if (reset) null else state.tracks.getOrNull(state.focusedIndex)?.romFileId
                        state.copy(
                            tracks = flat,
                            groups = groups,
                            coversByRomId = covers,
                            total = result.data.total,
                            localByRomFileId = if (reset) localStates else state.localByRomFileId + localStates,
                            isLoading = false,
                            isLoadingMore = false,
                            isUnsupported = false,
                            focusedIndex = when {
                                reset -> if (flat.isEmpty()) -1 else 0
                                focusedId == null -> state.focusedIndex
                                else -> flat.indexOfFirst { it.romFileId == focusedId }
                                    .takeIf { it >= 0 } ?: state.focusedIndex
                            }
                        )
                    }
                }
                is RomMResult.Error -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isUnsupported = result.code == 404,
                        errorMessage = if (result.code == 404) null else result.message
                    )
                }
            }
        }
    }

    private fun regroup(
        tracks: List<MusicTrackUi>,
        covers: Map<Long, String>
    ): Pair<List<MusicTrackUi>, List<GameGroup>> {
        val byRomId = LinkedHashMap<Long, MutableList<MusicTrackUi>>()
        for (track in tracks) byRomId.getOrPut(track.romId) { mutableListOf() }.add(track)
        val flat = ArrayList<MusicTrackUi>(tracks.size)
        val groups = ArrayList<GameGroup>(byRomId.size)
        for ((romId, groupTracks) in byRomId) {
            val sorted = groupTracks.sortedWith(
                compareBy(
                    { it.disc ?: Int.MAX_VALUE },
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.title }
                )
            )
            val first = sorted.first()
            groups.add(
                GameGroup(
                    romId = romId,
                    gameName = first.gameName,
                    platformName = first.platformName,
                    coverPath = covers[romId],
                    startIndex = flat.size,
                    tracks = sorted
                )
            )
            flat.addAll(sorted)
        }
        return flat to groups
    }

    private fun durationParams(st: MusicBrowserState): Pair<Double?, Double?> =
        when (st.mode) {
            MusicBrowserMode.BGM -> BGM_MIN_DURATION_SECONDS to null
            MusicBrowserMode.SFX -> null to st.sfxMaxDuration.toDouble()
        }

    fun adjustSfxMaxDuration(delta: Int) {
        val st = _uiState.value
        if (st.mode != MusicBrowserMode.SFX) return
        val next = (st.sfxMaxDuration + delta)
            .coerceIn(SFX_DURATION_MIN_SECONDS, SFX_DURATION_MAX_SECONDS)
        if (next == st.sfxMaxDuration) return
        _uiState.update { it.copy(sfxMaxDuration = next) }
        loadPage(reset = true)
    }

    fun openFacetChooser() {
        val st = _uiState.value
        if (st.isUnsupported || st.isOffline || st.facetPicker != null) return
        val options = buildList {
            add("Artist")
            add("Album")
            add("Genre")
            if (st.mode == MusicBrowserMode.SFX) add(DURATION_FILTER_OPTION)
            if (st.hasActiveFilters) add("Clear Filters")
        }
        _uiState.update {
            it.copy(facetPicker = FacetPickerUi(FacetPickerStage.CHOOSER, title = "Filter By", options = options))
        }
    }

    fun openFacetValues(facet: RomMMusicFacet) {
        _uiState.update {
            it.copy(
                facetPicker = FacetPickerUi(
                    stage = FacetPickerStage.VALUES,
                    facet = facet,
                    title = facetTitle(facet),
                    isLoading = true
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val st = _uiState.value
            val (minDuration, maxDuration) = durationParams(st)
            val params = romMRepository.buildMusicQueryParams(
                minDuration = minDuration,
                maxDuration = maxDuration,
                limit = FACET_LIMIT
            )
            when (val result = romMRepository.getMusicFacet(facet, params)) {
                is RomMResult.Success -> {
                    val values = result.data.items.map { it.label }
                    val selected = currentFilterFor(facet)
                    _uiState.update { state ->
                        val picker = state.facetPicker
                        if (picker?.facet != facet) state
                        else state.copy(
                            facetPicker = picker.copy(
                                options = listOf(allOptionFor(facet)) + values,
                                isLoading = false,
                                focusIndex = selected?.let { (values.indexOf(it) + 1).coerceAtLeast(0) } ?: 0
                            )
                        )
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update { it.copy(facetPicker = null) }
                    postNotice("Could not load filter options")
                }
            }
        }
    }

    fun moveFacetFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { st ->
            val picker = st.facetPicker ?: return@update st
            if (picker.options.isEmpty()) return@update st
            val next = (picker.focusIndex + delta).coerceIn(0, picker.options.size - 1)
            moved = next != picker.focusIndex
            st.copy(facetPicker = picker.copy(focusIndex = next))
        }
        return moved
    }

    fun confirmFacetSelection(index: Int? = null) {
        val st = _uiState.value
        val picker = st.facetPicker ?: return
        if (picker.isLoading) return
        val idx = index ?: picker.focusIndex
        when (picker.stage) {
            FacetPickerStage.CHOOSER -> when (picker.options.getOrNull(idx)) {
                "Artist" -> openFacetValues(RomMMusicFacet.ARTISTS)
                "Album" -> openFacetValues(RomMMusicFacet.ALBUMS)
                "Genre" -> openFacetValues(RomMMusicFacet.GENRES)
                DURATION_FILTER_OPTION -> {}
                "Clear Filters" -> clearFilters()
                else -> {}
            }
            FacetPickerStage.VALUES -> {
                val facet = picker.facet ?: return
                val option = picker.options.getOrNull(idx) ?: return
                val newValue = if (idx == 0) null else option
                _uiState.update { state ->
                    when (facet) {
                        RomMMusicFacet.ARTISTS -> state.copy(artistFilter = newValue, facetPicker = null)
                        RomMMusicFacet.ALBUMS -> state.copy(albumFilter = newValue, facetPicker = null)
                        RomMMusicFacet.GENRES -> state.copy(genreFilter = newValue, facetPicker = null)
                    }
                }
                loadPage(reset = true)
            }
        }
    }

    fun dismissFacetPicker() {
        _uiState.update { it.copy(facetPicker = null) }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(artistFilter = null, albumFilter = null, genreFilter = null, facetPicker = null)
        }
        loadPage(reset = true)
    }

    private fun currentFilterFor(facet: RomMMusicFacet): String? = when (facet) {
        RomMMusicFacet.ARTISTS -> _uiState.value.artistFilter
        RomMMusicFacet.ALBUMS -> _uiState.value.albumFilter
        RomMMusicFacet.GENRES -> _uiState.value.genreFilter
    }

    private fun facetTitle(facet: RomMMusicFacet): String = when (facet) {
        RomMMusicFacet.ARTISTS -> "Artist"
        RomMMusicFacet.ALBUMS -> "Album"
        RomMMusicFacet.GENRES -> "Genre"
    }

    private fun allOptionFor(facet: RomMMusicFacet): String = when (facet) {
        RomMMusicFacet.ARTISTS -> "All Artists"
        RomMMusicFacet.ALBUMS -> "All Albums"
        RomMMusicFacet.GENRES -> "All Genres"
    }

    fun togglePreview(index: Int? = null) {
        val st = _uiState.value
        val track = st.tracks.getOrNull(index ?: st.focusedIndex) ?: return
        if (st.previewingId == track.romFileId) {
            stopPreview()
        } else {
            startPreview(track)
        }
    }

    private fun startPreview(track: MusicTrackUi) {
        previewToken++
        val token = previewToken
        releasePlayer()
        _uiState.update { it.copy(previewingId = track.romFileId) }
        viewModelScope.launch(Dispatchers.IO) {
            val local = _uiState.value.localByRomFileId[track.romFileId]
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (local != null) {
                    player.setDataSource(local.localPath)
                } else {
                    val authToken = preferencesRepository.userPreferences.first().rommToken
                    val headers = authToken?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
                    val url = romMRepository.buildMediaUrlPublic(track.streamUrl)
                    player.setDataSource(context, Uri.parse(url), headers)
                }
                player.setOnPreparedListener { prepared ->
                    if (previewToken == token) {
                        val volume = ambientAudioManager.currentVolume
                        runCatching { prepared.setVolume(volume, volume) }
                        prepared.start()
                    } else {
                        runCatching { prepared.release() }
                    }
                }
                player.setOnCompletionListener { onPreviewEnded(track.romFileId) }
                player.setOnErrorListener { _, _, _ ->
                    onPreviewEnded(track.romFileId)
                    true
                }
                if (previewToken != token) {
                    runCatching { player.release() }
                    return@launch
                }
                previewPlayer = player
                player.prepareAsync()
            } catch (_: Exception) {
                runCatching { player.release() }
                onPreviewEnded(track.romFileId)
                postNotice("Preview failed")
            }
        }
    }

    private fun onPreviewEnded(romFileId: Long) {
        val wasCurrent = _uiState.value.previewingId == romFileId
        if (!wasCurrent) return
        _uiState.update { it.copy(previewingId = null) }
        releasePlayer()
    }

    fun stopPreview() {
        previewToken++
        val hadPreview = _uiState.value.previewingId != null || previewPlayer != null
        if (!hadPreview) return
        _uiState.update { it.copy(previewingId = null) }
        releasePlayer()
    }

    fun onBrowserClosed() {
        stopPreview()
        releaseSilence()
    }

    private fun acquireSilence() {
        if (silenceHeld) return
        silenceHeld = true
        ambientAudioManager.holdSilence()
    }

    private fun releaseSilence() {
        if (!silenceHeld) return
        silenceHeld = false
        ambientAudioManager.releaseSilence()
    }

    private fun releasePlayer() {
        val player = previewPlayer ?: return
        previewPlayer = null
        playerTeardownScope.launch {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    fun confirmRow(index: Int? = null) {
        val st = _uiState.value
        val track = st.tracks.getOrNull(index ?: st.focusedIndex) ?: return
        if (track.romFileId in st.downloadingIds) return
        when (st.mode) {
            MusicBrowserMode.BGM -> if (st.isInPlaylist(track)) removeFromPlaylist(track) else assignBgm(track)
            MusicBrowserMode.SFX -> assignSfx(track)
        }
    }

    private fun removeFromPlaylist(track: MusicTrackUi) {
        val st = _uiState.value
        val local = st.localByRomFileId[track.romFileId] ?: return
        val path = if (local.localPath in st.playlistPaths) {
            local.localPath
        } else {
            local.gameFileId?.let { st.playlistPathByFileId[it] } ?: local.localPath
        }
        viewModelScope.launch(Dispatchers.IO) {
            playlistCoordinator.removeOrDisable(path)
            postNotice("Removed from playlist")
        }
    }

    private fun assignBgm(track: MusicTrackUi) {
        stopPreview()
        viewModelScope.launch(Dispatchers.IO) {
            ensureDownloaded(track).onSuccess { local ->
                playlistCoordinator.add(local.localPath, track.title, local.gameFileId)
                postNotice("Added to playlist")
            }
        }
    }

    private fun assignSfx(track: MusicTrackUi) {
        stopPreview()
        viewModelScope.launch(Dispatchers.IO) {
            ensureDownloaded(track).onSuccess { local ->
                _sfxAssignedEvent.emit(local.localPath)
            }
        }
    }

    private suspend fun ensureDownloaded(track: MusicTrackUi): Result<LocalMusicTrackState> {
        _uiState.value.localByRomFileId[track.romFileId]?.let { return Result.success(it) }
        _uiState.update { it.copy(downloadingIds = it.downloadingIds + track.romFileId) }
        val result = downloadMusicTrack(
            rommFileId = track.romFileId,
            fileName = track.fileName,
            platformName = track.platformName,
            gameName = track.gameName,
            trackNumber = track.trackNumber,
            title = track.trackTitle
        )
        return result.fold(
            onSuccess = { file ->
                val local = getLocalMusicTrackState(listOf(track.toLookup()))[track.romFileId]
                    ?: LocalMusicTrackState(gameFileId = null, localPath = file.absolutePath)
                _uiState.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.romFileId,
                        localByRomFileId = it.localByRomFileId + (track.romFileId to local)
                    )
                }
                Result.success(local)
            },
            onFailure = { error ->
                _uiState.update { it.copy(downloadingIds = it.downloadingIds - track.romFileId) }
                postNotice("Download failed")
                Result.failure(error)
            }
        )
    }

    private fun postNotice(message: String) {
        noticeJob?.cancel()
        _uiState.update { it.copy(notice = message) }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_DURATION_MS)
            _uiState.update { it.copy(notice = null) }
        }
    }

    private fun RomMMusicTrack.toUi(): MusicTrackUi {
        val decodedName = runCatching {
            URLDecoder.decode(streamUrl.substringAfterLast('/'), "UTF-8")
        }.getOrElse { streamUrl.substringAfterLast('/') }
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        val artistAlbum = listOfNotNull(
            artist?.takeIf { it.isNotBlank() },
            album?.takeIf { it.isNotBlank() }
        ).joinToString(" - ").takeIf { it.isNotEmpty() }
        return MusicTrackUi(
            romFileId = romFileId,
            romId = romId,
            title = cleanTitle ?: decodedName,
            artistAlbum = artistAlbum,
            durationLabel = durationSeconds?.let { formatDuration(it) },
            fileName = decodedName,
            streamUrl = streamUrl,
            platformName = platformName,
            gameName = gameName?.takeIf { it.isNotBlank() } ?: "Unknown Game",
            trackNumber = track,
            disc = disc,
            trackTitle = cleanTitle
        )
    }

    private fun formatDuration(seconds: Double): String {
        val total = seconds.roundToInt().coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    override fun onCleared() {
        previewToken++
        releasePlayer()
        releaseSilence()
    }

    private fun MusicTrackUi.toLookup() = MusicTrackLookup(
        romFileId = romFileId,
        platformName = platformName,
        gameName = gameName,
        trackNumber = trackNumber,
        trackTitle = trackTitle,
        fileName = fileName
    )
}
