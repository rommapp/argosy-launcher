package com.nendo.argosy.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.GameSection
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.computeSections
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.common.GridFocusNavigator
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameGradientRequest
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.common.RefreshAndroidResult
import com.nendo.argosy.ui.screens.common.DiscPickerState
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.ui.screens.gamedetail.CollectionItemUi
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import com.nendo.argosy.ui.screens.home.toHomePlatformUi
import com.nendo.argosy.ui.util.GridUtils
import com.nendo.argosy.ui.ModalResetSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    SORT("Sort"),
    SEARCH("Search"),
    SOURCE("Source"),
    GENRE("Genre"),
    PLAYERS("Players")
}

enum class SourceFilter(val label: String) {
    ALL("All Games"),
    PLAYABLE("Playable"),
    FAVORITES("Favorites"),
    HIDDEN("Hidden")
}

data class ActiveFilters(
    val searchQuery: String = "",
    val source: SourceFilter = SourceFilter.ALL,
    val genres: Set<String> = emptySet(),
    val players: Set<String> = emptySet(),
    val sort: ActiveSort = ActiveSort()
) {
    val activeCount: Int
        get() = listOf(
            if (searchQuery.isNotEmpty()) 1 else 0,
            if (source != SourceFilter.ALL) 1 else 0,
            genres.size,
            players.size,
            if (sort.option != SortOption.TITLE) 1 else 0
        ).sum()

    val summary: String
        get() = when {
            activeCount == 0 -> "All Games"
            activeCount == 1 -> when {
                searchQuery.isNotEmpty() -> "\"$searchQuery\""
                source != SourceFilter.ALL -> source.label
                sort.option != SortOption.TITLE -> sort.option.label
                genres.isNotEmpty() -> genres.first()
                players.isNotEmpty() -> players.first()
                else -> "All Games"
            }
            else -> "$activeCount filters"
        }
}

data class FilterOptions(
    val genres: List<String> = emptyList(),
    val players: List<String> = emptyList()
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

sealed interface LibraryGridItem {
    data class Header(val label: String) : LibraryGridItem
    data class Game(val game: LibraryGameUi, val gameIndex: Int) : LibraryGridItem
}

data class LibraryGameUi(
    val id: Long,
    val title: String,
    val sortTitle: String,
    val platformId: Long,
    val platformSlug: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val gradientColors: Pair<Color, Color>? = null,
    val source: GameSource,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val isRommGame: Boolean,
    val isAndroidApp: Boolean,
    val emulatorName: String?,
    val needsInstall: Boolean = false,
    val isHidden: Boolean = false
) {
    val sourceIcon: ImageVector?
        get() = when (source) {
            GameSource.LOCAL_ONLY -> Icons.Default.Folder
            GameSource.ROMM_SYNCED -> Icons.Default.CheckCircle
            GameSource.ROMM_REMOTE -> null
            GameSource.STEAM -> Icons.Default.Cloud
            GameSource.ANDROID_APP -> null
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
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val isLoading: Boolean = true,
    val activeFilters: ActiveFilters = ActiveFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val filterCategoryIndex: Int = 0,
    val filterOptionIndex: Int = 0,
    val syncOverlayState: SyncOverlayState? = null,
    val discPickerState: DiscPickerState? = null,
    val discPickerFocusIndex: Int = 0,
    val isTouchMode: Boolean = false,
    val hasSelectedGame: Boolean = false,
    val screenWidthDp: Int = 0,
    val recentSearches: List<String> = emptyList(),
    val repairedCoverPaths: Map<Long, String> = emptyMap(),
    val showAddToCollectionModal: Boolean = false,
    val collectionGameId: Long? = null,
    val collections: List<CollectionItemUi> = emptyList(),
    val collectionModalFocusIndex: Int = 0,
    val showCreateCollectionDialog: Boolean = false,
    val gridItems: List<LibraryGridItem> = emptyList(),
    val sectionLabels: List<String> = emptyList(),
    val currentSectionLabel: String = "",
    val showSectionOverlay: Boolean = false,
    val overlaySectionLabel: String = "",
    val sectionJumpTrigger: Int = 0
) {
    val showSectionSidebar: Boolean
        get() = sectionLabels.size >= 3

    val columnsCount: Int
        get() = GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    val gridSpacingDp: Int
        get() = GridUtils.getGridSpacingDp(gridDensity)

    val currentPlatform: HomePlatformUi?
        get() = if (currentPlatformIndex >= 0) platforms.getOrNull(currentPlatformIndex) else null

    val focusedGame: LibraryGameUi?
        get() = games.getOrNull(focusedIndex)

    val currentFilterCategory: FilterCategory
        get() = FilterCategory.entries.getOrElse(filterCategoryIndex) { FilterCategory.SOURCE }

    val currentCategoryOptions: List<String>
        get() = when (currentFilterCategory) {
            FilterCategory.SORT -> SortOption.entries.map { option ->
                val directionIndicator = if (option == activeFilters.sort.option) {
                    if (activeFilters.sort.descending) " v" else " ^"
                } else ""
                option.label + directionIndicator
            }
            FilterCategory.SEARCH -> recentSearches
            FilterCategory.SOURCE -> SourceFilter.entries.map { it.label }
            FilterCategory.GENRE -> filterOptions.genres
            FilterCategory.PLAYERS -> filterOptions.players
        }

    val isCurrentCategoryMultiSelect: Boolean
        get() = currentFilterCategory !in listOf(FilterCategory.SORT, FilterCategory.SOURCE, FilterCategory.SEARCH)

    val selectedSourceIndex: Int
        get() = activeFilters.source.ordinal

    val selectedSortIndex: Int
        get() = activeFilters.sort.option.ordinal

    val selectedOptionsInCurrentCategory: Set<String>
        get() = when (currentFilterCategory) {
            FilterCategory.SORT -> {
                val option = activeFilters.sort.option
                val indicator = if (activeFilters.sort.descending) " v" else " ^"
                setOf(option.label + indicator)
            }
            FilterCategory.SEARCH -> emptySet()
            FilterCategory.SOURCE -> emptySet()
            FilterCategory.GENRE -> activeFilters.genres
            FilterCategory.PLAYERS -> activeFilters.players
        }

    val currentCategoryActiveCount: Int
        get() = when (currentFilterCategory) {
            FilterCategory.SORT -> if (activeFilters.sort.option != SortOption.TITLE) 1 else 0
            FilterCategory.SEARCH -> if (activeFilters.searchQuery.isNotEmpty()) 1 else 0
            FilterCategory.SOURCE -> if (activeFilters.source != SourceFilter.ALL) 1 else 0
            FilterCategory.GENRE -> activeFilters.genres.size
            FilterCategory.PLAYERS -> activeFilters.players.size
        }

    val availableCategories: List<FilterCategory>
        get() = FilterCategory.entries.filter { category ->
            when (category) {
                FilterCategory.SORT -> true
                FilterCategory.SEARCH -> true
                FilterCategory.SOURCE -> true
                FilterCategory.GENRE -> filterOptions.genres.isNotEmpty()
                FilterCategory.PLAYERS -> filterOptions.players.isNotEmpty()
            }
        }
}

private const val TAG = "LibraryVM"

sealed class LibraryEvent {
    data class NavigateToLaunch(val gameId: Long, val channelName: String? = null) : LibraryEvent()
    data class LaunchIntent(val intent: Intent) : LibraryEvent()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val collectionRepository: CollectionRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val notificationManager: NotificationManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val romMRepository: RomMRepository,
    private val playStoreService: PlayStoreService,
    private val imageCacheManager: ImageCacheManager,
    private val apkInstallManager: ApkInstallManager,
    private val syncPlatformUseCase: SyncPlatformUseCase,
    private val repairImageCacheUseCase: RepairImageCacheUseCase,
    private val modalResetSignal: ModalResetSignal,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val emulatorDetector: EmulatorDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var gamesJob: Job? = null
    private var pendingInitialPlatformId: Long? = null
    private var pendingInitialSourceFilter: SourceFilter? = null
    private var cachedPlatformDisplayNames: Map<Long, String> = emptyMap()

    private val pendingCoverRepairs = mutableSetOf<Long>()

    fun repairCoverImage(gameId: Long, failedPath: String) {
        if (pendingCoverRepairs.contains(gameId)) return
        pendingCoverRepairs.add(gameId)

        viewModelScope.launch {
            val repairedUrl = repairImageCacheUseCase.repairCover(gameId, failedPath)
            if (repairedUrl != null) {
                _uiState.update { state ->
                    state.copy(
                        repairedCoverPaths = state.repairedCoverPaths + (gameId to repairedUrl)
                    )
                }
            }
            pendingCoverRepairs.remove(gameId)
        }
    }

    init {
        modalResetSignal.signal.onEach {
            resetMenus()
        }.launchIn(viewModelScope)

        loadPlatforms()
        loadFilterOptions()
        observeGridDensity()
        observeSyncOverlay()
        observeCollectionModal()
        observeGradientChanges()
    }

    private fun resetMenus() {
        _uiState.update {
            it.copy(
                showFilterMenu = false,
                showQuickMenu = false,
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false
            )
        }
    }

    private fun observeSyncOverlay() {
        viewModelScope.launch {
            gameLaunchDelegate.syncOverlayState.collect { overlayState ->
                _uiState.update { it.copy(syncOverlayState = overlayState) }
            }
        }
        viewModelScope.launch {
            gameLaunchDelegate.discPickerState.collect { pickerState ->
                _uiState.update { it.copy(discPickerState = pickerState) }
            }
        }
    }

    private fun observeCollectionModal() {
        viewModelScope.launch {
            collectionModalDelegate.state.collect { modalState ->
                _uiState.update {
                    it.copy(
                        showAddToCollectionModal = modalState.isVisible,
                        collectionGameId = if (modalState.gameId != 0L) modalState.gameId else null,
                        collections = modalState.collections,
                        collectionModalFocusIndex = modalState.focusIndex,
                        showCreateCollectionDialog = modalState.showCreateDialog
                    )
                }
            }
        }
    }

    private fun observeGradientChanges() {
        viewModelScope.launch {
            gradientExtractionDelegate.gradients.collect { gradients ->
                _uiState.update { state ->
                    state.copy(
                        games = state.games.map { game ->
                            gradients[game.id]?.let { colors ->
                                game.copy(gradientColors = colors)
                            } ?: game
                        }
                    )
                }
            }
        }
    }

    fun selectDisc(discPath: String) {
        gameLaunchDelegate.selectDisc(viewModelScope, discPath)
    }

    fun dismissDiscPicker() {
        gameLaunchDelegate.dismissDiscPicker()
    }

    fun setDiscPickerFocusIndex(index: Int) {
        _uiState.update { it.copy(discPickerFocusIndex = index) }
    }

    private fun observeGridDensity() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collectLatest { prefs ->
                gradientExtractionDelegate.updatePreferences(prefs.gradientPreset, prefs.boxArtBorderStyle)
                _uiState.update {
                    it.copy(
                        gridDensity = prefs.gridDensity,
                        recentSearches = prefs.libraryRecentSearches
                    )
                }
            }
        }
    }

    private fun extractGradientsForVisibleGames(focusedIndex: Int) {
        val games = _uiState.value.games
        if (games.isEmpty()) return
        val cols = _uiState.value.columnsCount
        val buffer = cols * 3
        val requests = games.map { GameGradientRequest(it.id, it.coverPath) }
        gradientExtractionDelegate.extractForVisibleGames(viewModelScope, requests, focusedIndex, buffer)
    }

    private fun loadPlatforms() {
        viewModelScope.launch {
            Log.d(TAG, "loadPlatforms: starting observation")
            platformRepository.observeVisiblePlatforms().collect { platforms ->
                Log.d(TAG, "loadPlatforms: received ${platforms.size} platforms")
                cachedPlatformDisplayNames = platforms.associate { it.id to it.getDisplayName() }
                val platformUis = platforms.map { it.toHomePlatformUi(emulatorDetector) }

                val pendingPlatformIndex = pendingInitialPlatformId?.let { platformId ->
                    platformUis.indexOfFirst { it.id == platformId }.takeIf { it >= 0 }
                }

                _uiState.update { state ->
                    val currentPlatformId = state.platforms.getOrNull(state.currentPlatformIndex)?.id

                    val newPlatformIndex = when {
                        pendingPlatformIndex != null -> pendingPlatformIndex
                        state.currentPlatformIndex < 0 -> state.currentPlatformIndex
                        state.platforms.isEmpty() -> 0
                        else -> {
                            currentPlatformId?.let { id ->
                                platformUis.indexOfFirst { it.id == id }
                            }?.takeIf { it >= 0 } ?: 0
                        }
                    }

                    val platformStillExists = currentPlatformId != null &&
                        platformUis.any { it.id == currentPlatformId }
                    val newGameIndex = if (platformStillExists) state.focusedIndex else 0

                    state.copy(
                        platforms = platformUis,
                        currentPlatformIndex = newPlatformIndex,
                        focusedIndex = newGameIndex,
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
        // Fallback session end handling in case Android killed Argosy while emulator was running
        // (normal flow goes through LaunchScreen, but if app was killed, user returns here directly)
        gameLaunchDelegate.handleSessionEnd(viewModelScope)

        if (romMRepository.isConnected()) {
            viewModelScope.launch {
                romMRepository.refreshFavoritesIfNeeded()
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val genres = gameRepository.getDistinctGenres()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val players = gameRepository.getDistinctGameModes()
                .flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            Log.d(TAG, "loadFilterOptions: genres=${genres.size}, players=${players.size}")

            _uiState.update { state ->
                state.copy(
                    filterOptions = FilterOptions(
                        genres = genres,
                        players = players
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
            val baseFlow = if (filters.source == SourceFilter.HIDDEN) {
                if (platformIndex >= 0) {
                    val platformId = state.platforms[platformIndex].id
                    gameRepository.observeHiddenByPlatformList(platformId)
                } else {
                    gameRepository.observeHiddenList()
                }
            } else if (platformIndex >= 0) {
                val platformId = state.platforms[platformIndex].id
                when (filters.source) {
                    SourceFilter.PLAYABLE -> gameRepository.observePlayableByPlatformList(platformId)
                    SourceFilter.FAVORITES -> gameRepository.observeFavoritesByPlatformList(platformId)
                    else -> gameRepository.observeByPlatformList(platformId)
                }
            } else {
                when (filters.source) {
                    SourceFilter.ALL -> gameRepository.observeAllList()
                    SourceFilter.PLAYABLE -> gameRepository.observePlayableList()
                    SourceFilter.FAVORITES -> gameRepository.observeFavoritesList()
                    SourceFilter.HIDDEN -> gameRepository.observeHiddenList()
                }
            }

            baseFlow
                .catch { e ->
                    Log.e(TAG, "Error loading games, retrying: ${e.message}")
                    kotlinx.coroutines.delay(100)
                    emitAll(baseFlow)
                }
                .collectLatest { games ->
                    val filteredGames = games.filter { game ->
                        val matchesSearch = filters.searchQuery.isEmpty() ||
                            game.title.contains(filters.searchQuery, ignoreCase = true)
                        val matchesGenre = filters.genres.isEmpty() ||
                            filters.genres.contains(game.genre)
                        val matchesPlayers = filters.players.isEmpty() ||
                            game.gameModes?.split(",")?.map { it.trim() }?.any { it in filters.players } == true
                        matchesSearch && matchesGenre && matchesPlayers
                    }

                    val sections = computeSections(filteredGames, filters.sort)
                    val sectionLabels = sections.map { it.sidebarLabel }

                    var gameOffset = 0
                    val gridItems = sections.flatMap { section ->
                        val header = LibraryGridItem.Header(section.label)
                        val gameItems = section.games.mapIndexed { i, game ->
                            LibraryGridItem.Game(game.toUi(cachedPlatformDisplayNames), gameIndex = gameOffset + i)
                        }
                        gameOffset += section.games.size
                        listOf(header) + gameItems
                    }

                    val allGamesSorted = sections.flatMap { it.games }
                    val gamesList = allGamesSorted.map { it.toUi(cachedPlatformDisplayNames) }

                    Log.d(TAG, "loadGames: ${games.size} total, ${filteredGames.size} after filters, ${sections.size} sections")
                    _uiState.update { uiState ->
                        val shouldResetFocus = uiState.games.isEmpty()
                        val newFocusedIndex = if (shouldResetFocus) 0 else uiState.focusedIndex.coerceAtMost((gamesList.size - 1).coerceAtLeast(0))
                        val currentSectionLabel = computeSectionLabelForGameIndex(newFocusedIndex, sections)
                        uiState.copy(
                            games = gamesList,
                            focusedIndex = newFocusedIndex,
                            gridItems = gridItems,
                            sectionLabels = sectionLabels,
                            currentSectionLabel = currentSectionLabel
                        )
                    }
                    extractGradientsForVisibleGames(_uiState.value.focusedIndex)
                }
        }
    }

    private fun computeSectionLabelForGameIndex(gameIndex: Int, sections: List<GameSection>): String {
        var offset = 0
        for (section in sections) {
            if (gameIndex < offset + section.games.size) return section.sidebarLabel
            offset += section.games.size
        }
        return sections.firstOrNull()?.sidebarLabel ?: ""
    }

    private fun updateCurrentSectionFromFocus() {
        val state = _uiState.value
        val gameIndex = state.focusedIndex
        var offset = 0
        for (item in state.gridItems) {
            if (item is LibraryGridItem.Game && item.gameIndex == gameIndex) {
                val label = findSectionLabelForGridItem(state.gridItems, offset)
                if (label != state.currentSectionLabel) {
                    _uiState.update { it.copy(currentSectionLabel = label) }
                }
                return
            }
            offset++
        }
    }

    private fun findSectionLabelForGridItem(gridItems: List<LibraryGridItem>, gridIndex: Int): String {
        for (i in gridIndex downTo 0) {
            val item = gridItems[i]
            if (item is LibraryGridItem.Header) return item.label
        }
        return ""
    }

    private var sectionOverlayJob: Job? = null

    private fun showSectionOverlay(label: String) {
        sectionOverlayJob?.cancel()
        _uiState.update { it.copy(showSectionOverlay = true, overlaySectionLabel = label) }
        sectionOverlayJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(showSectionOverlay = false) }
        }
    }

    fun jumpToSection(sectionLabel: String, showOverlay: Boolean = true) {
        resetStickyColumn()
        val state = _uiState.value
        val firstGameInSection = state.gridItems.firstOrNull { item ->
            item is LibraryGridItem.Game && run {
                val headerIdx = state.gridItems.indexOf(item) - 1
                headerIdx >= 0 && state.gridItems[headerIdx].let { it is LibraryGridItem.Header && it.label == sectionLabel }
            }
        } as? LibraryGridItem.Game

        if (firstGameInSection == null) {
            val targetGame = state.gridItems.filterIsInstance<LibraryGridItem.Game>()
                .firstOrNull { game ->
                    val gIdx = state.gridItems.indexOf(game)
                    findSectionLabelForGridItem(state.gridItems, gIdx) == sectionLabel
                }
            if (targetGame != null) {
                _uiState.update {
                    it.copy(
                        focusedIndex = targetGame.gameIndex,
                        currentSectionLabel = sectionLabel,
                        isTouchMode = false,
                        sectionJumpTrigger = it.sectionJumpTrigger + 1
                    )
                }
                extractGradientsForVisibleGames(targetGame.gameIndex)
                if (showOverlay) showSectionOverlay(sectionLabel)
            }
            return
        }

        _uiState.update {
            it.copy(
                focusedIndex = firstGameInSection.gameIndex,
                currentSectionLabel = sectionLabel,
                isTouchMode = false,
                sectionJumpTrigger = it.sectionJumpTrigger + 1
            )
        }
        extractGradientsForVisibleGames(firstGameInSection.gameIndex)
        if (showOverlay) showSectionOverlay(sectionLabel)
    }

    fun jumpToNextSection() {
        val state = _uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return

        val currentIndex = labels.indexOf(state.currentSectionLabel)
        val nextIndex = if (currentIndex < 0 || currentIndex >= labels.lastIndex) 0 else currentIndex + 1
        jumpToSection(labels[nextIndex])
    }

    fun jumpToPreviousSection() {
        val state = _uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return

        val currentIndex = labels.indexOf(state.currentSectionLabel)
        val prevIndex = if (currentIndex <= 0) labels.lastIndex else currentIndex - 1
        jumpToSection(labels[prevIndex])
    }

    fun gameIndexToGridIndex(gameIndex: Int): Int {
        val gridItems = _uiState.value.gridItems
        for ((gridIdx, item) in gridItems.withIndex()) {
            if (item is LibraryGridItem.Game && item.gameIndex == gameIndex) return gridIdx
        }
        return 0
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
        resetStickyColumn()
        _uiState.update { it.copy(currentPlatformIndex = nextIndex, focusedIndex = 0) }
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
        resetStickyColumn()
        _uiState.update { it.copy(currentPlatformIndex = prevIndex, focusedIndex = 0) }
        loadGames()
    }

    fun syncCurrentPlatform() {
        val platform = _uiState.value.currentPlatform ?: return
        viewModelScope.launch {
            syncPlatformUseCase(platform.id, platform.name)
            loadGames()
        }
    }

    fun setInitialPlatform(platformId: Long) {
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

    private val gridNav = GridFocusNavigator()

    fun resetStickyColumn() { gridNav.resetStickyColumn() }

    fun moveFocus(direction: FocusMove): Boolean {
        val state = _uiState.value
        if (state.games.isEmpty()) return false

        val gridDirection = when (direction) {
            FocusMove.UP -> GridDirection.UP
            FocusMove.DOWN -> GridDirection.DOWN
            FocusMove.LEFT -> GridDirection.LEFT
            FocusMove.RIGHT -> GridDirection.RIGHT
        }
        val rows = GridFocusNavigator.buildGridRows(
            state.gridItems, state.columnsCount,
            isHeader = { it is LibraryGridItem.Header },
            gameIndex = { (it as LibraryGridItem.Game).gameIndex }
        )
        val newIndex = gridNav.navigate(gridDirection, state.focusedIndex, rows) ?: return false

        _uiState.update { it.copy(focusedIndex = newIndex, lastFocusMove = direction, isTouchMode = false) }
        updateCurrentSectionFromFocus()
        extractGradientsForVisibleGames(newIndex)
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
            val options = state.currentCategoryOptions
            if (options.isEmpty()) return@update state
            val maxIndex = options.size - 1
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
            FilterCategory.SORT -> {
                val selectedOption = SortOption.entries.getOrNull(optionIndex) ?: return
                val currentSort = state.activeFilters.sort
                val newSort = if (selectedOption == currentSort.option) {
                    currentSort.copy(descending = !currentSort.descending)
                } else {
                    ActiveSort(option = selectedOption, descending = selectedOption.defaultDescending)
                }
                state.activeFilters.copy(sort = newSort)
            }
            FilterCategory.SEARCH -> {
                val query = options.getOrNull(optionIndex) ?: return
                state.activeFilters.copy(searchQuery = query)
            }
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
        }

        _uiState.update { it.copy(activeFilters = newFilters) }
        loadGames()
    }

    fun clearCurrentCategoryFilters() {
        val state = _uiState.value
        val category = state.currentFilterCategory

        val newFilters = when (category) {
            FilterCategory.SORT -> state.activeFilters.copy(sort = ActiveSort())
            FilterCategory.SEARCH -> state.activeFilters.copy(searchQuery = "")
            FilterCategory.SOURCE -> state.activeFilters.copy(source = SourceFilter.ALL)
            FilterCategory.GENRE -> state.activeFilters.copy(genres = emptySet())
            FilterCategory.PLAYERS -> state.activeFilters.copy(players = emptySet())
        }

        _uiState.update { it.copy(activeFilters = newFilters) }
        loadGames()
    }

    fun clearAllFilters() {
        _uiState.update { it.copy(activeFilters = ActiveFilters(), filterOptionIndex = 0) }
        loadGames()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(activeFilters = it.activeFilters.copy(searchQuery = query)) }
        loadGames()
    }

    fun applySearchQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            preferencesRepository.addLibraryRecentSearch(query)
        }
        _uiState.update { it.copy(activeFilters = it.activeFilters.copy(searchQuery = query)) }
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
            val game = it.focusedGame ?: return@update it
            val canRefresh = game.isRommGame || game.isAndroidApp
            val hasDelete = game.isDownloaded || game.needsInstall
            var maxIndex = 5
            if (canRefresh) maxIndex++
            if (hasDelete) maxIndex++
            val newIndex = (it.quickMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(quickMenuFocusIndex = newIndex)
        }
    }

    fun confirmQuickMenuSelection(onGameSelect: (Long) -> Unit): InputResult {
        val game = _uiState.value.focusedGame ?: return InputResult.HANDLED
        val index = _uiState.value.quickMenuFocusIndex
        val isRommGame = game.isRommGame
        val isAndroidApp = game.isAndroidApp
        val canRefresh = isRommGame || isAndroidApp
        val isDownloaded = game.isDownloaded
        val needsInstall = game.needsInstall

        var currentIdx = 0
        val playIdx = currentIdx++
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val addToCollectionIdx = currentIdx++
        val refreshIdx = if (canRefresh) currentIdx++ else -1
        val resyncPlatformIdx = currentIdx++
        val deleteIdx = if (isDownloaded || needsInstall) currentIdx++ else -1
        val hideIdx = currentIdx

        return when (index) {
            playIdx -> {
                when {
                    needsInstall -> installApk(game.id)
                    isDownloaded -> launchGame(game.id)
                    else -> downloadGame(game.id)
                }
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
            addToCollectionIdx -> {
                toggleQuickMenu()
                showAddToCollectionModal(game.id)
                InputResult.HANDLED
            }
            refreshIdx -> {
                if (isAndroidApp) refreshAndroidGameData(game.id) else refreshGameData(game.id)
                InputResult.HANDLED
            }
            resyncPlatformIdx -> {
                syncCurrentPlatform()
                toggleQuickMenu()
                InputResult.HANDLED
            }
            deleteIdx -> {
                if (isAndroidApp) uninstallAndroidApp(game.id) else deleteLocalFile(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            hideIdx -> {
                if (game.isHidden) unhideGame(game.id) else hideGame(game.id)
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

    fun unhideGame(gameId: Long) {
        viewModelScope.launch {
            gameActions.unhideGame(gameId)
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

    fun refreshAndroidGameData(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.refreshAndroidGameData(gameId)) {
                is RefreshAndroidResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    loadGames()
                }
                is RefreshAndroidResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleQuickMenu()
        }
    }

    fun uninstallAndroidApp(gameId: Long) {
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val packageName = game.packageName ?: return@launch

            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            _events.emit(LibraryEvent.LaunchIntent(intent))
        }
    }

    fun launchGame(gameId: Long, channelName: String? = null) {
        viewModelScope.launch {
            _events.emit(LibraryEvent.NavigateToLaunch(gameId, channelName))
        }
    }

    fun downloadGame(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> {
                    notificationManager.showSuccess("Game already downloaded")
                }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
                is DownloadResult.ExtractionFailed -> {
                    notificationManager.showError("Extraction failed. Open game details to retry.")
                }
            }
        }
    }

    fun installApk(gameId: Long) {
        viewModelScope.launch {
            val success = apkInstallManager.installApkForGame(gameId)
            if (!success) {
                notificationManager.showError("Could not install APK")
            }
        }
    }

    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            gameActions.toggleFavorite(gameId)
            loadGames()
        }
    }

    fun showAddToCollectionModal(gameId: Long) {
        collectionModalDelegate.show(viewModelScope, gameId)
    }

    fun dismissAddToCollectionModal() {
        collectionModalDelegate.dismiss()
    }

    fun moveCollectionFocusUp() {
        collectionModalDelegate.moveFocusUp()
    }

    fun moveCollectionFocusDown() {
        collectionModalDelegate.moveFocusDown()
    }

    fun confirmCollectionSelection() {
        collectionModalDelegate.confirmSelection(viewModelScope)
    }

    fun toggleGameInCollection(collectionId: Long) {
        collectionModalDelegate.toggleCollection(viewModelScope, collectionId)
    }

    fun showCreateCollectionFromModal() {
        collectionModalDelegate.showCreateDialog()
    }

    fun hideCreateCollectionDialog() {
        collectionModalDelegate.hideCreateDialog()
    }

    fun createCollectionFromModal(name: String) {
        collectionModalDelegate.createAndAdd(viewModelScope, name)
    }

    private fun GameEntity.toUi(platformDisplayNames: Map<Long, String> = emptyMap()): LibraryGameUi {
        return LibraryGameUi(
            id = id,
            title = title,
            sortTitle = sortTitle,
            platformId = platformId,
            platformSlug = platformSlug,
            platformDisplayName = platformDisplayNames[platformId] ?: platformSlug,
            coverPath = coverPath,
            gradientColors = gradientExtractionDelegate.getGradient(id),
            source = source,
            isFavorite = isFavorite,
            isDownloaded = localPath != null || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isRommGame = rommId != null,
            isAndroidApp = source == GameSource.ANDROID_APP || platformSlug == "android",
            emulatorName = null,
            needsInstall = platformSlug == "android" && localPath != null && packageName == null && source != GameSource.ANDROID_APP,
            isHidden = isHidden
        )
    }

    private fun GameListItem.toUi(platformDisplayNames: Map<Long, String> = emptyMap()): LibraryGameUi {
        return LibraryGameUi(
            id = id,
            title = title,
            sortTitle = sortTitle,
            platformId = platformId,
            platformSlug = platformSlug,
            platformDisplayName = platformDisplayNames[platformId] ?: platformSlug,
            coverPath = coverPath,
            gradientColors = gradientExtractionDelegate.getGradient(id),
            source = source,
            isFavorite = isFavorite,
            isDownloaded = isDownloaded || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isRommGame = rommId != null,
            isAndroidApp = source == GameSource.ANDROID_APP || platformSlug == "android",
            emulatorName = null,
            needsInstall = platformSlug == "android" && localPath != null && packageName == null && source != GameSource.ANDROID_APP,
            isHidden = isHidden
        )
    }

    fun enterTouchMode() {
        _uiState.update { it.copy(isTouchMode = true, hasSelectedGame = false) }
    }

    fun exitTouchMode() {
        _uiState.update { it.copy(isTouchMode = false) }
    }

    fun updateScreenWidth(widthDp: Int) {
        if (_uiState.value.screenWidthDp != widthDp) {
            _uiState.update { it.copy(screenWidthDp = widthDp) }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(hasSelectedGame = false) }
    }

    fun setFocusIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.games.size) return
        if (index == state.focusedIndex && state.hasSelectedGame) return
        resetStickyColumn()
        _uiState.update { it.copy(focusedIndex = index, hasSelectedGame = true) }
        soundManager.play(SoundType.NAVIGATE)
    }

    fun handleItemTap(index: Int, onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        if (index < 0 || index >= state.games.size) return

        if (!state.hasSelectedGame || index != state.focusedIndex) {
            resetStickyColumn()
            _uiState.update { it.copy(focusedIndex = index, hasSelectedGame = true, isTouchMode = true) }
            soundManager.play(SoundType.NAVIGATE)
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
            resetStickyColumn()
            _uiState.update { it.copy(focusedIndex = index, hasSelectedGame = true, isTouchMode = true) }
        }
        toggleQuickMenu()
    }

    fun createInputHandler(
        isDefaultView: Boolean,
        onGameSelect: (Long) -> Unit,
        onNavigateToDefault: () -> Unit,
        onDrawerToggle: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> { moveCollectionFocusUp(); InputResult.HANDLED }
                state.showFilterMenu -> { moveFilterOptionFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(-1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.UP)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> { moveCollectionFocusDown(); InputResult.HANDLED }
                state.showFilterMenu -> { moveFilterOptionFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.DOWN)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> InputResult.HANDLED
                state.showFilterMenu -> { moveFilterCategoryFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                else -> if (moveFocus(FocusMove.LEFT)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> InputResult.HANDLED
                state.showFilterMenu -> { moveFilterCategoryFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                else -> if (moveFocus(FocusMove.RIGHT)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> {
                    confirmCollectionSelection()
                    InputResult.HANDLED
                }
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
                state.showAddToCollectionModal -> {
                    dismissAddToCollectionModal()
                    InputResult.HANDLED
                }
                state.showFilterMenu -> {
                    if (state.currentFilterCategory == FilterCategory.SEARCH &&
                        state.activeFilters.searchQuery.isNotEmpty()) {
                        applySearchQuery(state.activeFilters.searchQuery)
                    }
                    toggleFilterMenu()
                    InputResult.HANDLED
                }
                state.showQuickMenu -> {
                    toggleQuickMenu()
                    InputResult.HANDLED
                }
                isDefaultView -> InputResult.UNHANDLED
                else -> {
                    onNavigateToDefault()
                    InputResult.HANDLED
                }
            }
        }

        override fun onMenu(): InputResult {
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
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
            if (_uiState.value.showAddToCollectionModal || _uiState.value.showQuickMenu || _uiState.value.showFilterMenu) return InputResult.UNHANDLED
            toggleFavorite(game.id)
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (_uiState.value.showQuickMenu) return InputResult.HANDLED
            if (_uiState.value.showFilterMenu) {
                clearCurrentCategoryFilters()
                return InputResult.HANDLED
            }
            toggleFilterMenu()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (_uiState.value.focusedGame != null) {
                toggleQuickMenu()
            }
            return InputResult.HANDLED
        }

        override fun onPrevSection(): InputResult {
            val state = _uiState.value
            when {
                state.showAddToCollectionModal -> return InputResult.HANDLED
                state.showFilterMenu -> moveFilterOptionFocus(-5)
                state.showQuickMenu -> return InputResult.HANDLED
                else -> previousPlatform()
            }
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            when {
                state.showAddToCollectionModal -> return InputResult.HANDLED
                state.showFilterMenu -> moveFilterOptionFocus(5)
                state.showQuickMenu -> return InputResult.HANDLED
                else -> nextPlatform()
            }
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult {
            val state = _uiState.value
            if (state.showAddToCollectionModal || state.showFilterMenu || state.showQuickMenu) {
                return InputResult.HANDLED
            }
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToPreviousSection()
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }

        override fun onNextTrigger(): InputResult {
            val state = _uiState.value
            if (state.showAddToCollectionModal || state.showFilterMenu || state.showQuickMenu) {
                return InputResult.HANDLED
            }
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToNextSection()
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }
    }
}
