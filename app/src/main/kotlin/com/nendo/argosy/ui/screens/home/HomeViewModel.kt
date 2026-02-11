package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.Changelog
import com.nendo.argosy.domain.model.ChangelogEntry
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.recommendation.GenerateRecommendationsUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.DiscPickerState
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameGradientRequest
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.common.RefreshAndroidResult
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.ui.screens.gamedetail.CollectionItemUi
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.ui.audio.AmbientAudioManager
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nendo.argosy.data.platform.LocalPlatformIds
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val PLATFORM_GAMES_LIMIT = 20
private const val MAX_DISPLAYED_RECOMMENDATIONS = 8
private const val RECOMMENDATION_PENALTY = 0.9f
private val EXCLUDED_RECOMMENDATION_STATUSES = setOf(
    CompletionStatus.FINISHED.apiValue,
    CompletionStatus.COMPLETED_100.apiValue,
    CompletionStatus.NEVER_PLAYING.apiValue
)
private const val RECENT_GAMES_LIMIT = 10
private const val RECENT_GAMES_CANDIDATE_POOL = 40
private const val AUTO_SYNC_DAYS = 7L
private const val NEW_GAME_THRESHOLD_HOURS = 24L
private const val RECENT_PLAYED_THRESHOLD_HOURS = 4L
private const val MENU_INDEX_MAX_DOWNLOADED = 5
private const val MENU_INDEX_MAX_REMOTE = 4

private const val KEY_ROW_TYPE = "home_row_type"
private const val KEY_PLATFORM_INDEX = "home_platform_index"
private const val KEY_GAME_INDEX = "home_game_index"

private const val ROW_TYPE_FAVORITES = "favorites"
private const val ROW_TYPE_PLATFORM = "platform"
private const val ROW_TYPE_CONTINUE = "continue"
private const val ROW_TYPE_RECOMMENDATIONS = "recommendations"
private const val ROW_TYPE_STEAM = "steam"
private const val ROW_TYPE_ANDROID = "android"
private const val ROW_TYPE_PINNED_REGULAR = "pinned_regular"
private const val ROW_TYPE_PINNED_VIRTUAL = "pinned_virtual"

private const val KEY_PINNED_COLLECTION_ID = "home_pinned_collection_id"
private const val KEY_PINNED_TYPE = "home_pinned_type"
private const val KEY_PINNED_NAME = "home_pinned_name"

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
    val changelogEntry: ChangelogEntry? = null,
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
                    platformId = LocalPlatformIds.ANDROID,
                    platformName = "Android",
                    logoPath = null
                )
            }
            HomeRow.Steam -> {
                if (steamGames.isEmpty()) emptyList()
                else steamGames.map { HomeRowItem.Game(it) } + HomeRowItem.ViewAll(
                    platformId = LocalPlatformIds.STEAM,
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

private data class RecentGamesCache(
    val games: List<HomeGameUi>?,
    val version: Long
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val collectionDao: CollectionDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val gameNavigationContext: GameNavigationContext,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val downloadManager: DownloadManager,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val generateRecommendationsUseCase: GenerateRecommendationsUseCase,
    private val apkInstallManager: ApkInstallManager,
    private val repairImageCacheUseCase: RepairImageCacheUseCase,
    private val modalResetSignal: ModalResetSignal,
    private val getPinnedCollectionsUseCase: GetPinnedCollectionsUseCase,
    private val getGamesForPinnedCollectionUseCase: GetGamesForPinnedCollectionUseCase,
    private val gameRepository: GameRepository,
    private val playStoreService: com.nendo.argosy.data.remote.playstore.PlayStoreService,
    private val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val ambientLedManager: AmbientLedManager,
    private val ambientAudioManager: AmbientAudioManager,
    private val imagePrefetchManager: com.nendo.argosy.ui.util.ImagePrefetchManager,
    private val emulatorDetector: EmulatorDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val rowGameIndexes = mutableMapOf<HomeRow, Int>()
    private var rowLoadJob: kotlinx.coroutines.Job? = null
    private val loadViewDebounceMs = 150L
    private var achievementPrefetchJob: kotlinx.coroutines.Job? = null
    private val achievementPrefetchDebounceMs = 300L
    private var backgroundPrefetchJob: kotlinx.coroutines.Job? = null

    private val recentGamesCache = AtomicReference(RecentGamesCache(null, 0L))
    private val _pinnedGamesLoading = MutableStateFlow<Set<Long>>(emptySet())
    private var cachedPlatformDisplayNames: Map<Long, String> = emptyMap()
    private var currentBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID

    init {
        modalResetSignal.signal.onEach {
            resetMenus()
        }.launchIn(viewModelScope)

        loadData()
        initializeRomM()
        observeBackgroundSettings()
        observeSyncOverlay()
        observePlatformChanges()
        observeAchievementUpdates()
        observePinnedCollections()
        observeRecentlyPlayedChanges()
        observeFocusedGameForLed()
        observeCollectionModal()
    }

    private fun resetMenus() {
        _uiState.update { it.copy(showGameMenu = false) }
    }

    private fun observeFocusedGameForLed() {
        viewModelScope.launch {
            var previousGameId: Long? = null
            _uiState.collect { state ->
                val focusedGame = state.focusedGame
                if (focusedGame != null && focusedGame.id != previousGameId) {
                    previousGameId = focusedGame.id
                    ambientLedManager.setContext(AmbientLedContext.GAME_HOVER)
                    val colors = gradientExtractionDelegate.getGradient(focusedGame.id)
                    if (colors != null) {
                        ambientLedManager.setHoverColors(colors.first, colors.second)
                    } else {
                        ambientLedManager.clearHoverColors()
                    }
                } else if (focusedGame == null && previousGameId != null) {
                    previousGameId = null
                    ambientLedManager.clearHoverColors()
                    ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
                }
            }
        }
    }

    private fun observeAchievementUpdates() {
        viewModelScope.launch {
            achievementUpdateBus.updates.collect { update ->
                updateAchievementCountsInState(update.gameId, update.totalCount, update.earnedCount)
            }
        }
    }

    private fun observePinnedCollections() {
        viewModelScope.launch {
            getPinnedCollectionsUseCase().collect { pinnedList ->
                val allPinIds = pinnedList.map { it.id }.toSet()
                _pinnedGamesLoading.value = allPinIds
                _uiState.update { it.copy(pinnedCollections = pinnedList, pinnedGamesLoading = allPinIds) }

                pinnedList.forEach { pinned ->
                    launch { prefetchGamesForPinnedCollection(pinned) }
                }
            }
        }
    }

    private fun observeRecentlyPlayedChanges() {
        viewModelScope.launch {
            gameRepository.awaitStorageReady()
            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            gameDao.observeRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL).collect { recentlyPlayed ->
                val newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

                val playableGames = allCandidates.filter { game ->
                    when {
                        game.source == GameSource.STEAM -> true
                        game.source == GameSource.ANDROID_APP -> true
                        game.localPath != null -> File(game.localPath).exists()
                        else -> false
                    }
                }

                val sorted = sortRecentGamesWithNewPriority(playableGames)
                val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi(cachedPlatformDisplayNames) }

                recentGamesCache.set(RecentGamesCache(validated, recentGamesCache.get().version))

                _uiState.update { state ->
                    val newState = state.copy(recentGames = validated)
                    if (state.currentRow == HomeRow.Continue && validated.isEmpty()) {
                        val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                        newState.copy(currentRow = newRow, focusedGameIndex = 0)
                    } else {
                        newState
                    }
                }
            }
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

    private suspend fun prefetchGamesForPinnedCollection(pinned: PinnedCollection) {
        val games = getGamesForPinnedCollectionUseCase(pinned).first()
        val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
        _uiState.update { state ->
            state.copy(
                pinnedGames = state.pinnedGames + (pinned.id to gameUis),
                pinnedGamesLoading = state.pinnedGamesLoading - pinned.id
            )
        }
        _pinnedGamesLoading.update { it - pinned.id }
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

    fun selectDisc(discPath: String) {
        gameLaunchDelegate.selectDisc(viewModelScope, discPath)
    }

    fun dismissDiscPicker() {
        gameLaunchDelegate.dismissDiscPicker()
    }

    fun setDiscPickerFocusIndex(index: Int) {
        _uiState.update { it.copy(discPickerFocusIndex = index) }
    }

    private fun observeBackgroundSettings() {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                currentBorderStyle = prefs.boxArtBorderStyle
                gradientExtractionDelegate.updatePreferences(prefs.gradientPreset, prefs.boxArtBorderStyle)

                val wasMuted = _uiState.value.muteVideoPreview
                val nowMuted = prefs.videoWallpaperMuted

                _uiState.update {
                    it.copy(
                        backgroundBlur = prefs.backgroundBlur,
                        backgroundSaturation = prefs.backgroundSaturation,
                        backgroundOpacity = prefs.backgroundOpacity,
                        useGameBackground = prefs.useGameBackground,
                        customBackgroundPath = prefs.customBackgroundPath,
                        muteVideoPreview = nowMuted,
                        videoWallpaperEnabled = prefs.videoWallpaperEnabled,
                        videoWallpaperDelayMs = prefs.videoWallpaperDelaySeconds * 1000L
                    )
                }

                if (_uiState.value.isVideoPreviewActive && wasMuted != nowMuted) {
                    if (nowMuted) {
                        ambientAudioManager.fadeIn()
                    } else {
                        ambientAudioManager.fadeOut()
                    }
                }

                extractGradientsForVisibleGames(_uiState.value.focusedGameIndex)
            }
        }
    }

    private fun observePlatformChanges() {
        viewModelScope.launch {
            platformDao.observePlatformsWithGames().collect { platforms ->
                cachedPlatformDisplayNames = platforms.associate { it.id to it.getDisplayName() }
                val currentPlatforms = _uiState.value.platforms
                val newPlatformUis = platforms.map { it.toUi() }

                if (currentPlatforms.isEmpty() && newPlatformUis.isNotEmpty()) {
                    _uiState.update { state ->
                        val newRow = when {
                            state.recentGames.isNotEmpty() -> state.currentRow
                            state.favoriteGames.isNotEmpty() -> state.currentRow
                            else -> HomeRow.Platform(0)
                        }
                        state.copy(
                            platforms = newPlatformUis,
                            currentRow = newRow,
                            isLoading = false
                        )
                    }
                    if (_uiState.value.currentRow is HomeRow.Platform) {
                        val platform = platforms.firstOrNull()
                        if (platform != null) {
                            loadGamesForPlatformInternal(platform.id, 0)
                        }
                    }
                } else if (newPlatformUis != currentPlatforms) {
                    val currentIds = currentPlatforms.map { it.id }.toSet()
                    val newIds = newPlatformUis.map { it.id }.toSet()
                    val platformsChanged = currentIds != newIds

                    if (platformsChanged) {
                        rowGameIndexes.clear()
                        val state = _uiState.value
                        val newRow = run {
                            val row = state.currentRow
                            if (row is HomeRow.Platform) {
                                val currentPlatformId = currentPlatforms.getOrNull(row.index)?.id
                                val newIndex = currentPlatformId?.let { id ->
                                    newPlatformUis.indexOfFirst { it.id == id }
                                }?.takeIf { it >= 0 }

                                when {
                                    newIndex != null -> HomeRow.Platform(newIndex)
                                    newPlatformUis.isNotEmpty() -> HomeRow.Platform(0)
                                    else -> state.availableRows.firstOrNull() ?: HomeRow.Continue
                                }
                            } else {
                                row
                            }
                        }
                        _uiState.update {
                            it.copy(
                                platforms = newPlatformUis,
                                currentRow = newRow,
                                focusedGameIndex = 0
                            )
                        }
                        if (newRow is HomeRow.Platform) {
                            val platform = newPlatformUis.getOrNull(newRow.index)
                            if (platform != null) {
                                loadGamesForPlatformInternal(platform.id, newRow.index)
                            }
                        }
                    } else {
                        _uiState.update { it.copy(platforms = newPlatformUis) }
                    }
                }
            }
        }
    }

    private fun extractGradientsForVisibleGames(focusedIndex: Int) {
        val state = _uiState.value
        val games = state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game }
        if (games.isEmpty()) return

        val requests = games.map { GameGradientRequest(it.id, it.coverPath) }
        gradientExtractionDelegate.extractForVisibleGames(viewModelScope, requests, focusedIndex)
    }

    fun extractGradientForGame(gameId: Long, coverPath: String) {
        val isFocusedGame = _uiState.value.focusedGame?.id == gameId
        gradientExtractionDelegate.extractForGame(viewModelScope, gameId, coverPath, prioritize = isFocusedGame)
    }

    private fun restoreInitialState(): HomeUiState {
        val rowType = savedStateHandle.get<String>(KEY_ROW_TYPE)
        val platformIndex = savedStateHandle.get<Int>(KEY_PLATFORM_INDEX) ?: 0
        val gameIndex = savedStateHandle.get<Int>(KEY_GAME_INDEX) ?: 0

        val currentRow = when (rowType) {
            ROW_TYPE_FAVORITES -> HomeRow.Favorites
            ROW_TYPE_PLATFORM -> HomeRow.Platform(platformIndex)
            ROW_TYPE_CONTINUE -> HomeRow.Continue
            ROW_TYPE_RECOMMENDATIONS -> HomeRow.Recommendations
            ROW_TYPE_ANDROID -> HomeRow.Android
            ROW_TYPE_STEAM -> HomeRow.Steam
            ROW_TYPE_PINNED_REGULAR, ROW_TYPE_PINNED_VIRTUAL -> HomeRow.Continue
            else -> HomeRow.Continue
        }

        return HomeUiState(currentRow = currentRow, focusedGameIndex = gameIndex)
    }

    private fun saveCurrentState() {
        val state = _uiState.value
        val (rowType, platformIndex) = when (val row = state.currentRow) {
            HomeRow.Favorites -> ROW_TYPE_FAVORITES to 0
            is HomeRow.Platform -> ROW_TYPE_PLATFORM to row.index
            HomeRow.Continue -> ROW_TYPE_CONTINUE to 0
            HomeRow.Recommendations -> ROW_TYPE_RECOMMENDATIONS to 0
            HomeRow.Android -> ROW_TYPE_ANDROID to 0
            HomeRow.Steam -> ROW_TYPE_STEAM to 0
            is HomeRow.PinnedRegular -> {
                savedStateHandle[KEY_PINNED_COLLECTION_ID] = row.collectionId
                ROW_TYPE_PINNED_REGULAR to 0
            }
            is HomeRow.PinnedVirtual -> {
                savedStateHandle[KEY_PINNED_TYPE] = row.type.name
                savedStateHandle[KEY_PINNED_NAME] = row.name
                ROW_TYPE_PINNED_VIRTUAL to 0
            }
        }
        savedStateHandle[KEY_ROW_TYPE] = rowType
        savedStateHandle[KEY_PLATFORM_INDEX] = platformIndex
        savedStateHandle[KEY_GAME_INDEX] = state.focusedGameIndex
    }

    private fun initializeRomM() {
        viewModelScope.launch {
            romMRepository.initialize()

            val isConfigured = romMRepository.isConnected()
            _uiState.update { it.copy(isRommConfigured = isConfigured) }

            if (isConfigured) {
                val prefs = preferencesRepository.preferences.first()
                val lastSync = prefs.lastRommSync
                val oneWeekAgo = Instant.now().minus(AUTO_SYNC_DAYS, ChronoUnit.DAYS)

                if (lastSync == null || lastSync.isBefore(oneWeekAgo)) {
                    syncFromRomm()
                } else {
                    romMRepository.refreshFavoritesIfNeeded()
                    loadFavorites()
                }
            }
        }
    }

    private suspend fun discoverGamesIfNeeded(games: List<com.nendo.argosy.data.local.entity.GameEntity>): Boolean {
        val gamesNeedingDiscovery = games.filter { game ->
            game.source != GameSource.STEAM &&
            game.source != GameSource.ANDROID_APP &&
            game.rommId != null &&
            (game.localPath == null || !File(game.localPath).exists())
        }
        if (gamesNeedingDiscovery.isEmpty()) return false
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            gamesNeedingDiscovery.take(20).forEach { game ->
                gameRepository.validateAndDiscoverGame(game.id)
            }
        }
        return true
    }

    private fun loadData() {
        viewModelScope.launch {
            gameRepository.awaitStorageReady()
            val allPlatforms = platformDao.getPlatformsWithGames()
            val platforms = allPlatforms.filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
            cachedPlatformDisplayNames = allPlatforms.associate { it.id to it.getDisplayName() }
            var favorites = gameDao.getFavorites()
            val androidGames = gameDao.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
            val steamGames = gameDao.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)

            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            var recentlyPlayed = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            var newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
            var allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

            // Run discovery for displayed games
            val allDisplayed = (favorites + allCandidates).distinctBy { it.id }
            if (discoverGamesIfNeeded(allDisplayed)) {
                favorites = gameDao.getFavorites()
                recentlyPlayed = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }
            }

            val playableGames = allCandidates.filter { game ->
                when {
                    game.source == GameSource.STEAM -> true
                    game.source == GameSource.ANDROID_APP -> true
                    game.localPath != null -> File(game.localPath).exists()
                    else -> false
                }
            }
            val sortedRecent = sortRecentGamesWithNewPriority(playableGames)
            val validatedRecent = sortedRecent.take(RECENT_GAMES_LIMIT).map { it.toUi(cachedPlatformDisplayNames) }
            recentGamesCache.set(RecentGamesCache(validatedRecent, recentGamesCache.get().version))

            val platformUis = platforms.map { it.toUi() }
            val favoriteUis = favorites.map { it.toUi(cachedPlatformDisplayNames) }
            val androidGameUis = androidGames.map { it.toUi(cachedPlatformDisplayNames) }
            val steamGameUis = steamGames.map { it.toUi(cachedPlatformDisplayNames) }

            val startRow = when {
                validatedRecent.isNotEmpty() -> HomeRow.Continue
                favoriteUis.isNotEmpty() -> HomeRow.Favorites
                androidGameUis.isNotEmpty() -> HomeRow.Android
                steamGameUis.isNotEmpty() -> HomeRow.Steam
                platformUis.isNotEmpty() -> HomeRow.Platform(0)
                else -> HomeRow.Continue
            }

            _uiState.update { state ->
                state.copy(
                    platforms = platformUis,
                    recentGames = validatedRecent,
                    favoriteGames = favoriteUis,
                    androidGames = androidGameUis,
                    steamGames = steamGameUis,
                    currentRow = startRow,
                    isLoading = false
                )
            }

            if (startRow is HomeRow.Platform) {
                val platform = platforms.getOrNull(startRow.index)
                if (platform != null) {
                    loadGamesForPlatform(platform.id, startRow.index)
                }
            }

            loadRecommendations()
            launch { observeDownloadState() }
            extractGradientsForVisibleGames(0)
        }
    }

    private val completedGameIds = mutableSetOf<Long>()

    private suspend fun observeDownloadState() {
        downloadManager.state.collect { downloadState ->
            val indicators = mutableMapOf<Long, GameDownloadIndicator>()

            downloadState.activeDownloads.forEach { download ->
                val isExtracting = download.state == DownloadState.EXTRACTING
                indicators[download.gameId] = GameDownloadIndicator(
                    isDownloading = !isExtracting,
                    isExtracting = isExtracting,
                    progress = if (isExtracting) download.extractionPercent else download.progressPercent
                )
            }

            downloadState.queue.forEach { download ->
                val isExtracting = download.state == DownloadState.EXTRACTING
                val isPaused = download.state == DownloadState.PAUSED
                val isQueued = download.state == DownloadState.QUEUED
                if (isExtracting || isPaused || isQueued) {
                    indicators[download.gameId] = GameDownloadIndicator(
                        isDownloading = false,
                        isExtracting = isExtracting,
                        isPaused = isPaused,
                        isQueued = isQueued,
                        progress = if (isExtracting) download.extractionPercent else download.progressPercent
                    )
                }
            }

            val newlyCompleted = downloadState.completed
                .map { it.gameId }
                .filter { it !in completedGameIds }

            if (newlyCompleted.isNotEmpty()) {
                completedGameIds.addAll(newlyCompleted)
                invalidateRecentGamesCache()
                refreshCurrentRowInternal()
            }

            _uiState.update { it.copy(downloadIndicators = indicators) }
        }
    }

    private suspend fun loadRecentGames() {
        val currentCache = recentGamesCache.get()
        val startVersion = currentCache.version

        val gameUis = if (currentCache.games != null) {
            currentCache.games
        } else {
            val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
            var recentlyPlayed = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            var newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
            var allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

            // Run discovery for displayed games
            if (discoverGamesIfNeeded(allCandidates)) {
                recentlyPlayed = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }
            }

            val playableGames = allCandidates.filter { game ->
                when {
                    game.source == GameSource.STEAM -> true
                    game.source == GameSource.ANDROID_APP -> true
                    game.localPath != null -> File(game.localPath).exists()
                    else -> false
                }
            }
            val sorted = sortRecentGamesWithNewPriority(playableGames)
            val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi(cachedPlatformDisplayNames) }

            recentGamesCache.compareAndSet(
                RecentGamesCache(null, startVersion),
                RecentGamesCache(validated, startVersion)
            )
            validated
        }

        _uiState.update { state ->
            val newState = state.copy(recentGames = gameUis)
            if (state.currentRow == HomeRow.Continue && gameUis.isEmpty()) {
                val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                newState.copy(currentRow = newRow, focusedGameIndex = 0)
            } else {
                newState
            }
        }
    }

    fun refreshRecentGames() {
        viewModelScope.launch {
            loadRecentGames()
        }
    }

    private suspend fun loadFavorites() {
        var games = gameDao.getFavorites()
        if (discoverGamesIfNeeded(games)) {
            games = gameDao.getFavorites()
        }
        val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
        _uiState.update { state ->
            val newState = state.copy(favoriteGames = gameUis)
            if (state.currentRow == HomeRow.Favorites && gameUis.isEmpty()) {
                val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                newState.copy(currentRow = newRow, focusedGameIndex = 0)
            } else {
                newState
            }
        }
    }

    fun refreshFavorites() {
        viewModelScope.launch {
            loadFavorites()
        }
    }

    private suspend fun loadRecommendations() {
        val prefs = preferencesRepository.preferences.first()
        val storedIds = prefs.recommendedGameIds

        if (storedIds.isNotEmpty()) {
            val games = gameDao.getByIds(storedIds)
            val orderedGames = storedIds.mapNotNull { id -> games.find { it.id == id } }

            val displayedGames = orderedGames
                .filter { it.status !in EXCLUDED_RECOMMENDATION_STATUSES }
                .take(MAX_DISPLAYED_RECOMMENDATIONS)

            applyPenaltiesToDisplayed(displayedGames.map { it.id })

            val gameUis = displayedGames.map { it.toUi(cachedPlatformDisplayNames) }
            _uiState.update { it.copy(recommendedGames = gameUis) }
        }
    }

    private suspend fun applyPenaltiesToDisplayed(displayedIds: List<Long>) {
        val prefs = preferencesRepository.preferences.first()
        val penalties = prefs.recommendationPenalties.toMutableMap()
        var updated = false

        for (id in displayedIds) {
            val current = penalties[id] ?: 0f
            if (current < RECOMMENDATION_PENALTY) {
                penalties[id] = RECOMMENDATION_PENALTY
                updated = true
            }
        }

        if (updated) {
            val weekKey = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
                .toString()
            preferencesRepository.setRecommendationPenalties(penalties, weekKey)
        }
    }

    fun regenerateRecommendations() {
        viewModelScope.launch {
            val ids = generateRecommendationsUseCase(forceRegenerate = true)
            if (ids.isNotEmpty()) {
                val games = gameDao.getByIds(ids)
                val orderedGames = ids.mapNotNull { id -> games.find { it.id == id } }

                val displayedGames = orderedGames
                    .filter { it.status !in EXCLUDED_RECOMMENDATION_STATUSES }
                    .take(MAX_DISPLAYED_RECOMMENDATIONS)

                applyPenaltiesToDisplayed(displayedGames.map { it.id })

                val gameUis = displayedGames.map { it.toUi(cachedPlatformDisplayNames) }
                _uiState.update { it.copy(recommendedGames = gameUis) }
                notificationManager.showSuccess("Recommendations updated")
            } else {
                _uiState.update { it.copy(recommendedGames = emptyList()) }
                notificationManager.showError("Not enough games to generate recommendations")
            }
        }
    }

    private suspend fun loadPlatforms() {
        val allPlatforms = platformDao.getPlatformsWithGames()
        val platforms = allPlatforms.filter { it.id != LocalPlatformIds.STEAM && it.id != LocalPlatformIds.ANDROID }
        cachedPlatformDisplayNames = allPlatforms.associate { it.id to it.getDisplayName() }
        val platformUis = platforms.map { it.toUi() }
        val androidGames = gameDao.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
        val androidGameUis = androidGames.map { it.toUi(cachedPlatformDisplayNames) }
        val steamGames = gameDao.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)
        val steamGameUis = steamGames.map { it.toUi(cachedPlatformDisplayNames) }
        _uiState.update { state ->
            val shouldSwitchRow = platforms.isNotEmpty() &&
                state.currentRow == HomeRow.Continue &&
                state.recentGames.isEmpty() &&
                state.androidGames.isEmpty() &&
                state.steamGames.isEmpty()
            state.copy(
                platforms = platformUis,
                androidGames = androidGameUis,
                steamGames = steamGameUis,
                currentRow = if (shouldSwitchRow) HomeRow.Platform(0) else state.currentRow,
                isLoading = false
            )
        }
    }

    fun refreshPlatforms() {
        viewModelScope.launch {
            loadPlatforms()
        }
    }

    fun onResume() {
        // Fallback session end handling in case Android killed Argosy while emulator was running
        // (normal flow goes through LaunchScreen, but if app was killed, user returns here directly)
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
        invalidateRecentGamesCache()

        viewModelScope.launch {
            refreshCurrentRowInternal()
        }

        if (romMRepository.isConnected()) {
            viewModelScope.launch {
                romMRepository.refreshFavoritesIfNeeded()
                loadFavorites()
            }
        }

        viewModelScope.launch {
            refreshRecommendationsIfNeeded()
            checkForChangelog()
        }
    }

    private suspend fun checkForChangelog() {
        val prefs = preferencesRepository.preferences.first()
        val lastSeenVersion = prefs.lastSeenVersion
        val currentVersion = BuildConfig.VERSION_NAME

        if (lastSeenVersion == null) {
            preferencesRepository.setLastSeenVersion(currentVersion)
            return
        }

        if (lastSeenVersion != currentVersion) {
            val entry = Changelog.getEntry(currentVersion)
            if (entry != null) {
                _uiState.update { it.copy(changelogEntry = entry) }
            } else {
                preferencesRepository.setLastSeenVersion(currentVersion)
            }
        }
    }

    fun dismissChangelog() {
        viewModelScope.launch {
            preferencesRepository.setLastSeenVersion(BuildConfig.VERSION_NAME)
            _uiState.update { it.copy(changelogEntry = null) }
        }
    }

    fun handleChangelogAction(action: RequiredAction): RequiredAction {
        viewModelScope.launch {
            preferencesRepository.setLastSeenVersion(BuildConfig.VERSION_NAME)
            _uiState.update { it.copy(changelogEntry = null) }
        }
        return action
    }

    fun startVideoPreviewLoading(videoId: String) {
        _uiState.update {
            it.copy(
                isVideoPreviewLoading = true,
                videoPreviewId = videoId
            )
        }
    }

    fun activateVideoPreview() {
        _uiState.update {
            it.copy(
                isVideoPreviewActive = true,
                isVideoPreviewLoading = false
            )
        }
        if (!_uiState.value.muteVideoPreview) {
            ambientAudioManager.fadeOut()
        }
    }

    fun cancelVideoPreviewLoading() {
        _uiState.update {
            it.copy(
                isVideoPreviewLoading = false,
                videoPreviewId = null
            )
        }
        ambientAudioManager.fadeIn()
    }

    fun deactivateVideoPreview() {
        val wasActive = _uiState.value.isVideoPreviewActive || _uiState.value.isVideoPreviewLoading
        _uiState.update {
            it.copy(
                isVideoPreviewActive = false,
                isVideoPreviewLoading = false,
                videoPreviewId = null
            )
        }
        if (wasActive) {
            ambientAudioManager.fadeIn()
        }
    }

    private suspend fun refreshRecommendationsIfNeeded() {
        val prefs = preferencesRepository.preferences.first()
        val lastGen = prefs.lastRecommendationGeneration

        val shouldGenerate = if (lastGen == null) {
            true
        } else {
            val lastGenWeek = lastGen.atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY))

            val currentWeek = java.time.LocalDate.now()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY))

            currentWeek.isAfter(lastGenWeek)
        }

        if (shouldGenerate) {
            val ids = generateRecommendationsUseCase()
            if (ids.isNotEmpty()) {
                val games = gameDao.getByIds(ids)
                val orderedGames = ids.mapNotNull { id -> games.find { it.id == id } }
                _uiState.update { it.copy(recommendedGames = orderedGames.map { g -> g.toUi(cachedPlatformDisplayNames) }) }
            }
        }
    }

    private fun invalidateRecentGamesCache() {
        recentGamesCache.updateAndGet { RecentGamesCache(null, it.version + 1) }
    }

    private fun loadGamesForPlatform(platformId: Long, platformIndex: Int) {
        viewModelScope.launch {
            loadGamesForPlatformInternal(platformId, platformIndex)
        }
    }

    private suspend fun loadGamesForPlatformInternal(platformId: Long, platformIndex: Int) {
        var games = gameDao.getByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT)
        if (discoverGamesIfNeeded(games)) {
            games = gameDao.getByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT)
        }
        val platform = _uiState.value.platforms.getOrNull(platformIndex)
        val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi(cachedPlatformDisplayNames)) }
        val items: List<HomeRowItem> = if (platform != null) {
            gameItems + HomeRowItem.ViewAll(
                platformId = platform.id,
                platformName = platform.name,
                logoPath = platform.logoPath
            )
        } else {
            gameItems
        }

        _uiState.update { state ->
            state.copy(platformItems = items)
        }
    }

    private suspend fun loadGamesForPinnedCollection(pinId: Long) {
        val pinned = _uiState.value.pinnedCollections.find { it.id == pinId } ?: return
        var games = getGamesForPinnedCollectionUseCase(pinned).first()
        if (discoverGamesIfNeeded(games)) {
            games = getGamesForPinnedCollectionUseCase(pinned).first()
        }
        val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
        _uiState.update { state ->
            state.copy(
                pinnedGames = state.pinnedGames + (pinId to gameUis),
                pinnedGamesLoading = state.pinnedGamesLoading - pinId
            )
        }
    }

    fun nextRow() {
        val state = _uiState.value
        val rows = state.availableRows
        if (rows.isEmpty()) return

        rowGameIndexes[state.currentRow] = state.focusedGameIndex

        val currentIdx = rows.indexOf(state.currentRow)
        val nextIdx = if (currentIdx >= rows.lastIndex) 0 else currentIdx + 1
        val nextRow = rows[nextIdx]
        val savedIndex = rowGameIndexes[nextRow] ?: 0

        _uiState.update { it.copy(currentRow = nextRow, focusedGameIndex = savedIndex) }
        loadRowWithDebounce(nextRow)
        saveCurrentState()
    }

    fun previousRow() {
        val state = _uiState.value
        val rows = state.availableRows
        if (rows.isEmpty()) return

        rowGameIndexes[state.currentRow] = state.focusedGameIndex

        val currentIdx = rows.indexOf(state.currentRow)
        val prevIdx = if (currentIdx <= 0) rows.lastIndex else currentIdx - 1
        val prevRow = rows[prevIdx]
        val savedIndex = rowGameIndexes[prevRow] ?: 0

        _uiState.update { it.copy(currentRow = prevRow, focusedGameIndex = savedIndex) }
        loadRowWithDebounce(prevRow)
        saveCurrentState()
    }

    private fun loadRowWithDebounce(row: HomeRow) {
        rowLoadJob?.cancel()
        rowLoadJob = viewModelScope.launch {
            delay(loadViewDebounceMs)
            when (row) {
                is HomeRow.Platform -> {
                    val platform = _uiState.value.platforms.getOrNull(row.index)
                    if (platform != null) {
                        loadGamesForPlatform(platform.id, row.index)
                    }
                }
                HomeRow.Continue -> loadRecentGames()
                HomeRow.Favorites -> loadFavorites()
                HomeRow.Recommendations -> loadRecommendations()
                HomeRow.Android -> { }
                HomeRow.Steam -> { }
                is HomeRow.PinnedRegular -> loadGamesForPinnedCollection(row.pinId)
                is HomeRow.PinnedVirtual -> loadGamesForPinnedCollection(row.pinId)
            }
            extractGradientsForVisibleGames(_uiState.value.focusedGameIndex)
        }
    }

    fun nextGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex >= state.currentItems.size - 1) return false
        _uiState.update {
            if (it.focusedGameIndex >= it.currentItems.size - 1) it
            else it.copy(focusedGameIndex = it.focusedGameIndex + 1)
        }
        saveCurrentState()
        prefetchAchievementsDebounced()
        prefetchAdjacentBackgrounds(_uiState.value.focusedGameIndex)
        extractGradientsForVisibleGames(_uiState.value.focusedGameIndex)
        return true
    }

    fun previousGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex <= 0) return false
        _uiState.update {
            if (it.focusedGameIndex <= 0) it
            else it.copy(focusedGameIndex = it.focusedGameIndex - 1)
        }
        saveCurrentState()
        prefetchAchievementsDebounced()
        prefetchAdjacentBackgrounds(_uiState.value.focusedGameIndex)
        extractGradientsForVisibleGames(_uiState.value.focusedGameIndex)
        return true
    }

    private fun prefetchAchievementsDebounced() {
        achievementPrefetchJob?.cancel()
        achievementPrefetchJob = viewModelScope.launch {
            delay(achievementPrefetchDebounceMs)
            prefetchAchievementsForFocusedGame()
        }
    }

    private fun prefetchAdjacentBackgrounds(focusedIndex: Int) {
        backgroundPrefetchJob?.cancel()
        backgroundPrefetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(200)
            val games = _uiState.value.currentItems.filterIsInstance<HomeRowItem.Game>()
            val paths = listOf(focusedIndex - 1, focusedIndex + 1, focusedIndex + 2)
                .filter { it in games.indices }
                .mapNotNull { games[it].game.backgroundPath }
            imagePrefetchManager.prefetchBackgrounds(paths)
        }
    }

    fun setFocusIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.currentItems.size) return
        if (index == state.focusedGameIndex) return
        _uiState.update { it.copy(focusedGameIndex = index) }
        soundManager.play(SoundType.NAVIGATE)
        saveCurrentState()
        prefetchAchievementsDebounced()
        prefetchAdjacentBackgrounds(index)
        extractGradientsForVisibleGames(index)
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleItemTap(index: Int, _onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        if (index < 0 || index >= state.currentItems.size) return

        if (index != state.focusedGameIndex) {
            setFocusIndex(index)
            return
        }

        when (val item = state.currentItems[index]) {
            is HomeRowItem.Game -> {
                val game = item.game
                val indicator = state.downloadIndicatorFor(game.id)
                when {
                    game.needsInstall -> installApk(game.id)
                    game.isDownloaded -> launchGame(game.id)
                    indicator.isPaused || indicator.isQueued -> downloadManager.resumeDownload(game.id)
                    else -> queueDownload(game.id)
                }
            }
            is HomeRowItem.ViewAll -> {
                navigateToLibrary(item.platformId, item.sourceFilter)
            }
        }
    }

    fun handleItemLongPress(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.currentItems.size) return

        val item = state.currentItems[index]
        if (item !is HomeRowItem.Game) return

        if (index != state.focusedGameIndex) {
            _uiState.update { it.copy(focusedGameIndex = index) }
            saveCurrentState()
        }
        toggleGameMenu()
    }

    private fun scrollToFirstItem(): Boolean {
        val state = _uiState.value

        if (state.focusedGameIndex == 0) {
            return false
        }

        _uiState.update { it.copy(focusedGameIndex = 0) }
        return true
    }

    private fun navigateToContinuePlaying(): Boolean {
        val state = _uiState.value

        if (state.currentRow == HomeRow.Continue) {
            return false
        }

        if (state.recentGames.isEmpty()) {
            return false
        }

        rowGameIndexes[state.currentRow] = state.focusedGameIndex
        _uiState.update { it.copy(currentRow = HomeRow.Continue, focusedGameIndex = 0) }
        saveCurrentState()
        return true
    }

    fun toggleGameMenu() {
        val wasShowing = _uiState.value.showGameMenu
        _uiState.update {
            it.copy(showGameMenu = !it.showGameMenu, gameMenuFocusIndex = 0)
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun moveGameMenuFocus(delta: Int) {
        _uiState.update {
            val game = it.focusedGame
            val isDownloaded = game?.isDownloaded == true
            val needsInstall = game?.needsInstall == true
            val isRommGame = game?.isRommGame == true
            val isAndroidApp = game?.isAndroidApp == true
            var maxIndex = if (isDownloaded || needsInstall) MENU_INDEX_MAX_DOWNLOADED else MENU_INDEX_MAX_REMOTE
            if (isRommGame || isAndroidApp) maxIndex++ // Refresh Data
            if (isAndroidApp) maxIndex++ // Remove from Home
            val newIndex = (it.gameMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(gameMenuFocusIndex = newIndex)
        }
    }

    fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.focusedGame ?: return
        val index = state.gameMenuFocusIndex
        val isRommGame = game.isRommGame
        val isDownloaded = game.isDownloaded
        val needsInstall = game.needsInstall
        val isAndroidApp = game.isAndroidApp

        var currentIdx = 0
        val playIdx = currentIdx++
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val addToCollectionIdx = currentIdx++
        val refreshIdx = if (isRommGame || isAndroidApp) currentIdx++ else -1
        val deleteIdx = if (isDownloaded || needsInstall) currentIdx++ else -1
        val removeFromHomeIdx = if (isAndroidApp) currentIdx++ else -1
        val hideIdx = currentIdx

        when (index) {
            playIdx -> {
                toggleGameMenu()
                when {
                    needsInstall -> installApk(game.id)
                    isDownloaded -> launchGame(game.id)
                    else -> queueDownload(game.id)
                }
            }
            favoriteIdx -> toggleFavorite(game.id)
            detailsIdx -> {
                toggleGameMenu()
                gameNavigationContext.setContext(
                    state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
                )
                onGameSelect(game.id)
            }
            addToCollectionIdx -> {
                toggleGameMenu()
                showAddToCollectionModal(game.id)
            }
            refreshIdx -> {
                if (isAndroidApp) refreshAndroidGameData(game.id) else refreshGameData(game.id)
            }
            deleteIdx -> {
                toggleGameMenu()
                deleteLocalFile(game.id)
            }
            removeFromHomeIdx -> {
                toggleGameMenu()
                removeFromHome(game.id)
            }
            hideIdx -> {
                toggleGameMenu()
                hideGame(game.id)
            }
        }
    }

    fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            gameActions.toggleFavorite(gameId)
            refreshCurrentRowInternal()
        }
    }

    fun hideGame(gameId: Long) {
        viewModelScope.launch {
            gameActions.hideGame(gameId)
            refreshCurrentRowInternal()
        }
    }

    fun removeFromHome(gameId: Long) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId)
            if (game != null && game.source == GameSource.ANDROID_APP) {
                gameDao.delete(game)
                refreshCurrentRowInternal()
                soundManager.play(SoundType.UNFAVORITE)
            }
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

    fun refreshGameData(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.refreshGameData(gameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    refreshCurrentRowInternal()
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleGameMenu()
        }
    }

    fun refreshAndroidGameData(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameActions.refreshAndroidGameData(gameId)) {
                is RefreshAndroidResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    refreshCurrentRowInternal()
                }
                is RefreshAndroidResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            toggleGameMenu()
        }
    }

    fun deleteLocalFile(gameId: Long) {
        viewModelScope.launch {
            gameActions.deleteLocalFile(gameId)
            notificationManager.showSuccess("Download deleted")
            invalidateRecentGamesCache()
            refreshCurrentRowInternal()
        }
    }

    private fun uninstallAndroidApp(packageName: String?) {
        if (packageName == null) return
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        viewModelScope.launch {
            _events.emit(HomeEvent.LaunchIntent(intent))
        }
    }

    private suspend fun refreshCurrentRowInternal() {
        val state = _uiState.value
        val focusedGameId = state.focusedGame?.id

        when (val row = state.currentRow) {
            HomeRow.Favorites -> {
                val games = gameDao.getFavorites()
                val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
                val newIndex = if (focusedGameId != null) {
                    gameUis.indexOfFirst { it.id == focusedGameId }
                        .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(gameUis.lastIndex.coerceAtLeast(0))
                } else state.focusedGameIndex

                _uiState.update { s ->
                    val newState = s.copy(favoriteGames = gameUis, focusedGameIndex = newIndex)
                    if (s.currentRow == HomeRow.Favorites && gameUis.isEmpty()) {
                        val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                        newState.copy(currentRow = newRow, focusedGameIndex = 0)
                    } else newState
                }
            }
            HomeRow.Continue -> {
                invalidateRecentGamesCache()
                val currentCache = recentGamesCache.get()
                val startVersion = currentCache.version

                val newThreshold = Instant.now().minus(NEW_GAME_THRESHOLD_HOURS, ChronoUnit.HOURS)
                val recentlyPlayed = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                val newlyAdded = gameDao.getNewlyAddedPlayable(newThreshold, RECENT_GAMES_CANDIDATE_POOL)
                val allCandidates = (recentlyPlayed + newlyAdded).distinctBy { it.id }

                val playableGames = allCandidates.filter { game ->
                    when {
                        game.source == GameSource.STEAM -> true
                        game.source == GameSource.ANDROID_APP -> true
                        game.localPath != null -> File(game.localPath).exists()
                        else -> false
                    }
                }
                val sorted = sortRecentGamesWithNewPriority(playableGames)
                val validated = sorted.take(RECENT_GAMES_LIMIT).map { it.toUi(cachedPlatformDisplayNames) }

                recentGamesCache.compareAndSet(
                    RecentGamesCache(null, startVersion),
                    RecentGamesCache(validated, startVersion)
                )

                val newIndex = if (focusedGameId != null) {
                    validated.indexOfFirst { it.id == focusedGameId }
                        .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(validated.lastIndex.coerceAtLeast(0))
                } else state.focusedGameIndex

                _uiState.update { s ->
                    val newState = s.copy(recentGames = validated, focusedGameIndex = newIndex)
                    if (s.currentRow == HomeRow.Continue && validated.isEmpty()) {
                        val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                        newState.copy(currentRow = newRow, focusedGameIndex = 0)
                    } else newState
                }
            }
            is HomeRow.Platform -> {
                val platform = state.platforms.getOrNull(row.index) ?: return
                val games = gameDao.getByPlatformSorted(platform.id, limit = PLATFORM_GAMES_LIMIT)
                val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi(cachedPlatformDisplayNames)) }
                val items: List<HomeRowItem> = gameItems + HomeRowItem.ViewAll(
                    platformId = platform.id,
                    platformName = platform.name,
                    logoPath = platform.logoPath
                )

                val newIndex = if (focusedGameId != null) {
                    items.indexOfFirst { (it as? HomeRowItem.Game)?.game?.id == focusedGameId }
                        .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(items.lastIndex.coerceAtLeast(0))
                } else state.focusedGameIndex

                _uiState.update { s ->
                    s.copy(platformItems = items, focusedGameIndex = newIndex)
                }
            }
            HomeRow.Recommendations -> {
                loadRecommendations()
            }
            HomeRow.Android -> {
                val games = gameDao.getByPlatformSorted(LocalPlatformIds.ANDROID, limit = PLATFORM_GAMES_LIMIT)
                val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
                val newIndex = if (focusedGameId != null) {
                    gameUis.indexOfFirst { it.id == focusedGameId }
                        .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(gameUis.lastIndex.coerceAtLeast(0))
                } else state.focusedGameIndex

                _uiState.update { s ->
                    val newState = s.copy(androidGames = gameUis, focusedGameIndex = newIndex)
                    if (s.currentRow == HomeRow.Android && gameUis.isEmpty()) {
                        val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                        newState.copy(currentRow = newRow, focusedGameIndex = 0)
                    } else newState
                }
            }
            HomeRow.Steam -> {
                val games = gameDao.getByPlatformSorted(LocalPlatformIds.STEAM, limit = PLATFORM_GAMES_LIMIT)
                val gameUis = games.map { it.toUi(cachedPlatformDisplayNames) }
                val newIndex = if (focusedGameId != null) {
                    gameUis.indexOfFirst { it.id == focusedGameId }
                        .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(gameUis.lastIndex.coerceAtLeast(0))
                } else state.focusedGameIndex

                _uiState.update { s ->
                    val newState = s.copy(steamGames = gameUis, focusedGameIndex = newIndex)
                    if (s.currentRow == HomeRow.Steam && gameUis.isEmpty()) {
                        val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                        newState.copy(currentRow = newRow, focusedGameIndex = 0)
                    } else newState
                }
            }
            is HomeRow.PinnedRegular, is HomeRow.PinnedVirtual -> {
                val pinId = when (val r = row) {
                    is HomeRow.PinnedRegular -> r.pinId
                    is HomeRow.PinnedVirtual -> r.pinId
                    else -> return
                }
                loadGamesForPinnedCollection(pinId)
            }
        }
    }

    private var lastDownloadQueueTime = 0L
    private val downloadQueueDebounceMs = 300L

    fun queueDownload(gameId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastDownloadQueueTime < downloadQueueDebounceMs) return
        lastDownloadQueueTime = now

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

    private fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun launchGame(gameId: Long, channelName: String? = null) {
        deactivateVideoPreview()
        saveCurrentState()
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLaunch(gameId, channelName))
        }
    }

    private fun navigateToLibrary(platformId: Long? = null, sourceFilter: String? = null) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLibrary(platformId, sourceFilter))
        }
    }

    fun syncFromRomm() {
        viewModelScope.launch {
            when (val result = syncLibraryUseCase(initializeFirst = true)) {
                is SyncLibraryResult.Error -> notificationManager.showError(result.message)
                is SyncLibraryResult.Success -> refreshRecentGames()
            }
        }
    }

    fun showLaunchError(message: String) {
        notificationManager.showError(message)
    }

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

    private fun PlatformEntity.toUi() = HomePlatformUi(
        id = id,
        slug = slug,
        name = name,
        shortName = shortName,
        displayName = getDisplayName(),
        logoPath = logoPath,
        hasEmulator = emulatorDetector.hasAnyEmulator(slug)
    )

    private fun GameEntity.toUi(platformDisplayNames: Map<Long, String> = emptyMap()): HomeGameUi {
        val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
        val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
        val newThreshold = Instant.now().minus(24, ChronoUnit.HOURS)
        return HomeGameUi(
            id = id,
            title = title,
            platformId = platformId,
            platformSlug = platformSlug,
            platformDisplayName = platformDisplayNames[platformId] ?: platformSlug,
            coverPath = coverPath,
            gradientColors = gradientExtractionDelegate.getGradient(id),
            backgroundPath = effectiveBackground,
            developer = developer,
            releaseYear = releaseYear,
            genre = genre,
            isFavorite = isFavorite,
            isDownloaded = localPath != null || source == GameSource.STEAM || source == GameSource.ANDROID_APP,
            isRommGame = rommId != null,
            rating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            achievementCount = achievementCount,
            earnedAchievementCount = earnedAchievementCount,
            isAndroidApp = source == GameSource.ANDROID_APP || platformSlug == "android",
            packageName = packageName,
            needsInstall = platformSlug == "android" && localPath != null && packageName == null && source != GameSource.ANDROID_APP,
            youtubeVideoId = youtubeVideoId,
            isNew = addedAt.isAfter(newThreshold) && lastPlayed == null
        )
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
                state.showAddToCollectionModal -> {
                    moveCollectionFocusUp()
                    InputResult.HANDLED
                }
                state.showGameMenu -> {
                    moveGameMenuFocus(-1)
                    InputResult.HANDLED
                }
                else -> {
                    previousRow()
                    InputResult.handled(SoundType.SECTION_CHANGE)
                }
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                state.showAddToCollectionModal -> {
                    moveCollectionFocusDown()
                    InputResult.HANDLED
                }
                state.showGameMenu -> {
                    moveGameMenuFocus(1)
                    InputResult.HANDLED
                }
                else -> {
                    nextRow()
                    InputResult.handled(SoundType.SECTION_CHANGE)
                }
            }
        }

        override fun onLeft(): InputResult {
            if (_uiState.value.showAddToCollectionModal || _uiState.value.showGameMenu) return InputResult.HANDLED
            return if (previousGame()) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            if (_uiState.value.showAddToCollectionModal || _uiState.value.showGameMenu) return InputResult.HANDLED
            return if (nextGame()) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            when {
                state.showAddToCollectionModal -> {
                    confirmCollectionSelection()
                }
                state.showGameMenu -> {
                    confirmGameMenuSelection(onGameSelect)
                }
                else -> {
                    when (val item = state.focusedItem) {
                        is HomeRowItem.Game -> {
                            val game = item.game
                            val indicator = state.downloadIndicatorFor(game.id)
                            when {
                                game.needsInstall -> installApk(game.id)
                                game.isDownloaded -> launchGame(game.id)
                                indicator.isPaused || indicator.isQueued -> resumeDownload(game.id)
                                else -> queueDownload(game.id)
                            }
                        }
                        is HomeRowItem.ViewAll -> {
                            navigateToLibrary(item.platformId, item.sourceFilter)
                        }
                        null -> { }
                    }
                }
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            if (state.showAddToCollectionModal) {
                dismissAddToCollectionModal()
                return InputResult.HANDLED
            }
            if (state.showGameMenu) {
                toggleGameMenu()
                return InputResult.HANDLED
            }
            if (scrollToFirstItem()) {
                return InputResult.HANDLED
            }
            if (navigateToContinuePlaying()) {
                return InputResult.handled(SoundType.SECTION_CHANGE)
            }
            if (!isDefaultView) {
                onNavigateToDefault()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onMenu(): InputResult {
            val state = _uiState.value
            if (state.showAddToCollectionModal) {
                dismissAddToCollectionModal()
                return InputResult.UNHANDLED
            }
            if (state.showGameMenu) {
                toggleGameMenu()
                return InputResult.UNHANDLED
            }
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (_uiState.value.showAddToCollectionModal) return InputResult.HANDLED
            if (_uiState.value.focusedGame != null) {
                toggleGameMenu()
            }
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val game = _uiState.value.focusedGame ?: return InputResult.UNHANDLED
            toggleFavorite(game.id)
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            val game = state.focusedGame ?: return InputResult.UNHANDLED
            gameNavigationContext.setContext(
                state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
            )
            onGameSelect(game.id)
            return InputResult.HANDLED
        }
    }

    private fun prefetchAchievementsForFocusedGame() {
        val game = _uiState.value.focusedGame ?: return
        viewModelScope.launch {
            val entity = gameDao.getById(game.id) ?: return@launch
            val rommId = entity.rommId ?: return@launch
            val counts = fetchAchievementsUseCase(rommId, game.id) ?: return@launch
            updateAchievementCountsInState(game.id, counts.total, counts.earned)
        }
    }

    private fun updateAchievementCountsInState(gameId: Long, total: Int, earned: Int) {
        _uiState.update { state ->
            state.copy(
                recentGames = state.recentGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                favoriteGames = state.favoriteGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                recommendedGames = state.recommendedGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                androidGames = state.androidGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                steamGames = state.steamGames.map {
                    if (it.id == gameId) it.copy(achievementCount = total, earnedAchievementCount = earned) else it
                },
                platformItems = state.platformItems.map { item ->
                    when (item) {
                        is HomeRowItem.Game -> if (item.game.id == gameId) {
                            HomeRowItem.Game(item.game.copy(achievementCount = total, earnedAchievementCount = earned))
                        } else item
                        is HomeRowItem.ViewAll -> item
                    }
                }
            )
        }
    }
}
