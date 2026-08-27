package com.nendo.argosy.ui.screens.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.navigation.GameNavigationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * One row of results. A game and a media title are separate members rather than one row with nullable
 * halves because they answer to different screens and describe themselves with different facts: a
 * game by its platform and cover, a title by its library and poster. Modelled the way home's
 * [com.nendo.argosy.ui.components.CarouselItem] models the same pairing, so the two places that hold
 * both kinds hold them the same way.
 */
sealed class SearchResultUi {
    abstract val key: String
    abstract val title: String

    data class Game(
        override val key: String,
        override val title: String,
        val gameId: Long,
        val platformName: String?,
        val coverPath: String?,
        val developer: String?,
        val releaseYear: Int?
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

/**
 * The two groups are held apart rather than concatenated. Focus runs through them in order -- games,
 * then media -- so [focusedIndex] past the end of [gameResults] is a media row.
 *
 * [mediaSearchable] is false with no media account signed in, which is what keeps the screen from
 * offering an empty Media heading to someone who has no media.
 */
data class SearchUiState(
    val query: String = "",
    val gameResults: List<SearchResultUi.Game> = emptyList(),
    val mediaResults: List<SearchResultUi.Media> = emptyList(),
    val isSearching: Boolean = false,
    val mediaSearchable: Boolean = false,
    val focusedIndex: Int = -1,
    val showKeyboard: Boolean = true
) {
    val resultCount: Int get() = gameResults.size + mediaResults.size

    val hasResults: Boolean get() = gameResults.isNotEmpty() || mediaResults.isNotEmpty()

    val hasBothKinds: Boolean get() = gameResults.isNotEmpty() && mediaResults.isNotEmpty()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val mediaRepository: MediaRepository,
    private val gameNavigationContext: GameNavigationContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val platformNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val libraryNames = MutableStateFlow<Map<String, String>>(emptyMap())

    private var searchJob: Job? = null

    init {
        observeNameLookups()
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

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch(query)
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
                    focusedIndex = -1
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
                state.copy(
                    gameResults = results.first,
                    mediaResults = results.second,
                    isSearching = false,
                    focusedIndex = if (results.first.isEmpty() && results.second.isEmpty()) -1 else 0
                )
            }
        }
    }

    private suspend fun searchGames(query: String): List<SearchResultUi.Game> {
        val names = platformNames.value
        return gameRepository.search(query).first()
            .take(RESULTS_PER_KIND)
            .map { it.toSearchResult(names[it.platformId]) }
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
            )
        }
    }

    fun moveFocus(delta: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            if (!state.hasResults) return@update state
            val newIndex = (state.focusedIndex + delta).coerceIn(0, state.resultCount - 1)
            moved = newIndex != state.focusedIndex
            state.copy(focusedIndex = newIndex, showKeyboard = false)
        }
        return moved
    }

    /**
     * Moves to the head of the other group. With many games matching, the titles among the results sit
     * below all of them, and paging to the heading is the only way to reach them that does not cost a
     * scroll through everything else.
     */
    fun jumpToGroup(forward: Boolean): Boolean {
        val state = _uiState.value
        if (!state.hasBothKinds) return false
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
        _uiState.update { it.copy(showKeyboard = true, focusedIndex = -1) }
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
        _uiState.update {
            SearchUiState(mediaSearchable = it.mediaSearchable)
        }
    }

    private fun GameEntity.toSearchResult(platformName: String?) = SearchResultUi.Game(
        key = "game:$id",
        title = title,
        gameId = id,
        platformName = platformName,
        coverPath = coverPath,
        developer = developer,
        releaseYear = releaseYear
    )

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

    /**
     * The context is the game results alone. It drives the next/previous game gesture on the detail
     * screen, and a media item is not somewhere that gesture can land.
     */
    private fun setGameContext() {
        gameNavigationContext.setContext(_uiState.value.gameResults.map { it.gameId })
    }

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onMediaSelect: (String) -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            if (state.focusedIndex == 0) {
                focusKeyboard()
                return InputResult.HANDLED
            }
            return if (moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onDown(): InputResult {
            return if (moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

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
            when (val selection = selectionAt(_uiState.value.focusedIndex)) {
                is SearchSelection.OpenGame -> {
                    setGameContext()
                    onGameSelect(selection.gameId)
                }
                is SearchSelection.OpenMedia -> onMediaSelect(selection.itemId)
                null -> Unit
            }
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
     * Opens a result by its position in the focus order, for a tap. The focus index follows the press
     * so the controller carries on from where the finger left off rather than from where it was.
     */
    fun openAt(
        index: Int,
        onGameSelect: (Long) -> Unit,
        onMediaSelect: (String) -> Unit
    ) {
        _uiState.update { it.copy(focusedIndex = index, showKeyboard = false) }
        when (val selection = selectionAt(index)) {
            is SearchSelection.OpenGame -> {
                setGameContext()
                onGameSelect(selection.gameId)
            }
            is SearchSelection.OpenMedia -> onMediaSelect(selection.itemId)
            null -> Unit
        }
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val DEBOUNCE_MS = 300L

        /**
         * How many of each kind a search answers with. The cap is per kind rather than shared so a
         * library of thousands of games cannot spend the whole answer before media is reached.
         */
        private const val RESULTS_PER_KIND = 40
    }
}
