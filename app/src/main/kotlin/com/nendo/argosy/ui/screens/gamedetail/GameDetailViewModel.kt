package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.game.DeleteGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
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

data class DiscUi(
    val id: Long,
    val discNumber: Int,
    val fileName: String,
    val isDownloaded: Boolean,
    val isLastPlayed: Boolean
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
    val userRating: Int,
    val userDifficulty: Int,
    val isRommGame: Boolean,
    val isFavorite: Boolean,
    val playCount: Int,
    val playTimeMinutes: Int,
    val screenshots: List<ScreenshotPair>,
    val emulatorName: String?,
    val canPlay: Boolean,
    val isMultiDisc: Boolean = false,
    val lastPlayedDiscId: Long? = null
)

sealed class LaunchEvent {
    data class Launch(val intent: Intent) : LaunchEvent()
}

enum class GameDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    WAITING_FOR_STORAGE,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED
}

enum class RatingType { OPINION, DIFFICULTY }

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
    val currentGameIndex: Int = -1,
    val showRatingPicker: Boolean = false,
    val ratingPickerType: RatingType = RatingType.OPINION,
    val ratingPickerValue: Int = 0,
    val discs: List<DiscUi> = emptyList(),
    val showDiscPicker: Boolean = false,
    val discPickerFocusIndex: Int = 0,
    val showMissingDiscPrompt: Boolean = false,
    val missingDiscNumbers: List<Int> = emptyList()
) {
    val hasPreviousGame: Boolean get() = currentGameIndex > 0
    val hasNextGame: Boolean get() = currentGameIndex >= 0 && currentGameIndex < siblingGameIds.size - 1
}

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val launchGameUseCase: LaunchGameUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val romMRepository: RomMRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    private var currentGameId: Long = 0
    private var lastActionTime: Long = 0
    private val actionDebounceMs = 300L
    private var pageLoadTime: Long = 0
    private val pageLoadDebounceMs = 500L

    init {
        viewModelScope.launch {
            emulatorDetector.detectEmulators()
        }
        viewModelScope.launch {
            downloadManager.state.collect { queueState ->
                val gameId = currentGameId
                if (gameId == 0L) return@collect

                val activeDownload = queueState.activeDownloads.find { it.gameId == gameId }
                val queued = queueState.queue.find { it.gameId == gameId }
                val completed = queueState.completed.find { it.gameId == gameId }

                val (status, progress) = when {
                    activeDownload != null -> {
                        GameDownloadStatus.DOWNLOADING to activeDownload.progressPercent
                    }
                    queued?.state == DownloadState.PAUSED -> {
                        GameDownloadStatus.PAUSED to queued.progressPercent
                    }
                    queued?.state == DownloadState.WAITING_FOR_STORAGE -> {
                        GameDownloadStatus.WAITING_FOR_STORAGE to 0f
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
        pageLoadTime = System.currentTimeMillis()
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformDao.getById(game.platformId)

            val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
                ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

            val emulatorName = emulatorConfig?.displayName
                ?: emulatorDetector.getPreferredEmulator(game.platformId)?.def?.displayName

            val isSteamGame = game.source == GameSource.STEAM
            val fileExists = gameRepository.checkGameFileExists(gameId)

            val canPlay = if (isSteamGame) {
                val launcher = game.steamLauncher?.let { SteamLaunchers.getByPackage(it) }
                launcher?.isInstalled(context) == true
            } else if (game.isMultiDisc) {
                val downloadedCount = gameDiscDao.getDownloadedDiscCount(gameId)
                downloadedCount > 0 && emulatorDetector.hasAnyEmulator(game.platformId)
            } else {
                fileExists && emulatorDetector.hasAnyEmulator(game.platformId)
            }

            val downloadStatus = if (isSteamGame || fileExists) {
                GameDownloadStatus.DOWNLOADED
            } else if (game.isMultiDisc) {
                val downloadedCount = gameDiscDao.getDownloadedDiscCount(gameId)
                if (downloadedCount > 0) GameDownloadStatus.DOWNLOADED else GameDownloadStatus.NOT_DOWNLOADED
            } else {
                GameDownloadStatus.NOT_DOWNLOADED
            }

            val discsUi = if (game.isMultiDisc) {
                gameDiscDao.getDiscsForGame(gameId).map { disc ->
                    DiscUi(
                        id = disc.id,
                        discNumber = disc.discNumber,
                        fileName = disc.fileName,
                        isDownloaded = disc.localPath != null,
                        isLastPlayed = disc.id == game.lastPlayedDiscId
                    )
                }
            } else {
                emptyList()
            }

            var siblingIds = gameNavigationContext.getGameIds()
            if (siblingIds.isEmpty() || !siblingIds.contains(gameId)) {
                val platformGames = gameDao.getByPlatform(game.platformId)
                siblingIds = platformGames.map { it.id }
                gameNavigationContext.setContext(siblingIds)
            }
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
                    currentGameIndex = currentIndex,
                    discs = discsUi
                )
            }
        }
    }

    fun downloadGame() {
        val now = System.currentTimeMillis()
        if (now - pageLoadTime < pageLoadDebounceMs) return
        viewModelScope.launch {
            when (val result = gameActions.queueDownload(currentGameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
            }
        }
    }

    fun primaryAction() {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < actionDebounceMs) return
        lastActionTime = now

        val state = _uiState.value
        when (state.downloadStatus) {
            GameDownloadStatus.DOWNLOADED -> playGame()
            GameDownloadStatus.NOT_DOWNLOADED -> downloadGame()
            GameDownloadStatus.QUEUED,
            GameDownloadStatus.WAITING_FOR_STORAGE,
            GameDownloadStatus.DOWNLOADING,
            GameDownloadStatus.PAUSED -> {
                // Already in progress or paused
            }
        }
    }

    fun playGame(discId: Long? = null) {
        viewModelScope.launch {
            val game = _uiState.value.game ?: return@launch

            when (val result = launchGameUseCase(currentGameId, discId)) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    _launchEvents.emit(LaunchEvent.Launch(result.intent))
                }
                is LaunchResult.NoEmulator -> {
                    notificationManager.showError("No emulator installed for ${game.platformName}")
                }
                is LaunchResult.NoRomFile -> {
                    notificationManager.showError("ROM file not found. Download required.")
                }
                is LaunchResult.NoSteamLauncher -> {
                    notificationManager.showError("Steam launcher not installed")
                }
                is LaunchResult.MissingDiscs -> {
                    _uiState.update {
                        it.copy(
                            showMissingDiscPrompt = true,
                            missingDiscNumbers = result.missingDiscNumbers
                        )
                    }
                }
                is LaunchResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            gameActions.toggleFavorite(currentGameId)
            loadGame(currentGameId)
        }
    }

    fun toggleMoreOptions() {
        val wasShowing = _uiState.value.showMoreOptions
        _uiState.update {
            it.copy(
                showMoreOptions = !it.showMoreOptions,
                moreOptionsFocusIndex = 0
            )
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun moveOptionsFocus(delta: Int) {
        _uiState.update {
            val isDownloaded = it.downloadStatus == GameDownloadStatus.DOWNLOADED
            val isRommGame = it.game?.isRommGame == true
            val maxIndex = when {
                isDownloaded && isRommGame -> 4
                isDownloaded || isRommGame -> 3
                else -> 1
            }
            val newIndex = (it.moreOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun confirmOptionSelection(onBack: () -> Unit) {
        val state = _uiState.value
        val isDownloaded = state.downloadStatus == GameDownloadStatus.DOWNLOADED
        val isRommGame = state.game?.isRommGame == true
        val index = state.moreOptionsFocusIndex

        when {
            index == 0 -> showEmulatorPicker()
            index == 1 && isRommGame -> showRatingPicker(RatingType.OPINION)
            index == 2 && isRommGame -> showRatingPicker(RatingType.DIFFICULTY)
            index == 1 && !isRommGame && isDownloaded -> { toggleMoreOptions(); deleteLocalFile() }
            index == 1 && !isRommGame && !isDownloaded -> { hideGame(); onBack() }
            index == 3 && isRommGame && isDownloaded -> { toggleMoreOptions(); deleteLocalFile() }
            index == 3 && isRommGame && !isDownloaded -> { hideGame(); onBack() }
            index == 4 && isRommGame && isDownloaded -> { hideGame(); onBack() }
            index == 2 && !isRommGame && isDownloaded -> { hideGame(); onBack() }
            else -> toggleMoreOptions()
        }
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
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissEmulatorPicker() {
        _uiState.update { it.copy(showEmulatorPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
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

            configureEmulatorUseCase.setForGame(gameId, game.platformId, emulator)

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

    fun showDiscPicker() {
        val game = _uiState.value.game ?: return
        if (!game.isMultiDisc) return

        val discs = _uiState.value.discs
        val lastPlayedIndex = discs.indexOfFirst { it.isLastPlayed }.takeIf { it >= 0 } ?: 0

        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showDiscPicker = true,
                discPickerFocusIndex = lastPlayedIndex
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissDiscPicker() {
        _uiState.update { it.copy(showDiscPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveDiscPickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.discs.size - 1
            val newIndex = (state.discPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(discPickerFocusIndex = newIndex)
        }
    }

    fun confirmDiscSelection() {
        val state = _uiState.value
        val disc = state.discs.getOrNull(state.discPickerFocusIndex) ?: return
        _uiState.update { it.copy(showDiscPicker = false) }
        playGame(disc.id)
    }

    fun selectDiscAtIndex(index: Int) {
        val disc = _uiState.value.discs.getOrNull(index) ?: return
        _uiState.update { it.copy(showDiscPicker = false) }
        playGame(disc.id)
    }

    fun dismissMissingDiscPrompt() {
        _uiState.update { it.copy(showMissingDiscPrompt = false, missingDiscNumbers = emptyList()) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun repairAndPlay() {
        viewModelScope.launch {
            _uiState.update { it.copy(showMissingDiscPrompt = false, missingDiscNumbers = emptyList()) }

            when (val result = gameActions.repairMissingDiscs(currentGameId)) {
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} missing discs")
                }
                is DownloadResult.Queued -> { }
                is DownloadResult.Error -> notificationManager.showError(result.message)
            }
        }
    }

    fun showRatingPicker(type: RatingType) {
        val game = _uiState.value.game ?: return
        val currentValue = when (type) {
            RatingType.OPINION -> game.userRating
            RatingType.DIFFICULTY -> game.userDifficulty
        }
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showRatingPicker = true,
                ratingPickerType = type,
                ratingPickerValue = currentValue
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingPicker() {
        _uiState.update { it.copy(showRatingPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeRatingValue(delta: Int) {
        _uiState.update { state ->
            val newValue = (state.ratingPickerValue + delta).coerceIn(0, 10)
            state.copy(ratingPickerValue = newValue)
        }
    }

    fun confirmRating() {
        val state = _uiState.value
        val value = state.ratingPickerValue
        val type = state.ratingPickerType

        viewModelScope.launch {
            val result = when (type) {
                RatingType.OPINION -> romMRepository.updateUserRating(currentGameId, value)
                RatingType.DIFFICULTY -> romMRepository.updateUserDifficulty(currentGameId, value)
            }

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    loadGame(currentGameId)
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _uiState.update { it.copy(showRatingPicker = false) }
        }
    }

    fun hideGame() {
        viewModelScope.launch {
            gameActions.hideGame(currentGameId)
        }
    }

    private fun deleteLocalFile() {
        viewModelScope.launch {
            gameActions.deleteLocalFile(currentGameId)
            notificationManager.showSuccess("Download deleted")
            loadGame(currentGameId)
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
        val effectiveBackground = backgroundPath ?: remoteUrls.firstOrNull()
        return GameDetailUi(
            id = id,
            title = title,
            platformId = platformId,
            platformName = platformName,
            coverPath = coverPath,
            backgroundPath = effectiveBackground,
            developer = developer,
            publisher = publisher,
            releaseYear = releaseYear,
            genre = genre,
            description = description,
            players = players,
            rating = rating,
            userRating = userRating,
            userDifficulty = userDifficulty,
            isRommGame = rommId != null || source == GameSource.STEAM,
            isFavorite = isFavorite,
            playCount = playCount,
            playTimeMinutes = playTimeMinutes,
            screenshots = screenshots,
            emulatorName = emulatorName,
            canPlay = canPlay,
            isMultiDisc = isMultiDisc,
            lastPlayedDiscId = lastPlayedDiscId
        )
    }

    fun showLaunchError(message: String) {
        notificationManager.showError(message)
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onScrollUp: () -> Unit = {},
        onScrollDown: () -> Unit = {}
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            return when {
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                state.showDiscPicker -> {
                    moveDiscPickerFocus(-1)
                    InputResult.HANDLED
                }
                state.showEmulatorPicker -> {
                    moveEmulatorPickerFocus(-1)
                    InputResult.HANDLED
                }
                state.showMoreOptions -> {
                    moveOptionsFocus(-1)
                    InputResult.HANDLED
                }
                else -> {
                    onScrollUp()
                    InputResult.handled(SoundType.SILENT)
                }
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                state.showDiscPicker -> {
                    moveDiscPickerFocus(1)
                    InputResult.HANDLED
                }
                state.showEmulatorPicker -> {
                    moveEmulatorPickerFocus(1)
                    InputResult.HANDLED
                }
                state.showMoreOptions -> {
                    moveOptionsFocus(1)
                    InputResult.HANDLED
                }
                else -> {
                    onScrollDown()
                    InputResult.handled(SoundType.SILENT)
                }
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            when {
                state.showRatingPicker -> {
                    changeRatingValue(-1)
                    return InputResult.HANDLED
                }
                state.showMoreOptions || state.showEmulatorPicker || state.showDiscPicker || state.showMissingDiscPrompt -> {
                    return InputResult.UNHANDLED
                }
                else -> {
                    gameNavigationContext.getPreviousGameId(currentGameId)?.let { prevId ->
                        loadGame(prevId)
                    }
                    return InputResult.HANDLED
                }
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            when {
                state.showRatingPicker -> {
                    changeRatingValue(1)
                    return InputResult.HANDLED
                }
                state.showMoreOptions || state.showEmulatorPicker || state.showDiscPicker || state.showMissingDiscPrompt -> {
                    return InputResult.UNHANDLED
                }
                else -> {
                    gameNavigationContext.getNextGameId(currentGameId)?.let { nextId ->
                        loadGame(nextId)
                    }
                    return InputResult.HANDLED
                }
            }
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            when {
                state.showRatingPicker -> confirmRating()
                state.showMissingDiscPrompt -> repairAndPlay()
                state.showDiscPicker -> confirmDiscSelection()
                state.showEmulatorPicker -> confirmEmulatorSelection()
                state.showMoreOptions -> confirmOptionSelection(onBack)
                else -> primaryAction()
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            when {
                state.showRatingPicker -> dismissRatingPicker()
                state.showMissingDiscPrompt -> dismissMissingDiscPrompt()
                state.showDiscPicker -> dismissDiscPicker()
                state.showEmulatorPicker -> dismissEmulatorPicker()
                state.showMoreOptions -> toggleMoreOptions()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            val state = _uiState.value
            if (state.showRatingPicker) {
                dismissRatingPicker()
                return InputResult.UNHANDLED
            }
            if (state.showMissingDiscPrompt) {
                dismissMissingDiscPrompt()
                return InputResult.UNHANDLED
            }
            if (state.showDiscPicker) {
                dismissDiscPicker()
                return InputResult.UNHANDLED
            }
            if (state.showMoreOptions) {
                toggleMoreOptions()
                return InputResult.UNHANDLED
            }
            if (state.showEmulatorPicker) {
                dismissEmulatorPicker()
                return InputResult.UNHANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSecondaryAction(): InputResult {
            toggleFavorite()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            return InputResult.UNHANDLED
        }

        override fun onSelect(): InputResult {
            toggleMoreOptions()
            return InputResult.HANDLED
        }
    }
}
