package com.nendo.argosy.ui.screens.library

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.MediaLibraryStats
import com.nendo.argosy.data.local.dao.PlatformShowcaseStats
import com.nendo.argosy.data.local.dao.SHOWCASE_COVER_LIMIT
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.CustomGridShapeStore
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.GameSection
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.computeSections
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.LibraryListStyle
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveListStatusRepository
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.domain.model.SaveListState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCount
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import android.content.Context
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.common.GridFocusNavigator
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ListReorder
import com.nendo.argosy.ui.components.activeFilterSummary
import com.nendo.argosy.ui.common.toLibraryGameUi
import com.nendo.argosy.ui.common.toNotificationText
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameGradientRequest
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.common.RefreshAndroidResult
import com.nendo.argosy.ui.screens.common.DiscPickerState
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.GameStart
import com.nendo.argosy.ui.screens.common.PendingSiblingFocus
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.ui.screens.gamedetail.CollectionItemUi
import com.nendo.argosy.ui.screens.home.HomePlatformUi
import com.nendo.argosy.ui.screens.home.toHomePlatformUi
import com.nendo.argosy.ui.util.GridUtils
import com.nendo.argosy.ui.util.heldWhile
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.ui.dualscreen.CompanionFact
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The tabs of the filter modal. Routing runs on the entry itself and on its ordinal, never on the
 * text of the tab, so [labelRes] is only ever the half that shows.
 */
enum class FilterCategory(@StringRes val labelRes: Int) {
    SORT(R.string.library_filter_category_sort),
    SEARCH(R.string.library_filter_category_search),
    SOURCE(R.string.library_filter_category_source),
    PLATFORM(R.string.library_filter_category_platform),
    GENRE(R.string.library_filter_category_genre),
    REGION(R.string.library_filter_category_region),
    PLAYERS(R.string.library_filter_category_players),
    SERIES(R.string.library_filter_category_series)
}

/**
 * One filter category that is on: the tab it belongs to, the label or text that names its value,
 * and how many selections it holds. Every "which filters are active" question reads
 * [ActiveFilters.entries]; nothing else re-derives it from the fields.
 */
data class ActiveFilterEntry(
    val category: FilterCategory,
    @StringRes val labelRes: Int? = null,
    val text: String? = null,
    val count: Int = 1
)

/**
 * A platform a filter is limited to. [id] is what a game is matched against; [label] is carried
 * alongside only so a chip can name it without a lookup, and is never compared.
 */
data class PlatformRef(val id: Long, val label: String)

data class ActiveFilters(
    val searchQuery: String = "",
    val source: SourceFilter = SourceFilter.ALL,
    val platforms: Set<PlatformRef> = emptySet(),
    val genres: Set<String> = emptySet(),
    val regions: Set<String> = emptySet(),
    val players: PlayerCountBucket? = null,
    val series: Set<String> = emptySet(),
    val sort: ActiveSort = ActiveSort()
) {
    val entries: List<ActiveFilterEntry>
        get() = listOfNotNull(
            sort.option.takeIf { it != SortOption.TITLE }
                ?.let { ActiveFilterEntry(FilterCategory.SORT, labelRes = it.labelRes) },
            searchQuery.takeIf { it.isNotEmpty() }
                ?.let { ActiveFilterEntry(FilterCategory.SEARCH, text = it) },
            source.takeIf { it != SourceFilter.ALL }
                ?.let { ActiveFilterEntry(FilterCategory.SOURCE, labelRes = it.labelRes) },
            platforms.firstOrNull()?.let {
                ActiveFilterEntry(FilterCategory.PLATFORM, text = it.label, count = platforms.size)
            },
            multiSelectEntry(FilterCategory.GENRE, genres),
            multiSelectEntry(FilterCategory.REGION, regions),
            players?.let { ActiveFilterEntry(FilterCategory.PLAYERS, labelRes = it.labelRes) },
            multiSelectEntry(FilterCategory.SERIES, series)
        )

    val activeCount: Int
        get() = entries.sumOf { it.count }

    fun isActive(category: FilterCategory): Boolean = entries.any { it.category == category }

    fun summary(context: Context): String =
        chips.activeFilterSummary(context, R.plurals.library_filter_active_count)
            ?: context.getString(R.string.source_filter_all)

    private fun multiSelectEntry(category: FilterCategory, values: Set<String>): ActiveFilterEntry? =
        values.firstOrNull()?.let { ActiveFilterEntry(category, text = it, count = values.size) }
}

data class FilterOptions(
    val platforms: List<PlatformRef> = emptyList(),
    val genres: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val series: List<String> = emptyList()
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

/**
 * What the library is showing. [PLATFORM_GRID] is the landing when nothing was asked for; arriving
 * with a platform, a source or a configured library default goes straight to [GAMES], because those
 * callers have already chosen and a chooser would be in their way.
 */
enum class LibraryView {
    PLATFORM_GRID, GAMES
}

sealed interface LibraryGridItem {
    data class Header(val label: String, val sidebarLabel: String = label) : LibraryGridItem
    data class Game(val game: LibraryGameUi, val gameIndex: Int) : LibraryGridItem
}

/** Viewport-relative bounds of a focusable cover, used for spatial D-pad navigation in masonry mode. */
data class FocusCellBounds(
    val gameIndex: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class LibraryGameUi(
    val id: Long,
    val title: String,
    val sortTitle: String,
    val platformId: Long,
    val platformSlug: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val boxSpinePath: String? = null,
    val box3dPath: String? = null,
    val gradientColors: Pair<Color, Color>? = null,
    val source: GameSource,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val isRommGame: Boolean,
    val canRemoveFromLibrary: Boolean = false,
    val isAndroidApp: Boolean,
    val emulatorName: String?,
    val needsInstall: Boolean = false,
    val isHidden: Boolean = false,
    val listDetails: com.nendo.argosy.ui.common.GameListDetails
) {
    val sourceIcon: ImageVector?
        get() = when (source) {
            GameSource.LOCAL_ONLY -> Icons.Default.Folder
            GameSource.ROMM_SYNCED -> Icons.Default.CheckCircle
            GameSource.ROMM_REMOTE -> null
            GameSource.STEAM -> Icons.Default.Cloud
            GameSource.ANDROID_APP -> null
            GameSource.GAMENATIVE -> Icons.Default.Cloud
        }
}

data class LibraryUiState(
    val view: LibraryView = LibraryView.PLATFORM_GRID,
    val platformCells: List<LibraryCellUi> = emptyList(),
    val platformGridFocusedIndex: Int = 0,
    val platformReorder: ListReorder<LibraryCellUi>? = null,
    val canReturnToPlatformGrid: Boolean = false,
    val platforms: List<HomePlatformUi> = emptyList(),
    val currentPlatformIndex: Int = -1,
    val games: List<LibraryGameUi> = emptyList(),
    val focusedIndex: Int = 0,
    val lastFocusMove: FocusMove? = null,
    val currentFilter: LibraryFilter = LibraryFilter.ALL,
    val showFilterMenu: Boolean = false,
    val showQuickMenu: Boolean = false,
    val quickMenuFocusIndex: Int = 0,
    val quickMenuHasSiblingGroup: Boolean = false,
    val isCustomGridHome: Boolean = false,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val libraryListStyle: LibraryListStyle = LibraryListStyle.RELAXED,
    val boxArt3d: Boolean = false,
    val saveStates: Map<Long, SaveListState> = emptyMap(),
    val friendsActivity: Map<Int, List<FriendActivity>> = emptyMap(),
    val isLoading: Boolean = true,
    val activeFilters: ActiveFilters = ActiveFilters(),
    val filterOptions: FilterOptions = FilterOptions(),
    val filterCategoryIndex: Int = 0,
    val filterOptionIndex: Int = 0,
    val syncOverlayState: SyncOverlayState? = null,
    val discPickerState: DiscPickerState? = null,
    val discPickerFocusIndex: Int = 0,
    val variantPickerState: com.nendo.argosy.ui.screens.common.VariantPickerState? = null,
    val variantPickerFocusIndex: Int = 0,
    val memcardPickerState: com.nendo.argosy.ui.screens.common.MemcardPickerState? = null,
    val memcardPickerFocusIndex: Int = 0,
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
    val sectionJumpTrigger: Int = 0,
    val sectionRailFocusIndex: Int? = null,
    val hiddenGameCount: Int = 0
) {
    val showSectionSidebar: Boolean
        get() = sectionLabels.size >= MIN_SECTION_RAIL_LABELS

    val isSectionRailFocused: Boolean
        get() = sectionRailFocusIndex != null

    val sectionRailFocusedLabel: String?
        get() = sectionRailFocusIndex?.let { sectionLabels.getOrNull(it) }

    val isPlatformGrid: Boolean
        get() = view == LibraryView.PLATFORM_GRID

    val isReorderingPlatforms: Boolean
        get() = platformReorder != null

    val focusedPlatformCell: LibraryCellUi?
        get() = platformCells.getOrNull(platformGridFocusedIndex)?.takeIf { it.isPlatform }

    /**
     * A platform cell is a badge - a mark and two short lines - so it wants the game grid's narrower
     * columns rather than the app grid's wide ones. The landing only earns its place while the whole
     * shelf is in front of you, and app-width columns spent that on air.
     */
    val platformGridColumns: Int
        get() = GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    /**
     * Nothing to land on: no platform row, no media library, and nothing in the library as a whole.
     * A user with games but no platform rows still gets the grid, since All Games alone is a real
     * destination and hiding it would strand them, and so does one whose only collection is a media
     * library.
     */
    val platformGridIsEmpty: Boolean
        get() = platformCells.none { !it.isAllGames } &&
            platformCells.sumOf { it.itemCount } == 0

    val platformCellCount: Int
        get() = platformCells.count { it.isPlatform }

    val mediaCellCount: Int
        get() = platformCells.count { it.isMedia }

    val isListLayout: Boolean
        get() = libraryLayout == LibraryLayout.LIST

    val columnsCount: Int
        get() = if (isListLayout) 1 else GridUtils.getGameGridColumns(gridDensity, screenWidthDp)

    val gridSpacingDp: Int
        get() = GridUtils.getGridSpacingDp(gridDensity)

    val currentPlatform: HomePlatformUi?
        get() = if (currentPlatformIndex >= 0) platforms.getOrNull(currentPlatformIndex) else null

    val focusedGame: LibraryGameUi?
        get() = games.getOrNull(focusedIndex)

    val currentFilterCategory: FilterCategory
        get() = FilterCategory.entries.getOrElse(filterCategoryIndex) { FilterCategory.SOURCE }

    fun currentCategoryOptions(context: Context): List<String> =
        when (currentFilterCategory) {
            FilterCategory.SORT -> SortOption.entries.map { option ->
                val directionIndicator = if (option == activeFilters.sort.option) {
                    if (activeFilters.sort.descending) " v" else " ^"
                } else ""
                context.getString(option.labelRes) + directionIndicator
            }
            FilterCategory.SEARCH -> recentSearches
            FilterCategory.SOURCE -> SourceFilter.entries.map { filter ->
                val label = context.getString(filter.labelRes)
                if (filter == SourceFilter.HIDDEN && hiddenGameCount > 0)
                    "$label ($hiddenGameCount)"
                else label
            }
            FilterCategory.PLATFORM -> filterOptions.platforms.map { it.label }
            FilterCategory.GENRE -> filterOptions.genres
            FilterCategory.REGION -> filterOptions.regions
            FilterCategory.PLAYERS -> PlayerCountBucket.entries.map { context.getString(it.labelRes) }
            FilterCategory.SERIES -> filterOptions.series
        }

    val isCurrentCategoryMultiSelect: Boolean
        get() = currentFilterCategory !in listOf(
            FilterCategory.SORT,
            FilterCategory.SOURCE,
            FilterCategory.SEARCH,
            FilterCategory.PLAYERS
        )

    val selectedSourceIndex: Int
        get() = activeFilters.source.ordinal

    val selectedSortIndex: Int
        get() = activeFilters.sort.option.ordinal

    val selectedPlayersIndex: Int
        get() = activeFilters.players?.ordinal ?: -1

    val selectedIndicesInCurrentCategory: Set<Int>
        get() = when (currentFilterCategory) {
            FilterCategory.PLATFORM -> {
                val ids = activeFilters.platforms.map { it.id }.toSet()
                filterOptions.platforms.indices.filter { filterOptions.platforms[it].id in ids }.toSet()
            }
            FilterCategory.GENRE -> indicesOf(filterOptions.genres, activeFilters.genres)
            FilterCategory.REGION -> indicesOf(filterOptions.regions, activeFilters.regions)
            FilterCategory.SERIES -> indicesOf(filterOptions.series, activeFilters.series)
            FilterCategory.SORT,
            FilterCategory.SEARCH,
            FilterCategory.SOURCE,
            FilterCategory.PLAYERS -> emptySet()
        }

    private fun indicesOf(options: List<String>, selected: Set<String>): Set<Int> =
        options.indices.filter { options[it] in selected }.toSet()

    val availableCategories: List<FilterCategory>
        get() = FilterCategory.entries.filter { category ->
            when (category) {
                FilterCategory.SORT -> true
                FilterCategory.SEARCH -> true
                FilterCategory.SOURCE -> true
                FilterCategory.PLATFORM -> filterOptions.platforms.isNotEmpty()
                FilterCategory.GENRE -> filterOptions.genres.isNotEmpty()
                FilterCategory.REGION -> filterOptions.regions.isNotEmpty()
                FilterCategory.PLAYERS -> true
                FilterCategory.SERIES -> filterOptions.series.isNotEmpty()
            }
        }
}

private const val TAG = "LibraryVM"
private const val MIN_SECTION_RAIL_LABELS = 3
private const val FILTER_OPTION_PAGE = 5

sealed class LibraryEvent {
    data class LaunchIntent(val intent: Intent) : LibraryEvent()
    data object OpenSearchKeyboard : LibraryEvent()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val notificationManager: NotificationManager,
    private val preferencesRepository: UserPreferencesRepository,
    private val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    private val customGridShapeStore: CustomGridShapeStore,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val romMRepository: RomMRepository,
    private val playStoreService: PlayStoreService,
    private val imageCacheManager: ImageCacheManager,
    private val apkInstallManager: ApkInstallManager,
    private val platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue,
    private val repairImageCacheUseCase: RepairImageCacheUseCase,
    private val modalResetSignal: ModalResetSignal,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    downloadIndicatorSource: com.nendo.argosy.ui.screens.common.DownloadIndicatorSource,
    private val emulatorDetector: EmulatorDetector,
    private val steamLauncherLinkController: com.nendo.argosy.data.steam.SteamLauncherLinkController,
    private val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
    private val siblingChoice: com.nendo.argosy.ui.screens.common.SiblingChoiceDelegate,
    private val socialRepository: SocialRepository,
    private val saveListStatusRepository: SaveListStatusRepository,
    private val libraryDefaultPlatformMigration: com.nendo.argosy.data.repository.LibraryDefaultPlatformMigration,
    private val showcaseSource: com.nendo.argosy.ui.common.PresentationShowcaseSource,
    private val reorderPlatforms: com.nendo.argosy.domain.usecase.platform.ReorderPlatformsUseCase,
    private val quickNavigation: com.nendo.argosy.data.preferences.QuickNavigationSource,
    private val launchProgressTracker: com.nendo.argosy.data.emulator.LaunchProgressTracker
) : ViewModel() {

    val siblingChoiceState = siblingChoice.state

    val gameConfirm = com.nendo.argosy.ui.common.GameConfirmController(
        gameActions::deleteDownloadConfirm, gameActions::hideConfirm, viewModelScope
    )

    private val companionOwner = SlotOwner.of("library", this)
    private val platformShowcaseOwner = SlotOwner.of("library.platforms", this)
    private var isDescribing = false

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val downloadIndicators: StateFlow<Map<Long, com.nendo.argosy.ui.screens.home.GameDownloadIndicator>> =
        downloadIndicatorSource.indicators

    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    private var gamesJob: Job? = null
    private var quickMenuOpenJob: Job? = null
    private var pendingInitialPlatformId: Long? = null
    private var pendingInitialSourceFilter: SourceFilter? = null
    private var pendingInitialTileFilters: LibraryLinkFilters? = null
    private var explicitSourceRequested = false
    private var explicitTileFiltersRequested = false
    private var explicitDestinationRequested = false
    private var cachedPlatformDisplayNames: Map<Long, String> = emptyMap()
    private var cachedPlatformEntities: List<PlatformEntity> = emptyList()
    private var platformStatsCache: Map<Long, PlatformShowcaseStats>? = null
    private var cachedMediaLibraries: List<MediaLibraryEntity> = emptyList()
    private var pendingSiblingFocus: PendingSiblingFocus? = null

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
        observeMediaLibraries()
        loadFilterOptions()
        applyLibraryDefaults()
        observeGridDensity()
        observeListDecorations()
        observeSyncOverlay()
        observeCollectionModal()
        observeGradientChanges()
        observeHiddenCount()
        observeFocusForCompanion()
    }

    /**
     * Seeds the filter state from the configured library defaults.
     *
     * Runs once at construction rather than observing the preference, so a default change never
     * yanks the filters out from under someone mid-browse. A caller that opened the library at a
     * specific source (favorites, playable) has asked for something the default must not overwrite,
     * and the two arrive on independent coroutines, so that request is tracked rather than raced.
     *
     * A configured default source or platform is itself a destination, so it skips the platform
     * landing for the same reason an explicit caller does: the choice has already been made.
     */
    private fun applyLibraryDefaults() {
        viewModelScope.launch {
            libraryDefaultPlatformMigration.run()
            val prefs = preferencesRepository.userPreferences.first()
            val option = SortOption.entries.firstOrNull { it.name == prefs.libraryDefaultSort }
                ?: SortOption.TITLE
            val source = SourceFilter.entries.firstOrNull { it.name == prefs.libraryDefaultSource }
                ?: SourceFilter.ALL
            val defaultPlatformId = prefs.libraryDefaultPlatformId
                ?.takeIf { !explicitDestinationRequested }
                ?.let { platformRepository.getById(it) }
                ?.id
            val landsOnGames = explicitDestinationRequested ||
                source != SourceFilter.ALL ||
                defaultPlatformId != null
            _uiState.update {
                it.copy(
                    view = if (landsOnGames) LibraryView.GAMES else it.view,
                    activeFilters = if (explicitTileFiltersRequested) {
                        it.activeFilters
                    } else {
                        it.activeFilters.copy(
                            sort = ActiveSort(
                                option = option,
                                descending = prefs.libraryDefaultSortDescending
                                    ?: option.defaultDescending
                            ),
                            source = if (explicitSourceRequested) {
                                it.activeFilters.source
                            } else {
                                source
                            },
                            regions = prefs.libraryDefaultRegions,
                            players = prefs.libraryDefaultPlayers
                        )
                    }
                )
            }
            if (defaultPlatformId != null) openOnPlatform(defaultPlatformId)
            loadGames()
        }
    }

    private fun observeHiddenCount() {
        viewModelScope.launch {
            gameRepository.observeHiddenList().collect { games ->
                _uiState.update { it.copy(hiddenGameCount = games.size) }
                refreshPlatformCells()
            }
        }
    }

    /**
     * The media libraries the landing offers, or none at all while nobody is signed in.
     *
     * Sign-in is checked alongside the rows rather than relied on through them: the stored rows
     * outlive a sign-out until the next sync reconciles them, and a landing that kept offering the
     * previous account's libraries would open browses that answer empty.
     *
     * Nothing is synced from here. The library listing is filled by home's media delegate, which
     * already asks for it once when the table is empty, and a second unprompted crawl would make
     * opening the library cost a whole-server round trip.
     */
    private fun observeMediaLibraries() {
        viewModelScope.launch {
            combine(
                mediaRepository.isSignedIn,
                mediaRepository.observeLibraries()
            ) { signedIn, libraries ->
                if (signedIn) libraries else emptyList<MediaLibraryEntity>()
            }
                .collect { libraries ->
                    cachedMediaLibraries = libraries
                    refreshPlatformCells()
                }
        }
    }

    /**
     * Rebuilds the landing cells from aggregate count queries rather than by sizing any list.
     *
     * Hiding a game changes what a platform contains without touching the platforms table, so this
     * runs on the hidden-list flow as well as on the platform flow; otherwise a count would sit
     * stale against the list it labels.
     *
     * Media libraries follow the platforms rather than sorting in among them. The two families are
     * ordered by different things - platforms by the game library's own order, libraries by the
     * media server's - so there is no shared key to interleave on, and a fixed tail is the one
     * arrangement that puts them in the same place every time. Vertical focus wraps, so the tail is
     * one press up from the first row however many platforms sit between.
     */
    private suspend fun refreshPlatformCells() {
        platformStatsCache = null
        val counts = gameRepository.countsByPlatform()
        val mediaCounts: Map<String, Int> = if (cachedMediaLibraries.isEmpty()) {
            emptyMap()
        } else {
            mediaRepository.countsByLibrary()
        }
        val cells = listOf(allGamesCell(cachedPlatformEntities.sumOf { counts[it.id] ?: 0 })) +
            cachedPlatformEntities.map { it.toLibraryCellUi(counts[it.id] ?: 0) } +
            cachedMediaLibraries.mapNotNull { library ->
                mediaCounts[library.libraryId]?.let { library.toLibraryCellUi(it) }
            }
        _uiState.update { state ->
            if (state.isReorderingPlatforms) return@update state
            state.copy(
                platformCells = cells,
                platformGridFocusedIndex = state.platformGridFocusedIndex
                    .coerceAtMost((cells.size - 1).coerceAtLeast(0))
            )
        }
    }

    private fun resetMenus() {
        _uiState.update {
            it.copy(
                showFilterMenu = false,
                showQuickMenu = false,
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false,
                sectionRailFocusIndex = null
            )
        }
        quickMenuOpenJob?.cancel()
        siblingChoice.reset()
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
        viewModelScope.launch {
            gameLaunchDelegate.variantPickerState.collect { pickerState ->
                _uiState.update { it.copy(variantPickerState = pickerState) }
            }
        }
        viewModelScope.launch {
            gameLaunchDelegate.memcardPickerState.collect { pickerState ->
                _uiState.update { it.copy(memcardPickerState = pickerState) }
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
                            gradients[game.id]
                                ?.takeIf { it != game.gradientColors }
                                ?.let { game.copy(gradientColors = it) }
                                ?: game
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

    val menuWrapMode get() = preferencesRepository.menuWrapMode

    fun setDiscPickerFocusIndex(index: Int) {
        _uiState.update { it.copy(discPickerFocusIndex = index) }
    }

    fun selectVariant(variantFileId: Long?) {
        gameLaunchDelegate.selectVariant(viewModelScope, variantFileId)
    }

    fun dismissVariantPicker() {
        gameLaunchDelegate.dismissVariantPicker()
    }

    fun setVariantPickerFocusIndex(index: Int) {
        _uiState.update { it.copy(variantPickerFocusIndex = index) }
    }

    fun selectMemcard(cardPath: String) {
        gameLaunchDelegate.selectMemcard(viewModelScope, cardPath)
    }

    fun dismissMemcardPicker() {
        gameLaunchDelegate.dismissMemcardPicker()
    }

    fun setMemcardPickerFocusIndex(index: Int) {
        _uiState.update { it.copy(memcardPickerFocusIndex = index) }
    }

    @Volatile
    private var sortPartition: com.nendo.argosy.data.model.SortPartition =
        com.nendo.argosy.data.model.SortPartition.NONE

    private fun observeGridDensity() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collectLatest { prefs ->
                gradientExtractionDelegate.updatePreferences(prefs.gradientPreset, prefs.boxArtBorderStyle)
                val partition = com.nendo.argosy.data.model.SortPartition(
                    installedFirst = prefs.sortInstalledFirst,
                    favoritesFirst = prefs.sortFavoritesFirst
                )
                val partitionChanged = partition != sortPartition
                sortPartition = partition
                if (partitionChanged) loadGames()
                if (prefs.libraryLayout == LibraryLayout.LIST) masonryCells = emptyList()
                val leftList = _uiState.value.isListLayout && prefs.libraryLayout == LibraryLayout.GRID
                _uiState.update {
                    it.copy(
                        gridDensity = prefs.gridDensity,
                        libraryLayout = prefs.libraryLayout,
                        libraryListStyle = prefs.libraryListStyle,
                        boxArt3d = prefs.libraryBoxArt3d,
                        recentSearches = prefs.libraryRecentSearches,
                        isCustomGridHome = prefs.homeLayout.selected ==
                            com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID
                    )
                }
                if (leftList) extractGradientsForVisibleGames(_uiState.value.focusedIndex)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeListDecorations() {
        preferencesRepository.userPreferences
            .map { it.libraryLayout }
            .distinctUntilChanged()
            .flatMapLatest { layout ->
                if (layout == LibraryLayout.LIST) {
                    combine(
                        saveListStatusRepository.observeWorstByGame(),
                        socialRepository.friendsActivity
                    ) { saves, friends -> saves to friends }
                } else {
                    flowOf(emptyMap<Long, SaveListState>() to emptyMap<Int, List<FriendActivity>>())
                }
            }
            .onEach { (saves, friends) ->
                _uiState.update { it.copy(saveStates = saves, friendsActivity = friends) }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Tells the showcase screen which game this one has focused, so the other screen describes the
     * cursor rather than sitting on whatever it was last left showing.
     */
    private fun observeFocusForCompanion() {
        viewModelScope.launch {
            _uiState
                .map { if (it.view == LibraryView.PLATFORM_GRID) null else it.focusedGame?.id }
                .distinctUntilChanged()
                .collectLatest { publishCompanionDetail(it) }
        }

        _uiState
            .map { it.focusedPlatformCell }
            .distinctUntilChanged()
            .onEach { cell -> publishPlatformShowcase(cell) }
            .launchIn(viewModelScope)
    }

    private fun followSiblingPick(fromGameId: Long, shownGameId: Long) {
        if (fromGameId == shownGameId) return
        val state = _uiState.value
        val gameIds = state.games.map { it.id }
        if (state.focusedGame?.id != fromGameId && fromGameId in gameIds) return
        pendingSiblingFocus = PendingSiblingFocus(fromGameId, shownGameId, state.focusedIndex)
        val index = settleSiblingFocus(gameIds) ?: return
        _uiState.update { it.copy(focusedIndex = index, sectionJumpTrigger = it.sectionJumpTrigger + 1) }
        updateCurrentSectionFromFocus()
        extractGradientsForVisibleGames(index)
    }

    private fun settleSiblingFocus(gameIds: List<Long>): Int? {
        val pending = pendingSiblingFocus ?: return null
        val state = _uiState.value
        if (!pending.isHeldBy(state.focusedIndex, state.focusedGame?.id)) {
            pendingSiblingFocus = null
            return null
        }
        val (index, remaining) = pending.resolve(gameIds)
        pendingSiblingFocus = remaining
        return index
    }

    private val LibraryUiState.focusedPlatformCell: LibraryCellUi?
        get() = if (view == LibraryView.PLATFORM_GRID) platformCells.getOrNull(platformGridFocusedIndex) else null

    private suspend fun publishPlatformShowcase(cell: LibraryCellUi?) {
        if (!isDescribing) return
        val dsm = DualScreenManagerHolder.instance ?: return
        val owner = platformShowcaseOwner
        if (cell == null) {
            dsm.releaseSlot(owner)
            return
        }
        val showcase = when (val target = cell.target) {
            LibraryCellTarget.AllGames -> gameShowcaseFor(
                cell,
                platformStats().values.reduceOrNull(::combineStats),
                gameRepository.showcaseCovers(null, oneEntryPerGroup = true)
            )
            is LibraryCellTarget.Platform -> gameShowcaseFor(
                cell,
                platformStats()[target.platformId],
                gameRepository.showcaseCovers(target.platformId, oneEntryPerGroup = true)
            )
            is LibraryCellTarget.Media -> mediaShowcaseFor(
                cell,
                mediaRepository.libraryStats(target.libraryId),
                mediaRepository.showcasePosterUrls(target.libraryId, SHOWCASE_COVER_LIMIT)
            )
        }
        if (!isDescribing) return
        dsm.presentSlot(owner, showcase)
    }

    private suspend fun platformStats(): Map<Long, PlatformShowcaseStats> =
        platformStatsCache ?: gameRepository.statsByPlatform().also { platformStatsCache = it }

    private fun combineStats(a: PlatformShowcaseStats, b: PlatformShowcaseStats) = a.copy(
        gameCount = a.gameCount + b.gameCount,
        installedCount = a.installedCount + b.installedCount,
        achievementsEarned = a.achievementsEarned + b.achievementsEarned,
        achievementsTotal = a.achievementsTotal + b.achievementsTotal,
        playTimeMinutes = a.playTimeMinutes + b.playTimeMinutes,
        earliestYear = listOfNotNull(a.earliestYear, b.earliestYear).minOrNull(),
        latestYear = listOfNotNull(a.latestYear, b.latestYear).maxOrNull()
    )

    private fun gameShowcaseFor(
        cell: LibraryCellUi,
        stats: PlatformShowcaseStats?,
        covers: List<String>
    ) = com.nendo.argosy.ui.common.gameShowcase(
        context = context,
        name = if (cell.isAllGames) {
            context.getString(R.string.library_showcase_all_games)
        } else {
            cell.name
        },
        coverPaths = covers,
        stats = stats,
        fallbackCount = cell.itemCount
    )

    private fun mediaShowcaseFor(
        cell: LibraryCellUi,
        stats: MediaLibraryStats?,
        posters: List<String>
    ) = PresentationSlot.PlatformShowcase(
        name = cell.name,
        yearSpan = com.nendo.argosy.ui.common.showcaseYearSpan(stats?.earliestYear, stats?.latestYear),
        coverPaths = posters,
        facts = buildList {
            val countLabel = when (cell.mediaKind) {
                MediaCellKind.MOVIES -> R.string.library_showcase_fact_movies
                MediaCellKind.SHOWS, null -> R.string.library_showcase_fact_shows
            }
            add(fact(countLabel, (stats?.itemCount ?: cell.itemCount).toString()))
            stats ?: return@buildList
            if (stats.downloadedCount > 0) {
                add(fact(R.string.library_showcase_fact_downloaded, stats.downloadedCount.toString()))
            }
            add(fact(R.string.library_showcase_fact_watched, stats.watchedCount.toString()))
        }
    )

    private fun fact(@StringRes label: Int, value: String) =
        CompanionFact(context.getString(label), value)

    /**
     * Re-states the focused game to the showcase, for a return from somewhere that replaced it.
     *
     * The observer above only speaks when the focus changes, and coming back from a detail screen
     * changes nothing: without this the showcase keeps whatever the detail screen left there until
     * the viewer happens to move.
     */
    fun republishCompanionDetail() {
        isDescribing = true
        val state = _uiState.value
        if (state.view == LibraryView.PLATFORM_GRID) {
            viewModelScope.launch { publishPlatformShowcase(state.focusedPlatformCell) }
        } else {
            viewModelScope.launch { publishCompanionDetail(state.focusedGame?.id) }
        }
    }

    private suspend fun publishCompanionDetail(gameId: Long?) {
        if (!isDescribing) return
        val dsm = DualScreenManagerHolder.instance ?: return
        val friends = _uiState.value.friendsActivity
        val detail = gameId?.let { showcaseSource.gameDetail(it, friends) }
        if (!isDescribing) return
        dsm.setCompanionDetail(companionOwner, detail)
        if (gameId != null && showcaseSource.backfillLogo(gameId)) {
            val withLogo = showcaseSource.gameDetail(gameId, friends)
            if (isDescribing) dsm.setCompanionDetail(companionOwner, withLogo)
        }
    }

    /**
     * Stops describing this screen once it is no longer the one being driven.
     */
    fun clearCompanionDetail() {
        isDescribing = false
        val dsm = DualScreenManagerHolder.instance ?: return
        dsm.setCompanionDetail(companionOwner, null)
        dsm.releaseSlot(platformShowcaseOwner)
    }

    private fun extractGradientsForVisibleGames(focusedIndex: Int) {
        val games = _uiState.value.games
        if (games.isEmpty() || _uiState.value.isListLayout) return
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
                cachedPlatformEntities = platforms
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
                        isLoading = false,
                        filterOptions = state.filterOptions.copy(
                            platforms = platforms
                                .map { PlatformRef(it.id, it.getDisplayName()) }
                                .sortedBy { it.label }
                        )
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

                pendingInitialTileFilters?.let { tileFilters ->
                    if (applyTileFilters(tileFilters)) {
                        Log.d(TAG, "loadPlatforms: applied pending tile filters $tileFilters")
                        pendingInitialTileFilters = null
                    }
                }

                refreshPlatformCells()
                loadGames()
            }
        }
    }

    fun onResume() {
        republishCompanionDetail()

        if (romMRepository.isConnected()) {
            viewModelScope.launch {
                romMRepository.refreshFavoritesIfNeeded()
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val genres = gameRepository.getDistinctGenres(oneEntryPerGroup = true)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            val regions = gameRepository.getDistinctRegions()

            val series = collectionRepository.getNamesWithGamesByType(CollectionType.SERIES)

            Log.d(TAG, "loadFilterOptions: genres=${genres.size}, regions=${regions.size}, series=${series.size}")

            _uiState.update { state ->
                state.copy(
                    filterOptions = state.filterOptions.copy(
                        genres = genres,
                        regions = regions,
                        series = series
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

            val seriesIdsFlow = if (filters.series.isEmpty()) {
                flowOf<Set<Long>?>(null)
            } else {
                collectionRepository.observeGameIdsByTypeAndNames(CollectionType.SERIES, filters.series.toList())
                    .map<List<Long>, Set<Long>?> { it.toSet() }
            }

            val regionIdsFlow: Flow<Set<Long>?> = if (filters.regions.isEmpty()) {
                flowOf(null)
            } else {
                gameRepository.observeGameIdsInRegions(filters.regions)
            }

            val source = combine(baseFlow, seriesIdsFlow, regionIdsFlow) { games, seriesIds, regionIds ->
                Triple(games, seriesIds, regionIds)
            }

            source
                .catch { e ->
                    Log.e(TAG, "Error loading games, retrying: ${e.message}")
                    kotlinx.coroutines.delay(100)
                    emitAll(source)
                }
                .heldWhile(launchProgressTracker.launchLive)
                .collectLatest { (games, seriesIds, regionIds) ->
                    val normalizedQuery = com.nendo.argosy.util.SearchNormalizer.normalize(filters.searchQuery)
                    val platformIds = filters.platforms.map { it.id }.toSet()
                    val filteredGames = games.filter { game ->
                        val matchesSearch = filters.searchQuery.isEmpty() ||
                            com.nendo.argosy.util.SearchNormalizer.normalize(game.title).contains(normalizedQuery)
                        val matchesPlatform = platformIds.isEmpty() ||
                            game.platformId in platformIds
                        val matchesGenre = filters.genres.isEmpty() ||
                            filters.genres.contains(game.genre)
                        val matchesRegion = regionIds == null || game.id in regionIds
                        val matchesPlayers = filters.players?.admits(PlayerCount.parse(game.players)) ?: true
                        val matchesSeries = seriesIds == null || game.id in seriesIds
                        matchesSearch && matchesPlatform && matchesGenre && matchesRegion &&
                            matchesPlayers && matchesSeries
                    }

                    val sections = computeSections(filteredGames, filters.sort, sortPartition)
                    val sectionLabels = sections.map { it.sidebarLabel }

                    var gameOffset = 0
                    val gridItems = sections.flatMap { section ->
                        val header = LibraryGridItem.Header(section.label, section.sidebarLabel)
                        val gameItems = section.games.mapIndexed { i, game ->
                            LibraryGridItem.Game(game.toUi(cachedPlatformDisplayNames), gameIndex = gameOffset + i)
                        }
                        gameOffset += section.games.size
                        listOf(header) + gameItems
                    }

                    val allGamesSorted = sections.flatMap { it.games }
                    val gamesList = allGamesSorted.map { it.toUi(cachedPlatformDisplayNames) }

                    Log.d(TAG, "loadGames: ${games.size} total, ${filteredGames.size} after filters, ${sections.size} sections")
                    val siblingFocusIndex = settleSiblingFocus(gamesList.map { it.id })
                    _uiState.update { uiState ->
                        val shouldResetFocus = uiState.games.isEmpty()
                        val newFocusedIndex = siblingFocusIndex
                            ?: if (shouldResetFocus) 0 else uiState.focusedIndex.coerceAtMost((gamesList.size - 1).coerceAtLeast(0))
                        val currentSectionLabel = computeSectionLabelForGameIndex(newFocusedIndex, sections)
                        uiState.copy(
                            games = gamesList,
                            focusedIndex = newFocusedIndex,
                            gridItems = gridItems,
                            sectionLabels = sectionLabels,
                            currentSectionLabel = currentSectionLabel,
                            sectionRailFocusIndex = uiState.sectionRailFocusIndex
                                ?.takeIf { sectionLabels.size >= MIN_SECTION_RAIL_LABELS }
                                ?.coerceAtMost(sectionLabels.lastIndex),
                            sectionJumpTrigger = if (siblingFocusIndex != null) {
                                uiState.sectionJumpTrigger + 1
                            } else {
                                uiState.sectionJumpTrigger
                            }
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
            if (item is LibraryGridItem.Header) return item.sidebarLabel
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

    val quickNavigationEnabled: StateFlow<Boolean> get() = quickNavigation.enabled

    fun jumpToAdjacentSection(direction: Int) {
        val state = _uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return
        val currentIndex = labels.indexOf(state.currentSectionLabel)
        val target = if (currentIndex < 0) 0 else (currentIndex + direction).mod(labels.size)
        jumpToSection(labels[target])
    }

    fun jumpToSection(sectionLabel: String, showOverlay: Boolean = true) {
        resetStickyColumn()
        val state = _uiState.value
        val headerIndex = state.gridItems.indexOfFirst {
            it is LibraryGridItem.Header && it.sidebarLabel == sectionLabel
        }
        val firstGameInSection = if (headerIndex >= 0) {
            state.gridItems.getOrNull(headerIndex + 1) as? LibraryGridItem.Game
        } else {
            null
        }

        if (firstGameInSection == null) {
            val targetGame = state.gridItems
                .asSequence()
                .withIndex()
                .filter { (_, item) -> item is LibraryGridItem.Game }
                .firstOrNull { (index, _) ->
                    findSectionLabelForGridItem(state.gridItems, index) == sectionLabel
                }
                ?.value as? LibraryGridItem.Game
            if (targetGame != null) {
                _uiState.update {
                    it.copy(
                        focusedIndex = targetGame.gameIndex,
                        currentSectionLabel = sectionLabel,
                        isTouchMode = false,
                        sectionRailFocusIndex = null,
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
                sectionRailFocusIndex = null,
                sectionJumpTrigger = it.sectionJumpTrigger + 1
            )
        }
        extractGradientsForVisibleGames(firstGameInSection.gameIndex)
        if (showOverlay) showSectionOverlay(sectionLabel)
    }

    /**
     * Moves gamepad focus off the games onto the A-Z rail, at the letter of the section the cursor
     * is in. Returns false when the rail is not shown.
     */
    fun enterSectionRail(): Boolean {
        val state = _uiState.value
        if (!state.showSectionSidebar) return false
        val index = state.sectionLabels.indexOf(state.currentSectionLabel).coerceAtLeast(0)
        _uiState.update { it.copy(sectionRailFocusIndex = index, isTouchMode = false) }
        return true
    }

    fun moveSectionRailFocus(delta: Int) {
        _uiState.update { state ->
            val index = state.sectionRailFocusIndex ?: return@update state
            if (state.sectionLabels.isEmpty()) return@update state.copy(sectionRailFocusIndex = null)
            state.copy(sectionRailFocusIndex = (index + delta).mod(state.sectionLabels.size))
        }
    }

    fun confirmSectionRail() {
        val label = _uiState.value.sectionRailFocusedLabel
        exitSectionRail()
        label?.let { jumpToSection(it) }
    }

    fun exitSectionRail() {
        _uiState.update { it.copy(sectionRailFocusIndex = null) }
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
        _uiState.update {
            it.copy(currentPlatformIndex = nextIndex, focusedIndex = 0, sectionRailFocusIndex = null)
        }
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
        _uiState.update {
            it.copy(currentPlatformIndex = prevIndex, focusedIndex = 0, sectionRailFocusIndex = null)
        }
        loadGames()
    }

    fun syncCurrentPlatform() {
        val platform = _uiState.value.currentPlatform ?: return
        platformSyncQueue.enqueuePlatform(platform.id, platform.name) {
            viewModelScope.launch { loadGames() }
        }
    }

    private fun openOnPlatform(platformId: Long) {
        val index = _uiState.value.platforms.indexOfFirst { it.id == platformId }
        if (index < 0) {
            pendingInitialPlatformId = platformId
            return
        }
        _uiState.update { it.copy(currentPlatformIndex = index, canReturnToPlatformGrid = true) }
    }

    fun setInitialPlatform(platformId: Long) {
        explicitDestinationRequested = true
        _uiState.update { it.copy(view = LibraryView.GAMES, canReturnToPlatformGrid = false) }
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

    /**
     * Opens the library on a home tile's stored filters.
     *
     * Platforms arrive as ids and are held as display names, so this waits for the platform list
     * when it has not loaded yet. The cursor is left off any single platform: the filter carries
     * the narrowing whether it names one platform or several, and pinning the header as well would
     * put the same restriction in two places.
     */
    fun setInitialTileFilters(filters: LibraryLinkFilters) {
        explicitDestinationRequested = true
        explicitTileFiltersRequested = true
        explicitSourceRequested = true
        _uiState.update { it.copy(view = LibraryView.GAMES, canReturnToPlatformGrid = false) }
        if (!applyTileFilters(filters)) {
            Log.d(TAG, "setInitialTileFilters: platforms not loaded yet, storing pending $filters")
            pendingInitialTileFilters = filters
            return
        }
        loadGames()
    }

    private fun applyTileFilters(filters: LibraryLinkFilters): Boolean {
        if (filters.platformIds.isNotEmpty() && cachedPlatformDisplayNames.isEmpty()) return false
        val platforms = filters.platformIds
            .mapNotNull { id -> cachedPlatformDisplayNames[id]?.let { PlatformRef(id, it) } }
            .toSet()
        _uiState.update {
            it.copy(
                activeFilters = it.activeFilters.copy(
                    source = filters.source,
                    platforms = platforms,
                    genres = filters.genres,
                    regions = emptySet(),
                    series = filters.series,
                    players = filters.players,
                    sort = filters.sort
                )
            )
        }
        return true
    }

    fun setInitialSourceFilter(source: SourceFilter) {
        explicitSourceRequested = true
        explicitDestinationRequested = true
        _uiState.update { it.copy(view = LibraryView.GAMES, canReturnToPlatformGrid = false) }
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

    /**
     * Moves the cursor on the platform landing, wrapping top to bottom.
     *
     * LEFT from the first column deliberately reports no move: the app treats an unhandled LEFT as
     * "open the drawer", which is how every other grid in the launcher stays reachable from a
     * controller, so wrapping horizontally here would strand the drawer.
     */
    fun movePlatformGridFocus(direction: FocusMove): Boolean {
        val state = _uiState.value
        val total = state.platformCells.size
        if (total == 0) return false

        val cols = state.platformGridColumns.coerceAtLeast(1)
        val current = state.platformGridFocusedIndex
        val column = current % cols

        val target = when (direction) {
            FocusMove.LEFT -> if (column > 0) current - 1 else return false
            FocusMove.RIGHT -> if (column < cols - 1 && current + 1 < total) current + 1 else current
            FocusMove.UP, FocusMove.DOWN -> {
                val rows = (total + cols - 1) / cols
                val delta = if (direction == FocusMove.DOWN) 1 else -1
                val nextRow = (current / cols + delta).mod(rows)
                (nextRow * cols + column).coerceAtMost(total - 1)
            }
        }

        if (target == current) return true
        _uiState.update { it.copy(platformGridFocusedIndex = target) }
        return true
    }

    fun liftPlatformCell(): Boolean {
        var lifted = false
        _uiState.update { state ->
            if (state.isReorderingPlatforms || state.focusedPlatformCell == null) return@update state
            val first = state.platformCells.indexOfFirst { it.isPlatform }
            val platformCells = state.platformCells.filter { it.isPlatform }
            val reorder = ListReorder.lift(platformCells, state.platformGridFocusedIndex - first)
                ?: return@update state
            lifted = true
            state.copy(platformReorder = reorder)
        }
        return lifted
    }

    fun liftPlatformCellAt(cellIndex: Int): Boolean {
        val state = _uiState.value
        if (state.isReorderingPlatforms || state.platformCells.getOrNull(cellIndex)?.isPlatform != true) return false
        _uiState.update { it.copy(platformGridFocusedIndex = cellIndex) }
        return liftPlatformCell()
    }

    fun moveHeldPlatformCell(direction: FocusMove): Boolean {
        val state = _uiState.value
        val cols = state.platformGridColumns.coerceAtLeast(1)
        val delta = when (direction) {
            FocusMove.LEFT -> -1
            FocusMove.RIGHT -> 1
            FocusMove.UP -> -cols
            FocusMove.DOWN -> cols
        }
        return placeHeldPlatformCell(state.platformGridFocusedIndex + delta)
    }

    fun placeHeldPlatformCell(cellIndex: Int): Boolean {
        var moved = false
        _uiState.update { state ->
            val reorder = state.platformReorder ?: return@update state
            val first = state.platformCells.indexOfFirst { it.isPlatform }
            val platformCells = state.platformCells.filter { it.isPlatform }
            val step = reorder.moveTo(platformCells, cellIndex - first)
            if (!step.moved) return@update state
            moved = true
            state.withPlatformCells(first, platformCells.size, step.items).copy(
                platformReorder = step.reorder,
                platformGridFocusedIndex = first + step.reorder.heldIndex
            )
        }
        return moved
    }

    fun dropPlatformCell() {
        var committed: List<LibraryCellUi>? = null
        _uiState.update { state ->
            val reorder = state.platformReorder ?: return@update state
            committed = reorder.changedOrder(state.platformCells.filter { it.isPlatform })
            state.copy(platformReorder = null)
        }
        val platformIds = committed?.mapNotNull { (it.target as? LibraryCellTarget.Platform)?.platformId }
        viewModelScope.launch {
            if (platformIds == null || !reorderPlatforms(platformIds)) refreshPlatformCells()
        }
    }

    fun cancelPlatformCellLift() {
        _uiState.update { state ->
            val reorder = state.platformReorder ?: return@update state
            val first = state.platformCells.indexOfFirst { it.isPlatform }
            state.withPlatformCells(first, reorder.backup.size, reorder.backup).copy(
                platformReorder = null,
                platformGridFocusedIndex = first + reorder.originIndex
            )
        }
        viewModelScope.launch { refreshPlatformCells() }
    }

    private fun LibraryUiState.withPlatformCells(
        first: Int,
        count: Int,
        replacement: List<LibraryCellUi>
    ): LibraryUiState = copy(
        platformCells = platformCells.take(first) + replacement + platformCells.drop(first + count)
    )

    /**
     * Opens whatever the cell under [index] stands for.
     *
     * A platform or All Games swaps this screen over to its games. A media library is a destination
     * of its own screen, so it leaves rather than filtering: the cursor is parked on the cell first
     * so that coming back lands on the library that was opened, not on the top of the grid.
     */
    fun openLandingCell(index: Int, onMediaLibrarySelect: (String) -> Unit) {
        val state = _uiState.value
        val cell = state.platformCells.getOrNull(index) ?: return

        val target = cell.target
        if (target is LibraryCellTarget.Media) {
            _uiState.update { it.copy(platformGridFocusedIndex = index) }
            onMediaLibrarySelect(target.libraryId)
            return
        }

        val platformIndex = (target as? LibraryCellTarget.Platform)
            ?.let { platform -> state.platforms.indexOfFirst { it.id == platform.platformId } }
            ?.takeIf { it >= 0 }
            ?: -1

        resetStickyColumn()
        _uiState.update {
            it.copy(
                view = LibraryView.GAMES,
                canReturnToPlatformGrid = true,
                platformGridFocusedIndex = index,
                currentPlatformIndex = platformIndex,
                focusedIndex = 0,
                sectionRailFocusIndex = null
            )
        }
        loadGames()
    }

    /**
     * Returns to the landing. Counts are rebuilt on the way back because a game can have been hidden,
     * downloaded or deleted while the list was open.
     */
    fun returnToPlatformGrid() {
        _uiState.update { it.copy(view = LibraryView.PLATFORM_GRID, sectionRailFocusIndex = null) }
        viewModelScope.launch { refreshPlatformCells() }
    }

    private val gridNav = GridFocusNavigator()

    fun resetStickyColumn() { gridNav.resetStickyColumn() }

    /**
     * Geometry of the currently laid-out covers, pushed by the masonry grid. When
     * present, D-pad navigation is resolved spatially (the masonry layout does not
     * follow the reading-order rows the [GridFocusNavigator] assumes).
     */
    @Volatile private var masonryCells: List<FocusCellBounds> = emptyList()

    fun setMasonryCells(cells: List<FocusCellBounds>) { masonryCells = cells }

    fun moveFocus(direction: FocusMove): Boolean {
        val state = _uiState.value
        if (state.games.isEmpty()) return false

        val gridDirection = when (direction) {
            FocusMove.UP -> GridDirection.UP
            FocusMove.DOWN -> GridDirection.DOWN
            FocusMove.LEFT -> GridDirection.LEFT
            FocusMove.RIGHT -> GridDirection.RIGHT
        }
        val newIndex = if (masonryCells.isNotEmpty() && !state.isListLayout) {
            navigateMasonry(gridDirection, state.focusedIndex, state.columnsCount, state.games.size)
        } else {
            val rows = GridFocusNavigator.buildGridRows(
                state.gridItems, state.columnsCount,
                isHeader = { it is LibraryGridItem.Header },
                gameIndex = { (it as LibraryGridItem.Game).gameIndex }
            )
            gridNav.navigate(gridDirection, state.focusedIndex, rows)
        } ?: return false

        _uiState.update { it.copy(focusedIndex = newIndex, lastFocusMove = direction, isTouchMode = false) }
        updateCurrentSectionFromFocus()
        extractGradientsForVisibleGames(newIndex)
        return true
    }

    /**
     * Resolves a D-pad move using the real on-screen positions of the masonry
     * covers: pick the nearest cell in the pressed direction, preferring cells
     * that overlap the current one on the perpendicular axis (same row / column).
     * Falls back to a reading-order row jump for vertical moves onto covers that
     * are not laid out yet (just off-screen).
     */
    private fun navigateMasonry(
        direction: GridDirection,
        currentIndex: Int,
        columns: Int,
        gamesCount: Int
    ): Int? {
        val cells = masonryCells
        val cur = cells.find { it.gameIndex == currentIndex } ?: return null
        val cx = (cur.left + cur.right) / 2f
        val cy = (cur.top + cur.bottom) / 2f
        fun cxOf(c: FocusCellBounds) = (c.left + c.right) / 2f
        fun cyOf(c: FocusCellBounds) = (c.top + c.bottom) / 2f
        fun vOverlap(c: FocusCellBounds) = c.top < cur.bottom && c.bottom > cur.top
        fun hOverlap(c: FocusCellBounds) = c.left < cur.right && c.right > cur.left
        val others = cells.filter { it.gameIndex != currentIndex }

        val pick = when (direction) {
            GridDirection.LEFT, GridDirection.RIGHT -> {
                val inDir = others.filter {
                    if (direction == GridDirection.LEFT) cxOf(it) < cx - 1f else cxOf(it) > cx + 1f
                }
                inDir.minWithOrNull(
                    compareByDescending<FocusCellBounds> { vOverlap(it) }
                        .thenBy { kotlin.math.abs(cxOf(it) - cx) }
                        .thenBy { kotlin.math.abs(cyOf(it) - cy) }
                )
            }
            GridDirection.UP, GridDirection.DOWN -> {
                val inDir = others.filter {
                    if (direction == GridDirection.UP) cyOf(it) < cy - 1f else cyOf(it) > cy + 1f
                }
                inDir.minWithOrNull(
                    compareByDescending<FocusCellBounds> { hOverlap(it) }
                        .thenBy { kotlin.math.abs(cyOf(it) - cy) }
                        .thenBy { kotlin.math.abs(cxOf(it) - cx) }
                )
            }
        }
        if (pick != null) return pick.gameIndex

        return when (direction) {
            GridDirection.DOWN -> (currentIndex + columns).takeIf { it < gamesCount }
            GridDirection.UP -> (currentIndex - columns).takeIf { it >= 0 }
            else -> null
        }
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
            val options = state.currentCategoryOptions(context)
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
        val options = state.currentCategoryOptions(context)

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
            FilterCategory.PLATFORM -> {
                val platform = state.filterOptions.platforms.getOrNull(optionIndex) ?: return
                val current = state.activeFilters.platforms
                val updated = if (platform in current) current - platform else current + platform
                state.activeFilters.copy(platforms = updated)
            }
            FilterCategory.GENRE -> {
                val genre = options.getOrNull(optionIndex) ?: return
                val currentGenres = state.activeFilters.genres
                val newGenres = if (genre in currentGenres) currentGenres - genre else currentGenres + genre
                state.activeFilters.copy(genres = newGenres)
            }
            FilterCategory.REGION -> {
                val region = options.getOrNull(optionIndex) ?: return
                val current = state.activeFilters.regions
                val updated = if (region in current) current - region else current + region
                state.activeFilters.copy(regions = updated)
            }
            FilterCategory.PLAYERS -> {
                val bucket = PlayerCountBucket.entries.getOrNull(optionIndex) ?: return
                val updated = if (bucket == state.activeFilters.players) null else bucket
                state.activeFilters.copy(players = updated)
            }
            FilterCategory.SERIES -> {
                val series = options.getOrNull(optionIndex) ?: return
                val current = state.activeFilters.series
                val updated = if (series in current) current - series else current + series
                state.activeFilters.copy(series = updated)
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
            FilterCategory.PLATFORM -> state.activeFilters.copy(platforms = emptySet())
            FilterCategory.GENRE -> state.activeFilters.copy(genres = emptySet())
            FilterCategory.REGION -> state.activeFilters.copy(regions = emptySet())
            FilterCategory.PLAYERS -> state.activeFilters.copy(players = null)
            FilterCategory.SERIES -> state.activeFilters.copy(series = emptySet())
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

    /**
     * Puts a game on the curated home grid, in the first free cell of its last page - the same
     * placement a finished download gets. The library has no page in view to aim at, so appending
     * is the only placement it can honestly offer.
     */
    fun addGameToHomeGrid(gameId: Long) {
        viewModelScope.launch {
            val config = preferencesRepository.userPreferences.first().homeLayout.customGrid
            homeTileRepository.append(
                ownerUserId = syncPreferencesRepository.getRommUserId(),
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(gameId),
                layout = customGridShapeStore.layoutFor(config)
            )
            notificationManager.showSuccess(
                NotificationText.Res(R.string.library_notice_added_to_home_grid)
            )
        }
    }

    fun toggleQuickMenu() {
        if (_uiState.value.showQuickMenu) {
            quickMenuOpenJob?.cancel()
            _uiState.update { it.copy(showQuickMenu = false, quickMenuFocusIndex = 0) }
            soundManager.play(SoundType.CLOSE_MODAL)
            return
        }
        if (quickMenuOpenJob?.isActive == true) return
        val gameId = _uiState.value.focusedGame?.id ?: return
        quickMenuOpenJob = viewModelScope.launch {
            val hasChoice = siblingChoice.hasChoice(gameId)
            if (_uiState.value.focusedGame?.id != gameId) return@launch
            _uiState.update {
                it.copy(showQuickMenu = true, quickMenuFocusIndex = 0, quickMenuHasSiblingGroup = hasChoice)
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    private fun quickMenuRows(state: LibraryUiState): List<LibraryQuickMenuRow> {
        val game = state.focusedGame ?: return emptyList()
        return libraryQuickMenuRows(game, state.isCustomGridHome, state.quickMenuHasSiblingGroup)
    }

    fun moveQuickMenuFocus(delta: Int) {
        _uiState.update {
            val maxIndex = quickMenuRows(it).lastIndex
            if (maxIndex < 0) return@update it
            val newIndex = (it.quickMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(quickMenuFocusIndex = newIndex)
        }
    }

    fun confirmQuickMenuSelection(onGameSelect: (Long) -> Unit): InputResult {
        val state = _uiState.value
        val game = state.focusedGame ?: return InputResult.HANDLED
        val row = quickMenuRows(state).getOrNull(state.quickMenuFocusIndex) ?: return InputResult.HANDLED

        return when (row) {
            LibraryQuickMenuRow.PRIMARY -> {
                when {
                    game.needsInstall -> installApk(game.id)
                    game.isDownloaded -> launchGame(game.id)
                    game.source == com.nendo.argosy.data.model.GameSource.STEAM -> linkSteamLauncher(game.id)
                    else -> downloadGame(game.id)
                }
                toggleQuickMenu()
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.FAVORITE -> {
                val sound = if (game.isFavorite) SoundType.UNFAVORITE else SoundType.FAVORITE
                toggleFavorite(game.id)
                InputResult.handled(sound)
            }
            LibraryQuickMenuRow.DETAILS -> {
                gameNavigationContext.setContext(state.games.map { it.id })
                onGameSelect(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.ADD_TO_COLLECTION -> {
                toggleQuickMenu()
                showAddToCollectionModal(game.id)
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.ADD_TO_GRID -> {
                toggleQuickMenu()
                addGameToHomeGrid(game.id)
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.ACTIVE_VARIANT -> {
                openActiveVariant(game.id)
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.REFRESH -> {
                if (game.isAndroidApp) refreshAndroidGameData(game.id) else refreshGameData(game.id)
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.RESYNC_PLATFORM -> {
                syncCurrentPlatform()
                toggleQuickMenu()
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.DELETE -> {
                if (game.isAndroidApp) uninstallAndroidApp(game.id) else deleteLocalFile(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.REMOVE_FROM_LIBRARY -> {
                toggleQuickMenu()
                removeFromLibrary(game.id)
                InputResult.HANDLED
            }
            LibraryQuickMenuRow.HIDE -> {
                if (game.isHidden) unhideGame(game.id) else hideGame(game.id)
                toggleQuickMenu()
                InputResult.HANDLED
            }
        }
    }

    fun removeFromLibrary(gameId: Long) {
        viewModelScope.launch {
            if (gameActions.removeFromLibrary(gameId)) {
                notificationManager.showSuccess(
                    NotificationText.Res(R.string.library_notice_removed_from_library)
                )
                loadGames()
            } else {
                notificationManager.showError(
                    NotificationText.Res(R.string.library_notice_remove_failed)
                )
            }
        }
    }

    fun openActiveVariant(gameId: Long) {
        toggleQuickMenu()
        siblingChoice.openActiveVariant(viewModelScope, gameId) { shownGameId ->
            followSiblingPick(gameId, shownGameId)
        }
    }

    fun moveSiblingChoiceFocus(delta: Int) = siblingChoice.moveFocus(delta)

    fun setSiblingChoiceFocus(index: Int) = siblingChoice.setFocus(index)

    fun confirmSiblingChoice() = siblingChoice.confirm(viewModelScope)

    fun dismissSiblingChoice() = siblingChoice.dismiss()

    fun hideGame(gameId: Long) {
        gameConfirm.requestHide(gameId) {
            viewModelScope.launch { gameActions.hideGame(gameId) }
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
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.library_notice_game_data_refreshed)
                    )
                    loadGames()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
                }
            }
            toggleQuickMenu()
        }
    }

    fun deleteLocalFile(gameId: Long) {
        gameConfirm.requestDelete(gameId) { withSoundtrack ->
            viewModelScope.launch {
                gameActions.deleteLocalFile(gameId, withSoundtrack)
                notificationManager.showSuccess(
                    NotificationText.Res(R.string.library_notice_download_deleted)
                )
            }
        }
    }

    fun refreshAndroidGameData(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.refreshAndroidGameData(gameId)) {
                is RefreshAndroidResult.Success -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.library_notice_android_data_refreshed)
                    )
                    loadGames()
                }
                is RefreshAndroidResult.Error -> {
                    notificationManager.showError(NotificationText.Raw(result.message))
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
        gameLaunchDelegate.launchGame(
            scope = viewModelScope,
            gameId = gameId,
            channelName = channelName,
            start = GameStart(gameId, onFailure = {
                notificationManager.showError(NotificationText.Res(R.string.notif_gamelaunch_launch_failed))
            })
        )
    }

    fun downloadGame(gameId: Long) {
        siblingChoice.requestDownload(viewModelScope, gameId) { chosenGameId ->
            followSiblingPick(gameId, chosenGameId)
            queueDownload(chosenGameId)
        }
    }

    private fun queueDownload(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.library_notice_already_downloaded)
                    )
                }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess(
                        NotificationText.Plural(
                            R.plurals.library_notice_downloading_discs,
                            result.discCount,
                            listOf(result.discCount)
                        )
                    )
                }
                is DownloadResult.Error -> notificationManager.showError(result.reason.toNotificationText())
                is DownloadResult.ExtractionFailed -> {
                    notificationManager.showError(
                        NotificationText.Res(R.string.library_notice_extraction_failed)
                    )
                }
            }
        }
    }

    fun linkSteamLauncher(gameId: Long) {
        steamLauncherLinkController.requestLink(gameId)
    }

    fun installApk(gameId: Long) {
        viewModelScope.launch {
            val success = apkInstallManager.installApkForGame(gameId)
            if (!success) {
                notificationManager.showError(
                    NotificationText.Res(R.string.library_notice_apk_install_failed)
                )
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

    private suspend fun GameEntity.toUi(platformDisplayNames: Map<Long, String> = emptyMap()): LibraryGameUi =
        toLibraryGameUi(
            downloadStatus = downloadFileStatusRepository,
            art = imageCacheManager.artFor(id),
            platformDisplayName = platformDisplayNames[platformId],
            gradientColors = gradientExtractionDelegate.getGradient(id)
        )

    private suspend fun GameListItem.toUi(platformDisplayNames: Map<Long, String> = emptyMap()): LibraryGameUi =
        toLibraryGameUi(
            downloadStatus = downloadFileStatusRepository,
            art = imageCacheManager.artFor(id),
            platformDisplayName = platformDisplayNames[platformId],
            gradientColors = gradientExtractionDelegate.getGradient(id)
        )

    fun enterTouchMode() {
        _uiState.update { it.copy(isTouchMode = true, hasSelectedGame = false, sectionRailFocusIndex = null) }
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
            _uiState.update {
                it.copy(
                    focusedIndex = index,
                    hasSelectedGame = true,
                    isTouchMode = true,
                    sectionRailFocusIndex = null
                )
            }
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
        exitSectionRail()
        toggleQuickMenu()
    }

    fun createInputHandler(
        isDefaultView: Boolean,
        onGameSelect: (Long) -> Unit,
        onMediaLibrarySelect: (String) -> Unit,
        onNavigateToDefault: () -> Unit,
        onDrawerToggle: () -> Unit
    ): InputHandler = object : InputHandler {
        private val isLiftingPlatform: Boolean
            get() = _uiState.value.let { it.isPlatformGrid && it.isReorderingPlatforms }

        private fun moveHeld(direction: FocusMove): InputResult =
            if (moveHeldPlatformCell(direction)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onUp(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> moveHeld(FocusMove.UP)
                state.isPlatformGrid ->
                    if (movePlatformGridFocus(FocusMove.UP)) InputResult.HANDLED else InputResult.UNHANDLED
                state.showAddToCollectionModal -> { moveCollectionFocusUp(); InputResult.HANDLED }
                state.showFilterMenu -> { moveFilterOptionFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(-1); InputResult.HANDLED }
                state.isSectionRailFocused -> { moveSectionRailFocus(-1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.UP)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> moveHeld(FocusMove.DOWN)
                state.isPlatformGrid ->
                    if (movePlatformGridFocus(FocusMove.DOWN)) InputResult.HANDLED else InputResult.UNHANDLED
                state.showAddToCollectionModal -> { moveCollectionFocusDown(); InputResult.HANDLED }
                state.showFilterMenu -> { moveFilterOptionFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> { moveQuickMenuFocus(1); InputResult.HANDLED }
                state.isSectionRailFocused -> { moveSectionRailFocus(1); InputResult.HANDLED }
                else -> if (moveFocus(FocusMove.DOWN)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> moveHeld(FocusMove.LEFT)
                state.isPlatformGrid ->
                    if (movePlatformGridFocus(FocusMove.LEFT)) InputResult.HANDLED else InputResult.UNHANDLED
                state.showAddToCollectionModal -> InputResult.HANDLED
                state.showFilterMenu -> { moveFilterCategoryFocus(-1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                state.isSectionRailFocused -> {
                    confirmSectionRail()
                    InputResult.handled(SoundType.SECTION_CHANGE)
                }
                else -> if (moveFocus(FocusMove.LEFT)) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> moveHeld(FocusMove.RIGHT)
                state.isPlatformGrid ->
                    if (movePlatformGridFocus(FocusMove.RIGHT)) InputResult.HANDLED else InputResult.UNHANDLED
                state.showAddToCollectionModal -> InputResult.HANDLED
                state.showFilterMenu -> { moveFilterCategoryFocus(1); InputResult.HANDLED }
                state.showQuickMenu -> InputResult.HANDLED
                state.isSectionRailFocused -> InputResult.handled(SoundType.BOUNDARY)
                moveFocus(FocusMove.RIGHT) || enterSectionRail() -> InputResult.HANDLED
                else -> InputResult.UNHANDLED
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> {
                    dropPlatformCell()
                    InputResult.handled(SoundType.SELECT)
                }
                state.isPlatformGrid -> {
                    openLandingCell(state.platformGridFocusedIndex, onMediaLibrarySelect)
                    InputResult.HANDLED
                }
                state.showAddToCollectionModal -> {
                    confirmCollectionSelection()
                    InputResult.HANDLED
                }
                state.showFilterMenu -> {
                    confirmFilterSelection()
                    InputResult.HANDLED
                }
                state.showQuickMenu -> confirmQuickMenuSelection(onGameSelect)
                state.isSectionRailFocused -> {
                    confirmSectionRail()
                    InputResult.handled(SoundType.SECTION_CHANGE)
                }
                else -> {
                    state.focusedGame?.let { game -> onGameSelect(game.id) }
                    InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> {
                    cancelPlatformCellLift()
                    InputResult.handled(SoundType.BACK)
                }
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
                state.isSectionRailFocused -> {
                    exitSectionRail()
                    InputResult.HANDLED
                }
                state.canReturnToPlatformGrid && !state.isPlatformGrid -> {
                    returnToPlatformGrid()
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
            if (isLiftingPlatform) return InputResult.HANDLED
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (_uiState.value.showQuickMenu) {
                toggleQuickMenu()
                return InputResult.UNHANDLED
            }
            if (_uiState.value.showFilterMenu) {
                toggleFilterMenu()
                return InputResult.HANDLED
            }
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (isLiftingPlatform) {
                dropPlatformCell()
                return InputResult.handled(SoundType.SELECT)
            }
            if (_uiState.value.isPlatformGrid) {
                return if (liftPlatformCell()) InputResult.handled(SoundType.SELECT) else InputResult.HANDLED
            }
            if (_uiState.value.isSectionRailFocused) return InputResult.HANDLED
            if (_uiState.value.showFilterMenu && _uiState.value.currentFilterCategory == FilterCategory.SEARCH) {
                viewModelScope.launch { _events.emit(LibraryEvent.OpenSearchKeyboard) }
                return InputResult.HANDLED
            }
            val game = _uiState.value.focusedGame ?: return InputResult.UNHANDLED
            if (_uiState.value.showAddToCollectionModal || _uiState.value.showQuickMenu || _uiState.value.showFilterMenu) return InputResult.HANDLED
            if (_uiState.value.activeFilters.source == SourceFilter.HIDDEN) {
                unhideGame(game.id)
            } else {
                toggleFavorite(game.id)
            }
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (_uiState.value.isPlatformGrid) return InputResult.HANDLED
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (_uiState.value.showQuickMenu) return InputResult.HANDLED
            if (_uiState.value.showFilterMenu) {
                clearCurrentCategoryFilters()
                return InputResult.HANDLED
            }
            if (_uiState.value.isSectionRailFocused) return InputResult.HANDLED
            toggleFilterMenu()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (isLiftingPlatform) return InputResult.HANDLED
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) return InputResult.UNHANDLED
            if (_uiState.value.isPlatformGrid) return InputResult.HANDLED
            if (_uiState.value.isSectionRailFocused) return InputResult.HANDLED
            if (_uiState.value.focusedGame != null) {
                toggleQuickMenu()
            }
            return InputResult.HANDLED
        }

        override fun onLongSelect(): InputResult =
            if (_uiState.value.showAddToCollectionModal || isLiftingPlatform) InputResult.HANDLED
            else InputResult.UNHANDLED

        override fun onLongConfirm(): InputResult {
            if (_uiState.value.isPlatformGrid) return InputResult.handled(SoundType.BOUNDARY)
            if (_uiState.value.focusedGame == null) return InputResult.handled(SoundType.BOUNDARY)
            if (_uiState.value.isSectionRailFocused) return InputResult.handled(SoundType.BOUNDARY)
            toggleQuickMenu()
            return InputResult.HANDLED
        }

        override fun onPrevSection(): InputResult = bumper(-1)

        override fun onNextSection(): InputResult = bumper(1)

        override fun onPrevTrigger(): InputResult = trigger(-1)

        override fun onNextTrigger(): InputResult = trigger(1)

        override fun onRightStickUp(): InputResult = sectionJump(-1)

        override fun onRightStickDown(): InputResult = sectionJump(1)

        private fun bumper(direction: Int): InputResult {
            val state = _uiState.value
            return when {
                isLiftingPlatform -> InputResult.HANDLED
                state.showFilterMenu -> { moveFilterOptionFocus(direction * FILTER_OPTION_PAGE); InputResult.HANDLED }
                !quickNavigation.enabled.value -> switchPlatform(direction)
                state.hasInternalFocus && !state.isOnPlatformLanding -> InputResult.HANDLED
                else -> InputResult.UNHANDLED
            }
        }

        private fun trigger(direction: Int): InputResult {
            if (quickNavigation.enabled.value) return switchPlatform(direction)
            return sectionJump(direction)
        }

        private fun sectionJump(direction: Int): InputResult {
            val state = _uiState.value
            if (state.hasInternalFocus || isLiftingPlatform) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToAdjacentSection(direction)
            return InputResult.handled(SoundType.SECTION_CHANGE)
        }

        private fun switchPlatform(direction: Int): InputResult {
            if (_uiState.value.hasInternalFocus) return InputResult.HANDLED
            if (direction < 0) previousPlatform() else nextPlatform()
            return InputResult.HANDLED
        }
    }

    private val LibraryUiState.hasInternalFocus: Boolean
        get() = isPlatformGrid || showAddToCollectionModal || showFilterMenu || showQuickMenu ||
            isSectionRailFocused

    private val LibraryUiState.isOnPlatformLanding: Boolean
        get() = isPlatformGrid && !showAddToCollectionModal && !showFilterMenu && !showQuickMenu
}
