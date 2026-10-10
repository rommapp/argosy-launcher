package com.nendo.argosy.ui.quickmenu

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.SearchCandidate
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.usecase.quickmenu.GetTopUnplayedUseCase
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.needsAndroidInstall
import com.nendo.argosy.ui.common.resolveDownloaded
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.LibrarySyncBus
import com.nendo.argosy.util.FuzzySearch
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QuickMenuOrb {
    SEARCH, RANDOM, MOST_PLAYED, TOP_UNPLAYED, RECENT, FAVORITES
}

enum class MetadataType {
    NONE, RATING, PLAY_TIME, RELATIVE_TIME
}

data class GameRowUi(
    val id: Long,
    val title: String,
    val platformName: String?,
    val coverPath: String?,
    val metadata: String,
    val metadataType: MetadataType = MetadataType.NONE,
    val isDownloaded: Boolean,
    val titleId: String? = null
)

data class GameCardUi(
    val id: Long,
    val title: String,
    val platformName: String?,
    val coverPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val developer: String?,
    val rating: Float?,
    val genre: String?,
    val description: String?,
    val isDownloaded: Boolean,
    val needsInstall: Boolean,
    val isFavorite: Boolean
)

data class QuickMenuUiState(
    val isVisible: Boolean = false,
    val selectedOrb: QuickMenuOrb = QuickMenuOrb.MOST_PLAYED,
    val contentFocused: Boolean = false,
    val focusedContentIndex: Int = 0,
    val searchQuery: String = "",
    val searchResults: List<GameRowUi> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val searchInputFocused: Boolean = true,
    val randomGame: GameCardUi? = null,
    val isRandomResolved: Boolean = false,
    val mostPlayedGames: List<GameRowUi> = emptyList(),
    val topUnplayedGames: List<GameRowUi> = emptyList(),
    val recentGames: List<GameRowUi> = emptyList(),
    val favoriteGames: List<GameRowUi> = emptyList(),
    val isLoading: Boolean = false
)

private const val LIST_LIMIT = 20

@HiltViewModel
class QuickMenuViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val getTopUnplayedUseCase: GetTopUnplayedUseCase,
    private val librarySyncBus: LibrarySyncBus,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameActions: GameActionsDelegate,
    private val downloadStatus: DownloadFileStatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickMenuUiState())
    val uiState: StateFlow<QuickMenuUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var randomJob: Job? = null
    private val platformCache = mutableMapOf<Long, String>()
    private var searchCandidates: List<SearchCandidate>? = null

    init {
        viewModelScope.launch {
            librarySyncBus.syncCompleted.collect {
                invalidateSearchCache()
            }
        }
    }

    fun show() {
        _uiState.update {
            QuickMenuUiState(
                isVisible = true,
                selectedOrb = QuickMenuOrb.MOST_PLAYED,
                contentFocused = false,
                focusedContentIndex = 0
            )
        }
        loadAllData()
    }

    fun hide() {
        _uiState.update { it.copy(isVisible = false) }
    }

    fun toggle() {
        if (_uiState.value.isVisible) hide() else show()
    }

    fun selectOrb(orb: QuickMenuOrb) {
        _uiState.update {
            it.copy(
                selectedOrb = orb,
                focusedContentIndex = 0
            )
        }
    }

    fun moveOrbLeft() {
        val orbs = QuickMenuOrb.entries
        val currentIndex = orbs.indexOf(_uiState.value.selectedOrb)
        val newIndex = if (currentIndex > 0) currentIndex - 1 else orbs.lastIndex
        selectOrb(orbs[newIndex])
    }

    fun moveOrbRight() {
        val orbs = QuickMenuOrb.entries
        val currentIndex = orbs.indexOf(_uiState.value.selectedOrb)
        val newIndex = if (currentIndex < orbs.lastIndex) currentIndex + 1 else 0
        selectOrb(orbs[newIndex])
    }

    fun enterContent() {
        val isSearch = _uiState.value.selectedOrb == QuickMenuOrb.SEARCH
        _uiState.update {
            it.copy(
                contentFocused = true,
                focusedContentIndex = 0,
                searchInputFocused = isSearch
            )
        }
    }

    fun exitContent() {
        _uiState.update { it.copy(contentFocused = false, searchInputFocused = true) }
    }

    fun moveContentUp() {
        _uiState.update { state ->
            if (state.selectedOrb == QuickMenuOrb.SEARCH && !state.searchInputFocused && state.focusedContentIndex == 0) {
                state.copy(searchInputFocused = true)
            } else {
                val newIndex = (state.focusedContentIndex - 1).coerceAtLeast(0)
                state.copy(focusedContentIndex = newIndex)
            }
        }
    }

    fun moveContentDown() {
        _uiState.update { state ->
            if (state.selectedOrb == QuickMenuOrb.SEARCH && state.searchInputFocused) {
                val hasItems = if (state.searchQuery.length < 2) {
                    state.recentSearches.isNotEmpty()
                } else {
                    state.searchResults.isNotEmpty()
                }
                if (hasItems) {
                    state.copy(searchInputFocused = false, focusedContentIndex = 0)
                } else {
                    state
                }
            } else {
                val maxIndex = getCurrentContentSize() - 1
                val newIndex = (state.focusedContentIndex + 1).coerceAtMost(maxIndex.coerceAtLeast(0))
                state.copy(focusedContentIndex = newIndex)
            }
        }
    }

    fun getSelectedGameId(): Long? {
        val state = _uiState.value
        if (!state.contentFocused) return null

        return when (state.selectedOrb) {
            QuickMenuOrb.SEARCH -> {
                if (state.searchQuery.length < 2) null
                else state.searchResults.getOrNull(state.focusedContentIndex)?.id
            }
            QuickMenuOrb.RANDOM -> state.randomGame?.id
            QuickMenuOrb.MOST_PLAYED -> state.mostPlayedGames.getOrNull(state.focusedContentIndex)?.id
            QuickMenuOrb.TOP_UNPLAYED -> state.topUnplayedGames.getOrNull(state.focusedContentIndex)?.id
            QuickMenuOrb.RECENT -> state.recentGames.getOrNull(state.focusedContentIndex)?.id
            QuickMenuOrb.FAVORITES -> state.favoriteGames.getOrNull(state.focusedContentIndex)?.id
        }
    }

    fun isOnRecentSearches(): Boolean {
        val state = _uiState.value
        return state.selectedOrb == QuickMenuOrb.SEARCH &&
            state.contentFocused &&
            !state.searchInputFocused &&
            state.searchQuery.length < 2 &&
            state.recentSearches.isNotEmpty()
    }

    fun selectRecentSearch(index: Int) {
        val state = _uiState.value
        val query = state.recentSearches.getOrNull(index) ?: return
        selectRecentSearch(query)
    }

    fun selectRecentSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query, focusedContentIndex = 0) }
        performSearch(query)
    }

    fun saveSearchQuery() {
        val query = _uiState.value.searchQuery
        if (query.length >= 2) {
            viewModelScope.launch {
                preferencesRepository.addLibraryRecentSearch(query)
                loadRecentSearches()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        performSearch(query)
    }

    fun rerollRandom() {
        randomJob?.cancel()
        randomJob = viewModelScope.launch {
            loadRandomGame(excludeId = _uiState.value.randomGame?.id)
        }
    }

    fun toggleRandomFavorite() {
        val gameId = _uiState.value.randomGame?.id ?: return
        viewModelScope.launch {
            val isFavorite = gameActions.toggleFavorite(gameId) ?: return@launch
            _uiState.update { state ->
                val card = state.randomGame
                if (card != null && card.id == gameId) state.copy(randomGame = card.copy(isFavorite = isFavorite)) else state
            }
            loadFavorites()
        }
    }

    private fun getCurrentContentSize(): Int {
        val state = _uiState.value
        return when (state.selectedOrb) {
            QuickMenuOrb.SEARCH -> {
                if (state.searchQuery.length < 2) state.recentSearches.size
                else state.searchResults.size
            }
            QuickMenuOrb.RANDOM -> if (state.randomGame != null) 1 else 0
            QuickMenuOrb.MOST_PLAYED -> state.mostPlayedGames.size
            QuickMenuOrb.TOP_UNPLAYED -> state.topUnplayedGames.size
            QuickMenuOrb.RECENT -> state.recentGames.size
            QuickMenuOrb.FAVORITES -> state.favoriteGames.size
        }
    }

    private fun loadAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            launch { loadMostPlayed() }
            launch { loadTopUnplayed() }
            launch { loadRecent() }
            launch { loadFavorites() }
            randomJob?.cancel()
            randomJob = launch { loadRandomGame(excludeId = null) }
            launch { loadRecentSearches() }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadRecentSearches() {
        val prefs = preferencesRepository.preferences.first()
        _uiState.update { it.copy(recentSearches = prefs.libraryRecentSearches) }
    }

    private suspend fun loadMostPlayed() {
        val games = gameRepository.getPlayedGames().take(LIST_LIMIT)
        val rows = games.map { it.toGameRowUi(MetadataType.PLAY_TIME) { formatPlayTime(context, it.playTimeMinutes) } }
        _uiState.update { it.copy(mostPlayedGames = rows) }
    }

    private suspend fun loadTopUnplayed() {
        val games = getTopUnplayedUseCase(LIST_LIMIT).first()
        val rows = games.map { it.toGameRowUi(MetadataType.RATING) { formatRating(it.rating) } }
        _uiState.update { it.copy(topUnplayedGames = rows) }
    }

    private suspend fun loadRecent() {
        val games = gameRepository.getRecentlyPlayed(LIST_LIMIT)
        val rows = games.map { it.toGameRowUi(MetadataType.RELATIVE_TIME) { formatRelativeTime(context, it.lastPlayed) } }
        _uiState.update { it.copy(recentGames = rows) }
    }

    private suspend fun loadFavorites() {
        val games = gameRepository.getFavorites().take(LIST_LIMIT)
        val rows = games.map { it.toGameRowUi(MetadataType.NONE) { "" } }
        _uiState.update { it.copy(favoriteGames = rows) }
    }

    private suspend fun loadRandomGame(excludeId: Long?) {
        val first = gameRepository.getRandomGame()
        val game = if (excludeId != null && first?.id == excludeId) {
            gameRepository.getRandomGame() ?: first
        } else {
            first
        }
        val card = game?.toGameCardUi()
        _uiState.update { it.copy(randomGame = card, isRandomResolved = true) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()

        if (query.length < 2) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            val candidates = searchCandidates ?: gameRepository.getSearchCandidates().also {
                searchCandidates = it
            }
            val matched = FuzzySearch.search(query, candidates, limit = 10)
            if (matched.isEmpty()) {
                _uiState.update { it.copy(searchResults = emptyList(), focusedContentIndex = 0) }
                return@launch
            }
            val matchedIds = matched.map { it.id }
            val games = gameRepository.getByIds(matchedIds)
            val gamesById = games.associateBy { it.id }
            val orderedGames = matchedIds.mapNotNull { gamesById[it] }
            val rows = orderedGames.map { it.toGameRowUi(MetadataType.RATING) { formatRating(it.rating) } }
            _uiState.update { it.copy(searchResults = rows, focusedContentIndex = 0) }
        }
    }

    fun invalidateSearchCache() {
        searchCandidates = null
    }

    private suspend fun GameEntity.toGameRowUi(
        metadataType: MetadataType,
        metadataProvider: () -> String
    ): GameRowUi {
        val platformName = getPlatformName(platformId)
        return GameRowUi(
            id = id,
            title = title,
            platformName = platformName,
            coverPath = gameRepository.getArt(id).coverPath,
            metadata = metadataProvider(),
            metadataType = metadataType,
            isDownloaded = localPath != null,
            titleId = displayTitleId
        )
    }

    private suspend fun GameEntity.toGameCardUi(): GameCardUi {
        val platformName = getPlatformName(platformId)
        val firstScreenshot = gameRepository.getScreenshots(id).firstOrNull()?.sourceUrl
        val art = gameRepository.getArt(id)
        return GameCardUi(
            id = id,
            title = title,
            platformName = platformName,
            coverPath = art.coverPath,
            backdropPath = art.backgroundPath ?: firstScreenshot ?: art.coverPath,
            year = releaseYear,
            developer = developer,
            rating = rating,
            genre = genre,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            isDownloaded = resolveDownloaded(downloadStatus),
            needsInstall = needsAndroidInstall,
            isFavorite = isFavorite
        )
    }

    private suspend fun getPlatformName(platformId: Long): String? {
        platformCache[platformId]?.let { return it }
        val name = platformRepository.getById(platformId)?.name ?: return null
        platformCache[platformId] = name
        return name
    }

    private fun formatRating(rating: Float?): String {
        return rating?.let { "${it.toInt()}%" } ?: ""
    }
}
