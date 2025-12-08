package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
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
import android.content.Intent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val PLATFORM_GAMES_LIMIT = 20
private const val RECENT_GAMES_LIMIT = 10
private const val AUTO_SYNC_DAYS = 7L
private const val MENU_INDEX_MAX_DOWNLOADED = 4
private const val MENU_INDEX_MAX_REMOTE = 3

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
    val rating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE
)

sealed class HomeRowItem {
    data class Game(val game: HomeGameUi) : HomeRowItem()
    data class ViewAll(
        val platformId: String,
        val platformName: String,
        val logoPath: String?
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
    val downloadIndicators: Map<Long, GameDownloadIndicator> = emptyMap()
) {
    val availableRows: List<HomeRow>
        get() = buildList {
            if (favoriteGames.isNotEmpty()) add(HomeRow.Favorites)
            platforms.forEachIndexed { index, _ -> add(HomeRow.Platform(index)) }
            if (recentGames.isNotEmpty()) add(HomeRow.Continue)
        }

    val currentPlatform: HomePlatformUi?
        get() = (currentRow as? HomeRow.Platform)?.let { platforms.getOrNull(it.index) }

    val currentItems: List<HomeRowItem>
        get() = when (currentRow) {
            HomeRow.Favorites -> favoriteGames.map { HomeRowItem.Game(it) }
            is HomeRow.Platform -> platformItems
            HomeRow.Continue -> recentGames.map { HomeRowItem.Game(it) }
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
    data class NavigateToLibrary(val platformId: String) : HomeEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val gameNavigationContext: GameNavigationContext,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val launchGameUseCase: LaunchGameUseCase,
    private val downloadManager: DownloadManager,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private val rowGameIndexes = mutableMapOf<HomeRow, Int>()
    private var platformGamesJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
        initializeRomM()
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
            val totalGames = gameDao.countAll()
            if (totalGames == 0) {
                _uiState.update { it.copy(isLoading = false) }
            }
            launch { loadRecentGames() }
            launch { loadFavorites() }
            launch { loadPlatforms() }
            launch { observeDownloadState() }
        }
    }

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

            _uiState.update { it.copy(downloadIndicators = indicators) }
        }
    }

    private suspend fun loadRecentGames() {
        gameDao.observeRecentlyPlayed(RECENT_GAMES_LIMIT).collect { games ->
            val filtered = games.filter { it.lastPlayed != null }
            val gameUis = filtered.map { it.toUi() }
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
    }

    private suspend fun loadFavorites() {
        gameDao.observeFavorites().collect { games ->
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
    }

    private suspend fun loadPlatforms() {
        platformDao.observePlatformsWithGames().collect { platforms ->
            val platformUis = platforms.map { it.toUi() }
            _uiState.update { state ->
                val shouldSwitchRow = platforms.isNotEmpty() &&
                    state.currentRow == HomeRow.Continue &&
                    state.recentGames.isEmpty()
                state.copy(
                    platforms = platformUis,
                    currentRow = if (shouldSwitchRow) HomeRow.Platform(0) else state.currentRow
                )
            }
            if (platforms.isNotEmpty() && _uiState.value.platformItems.isEmpty()) {
                loadGamesForPlatform(platforms.first().id, platformIndex = 0, setLoadingFalse = true)
            }
        }
    }

    private fun loadGamesForPlatform(
        platformId: String,
        platformIndex: Int,
        useSavedIndex: Boolean = false,
        setLoadingFalse: Boolean = false
    ) {
        platformGamesJob?.cancel()
        platformGamesJob = viewModelScope.launch {
            var isFirstEmission = true
            gameDao.observeByPlatformSorted(platformId, limit = PLATFORM_GAMES_LIMIT).collect { games ->
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
                    val shouldClearLoading = setLoadingFalse && isFirstEmission
                    if (useSavedIndex && isFirstEmission) {
                        val row = HomeRow.Platform(platformIndex)
                        val savedIndex = rowGameIndexes[row] ?: 0
                        isFirstEmission = false
                        state.copy(
                            platformItems = items,
                            focusedGameIndex = savedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                            isLoading = if (shouldClearLoading) false else state.isLoading
                        )
                    } else {
                        val focusedGameId = state.focusedGame?.id
                        val newIndex = if (focusedGameId != null) {
                            items.indexOfFirst { (it as? HomeRowItem.Game)?.game?.id == focusedGameId }
                                .takeIf { it >= 0 } ?: state.focusedGameIndex
                        } else {
                            state.focusedGameIndex
                        }
                        isFirstEmission = false
                        state.copy(
                            platformItems = items,
                            focusedGameIndex = newIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                            isLoading = if (shouldClearLoading) false else state.isLoading
                        )
                    }
                }
            }
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

        if (nextRow is HomeRow.Platform) {
            _uiState.update { it.copy(currentRow = nextRow, focusedGameIndex = savedIndex, platformItems = emptyList()) }
            val platform = state.platforms.getOrNull(nextRow.index)
            if (platform != null) {
                loadGamesForPlatform(platform.id, nextRow.index, useSavedIndex = true)
            }
        } else {
            _uiState.update { it.copy(currentRow = nextRow, focusedGameIndex = savedIndex) }
        }
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

        if (prevRow is HomeRow.Platform) {
            _uiState.update { it.copy(currentRow = prevRow, focusedGameIndex = savedIndex, platformItems = emptyList()) }
            val platform = state.platforms.getOrNull(prevRow.index)
            if (platform != null) {
                loadGamesForPlatform(platform.id, prevRow.index, useSavedIndex = true)
            }
        } else {
            _uiState.update { it.copy(currentRow = prevRow, focusedGameIndex = savedIndex) }
        }
    }

    fun nextGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex >= state.currentItems.size - 1) return false
        _uiState.update { it.copy(focusedGameIndex = state.focusedGameIndex + 1) }
        return true
    }

    fun previousGame(): Boolean {
        val state = _uiState.value
        if (state.currentItems.isEmpty()) return false
        if (state.focusedGameIndex <= 0) return false
        _uiState.update { it.copy(focusedGameIndex = state.focusedGameIndex - 1) }
        return true
    }

    private fun scrollToFirstItem(): Boolean {
        val state = _uiState.value

        if (state.focusedGameIndex == 0) {
            return false
        }

        _uiState.update { it.copy(focusedGameIndex = 0) }
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
            val maxIndex = if (it.focusedGame?.isDownloaded == true) MENU_INDEX_MAX_DOWNLOADED else MENU_INDEX_MAX_REMOTE
            val newIndex = (it.gameMenuFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(gameMenuFocusIndex = newIndex)
        }
    }

    fun confirmGameMenuSelection(onGameSelect: (Long) -> Unit) {
        val state = _uiState.value
        val game = state.focusedGame ?: return
        when (state.gameMenuFocusIndex) {
            0 -> {
                toggleGameMenu()
                if (game.isDownloaded) {
                    launchGame(game.id)
                } else {
                    queueDownload(game.id)
                }
            }
            1 -> toggleFavorite(game.id)
            2 -> {
                toggleGameMenu()
                gameNavigationContext.setContext(
                    state.currentItems.filterIsInstance<HomeRowItem.Game>().map { it.game.id }
                )
                onGameSelect(game.id)
            }
            3 -> {
                if (game.isDownloaded) {
                    toggleGameMenu()
                    deleteLocalFile(game.id)
                } else {
                    toggleGameMenu()
                    hideGame(game.id)
                }
            }
            4 -> {
                toggleGameMenu()
                hideGame(game.id)
            }
        }
    }

    private fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            gameActions.toggleFavorite(gameId)
        }
    }

    private fun hideGame(gameId: Long) {
        viewModelScope.launch {
            gameActions.hideGame(gameId)
        }
    }

    private fun deleteLocalFile(gameId: Long) {
        viewModelScope.launch {
            gameActions.deleteLocalFile(gameId)
            notificationManager.showSuccess("Download deleted")
        }
    }

    private var lastDownloadQueueTime = 0L
    private val downloadQueueDebounceMs = 300L

    private fun queueDownload(gameId: Long) {
        val now = System.currentTimeMillis()
        if (now - lastDownloadQueueTime < downloadQueueDebounceMs) return
        lastDownloadQueueTime = now

        viewModelScope.launch {
            when (val result = gameActions.queueDownload(gameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.Error -> notificationManager.showError(result.message)
            }
        }
    }

    private fun resumeDownload(gameId: Long) {
        downloadManager.resumeDownload(gameId)
    }

    fun launchGame(gameId: Long) {
        viewModelScope.launch {
            when (val result = launchGameUseCase(gameId)) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    _events.emit(HomeEvent.LaunchGame(result.intent))
                }
                is LaunchResult.NoEmulator -> {
                    notificationManager.showError("No emulator installed for this platform")
                }
                is LaunchResult.NoRomFile -> {
                    notificationManager.showError("ROM file not found")
                }
                is LaunchResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
        }
    }

    private fun navigateToLibrary(platformId: String) {
        viewModelScope.launch {
            _events.emit(HomeEvent.NavigateToLibrary(platformId))
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
        return HomeGameUi(
            id = id,
            title = title,
            coverPath = coverPath,
            backgroundPath = backgroundPath ?: firstScreenshot ?: coverPath,
            developer = developer,
            releaseYear = releaseYear,
            genre = genre,
            isFavorite = isFavorite,
            isDownloaded = localPath != null,
            rating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty
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
                        navigateToLibrary(item.platformId)
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
            return if (scrollToFirstItem()) InputResult.HANDLED else InputResult.UNHANDLED
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
            val game = _uiState.value.focusedGame ?: return InputResult.UNHANDLED
            onGameSelect(game.id)
            return InputResult.HANDLED
        }
    }
}
