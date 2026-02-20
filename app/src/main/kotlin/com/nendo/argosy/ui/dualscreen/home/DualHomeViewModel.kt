/**
 * DUAL-SCREEN COMPONENT - Lower display ViewModel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Manages game carousel state, platform switching, collections, and library grid.
 */
package com.nendo.argosy.ui.dualscreen.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.Section
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SortableProps
import com.nendo.argosy.data.model.computeGenericSections
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
private const val RECENT_GAMES_LIMIT = 20
private const val PLATFORM_GAMES_LIMIT = 20
private const val LIBRARY_GRID_COLUMNS = 6

sealed class DualHomeSection(val title: String) {
    data object Recent : DualHomeSection("Continue Playing")
    data object Favorites : DualHomeSection("Favorites")
    data class Platform(
        val id: Long,
        val slug: String,
        val name: String,
        val displayName: String,
        val logoPath: String?
    ) : DualHomeSection(displayName)
}

enum class DualHomeFocusZone { CAROUSEL, APP_BAR }

enum class DualHomeViewMode { CAROUSEL, COLLECTIONS, COLLECTION_GAMES, LIBRARY_GRID }

enum class ForwardingMode { NONE, OVERLAY, BACKGROUND }

sealed class DualCollectionListItem {
    data class Header(val title: String) : DualCollectionListItem()
    data class Collection(
        val id: Long,
        val name: String,
        val description: String?,
        val gameCount: Int,
        val coverPaths: List<String>,
        val type: CollectionType,
        val platformSummary: String,
        val totalPlaytimeMinutes: Int
    ) : DualCollectionListItem()
}

enum class DualFilterCategory(val label: String) {
    SORT("Sort"), SEARCH("Search"), SOURCE("Source"), GENRE("Genre"), PLAYERS("Players"), FRANCHISE("Franchise")
}

data class DualFilterOption(
    val label: String,
    val isSelected: Boolean
)

data class DualActiveFilters(
    val source: String = "ALL",
    val genres: Set<String> = emptySet(),
    val players: Set<String> = emptySet(),
    val franchises: Set<String> = emptySet(),
    val searchQuery: String = "",
    val platformId: Long? = null,
    val sort: ActiveSort = ActiveSort()
)

sealed interface DualLibraryGridItem {
    data class Header(val label: String) : DualLibraryGridItem
    data class Game(val game: DualHomeGameUi, val gameIndex: Int) : DualLibraryGridItem
}

data class DualHomeUiState(
    val sections: List<DualHomeSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val games: List<DualHomeGameUi> = emptyList(),
    val selectedIndex: Int = 0,
    val isLoading: Boolean = true,
    val focusZone: DualHomeFocusZone = DualHomeFocusZone.CAROUSEL,
    val appBarIndex: Int = 0,
    val platformTotalCount: Int = 0,
    val viewMode: DualHomeViewMode = DualHomeViewMode.CAROUSEL,
    val collectionItems: List<DualCollectionListItem> = emptyList(),
    val selectedCollectionIndex: Int = 0,
    val collectionGames: List<DualHomeGameUi> = emptyList(),
    val collectionGamesFocusedIndex: Int = 0,
    val activeCollectionName: String = "",
    val libraryGames: List<DualHomeGameUi> = emptyList(),
    val libraryGridItems: List<DualLibraryGridItem> = emptyList(),
    val libraryFocusedIndex: Int = 0,
    val sectionLabels: List<String> = emptyList(),
    val currentSectionLabel: String = "",
    val libraryColumns: Int = LIBRARY_GRID_COLUMNS,
    val showFilterOverlay: Boolean = false,
    val filterCategory: DualFilterCategory = DualFilterCategory.SOURCE,
    val filterOptions: List<DualFilterOption> = emptyList(),
    val filterFocusedIndex: Int = 0,
    val activeFilters: DualActiveFilters = DualActiveFilters(),
    val showSectionOverlay: Boolean = false,
    val overlaySectionLabel: String = "",
    val libraryPlatformLabel: String = "All"
) {
    val currentSection: DualHomeSection?
        get() = sections.getOrNull(currentSectionIndex)

    val totalCount: Int
        get() = if (platformTotalCount > 0) platformTotalCount else games.size

    val hasMoreGames: Boolean
        get() = platformTotalCount > games.size

    val currentPlatformId: Long?
        get() = (currentSection as? DualHomeSection.Platform)?.id

    val isViewAllFocused: Boolean
        get() = hasMoreGames && selectedIndex == games.size

    val platformName: String
        get() = currentSection?.title ?: ""

    val selectedGame: DualHomeGameUi?
        get() = games.getOrNull(selectedIndex)
}

class DualHomeViewModel(
    private val gameDao: GameDao,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val downloadQueueDao: DownloadQueueDao,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualHomeUiState())
    val uiState: StateFlow<DualHomeUiState> = _uiState.asStateFlow()

    private val _forwardingMode = MutableStateFlow(ForwardingMode.NONE)
    val forwardingMode: StateFlow<ForwardingMode> = _forwardingMode.asStateFlow()

    private var allLibraryGames: List<DualHomeGameUi> = emptyList()
    private var letterOverlayJob: kotlinx.coroutines.Job? = null

    fun startDrawerForwarding() { _forwardingMode.value = ForwardingMode.OVERLAY }
    fun startBackgroundForwarding() { _forwardingMode.value = ForwardingMode.BACKGROUND }
    fun stopDrawerForwarding() { _forwardingMode.value = ForwardingMode.NONE }

    init {
        loadData()
        observeDownloads()
    }

    private fun loadData() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update {
                it.copy(
                    sections = sections,
                    isLoading = false
                )
            }
            loadGamesForCurrentSection()
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            var previouslyDownloading = emptySet<Long>()

            downloadQueueDao.observeActiveDownloads().collect { entities ->
                val downloadsByGameId = entities.associateBy { it.gameId }
                val currentlyDownloading = downloadsByGameId.keys

                val completedDownloads = previouslyDownloading - currentlyDownloading
                previouslyDownloading = currentlyDownloading

                if (completedDownloads.isNotEmpty()) {
                    loadGamesForCurrentSectionSuspend()
                    if (_uiState.value.viewMode == DualHomeViewMode.LIBRARY_GRID) {
                        val platformId = _uiState.value.activeFilters.platformId
                        if (platformId != null) {
                            loadLibraryGamesForPlatform(platformId)
                        } else {
                            loadLibraryGames()
                        }
                    }
                    if (_uiState.value.viewMode == DualHomeViewMode.COLLECTION_GAMES) {
                        val collectionId = selectedCollectionItem()?.id
                        if (collectionId != null) {
                            enterCollectionGames(collectionId)
                        }
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            games = state.games.map { game ->
                                game.copy(downloadProgress = progressForGame(game.id, downloadsByGameId))
                            },
                            libraryGames = state.libraryGames.map { game ->
                                game.copy(downloadProgress = progressForGame(game.id, downloadsByGameId))
                            },
                            collectionGames = state.collectionGames.map { game ->
                                game.copy(downloadProgress = progressForGame(game.id, downloadsByGameId))
                            }
                        )
                    }
                }
            }
        }
    }

    private fun progressForGame(
        gameId: Long,
        downloads: Map<Long, com.nendo.argosy.data.local.entity.DownloadQueueEntity>
    ): Float? {
        val entity = downloads[gameId] ?: return null
        return if (entity.totalBytes > 0) {
            (entity.bytesDownloaded.toFloat() / entity.totalBytes).coerceIn(0f, 1f)
        } else 0f
    }

    private suspend fun buildSections(): List<DualHomeSection> {
        val sections = mutableListOf<DualHomeSection>()

        val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val recentGames = gameDao.getRecentlyPlayed(limit = 1)
        val newGames = gameDao.getNewlyAddedPlayable(newThreshold, 1)
        if (recentGames.isNotEmpty() || newGames.isNotEmpty()) {
            sections.add(DualHomeSection.Recent)
        }

        val favorites = gameDao.getFavorites()
        if (favorites.isNotEmpty()) {
            sections.add(DualHomeSection.Favorites)
        }

        val platforms = platformRepository.getPlatformsWithGames()
            .filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
        platforms.forEach { platform ->
            sections.add(
                DualHomeSection.Platform(
                    id = platform.id,
                    slug = platform.slug,
                    name = platform.name,
                    displayName = platform.getDisplayName(),
                    logoPath = platform.logoPath
                )
            )
        }

        return sections
    }

    private fun loadGamesForCurrentSection() {
        viewModelScope.launch { loadGamesForCurrentSectionSuspend() }
    }

    private suspend fun loadGamesForCurrentSectionSuspend() {
        val section = _uiState.value.currentSection ?: return

        var realCount = 0
        val games = when (section) {
            is DualHomeSection.Recent -> {
                val newThreshold = Instant.now().minus(
                    NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS
                )
                val recentlyPlayed = gameDao.getRecentlyPlayed(
                    limit = RECENT_GAMES_LIMIT
                )
                val newlyAdded = gameDao.getNewlyAddedPlayable(
                    newThreshold, RECENT_GAMES_LIMIT
                )
                val allCandidates = (recentlyPlayed + newlyAdded)
                    .distinctBy { it.id }

                val playable = allCandidates.filter { game ->
                    when {
                        game.source == GameSource.STEAM -> true
                        game.source == GameSource.ANDROID_APP -> true
                        game.localPath != null ->
                            File(game.localPath).exists()
                        else -> false
                    }
                }

                sortRecentGamesWithNewPriority(playable)
                    .take(RECENT_GAMES_LIMIT)
                    .map { it.toUi() }
            }
            is DualHomeSection.Favorites -> {
                gameDao.getFavorites().map { it.toUi() }
            }
            is DualHomeSection.Platform -> {
                realCount = gameDao.countByPlatform(section.id)
                gameDao.getByPlatformSorted(
                    section.id, limit = PLATFORM_GAMES_LIMIT
                )
                    .filter { !it.isHidden }
                    .map { it.toUi() }
            }
        }

        _uiState.update {
            it.copy(games = games, platformTotalCount = realCount)
        }
    }

    private fun sortRecentGamesWithNewPriority(games: List<GameEntity>): List<GameEntity> {
        val now = Instant.now()
        val newThreshold = now.minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val recentPlayedThreshold = now.minus(RECENT_PLAYED_THRESHOLD_HOURS, ChronoUnit.HOURS)

        return games.sortedWith(
            compareBy<GameEntity> { game ->
                val isNew = game.addedAt.isAfter(newThreshold) && game.lastPlayed == null
                val playedRecently = game.lastPlayed?.isAfter(recentPlayedThreshold) == true
                when {
                    playedRecently -> 0
                    isNew -> 1
                    else -> 2
                }
            }.thenByDescending { game ->
                game.lastPlayed?.toEpochMilli() ?: game.addedAt.toEpochMilli()
            }
        )
    }

    fun restorePosition(sectionIndex: Int, selectedIndex: Int) {
        viewModelScope.launch {
            val sections = _uiState.value.sections
            if (sections.isEmpty()) return@launch
            val coercedSection = sectionIndex.coerceIn(
                0, sections.size - 1
            )
            _uiState.update {
                it.copy(
                    currentSectionIndex = coercedSection,
                    selectedIndex = 0
                )
            }
            loadGamesForCurrentSectionSuspend()
            val games = _uiState.value.games
            if (games.isNotEmpty()) {
                val maxIndex = if (_uiState.value.hasMoreGames) {
                    games.size
                } else {
                    games.size - 1
                }
                _uiState.update {
                    it.copy(
                        selectedIndex = selectedIndex
                            .coerceIn(0, maxIndex)
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update { it.copy(sections = sections) }
            loadGamesForCurrentSection()
        }
    }

    fun nextSection() {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = (state.currentSectionIndex + 1) % state.sections.size
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
        loadGamesForCurrentSection()
    }

    fun previousSection() {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = if (state.currentSectionIndex <= 0) {
            state.sections.size - 1
        } else {
            state.currentSectionIndex - 1
        }
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
        loadGamesForCurrentSection()
    }

    fun selectNext() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val maxIndex = if (state.hasMoreGames) state.games.size else state.games.size - 1
        val newIndex = (state.selectedIndex + 1).coerceAtMost(maxIndex)
        _uiState.update { it.copy(selectedIndex = newIndex) }
    }

    fun selectPrevious() {
        val state = _uiState.value
        if (state.games.isEmpty()) return
        val newIndex = (state.selectedIndex - 1).coerceAtLeast(0)
        _uiState.update { it.copy(selectedIndex = newIndex) }
    }

    fun setSelectedIndex(index: Int) {
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, maxOf(0, it.games.size - 1))) }
    }

    fun focusAppBar(appCount: Int) {
        _uiState.update { it.copy(
            focusZone = DualHomeFocusZone.APP_BAR,
            appBarIndex = if (appCount > 0) it.appBarIndex.coerceIn(0, appCount - 1) else -1
        )}
    }

    fun focusCarousel() {
        _uiState.update { it.copy(focusZone = DualHomeFocusZone.CAROUSEL) }
    }

    fun selectNextApp(appCount: Int) {
        _uiState.update { it.copy(
            appBarIndex = (it.appBarIndex + 1).coerceAtMost(appCount - 1)
        )}
    }

    fun selectPreviousApp() {
        _uiState.update { it.copy(
            appBarIndex = (it.appBarIndex - 1).coerceAtLeast(-1)
        )}
    }

    fun toggleFavorite() {
        val game = _uiState.value.selectedGame ?: return
        viewModelScope.launch {
            gameDao.updateFavorite(game.id, !game.isFavorite)
            loadGamesForCurrentSection()
        }
    }

    fun getGameDetailIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://game/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getLaunchIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://play/$gameId")
            setPackage(context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    // --- View Mode Navigation ---

    fun enterCollections() {
        _uiState.update { it.copy(viewMode = DualHomeViewMode.COLLECTIONS) }
        loadCollections()
    }

    fun exitToCarousel() {
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.CAROUSEL,
            showFilterOverlay = false
        )}
    }

    fun enterCollectionGames(collectionId: Long, onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            val games = collectionRepository.getGamesInCollection(collectionId)
                .filter { !it.isHidden }
                .map { it.toUi() }
            val item = _uiState.value.collectionItems
                .filterIsInstance<DualCollectionListItem.Collection>()
                .find { it.id == collectionId }
            _uiState.update { it.copy(
                viewMode = DualHomeViewMode.COLLECTION_GAMES,
                collectionGames = games,
                collectionGamesFocusedIndex = 0,
                activeCollectionName = item?.name ?: ""
            )}
            onLoaded?.invoke()
        }
    }

    fun exitCollectionGames() {
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.COLLECTIONS,
            collectionGames = emptyList(),
            collectionGamesFocusedIndex = 0
        )}
    }

    fun enterLibraryGrid(onLoaded: (() -> Unit)? = null) {
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.LIBRARY_GRID,
            activeFilters = DualActiveFilters(),
            libraryPlatformLabel = "All"
        )}
        loadLibraryGames(onLoaded)
    }

    fun enterLibraryGridForPlatform(platformId: Long, onLoaded: (() -> Unit)? = null) {
        val platformName = _uiState.value.sections
            .filterIsInstance<DualHomeSection.Platform>()
            .find { it.id == platformId }?.displayName ?: "All"
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.LIBRARY_GRID,
            activeFilters = DualActiveFilters(platformId = platformId),
            libraryPlatformLabel = platformName
        )}
        loadLibraryGamesForPlatform(platformId, onLoaded)
    }

    fun toggleLibraryGrid(onLoaded: (() -> Unit)? = null) {
        val current = _uiState.value.viewMode
        if (current == DualHomeViewMode.LIBRARY_GRID) {
            exitToCarousel()
            onLoaded?.invoke()
        } else {
            enterLibraryGrid(onLoaded)
        }
    }

    fun cycleLibraryPlatform(direction: Int, onLoaded: (() -> Unit)? = null) {
        val platformSections = _uiState.value.sections.filterIsInstance<DualHomeSection.Platform>()
        val currentPlatformId = _uiState.value.activeFilters.platformId

        // Build list: null (All) + platform IDs
        val options = listOf<Long?>(null) + platformSections.map { it.id }
        val currentIndex = if (currentPlatformId == null) 0
            else options.indexOf(currentPlatformId).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + direction + options.size) % options.size
        val nextPlatformId = options[nextIndex]

        val nextLabel = if (nextPlatformId != null) {
            platformSections.find { it.id == nextPlatformId }?.displayName ?: "All"
        } else "All"

        _uiState.update { it.copy(
            activeFilters = it.activeFilters.copy(platformId = nextPlatformId),
            libraryFocusedIndex = 0,
            libraryPlatformLabel = nextLabel
        )}

        if (nextPlatformId != null) {
            loadLibraryGamesForPlatform(nextPlatformId, onLoaded)
        } else {
            loadLibraryGames(onLoaded)
        }
    }

    // --- Collection Navigation ---

    fun moveCollectionFocus(delta: Int) {
        val state = _uiState.value
        val items = state.collectionItems
        if (items.isEmpty()) return

        val currentIdx = state.selectedCollectionIndex
        var nextIdx = currentIdx + delta

        if (delta > 0) {
            while (nextIdx < items.size && items[nextIdx] is DualCollectionListItem.Header) {
                nextIdx++
            }
        } else {
            while (nextIdx >= 0 && items[nextIdx] is DualCollectionListItem.Header) {
                nextIdx--
            }
        }

        if (nextIdx !in items.indices) return
        if (items[nextIdx] is DualCollectionListItem.Header) return

        _uiState.update { it.copy(selectedCollectionIndex = nextIdx) }
    }

    fun selectedCollectionItem(): DualCollectionListItem.Collection? {
        val state = _uiState.value
        return state.collectionItems.getOrNull(state.selectedCollectionIndex)
            as? DualCollectionListItem.Collection
    }

    // --- Collection Games Navigation ---

    fun moveCollectionGamesFocus(delta: Int) {
        val state = _uiState.value
        if (state.collectionGames.isEmpty()) return
        val newIndex = (state.collectionGamesFocusedIndex + delta)
            .coerceIn(0, state.collectionGames.size - 1)
        _uiState.update { it.copy(collectionGamesFocusedIndex = newIndex) }
    }

    fun focusedCollectionGame(): DualHomeGameUi? {
        val state = _uiState.value
        return state.collectionGames.getOrNull(state.collectionGamesFocusedIndex)
    }

    // --- Library Grid Navigation ---

    fun moveLibraryFocus(delta: Int) {
        val state = _uiState.value
        if (state.libraryGames.isEmpty()) return
        val newIndex = (state.libraryFocusedIndex + delta)
            .coerceIn(0, state.libraryGames.size - 1)
        val sections = computeGenericSections(state.libraryGames, state.activeFilters.sort, DualHomeGameUiProps)
        val orderedSections = if (state.activeFilters.sort.descending) sections else sections.reversed()
        val newLabel = sectionLabelForGameIndex(newIndex, orderedSections)
        _uiState.update { it.copy(
            libraryFocusedIndex = newIndex,
            currentSectionLabel = newLabel
        )}
    }

    fun jumpToSection(label: String) {
        val state = _uiState.value
        val sections = computeGenericSections(state.libraryGames, state.activeFilters.sort, DualHomeGameUiProps)
        val orderedSections = if (state.activeFilters.sort.descending) sections else sections.reversed()
        var offset = 0
        for (section in orderedSections) {
            if (section.sidebarLabel == label) {
                _uiState.update { it.copy(
                    libraryFocusedIndex = offset,
                    currentSectionLabel = label
                )}
                return
            }
            offset += section.items.size
        }
    }

    fun nextSortSection() {
        val state = _uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return
        val currentIdx = labels.indexOf(state.currentSectionLabel)
        val nextIdx = (currentIdx + 1).coerceAtMost(labels.size - 1)
        jumpToSection(labels[nextIdx])
        showSectionOverlay(labels[nextIdx])
    }

    fun previousSortSection() {
        val state = _uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return
        val currentIdx = labels.indexOf(state.currentSectionLabel)
        val prevIdx = (currentIdx - 1).coerceAtLeast(0)
        jumpToSection(labels[prevIdx])
        showSectionOverlay(labels[prevIdx])
    }

    private fun showSectionOverlay(label: String) {
        letterOverlayJob?.cancel()
        _uiState.update { it.copy(showSectionOverlay = true, overlaySectionLabel = label) }
        letterOverlayJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _uiState.update { it.copy(showSectionOverlay = false) }
        }
    }

    fun gameIndexToGridIndex(gameIndex: Int): Int {
        val gridItems = _uiState.value.libraryGridItems
        for ((gridIdx, item) in gridItems.withIndex()) {
            if (item is DualLibraryGridItem.Game && item.gameIndex == gameIndex) return gridIdx
        }
        return 0
    }

    // --- Filter Overlay ---

    fun toggleFilterOverlay() {
        val state = _uiState.value
        if (state.showFilterOverlay) {
            _uiState.update { it.copy(showFilterOverlay = false) }
        } else {
            val options = buildFilterOptions(state.filterCategory, state.activeFilters)
            _uiState.update { it.copy(
                showFilterOverlay = true,
                filterOptions = options,
                filterFocusedIndex = 0
            )}
        }
    }

    fun setFilterCategory(category: DualFilterCategory) {
        val state = _uiState.value
        val options = buildFilterOptions(category, state.activeFilters)
        _uiState.update { it.copy(
            filterCategory = category,
            filterOptions = options,
            filterFocusedIndex = 0
        )}
    }

    fun nextFilterCategory() {
        val categories = DualFilterCategory.entries
        val currentIdx = categories.indexOf(_uiState.value.filterCategory)
        val nextIdx = (currentIdx + 1) % categories.size
        setFilterCategory(categories[nextIdx])
    }

    fun previousFilterCategory() {
        val categories = DualFilterCategory.entries
        val currentIdx = categories.indexOf(_uiState.value.filterCategory)
        val prevIdx = if (currentIdx <= 0) categories.size - 1 else currentIdx - 1
        setFilterCategory(categories[prevIdx])
    }

    fun moveFilterFocus(delta: Int) {
        val state = _uiState.value
        if (state.filterOptions.isEmpty()) return
        val newIndex = (state.filterFocusedIndex + delta)
            .coerceIn(0, state.filterOptions.size - 1)
        _uiState.update { it.copy(filterFocusedIndex = newIndex) }
    }

    fun jumpFilterToNextLetter() {
        val state = _uiState.value
        val options = state.filterOptions
        if (options.isEmpty()) return
        val currentLetter = options[state.filterFocusedIndex].label.firstOrNull()?.uppercaseChar()
        val nextIndex = options.indexOfFirst { opt ->
            val first = opt.label.firstOrNull()?.uppercaseChar()
            first != null && first != currentLetter
                && options.indexOf(opt) > state.filterFocusedIndex
        }
        if (nextIndex >= 0) {
            _uiState.update { it.copy(filterFocusedIndex = nextIndex) }
        }
    }

    fun jumpFilterToPreviousLetter() {
        val state = _uiState.value
        val options = state.filterOptions
        if (options.isEmpty()) return
        val currentLetter = options[state.filterFocusedIndex].label.firstOrNull()?.uppercaseChar()
        val prevGroupStart = options.indexOfLast { opt ->
            val first = opt.label.firstOrNull()?.uppercaseChar()
            first != null && first != currentLetter
                && options.indexOf(opt) < state.filterFocusedIndex
        }
        if (prevGroupStart >= 0) {
            val targetLetter = options[prevGroupStart].label.firstOrNull()?.uppercaseChar()
            val groupStart = options.indexOfFirst { opt ->
                opt.label.firstOrNull()?.uppercaseChar() == targetLetter
            }
            _uiState.update { it.copy(filterFocusedIndex = groupStart.coerceAtLeast(0)) }
        }
    }

    fun confirmFilter() {
        val state = _uiState.value
        val option = state.filterOptions.getOrNull(state.filterFocusedIndex) ?: return
        val label = option.label
        val newFilters = when (state.filterCategory) {
            DualFilterCategory.SORT -> {
                val selectedOption = SortOption.entries.getOrNull(state.filterFocusedIndex) ?: return
                val currentSort = state.activeFilters.sort
                val newSort = if (selectedOption == currentSort.option) {
                    currentSort.copy(descending = !currentSort.descending)
                } else {
                    ActiveSort(option = selectedOption, descending = selectedOption.defaultDescending)
                }
                state.activeFilters.copy(sort = newSort)
            }
            DualFilterCategory.SOURCE -> state.activeFilters.copy(source = label)
            DualFilterCategory.GENRE -> {
                val updated = if (state.activeFilters.genres.contains(label))
                    state.activeFilters.genres - label
                else
                    state.activeFilters.genres + label
                state.activeFilters.copy(genres = updated)
            }
            DualFilterCategory.PLAYERS -> {
                val updated = if (state.activeFilters.players.contains(label))
                    state.activeFilters.players - label
                else
                    state.activeFilters.players + label
                state.activeFilters.copy(players = updated)
            }
            DualFilterCategory.FRANCHISE -> {
                val updated = if (state.activeFilters.franchises.contains(label))
                    state.activeFilters.franchises - label
                else
                    state.activeFilters.franchises + label
                state.activeFilters.copy(franchises = updated)
            }
            DualFilterCategory.SEARCH -> state.activeFilters
        }
        _uiState.update { it.copy(activeFilters = newFilters) }
        applyFilters(newFilters)
    }

    fun clearCategoryFilters() {
        val state = _uiState.value
        val newFilters = when (state.filterCategory) {
            DualFilterCategory.SORT -> state.activeFilters.copy(sort = ActiveSort())
            DualFilterCategory.SOURCE -> state.activeFilters.copy(source = "ALL")
            DualFilterCategory.GENRE -> state.activeFilters.copy(genres = emptySet())
            DualFilterCategory.PLAYERS -> state.activeFilters.copy(players = emptySet())
            DualFilterCategory.FRANCHISE -> state.activeFilters.copy(franchises = emptySet())
            DualFilterCategory.SEARCH -> state.activeFilters.copy(searchQuery = "")
        }
        _uiState.update { it.copy(activeFilters = newFilters) }
        applyFilters(newFilters)
    }

    // --- Private: Collection Loading ---

    private fun loadCollections() {
        viewModelScope.launch {
            val items = mutableListOf<DualCollectionListItem>()

            val userCollections = collectionRepository.getAllByType(CollectionType.REGULAR)
                .filter { it.name.isNotBlank() && it.name.lowercase() != "favorites" }
            if (userCollections.isNotEmpty()) {
                items.add(DualCollectionListItem.Header("MY COLLECTIONS"))
                userCollections.forEach { entity ->
                    items.add(buildCollectionItem(entity))
                }
            }

            val genres = collectionRepository.getAllByType(CollectionType.GENRE)
                .filter { it.name.isNotBlank() }
            if (genres.isNotEmpty()) {
                items.add(DualCollectionListItem.Header("GENRES"))
                genres.forEach { entity ->
                    items.add(buildCollectionItem(entity))
                }
            }

            val gameModes = collectionRepository.getAllByType(CollectionType.GAME_MODE)
                .filter { it.name.isNotBlank() }
            if (gameModes.isNotEmpty()) {
                items.add(DualCollectionListItem.Header("GAME MODES"))
                gameModes.forEach { entity ->
                    items.add(buildCollectionItem(entity))
                }
            }

            val firstCollectionIdx = items.indexOfFirst {
                it is DualCollectionListItem.Collection
            }.coerceAtLeast(0)

            _uiState.update { it.copy(
                collectionItems = items,
                selectedCollectionIndex = firstCollectionIdx
            )}
        }
    }

    private suspend fun buildCollectionItem(
        entity: CollectionEntity
    ): DualCollectionListItem.Collection {
        val games = collectionRepository.getGamesInCollection(entity.id)
        val coverPaths = collectionRepository.getCollectionCoverPaths(entity.id)
        val platformGroups = games.groupBy { it.platformSlug }
        val platformSummary = platformGroups.entries
            .sortedByDescending { it.value.size }
            .take(3)
            .joinToString(", ") { "${it.key}: ${it.value.size}" }
        val totalPlaytime = games.sumOf { it.playTimeMinutes }
        return DualCollectionListItem.Collection(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            gameCount = games.size,
            coverPaths = coverPaths,
            type = entity.type,
            platformSummary = platformSummary,
            totalPlaytimeMinutes = totalPlaytime
        )
    }

    // --- Private: Library Loading ---



    private fun sectionLabelForGameIndex(gameIndex: Int, sections: List<Section<DualHomeGameUi>>): String {
        var offset = 0
        for (section in sections) {
            if (gameIndex < offset + section.items.size) return section.sidebarLabel
            offset += section.items.size
        }
        return sections.firstOrNull()?.sidebarLabel ?: ""
    }

    data class SortResult(
        val games: List<DualHomeGameUi>,
        val sections: List<Section<DualHomeGameUi>>,
        val gridItems: List<DualLibraryGridItem>,
        val labels: List<String>
    )

    private fun applySort(games: List<DualHomeGameUi>, sort: ActiveSort): SortResult {
        val sections = computeGenericSections(games, sort, DualHomeGameUiProps)
        val orderedSections = if (sort.descending) sections else sections.reversed()
        val sortedGames = orderedSections.flatMap { it.items }
        val labels = orderedSections.map { it.sidebarLabel }
        var gameOffset = 0
        val gridItems = orderedSections.flatMap { section ->
            val header = DualLibraryGridItem.Header(section.label)
            val gameItems = section.items.mapIndexed { i, game ->
                DualLibraryGridItem.Game(game, gameIndex = gameOffset + i)
            }
            gameOffset += section.items.size
            listOf(header) + gameItems
        }
        return SortResult(sortedGames, orderedSections, gridItems, labels)
    }

    private fun loadLibraryGames(onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            val allGames = gameDao.getAllSortedByTitle()
                .map { it.toUi() }
            allLibraryGames = allGames
            val filters = _uiState.value.activeFilters
            val filtered = applyFiltersToList(allGames, filters)
            val result = applySort(filtered, filters.sort)
            _uiState.update { it.copy(
                libraryGames = result.games,
                libraryGridItems = result.gridItems,
                sectionLabels = result.labels,
                currentSectionLabel = result.labels.firstOrNull() ?: "",
                libraryFocusedIndex = 0
            )}
            onLoaded?.invoke()
        }
    }

    private fun loadLibraryGamesForPlatform(platformId: Long, onLoaded: (() -> Unit)? = null) {
        viewModelScope.launch {
            val platformGames = gameDao.getByPlatform(platformId)
                .map { it.toUi() }
            allLibraryGames = platformGames
            val filters = _uiState.value.activeFilters
            val filtered = applyFiltersToList(platformGames, filters)
            val result = applySort(filtered, filters.sort)
            _uiState.update { it.copy(
                libraryGames = result.games,
                libraryGridItems = result.gridItems,
                sectionLabels = result.labels,
                currentSectionLabel = result.labels.firstOrNull() ?: "",
                libraryFocusedIndex = 0
            )}
            onLoaded?.invoke()
        }
    }

    private fun applyFilters(filters: DualActiveFilters) {
        val filtered = applyFiltersToList(allLibraryGames, filters)
        val result = applySort(filtered, filters.sort)
        val options = buildFilterOptions(_uiState.value.filterCategory, filters)
        _uiState.update { it.copy(
            libraryGames = result.games,
            libraryGridItems = result.gridItems,
            sectionLabels = result.labels,
            currentSectionLabel = result.labels.firstOrNull() ?: "",
            libraryFocusedIndex = 0,
            filterOptions = options
        )}
    }

    private fun applyFiltersToList(
        games: List<DualHomeGameUi>,
        filters: DualActiveFilters
    ): List<DualHomeGameUi> {
        return games.filter { game ->
            val matchesSource = when (filters.source) {
                "PLAYABLE" -> game.isPlayable
                "FAVORITES" -> game.isFavorite
                else -> true
            }
            val matchesSearch = filters.searchQuery.isBlank() ||
                game.title.contains(filters.searchQuery, ignoreCase = true)
            val matchesGenre = filters.genres.isEmpty() ||
                filters.genres.contains(game.genre)
            val matchesPlayers = filters.players.isEmpty() ||
                game.gameModes?.split(",")
                    ?.map { it.trim() }
                    ?.any { it in filters.players } == true
            val matchesFranchise = filters.franchises.isEmpty() ||
                game.franchises?.split(",")
                    ?.map { it.trim() }
                    ?.any { it in filters.franchises } == true
            matchesSource && matchesSearch && matchesGenre && matchesPlayers && matchesFranchise
        }
    }

    private fun buildFilterOptions(
        category: DualFilterCategory,
        filters: DualActiveFilters
    ): List<DualFilterOption> {
        return when (category) {
            DualFilterCategory.SORT -> SortOption.entries.map { option ->
                val directionIndicator = if (option == filters.sort.option) {
                    if (filters.sort.descending) " v" else " ^"
                } else ""
                DualFilterOption(
                    label = option.label + directionIndicator,
                    isSelected = option == filters.sort.option
                )
            }
            DualFilterCategory.SOURCE -> listOf(
                DualFilterOption("ALL", filters.source == "ALL"),
                DualFilterOption("PLAYABLE", filters.source == "PLAYABLE"),
                DualFilterOption("FAVORITES", filters.source == "FAVORITES")
            )
            DualFilterCategory.GENRE -> {
                val genres = allLibraryGames
                    .mapNotNull { it.genre }
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                genres.map { DualFilterOption(it, filters.genres.contains(it)) }
            }
            DualFilterCategory.PLAYERS -> {
                val players = allLibraryGames
                    .mapNotNull { it.gameModes }
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                players.map { DualFilterOption(it, filters.players.contains(it)) }
            }
            DualFilterCategory.FRANCHISE -> {
                val franchises = allLibraryGames
                    .mapNotNull { it.franchises }
                    .flatMap { it.split(",") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                franchises.map { DualFilterOption(it, filters.franchises.contains(it)) }
            }
            DualFilterCategory.SEARCH -> emptyList()
        }
    }

    // --- Private: Entity to UI ---

    private fun GameEntity.toUi(): DualHomeGameUi {
        val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
        val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
        return DualHomeGameUi(
            id = id,
            title = title,
            sortTitle = sortTitle,
            coverPath = coverPath,
            platformName = platformSlug,
            platformSlug = platformSlug,
            playTimeMinutes = playTimeMinutes,
            lastPlayedAt = lastPlayed?.toEpochMilli(),
            status = status,
            communityRating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            isPlayable = localPath != null || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isFavorite = isFavorite,
            backgroundPath = effectiveBackground,
            description = description,
            developer = developer,
            releaseYear = releaseYear,
            titleId = null,
            genre = genre,
            gameModes = gameModes,
            franchises = franchises,
            addedAt = addedAt.toEpochMilli(),
            playCount = playCount
        )
    }
}

object DualHomeGameUiProps : SortableProps<DualHomeGameUi> {
    override fun sortTitle(item: DualHomeGameUi) = item.sortTitle
    override fun rating(item: DualHomeGameUi) = item.communityRating
    override fun userRating(item: DualHomeGameUi) = item.userRating
    override fun userDifficulty(item: DualHomeGameUi) = item.userDifficulty
    override fun releaseYear(item: DualHomeGameUi) = item.releaseYear
    override fun playCount(item: DualHomeGameUi) = item.playCount
    override fun playTimeMinutes(item: DualHomeGameUi) = item.playTimeMinutes
    override fun lastPlayedEpochMilli(item: DualHomeGameUi) = item.lastPlayedAt
    override fun addedAtEpochMilli(item: DualHomeGameUi) = item.addedAt ?: 0L
}
