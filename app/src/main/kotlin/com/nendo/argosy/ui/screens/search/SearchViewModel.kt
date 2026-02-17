package com.nendo.argosy.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.navigation.GameNavigationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchResultUi(
    val id: Long,
    val title: String,
    val platformName: String,
    val coverPath: String?,
    val developer: String?,
    val releaseYear: Int?
)

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResultUi> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val focusedIndex: Int = -1,
    val showKeyboard: Boolean = true
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val gameNavigationContext: GameNavigationContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        performSearch(query)
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.length < 2) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(300)

            val games = gameRepository.search(query).first()
            val results = games.map { game ->
                val platform = platformRepository.getById(game.platformId)
                game.toSearchResult(platform?.name ?: "Unknown")
            }

            _uiState.update { state ->
                state.copy(
                    results = results,
                    isSearching = false,
                    focusedIndex = if (results.isNotEmpty()) 0 else -1
                )
            }
        }
    }

    fun moveFocus(delta: Int) {
        _uiState.update { state ->
            if (state.results.isEmpty()) return@update state
            val newIndex = (state.focusedIndex + delta).coerceIn(0, state.results.size - 1)
            state.copy(focusedIndex = newIndex, showKeyboard = false)
        }
    }

    fun focusKeyboard() {
        _uiState.update { it.copy(showKeyboard = true, focusedIndex = -1) }
    }

    fun getSelectedGameId(): Long? {
        val state = _uiState.value
        return if (state.focusedIndex >= 0) {
            state.results.getOrNull(state.focusedIndex)?.id
        } else null
    }

    fun clearSearch() {
        _uiState.update { SearchUiState() }
    }

    private fun GameEntity.toSearchResult(platformName: String) = SearchResultUi(
        id = id,
        title = title,
        platformName = platformName,
        coverPath = coverPath,
        developer = developer,
        releaseYear = releaseYear
    )

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            if (state.focusedIndex == 0) {
                focusKeyboard()
            } else {
                moveFocus(-1)
            }
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            moveFocus(1)
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            getSelectedGameId()?.let { gameId ->
                gameNavigationContext.setContext(_uiState.value.results.map { it.id })
                onGameSelect(gameId)
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
}
