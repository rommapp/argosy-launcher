package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
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

data class AchievementUi(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val type: String?,
    val badgeUrl: String?,
    val isUnlocked: Boolean = false
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
    val achievements: List<AchievementUi> = emptyList(),
    val emulatorName: String?,
    val canPlay: Boolean,
    val isMultiDisc: Boolean = false,
    val lastPlayedDiscId: Long? = null,
    val isRetroArchEmulator: Boolean = false,
    val selectedCoreName: String? = null
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
    val showCorePicker: Boolean = false,
    val availableCores: List<RetroArchCore> = emptyList(),
    val corePickerFocusIndex: Int = 0,
    val selectedCoreId: String? = null,
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
    private val gameActions: GameActionsDelegate,
    private val achievementDao: com.nendo.argosy.data.local.dao.AchievementDao
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

            val gameSpecificConfig = emulatorConfigDao.getByGameId(gameId)
            val platformDefaultConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
            val emulatorConfig = gameSpecificConfig ?: platformDefaultConfig

            val emulatorName = emulatorConfig?.displayName
                ?: emulatorDetector.getPreferredEmulator(game.platformId)?.def?.displayName

            val emulatorDef = emulatorConfig?.packageName?.let { EmulatorRegistry.getByPackage(it) }
                ?: emulatorDetector.getPreferredEmulator(game.platformId)?.def
            val isRetroArch = emulatorDef?.launchConfig is LaunchConfig.RetroArch

            val selectedCoreId = gameSpecificConfig?.coreName
                ?: platformDefaultConfig?.coreName
                ?: EmulatorRegistry.getDefaultCore(game.platformId)?.id
            val selectedCoreName = if (isRetroArch) {
                EmulatorRegistry.getCoresForPlatform(game.platformId)
                    .find { it.id == selectedCoreId }?.displayName
            } else null

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

            val cachedAchievements = if (game.rommId != null) {
                achievementDao.getByGameId(gameId).map { it.toUi() }
            } else {
                emptyList()
            }

            _uiState.update { state ->
                state.copy(
                    game = game.toUi(
                        platformName = platform?.name ?: "Unknown",
                        emulatorName = emulatorName,
                        canPlay = canPlay,
                        isRetroArch = isRetroArch,
                        selectedCoreName = selectedCoreName,
                        achievements = cachedAchievements
                    ),
                    isLoading = false,
                    downloadStatus = downloadStatus,
                    downloadProgress = if (downloadStatus == GameDownloadStatus.DOWNLOADED) 1f else 0f,
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex,
                    discs = discsUi,
                    availableCores = if (isRetroArch) EmulatorRegistry.getCoresForPlatform(game.platformId) else emptyList(),
                    selectedCoreId = selectedCoreId
                )
            }

            if (game.rommId != null) {
                refreshAchievementsInBackground(game.rommId, gameId)
            }
        }
    }
    private suspend fun fetchAndCacheAchievements(rommId: Long, gameId: Long): List<AchievementUi> {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements ?: emptyList()
                if (apiAchievements.isEmpty()) return emptyList()

                val earnedBadgeIds = getEarnedBadgeIds(rom.raId)

                val entities = apiAchievements.map { achievement ->
                    val isUnlocked = achievement.badgeId in earnedBadgeIds
                    com.nendo.argosy.data.local.entity.AchievementEntity(
                        gameId = gameId,
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = achievement.badgeUrl,
                        badgeUrlLock = achievement.badgeUrlLock,
                        isUnlocked = isUnlocked
                    )
                }
                achievementDao.replaceForGame(gameId, entities)

                val earnedCount = entities.count { it.isUnlocked }
                gameDao.updateAchievementCount(gameId, entities.size, earnedCount)

                apiAchievements.map { achievement ->
                    val isUnlocked = achievement.badgeId in earnedBadgeIds
                    AchievementUi(
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = if (isUnlocked) achievement.badgeUrl else (achievement.badgeUrlLock ?: achievement.badgeUrl),
                        isUnlocked = isUnlocked
                    )
                }
            }
            is RomMResult.Error -> emptyList()
        }
    }

    private fun getEarnedBadgeIds(gameRaId: Long?): Set<String> {
        if (gameRaId == null) return emptySet()
        return romMRepository.getEarnedBadgeIds(gameRaId)
    }

    private suspend fun refreshAchievementsInBackground(rommId: Long, gameId: Long) {
        val fresh = fetchAndCacheAchievements(rommId, gameId)
        if (fresh.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(game = state.game?.copy(achievements = fresh))
            }
        }
    }

    private fun com.nendo.argosy.data.local.entity.AchievementEntity.toUi() = AchievementUi(
        raId = raId,
        title = title,
        description = description,
        points = points,
        type = type,
        badgeUrl = badgeUrlLock ?: badgeUrl,
        isUnlocked = isUnlocked
    )

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
                is LaunchResult.NoCore -> {
                    notificationManager.showError("No compatible RetroArch core installed for ${result.platformId}")
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
            val isRetroArch = it.game?.isRetroArchEmulator == true
            val isMultiDisc = it.game?.isMultiDisc == true
            var optionCount = 2  // Base: Emulator + Hide
            if (isMultiDisc) optionCount++  // Select Disc
            if (isRetroArch) optionCount++  // Change Core
            if (isRommGame) optionCount += 2  // Rate + Difficulty
            if (isDownloaded) optionCount++  // Delete
            val maxIndex = optionCount - 1
            val newIndex = (it.moreOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun confirmOptionSelection(onBack: () -> Unit) {
        val state = _uiState.value
        val isDownloaded = state.downloadStatus == GameDownloadStatus.DOWNLOADED
        val isRommGame = state.game?.isRommGame == true
        val isRetroArch = state.game?.isRetroArchEmulator == true
        val isMultiDisc = state.game?.isMultiDisc == true
        val index = state.moreOptionsFocusIndex

        var currentIdx = 0
        val discIdx = if (isMultiDisc) currentIdx++ else -1
        val emulatorIdx = currentIdx++
        val coreIdx = if (isRetroArch) currentIdx++ else -1
        val rateIdx = if (isRommGame) currentIdx++ else -1
        val difficultyIdx = if (isRommGame) currentIdx++ else -1
        val deleteIdx = if (isDownloaded) currentIdx++ else -1
        val hideIdx = currentIdx

        when (index) {
            discIdx -> showDiscPicker()
            emulatorIdx -> showEmulatorPicker()
            coreIdx -> showCorePicker()
            rateIdx -> showRatingPicker(RatingType.OPINION)
            difficultyIdx -> showRatingPicker(RatingType.DIFFICULTY)
            deleteIdx -> { toggleMoreOptions(); deleteLocalFile() }
            hideIdx -> { hideGame(); onBack() }
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

    fun showCorePicker() {
        val game = _uiState.value.game ?: return
        if (!game.isRetroArchEmulator) return
        val cores = EmulatorRegistry.getCoresForPlatform(game.platformId)
        if (cores.isEmpty()) return

        val initialIndex = _uiState.value.selectedCoreId?.let { selectedId ->
            val idx = cores.indexOfFirst { it.id == selectedId }
            if (idx >= 0) idx + 1 else 1
        } ?: 0

        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showCorePicker = true,
                availableCores = cores,
                corePickerFocusIndex = initialIndex
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissCorePicker() {
        _uiState.update { it.copy(showCorePicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveCorePickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.availableCores.size
            val newIndex = (state.corePickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(corePickerFocusIndex = newIndex)
        }
    }

    fun selectCore(coreId: String?) {
        viewModelScope.launch {
            val gameId = currentGameId
            configureEmulatorUseCase.setCoreForGame(gameId, coreId)
            _uiState.update { it.copy(showCorePicker = false) }
            loadGame(gameId)
        }
    }

    fun confirmCoreSelection() {
        val state = _uiState.value
        val index = state.corePickerFocusIndex
        if (index == 0) {
            selectCore(null)
        } else {
            val core = state.availableCores.getOrNull(index - 1)
            selectCore(core?.id)
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
        canPlay: Boolean,
        isRetroArch: Boolean = false,
        selectedCoreName: String? = null,
        achievements: List<AchievementUi> = emptyList()
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
            achievements = achievements,
            emulatorName = emulatorName,
            canPlay = canPlay,
            isMultiDisc = isMultiDisc,
            lastPlayedDiscId = lastPlayedDiscId,
            isRetroArchEmulator = isRetroArch,
            selectedCoreName = selectedCoreName
        )
    }

    fun showLaunchError(message: String) {
        notificationManager.showError(message)
    }

    fun navigateToPreviousGame() {
        gameNavigationContext.getPreviousGameId(currentGameId)?.let { prevId ->
            loadGame(prevId)
        }
    }

    fun navigateToNextGame() {
        gameNavigationContext.getNextGameId(currentGameId)?.let { nextId ->
            loadGame(nextId)
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onSnapUp: () -> Boolean = { false },
        onSnapDown: () -> Boolean = { false },
        onSectionLeft: () -> Unit = {},
        onSectionRight: () -> Unit = {},
        onPrevGame: () -> Unit = {},
        onNextGame: () -> Unit = {}
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            return when {
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                state.showCorePicker -> {
                    moveCorePickerFocus(-1)
                    InputResult.HANDLED
                }
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
                    if (onSnapUp()) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            return when {
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                state.showCorePicker -> {
                    moveCorePickerFocus(1)
                    InputResult.HANDLED
                }
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
                    if (onSnapDown()) InputResult.HANDLED else InputResult.UNHANDLED
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
                state.showMoreOptions || state.showEmulatorPicker || state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt -> {
                    return InputResult.UNHANDLED
                }
                else -> {
                    onSectionLeft()
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
                state.showMoreOptions || state.showEmulatorPicker || state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt -> {
                    return InputResult.UNHANDLED
                }
                else -> {
                    onSectionRight()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onPrevSection(): InputResult {
            val state = _uiState.value
            if (state.showRatingPicker || state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt) {
                return InputResult.UNHANDLED
            }
            onPrevGame()
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            if (state.showRatingPicker || state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt) {
                return InputResult.UNHANDLED
            }
            onNextGame()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            when {
                state.showRatingPicker -> confirmRating()
                state.showMissingDiscPrompt -> repairAndPlay()
                state.showCorePicker -> confirmCoreSelection()
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
                state.showCorePicker -> dismissCorePicker()
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
            if (state.showCorePicker) {
                dismissCorePicker()
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
