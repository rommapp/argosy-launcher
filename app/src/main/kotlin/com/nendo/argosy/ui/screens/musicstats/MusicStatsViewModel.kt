package com.nendo.argosy.ui.screens.musicstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.data.music.stats.MusicStatsRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicContextTagKind
import com.nendo.argosy.domain.model.MusicMonthTotals
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.music.MusicStatsCalendar
import com.nendo.argosy.ui.screens.music.MusicPlayback
import com.nendo.argosy.ui.screens.music.musicGuarded
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

private const val TAG = "MusicStatsViewModel"
private const val SOURCE_PREFIX = "music_stats:"
private const val TOP_TRACKS_SHOWN = 12
private const val TRACK_POOL = 100
private const val TOP_ALBUMS = 12
private const val TOP_ARTISTS = 12
private const val FLASHBACK_PER_YEAR = 10
private const val PAIRING_PER_GROUP = 10
private const val PAIRING_GROUPS = 12

private class MonthData(
    val totals: MusicMonthTotals,
    val pool: List<MusicStatTrack>,
    val shelves: List<MusicStatsShelf>
)

@HiltViewModel
class MusicStatsViewModel @Inject constructor(
    private val stats: MusicStatsRepository,
    private val library: MusicLibrary,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val playback: MusicPlayback
) : ViewModel() {

    private val today = LocalDate.now()
    private val currentYearMonth = MusicStatsCalendar.yearMonthOf(today)

    private val _uiState = MutableStateFlow(
        MusicStatsUiState(yearMonth = currentYearMonth, currentYearMonth = currentYearMonth)
    )
    val uiState: StateFlow<MusicStatsUiState> = _uiState.asStateFlow()

    private val _playbackStarted = Channel<Unit>(Channel.CONFLATED)
    val playbackStarted: Flow<Unit> = _playbackStarted.receiveAsFlow()

    private val catalogLock = Mutex()
    private val catalog = mutableMapOf<String, MusicAlbum>()
    private val monthPool = MutableStateFlow<List<MusicStatTrack>>(emptyList())
    private var monthJob: Job? = null

    init {
        loadMonth(currentYearMonth)
        loadFlashback()
        loadGames()
    }

    fun retry() {
        when (_uiState.value.tab) {
            MusicStatsTab.MONTH -> loadMonth(_uiState.value.yearMonth)
            MusicStatsTab.FLASHBACK -> loadFlashback()
            MusicStatsTab.GAMES -> loadGames()
        }
    }

    private fun loadMonth(yearMonth: Int) {
        monthJob?.cancel()
        _uiState.update { it.copy(month = it.month.copy(isLoading = true, loadFailed = false)) }
        monthJob = viewModelScope.launch {
            val range = MusicStatsCalendar.dayRange(yearMonth)
            val loaded = musicGuarded(TAG, "month") {
                coroutineScope {
                    val totals = async { stats.monthTotals(yearMonth) }
                    val tracks = async { stats.topTracks(range.first, range.last, TRACK_POOL) }
                    val albums = async { stats.topAlbums(range.first, range.last, TOP_ALBUMS) }
                    val artists = async { stats.topArtists(range.first, range.last, TOP_ARTISTS) }
                    val pool = tracks.await()
                    MonthData(
                        totals = totals.await(),
                        pool = pool,
                        shelves = MusicStatsShelves.monthShelves(pool.take(TOP_TRACKS_SHOWN), albums.await(), artists.await())
                    )
                }
            }
            if (_uiState.value.yearMonth != yearMonth) return@launch
            monthPool.value = loaded?.pool.orEmpty()
            _uiState.update { state ->
                if (state.yearMonth != yearMonth) return@update state
                val page = if (loaded == null) {
                    state.month.copy(isLoading = false, loadFailed = true)
                } else {
                    MusicStatsPage(shelves = loaded.shelves, isLoading = false)
                }
                state.copy(totals = loaded?.totals, month = page).clampedFocus()
            }
            if (loaded != null) attachArt(MusicStatsTab.MONTH)
        }
    }

    private fun loadFlashback() {
        _uiState.update { it.copy(flashback = it.flashback.copy(isLoading = true, loadFailed = false)) }
        viewModelScope.launch {
            val shelves = musicGuarded(TAG, "flashback") {
                coroutineScope {
                    val day = async { stats.flashbackOnThisDay(MusicStatsCalendar.monthDayOf(today), FLASHBACK_PER_YEAR) }
                    val month = async { stats.flashbackMonth(today.monthValue, FLASHBACK_PER_YEAR) }
                    MusicStatsShelves.flashbackShelves(day.await(), month.await(), today.monthValue, today.year)
                }
            }
            _uiState.update { state ->
                val page = shelves?.let { MusicStatsPage(shelves = it, isLoading = false) }
                    ?: state.flashback.copy(isLoading = false, loadFailed = true)
                state.copy(flashback = page).clampedFocus()
            }
            if (shelves != null) attachArt(MusicStatsTab.FLASHBACK)
        }
    }

    private fun loadGames() {
        _uiState.update { it.copy(games = it.games.copy(isLoading = true, loadFailed = false)) }
        viewModelScope.launch {
            val shelves = musicGuarded(TAG, "pairings") {
                coroutineScope {
                    val byGame = async { stats.pairingsByGame(PAIRING_PER_GROUP, PAIRING_GROUPS) }
                    val byPlatform = async { stats.pairingsByPlatform(PAIRING_PER_GROUP, PAIRING_GROUPS) }
                    val byGenre = async { stats.pairingsByTag(MusicContextTagKind.GENRE, PAIRING_PER_GROUP, PAIRING_GROUPS) }
                    val byFranchise = async {
                        stats.pairingsByTag(MusicContextTagKind.FRANCHISE, PAIRING_PER_GROUP, PAIRING_GROUPS)
                    }
                    val games = byGame.await()
                    val platforms = byPlatform.await()
                    val gameIds = stats.gameIdsFor(games.map { it.groupKey })
                    val artByGame = gameRepository.getArt(gameIds.values.toSet())
                    val covers = gameIds.mapNotNull { (key, id) -> artByGame[id]?.coverPath?.let { key to it } }.toMap()
                    val platformNames = if (platforms.isEmpty()) {
                        emptyMap()
                    } else {
                        platformRepository.getAllPlatforms().associate { it.slug to it.name }
                    }
                    MusicStatsShelves.pairingShelves(MusicStatsPairingKind.GAME, games, covers = covers) +
                        MusicStatsShelves.pairingShelves(MusicStatsPairingKind.PLATFORM, platforms, labels = platformNames) +
                        MusicStatsShelves.pairingShelves(MusicStatsPairingKind.GENRE, byGenre.await()) +
                        MusicStatsShelves.pairingShelves(MusicStatsPairingKind.FRANCHISE, byFranchise.await())
                }
            }
            _uiState.update { state ->
                val page = shelves?.let { MusicStatsPage(shelves = it, isLoading = false) }
                    ?: state.games.copy(isLoading = false, loadFailed = true)
                state.copy(games = page).clampedFocus()
            }
            if (shelves != null) attachArt(MusicStatsTab.GAMES)
        }
    }

    private suspend fun attachArt(tab: MusicStatsTab) {
        val keys = MusicStatsShelves.albumKeysOf(pageOf(_uiState.value, tab).shelves)
        val missing = catalogLock.withLock { keys - catalog.keys }
        if (missing.isNotEmpty()) {
            val found = musicGuarded(TAG, "album art") { library.albumsForKeys(missing) } ?: return
            catalogLock.withLock { catalog.putAll(found) }
        }
        val known = catalogLock.withLock { catalog.toMap() }
        _uiState.update { state ->
            val page = pageOf(state, tab)
            val shelves = MusicStatsShelves.withCatalog(page.shelves, known)
            when (tab) {
                MusicStatsTab.MONTH -> state.copy(month = page.copy(shelves = shelves))
                MusicStatsTab.FLASHBACK -> state.copy(flashback = page.copy(shelves = shelves))
                MusicStatsTab.GAMES -> state.copy(games = page.copy(shelves = shelves))
            }
        }
    }

    private fun pageOf(state: MusicStatsUiState, tab: MusicStatsTab): MusicStatsPage = when (tab) {
        MusicStatsTab.MONTH -> state.month
        MusicStatsTab.FLASHBACK -> state.flashback
        MusicStatsTab.GAMES -> state.games
    }

    fun cycleTab(delta: Int) {
        val tabs = MusicStatsTab.entries
        selectTab(tabs[(_uiState.value.tab.ordinal + delta).mod(tabs.size)])
    }

    fun selectTab(tab: MusicStatsTab) {
        _uiState.update { it.copy(tab = tab, shelfIndex = 0, itemIndex = 0, options = null) }
    }

    fun stepMonth(delta: Int): Boolean {
        val state = _uiState.value
        if (delta > 0 && !state.canStepForward) return false
        val target = MusicStatsCalendar.step(state.yearMonth, delta)
        _uiState.update { it.copy(yearMonth = target, totals = null) }
        loadMonth(target)
        return true
    }

    fun moveShelf(delta: Int): Boolean {
        val state = _uiState.value
        val target = state.shelfIndex + delta
        if (target !in state.page.shelves.indices) return false
        _uiState.update { it.copy(shelfIndex = target, itemIndex = 0) }
        return true
    }

    fun moveItem(delta: Int): Boolean {
        val shelf = _uiState.value.focusedShelf ?: return false
        if (shelf.kind == MusicStatsShelfKind.MONTH) return stepMonth(delta)
        if (shelf.itemCount < 2) return false
        _uiState.update { it.copy(itemIndex = (it.itemIndex + delta).mod(shelf.itemCount)) }
        return true
    }

    fun focus(shelfIndex: Int, itemIndex: Int) {
        _uiState.update { state ->
            val shelf = state.page.shelves.getOrNull(shelfIndex) ?: return@update state
            state.copy(shelfIndex = shelfIndex, itemIndex = itemIndex.coerceIn(0, (shelf.itemCount - 1).coerceAtLeast(0)))
        }
    }

    fun playFocusedItem(): Boolean {
        val state = _uiState.value
        val shelf = state.focusedShelf ?: return false
        if (shelf.kind == MusicStatsShelfKind.MONTH || shelf.itemCount == 0) return false
        play(shelf, state.itemIndex, startAtItem = true, shuffle = false)
        return true
    }

    fun playFocusedShelf(): Boolean {
        val state = _uiState.value
        if (!state.canPlayFocused) return false
        val shelf = state.focusedShelf ?: return false
        play(shelf, state.itemIndex, startAtItem = false, shuffle = false)
        return true
    }

    fun tapItem(shelfIndex: Int, itemIndex: Int) {
        focus(shelfIndex, itemIndex)
        playFocusedItem()
    }

    fun openOptions(): Boolean {
        val state = _uiState.value
        if (!state.canPlayFocused) return false
        val shelf = state.focusedShelf ?: return false
        _uiState.update { it.copy(options = MusicStatsOptionsState(shelf.key, state.itemIndex)) }
        return true
    }

    fun openOptionsAt(shelfIndex: Int, itemIndex: Int) {
        focus(shelfIndex, itemIndex)
        openOptions()
    }

    fun moveOptionsFocus(delta: Int) = _uiState.update { state ->
        val options = state.options ?: return@update state
        state.copy(options = options.copy(focusIndex = (options.focusIndex + delta).mod(options.actions.size)))
    }

    fun focusOption(index: Int) = _uiState.update { state ->
        val options = state.options ?: return@update state
        state.copy(options = options.copy(focusIndex = index.coerceIn(0, options.actions.lastIndex)))
    }

    fun confirmOption() {
        val state = _uiState.value
        val options = state.options ?: return
        val shelf = state.optionsShelf
        dismissOptions()
        if (shelf == null) return
        val shuffle = options.actions.getOrNull(options.focusIndex) == MusicStatsOptionAction.SHUFFLE
        play(shelf, options.itemIndex, startAtItem = false, shuffle = shuffle)
    }

    fun dismissOptions() = _uiState.update { it.copy(options = null) }

    private fun play(shelf: MusicStatsShelf, itemIndex: Int, startAtItem: Boolean, shuffle: Boolean) {
        val pool = monthPool.value
        viewModelScope.launch {
            val started = when (shelf.kind) {
                MusicStatsShelfKind.MONTH -> playback.playStats(sourceIdOf(shelf.key), null, pool, 0, shuffle)
                MusicStatsShelfKind.TRACKS -> playback.playStats(
                    sourceId = sourceIdOf(shelf.key),
                    label = (shelf.title as? MusicStatsShelfTitle.Pairing)?.label,
                    stats = shelf.tracks.map { it.stat },
                    startIndex = if (startAtItem) itemIndex else 0,
                    shuffle = shuffle
                )
                MusicStatsShelfKind.ALBUMS -> shelf.albums.getOrNull(itemIndex)?.let { playAlbum(it, pool, shuffle) } == true
                MusicStatsShelfKind.ARTISTS -> shelf.artists.getOrNull(itemIndex)?.let { artist ->
                    playback.playStats(
                        sourceId = sourceIdOf(artist.artistKey),
                        label = artist.name,
                        stats = MusicStatsShelves.artistTracks(pool, artist.artistKey),
                        startIndex = 0,
                        shuffle = shuffle
                    )
                } == true
            }
            if (started) _playbackStarted.trySend(Unit)
        }
    }

    private suspend fun playAlbum(album: MusicStatsAlbumUi, pool: List<MusicStatTrack>, shuffle: Boolean): Boolean {
        val catalogAlbum = album.album
        val label = catalogAlbum?.title ?: album.stat.title
        val tracks = catalogAlbum?.let { musicGuarded(TAG, "album tracks") { library.albumTracks(it) } }
        if (!tracks.isNullOrEmpty()) {
            return playback.play(album.stat.albumKey, label, if (shuffle) tracks.shuffled() else tracks, 0)
        }
        return playback.playStats(album.stat.albumKey, label, MusicStatsShelves.albumTracks(pool, album.stat.albumKey), 0, shuffle)
    }

    private fun sourceIdOf(key: String): String = "$SOURCE_PREFIX$key"

    private fun MusicStatsUiState.clampedFocus(): MusicStatsUiState {
        val shelves = page.shelves
        if (shelves.isEmpty()) return copy(shelfIndex = 0, itemIndex = 0)
        val shelfIndex = shelfIndex.coerceIn(0, shelves.lastIndex)
        val count = shelves[shelfIndex].itemCount
        return copy(shelfIndex = shelfIndex, itemIndex = itemIndex.coerceIn(0, (count - 1).coerceAtLeast(0)))
    }
}
