package com.nendo.argosy.ui.screens.collections

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.local.entity.toListItem
import com.nendo.argosy.data.model.SortPartition
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.splitRegions
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.common.GridFocusNavigator
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.ui.common.toLibraryGameUi
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.screens.library.ActiveFilters
import com.nendo.argosy.ui.screens.library.FILTER_OPTION_PAGE
import com.nendo.argosy.ui.screens.library.FilterCategory
import com.nendo.argosy.ui.screens.library.FilterMemoryScope
import com.nendo.argosy.ui.screens.library.FilterOptions
import com.nendo.argosy.ui.screens.library.FilterPanelState
import com.nendo.argosy.ui.screens.library.FocusMove
import com.nendo.argosy.ui.screens.library.LibraryFilterIds
import com.nendo.argosy.ui.screens.library.LibraryFilterMemory
import com.nendo.argosy.ui.screens.library.LibraryGridItem
import com.nendo.argosy.ui.screens.library.LibraryListing
import com.nendo.argosy.ui.screens.library.PlatformRef
import com.nendo.argosy.ui.screens.library.admitsSource
import com.nendo.argosy.ui.screens.library.buildLibraryListing
import com.nendo.argosy.ui.screens.library.observeLibraryFilterIds
import com.nendo.argosy.ui.util.GridUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The filter panel's state on a collection or category screen: the panel itself, whether it is
 * open, and whether its search keyboard is up.
 */
data class GameSetFilterUi(
    val panel: FilterPanelState = FilterPanelState(),
    val showFilterMenu: Boolean = false,
    val isSearchKeyboardOpen: Boolean = false
)

/**
 * The game list preferences a collection shares with the library, plus the width that decides how
 * many columns the grid gets.
 */
data class GameSetLayout(
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val boxArt3d: Boolean = false,
    val screenWidthDp: Int = 0
) {
    val isListLayout: Boolean
        get() = libraryLayout == LibraryLayout.LIST

    val columnsCount: Int
        get() = if (isListLayout) 1 else GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    val gridSpacingDp: Int
        get() = GridUtils.getGridSpacingDp(gridDensity)
}

data class GameSetListing(
    val listing: LibraryListing = LibraryListing(),
    val totalCount: Int = 0,
    val rommGameIds: Set<Long> = emptySet(),
    val isLoaded: Boolean = false
)

private class GameSetBase(
    val entities: List<GameEntity>,
    val hiddenIds: Set<Long>,
    val platformNames: Map<Long, String>,
    val seriesNames: List<String>,
    val partition: SortPartition
)

/**
 * The library's filter panel, sort and grid focus for a set of games that is not the library:
 * a collection or a genre, series or mode category. Each screen builds one with its own
 * [FilterMemoryScope]; the library's own remembered filters are never read or written.
 */
class GameSetBrowser private constructor(
    private val scope: CoroutineScope,
    private val memoryScope: FilterMemoryScope,
    private val deps: Dependencies
) {
    class Dependencies @Inject constructor(
        val gameRepository: GameRepository,
        val collectionRepository: CollectionRepository,
        val platformRepository: PlatformRepository,
        val imageCacheManager: ImageCacheManager,
        val downloadFileStatusRepository: DownloadFileStatusRepository,
        val preferencesRepository: UserPreferencesRepository,
        val soundManager: SoundFeedbackManager,
        val filterMemory: LibraryFilterMemory,
        val modalResetSignal: ModalResetSignal
    ) {
        fun create(scope: CoroutineScope, memoryScope: FilterMemoryScope) =
            GameSetBrowser(scope, memoryScope, this)
    }

    private val _filter = MutableStateFlow(
        GameSetFilterUi(
            panel = FilterPanelState(
                activeFilters = deps.filterMemory.filtersFor(memoryScope) ?: ActiveFilters()
            )
        )
    )
    val filter: StateFlow<GameSetFilterUi> = _filter.asStateFlow()

    private val _layout = MutableStateFlow(GameSetLayout())
    val layout: StateFlow<GameSetLayout> = _layout.asStateFlow()

    private val partition = MutableStateFlow(SortPartition.NONE)

    private val gridNav = GridFocusNavigator()

    init {
        scope.launch {
            deps.preferencesRepository.userPreferences.collect { prefs ->
                partition.value = SortPartition(
                    installedFirst = prefs.sortInstalledFirst,
                    favoritesFirst = prefs.sortFavoritesFirst
                )
                _layout.update {
                    it.copy(
                        libraryLayout = prefs.libraryLayout,
                        gridDensity = prefs.gridDensity,
                        boxArt3d = prefs.libraryBoxArt3d
                    )
                }
                _filter.update { it.copy(panel = it.panel.copy(recentSearches = prefs.libraryRecentSearches)) }
            }
        }
        scope.launch {
            deps.modalResetSignal.signal.collect {
                _filter.update { it.copy(showFilterMenu = false, isSearchKeyboardOpen = false) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun listing(entities: Flow<List<GameEntity>>): Flow<GameSetListing> {
        val base = combine(
            entities,
            deps.gameRepository.observeHiddenList().map { hidden -> hidden.mapTo(HashSet()) { it.id } },
            deps.platformRepository.observeAllPlatforms().map { platforms ->
                platforms.associate { it.id to it.getDisplayName() }
            },
            partition
        ) { games, hiddenIds, platformNames, sortPartition ->
            GameSetBase(
                entities = games,
                hiddenIds = hiddenIds,
                platformNames = platformNames,
                seriesNames = deps.collectionRepository.getNamesByTypeForGames(
                    CollectionType.SERIES,
                    games.map { it.id }
                ),
                partition = sortPartition
            )
        }.onEach { publishFilterOptions(it) }

        val filters = _filter.map { it.panel.activeFilters }.distinctUntilChanged()

        return combine(base, filters) { set, active -> set to active }
            .flatMapLatest { (set, active) ->
                observeLibraryFilterIds(active, deps.gameRepository, deps.collectionRepository)
                    .map { ids -> buildListing(set, active, ids) }
            }
            .flowOn(Dispatchers.Default)
    }

    private suspend fun buildListing(
        set: GameSetBase,
        filters: ActiveFilters,
        ids: LibraryFilterIds
    ): GameSetListing {
        val items = set.entities.map { it.toListItem(isHidden = it.id in set.hiddenIds) }
        val listing = buildLibraryListing(
            games = items.filter { filters.source.admitsSource(it) },
            filters = filters,
            ids = ids,
            partition = set.partition
        ) { game ->
            game.toLibraryGameUi(
                downloadStatus = deps.downloadFileStatusRepository,
                art = deps.imageCacheManager.artFor(game.id),
                platformDisplayName = set.platformNames[game.platformId]
            )
        }
        return GameSetListing(
            listing = listing,
            totalCount = set.entities.size,
            rommGameIds = set.entities.filter { it.rommId != null }.mapTo(HashSet()) { it.id },
            isLoaded = true
        )
    }

    private fun publishFilterOptions(set: GameSetBase) {
        val options = FilterOptions(
            platforms = set.entities.distinctBy { it.platformId }
                .map { PlatformRef(it.platformId, set.platformNames[it.platformId] ?: it.platformSlug) }
                .sortedBy { it.label },
            genres = set.entities.mapNotNull { it.genre?.trim() }.filter { it.isNotBlank() }.distinct().sorted(),
            regions = set.entities.flatMap { splitRegions(it.regions) }.distinct().sorted(),
            series = set.seriesNames
        )
        val hiddenCount = set.entities.count { it.id in set.hiddenIds }
        _filter.update { it.copy(panel = it.panel.copy(filterOptions = options, hiddenGameCount = hiddenCount)) }
    }

    fun updateScreenWidth(widthDp: Int) {
        _layout.update { if (it.screenWidthDp == widthDp) it else it.copy(screenWidthDp = widthDp) }
    }

    fun resetStickyColumn() = gridNav.resetStickyColumn()

    fun moveFocus(direction: FocusMove, currentIndex: Int, listing: LibraryListing, columns: Int): Int? {
        if (listing.games.isEmpty()) return null
        val rows = GridFocusNavigator.buildGridRows(
            listing.gridItems,
            columns.coerceAtLeast(1),
            isHeader = { it is LibraryGridItem.Header },
            gameIndex = { (it as LibraryGridItem.Game).gameIndex }
        )
        val gridDirection = when (direction) {
            FocusMove.UP -> GridDirection.UP
            FocusMove.DOWN -> GridDirection.DOWN
            FocusMove.LEFT -> GridDirection.LEFT
            FocusMove.RIGHT -> GridDirection.RIGHT
        }
        return gridNav.navigate(gridDirection, currentIndex, rows)
    }

    private fun setFilters(filters: ActiveFilters) {
        _filter.update { it.copy(panel = it.panel.copy(activeFilters = filters)) }
        deps.filterMemory.remember(memoryScope, filters)
    }

    private fun updatePanel(transform: (FilterPanelState) -> FilterPanelState) {
        _filter.update { it.copy(panel = transform(it.panel)) }
    }

    fun toggleFilterMenu() {
        val opening = !_filter.value.showFilterMenu
        _filter.update {
            it.copy(
                showFilterMenu = opening,
                isSearchKeyboardOpen = false,
                panel = if (opening) it.panel.opened() else it.panel
            )
        }
        deps.soundManager.play(if (opening) SoundType.OPEN_MODAL else SoundType.CLOSE_MODAL)
    }

    fun closeFilterMenu() {
        if (_filter.value.showFilterMenu) toggleFilterMenu()
    }

    fun setFilterCategory(category: FilterCategory) = updatePanel { it.withCategory(category) }

    fun selectFilterOption(index: Int) {
        updatePanel { it.movedOption(index - it.optionIndex) }
        confirmFilterSelection()
    }

    fun confirmFilterSelection() {
        val panel = _filter.value.panel
        if (panel.isFilterSearchFieldFocused) {
            openSearchKeyboard()
            return
        }
        panel.confirmedFilters()?.let(::setFilters)
    }

    fun clearCurrentCategoryFilters() = setFilters(_filter.value.panel.clearedCategoryFilters())

    fun openSearchPanel() {
        if (!_filter.value.showFilterMenu) toggleFilterMenu()
        setFilterCategory(FilterCategory.SEARCH)
        openSearchKeyboard()
    }

    fun playNavigateSound() = deps.soundManager.play(SoundType.NAVIGATE)

    fun openSearchKeyboard() {
        _filter.update { it.copy(isSearchKeyboardOpen = true, panel = it.panel.copy(optionIndex = 0)) }
    }

    fun closeSearchKeyboard() {
        _filter.update { it.copy(isSearchKeyboardOpen = false) }
    }

    fun updateSearchQuery(query: String) =
        setFilters(_filter.value.panel.activeFilters.copy(searchQuery = query))

    private fun applySearchQuery(query: String) {
        if (query.isBlank()) return
        scope.launch { deps.preferencesRepository.addLibraryRecentSearch(query) }
        updateSearchQuery(query)
    }

    val filterMenuInput: InputHandler = object : InputHandler {
        override fun onUp(): InputResult = handled { updatePanel { it.movedOption(-1) } }
        override fun onDown(): InputResult = handled { updatePanel { it.movedOption(1) } }
        override fun onLeft(): InputResult = handled { updatePanel { it.movedCategory(-1) } }
        override fun onRight(): InputResult = handled { updatePanel { it.movedCategory(1) } }
        override fun onConfirm(): InputResult = handled { confirmFilterSelection() }
        override fun onContextMenu(): InputResult = handled { clearCurrentCategoryFilters() }
        override fun onMenu(): InputResult = handled { toggleFilterMenu() }
        override fun onPrevSection(): InputResult = handled { updatePanel { it.movedOption(-FILTER_OPTION_PAGE) } }
        override fun onNextSection(): InputResult = handled { updatePanel { it.movedOption(FILTER_OPTION_PAGE) } }
        override fun onBack(): InputResult = handled {
            val panel = _filter.value.panel
            if (panel.currentFilterCategory == FilterCategory.SEARCH && panel.activeFilters.searchQuery.isNotEmpty()) {
                applySearchQuery(panel.activeFilters.searchQuery)
            }
            toggleFilterMenu()
        }
        override fun onSecondaryAction(): InputResult = InputResult.HANDLED
        override fun onSelect(): InputResult = InputResult.HANDLED
        override fun onPrevTrigger(): InputResult = InputResult.HANDLED
        override fun onNextTrigger(): InputResult = InputResult.HANDLED
        override fun onLongConfirm(): InputResult = InputResult.HANDLED
        override fun onLongSelect(): InputResult = InputResult.HANDLED
        override fun onLeftStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickClick(): InputResult = InputResult.HANDLED
        override fun onRightStickUp(): InputResult = InputResult.HANDLED
        override fun onRightStickDown(): InputResult = InputResult.HANDLED
        override fun onRightStickLeft(): InputResult = InputResult.HANDLED
        override fun onRightStickRight(): InputResult = InputResult.HANDLED

        private fun handled(action: () -> Unit): InputResult {
            action()
            return InputResult.HANDLED
        }
    }

    fun routeInput(screen: InputHandler): InputHandler = object : InputHandler {
        private val active: InputHandler
            get() = if (_filter.value.showFilterMenu) filterMenuInput else screen

        override fun onUp() = active.onUp()
        override fun onDown() = active.onDown()
        override fun onLeft() = active.onLeft()
        override fun onRight() = active.onRight()
        override fun onConfirm() = active.onConfirm()
        override fun onBack() = active.onBack()
        override fun onMenu() = active.onMenu()
        override fun onSecondaryAction() = active.onSecondaryAction()
        override fun onContextMenu() = active.onContextMenu()
        override fun onPrevSection() = active.onPrevSection()
        override fun onNextSection() = active.onNextSection()
        override fun onPrevTrigger() = active.onPrevTrigger()
        override fun onNextTrigger() = active.onNextTrigger()
        override fun onSelect() = active.onSelect()
        override fun onLeftStickClick() = active.onLeftStickClick()
        override fun onRightStickClick() = active.onRightStickClick()
        override fun onRightStickUp() = active.onRightStickUp()
        override fun onRightStickDown() = active.onRightStickDown()
        override fun onRightStickLeft() = active.onRightStickLeft()
        override fun onRightStickRight() = active.onRightStickRight()
        override fun onLongConfirm() = active.onLongConfirm()
        override fun onLongSelect() = active.onLongSelect()
    }
}
