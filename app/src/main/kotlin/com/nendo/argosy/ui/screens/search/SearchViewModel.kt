package com.nendo.argosy.ui.screens.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.common.toHomeGameUi
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.util.GridUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class SearchResultUi {
    abstract val key: String
    abstract val title: String

    data class Game(
        override val key: String,
        override val title: String,
        val gameId: Long,
        val card: HomeGameUi
    ) : SearchResultUi()

    data class Media(
        override val key: String,
        override val title: String,
        val itemId: String,
        val libraryName: String?,
        val posterUrl: String,
        @StringRes val kindLabelRes: Int,
        val releaseYear: Int?
    ) : SearchResultUi()
}

/**
 * What a press on a result opens. Named rather than inferred at the call site so the screen cannot
 * send a media item to game detail by reading the wrong half of a row.
 */
sealed class SearchSelection {
    data class OpenGame(val gameId: Long) : SearchSelection()
    data class OpenMedia(val itemId: String) : SearchSelection()
}

data class SearchUiState(
    val query: String = "",
    val gameResults: List<SearchResultUi.Game> = emptyList(),
    val mediaResults: List<SearchResultUi.Media> = emptyList(),
    val isSearching: Boolean = false,
    val mediaSearchable: Boolean = false,
    val focusedIndex: Int = -1,
    val showKeyboard: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val recentGames: List<SearchResultUi.Game> = emptyList(),
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val screenWidthDp: Int = 0,
    val previewFocused: Boolean = false,
    val previewIndex: Int = 0,
    val dualScreen: Boolean = false,
    val historyFocused: Boolean = false,
    val historyIndex: Int = 0
) {
    val isFieldFocused: Boolean get() = focusedIndex == SearchViewModel.SEARCH_FIELD_INDEX

    val resultCount: Int get() = gameResults.size + mediaResults.size

    val hasResults: Boolean get() = gameResults.isNotEmpty() || mediaResults.isNotEmpty()

    val hasBothKinds: Boolean get() = gameResults.isNotEmpty() && mediaResults.isNotEmpty()

    val isLanding: Boolean get() = query.length < SearchViewModel.MIN_QUERY_LENGTH

    val hasLandingContent: Boolean get() = recentSearches.isNotEmpty() || recentGames.isNotEmpty()

    val gameColumns: Int get() = GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    val gridSpacingDp: Int get() = GridUtils.getGridSpacingDp(gridDensity)

    val previewGames: List<SearchResultUi.Game> get() = gameResults.take(SearchViewModel.PREVIEW_LIMIT)

    val presentationColumns: Int get() = GridUtils.getPresentationGameColumns(gridDensity)

    val coverGames: List<SearchResultUi.Game>
        get() = if (isLanding) recentGames.filter { it.title.contains(query.trim(), ignoreCase = true) } else gameResults

    val historyEntries: List<String>
        get() = recentSearches.filter { it.contains(query.trim(), ignoreCase = true) }

    internal val focusRegions: List<FocusRegion>
        get() = if (isLanding) {
            listOf(FocusRegion(recentSearches.size, 1), FocusRegion(recentGames.size, recentGames.size))
        } else {
            listOf(FocusRegion(gameResults.size, gameColumns), FocusRegion(mediaResults.size, 1))
        }

    internal val presentationRegions: List<FocusRegion>
        get() = listOf(FocusRegion(coverGames.size, presentationColumns))
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val downloadFileStatusRepository: DownloadFileStatusRepository,
    private val gameNavigationContext: GameNavigationContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val platformNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val libraryNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private var searchJob: Job? = null
    private var openedWithQuery = false

    init {
        observeNameLookups()
        observeLanding()
    }

    /**
     * Platform and library names are read once and held, not fetched per result. Resolving them a row
     * at a time is a database round trip per hit on every keystroke, which is the difference between
     * a search that keeps up with typing and one that does not.
     */
    private fun observeNameLookups() {
        viewModelScope.launch {
            platformRepository.observeAllPlatforms().collect { platforms ->
                platformNames.value = platforms.associate { it.id to it.name }
            }
        }
        viewModelScope.launch {
            mediaRepository.isSignedIn.collect { signedIn ->
                _uiState.update { it.copy(mediaSearchable = signedIn) }
                if (!signedIn) clearMediaResults()
            }
        }
        viewModelScope.launch {
            mediaRepository.observeLibraries().collect { libraries ->
                libraryNames.value = libraries.associate { it.libraryId to it.name }
            }
        }
    }

    private fun observeLanding() {
        viewModelScope.launch {
            preferencesRepository.preferences
                .map { it.libraryRecentSearches to it.gridDensity }
                .distinctUntilChanged()
                .collect { (searches, density) ->
                    _uiState.update {
                        it.copy(recentSearches = searches, gridDensity = density).withAboveKeyboardClamped()
                    }
                }
        }
        viewModelScope.launch {
            gameRepository.observeRecentlyPlayed(RECENT_GAMES_LIMIT).collect { games ->
                val cards = withContext(Dispatchers.IO) { games.toGameResults() }
                _uiState.update { it.copy(recentGames = cards).withAboveKeyboardClamped() }
            }
        }
    }

    fun updateScreenWidth(widthDp: Int) {
        _uiState.update { if (it.screenWidthDp == widthDp) it else it.copy(screenWidthDp = widthDp) }
    }

    fun setDualScreen(enabled: Boolean) {
        _uiState.update { state ->
            if (state.dualScreen == enabled) return@update state
            state.copy(
                dualScreen = enabled,
                previewFocused = false,
                previewIndex = 0,
                historyFocused = false,
                historyIndex = 0,
                focusedIndex = if (enabled) SEARCH_FIELD_INDEX else state.focusedIndex
            )
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query).withAboveKeyboardClamped() }
        performSearch(query)
    }

    /**
     * Runs [query] the first time the screen opens with one, the way a recent search does. A
     * screen rebuilt for a configuration change keeps whatever the user has typed since.
     */
    fun openWithQuery(query: String) {
        if (openedWithQuery) return
        openedWithQuery = true
        selectRecentSearch(query)
    }

    fun selectRecentSearch(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                focusedIndex = SEARCH_FIELD_INDEX,
                showKeyboard = it.dualScreen,
                previewFocused = false,
                historyFocused = false
            )
        }
        performSearch(query)
    }

    fun submitQuery() {
        rememberQuery()
        if (!_uiState.value.dualScreen) {
            closeKeyboard()
            return
        }
        _uiState.update { state ->
            if (state.coverGames.isEmpty()) state else state.copy(previewFocused = true, previewIndex = 0, historyFocused = false)
        }
    }

    /**
     * Both sides are asked at once and each is capped on its own, so neither can crowd the other out
     * of the answer. Concatenating an unbounded game query with a capped media one would put a title
     * behind however many games happened to match, which is the same as not finding it.
     */
    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update {
                it.copy(
                    gameResults = emptyList(),
                    mediaResults = emptyList(),
                    isSearching = false,
                    focusedIndex = -1,
                    previewFocused = false,
                    previewIndex = 0
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(DEBOUNCE_MS)

            val mediaEnabled = _uiState.value.mediaSearchable
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    val games = async { searchGames(query) }
                    val media = async { if (mediaEnabled) searchMedia(query) else emptyList() }
                    games.await() to media.await()
                }
            }

            _uiState.update { state ->
                val noResults = results.first.isEmpty() && results.second.isEmpty()
                val next = state.copy(
                    gameResults = results.first,
                    mediaResults = results.second,
                    isSearching = false,
                    focusedIndex = if (state.showKeyboard || state.dualScreen || noResults) -1 else 0
                ).withAboveKeyboardClamped()
                if (next.previewFocused) next else next.copy(previewIndex = 0)
            }
        }
    }

    private suspend fun searchGames(query: String): List<SearchResultUi.Game> =
        gameRepository.search(query).first().take(RESULTS_PER_KIND).toGameResults()

    private suspend fun List<GameEntity>.toGameResults(): List<SearchResultUi.Game> {
        val names = platformNames.value
        val art = gameRepository.getArt(map { it.id })
        return map { game ->
            SearchResultUi.Game(
                key = "game:${game.id}",
                title = game.title,
                gameId = game.id,
                card = game.toHomeGameUi(
                    downloadStatus = downloadFileStatusRepository,
                    art = art[game.id],
                    firstScreenshotUrl = null,
                    platformDisplayName = names[game.platformId]
                )
            )
        }
    }

    private suspend fun searchMedia(query: String): List<SearchResultUi.Media> {
        val names = libraryNames.value
        return mediaRepository.search(query, RESULTS_PER_KIND)
            .map { it.toSearchResult(names[it.libraryId]) }
    }

    private fun clearMediaResults() {
        _uiState.update { state ->
            if (state.mediaResults.isEmpty()) return@update state
            state.copy(
                mediaResults = emptyList(),
                focusedIndex = state.focusedIndex.coerceAtMost(state.gameResults.lastIndex)
            ).withAboveKeyboardClamped()
        }
    }

    private fun targetOf(move: FocusMove): FocusTarget {
        val state = _uiState.value
        if (state.isFieldFocused) {
            val hasStops = state.focusRegions.any { it.size > 0 }
            return if (move == FocusMove.DOWN && hasStops) FocusTarget.Stop(0) else FocusTarget.Boundary
        }
        return moveAcross(state.focusRegions, state.focusedIndex, move)
    }

    private fun applyTarget(target: FocusTarget): Boolean = when (target) {
        is FocusTarget.Stop -> {
            _uiState.update { it.copy(focusedIndex = target.index, showKeyboard = false) }
            true
        }
        FocusTarget.Field -> {
            _uiState.update { it.copy(focusedIndex = SEARCH_FIELD_INDEX) }
            true
        }
        FocusTarget.Boundary -> false
    }

    /**
     * Moves to the head of the other group. With many games matching, the titles among the results sit
     * below all of them, and paging to the heading is the only way to reach them that does not cost a
     * scroll through everything else.
     */
    fun jumpToGroup(forward: Boolean): Boolean {
        val state = _uiState.value
        if (state.dualScreen || !state.hasBothKinds) return false
        val inGames = state.focusedIndex < state.gameResults.size
        if (forward == !inGames) return false
        val target = if (forward) state.gameResults.size else 0
        _uiState.update { it.copy(focusedIndex = target, showKeyboard = false) }
        return true
    }

    /**
     * Moves to whichever group is not the focused one, for a tap on the paging hint. A tap has no
     * direction to it, so it toggles where the shoulder buttons page.
     */
    fun toggleGroup() {
        val state = _uiState.value
        if (!state.hasBothKinds) return
        jumpToGroup(forward = state.focusedIndex < state.gameResults.size)
    }

    fun focusKeyboard() {
        _uiState.update {
            it.copy(showKeyboard = true, focusedIndex = SEARCH_FIELD_INDEX, previewFocused = false, historyFocused = false)
        }
    }

    fun closeKeyboard() {
        _uiState.update { state ->
            state.copy(
                showKeyboard = false,
                previewFocused = false,
                historyFocused = false,
                focusedIndex = if (state.hasResults && !state.dualScreen) 0 else state.focusedIndex
            )
        }
    }

    /**
     * Takes focus from the keyboard into what sits above it. On one screen that is the result row.
     * On two it is the history list, or the presentation grid when no history matches. False when
     * that holds nothing, which leaves the keyboard to handle the press itself.
     */
    fun enterPreview(): Boolean {
        var entered = false
        _uiState.update { state ->
            val next = state.enteringAboveKeyboard()
            entered = next != null
            next ?: state
        }
        return entered
    }

    private fun exitPreview() {
        _uiState.update { it.returningToKeyboard() }
    }

    private fun moveAboveKeyboard(move: FocusMove): InputResult {
        var moved = false
        _uiState.update { state ->
            val next = state.aboveKeyboardStep(move)
            moved = next != null
            next ?: state
        }
        return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun openCoverAt(index: Int, onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val covers = state.coverGames
        val game = covers.getOrNull(index) ?: return
        if (!state.isLanding) rememberQuery()
        _uiState.update { it.copy(previewFocused = true, previewIndex = index, historyFocused = false) }
        openGame(game.gameId, covers, onGameSelect)
    }

    fun selectHistoryAt(index: Int) {
        val entry = _uiState.value.historyEntries.getOrNull(index) ?: return
        selectRecentSearch(entry)
    }

    private fun movePreview(delta: Int) {
        _uiState.update { state ->
            val count = state.previewGames.size
            if (count == 0) state else state.copy(previewIndex = (state.previewIndex + delta).mod(count))
        }
    }

    fun openPreviewAt(index: Int, onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.previewGames.getOrNull(index) ?: return
        rememberQuery()
        _uiState.update {
            it.copy(previewFocused = false, previewIndex = index, showKeyboard = false, focusedIndex = index)
        }
        openGame(game.gameId, state.gameResults, onGameSelect)
    }

    fun selectionAt(index: Int): SearchSelection? = when (val result = resultAt(index)) {
        is SearchResultUi.Game -> SearchSelection.OpenGame(result.gameId)
        is SearchResultUi.Media -> SearchSelection.OpenMedia(result.itemId)
        null -> null
    }

    private fun resultAt(index: Int): SearchResultUi? {
        val state = _uiState.value
        if (index < 0) return null
        return state.gameResults.getOrNull(index)
            ?: state.mediaResults.getOrNull(index - state.gameResults.size)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                gameResults = emptyList(),
                mediaResults = emptyList(),
                isSearching = false,
                focusedIndex = SEARCH_FIELD_INDEX,
                showKeyboard = true,
                previewFocused = false,
                previewIndex = 0,
                historyFocused = false
            )
        }
    }

    private fun rememberQuery() {
        val query = _uiState.value.query
        if (query.length < MIN_QUERY_LENGTH) return
        viewModelScope.launch { preferencesRepository.addLibraryRecentSearch(query) }
    }

    private fun MediaItemEntity.toSearchResult(libraryName: String?) = SearchResultUi.Media(
        key = "media:$itemId",
        title = name,
        itemId = itemId,
        libraryName = libraryName,
        posterUrl = mediaRepository.posterUrl(itemId, primaryImageTag),
        kindLabelRes = if (MediaItemType.fromWire(itemType) == MediaItemType.SERIES) {
            R.string.library_search_result_kind_series
        } else {
            R.string.library_search_result_kind_movie
        },
        releaseYear = productionYear
    )

    private fun openGame(gameId: Long, group: List<SearchResultUi.Game>, onGameSelect: (Long) -> Unit) {
        gameNavigationContext.setContext(group.map { it.gameId })
        onGameSelect(gameId)
    }

    private fun activate(index: Int, onGameSelect: (Long) -> Unit, onMediaSelect: (String) -> Unit) {
        val state = _uiState.value
        if (state.isLanding) {
            val search = state.recentSearches.getOrNull(index)
            if (search != null) {
                selectRecentSearch(search)
                return
            }
            val game = state.recentGames.getOrNull(index - state.recentSearches.size) ?: return
            openGame(game.gameId, state.recentGames, onGameSelect)
            return
        }
        when (val selection = selectionAt(index)) {
            is SearchSelection.OpenGame -> {
                rememberQuery()
                openGame(selection.gameId, state.gameResults, onGameSelect)
            }
            is SearchSelection.OpenMedia -> {
                rememberQuery()
                onMediaSelect(selection.itemId)
            }
            null -> Unit
        }
    }

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onMediaSelect: (String) -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = moveVertically(FocusMove.UP)

        override fun onDown(): InputResult = moveVertically(FocusMove.DOWN)

        private fun moveVertically(move: FocusMove): InputResult {
            val moved = if (_uiState.value.dualScreen) enterPreview() else applyTarget(targetOf(move))
            return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onLeft(): InputResult {
            if (_uiState.value.isFieldFocused) return InputResult.UNHANDLED
            return if (applyTarget(targetOf(FocusMove.LEFT))) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            if (_uiState.value.isFieldFocused) return InputResult.UNHANDLED
            return if (applyTarget(targetOf(FocusMove.RIGHT))) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onPrevSection(): InputResult {
            return if (jumpToGroup(forward = false)) {
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onNextSection(): InputResult {
            return if (jumpToGroup(forward = true)) {
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onConfirm(): InputResult {
            if (_uiState.value.isFieldFocused) {
                focusKeyboard()
                return InputResult.HANDLED
            }
            activate(_uiState.value.focusedIndex, onGameSelect, onMediaSelect)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            if (state.query.isNotEmpty()) {
                clearSearch()
            } else {
                onBack()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult = InputResult.UNHANDLED
    }

    /**
     * Drives the result row above the open keyboard. It sits over the keyboard's own handler and
     * claims every button; Down or Back returns focus to the keyboard.
     */
    fun createPreviewInputHandler(onGameSelect: (Long) -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = InputResult.handled(SoundType.BOUNDARY)

        override fun onDown(): InputResult {
            exitPreview()
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult {
            movePreview(-1)
            return InputResult.HANDLED
        }

        override fun onRight(): InputResult {
            movePreview(1)
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            openPreviewAt(_uiState.value.previewIndex, onGameSelect)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            exitPreview()
            return InputResult.handled(SoundType.BACK)
        }

        override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onContextMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSecondaryAction(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onPrevSection(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onPrevTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSelect(): InputResult = InputResult.handled(SoundType.SILENT)
    }

    /**
     * Drives the history list and the presentation grid while two-screen focus sits on either. It
     * sits over the keyboard's own handler and claims every button. Back returns focus to the
     * keyboard.
     */
    fun createDualScreenInputHandler(onGameSelect: (Long) -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = moveAboveKeyboard(FocusMove.UP)
        override fun onDown(): InputResult = moveAboveKeyboard(FocusMove.DOWN)
        override fun onLeft(): InputResult = moveAboveKeyboard(FocusMove.LEFT)
        override fun onRight(): InputResult = moveAboveKeyboard(FocusMove.RIGHT)

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            if (state.historyFocused) selectHistoryAt(state.historyIndex) else openCoverAt(state.previewIndex, onGameSelect)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            exitPreview()
            return InputResult.handled(SoundType.BACK)
        }

        override fun onPrevSection(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)

        override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onContextMenu(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSecondaryAction(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onPrevTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onNextTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
        override fun onSelect(): InputResult = InputResult.handled(SoundType.SILENT)
    }

    fun openAt(
        index: Int,
        onGameSelect: (Long) -> Unit,
        onMediaSelect: (String) -> Unit
    ) {
        _uiState.update { it.copy(focusedIndex = index, showKeyboard = false, previewFocused = false) }
        activate(index, onGameSelect, onMediaSelect)
    }

    companion object {
        const val SEARCH_FIELD_INDEX = -1
        const val MIN_QUERY_LENGTH = 2
        const val PREVIEW_LIMIT = 12
        private const val RECENT_GAMES_LIMIT = 20
        private const val DEBOUNCE_MS = 300L

        /**
         * How many of each kind a search answers with. The cap is per kind rather than shared so a
         * library of thousands of games cannot spend the whole answer before media is reached.
         */
        private const val RESULTS_PER_KIND = 40
    }
}
