package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.domain.model.RequiredAction
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.ui.screens.home.delegates.GameMenuAction
import com.nendo.argosy.ui.screens.home.delegates.HomeDownloadDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeGameMenuDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import com.nendo.argosy.ui.screens.home.delegates.HomeLibraryDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeNavigationDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeSyncDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeVideoPreviewDelegate
import com.nendo.argosy.ui.screens.home.delegates.PlatformChangeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
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
    val gameMenuDelegate: HomeGameMenuDelegate
) : ViewModel(), HomeInputActions {

    private val _uiState = MutableStateFlow(restoreInitialState())
    override val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var achievementPrefetchJob: Job? = null
    private val achievementPrefetchDebounceMs = 300L
    private var currentBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID

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
        observeSyncOverlay()
        observePlatformChanges()
        observeAchievementUpdates()
        libraryDelegate.observePinnedCollections(viewModelScope)
        observeRecentlyPlayedChanges()
        observeFocusedGameForLed()
        observeCollectionModal()
        observeDelegateStates()
    }

    private fun observeDelegateStates() {
        viewModelScope.launch {
            libraryDelegate.state.collect { lib ->
                _uiState.update {
                    it.copy(
                        platforms = lib.platforms,
                        platformItems = lib.platformItems,
                        recentGames = lib.recentGames,
                        favoriteGames = lib.favoriteGames,
                        recommendedGames = lib.recommendedGames,
                        androidGames = lib.androidGames,
                        steamGames = lib.steamGames,
                        pinnedCollections = lib.pinnedCollections,
                        pinnedGames = lib.pinnedGames,
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
    }

    private fun restoreInitialState(): HomeUiState {
        val (row, gameIndex) = navigationDelegate.restoreInitialRow(savedStateHandle)
        return HomeUiState(currentRow = row, focusedGameIndex = gameIndex)
    }

    private fun saveCurrentState() {
        val state = _uiState.value
        navigationDelegate.saveCurrentState(savedStateHandle, state.currentRow, state.focusedGameIndex)
    }

    private fun flushLibraryState() {
        val lib = libraryDelegate.state.value
        _uiState.update {
            it.copy(
                platforms = lib.platforms,
                platformItems = lib.platformItems,
                recentGames = lib.recentGames,
                favoriteGames = lib.favoriteGames,
                recommendedGames = lib.recommendedGames,
                androidGames = lib.androidGames,
                steamGames = lib.steamGames,
                pinnedCollections = lib.pinnedCollections,
                pinnedGames = lib.pinnedGames,
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
                        customBackgroundPath = prefs.customBackgroundPath
                    )
                }

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

    private fun observePlatformChanges() {
        libraryDelegate.observePlatformChanges(viewModelScope) { currentPlatforms, newPlatformUis ->
            if (newPlatformUis == currentPlatforms) return@observePlatformChanges
            val result = navigationDelegate.reconcilePlatformChange(_uiState.value, currentPlatforms, newPlatformUis)
            when (result) {
                is PlatformChangeResult.Initial -> {
                    _uiState.update { it.copy(platforms = result.platforms, currentRow = result.row, isLoading = false) }
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
            _uiState.update { it.copy(currentRow = startRow, isLoading = false) }
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

    private suspend fun refreshCurrentRowInternal() {
        val state = _uiState.value
        val focusedGameId = state.focusedGame?.id
        val result = libraryDelegate.refreshCurrentRow(state.currentRow, focusedGameId)

        val newIndex = if (focusedGameId != null) {
            result.gameIds.indexOf(focusedGameId)
                .takeIf { it >= 0 } ?: state.focusedGameIndex.coerceAtMost(result.gameIds.lastIndex.coerceAtLeast(0))
        } else state.focusedGameIndex

        flushLibraryState()

        if (result.isEmpty) {
            val newRow = _uiState.value.availableRows.firstOrNull() ?: HomeRow.Continue
            _uiState.update { it.copy(currentRow = newRow, focusedGameIndex = 0) }
        } else {
            _uiState.update { it.copy(focusedGameIndex = newIndex) }
        }
    }

    // --- Public API: Navigation ---

    override fun nextRow() {
        val result = navigationDelegate.nextRow(_uiState.value) ?: return
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        navigationDelegate.loadRowWithDebounce(viewModelScope, result.first) { row ->
            loadRowContent(row)
        }
        saveCurrentState()
    }

    override fun previousRow() {
        val result = navigationDelegate.previousRow(_uiState.value) ?: return
        _uiState.update { it.copy(currentRow = result.first, focusedGameIndex = result.second) }
        navigationDelegate.loadRowWithDebounce(viewModelScope, result.first) { row ->
            loadRowContent(row)
        }
        saveCurrentState()
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
                    else -> downloadDelegate.queueDownload(viewModelScope, game.id)
                }
            }
            is HomeRowItem.ViewAll -> navigateToLibrary(item.platformId, item.sourceFilter)
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

    override fun launchGame(gameId: Long, channelName: String?) {
        videoPreviewDelegate.deactivateVideoPreview()
        saveCurrentState()
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLaunch(gameId, channelName))
        }
    }

    override fun toggleFavorite(gameId: Long) {
        gameMenuDelegate.toggleFavorite(viewModelScope, gameId) { refreshCurrentRowInternal() }
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

    override fun installApk(gameId: Long) {
        downloadDelegate.installApk(viewModelScope, gameId)
    }

    // --- Public API: Game Menu ---

    override fun toggleGameMenu() = gameMenuDelegate.toggleGameMenu()

    override fun moveGameMenuFocus(delta: Int) {
        gameMenuDelegate.moveGameMenuFocus(delta, _uiState.value.focusedGame)
    }

    override fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.focusedGame ?: return

        when (val action = gameMenuDelegate.resolveMenuAction(state.gameMenuFocusIndex, game)) {
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

    // --- Public API: Sync & Changelog ---

    fun syncFromRomm() = syncDelegate.syncFromRomm(viewModelScope) { refreshRecentGames() }
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
    fun extractGradientForGame(gameId: Long, coverPath: String) {
        val isFocused = _uiState.value.focusedGame?.id == gameId
        libraryDelegate.extractGradientForGame(viewModelScope, gameId, coverPath, isFocused)
    }
    fun repairCoverImage(gameId: Long, failedPath: String) = libraryDelegate.repairCoverImage(viewModelScope, gameId, failedPath)
    fun showLaunchError(message: String) = notificationManager.showError(message)

    // --- Public API: Lifecycle ---

    fun onResume() {
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
        libraryDelegate.invalidateRecentGamesCache()
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
            val rommId = entity.rommId ?: return@launch
            val counts = fetchAchievementsUseCase(rommId, game.id) ?: return@launch
            libraryDelegate.updateAchievementCounts(game.id, counts.total, counts.earned)
        }
    }
}
