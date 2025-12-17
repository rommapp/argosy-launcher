package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
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
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val PLATFORM_GAMES_LIMIT = 20
private const val RECENT_GAMES_LIMIT = 10
private const val RECENT_GAMES_CANDIDATE_POOL = 40
private const val AUTO_SYNC_DAYS = 7L
private const val MENU_INDEX_MAX_DOWNLOADED = 4
private const val MENU_INDEX_MAX_REMOTE = 3

private const val KEY_ROW_TYPE = "home_row_type"
private const val KEY_PLATFORM_INDEX = "home_platform_index"
private const val KEY_GAME_INDEX = "home_game_index"

private const val ROW_TYPE_FAVORITES = "favorites"
private const val ROW_TYPE_PLATFORM = "platform"
private const val ROW_TYPE_CONTINUE = "continue"

data class GameDownloadIndicator(
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val isQueued: Boolean = false,
    val progress: Float = 0f
) {
    val isActive: Boolean get() = isDownloading || isPaused || isQueued

    companion object {
        val NONE = GameDownloadIndicator()
    }
}

data class HomeGameUi(
    val id: Long,
    val title: String,
    val coverPath: String?,
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
    val downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE
)

sealed class HomeRowItem {
    data class Game(val game: HomeGameUi) : HomeRowItem()
    data class ViewAll(
        val platformId: String? = null,
        val platformName: String? = null,
        val logoPath: String? = null,
        val sourceFilter: String? = null,
        val label: String = "View All"
    ) : HomeRowItem()
}

data class HomePlatformUi(
    val id: String,
    val name: String,
    val shortName: String,
    val logoPath: String?
)

sealed class HomeRow {
    data object Favorites : HomeRow()
    data class Platform(val index: Int) : HomeRow()
    data object Continue : HomeRow()
}

data class HomeUiState(
    val platforms: List<HomePlatformUi> = emptyList(),
    val platformItems: List<HomeRowItem> = emptyList(),
    val focusedGameIndex: Int = 0,
    val recentGames: List<HomeGameUi> = emptyList(),
    val favoriteGames: List<HomeGameUi> = emptyList(),
    val currentRow: HomeRow = HomeRow.Continue,
    val isLoading: Boolean = true,
    val isRommConfigured: Boolean = false,
    val showGameMenu: Boolean = false,
    val gameMenuFocusIndex: Int = 0,
    val downloadIndicators: Map<Long, GameDownloadIndicator> = emptyMap(),
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val syncOverlayState: SyncOverlayState? = null
) {
    val availableRows: List<HomeRow>
        get() = buildList {
            if (recentGames.isNotEmpty()) add(HomeRow.Continue)
            if (favoriteGames.isNotEmpty()) add(HomeRow.Favorites)
            platforms.forEachIndexed { index, _ -> add(HomeRow.Platform(index)) }
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
        }

    fun downloadIndicatorFor(gameId: Long): GameDownloadIndicator =
        downloadIndicators[gameId] ?: GameDownloadIndicator.NONE
}

sealed class HomeEvent {
    data class LaunchGame(val intent: Intent) : HomeEvent()
    data class NavigateToLibrary(
        val platformId: String? = null,
        val sourceFilter: String? = null
    ) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val gameNavigationContext: GameNavigationContext,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val downloadManager: DownloadManager,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val fetchAchievementsUseCase: FetchAchievementsUseCase
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

    private var cachedValidatedRecentGames: List<HomeGameUi>? = null
    private var recentGamesCacheInvalid = true

    init {
        loadData()
        initializeRomM()
        observeBackgroundSettings()
        observeSyncOverlay()
        observePlatformChanges()
    }

    private fun observeSyncOverlay() {
        viewModelScope.launch {
            gameLaunchDelegate.syncOverlayState.collect { overlayState ->
                _uiState.update { it.copy(syncOverlayState = overlayState) }
            }
        }
    }

    private fun observeBackgroundSettings() {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        backgroundBlur = prefs.backgroundBlur,
                        backgroundSaturation = prefs.backgroundSaturation,
                        backgroundOpacity = prefs.backgroundOpacity,
                        useGameBackground = prefs.useGameBackground,
                        customBackgroundPath = prefs.customBackgroundPath
                    )
                }
            }
        }
    }

    private fun observePlatformChanges() {
        viewModelScope.launch {
            platformDao.observePlatformsWithGames().collect { platforms ->
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
                        val newRow = when (val row = state.currentRow) {
                            is HomeRow.Platform -> {
                                val currentPlatformId = currentPlatforms.getOrNull(row.index)?.id
                                val newIndex = currentPlatformId?.let { id ->
                                    newPlatformUis.indexOfFirst { it.id == id }
                                }?.takeIf { it >= 0 }

                                when {
                                    newIndex != null -> HomeRow.Platform(newIndex)
                                    newPlatformUis.isNotEmpty() -> HomeRow.Platform(0)
                                    else -> state.availableRows.firstOrNull() ?: HomeRow.Continue
                                }
                            }
                            else -> row
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

    private fun restoreInitialState(): HomeUiState {
        val rowType = savedStateHandle.get<String>(KEY_ROW_TYPE)
        val platformIndex = savedStateHandle.get<Int>(KEY_PLATFORM_INDEX) ?: 0
        val gameIndex = savedStateHandle.get<Int>(KEY_GAME_INDEX) ?: 0

        val currentRow = when (rowType) {
            ROW_TYPE_FAVORITES -> HomeRow.Favorites
            ROW_TYPE_PLATFORM -> HomeRow.Platform(platformIndex)
            ROW_TYPE_CONTINUE -> HomeRow.Continue
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
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val platforms = platformDao.getPlatformsWithGames()
            val favorites = gameDao.getFavorites()

            val candidatePool = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            val validatedRecent = mutableListOf<HomeGameUi>()
            for (game in candidatePool) {
                if (validatedRecent.size >= RECENT_GAMES_LIMIT) break
                val isPlayable = when {
                    game.source == GameSource.STEAM -> true
                    game.localPath != null -> File(game.localPath).exists()
                    else -> false
                }
                if (isPlayable) {
                    validatedRecent.add(game.toUi())
                }
            }
            cachedValidatedRecentGames = validatedRecent
            recentGamesCacheInvalid = false

            val platformUis = platforms.map { it.toUi() }
            val favoriteUis = favorites.map { it.toUi() }

            val startRow = when {
                validatedRecent.isNotEmpty() -> HomeRow.Continue
                favoriteUis.isNotEmpty() -> HomeRow.Favorites
                platformUis.isNotEmpty() -> HomeRow.Platform(0)
                else -> HomeRow.Continue
            }

            _uiState.update { state ->
                state.copy(
                    platforms = platformUis,
                    recentGames = validatedRecent,
                    favoriteGames = favoriteUis,
                    currentRow = startRow,
                    isLoading = false
                )
            }

            // Load platform games if starting on a platform row
            if (startRow is HomeRow.Platform) {
                val platform = platforms.getOrNull(startRow.index)
                if (platform != null) {
                    loadGamesForPlatform(platform.id, startRow.index)
                }
            }

            // Start observing download state
            launch { observeDownloadState() }
        }
    }

    private val completedGameIds = mutableSetOf<Long>()

    private suspend fun observeDownloadState() {
        downloadManager.state.collect { downloadState ->
            val indicators = mutableMapOf<Long, GameDownloadIndicator>()

            downloadState.activeDownloads.forEach { download ->
                indicators[download.gameId] = GameDownloadIndicator(
                    isDownloading = true,
                    progress = download.progressPercent
                )
            }

            downloadState.queue.forEach { download ->
                val isPaused = download.state == DownloadState.PAUSED
                val isQueued = download.state == DownloadState.QUEUED
                if (isPaused || isQueued) {
                    indicators[download.gameId] = GameDownloadIndicator(
                        isDownloading = false,
                        isPaused = isPaused,
                        isQueued = isQueued,
                        progress = download.progressPercent
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
        val gameUis = if (!recentGamesCacheInvalid && cachedValidatedRecentGames != null) {
            cachedValidatedRecentGames!!
        } else {
            val candidatePool = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
            val validated = mutableListOf<HomeGameUi>()

            for (game in candidatePool) {
                if (validated.size >= RECENT_GAMES_LIMIT) break

                val isPlayable = when {
                    game.source == GameSource.STEAM -> true
                    game.localPath != null -> File(game.localPath).exists()
                    else -> false
                }

                if (isPlayable) {
                    validated.add(game.toUi())
                }
            }

            cachedValidatedRecentGames = validated
            recentGamesCacheInvalid = false
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
        val games = gameDao.getFavorites()
        val gameUis = games.map { it.toUi() }
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

    private suspend fun loadPlatforms() {
        val platforms = platformDao.getPlatformsWithGames()
        val platformUis = platforms.map { it.toUi() }
        _uiState.update { state ->
            val shouldSwitchRow = platforms.isNotEmpty() &&
                state.currentRow == HomeRow.Continue &&
                state.recentGames.isEmpty()
            state.copy(
                platforms = platformUis,
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
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
        invalidateRecentGamesCache()
    }

    private fun invalidateRecentGamesCache() {
        recentGamesCacheInvalid = true
        cachedValidatedRecentGames = null
    }

    private fun loadGamesForPlatform(platformId: String, platformIndex: Int) {
        viewModelScope.launch {
            loadGamesForPlatformInternal(platformId, platformIndex)
        }
    }

    private suspend fun loadGamesForPlatformInternal(platformId: String, platformIndex: Int) {
        val games = gameDao.getByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT)
        val platform = _uiState.value.platforms.getOrNull(platformIndex)
        val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi()) }
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
            }
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
        return true
    }

    private fun prefetchAchievementsDebounced() {
        achievementPrefetchJob?.cancel()
        achievementPrefetchJob = viewModelScope.launch {
            delay(achievementPrefetchDebounceMs)
            prefetchAchievementsForFocusedGame()
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
    }

    fun handleItemTap(index: Int, onGameSelect: (Long) -> Unit) {
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
            val isRommGame = game?.isRommGame == true
            var maxIndex = if (isDownloaded) MENU_INDEX_MAX_DOWNLOADED else MENU_INDEX_MAX_REMOTE
            if (isRommGame) maxIndex++
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

        var currentIdx = 0
        val playIdx = currentIdx++
        val favoriteIdx = currentIdx++
        val detailsIdx = currentIdx++
        val refreshIdx = if (isRommGame) currentIdx++ else -1
        val deleteIdx = if (isDownloaded) currentIdx++ else -1
        val hideIdx = currentIdx

        when (index) {
            playIdx -> {
                toggleGameMenu()
                if (isDownloaded) launchGame(game.id) else queueDownload(game.id)
            }
            favoriteIdx -> toggleFavorite(game.id)
            detailsIdx -> {
                toggleGameMenu()
                gameNavigationContext.setContext(
                    state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
                )
                onGameSelect(game.id)
            }
            refreshIdx -> refreshGameData(game.id)
            deleteIdx -> {
                toggleGameMenu()
                deleteLocalFile(game.id)
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

    fun deleteLocalFile(gameId: Long) {
        viewModelScope.launch {
            gameActions.deleteLocalFile(gameId)
            notificationManager.showSuccess("Download deleted")
            invalidateRecentGamesCache()
            refreshCurrentRowInternal()
        }
    }

    private suspend fun refreshCurrentRowInternal() {
        val state = _uiState.value
        val focusedGameId = state.focusedGame?.id

        when (val row = state.currentRow) {
            HomeRow.Favorites -> {
                val games = gameDao.getFavorites()
                val gameUis = games.map { it.toUi() }
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
                val candidatePool = gameDao.getRecentlyPlayed(RECENT_GAMES_CANDIDATE_POOL)
                val validated = mutableListOf<HomeGameUi>()

                for (game in candidatePool) {
                    if (validated.size >= RECENT_GAMES_LIMIT) break
                    val isPlayable = when {
                        game.source == GameSource.STEAM -> true
                        game.localPath != null -> File(game.localPath).exists()
                        else -> false
                    }
                    if (isPlayable) {
                        validated.add(game.toUi())
                    }
                }

                cachedValidatedRecentGames = validated
                recentGamesCacheInvalid = false

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
                val gameItems: List<HomeRowItem> = games.map { HomeRowItem.Game(it.toUi()) }
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
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
            }
        }
    }

    private fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun launchGame(gameId: Long) {
        saveCurrentState()
        gameLaunchDelegate.launchGame(viewModelScope, gameId) { intent ->
            viewModelScope.launch { _events.emit(HomeEvent.LaunchGame(intent)) }
        }
    }

    private fun navigateToLibrary(platformId: String? = null, sourceFilter: String? = null) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLibrary(platformId, sourceFilter))
        }
    }

    fun syncFromRomm() {
        viewModelScope.launch {
            when (val result = syncLibraryUseCase(initializeFirst = true)) {
                is SyncLibraryResult.Error -> notificationManager.showError(result.message)
                is SyncLibraryResult.Success -> { }
            }
        }
    }

    fun showLaunchError(message: String) {
        notificationManager.showError(message)
    }

    private fun PlatformEntity.toUi() = HomePlatformUi(
        id = id,
        name = name,
        shortName = shortName,
        logoPath = logoPath
    )

    private fun GameEntity.toUi(): HomeGameUi {
        val firstScreenshot = screenshotPaths?.split(",")?.firstOrNull()?.takeIf { it.isNotBlank() }
        val effectiveBackground = backgroundPath ?: firstScreenshot ?: coverPath
        return HomeGameUi(
            id = id,
            title = title,
            coverPath = coverPath,
            backgroundPath = effectiveBackground,
            developer = developer,
            releaseYear = releaseYear,
            genre = genre,
            isFavorite = isFavorite,
            isDownloaded = localPath != null || source == GameSource.STEAM,
            isRommGame = rommId != null,
            rating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            achievementCount = achievementCount,
            earnedAchievementCount = earnedAchievementCount
        )
    }

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onDrawerToggle: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            return if (_uiState.value.showGameMenu) {
                moveGameMenuFocus(-1)
                InputResult.HANDLED
            } else {
                previousRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }

        override fun onDown(): InputResult {
            return if (_uiState.value.showGameMenu) {
                moveGameMenuFocus(1)
                InputResult.HANDLED
            } else {
                nextRow()
                InputResult.handled(SoundType.SECTION_CHANGE)
            }
        }

        override fun onLeft(): InputResult {
            if (_uiState.value.showGameMenu) return InputResult.HANDLED
            return if (previousGame()) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            if (_uiState.value.showGameMenu) return InputResult.HANDLED
            return if (nextGame()) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            if (_uiState.value.showGameMenu) {
                confirmGameMenuSelection(onGameSelect)
            } else {
                when (val item = _uiState.value.focusedItem) {
                    is HomeRowItem.Game -> {
                        val game = item.game
                        val indicator = _uiState.value.downloadIndicatorFor(game.id)
                        when {
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
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (_uiState.value.showGameMenu) {
                toggleGameMenu()
                return InputResult.HANDLED
            }
            if (scrollToFirstItem()) {
                return InputResult.HANDLED
            }
            if (navigateToContinuePlaying()) {
                return InputResult.handled(SoundType.SECTION_CHANGE)
            }
            return InputResult.UNHANDLED
        }

        override fun onMenu(): InputResult {
            if (_uiState.value.showGameMenu) {
                toggleGameMenu()
                return InputResult.UNHANDLED
            }
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
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
