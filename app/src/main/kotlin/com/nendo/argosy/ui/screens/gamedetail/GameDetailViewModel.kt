package com.nendo.argosy.ui.screens.gamedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.GameUpdateBus
import com.nendo.argosy.ui.screens.gamedetail.delegates.AchievementDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.DownloadDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.MoreOptionsDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.PickerSelection
import com.nendo.argosy.ui.screens.gamedetail.delegates.PlayOptionsDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.RatingsStatusDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.SaveManagementDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.ScreenshotDelegate
import com.nendo.argosy.ui.ModalResetSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameDetailViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformRepository: PlatformRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorResolver: EmulatorResolver,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val launchGameUseCase: LaunchGameUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val romMRepository: RomMRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val imageCacheManager: ImageCacheManager,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val gameUpdateBus: GameUpdateBus,
    private val repairImageCacheUseCase: RepairImageCacheUseCase,
    private val modalResetSignal: ModalResetSignal,
    private val titleIdDownloadObserver: com.nendo.argosy.data.emulator.TitleIdDownloadObserver,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    val pickerModalDelegate: PickerModalDelegate,
    private val achievementDelegate: AchievementDelegate,
    private val downloadDelegate: DownloadDelegate,
    private val saveManagement: SaveManagementDelegate,
    private val screenshotDelegate: ScreenshotDelegate,
    private val ratingsStatus: RatingsStatusDelegate,
    private val playOptionsDelegate: PlayOptionsDelegate,
    private val moreOptionsDelegate: MoreOptionsDelegate
) : ViewModel() {

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    private val _uiState = MutableStateFlow(GameDetailUiState())
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private val _launchEvents = MutableSharedFlow<LaunchEvent>()
    val launchEvents: SharedFlow<LaunchEvent> = _launchEvents.asSharedFlow()

    private var currentGameId: Long = 0
    private var lastActionTime: Long = 0
    private val actionDebounceMs = 300L
    private var pageLoadTime: Long = 0
    private val pageLoadDebounceMs = 500L

    private var backgroundRepairPending = false
    private var gameFilesObserverJob: kotlinx.coroutines.Job? = null
    private var gameEntityObserverJob: kotlinx.coroutines.Job? = null

    val saveChannelDelegate get() = saveManagement.saveChannelDelegate

    private val _requestSafGrant = MutableStateFlow(false)
    val requestSafGrant: StateFlow<Boolean> = _requestSafGrant.asStateFlow()

    override fun onCleared() {
        super.onCleared()
        imageCacheManager.resumeBackgroundCaching()
    }

    @Deprecated("Hardcore conflict is now handled by GameLaunchDelegate callbacks")
    fun onKeepHardcore() { }

    @Deprecated("Hardcore conflict is now handled by GameLaunchDelegate callbacks")
    fun onDowngradeToCasual() { }

    @Deprecated("Hardcore conflict is now handled by GameLaunchDelegate callbacks")
    fun onKeepLocal() { }

    fun setHardcoreConflictFocusIndex(index: Int) {
        _uiState.update { it.copy(hardcoreConflictFocusIndex = index) }
    }

    fun repairBackgroundImage(gameId: Long, failedPath: String) {
        if (backgroundRepairPending) return
        backgroundRepairPending = true

        viewModelScope.launch {
            val repairedUrl = repairImageCacheUseCase.repairBackground(gameId, failedPath)
            if (repairedUrl != null) {
                _uiState.update { it.copy(repairedBackgroundPath = repairedUrl) }
            }
            backgroundRepairPending = false
        }
    }

    init {
        modalResetSignal.signal.onEach { resetAllModals() }.launchIn(viewModelScope)

        viewModelScope.launch { emulatorDetector.detectEmulators() }

        viewModelScope.launch {
            saveManagement.saveChannelDelegate.state.collect { saveState ->
                _uiState.update { it.copy(saveChannel = saveState) }
            }
        }
        viewModelScope.launch {
            gameLaunchDelegate.syncOverlayState.collect { overlayState ->
                _uiState.update { it.copy(syncOverlayState = overlayState) }
            }
        }
        viewModelScope.launch {
            collectionModalDelegate.state.collect { modalState ->
                _uiState.update {
                    it.copy(
                        showAddToCollectionModal = modalState.isVisible,
                        collections = modalState.collections,
                        collectionModalFocusIndex = modalState.focusIndex,
                        showCreateCollectionDialog = modalState.showCreateDialog
                    )
                }
            }
        }
        viewModelScope.launch {
            pickerModalDelegate.selection.collect { selection ->
                if (selection == null) return@collect
                handlePickerSelection(selection)
                pickerModalDelegate.clearSelection()
            }
        }

        downloadDelegate.observeDownloads(viewModelScope, { currentGameId }) { gameId ->
            loadGame(gameId)
        }

        viewModelScope.launch {
            downloadDelegate.state.collect { dlState ->
                _uiState.update {
                    it.copy(
                        downloadStatus = dlState.downloadStatus,
                        downloadProgress = dlState.downloadProgress,
                        downloadSizeBytes = dlState.downloadSizeBytes,
                        isRefreshingGameData = dlState.isRefreshingGameData,
                        showExtractionFailedPrompt = dlState.showExtractionFailedPrompt,
                        extractionFailedInfo = dlState.extractionFailedInfo,
                        extractionPromptFocusIndex = dlState.extractionPromptFocusIndex,
                        showMissingDiscPrompt = dlState.showMissingDiscPrompt,
                        missingDiscNumbers = dlState.missingDiscNumbers
                    )
                }
            }
        }
        viewModelScope.launch {
            downloadDelegate.launchEvents.collect { _launchEvents.emit(it) }
        }

        viewModelScope.launch {
            screenshotDelegate.state.collect { ssState ->
                _uiState.update {
                    it.copy(
                        focusedScreenshotIndex = ssState.focusedScreenshotIndex,
                        showScreenshotViewer = ssState.showScreenshotViewer,
                        viewerScreenshotIndex = ssState.viewerScreenshotIndex
                    )
                }
            }
        }

        viewModelScope.launch {
            ratingsStatus.state.collect { rsState ->
                _uiState.update {
                    it.copy(
                        showRatingPicker = rsState.showRatingPicker,
                        ratingPickerType = rsState.ratingPickerType,
                        ratingPickerValue = rsState.ratingPickerValue,
                        showStatusPicker = rsState.showStatusPicker,
                        statusPickerValue = rsState.statusPickerValue,
                        showRatingsStatusMenu = rsState.showRatingsStatusMenu,
                        ratingsStatusFocusIndex = rsState.ratingsStatusFocusIndex
                    )
                }
            }
        }

        viewModelScope.launch {
            playOptionsDelegate.state.collect { poState ->
                _uiState.update {
                    it.copy(
                        showPlayOptions = poState.showPlayOptions,
                        playOptionsFocusIndex = poState.playOptionsFocusIndex,
                        hasCasualSaves = poState.hasCasualSaves,
                        hasHardcoreSave = poState.hasHardcoreSave,
                        hasRASupport = poState.hasRASupport,
                        isRALoggedIn = poState.isRALoggedIn,
                        isOnline = poState.isOnline
                    )
                }
            }
        }

        viewModelScope.launch {
            moreOptionsDelegate.state.collect { moState ->
                _uiState.update {
                    it.copy(
                        showMoreOptions = moState.showMoreOptions,
                        moreOptionsFocusIndex = moState.moreOptionsFocusIndex
                    )
                }
            }
        }

        viewModelScope.launch {
            achievementDelegate.achievements.collect { achievements ->
                _uiState.update { state ->
                    state.copy(game = state.game?.copy(achievements = achievements))
                }
            }
        }

        viewModelScope.launch {
            gameUpdateBus.updates.collect { update ->
                if (update.gameId == currentGameId) {
                    _uiState.update { state ->
                        state.copy(
                            game = state.game?.copy(
                                playTimeMinutes = update.playTimeMinutes ?: state.game.playTimeMinutes,
                                status = update.status ?: state.game.status
                            )
                        )
                    }
                }
            }
        }
    }

    private suspend fun handlePickerSelection(selection: PickerSelection) {
        when (selection) {
            is PickerSelection.Emulator -> {
                val gameId = currentGameId
                val game = gameRepository.getById(gameId) ?: return
                configureEmulatorUseCase.setForGame(gameId, game.platformId, game.platformSlug, selection.emulator)
                loadGame(gameId)
            }
            is PickerSelection.Core -> {
                configureEmulatorUseCase.setCoreForGame(currentGameId, selection.coreId)
                loadGame(currentGameId)
            }
            is PickerSelection.SteamLauncher -> {
                val launcher = selection.launcher
                if (launcher == null) {
                    gameRepository.updateSteamLauncher(currentGameId, null, false)
                } else {
                    gameRepository.updateSteamLauncher(currentGameId, launcher.packageName, true)
                }
                loadGame(currentGameId)
            }
            is PickerSelection.Disc -> {
                val result = launchGameUseCase(currentGameId, selectedDiscPath = selection.discPath)
                when (result) {
                    is LaunchResult.Success -> {
                        soundManager.play(SoundType.LAUNCH_GAME)
                        val options = displayAffinityHelper.getActivityOptions(
                            forEmulator = true,
                            rolesSwapped = sessionStateStore.isRolesSwapped()
                        )
                        _launchEvents.emit(LaunchEvent.LaunchIntent(result.intent, options))
                    }
                    is LaunchResult.Error -> notificationManager.showError(result.message)
                    else -> notificationManager.showError("Failed to launch disc")
                }
            }
            is PickerSelection.UpdateFile -> {
                val game = _uiState.value.game ?: return
                downloadDelegate.downloadUpdateFile(
                    viewModelScope, currentGameId, selection.file,
                    game.title, game.platformSlug, game.coverPath
                )
            }
        }
    }

    fun loadGame(gameId: Long) {
        currentGameId = gameId
        pageLoadTime = System.currentTimeMillis()
        imageCacheManager.pauseBackgroundCaching()
        viewModelScope.launch {
            if (emulatorDetector.installedEmulators.value.isEmpty()) {
                emulatorDetector.detectEmulators()
            }

            val game = gameRepository.getById(gameId) ?: return@launch
            val platform = platformRepository.getById(game.platformId)

            val gameSpecificConfig = emulatorConfigDao.getByGameId(gameId)
            val platformDefaultConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
            val emulatorConfig = gameSpecificConfig ?: platformDefaultConfig

            val emulatorName = emulatorConfig?.displayName
                ?: emulatorDetector.getPreferredEmulator(game.platformSlug)?.def?.displayName

            val emulatorDef = emulatorConfig?.packageName?.let { emulatorDetector.getByPackage(it) }
                ?: emulatorDetector.getPreferredEmulator(game.platformSlug)?.def
            val isRetroArch = emulatorDef?.launchConfig is LaunchConfig.RetroArch
            val isBuiltIn = emulatorDef?.launchConfig is LaunchConfig.BuiltIn

            val selectedCoreId = gameSpecificConfig?.coreName
                ?: platformDefaultConfig?.coreName
                ?: EmulatorRegistry.getDefaultCore(game.platformSlug)?.id
            val selectedCoreName = if (isRetroArch || isBuiltIn) {
                EmulatorRegistry.getCoresForPlatform(game.platformSlug)
                    .find { it.id == selectedCoreId }?.displayName
            } else null

            val isSteamGame = game.source == GameSource.STEAM
            val isAndroidApp = game.source == GameSource.ANDROID_APP || game.platformSlug == "android"
            val steamLauncherName = if (isSteamGame) {
                game.steamLauncher?.let { SteamLaunchers.getByPackage(it)?.displayName } ?: "Auto"
            } else null
            val fileExists = gameRepository.validateAndDiscoverGame(gameId)

            val canPlay = when {
                game.source == GameSource.ANDROID_APP -> true
                isAndroidApp -> game.packageName != null
                isSteamGame -> {
                    val launcher = game.steamLauncher?.let { SteamLaunchers.getByPackage(it) }
                        ?: SteamLaunchers.getPreferred(context)
                    launcher?.isInstalled(context) == true
                }
                game.isMultiDisc -> {
                    val downloadedCount = gameDiscDao.getDownloadedDiscCount(gameId)
                    downloadedCount > 0 && emulatorDetector.hasAnyEmulator(game.platformSlug)
                }
                else -> fileExists && emulatorDetector.hasAnyEmulator(game.platformSlug)
            }

            val downloadStatus = when {
                game.source == GameSource.ANDROID_APP -> GameDownloadStatus.DOWNLOADED
                isAndroidApp && fileExists && game.packageName == null -> GameDownloadStatus.NEEDS_INSTALL
                isSteamGame || fileExists -> GameDownloadStatus.DOWNLOADED
                game.isMultiDisc -> {
                    val downloadedCount = gameDiscDao.getDownloadedDiscCount(gameId)
                    if (downloadedCount > 0) GameDownloadStatus.DOWNLOADED else GameDownloadStatus.NOT_DOWNLOADED
                }
                else -> GameDownloadStatus.NOT_DOWNLOADED
            }

            var siblingIds = gameNavigationContext.getGameIds()
            if (siblingIds.isEmpty() || !siblingIds.contains(gameId)) {
                val platformGames = gameRepository.getByPlatform(game.platformId)
                siblingIds = platformGames.map { it.id }
                gameNavigationContext.setContext(siblingIds)
            }
            val currentIndex = gameNavigationContext.getIndex(gameId)

            achievementDelegate.loadCached(gameId, game.rommId != null)
            val cachedAchievements = achievementDelegate.achievements.value

            val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
            val prefs = preferencesRepository.userPreferences.first()
            val canManageSaves = prefs.saveSyncEnabled &&
                downloadStatus == GameDownloadStatus.DOWNLOADED &&
                game.rommId != null &&
                emulatorId != null &&
                SavePathRegistry.getConfig(emulatorId) != null

            val saveStatusInfo = if (canManageSaves) {
                saveManagement.loadSaveStatusInfo(gameId, emulatorId!!, game.activeSaveChannel, game.activeSaveTimestamp)
            } else null

            val (updateFilesUi, dlcFilesUi) = loadUpdateAndDlcFiles(gameId, game.platformSlug, game.localPath)

            val downloadSizeBytes = when {
                game.isMultiDisc -> gameDiscDao.getTotalFileSize(gameId)
                else -> game.fileSizeBytes
            }

            downloadDelegate.updateDownloadStatus(downloadStatus, if (downloadStatus == GameDownloadStatus.DOWNLOADED) 1f else 0f)
            downloadDelegate.updateDownloadSize(downloadSizeBytes)

            _uiState.update { state ->
                state.copy(
                    game = game.toGameDetailUi(
                        platformName = platform?.name ?: "Unknown",
                        emulatorName = emulatorName,
                        canPlay = canPlay,
                        isRetroArch = isRetroArch,
                        isBuiltIn = isBuiltIn,
                        selectedCoreName = selectedCoreName,
                        achievements = cachedAchievements,
                        canManageSaves = canManageSaves,
                        steamLauncherName = steamLauncherName
                    ),
                    isLoading = false,
                    selectedCoreId = selectedCoreId,
                    saveChannel = state.saveChannel.copy(activeChannel = game.activeSaveChannel),
                    saveStatusInfo = saveStatusInfo,
                    updateFiles = updateFilesUi,
                    dlcFiles = dlcFilesUi,
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex
                )
            }

            if (game.rommId != null) {
                refreshUserPropsInBackground(gameId)
                refreshAchievementsInBackground(game.rommId, gameId)
                if (!game.isMultiDisc && (game.fileSizeBytes == null || game.fileSizeBytes == 0L)) {
                    downloadDelegate.refreshDownloadSizeInBackground(viewModelScope, game.rommId, gameId)
                }
            }

            val needsTitleId = game.platformSlug in setOf("switch", "wiiu", "3ds", "vita", "psvita", "psp")
            if (needsTitleId && game.titleId == null && game.localPath != null) {
                viewModelScope.launch {
                    titleIdDownloadObserver.extractTitleIdForGame(gameId)
                }
            }

            observeGameFiles(gameId, game.platformSlug, game.localPath)
            observeGameEntity(gameId)
        }
    }

    private fun observeGameFiles(gameId: Long, platformSlug: String, localPath: String?) {
        gameFilesObserverJob?.cancel()
        gameFilesObserverJob = viewModelScope.launch {
            gameFileDao.observeFilesForGame(gameId).collect { files ->
                val hasUpdateSupport = ZipExtractor.hasUpdateSupport(platformSlug)

                val localUpdateFileNames = if (hasUpdateSupport && localPath != null) {
                    ZipExtractor.listAllUpdateFiles(localPath, platformSlug).map { it.name }.toSet()
                } else emptySet()

                val localDlcFileNames = if (hasUpdateSupport && localPath != null) {
                    ZipExtractor.listAllDlcFiles(localPath, platformSlug).map { it.name }.toSet()
                } else emptySet()

                val dbUpdates = files.filter { it.category == "update" }.map { file ->
                    UpdateFileUi(
                        fileName = file.fileName, filePath = file.filePath,
                        sizeBytes = file.fileSize, type = UpdateFileType.UPDATE,
                        isDownloaded = file.fileName in localUpdateFileNames,
                        gameFileId = file.id, rommFileId = file.rommFileId, romId = file.romId
                    )
                }

                val dbDlc = files.filter { it.category == "dlc" }.map { file ->
                    UpdateFileUi(
                        fileName = file.fileName, filePath = file.filePath,
                        sizeBytes = file.fileSize, type = UpdateFileType.DLC,
                        isDownloaded = file.fileName in localDlcFileNames,
                        gameFileId = file.id, rommFileId = file.rommFileId, romId = file.romId
                    )
                }

                val localUpdates = if (hasUpdateSupport && localPath != null) {
                    ZipExtractor.listAllUpdateFiles(localPath, platformSlug)
                        .filter { file -> dbUpdates.none { it.fileName == file.name } }
                        .map { file ->
                            UpdateFileUi(fileName = file.name, filePath = file.absolutePath,
                                sizeBytes = file.length(), type = UpdateFileType.UPDATE, isDownloaded = true)
                        }
                } else emptyList()

                val localDlc = if (hasUpdateSupport && localPath != null) {
                    ZipExtractor.listAllDlcFiles(localPath, platformSlug)
                        .filter { file -> dbDlc.none { it.fileName == file.name } }
                        .map { file ->
                            UpdateFileUi(fileName = file.name, filePath = file.absolutePath,
                                sizeBytes = file.length(), type = UpdateFileType.DLC, isDownloaded = true)
                        }
                } else emptyList()

                _uiState.update { state ->
                    state.copy(updateFiles = dbUpdates + localUpdates, dlcFiles = dbDlc + localDlc)
                }
            }
        }
    }

    private fun observeGameEntity(gameId: Long) {
        gameEntityObserverJob?.cancel()
        gameEntityObserverJob = viewModelScope.launch {
            gameRepository.observeById(gameId).collect { updatedGame ->
                if (updatedGame == null) return@collect
                _uiState.update { state ->
                    val currentGame = state.game ?: return@update state
                    val gameUpdated = currentGame.titleId != updatedGame.titleId
                    val oldTimestamp = state.saveStatusInfo?.activeSaveTimestamp
                    val newTimestamp = updatedGame.activeSaveTimestamp
                    val oldChannel = state.saveChannel.activeChannel
                    val newChannel = updatedGame.activeSaveChannel
                    val saveUpdated = oldTimestamp != newTimestamp || oldChannel != newChannel

                    when {
                        gameUpdated || saveUpdated -> state.copy(
                            game = currentGame.copy(titleId = updatedGame.titleId),
                            saveChannel = state.saveChannel.copy(activeChannel = updatedGame.activeSaveChannel),
                            saveStatusInfo = state.saveStatusInfo?.copy(
                                channelName = updatedGame.activeSaveChannel,
                                activeSaveTimestamp = updatedGame.activeSaveTimestamp
                            )
                        )
                        else -> state
                    }
                }
            }
        }
    }

    private fun refreshUserPropsInBackground(gameId: Long) {
        viewModelScope.launch {
            when (romMRepository.refreshUserProps(gameId)) {
                is RomMResult.Success -> {
                    val refreshedGame = gameRepository.getById(gameId) ?: return@launch
                    _uiState.update { state ->
                        state.copy(
                            game = state.game?.copy(
                                userRating = refreshedGame.userRating,
                                userDifficulty = refreshedGame.userDifficulty,
                                status = refreshedGame.status
                            )
                        )
                    }
                }
                is RomMResult.Error -> { }
            }
        }
    }

    private fun refreshAchievementsInBackground(rommId: Long, gameId: Long) {
        achievementDelegate.refresh(viewModelScope, gameId, rommId)
    }

    private suspend fun loadUpdateAndDlcFiles(
        gameId: Long, platformSlug: String, localPath: String?
    ): Pair<List<UpdateFileUi>, List<UpdateFileUi>> {
        val hasUpdateSupport = ZipExtractor.hasUpdateSupport(platformSlug)
        val remoteFiles = gameFileDao.getFilesForGame(gameId)

        val localUpdateFileNames = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllUpdateFiles(localPath, platformSlug).map { it.name }.toSet()
        } else emptySet()

        val localDlcFileNames = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllDlcFiles(localPath, platformSlug).map { it.name }.toSet()
        } else emptySet()

        val dbUpdates = remoteFiles.filter { it.category == "update" }.map { file ->
            UpdateFileUi(
                fileName = file.fileName, filePath = file.filePath,
                sizeBytes = file.fileSize, type = UpdateFileType.UPDATE,
                isDownloaded = file.fileName in localUpdateFileNames,
                gameFileId = file.id, rommFileId = file.rommFileId, romId = file.romId
            )
        }

        val dbDlc = remoteFiles.filter { it.category == "dlc" }.map { file ->
            UpdateFileUi(
                fileName = file.fileName, filePath = file.filePath,
                sizeBytes = file.fileSize, type = UpdateFileType.DLC,
                isDownloaded = file.fileName in localDlcFileNames,
                gameFileId = file.id, rommFileId = file.rommFileId, romId = file.romId
            )
        }

        val localUpdates = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllUpdateFiles(localPath, platformSlug)
                .filter { file -> dbUpdates.none { it.fileName == file.name } }
                .map { file ->
                    UpdateFileUi(fileName = file.name, filePath = file.absolutePath,
                        sizeBytes = file.length(), type = UpdateFileType.UPDATE, isDownloaded = true)
                }
        } else emptyList()

        val localDlc = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllDlcFiles(localPath, platformSlug)
                .filter { file -> dbDlc.none { it.fileName == file.name } }
                .map { file ->
                    UpdateFileUi(fileName = file.name, filePath = file.absolutePath,
                        sizeBytes = file.length(), type = UpdateFileType.DLC, isDownloaded = true)
                }
        } else emptyList()

        return (dbUpdates + localUpdates) to (dbDlc + localDlc)
    }

    // --- Download delegate forwarding ---

    fun downloadGame() = downloadDelegate.downloadGame(viewModelScope, currentGameId, pageLoadTime, pageLoadDebounceMs)

    fun downloadUpdateFile(file: UpdateFileUi) {
        val game = _uiState.value.game ?: return
        downloadDelegate.downloadUpdateFile(viewModelScope, currentGameId, file, game.title, game.platformSlug, game.coverPath)
    }

    fun dismissExtractionPrompt() = downloadDelegate.dismissExtractionPrompt()

    fun moveExtractionPromptFocus(delta: Int) = downloadDelegate.moveExtractionPromptFocus(delta)

    fun confirmExtractionPromptSelection() = downloadDelegate.confirmExtractionPromptSelection(viewModelScope)

    fun dismissMissingDiscPrompt() = downloadDelegate.dismissMissingDiscPrompt()

    fun repairAndPlay() = downloadDelegate.repairAndPlay(viewModelScope, currentGameId)

    fun refreshGameData() {
        downloadDelegate.refreshGameData(viewModelScope, currentGameId) {
            loadGame(currentGameId)
        }
    }

    // --- Play/Launch ---

    fun onResume() {
        if (gameLaunchDelegate.isSyncing) return
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
    }

    fun primaryAction() {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < actionDebounceMs) return
        lastActionTime = now

        val state = _uiState.value
        when (state.downloadStatus) {
            GameDownloadStatus.DOWNLOADED -> playGame()
            GameDownloadStatus.NEEDS_INSTALL -> downloadDelegate.installApk(viewModelScope, currentGameId)
            GameDownloadStatus.NOT_DOWNLOADED -> downloadGame()
            GameDownloadStatus.QUEUED, GameDownloadStatus.WAITING_FOR_STORAGE,
            GameDownloadStatus.DOWNLOADING, GameDownloadStatus.EXTRACTING,
            GameDownloadStatus.PAUSED -> { }
        }
    }

    fun playGame(discId: Long? = null) {
        if (gameLaunchDelegate.isSyncing) return

        viewModelScope.launch {
            val currentGame = _uiState.value.game ?: return@launch

            if (currentGame.isBuiltInEmulator && currentGame.achievements.isNotEmpty()) {
                if (playOptionsDelegate.shouldShowModeSelection(currentGameId, true, true)) {
                    playOptionsDelegate.showFreshGameModeSelection(viewModelScope, currentGameId)
                    return@launch
                }
            }

            _launchEvents.emit(LaunchEvent.NavigateToLaunch(currentGameId, discId = discId))
        }
    }

    fun showPlayOptions() {
        val hasAchievements = _uiState.value.game?.achievements?.isNotEmpty() == true
        playOptionsDelegate.showPlayOptions(viewModelScope, currentGameId, hasAchievements)
    }

    fun dismissPlayOptions() = playOptionsDelegate.dismissPlayOptions()

    fun movePlayOptionsFocus(delta: Int) = playOptionsDelegate.movePlayOptionsFocus(delta)

    private fun confirmPlayOptionSelection() {
        val action = playOptionsDelegate.confirmPlayOptionSelection() ?: return
        handlePlayOption(action)
    }

    fun handlePlayOption(action: com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction) {
        playOptionsDelegate.dismissPlayOptions()
        viewModelScope.launch {
            val launchMode = when (action) {
                is com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.Resume ->
                    com.nendo.argosy.libretro.LaunchMode.RESUME
                is com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewCasual ->
                    com.nendo.argosy.libretro.LaunchMode.NEW_CASUAL
                is com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewHardcore ->
                    com.nendo.argosy.libretro.LaunchMode.NEW_HARDCORE
                is com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.ResumeHardcore ->
                    com.nendo.argosy.libretro.LaunchMode.RESUME_HARDCORE
            }
            launchWithMode(launchMode)
        }
    }

    private suspend fun launchWithMode(launchMode: com.nendo.argosy.libretro.LaunchMode) {
        if (launchMode == com.nendo.argosy.libretro.LaunchMode.NEW_CASUAL ||
            launchMode == com.nendo.argosy.libretro.LaunchMode.NEW_HARDCORE) {
            gameRepository.updateActiveSaveChannel(currentGameId, null)
            gameRepository.updateActiveSaveTimestamp(currentGameId, null)
            _uiState.update { state ->
                state.copy(
                    saveChannel = state.saveChannel.copy(activeChannel = null),
                    saveStatusInfo = state.saveStatusInfo?.copy(channelName = null, activeSaveTimestamp = null)
                )
            }
        }

        val result = launchGameUseCase(currentGameId)
        when (result) {
            is LaunchResult.Success -> {
                val intentWithMode = result.intent.apply {
                    putExtra(com.nendo.argosy.libretro.LaunchMode.EXTRA_LAUNCH_MODE, launchMode.name)
                }
                soundManager.play(SoundType.LAUNCH_GAME)
                val options = displayAffinityHelper.getActivityOptions(
                    forEmulator = true, rolesSwapped = sessionStateStore.isRolesSwapped()
                )
                _launchEvents.emit(LaunchEvent.LaunchIntent(intentWithMode, options))
            }
            is LaunchResult.SelectDisc -> pickerModalDelegate.showDiscPicker(result.discs)
            is LaunchResult.NoEmulator -> showEmulatorPicker()
            is LaunchResult.NoCore -> showCorePicker()
            is LaunchResult.MissingDiscs -> downloadDelegate.showMissingDiscPrompt(result.missingDiscNumbers)
            is LaunchResult.NoRomFile, is LaunchResult.NoSteamLauncher,
            is LaunchResult.NoAndroidApp, is LaunchResult.NoScummVMGameId,
            is LaunchResult.Error -> { }
        }
    }

    // --- More Options delegate forwarding ---

    fun toggleMoreOptions() = moreOptionsDelegate.toggleMoreOptions()

    fun moveOptionsFocus(delta: Int) {
        val state = _uiState.value
        moreOptionsDelegate.moveOptionsFocus(
            delta = delta,
            downloadStatus = state.downloadStatus,
            isRommGame = state.game?.isRommGame == true,
            isAndroidApp = state.game?.isAndroidApp == true,
            canManageSaves = state.game?.canManageSaves == true,
            isRetroArch = state.game?.isRetroArchEmulator == true,
            isMultiDisc = state.game?.isMultiDisc == true,
            isSteamGame = state.game?.isSteamGame == true,
            hasUpdates = state.updateFiles.isNotEmpty() || state.dlcFiles.isNotEmpty()
        )
    }

    fun handleMoreOptionAction(action: MoreOptionAction, onBack: () -> Unit) {
        val isAndroidApp = _uiState.value.game?.isAndroidApp == true
        when (action) {
            MoreOptionAction.ManageSaves -> showSaveCacheDialog()
            MoreOptionAction.RatingsStatus -> showRatingsStatusMenu()
            MoreOptionAction.RateGame -> showRatingPicker(RatingType.OPINION)
            MoreOptionAction.SetDifficulty -> showRatingPicker(RatingType.DIFFICULTY)
            MoreOptionAction.SetStatus -> showStatusPicker()
            MoreOptionAction.ChangeEmulator -> showEmulatorPicker()
            MoreOptionAction.ChangeSteamLauncher -> showSteamLauncherPicker()
            MoreOptionAction.ChangeCore -> showCorePicker()
            MoreOptionAction.SelectDisc -> showDiscPicker()
            MoreOptionAction.UpdatesDlc -> showUpdatesPicker()
            MoreOptionAction.RefreshData -> refreshAndroidOrRommData()
            MoreOptionAction.RefreshTitleId -> refreshTitleId()
            MoreOptionAction.AddToCollection -> showAddToCollectionModal()
            MoreOptionAction.Delete -> {
                toggleMoreOptions()
                if (isAndroidApp) {
                    val pkg = _uiState.value.game?.packageName ?: return
                    downloadDelegate.uninstallAndroidApp(viewModelScope, pkg)
                } else {
                    val isSteam = _uiState.value.game?.isSteamGame == true
                    downloadDelegate.deleteLocalFile(viewModelScope, currentGameId, isSteam) { loadGame(currentGameId) }
                }
            }
            MoreOptionAction.ToggleHide -> { toggleHideGame(); onBack() }
        }
    }

    fun confirmOptionSelection(onBack: () -> Unit) {
        val state = _uiState.value
        val action = moreOptionsDelegate.resolveOptionAction(
            downloadStatus = state.downloadStatus,
            isRommGame = state.game?.isRommGame == true,
            isAndroidApp = state.game?.isAndroidApp == true,
            canManageSaves = state.game?.canManageSaves == true,
            isRetroArch = state.game?.isRetroArchEmulator == true,
            isMultiDisc = state.game?.isMultiDisc == true,
            isSteamGame = state.game?.isSteamGame == true,
            hasUpdates = state.updateFiles.isNotEmpty() || state.dlcFiles.isNotEmpty(),
            platformSlug = state.game?.platformSlug
        )
        if (action != null) {
            handleMoreOptionAction(action, onBack)
        } else {
            toggleMoreOptions()
        }
    }

    // --- Ratings/Status delegate forwarding ---

    fun showRatingPicker(type: RatingType) {
        val game = _uiState.value.game ?: return
        val currentValue = when (type) {
            RatingType.OPINION -> game.userRating
            RatingType.DIFFICULTY -> game.userDifficulty
        }
        moreOptionsDelegate.reset()
        ratingsStatus.showRatingPicker(type, currentValue)
    }

    fun dismissRatingPicker() = ratingsStatus.dismissRatingPicker()

    fun showRatingsStatusMenu() {
        moreOptionsDelegate.reset()
        ratingsStatus.showRatingsStatusMenu()
    }

    fun dismissRatingsStatusMenu() {
        ratingsStatus.dismissRatingsStatusMenu()
        moreOptionsDelegate.toggleMoreOptions()
    }

    fun changeRatingsStatusFocus(delta: Int) = ratingsStatus.changeRatingsStatusFocus(delta)

    fun confirmRatingsStatusSelection() {
        when (ratingsStatus.getRatingsStatusAction()) {
            0 -> showRatingPicker(RatingType.OPINION)
            1 -> showRatingPicker(RatingType.DIFFICULTY)
            2 -> showStatusPicker()
        }
    }

    fun changeRatingValue(delta: Int) = ratingsStatus.changeRatingValue(delta)

    fun setRatingValue(value: Int) = ratingsStatus.setRatingValue(value)

    fun confirmRating() = ratingsStatus.confirmRating(viewModelScope, currentGameId) { loadGame(currentGameId) }

    fun showStatusPicker() {
        val game = _uiState.value.game ?: return
        ratingsStatus.showStatusPicker(game.status)
    }

    fun dismissStatusPicker() = ratingsStatus.dismissStatusPicker()

    fun changeStatusValue(delta: Int) = ratingsStatus.changeStatusValue(delta)

    fun selectStatus(value: String) {
        ratingsStatus.selectStatus(value)
        confirmStatus()
    }

    fun confirmStatus() = ratingsStatus.confirmStatus(viewModelScope, currentGameId) { loadGame(currentGameId) }

    // --- Screenshot delegate forwarding ---

    fun setFocusedScreenshotIndex(index: Int) = screenshotDelegate.setFocusedScreenshotIndex(index)

    fun moveScreenshotFocus(delta: Int) {
        screenshotDelegate.moveScreenshotFocus(delta, _uiState.value.game?.screenshots ?: emptyList())
    }

    fun openScreenshotViewer(index: Int? = null) {
        screenshotDelegate.openScreenshotViewer(_uiState.value.game?.screenshots ?: emptyList(), index)
    }

    fun closeScreenshotViewer() = screenshotDelegate.closeScreenshotViewer()

    fun moveViewerIndex(delta: Int) {
        screenshotDelegate.moveViewerIndex(delta, _uiState.value.game?.screenshots ?: emptyList())
    }

    fun setCurrentScreenshotAsBackground() {
        screenshotDelegate.setCurrentScreenshotAsBackground(
            viewModelScope, currentGameId, _uiState.value.game?.screenshots ?: emptyList()
        ) { loadGame(currentGameId) }
    }

    // --- Save management delegate forwarding ---

    fun showSaveCacheDialog() {
        moreOptionsDelegate.reset()
        saveManagement.showSaveCacheDialog(viewModelScope, currentGameId, _uiState.value.saveChannel.activeChannel) {
            notificationManager.showError("Cannot determine emulator for saves")
        }
    }

    fun dismissSaveCacheDialog() = saveManagement.saveChannelDelegate.dismiss()

    fun moveSaveCacheFocus(delta: Int) = saveManagement.saveChannelDelegate.moveFocus(delta)

    fun setSaveCacheFocusIndex(index: Int) = saveManagement.saveChannelDelegate.setFocusIndex(index)

    fun setSlotIndex(index: Int) = saveManagement.saveChannelDelegate.setSlotIndex(index)

    fun setHistoryIndex(index: Int) = saveManagement.saveChannelDelegate.setHistoryIndex(index)

    fun handleSaveCacheLongPress(index: Int) = saveManagement.saveChannelDelegate.handleLongPress(index)

    fun focusSlotsColumn() = saveManagement.saveChannelDelegate.focusSlotsColumn()

    fun focusHistoryColumn() = saveManagement.saveChannelDelegate.focusHistoryColumn()

    fun switchSaveTab(tab: com.nendo.argosy.ui.common.savechannel.SaveTab) = saveManagement.saveChannelDelegate.switchTab(tab)

    fun confirmSaveCacheSelection() {
        val game = _uiState.value.game ?: return
        saveManagement.confirmSaveCacheSelection(viewModelScope, currentGameId, game.platformId, game.platformSlug, ::handleSaveStatusChanged)
    }

    fun dismissRestoreConfirmation() = saveManagement.saveChannelDelegate.dismissRestoreConfirmation()

    fun restoreSave(syncToServer: Boolean) {
        val game = _uiState.value.game ?: return
        saveManagement.restoreSave(viewModelScope, currentGameId, game.platformId, game.platformSlug, syncToServer, ::handleSaveStatusChanged)
    }

    fun dismissRenameDialog() = saveManagement.saveChannelDelegate.dismissRenameDialog()

    fun updateRenameText(text: String) = saveManagement.saveChannelDelegate.updateRenameText(text)

    fun confirmRename() = saveManagement.saveChannelDelegate.confirmRename(viewModelScope)

    fun saveChannelSecondaryAction() = saveManagement.saveChannelDelegate.secondaryAction(viewModelScope, ::handleSaveStatusChanged)

    fun saveChannelTertiaryAction() = saveManagement.saveChannelDelegate.tertiaryAction()

    fun dismissDeleteConfirmation() = saveManagement.saveChannelDelegate.dismissDeleteConfirmation()

    fun confirmDeleteChannel() = saveManagement.saveChannelDelegate.confirmDeleteChannel(viewModelScope, ::handleSaveStatusChanged)

    fun dismissMigrateConfirmation() = saveManagement.saveChannelDelegate.dismissMigrateConfirmation()

    fun confirmMigrateChannel() {
        val game = _uiState.value.game ?: return
        saveManagement.confirmMigrateChannel(viewModelScope, currentGameId, game.platformId, game.platformSlug, ::handleSaveStatusChanged)
    }

    fun dismissDeleteLegacyConfirmation() = saveManagement.saveChannelDelegate.dismissDeleteLegacyConfirmation()

    fun confirmDeleteLegacyChannel() = saveManagement.saveChannelDelegate.confirmDeleteLegacyChannel(viewModelScope)

    private fun handleSaveStatusChanged(event: SaveStatusEvent) {
        val currentStatus = _uiState.value.saveStatusInfo?.status ?: com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NO_SAVE
        _uiState.update { state ->
            state.copy(
                saveChannel = state.saveChannel.copy(activeChannel = event.channelName),
                saveStatusInfo = SaveStatusInfo(
                    status = currentStatus,
                    channelName = event.channelName,
                    activeSaveTimestamp = event.timestamp,
                    lastSyncTime = state.saveStatusInfo?.lastSyncTime
                )
            )
        }
    }

    // --- Picker delegate forwarding ---

    fun showEmulatorPicker() {
        val game = _uiState.value.game ?: return
        moreOptionsDelegate.reset()
        viewModelScope.launch { pickerModalDelegate.showEmulatorPicker(game.platformSlug) }
    }

    fun dismissEmulatorPicker() = pickerModalDelegate.dismissEmulatorPicker()
    fun moveEmulatorPickerFocus(delta: Int) = pickerModalDelegate.moveEmulatorPickerFocus(delta)
    fun confirmEmulatorSelection() = pickerModalDelegate.confirmEmulatorSelection()

    fun showDiscPicker() { toggleMoreOptions(); playGame() }
    fun dismissDiscPicker() = pickerModalDelegate.dismissDiscPicker()
    fun navigateDiscPicker(direction: Int) = pickerModalDelegate.moveDiscPickerFocus(direction)
    fun selectFocusedDisc() = pickerModalDelegate.confirmDiscSelection()

    fun showSteamLauncherPicker() {
        val game = _uiState.value.game ?: return
        if (!game.isSteamGame) return
        moreOptionsDelegate.reset()
        pickerModalDelegate.showSteamLauncherPicker()
    }

    fun dismissSteamLauncherPicker() = pickerModalDelegate.dismissSteamLauncherPicker()
    fun moveSteamLauncherPickerFocus(delta: Int) = pickerModalDelegate.moveSteamLauncherPickerFocus(delta)
    fun confirmSteamLauncherSelection() = pickerModalDelegate.confirmSteamLauncherSelection()

    fun showCorePicker() {
        val game = _uiState.value.game ?: return
        if (!game.isRetroArchEmulator) return
        moreOptionsDelegate.reset()
        pickerModalDelegate.showCorePicker(game.platformSlug, _uiState.value.selectedCoreId)
    }

    fun dismissCorePicker() = pickerModalDelegate.dismissCorePicker()
    fun moveCorePickerFocus(delta: Int) = pickerModalDelegate.moveCorePickerFocus(delta)
    fun confirmCoreSelection() = pickerModalDelegate.confirmCoreSelection()

    fun showUpdatesPicker() {
        val state = _uiState.value
        if (state.updateFiles.isEmpty() && state.dlcFiles.isEmpty()) return
        moreOptionsDelegate.reset()
        pickerModalDelegate.showUpdatesPicker()
    }

    fun dismissUpdatesPicker() = pickerModalDelegate.dismissUpdatesPicker()

    fun moveUpdatesPickerFocus(delta: Int) {
        pickerModalDelegate.moveUpdatesPickerFocus(delta, _uiState.value.updateFiles, _uiState.value.dlcFiles)
    }

    private fun confirmUpdatesSelection() {
        pickerModalDelegate.confirmUpdatesSelection(_uiState.value.updateFiles, _uiState.value.dlcFiles)
    }

    fun installAllUpdatesAndDlc() { pickerModalDelegate.dismissUpdatesPicker(); playGame() }

    // --- Game actions ---

    fun toggleFavorite() {
        viewModelScope.launch {
            gameActions.toggleFavorite(currentGameId)
            loadGame(currentGameId)
        }
    }

    fun toggleHideGame() {
        viewModelScope.launch {
            val isHidden = _uiState.value.game?.isHidden ?: false
            if (isHidden) gameActions.unhideGame(currentGameId) else gameActions.hideGame(currentGameId)
        }
    }

    private fun refreshAndroidOrRommData() {
        val game = _uiState.value.game ?: return
        if (game.isAndroidApp) {
            val packageName = game.packageName ?: return
            moreOptionsDelegate.reset()
            downloadDelegate.refreshAndroidAppData(viewModelScope, currentGameId, packageName) { loadGame(currentGameId) }
        } else {
            moreOptionsDelegate.reset()
            refreshGameData()
        }
    }

    private fun refreshTitleId() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            moreOptionsDelegate.reset()
            titleIdDownloadObserver.extractTitleIdForGame(gameId)
        }
    }

    fun showLaunchError(message: String) = notificationManager.showError(message)

    // --- Navigation ---

    fun navigateToPreviousGame() {
        gameNavigationContext.getPreviousGameId(currentGameId)?.let { prevId ->
            _uiState.update { it.copy(menuFocusIndex = 0) }
            loadGame(prevId)
        }
    }

    fun navigateToNextGame() {
        gameNavigationContext.getNextGameId(currentGameId)?.let { nextId ->
            _uiState.update { it.copy(menuFocusIndex = 0) }
            loadGame(nextId)
        }
    }

    // --- Menu ---

    fun getMenuItemCount(): Int {
        val game = _uiState.value.game ?: return 4
        return getMenuItems().size
    }

    fun moveMenuFocus(delta: Int) {
        val menuItemCount = getMenuItemCount()
        if (menuItemCount == 0) return
        _uiState.update { state ->
            val newIndex = (state.menuFocusIndex + delta).coerceIn(0, menuItemCount - 1)
            state.copy(menuFocusIndex = newIndex)
        }
    }

    fun setMenuFocusIndex(index: Int) {
        val menuItemCount = getMenuItemCount()
        if (menuItemCount == 0) return
        _uiState.update { state ->
            state.copy(menuFocusIndex = index.coerceIn(0, menuItemCount - 1))
        }
    }

    fun getFocusedMenuItemIndex(): Int = _uiState.value.menuFocusIndex

    enum class MenuAction { PLAY, FAVORITE, OPTIONS, DETAILS, DESCRIPTION, SCREENSHOTS, ACHIEVEMENTS }

    fun getMenuItems(): List<MenuAction> {
        val game = _uiState.value.game ?: return listOf(MenuAction.PLAY, MenuAction.FAVORITE, MenuAction.OPTIONS, MenuAction.DETAILS)
        return buildList {
            add(MenuAction.PLAY); add(MenuAction.FAVORITE); add(MenuAction.OPTIONS); add(MenuAction.DETAILS)
            if (!game.description.isNullOrBlank()) add(MenuAction.DESCRIPTION)
            if (game.screenshots.isNotEmpty()) add(MenuAction.SCREENSHOTS)
            if (game.achievements.isNotEmpty()) add(MenuAction.ACHIEVEMENTS)
        }
    }

    fun getFocusedMenuAction(): MenuAction? = getMenuItems().getOrNull(_uiState.value.menuFocusIndex)

    fun executeMenuAction() {
        when (getFocusedMenuAction()) {
            MenuAction.PLAY -> primaryAction()
            MenuAction.FAVORITE -> toggleFavorite()
            MenuAction.OPTIONS -> toggleMoreOptions()
            MenuAction.DETAILS -> {}
            MenuAction.DESCRIPTION -> {}
            MenuAction.SCREENSHOTS -> openScreenshotViewer()
            MenuAction.ACHIEVEMENTS -> showAchievementList()
            null -> {}
        }
    }

    // --- Achievements ---

    fun showAchievementList() {
        _uiState.update { it.copy(showAchievementList = true, achievementListFocusIndex = 0) }
    }

    fun hideAchievementList() {
        _uiState.update { it.copy(showAchievementList = false, achievementListFocusIndex = 0) }
    }

    fun moveAchievementListFocus(delta: Int) {
        val achievements = _uiState.value.game?.achievements ?: return
        if (achievements.isEmpty()) return
        _uiState.update { state ->
            val newIndex = (state.achievementListFocusIndex + delta).coerceIn(0, achievements.size - 1)
            state.copy(achievementListFocusIndex = newIndex)
        }
    }

    // --- Collection modal ---

    fun showAddToCollectionModal() {
        val gameId = currentGameId
        if (gameId == 0L) return
        moreOptionsDelegate.reset()
        collectionModalDelegate.show(viewModelScope, gameId)
    }

    fun dismissAddToCollectionModal() = collectionModalDelegate.dismiss()
    fun moveCollectionFocusUp() = collectionModalDelegate.moveFocusUp()
    fun moveCollectionFocusDown() = collectionModalDelegate.moveFocusDown()
    fun confirmCollectionSelection() = collectionModalDelegate.confirmSelection(viewModelScope)
    fun toggleGameInCollection(collectionId: Long) = collectionModalDelegate.toggleCollection(viewModelScope, collectionId)
    fun showCreateCollectionFromModal() = collectionModalDelegate.showCreateDialog()
    fun hideCreateCollectionDialog() = collectionModalDelegate.hideCreateDialog()
    fun createCollectionFromModal(name: String) = collectionModalDelegate.createAndAdd(viewModelScope, name)

    // --- Permission modal ---

    fun dismissPermissionModal() {
        _uiState.update { it.copy(showPermissionModal = false) }
    }

    fun openAllFilesAccessSettings() {
        _uiState.update { it.copy(showPermissionModal = false) }
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun requestSafGrant() {
        _uiState.update { it.copy(showPermissionModal = false) }
        _requestSafGrant.value = true
    }

    fun onSafGrantResult(uri: android.net.Uri?) {
        _requestSafGrant.value = false
        if (uri == null) return

        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                preferencesRepository.setAndroidDataSafUri(uri.toString())
                playGame()
            } catch (e: Exception) {
                android.util.Log.e("GameDetailViewModel", "Failed to persist SAF permission: ${e.message}")
            }
        }
    }

    fun disableSaveSync() {
        viewModelScope.launch {
            preferencesRepository.setSaveSyncEnabled(false)
            _uiState.update { it.copy(showPermissionModal = false) }
            playGame()
        }
    }

    // --- Modal management ---

    private fun dismissAllModals() {
        resetAllModals()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun resetAllModals() {
        pickerModalDelegate.reset()
        moreOptionsDelegate.reset()
        ratingsStatus.reset()
        playOptionsDelegate.reset()
        screenshotDelegate.reset()
        _uiState.update {
            it.copy(
                showPermissionModal = false,
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false,
                showAchievementList = false,
                saveChannel = it.saveChannel.copy(
                    isVisible = false,
                    showRestoreConfirmation = false,
                    showRenameDialog = false,
                    showDeleteConfirmation = false
                )
            )
        }
    }

    // --- Input Handler ---

    fun createInputHandler(
        onBack: () -> Unit,
        onSnapUp: () -> Boolean = { false },
        onSnapDown: () -> Boolean = { false },
        onSectionLeft: () -> Unit = {},
        onSectionRight: () -> Unit = {},
        onPrevGame: () -> Unit = {},
        onNextGame: () -> Unit = {},
        isInScreenshotsSection: () -> Boolean = { false }
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            return when {
                saveState.showRenameDialog -> InputResult.UNHANDLED
                saveState.showRestoreConfirmation -> InputResult.UNHANDLED
                saveState.showDeleteConfirmation -> InputResult.UNHANDLED
                saveState.isVisible -> { moveSaveCacheFocus(-1); InputResult.HANDLED }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showPermissionModal -> InputResult.UNHANDLED
                state.showStatusPicker -> { changeStatusValue(-1); InputResult.HANDLED }
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                pickerState.showCorePicker -> { moveCorePickerFocus(-1); InputResult.HANDLED }
                pickerState.showDiscPicker -> { navigateDiscPicker(-1); InputResult.HANDLED }
                pickerState.showUpdatesPicker -> { moveUpdatesPickerFocus(-1); InputResult.HANDLED }
                pickerState.showEmulatorPicker -> { moveEmulatorPickerFocus(-1); InputResult.HANDLED }
                pickerState.showSteamLauncherPicker -> { moveSteamLauncherPickerFocus(-1); InputResult.HANDLED }
                state.showAddToCollectionModal -> { moveCollectionFocusUp(); InputResult.HANDLED }
                state.showRatingsStatusMenu -> { changeRatingsStatusFocus(-1); InputResult.HANDLED }
                state.showPlayOptions -> { movePlayOptionsFocus(-1); InputResult.HANDLED }
                state.showMoreOptions -> { moveOptionsFocus(-1); InputResult.HANDLED }
                state.showAchievementList -> { moveAchievementListFocus(-1); InputResult.HANDLED }
                else -> if (onSnapUp()) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            return when {
                saveState.showRenameDialog -> InputResult.UNHANDLED
                saveState.showRestoreConfirmation -> InputResult.UNHANDLED
                saveState.showDeleteConfirmation -> InputResult.UNHANDLED
                saveState.isVisible -> { moveSaveCacheFocus(1); InputResult.HANDLED }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showPermissionModal -> InputResult.UNHANDLED
                state.showStatusPicker -> { changeStatusValue(1); InputResult.HANDLED }
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                pickerState.showCorePicker -> { moveCorePickerFocus(1); InputResult.HANDLED }
                pickerState.showDiscPicker -> { navigateDiscPicker(1); InputResult.HANDLED }
                pickerState.showUpdatesPicker -> { moveUpdatesPickerFocus(1); InputResult.HANDLED }
                pickerState.showEmulatorPicker -> { moveEmulatorPickerFocus(1); InputResult.HANDLED }
                pickerState.showSteamLauncherPicker -> { moveSteamLauncherPickerFocus(1); InputResult.HANDLED }
                state.showAddToCollectionModal -> { moveCollectionFocusDown(); InputResult.HANDLED }
                state.showRatingsStatusMenu -> { changeRatingsStatusFocus(1); InputResult.HANDLED }
                state.showPlayOptions -> { movePlayOptionsFocus(1); InputResult.HANDLED }
                state.showMoreOptions -> { moveOptionsFocus(1); InputResult.HANDLED }
                state.showAchievementList -> { moveAchievementListFocus(1); InputResult.HANDLED }
                else -> if (onSnapDown()) InputResult.HANDLED else InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRestoreConfirmation || saveState.showDeleteConfirmation || saveState.showRenameDialog || saveState.showMigrateConfirmation || saveState.showDeleteLegacyConfirmation -> return InputResult.UNHANDLED
                saveState.isVisible -> {
                    when (saveState.selectedTab) {
                        com.nendo.argosy.ui.common.savechannel.SaveTab.SAVES -> focusSlotsColumn()
                        com.nendo.argosy.ui.common.savechannel.SaveTab.STATES -> {}
                    }
                    return InputResult.HANDLED
                }
                state.showScreenshotViewer -> { moveViewerIndex(-1); return InputResult.HANDLED }
                state.showRatingPicker -> { changeRatingValue(-1); return InputResult.HANDLED }
                state.showExtractionFailedPrompt -> { moveExtractionPromptFocus(-1); return InputResult.HANDLED }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt -> return InputResult.UNHANDLED
                else -> { onSectionLeft(); return InputResult.HANDLED }
            }
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRestoreConfirmation || saveState.showDeleteConfirmation || saveState.showRenameDialog || saveState.showMigrateConfirmation || saveState.showDeleteLegacyConfirmation -> return InputResult.UNHANDLED
                saveState.isVisible -> {
                    when (saveState.selectedTab) {
                        com.nendo.argosy.ui.common.savechannel.SaveTab.SAVES -> focusHistoryColumn()
                        com.nendo.argosy.ui.common.savechannel.SaveTab.STATES -> {}
                    }
                    return InputResult.HANDLED
                }
                state.showScreenshotViewer -> { moveViewerIndex(1); return InputResult.HANDLED }
                state.showRatingPicker -> { changeRatingValue(1); return InputResult.HANDLED }
                state.showExtractionFailedPrompt -> { moveExtractionPromptFocus(1); return InputResult.HANDLED }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt -> return InputResult.UNHANDLED
                else -> { onSectionRight(); return InputResult.HANDLED }
            }
        }

        override fun onPrevSection(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            if (saveState.isVisible) return InputResult.UNHANDLED
            if (saveState.showRestoreConfirmation || state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker || state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt || state.showExtractionFailedPrompt) return InputResult.UNHANDLED
            onPrevGame(); return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            if (saveState.isVisible) { saveManagement.saveChannelDelegate.syncServerSaves(viewModelScope); return InputResult.HANDLED }
            if (saveState.showRestoreConfirmation || state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker || state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt || state.showExtractionFailedPrompt) return InputResult.UNHANDLED
            onNextGame(); return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRenameDialog -> confirmRename()
                saveState.showDeleteConfirmation -> confirmDeleteChannel()
                saveState.showRestoreConfirmation -> restoreSave(syncToServer = false)
                saveState.showMigrateConfirmation -> confirmMigrateChannel()
                saveState.showDeleteLegacyConfirmation -> confirmDeleteLegacyChannel()
                saveState.isVisible -> confirmSaveCacheSelection()
                state.showScreenshotViewer -> closeScreenshotViewer()
                state.showPermissionModal -> return InputResult.UNHANDLED
                state.showRatingPicker -> confirmRating()
                state.showStatusPicker -> confirmStatus()
                state.showMissingDiscPrompt -> repairAndPlay()
                state.showExtractionFailedPrompt -> confirmExtractionPromptSelection()
                pickerState.showCorePicker -> confirmCoreSelection()
                pickerState.showDiscPicker -> selectFocusedDisc()
                pickerState.showUpdatesPicker -> confirmUpdatesSelection()
                pickerState.showEmulatorPicker -> confirmEmulatorSelection()
                pickerState.showSteamLauncherPicker -> confirmSteamLauncherSelection()
                state.showAddToCollectionModal -> confirmCollectionSelection()
                state.showRatingsStatusMenu -> confirmRatingsStatusSelection()
                state.showPlayOptions -> confirmPlayOptionSelection()
                state.showMoreOptions -> confirmOptionSelection(onBack)
                else -> executeMenuAction()
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRenameDialog -> dismissRenameDialog()
                saveState.showDeleteConfirmation -> dismissDeleteConfirmation()
                saveState.showRestoreConfirmation -> dismissRestoreConfirmation()
                saveState.showMigrateConfirmation -> dismissMigrateConfirmation()
                saveState.showDeleteLegacyConfirmation -> dismissDeleteLegacyConfirmation()
                saveState.isVisible -> dismissSaveCacheDialog()
                state.showScreenshotViewer -> closeScreenshotViewer()
                state.showAchievementList -> hideAchievementList()
                state.showRatingPicker -> dismissRatingPicker()
                state.showStatusPicker -> dismissStatusPicker()
                state.showMissingDiscPrompt -> dismissMissingDiscPrompt()
                state.showExtractionFailedPrompt -> dismissExtractionPrompt()
                pickerState.showCorePicker -> dismissCorePicker()
                pickerState.showDiscPicker -> dismissDiscPicker()
                pickerState.showUpdatesPicker -> dismissUpdatesPicker()
                pickerState.showEmulatorPicker -> dismissEmulatorPicker()
                pickerState.showSteamLauncherPicker -> dismissSteamLauncherPicker()
                state.showPermissionModal -> dismissPermissionModal()
                state.showAddToCollectionModal -> dismissAddToCollectionModal()
                state.showRatingsStatusMenu -> dismissRatingsStatusMenu()
                state.showPlayOptions -> dismissPlayOptions()
                state.showMoreOptions -> toggleMoreOptions()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            if (saveState.showRenameDialog) { dismissRenameDialog(); return InputResult.UNHANDLED }
            if (saveState.showDeleteConfirmation) { dismissDeleteConfirmation(); return InputResult.UNHANDLED }
            if (saveState.showRestoreConfirmation) { dismissRestoreConfirmation(); return InputResult.UNHANDLED }
            if (saveState.isVisible) { dismissSaveCacheDialog(); return InputResult.UNHANDLED }
            if (state.showRatingPicker) { dismissRatingPicker(); return InputResult.UNHANDLED }
            if (state.showStatusPicker) { dismissStatusPicker(); return InputResult.UNHANDLED }
            if (state.showMissingDiscPrompt) { dismissMissingDiscPrompt(); return InputResult.UNHANDLED }
            if (pickerState.showCorePicker) { dismissCorePicker(); return InputResult.UNHANDLED }
            if (pickerState.showUpdatesPicker) { dismissUpdatesPicker(); return InputResult.UNHANDLED }
            if (state.showPlayOptions) { dismissPlayOptions(); return InputResult.UNHANDLED }
            if (state.showMoreOptions) { toggleMoreOptions(); return InputResult.UNHANDLED }
            if (pickerState.showEmulatorPicker) { dismissEmulatorPicker(); return InputResult.UNHANDLED }
            if (pickerState.showSteamLauncherPicker) { dismissSteamLauncherPicker(); return InputResult.UNHANDLED }
            if (state.showAddToCollectionModal) { dismissAddToCollectionModal(); return InputResult.UNHANDLED }
            return InputResult.UNHANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation && !saveState.showMigrateConfirmation && !saveState.showDeleteLegacyConfirmation) {
                saveChannelSecondaryAction(); return InputResult.HANDLED
            }
            toggleFavorite(); return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation && !saveState.showMigrateConfirmation && !saveState.showDeleteLegacyConfirmation) {
                saveChannelTertiaryAction(); return InputResult.HANDLED
            }
            if (state.showScreenshotViewer) { setCurrentScreenshotAsBackground(); return InputResult.HANDLED }
            if (state.downloadStatus == GameDownloadStatus.DOWNLOADED && state.game?.isBuiltInEmulator == true) { showPlayOptions(); return InputResult.HANDLED }
            return InputResult.UNHANDLED
        }

        override fun onSelect(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            val anyModalOpen = state.showMoreOptions || state.showPlayOptions || pickerState.hasAnyPickerOpen || state.showRatingPicker || state.showStatusPicker || state.showMissingDiscPrompt || state.showScreenshotViewer || saveState.isVisible
            if (anyModalOpen) { dismissAllModals(); return InputResult.HANDLED }
            toggleMoreOptions(); return InputResult.HANDLED
        }
    }
}
