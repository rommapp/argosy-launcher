package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.core.notification.NotificationManager
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
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
    private val homeGridPageRepository: com.nendo.argosy.data.repository.HomeGridPageRepository,
    private val pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource,
    private val collectionRepository: com.nendo.argosy.data.repository.CollectionRepository,
    private val advanceCollectionFocusUseCase:
        com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase,
    private val prepareCollectionQueueUseCase:
        com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase,
    private val homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
) : ViewModel(), HomeInputActions {

    private val _uiState = MutableStateFlow(restoreInitialState())
    override val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()


    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    private val customGrid = com.nendo.argosy.ui.home.grid.CustomGridCoordinator(
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
        pickerEntries = { category, query ->
            when (category) {
                com.nendo.argosy.ui.components.TilePickerCategory.GAMES ->
                    libraryDelegate.searchInstalledForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.COLLECTIONS ->
                    libraryDelegate.collectionsForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.APPS ->
                    libraryDelegate.appsForTiles(query)
                com.nendo.argosy.ui.components.TilePickerCategory.MEDIA ->
                    mediaDelegate.searchForTiles(query)
            }
        },
        mediaCatalog = tilePickerDelegate,
        read = { _uiState.value.customGrid },
        write = { transform -> _uiState.update { it.copy(customGrid = transform(it.customGrid)) } }
    )

    private var storedTiles: List<com.nendo.argosy.domain.model.HomeTile> = emptyList()
    private var tileMediaShown: Boolean = false

    private var achievementPrefetchJob: Job? = null
    private val achievementPrefetchDebounceMs = 300L
    private val achievementRefetchThresholdMs = 5 * 60 * 1000L
    private var currentBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID
    private var lastShowsEveryGame: Boolean? = null

    init {
        modalResetSignal.signal.onEach {
            gameMenuDelegate.resetMenu()
        }.launchIn(viewModelScope)

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
        observeFocusedGameForLed()
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
                    )
                }
            }
        }
        viewModelScope.launch {
            downloadDelegate.downloadIndicators.collect { indicators ->
                _uiState.update { it.copy(downloadIndicators = indicators) }
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
                        mediaDownloadProgress = media.downloadProgress,
                        isMediaSignedIn = media.isSignedIn,
                        isMediaLoading = media.isLoading,
                        showNextUpRow = media.showNextUp,
                        showContinueWatchingRow = media.showContinueWatching,
                        showMediaLibraryRows = media.showLibraries,
                        mediaResumePrompt = media.resumePrompt
                    )
                    if (updated.holdsCurrentRow) {
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
        val (savedRow, gameIndex) = navigationDelegate.restoreInitialRow(savedStateHandle)
        val preloaded = libraryDelegate.initialLoadComplete
        val effectiveRow = if (preloaded && savedRow == HomeRow.Continue) {
            libraryDelegate.cachedStartRow
        } else {
            savedRow
        }
        return HomeUiState(
            currentRow = effectiveRow,
            focusedGameIndex = gameIndex,
            isLoading = !preloaded
        )
    }

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
            )
        }
    }

    private fun observeFocusedGameForLed() {
        viewModelScope.launch {
            var previousGameId: Long? = null
            _uiState.collect { state ->
                val focusedGame = state.focusedGame
                if (focusedGame != null && focusedGame.id != previousGameId) {
                    previousGameId = focusedGame.id
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

                _uiState.update {
                    it.copy(
                        backgroundBlur = prefs.backgroundBlur,
                        backgroundSaturation = prefs.backgroundSaturation,
                        backgroundOpacity = prefs.backgroundOpacity,
                        useGameBackground = prefs.useGameBackground,
                        customBackgroundPath = prefs.customBackgroundPath,
                        homeBackgroundMode = prefs.homeBackgroundMode,
                        carouselConfig = prefs.homeLayout.carousel,
                        autoGridConfig = prefs.homeLayout.autoGrid,
                        customGridConfig = prefs.homeLayout.customGrid,
                        layoutKind = prefs.homeLayout.selected
                    )
                }
                customGrid.applyConfig(
                    autoFit = prefs.homeLayout.customGrid.autoFit,
                    storedPages = prefs.homeLayout.customGrid.pageCount
                )

                val showsEveryGame = prefs.homeLayout.selected == HomeLayoutKind.AUTO_GRID &&
                    prefs.homeLayout.autoGrid.showAllGames
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
                        }
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
        viewModelScope.launch { libraryDelegate.loadGamesForPlatformInternal(platform.id, row.index) }
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
    private suspend fun refreshCurrentRowInternal() {
        val state = _uiState.value
        if (state.isMediaRow) {
            flushLibraryState()
            return
        }
        val focusedGameId = state.focusedGame?.id
        val result = libraryDelegate.refreshCurrentRow(state.currentRow, focusedGameId)

        val newIndex = if (focusedGameId != null) {
            result.gameIds.indexOf(focusedGameId)
                .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(result.gameIds.lastIndex.coerceAtLeast(0))
        } else state.focusedGameIndex

        flushLibraryState()

        if (result.isEmpty && _uiState.value.currentItems.isEmpty()) {
            val newRow = _uiState.value.availableRows.firstOrNull() ?: HomeRow.Continue
            _uiState.update { it.copy(currentRow = newRow, focusedGameIndex = 0) }
        } else {
            _uiState.update {
                it.copy(
                    focusedGameIndex = newIndex
                        .coerceIn(0, (it.currentItems.size - 1).coerceAtLeast(0))
                )
            }
        }
    }

    // --- Public API: Navigation ---

    override fun nextRow() {
        val result = navigationDelegate.nextRow(_uiState.value) ?: return
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRowWithDebounce(viewModelScope, result.first) { row ->
            loadRowContent(row)
        }
        saveCurrentState()
    }

    fun selectRow(row: HomeRow) {
        val state = _uiState.value
        if (row == state.currentRow || row !in state.availableRows) return
        _uiState.update { it.copy(currentRow = row, focusedGameIndex = 0) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRowWithDebounce(viewModelScope, row) { loadRowContent(it) }
        saveCurrentState()
    }

    override fun previousRow() {
        val result = navigationDelegate.previousRow(_uiState.value) ?: return
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        syncSelectedMediaLibrary()
        navigationDelegate.loadRowWithDebounce(viewModelScope, result.first) { row ->
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
                    libraryDelegate.loadGamesForPlatformInternal(platform.id, row.index)
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
            title = "Playing next",
            subtitle = result.nextTitle,
            type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
            duration = com.nendo.argosy.core.notification.NotificationDuration.SHORT
        )
        return result.nextGameId
    }

    private fun observeHomeTiles() {
        viewModelScope.launch {
            homeTileRepository.observeTiles(syncPreferencesRepository.getRommUserId())
                .collect { tiles ->
                    storedTiles = tiles
                    publishHomeTiles(tiles)
                }
        }
        customGrid.observePageSettings { applyPageAudio() }
    }

    /**
     * Hands the grid the tiles it can actually draw, and resolves what each one points at.
     *
     * The stored list is kept whole here and filtered on the way out, so hiding media while signed
     * out never writes anything: the page the database holds is still the page the user arranged.
     */
    private suspend fun publishHomeTiles(tiles: List<com.nendo.argosy.domain.model.HomeTile>) {
        val shown = if (tileMediaShown) {
            tiles
        } else {
            tiles.filterNot { it.target is HomeTileTargetRef.Media }
        }
        val games = libraryDelegate.resolveTileGames(
            shown.mapNotNull {
                when (val target = it.target) {
                    is HomeTileTargetRef.Game -> target.gameId
                    is HomeTileTargetRef.Collection -> target.focusGameId
                    else -> null
                }
            }.distinct()
        )
        val collections = libraryDelegate.resolveTileCollections(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.Collection)?.collectionId }.distinct()
        )
        val apps = libraryDelegate.resolveTileApps(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.App)?.packageName }.distinct()
        )
        mediaDelegate.selectTileItems(
            shown.mapNotNull { (it.target as? HomeTileTargetRef.Media)?.itemId }.distinct()
        )
        customGrid.setTiles(shown)
        _uiState.update {
            it.copy(tileGames = games, tileCollections = collections, tileApps = apps)
        }
        resolveTilePlayback(shown)
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
    fun setCustomGridShape(columns: Int, rows: Int) = customGrid.setShape(columns, rows)

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
    override fun launchTileApp(packageName: String) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val options = if (prefs.appAffinityEnabled) {
                displayAffinityHelper.getActivityOptions(forEmulator = false)
            } else {
                null
            }
            _events.emit(HomeEvent.LaunchIntent(intent, options))
        }
    }

    override fun openTileCollection(collectionId: Long) {
        viewModelScope.launch { _events.emit(HomeEvent.NavigateToCollections(collectionId)) }
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
        val released = customGrid.disengageTile()
        if (released) videoPreviewDelegate.releaseTileAudio()
        return released
    }

    override fun toggleEngagedPlayback() = customGrid.toggleEngagedPlayback()

    override fun seekEngagedTile(forward: Boolean) = customGrid.seekEngagedTile(forward)

    fun rememberTilePlaybackPosition(filePath: String, positionMs: Long) =
        customGrid.rememberPlaybackPosition(filePath, positionMs)

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
        val reached = grid.tilePlayback[engaged.id]?.let { grid.playbackPositions[it] } ?: 0L
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
            is HomeRowItem.Game -> {
                val game = item.game
                val indicator = state.downloadIndicatorFor(game.id)
                when {
                    game.needsInstall -> downloadDelegate.installApk(viewModelScope, game.id)
                    game.isDownloaded -> launchGame(game.id)
                    indicator.isPaused || indicator.isQueued -> downloadDelegate.resumeDownload(game.id)
                    game.isSteamGame -> queueSteamDownload(game.id)
                    else -> downloadDelegate.queueDownload(viewModelScope, game.id)
                }
            }
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
                    val options = displayAffinityHelper.getActivityOptions(
                        forEmulator = true, rolesSwapped = sessionStateStore.isRolesSwapped()
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
        downloadDelegate.queueDownload(viewModelScope, gameId)
    }

    override fun queueSteamDownload(gameId: Long) {
        steamDownloadPromptController.requestSteamDownload(gameId)
    }

    override fun installApk(gameId: Long) {
        downloadDelegate.installApk(viewModelScope, gameId)
    }

    // --- Public API: Game Menu ---

    override fun toggleGameMenu() = gameMenuDelegate.toggleGameMenu()

    override fun moveGameMenuFocus(delta: Int) {
        val state = _uiState.value
        val isPlatformRow = state.currentRow is HomeRow.Platform
        gameMenuDelegate.moveGameMenuFocus(delta, state.focusedGame, isPlatformRow)
    }

    override fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.focusedGame ?: return
        val isPlatformRow = state.currentRow is HomeRow.Platform

        when (val action = gameMenuDelegate.resolveMenuAction(state.gameMenuFocusIndex, game, isPlatformRow)) {
            is GameMenuAction.Play -> {
                toggleGameMenu()
                when {
                    action.needsInstall -> installApk(action.gameId)
                    action.isDownloaded -> launchGame(action.gameId)
                    else -> queueDownload(action.gameId)
                }
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
    fun showLaunchError(message: String) = notificationManager.showError(message)

    // --- Public API: Lifecycle ---

    fun onResume() {
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
        libraryDelegate.invalidateRecentGamesCache()
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
        onDrawerToggle: () -> Unit
    ): InputHandler = HomeInputHandler(
        actions = this,
        isDefaultView = isDefaultView,
        onGameSelect = onGameSelect,
        onNavigateToDefault = onNavigateToDefault,
        onDrawerToggle = onDrawerToggle
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

private fun HomeGameUi.applyGradient(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): HomeGameUi =
    gradients[id]?.let { copy(gradientColors = it) } ?: this

private fun List<HomeGameUi>.applyGradients(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeGameUi> =
    map { it.applyGradient(gradients) }

private fun HomeMediaUi.applyMediaGradient(gradients: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): HomeMediaUi =
    gradients[itemId]?.takeIf { it != gradientColors }?.let { copy(gradientColors = it) } ?: this

private fun List<HomeMediaUi>.applyMediaGradients(gradients: Map<String, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeMediaUi> =
    map { it.applyMediaGradient(gradients) }

private fun List<HomeRowItem>.applyRowGradients(gradients: Map<Long, Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color>>): List<HomeRowItem> =
    map { item ->
        when (item) {
            is HomeRowItem.Game -> HomeRowItem.Game(item.game.applyGradient(gradients))
            else -> item
        }
    }
