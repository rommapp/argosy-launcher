package com.nendo.argosy.ui.screens.library

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FilterCategory(val label: String) {
    SOURCE("Source"),
    GENRE("Genre"),
    PLAYERS("Players"),
    FRANCHISE("Franchise")
}

enum class SourceFilter(val label: String) {
    ALL("All Games"),
    PLAYABLE("Playable")
}

data class ActiveFilters(
    val source: SourceFilter = SourceFilter.ALL,
    val genres: Set<String> = emptySet(),
    val players: Set<String> = emptySet(),
    val franchises: Set<String> = emptySet()
) {
    val activeCount: Int
        get() = listOf(
            if (source != SourceFilter.ALL) 1 else 0,
            genres.size,
            players.size,
            franchises.size
        ).sum()

    val summary: String
        get() = when {
            activeCount == 0 -> "All Games"
            activeCount == 1 -> when {
                source != SourceFilter.ALL -> source.label
                genres.isNotEmpty() -> genres.first()
                players.isNotEmpty() -> players.first()
                franchises.isNotEmpty() -> franchises.first()
                else -> "All Games"
            }
            else -> "$activeCount filters"
        }
}

data class FilterOptions(
    val genres: List<String> = emptyList(),
    val players: List<String> = emptyList(),
    val franchises: List<String> = emptyList()
)

// Keep for backward compatibility during transition
enum class LibraryFilter(val label: String) {
    ALL("All Games"),
    PLAYABLE("Playable Only"),
    LOCAL("Local Only"),
    SYNCED("Synced Only"),
    REMOTE("Remote Only")
}

enum class FocusMove {
    UP, DOWN, LEFT, RIGHT
}

data class LibraryGameUi(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val source: GameSource,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val emulatorName: String?
) {
    val sourceIcon: ImageVector?
        get() = when (source) {
            GameSource.LOCAL_ONLY -> Icons.Default.Folder
            GameSource.ROMM_SYNCED -> Icons.Default.CheckCircle
            GameSource.ROMM_REMOTE -> null
        }
}

data class LibraryUiState(
    val platforms: List<HomePlatformUi> = emptyList(),
    val currentPlatformIndex: Int = -1,
    val games: List<LibraryGameUi> = emptyList(),
    val focusedIndex: Int = 0,
    val currentFilter: LibraryFilter = LibraryFilter.ALL,
    val showFilterMenu: Boolean = false,
    val showQuickMenu: Boolean = false,
    val quickMenuFocusIndex: Int = 0,
    val columnsCount: Int = 4,
    val isLoading: Boolean = true,
    val activeFilters: ActiveFilters = ActiveFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val filterCategoryIndex: Int = 0,
    val filterOptionIndex: Int = 0
) {
    val currentPlatform: HomePlatformUi?
        get() = if (currentPlatformIndex >= 0) platforms.getOrNull(currentPlatformIndex) else null

    val focusedGame: LibraryGameUi?
        get() = games.getOrNull(focusedIndex)

    val currentFilterCategory: FilterCategory
        get() = FilterCategory.entries.getOrElse(filterCategoryIndex) { FilterCategory.SOURCE }

    val currentCategoryOptions: List<String>
        get() = when (currentFilterCategory) {
            FilterCategory.SOURCE -> SourceFilter.entries.map { it.label }
            FilterCategory.GENRE -> filterOptions.genres
            FilterCategory.PLAYERS -> filterOptions.players
            FilterCategory.FRANCHISE -> filterOptions.franchises
        }

    val isCurrentCategoryMultiSelect: Boolean
        get() = currentFilterCategory != FilterCategory.SOURCE

    val selectedSourceIndex: Int
        get() = activeFilters.source.ordinal

    val selectedOptionsInCurrentCategory: Set<String>
        get() = when (currentFilterCategory) {
            FilterCategory.SOURCE -> emptySet()
            FilterCategory.GENRE -> activeFilters.genres
            FilterCategory.PLAYERS -> activeFilters.players
            FilterCategory.FRANCHISE -> activeFilters.franchises
        }

    val currentCategoryActiveCount: Int
        get() = when (currentFilterCategory) {
            FilterCategory.SOURCE -> if (activeFilters.source != SourceFilter.ALL) 1 else 0
            FilterCategory.GENRE -> activeFilters.genres.size
            FilterCategory.PLAYERS -> activeFilters.players.size
            FilterCategory.FRANCHISE -> activeFilters.franchises.size
        }

    val availableCategories: List<FilterCategory>
        get() = FilterCategory.entries.filter { category ->
            when (category) {
                FilterCategory.SOURCE -> true
                FilterCategory.GENRE -> filterOptions.genres.isNotEmpty()
                FilterCategory.PLAYERS -> filterOptions.players.isNotEmpty()
                FilterCategory.FRANCHISE -> filterOptions.franchises.isNotEmpty()
            }
        }
}

private const val TAG = "LibraryVM"

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val gameNavigationContext: GameNavigationContext,
    private val deleteGameUseCase: DeleteGameUseCase,
    private val notificationManager: NotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var gamesJob: Job? = null

    init {
        loadPlatforms()
        loadFilterOptions()
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            Log.d(TAG, "loadPlatforms: starting observation")
            platformDao.observeVisiblePlatforms().collect { platforms ->
                Log.d(TAG, "loadPlatforms: received ${platforms.size} platforms")
                val platformUis = platforms.map { it.toUi() }
                _uiState.update { state ->
                    state.copy(
                        platforms = platformUis,
                        isLoading = false
                    )
                }
                loadGames()
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val genres = gameDao.getDistinctGenres()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val players = gameDao.getDistinctGameModes()
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val franchises = gameDao.getDistinctFranchises()
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            Log.d(TAG, "loadFilterOptions: genres=${genres.size}, players=${players.size}, franchises=${franchises.size}")

            _uiState.update { state ->
                state.copy(
                    filterOptions = FilterOptions(
                        genres = genres,
                        players = players,
                        franchises = franchises
                    )
                )
            }
        }
    }

    private fun loadGames() {
        val state = _uiState.value
        val platformIndex = state.currentPlatformIndex
        val filters = state.activeFilters
        Log.d(TAG, "loadGames: platformIndex=$platformIndex, filters=$filters")

        gamesJob?.cancel()
        gamesJob = viewModelScope.launch {
            val baseFlow = when {
                platformIndex >= 0 -> {
                    val platformId = state.platforms[platformIndex].id
                    gameDao.observeByPlatform(platformId)
                }
                else -> when (filters.source) {
                    SourceFilter.ALL -> gameDao.observeAll()
                    SourceFilter.PLAYABLE -> gameDao.observePlayable()
                }
            }

            baseFlow.collectLatest { games ->
                val filteredGames = games.filter { game ->
                    val matchesGenre = filters.genres.isEmpty() ||
                        filters.genres.contains(game.genre)
                    val matchesPlayers = filters.players.isEmpty() ||
                        game.gameModes?.split(",")?.map { it.trim() }?.any { it in filters.players } == true
                    val matchesFranchise = filters.franchises.isEmpty() ||
                        game.franchises?.split(",")?.map { it.trim() }?.any { it in filters.franchises } == true
                    matchesGenre && matchesPlayers && matchesFranchise
                }

                Log.d(TAG, "loadGames: ${games.size} total, ${filteredGames.size} after filters")
                gameNavigationContext.setContext(filteredGames.map { it.id })
                _uiState.update { uiState ->
                    val shouldResetFocus = uiState.games.isEmpty()
                    uiState.copy(
                        games = filteredGames.map { it.toUi() },
                        focusedIndex = if (shouldResetFocus) 0 else uiState.focusedIndex.coerceAtMost((filteredGames.size - 1).coerceAtLeast(0))
                    )
                }
            }
        }
    }

    fun nextPlatform() {
        Log.d(TAG, "nextPlatform called, currentIndex=${_uiState.value.currentPlatformIndex}")
        val state = _uiState.value
        if (state.platforms.isEmpty()) return

        val nextIndex = when {
            state.currentPlatformIndex < 0 -> 0
            state.currentPlatformIndex >= state.platforms.size - 1 -> -1
            else -> state.currentPlatformIndex + 1
        }

        Log.d(TAG, "nextPlatform: changing to index $nextIndex")
        _uiState.update { it.copy(currentPlatformIndex = nextIndex) }
        loadGames()
    }

    fun previousPlatform() {
        Log.d(TAG, "previousPlatform called, currentIndex=${_uiState.value.currentPlatformIndex}")
        val state = _uiState.value
        if (state.platforms.isEmpty()) return

        val prevIndex = when {
            state.currentPlatformIndex < 0 -> state.platforms.size - 1
            state.currentPlatformIndex == 0 -> -1
            else -> state.currentPlatformIndex - 1
        }

        Log.d(TAG, "previousPlatform: changing to index $prevIndex")
        _uiState.update { it.copy(currentPlatformIndex = prevIndex) }
        loadGames()
    }

    fun moveFocus(direction: FocusMove) {
        _uiState.update { state ->
            if (state.games.isEmpty()) return@update state

            val cols = state.columnsCount
            val total = state.games.size
            val current = state.focusedIndex

            val newIndex = when (direction) {
                FocusMove.UP -> {
                    val target = current - cols
                    if (target >= 0) target else current
                }
                FocusMove.DOWN -> {
                    val target = current + cols
                    if (target < total) target else current
                }
                FocusMove.LEFT -> {
                    if (current % cols > 0) current - 1 else current
                }
                FocusMove.RIGHT -> {
                    if (current % cols < cols - 1 && current + 1 < total) current + 1 else current
                }
            }

            Log.d(TAG, "moveFocus: $direction, $current -> $newIndex (cols=$cols, total=$total)")
            state.copy(focusedIndex = newIndex)
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(currentFilter = filter) }
        loadGames()
    }

    fun toggleFilterMenu() {
        _uiState.update { state ->
            val newShowFilter = !state.showFilterMenu
            state.copy(
                showFilterMenu = newShowFilter,
                filterCategoryIndex = if (newShowFilter) 0 else state.filterCategoryIndex,
                filterOptionIndex = if (newShowFilter) 0 else state.filterOptionIndex
            )
        }
    }

    fun moveFilterCategoryFocus(delta: Int) {
        _uiState.update { state ->
            val categories = state.availableCategories
            val currentCategoryIndex = categories.indexOfFirst { it == state.currentFilterCategory }
            val newCategoryIndex = (currentCategoryIndex + delta).coerceIn(0, categories.size - 1)
            val newCategory = categories.getOrElse(newCategoryIndex) { FilterCategory.SOURCE }
            val globalIndex = FilterCategory.entries.indexOf(newCategory)

            state.copy(
                filterCategoryIndex = globalIndex,
                filterOptionIndex = 0
            )
        }
    }

    fun moveFilterOptionFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.currentCategoryOptions.size - 1
            val newIndex = (state.filterOptionIndex + delta).coerceIn(0, maxIndex)
            state.copy(filterOptionIndex = newIndex)
        }
    }

    fun confirmFilterSelection() {
        val state = _uiState.value
        val category = state.currentFilterCategory
        val optionIndex = state.filterOptionIndex
        val options = state.currentCategoryOptions

        val newFilters = when (category) {
            FilterCategory.SOURCE -> {
                val source = SourceFilter.entries.getOrElse(optionIndex) { SourceFilter.ALL }
                state.activeFilters.copy(source = source)
            }
            FilterCategory.GENRE -> {
                val genre = options.getOrNull(optionIndex) ?: return
                val currentGenres = state.activeFilters.genres
                val newGenres = if (genre in currentGenres) currentGenres - genre else currentGenres + genre
                state.activeFilters.copy(genres = newGenres)
            }
            FilterCategory.PLAYERS -> {
                val player = options.getOrNull(optionIndex) ?: return
                val currentPlayers = state.activeFilters.players
                val newPlayers = if (player in currentPlayers) currentPlayers - player else currentPlayers + player
                state.activeFilters.copy(players = newPlayers)
            }
            FilterCategory.FRANCHISE -> {
                val franchise = options.getOrNull(optionIndex) ?: return
                val currentFranchises = state.activeFilters.franchises
                val newFranchises = if (franchise in currentFranchises) currentFranchises - franchise else currentFranchises + franchise
                state.activeFilters.copy(franchises = newFranchises)
            }
        }

        _uiState.update { it.copy(activeFilters = newFilters) }
        loadGames()
    }

    fun clearCurrentCategoryFilters() {
        val state = _uiState.value
        val category = state.currentFilterCategory

        val newFilters = when (category) {
            FilterCategory.SOURCE -> state.activeFilters.copy(source = SourceFilter.ALL)
            FilterCategory.GENRE -> state.activeFilters.copy(genres = emptySet())
            FilterCategory.PLAYERS -> state.activeFilters.copy(players = emptySet())
            FilterCategory.FRANCHISE -> state.activeFilters.copy(franchises = emptySet())
        }

        _uiState.update { it.copy(activeFilters = newFilters) }
        loadGames()
    }

    fun clearAllFilters() {
        _uiState.update { it.copy(activeFilters = ActiveFilters(), filterOptionIndex = 0) }
        loadGames()
    }

    fun toggleQuickMenu() {
        _uiState.update { it.copy(showQuickMenu = !it.showQuickMenu, quickMenuFocusIndex = 0) }
    }

    fun moveQuickMenuFocus(delta: Int) {
        _uiState.update {
            val maxIndex = if (it.focusedGame?.isDownloaded == true) 4 else 3
            val newIndex = (it.quickMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(quickMenuFocusIndex = newIndex)
        }
    }

    fun confirmQuickMenuSelection(onGameSelect: (Long) -> Unit) {
        val game = _uiState.value.focusedGame ?: return
        when (_uiState.value.quickMenuFocusIndex) {
            0 -> onGameSelect(game.id)
            1 -> toggleFavorite(game.id)
            2 -> onGameSelect(game.id)
            3 -> {
                if (game.isDownloaded) {
                    deleteLocalFile(game.id)
                } else {
                    hideGame(game.id)
                }
            }
            4 -> hideGame(game.id)
        }
        toggleQuickMenu()
    }

    fun hideGame(gameId: Long) {
        viewModelScope.launch {
            gameDao.updateHidden(gameId, true)
        }
    }

    private fun deleteLocalFile(gameId: Long) {
        viewModelScope.launch {
            deleteGameUseCase(gameId)
            notificationManager.showSuccess("Download deleted")
        }
    }

    fun launchGame(gameId: Long) {
        viewModelScope.launch {
            // TODO: Launch emulator with game
        }
    }

    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            gameDao.updateFavorite(gameId, !game.isFavorite)
            loadGames()
        }
    }

    private fun PlatformEntity.toUi() = HomePlatformUi(
        id = id,
        name = name,
        shortName = shortName,
        logoPath = logoPath
    )

    private fun GameEntity.toUi() = LibraryGameUi(
        id = id,
        title = title,
        coverPath = coverPath,
        source = source,
        isFavorite = isFavorite,
        isDownloaded = localPath != null,
        emulatorName = null
    )

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onDrawerToggle: () -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(-1)
                state.showQuickMenu -> moveQuickMenuFocus(-1)
                else -> moveFocus(FocusMove.UP)
            }
            return true
        }

        override fun onDown(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(1)
                state.showQuickMenu -> moveQuickMenuFocus(1)
                else -> moveFocus(FocusMove.DOWN)
            }
            return true
        }

        override fun onLeft(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterCategoryFocus(-1)
                state.showQuickMenu -> return true
                else -> moveFocus(FocusMove.LEFT)
            }
            return true
        }

        override fun onRight(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterCategoryFocus(1)
                state.showQuickMenu -> return true
                else -> moveFocus(FocusMove.RIGHT)
            }
            return true
        }

        override fun onConfirm(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> confirmFilterSelection()
                state.showQuickMenu -> confirmQuickMenuSelection(onGameSelect)
                else -> state.focusedGame?.let { game -> onGameSelect(game.id) }
            }
            return true
        }

        override fun onBack(): Boolean {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> {
                    toggleFilterMenu()
                    true
                }
                state.showQuickMenu -> {
                    toggleQuickMenu()
                    true
                }
                else -> {
                    onBack()
                    true
                }
            }
        }

        override fun onMenu(): Boolean {
            if (_uiState.value.showQuickMenu) {
                toggleQuickMenu()
                return false
            }
            if (_uiState.value.showFilterMenu) {
                toggleFilterMenu()
                return false
            }
            onDrawerToggle()
            return true
        }

        override fun onSecondaryAction(): Boolean {
            if (_uiState.value.showQuickMenu) return true
            toggleFilterMenu()
            return true
        }

        override fun onContextMenu(): Boolean {
            if (_uiState.value.showFilterMenu) {
                clearCurrentCategoryFilters()
                return true
            }
            return false
        }

        override fun onSelect(): Boolean {
            if (_uiState.value.focusedGame != null) {
                toggleQuickMenu()
            }
            return true
        }

        override fun onPrevSection(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(-5)
                state.showQuickMenu -> return true
                else -> previousPlatform()
            }
            return true
        }

        override fun onNextSection(): Boolean {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(5)
                state.showQuickMenu -> return true
                else -> nextPlatform()
            }
            return true
        }
    }
}
