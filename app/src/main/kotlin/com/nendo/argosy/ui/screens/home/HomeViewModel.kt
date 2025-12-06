package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationDuration
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import android.content.Intent
import android.util.Log
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

private const val TAG = "HomeViewModel"

data class HomeGameUi(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val backgroundPath: String?,
    val developer: String?,
    val releaseYear: Int?,
    val genre: String?,
    val isFavorite: Boolean,
    val isDownloaded: Boolean
)

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
    val platformGames: List<HomeGameUi> = emptyList(),
    val focusedGameIndex: Int = 0,
    val recentGames: List<HomeGameUi> = emptyList(),
    val favoriteGames: List<HomeGameUi> = emptyList(),
    val currentRow: HomeRow = HomeRow.Continue,
    val isLoading: Boolean = true,
    val showGameMenu: Boolean = false,
    val gameMenuFocusIndex: Int = 0
) {
    val availableRows: List<HomeRow>
        get() = buildList {
            if (favoriteGames.isNotEmpty()) add(HomeRow.Favorites)
            platforms.forEachIndexed { index, _ -> add(HomeRow.Platform(index)) }
            if (recentGames.isNotEmpty()) add(HomeRow.Continue)
        }

    val currentPlatform: HomePlatformUi?
        get() = (currentRow as? HomeRow.Platform)?.let { platforms.getOrNull(it.index) }

    val currentGames: List<HomeGameUi>
        get() = when (currentRow) {
            HomeRow.Favorites -> favoriteGames
            is HomeRow.Platform -> platformGames
            HomeRow.Continue -> recentGames
        }

    val focusedGame: HomeGameUi?
        get() = currentGames.getOrNull(focusedGameIndex)

    val rowTitle: String
        get() = when (currentRow) {
            HomeRow.Favorites -> "Favorites"
            is HomeRow.Platform -> currentPlatform?.name ?: "Unknown"
            HomeRow.Continue -> "Continue Playing"
        }
}

sealed class HomeLaunchEvent {
    data class Launch(val intent: Intent) : HomeLaunchEvent()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val downloadManager: DownloadManager,
    private val gameLauncher: GameLauncher,
    private val gameNavigationContext: GameNavigationContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _launchEvents = MutableSharedFlow<HomeLaunchEvent>()
    val launchEvents: SharedFlow<HomeLaunchEvent> = _launchEvents.asSharedFlow()

    private val rowGameIndexes = mutableMapOf<HomeRow, Int>()
    private var platformGamesJob: kotlinx.coroutines.Job? = null

    init {
        loadData()
        initializeRomM()
    }

    private fun initializeRomM() {
        viewModelScope.launch {
            romMRepository.initialize()

            if (romMRepository.isConnected()) {
                val prefs = preferencesRepository.preferences.first()
                val lastSync = prefs.lastRommSync
                val oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS)

                if (lastSync == null || lastSync.isBefore(oneWeekAgo)) {
                    syncFromRomm()
                }
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            launch { loadRecentGames() }
            launch { loadFavorites() }
            launch { loadPlatforms() }
        }
    }

    private suspend fun loadRecentGames() {
        gameDao.observeRecentlyPlayed(10).collect { games ->
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
                state.copy(
                    platforms = platformUis,
                    isLoading = false
                )
            }
            if (platforms.isNotEmpty() && _uiState.value.platformGames.isEmpty()) {
                loadGamesForPlatform(platforms.first().id, platformIndex = 0)
            }
        }
    }

    private fun loadGamesForPlatform(platformId: String, platformIndex: Int, useSavedIndex: Boolean = false) {
        platformGamesJob?.cancel()
        platformGamesJob = viewModelScope.launch {
            var isFirstEmission = true
            gameDao.observeByPlatform(platformId).collect { games ->
                val gameUis = games.map { it.toUi() }

                _uiState.update { state ->
                    if (useSavedIndex && isFirstEmission) {
                        val row = HomeRow.Platform(platformIndex)
                        val savedIndex = rowGameIndexes[row] ?: 0
                        isFirstEmission = false
                        state.copy(
                            platformGames = gameUis,
                            focusedGameIndex = savedIndex.coerceIn(0, (gameUis.size - 1).coerceAtLeast(0))
                        )
                    } else {
                        isFirstEmission = false
                        state.copy(platformGames = gameUis)
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

        _uiState.update { it.copy(currentRow = nextRow, focusedGameIndex = savedIndex) }

        if (nextRow is HomeRow.Platform) {
            val platform = state.platforms.getOrNull(nextRow.index)
            if (platform != null) {
                loadGamesForPlatform(platform.id, nextRow.index, useSavedIndex = true)
            }
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

        _uiState.update { it.copy(currentRow = prevRow, focusedGameIndex = savedIndex) }

        if (prevRow is HomeRow.Platform) {
            val platform = state.platforms.getOrNull(prevRow.index)
            if (platform != null) {
                loadGamesForPlatform(platform.id, prevRow.index, useSavedIndex = true)
            }
        }
    }

    fun nextGame() {
        _uiState.update { state ->
            if (state.currentGames.isEmpty()) return@update state
            val nextIndex = (state.focusedGameIndex + 1).coerceAtMost(state.currentGames.size - 1)
            state.copy(focusedGameIndex = nextIndex)
        }
    }

    fun previousGame() {
        _uiState.update { state ->
            if (state.currentGames.isEmpty()) return@update state
            val prevIndex = (state.focusedGameIndex - 1).coerceAtLeast(0)
            state.copy(focusedGameIndex = prevIndex)
        }
    }

    fun toggleGameMenu() {
        _uiState.update {
            it.copy(showGameMenu = !it.showGameMenu, gameMenuFocusIndex = 0)
        }
    }

    fun moveGameMenuFocus(delta: Int) {
        _uiState.update {
            val newIndex = (it.gameMenuFocusIndex + delta).coerceIn(0, 3)
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
                gameNavigationContext.setContext(state.currentGames.map { it.id })
                onGameSelect(game.id)
            }
            3 -> {
                toggleGameMenu()
                hideGame(game.id)
            }
        }
    }

    private fun toggleFavorite(gameId: Long) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            gameDao.updateFavorite(gameId, !game.isFavorite)
        }
    }

    private fun hideGame(gameId: Long) {
        viewModelScope.launch {
            gameDao.updateHidden(gameId, true)
        }
    }

    private fun queueDownload(gameId: Long) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            val rommId = game.rommId ?: run {
                showError("Game not synced from RomM")
                return@launch
            }

            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val fileName = rom.fileName ?: "${game.title}.rom"

                    downloadManager.enqueueDownload(
                        gameId = gameId,
                        rommId = rommId,
                        fileName = fileName,
                        gameTitle = game.title,
                        platformSlug = rom.platformSlug,
                        coverPath = game.coverPath,
                        expectedSizeBytes = rom.fileSize
                    )
                }
                is RomMResult.Error -> {
                    showError("Failed to get ROM info: ${result.message}")
                }
            }
        }
    }

    fun launchGame(gameId: Long) {
        viewModelScope.launch {
            when (val result = gameLauncher.launch(gameId)) {
                is LaunchResult.Success -> {
                    _launchEvents.emit(HomeLaunchEvent.Launch(result.intent))
                }
                is LaunchResult.NoEmulator -> {
                    showError("No emulator installed for this platform")
                }
                is LaunchResult.NoRomFile -> {
                    showError("ROM file not found")
                }
                is LaunchResult.Error -> {
                    showError(result.message)
                }
            }
        }
    }

    fun syncFromRomm() {
        viewModelScope.launch {
            Log.d(TAG, "syncFromRomm: starting")
            romMRepository.initialize()
            if (!romMRepository.isConnected()) {
                Log.d(TAG, "syncFromRomm: not connected")
                showError("RomM not connected")
                return@launch
            }

            Log.d(TAG, "syncFromRomm: fetching summary")
            when (val summary = romMRepository.getLibrarySummary()) {
                is RomMResult.Error -> {
                    Log.e(TAG, "syncFromRomm: summary error: ${summary.message}")
                    showError(summary.message)
                    return@launch
                }
                is RomMResult.Success -> {
                    val (platformCount, _) = summary.data
                    Log.d(TAG, "syncFromRomm: got $platformCount platforms, showing persistent")
                    notificationManager.showPersistent(
                        title = "Syncing from Rom Manager",
                        subtitle = "Starting...",
                        key = "romm-sync",
                        progress = NotificationProgress(0, platformCount)
                    )

                    try {
                        withContext(NonCancellable) {
                            Log.d(TAG, "syncFromRomm: calling syncLibrary")
                            val result = romMRepository.syncLibrary { current, total, platform ->
                                Log.d(TAG, "syncFromRomm: progress $current/$total - $platform")
                                notificationManager.updatePersistent(
                                    key = "romm-sync",
                                    subtitle = platform,
                                    progress = NotificationProgress(current, total)
                                )
                            }

                            Log.d(TAG, "syncFromRomm: syncLibrary returned - added=${result.gamesAdded}, updated=${result.gamesUpdated}, errors=${result.errors}")

                            if (result.errors.isEmpty()) {
                                Log.d(TAG, "syncFromRomm: completing with success")
                                notificationManager.completePersistent(
                                    key = "romm-sync",
                                    title = "Sync complete",
                                    subtitle = "${result.gamesAdded} added, ${result.gamesUpdated} updated",
                                    type = NotificationType.SUCCESS
                                )
                            } else {
                                Log.d(TAG, "syncFromRomm: completing with errors")
                                notificationManager.completePersistent(
                                    key = "romm-sync",
                                    title = "Sync completed with errors",
                                    subtitle = "${result.errors.size} platform(s) failed",
                                    type = NotificationType.ERROR
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "syncFromRomm: exception", e)
                        withContext(NonCancellable) {
                            notificationManager.completePersistent(
                                key = "romm-sync",
                                title = "Sync failed",
                                subtitle = e.message,
                                type = NotificationType.ERROR
                            )
                        }
                    }
                }
            }
            Log.d(TAG, "syncFromRomm: done")
        }
    }

    private fun showError(message: String) {
        notificationManager.show(
            title = "Error",
            subtitle = message,
            type = NotificationType.ERROR,
            duration = NotificationDuration.SHORT
        )
    }

    private fun showSuccess(message: String) {
        notificationManager.show(
            title = message,
            type = NotificationType.SUCCESS,
            duration = NotificationDuration.SHORT
        )
    }

    private fun showInfo(message: String) {
        notificationManager.show(
            title = message,
            type = NotificationType.INFO,
            duration = NotificationDuration.SHORT,
            key = "home-status",
            immediate = false
        )
    }

    fun showLaunchError(message: String) {
        showError(message)
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
            isDownloaded = localPath != null
        )
    }

    fun createInputHandler(
        onGameSelect: (Long) -> Unit,
        onDrawerToggle: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            if (_uiState.value.showGameMenu) {
                moveGameMenuFocus(-1)
            } else {
                previousRow()
            }
            return true
        }

        override fun onDown(): Boolean {
            if (_uiState.value.showGameMenu) {
                moveGameMenuFocus(1)
            } else {
                nextRow()
            }
            return true
        }

        override fun onLeft(): Boolean {
            if (_uiState.value.showGameMenu) return true
            previousGame()
            return true
        }

        override fun onRight(): Boolean {
            if (_uiState.value.showGameMenu) return true
            nextGame()
            return true
        }

        override fun onConfirm(): Boolean {
            if (_uiState.value.showGameMenu) {
                confirmGameMenuSelection(onGameSelect)
            } else {
                _uiState.value.focusedGame?.let { game ->
                    if (game.isDownloaded) {
                        launchGame(game.id)
                    } else {
                        queueDownload(game.id)
                    }
                }
            }
            return true
        }

        override fun onBack(): Boolean {
            if (_uiState.value.showGameMenu) {
                toggleGameMenu()
                return true
            }
            return false
        }

        override fun onMenu(): Boolean {
            if (_uiState.value.showGameMenu) return true
            onDrawerToggle()
            return true
        }

        override fun onSelect(): Boolean {
            if (_uiState.value.focusedGame != null) {
                toggleGameMenu()
            }
            return true
        }
    }
}
