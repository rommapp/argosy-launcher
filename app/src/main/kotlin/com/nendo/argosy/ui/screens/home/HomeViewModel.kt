package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.APP_BAR_DRAWER_INDEX
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.repository.CustomGridShapeStore
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.common.GridFocusNavigator
import com.nendo.argosy.ui.common.groundGameId
import com.nendo.argosy.ui.common.toGridStatus
import com.nendo.argosy.domain.model.FeatureTileContent
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.MediaTilePlayback
import com.nendo.argosy.ui.components.AutoGridMove
import com.nendo.argosy.ui.components.autoGridMove
import com.nendo.argosy.ui.screens.home.delegates.GameMenuAction
import com.nendo.argosy.ui.screens.home.delegates.HomeDownloadDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeGameMenuDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import com.nendo.argosy.ui.screens.home.delegates.HomeLibraryDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeMediaDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeNavigationDelegate
import com.nendo.argosy.domain.model.MediaPlayTarget
import com.nendo.argosy.ui.screens.home.delegates.HomeSyncDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeTilePickerDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeVideoPreviewDelegate
import com.nendo.argosy.ui.screens.home.delegates.PlatformChangeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The widgets tab's rows in this surface's words. The list itself is written once in
 * [com.nendo.argosy.ui.common.featureTilePickerEntries].
 */
private val HOME_FEATURE_TILE_PICKER_STRINGS = com.nendo.argosy.ui.common.FeatureTilePickerStrings(
    randomTitle = R.string.tile_picker_feature_random_title,
    randomSubtitle = R.string.tile_picker_feature_random_subtitle,
    continueTitle = R.string.tile_picker_feature_continue_title,
    continueSubtitle = R.string.tile_picker_feature_continue_subtitle,
    raTitle = R.string.tile_picker_feature_ra_title,
    raSubtitle = R.string.tile_picker_feature_ra_subtitle,
    libraryLinkTitle = R.string.tile_picker_feature_library_link_title,
    libraryLinkSubtitle = R.string.tile_picker_feature_library_link_subtitle
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    private val emulatorLaunchTargetResolver: com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver,
    private val appShortcutActions: com.nendo.argosy.ui.screens.common.AppShortcutActions,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val gameNavigationContext: GameNavigationContext,
    private val downloadManager: DownloadManager,
    private val soundManager: SoundFeedbackManager,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val modalResetSignal: ModalResetSignal,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val ambientLedManager: AmbientLedManager,
    val libraryDelegate: HomeLibraryDelegate,
    val navigationDelegate: HomeNavigationDelegate,
    val downloadDelegate: HomeDownloadDelegate,
    val syncDelegate: HomeSyncDelegate,
    val videoPreviewDelegate: HomeVideoPreviewDelegate,
    val gameMenuDelegate: HomeGameMenuDelegate,
    val mediaDelegate: HomeMediaDelegate,
    val tilePickerDelegate: HomeTilePickerDelegate,
    private val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager,
    private val steamDownloadPromptController: com.nendo.argosy.data.steam.SteamDownloadPromptController,
    private val appsRepository: com.nendo.argosy.data.repository.AppsRepository,
    private val homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository,
    private val customGridShapeStore: CustomGridShapeStore,
    private val homeGridPageRepository: com.nendo.argosy.data.repository.HomeGridPageRepository,
    private val raTileContentRepository: com.nendo.argosy.data.repository.RaTileContentRepository,
    private val pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource,
    private val collectionRepository: com.nendo.argosy.data.repository.CollectionRepository,
    private val advanceCollectionFocusUseCase:
        com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase,
    private val prepareCollectionQueueUseCase:
        com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase,
    private val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val socialRepository: com.nendo.argosy.data.social.SocialRepository,
    private val romMRepository: com.nendo.argosy.data.remote.romm.RomMRepository,
    private val siblingChoice: com.nendo.argosy.ui.screens.common.SiblingChoiceDelegate,
    private val showcaseSource: com.nendo.argosy.ui.common.PresentationShowcaseSource,
    private val quickNavigationSource: com.nendo.argosy.data.preferences.QuickNavigationSource,
    private val firstFrameCache: HomeFirstFrameCache
) : ViewModel(), HomeInputActions {

    val quickNavigationEnabled: StateFlow<Boolean> get() = quickNavigationSource.enabled

    override fun quickNavigation(): Boolean = quickNavigationSource.enabled.value

    val siblingChoiceState = siblingChoice.state

    private val logoAttempts = mutableSetOf<Long>()
    private var gameMenuOpenJob: Job? = null

    private val companionOwner = com.nendo.argosy.ui.dualscreen.SlotOwner.of("home", this)
    private val tileShowcaseOwner = com.nendo.argosy.ui.dualscreen.SlotOwner.of("home.tile", this)
    private var previousTileSlot: com.nendo.argosy.ui.dualscreen.PresentationSlot? = null
    private var isDescribing = false

    private val _uiState = MutableStateFlow(restoreInitialState())
    override val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Separate from [uiState] because download progress advances twice a second: folding it in
     * rebuilt every row and recomposed the whole screen for a game that may not even be on it.
     */
    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> =
        downloadDelegate.downloadIndicators

    /**
     * Out of [uiState] for the same reason as [downloadIndicators]: it advances twice a second,
     * and folding it in also re-ran the row and focus recalculation on every tick.
     */
    val mediaDownloadProgress: StateFlow<Map<String, com.nendo.argosy.data.repository.MediaTransferProgress>> =
        mediaDelegate.state
            .map { it.downloadProgress }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    private val customGrid = com.nendo.argosy.ui.home.grid.CustomGridCoordinator(
        context = context,
        scope = viewModelScope,
        repository = homeTileRepository,
        pageRepository = homeGridPageRepository,
        pageChooserEntries = { chooser -> pageChooserEntriesFor(chooser) },
        onAdvanceFocusGame = { collectionId, current -> advanceCollectionFocus(collectionId, current) },
        onPrepareQueue = { collectionId, active -> prepareCollectionQueueUseCase(collectionId, active) },
        onFirstQueueGame = { collectionId -> firstGameInCollection(collectionId) },
        ownerUserId = { syncPreferencesRepository.getRommUserId() },
        onPageAdded = { count -> persistCustomGridPageCount(count) },
        onPageRemoved = { count -> persistCustomGridPageRemoval(count) },
        pickerEntries = { category, query, libraryId ->
            when (category) {
                com.nendo.argosy.ui.components.TilePickerCategory.GAMES ->
                    libraryDelegate.searchInstalledForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.COLLECTIONS ->
                    libraryDelegate.collectionsForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.APPS ->
                    libraryDelegate.appsForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.MEDIA ->
                    mediaDelegate.searchForTiles(query, libraryId)
                com.nendo.argosy.ui.components.TilePickerCategory.FEATURES ->
                    featureTileEntries()
            }
        },
        mediaCatalog = tilePickerDelegate,
        featureFilterOptions = {
            com.nendo.argosy.ui.components.FeatureFilterOptions(
                platforms = libraryDelegate.platformOptionsForTiles(),
                genres = gameRepository.getDistinctGenres(oneEntryPerGroup = false),
                series = collectionRepository.getNamesWithGamesByType(CollectionType.SERIES)
            )
        },
        raGamePickerEntries = { query -> libraryDelegate.searchRaCompatibleForTiles(query) },
        read = { _uiState.value.customGrid },
        write = { transform -> _uiState.update { it.copy(customGrid = transform(it.customGrid)) } }
    )

    private var storedTiles: List<com.nendo.argosy.domain.model.HomeTile> = emptyList()
    private var storedTilesLoaded = false
    private var tileMediaShown: Boolean = false

    private var achievementPrefetchJob: Job? = null
    private val achievementPrefetchDebounceMs = 300L
    private val achievementRefetchThresholdMs = 5 * 60 * 1000L
    private var currentBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID
    private var lastShowsEveryGame: Boolean? = null

    init {
        modalResetSignal.signal.onEach {
            gameMenuOpenJob?.cancel()
            gameMenuDelegate.resetMenu()
            siblingChoice.reset()
        }.launchIn(viewModelScope)

        _uiState
            .map { HomeFocusSnapshot(it.currentRow, it.focusedGameIndex, it.customGrid.page, it.customGrid.cell) }
            .distinctUntilChanged()
            .onEach { firstFrameCache.focus = it }
            .launchIn(viewModelScope)

        combine(_uiState, gameMenuDelegate.state) { state, menu -> showsScreenNumbers(state, menu) }
            .distinctUntilChanged()
            .onEach { show ->
                val manager = DualScreenManagerHolder.instance ?: return@onEach
                if (show) {
                    manager.showScreenNumbers(com.nendo.argosy.hardware.DisplayBadgeSize.SMALL)
                } else {
                    manager.hideScreenNumbers()
                }
            }
            .launchIn(viewModelScope)

        loadData()
        syncDelegate.initializeRomM(
            viewModelScope,
            onSyncComplete = { refreshRecentGames() },
            onFavoritesRefreshed = { libraryDelegate.loadFavorites() }
        )
        observeBackgroundSettings()
        observeGradientChanges()
        observeSyncOverlay()
        observePlatformChanges()
        observeAchievementUpdates()
        libraryDelegate.observePinnedCollections(viewModelScope)
        observeRecentlyPlayedChanges()
        observeFocusedGame()
        observeSiblingPicks()
        observeCollectionModal()
        observeDelegateStates()
        observeHomeTiles()
        observeTilePrompts()
        customGrid.setLocalVideoSupported(true)
        mediaDelegate.observe(viewModelScope)
        gradientExtractionDelegate.startBackgroundProcessing(viewModelScope)
    }

    private fun observeDelegateStates() {
        viewModelScope.launch {
            libraryDelegate.state.collect { lib ->
                val gradients = gradientExtractionDelegate.gradients.value
                _uiState.update {
                    it.copy(
                        platforms = lib.platforms,
                        platformItems = lib.platformItems.applyRowGradients(gradients),
                        platformItemsFor = lib.platformItemsFor,
                        recentGames = lib.recentGames.applyGradients(gradients),
                        favoriteGames = lib.favoriteGames.applyGradients(gradients),
                        recommendedGames = lib.recommendedGames.applyGradients(gradients),
                        androidGames = lib.androidGames.applyGradients(gradients),
                        steamGames = lib.steamGames.applyGradients(gradients),
                        pinnedCollections = lib.pinnedCollections,
                        pinnedGames = lib.pinnedGames.mapValues { (_, games) ->
                            games.applyGradients(gradients)
                        },
                        pinnedGamesLoading = lib.pinnedGamesLoading,
                        repairedCoverPaths = lib.repairedCoverPaths
                    ).clampedToCompletePlatformRow(lib.platformItemsComplete)
                        .keepingFocusOn(it.focusedGame?.id)
                }
            }
        }
        viewModelScope.launch {
            syncDelegate.state.collect { sync ->
                _uiState.update {
                    it.copy(
                        isRommConfigured = sync.isRommConfigured,
                        changelogEntry = sync.changelogEntry
                    )
                }
            }
        }
        viewModelScope.launch {
            videoPreviewDelegate.state.collect { vp ->
                _uiState.update {
                    it.copy(
                        isVideoPreviewActive = vp.isVideoPreviewActive,
                        videoPreviewId = vp.videoPreviewId,
                        isVideoPreviewLoading = vp.isVideoPreviewLoading,
                        muteVideoPreview = vp.muteVideoPreview,
                        videoWallpaperEnabled = vp.videoWallpaperEnabled,
                        videoWallpaperDelayMs = vp.videoWallpaperDelayMs
                    )
                }
            }
        }
        viewModelScope.launch {
            gameMenuDelegate.state.collect { menu ->
                _uiState.update {
                    it.copy(
                        showGameMenu = menu.showGameMenu,
                        gameMenuFocusIndex = menu.gameMenuFocusIndex
                    )
                }
            }
        }
        viewModelScope.launch {
            mediaDelegate.state.collect { media ->
                _uiState.update { state ->
                    val updated = state.copy(
                        nextUpMedia = media.nextUp,
                        continueWatchingMedia = media.continueWatching,
                        favoriteMedia = media.favorites,
                        tileMedia = media.tileItems,
                        mediaLibraries = media.libraries,
                        mediaLibraryItems = media.libraryItems,
                        mediaLibraryItemsFor = media.libraryItemsFor,
                        mediaLibrariesLoaded = media.librariesLoaded,
                        isMediaSignedIn = media.isSignedIn,
                        isMediaLoading = media.isLoading,
                        showNextUpRow = media.showNextUp,
                        showContinueWatchingRow = media.showContinueWatching,
                        showMediaLibraryRows = media.showLibraries,
                        mediaResumePrompt = media.resumePrompt
                    )
                    if (updated.holdsCurrentRow && updated.isPlatformRowLoading) {
                        updated
                    } else if (updated.holdsCurrentRow) {
                        updated.copy(
                            focusedGameIndex = updated.focusedGameIndex
                                .coerceIn(0, (updated.currentItems.size - 1).coerceAtLeast(0))
                        )
                    } else {
                        updated.copy(
                            currentRow = updated.availableRows.firstOrNull() ?: HomeRow.Continue,
                            focusedGameIndex = 0
                        )
                    }
                }
                syncSelectedMediaLibrary()
                customGrid.setMediaAvailable(media.isSignedIn)
                applyTileMediaVisibility(media.isSignedIn)
            }
        }
    }

    /**
     * Keeps the curated grid's tiles in step with whether media exists here at all.
     *
     * Signing out takes the media tiles off the page rather than leaving them as dead squares: with
     * no account there is nothing behind one, and a permanent unavailable tile is exactly the orphan
     * a reader who does not use media should never meet. Nothing is deleted -- the rows stay stored
     * and the tiles come back on the next sign-in, taking whatever cells are still free.
     */
    private fun applyTileMediaVisibility(signedIn: Boolean) {
        if (signedIn == tileMediaShown) return
        tileMediaShown = signedIn
        if (!storedTilesLoaded) return
        viewModelScope.launch { publishHomeTiles(storedTiles) }
    }

    /**
     * Names the library the row under the cursor browses, so the delegate reads that one and no
     * other. Called wherever the row can change, including the moment the library listing first
     * arrives, since a row restored from saved state names a library nothing has looked up yet.
     */
    private fun syncSelectedMediaLibrary() {
        mediaDelegate.selectLibrary(_uiState.value.currentMediaLibrary?.libraryId)
    }

    private fun restoreInitialState(): HomeUiState {
        val carriedFocus = firstFrameCache.focus?.takeIf { !navigationDelegate.hasSavedRow(savedStateHandle) }
        val (savedRow, gameIndex) = carriedFocus?.let { it.row to it.gameIndex }
            ?: navigationDelegate.restoreInitialRow(savedStateHandle)
        val preloaded = libraryDelegate.initialLoadComplete
        val effectiveRow = if (preloaded && savedRow == HomeRow.Continue && carriedFocus == null) {
            libraryDelegate.cachedStartRow
        } else {
            savedRow
        }
        val base = HomeUiState(
            currentRow = effectiveRow,
            focusedGameIndex = gameIndex,
            isLoading = !preloaded
        )
        val prefs = preferencesRepository.latest ?: return base
        val grid = prefs.homeLayout.customGrid
        val seeded = base.withLayoutFrom(prefs).copy(
            customGrid = base.customGrid.copy(
                autoFit = grid.autoFit,
                storedPages = grid.pageCount,
                scrollAxis = grid.scrollAxis,
                pageSettings = firstFrameCache.pageSettings ?: base.customGrid.pageSettings
            )
        )
        val tiles = firstFrameCache.tiles?.takeIf { it.gridKind == grid.gridKind } ?: return seeded
        return seeded.copy(
            customGrid = seeded.customGrid.copy(
                tiles = tiles.tiles,
                raTile = tiles.raTile,
                page = carriedFocus?.gridPage ?: seeded.customGrid.page,
                cell = carriedFocus?.gridCell ?: seeded.customGrid.cell
            ),
            tileGames = tiles.tileGames,
            tileCollections = tiles.tileCollections,
            tileApps = tiles.tileApps,
            tileLibraryLinks = tiles.tileLibraryLinks,
            tileShowcases = tiles.tileShowcases,
            continueGameId = tiles.continueGameId,
            raTileSummary = tiles.raTileSummary
        )
    }

    private fun HomeUiState.withLayoutFrom(prefs: UserPreferences): HomeUiState = copy(
        backgroundBlur = prefs.backgroundBlur,
        backgroundSaturation = prefs.backgroundSaturation,
        backgroundOpacity = prefs.backgroundOpacity,
        useGameBackground = prefs.useGameBackground,
        customBackgroundPath = prefs.customBackgroundPath,
        homeBackgroundMode = prefs.homeBackgroundMode,
        carouselConfig = prefs.homeLayout.carousel,
        autoGridConfig = prefs.homeLayout.autoGrid,
        boxArt3d = prefs.homeLayout.boxArt3d,
        customGridConfig = prefs.homeLayout.customGrid,
        layoutKind = prefs.homeLayout.selected,
        homeApps = prefs.secondaryHomeApps.toList()
    )

    private fun saveCurrentState() {
        val state = _uiState.value
        navigationDelegate.saveCurrentState(savedStateHandle, state.currentRow, state.focusedGameIndex)
    }

    private fun flushLibraryState() {
        val lib = libraryDelegate.state.value
        val gradients = gradientExtractionDelegate.gradients.value
        _uiState.update {
            it.copy(
                platforms = lib.platforms,
                platformItems = lib.platformItems.applyRowGradients(gradients),
                platformItemsFor = lib.platformItemsFor,
                recentGames = lib.recentGames.applyGradients(gradients),
                favoriteGames = lib.favoriteGames.applyGradients(gradients),
                recommendedGames = lib.recommendedGames.applyGradients(gradients),
                androidGames = lib.androidGames.applyGradients(gradients),
                steamGames = lib.steamGames.applyGradients(gradients),
                pinnedCollections = lib.pinnedCollections,
                pinnedGames = lib.pinnedGames.mapValues { (_, games) ->
                    games.applyGradients(gradients)
                },
                pinnedGamesLoading = lib.pinnedGamesLoading,
                repairedCoverPaths = lib.repairedCoverPaths
            ).keepingFocusOn(it.focusedGame?.id)
        }
    }

    private fun observeFocusedGame() {
        viewModelScope.launch {
            socialRepository.friendsActivity.collect { activity ->
                _uiState.update { it.copy(friendsActivity = activity) }
            }
        }
        viewModelScope.launch {
            var previousGameId: Long? = null
            var previousGame: HomeGameUi? = null
            var previousFriends: List<com.nendo.argosy.data.social.FriendActivity> = emptyList()
            _uiState.collect { state ->
                val focusedGame = state.focusedGame
                publishTileShowcase(state)
                val friends = state.friendsFor(focusedGame)
                val sameGame = focusedGame?.id == previousGameId
                if (focusedGame == previousGame && friends == previousFriends) return@collect
                previousGame = focusedGame
                previousFriends = friends
                publishCompanionDetail(focusedGame)
                if (sameGame) return@collect
                previousGameId = focusedGame?.id
                focusedGame?.let { backfillLogo(it) }
                if (focusedGame != null) {
                    ambientLedManager.setContext(AmbientLedContext.GAME_HOVER)
                    if (ambientLedManager.coverArtEnabled) {
                        val colors = focusedGame.gradientColors
                            ?: gradientExtractionDelegate.getGradient(focusedGame.id)
                        if (colors != null) {
                            ambientLedManager.setHoverColors(colors.first, colors.second)
                        } else {
                            ambientLedManager.clearHoverColors()
                        }
                    }
                } else {
                    ambientLedManager.clearHoverColors()
                    ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
                }
            }
        }
    }

    private fun observeSiblingPicks() {
        siblingChoice.pickChanges
            .onEach { change ->
                refreshTileGamesAndFeatures()
                refreshCurrentRowInternal(anchorGameId = change.refocusTarget(_uiState.value.focusedGame?.id))
            }
            .launchIn(viewModelScope)
    }

    private fun publishTileShowcase(state: HomeUiState) {
        if (!isDescribing) return
        val dsm = DualScreenManagerHolder.instance ?: return
        val tile = state.focusedTile
            ?.takeIf { state.layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID }
        val slot = tile?.let { tileShowcaseFor(it, state) }
        if (slot == previousTileSlot) return
        previousTileSlot = slot
        if (slot == null) dsm.releaseSlot(tileShowcaseOwner) else dsm.presentSlot(tileShowcaseOwner, slot)
    }

    private fun tileShowcaseFor(
        tile: com.nendo.argosy.domain.model.HomeTile,
        state: HomeUiState
    ): com.nendo.argosy.ui.dualscreen.PresentationSlot? = when (val target = tile.target) {
        is HomeTileTargetRef.Game -> null
        is HomeTileTargetRef.Collection -> if (target.focusGameId != null) {
            null
        } else {
            state.tileShowcases[tile.id]
        }
        is HomeTileTargetRef.VirtualCollection -> state.tileShowcases[tile.id]
        is HomeTileTargetRef.App -> state.tileApps[target.packageName]?.let { name ->
            detail(
                title = name,
                subtitle = context.getString(R.string.home_grid_tile_app_subtitle)
            )
        }
        is HomeTileTargetRef.Media -> state.tileMedia[target.itemId]?.let { media ->
            detail(
                title = media.title,
                subtitle = media.subtitle,
                overview = media.overview,
                artUrl = media.posterUrl,
                backdropUrl = media.backdropUrl
            )
        }
        is HomeTileTargetRef.LocalMedia -> detail(
            title = target.filePath.substringAfterLast('/').substringBeforeLast('.'),
            subtitle = context.getString(R.string.home_grid_tile_local_media_subtitle),
            artUrl = state.customGrid.tilePlayback[tile.id]
        )
        is HomeTileTargetRef.Feature -> when (target.kind) {
            FeatureTileKind.RANDOM_GAME, FeatureTileKind.CONTINUE -> null
            FeatureTileKind.RA_SUMMARY -> raTileDetail(state)
            FeatureTileKind.LIBRARY_LINK -> state.tileShowcases[tile.id]
        }
        HomeTileTargetRef.Unresolvable -> null
    }

    private fun detail(
        title: String,
        subtitle: String? = null,
        overview: String? = null,
        artUrl: String? = null,
        backdropUrl: String? = null,
        facts: List<com.nendo.argosy.ui.dualscreen.CompanionFact> = emptyList()
    ) = com.nendo.argosy.ui.dualscreen.PresentationSlot.Detail(
        com.nendo.argosy.ui.dualscreen.CompanionDetail(
            title = title,
            subtitle = subtitle,
            overview = overview,
            artUrl = artUrl,
            backdropUrl = backdropUrl,
            facts = facts
        )
    )

    private fun raTileDetail(state: HomeUiState): com.nendo.argosy.ui.dualscreen.PresentationSlot? {
        val summary = state.raTileSummary ?: return detail(
            title = context.getString(R.string.home_grid_tile_feature_ra_label),
            subtitle = context.getString(R.string.home_grid_tile_feature_ra_signed_out)
        )
        return detail(
            title = summary.username,
            subtitle = context.getString(R.string.home_grid_tile_feature_ra_label),
            facts = listOf(
                com.nendo.argosy.ui.dualscreen.CompanionFact(
                    context.getString(R.string.home_grid_tile_ra_points_label),
                    summary.points.toString()
                ),
                com.nendo.argosy.ui.dualscreen.CompanionFact(
                    context.getString(R.string.home_grid_tile_ra_unlocks_label),
                    summary.unlocks.toString()
                )
            )
        )
    }

    private var logoPrefetch: kotlinx.coroutines.Job? = null

    private fun backfillLogo(focused: HomeGameUi) {
        val layout = DualScreenManagerHolder.instance?.presentationStyle?.value?.layout
        if (layout != com.nendo.argosy.domain.model.PresentationLayout.LOGO) return
        val state = _uiState.value
        val onScreen = listOf(focused) + state.tileGames.values +
            state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game }
        val pending = onScreen.filter { it.logoPath == null && it.id !in logoAttempts }
            .distinctBy { it.id }
        if (pending.isEmpty() || logoPrefetch?.isActive == true) return
        pending.forEach { logoAttempts.add(it.id) }
        logoPrefetch = viewModelScope.launch {
            var fetched = false
            pending.forEach { game ->
                val path = romMRepository.fetchLogo(game.id)
                if (path?.startsWith("/") == true) {
                    fetched = true
                    if (game.id == _uiState.value.focusedGame?.id) refreshLogoHolders()
                }
            }
            if (fetched) refreshLogoHolders()
        }
    }

    private suspend fun refreshLogoHolders() {
        refreshTileGamesAndFeatures()
        refreshCurrentRowInternal()
    }

    private fun publishCompanionDetail(game: HomeGameUi?) {
        if (!isDescribing) return
        DualScreenManagerHolder.instance?.setCompanionDetail(
            companionOwner,
            game?.toCompanionDetail(_uiState.value.friendsFor(game))
        )
    }

    fun republishCompanionDetail() {
        isDescribing = true
        publishCompanionDetail(_uiState.value.focusedGame)
        publishTileShowcase(_uiState.value)
    }

    fun clearCompanionDetail() {
        isDescribing = false
        previousTileSlot = null
        DualScreenManagerHolder.instance?.let {
            it.setCompanionDetail(companionOwner, null)
            it.releaseSlot(tileShowcaseOwner)
        }
    }

    private fun observeAchievementUpdates() {
        viewModelScope.launch {
            achievementUpdateBus.updates.collect { update ->
                libraryDelegate.updateAchievementCounts(update.gameId, update.totalCount, update.earnedCount)
            }
        }
    }

    private fun observeRecentlyPlayedChanges() {
        libraryDelegate.observeRecentlyPlayedChanges(viewModelScope) { validated ->
            _uiState.update { state ->
                val newState = state.copy(recentGames = validated)
                if (state.currentRow == HomeRow.Continue && validated.isEmpty()) {
                    val newRow = newState.availableRows.firstOrNull() ?: HomeRow.Continue
                    newState.copy(currentRow = newRow, focusedGameIndex = 0)
                } else {
                    newState
                }
            }
            refreshTileGamesAndFeatures()
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

    private fun observeBackgroundSettings() {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                currentBorderStyle = prefs.boxArtBorderStyle
                gradientExtractionDelegate.updatePreferences(prefs.gradientPreset, prefs.boxArtBorderStyle)

                _uiState.update { it.withLayoutFrom(prefs) }
                val scrollAxis = prefs.homeLayout.customGrid.scrollAxis
                val axisChanged = _uiState.value.customGrid.scrollAxis != scrollAxis
                customGrid.applyConfig(
                    autoFit = prefs.homeLayout.customGrid.autoFit,
                    storedPages = prefs.homeLayout.customGrid.pageCount,
                    scrollAxis = scrollAxis
                )
                if (axisChanged) applyPageAudio()

                val showsEveryGame = prefs.homeLayout.showsEveryGame
                if (lastShowsEveryGame != null && lastShowsEveryGame != showsEveryGame) {
                    refreshCurrentRowInternal()
                }
                lastShowsEveryGame = showsEveryGame

                videoPreviewDelegate.updateFromPreferences(
                    muteVideoPreview = prefs.videoWallpaperMuted,
                    videoWallpaperEnabled = prefs.videoWallpaperEnabled,
                    videoWallpaperDelaySeconds = prefs.videoWallpaperDelaySeconds
                )

                libraryDelegate.extractGradientsForVisibleGames(
                    viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex
                )
            }
        }
    }

    private fun observeGradientChanges() {
        viewModelScope.launch {
            gradientExtractionDelegate.gradients.collect { gradients ->
                _uiState.update { state ->
                    state.copy(
                        recentGames = state.recentGames.applyGradients(gradients),
                        favoriteGames = state.favoriteGames.applyGradients(gradients),
                        recommendedGames = state.recommendedGames.applyGradients(gradients),
                        androidGames = state.androidGames.applyGradients(gradients),
                        steamGames = state.steamGames.applyGradients(gradients),
                        platformItems = state.platformItems.applyRowGradients(gradients),
                        pinnedGames = state.pinnedGames.mapValues { (_, games) ->
                            games.applyGradients(gradients)
                        },
                        tileGames = state.tileGames.mapValues { (_, game) -> game.applyGradient(gradients) }
                    )
                }
            }
        }
        viewModelScope.launch {
            gradientExtractionDelegate.mediaGradients.collect { gradients ->
                if (gradients.isEmpty()) return@collect
                _uiState.update { state ->
                    state.copy(
                        nextUpMedia = state.nextUpMedia.applyMediaGradients(gradients),
                        continueWatchingMedia = state.continueWatchingMedia.applyMediaGradients(gradients),
                        favoriteMedia = state.favoriteMedia.applyMediaGradients(gradients),
                        mediaLibraryItems = state.mediaLibraryItems.applyMediaGradients(gradients),
                        tileMedia = state.tileMedia.mapValues { (_, media) ->
                            media.applyMediaGradient(gradients)
                        }
                    )
                }
            }
        }
    }

    private fun observePlatformChanges() {
        libraryDelegate.observePlatformChanges(viewModelScope) { currentPlatforms, newPlatformUis ->
            if (newPlatformUis == currentPlatforms) return@observePlatformChanges
            val result = navigationDelegate.reconcilePlatformChange(_uiState.value, currentPlatforms, newPlatformUis)
            when (result) {
                is PlatformChangeResult.Initial -> {
                    _uiState.update {
                        it.copy(platforms = result.platforms, isLoading = false).movedTo(result.row)
                    }
                    loadPlatformRowIfNeeded(result.row, result.platforms)
                }
                is PlatformChangeResult.DisplayOnly -> {
                    _uiState.update { it.copy(platforms = result.platforms) }
                    val updated = _uiState.value
                    if (updated.isPlatformRowLoading) {
                        loadPlatformRowIfNeeded(updated.currentRow, result.platforms)
                    }
                }
                is PlatformChangeResult.StructuralChange -> {
                    _uiState.update { it.copy(platforms = result.platforms, currentRow = result.row, focusedGameIndex = 0) }
                    loadPlatformRowIfNeeded(result.row, result.platforms)
                }
            }
        }
    }

    private fun loadPlatformRowIfNeeded(row: HomeRow, platforms: List<HomePlatformUi>) {
        if (row !is HomeRow.Platform) return
        val platform = platforms.getOrNull(row.index) ?: return
        viewModelScope.launch { libraryDelegate.loadPlatformGames(platform) }
    }

    private fun loadData() {
        libraryDelegate.loadInitialData(viewModelScope) { startRow ->
            flushLibraryState()
            _uiState.update { it.copy(isLoading = false).movedTo(startRow) }
            viewModelScope.launch {
                downloadDelegate.observeDownloadState(viewModelScope) {
                    libraryDelegate.invalidateRecentGamesCache()
                    refreshCurrentRowInternal()
                }
            }
            libraryDelegate.extractGradientsForVisibleGames(
                viewModelScope, _uiState.value.currentItems, 0
            )
        }
    }

    /**
     * A media rail is owned by its own delegate and holds no games, so refreshing the game library
     * has nothing to say about it. Running the game path over one would reset its cursor to the
     * first tile on every return to the foreground and, on an empty rail, throw the user off the row
     * instead of leaving the empty state up.
     *
     * A refresh only ever speaks for the games on a row, so the row itself is asked before the
     * cursor is moved off it: Favorites can hold nothing but titles, and those are not gone just
     * because the game half of the answer came back empty.
     */
    private suspend fun refreshCurrentRowInternal(anchorGameId: Long? = null) {
        val state = _uiState.value
        if (state.isMediaRow) {
            flushLibraryState()
            return
        }
        val result = libraryDelegate.refreshCurrentRow(state.currentRow, anchorGameId ?: state.focusedGame?.id)
        flushLibraryState()
        if (_uiState.value.currentRow != state.currentRow) return

        if (result.isEmpty && _uiState.value.currentItems.isEmpty()) {
            val newRow = _uiState.value.availableRows.firstOrNull() ?: HomeRow.Continue
            _uiState.update { it.copy(currentRow = newRow, focusedGameIndex = 0) }
        } else if (anchorGameId != null) {
            _uiState.update { it.keepingFocusOn(anchorGameId) }
        }
    }

    // --- Public API: Navigation ---

    private fun appBarSlotCount(): Int =
        _uiState.value.homeApps.size + 1

    private fun appBarIsDrawn(): Boolean =
        DualScreenManagerHolder.instance?.presentationShowsHints?.value == true

    override fun focusAppBar(): Boolean {
        if (!appBarIsDrawn()) return false
        _uiState.update { it.copy(appBarFocused = true, appBarIndex = 0) }
        return true
    }

    override fun releaseAppBar() {
        _uiState.update { it.copy(appBarFocused = false, appBarToolsOpen = false) }
    }

    override fun moveAppBarFocus(delta: Int) {
        val slots = appBarSlotCount()
        _uiState.update { it.copy(appBarIndex = appBarFocusMove(it.appBarIndex, delta, slots)) }
    }

    override fun activateAppBarSlot() {
        if (!appBarIsDrawn()) {
            releaseAppBar()
            return
        }
        val state = _uiState.value
        when (val index = state.appBarIndex) {
            APP_BAR_DRAWER_INDEX -> openAppDrawer()
            in state.homeApps.indices -> launchTileApp(state.homeApps[index])
            else -> toggleAppBarTools()
        }
    }

    fun toggleAppBarTools() {
        _uiState.update {
            it.copy(appBarToolsOpen = !it.appBarToolsOpen, appBarToolIndex = 1)
        }
    }

    fun focusAppBarTool(index: Int) {
        _uiState.update { it.copy(appBarToolIndex = index) }
    }

    fun dismissAppBarTools() {
        _uiState.update { it.copy(appBarToolsOpen = false) }
    }

    override fun nextRow() {
        val result = navigationDelegate.nextRow(_uiState.value, wrap = !appBarIsDrawn()) ?: run {
            focusAppBar()
            return
        }
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRow(viewModelScope, result.first) { row ->
            loadRowContent(row)
        }
        saveCurrentState()
    }

    fun selectRow(row: HomeRow) {
        val state = _uiState.value
        if (row == state.currentRow || row !in state.availableRows) return
        _uiState.update { it.copy(currentRow = row, focusedGameIndex = 0) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRow(viewModelScope, row) { loadRowContent(it) }
        saveCurrentState()
    }

    override fun previousRow() {
        val result = navigationDelegate.previousRow(_uiState.value) ?: return
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRow(viewModelScope, result.first) { row ->
            loadRowContent(row)
        }
        saveCurrentState()
    }

    /**
     * What confirm does to the tile under the cursor on a media row: it plays, whichever row the tile
     * is on. A rail already named the episode; a library tile has to be asked what it stands for
     * first, which is why this is the one media action that cannot answer on the spot.
     */
    private fun activateFocusedMedia(media: HomeMediaUi) {
        startMedia(media, startOver = false)
    }

    private fun startMedia(media: HomeMediaUi, startOver: Boolean) {
        viewModelScope.launch {
            when (val target = mediaDelegate.resolvePlayTarget(media)) {
                is MediaPlayTarget.Play -> playMedia(target.itemId, startOver)
                is MediaPlayTarget.OpenDetail ->
                    _events.emit(HomeEvent.NavigateToMediaDetail(target.itemId))
            }
        }
    }

    private suspend fun loadRowContent(row: HomeRow) {
        when (row) {
            is HomeRow.Platform -> {
                val platform = _uiState.value.platforms.getOrNull(row.index)
                if (platform != null) {
                    libraryDelegate.loadPlatformGames(platform)
                }
            }
            HomeRow.Continue -> libraryDelegate.loadRecentGames()
            HomeRow.Favorites -> libraryDelegate.loadFavorites()
            HomeRow.Recommendations -> libraryDelegate.loadRecommendations()
            HomeRow.Android -> { }
            HomeRow.Steam -> { }
            HomeRow.ContinueWatching -> mediaDelegate.refresh(viewModelScope)
            HomeRow.NextUp -> mediaDelegate.refresh(viewModelScope)
            is HomeRow.MediaLibrary -> syncSelectedMediaLibrary()
            is HomeRow.PinnedRegular -> libraryDelegate.loadGamesForPinnedCollection(row.pinId)
            is HomeRow.PinnedVirtual -> libraryDelegate.loadGamesForPinnedCollection(row.pinId)
        }
        flushLibraryState()
        libraryDelegate.extractGradientsForVisibleGames(
            viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex
        )
    }

    override fun nextGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex >= state.currentItems.size - 1) return false
        _uiState.update {
            if (it.focusedGameIndex >= it.currentItems.size - 1) it
            else it.copy(focusedGameIndex = it.focusedGameIndex + 1)
        }
        saveCurrentState()
        prefetchAchievementsDebounced()
        navigationDelegate.prefetchAdjacentBackgrounds(viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex)
        libraryDelegate.extractGradientsForVisibleGames(viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex)
        return true
    }

    override fun previousGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex <= 0) return false
        _uiState.update {
            if (it.focusedGameIndex <= 0) it
            else it.copy(focusedGameIndex = it.focusedGameIndex - 1)
        }
        saveCurrentState()
        prefetchAchievementsDebounced()
        navigationDelegate.prefetchAdjacentBackgrounds(viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex)
        libraryDelegate.extractGradientsForVisibleGames(viewModelScope, _uiState.value.currentItems, _uiState.value.focusedGameIndex)
        return true
    }

    /**
     * Tiles and the games they point at. The lookup is by id across the whole library rather than
     * from the current section, because a curated page is not a section: the games on it have
     * nothing in common except that someone put them there.
     */
    /**
     * Offers left by finished downloads while the launcher was elsewhere. Drained one at a time and
     * only while the curated grid is the layout in use, since that is the only place a tile means
     * anything.
     */
    private fun observeTilePrompts() {
        viewModelScope.launch {
            homeTilePromptQueue.pending.collect { pending ->
                val gameId = pending.firstOrNull() ?: return@collect
                if (_uiState.value.layoutKind !=
                    com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID
                ) return@collect
                val entry = libraryDelegate.tilePickerEntryFor(gameId)
                if (entry == null) {
                    homeTilePromptQueue.resolve(gameId)
                    return@collect
                }
                customGrid.showPendingAdd(entry)
            }
        }
    }

    override fun confirmPendingTileAdd() =
        customGrid.confirmPendingAdd { homeTilePromptQueue.resolve(it) }

    override fun dismissPendingTileAdd() =
        customGrid.dismissPendingAdd { homeTilePromptQueue.resolve(it) }

    override fun movePendingTileAddFocus(delta: Int) = customGrid.movePendingAddFocus(delta)

    /**
     * The rows a page chooser shows, fetched fresh each time because the soundtrack library and the
     * artwork a game has both change while the app is running.
     */
    private suspend fun pageChooserEntriesFor(
        chooser: com.nendo.argosy.ui.components.PageChooserState
    ): List<com.nendo.argosy.ui.components.PageChooserEntry> =
        pageChooserEntrySource.entriesFor(chooser, _uiState.value.customGrid.focusedCollection)

    private suspend fun firstGameInCollection(collectionId: Long): Long? =
        collectionRepository.getGamesInCollection(collectionId).firstOrNull()?.id

    /**
     * Marks the game being left as finished and answers with the next one in the collection.
     *
     * Finishing is the reason the queue moves, so it is recorded through the path that also tells
     * RomM rather than only the local row. A status the user set deliberately to say they are done
     * with a game -- retired, never playing, already 100% -- is left alone.
     */
    private suspend fun advanceCollectionFocus(collectionId: Long, currentGameId: Long): Long? {
        val result = advanceCollectionFocusUseCase(collectionId, currentGameId) ?: return null
        notificationManager.show(
            title = NotificationText.Res(R.string.home_notice_playing_next),
            subtitle = NotificationText.Raw(result.nextTitle),
            type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
            duration = com.nendo.argosy.core.notification.NotificationDuration.SHORT
        )
        return result.nextGameId
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeHomeTiles() {
        viewModelScope.launch {
            val owner = syncPreferencesRepository.getRommUserId()
            preferencesRepository.preferences
                .map { it.homeLayout.customGrid.gridKind }
                .distinctUntilChanged()
                .flatMapLatest { kind -> homeTileRepository.observeTiles(owner, kind) }
                .collect { tiles ->
                    storedTiles = tiles
                    storedTilesLoaded = true
                    publishHomeTiles(tiles)
                }
        }
        customGrid.observePageSettings {
            firstFrameCache.pageSettings = _uiState.value.customGrid.pageSettings
            applyPageAudio()
        }
    }

    /**
     * Hands the grid the tiles it can actually draw, and resolves what each one points at.
     *
     * The stored list is kept whole here and filtered on the way out, so hiding media while signed
     * out never writes anything: the page the database holds is still the page the user arranged.
     */
    private suspend fun publishHomeTiles(tiles: List<com.nendo.argosy.domain.model.HomeTile>) {
        val shown = shownTiles(tiles)
        val feature = featureTileContent(shown)
        val libraryLinks = resolveLibraryLinks(shown)
        val games = libraryDelegate.resolveTileGames(
            (
                shown.mapNotNull {
                    when (val target = it.target) {
                        is HomeTileTargetRef.Game -> target.gameId
                        is HomeTileTargetRef.Collection -> target.focusGameId
                        is HomeTileTargetRef.Feature -> target.pickedGameId
                        else -> null
                    }
                } + listOfNotNull(feature.continueGameId, feature.raSummary?.groundGameId) +
                    libraryLinks.values.mapNotNull { it.coverGameId }
            ).distinct()
        )
        val collections = libraryDelegate.resolveTileCollections(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.Collection)?.collectionId }.distinct()
        )
        val apps = libraryDelegate.resolveTileApps(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.App)?.packageName }.distinct()
        )
        val showcases = resolveTileShowcases(shown, libraryLinks, collections)
        mediaDelegate.selectTileItems(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.Media)?.itemId }.distinct()
        )
        customGrid.setTiles(shown)
        customGrid.setRaTile(feature.raSummary.toGridStatus())
        val gradients = gradientExtractionDelegate.gradients.value
        _uiState.update {
            it.copy(
                tileGames = games.mapValues { (_, game) -> game.applyGradient(gradients) },
                tileCollections = collections,
                tileApps = apps,
                tileLibraryLinks = libraryLinks,
                tileShowcases = showcases,
                continueGameId = feature.continueGameId,
                raTileSummary = feature.raSummary
            )
        }
        rememberPublishedTiles()
        resolveTilePlayback(shown)
        ensureRandomPicks(shown, games)
    }

    private fun rememberPublishedTiles() {
        val state = _uiState.value
        firstFrameCache.tiles = HomeTilesSnapshot(
            gridKind = state.customGridConfig.gridKind,
            tiles = state.customGrid.tiles,
            raTile = state.customGrid.raTile,
            tileGames = state.tileGames,
            tileCollections = state.tileCollections,
            tileApps = state.tileApps,
            tileLibraryLinks = state.tileLibraryLinks,
            tileShowcases = state.tileShowcases,
            continueGameId = state.continueGameId,
            raTileSummary = state.raTileSummary
        )
    }

    private fun shownTiles(
        tiles: List<com.nendo.argosy.domain.model.HomeTile>
    ): List<com.nendo.argosy.domain.model.HomeTile> =
        if (tileMediaShown) tiles else tiles.filterNot { it.target is HomeTileTargetRef.Media }

    /**
     * What the continue and RetroAchievements tiles show, read fresh each time. Both move while
     * the grid stands still: a session puts another game at the top of recently played and can
     * add unlocks, and neither touches the stored tile list.
     */
    private suspend fun featureTileContent(
        shown: List<com.nendo.argosy.domain.model.HomeTile>
    ): FeatureTileContent {
        val features = shown.mapNotNull { it.target as? HomeTileTargetRef.Feature }
        val raTile = features.firstOrNull { it.kind == FeatureTileKind.RA_SUMMARY }
        return FeatureTileContent(
            continueGameId = if (features.any { it.kind == FeatureTileKind.CONTINUE }) {
                gameRepository.getRecentlyPlayed(1).firstOrNull()?.id
            } else {
                null
            },
            raSummary = raTile?.let { raTileContentRepository.load(it.pickedGameId) }
        )
    }

    private fun refreshTileGamesAndFeatures() {
        viewModelScope.launch {
            val feature = featureTileContent(shownTiles(storedTiles))
            val resolved = libraryDelegate.resolveTileGames(
                (
                    _uiState.value.tileGames.keys +
                        listOfNotNull(feature.continueGameId, feature.raSummary?.groundGameId)
                ).distinct()
            )
            val gradients = gradientExtractionDelegate.gradients.value
            customGrid.setRaTile(feature.raSummary.toGridStatus())
            _uiState.update {
                it.copy(
                    tileGames = it.tileGames +
                        resolved.mapValues { (_, game) -> game.applyGradient(gradients) },
                    continueGameId = feature.continueGameId,
                    raTileSummary = feature.raSummary
                )
            }
        }
    }

    /**
     * Gives every random tile a game it can show. A tile arrives without one when it was just
     * placed, or its stored pick has since left the library; either way the pick is written back,
     * and the tile flow re-emits with it. A filter nothing matches leaves the tile empty rather than
     * writing anything, so this cannot spin.
     */
    private suspend fun ensureRandomPicks(
        tiles: List<com.nendo.argosy.domain.model.HomeTile>,
        resolved: Map<Long, HomeGameUi>
    ) {
        tiles.forEach { tile ->
            val target = tile.target as? HomeTileTargetRef.Feature ?: return@forEach
            if (target.kind != FeatureTileKind.RANDOM_GAME) return@forEach
            if (target.pickedGameId != null && resolved.containsKey(target.pickedGameId)) return@forEach
            val pick = gameRepository.pickRandomGame(target.filters) ?: return@forEach
            homeTileRepository.updateFeaturePick(tile.id, pick.id)
        }
    }

    private suspend fun resolveLibraryLinks(
        tiles: List<com.nendo.argosy.domain.model.HomeTile>
    ): Map<Long, LibraryLinkTileUi> {
        val links = tiles.mapNotNull { tile ->
            val target = tile.target as? HomeTileTargetRef.Feature ?: return@mapNotNull null
            if (target.kind != FeatureTileKind.LIBRARY_LINK) return@mapNotNull null
            tile.id to (target.libraryLink ?: com.nendo.argosy.domain.model.LibraryLinkFilters())
        }
        if (links.isEmpty()) return emptyMap()
        val platformNames = libraryDelegate.platformOptionsForTiles()
            .associate { it.id to it.shortLabel }
        return links.associate { (tileId, filters) ->
            val seriesGameIds = filters.series.takeIf { it.isNotEmpty() }?.let { names ->
                collectionRepository.observeGameIdsByTypeAndNames(CollectionType.SERIES, names.toList())
                    .first()
                    .toSet()
            }
            val summary = gameRepository.summarizeLibraryLink(filters, seriesGameIds)
            tileId to LibraryLinkTileUi(
                gameCount = summary.gameCount,
                coverGameId = summary.coverGameId,
                gameIds = summary.gameIds,
                platformNames = filters.platformIds.mapNotNull { platformNames[it] }.sorted(),
                genres = filters.genres.sorted(),
                series = filters.series.sorted(),
                source = filters.source,
                players = filters.players
            )
        }
    }

    private suspend fun resolveTileShowcases(
        tiles: List<com.nendo.argosy.domain.model.HomeTile>,
        links: Map<Long, LibraryLinkTileUi>,
        collections: Map<Long, com.nendo.argosy.ui.components.TileCollectionUi>
    ): Map<Long, com.nendo.argosy.ui.dualscreen.PresentationSlot.PlatformShowcase> =
        tiles.mapNotNull { tile ->
            val named = when (val target = tile.target) {
                is HomeTileTargetRef.Collection -> if (target.focusGameId != null) {
                    null
                } else {
                    collections[target.collectionId]?.name?.let { name ->
                        name to collectionRepository.getGameIdsInCollection(target.collectionId)
                    }
                }
                is HomeTileTargetRef.VirtualCollection -> {
                    val type = runCatching {
                        com.nendo.argosy.data.local.entity.CollectionType.valueOf(target.type)
                    }.getOrNull()
                    type?.let { target.name to collectionRepository.virtualGameIds(it, target.name) }
                }
                is HomeTileTargetRef.Feature ->
                    if (target.kind != FeatureTileKind.LIBRARY_LINK) {
                        null
                    } else {
                        links[tile.id]?.let { link ->
                            com.nendo.argosy.ui.common.libraryLinkLabel(
                                link,
                                context,
                                HOME_FEATURE_TILE_STRINGS
                            ) to link.gameIds
                        }
                    }
                else -> null
            } ?: return@mapNotNull null
            val (name, gameIds) = named
            tile.id to showcaseSource.collectionShowcase(name, gameIds)
        }.toMap()

    private fun featureTileEntries(): List<com.nendo.argosy.ui.components.TilePickerEntry> =
        com.nendo.argosy.ui.common.featureTilePickerEntries(
            context = context,
            strings = HOME_FEATURE_TILE_PICKER_STRINGS
        )

    override fun rerollRandomTile() {
        val tile = _uiState.value.customGrid.focusedTile ?: return
        val target = tile.target as? HomeTileTargetRef.Feature ?: return
        if (target.kind != FeatureTileKind.RANDOM_GAME) return
        viewModelScope.launch {
            val pick = gameRepository.pickRandomGame(target.filters, excludeGameId = target.pickedGameId)
                ?: return@launch
            homeTileRepository.updateFeaturePick(tile.id, pick.id)
        }
    }

    /**
     * Works out which tiles have a file on this device to play. A tile with nothing local draws its
     * poster, so the map is the whole answer to whether a tile can preview or be engaged.
     */
    private fun resolveTilePlayback(tiles: List<com.nendo.argosy.domain.model.HomeTile>) {
        viewModelScope.launch {
            val playable = mutableMapOf<Long, String>()
            val resumePoints = mutableMapOf<String, Long>()
            tiles
                .filter { it.target is HomeTileTargetRef.Media || it.target is HomeTileTargetRef.LocalMedia }
                .forEach { tile ->
                    val ready = tilePickerDelegate.playbackFor(tile) as? MediaTilePlayback.Ready
                        ?: return@forEach
                    playable[tile.id] = ready.localPath
                    if (ready.resumeTicks > 0) {
                        resumePoints[ready.localPath] =
                            ready.resumeTicks / com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
                    }
                }
            customGrid.setTilePlayback(playable)
            customGrid.seedPlaybackPositions(resumePoints)
        }
    }

    /**
     * Grid shape is a property of the display, so the renderer measures it and reports it back
     * here; navigation needs the same columns and rows the user can see or the cursor leaves the
     * page at a different edge than the art does.
     */
    fun setCustomGridShape(resolved: com.nendo.argosy.domain.model.ResolvedGridShape) {
        customGrid.setShape(resolved.shape.columns, resolved.shape.rows)
        customGridShapeStore.report(resolved)
        persistLegacyLaneOrientation(resolved.portrait)
    }

    private fun persistLegacyLaneOrientation(portrait: Boolean) {
        if (!_uiState.value.customGridConfig.lanesOnShortEdge) return
        viewModelScope.launch {
            val settings = preferencesRepository.userPreferences.first().homeLayout
            if (!settings.customGrid.lanesOnShortEdge) return@launch
            preferencesRepository.setHomeLayout(
                settings.copy(customGrid = settings.customGrid.orientedTo(portrait))
            )
        }
    }

    fun openHiddenCustomGridTiles() = customGrid.openHiddenTiles()

    override fun moveCustomGridFocus(
        direction: com.nendo.argosy.domain.model.GridDirection2D
    ): Boolean = customGrid.moveFocus(direction)

    override fun turnCustomGridPage(delta: Int): Boolean {
        val turned = customGrid.turnPage(delta)
        if (turned) applyPageAudio()
        return turned
    }

    /**
     * Hands the output to the page in view, so a page carrying its own sound replaces the
     * launcher's music for as long as it is shown.
     */
    private fun applyPageAudio() {
        val grid = _uiState.value.customGrid
        val pageOwns = _uiState.value.layoutKind == HomeLayoutKind.CUSTOM_GRID &&
            grid.currentPageSettings.silencesGlobalAudio
        videoPreviewDelegate.setPageOwnsAudio(pageOwns)
    }

    fun setCustomGridCell(cell: com.nendo.argosy.domain.model.GridCell) = customGrid.setCell(cell)

    fun moveEditingTileTo(cell: com.nendo.argosy.domain.model.GridCell) =
        customGrid.moveEditingTileTo(cell)

    fun resizeEditingTileTo(cell: com.nendo.argosy.domain.model.GridCell) =
        customGrid.resizeEditingTileTo(cell)

    fun focusedTile(): com.nendo.argosy.domain.model.HomeTile? = customGrid.focusedTile()

    override fun focusedTileGameId(): Long? = customGrid.focusedGameId()

    fun placeGameOnFocusedCell(gameId: Long) =
        customGrid.placeOnFocusedCell(HomeTileTargetRef.Game(gameId))

    fun tileMenuActions(): List<com.nendo.argosy.ui.components.CustomTileMenuAction> =
        _uiState.value.customGrid.menuActions

    override fun openTileMenu() = customGrid.openMenu()

    override fun closeTileMenu() = customGrid.closeMenu()

    override fun moveTileMenuFocus(delta: Int) = customGrid.moveMenuFocus(delta)

    override fun confirmTileMenu() = customGrid.confirmMenu()

    fun removeFocusedTile() = customGrid.removeFocusedTile()

    val isOnAddPage: Boolean
        get() = _uiState.value.customGrid.isOnAddPage

    override fun confirmAddPage() = customGrid.confirmAddPage()

    fun deleteCustomGridPage() = customGrid.deleteCurrentPage()

    /**
     * Remembers a page that holds nothing, when the layout is set to keep blank pages. Pages are
     * otherwise implied by the tiles on them, so an empty one has nowhere to live but the config.
     */
    private fun persistCustomGridPageCount(count: Int) {
        val config = _uiState.value.customGridConfig
        if (!config.persistBlankPages || count <= config.pageCount) return
        viewModelScope.launch {
            val settings = preferencesRepository.userPreferences.first().homeLayout
            preferencesRepository.setHomeLayout(
                settings.copy(customGrid = settings.customGrid.copy(pageCount = count))
            )
        }
    }

    /**
     * Forgets a remembered blank page. Without this the config keeps claiming the page the delete
     * just removed, and the next preferences emission puts it straight back.
     */
    private fun persistCustomGridPageRemoval(count: Int) {
        val config = _uiState.value.customGridConfig
        if (config.pageCount <= count) return
        viewModelScope.launch {
            val settings = preferencesRepository.userPreferences.first().homeLayout
            preferencesRepository.setHomeLayout(
                settings.copy(customGrid = settings.customGrid.copy(pageCount = count))
            )
        }
    }

    /**
     * Opens the picker for the focused cell. Offers installed games only, since a grid you curate
     * is somewhere you reach for something to play rather than something to fetch.
     */
    override fun openTilePicker() = customGrid.openPicker()

    override fun closeTilePicker() = customGrid.closePicker()

    override fun backOutOfPickerLibrary(): Boolean = customGrid.backOutOfPickerLibrary()

    fun setTilePickerQuery(query: String) = customGrid.setPickerQuery(query)

    override fun toggleTilePickerSearch() = customGrid.togglePickerSearch()

    override fun moveTilePickerFocus(delta: Int) = customGrid.movePickerFocus(delta)

    override fun confirmTilePickerSelection() = customGrid.confirmPickerSelection()

    fun selectTilePickerEntry(entry: com.nendo.argosy.ui.components.TilePickerEntry) =
        customGrid.selectPickerEntry(entry)

    override fun cycleTilePickerCategory(delta: Int) = customGrid.cyclePickerCategory(delta)

    override fun jumpTilePickerLetter(forward: Boolean) = customGrid.jumpPickerLetter(forward)

    fun setTilePickerCategory(category: com.nendo.argosy.ui.components.TilePickerCategory) =
        customGrid.setPickerCategory(category)

    override fun moveMediaTileSetupFocus(delta: Int) = customGrid.moveMediaSetupFocus(delta)

    override fun moveMediaTileSetupSideways(towardsEnd: Boolean) =
        customGrid.moveMediaSetupSideways(towardsEnd)

    override fun confirmMediaTileSetup() = customGrid.confirmMediaSetup()

    /**
     * The touch entry to the same answer the d-pad gives. A tapped row names its own position, so
     * focus moves there before it is acted on and the two modalities cannot diverge.
     */
    fun confirmMediaTileSetupAt(index: Int) = customGrid.confirmMediaSetup(index)

    override fun backFromMediaTileSetup() {
        customGrid.backFromMediaSetup()
    }

    override fun moveFeatureTileSetupFocus(delta: Int) = customGrid.moveFeatureSetupFocus(delta)

    override fun confirmFeatureTileSetup() = customGrid.confirmFeatureSetup()

    fun confirmFeatureTileSetupAt(index: Int) = customGrid.confirmFeatureSetup(index)

    override fun backFromFeatureTileSetup() {
        customGrid.backFromFeatureSetup()
    }

    override fun confirmMediaTileNotice() = customGrid.confirmMediaTileNotice()

    override fun dismissMediaTileNotice() = customGrid.dismissMediaTileNotice()

    override fun moveMediaTileNoticeFocus(delta: Int) = customGrid.moveMediaTileNoticeFocus(delta)

    fun closeTileFileBrowser() = customGrid.closeFileBrowser()

    fun placeLocalVideoTile(path: String) = customGrid.placeLocalVideo(path)

    /**
     * Activates a tile that is not a game. An app launches through the same intent path the apps
     * screen uses; a collection has no destination of its own on this surface, so it opens the
     * collections screen rather than pretending to filter something.
     */
    override fun launchTileApp(packageName: String) = launchApp(packageName)

    private fun launchApp(packageName: String, pinnedScreenKey: String? = null) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        viewModelScope.launch {
            if (pinnedScreenKey != null) {
                preferencesRepository.setAppDisplayTarget(packageName, pinnedScreenKey)
            }
            val dsm = DualScreenManagerHolder.instance
            val options = displayAffinityHelper.getAppLaunchOptions(
                preferredScreenKey = pinnedScreenKey
                    ?: preferencesRepository.preferences.first().appDisplayTargets[packageName],
                rolesSwapped = dsm?.isRolesSwapped?.value == true,
                occupiedDisplayId = dsm?.emulatorDisplayId
            )
            _events.emit(HomeEvent.LaunchIntent(intent, options))
        }
    }

    override fun openAppBarAppMenu(): Boolean {
        val state = _uiState.value
        val packageName = state.appDrawer?.focusedPackage
            ?: state.homeApps.getOrNull(state.appBarIndex)
            ?: return false
        return openAppMenuFor(packageName)
    }

    private fun openAppMenuFor(packageName: String): Boolean {
        val screens = DualScreenManagerHolder.instance
            ?.focusableDisplays()
            ?.map { screen ->
                com.nendo.argosy.ui.components.AppMenuRow.OpenOnScreen(
                    screen.displayId,
                    screen.number,
                    screen.key
                )
            }
            .orEmpty()
        val rows = buildList<com.nendo.argosy.ui.components.AppMenuRow> {
            if (screens.size > 1) addAll(screens)
            add(
                com.nendo.argosy.ui.components.AppMenuRow.Action(
                    com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_SECONDARY_HOME
                )
            )
            add(
                com.nendo.argosy.ui.components.AppMenuRow.Action(
                    com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_VISIBILITY
                )
            )
            add(
                com.nendo.argosy.ui.components.AppMenuRow.Action(
                    com.nendo.argosy.ui.components.AppContextMenuItem.UNINSTALL
                )
            )
        }
        _uiState.update {
            it.copy(
                appBarMenu = AppBarLaunchMenu(
                    packageName = packageName,
                    label = appsRepository.getAppLabel(packageName) ?: packageName,
                    rows = rows
                )
            )
        }
        viewModelScope.launch {
            val pinned = appShortcutActions.isPinned(packageName)
            val hidden = appShortcutActions.isHidden(packageName)
            _uiState.update { state ->
                state.appBarMenu
                    ?.takeIf { it.packageName == packageName }
                    ?.let { state.copy(appBarMenu = it.copy(isPinned = pinned, isHidden = hidden)) }
                    ?: state
            }
        }
        return true
    }

    override fun moveAppBarAppMenu(delta: Int) {
        val menu = _uiState.value.appBarMenu ?: return
        val next = (menu.focusIndex + delta).mod(menu.rows.size)
        _uiState.update { it.copy(appBarMenu = menu.copy(focusIndex = next)) }
    }

    override fun confirmAppBarAppMenu() {
        val menu = _uiState.value.appBarMenu ?: return
        val row = menu.rows.getOrNull(menu.focusIndex) ?: return
        val packageName = menu.packageName
        dismissAppBarAppMenu()
        when (row) {
            is com.nendo.argosy.ui.components.AppMenuRow.OpenOnScreen ->
                launchApp(packageName, pinnedScreenKey = row.screenKey)
            is com.nendo.argosy.ui.components.AppMenuRow.Action -> when (row.item) {
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_SECONDARY_HOME ->
                    viewModelScope.launch { appShortcutActions.togglePinned(packageName) }
                com.nendo.argosy.ui.components.AppContextMenuItem.TOGGLE_VISIBILITY ->
                    viewModelScope.launch { appShortcutActions.toggleHidden(packageName) }
                com.nendo.argosy.ui.components.AppContextMenuItem.UNINSTALL ->
                    viewModelScope.launch {
                        _events.emit(
                            HomeEvent.LaunchIntent(appShortcutActions.uninstallIntent(packageName))
                        )
                    }
                else -> Unit
            }
        }
    }

    override fun dismissAppBarAppMenu() {
        if (_uiState.value.appBarMenu == null) return
        _uiState.update { it.copy(appBarMenu = null) }
    }

    override fun openAppDrawer() {
        if (_uiState.value.appDrawer != null) return
        _uiState.update { it.copy(appDrawer = AppDrawerState()) }
        viewModelScope.launch {
            val apps = appsRepository.getInstalledApps()
                .map { com.nendo.argosy.ui.components.AppDrawerEntry(it.packageName, it.label) }
                .sortedBy { it.label.lowercase() }
            _uiState.update { state ->
                state.appDrawer?.let { drawer ->
                    state.copy(appDrawer = drawer.copy(apps = apps))
                } ?: state
            }
        }
    }

    override fun moveAppDrawer(delta: Int) {
        val drawer = _uiState.value.appDrawer ?: return
        if (drawer.apps.isEmpty()) return
        val next = (drawer.focusIndex + delta).mod(drawer.apps.size)
        _uiState.update { it.copy(appDrawer = drawer.copy(focusIndex = next)) }
    }

    override fun confirmAppDrawer() {
        val packageName = _uiState.value.appDrawer?.focusedPackage ?: return
        dismissAppDrawer()
        launchTileApp(packageName)
    }

    override fun dismissAppDrawer() {
        if (_uiState.value.appDrawer == null) return
        _uiState.update { it.copy(appDrawer = null) }
    }

    override fun openTileCollection(collectionId: Long) {
        viewModelScope.launch { _events.emit(HomeEvent.NavigateToCollections(collectionId)) }
    }

    override fun openTileLibraryLink(filters: com.nendo.argosy.domain.model.LibraryLinkFilters) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLibrary(tileFilters = filters))
        }
    }

    /**
     * Plays a pinned title. The tile holds the show, so which episode starts is worked out here the
     * same way a row's tile works it out -- resume where one was left, otherwise whatever the server
     * says comes next -- and a show nothing playable can be found in opens its detail screen instead
     * of doing nothing. A tile whose title has not been synced yet has nothing to resolve, so the
     * press opens what the grid can offer rather than failing silently.
     */
    override fun playTileMedia(itemId: String) {
        val media = _uiState.value.tileMedia[itemId]
        if (media == null) {
            viewModelScope.launch { _events.emit(HomeEvent.NavigateToMediaDetail(itemId)) }
            return
        }
        startMedia(media, startOver = false)
    }

    override fun engageFocusedTile(): Boolean {
        val engaged = customGrid.engageFocusedTile()
        if (engaged) videoPreviewDelegate.holdForTileAudio()
        return engaged
    }

    override fun disengageTile(): Boolean {
        flushPlaybackPositions()
        val released = customGrid.disengageTile()
        if (released) videoPreviewDelegate.releaseTileAudio()
        return released
    }

    override fun toggleEngagedPlayback() = customGrid.toggleEngagedPlayback()

    override fun seekEngagedTile(forward: Boolean) = customGrid.seekEngagedTile(forward)

    override fun stepEngagedTile(delta: Int): Boolean = customGrid.stepEngaged(delta)

    override fun browseFocusedRaTile(): Boolean = customGrid.browseFocusedRaTile()

    override fun engageRaTileAt(index: Int): Boolean = customGrid.engageRaTileAt(index)

    override fun openRaSignIn() {
        viewModelScope.launch {
            _events.emit(
                HomeEvent.NavigateToSettings(
                    com.nendo.argosy.ui.screens.settings.SettingsSection.RETRO_ACHIEVEMENTS.name
                )
            )
        }
    }

    private val livePlaybackPositions = mutableMapOf<String, Long>()

    /**
     * Held outside the grid state while a tile plays. The engaged tile tracks its own position for
     * display; publishing every poll rewrote `playbackPositions` twice a second, which handed the
     * grid a new map and recomposed every tile to change a value only a future start reads.
     */
    fun rememberTilePlaybackPosition(filePath: String, positionMs: Long) {
        livePlaybackPositions[filePath] = positionMs
    }

    private fun flushPlaybackPositions() {
        if (livePlaybackPositions.isEmpty()) return
        livePlaybackPositions.forEach { (path, position) ->
            customGrid.rememberPlaybackPosition(path, position)
        }
        livePlaybackPositions.clear()
    }

    private fun reachedPositionFor(filePath: String?): Long? =
        filePath?.let { livePlaybackPositions[it] }

    /**
     * Hands the engaged tile to the fullscreen player and lets go of it here, so one file is never
     * open on two surfaces at once. A tile pointing at a loose file has no library item behind it
     * to open, so it stays where it is and keeps playing.
     */
    override fun openEngagedFullscreen() {
        val grid = _uiState.value.customGrid
        val engaged = grid.engagedTile ?: return
        val target = engaged.target
        if (target !is HomeTileTargetRef.Media) return
        val playingPath = grid.tilePlayback[engaged.id]
        val reached = reachedPositionFor(playingPath)
            ?: playingPath?.let { grid.playbackPositions[it] }
            ?: 0L
        disengageTile()
        viewModelScope.launch {
            mediaDelegate.handOffPosition(target.itemId, reached)
            playTileMedia(target.itemId)
        }
    }

    override fun enterTileMoveMode() = customGrid.enterMoveMode()

    override fun exitTileMoveMode() = customGrid.commitEdit()

    override fun commitTileEdit() = customGrid.commitEdit()

    override fun cancelTileEdit() = customGrid.cancelEdit()

    override fun toggleTileEditMode() = customGrid.toggleEditMode()

    override fun advanceFocusGame() = customGrid.advanceFocusGame()

    override fun movePageChooserFocus(delta: Int) = customGrid.movePageChooserFocus(delta)

    override fun confirmPageChooser() = customGrid.confirmPageChooser()

    override fun backOutOfPageChooser() {
        customGrid.backOutOfPageChooser()
    }

    fun setPageChooserQuery(query: String) = customGrid.setPageChooserQuery(query)

    fun closePageChooser() = customGrid.closePageChooser()

    override fun moveFocusedTile(
        direction: com.nendo.argosy.domain.model.GridDirection2D
    ): Boolean = customGrid.moveFocusedTile(direction)

    override fun resizeFocusedTile(
        direction: com.nendo.argosy.domain.model.GridDirection2D
    ): Boolean = customGrid.resizeFocusedTile(direction)

    override fun moveGridFocus(direction: GridDirection): AutoGridMove {
        val state = _uiState.value
        val move = autoGridMove(
            itemCount = state.currentItems.size,
            config = state.autoGridConfig,
            currentIndex = state.focusedGameIndex,
            direction = direction
        )
        val target = (move as? AutoGridMove.Focus)?.index ?: return move
        _uiState.update { it.copy(focusedGameIndex = target) }
        saveCurrentState()
        prefetchAchievementsDebounced()
        navigationDelegate.prefetchAdjacentBackgrounds(viewModelScope, _uiState.value.currentItems, target)
        libraryDelegate.extractGradientsForVisibleGames(viewModelScope, _uiState.value.currentItems, target)
        return move
    }

    fun setFocusIndex(index: Int) {
        if (!navigationDelegate.setFocusIndex(_uiState.value, index)) return
        _uiState.update { it.copy(focusedGameIndex = index) }
        saveCurrentState()
        prefetchAchievementsDebounced()
        navigationDelegate.prefetchAdjacentBackgrounds(viewModelScope, _uiState.value.currentItems, index)
        libraryDelegate.extractGradientsForVisibleGames(viewModelScope, _uiState.value.currentItems, index)
    }

    // --- Public API: Game Interaction ---

    @Suppress("UNUSED_PARAMETER")
    fun handleItemTap(index: Int, _onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        if (index < 0 || index >= state.currentItems.size) return

        if (index != state.focusedGameIndex) {
            setFocusIndex(index)
            return
        }

        when (val item = state.currentItems[index]) {
            is HomeRowItem.Game -> activateGame(item.game)
            is HomeRowItem.Media -> activateFocusedMedia(item.media)
            is HomeRowItem.ViewAll -> navigateToLibrary(item.platformId, item.sourceFilter)
        }
    }

    /**
     * Touch's equivalent of holding confirm. On a game it opens quick actions; on a media tile it
     * asks whether to start over, which is the only way a touch user reaches that choice. A tile with
     * nothing to resume has no second answer to give, so the hold plays it rather than opening a
     * prompt that offers the same thing twice.
     */
    fun handleItemLongPress(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.currentItems.size) return
        val item = state.currentItems[index]
        if (item is HomeRowItem.Media) {
            if (index != state.focusedGameIndex) {
                _uiState.update { it.copy(focusedGameIndex = index) }
                saveCurrentState()
            }
            if (!mediaDelegate.openResumePrompt(item.media)) {
                startMedia(item.media, startOver = false)
            }
            return
        }
        if (item !is HomeRowItem.Game) return

        if (index != state.focusedGameIndex) {
            _uiState.update { it.copy(focusedGameIndex = index) }
            saveCurrentState()
        }
        toggleGameMenu()
    }

    /**
     * What pressing a game does, wherever it is pressed: play it when it is here, otherwise get it
     * here. One decision for the rail, the game menu and every tile, so a tile pointing at a game
     * that is not downloaded fetches it the way the rail would rather than failing to launch.
     */
    override fun activateGame(game: HomeGameUi) = activate(game, exactRow = false)

    override fun activateExactGame(game: HomeGameUi) = activate(game, exactRow = true)

    fun activateFocusedGame(game: HomeGameUi) =
        activate(game, exactRow = _uiState.value.layoutKind == HomeLayoutKind.CUSTOM_GRID)

    private fun activate(game: HomeGameUi, exactRow: Boolean) {
        val indicator = downloadIndicators.value[game.id] ?: GameDownloadIndicator.NONE
        when (homeGameActivation(game, indicator, exactRow)) {
            HomeGameActivation.INSTALL -> installApk(game.id)
            HomeGameActivation.LAUNCH -> launchGame(game.id)
            HomeGameActivation.RESUME_DOWNLOAD -> resumeDownload(game.id)
            HomeGameActivation.STEAM_DOWNLOAD -> queueSteamDownload(game.id)
            HomeGameActivation.DOWNLOAD_EXACT -> downloadDelegate.queueDownload(viewModelScope, game.id)
            HomeGameActivation.DOWNLOAD_WITH_CHOICE -> queueDownload(game.id)
        }
    }

    override fun launchGame(gameId: Long, channelName: String?) {
        videoPreviewDelegate.deactivateVideoPreview()
        saveCurrentState()
        gameLaunchDelegate.launchGame(
            scope = viewModelScope,
            gameId = gameId,
            channelName = channelName,
            allowVariantPrompt = false,
            onLaunch = { intent ->
                viewModelScope.launch {
                    val options = emulatorLaunchTargetResolver.launchOptionsFor(gameId)
                    _events.emit(HomeEvent.LaunchIntent(intent, options))
                }
            }
        )
    }

    private fun playGameOnDisplay(gameId: Long, displayId: Int) {
        videoPreviewDelegate.deactivateVideoPreview()
        saveCurrentState()
        gameLaunchDelegate.launchGame(
            scope = viewModelScope,
            gameId = gameId,
            allowVariantPrompt = false,
            onLaunch = { intent ->
                viewModelScope.launch {
                    val options = emulatorLaunchTargetResolver.launchOptionsFor(
                        gameId = gameId,
                        overrideDisplayId = displayId
                    )
                    _events.emit(HomeEvent.LaunchIntent(intent, options))
                }
            }
        )
    }

    override fun toggleFavorite(gameId: Long) {
        gameMenuDelegate.toggleFavorite(viewModelScope, gameId) { refreshCurrentRowInternal() }
    }

    /**
     * Unmarks the title under the cursor. Only the Favorites row reaches this, so the answer is
     * always to remove: the row is the set, and the button that put a title in it is the button that
     * takes it back out. The row rebuilds itself from the stored flag, so nothing has to be told.
     */
    override fun unfavoriteMedia(itemId: String) {
        viewModelScope.launch { mediaDelegate.unfavorite(itemId) }
    }

    fun hideGame(gameId: Long) {
        gameMenuDelegate.hideGame(viewModelScope, gameId) { refreshCurrentRowInternal() }
    }

    fun removeFromHome(gameId: Long) {
        gameMenuDelegate.removeFromHome(viewModelScope, gameId) { refreshCurrentRowInternal() }
    }

    fun refreshGameData(gameId: Long) {
        gameMenuDelegate.refreshGameData(viewModelScope, gameId) { refreshCurrentRowInternal() }
    }

    fun refreshAndroidGameData(gameId: Long) {
        gameMenuDelegate.refreshAndroidGameData(viewModelScope, gameId) { refreshCurrentRowInternal() }
    }

    fun deleteLocalFile(gameId: Long) {
        downloadDelegate.deleteLocalFile(viewModelScope, gameId) {
            libraryDelegate.invalidateRecentGamesCache()
            refreshCurrentRowInternal()
        }
    }

    override fun queueDownload(gameId: Long) {
        siblingChoice.requestDownload(viewModelScope, gameId) { chosenGameId ->
            downloadDelegate.queueDownload(viewModelScope, chosenGameId)
        }
    }

    override fun queueSteamDownload(gameId: Long) {
        steamDownloadPromptController.requestSteamDownload(gameId)
    }

    override fun installApk(gameId: Long) {
        downloadDelegate.installApk(viewModelScope, gameId)
    }

    // --- Public API: Game Menu ---

    override fun toggleGameMenu() {
        if (gameMenuDelegate.state.value.showGameMenu) {
            gameMenuOpenJob?.cancel()
            _uiState.update { it.copy(gameMenuDisplays = emptyList()) }
            gameMenuDelegate.toggleGameMenu()
            return
        }
        if (gameMenuOpenJob?.isActive == true) return
        val gameId = _uiState.value.focusedGame?.id
        gameMenuOpenJob = viewModelScope.launch {
            val hasChoice = gameId != null && siblingChoice.hasChoice(gameId)
            if (_uiState.value.focusedGame?.id != gameId) return@launch
            val displays = DualScreenManagerHolder.instance
                ?.focusableDisplays()
                ?.map { screen ->
                    com.nendo.argosy.ui.components.AppLaunchTarget(screen.displayId, screen.number)
                }
                .orEmpty()
            _uiState.update { it.copy(gameMenuDisplays = displays, gameMenuHasSiblingGroup = hasChoice) }
            gameMenuDelegate.toggleGameMenu()
        }
    }

    override fun moveGameMenuFocus(delta: Int) {
        val state = _uiState.value
        val isPlatformRow = state.currentRow is HomeRow.Platform
        val extraRows = if (state.gameMenuDisplays.size > 1 && state.focusedGame?.isDownloaded == true) {
            state.gameMenuDisplays.size
        } else {
            0
        }
        gameMenuDelegate.moveGameMenuFocus(
            delta,
            state.focusedGame,
            isPlatformRow,
            extraRows,
            state.gameMenuHasSiblingGroup
        )
    }

    fun openActiveVariant(gameId: Long) {
        toggleGameMenu()
        siblingChoice.openActiveVariant(viewModelScope, gameId) { }
    }

    fun moveSiblingChoiceFocus(delta: Int) = siblingChoice.moveFocus(delta)

    fun setSiblingChoiceFocus(index: Int) = siblingChoice.setFocus(index)

    fun confirmSiblingChoice() = siblingChoice.confirm(viewModelScope)

    fun dismissSiblingChoice() = siblingChoice.dismiss()

    override fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.focusedGame ?: return
        val isPlatformRow = state.currentRow is HomeRow.Platform

        val action = gameMenuDelegate.resolveMenuAction(
            state.gameMenuFocusIndex,
            game,
            isPlatformRow,
            state.gameMenuDisplays.map { it.displayId },
            state.gameMenuHasSiblingGroup
        )
        when (action) {
            is GameMenuAction.Play -> {
                toggleGameMenu()
                activateFocusedGame(game)
            }
            is GameMenuAction.PlayOnDisplay -> {
                toggleGameMenu()
                playGameOnDisplay(action.gameId, action.displayId)
            }
            is GameMenuAction.ToggleFavorite -> toggleFavorite(action.gameId)
            is GameMenuAction.ViewDetails -> {
                toggleGameMenu()
                gameNavigationContext.setContext(
                    state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
                )
                onGameSelect(action.gameId)
            }
            is GameMenuAction.AddToCollection -> {
                toggleGameMenu()
                showAddToCollectionModal(action.gameId)
            }
            is GameMenuAction.ActiveVariant -> openActiveVariant(action.gameId)
            is GameMenuAction.Refresh -> {
                if (action.isAndroidApp) refreshAndroidGameData(action.gameId)
                else refreshGameData(action.gameId)
            }
            is GameMenuAction.ResyncPlatform -> {
                toggleGameMenu()
                syncPlatform(action.platformId, action.platformName)
            }
            is GameMenuAction.Delete -> {
                toggleGameMenu()
                deleteLocalFile(action.gameId)
            }
            is GameMenuAction.RemoveFromHome -> {
                toggleGameMenu()
                removeFromHome(action.gameId)
            }
            is GameMenuAction.Hide -> {
                toggleGameMenu()
                hideGame(action.gameId)
            }
        }
    }

    fun syncPlatform(platformId: Long, platformName: String) {
        syncDelegate.resyncPlatform(viewModelScope, platformId, platformName) {
            refreshCurrentRowInternal()
        }
    }

    /**
     * Starts one media item. The tile a user confirms wears a series, but what plays is the episode
     * the rail named: the one the server says comes next, or the one that was left part watched. The
     * id handed on here is always that episode's and never the series'.
     */
    private fun playMedia(itemId: String, startOver: Boolean) {
        videoPreviewDelegate.deactivateVideoPreview()
        saveCurrentState()
        viewModelScope.launch { _events.emit(HomeEvent.PlayMedia(itemId, startOver)) }
    }

    override fun playFocusedMedia(startOver: Boolean) {
        val media = _uiState.value.focusedMedia ?: return
        startMedia(media, startOver)
    }

    override fun confirmFocusedMedia() {
        val media = _uiState.value.focusedMedia ?: return
        activateFocusedMedia(media)
    }

    override fun openMediaResumePrompt(): Boolean {
        val media = _uiState.value.focusedMedia ?: return false
        return mediaDelegate.openResumePrompt(media)
    }

    override fun openFocusedMediaDetail() {
        val media = _uiState.value.focusedMedia ?: return
        viewModelScope.launch { _events.emit(HomeEvent.NavigateToMediaDetail(media.detailItemId)) }
    }

    /**
     * What confirm does on a media row with nothing in it. A library row's contents come from the
     * library sync rather than from a rail fetch, so an empty one asks for that instead.
     */
    override fun refreshMediaRails() {
        if (_uiState.value.isMediaLibraryRow) {
            mediaDelegate.refreshLibraries(viewModelScope)
        } else {
            mediaDelegate.refresh(viewModelScope)
        }
    }

    fun dismissMediaResumePrompt() = mediaDelegate.dismissResumePrompt()

    fun resumeMedia(itemId: String) {
        mediaDelegate.dismissResumePrompt()
        playMedia(itemId, startOver = false)
    }

    fun startMediaOver(itemId: String) {
        mediaDelegate.dismissResumePrompt()
        playMedia(itemId, startOver = true)
    }

    // --- Public API: Collection Modal ---

    fun showAddToCollectionModal(gameId: Long) = collectionModalDelegate.show(viewModelScope, gameId)
    override fun dismissAddToCollectionModal() = collectionModalDelegate.dismiss()
    override fun moveCollectionFocusUp() = collectionModalDelegate.moveFocusUp()
    override fun moveCollectionFocusDown() = collectionModalDelegate.moveFocusDown()
    override fun confirmCollectionSelection() { collectionModalDelegate.confirmSelection(viewModelScope) }
    fun toggleGameInCollection(collectionId: Long) = collectionModalDelegate.toggleCollection(viewModelScope, collectionId)
    fun showCreateCollectionFromModal() = collectionModalDelegate.showCreateDialog()
    fun hideCreateCollectionDialog() = collectionModalDelegate.hideCreateDialog()
    fun createCollectionFromModal(name: String) = collectionModalDelegate.createAndAdd(viewModelScope, name)

    // --- Public API: Disc Picker ---

    fun selectDisc(discPath: String) = gameLaunchDelegate.selectDisc(viewModelScope, discPath)
    fun dismissDiscPicker() = gameLaunchDelegate.dismissDiscPicker()
    fun setDiscPickerFocusIndex(index: Int) { _uiState.update { it.copy(discPickerFocusIndex = index) } }

    fun selectMemcard(cardPath: String) = gameLaunchDelegate.selectMemcard(viewModelScope, cardPath)
    fun dismissMemcardPicker() = gameLaunchDelegate.dismissMemcardPicker()
    fun setMemcardPickerFocusIndex(index: Int) { _uiState.update { it.copy(memcardPickerFocusIndex = index) } }

    // --- Public API: Sync & Changelog ---

    override fun syncFromRomm() = syncDelegate.syncFromRomm(viewModelScope) { refreshRecentGames() }
    fun dismissChangelog() = syncDelegate.dismissChangelog(viewModelScope)
    fun handleChangelogAction(action: RequiredAction): RequiredAction = syncDelegate.handleChangelogAction(viewModelScope, action)

    // --- Public API: Video Preview ---

    fun startVideoPreviewLoading(videoId: String) = videoPreviewDelegate.startVideoPreviewLoading(videoId)
    fun activateVideoPreview() = videoPreviewDelegate.activateVideoPreview()
    fun cancelVideoPreviewLoading() = videoPreviewDelegate.cancelVideoPreviewLoading()
    fun deactivateVideoPreview() = videoPreviewDelegate.deactivateVideoPreview()

    // --- Public API: Library ---

    fun refreshRecentGames() { viewModelScope.launch { libraryDelegate.loadRecentGames() } }
    fun refreshFavorites() { viewModelScope.launch { libraryDelegate.loadFavorites() } }
    fun refreshPlatforms() { viewModelScope.launch { libraryDelegate.loadPlatforms() } }
    fun regenerateRecommendations() = libraryDelegate.regenerateRecommendations(viewModelScope)
    fun extractGradientForGame(gameId: Long, bitmap: android.graphics.Bitmap) {
        val isFocused = _uiState.value.focusedGame?.id == gameId
        libraryDelegate.extractGradientForGame(viewModelScope, gameId, bitmap, isFocused)
    }

    fun extractGradientForMedia(itemId: String, bitmap: android.graphics.Bitmap) {
        val isFocused = _uiState.value.focusedMedia?.itemId == itemId
        gradientExtractionDelegate.extractForMedia(viewModelScope, itemId, bitmap, prioritize = isFocused)
    }
    fun repairCoverImage(gameId: Long, failedPath: String) = libraryDelegate.repairCoverImage(viewModelScope, gameId, failedPath)
    fun showLaunchError(message: String) = notificationManager.showError(NotificationText.Raw(message))

    // --- Public API: Lifecycle ---

    fun onResume() {
        libraryDelegate.invalidateRecentGamesCache()
        refreshTileGamesAndFeatures()
        mediaDelegate.refresh(viewModelScope)
        viewModelScope.launch { refreshCurrentRowInternal() }
        syncDelegate.refreshFavoritesIfConnected(viewModelScope) {
            libraryDelegate.loadFavorites()
        }
        viewModelScope.launch {
            libraryDelegate.refreshRecommendationsIfNeeded()
            syncDelegate.checkForChangelog()
        }
    }

    // --- Public API: Input Handler ---

    fun createInputHandler(
        isDefaultView: Boolean,
        onGameSelect: (Long) -> Unit,
        onNavigateToDefault: () -> Unit,
        onDrawerToggle: () -> Unit,
        onScrollOverflow: ((Int) -> Boolean)? = null
    ): InputHandler = HomeInputHandler(
        actions = this,
        isDefaultView = isDefaultView,
        onGameSelect = onGameSelect,
        onNavigateToDefault = onNavigateToDefault,
        onDrawerToggle = onDrawerToggle,
        onScrollOverflow = onScrollOverflow
    )

    // --- HomeInputActions Implementation ---

    override fun resumeDownload(gameId: Long) = downloadDelegate.resumeDownload(gameId)

    override fun navigateToLibrary(platformId: Long?, sourceFilter: String?) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLibrary(platformId, sourceFilter))
        }
    }

    override fun setNavigationContext(gameIds: List<Long>) {
        gameNavigationContext.setContext(gameIds)
    }

    override fun scrollToFirst(): Boolean {
        val state = _uiState.value
        if (!navigationDelegate.scrollToFirstItem(state.focusedGameIndex)) return false
        _uiState.update { it.copy(focusedGameIndex = 0) }
        return true
    }

    override fun navigateToContinuePlaying(): Boolean {
        val state = _uiState.value
        if (!navigationDelegate.navigateToContinuePlaying(state)) return false
        _uiState.update { it.copy(currentRow = HomeRow.Continue, focusedGameIndex = 0) }
        saveCurrentState()
        return true
    }

    // --- Private Helpers ---

    private fun prefetchAchievementsDebounced() {
        achievementPrefetchJob?.cancel()
        achievementPrefetchJob = viewModelScope.launch {
            delay(achievementPrefetchDebounceMs)
            prefetchAchievementsForFocusedGame()
        }
    }

    private fun prefetchAchievementsForFocusedGame() {
        val game = _uiState.value.focusedGame ?: return
        viewModelScope.launch {
            val entity = gameRepository.getById(game.id) ?: return@launch
            val fetchedAt = entity.achievementsFetchedAt
            if (fetchedAt != null && System.currentTimeMillis() - fetchedAt < achievementRefetchThresholdMs) return@launch
            val rommId = entity.rommId
            val raId = entity.effectiveRaId
            if (rommId == null && raId == null && !RAConsoleIds.isSupported(entity.platformSlug)) return@launch
            val counts = fetchAchievementsUseCase(gameId = game.id, rommId = rommId, raId = raId) ?: return@launch
            libraryDelegate.updateAchievementCounts(game.id, counts.total, counts.earned)
        }
    }
}

private fun HomeUiState.clampedToCompletePlatformRow(complete: Boolean): HomeUiState {
    if (!complete || currentRow !is HomeRow.Platform || isPlatformRowLoading) return this
    val lastIndex = (currentItems.size - 1).coerceAtLeast(0)
    return if (focusedGameIndex > lastIndex) copy(focusedGameIndex = lastIndex) else this
}

private fun HomeGameUi.applyGradient(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): HomeGameUi =
    gradients[id]?.takeIf { it != gradientColors }?.let { copy(gradientColors = it) } ?: this

private fun List<HomeGameUi>.applyGradients(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeGameUi> =
    map { it.applyGradient(gradients) }

/**
 * The app bar slot [delta] steps from [current], over the drawer at -1 through `slots - 1`,
 * wrapping at both ends.
 */
internal fun appBarFocusMove(current: Int, delta: Int, slots: Int): Int =
    (current + 1 + delta).mod(slots + 1) - 1

private fun HomeMediaUi.applyMediaGradient(gradients: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): HomeMediaUi =
    gradients[itemId]?.takeIf { it != gradientColors }?.let { copy(gradientColors = it) } ?: this

private fun List<HomeMediaUi>.applyMediaGradients(gradients: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeMediaUi> =
    map { it.applyMediaGradient(gradients) }

private fun List<HomeRowItem>.applyRowGradients(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeRowItem> =
    map { item ->
        when (item) {
            is HomeRowItem.Game -> {
                val game = item.game.applyGradient(gradients)
                if (game === item.game) item else HomeRowItem.Game(game)
            }
            else -> item
        }
    }
