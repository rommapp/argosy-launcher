package com.nendo.argosy.ui.screens.home

import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.domain.model.HomeSectionKind
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.ui.screens.common.DiscPickerState
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.ui.screens.gamedetail.CollectionItemUi

data class GameDownloadIndicator(
    val isDownloading: Boolean = false,
    val isExtracting: Boolean = false,
    val isPaused: Boolean = false,
    val isQueued: Boolean = false,
    val progress: Float = 0f
) {
    /**
     * Whether work is moving right now. Paused is not active: nothing is progressing, and a cover
     * that keeps rippling over a stopped download states the opposite of what is true.
     */
    val isActive: Boolean get() = isDownloading || isExtracting || isQueued

    /**
     * Whether the cover should be drawn as a part-finished copy at all. Wider than [isActive],
     * because a paused transfer still has bytes on disk worth showing - it is stilled, not gone.
     */
    val isShown: Boolean get() = isActive || isPaused

    companion object {
        val NONE = GameDownloadIndicator()
    }
}

data class HomeGameUi(
    val id: Long,
    val title: String,
    val platformId: Long,
    val platformSlug: String,
    val platformDisplayName: String,
    val coverPath: String?,
    val coverAspectRatio: Float? = null,
    val gradientColors: Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>? = null,
    val backgroundPath: String?,
    val boxBackPath: String? = null,
    val boxSpinePath: String? = null,
    val developer: String?,
    val releaseYear: Int?,
    val genre: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val isRommGame: Boolean = false,
    val isSteamGame: Boolean = false,
    val rating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    val isAndroidApp: Boolean = false,
    val packageName: String? = null,
    val needsInstall: Boolean = false,
    val youtubeVideoId: String? = null,
    val isNew: Boolean = false,
    val isHidden: Boolean = false,
    val sortTitle: String = "",
    val gameModes: String? = null,
    val franchises: String? = null,
    val addedAt: Long? = null,
    val playCount: Int = 0,
    val playTimeMinutes: Int = 0,
    val lastPlayedAt: Long? = null,
    val isPlayable: Boolean = isDownloaded,
    val description: String? = null,
    val status: String? = null,
    val titleId: String? = null
)

/**
 * One tile on a media row.
 *
 * The tile and the thing it plays are deliberately not the same item: for an episode the tile wears
 * the show's poster and the show's name, because that is how someone recognises what they are
 * watching, while [itemId] is the episode the server said to play. [subtitle] carries the episode
 * that confirm will actually start, so the tile never hides which one that is.
 *
 * [resumeTicks] is zero for anything not yet started, which is the normal case on the Next Up rail:
 * the episode after a finished one has never been played, so there is nothing to resume and confirm
 * simply starts it.
 *
 * A series tile stands for a whole show rather than one position in it, so [itemId] is the series
 * and the episode a press starts has to be worked out. [resumeItemId] carries that episode for the
 * one case it is known without asking anything -- a show the Continue Watching rail is already
 * holding a part-watched episode of -- which is what lets the footer promise Resume and mean it.
 */
data class HomeMediaUi(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String,
    val seriesId: String? = null,
    val isEpisode: Boolean = false,
    val isSeries: Boolean = false,
    val availability: MediaAvailability = MediaAvailability.NOT_DOWNLOADED,
    val resumeTicks: Long = 0,
    val progressFraction: Float = 0f,
    val resumeItemId: String? = null
) {
    val hasResumePosition: Boolean get() = resumeTicks > 0

    val isDownloaded: Boolean get() = availability.hasLocalCopy

    /**
     * The item a resume prompt is about. A series tile is asking about the episode it would resume,
     * never about the show, because starting a show over is not a thing the player can be told to do.
     */
    val resumeTargetId: String get() = resumeItemId ?: itemId

    /**
     * What opening the details of this tile should show. An episode's details are its show's, since
     * that is where the seasons and the rest of the episodes are.
     */
    val detailItemId: String get() = seriesId ?: itemId
}

sealed class HomeRowItem {
    data class Game(val game: HomeGameUi) : HomeRowItem()
    data class Media(val media: HomeMediaUi) : HomeRowItem()
    data class ViewAll(
        val platformId: Long? = null,
        val platformName: String? = null,
        val logoPath: String? = null,
        val sourceFilter: String? = null,
        val label: String = "View All"
    ) : HomeRowItem()
}

data class HomePlatformUi(
    val id: Long,
    val slug: String,
    val name: String,
    val shortName: String,
    val displayName: String,
    val logoPath: String?,
    val hasEmulator: Boolean = true
)

fun PlatformEntity.toHomePlatformUi(emulatorDetector: EmulatorDetector) = HomePlatformUi(
    id = id,
    slug = slug,
    name = name,
    shortName = shortName,
    displayName = getDisplayName(),
    logoPath = logoPath,
    hasEmulator = emulatorDetector.hasAnyEmulator(slug)
)

/**
 * One row on the home surface.
 *
 * A row that repeats carries the thing it repeats over, the way [Platform] carries a position in the
 * platform list; [MediaLibrary] is the same shape because the libraries a media server offers are
 * discovered at runtime and there is no fixed set of them to name here.
 */
sealed class HomeRow(val kind: HomeSectionKind) {
    data object Favorites : HomeRow(HomeSectionKind.FAVORITES)
    data class Platform(val index: Int) : HomeRow(HomeSectionKind.PLATFORM)
    data object Continue : HomeRow(HomeSectionKind.CONTINUE)
    data object Recommendations : HomeRow(HomeSectionKind.RECOMMENDATIONS)
    data object Android : HomeRow(HomeSectionKind.ANDROID)
    data object Steam : HomeRow(HomeSectionKind.STEAM)
    data object ContinueWatching : HomeRow(HomeSectionKind.CONTINUE_WATCHING)
    data object NextUp : HomeRow(HomeSectionKind.NEXT_UP)
    data class MediaLibrary(val index: Int) : HomeRow(HomeSectionKind.MEDIA_LIBRARY)
    data class PinnedRegular(val pinId: Long, val collectionId: Long, val name: String) :
        HomeRow(HomeSectionKind.PINNED_REGULAR)
    data class PinnedVirtual(val pinId: Long, val type: CategoryType, val name: String) :
        HomeRow(HomeSectionKind.PINNED_VIRTUAL)
}

data class HomeUiState(
    val platforms: List<HomePlatformUi> = emptyList(),
    val platformItems: List<HomeRowItem> = emptyList(),
    val focusedGameIndex: Int = 0,
    val recentGames: List<HomeGameUi> = emptyList(),
    val favoriteGames: List<HomeGameUi> = emptyList(),
    val recommendedGames: List<HomeGameUi> = emptyList(),
    val androidGames: List<HomeGameUi> = emptyList(),
    val steamGames: List<HomeGameUi> = emptyList(),
    val pinnedCollections: List<PinnedCollection> = emptyList(),
    val pinnedGames: Map<Long, List<HomeGameUi>> = emptyMap(),
    val pinnedGamesLoading: Set<Long> = emptySet(),
    val nextUpMedia: List<HomeMediaUi> = emptyList(),
    val continueWatchingMedia: List<HomeMediaUi> = emptyList(),
    val favoriteMedia: List<HomeMediaUi> = emptyList(),
    val mediaLibraries: List<com.nendo.argosy.ui.screens.media.MediaLibraryUi> = emptyList(),
    val mediaLibraryItems: List<HomeMediaUi> = emptyList(),
    val mediaLibraryItemsFor: String? = null,
    val mediaLibrariesLoaded: Boolean = false,
    val mediaDownloadProgress: Map<String, com.nendo.argosy.data.repository.MediaTransferProgress> =
        emptyMap(),
    val isMediaSignedIn: Boolean = false,
    val isMediaLoading: Boolean = false,
    val showNextUpRow: Boolean = true,
    val showContinueWatchingRow: Boolean = false,
    val showMediaLibraryRows: Boolean = true,
    val mediaResumePrompt: com.nendo.argosy.ui.screens.media.MediaResumePrompt? = null,
    val currentRow: HomeRow = HomeRow.Continue,
    val carouselConfig: com.nendo.argosy.domain.model.CarouselConfig =
        com.nendo.argosy.domain.model.CarouselConfig(),
    val autoGridConfig: com.nendo.argosy.domain.model.AutoGridConfig =
        com.nendo.argosy.domain.model.AutoGridConfig(),
    val layoutKind: com.nendo.argosy.domain.model.HomeLayoutKind =
        com.nendo.argosy.domain.model.HomeLayoutKind.CAROUSEL,
    val customGridConfig: com.nendo.argosy.domain.model.CustomGridConfig =
        com.nendo.argosy.domain.model.CustomGridConfig(),
    val customGrid: com.nendo.argosy.ui.components.CustomGridState =
        com.nendo.argosy.ui.components.CustomGridState(),
    val tileGames: Map<Long, HomeGameUi> = emptyMap(),
    val tileCollections: Map<Long, com.nendo.argosy.ui.components.TileCollectionUi> = emptyMap(),
    val tileApps: Map<String, String> = emptyMap(),
    val tileMedia: Map<String, HomeMediaUi> = emptyMap(),
    val isLoading: Boolean = true,
    val isRommConfigured: Boolean = false,
    val showGameMenu: Boolean = false,
    val gameMenuFocusIndex: Int = 0,
    val showAddToCollectionModal: Boolean = false,
    val collectionGameId: Long? = null,
    val collections: List<CollectionItemUi> = emptyList(),
    val collectionModalFocusIndex: Int = 0,
    val showCreateCollectionDialog: Boolean = false,
    val downloadIndicators: Map<Long, GameDownloadIndicator> = emptyMap(),
    val repairedCoverPaths: Map<Long, String> = emptyMap(),
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val homeBackgroundMode: HomeBackgroundMode = HomeBackgroundMode.GAME_ART,
    val syncOverlayState: SyncOverlayState? = null,
    val discPickerState: DiscPickerState? = null,
    val discPickerFocusIndex: Int = 0,
    val memcardPickerState: com.nendo.argosy.ui.screens.common.MemcardPickerState? = null,
    val memcardPickerFocusIndex: Int = 0,
    val changelogEntry: com.nendo.argosy.domain.model.ChangelogEntry? = null,
    val isVideoPreviewActive: Boolean = false,
    val videoPreviewId: String? = null,
    val isVideoPreviewLoading: Boolean = false,
    val muteVideoPreview: Boolean = false,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelayMs: Long = 3000L
) {
    /**
     * The rows on offer, in the order [HomeSectionKind] declares them: the fixed opening run, then
     * every repeating run, then the fixed closing run. Walking the three lists rather than hardcoding
     * the sequence here is what keeps this screen and the companion agreeing on order.
     */
    val availableRows: List<HomeRow>
        get() = buildList {
            HomeSectionKind.LEADING.forEach { kind -> fixedRow(kind)?.let { add(it) } }
            HomeSectionKind.REPEATING.forEach { kind -> addAll(repeatingRows(kind)) }
            HomeSectionKind.TRAILING.forEach { kind -> fixedRow(kind)?.let { add(it) } }
        }

    private fun fixedRow(kind: HomeSectionKind): HomeRow? = when (kind) {
        HomeSectionKind.CONTINUE -> HomeRow.Continue.takeIf { recentGames.isNotEmpty() }
        HomeSectionKind.RECOMMENDATIONS ->
            HomeRow.Recommendations.takeIf { recommendedGames.isNotEmpty() }
        HomeSectionKind.FAVORITES -> HomeRow.Favorites.takeIf { hasFavorites }
        HomeSectionKind.ANDROID -> HomeRow.Android.takeIf { androidGames.isNotEmpty() }
        HomeSectionKind.STEAM -> HomeRow.Steam.takeIf { steamGames.isNotEmpty() }
        HomeSectionKind.CONTINUE_WATCHING ->
            HomeRow.ContinueWatching.takeIf { showsMediaRow(showContinueWatchingRow) }
        HomeSectionKind.NEXT_UP -> HomeRow.NextUp.takeIf { showsMediaRow(showNextUpRow) }
        else -> null
    }

    /**
     * The rows one repeating kind contributes.
     *
     * The two pinned kinds share a single display order rather than running one after the other, so
     * the whole pinned run is built at the first of them and the second contributes nothing; sorting
     * each kind separately would split a hand-ordered set of pins into two blocks.
     */
    private fun repeatingRows(kind: HomeSectionKind): List<HomeRow> = when (kind) {
        HomeSectionKind.PLATFORM -> platforms.indices.map { HomeRow.Platform(it) }
        HomeSectionKind.PINNED_REGULAR -> pinnedRows
        HomeSectionKind.PINNED_VIRTUAL -> emptyList()
        HomeSectionKind.MEDIA_LIBRARY ->
            if (showsMediaRow(showMediaLibraryRows)) {
                mediaLibraries.indices.map { HomeRow.MediaLibrary(it) }
            } else {
                emptyList()
            }
        else -> emptyList()
    }

    private val pinnedRows: List<HomeRow>
        get() = pinnedCollections.sortedByDescending { it.displayOrder }.map { pinned ->
            when (pinned) {
                is PinnedCollection.Regular ->
                    HomeRow.PinnedRegular(pinned.id, pinned.collectionId, pinned.displayName)
                is PinnedCollection.Virtual ->
                    HomeRow.PinnedVirtual(pinned.id, pinned.type, pinned.categoryName)
            }
        }

    /**
     * A media row stands whether or not it has anything in it, so long as it is switched on and an
     * account is signed in: an empty row is worth saying out loud, because "nothing up next" is an
     * answer, whereas a row that quietly disappears reads as media being broken.
     *
     * Being signed out removes it instead, since offering a media row to someone with no media
     * server is advertising a feature they have not asked for.
     */
    private fun showsMediaRow(enabled: Boolean): Boolean = enabled && isMediaSignedIn

    /**
     * The titles among the favourites, which is nothing at all without a media account. Marking a
     * title a favourite writes to the media server, so the row can only speak for an account that is
     * signed in; the flags are not lost while it is not, they simply have nobody to belong to.
     */
    val favoriteMediaShown: List<HomeMediaUi>
        get() = if (isMediaSignedIn) favoriteMedia else emptyList()

    /**
     * Whether the Favorites row stands. Either kind alone is enough: a library of favourite shows
     * and no favourite games is still a set of favourites, and hiding the row for it would be the
     * same write-only button the media half was before it had anywhere to appear.
     */
    val hasFavorites: Boolean
        get() = favoriteGames.isNotEmpty() || favoriteMediaShown.isNotEmpty()

    /**
     * Whether the row under the cursor is still one of the rows on offer.
     *
     * A library row is held while the library listing has not arrived: until then there are no
     * library rows to be found among the available ones, and a row restored from saved state would be
     * discarded a moment before the row it names comes into existence.
     */
    val holdsCurrentRow: Boolean
        get() = when {
            currentRow in availableRows -> true
            currentRow is HomeRow.MediaLibrary -> !mediaLibrariesLoaded
            else -> false
        }

    val currentPlatform: HomePlatformUi?
        get() = (currentRow as? HomeRow.Platform)?.let { platforms.getOrNull(it.index) }

    val currentMediaLibrary: com.nendo.argosy.ui.screens.media.MediaLibraryUi?
        get() = (currentRow as? HomeRow.MediaLibrary)?.let { mediaLibraries.getOrNull(it.index) }

    /**
     * What the row under the cursor holds, in the order it is walked.
     *
     * Favorites is the one row carrying both kinds, and they run one after the other -- games, then
     * titles -- rather than being interleaved, the way search holds its two kinds apart. There is no
     * sort key both answer to: a game orders by its sort title and a title by the server's own, so
     * any merged order would be invented here and would shuffle on every refresh. Games lead because
     * the row was theirs, and an existing shelf should not move when titles start appearing after it.
     */
    val currentItems: List<HomeRowItem>
        get() = when (currentRow) {
            HomeRow.Favorites -> {
                if (!hasFavorites) emptyList()
                else favoriteGames.map { HomeRowItem.Game(it) } +
                    favoriteMediaShown.map { HomeRowItem.Media(it) } +
                    HomeRowItem.ViewAll(sourceFilter = "FAVORITES", label = "View All")
            }
            is HomeRow.Platform -> platformItems
            HomeRow.Continue -> {
                if (recentGames.isEmpty()) emptyList()
                else recentGames.map { HomeRowItem.Game(it) } + HomeRowItem.ViewAll(
                    sourceFilter = "PLAYABLE",
                    label = "View All"
                )
            }
            HomeRow.Recommendations -> {
                if (recommendedGames.isEmpty()) emptyList()
                else recommendedGames.map { HomeRowItem.Game(it) }
            }
            HomeRow.Android -> {
                if (androidGames.isEmpty()) emptyList()
                else androidGames.map { HomeRowItem.Game(it) } + HomeRowItem.ViewAll(
                    platformId = com.nendo.argosy.data.platform.LocalPlatformIds.ANDROID,
                    platformName = "Android",
                    logoPath = null
                )
            }
            HomeRow.Steam -> {
                if (steamGames.isEmpty()) emptyList()
                else steamGames.map { HomeRowItem.Game(it) } + HomeRowItem.ViewAll(
                    platformId = com.nendo.argosy.data.platform.LocalPlatformIds.STEAM,
                    platformName = "Steam",
                    logoPath = null
                )
            }
            HomeRow.ContinueWatching -> continueWatchingMedia.map { HomeRowItem.Media(it) }
            HomeRow.NextUp -> nextUpMedia.map { HomeRowItem.Media(it) }
            is HomeRow.MediaLibrary -> {
                if (mediaLibraryItemsFor == currentMediaLibrary?.libraryId) {
                    mediaLibraryItems.map { HomeRowItem.Media(it) }
                } else {
                    emptyList()
                }
            }
            is HomeRow.PinnedRegular -> {
                pinnedGames[currentRow.pinId]?.map { HomeRowItem.Game(it) } ?: emptyList()
            }
            is HomeRow.PinnedVirtual -> {
                pinnedGames[currentRow.pinId]?.map { HomeRowItem.Game(it) } ?: emptyList()
            }
        }

    /**
     * Whether the row on screen is made of media rather than games. Every media row draws the same
     * tiles and suppresses the game info panel, so presentation reads this one accessor.
     */
    val isMediaRow: Boolean
        get() = currentRow.kind in HomeSectionKind.MEDIA

    /**
     * A library row browses a library's own top-level items, so its tile is a movie or a series
     * rather than the specific episode the two personal rails name. Confirm no longer routes on it --
     * every media row plays -- but where a row's contents come from still does: a library is filled
     * by the library sync and the rails by their own fetch, so an empty one asks for a different
     * thing.
     */
    val isMediaLibraryRow: Boolean
        get() = currentRow is HomeRow.MediaLibrary

    /**
     * Whether the media row on screen is still waiting on its contents. A library row that has not
     * had its own items delivered yet is loading rather than empty, since saying "nothing here" about
     * a library that simply has not been read would be wrong every time a row is entered.
     */
    val isMediaRowLoading: Boolean
        get() = when {
            !isMediaRow -> false
            isMediaLibraryRow -> mediaLibraryItemsFor != currentMediaLibrary?.libraryId
            else -> isMediaLoading
        }

    /**
     * The title under the cursor, whichever layout is showing. A curated grid answers from its own
     * cursor for the same reason [focusedGame] does: the tiles on a page are not a row, so nothing
     * downstream can find the focused title by looking at one.
     */
    val focusedMedia: HomeMediaUi?
        get() = if (layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
            focusedTileMedia
        } else {
            (focusedItem as? HomeRowItem.Media)?.media
        }

    val focusedItem: HomeRowItem?
        get() = currentItems.getOrNull(focusedGameIndex)

    /**
     * The game under the cursor, whichever layout is showing. Everything downstream reads this one
     * accessor - background art, the info panel, favourites, details, the ambient LED - so a curated
     * grid has to answer it from its own cursor rather than leave them all reading a section list
     * that this layout never puts on screen.
     */
    val focusedGame: HomeGameUi?
        get() = if (layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
            focusedTileGame
        } else {
            (focusedItem as? HomeRowItem.Game)?.game
        }

    val focusedTile: com.nendo.argosy.domain.model.HomeTile?
        get() = customGrid.focusedTile

    val focusedTileGame: HomeGameUi?
        get() = (focusedTile?.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Game)
            ?.let { tileGames[it.gameId] }

    val focusedTileMedia: HomeMediaUi?
        get() = (focusedTile?.target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Media)
            ?.let { tileMedia[it.itemId] }

    val rowTitle: String
        get() = when (currentRow) {
            HomeRow.Favorites -> "Favorites"
            is HomeRow.Platform -> currentPlatform?.name ?: "Unknown"
            HomeRow.Continue -> "Continue Playing"
            HomeRow.Recommendations -> "Recommended For You"
            HomeRow.Android -> "Android"
            HomeRow.Steam -> "Steam"
            HomeRow.ContinueWatching -> "Continue Watching"
            HomeRow.NextUp -> "Next Up"
            is HomeRow.MediaLibrary -> currentMediaLibrary?.name ?: "Library"
            is HomeRow.PinnedRegular -> currentRow.name
            is HomeRow.PinnedVirtual -> currentRow.name
        }

    fun shortLabelFor(row: HomeRow): String = when (row) {
        HomeRow.Continue -> "Recent"
        HomeRow.Recommendations -> "Picks"
        HomeRow.Favorites -> "Favs"
        HomeRow.Android -> "Android"
        HomeRow.Steam -> "Steam"
        HomeRow.ContinueWatching -> "Watch"
        HomeRow.NextUp -> "Next Up"
        is HomeRow.Platform -> platforms.getOrNull(row.index)?.let { p ->
            // Strip manufacturer prefix when result lands in 4..9 chars; else raw name if short; else acronym.
            val normalized = PlatformDefinitions.normalizeDisplayName(p.name)
            when {
                normalized.length in 4..9 -> normalized
                p.name.length <= 9        -> p.name
                else                       -> p.shortName
            }
        } ?: "?"
        is HomeRow.MediaLibrary -> mediaLibraries.getOrNull(row.index)?.name?.take(6) ?: "?"
        is HomeRow.PinnedRegular -> row.name.take(6)
        is HomeRow.PinnedVirtual -> row.name.take(6)
    }

    fun breadcrumbItems(maxNeighbors: Int = 2): List<BreadcrumbItem> {
        val rows = availableRows
        if (rows.isEmpty()) return emptyList()
        val currentIdx = rows.indexOf(currentRow).coerceAtLeast(0)
        return (-maxNeighbors..maxNeighbors).map { offset ->
            val idx = (currentIdx + offset).mod(rows.size)
            BreadcrumbItem(
                label = shortLabelFor(rows[idx]),
                isCurrent = idx == currentIdx
            )
        }
    }

    fun downloadIndicatorFor(gameId: Long): GameDownloadIndicator =
        downloadIndicators[gameId] ?: GameDownloadIndicator.NONE

    /**
     * The same indicator a game tile gets, for a title being fetched. A series answers for whatever
     * episode of it is on the way, so the row a viewer is actually looking at is the one that shows
     * the work.
     */
    fun mediaDownloadIndicatorFor(media: HomeMediaUi): GameDownloadIndicator {
        val transfer = mediaDownloadProgress[media.itemId]
            ?: media.seriesId?.let { mediaDownloadProgress[it] }
            ?: return GameDownloadIndicator.NONE
        return GameDownloadIndicator(
            isDownloading = !transfer.isPaused,
            isPaused = transfer.isPaused,
            progress = transfer.fraction
        )
    }

    val homeTiles: List<com.nendo.argosy.domain.model.HomeTile> get() = customGrid.tiles

    val customGridPage: Int get() = customGrid.page

    val customGridPageCount: Int get() = customGrid.pageCount

    val customGridCell: com.nendo.argosy.domain.model.GridCell get() = customGrid.cell

    val showTilePicker: Boolean get() = customGrid.showPicker

    val tilePickerQuery: String get() = customGrid.pickerQuery

    val tilePickerFocusIndex: Int get() = customGrid.pickerFocusIndex

    val tilePickerEntries: List<com.nendo.argosy.ui.components.TilePickerEntry>
        get() = customGrid.pickerEntries

    val mediaTileSetup: com.nendo.argosy.ui.components.MediaTileSetup?
        get() = customGrid.mediaSetup

    val mediaTileNotice: com.nendo.argosy.ui.components.MediaTileNotice?
        get() = customGrid.mediaTileNotice

    val showMediaTileSetup: Boolean get() = customGrid.isMediaSetupOpen

    val showTileFileBrowser: Boolean get() = customGrid.showFileBrowser

    fun tilesOnPage(pageIndex: Int): List<com.nendo.argosy.domain.model.HomeTile> =
        customGrid.tilesOnPage(pageIndex)

    /**
     * What a tile draws. A game whose row survived but whose library entry did not resolves to a
     * missing marker rather than to nothing, so a page keeps its shape and says what is wrong. A
     * pinned title the library sync has not stored yet reads the same way, and fills itself in as
     * soon as the library holding it is read.
     */
    fun tileContentFor(
        tile: com.nendo.argosy.domain.model.HomeTile
    ): com.nendo.argosy.ui.components.CustomGridTileContent? =
        when (val target = tile.target) {
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Game -> {
                val game = tileGames[target.gameId]
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = game,
                    label = game?.title ?: "Missing game",
                    isMissing = game == null,
                    subtitle = game?.platformDisplayName,
                    stats = game?.let { com.nendo.argosy.ui.components.tileStatsFor(it) }.orEmpty()
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection -> {
                val collection = tileCollections[target.collectionId]
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = collection?.name ?: "Missing collection",
                    isMissing = collection == null,
                    coverPath = collection?.coverPath,
                    subtitle = "Collection",
                    stats = collection?.let {
                        listOf(
                            com.nendo.argosy.ui.components.TileStat(
                                "Games",
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
                    label = name ?: "Missing app",
                    isMissing = name == null,
                    packageName = target.packageName,
                    subtitle = "App"
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.Media -> {
                val media = tileMedia[target.itemId]
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    media = media,
                    label = media?.title ?: "Missing title",
                    isMissing = media == null,
                    subtitle = media?.subtitle,
                    posterUrl = media?.posterUrl
                )
            }
            is com.nendo.argosy.domain.model.HomeTileTargetRef.LocalMedia ->
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = target.filePath.substringAfterLast('/').substringBeforeLast('.'),
                    subtitle = "On this device"
                )
            com.nendo.argosy.domain.model.HomeTileTargetRef.Unresolvable ->
                com.nendo.argosy.ui.components.CustomGridTileContent(
                    game = null,
                    label = "Unavailable",
                    isMissing = true
                )
        }
}

data class BreadcrumbItem(val label: String, val isCurrent: Boolean)

sealed class HomeEvent {
    data class LaunchIntent(val intent: Intent, val options: android.os.Bundle? = null) : HomeEvent()
    data class NavigateToCollections(val collectionId: Long) : HomeEvent()
    data class NavigateToLibrary(
        val platformId: Long? = null,
        val sourceFilter: String? = null
    ) : HomeEvent()

    /**
     * Starts playback of one media item. [startOver] discards the stored position rather than
     * resuming from it, which is the only thing the Start Over prompt does differently.
     */
    data class PlayMedia(val itemId: String, val startOver: Boolean) : HomeEvent()
    data class NavigateToMediaDetail(val itemId: String) : HomeEvent()
}
