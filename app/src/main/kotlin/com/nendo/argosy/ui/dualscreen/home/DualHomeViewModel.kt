/**
 * DUAL-SCREEN COMPONENT - Lower display ViewModel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Manages game carousel state, platform switching, collections, and library grid.
 */
package com.nendo.argosy.ui.dualscreen.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.repository.DownloadQueueRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.ui.common.appId
import com.nendo.argosy.ui.common.toIndicator
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.domain.model.HomeSectionKind
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.Section
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SortableProps
import com.nendo.argosy.data.model.computeGenericSections
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.common.GridFocusNavigator
import com.nendo.argosy.ui.components.AutoGridMove
import com.nendo.argosy.ui.components.autoGridMove
import com.nendo.argosy.ui.common.toHomeGameUi
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.toHomeMediaUi
import com.nendo.argosy.ui.screens.media.episodeLabel
import com.nendo.argosy.ui.screens.media.toCompanionDetail
import com.nendo.argosy.ui.screens.media.toMediaItemUi
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.screens.library.SourceFilter
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
private const val RECENT_GAMES_LIMIT = 20
private const val RECENT_GAMES_LIMIT_GRID = 32
private const val PLATFORM_GAMES_LIMIT = 20
private const val LIBRARY_GRID_COLUMNS = 6

private const val SECTION_KIND_RECENT = "RECENT"
private const val SECTION_KIND_FAVORITES = "FAVORITES"
private const val SECTION_KIND_PLATFORM = "PLATFORM"
private const val SECTION_KIND_RECOMMENDATIONS = "RECOMMENDATIONS"
private const val SECTION_KIND_ANDROID = "ANDROID"
private const val SECTION_KIND_STEAM = "STEAM"
private const val SECTION_KIND_PINNED = "PINNED"
private const val SECTION_KIND_MEDIA = "MEDIA"
private const val RESTORE_MAX_DEFERRALS = 8
private const val MEDIA_RESUME_START_OVER_INDEX = 0
private const val MEDIA_RESUME_OPTION_COUNT = 2
private const val MEDIA_NOTICE_DURATION_MS = 4000L

/**
 * A row of the companion home.
 *
 * [title] carries the name of the rows whose text is library data - a platform, a pinned
 * collection, a media library - and stays empty for the fixed rows, whose wording is translated.
 * Those set [titleRes] and [shortTitleRes] instead, and every read site goes through
 * [resolveTitle] or [resolveShortTitle] rather than the fields.
 */
sealed class DualHomeSection(
    val kind: HomeSectionKind,
    val title: String,
    val shortTitle: String = title,
    @StringRes val titleRes: Int? = null,
    @StringRes val shortTitleRes: Int? = null
) {
    data object Recent : DualHomeSection(
        HomeSectionKind.CONTINUE,
        "",
        titleRes = R.string.dual_home_section_recent_title,
        shortTitleRes = R.string.dual_home_section_recent_short
    )
    data object Recommendations : DualHomeSection(
        HomeSectionKind.RECOMMENDATIONS,
        "",
        titleRes = R.string.dual_home_section_recommendations_title,
        shortTitleRes = R.string.dual_home_section_recommendations_short
    )
    data object Favorites : DualHomeSection(
        HomeSectionKind.FAVORITES,
        "",
        titleRes = R.string.dual_home_section_favorites_title
    )
    data object Android : DualHomeSection(HomeSectionKind.ANDROID, "Android")
    data object Steam : DualHomeSection(HomeSectionKind.STEAM, "Steam")
    data class Platform(
        val id: Long,
        val slug: String,
        val name: String,
        val displayName: String,
        val shortName: String?,
        val logoPath: String?
    ) : DualHomeSection(HomeSectionKind.PLATFORM, displayName, shortName ?: displayName)

    data class Pinned(
        val pinned: PinnedCollection
    ) : DualHomeSection(
        if (pinned is PinnedCollection.Virtual) HomeSectionKind.PINNED_VIRTUAL else HomeSectionKind.PINNED_REGULAR,
        pinned.displayName
    )

    data class MediaLibrary(
        val libraryId: String,
        val name: String
    ) : DualHomeSection(HomeSectionKind.MEDIA_LIBRARY, name)
}

/**
 * The row's full name, translated where the row is a fixed one.
 */
fun DualHomeSection.resolveTitle(context: Context): String =
    titleRes?.let(context::getString) ?: title

/**
 * The row's name for the breadcrumb strip. A row with no short form of its own falls back to its
 * full name, translated or not, the way the class's own default did.
 */
fun DualHomeSection.resolveShortTitle(context: Context): String =
    shortTitleRes?.let(context::getString)
        ?: titleRes?.let(context::getString)
        ?: shortTitle

enum class DualHomeFocusZone { CAROUSEL, APP_BAR }

enum class DualHomeViewMode { CAROUSEL, COLLECTIONS, COLLECTION_GAMES, LIBRARY_GRID, MEDIA_GRID, MEDIA_INFO }

data class DualCollectionPickerEntry(val id: Long, val name: String, val isMember: Boolean)

enum class DualLibraryMenuAction(@StringRes val labelRes: Int) {
    PLAY(R.string.dual_library_menu_play),
    INSTALL(R.string.dual_library_menu_install),
    DOWNLOAD(R.string.dual_library_menu_download),
    FAVORITE(R.string.dual_library_menu_favorite),
    UNFAVORITE(R.string.dual_library_menu_unfavorite),
    DETAILS(R.string.dual_library_menu_details),
    ADD_TO_COLLECTION(R.string.dual_library_menu_add_to_collection),
    ADD_TO_GRID(R.string.dual_library_menu_add_to_grid),
    REFRESH(R.string.dual_library_menu_refresh),
    RESYNC_PLATFORM(R.string.dual_library_menu_resync_platform),
    DELETE(R.string.dual_library_menu_delete),
    UNINSTALL(R.string.dual_library_menu_uninstall),
    HIDE(R.string.dual_library_menu_hide),
    SHOW(R.string.dual_library_menu_show)
}

enum class ForwardingMode { NONE, OVERLAY, BACKGROUND }

enum class DualMediaMenuAction(@StringRes val labelRes: Int) {
    OPEN_INFO(R.string.dual_media_menu_open_info),
    START_OVER(R.string.dual_media_menu_start_over),
    FAVORITE(R.string.dual_media_menu_favorite),
    UNFAVORITE(R.string.dual_media_menu_unfavorite),
    DOWNLOAD(R.string.dual_media_menu_download),
    REMOVE_DOWNLOADS(R.string.dual_media_menu_remove_downloads),
    REFRESH(R.string.dual_media_menu_refresh)
}

/**
 * The options menu raised over one media tile. The target is captured when the menu opens rather
 * than read back from the cursor while it is up, so a grid refreshing underneath it cannot move
 * which title a confirm acts on.
 */
data class DualMediaMenuState(
    val item: com.nendo.argosy.ui.screens.media.MediaItemUi,
    val actions: List<DualMediaMenuAction> = emptyList(),
    val focusIndex: Int = 0
)

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
        val totalPlaytimeMinutes: Int,
        val installedCount: Int = 0,
        val achievementsEarned: Int = 0,
        val achievementsTotal: Int = 0
    ) : DualCollectionListItem()
}

enum class DualFilterCategory(@StringRes val labelRes: Int) {
    SORT(R.string.dual_filter_category_sort),
    SEARCH(R.string.dual_filter_category_search),
    SOURCE(R.string.dual_filter_category_source),
    GENRE(R.string.dual_filter_category_genre),
    PLAYERS(R.string.dual_filter_category_players),
    FRANCHISE(R.string.dual_filter_category_franchise)
}

/**
 * A row in the dual-screen filter list. [label] is what the row reads; [value] is what
 * selecting it stores. They coincide for genres, where the text is library data, and differ
 * for the source rows, which store an enum name.
 */
data class DualFilterOption(
    val label: String,
    val isSelected: Boolean,
    val value: String = label
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
    data class Game(val game: HomeGameUi, val gameIndex: Int) : DualLibraryGridItem
}

data class DualHomeUiState(
    val sections: List<DualHomeSection> = emptyList(),
    val currentSectionIndex: Int = 0,
    val games: List<HomeGameUi> = emptyList(),
    val mediaItems: List<com.nendo.argosy.ui.screens.home.HomeMediaUi> = emptyList(),
    val mediaGridItems: List<com.nendo.argosy.ui.screens.media.MediaItemUi> = emptyList(),
    val mediaGridFocusedIndex: Int = 0,
    val mediaGridColumns: Int = 1,
    val mediaResumePrompt: com.nendo.argosy.ui.screens.media.MediaResumePrompt? = null,
    val mediaResumeFocusIndex: Int = 0,
    val mediaLibraries: List<DualHomeSection.MediaLibrary> = emptyList(),
    val mediaLibraryIndex: Int = 0,
    val selectedIndex: Int = 0,
    val isLoading: Boolean = true,
    val focusZone: DualHomeFocusZone = DualHomeFocusZone.CAROUSEL,
    val appBarIndex: Int = 0,
    val platformTotalCount: Int = 0,
    val viewMode: DualHomeViewMode = DualHomeViewMode.CAROUSEL,
    val collectionItems: List<DualCollectionListItem> = emptyList(),
    val selectedCollectionIndex: Int = 0,
    val collectionGames: List<HomeGameUi> = emptyList(),
    val collectionGamesFocusedIndex: Int = 0,
    val activeCollectionName: String = "",
    val libraryGames: List<HomeGameUi> = emptyList(),
    val libraryGridItems: List<DualLibraryGridItem> = emptyList(),
    val libraryFocusedIndex: Int = 0,
    val sectionLabels: List<String> = emptyList(),
    val carouselConfig: com.nendo.argosy.domain.model.CarouselConfig =
        com.nendo.argosy.domain.model.CarouselConfig(),
    val autoGridConfig: com.nendo.argosy.domain.model.AutoGridConfig =
        com.nendo.argosy.domain.model.AutoGridConfig(),
    val layoutKind: com.nendo.argosy.domain.model.HomeLayoutKind =
        com.nendo.argosy.domain.model.HomeLayoutKind.CAROUSEL,
    val customGridConfig: com.nendo.argosy.domain.model.CustomGridConfig =
        com.nendo.argosy.domain.model.CustomGridConfig(),
    val backgroundBlur: Int = 0,
    val collectionOpenedFromTile: Boolean = false,
    val showLibraryMenu: Boolean = false,
    val libraryMenuFocusIndex: Int = 0,
    val collectionPickerGameId: Long? = null,
    val collectionPickerEntries: List<DualCollectionPickerEntry> = emptyList(),
    val collectionPickerFocusIndex: Int = 0,
    val customGrid: com.nendo.argosy.ui.components.CustomGridState =
        com.nendo.argosy.ui.components.CustomGridState(),
    val tileGames: Map<Long, HomeGameUi> = emptyMap(),
    val tileCollections: Map<Long, com.nendo.argosy.ui.components.TileCollectionUi> = emptyMap(),
    val tileApps: Map<String, String> = emptyMap(),
    val continueGameId: Long? = null,
    val raTileSummary: com.nendo.argosy.domain.model.RaAccountSummary? = null,
    val currentSectionLabel: String = "",
    val libraryColumns: Int = LIBRARY_GRID_COLUMNS,
    val showFilterOverlay: Boolean = false,
    val filterCategory: DualFilterCategory = DualFilterCategory.SOURCE,
    val filterOptions: List<DualFilterOption> = emptyList(),
    val filterFocusedIndex: Int = 0,
    val activeFilters: DualActiveFilters = DualActiveFilters(),
    val showSectionOverlay: Boolean = false,
    val overlaySectionLabel: String = "",
    val libraryPlatformLabel: String = "",
    val repairedCoverPaths: Map<Long, String> = emptyMap(),
    val mediaDownloadProgress: Map<String, com.nendo.argosy.data.repository.MediaTransferProgress> =
        emptyMap(),
    val mediaMenu: DualMediaMenuState? = null,
    val mediaDownloadPrompt: com.nendo.argosy.ui.screens.media.MediaDownloadPrompt? = null,
    val mediaPromptItem: com.nendo.argosy.ui.screens.media.MediaItemUi? = null,
    val mediaNotice: String? = null,
    val mediaInfoItemId: String? = null,
    val mediaInfoSiblingIds: List<String> = emptyList(),
    val mediaInfoReturnMode: DualHomeViewMode = DualHomeViewMode.CAROUSEL
) {
    val currentSection: DualHomeSection?
        get() = sections.getOrNull(currentSectionIndex)

    /**
     * How many tiles the current row holds, whichever kind of thing it holds.
     *
     * Navigation, counts and clamping all ask this rather than the game list, because a media row
     * fills [mediaItems] and leaves [games] empty; anything still measuring the row by its games
     * reads a media row as empty and refuses to move.
     */
    val rowItemCount: Int
        get() = if (mediaItems.isNotEmpty()) mediaItems.size else games.size

    val totalCount: Int
        get() = if (platformTotalCount > 0) platformTotalCount else rowItemCount

    val hasMoreGames: Boolean
        get() = platformTotalCount > games.size

    val currentPlatformId: Long?
        get() = (currentSection as? DualHomeSection.Platform)?.id

    val isViewAllFocused: Boolean
        get() = hasMoreGames && selectedIndex == games.size

    fun platformName(context: Context): String =
        currentSection?.resolveTitle(context).orEmpty()

    val selectedGame: HomeGameUi?
        get() = games.getOrNull(selectedIndex)

    /**
     * Whether a text field on this display is meant to hold Compose focus. Nothing else on the
     * companion may take it: a focused card swallows the d-pad, and the launcher decides selection
     * itself, so focus is granted only for typing and only while a field is on screen.
     */
    val isTextEntryActive: Boolean
        get() = customGrid.pickerSearchActive ||
            (
                viewMode == DualHomeViewMode.LIBRARY_GRID &&
                    showFilterOverlay &&
                    filterCategory == DualFilterCategory.SEARCH
                )

    /**
     * The same indicator a game tile gets, for a title being fetched. A series answers for whatever
     * episode of it is on the way, matching the single-screen home's rule.
     */
    fun mediaDownloadIndicatorFor(
        media: com.nendo.argosy.ui.screens.home.HomeMediaUi
    ): GameDownloadIndicator {
        val transfer = mediaDownloadProgress[media.itemId]
            ?: media.seriesId?.let { mediaDownloadProgress[it] }
            ?: return GameDownloadIndicator.NONE
        return GameDownloadIndicator(
            isDownloading = !transfer.isPaused,
            isPaused = transfer.isPaused,
            progress = transfer.fraction
        )
    }

    val homeTiles: List<com.nendo.argosy.domain.model.HomeTile>
        get() = customGrid.tiles

    val customGridPage: Int get() = customGrid.page

    val customGridPageCount: Int get() = customGrid.pageCount

    val customGridCell: com.nendo.argosy.domain.model.GridCell get() = customGrid.cell

    val tileEditMode: com.nendo.argosy.ui.components.TileEditMode get() = customGrid.editMode

    val editingTileId: Long? get() = customGrid.editingTileId

    val editingTile: com.nendo.argosy.domain.model.HomeTile? get() = customGrid.editingTile

    val overlappedTileIds: Set<Long> get() = customGrid.overlappedTileIds

    val showTileMenu: Boolean get() = customGrid.showMenu

    val tileMenuFocusIndex: Int get() = customGrid.menuFocusIndex

    val showTilePicker: Boolean get() = customGrid.showPicker

    val tilePickerQuery: String get() = customGrid.pickerQuery

    val tilePickerFocusIndex: Int get() = customGrid.pickerFocusIndex

    val tilePickerEntries: List<com.nendo.argosy.ui.components.TilePickerEntry>
        get() = customGrid.pickerEntries

    fun tilesOnPage(pageIndex: Int): List<com.nendo.argosy.domain.model.HomeTile> =
        customGrid.tilesOnPage(pageIndex)

    val focusedTileGameId: Long?
        get() = customGrid.focusedGameId

    /**
     * What a tile draws on the companion. A media target reads as unavailable rather than being
     * drawn: this screen has no media repository to resolve one against, and such tiles are filtered
     * out before they arrive, so the branch is there to make a new kind of target a compile error
     * here instead of a square that silently renders nothing.
     */
    fun tileContentFor(
        tile: com.nendo.argosy.domain.model.HomeTile,
        context: Context
    ): com.nendo.argosy.ui.components.CustomGridTileContent? =
        when (val target = tile.target) {
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Game -> {
                val game = tileGames[target.gameId]
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = game,
                    label = game?.title
                        ?: context.getString(R.string.dual_tile_missing_game),
                    isMissing = game == null,
                    subtitle = game?.platformDisplayName,
                    stats = game?.let { com.nendo.argosy.ui.components.tileStatsFor(it, context) }.orEmpty()
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection -> {
                val collection = tileCollections[target.collectionId]
                val focus = target.focusGameId?.let { tileGames[it] }
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = focus,
                    label = focus?.title ?: collection?.name
                        ?: context.getString(R.string.dual_tile_missing_collection),
                    isMissing = collection == null,
                    coverPath = if (focus == null) collection?.coverPath else null,
                    subtitle = collection?.name?.takeIf { focus != null }
                        ?: context.getString(R.string.dual_tile_collection_subtitle),
                    isCollectionQueue = focus != null,
                    stats = collection?.let {
                        listOf(
                            com.nendo.argosy.ui.components.TileStat(
                                context.getString(R.string.dual_tile_collection_games_stat),
                                it.gameCount.toString()
                            )
                        )
                    }.orEmpty()
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.VirtualCollection ->
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = target.name
                )
            is com.nendo.argosy.domain.model.HomeTileTargetRef.App -> {
                val name = tileApps[target.packageName]
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = name ?: context.getString(R.string.dual_tile_missing_app),
                    isMissing = name == null,
                    packageName = target.packageName,
                    subtitle = context.getString(R.string.dual_tile_app_subtitle)
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Feature ->
                featureTileContent(target, context)
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Media,
            is com.nendo.argosy.domain.model.HomeTileTargetRef.LocalMedia,
            com.nendo.argosy.domain.model.HomeTileTargetRef.Unresolvable ->
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = context.getString(R.string.dual_tile_unavailable),
                    isMissing = true
                )
        }

    private fun featureTileContent(
        target: com.nendo.argosy.domain.model.HomeTileTargetRef.Feature,
        context: Context
    ): com.nendo.argosy.ui.components.CustomGridTileContent = when (target.kind) {
        com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME -> {
            val game = target.pickedGameId?.let { tileGames[it] }
            com.nendo.argosy.ui.components.CustomGridTileContent(
                game = game,
                label = game?.title ?: context.getString(R.string.dual_tile_feature_random_empty),
                subtitle = context.getString(R.string.dual_tile_feature_random),
                stats = game?.let { com.nendo.argosy.ui.components.tileStatsFor(it, context) }.orEmpty(),
                isRandom = true
            )
        }
        com.nendo.argosy.domain.model.FeatureTileKind.CONTINUE -> {
            val game = continueGameId?.let { tileGames[it] }
            com.nendo.argosy.ui.components.CustomGridTileContent(
                game = game,
                label = game?.title ?: context.getString(R.string.dual_tile_feature_continue_empty),
                subtitle = context.getString(R.string.dual_tile_feature_continue),
                stats = game?.let { com.nendo.argosy.ui.components.tileStatsFor(it, context) }.orEmpty()
            )
        }
        com.nendo.argosy.domain.model.FeatureTileKind.RA_SUMMARY -> {
            val summary = raTileSummary
            com.nendo.argosy.ui.components.CustomGridTileContent(
                game = null,
                label = summary?.username ?: context.getString(R.string.dual_tile_feature_ra),
                subtitle = when {
                    summary == null -> context.getString(R.string.dual_tile_feature_ra_signed_out)
                    summary.latestTitle == null ->
                        context.getString(R.string.dual_tile_feature_ra_no_unlocks)
                    else -> context.getString(R.string.dual_tile_feature_ra_latest, summary.latestTitle)
                },
                stats = summary?.let {
                    listOf(
                        com.nendo.argosy.ui.components.TileStat(
                            context.getString(R.string.dual_tile_stat_points),
                            it.points.toString()
                        ),
                        com.nendo.argosy.ui.components.TileStat(
                            context.getString(R.string.dual_tile_stat_unlocks),
                            it.unlocks.toString()
                        )
                    )
                }.orEmpty()
            )
        }
    }
}

class DualHomeViewModel(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val advanceCollectionFocusUseCase:
        com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase? = null,
    private val prepareCollectionQueueUseCase:
        com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase? = null,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val context: Context,
    private val preferencesRepository: UserPreferencesRepository? = null,
    private val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager? = null,
    private val repairImageCacheUseCase: RepairImageCacheUseCase? = null,
    private val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
    private val gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate? = null,
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase? = null,
    private val getGamesForPinnedCollectionUseCase: GetGamesForPinnedCollectionUseCase? = null,
    private val sessionStateStore: SessionStateStore? = null,
    private val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository? = null,
    private val retroAchievementsRepository:
        com.nendo.argosy.data.repository.RetroAchievementsRepository? = null,
    private val homeGridPageRepository:
        com.nendo.argosy.data.repository.HomeGridPageRepository? = null,
    private val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue? = null,
    private val appsRepository: com.nendo.argosy.data.repository.AppsRepository? = null,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository? = null,
    private val pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource? = null,
    private val ambientAudioManager: com.nendo.argosy.ui.audio.AmbientAudioManager? = null,
    private val mediaRepository: com.nendo.argosy.data.repository.MediaRepository? = null,
    private val resolveMediaPlayTargetUseCase:
        com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase? = null,
    private val mediaAvailabilityVerifier:
        com.nendo.argosy.data.media.MediaAvailabilityVerifier? = null,
    private val mediaDownloadDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaDownloadDelegate? = null,
    private val mediaSiblingsDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaSiblingsDelegate? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualHomeUiState())
    val uiState: StateFlow<DualHomeUiState> = _uiState.asStateFlow()

    private val _forwardingMode = MutableStateFlow(ForwardingMode.NONE)
    val forwardingMode: StateFlow<ForwardingMode> = _forwardingMode.asStateFlow()

    private var allLibraryGames: List<HomeGameUi> = emptyList()
    private var libraryLoadedHidden = false

    private val tilePickerLimit = 60

    private val emulatorPackages: Set<String> by lazy {
        com.nendo.argosy.data.emulator.EmulatorRegistry.getAll()
            .map { it.packageName }
            .toSet()
    }

    private val customGrid = com.nendo.argosy.ui.home.grid.CustomGridCoordinator(
        context = context,
        scope = viewModelScope,
        repository = homeTileRepository,
        pageRepository = homeGridPageRepository,
        ownerUserId = { syncPreferencesRepository?.getRommUserId() },
        onPageAdded = { count -> persistCustomGridPageCount(count) },
        onPageRemoved = { count -> persistCustomGridPageRemoval(count) },
        pickerEntries = { category, query -> tilePickerEntriesFor(category, query) },
        onAdvanceFocusGame = { collectionId, current -> advanceCollectionFocus(collectionId, current) },
        onPrepareQueue = { collectionId, active ->
            prepareCollectionQueueUseCase?.invoke(collectionId, active)
            Unit
        },
        onFirstQueueGame = { collectionId -> firstGameInCollection(collectionId) },
        pageChooserEntries = { chooser -> pageChooserEntriesFor(chooser) },
        read = { _uiState.value.customGrid },
        write = { transform -> _uiState.update { it.copy(customGrid = transform(it.customGrid)) } }
    )

    /**
     * Reads the library rather than the cached list, because that cache is only filled once the
     * library grid has been opened and the picker has to work on a first run too.
     *
     * The media tab is never offered on this screen, so its branch answers with nothing rather than
     * being reached.
     */
    private suspend fun tilePickerEntriesFor(
        category: com.nendo.argosy.ui.components.TilePickerCategory,
        query: String
    ): List<com.nendo.argosy.ui.components.TilePickerEntry> = when (category) {
        com.nendo.argosy.ui.components.TilePickerCategory.GAMES ->
            gameRepository.getAllSortedByTitle()
                .map { it.toUi() }
                .filter { it.isPlayable }
                .filter { query.isBlank() || it.title.lowercase().contains(query) }
                .take(tilePickerLimit)
                .map { game ->
                    com.nendo.argosy.ui.components.TilePickerEntry(
                        target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(game.id),
                        title = game.title,
                        subtitle = game.platformDisplayName,
                        coverPath = game.coverPath
                    )
                }
        com.nendo.argosy.ui.components.TilePickerCategory.COLLECTIONS ->
            collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() }
                .filter { query.isBlank() || it.name.lowercase().contains(query) }
                .take(tilePickerLimit)
                .map { collection ->
                    val count = collectionRepository.getGameCountInCollection(collection.id)
                    com.nendo.argosy.ui.components.TilePickerEntry(
                        target = com.nendo.argosy.domain.model.HomeTileTargetRef
                            .Collection(collection.id),
                        title = collection.name,
                        subtitle = context.resources.getQuantityString(
                            R.plurals.dual_tile_picker_collection_game_count,
                            count,
                            count
                        ),
                        coverPath = collectionRepository
                            .getCollectionCoverPaths(collection.id)
                            .firstOrNull()
                    )
                }
        com.nendo.argosy.ui.components.TilePickerCategory.APPS ->
            appsRepository?.getInstalledApps(includeSystemApps = false).orEmpty()
                .filter { query.isBlank() || it.label.lowercase().contains(query) }
                .sortedWith(
                    compareByDescending<com.nendo.argosy.data.repository.InstalledApp> {
                        it.packageName in emulatorPackages
                    }.thenBy { it.label.lowercase() }
                )
                .take(tilePickerLimit)
                .map { app ->
                    com.nendo.argosy.ui.components.TilePickerEntry(
                        target = com.nendo.argosy.domain.model.HomeTileTargetRef
                            .App(app.packageName),
                        title = app.label,
                        subtitle = context.getString(
                            if (app.packageName in emulatorPackages) {
                                R.string.dual_tile_picker_app_emulator
                            } else {
                                R.string.dual_tile_picker_app_other
                            }
                        ),
                        packageName = app.packageName
                    )
                }
        com.nendo.argosy.ui.components.TilePickerCategory.MEDIA -> emptyList()
        com.nendo.argosy.ui.components.TilePickerCategory.FEATURES -> listOf(
            com.nendo.argosy.ui.components.TilePickerEntry(
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Feature(
                    com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME
                ),
                title = context.getString(R.string.tile_picker_feature_random_title),
                subtitle = context.getString(R.string.tile_picker_feature_random_subtitle)
            ),
            com.nendo.argosy.ui.components.TilePickerEntry(
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Feature(
                    com.nendo.argosy.domain.model.FeatureTileKind.CONTINUE
                ),
                title = context.getString(R.string.tile_picker_feature_continue_title),
                subtitle = context.getString(R.string.tile_picker_feature_continue_subtitle)
            ),
            com.nendo.argosy.ui.components.TilePickerEntry(
                target = com.nendo.argosy.domain.model.HomeTileTargetRef.Feature(
                    com.nendo.argosy.domain.model.FeatureTileKind.RA_SUMMARY
                ),
                title = context.getString(R.string.tile_picker_feature_ra_title),
                subtitle = context.getString(R.string.tile_picker_feature_ra_subtitle)
            )
        )
    }

    private var latestDownloads: Map<Long, com.nendo.argosy.data.local.entity.DownloadQueueEntity> = emptyMap()
    private val pendingCoverRepairs = mutableSetOf<Long>()
    private var letterOverlayJob: kotlinx.coroutines.Job? = null
    private var mediaNoticeJob: kotlinx.coroutines.Job? = null
    private var mediaInfoSiblingsJob: kotlinx.coroutines.Job? = null
    private var mediaInfoSiblingLibraryId: String? = null

    private data class PendingRestore(
        val sectionKind: String,
        val platformId: Long,
        val pinId: Long,
        val gameId: Long,
        val mediaLibraryId: String,
        val mediaItemId: String,
        val filters: DualActiveFilters?,
        val legacySectionIndex: Int,
        val legacySelectedIndex: Int,
        val hasIdentity: Boolean
    )

    private var pendingRestore: PendingRestore? = null
    private var restoreDeferrals = 0

    /** Invoked once the lower carousel has settled on its restored section + game. */
    var onRestoreComplete: (() -> Unit)? = null

    private val _restorePending = MutableStateFlow(false)

    /**
     * Whether a restore has been asked for and not yet landed.
     *
     * A caller that is about to reveal this surface waits on this so the screen is already sitting
     * on the right section and game when it appears. It stays raised while a restore defers on a
     * section list that has not loaded, so anyone waiting must bound the wait rather than assume it
     * always falls.
     */
    val restorePending: StateFlow<Boolean> = _restorePending.asStateFlow()

    fun startDrawerForwarding() { _forwardingMode.value = ForwardingMode.OVERLAY }
    fun startBackgroundForwarding() { _forwardingMode.value = ForwardingMode.BACKGROUND }
    fun stopDrawerForwarding() { _forwardingMode.value = ForwardingMode.NONE }

    init {
        loadData()
        observeDownloads()
        observePlatformChanges()
        observeGradientChanges()
        observeMediaGradientChanges()
        observeMediaFocusForCompanion()
        observeMediaInfoExit()
        observeMediaAvailability()
        observeMediaDownloadProgress()
        observeLayoutConfig()
    }

    @Volatile
    private var sortPartition: com.nendo.argosy.data.model.SortPartition =
        com.nendo.argosy.data.model.SortPartition.NONE

    private fun observeLayoutConfig() {
        val prefs = preferencesRepository ?: return
        viewModelScope.launch {
            prefs.userPreferences.collect { preferences ->
                val partition = com.nendo.argosy.data.model.SortPartition(
                    installedFirst = preferences.sortInstalledFirst,
                    favoritesFirst = preferences.sortFavoritesFirst
                )
                if (partition != sortPartition) {
                    sortPartition = partition
                    if (allLibraryGames.isNotEmpty()) {
                        val filters = _uiState.value.activeFilters
                        val filtered = applyFiltersToList(allLibraryGames, filters)
                        updateLibraryState(applySort(filtered, filters.sort), preserveFocus = true)
                    }
                }
                _uiState.update {
                    it.copy(
                        carouselConfig = preferences.homeLayout.carousel,
                        autoGridConfig = preferences.homeLayout.autoGrid,
                        customGridConfig = preferences.homeLayout.customGrid,
                        layoutKind = preferences.homeLayout.selected,
                        backgroundBlur = preferences.backgroundBlur
                    )
                }
                customGrid.applyConfig(
                    autoFit = preferences.homeLayout.customGrid.autoFit,
                    storedPages = preferences.homeLayout.customGrid.pageCount
                )
            }
        }
    }

    private fun observeGradientChanges() {
        val delegate = gradientExtractionDelegate ?: return
        viewModelScope.launch {
            delegate.gradients.collect { gradients ->
                _uiState.update { state ->
                    state.copy(
                        games = state.games.map { it.applyGradient(gradients) }
                    )
                }
                allLibraryGames = allLibraryGames.map { it.applyGradient(gradients) }
            }
        }
    }

    private fun HomeGameUi.applyGradient(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): HomeGameUi =
        gradients[id]?.let { copy(gradientColors = it) } ?: this

    private fun loadData() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update {
                it.copy(
                    sections = sections,
                    isLoading = false
                )
            }
            loadGamesForCurrentSectionSuspend()
            applyPendingRestore()
        }
    }

    private fun observePlatformChanges() {
        viewModelScope.launch {
            platformRepository.observePlatformsWithGames().collect { platforms ->
                val newPlatformSections = platformSections(platforms)

                val state = _uiState.value
                val leading = state.sections.filterNot {
                    it is DualHomeSection.Platform || it is DualHomeSection.Pinned
                }
                val pinned = state.sections.filterIsInstance<DualHomeSection.Pinned>()
                val updatedSections = leading + newPlatformSections + pinned

                _uiState.update {
                    it.copy(
                        sections = updatedSections,
                        currentSectionIndex = remapSectionIndex(state.currentSection, updatedSections)
                    )
                }
                applyPendingRestore()
            }
        }
    }

    private var steamIndicators: Map<Long, com.nendo.argosy.ui.screens.home.GameDownloadIndicator> = emptyMap()

    private fun observeDownloads() {
        viewModelScope.launch {
            var previouslyDownloading = emptySet<Long>()

            downloadQueueRepository.observeActiveDownloads().collect { entities ->
                val downloadsByGameId = entities.associateBy { it.gameId }
                val currentlyDownloading = downloadsByGameId.keys

                latestDownloads = downloadsByGameId
                val completedDownloads = previouslyDownloading - currentlyDownloading
                previouslyDownloading = currentlyDownloading

                if (completedDownloads.isNotEmpty()) {
                    loadGamesForCurrentSectionSuspend()
                    if (_uiState.value.viewMode == DualHomeViewMode.LIBRARY_GRID) {
                        val platformId = _uiState.value.activeFilters.platformId
                        if (platformId != null) {
                            loadLibraryGamesForPlatform(platformId, hidden = libraryLoadedHidden, preserveFocus = true)
                        } else {
                            loadLibraryGames(hidden = libraryLoadedHidden, preserveFocus = true)
                        }
                    }
                    if (_uiState.value.viewMode == DualHomeViewMode.COLLECTION_GAMES) {
                        val collectionId = selectedCollectionItem()?.id
                        if (collectionId != null) {
                            enterCollectionGames(collectionId, preserveFocus = true)
                        }
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            games = state.games.map { game ->
                                game.copy(downloadIndicator = indicatorForGame(game.id, downloadsByGameId))
                            },
                            libraryGames = state.libraryGames.map { game ->
                                game.copy(downloadIndicator = indicatorForGame(game.id, downloadsByGameId))
                            },
                            collectionGames = state.collectionGames.map { game ->
                                game.copy(downloadIndicator = indicatorForGame(game.id, downloadsByGameId))
                            }
                        )
                    }
                }
            }
        }

        val scm = steamContentManager ?: return
        viewModelScope.launch {
            var lastSteamGameId: Long? = null
            scm.downloadState.collect { steamState ->
                if (steamState is SteamDownloadState.Idle) {
                    val prev = lastSteamGameId
                    lastSteamGameId = null
                    if (prev != null) {
                        steamIndicators = steamIndicators - prev
                        loadGamesForCurrentSectionSuspend()
                    }
                    return@collect
                }
                val appId = steamState.appId ?: return@collect
                val game = gameRepository.getBySteamAppId(appId) ?: return@collect
                lastSteamGameId = game.id
                val activeDl = scm.activeDownload.value
                val progress = activeDl?.progress ?: when (steamState) {
                    is SteamDownloadState.Paused -> steamState.progress
                    else -> 0f
                }
                val indicator = steamState.toIndicator(progress)
                if (indicator != null) {
                    steamIndicators = steamIndicators + (game.id to indicator)
                    updateSteamIndicators()
                } else {
                    steamIndicators = steamIndicators - game.id
                    if (steamState is SteamDownloadState.Completed) {
                        loadGamesForCurrentSectionSuspend()
                    }
                }
            }
        }
    }

    private fun List<HomeGameUi>.withCurrentDownloadState(): List<HomeGameUi> {
        if (latestDownloads.isEmpty() && steamIndicators.isEmpty()) return this
        return map { game ->
            val indicator = steamIndicators[game.id]
                ?: indicatorForGame(game.id, latestDownloads)
            game.copy(downloadIndicator = indicator)
        }
    }

    private fun indicatorForGame(
        gameId: Long,
        downloads: Map<Long, com.nendo.argosy.data.local.entity.DownloadQueueEntity>
    ): GameDownloadIndicator {
        val entity = downloads[gameId] ?: return GameDownloadIndicator.NONE
        val progress = if (entity.totalBytes > 0) {
            (entity.bytesDownloaded.toFloat() / entity.totalBytes).coerceIn(0f, 1f)
        } else 0f
        return GameDownloadIndicator(isDownloading = true, progress = progress)
    }

    private fun updateSteamIndicators() {
        _uiState.update { state ->
            state.copy(
                games = state.games.map { game ->
                    val indicator = steamIndicators[game.id] ?: game.downloadIndicator
                    game.copy(downloadIndicator = indicator)
                },
                libraryGames = state.libraryGames.map { game ->
                    val indicator = steamIndicators[game.id] ?: game.downloadIndicator
                    game.copy(downloadIndicator = indicator)
                },
                collectionGames = state.collectionGames.map { game ->
                    val indicator = steamIndicators[game.id] ?: game.downloadIndicator
                    game.copy(downloadIndicator = indicator)
                }
            )
        }
    }

    /**
     * The same listing the single-screen home offers, in the order [HomeSectionKind] declares. A row
     * appears only when it has content, matching home's own rule, and the Steam and Android
     * platforms are held back from the platform run because they are rows in their own right.
     */
    private suspend fun buildSections(): List<DualHomeSection> {
        val sections = mutableListOf<DualHomeSection>()

        val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
        val hasRecent = gameRepository.getRecentlyPlayed(limit = 1).isNotEmpty() ||
            gameRepository.getNewlyAdded(newThreshold, isInstalledOnlyEnabled(), 1).isNotEmpty()
        val hasRecommendations = gameRepository.getByIds(recommendedGameIds()).isNotEmpty()
        val hasFavorites = gameRepository.getFavorites().isNotEmpty()
        val hasAndroid = gameRepository.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = 1).isNotEmpty()
        val hasSteam = gameRepository.getByPlatformSorted(LocalPlatformIds.STEAM, limit = 1).isNotEmpty()

        HomeSectionKind.LEADING.forEach { kind ->
            val section = when (kind) {
                HomeSectionKind.CONTINUE -> DualHomeSection.Recent.takeIf { hasRecent }
                HomeSectionKind.RECOMMENDATIONS -> DualHomeSection.Recommendations.takeIf { hasRecommendations }
                HomeSectionKind.FAVORITES -> DualHomeSection.Favorites.takeIf { hasFavorites }
                HomeSectionKind.ANDROID -> DualHomeSection.Android.takeIf { hasAndroid }
                HomeSectionKind.STEAM -> DualHomeSection.Steam.takeIf { hasSteam }
                else -> null
            }
            section?.let { sections.add(it) }
        }

        sections.addAll(platformSections(platformRepository.getPlatformsWithGames()))
        sections.addAll(pinnedSections())
        sections.addAll(mediaLibrarySections())

        return sections
    }

    /**
     * One row per media library the server offers, matching the single-screen home.
     *
     * A library with nothing in it is left out, because a row that opens onto an empty shelf reads
     * as a fault rather than as an empty library.
     */
    private suspend fun mediaLibrarySections(): List<DualHomeSection.MediaLibrary> {
        val repository = mediaRepository ?: return emptyList()
        return repository.observeLibraries().first()
            .map { DualHomeSection.MediaLibrary(it.libraryId, it.name) }
            .filter { repository.observeLibraryItems(it.libraryId).first().isNotEmpty() }
    }

    private fun platformSections(platforms: List<PlatformEntity>): List<DualHomeSection.Platform> =
        platforms
            .filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
            .map { platform ->
                DualHomeSection.Platform(
                    id = platform.id,
                    slug = platform.slug,
                    name = platform.name,
                    displayName = platform.getDisplayName(),
                    shortName = platform.shortName,
                    logoPath = platform.logoPath
                )
            }

    private suspend fun pinnedSections(): List<DualHomeSection> =
        (getPinnedCollectionsUseCase?.invoke()?.first() ?: emptyList())
            .sortedByDescending { it.displayOrder }
            .map { DualHomeSection.Pinned(it) }

    private suspend fun recommendedGameIds(): List<Long> =
        preferencesRepository?.userPreferences?.first()?.recommendedGameIds ?: emptyList()

    private fun loadGamesForCurrentSection() {
        viewModelScope.launch { loadGamesForCurrentSectionSuspend() }
    }

    private suspend fun filterPlayable(games: List<GameEntity>): List<GameEntity> {
        return games.filter { downloadFileStatusRepository.isContentAvailable(it) }
    }

    private suspend fun isInstalledOnlyEnabled(): Boolean {
        return preferencesRepository?.userPreferences?.first()?.installedOnlyHome == true
    }

    /**
     * Whether a section should carry its whole library rather than a leading slice. The companion
     * display answers the same setting the main one does, or the option would only work on
     * whichever screen happened to be looked at.
     */
    private suspend fun showsEveryGame(): Boolean {
        val layout = preferencesRepository?.userPreferences?.first()?.homeLayout ?: return false
        return layout.selected == com.nendo.argosy.domain.model.HomeLayoutKind.AUTO_GRID &&
            layout.autoGrid.showAllGames
    }

    private suspend fun loadGamesForCurrentSectionSuspend() {
        val section = _uiState.value.currentSection ?: return
        if (section is DualHomeSection.MediaLibrary) {
            loadMediaForSection(section)
            return
        }
        val installedOnly = isInstalledOnlyEnabled()
        val uncapped = showsEveryGame()
        val platformLimit = if (uncapped) Int.MAX_VALUE else PLATFORM_GAMES_LIMIT
        val recentLimit = if (uncapped) RECENT_GAMES_LIMIT_GRID else RECENT_GAMES_LIMIT

        var realCount = 0
        val games = when (section) {
            is DualHomeSection.Recent -> {
                val newThreshold = Instant.now().minus(
                    NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS
                )
                val recentlyPlayed = gameRepository.getRecentlyPlayed(
                    limit = recentLimit
                )
                val newlyAdded = gameRepository.getNewlyAdded(
                    newThreshold, installedOnly, recentLimit
                )
                val allCandidates = (recentlyPlayed + newlyAdded)
                    .distinctBy { it.id }

                val playable = if (installedOnly) filterPlayable(allCandidates) else allCandidates

                sortRecentGamesWithNewPriority(playable)
                    .take(recentLimit)
                    .map { it.toUi() }
            }
            is DualHomeSection.Favorites -> {
                var favorites = gameRepository.getFavorites()
                if (installedOnly) favorites = filterPlayable(favorites)
                favorites.map { it.toUi() }
            }
            is DualHomeSection.Platform -> {
                realCount = gameRepository.countByPlatform(section.id)
                var platformGames = gameRepository.getByPlatformSorted(
                    section.id, limit = platformLimit
                )
                if (installedOnly) platformGames = filterPlayable(platformGames)
                platformGames.map { it.toUi() }
            }
            is DualHomeSection.Recommendations -> {
                val ids = recommendedGameIds()
                val byId = gameRepository.getByIds(ids).associateBy { it.id }
                ids.mapNotNull { byId[it] }.map { it.toUi() }
            }
            is DualHomeSection.Android -> {
                var androidGames = gameRepository.getByPlatformSorted(
                    LocalPlatformIds.ANDROID, limit = platformLimit
                )
                if (installedOnly) androidGames = filterPlayable(androidGames)
                androidGames.map { it.toUi() }
            }
            is DualHomeSection.Steam -> {
                gameRepository.getByPlatformSorted(
                    LocalPlatformIds.STEAM, limit = platformLimit
                ).map { it.toUi() }
            }
            is DualHomeSection.Pinned -> {
                var pinnedGames = getGamesForPinnedCollectionUseCase?.invoke(section.pinned)?.first().orEmpty()
                if (installedOnly) pinnedGames = filterPlayable(pinnedGames)
                pinnedGames.map { it.toUi() }
            }
            is DualHomeSection.MediaLibrary -> emptyList()
        }

        _uiState.update {
            val previousId = it.selectedGame?.id
            val remapped = games.indexOfFirst { g -> g.id == previousId }
            val newIndex = if (remapped >= 0) remapped
            else it.selectedIndex.coerceIn(0, (games.size - 1).coerceAtLeast(0))
            it.copy(
                games = games,
                mediaItems = emptyList(),
                platformTotalCount = realCount,
                selectedIndex = newIndex
            )
        }
    }

    /**
     * Fills a media row with the library's titles.
     *
     * Media and games do not share a tile model, so a media row empties the game list rather than
     * translating titles into games: a film given a game's shape would answer questions about
     * emulators and downloads that mean nothing to it.
     */
    private suspend fun loadMediaForSection(section: DualHomeSection.MediaLibrary) {
        val repository = mediaRepository ?: return
        val entities = repository.observeLibraryItems(section.libraryId).first()
        val userData = repository.getUserDataFor(entities.map { it.itemId })
        val seriesIds = entities.mapNotNull { it.seriesId }.distinct()
        val series = seriesIds.mapNotNull { repository.getItem(it) }.associateBy { it.itemId }
        val gradients = gradientExtractionDelegate?.mediaGradients?.value.orEmpty()
        val verified = mediaAvailabilityVerifier?.availability?.value.orEmpty()
        val items = entities.map {
            it.toHomeMediaUi(
                context,
                repository,
                userData[it.itemId],
                series[it.seriesId],
                verified = verified[it.itemId],
                gradientColors = gradients[it.itemId]
            )
        }
        _uiState.update {
            it.copy(
                games = emptyList(),
                mediaItems = items,
                platformTotalCount = 0,
                selectedIndex = it.selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            )
        }
    }

    /**
     * Starts the focused title, resolving a show to the episode a press should actually play
     * through the shared resolver every surface uses.
     *
     * A show the resolver can only answer with its detail screen is left alone here: this display
     * has no media detail surface, and the showcase is already describing the title.
     */
    fun playFocusedMedia() {
        val itemId = focusedMediaItemId() ?: return
        val resolve = resolveMediaPlayTargetUseCase ?: return
        viewModelScope.launch {
            when (val target = resolve(itemId)) {
                is com.nendo.argosy.domain.model.MediaPlayTarget.Play ->
                    com.nendo.argosy.DualScreenManagerHolder.instance?.playMediaItem(target.itemId)
                is com.nendo.argosy.domain.model.MediaPlayTarget.OpenDetail -> Unit
            }
        }
    }

    /**
     * The media title the cursor is on, or null when the cursor is not on one. Answers for the
     * grid and the carousel row alike, so a caller acting on "the focused title" does not have to
     * know which of the two the viewer is looking at.
     */
    fun focusedMediaItemId(): String? = focusedMediaId(_uiState.value)

    /**
     * Opens the media browser on this screen, which is the one being driven.
     *
     * Media is a destination like Library, so it belongs on the interactive display rather than in
     * the primary's navigation graph, which Android pins to the default display and would put the
     * browser on whichever screen the viewer is not using.
     */
    fun enterMediaGrid(libraryId: String? = null, onLoaded: (() -> Unit)? = null) {
        mediaAvailabilityVerifier?.verifyOnOpen()
        viewModelScope.launch {
            val repository = mediaRepository ?: return@launch
            val libraries = repository.observeLibraries().first()
                .map { DualHomeSection.MediaLibrary(it.libraryId, it.name) }
            if (libraries.isEmpty()) return@launch
            val index = libraries.indexOfFirst { it.libraryId == libraryId }.coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    viewMode = DualHomeViewMode.MEDIA_GRID,
                    mediaLibraries = libraries,
                    mediaLibraryIndex = index,
                    mediaGridFocusedIndex = 0
                )
            }
            loadMediaGrid()
            onLoaded?.invoke()
        }
    }

    fun cycleMediaLibrary(direction: Int, onLoaded: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.mediaLibraries.size < 2) return
        val next = (state.mediaLibraryIndex + direction).mod(state.mediaLibraries.size)
        _uiState.update { it.copy(mediaLibraryIndex = next, mediaGridFocusedIndex = 0) }
        viewModelScope.launch {
            loadMediaGrid()
            onLoaded?.invoke()
        }
    }

    private suspend fun loadMediaGrid() {
        val repository = mediaRepository ?: return
        val state = _uiState.value
        val library = state.mediaLibraries.getOrNull(state.mediaLibraryIndex) ?: return
        val entities = repository.observeLibraryItems(library.libraryId).first()
        gradientExtractionDelegate?.loadPersistedMediaGradients(
            viewModelScope,
            entities.map { it.itemId }
        )
        val userData = repository.getUserDataFor(entities.map { it.itemId })
        val gradients = gradientExtractionDelegate?.mediaGradients?.value.orEmpty()
        val verified = mediaAvailabilityVerifier?.availability?.value.orEmpty()
        _uiState.update {
            it.copy(
                mediaGridItems = entities.map { entity ->
                    entity.toMediaItemUi(repository, userData[entity.itemId], verified, gradients)
                },
                mediaGridFocusedIndex = it.mediaGridFocusedIndex
                    .coerceIn(0, (entities.size - 1).coerceAtLeast(0))
            )
        }
    }

    /**
     * Asks the server for the library listing again and rebuilds the grid from what it answers,
     * the deliberate, user-asked-for refresh the single-screen media library offers on the same
     * button.
     */
    fun refreshMediaGrid() {
        val repository = mediaRepository ?: return
        mediaAvailabilityVerifier?.verifyOnOpen()
        viewModelScope.launch {
            repository.refreshLibraries()
            loadMediaGrid()
        }
    }

    fun moveMediaGridFocus(direction: GridDirection, columns: Int): Boolean {
        val state = _uiState.value
        val count = state.mediaGridItems.size
        if (count == 0) return false
        val current = state.mediaGridFocusedIndex
        val target = when (direction) {
            GridDirection.LEFT -> if (current % columns == 0) current else current - 1
            GridDirection.RIGHT -> if ((current + 1) % columns == 0) current else current + 1
            GridDirection.UP -> current - columns
            GridDirection.DOWN -> current + columns
        }
        if (target < 0 || target >= count) return false
        _uiState.update { it.copy(mediaGridFocusedIndex = target) }
        return true
    }

    fun setMediaGridColumns(columns: Int) {
        if (columns <= 0) return
        _uiState.update {
            if (it.mediaGridColumns == columns) it else it.copy(mediaGridColumns = columns)
        }
    }

    fun setMediaGridFocus(index: Int) {
        _uiState.update {
            it.copy(
                mediaGridFocusedIndex = index
                    .coerceIn(0, (it.mediaGridItems.size - 1).coerceAtLeast(0))
            )
        }
    }

    /**
     * Raises the Start Over prompt for one grid tile. A series is not itself playable and an item
     * with nothing to resume has no choice to offer, so neither raises the prompt and the caller
     * plays instead.
     */
    fun openMediaResumePrompt(index: Int): Boolean {
        val item = _uiState.value.mediaGridItems.getOrNull(index) ?: return false
        if (!item.isPlayable || !item.hasResumePosition) return false
        _uiState.update {
            it.copy(
                mediaGridFocusedIndex = index,
                mediaResumePrompt = com.nendo.argosy.ui.screens.media.MediaResumePrompt(
                    itemId = item.itemId,
                    title = item.title,
                    subtitle = item.episodeLabel(context) ?: item.year?.toString(),
                    resumeTicks = item.resumeTicks
                ),
                mediaResumeFocusIndex = MEDIA_RESUME_START_OVER_INDEX
            )
        }
        return true
    }

    fun openMediaResumePromptForFocused(): Boolean =
        openMediaResumePrompt(_uiState.value.mediaGridFocusedIndex)

    fun moveMediaResumeFocus(delta: Int) {
        _uiState.update {
            it.copy(
                mediaResumeFocusIndex = (it.mediaResumeFocusIndex + delta)
                    .mod(MEDIA_RESUME_OPTION_COUNT)
            )
        }
    }

    fun confirmMediaResumePrompt() {
        val state = _uiState.value
        val prompt = state.mediaResumePrompt ?: return
        startMediaFromPrompt(
            prompt.itemId,
            startOver = state.mediaResumeFocusIndex == MEDIA_RESUME_START_OVER_INDEX
        )
    }

    fun startMediaFromPrompt(itemId: String, startOver: Boolean) {
        dismissMediaResumePrompt()
        com.nendo.argosy.DualScreenManagerHolder.instance?.playMediaItem(itemId, startOver)
    }

    fun dismissMediaResumePrompt() {
        _uiState.update { it.copy(mediaResumePrompt = null) }
    }

    /**
     * Rebuilds whatever media surface is on screen when a verification pass lands, so a badge over
     * a file on ejected storage corrects itself without the row being re-entered.
     */
    private fun observeMediaAvailability() {
        val verifier = mediaAvailabilityVerifier ?: return
        viewModelScope.launch {
            verifier.availability.collect {
                reloadMediaSurfaces()
            }
        }
    }

    private fun observeMediaDownloadProgress() {
        val repository = mediaRepository ?: return
        viewModelScope.launch {
            repository.observeDownloadProgress().distinctUntilChanged().collect { progress ->
                _uiState.update { it.copy(mediaDownloadProgress = progress) }
            }
        }
    }

    private suspend fun reloadMediaSurfaces() {
        val state = _uiState.value
        if (state.viewMode == DualHomeViewMode.MEDIA_GRID) loadMediaGrid()
        (state.currentSection as? DualHomeSection.MediaLibrary)?.let { loadMediaForSection(it) }
    }

    private suspend fun mediaItemFor(itemId: String): com.nendo.argosy.ui.screens.media.MediaItemUi? {
        val repository = mediaRepository ?: return null
        _uiState.value.mediaGridItems.firstOrNull { it.itemId == itemId }?.let { return it }
        val entity = repository.getItem(itemId) ?: return null
        return entity.toMediaItemUi(
            repository,
            repository.getUserData(itemId),
            mediaAvailabilityVerifier?.availability?.value.orEmpty()
        )
    }

    /**
     * Flips the favourite flag on the focused media title, for the grid and the carousel rows
     * alike. Answers false when the cursor is not on a media title, which is the caller's cue to
     * fall through to the game handling.
     */
    fun toggleFocusedMediaFavorite(): Boolean {
        val itemId = focusedMediaItemId() ?: return false
        val repository = mediaRepository ?: return false
        viewModelScope.launch {
            repository.toggleFavorite(itemId)
            reloadMediaSurfaces()
        }
        return true
    }

    /**
     * Raises the options menu over the focused media title. Built asynchronously because whether
     * removal is worth offering is a count of files on this device, not a flag on the tile.
     */
    fun openMediaMenuForFocused(): Boolean {
        val itemId = focusedMediaItemId() ?: return false
        openMediaMenu(itemId)
        return true
    }

    fun openMediaMenu(itemId: String) {
        viewModelScope.launch {
            val item = mediaItemFor(itemId) ?: return@launch
            val downloaded = mediaDownloadDelegate?.summaryFor(item, 0)?.downloaded ?: 0
            val actions = buildList {
                add(DualMediaMenuAction.OPEN_INFO)
                if (item.isPlayable && item.hasResumePosition) {
                    add(DualMediaMenuAction.START_OVER)
                }
                add(
                    if (item.isFavorite) DualMediaMenuAction.UNFAVORITE
                    else DualMediaMenuAction.FAVORITE
                )
                if (mediaDownloadDelegate != null) {
                    add(DualMediaMenuAction.DOWNLOAD)
                    if (downloaded > 0) add(DualMediaMenuAction.REMOVE_DOWNLOADS)
                }
                if (_uiState.value.viewMode == DualHomeViewMode.MEDIA_GRID) {
                    add(DualMediaMenuAction.REFRESH)
                }
            }
            _uiState.update {
                it.copy(mediaMenu = DualMediaMenuState(item = item, actions = actions))
            }
        }
    }

    fun closeMediaMenu() = _uiState.update { it.copy(mediaMenu = null) }

    /**
     * Says something a menu action could not do, on this screen, and takes the words back down on
     * its own. The alternative was the menu closing over a silent failure, which reads as the
     * action having worked.
     */
    private fun showMediaNotice(message: String) {
        mediaNoticeJob?.cancel()
        _uiState.update { it.copy(mediaNotice = message) }
        mediaNoticeJob = viewModelScope.launch {
            kotlinx.coroutines.delay(MEDIA_NOTICE_DURATION_MS)
            _uiState.update { it.copy(mediaNotice = null) }
        }
    }

    fun moveMediaMenuFocus(delta: Int) = _uiState.update { state ->
        val menu = state.mediaMenu ?: return@update state
        val maxIndex = (menu.actions.size - 1).coerceAtLeast(0)
        state.copy(mediaMenu = menu.copy(focusIndex = (menu.focusIndex + delta).coerceIn(0, maxIndex)))
    }

    fun confirmMediaMenu() {
        val menu = _uiState.value.mediaMenu ?: return
        val action = menu.actions.getOrNull(menu.focusIndex) ?: return
        _uiState.update { it.copy(mediaMenu = null) }
        when (action) {
            DualMediaMenuAction.OPEN_INFO -> enterMediaInfo(menu.item.itemId)
            DualMediaMenuAction.START_OVER ->
                com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.playMediaItem(menu.item.itemId, startOver = true)
            DualMediaMenuAction.FAVORITE, DualMediaMenuAction.UNFAVORITE ->
                viewModelScope.launch {
                    mediaRepository?.toggleFavorite(menu.item.itemId)
                    reloadMediaSurfaces()
                }
            DualMediaMenuAction.DOWNLOAD -> viewModelScope.launch {
                when (val outcome = mediaDownloadDelegate?.openPrompt(menu.item)) {
                    is com.nendo.argosy.ui.screens.media.delegates.MediaDownloadPromptOutcome.Ready ->
                        _uiState.update {
                            it.copy(mediaDownloadPrompt = outcome.prompt, mediaPromptItem = menu.item)
                        }
                    is com.nendo.argosy.ui.screens.media.delegates.MediaDownloadPromptOutcome.Refused ->
                        showMediaNotice(outcome.reason)
                    null -> Unit
                }
            }
            DualMediaMenuAction.REMOVE_DOWNLOADS -> viewModelScope.launch {
                val prompt = mediaDownloadDelegate?.openRemovalPrompt(menu.item) ?: return@launch
                _uiState.update {
                    it.copy(mediaDownloadPrompt = prompt, mediaPromptItem = menu.item)
                }
            }
            DualMediaMenuAction.REFRESH -> refreshMediaGrid()
        }
    }

    /**
     * Opens the media information panel for one title on this screen, remembering where the viewer
     * came from so Back returns there. The requested id is published through DSM because the panel
     * view model is owned per surface and only DSM state reaches whichever one is being driven; the
     * view-mode broadcast lives here so the touch and gamepad entries cannot diverge.
     */
    fun enterMediaInfo(itemId: String) {
        val holder = com.nendo.argosy.DualScreenManagerHolder.instance ?: return
        _uiState.update { state ->
            state.copy(
                viewMode = DualHomeViewMode.MEDIA_INFO,
                mediaInfoItemId = itemId,
                mediaInfoReturnMode = if (state.viewMode == DualHomeViewMode.MEDIA_INFO) {
                    state.mediaInfoReturnMode
                } else {
                    state.viewMode
                }
            )
        }
        holder.requestMediaInfo(itemId)
        holder.onViewModeChanged(DualHomeViewMode.MEDIA_INFO.name, false, false)
        observeMediaInfoSiblings(itemId)
    }

    /**
     * Tracks the library run the information panel's shoulder buttons walk. Keyed by the library
     * rather than the title so a step to a sibling keeps the run it is walking, the same carry the
     * single-screen detail screen does.
     */
    private fun observeMediaInfoSiblings(itemId: String) {
        val delegate = mediaSiblingsDelegate ?: return
        viewModelScope.launch {
            val libraryId = delegate.libraryIdOf(itemId)
            if (libraryId == null) {
                clearMediaInfoSiblings()
                return@launch
            }
            if (mediaInfoSiblingLibraryId == libraryId && mediaInfoSiblingsJob?.isActive == true) {
                return@launch
            }
            mediaInfoSiblingsJob?.cancel()
            mediaInfoSiblingLibraryId = libraryId
            mediaInfoSiblingsJob = viewModelScope.launch {
                delegate.siblingIdsFlow(libraryId).collect { ids ->
                    _uiState.update { it.copy(mediaInfoSiblingIds = ids) }
                }
            }
        }
    }

    private fun clearMediaInfoSiblings() {
        mediaInfoSiblingsJob?.cancel()
        mediaInfoSiblingsJob = null
        mediaInfoSiblingLibraryId = null
        _uiState.update { it.copy(mediaInfoSiblingIds = emptyList()) }
    }

    /**
     * Steps the information panel to the title beside the open one, in the order its library is
     * shown in. Answers false at either end of the run and for a title with none, which is the
     * caller's cue to sound a boundary rather than wrap to the other end.
     */
    fun stepMediaInfoSibling(direction: Int): Boolean {
        val state = _uiState.value
        if (state.viewMode != DualHomeViewMode.MEDIA_INFO) return false
        val current = state.mediaInfoItemId ?: return false
        val index = state.mediaInfoSiblingIds.indexOf(current)
        if (index < 0) return false
        val target = state.mediaInfoSiblingIds.getOrNull(index + direction) ?: return false
        enterMediaInfo(target)
        return true
    }

    /**
     * Leaves the information panel for wherever it was opened from, and hands the panel back to
     * following the playback. Broadcast here for the same reason [enterMediaInfo] broadcasts.
     */
    fun exitMediaInfo() {
        if (_uiState.value.viewMode != DualHomeViewMode.MEDIA_INFO) return
        _uiState.update {
            it.copy(viewMode = it.mediaInfoReturnMode, mediaInfoItemId = null)
        }
        val holder = com.nendo.argosy.DualScreenManagerHolder.instance
        holder?.clearMediaInfoRequest()
        holder?.onViewModeChanged(_uiState.value.viewMode.name, false, false)
    }

    /**
     * Acts on one row of the information panel. A playable resolution starts the title; a series
     * the resolver cannot reduce to an episode opens its own information instead, which is the
     * surface that can, unlike the play path, do something useful with it.
     */
    fun confirmMediaInfoRow(itemId: String) {
        val resolve = resolveMediaPlayTargetUseCase ?: return
        viewModelScope.launch {
            when (val target = resolve(itemId)) {
                is com.nendo.argosy.domain.model.MediaPlayTarget.Play ->
                    com.nendo.argosy.DualScreenManagerHolder.instance?.playMediaItem(target.itemId)
                is com.nendo.argosy.domain.model.MediaPlayTarget.OpenDetail ->
                    enterMediaInfo(target.itemId)
            }
        }
    }

    fun moveMediaDownloadFocus(delta: Int) {
        val delegate = mediaDownloadDelegate ?: return
        val prompt = _uiState.value.mediaDownloadPrompt ?: return
        _uiState.update { it.copy(mediaDownloadPrompt = delegate.moveFocus(prompt, delta)) }
    }

    fun focusMediaDownloadOption(index: Int) {
        val delegate = mediaDownloadDelegate ?: return
        val prompt = _uiState.value.mediaDownloadPrompt ?: return
        _uiState.update { it.copy(mediaDownloadPrompt = delegate.focus(prompt, index)) }
    }

    fun confirmMediaDownloadOption() {
        val delegate = mediaDownloadDelegate ?: return
        val state = _uiState.value
        val prompt = state.mediaDownloadPrompt ?: return
        val item = state.mediaPromptItem ?: return
        if (prompt.step == com.nendo.argosy.ui.screens.media.MediaDownloadStep.EPISODES) {
            when {
                prompt.episodes.isCancelFocused -> dismissMediaDownloadPrompt()
                prompt.episodes.isConfirmFocused -> commitMediaEpisodeSelection()
                else -> _uiState.update {
                    it.copy(mediaDownloadPrompt = delegate.toggleEpisode(prompt))
                }
            }
            return
        }
        viewModelScope.launch {
            val next = delegate.advance(prompt, item)
            _uiState.update {
                it.copy(
                    mediaDownloadPrompt = next,
                    mediaPromptItem = if (next == null) null else it.mediaPromptItem
                )
            }
            if (next == null) reloadMediaSurfaces()
        }
    }

    fun commitMediaEpisodeSelection() {
        val delegate = mediaDownloadDelegate ?: return
        val prompt = _uiState.value.mediaDownloadPrompt ?: return
        if (prompt.step != com.nendo.argosy.ui.screens.media.MediaDownloadStep.EPISODES) return
        if (!prompt.episodes.hasSelection) return
        viewModelScope.launch {
            val next = delegate.confirmEpisodeSelection(prompt)
            _uiState.update {
                it.copy(
                    mediaDownloadPrompt = next,
                    mediaPromptItem = if (next == null) null else it.mediaPromptItem
                )
            }
        }
    }

    fun moveMediaDownloadSideways(towardsEnd: Boolean) {
        val delegate = mediaDownloadDelegate ?: return
        val prompt = _uiState.value.mediaDownloadPrompt ?: return
        if (prompt.step != com.nendo.argosy.ui.screens.media.MediaDownloadStep.EPISODES) return
        _uiState.update { it.copy(mediaDownloadPrompt = delegate.moveSideways(prompt, towardsEnd)) }
    }

    fun dismissMediaDownloadPrompt() {
        _uiState.update { it.copy(mediaDownloadPrompt = null, mediaPromptItem = null) }
    }

    /**
     * Samples a grid poster the first time it finishes decoding, the way the single-screen library
     * does, so the colours a poster carries follow the title onto whichever surface draws it next.
     */
    fun onMediaPosterLoaded(itemId: String, bitmap: android.graphics.Bitmap) {
        gradientExtractionDelegate?.extractForMedia(viewModelScope, itemId, bitmap)
    }

    private fun observeMediaGradientChanges() {
        val delegate = gradientExtractionDelegate ?: return
        viewModelScope.launch {
            delegate.mediaGradients.collect { gradients ->
                if (gradients.isEmpty()) return@collect
                _uiState.update { state ->
                    state.copy(
                        mediaGridItems = state.mediaGridItems.map { item ->
                            gradients[item.itemId]
                                ?.takeIf { it != item.gradientColors }
                                ?.let { item.copy(gradientColors = it) }
                                ?: item
                        }
                    )
                }
            }
        }
    }

    /**
     * Tells the showcase screen what this one has focused, for the media grid and the carousel's
     * media rows alike. Driven off state rather than called from each navigation method, following
     * the single-screen media library's convention, so every path that moves the cursor reaches it.
     */
    private fun observeMediaFocusForCompanion() {
        viewModelScope.launch {
            _uiState
                .map { focusedMediaId(it) }
                .distinctUntilChanged()
                .collect { itemId -> publishMediaCompanionDetail(itemId) }
        }
    }

    /**
     * Retires the info request whenever this surface leaves MEDIA_INFO through any door, not just
     * the Back exit: several flows change the view mode directly, and a request left standing would
     * pin the shared panel to a title the viewer has already walked away from.
     */
    private fun observeMediaInfoExit() {
        viewModelScope.launch {
            _uiState
                .map { it.viewMode }
                .distinctUntilChanged()
                .collect { mode ->
                    if (mode != DualHomeViewMode.MEDIA_INFO &&
                        _uiState.value.mediaInfoItemId != null
                    ) {
                        _uiState.update { it.copy(mediaInfoItemId = null) }
                        clearMediaInfoSiblings()
                        com.nendo.argosy.DualScreenManagerHolder.instance?.clearMediaInfoRequest()
                    }
                }
        }
    }

    /**
     * The media title under the cursor, or null when the cursor is not on one.
     *
     * The carousel's media row only counts while the carousel is the mode being shown. The section
     * index survives a move into the library grid, the collections list or the collection games, so
     * a row-based answer keeps naming a title the viewer has already left; the showcase then holds
     * that description over every game the new mode focuses, because a standing media detail outranks
     * the showcase state those modes publish.
     */
    private fun focusedMediaId(state: DualHomeUiState): String? = when (state.viewMode) {
        DualHomeViewMode.MEDIA_INFO -> state.mediaInfoItemId
        DualHomeViewMode.MEDIA_GRID ->
            state.mediaGridItems.getOrNull(state.mediaGridFocusedIndex)?.itemId
        DualHomeViewMode.CAROUSEL ->
            if (state.currentSection is DualHomeSection.MediaLibrary) {
                state.mediaItems.getOrNull(state.selectedIndex)?.itemId
            } else {
                null
            }
        else -> null
    }

    private suspend fun publishMediaCompanionDetail(itemId: String?) {
        val holder = com.nendo.argosy.DualScreenManagerHolder.instance ?: return
        if (itemId == null) {
            holder.setCompanionDetail(null)
            return
        }
        val repository = mediaRepository ?: return
        val item = _uiState.value.mediaGridItems.firstOrNull { it.itemId == itemId }
            ?: repository.getItem(itemId)
                ?.toMediaItemUi(repository, repository.getUserData(itemId))
            ?: return
        holder.setCompanionDetail(item.toCompanionDetail(context))
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

    /**
     * Adopts a saved position, if there is one worth adopting.
     *
     * Called whenever this surface becomes the one being driven, not only when it is built. The two
     * home surfaces each keep their own cursor in memory, so a role swap that does not re-read the
     * shared context resumes wherever that instance was left rather than where the user just was,
     * and the rail and selection appear to jump on their own.
     */
    fun restoreNavContextIfPresent(ctx: SessionStateStore.CarouselNavContext) {
        val hasSomethingToRestore = ctx.hasContext ||
            ctx.legacySectionIndex > 0 ||
            ctx.legacySelectedIndex > 0
        if (hasSomethingToRestore) restoreNavContext(ctx)
    }

    fun restoreNavContext(ctx: SessionStateStore.CarouselNavContext) {
        pendingRestore = PendingRestore(
            sectionKind = ctx.sectionKind,
            platformId = ctx.platformId,
            pinId = ctx.pinId,
            gameId = ctx.gameId,
            mediaLibraryId = ctx.mediaLibraryId,
            mediaItemId = ctx.mediaItemId,
            filters = if (ctx.hasContext) ctx.toActiveFilters() else null,
            legacySectionIndex = ctx.legacySectionIndex,
            legacySelectedIndex = ctx.legacySelectedIndex,
            hasIdentity = ctx.hasContext && ctx.sectionKind.isNotEmpty()
        )
        restoreDeferrals = 0
        _restorePending.value = true
        viewModelScope.launch { applyPendingRestore() }
    }

    fun restorePosition(sectionIndex: Int, selectedIndex: Int) {
        pendingRestore = PendingRestore(
            sectionKind = "",
            platformId = -1L,
            pinId = -1L,
            gameId = -1L,
            mediaLibraryId = "",
            mediaItemId = "",
            filters = null,
            legacySectionIndex = sectionIndex,
            legacySelectedIndex = selectedIndex,
            hasIdentity = false
        )
        restoreDeferrals = 0
        viewModelScope.launch { applyPendingRestore() }
    }

    private fun SessionStateStore.CarouselNavContext.toActiveFilters(): DualActiveFilters =
        DualActiveFilters(
            source = filterSource,
            genres = genres,
            players = players,
            franchises = franchises,
            searchQuery = filterSearch,
            platformId = filterPlatformId.takeIf { it > 0 },
            sort = ActiveSort(
                option = SortOption.entries.firstOrNull { it.name == sortOption } ?: ActiveSort().option,
                descending = sortDescending
            )
        )

    private fun resolveRestoreSection(
        pending: PendingRestore,
        sections: List<DualHomeSection>
    ): Int = when (pending.sectionKind) {
        SECTION_KIND_RECENT -> sections.indexOfFirst { it is DualHomeSection.Recent }
        SECTION_KIND_RECOMMENDATIONS -> sections.indexOfFirst { it is DualHomeSection.Recommendations }
        SECTION_KIND_FAVORITES -> sections.indexOfFirst { it is DualHomeSection.Favorites }
        SECTION_KIND_ANDROID -> sections.indexOfFirst { it is DualHomeSection.Android }
        SECTION_KIND_STEAM -> sections.indexOfFirst { it is DualHomeSection.Steam }
        SECTION_KIND_PLATFORM -> sections.indexOfFirst {
            it is DualHomeSection.Platform && it.id == pending.platformId
        }
        SECTION_KIND_PINNED -> sections.indexOfFirst {
            it is DualHomeSection.Pinned && it.pinned.id == pending.pinId
        }
        SECTION_KIND_MEDIA -> if (pending.mediaLibraryId.isEmpty()) -1 else {
            sections.indexOfFirst {
                it is DualHomeSection.MediaLibrary && it.libraryId == pending.mediaLibraryId
            }
        }
        else -> -1
    }

    /**
     * Whether the cursor is sitting on a media row, which decides whether Confirm plays a title or
     * opens a game.
     */
    fun isOnMediaSection(): Boolean =
        _uiState.value.currentSection is DualHomeSection.MediaLibrary

    private suspend fun applyPendingRestore() {
        val pending = pendingRestore ?: run {
            _restorePending.value = false
            return
        }
        val sections = _uiState.value.sections
        if (sections.isEmpty()) return

        val resolved = resolveRestoreSection(pending, sections)
        if (resolved < 0 && pending.hasIdentity && restoreDeferrals < RESTORE_MAX_DEFERRALS) {
            restoreDeferrals++
            return
        }
        pendingRestore = null
        restoreDeferrals = 0

        pending.filters?.let { restored ->
            _uiState.update { it.copy(activeFilters = restored) }
        }

        when {
            resolved >= 0 -> applyRestoreToSection(resolved, pending)
            restoreByGameIdentity(pending, sections) -> Unit
            restoreByMediaIdentity(pending, sections) -> Unit
            else -> applyRestoreToSection(
                pending.legacySectionIndex.coerceIn(0, sections.size - 1), pending
            )
        }

        _restorePending.value = false
        onRestoreComplete?.invoke()
    }

    private suspend fun applyRestoreToSection(sectionIndex: Int, pending: PendingRestore) {
        _uiState.update {
            it.copy(currentSectionIndex = sectionIndex, selectedIndex = 0)
        }
        loadGamesForCurrentSectionSuspend()

        val media = _uiState.value.mediaItems
        if (media.isNotEmpty()) {
            val byItem = if (pending.mediaItemId.isNotEmpty()) {
                media.indexOfFirst { it.itemId == pending.mediaItemId }
            } else {
                -1
            }
            val target = if (byItem >= 0) byItem else pending.legacySelectedIndex
            _uiState.update { it.copy(selectedIndex = target.coerceIn(0, media.size - 1)) }
            return
        }

        val games = _uiState.value.games
        if (games.isEmpty()) return
        val maxIndex = if (_uiState.value.hasMoreGames) games.size else games.size - 1
        val byId = if (pending.gameId > 0) games.indexOfFirst { it.id == pending.gameId } else -1
        val target = if (byId >= 0) byId else pending.legacySelectedIndex
        _uiState.update { it.copy(selectedIndex = target.coerceIn(0, maxIndex)) }
    }

    private suspend fun restoreByGameIdentity(
        pending: PendingRestore,
        sections: List<DualHomeSection>
    ): Boolean {
        if (pending.gameId <= 0) return false
        val game = gameRepository.getById(pending.gameId) ?: return false
        val platformIndex = sections.indexOfFirst {
            it is DualHomeSection.Platform && it.id == game.platformId
        }
        if (platformIndex < 0) return false
        applyRestoreToSection(platformIndex, pending)
        return true
    }

    private suspend fun restoreByMediaIdentity(
        pending: PendingRestore,
        sections: List<DualHomeSection>
    ): Boolean {
        if (pending.mediaItemId.isEmpty()) return false
        val libraryId = mediaRepository?.getItem(pending.mediaItemId)?.libraryId ?: return false
        val libraryIndex = sections.indexOfFirst {
            it is DualHomeSection.MediaLibrary && it.libraryId == libraryId
        }
        if (libraryIndex < 0) return false
        applyRestoreToSection(libraryIndex, pending)
        return true
    }

    fun currentNavContext(): SessionStateStore.CarouselNavContext {
        val state = _uiState.value
        val section = state.currentSection
        val kind = when (section) {
            is DualHomeSection.Recent -> SECTION_KIND_RECENT
            is DualHomeSection.Recommendations -> SECTION_KIND_RECOMMENDATIONS
            is DualHomeSection.Favorites -> SECTION_KIND_FAVORITES
            is DualHomeSection.Android -> SECTION_KIND_ANDROID
            is DualHomeSection.Steam -> SECTION_KIND_STEAM
            is DualHomeSection.Platform -> SECTION_KIND_PLATFORM
            is DualHomeSection.Pinned -> SECTION_KIND_PINNED
            is DualHomeSection.MediaLibrary -> SECTION_KIND_MEDIA
            null -> ""
        }
        val filters = state.activeFilters
        val mediaSection = section as? DualHomeSection.MediaLibrary
        return SessionStateStore.CarouselNavContext(
            hasContext = section != null,
            sectionKind = kind,
            platformId = (section as? DualHomeSection.Platform)?.id ?: -1L,
            pinId = (section as? DualHomeSection.Pinned)?.pinned?.id ?: -1L,
            gameId = state.selectedGame?.id ?: -1L,
            mediaLibraryId = mediaSection?.libraryId ?: "",
            mediaItemId = if (mediaSection != null) {
                state.mediaItems.getOrNull(state.selectedIndex)?.itemId ?: ""
            } else {
                ""
            },
            legacySectionIndex = state.currentSectionIndex,
            legacySelectedIndex = state.selectedIndex,
            filterSource = filters.source,
            filterPlatformId = filters.platformId ?: -1L,
            filterSearch = filters.searchQuery,
            sortOption = filters.sort.option.name,
            sortDescending = filters.sort.descending,
            genres = filters.genres,
            players = filters.players,
            franchises = filters.franchises
        )
    }

    fun refresh() {
        viewModelScope.launch {
            val sections = buildSections()
            _uiState.update { state ->
                state.copy(
                    sections = sections,
                    currentSectionIndex = remapSectionIndex(state.currentSection, sections)
                )
            }
            loadGamesForCurrentSection()
        }
    }

    private fun remapSectionIndex(current: DualHomeSection?, sections: List<DualHomeSection>): Int =
        current?.let { c ->
            sections.indexOfFirst { s ->
                when {
                    c is DualHomeSection.Platform && s is DualHomeSection.Platform -> c.id == s.id
                    c is DualHomeSection.MediaLibrary && s is DualHomeSection.MediaLibrary ->
                        c.libraryId == s.libraryId
                    else -> c::class == s::class
                }
            }.takeIf { it >= 0 }
        } ?: 0

    fun nextSection(onLoaded: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = (state.currentSectionIndex + 1) % state.sections.size
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
        viewModelScope.launch {
            loadGamesForCurrentSectionSuspend()
            persistSection()
            onLoaded?.invoke()
        }
    }

    /**
     * Jump straight to a section, as tapping its name in the breadcrumb does. Mirrors what the
     * bumper navigation does on arrival so a tap and a bumper leave the same state behind.
     */
    fun setSectionIndex(index: Int, onLoaded: (() -> Unit)? = null) {
        val state = _uiState.value
        if (index !in state.sections.indices || index == state.currentSectionIndex) return

        _uiState.update { it.copy(currentSectionIndex = index, selectedIndex = 0) }
        viewModelScope.launch {
            loadGamesForCurrentSectionSuspend()
            persistSection()
            onLoaded?.invoke()
        }
    }

    fun previousSection(onLoaded: (() -> Unit)? = null) {
        val state = _uiState.value
        if (state.sections.isEmpty()) return

        val newIndex = if (state.currentSectionIndex <= 0) {
            state.sections.size - 1
        } else {
            state.currentSectionIndex - 1
        }
        _uiState.update { it.copy(currentSectionIndex = newIndex, selectedIndex = 0) }
        viewModelScope.launch {
            loadGamesForCurrentSectionSuspend()
            persistSection()
            onLoaded?.invoke()
        }
    }

    /**
     * Records where the carousel is so the next launch resumes here. Owned by the view model rather
     * than the input handlers because a section can change from a bumper, a tap, or either of the
     * two handlers, and persisting per call site is how the companion and swapped-roles paths
     * drifted apart in the first place.
     */
    private fun persistSection() {
        val store = sessionStateStore ?: return
        store.setCarouselNavContext(currentNavContext())
    }

    fun selectNext() {
        val state = _uiState.value
        if (state.rowItemCount == 0) return
        val maxIndex = if (state.hasMoreGames) state.rowItemCount else state.rowItemCount - 1
        val newIndex = (state.selectedIndex + 1).coerceAtMost(maxIndex)
        _uiState.update { it.copy(selectedIndex = newIndex) }
        persistSection()
    }

    fun selectPrevious() {
        val state = _uiState.value
        if (state.rowItemCount == 0) return
        val newIndex = (state.selectedIndex - 1).coerceAtLeast(0)
        _uiState.update { it.copy(selectedIndex = newIndex) }
        persistSection()
    }

    /**
     * Mirrors the phone's tile observation so both surfaces read one curated grid. Editing happens
     * on whichever screen the grid is shown on, which on a dual-screen handheld is the lower one.
     *
     * Media tiles are left off this screen, the same way media rows are: the companion has no media
     * repository to resolve one against, and the second screen already shows what is being watched
     * in its own panel. Nothing is written, so the tiles are still on the page the phone draws.
     */
    fun observeHomeTiles() {
        customGrid.observePageSettings { applyPageAudio() }
        val tiles = homeTileRepository ?: return
        viewModelScope.launch {
            tiles.observeTiles(syncPreferencesRepository?.getRommUserId())
                .map { stored ->
                    stored.filterNot {
                        it.target is com.nendo.argosy.domain.model.HomeTileTargetRef.Media ||
                            it.target is com.nendo.argosy.domain.model.HomeTileTargetRef.LocalMedia
                    }
                }
                .collect { rows ->
                    val features = rows.mapNotNull {
                        it.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Feature
                    }
                    val continueGameId = if (
                        features.any { it.kind == com.nendo.argosy.domain.model.FeatureTileKind.CONTINUE }
                    ) {
                        gameRepository.getRecentlyPlayed(1).firstOrNull()?.id
                    } else {
                        null
                    }
                    val raSummary = if (
                        features.any { it.kind == com.nendo.argosy.domain.model.FeatureTileKind.RA_SUMMARY }
                    ) {
                        retroAchievementsRepository?.getAccountSummary()
                    } else {
                        null
                    }
                    val gameIds = (
                        rows.mapNotNull {
                            when (val target = it.target) {
                                is com.nendo.argosy.domain.model.HomeTileTargetRef.Game -> target.gameId
                                is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection ->
                                    target.focusGameId
                                is com.nendo.argosy.domain.model.HomeTileTargetRef.Feature ->
                                    target.pickedGameId
                                else -> null
                            }
                        } + listOfNotNull(continueGameId)
                    ).distinct()
                    val games = if (gameIds.isEmpty()) {
                        emptyMap()
                    } else {
                        gameRepository.getByIds(gameIds).associate { entity ->
                            entity.id to entity.toUi()
                        }
                    }
                    val collectionIds = rows.mapNotNull {
                        (it.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Collection)
                            ?.collectionId
                    }.distinct()
                    val collections = if (collectionIds.isEmpty()) {
                        emptyMap()
                    } else {
                        collectionRepository.getAllCollections()
                            .filter { it.id in collectionIds }
                            .associate { collection ->
                                collection.id to com.nendo.argosy.ui.components.TileCollectionUi(
                                    name = collection.name,
                                    coverPath = collectionRepository
                                        .getCollectionCoverPaths(collection.id)
                                        .firstOrNull(),
                                    gameCount = collectionRepository
                                        .getGameCountInCollection(collection.id)
                                )
                            }
                    }
                    val packageNames = rows.mapNotNull {
                        (it.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.App)
                            ?.packageName
                    }.distinct()
                    val apps = if (packageNames.isEmpty()) {
                        emptyMap()
                    } else {
                        appsRepository?.getInstalledApps(includeSystemApps = true).orEmpty()
                            .filter { it.packageName in packageNames }
                            .associate { it.packageName to it.label }
                    }
                    customGrid.setTiles(rows)
                    _uiState.update {
                        it.copy(
                            tileGames = games,
                            tileCollections = collections,
                            tileApps = apps,
                            continueGameId = continueGameId,
                            raTileSummary = raSummary
                        )
                    }
                    ensureRandomPicks(rows, games, tiles)
                }
        }
    }

    private suspend fun ensureRandomPicks(
        rows: List<com.nendo.argosy.domain.model.HomeTile>,
        resolved: Map<Long, HomeGameUi>,
        tiles: com.nendo.argosy.data.repository.HomeTileRepository
    ) {
        rows.forEach { tile ->
            val target = tile.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Feature
                ?: return@forEach
            if (target.kind != com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME) return@forEach
            if (target.pickedGameId != null && resolved.containsKey(target.pickedGameId)) return@forEach
            val pick = gameRepository.pickRandomGame(target.filters) ?: return@forEach
            tiles.updateFeaturePick(tile.id, pick.id)
        }
    }

    fun rerollRandomTile() {
        val tile = customGrid.focusedTile() ?: return
        val target = tile.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Feature ?: return
        if (target.kind != com.nendo.argosy.domain.model.FeatureTileKind.RANDOM_GAME) return
        val tiles = homeTileRepository ?: return
        viewModelScope.launch {
            val pick = gameRepository.pickRandomGame(target.filters, excludeGameId = target.pickedGameId)
                ?: return@launch
            tiles.updateFeaturePick(tile.id, pick.id)
        }
    }

    fun setCustomGridShape(columns: Int, rows: Int) = customGrid.setShape(columns, rows)

    /**
     * Offers left by finished downloads while the launcher was elsewhere. Drained one at a time and
     * only while the curated grid is the layout in use, since that is the only place a tile means
     * anything.
     */
    fun observeTilePrompts() {
        val queue = homeTilePromptQueue ?: return
        viewModelScope.launch {
            queue.pending.collect { pending ->
                val gameId = pending.firstOrNull() ?: return@collect
                val state = _uiState.value
                if (state.layoutKind != com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
                    return@collect
                }
                val game = gameRepository.getByIds(listOf(gameId)).firstOrNull()?.toUi()
                if (game == null) {
                    queue.resolve(gameId)
                    return@collect
                }
                customGrid.showPendingAdd(
                    com.nendo.argosy.ui.components.TilePickerEntry(
                        target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(game.id),
                        title = game.title,
                        subtitle = game.platformDisplayName,
                        coverPath = game.coverPath
                    )
                )
            }
        }
    }

    fun confirmPendingTileAdd() =
        customGrid.confirmPendingAdd { id -> homeTilePromptQueue?.resolve(id) }

    fun dismissPendingTileAdd() =
        customGrid.dismissPendingAdd { id -> homeTilePromptQueue?.resolve(id) }

    fun movePendingTileAddFocus(delta: Int) = customGrid.movePendingAddFocus(delta)

    fun moveCustomGridFocus(
        direction: com.nendo.argosy.domain.model.GridDirection2D
    ): Boolean = customGrid.moveFocus(direction)

    fun turnCustomGridPage(delta: Int): Boolean {
        val turned = customGrid.turnPage(delta)
        if (turned) applyPageAudio()
        return turned
    }

    /**
     * Hands the output to the page in view, so a page carrying its own sound replaces the
     * launcher's music for as long as it is shown.
     */
    private fun applyPageAudio() {
        val audio = ambientAudioManager ?: return
        val pageOwns = _uiState.value.layoutKind ==
            com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID &&
            _uiState.value.customGrid.currentPageSettings.silencesGlobalAudio
        if (pageOwns) audio.fadeOut() else audio.fadeIn()
    }

    fun setCustomGridCell(cell: com.nendo.argosy.domain.model.GridCell) = customGrid.setCell(cell)

    fun moveEditingTileTo(cell: com.nendo.argosy.domain.model.GridCell) =
        customGrid.moveEditingTileTo(cell)

    fun resizeEditingTileTo(cell: com.nendo.argosy.domain.model.GridCell) =
        customGrid.resizeEditingTileTo(cell)

    fun focusedTile(): com.nendo.argosy.domain.model.HomeTile? = customGrid.focusedTile()

    fun focusedTileGameId(): Long? = customGrid.focusedGameId()

    fun tileMenuActions(): List<com.nendo.argosy.ui.components.CustomTileMenuAction> =
        _uiState.value.customGrid.menuActions

    fun openTileMenu() = customGrid.openMenu()

    fun closeTileMenu() = customGrid.closeMenu()

    fun moveTileMenuFocus(delta: Int) = customGrid.moveMenuFocus(delta)

    fun confirmTileMenu() = customGrid.confirmMenu()

    fun resizeFocusedTile(horizontal: Boolean, grow: Boolean): Boolean =
        customGrid.resizeFocusedTile(horizontal, grow)

    fun resizeFocusedTileBy(direction: com.nendo.argosy.domain.model.GridDirection2D): Boolean =
        customGrid.resizeFocusedTile(direction)

    val isOnAddPage: Boolean
        get() = _uiState.value.customGrid.isOnAddPage

    fun confirmAddPage() = customGrid.confirmAddPage()

    fun deleteCustomGridPage() = customGrid.deleteCurrentPage()

    /**
     * Remembers a page that holds nothing, when the layout is set to keep blank pages. Pages are
     * otherwise implied by the tiles on them, so an empty one has nowhere to live but the config.
     */
    private fun persistCustomGridPageCount(count: Int) {
        val prefs = preferencesRepository ?: return
        val config = _uiState.value.customGridConfig
        if (!config.persistBlankPages || count <= config.pageCount) return
        viewModelScope.launch {
            val settings = prefs.userPreferences.first().homeLayout
            prefs.setHomeLayout(
                settings.copy(customGrid = settings.customGrid.copy(pageCount = count))
            )
        }
    }

    /**
     * Forgets a remembered blank page. Without this the config keeps claiming the page the delete
     * just removed, and the next preferences emission puts it straight back.
     */
    private fun persistCustomGridPageRemoval(count: Int) {
        val prefs = preferencesRepository ?: return
        val config = _uiState.value.customGridConfig
        if (config.pageCount <= count) return
        viewModelScope.launch {
            val settings = prefs.userPreferences.first().homeLayout
            prefs.setHomeLayout(
                settings.copy(customGrid = settings.customGrid.copy(pageCount = count))
            )
        }
    }

    fun openTilePicker() = customGrid.openPicker()

    fun advanceFocusGame() = customGrid.advanceFocusGame()

    private suspend fun firstGameInCollection(collectionId: Long): Long? =
        collectionRepository.getGamesInCollection(collectionId).firstOrNull()?.id

    /**
     * The rows the shared page chooser shows here. The file browser is a Compose screen with its
     * own view model, which this display cannot host, so its source is left out of the backdrop
     * list until the companion can open one.
     */
    private suspend fun pageChooserEntriesFor(
        chooser: com.nendo.argosy.ui.components.PageChooserState
    ): List<com.nendo.argosy.ui.components.PageChooserEntry> =
        pageChooserEntrySource?.entriesFor(
            chooser = chooser,
            focusedCollection = _uiState.value.customGrid.focusedCollection,
            canBrowseFiles = false
        ) ?: emptyList()

    private suspend fun advanceCollectionFocus(collectionId: Long, currentGameId: Long): Long? =
        advanceCollectionFocusUseCase?.invoke(collectionId, currentGameId)?.nextGameId

    fun closeTilePicker() = customGrid.closePicker()

    fun moveTilePickerFocus(delta: Int) = customGrid.movePickerFocus(delta)

    fun setTilePickerQuery(query: String) = customGrid.setPickerQuery(query)

    fun toggleTilePickerSearch() = customGrid.togglePickerSearch()

    fun confirmTilePickerSelection() = customGrid.confirmPickerSelection()

    fun selectTilePickerEntry(entry: com.nendo.argosy.ui.components.TilePickerEntry) =
        customGrid.selectPickerEntry(entry)

    fun movePageChooserFocus(delta: Int) = customGrid.movePageChooserFocus(delta)

    fun confirmPageChooser() = customGrid.confirmPageChooser()

    fun closePageChooser() = customGrid.closePageChooser()

    fun setPageChooserQuery(query: String) = customGrid.setPageChooserQuery(query)

    fun cycleTilePickerCategory(delta: Int) = customGrid.cyclePickerCategory(delta)

    fun setTilePickerCategory(category: com.nendo.argosy.ui.components.TilePickerCategory) =
        customGrid.setPickerCategory(category)

    fun enterTileMoveMode() = customGrid.enterMoveMode()

    fun commitTileEdit() = customGrid.commitEdit()

    fun cancelTileEdit() = customGrid.cancelEdit()

    fun exitTileMoveMode() = customGrid.commitEdit()

    fun toggleTileEditMode() = customGrid.toggleEditMode()

    fun moveFocusedTile(direction: com.nendo.argosy.domain.model.GridDirection2D): Boolean =
        customGrid.moveFocusedTile(direction)

    fun removeFocusedTile() = customGrid.removeFocusedTile()


    fun moveCarouselGridFocus(direction: GridDirection): AutoGridMove {
        val state = _uiState.value
        if (state.rowItemCount == 0) return AutoGridMove.None
        val count = if (state.hasMoreGames) state.rowItemCount + 1 else state.rowItemCount
        val move = autoGridMove(
            itemCount = count,
            config = state.autoGridConfig,
            currentIndex = state.selectedIndex,
            direction = direction
        )
        val target = (move as? AutoGridMove.Focus)?.index ?: return move
        _uiState.update { it.copy(selectedIndex = target) }
        persistSection()
        return move
    }

    fun setSelectedIndex(index: Int) {
        _uiState.update { it.copy(selectedIndex = index.coerceIn(0, maxOf(0, it.rowItemCount - 1))) }
    }

    fun selectByTouch(index: Int) {
        setSelectedIndex(index)
        persistSection()
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
            gameRepository.updateFavoriteWithSync(game.id, !game.isFavorite)
            loadGamesForCurrentSection()
        }
    }

    /**
     * Favourites a game by id rather than by carousel position, for the curated grid where the
     * selection is a cell and the section's game list is not on screen at all.
     */
    fun toggleFavoriteById(gameId: Long) {
        val current = _uiState.value.tileGames[gameId] ?: return
        viewModelScope.launch {
            gameRepository.updateFavoriteWithSync(gameId, !current.isFavorite)
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

    fun enterCollectionGames(
        collectionId: Long,
        preserveFocus: Boolean = false,
        fromTile: Boolean = false,
        onLoaded: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val hiddenIds = gameRepository.getHiddenGameIds()
            val games = collectionRepository.getGamesInCollection(collectionId)
                .filter { it.id !in hiddenIds }
                .map { it.toUi() }
            val item = _uiState.value.collectionItems
                .filterIsInstance<DualCollectionListItem.Collection>()
                .find { it.id == collectionId }
            _uiState.update { it.copy(
                viewMode = DualHomeViewMode.COLLECTION_GAMES,
                collectionOpenedFromTile = fromTile,
                collectionGames = games,
                collectionGamesFocusedIndex = if (preserveFocus) {
                    remapFocusIndex(it.collectionGames, it.collectionGamesFocusedIndex, games)
                } else 0,
                activeCollectionName = item?.name
                    ?: collectionRepository.getAllCollections()
                        .firstOrNull { c -> c.id == collectionId }?.name
                    ?: ""
            )}
            onLoaded?.invoke()
        }
    }

    private fun <T> remapFocusIndex(
        old: List<T>,
        oldIndex: Int,
        new: List<T>,
        idOf: (T) -> Long
    ): Int {
        val previousId = old.getOrNull(oldIndex)?.let(idOf) ?: return 0
        val remapped = new.indexOfFirst { idOf(it) == previousId }
        return if (remapped >= 0) remapped
        else oldIndex.coerceIn(0, (new.size - 1).coerceAtLeast(0))
    }

    private fun remapFocusIndex(old: List<HomeGameUi>, oldIndex: Int, new: List<HomeGameUi>): Int =
        remapFocusIndex(old, oldIndex, new) { it.id }

    /**
     * Leaves a collection for wherever it was opened from. A collection reached by tile was never
     * on the collections list, so returning there lands on a list that was never loaded; back has to
     * mean the grid in that case.
     */
    fun exitCollectionGames() {
        _uiState.update { it.copy(
            viewMode = if (it.collectionOpenedFromTile) {
                DualHomeViewMode.CAROUSEL
            } else {
                DualHomeViewMode.COLLECTIONS
            },
            collectionOpenedFromTile = false,
            collectionGames = emptyList(),
            collectionGamesFocusedIndex = 0
        )}
    }

    private fun allPlatformsLabel(): String =
        context.getString(R.string.dual_library_platform_filter_all)

    fun enterLibraryGrid(onLoaded: (() -> Unit)? = null) {
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.LIBRARY_GRID,
            activeFilters = DualActiveFilters(),
            libraryPlatformLabel = allPlatformsLabel()
        )}
        loadLibraryGames(onLoaded = onLoaded)
    }

    fun enterLibraryGridForPlatform(platformId: Long, onLoaded: (() -> Unit)? = null) {
        val platformName = _uiState.value.sections
            .filterIsInstance<DualHomeSection.Platform>()
            .find { it.id == platformId }?.displayName ?: allPlatformsLabel()
        _uiState.update { it.copy(
            viewMode = DualHomeViewMode.LIBRARY_GRID,
            activeFilters = DualActiveFilters(platformId = platformId),
            libraryPlatformLabel = platformName
        )}
        loadLibraryGamesForPlatform(platformId, onLoaded = onLoaded)
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
            platformSections.find { it.id == nextPlatformId }?.displayName ?: allPlatformsLabel()
        } else allPlatformsLabel()

        _uiState.update { it.copy(
            activeFilters = it.activeFilters.copy(platformId = nextPlatformId),
            libraryFocusedIndex = 0,
            libraryPlatformLabel = nextLabel
        )}

        if (nextPlatformId != null) {
            loadLibraryGamesForPlatform(nextPlatformId, onLoaded = onLoaded)
        } else {
            loadLibraryGames(onLoaded = onLoaded)
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

    /**
     * The showcase for a collection named by id rather than picked from the list. A tile opens a
     * collection the list never loaded, so the summary has to be built from the repository instead
     * of read out of a list that may be empty.
     */
    fun loadCollectionShowcase(
        collectionId: Long,
        onReady: (DualCollectionShowcaseState) -> Unit
    ) {
        viewModelScope.launch { collectionShowcaseFor(collectionId)?.let(onReady) }
    }

    suspend fun collectionShowcaseFor(collectionId: Long): DualCollectionShowcaseState? {
        val entity = collectionRepository.getAllCollections()
            .firstOrNull { it.id == collectionId } ?: return null
        val item = buildCollectionItem(entity)
        return DualCollectionShowcaseState(
            name = item.name,
            description = item.description,
            coverPaths = item.coverPaths,
            gameCount = item.gameCount,
            platformSummary = item.platformSummary,
            totalPlaytimeMinutes = item.totalPlaytimeMinutes,
            installedCount = item.installedCount,
            achievementsEarned = item.achievementsEarned,
            achievementsTotal = item.achievementsTotal
        )
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

    /**
     * The options a library game offers on the companion. Mirrors the primary screen's quick menu
     * as far as this surface can act: launching, favouriting, details and, when the home is a
     * curated grid, putting the game on it.
     */
    fun libraryMenuActions(): List<DualLibraryMenuAction> {
        val game = focusedLibraryGame() ?: return emptyList()
        return buildList {
            add(
                when {
                    game.needsInstall -> DualLibraryMenuAction.INSTALL
                    game.isDownloaded -> DualLibraryMenuAction.PLAY
                    else -> DualLibraryMenuAction.DOWNLOAD
                }
            )
            add(if (game.isFavorite) DualLibraryMenuAction.UNFAVORITE else DualLibraryMenuAction.FAVORITE)
            add(DualLibraryMenuAction.DETAILS)
            add(DualLibraryMenuAction.ADD_TO_COLLECTION)
            if (_uiState.value.layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
                add(DualLibraryMenuAction.ADD_TO_GRID)
            }
            if (game.isRommGame || game.isAndroidApp) add(DualLibraryMenuAction.REFRESH)
            add(DualLibraryMenuAction.RESYNC_PLATFORM)
            if (game.isDownloaded || game.needsInstall) {
                add(
                    if (game.isAndroidApp && game.isDownloaded) {
                        DualLibraryMenuAction.UNINSTALL
                    } else {
                        DualLibraryMenuAction.DELETE
                    }
                )
            }
            add(if (game.isHidden) DualLibraryMenuAction.SHOW else DualLibraryMenuAction.HIDE)
        }
    }

    fun focusedLibraryGame(): HomeGameUi? {
        val state = _uiState.value
        return state.libraryGames.getOrNull(state.libraryFocusedIndex)
    }

    fun openLibraryGameMenu() {
        if (focusedLibraryGame() == null) return
        _uiState.update { it.copy(showLibraryMenu = true, libraryMenuFocusIndex = 0) }
    }

    fun closeLibraryGameMenu() = _uiState.update { it.copy(showLibraryMenu = false) }

    fun moveLibraryMenuFocus(delta: Int) = _uiState.update {
        val maxIndex = (libraryMenuActions().size - 1).coerceAtLeast(0)
        it.copy(libraryMenuFocusIndex = (it.libraryMenuFocusIndex + delta).coerceIn(0, maxIndex))
    }

    /**
     * Closes the menu and reports what was chosen, because launching a game belongs to the host
     * activity rather than here; the actions this layer owns are applied on the way out.
     */
    fun confirmLibraryMenu(): DualLibraryMenuAction? {
        val action = libraryMenuActions().getOrNull(_uiState.value.libraryMenuFocusIndex)
        val game = focusedLibraryGame()
        closeLibraryGameMenu()
        if (action == null || game == null) return null
        when (action) {
            DualLibraryMenuAction.FAVORITE, DualLibraryMenuAction.UNFAVORITE ->
                viewModelScope.launch {
                    gameRepository.updateFavoriteWithSync(game.id, !game.isFavorite)
                }
            DualLibraryMenuAction.ADD_TO_GRID -> viewModelScope.launch {
                homeTileRepository?.appendToLastPage(
                    ownerUserId = syncPreferencesRepository?.getRommUserId(),
                    target = com.nendo.argosy.domain.model.HomeTileTargetRef.Game(game.id),
                    columns = _uiState.value.customGridConfig.laneCount
                )
            }
            DualLibraryMenuAction.ADD_TO_COLLECTION -> openLibraryCollectionPicker(game.id)
            else -> Unit
        }
        return action
    }

    /**
     * Carries out the library menu choices this surface cannot apply itself. Anything that edits the
     * library is done by the primary process through the direct-action channel, which already owns
     * those flows and their confirmations; opening details stays with the caller because only the
     * host knows which display the detail screen belongs on.
     *
     * Touch and gamepad both come through here, so a choice cannot mean one thing when it is tapped
     * and another when it is confirmed with a button.
     */
    fun applyLibraryMenuAction(
        action: DualLibraryMenuAction?,
        game: HomeGameUi,
        onOpenDetails: (Long) -> Unit
    ) {
        val dsm = com.nendo.argosy.DualScreenManagerHolder.instance
        when (action) {
            DualLibraryMenuAction.PLAY, DualLibraryMenuAction.INSTALL ->
                if (game.isSteamGame && !game.isPlayable) {
                    dsm?.openSteamChooserForHome(game.id)
                } else {
                    dsm?.handleDirectAction(if (game.isPlayable) "PLAY" else "DOWNLOAD", game.id)
                }
            DualLibraryMenuAction.DOWNLOAD -> dsm?.handleDirectAction("DOWNLOAD", game.id)
            DualLibraryMenuAction.DETAILS -> onOpenDetails(game.id)
            DualLibraryMenuAction.REFRESH -> dsm?.handleDirectAction("REFRESH_METADATA", game.id)
            DualLibraryMenuAction.RESYNC_PLATFORM ->
                dsm?.handleDirectAction("RESYNC_PLATFORM", game.id)
            DualLibraryMenuAction.DELETE, DualLibraryMenuAction.UNINSTALL ->
                dsm?.handleDirectAction("DELETE", game.id)
            DualLibraryMenuAction.HIDE -> dsm?.handleDirectAction("HIDE", game.id)
            DualLibraryMenuAction.SHOW -> dsm?.handleDirectAction("UNHIDE", game.id)
            else -> Unit
        }
    }

    /**
     * Collection membership for the focused game, read straight from the repository. The primary
     * screen's picker is driven by its own view model, which this process cannot reach, so the
     * companion builds the same list from the same source rather than asking for it.
     */
    private fun openLibraryCollectionPicker(gameId: Long) {
        viewModelScope.launch {
            val member = collectionRepository.getCollectionIdsForGame(gameId).toSet()
            val entries = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() }
                .map { DualCollectionPickerEntry(it.id, it.name, it.id in member) }
            _uiState.update {
                it.copy(
                    collectionPickerGameId = gameId,
                    collectionPickerEntries = entries,
                    collectionPickerFocusIndex = 0
                )
            }
        }
    }

    fun closeCollectionPicker() = _uiState.update {
        it.copy(collectionPickerGameId = null, collectionPickerEntries = emptyList())
    }

    fun moveCollectionPickerFocus(delta: Int) = _uiState.update {
        val maxIndex = (it.collectionPickerEntries.size - 1).coerceAtLeast(0)
        it.copy(
            collectionPickerFocusIndex =
                (it.collectionPickerFocusIndex + delta).coerceIn(0, maxIndex)
        )
    }

    fun confirmCollectionPicker() {
        val state = _uiState.value
        val gameId = state.collectionPickerGameId ?: return
        val entry = state.collectionPickerEntries.getOrNull(state.collectionPickerFocusIndex)
            ?: return
        viewModelScope.launch {
            if (entry.isMember) {
                collectionRepository.removeGameFromCollection(entry.id, gameId)
            } else {
                collectionRepository.addGameToCollection(
                    com.nendo.argosy.data.local.entity.CollectionGameEntity(
                        collectionId = entry.id,
                        gameId = gameId
                    )
                )
            }
            openLibraryCollectionPicker(gameId)
        }
    }

    fun focusedCollectionGame(): HomeGameUi? {
        val state = _uiState.value
        return state.collectionGames.getOrNull(state.collectionGamesFocusedIndex)
    }

    // --- Library Grid Navigation ---

    private val libraryNav = GridFocusNavigator()


    fun resetStickyLibraryColumn() { libraryNav.resetStickyColumn() }

    fun setLibraryFocusIndex(index: Int) {
        libraryNav.resetStickyColumn()
        val clamped = index.coerceIn(0, (_uiState.value.libraryGames.size - 1).coerceAtLeast(0))
        updateLibraryFocus(clamped)
    }

    private fun libraryGridRows(): List<List<Int>> {
        val state = _uiState.value
        return GridFocusNavigator.buildGridRows(
            state.libraryGridItems, state.libraryColumns,
            isHeader = { it is DualLibraryGridItem.Header },
            gameIndex = { (it as DualLibraryGridItem.Game).gameIndex }
        )
    }

    private fun updateLibraryFocus(newIndex: Int) {
        val state = _uiState.value
        val orderedSections = com.nendo.argosy.data.model.computePartitionedSections(
            state.libraryGames, state.activeFilters.sort, HomeGameUiSortProps, sortPartition
        )
        val newLabel = sectionLabelForGameIndex(newIndex, orderedSections)
        _uiState.update { it.copy(
            libraryFocusedIndex = newIndex,
            currentSectionLabel = newLabel
        )}
    }

    private fun moveLibrary(direction: GridDirection) {
        val state = _uiState.value
        if (state.libraryGames.isEmpty()) return
        val newIndex = libraryNav.navigate(direction, state.libraryFocusedIndex, libraryGridRows()) ?: return
        updateLibraryFocus(newIndex)
    }

    fun moveLibraryFocusUp() = moveLibrary(GridDirection.UP)
    fun moveLibraryFocusDown() = moveLibrary(GridDirection.DOWN)
    fun moveLibraryFocusLeft() = moveLibrary(GridDirection.LEFT)
    fun moveLibraryFocusRight() = moveLibrary(GridDirection.RIGHT)

    fun jumpToSection(label: String) {
        libraryNav.resetStickyColumn()
        val state = _uiState.value
        val orderedSections = com.nendo.argosy.data.model.computePartitionedSections(
            state.libraryGames, state.activeFilters.sort, HomeGameUiSortProps, sortPartition
        )
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
            DualFilterCategory.SOURCE -> state.activeFilters.copy(source = option.value)
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

    fun updateSearchQuery(query: String) {
        val newFilters = _uiState.value.activeFilters.copy(searchQuery = query)
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
                items.add(
                    DualCollectionListItem.Header(
                        context.getString(R.string.dual_collections_header_user)
                    )
                )
                userCollections.forEach { entity ->
                    items.add(buildCollectionItem(entity))
                }
            }

            val genres = collectionRepository.getAllByType(CollectionType.GENRE)
                .filter { it.name.isNotBlank() }
            if (genres.isNotEmpty()) {
                items.add(
                    DualCollectionListItem.Header(
                        context.getString(R.string.dual_collections_header_genres)
                    )
                )
                genres.forEach { entity ->
                    items.add(buildCollectionItem(entity))
                }
            }

            val gameModes = collectionRepository.getAllByType(CollectionType.GAME_MODE)
                .filter { it.name.isNotBlank() }
            if (gameModes.isNotEmpty()) {
                items.add(
                    DualCollectionListItem.Header(
                        context.getString(R.string.dual_collections_header_game_modes)
                    )
                )
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
            totalPlaytimeMinutes = totalPlaytime,
            installedCount = games.count { it.isDownloaded },
            achievementsEarned = games.sumOf { it.earnedAchievementCount },
            achievementsTotal = games.sumOf { it.achievementCount }
        )
    }

    // --- Private: Library Loading ---



    private fun sectionLabelForGameIndex(gameIndex: Int, sections: List<Section<HomeGameUi>>): String {
        var offset = 0
        for (section in sections) {
            if (gameIndex < offset + section.items.size) return section.sidebarLabel
            offset += section.items.size
        }
        return sections.firstOrNull()?.sidebarLabel ?: ""
    }

    data class SortResult(
        val games: List<HomeGameUi>,
        val sections: List<Section<HomeGameUi>>,
        val gridItems: List<DualLibraryGridItem>,
        val labels: List<String>
    )

    private fun applySort(games: List<HomeGameUi>, sort: ActiveSort): SortResult {
        val orderedSections = com.nendo.argosy.data.model.computePartitionedSections(
            games, sort, HomeGameUiSortProps, sortPartition
        )
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

    private fun loadLibraryGames(
        hidden: Boolean = false,
        preserveFocus: Boolean = false,
        onLoaded: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val entities = if (hidden) gameRepository.getHiddenSortedByTitle()
                           else gameRepository.getAllSortedByTitle()
            val allGames = entities.map { it.toUi() }
            allLibraryGames = allGames
            libraryLoadedHidden = hidden
            val filters = _uiState.value.activeFilters
            val filtered = applyFiltersToList(allGames, filters)
            val result = applySort(filtered, filters.sort)
            updateLibraryState(result, preserveFocus)
            onLoaded?.invoke()
        }
    }

    private fun loadLibraryGamesForPlatform(
        platformId: Long,
        hidden: Boolean = false,
        preserveFocus: Boolean = false,
        onLoaded: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val entities = if (hidden) gameRepository.getHiddenByPlatform(platformId)
                           else gameRepository.getByPlatform(platformId)
            val platformGames = entities.map { it.toUi() }
            allLibraryGames = platformGames
            libraryLoadedHidden = hidden
            val filters = _uiState.value.activeFilters
            val filtered = applyFiltersToList(platformGames, filters)
            val result = applySort(filtered, filters.sort)
            updateLibraryState(result, preserveFocus)
            onLoaded?.invoke()
        }
    }

    private fun updateLibraryState(result: SortResult, preserveFocus: Boolean) {
        _uiState.update { it.copy(
            libraryGames = result.games.withCurrentDownloadState(),
            libraryGridItems = result.gridItems,
            sectionLabels = result.labels,
            currentSectionLabel = result.labels.firstOrNull() ?: "",
            libraryFocusedIndex = if (preserveFocus) {
                remapFocusIndex(it.libraryGames, it.libraryFocusedIndex, result.games)
            } else 0
        )}
    }

    private fun applyFilters(filters: DualActiveFilters) {
        val wantHidden = filters.source == "HIDDEN"
        if (wantHidden != libraryLoadedHidden) {
            val options = buildFilterOptions(_uiState.value.filterCategory, filters)
            _uiState.update { it.copy(filterOptions = options) }
            val platformId = _uiState.value.activeFilters.platformId
            if (platformId != null) loadLibraryGamesForPlatform(platformId, hidden = wantHidden)
            else loadLibraryGames(hidden = wantHidden)
            return
        }
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
        games: List<HomeGameUi>,
        filters: DualActiveFilters
    ): List<HomeGameUi> {
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
                    label = context.getString(option.labelRes) + directionIndicator,
                    isSelected = option == filters.sort.option
                )
            }
            DualFilterCategory.SOURCE -> SourceFilter.entries.map { source ->
                DualFilterOption(
                    label = context.getString(source.labelRes),
                    isSelected = filters.source == source.name,
                    value = source.name
                )
            }
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

    private suspend fun GameEntity.toUi(): HomeGameUi =
        toHomeGameUi(
            downloadStatus = downloadFileStatusRepository,
            gradientColors = gradientExtractionDelegate?.getGradient(id)
        )

    // --- Cover Repair ---

    fun repairCoverImage(gameId: Long, failedPath: String) {
        val useCase = repairImageCacheUseCase ?: return
        if (!pendingCoverRepairs.add(gameId)) return
        viewModelScope.launch {
            val url = useCase.repairCover(gameId, failedPath)
            if (url != null) {
                _uiState.update { it.copy(repairedCoverPaths = it.repairedCoverPaths + (gameId to url)) }
            }
            pendingCoverRepairs.remove(gameId)
        }
    }
}

object HomeGameUiSortProps : SortableProps<HomeGameUi> {
    override fun isInstalled(item: HomeGameUi) = item.isDownloaded
    override fun isFavorite(item: HomeGameUi) = item.isFavorite
    override fun sortTitle(item: HomeGameUi) = item.sortTitle
    override fun rating(item: HomeGameUi) = item.rating
    override fun userRating(item: HomeGameUi) = item.userRating
    override fun userDifficulty(item: HomeGameUi) = item.userDifficulty
    override fun releaseYear(item: HomeGameUi) = item.releaseYear
    override fun playCount(item: HomeGameUi) = item.playCount
    override fun playTimeMinutes(item: HomeGameUi) = item.playTimeMinutes
    override fun lastPlayedEpochMilli(item: HomeGameUi) = item.lastPlayedAt
    override fun addedAtEpochMilli(item: HomeGameUi) = item.addedAt ?: 0L
}
