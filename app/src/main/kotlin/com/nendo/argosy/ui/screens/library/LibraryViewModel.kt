package com.nendo.argosy.ui.screens.library

import android.content.Intent
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
import com.nendo.argosy.data.preferences.UiDensity
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    PLAYABLE("Playable"),
    FAVORITES("Favorites")
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
    val isRommGame: Boolean,
    val emulatorName: String?
) {
    val sourceIcon: ImageVector?
        get() = when (source) {
            GameSource.LOCAL_ONLY -> Icons.Default.Folder
            GameSource.ROMM_SYNCED -> Icons.Default.CheckCircle
            GameSource.ROMM_REMOTE -> null
            GameSource.STEAM -> Icons.Default.Cloud
        }
}

data class LibraryUiState(
    val platforms: List<HomePlatformUi> = emptyList(),
    val currentPlatformIndex: Int = -1,
    val games: List<LibraryGameUi> = emptyList(),
    val focusedIndex: Int = 0,
    val lastFocusMove: FocusMove? = null,
    val currentFilter: LibraryFilter = LibraryFilter.ALL,
    val showFilterMenu: Boolean = false,
    val showQuickMenu: Boolean = false,
    val quickMenuFocusIndex: Int = 0,
    val uiDensity: UiDensity = UiDensity.NORMAL,
    val isLoading: Boolean = true,
    val activeFilters: ActiveFilters = ActiveFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val filterCategoryIndex: Int = 0,
    val filterOptionIndex: Int = 0,
    val syncOverlayState: SyncOverlayState? = null,
    val isTouchScrolling: Boolean = false
) {
    val columnsCount: Int
        get() = when (uiDensity) {
            UiDensity.COMPACT -> 5
            UiDensity.NORMAL -> 4
            UiDensity.SPACIOUS -> 3
        }

    val cardHeightDp: Int
        get() = when (uiDensity) {
            UiDensity.COMPACT -> 150
            UiDensity.NORMAL -> 180
            UiDensity.SPACIOUS -> 220
        }

    val gridSpacingDp: Int
        get() = when (uiDensity) {
            UiDensity.COMPACT -> 12
            UiDensity.NORMAL -> 16
            UiDensity.SPACIOUS -> 20
        }

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

sealed class LibraryEvent {
    data class LaunchGame(val intent: Intent) : LibraryEvent()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val gameNavigationContext: GameNavigationContext,
    private val notificationManager: NotificationManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var gamesJob: Job? = null
    private var pendingInitialPlatformId: String? = null
    private var pendingInitialSourceFilter: SourceFilter? = null

    init {
        loadPlatforms()
        loadFilterOptions()
        observeUiDensity()
        observeSyncOverlay()
    }

    private fun observeSyncOverlay() {
        viewModelScope.launch {
            gameLaunchDelegate.syncOverlayState.collect { overlayState ->
                _uiState.update { it.copy(syncOverlayState = overlayState) }
            }
        }
    }

    private fun observeUiDensity() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collectLatest { prefs ->
                _uiState.update { it.copy(uiDensity = prefs.uiDensity) }
            }
        }
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            Log.d(TAG, "loadPlatforms: starting observation")
            platformDao.observeVisiblePlatforms().collect { platforms ->
                Log.d(TAG, "loadPlatforms: received ${platforms.size} platforms")
                val platformUis = platforms.map { it.toUi() }

                val pendingPlatformIndex = pendingInitialPlatformId?.let { platformId ->
                    platformUis.indexOfFirst { it.id == platformId }.takeIf { it >= 0 }
                }

                _uiState.update { state ->
                    state.copy(
                        platforms = platformUis,
                        currentPlatformIndex = pendingPlatformIndex ?: state.currentPlatformIndex,
                        isLoading = false
                    )
                }

                if (pendingPlatformIndex != null) {
                    Log.d(TAG, "loadPlatforms: applied pending platform $pendingInitialPlatformId at index $pendingPlatformIndex")
                    pendingInitialPlatformId = null
                }

                pendingInitialSourceFilter?.let { sourceFilter ->
                    Log.d(TAG, "loadPlatforms: applying pending source filter $sourceFilter")
                    _uiState.update { it.copy(activeFilters = it.activeFilters.copy(source = sourceFilter)) }
                    pendingInitialSourceFilter = null
                }

                loadGames()
            }
        }
    }

    fun onResume() {
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
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
                    SourceFilter.FAVORITES -> gameDao.observeFavorites()
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

    fun setInitialPlatform(platformId: String) {
        val state = _uiState.value
        if (state.platforms.isEmpty()) {
            Log.d(TAG, "setInitialPlatform: platforms not loaded yet, storing pending platformId=$platformId")
            pendingInitialPlatformId = platformId
            return
        }
        val index = state.platforms.indexOfFirst { it.id == platformId }
        if (index >= 0 && index != state.currentPlatformIndex) {
            Log.d(TAG, "setInitialPlatform: setting platform to $platformId (index $index)")
            _uiState.update { it.copy(currentPlatformIndex = index) }
            loadGames()
        }
    }

    fun setInitialSourceFilter(source: SourceFilter) {
        val state = _uiState.value
        if (state.platforms.isEmpty()) {
            Log.d(TAG, "setInitialSourceFilter: platforms not loaded yet, storing pending source=$source")
            pendingInitialSourceFilter = source
            return
        }
        if (state.activeFilters.source != source) {
            Log.d(TAG, "setInitialSourceFilter: setting source to $source")
            _uiState.update { it.copy(activeFilters = it.activeFilters.copy(source = source)) }
            loadGames()
        }
    }

    fun moveFocus(direction: FocusMove): Boolean {
        val state = _uiState.value
        if (state.games.isEmpty()) return false

        val cols = state.columnsCount
        val total = state.games.size
        val current = state.focusedIndex

        val newIndex = when (direction) {
            FocusMove.UP -> {
                val target = current - cols
                if (target >= 0) target else null
            }
            FocusMove.DOWN -> {
                val target = current + cols
                if (target < total) target else null
            }
            FocusMove.LEFT -> {
                if (current % cols > 0) current - 1 else null
            }
            FocusMove.RIGHT -> {
                if (current % cols < cols - 1 && current + 1 < total) current + 1 else null
            }
        }

        if (newIndex == null) return false

        Log.d(TAG, "moveFocus: $direction, $current -> $newIndex (cols=$cols, total=$total)")
        _uiState.update { it.copy(focusedIndex = newIndex, lastFocusMove = direction) }
        return true
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(currentFilter = filter) }
        loadGames()
    }

    fun toggleFilterMenu() {
        val wasShowing = _uiState.value.showFilterMenu
        _uiState.update { state ->
            val newShowFilter = !state.showFilterMenu
            state.copy(
                showFilterMenu = newShowFilter,
                filterCategoryIndex = if (newShowFilter) 0 else state.filterCategoryIndex,
                filterOptionIndex = if (newShowFilter) 0 else state.filterOptionIndex
            )
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
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

    fun setFilterCategory(category: FilterCategory) {
        val globalIndex = FilterCategory.entries.indexOf(category)
        _uiState.update { state ->
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
        val wasShowing = _uiState.value.showQuickMenu
        _uiState.update { it.copy(showQuickMenu = !it.showQuickMenu, quickMenuFocusIndex = 0) }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun moveQuickMenuFocus(delta: Int) {
        _uiState.update {
            val game = it.focusedGame
            val isDownloaded = game?.isDownloaded == true
            val isRommGame = game?.isRommGame == true
            var maxIndex = if (isDownloaded) 4 else 3
            if (isRommGame) maxIndex++
            val newIndex = (it.quickMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(quickMenuFocusIndex = newIndex)
        }
    }

    fun confirmQuickMenuSelection(onGameSelect: (Long) -> Unit): InputResult {
        val game = _uiState.value.focusedGame ?: return InputResult.HANDLED
        val index = _uiState.value.quickMenuFocusIndex
        val isRommGame = game.isRommGame
        val isDownloaded = game.isDownloaded

        var currentIdx = 0
        val playIdx = currentIdx++
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val refreshIdx = if (isRommGame) currentIdx++ else -1
        val deleteIdx = if (isDownloaded) currentIdx++ else -1
        val hideIdx = currentIdx

        return when (index) {
            playIdx -> {
                if (isDownloaded) launchGame(game.id) else downloadGame(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            favoriteIdx -> {
                val sound = if (game.isFavorite) SoundType.UNFAVORITE else SoundType.FAVORITE
                toggleFavorite(game.id)
                InputResult.handled(sound)
            }
            detailsIdx -> {
                gameNavigationContext.setContext(_uiState.value.games.map { it.id })
                onGameSelect(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            refreshIdx -> {
                refreshGameData(game.id)
                InputResult.HANDLED
            }
            deleteIdx -> {
                deleteLocalFile(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            hideIdx -> {
                hideGame(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    fun hideGame(gameId: Long) {
        viewModelScope.launch {
            gameActions.hideGame(gameId)
        }
    }

    fun refreshGameData(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.refreshGameData(gameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    loadGames()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleQuickMenu()
        }
    }

    fun deleteLocalFile(gameId: Long) {
        viewModelScope.launch {
            gameActions.deleteLocalFile(gameId)
            notificationManager.showSuccess("Download deleted")
        }
    }

    fun launchGame(gameId: Long) {
        gameLaunchDelegate.launchGame(viewModelScope, gameId) { intent ->
            viewModelScope.launch { _events.emit(LibraryEvent.LaunchGame(intent)) }
        }
    }

    fun downloadGame(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
            }
        }
    }

    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            gameActions.toggleFavorite(gameId)
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
        isDownloaded = localPath != null || source == GameSource.STEAM,
        isRommGame = rommId != null,
        emulatorName = null
    )

    fun setTouchScrolling(isScrolling: Boolean) {
        _uiState.update { it.copy(isTouchScrolling = isScrolling) }
    }

    fun setFocusIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.games.size) return
        if (index == state.focusedIndex) return
        _uiState.update { it.copy(focusedIndex = index, isTouchScrolling = false) }
        soundManager.play(SoundType.NAVIGATE)
    }

    fun handleItemTap(index: Int, onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        if (index < 0 || index >= state.games.size) return

        if (state.isTouchScrolling || index != state.focusedIndex) {
            setFocusIndex(index)
            return
        }

        val game = state.games[index]
        gameNavigationContext.setContext(state.games.map { it.id })
        onGameSelect(game.id)
    }

    fun handleItemLongPress(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.games.size) return

        if (index != state.focusedIndex) {
            _uiState.update { it.copy(focusedIndex = index, isTouchScrolling = false) }
        }
        toggleQuickMenu()
    }

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onDrawerToggle: () -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> { moveFilterOptionFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(-1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.UP)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> { moveFilterOptionFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.DOWN)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> { moveFilterCategoryFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                else -> if (moveFocus(FocusMove.LEFT)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> { moveFilterCategoryFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                else -> if (moveFocus(FocusMove.RIGHT)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> {
                    confirmFilterSelection()
                    InputResult.HANDLED
                }
                state.showQuickMenu -> confirmQuickMenuSelection(onGameSelect)
                else -> {
                    state.focusedGame?.let { game -> onGameSelect(game.id) }
                    InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            return when {
                state.showFilterMenu -> {
                    toggleFilterMenu()
                    InputResult.HANDLED
                }
                state.showQuickMenu -> {
                    toggleQuickMenu()
                    InputResult.HANDLED
                }
                else -> {
                    onBack()
                    InputResult.HANDLED
                }
            }
        }

        override fun onMenu(): InputResult {
            if (_uiState.value.showQuickMenu) {
                toggleQuickMenu()
                return InputResult.UNHANDLED
            }
            if (_uiState.value.showFilterMenu) {
                toggleFilterMenu()
                return InputResult.UNHANDLED
            }
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val game = _uiState.value.focusedGame ?: return InputResult.UNHANDLED
            if (_uiState.value.showQuickMenu || _uiState.value.showFilterMenu) return InputResult.UNHANDLED
            toggleFavorite(game.id)
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (_uiState.value.showQuickMenu) return InputResult.HANDLED
            if (_uiState.value.showFilterMenu) {
                clearCurrentCategoryFilters()
                return InputResult.HANDLED
            }
            toggleFilterMenu()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (_uiState.value.focusedGame != null) {
                toggleQuickMenu()
            }
            return InputResult.HANDLED
        }

        override fun onPrevSection(): InputResult {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(-5)
                state.showQuickMenu -> return InputResult.HANDLED
                else -> previousPlatform()
            }
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            when {
                state.showFilterMenu -> moveFilterOptionFocus(5)
                state.showQuickMenu -> return InputResult.HANDLED
                else -> nextPlatform()
            }
            return InputResult.HANDLED
        }
    }
}
