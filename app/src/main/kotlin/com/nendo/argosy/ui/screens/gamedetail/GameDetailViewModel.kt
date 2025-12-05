package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationDuration
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScreenshotPair(
    val remoteUrl: String,
    val cachedPath: String?
)

data class GameDetailUi(
    val id: Long,
    val title: String,
    val platformId: String,
    val platformName: String,
    val coverPath: String?,
    val backgroundPath: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val genre: String?,
    val description: String?,
    val players: String?,
    val rating: Float?,
    val isFavorite: Boolean,
    val playCount: Int,
    val playTimeMinutes: Int,
    val screenshots: List<ScreenshotPair>,
    val emulatorName: String?,
    val canPlay: Boolean
)

sealed class LaunchEvent {
    data class Launch(val intent: Intent) : LaunchEvent()
}

enum class GameDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED
}

data class GameDetailUiState(
    val game: GameDetailUi? = null,
    val showMoreOptions: Boolean = false,
    val moreOptionsFocusIndex: Int = 0,
    val isLoading: Boolean = true,
    val downloadStatus: GameDownloadStatus = GameDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val showEmulatorPicker: Boolean = false,
    val availableEmulators: List<InstalledEmulator> = emptyList(),
    val emulatorPickerFocusIndex: Int = 0,
    val siblingGameIds: List<Long> = emptyList(),
    val currentGameIndex: Int = -1
) {
    val hasPreviousGame: Boolean get() = currentGameIndex > 0
    val hasNextGame: Boolean get() = currentGameIndex >= 0 && currentGameIndex < siblingGameIds.size - 1
}

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    private val gameDao: GameDao,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val gameLauncher: GameLauncher,
    private val emulatorDetector: EmulatorDetector,
    private val playSessionTracker: PlaySessionTracker,
    private val downloadManager: DownloadManager,
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val gameNavigationContext: GameNavigationContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    private var currentGameId: Long = 0
    private var lastActionTime: Long = 0
    private val actionDebounceMs = 300L

    init {
        viewModelScope.launch {
            emulatorDetector.detectEmulators()
        }
        viewModelScope.launch {
            downloadManager.state.collect { queueState ->
                val gameId = currentGameId
                if (gameId == 0L) return@collect

                val activeDownload = queueState.activeDownload
                val queued = queueState.queue.find { it.gameId == gameId }
                val completed = queueState.completed.find { it.gameId == gameId }

                val (status, progress) = when {
                    activeDownload?.gameId == gameId -> {
                        GameDownloadStatus.DOWNLOADING to activeDownload.progressPercent
                    }
                    queued != null -> {
                        GameDownloadStatus.QUEUED to 0f
                    }
                    completed?.state == DownloadState.COMPLETED -> {
                        loadGame(gameId)
                        GameDownloadStatus.DOWNLOADED to 1f
                    }
                    completed?.state == DownloadState.FAILED -> {
                        GameDownloadStatus.NOT_DOWNLOADED to 0f
                    }
                    else -> {
                        val game = _uiState.value.game
                        if (game?.canPlay == true) {
                            GameDownloadStatus.DOWNLOADED to 1f
                        } else {
                            GameDownloadStatus.NOT_DOWNLOADED to 0f
                        }
                    }
                }

                _uiState.update { it.copy(downloadStatus = status, downloadProgress = progress) }
            }
        }
    }

    fun loadGame(gameId: Long) {
        currentGameId = gameId
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformDao.getById(game.platformId)

            val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
                ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

            val emulatorName = emulatorConfig?.displayName
                ?: emulatorDetector.getPreferredEmulator(game.platformId)?.def?.displayName

            val fileExists = gameRepository.validateGameFile(gameId)

            val canPlay = fileExists && emulatorDetector.hasAnyEmulator(game.platformId)

            val downloadStatus = if (fileExists) {
                GameDownloadStatus.DOWNLOADED
            } else {
                GameDownloadStatus.NOT_DOWNLOADED
            }

            val siblingIds = gameNavigationContext.getGameIds()
            val currentIndex = gameNavigationContext.getIndex(gameId)

            _uiState.update { state ->
                state.copy(
                    game = game.toUi(
                        platformName = platform?.name ?: "Unknown",
                        emulatorName = emulatorName,
                        canPlay = canPlay
                    ),
                    isLoading = false,
                    downloadStatus = downloadStatus,
                    downloadProgress = if (downloadStatus == GameDownloadStatus.DOWNLOADED) 1f else 0f,
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex
                )
            }
        }
    }

    fun downloadGame() {
        viewModelScope.launch {
            val game = gameDao.getById(currentGameId) ?: return@launch
            val rommId = game.rommId ?: run {
                showError("Game not synced from RomM")
                return@launch
            }

            when (val result = romMRepository.getRom(rommId)) {
                is RomMResult.Success -> {
                    val rom = result.data
                    val fileName = rom.fileName ?: "${game.title}.rom"

                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (ext in INVALID_ROM_EXTENSIONS) {
                        showError("Invalid ROM file type: .$ext")
                        return@launch
                    }

                    downloadManager.enqueueDownload(
                        gameId = currentGameId,
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

    companion object {
        private val INVALID_ROM_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "gif", "webp", "bmp",
            "html", "htm", "txt", "md", "pdf"
        )
    }

    fun primaryAction() {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < actionDebounceMs) return
        lastActionTime = now

        val state = _uiState.value
        when (state.downloadStatus) {
            GameDownloadStatus.DOWNLOADED -> playGame()
            GameDownloadStatus.NOT_DOWNLOADED -> downloadGame()
            GameDownloadStatus.QUEUED, GameDownloadStatus.DOWNLOADING -> {
                // Already in progress
            }
        }
    }

    fun playGame() {
        viewModelScope.launch {
            val game = _uiState.value.game ?: return@launch

            when (val result = gameLauncher.launch(currentGameId)) {
                is LaunchResult.Success -> {
                    playSessionTracker.startSession(
                        gameId = currentGameId,
                        emulatorPackage = result.intent.`package` ?: ""
                    )
                    _launchEvents.emit(LaunchEvent.Launch(result.intent))
                }
                is LaunchResult.NoEmulator -> {
                    showError("No emulator installed for ${game.platformName}")
                }
                is LaunchResult.NoRomFile -> {
                    showError("ROM file not found. Download required.")
                }
                is LaunchResult.Error -> {
                    showError(result.message)
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val game = gameDao.getById(currentGameId) ?: return@launch
            gameDao.updateFavorite(currentGameId, !game.isFavorite)
            loadGame(currentGameId)
        }
    }

    fun toggleMoreOptions() {
        _uiState.update {
            it.copy(
                showMoreOptions = !it.showMoreOptions,
                moreOptionsFocusIndex = 0
            )
        }
    }

    fun moveOptionsFocus(delta: Int) {
        _uiState.update {
            val newIndex = (it.moreOptionsFocusIndex + delta).coerceIn(0, 1)
            it.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun confirmOptionSelection(onBack: () -> Unit) {
        when (_uiState.value.moreOptionsFocusIndex) {
            0 -> {
                showEmulatorPicker()
                return
            }
            1 -> {
                hideGame()
                onBack()
                return
            }
        }
        toggleMoreOptions()
    }

    fun showEmulatorPicker() {
        val game = _uiState.value.game ?: return
        val available = emulatorDetector.getInstalledForPlatform(game.platformId)
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showEmulatorPicker = true,
                availableEmulators = available,
                emulatorPickerFocusIndex = 0
            )
        }
    }

    fun dismissEmulatorPicker() {
        _uiState.update { it.copy(showEmulatorPicker = false) }
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.availableEmulators.size
            val newIndex = (state.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulatorPickerFocusIndex = newIndex)
        }
    }

    fun selectEmulator(emulator: InstalledEmulator?) {
        viewModelScope.launch {
            val gameId = currentGameId
            val game = gameDao.getById(gameId) ?: return@launch

            emulatorConfigDao.deleteGameOverride(gameId)

            if (emulator != null) {
                val config = EmulatorConfigEntity(
                    platformId = game.platformId,
                    gameId = gameId,
                    packageName = emulator.def.packageName,
                    displayName = emulator.def.displayName,
                    coreName = EmulatorRegistry.getRetroArchCores()[game.platformId],
                    isDefault = false
                )
                emulatorConfigDao.insert(config)
            }

            _uiState.update { it.copy(showEmulatorPicker = false) }
            loadGame(gameId)
        }
    }

    fun confirmEmulatorSelection() {
        val state = _uiState.value
        val index = state.emulatorPickerFocusIndex
        if (index == 0) {
            selectEmulator(null)
        } else {
            val emulator = state.availableEmulators.getOrNull(index - 1)
            selectEmulator(emulator)
        }
    }

    fun hideGame() {
        viewModelScope.launch {
            gameDao.updateHidden(currentGameId, true)
        }
    }

    private fun GameEntity.toUi(
        platformName: String,
        emulatorName: String?,
        canPlay: Boolean
    ): GameDetailUi {
        val remoteUrls = screenshotPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val cachedPaths = cachedScreenshotPaths?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val screenshots = remoteUrls.mapIndexed { index, url ->
            ScreenshotPair(
                remoteUrl = url,
                cachedPath = cachedPaths.getOrNull(index)
            )
        }
        return GameDetailUi(
            id = id,
            title = title,
            platformId = platformId,
            platformName = platformName,
            coverPath = coverPath,
            backgroundPath = backgroundPath,
            developer = developer,
            publisher = publisher,
            releaseYear = releaseYear,
            genre = genre,
            description = description,
            players = players,
            rating = rating,
            isFavorite = isFavorite,
            playCount = playCount,
            playTimeMinutes = playTimeMinutes,
            screenshots = screenshots,
            emulatorName = emulatorName,
            canPlay = canPlay
        )
    }

    private fun showError(message: String) {
        notificationManager.show(
            title = "Error",
            subtitle = message,
            type = NotificationType.ERROR,
            duration = NotificationDuration.SHORT
        )
    }

    fun showLaunchError(message: String) {
        showError(message)
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onScrollUp: () -> Unit = {},
        onScrollDown: () -> Unit = {}
    ): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            val state = _uiState.value
            when {
                state.showEmulatorPicker -> moveEmulatorPickerFocus(-1)
                state.showMoreOptions -> moveOptionsFocus(-1)
                else -> onScrollUp()
            }
            return true
        }

        override fun onDown(): Boolean {
            val state = _uiState.value
            when {
                state.showEmulatorPicker -> moveEmulatorPickerFocus(1)
                state.showMoreOptions -> moveOptionsFocus(1)
                else -> onScrollDown()
            }
            return true
        }

        override fun onLeft(): Boolean {
            val state = _uiState.value
            if (state.showMoreOptions || state.showEmulatorPicker) return false
            gameNavigationContext.getPreviousGameId(currentGameId)?.let { prevId ->
                loadGame(prevId)
            }
            return true
        }

        override fun onRight(): Boolean {
            val state = _uiState.value
            if (state.showMoreOptions || state.showEmulatorPicker) return false
            gameNavigationContext.getNextGameId(currentGameId)?.let { nextId ->
                loadGame(nextId)
            }
            return true
        }

        override fun onConfirm(): Boolean {
            val state = _uiState.value
            when {
                state.showEmulatorPicker -> confirmEmulatorSelection()
                state.showMoreOptions -> confirmOptionSelection(onBack)
                else -> primaryAction()
            }
            return true
        }

        override fun onBack(): Boolean {
            val state = _uiState.value
            when {
                state.showEmulatorPicker -> dismissEmulatorPicker()
                state.showMoreOptions -> toggleMoreOptions()
                else -> onBack()
            }
            return true
        }

        override fun onMenu(): Boolean = false

        override fun onContextMenu(): Boolean {
            toggleFavorite()
            return true
        }

        override fun onSelect(): Boolean {
            toggleMoreOptions()
            return true
        }
    }
}
