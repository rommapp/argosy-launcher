package com.nendo.argosy.ui.screens.home

import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
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
    val isActive: Boolean get() = isDownloading || isExtracting || isPaused || isQueued

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

sealed class HomeRowItem {
    data class Game(val game: HomeGameUi) : HomeRowItem()
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

sealed class HomeRow(val kind: HomeSectionKind) {
    data object Favorites : HomeRow(HomeSectionKind.FAVORITES)
    data class Platform(val index: Int) : HomeRow(HomeSectionKind.PLATFORM)
    data object Continue : HomeRow(HomeSectionKind.CONTINUE)
    data object Recommendations : HomeRow(HomeSectionKind.RECOMMENDATIONS)
    data object Android : HomeRow(HomeSectionKind.ANDROID)
    data object Steam : HomeRow(HomeSectionKind.STEAM)
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
    val availableRows: List<HomeRow>
        get() = buildList {
            HomeSectionKind.LEADING.forEach { kind ->
                val row = when (kind) {
                    HomeSectionKind.CONTINUE -> HomeRow.Continue.takeIf { recentGames.isNotEmpty() }
                    HomeSectionKind.RECOMMENDATIONS -> HomeRow.Recommendations.takeIf { recommendedGames.isNotEmpty() }
                    HomeSectionKind.FAVORITES -> HomeRow.Favorites.takeIf { favoriteGames.isNotEmpty() }
                    HomeSectionKind.ANDROID -> HomeRow.Android.takeIf { androidGames.isNotEmpty() }
                    HomeSectionKind.STEAM -> HomeRow.Steam.takeIf { steamGames.isNotEmpty() }
                    else -> null
                }
                row?.let { add(it) }
            }
            platforms.forEachIndexed { index, _ -> add(HomeRow.Platform(index)) }
            pinnedCollections.sortedByDescending { it.displayOrder }.forEach { pinned ->
                when (pinned) {
                    is PinnedCollection.Regular -> add(
                        HomeRow.PinnedRegular(pinned.id, pinned.collectionId, pinned.displayName)
                    )
                    is PinnedCollection.Virtual -> add(
                        HomeRow.PinnedVirtual(pinned.id, pinned.type, pinned.categoryName)
                    )
                }
            }
        }

    val currentPlatform: HomePlatformUi?
        get() = (currentRow as? HomeRow.Platform)?.let { platforms.getOrNull(it.index) }

    val currentItems: List<HomeRowItem>
        get() = when (currentRow) {
            HomeRow.Favorites -> {
                if (favoriteGames.isEmpty()) emptyList()
                else favoriteGames.map { HomeRowItem.Game(it) } + HomeRowItem.ViewAll(
                    sourceFilter = "FAVORITES",
                    label = "View All"
                )
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
            is HomeRow.PinnedRegular -> {
                pinnedGames[currentRow.pinId]?.map { HomeRowItem.Game(it) } ?: emptyList()
            }
            is HomeRow.PinnedVirtual -> {
                pinnedGames[currentRow.pinId]?.map { HomeRowItem.Game(it) } ?: emptyList()
            }
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

    val rowTitle: String
        get() = when (currentRow) {
            HomeRow.Favorites -> "Favorites"
            is HomeRow.Platform -> currentPlatform?.name ?: "Unknown"
            HomeRow.Continue -> "Continue Playing"
            HomeRow.Recommendations -> "Recommended For You"
            HomeRow.Android -> "Android"
            HomeRow.Steam -> "Steam"
            is HomeRow.PinnedRegular -> currentRow.name
            is HomeRow.PinnedVirtual -> currentRow.name
        }

    fun shortLabelFor(row: HomeRow): String = when (row) {
        HomeRow.Continue -> "Recent"
        HomeRow.Recommendations -> "Picks"
        HomeRow.Favorites -> "Favs"
        HomeRow.Android -> "Android"
        HomeRow.Steam -> "Steam"
        is HomeRow.Platform -> platforms.getOrNull(row.index)?.let { p ->
            // Strip manufacturer prefix when result lands in 4..9 chars; else raw name if short; else acronym.
            val normalized = PlatformDefinitions.normalizeDisplayName(p.name)
            when {
                normalized.length in 4..9 -> normalized
                p.name.length <= 9        -> p.name
                else                       -> p.shortName
            }
        } ?: "?"
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

    val homeTiles: List<com.nendo.argosy.domain.model.HomeTile> get() = customGrid.tiles

    val customGridPage: Int get() = customGrid.page

    val customGridPageCount: Int get() = customGrid.pageCount

    val customGridCell: com.nendo.argosy.domain.model.GridCell get() = customGrid.cell

    val showTilePicker: Boolean get() = customGrid.showPicker

    val tilePickerQuery: String get() = customGrid.pickerQuery

    val tilePickerFocusIndex: Int get() = customGrid.pickerFocusIndex

    val tilePickerEntries: List<com.nendo.argosy.ui.components.TilePickerEntry>
        get() = customGrid.pickerEntries

    fun tilesOnPage(pageIndex: Int): List<com.nendo.argosy.domain.model.HomeTile> =
        customGrid.tilesOnPage(pageIndex)

    /**
     * What a tile draws. A game whose row survived but whose library entry did not resolves to a
     * missing marker rather than to nothing, so a page keeps its shape and says what is wrong.
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
}
