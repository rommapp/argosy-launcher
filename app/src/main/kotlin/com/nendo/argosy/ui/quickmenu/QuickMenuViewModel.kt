package com.nendo.argosy.ui.quickmenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.screens.common.DownloadIndicatorSource
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickMenuViewModel @Inject constructor(
    private val loader: QuickMenuLoader,
    private val gameActions: GameActionsDelegate,
    downloadIndicatorSource: DownloadIndicatorSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickMenuUiState())
    val uiState: StateFlow<QuickMenuUiState> = _uiState.asStateFlow()

    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> = downloadIndicatorSource.indicators

    private var hasOpened = false
    private var loadJob: Job? = null
    private var rollJob: Job? = null

    init {
        observeExtras()
        observeSearchResults()
        observePoolSize()
    }

    private fun observeExtras() {
        viewModelScope.launch {
            _uiState
                .map { state -> if (state.isVisible) state.selectedOrb to state.focusedGame()?.id else null }
                .distinctUntilChanged()
                .collectLatest { key ->
                    val (section, gameId) = key ?: return@collectLatest
                    if (gameId == null) return@collectLatest
                    val extras = loader.extras(section, gameId) ?: return@collectLatest
                    _uiState.update { it.copy(extras = extras) }
                }
        }
    }

    private fun observeSearchResults() {
        viewModelScope.launch {
            _uiState
                .map { state ->
                    if (state.isVisible && state.selectedOrb == QuickMenuOrb.SEARCH) state.focusedSearch?.query else null
                }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query == null) {
                        _uiState.update { it.copy(searchResults = null) }
                        return@collectLatest
                    }
                    _uiState.update { it.copy(searchResults = SearchResultsPreview(query, null)) }
                    val games = loader.searchResults(query)
                    _uiState.update { state ->
                        if (state.searchResults?.query != query) state
                        else state.copy(searchResults = SearchResultsPreview(query, games))
                    }
                }
        }
    }

    private fun observePoolSize() {
        viewModelScope.launch {
            _uiState
                .map { state -> if (state.isVisible) state.random.filters else null }
                .distinctUntilChanged()
                .collectLatest { filters ->
                    if (filters == null) return@collectLatest
                    val size = loader.poolSize(filters)
                    _uiState.update { state ->
                        if (state.random.filters != filters) state
                        else state.copy(random = state.random.copy(poolSize = size))
                    }
                }
        }
    }

    fun show() {
        val firstOpen = !hasOpened
        hasOpened = true
        _uiState.update { it.opened(firstOpen) }
        loadAll(firstOpen)
    }

    fun hide() {
        val dismissed = _uiState.value.topNew.dismissedIds
        _uiState.update { it.copy(isVisible = false, picker = null, actionsMenu = null) }
        if (dismissed.isNotEmpty()) {
            viewModelScope.launch { loader.saveNotInterested(dismissed) }
        }
    }

    fun setFormFactor(formFactor: QuickMenuFormFactor) {
        _uiState.update { if (it.formFactor == formFactor) it else it.copy(formFactor = formFactor) }
    }

    fun enterTouchMode() {
        _uiState.update { if (it.isTouchMode) it else it.copy(isTouchMode = true) }
    }

    fun exitTouchMode() {
        _uiState.update { if (it.isTouchMode) it.copy(isTouchMode = false) else it }
    }

    fun selectOrb(orb: QuickMenuOrb) {
        _uiState.update { if (it.selectedOrb == orb) it else it.entering(orb) }
    }

    fun moveSection(delta: Int) {
        _uiState.update { it.movingSection(delta) }
    }

    /**
     * Moves the cursor inside the selected section. False when the press goes nowhere.
     */
    fun step(direction: FocusStep): Boolean {
        var moved = false
        _uiState.update { state ->
            val next = state.stepped(direction)
            moved = next != null
            next ?: state
        }
        return moved
    }

    fun focusAt(index: Int) {
        _uiState.update { state ->
            val orb = state.selectedOrb
            val size = if (orb == QuickMenuOrb.SEARCH) state.search.rowCount + 1 else state.gamesOf(orb).size
            if (index !in 0 until size) state else state.withFocusIndex(orb, index)
        }
    }

    fun focusHeader() {
        _uiState.update { state ->
            if (!state.selectedOrb.hasHeaderControl) state else state.withHeaderFocus(state.selectedOrb)
        }
    }

    fun focusRandom(stop: RandomStop) {
        _uiState.update { state ->
            if (!state.random.isEnabled(stop)) state else state.copy(random = state.random.focusing(stop))
        }
    }

    fun rollAgain() {
        if (!_uiState.value.random.isEnabled(RandomStop.ROLL)) return
        _uiState.update { it.copy(random = it.random.focusing(RandomStop.ROLL)) }
        roll()
    }

    fun pickRandom(index: Int) {
        _uiState.update { it.copy(random = it.random.picking(index)) }
    }

    fun focusRandomHistory(index: Int) {
        _uiState.update { it.copy(random = it.random.focusingHistory(index)) }
    }

    fun cycleRandomPlatform(delta: Int) {
        applyPool { it.cyclingPlatform(delta) }
    }

    fun selectRandomPlatform(index: Int) {
        applyPool { it.withPlatformIndex(index) }
    }

    fun setRandomInstalledOnly(on: Boolean): Boolean {
        if (_uiState.value.random.installedOnly == on) return false
        applyPool { it.withInstalledOnly(on) }
        return true
    }

    fun toggleRandomInstalledOnly() {
        applyPool { it.withInstalledOnly(!it.installedOnly) }
    }

    private fun applyPool(transform: (RandomState) -> RandomState) {
        _uiState.update { it.copy(random = transform(it.random).copy(isPoolEmpty = false)) }
        if (!_uiState.value.random.pickFitsPool) roll()
    }

    private fun roll() {
        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            val random = _uiState.value.random
            val game = loader.roll(random.filters, excludeGameId = random.current?.id)
            _uiState.update { state ->
                if (state.random.filters != random.filters) state else state.copy(random = state.random.withRoll(game))
            }
        }
    }

    fun cycleMostPlayedRange(delta: Int) {
        val ranges = MostPlayedRange.entries
        selectMostPlayedRange(ranges[(_uiState.value.mostPlayed.range.ordinal + delta).mod(ranges.size)])
    }

    fun selectMostPlayedRange(range: MostPlayedRange) {
        if (_uiState.value.mostPlayed.range == range) return
        _uiState.update { it.copy(mostPlayed = it.mostPlayed.copy(range = range)) }
        viewModelScope.launch { loadMostPlayed(range) }
    }

    fun cycleFavoritesSort(delta: Int) {
        _uiState.update { state ->
            state.replacingSection(QuickMenuOrb.FAVORITES) { it.copy(favorites = it.favorites.cyclingSort(delta)) }
        }
    }

    fun selectFavoritesSort(sort: FavoritesSort) {
        _uiState.update { state ->
            state.replacingSection(QuickMenuOrb.FAVORITES) { it.copy(favorites = it.favorites.sortedBy(sort)) }
        }
    }

    fun openPicker(picker: QuickMenuPicker) {
        _uiState.update { it.copy(picker = picker) }
    }

    fun closePicker() {
        _uiState.update { it.copy(picker = null) }
    }

    fun toggleNotInterested(gameId: Long) {
        _uiState.update { it.copy(topNew = it.topNew.togglingDismissal(gameId)) }
    }

    /**
     * Toggles [gameId]'s favorite flag. In Favorites the tile stays in place and is marked removed,
     * so a second toggle restores it.
     */
    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            val isFavorite = gameActions.toggleFavorite(gameId) ?: return@launch
            _uiState.update { it.applyingFavorite(gameId, isFavorite) }
        }
    }

    fun openActions(game: QuickMenuGameUi) {
        _uiState.update { state ->
            state.copy(actionsMenu = GameActionsMenuState(game, state.selectedOrb, gameActionsFor(state.selectedOrb)))
        }
    }

    fun moveActionsFocus(delta: Int) {
        _uiState.update { state ->
            val menu = state.actionsMenu ?: return@update state
            state.copy(actionsMenu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(menu.actions.size)))
        }
    }

    fun closeActions() {
        _uiState.update { it.copy(actionsMenu = null) }
    }

    private fun QuickMenuUiState.applyingFavorite(gameId: Long, isFavorite: Boolean): QuickMenuUiState {
        val flip: (QuickMenuGameUi) -> QuickMenuGameUi = { if (it.id == gameId) it.copy(isFavorite = isFavorite) else it }
        return copy(
            random = random.replacingGame(gameId, flip),
            mostPlayed = mostPlayed.copy(rows = mostPlayed.rows.map { it.copy(game = flip(it.game)) }),
            topNew = topNew.copy(tiles = topNew.tiles.map { it.copy(game = flip(it.game)) }),
            recent = recent.copy(rows = recent.rows.map { it.copy(game = flip(it.game)) }),
            favorites = favorites.markingFavorite(gameId, isFavorite).copy(games = favorites.games.map(flip)),
            search = search.copy(recentlyAdded = search.recentlyAdded.map(flip)),
            actionsMenu = actionsMenu?.let { menu -> menu.copy(game = flip(menu.game)) }
        )
    }

    private fun loadAll(firstOpen: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            launch {
                val density = loader.gridDensity()
                _uiState.update { it.copy(gridDensity = density) }
            }
            launch { loadRecent() }
            launch { loadMostPlayed(_uiState.value.mostPlayed.range) }
            launch { loadTopNew() }
            launch { loadFavorites() }
            launch { loadSearchTab() }
            launch { loadRandom(firstOpen) }
        }
    }

    private suspend fun loadRecent() {
        val rows = loader.recent()
        _uiState.update { state ->
            state.replacingSection(QuickMenuOrb.RECENT) { it.copy(recent = RecentState(rows = rows, isLoaded = true)) }
        }
    }

    private suspend fun loadMostPlayed(range: MostPlayedRange) {
        val rows = loader.mostPlayed(range)
        _uiState.update { state ->
            if (state.mostPlayed.range != range) return@update state
            state.replacingSection(QuickMenuOrb.MOST_PLAYED) {
                it.copy(mostPlayed = MostPlayedState(range = range, rows = rows, isLoaded = true))
            }
        }
    }

    private suspend fun loadTopNew() {
        val tiles = loader.topNew()
        _uiState.update { state ->
            state.replacingSection(QuickMenuOrb.TOP_UNPLAYED) { it.copy(topNew = TopNewState(tiles = tiles, isLoaded = true)) }
        }
    }

    private suspend fun loadFavorites() {
        val sort = _uiState.value.favorites.sort
        val games = loader.favorites(sort)
        _uiState.update { state ->
            state.replacingSection(QuickMenuOrb.FAVORITES) {
                it.copy(favorites = FavoritesState(games = sortFavorites(games, it.favorites.sort), sort = it.favorites.sort, isLoaded = true))
            }
        }
    }

    private suspend fun loadSearchTab() {
        val search = loader.searchTab()
        _uiState.update { state ->
            val index = state.focusIndexOf(QuickMenuOrb.SEARCH).coerceIn(SEARCH_FIELD_INDEX, search.rowCount)
            state.copy(search = search).withFocusIndex(QuickMenuOrb.SEARCH, index)
        }
    }

    private suspend fun loadRandom(firstOpen: Boolean) {
        val platforms = loader.randomPlatforms()
        val installedOnly = if (firstOpen) loader.startsInstalledOnly() else null
        _uiState.update { state ->
            val withPlatforms = state.random.withPlatforms(platforms)
            state.copy(random = installedOnly?.let { withPlatforms.withInstalledOnly(it) } ?: withPlatforms)
        }
        if (_uiState.value.random.current != null) return
        val filters = _uiState.value.random.filters
        val game = loader.roll(filters, excludeGameId = null)
        _uiState.update { state ->
            if (state.random.filters != filters) return@update state
            val rolled = state.random.withRoll(game)
            val landed = if (state.random.focus == RandomStop.PLATFORM && !state.random.isEnabled(RandomStop.PLAY)) rolled.entered() else rolled
            state.copy(random = landed)
        }
    }
}
