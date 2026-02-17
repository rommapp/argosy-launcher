package com.nendo.argosy.ui.screens.home

import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
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
    val developer: String?,
    val releaseYear: Int?,
    val genre: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val isRommGame: Boolean = false,
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
    val isNew: Boolean = false
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

sealed class HomeRow {
    data object Favorites : HomeRow()
    data class Platform(val index: Int) : HomeRow()
    data object Continue : HomeRow()
    data object Recommendations : HomeRow()
    data object Android : HomeRow()
    data object Steam : HomeRow()
    data class PinnedRegular(val pinId: Long, val collectionId: Long, val name: String) : HomeRow()
    data class PinnedVirtual(val pinId: Long, val type: CategoryType, val name: String) : HomeRow()
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
    val syncOverlayState: SyncOverlayState? = null,
    val discPickerState: DiscPickerState? = null,
    val discPickerFocusIndex: Int = 0,
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
            if (recentGames.isNotEmpty()) add(HomeRow.Continue)
            if (recommendedGames.isNotEmpty()) add(HomeRow.Recommendations)
            if (favoriteGames.isNotEmpty()) add(HomeRow.Favorites)
            if (androidGames.isNotEmpty()) add(HomeRow.Android)
            if (steamGames.isNotEmpty()) add(HomeRow.Steam)
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

    val focusedGame: HomeGameUi?
        get() = (focusedItem as? HomeRowItem.Game)?.game

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

    fun downloadIndicatorFor(gameId: Long): GameDownloadIndicator =
        downloadIndicators[gameId] ?: GameDownloadIndicator.NONE
}

sealed class HomeEvent {
    data class NavigateToLaunch(
        val gameId: Long,
        val channelName: String? = null
    ) : HomeEvent()
    data class LaunchIntent(val intent: Intent) : HomeEvent()
    data class NavigateToLibrary(
        val platformId: Long? = null,
        val sourceFilter: String? = null
    ) : HomeEvent()
}
