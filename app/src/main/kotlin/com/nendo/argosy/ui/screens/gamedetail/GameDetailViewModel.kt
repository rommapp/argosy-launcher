package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.domain.usecase.save.CheckSaveSyncPermissionUseCase
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.ui.common.savechannel.SaveChannelDelegate
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.ui.screens.common.CollectionModalDelegate
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.GameUpdateBus
import com.nendo.argosy.ui.screens.gamedetail.delegates.AchievementDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.GameRatingDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.PickerSelection
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
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val gameFileDao: GameFileDao,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorResolver: EmulatorResolver,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val launchGameUseCase: LaunchGameUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val romMRepository: RomMRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val achievementDelegate: AchievementDelegate,
    private val gameRatingDelegate: GameRatingDelegate,
    private val collectionModalDelegate: CollectionModalDelegate,
    private val achievementDao: com.nendo.argosy.data.local.dao.AchievementDao,
    private val imageCacheManager: ImageCacheManager,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    val saveChannelDelegate: SaveChannelDelegate,
    private val saveSyncDao: SaveSyncDao,
    private val checkSaveSyncPermissionUseCase: CheckSaveSyncPermissionUseCase,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val gameUpdateBus: GameUpdateBus,
    private val playStoreService: PlayStoreService,
    private val apkInstallManager: ApkInstallManager,
    private val repairImageCacheUseCase: RepairImageCacheUseCase,
    private val modalResetSignal: ModalResetSignal,
    private val gameLauncher: GameLauncher,
    private val collectionDao: com.nendo.argosy.data.local.dao.CollectionDao,
    private val saveCacheManager: com.nendo.argosy.data.repository.SaveCacheManager,
    private val raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository,
    private val saveSyncRepository: SaveSyncRepository,
    val pickerModalDelegate: PickerModalDelegate,
    private val titleIdDownloadObserver: com.nendo.argosy.data.emulator.TitleIdDownloadObserver,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper
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
        modalResetSignal.signal.onEach {
            resetAllModals()
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            emulatorDetector.detectEmulators()
        }
        viewModelScope.launch {
            saveChannelDelegate.state.collect { saveState ->
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
        viewModelScope.launch {
            downloadManager.state.collect { queueState ->
                val gameId = currentGameId
                if (gameId == 0L) return@collect

                val activeDownload = queueState.activeDownloads.find { it.gameId == gameId }
                val queued = queueState.queue.find { it.gameId == gameId }
                val completed = queueState.completed.find { it.gameId == gameId }

                val (status, progress) = when {
                    activeDownload?.state == DownloadState.EXTRACTING -> {
                        GameDownloadStatus.EXTRACTING to activeDownload.extractionPercent
                    }
                    activeDownload != null -> {
                        GameDownloadStatus.DOWNLOADING to activeDownload.progressPercent
                    }
                    queued?.state == DownloadState.EXTRACTING -> {
                        GameDownloadStatus.EXTRACTING to queued.extractionPercent
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
                        val currentStatus = _uiState.value.downloadStatus
                        if (currentStatus != GameDownloadStatus.NOT_DOWNLOADED) {
                            currentStatus to _uiState.value.downloadProgress
                        } else {
                            val game = _uiState.value.game
                            if (game?.canPlay == true) {
                                GameDownloadStatus.DOWNLOADED to 1f
                            } else {
                                GameDownloadStatus.NOT_DOWNLOADED to 0f
                            }
                        }
                    }
                }

                _uiState.update { it.copy(downloadStatus = status, downloadProgress = progress) }
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
                val game = gameDao.getById(gameId) ?: return
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
                    gameDao.updateSteamLauncher(currentGameId, null, false)
                } else {
                    gameDao.updateSteamLauncher(currentGameId, launcher.packageName, true)
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
                    is LaunchResult.Error -> {
                        notificationManager.showError(result.message)
                    }
                    else -> {
                        notificationManager.showError("Failed to launch disc")
                    }
                }
            }
            is PickerSelection.UpdateFile -> {
                downloadUpdateFile(selection.file)
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

            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformDao.getById(game.platformId)

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
                val platformGames = gameDao.getByPlatform(game.platformId)
                siblingIds = platformGames.map { it.id }
                gameNavigationContext.setContext(siblingIds)
            }
            val currentIndex = gameNavigationContext.getIndex(gameId)

            val cachedAchievements = if (game.rommId != null) {
                achievementDao.getByGameId(gameId).map { it.toAchievementUi() }
            } else {
                emptyList()
            }

            val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
            val canManageSaves = downloadStatus == GameDownloadStatus.DOWNLOADED &&
                game.rommId != null &&
                emulatorId != null &&
                SavePathRegistry.getConfig(emulatorId) != null

            val saveStatusInfo = if (canManageSaves) {
                loadSaveStatusInfo(gameId, emulatorId!!, game.activeSaveChannel, game.activeSaveTimestamp)
            } else null

            val (updateFilesUi, dlcFilesUi) = loadUpdateAndDlcFiles(gameId, game.platformSlug, game.localPath)

            val downloadSizeBytes = when {
                game.isMultiDisc -> gameDiscDao.getTotalFileSize(gameId)
                else -> game.fileSizeBytes
            }

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
                    downloadStatus = downloadStatus,
                    downloadProgress = if (downloadStatus == GameDownloadStatus.DOWNLOADED) 1f else 0f,
                    downloadSizeBytes = downloadSizeBytes,
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex,
                    selectedCoreId = selectedCoreId,
                    saveChannel = state.saveChannel.copy(activeChannel = game.activeSaveChannel),
                    saveStatusInfo = saveStatusInfo,
                    updateFiles = updateFilesUi,
                    dlcFiles = dlcFilesUi
                )
            }

            if (game.rommId != null) {
                refreshUserPropsInBackground(gameId)
                refreshAchievementsInBackground(game.rommId, gameId)
                if (!game.isMultiDisc && (game.fileSizeBytes == null || game.fileSizeBytes == 0L)) {
                    refreshDownloadSizeInBackground(game.rommId, gameId)
                }
            }

            // Extract title ID for folder-based save platforms if not already set
            val needsTitleId = game.platformSlug in setOf("switch", "wiiu", "3ds", "vita", "psvita", "psp")
            if (needsTitleId && game.titleId == null && game.localPath != null) {
                viewModelScope.launch {
                    titleIdDownloadObserver.extractTitleIdForGame(gameId)
                }
            }

            gameFilesObserverJob?.cancel()
            gameFilesObserverJob = viewModelScope.launch {
                gameFileDao.observeFilesForGame(gameId).collect { files ->
                    val platformSlug = game.platformSlug
                    val localPath = game.localPath
                    val hasUpdateSupport = ZipExtractor.hasUpdateSupport(platformSlug)

                    val localUpdateFileNames = if (hasUpdateSupport && localPath != null) {
                        ZipExtractor.listAllUpdateFiles(localPath, platformSlug).map { it.name }.toSet()
                    } else emptySet()

                    val localDlcFileNames = if (hasUpdateSupport && localPath != null) {
                        ZipExtractor.listAllDlcFiles(localPath, platformSlug).map { it.name }.toSet()
                    } else emptySet()

                    val dbUpdates = files
                        .filter { it.category == "update" }
                        .map { file ->
                            UpdateFileUi(
                                fileName = file.fileName,
                                filePath = file.filePath,
                                sizeBytes = file.fileSize,
                                type = UpdateFileType.UPDATE,
                                isDownloaded = file.fileName in localUpdateFileNames,
                                gameFileId = file.id,
                                rommFileId = file.rommFileId,
                                romId = file.romId
                            )
                        }

                    val dbDlc = files
                        .filter { it.category == "dlc" }
                        .map { file ->
                            UpdateFileUi(
                                fileName = file.fileName,
                                filePath = file.filePath,
                                sizeBytes = file.fileSize,
                                type = UpdateFileType.DLC,
                                isDownloaded = file.fileName in localDlcFileNames,
                                gameFileId = file.id,
                                rommFileId = file.rommFileId,
                                romId = file.romId
                            )
                        }

                    val localUpdates = if (hasUpdateSupport && localPath != null) {
                        ZipExtractor.listAllUpdateFiles(localPath, platformSlug)
                            .filter { file -> dbUpdates.none { it.fileName == file.name } }
                            .map { file ->
                                UpdateFileUi(
                                    fileName = file.name,
                                    filePath = file.absolutePath,
                                    sizeBytes = file.length(),
                                    type = UpdateFileType.UPDATE,
                                    isDownloaded = true
                                )
                            }
                    } else emptyList()

                    val localDlc = if (hasUpdateSupport && localPath != null) {
                        ZipExtractor.listAllDlcFiles(localPath, platformSlug)
                            .filter { file -> dbDlc.none { it.fileName == file.name } }
                            .map { file ->
                                UpdateFileUi(
                                    fileName = file.name,
                                    filePath = file.absolutePath,
                                    sizeBytes = file.length(),
                                    type = UpdateFileType.DLC,
                                    isDownloaded = true
                                )
                            }
                    } else emptyList()

                    _uiState.update { state ->
                        state.copy(
                            updateFiles = dbUpdates + localUpdates,
                            dlcFiles = dbDlc + localDlc
                        )
                    }
                }
            }

            gameEntityObserverJob?.cancel()
            gameEntityObserverJob = viewModelScope.launch {
                gameDao.observeById(gameId).collect { updatedGame ->
                    if (updatedGame == null) return@collect
                    _uiState.update { state ->
                        val currentGame = state.game ?: return@update state
                        val gameUpdated = currentGame.titleId != updatedGame.titleId
                        val oldTimestamp = state.saveStatusInfo?.activeSaveTimestamp
                        val newTimestamp = updatedGame.activeSaveTimestamp
                        val oldChannel = state.saveChannel.activeChannel
                        val newChannel = updatedGame.activeSaveChannel
                        val saveUpdated = oldTimestamp != newTimestamp || oldChannel != newChannel

                        android.util.Log.d("GameDetailVM", "[SaveTimestamp] Observer fired | gameId=$gameId | oldTs=$oldTimestamp, newTs=$newTimestamp | oldCh=$oldChannel, newCh=$newChannel | saveUpdated=$saveUpdated | saveStatusInfo=${state.saveStatusInfo != null}")

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
    }

    private fun refreshUserPropsInBackground(gameId: Long) {
        viewModelScope.launch {
            when (romMRepository.refreshUserProps(gameId)) {
                is RomMResult.Success -> {
                    val refreshedGame = gameDao.getById(gameId) ?: return@launch
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

    private suspend fun loadSaveStatusInfo(
        gameId: Long,
        emulatorId: String,
        activeChannel: String?,
        activeSaveTimestamp: Long?
    ): SaveStatusInfo? {
        val syncEntity = if (activeChannel != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, activeChannel)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }

        val cacheTimestamp = if (activeChannel != null) {
            saveCacheManager.getMostRecentInChannel(gameId, activeChannel)?.cachedAt
        } else {
            saveCacheManager.getMostRecentSave(gameId)?.cachedAt
        }

        val effectiveTimestamp = activeSaveTimestamp
            ?: cacheTimestamp?.toEpochMilli()

        if (activeSaveTimestamp == null && effectiveTimestamp != null) {
            gameDao.updateActiveSaveTimestamp(gameId, effectiveTimestamp)
        }

        val lastSyncTime = syncEntity?.lastSyncedAt
            ?: syncEntity?.localUpdatedAt
            ?: syncEntity?.serverUpdatedAt
            ?: cacheTimestamp

        return if (syncEntity != null) {
            SaveStatusInfo(
                status = when (syncEntity.syncStatus) {
                    SaveSyncEntity.STATUS_SYNCED -> SaveSyncStatus.SYNCED
                    SaveSyncEntity.STATUS_LOCAL_NEWER -> SaveSyncStatus.LOCAL_NEWER
                    SaveSyncEntity.STATUS_SERVER_NEWER -> SaveSyncStatus.LOCAL_NEWER
                    SaveSyncEntity.STATUS_PENDING_UPLOAD -> SaveSyncStatus.PENDING_UPLOAD
                    SaveSyncEntity.STATUS_CONFLICT -> SaveSyncStatus.LOCAL_NEWER
                    else -> SaveSyncStatus.NO_SAVE
                },
                channelName = activeChannel,
                activeSaveTimestamp = effectiveTimestamp,
                lastSyncTime = lastSyncTime
            )
        } else {
            SaveStatusInfo(
                status = SaveSyncStatus.NO_SAVE,
                channelName = activeChannel,
                activeSaveTimestamp = effectiveTimestamp,
                lastSyncTime = lastSyncTime
            )
        }
    }

    private fun handleSaveStatusChanged(event: SaveStatusEvent) {
        val status = if (event.isLocalOnly) {
            SaveSyncStatus.LOCAL_ONLY
        } else {
            _uiState.value.saveStatusInfo?.status?.takeIf {
                it != SaveSyncStatus.LOCAL_ONLY
            } ?: SaveSyncStatus.SYNCED
        }

        _uiState.update { state ->
            state.copy(
                saveChannel = state.saveChannel.copy(activeChannel = event.channelName),
                saveStatusInfo = SaveStatusInfo(
                    status = status,
                    channelName = event.channelName,
                    activeSaveTimestamp = event.timestamp,
                    lastSyncTime = if (event.isLocalOnly) null else state.saveStatusInfo?.lastSyncTime
                )
            )
        }
    }

    private suspend fun fetchAndCacheAchievements(rommId: Long, gameId: Long): List<AchievementUi> {
        // First, try to get RA ID from the game
        val game = gameDao.getById(gameId)
        val raId = game?.raId

        // If we have an RA ID and RA credentials, fetch directly from RA
        if (raId != null) {
            val raAchievements = fetchAchievementsFromRA(raId, gameId)
            if (raAchievements.isNotEmpty()) {
                return raAchievements
            }
        }

        // Fall back to RomM
        return fetchAchievementsFromRomM(rommId, gameId)
    }

    private suspend fun fetchAchievementsFromRA(raId: Long, gameId: Long): List<AchievementUi> {
        val raData = raRepository.getGameAchievementsWithProgress(raId) ?: return emptyList()

        val entities = raData.achievements.map { achievement ->
            val isUnlocked = achievement.id in raData.unlockedIds
            val badgeUrl = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" }
            val badgeUrlLock = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/${it}_lock.png" }

            com.nendo.argosy.data.local.entity.AchievementEntity(
                gameId = gameId,
                raId = achievement.id,
                title = achievement.title,
                description = achievement.description ?: "",
                points = achievement.points,
                type = achievement.type,
                badgeUrl = badgeUrl,
                badgeUrlLock = badgeUrlLock,
                unlockedAt = if (isUnlocked) System.currentTimeMillis() else null,
                unlockedHardcoreAt = null
            )
        }
        achievementDao.replaceForGame(gameId, entities)
        gameDao.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

        gameDao.updateAchievementCount(gameId, raData.totalCount, raData.earnedCount)
        achievementUpdateBus.emit(
            AchievementUpdateBus.AchievementUpdate(gameId, raData.totalCount, raData.earnedCount)
        )

        val savedAchievements = achievementDao.getByGameId(gameId)
        savedAchievements.forEach { achievement ->
            if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
            }
        }

        return raData.achievements.map { achievement ->
            val isUnlocked = achievement.id in raData.unlockedIds
            val badgeUrl = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" }
            val badgeUrlLock = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/${it}_lock.png" }

            AchievementUi(
                raId = achievement.id,
                title = achievement.title,
                description = achievement.description ?: "",
                points = achievement.points,
                type = achievement.type,
                badgeUrl = if (isUnlocked) badgeUrl else (badgeUrlLock ?: badgeUrl),
                isUnlocked = isUnlocked,
                isUnlockedHardcore = false
            )
        }
    }

    private suspend fun fetchAchievementsFromRomM(rommId: Long, gameId: Long): List<AchievementUi> {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements ?: emptyList()
                if (apiAchievements.isEmpty()) return emptyList()

                val earnedAchievements = getEarnedAchievements(rom.raId)
                val earnedByBadgeId = earnedAchievements.associateBy { it.id }

                val entities = apiAchievements.map { achievement ->
                    val earned = earnedByBadgeId[achievement.badgeId]
                    val unlockedAt = earned?.date?.let { parseTimestamp(it) }
                    val unlockedHardcoreAt = earned?.dateHardcore?.let { parseTimestamp(it) }

                    com.nendo.argosy.data.local.entity.AchievementEntity(
                        gameId = gameId,
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = achievement.badgeUrl,
                        badgeUrlLock = achievement.badgeUrlLock,
                        unlockedAt = unlockedAt,
                        unlockedHardcoreAt = unlockedHardcoreAt
                    )
                }
                achievementDao.replaceForGame(gameId, entities)
                gameDao.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())

                val earnedCount = entities.count { it.isUnlocked }
                gameDao.updateAchievementCount(gameId, entities.size, earnedCount)
                achievementUpdateBus.emit(
                    AchievementUpdateBus.AchievementUpdate(gameId, entities.size, earnedCount)
                )

                val savedAchievements = achievementDao.getByGameId(gameId)
                savedAchievements.forEach { achievement ->
                    if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                        imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
                    }
                }

                apiAchievements.map { achievement ->
                    val earned = earnedByBadgeId[achievement.badgeId]
                    val isUnlocked = earned != null
                    val isUnlockedHardcore = earned?.dateHardcore != null
                    AchievementUi(
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = if (isUnlocked) achievement.badgeUrl else (achievement.badgeUrlLock ?: achievement.badgeUrl),
                        isUnlocked = isUnlocked,
                        isUnlockedHardcore = isUnlockedHardcore
                    )
                }
            }
            is RomMResult.Error -> emptyList()
        }
    }

    private fun getEarnedAchievements(gameRaId: Long?): List<com.nendo.argosy.data.remote.romm.RomMEarnedAchievement> {
        if (gameRaId == null) return emptyList()
        return romMRepository.getEarnedAchievements(gameRaId)
    }

    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            java.time.ZonedDateTime.parse(timestamp, java.time.format.DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(timestamp).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun refreshAchievementsInBackground(rommId: Long, gameId: Long) {
        // Always fetch fresh data when viewing game details to get current unlock status
        // The API call is lightweight and ensures multi-device sync
        val fresh = fetchAndCacheAchievements(rommId, gameId)
        if (fresh.isNotEmpty()) {
            _uiState.update { state ->
                state.copy(game = state.game?.copy(achievements = fresh))
            }
        }
    }

    private suspend fun refreshDownloadSizeInBackground(rommId: Long, gameId: Long) {
        when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val mainFile = rom.files
                    ?.filter { it.category == null && !it.fileName.startsWith(".") }
                    ?.maxByOrNull { it.fileSizeBytes }
                val sizeBytes = mainFile?.fileSizeBytes ?: rom.fileSize
                if (sizeBytes > 0) {
                    gameDao.updateFileSize(gameId, sizeBytes)
                    _uiState.update { state ->
                        state.copy(downloadSizeBytes = sizeBytes)
                    }
                }
            }
            else -> { }
        }
    }

    private suspend fun loadUpdateAndDlcFiles(
        gameId: Long,
        platformSlug: String,
        localPath: String?
    ): Pair<List<UpdateFileUi>, List<UpdateFileUi>> {
        val hasUpdateSupport = ZipExtractor.hasUpdateSupport(platformSlug)
        val remoteFiles = gameFileDao.getFilesForGame(gameId)

        val localUpdateFileNames = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllUpdateFiles(localPath, platformSlug).map { it.name }.toSet()
        } else emptySet()

        val localDlcFileNames = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllDlcFiles(localPath, platformSlug).map { it.name }.toSet()
        } else emptySet()

        val dbUpdates = remoteFiles
            .filter { it.category == "update" }
            .map { file ->
                UpdateFileUi(
                    fileName = file.fileName,
                    filePath = file.filePath,
                    sizeBytes = file.fileSize,
                    type = UpdateFileType.UPDATE,
                    isDownloaded = file.fileName in localUpdateFileNames,
                    gameFileId = file.id,
                    rommFileId = file.rommFileId,
                    romId = file.romId
                )
            }

        val dbDlc = remoteFiles
            .filter { it.category == "dlc" }
            .map { file ->
                UpdateFileUi(
                    fileName = file.fileName,
                    filePath = file.filePath,
                    sizeBytes = file.fileSize,
                    type = UpdateFileType.DLC,
                    isDownloaded = file.fileName in localDlcFileNames,
                    gameFileId = file.id,
                    rommFileId = file.rommFileId,
                    romId = file.romId
                )
            }

        val localUpdates = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllUpdateFiles(localPath, platformSlug)
                .filter { file -> dbUpdates.none { it.fileName == file.name } }
                .map { file ->
                    UpdateFileUi(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        type = UpdateFileType.UPDATE,
                        isDownloaded = true
                    )
                }
        } else emptyList()

        val localDlc = if (hasUpdateSupport && localPath != null) {
            ZipExtractor.listAllDlcFiles(localPath, platformSlug)
                .filter { file -> dbDlc.none { it.fileName == file.name } }
                .map { file ->
                    UpdateFileUi(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        type = UpdateFileType.DLC,
                        isDownloaded = true
                    )
                }
        } else emptyList()

        return (dbUpdates + localUpdates) to (dbDlc + localDlc)
    }

    fun downloadUpdateFile(file: UpdateFileUi) {
        val game = _uiState.value.game ?: return
        val gameFileId = file.gameFileId ?: return
        val rommFileId = file.rommFileId ?: return

        viewModelScope.launch {
            downloadManager.enqueueGameFileDownload(
                gameId = currentGameId,
                gameFileId = gameFileId,
                rommFileId = rommFileId,
                fileName = file.fileName,
                category = file.type.name.lowercase(),
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                coverPath = game.coverPath,
                expectedSizeBytes = file.sizeBytes
            )
            notificationManager.showSuccess("Download queued: ${file.fileName}")
        }
    }

    fun downloadGame() {
        val now = System.currentTimeMillis()
        if (now - pageLoadTime < pageLoadDebounceMs) return
        viewModelScope.launch {
            when (val result = gameActions.queueDownload(currentGameId)) {
                is DownloadResult.Queued -> { }
                is DownloadResult.AlreadyDownloaded -> {
                    notificationManager.showSuccess("Game already downloaded")
                    refreshGameData()
                }
                is DownloadResult.MultiDiscQueued -> {
                    notificationManager.showSuccess("Downloading ${result.discCount} discs")
                }
                is DownloadResult.Error -> notificationManager.showError(result.message)
                is DownloadResult.ExtractionFailed -> {
                    _uiState.update {
                        it.copy(
                            showExtractionFailedPrompt = true,
                            extractionFailedInfo = ExtractionFailedInfo(
                                gameId = result.gameId,
                                fileName = result.fileName,
                                errorReason = result.errorReason
                            ),
                            extractionPromptFocusIndex = 0
                        )
                    }
                    soundManager.play(SoundType.OPEN_MODAL)
                }
            }
        }
    }

    fun dismissExtractionPrompt() {
        _uiState.update {
            it.copy(
                showExtractionFailedPrompt = false,
                extractionFailedInfo = null,
                extractionPromptFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveExtractionPromptFocus(delta: Int) {
        _uiState.update { state ->
            val newIndex = (state.extractionPromptFocusIndex + delta).coerceIn(0, 1)
            state.copy(extractionPromptFocusIndex = newIndex)
        }
    }

    fun confirmExtractionPromptSelection() {
        val info = _uiState.value.extractionFailedInfo ?: return
        val focusIndex = _uiState.value.extractionPromptFocusIndex

        dismissExtractionPrompt()

        viewModelScope.launch {
            when (focusIndex) {
                0 -> {
                    when (val result = gameActions.retryExtraction(info.gameId)) {
                        is DownloadResult.Queued -> notificationManager.showSuccess("Extraction succeeded")
                        is DownloadResult.Error -> notificationManager.showError(result.message)
                        else -> { }
                    }
                }
                1 -> {
                    when (val result = gameActions.redownload(info.gameId)) {
                        is DownloadResult.Queued -> notificationManager.showSuccess("Redownload started")
                        is DownloadResult.Error -> notificationManager.showError(result.message)
                        else -> { }
                    }
                }
            }
        }
    }

    fun onResume() {
        if (gameLaunchDelegate.isSyncing) return
        // Fallback session end handling in case Android killed Argosy while emulator was running
        // (normal flow goes through LaunchScreen, but if app was killed, user returns here directly)
        gameLaunchDelegate.handleSessionEnd(viewModelScope)
    }

    fun primaryAction() {
        val now = System.currentTimeMillis()
        if (now - lastActionTime < actionDebounceMs) return
        lastActionTime = now

        val state = _uiState.value
        when (state.downloadStatus) {
            GameDownloadStatus.DOWNLOADED -> playGame()
            GameDownloadStatus.NEEDS_INSTALL -> installApk()
            GameDownloadStatus.NOT_DOWNLOADED -> downloadGame()
            GameDownloadStatus.QUEUED,
            GameDownloadStatus.WAITING_FOR_STORAGE,
            GameDownloadStatus.DOWNLOADING,
            GameDownloadStatus.EXTRACTING,
            GameDownloadStatus.PAUSED -> {
                // Already in progress, extracting, or paused
            }
        }
    }

    private fun installApk() {
        viewModelScope.launch {
            val success = apkInstallManager.installApkForGame(currentGameId)
            if (!success) {
                notificationManager.showError("Could not install APK")
            }
        }
    }

    fun playGame(discId: Long? = null) {
        if (gameLaunchDelegate.isSyncing) return

        viewModelScope.launch {
            val currentGame = _uiState.value.game ?: return@launch

            // For fresh games with RA support, show mode selection
            if (currentGame.isBuiltInEmulator && currentGame.achievements.isNotEmpty()) {
                val hasSaves = saveCacheManager.getCachesForGameOnce(currentGameId).isNotEmpty()
                val isRALoggedIn = raRepository.isLoggedIn()

                if (!hasSaves && isRALoggedIn) {
                    val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
                    _uiState.update {
                        it.copy(
                            showPlayOptions = true,
                            playOptionsFocusIndex = 0,
                            hasCasualSaves = false,
                            hasHardcoreSave = false,
                            isRALoggedIn = true,
                            isOnline = isOnline
                        )
                    }
                    soundManager.play(SoundType.OPEN_MODAL)
                    return@launch
                }
            }

            // Check permissions before sync (GameDetail-specific modals)
            val permissionResult = checkSaveSyncPermissionUseCase()
            when (permissionResult) {
                is CheckSaveSyncPermissionUseCase.Result.MissingStoragePermission -> {
                    _uiState.update { it.copy(showPermissionModal = true, permissionModalType = PermissionModalType.STORAGE) }
                    return@launch
                }
                is CheckSaveSyncPermissionUseCase.Result.MissingSafGrant -> {
                    _uiState.update { it.copy(showPermissionModal = true, permissionModalType = PermissionModalType.SAF) }
                    return@launch
                }
                else -> { /* Permission granted, continue */ }
            }

            // Navigate to LaunchScreen which handles: resume check, sync, launch, session end
            _launchEvents.emit(LaunchEvent.NavigateToLaunch(currentGameId, discId = discId))
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

    fun showPlayOptions() {
        viewModelScope.launch {
            val hasCasualSaves = saveCacheManager.getCachesForGameOnce(currentGameId)
                .any { !it.isHardcore }
            val hasHardcoreSave = saveCacheManager.hasHardcoreSave(currentGameId)
            val hasRASupport = _uiState.value.game?.achievements?.isNotEmpty() == true
            val isRALoggedIn = raRepository.isLoggedIn()
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)

            _uiState.update {
                it.copy(
                    showPlayOptions = true,
                    playOptionsFocusIndex = 0,
                    hasCasualSaves = hasCasualSaves,
                    hasHardcoreSave = hasHardcoreSave,
                    hasRASupport = hasRASupport,
                    isRALoggedIn = isRALoggedIn,
                    isOnline = isOnline
                )
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    fun dismissPlayOptions() {
        _uiState.update {
            it.copy(showPlayOptions = false)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun movePlayOptionsFocus(delta: Int) {
        _uiState.update {
            val state = it
            var optionCount = 1  // New Game (Casual) - always present

            if (state.hasCasualSaves) optionCount++  // Resume
            if (state.hasHardcoreSave) optionCount++  // Resume Hardcore
            if (state.hasRASupport && state.isRALoggedIn) optionCount++  // New Game (Hardcore)

            val maxIndex = (optionCount - 1).coerceAtLeast(0)
            val newIndex = (it.playOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(playOptionsFocusIndex = newIndex)
        }
    }

    private fun confirmPlayOptionSelection() {
        val state = _uiState.value
        val focusIndex = state.playOptionsFocusIndex

        var currentIdx = 0
        val resumeIdx = if (state.hasCasualSaves) currentIdx++ else -1
        val resumeHardcoreIdx = if (state.hasHardcoreSave) currentIdx++ else -1
        val newCasualIdx = currentIdx++
        val newHardcoreIdx = if (state.hasRASupport && state.isRALoggedIn) currentIdx else -1

        val action = when (focusIndex) {
            resumeIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.Resume
            resumeHardcoreIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.ResumeHardcore
            newCasualIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewCasual
            newHardcoreIdx -> {
                if (!state.isOnline) return
                com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewHardcore
            }
            else -> return
        }
        handlePlayOption(action)
    }

    fun handlePlayOption(action: com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction) {
        dismissPlayOptions()
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
        // Clear active save selection for NEW games so saves go to timeline, not old channel
        if (launchMode == com.nendo.argosy.libretro.LaunchMode.NEW_CASUAL ||
            launchMode == com.nendo.argosy.libretro.LaunchMode.NEW_HARDCORE) {
            gameDao.updateActiveSaveChannel(currentGameId, null)
            gameDao.updateActiveSaveTimestamp(currentGameId, null)
            _uiState.update { state ->
                state.copy(
                    saveChannel = state.saveChannel.copy(activeChannel = null),
                    saveStatusInfo = state.saveStatusInfo?.copy(
                        channelName = null,
                        activeSaveTimestamp = null
                    )
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
                    forEmulator = true,
                    rolesSwapped = sessionStateStore.isRolesSwapped()
                )
                _launchEvents.emit(LaunchEvent.LaunchIntent(intentWithMode, options))
            }
            is LaunchResult.SelectDisc -> {
                pickerModalDelegate.showDiscPicker(result.discs)
            }
            is LaunchResult.NoEmulator -> {
                showEmulatorPicker()
            }
            is LaunchResult.NoCore -> {
                showCorePicker()
            }
            is LaunchResult.MissingDiscs -> {
                _uiState.update {
                    it.copy(
                        showMissingDiscPrompt = true,
                        missingDiscNumbers = result.missingDiscNumbers
                    )
                }
            }
            is LaunchResult.NoRomFile,
            is LaunchResult.NoSteamLauncher,
            is LaunchResult.NoAndroidApp,
            is LaunchResult.NoScummVMGameId,
            is LaunchResult.Error -> {
                // Handle error silently for now
            }
        }
    }

    fun moveOptionsFocus(delta: Int) {
        _uiState.update {
            val isDownloaded = it.downloadStatus == GameDownloadStatus.DOWNLOADED
            val isRommGame = it.game?.isRommGame == true
            val isAndroidApp = it.game?.isAndroidApp == true
            val canTrackProgress = isRommGame || isAndroidApp
            val canManageSaves = it.game?.canManageSaves == true
            val isRetroArch = it.game?.isRetroArchEmulator == true
            val isMultiDisc = it.game?.isMultiDisc == true
            val isSteamGame = it.game?.isSteamGame == true
            val isEmulatedGame = !isSteamGame && !isAndroidApp
            val hasUpdates = it.updateFiles.isNotEmpty() || it.dlcFiles.isNotEmpty()

            var optionCount = 1  // Base: Hide (always present)
            if (canManageSaves) optionCount++  // Manage Cached Saves
            if (canTrackProgress) optionCount += 2  // Ratings & Status + Refresh
            if (isSteamGame || isEmulatedGame) optionCount++  // Emulator/Launcher (not for Android)
            if (isRetroArch && isEmulatedGame) optionCount++  // Change Core (emulated only)
            if (isMultiDisc) optionCount++  // Select Disc
            if (hasUpdates) optionCount++  // Updates/DLC
            optionCount++  // Add to Collection (always present)
            if (isDownloaded || isAndroidApp) optionCount++  // Delete/Uninstall

            val maxIndex = optionCount - 1
            val newIndex = (it.moreOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(moreOptionsFocusIndex = newIndex)
        }
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
                if (isAndroidApp) uninstallAndroidApp() else deleteLocalFile()
            }
            MoreOptionAction.ToggleHide -> { toggleHideGame(); onBack() }
        }
    }

    fun confirmOptionSelection(onBack: () -> Unit) {
        val state = _uiState.value
        val isDownloaded = state.downloadStatus == GameDownloadStatus.DOWNLOADED
        val isRommGame = state.game?.isRommGame == true
        val canManageSaves = state.game?.canManageSaves == true
        val isRetroArch = state.game?.isRetroArchEmulator == true
        val isMultiDisc = state.game?.isMultiDisc == true
        val isSteamGame = state.game?.isSteamGame == true
        val isAndroidApp = state.game?.isAndroidApp == true
        val canTrackProgress = isRommGame || isAndroidApp
        val isEmulatedGame = !isSteamGame && !isAndroidApp
        val hasUpdates = state.updateFiles.isNotEmpty() || state.dlcFiles.isNotEmpty()
        val platformSlug = state.game?.platformSlug
        val usesTitleId = platformSlug in setOf("switch", "wiiu", "3ds", "vita", "psvita", "psp", "wii")
        val index = state.moreOptionsFocusIndex

        var currentIdx = 0
        val saveCacheIdx = if (canManageSaves) currentIdx++ else -1
        val ratingsStatusIdx = if (canTrackProgress) currentIdx++ else -1
        val emulatorOrLauncherIdx = if (isSteamGame || isEmulatedGame) currentIdx++ else -1
        val coreIdx = if (isRetroArch && isEmulatedGame) currentIdx++ else -1
        val titleIdIdx = if (usesTitleId && isEmulatedGame) currentIdx++ else -1
        val discIdx = if (isMultiDisc) currentIdx++ else -1
        val updatesIdx = if (hasUpdates) currentIdx++ else -1
        val refreshIdx = if (canTrackProgress) currentIdx++ else -1
        val addToCollectionIdx = currentIdx++
        val deleteIdx = if (isDownloaded || isAndroidApp) currentIdx++ else -1
        val hideIdx = currentIdx

        val action = when (index) {
            saveCacheIdx -> MoreOptionAction.ManageSaves
            ratingsStatusIdx -> MoreOptionAction.RatingsStatus
            emulatorOrLauncherIdx -> if (isSteamGame) MoreOptionAction.ChangeSteamLauncher else MoreOptionAction.ChangeEmulator
            coreIdx -> MoreOptionAction.ChangeCore
            titleIdIdx -> MoreOptionAction.RefreshTitleId
            discIdx -> MoreOptionAction.SelectDisc
            updatesIdx -> MoreOptionAction.UpdatesDlc
            refreshIdx -> MoreOptionAction.RefreshData
            addToCollectionIdx -> MoreOptionAction.AddToCollection
            deleteIdx -> MoreOptionAction.Delete
            hideIdx -> MoreOptionAction.ToggleHide
            else -> {
                toggleMoreOptions()
                return
            }
        }
        handleMoreOptionAction(action, onBack)
    }

    fun showEmulatorPicker() {
        val game = _uiState.value.game ?: return
        _uiState.update { it.copy(showMoreOptions = false) }
        viewModelScope.launch {
            pickerModalDelegate.showEmulatorPicker(game.platformSlug)
        }
    }

    fun dismissEmulatorPicker() = pickerModalDelegate.dismissEmulatorPicker()

    fun moveEmulatorPickerFocus(delta: Int) = pickerModalDelegate.moveEmulatorPickerFocus(delta)

    fun confirmEmulatorSelection() = pickerModalDelegate.confirmEmulatorSelection()

    fun showDiscPicker() {
        toggleMoreOptions()
        playGame()
    }

    fun dismissDiscPicker() = pickerModalDelegate.dismissDiscPicker()

    fun navigateDiscPicker(direction: Int) = pickerModalDelegate.moveDiscPickerFocus(direction)

    fun selectFocusedDisc() = pickerModalDelegate.confirmDiscSelection()

    fun showSteamLauncherPicker() {
        val game = _uiState.value.game ?: return
        if (!game.isSteamGame) return
        _uiState.update { it.copy(showMoreOptions = false) }
        pickerModalDelegate.showSteamLauncherPicker()
    }

    fun dismissSteamLauncherPicker() = pickerModalDelegate.dismissSteamLauncherPicker()

    fun moveSteamLauncherPickerFocus(delta: Int) = pickerModalDelegate.moveSteamLauncherPickerFocus(delta)

    fun confirmSteamLauncherSelection() = pickerModalDelegate.confirmSteamLauncherSelection()

    fun showCorePicker() {
        val game = _uiState.value.game ?: return
        if (!game.isRetroArchEmulator) return
        _uiState.update { it.copy(showMoreOptions = false) }
        pickerModalDelegate.showCorePicker(game.platformSlug, _uiState.value.selectedCoreId)
    }

    fun dismissCorePicker() = pickerModalDelegate.dismissCorePicker()

    fun moveCorePickerFocus(delta: Int) = pickerModalDelegate.moveCorePickerFocus(delta)

    fun confirmCoreSelection() = pickerModalDelegate.confirmCoreSelection()

    fun showUpdatesPicker() {
        val state = _uiState.value
        if (state.updateFiles.isEmpty() && state.dlcFiles.isEmpty()) return
        _uiState.update { it.copy(showMoreOptions = false) }
        pickerModalDelegate.showUpdatesPicker()
    }

    fun dismissUpdatesPicker() = pickerModalDelegate.dismissUpdatesPicker()

    fun moveUpdatesPickerFocus(delta: Int) {
        val state = _uiState.value
        pickerModalDelegate.moveUpdatesPickerFocus(delta, state.updateFiles, state.dlcFiles)
    }

    private fun confirmUpdatesSelection() {
        val state = _uiState.value
        pickerModalDelegate.confirmUpdatesSelection(state.updateFiles, state.dlcFiles)
    }

    fun installAllUpdatesAndDlc() {
        pickerModalDelegate.dismissUpdatesPicker()
        playGame()
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
                is DownloadResult.AlreadyDownloaded -> { }
                is DownloadResult.Error -> notificationManager.showError(result.message)
                is DownloadResult.ExtractionFailed -> { }
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
                showRatingsStatusMenu = false,
                showRatingPicker = true,
                ratingPickerType = type,
                ratingPickerValue = currentValue
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingPicker() {
        _uiState.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun showRatingsStatusMenu() {
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showRatingsStatusMenu = true,
                ratingsStatusFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRatingsStatusMenu() {
        _uiState.update {
            it.copy(
                showRatingsStatusMenu = false,
                showMoreOptions = true
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeRatingsStatusFocus(delta: Int) {
        _uiState.update { state ->
            val newIndex = (state.ratingsStatusFocusIndex + delta).coerceIn(0, 2)
            state.copy(ratingsStatusFocusIndex = newIndex)
        }
    }

    fun confirmRatingsStatusSelection() {
        when (_uiState.value.ratingsStatusFocusIndex) {
            0 -> showRatingPicker(RatingType.OPINION)
            1 -> showRatingPicker(RatingType.DIFFICULTY)
            2 -> showStatusPicker()
        }
    }

    fun changeRatingValue(delta: Int) {
        _uiState.update { state ->
            val newValue = (state.ratingPickerValue + delta).coerceIn(0, 10)
            state.copy(ratingPickerValue = newValue)
        }
    }

    fun setRatingValue(value: Int) {
        _uiState.update { it.copy(ratingPickerValue = value.coerceIn(0, 10)) }
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
                    val label = if (type == RatingType.OPINION) "Rating" else "Difficulty"
                    notificationManager.showSuccess("$label saved")
                    loadGame(currentGameId)
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _uiState.update { it.copy(showRatingPicker = false, showRatingsStatusMenu = true) }
        }
    }

    fun showStatusPicker() {
        val game = _uiState.value.game ?: return
        _uiState.update {
            it.copy(
                showRatingsStatusMenu = false,
                showStatusPicker = true,
                statusPickerValue = game.status
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissStatusPicker() {
        _uiState.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun changeStatusValue(delta: Int) {
        _uiState.update { state ->
            val newValue = if (delta > 0) {
                com.nendo.argosy.domain.model.CompletionStatus.cycleNext(state.statusPickerValue)
            } else {
                com.nendo.argosy.domain.model.CompletionStatus.cyclePrev(state.statusPickerValue)
            }
            state.copy(statusPickerValue = newValue)
        }
    }

    fun selectStatus(value: String) {
        _uiState.update { it.copy(statusPickerValue = value) }
        confirmStatus()
    }

    fun confirmStatus() {
        val state = _uiState.value
        val value = state.statusPickerValue

        viewModelScope.launch {
            val result = romMRepository.updateUserStatus(currentGameId, value)

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    notificationManager.showSuccess("Status saved")
                    loadGame(currentGameId)
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _uiState.update { it.copy(showStatusPicker = false, showRatingsStatusMenu = true) }
        }
    }

    fun toggleHideGame() {
        viewModelScope.launch {
            val isHidden = _uiState.value.game?.isHidden ?: false
            if (isHidden) {
                gameActions.unhideGame(currentGameId)
            } else {
                gameActions.hideGame(currentGameId)
            }
        }
    }

    fun showSaveCacheDialog() {
        _uiState.update { it.copy(showMoreOptions = false) }
        viewModelScope.launch {
            val game = gameDao.getById(currentGameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, game.platformId, game.platformSlug)
            if (emulatorId == null) {
                notificationManager.showError("Cannot determine emulator for saves")
                return@launch
            }
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(currentGameId, game.platformId, game.platformSlug)
            val savePath = computeEffectiveSavePath(emulatorId, game.platformSlug)
            saveChannelDelegate.show(
                scope = viewModelScope,
                gameId = currentGameId,
                activeChannel = _uiState.value.saveChannel.activeChannel,
                savePath = savePath,
                emulatorId = emulatorId,
                emulatorPackage = emulatorPackage
            )
        }
    }

    private suspend fun computeEffectiveSavePath(emulatorId: String, platformSlug: String): String? {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            return userConfig.savePathPattern
        }
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null
        val paths = SavePathRegistry.resolvePath(config, platformSlug)
        return paths.firstOrNull()
    }

    fun dismissSaveCacheDialog() {
        saveChannelDelegate.dismiss()
    }

    fun moveSaveCacheFocus(delta: Int) {
        saveChannelDelegate.moveFocus(delta)
    }

    fun setSaveCacheFocusIndex(index: Int) {
        saveChannelDelegate.setFocusIndex(index)
    }

    fun handleSaveCacheLongPress(index: Int) {
        saveChannelDelegate.handleLongPress(index)
    }

    fun switchSaveTab(tab: com.nendo.argosy.ui.common.savechannel.SaveTab) {
        saveChannelDelegate.switchTab(tab)
    }

    fun confirmSaveCacheSelection() {
        val game = _uiState.value.game ?: return
        viewModelScope.launch {
            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, game.platformId, game.platformSlug)
            if (emulatorId == null) {
                notificationManager.showError("Cannot determine emulator")
                return@launch
            }
            saveChannelDelegate.confirmSelection(
                scope = viewModelScope,
                emulatorId = emulatorId,
                onSaveStatusChanged = ::handleSaveStatusChanged,
                onRestored = { }
            )
        }
    }

    fun dismissRestoreConfirmation() {
        saveChannelDelegate.dismissRestoreConfirmation()
    }

    fun restoreSave(syncToServer: Boolean) {
        val game = _uiState.value.game ?: return

        viewModelScope.launch {
            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, game.platformId, game.platformSlug)

            if (emulatorId == null) {
                notificationManager.showError("Cannot determine emulator for save restore")
                return@launch
            }

            saveChannelDelegate.restoreSave(
                scope = viewModelScope,
                emulatorId = emulatorId,
                syncToServer = syncToServer,
                onSaveStatusChanged = ::handleSaveStatusChanged
            )
        }
    }

    fun dismissRenameDialog() {
        saveChannelDelegate.dismissRenameDialog()
    }

    fun updateRenameText(text: String) {
        saveChannelDelegate.updateRenameText(text)
    }

    fun confirmRename() {
        val state = _uiState.value.saveChannel
        if (state.selectedTab == com.nendo.argosy.ui.common.savechannel.SaveTab.TIMELINE) {
            saveChannelDelegate.confirmCreateChannel(viewModelScope)
        } else {
            saveChannelDelegate.confirmRenameChannel(viewModelScope)
        }
    }

    fun saveChannelSecondaryAction() {
        saveChannelDelegate.secondaryAction(viewModelScope, ::handleSaveStatusChanged)
    }

    fun saveChannelTertiaryAction() {
        saveChannelDelegate.tertiaryAction()
    }

    fun dismissDeleteConfirmation() {
        saveChannelDelegate.dismissDeleteConfirmation()
    }

    fun confirmDeleteChannel() {
        saveChannelDelegate.confirmDeleteChannel(viewModelScope, ::handleSaveStatusChanged)
    }

    fun refreshGameData() {
        if (_uiState.value.isRefreshingGameData) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingGameData = true) }
            when (val result = gameActions.refreshGameData(currentGameId)) {
                is RomMResult.Success -> {
                    notificationManager.showSuccess("Game data refreshed")
                    loadGame(currentGameId)
                }
                is RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _uiState.update { it.copy(isRefreshingGameData = false, showMoreOptions = false) }
        }
    }

    private fun refreshAndroidOrRommData() {
        val game = _uiState.value.game ?: return
        if (game.isAndroidApp) {
            refreshAndroidAppData()
        } else {
            refreshGameData()
        }
    }

    private fun refreshTitleId() {
        val gameId = _uiState.value.game?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showMoreOptions = false) }
            titleIdDownloadObserver.extractTitleIdForGame(gameId)
        }
    }

    private fun refreshAndroidAppData() {
        val packageName = _uiState.value.game?.packageName ?: return
        if (_uiState.value.isRefreshingGameData) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingGameData = true, showMoreOptions = false) }
            try {
                val details = playStoreService.getAppDetails(packageName).getOrNull()
                if (details != null) {
                    val game = gameDao.getById(currentGameId)
                    if (game != null) {
                        val updated = game.copy(
                            description = details.description ?: game.description,
                            developer = details.developer ?: game.developer,
                            genre = details.genre ?: game.genre,
                            rating = details.ratingPercent ?: game.rating,
                            screenshotPaths = details.screenshotUrls.takeIf { it.isNotEmpty() }?.joinToString(",") ?: game.screenshotPaths,
                            backgroundPath = details.screenshotUrls.firstOrNull() ?: game.backgroundPath
                        )
                        gameDao.update(updated)

                        details.coverUrl?.let { url ->
                            imageCacheManager.queueCoverCacheByGameId(url, currentGameId)
                        }
                        if (details.screenshotUrls.isNotEmpty()) {
                            imageCacheManager.queueScreenshotCacheByGameId(currentGameId, details.screenshotUrls)
                        }

                        notificationManager.showSuccess("Game data refreshed")
                        loadGame(currentGameId)
                    }
                } else {
                    notificationManager.showError("Could not fetch app data")
                }
            } catch (e: Exception) {
                notificationManager.showError("Failed to refresh: ${e.message}")
            }
            _uiState.update { it.copy(isRefreshingGameData = false) }
        }
    }

    private fun deleteLocalFile() {
        val isSteamGame = _uiState.value.game?.isSteamGame == true
        viewModelScope.launch {
            gameActions.deleteLocalFile(currentGameId)
            if (isSteamGame) {
                notificationManager.showSuccess("Game removed")
                _launchEvents.emit(LaunchEvent.NavigateBack)
            } else {
                notificationManager.showSuccess("Download deleted")
                loadGame(currentGameId)
            }
        }
    }

    private fun uninstallAndroidApp() {
        val packageName = _uiState.value.game?.packageName ?: return
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        viewModelScope.launch {
            _launchEvents.emit(LaunchEvent.LaunchIntent(intent))
        }
    }


    fun showLaunchError(message: String) {
        notificationManager.showError(message)
    }

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

    fun setFocusedScreenshotIndex(index: Int) {
        _uiState.update { it.copy(focusedScreenshotIndex = index) }
    }

    fun moveScreenshotFocus(delta: Int) {
        val screenshots = _uiState.value.game?.screenshots ?: return
        if (screenshots.isEmpty()) return
        _uiState.update { state ->
            val newIndex = (state.focusedScreenshotIndex + delta).mod(screenshots.size)
            state.copy(focusedScreenshotIndex = newIndex)
        }
    }

    fun openScreenshotViewer(index: Int? = null) {
        val screenshots = _uiState.value.game?.screenshots ?: return
        if (screenshots.isEmpty()) return
        val viewerIndex = index ?: _uiState.value.focusedScreenshotIndex
        _uiState.update {
            it.copy(
                showScreenshotViewer = true,
                viewerScreenshotIndex = viewerIndex.coerceIn(0, screenshots.size - 1)
            )
        }
    }

    fun closeScreenshotViewer() {
        _uiState.update { state ->
            state.copy(
                showScreenshotViewer = false,
                focusedScreenshotIndex = state.viewerScreenshotIndex
            )
        }
    }

    fun moveViewerIndex(delta: Int) {
        val screenshots = _uiState.value.game?.screenshots ?: return
        if (screenshots.isEmpty()) return
        _uiState.update { state ->
            val newIndex = (state.viewerScreenshotIndex + delta).mod(screenshots.size)
            state.copy(viewerScreenshotIndex = newIndex)
        }
    }

    fun getMenuItemCount(): Int {
        val game = _uiState.value.game ?: return 4
        return buildList {
            add("PLAY")
            add("FAVORITE")
            add("OPTIONS")
            add("DETAILS")
            if (!game.description.isNullOrBlank()) add("DESCRIPTION")
            if (game.screenshots.isNotEmpty()) add("SCREENSHOTS")
            if (game.achievements.isNotEmpty()) add("ACHIEVEMENTS")
        }.size
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

    fun getFocusedMenuItemIndex(): Int {
        return _uiState.value.menuFocusIndex
    }

    enum class MenuAction {
        PLAY, FAVORITE, OPTIONS, DETAILS, DESCRIPTION, SCREENSHOTS, ACHIEVEMENTS
    }

    fun getMenuItems(): List<MenuAction> {
        val game = _uiState.value.game ?: return listOf(MenuAction.PLAY, MenuAction.FAVORITE, MenuAction.OPTIONS, MenuAction.DETAILS)
        return buildList {
            add(MenuAction.PLAY)
            add(MenuAction.FAVORITE)
            add(MenuAction.OPTIONS)
            add(MenuAction.DETAILS)
            if (!game.description.isNullOrBlank()) add(MenuAction.DESCRIPTION)
            if (game.screenshots.isNotEmpty()) add(MenuAction.SCREENSHOTS)
            if (game.achievements.isNotEmpty()) add(MenuAction.ACHIEVEMENTS)
        }
    }

    fun getFocusedMenuAction(): MenuAction? {
        val menuItems = getMenuItems()
        val focusIndex = _uiState.value.menuFocusIndex
        return menuItems.getOrNull(focusIndex)
    }

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

    fun setCurrentScreenshotAsBackground() {
        val state = _uiState.value
        val screenshots = state.game?.screenshots ?: return
        if (screenshots.isEmpty()) return

        val screenshot = screenshots.getOrNull(state.viewerScreenshotIndex) ?: return
        val screenshotPath = screenshot.cachedPath ?: screenshot.remoteUrl

        viewModelScope.launch {
            val success = imageCacheManager.setScreenshotAsBackground(currentGameId, screenshotPath)
            if (success) {
                notificationManager.showSuccess("Background updated")
                loadGame(currentGameId)
            } else {
                notificationManager.showError("Failed to set background")
            }
        }
    }

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
                saveState.isVisible -> {
                    moveSaveCacheFocus(-1)
                    InputResult.HANDLED
                }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showPermissionModal -> InputResult.UNHANDLED
                state.showStatusPicker -> {
                    changeStatusValue(-1)
                    InputResult.HANDLED
                }
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                pickerState.showCorePicker -> {
                    moveCorePickerFocus(-1)
                    InputResult.HANDLED
                }
                pickerState.showDiscPicker -> {
                    navigateDiscPicker(-1)
                    InputResult.HANDLED
                }
                pickerState.showUpdatesPicker -> {
                    moveUpdatesPickerFocus(-1)
                    InputResult.HANDLED
                }
                pickerState.showEmulatorPicker -> {
                    moveEmulatorPickerFocus(-1)
                    InputResult.HANDLED
                }
                pickerState.showSteamLauncherPicker -> {
                    moveSteamLauncherPickerFocus(-1)
                    InputResult.HANDLED
                }
                state.showAddToCollectionModal -> {
                    moveCollectionFocusUp()
                    InputResult.HANDLED
                }
                state.showRatingsStatusMenu -> {
                    changeRatingsStatusFocus(-1)
                    InputResult.HANDLED
                }
                state.showPlayOptions -> {
                    movePlayOptionsFocus(-1)
                    InputResult.HANDLED
                }
                state.showMoreOptions -> {
                    moveOptionsFocus(-1)
                    InputResult.HANDLED
                }
                state.showAchievementList -> {
                    moveAchievementListFocus(-1)
                    InputResult.HANDLED
                }
                else -> {
                    if (onSnapUp()) InputResult.HANDLED else InputResult.UNHANDLED
                }
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
                saveState.isVisible -> {
                    moveSaveCacheFocus(1)
                    InputResult.HANDLED
                }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showPermissionModal -> InputResult.UNHANDLED
                state.showStatusPicker -> {
                    changeStatusValue(1)
                    InputResult.HANDLED
                }
                state.showMissingDiscPrompt -> InputResult.UNHANDLED
                pickerState.showCorePicker -> {
                    moveCorePickerFocus(1)
                    InputResult.HANDLED
                }
                pickerState.showDiscPicker -> {
                    navigateDiscPicker(1)
                    InputResult.HANDLED
                }
                pickerState.showUpdatesPicker -> {
                    moveUpdatesPickerFocus(1)
                    InputResult.HANDLED
                }
                pickerState.showEmulatorPicker -> {
                    moveEmulatorPickerFocus(1)
                    InputResult.HANDLED
                }
                pickerState.showSteamLauncherPicker -> {
                    moveSteamLauncherPickerFocus(1)
                    InputResult.HANDLED
                }
                state.showAddToCollectionModal -> {
                    moveCollectionFocusDown()
                    InputResult.HANDLED
                }
                state.showRatingsStatusMenu -> {
                    changeRatingsStatusFocus(1)
                    InputResult.HANDLED
                }
                state.showPlayOptions -> {
                    movePlayOptionsFocus(1)
                    InputResult.HANDLED
                }
                state.showMoreOptions -> {
                    moveOptionsFocus(1)
                    InputResult.HANDLED
                }
                state.showAchievementList -> {
                    moveAchievementListFocus(1)
                    InputResult.HANDLED
                }
                else -> {
                    if (onSnapDown()) InputResult.HANDLED else InputResult.UNHANDLED
                }
            }
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRestoreConfirmation || saveState.showDeleteConfirmation || saveState.showRenameDialog -> {
                    return InputResult.UNHANDLED
                }
                saveState.isVisible -> {
                    when (saveState.selectedTab) {
                        com.nendo.argosy.ui.common.savechannel.SaveTab.STATES -> {
                            switchSaveTab(com.nendo.argosy.ui.common.savechannel.SaveTab.TIMELINE)
                        }
                        com.nendo.argosy.ui.common.savechannel.SaveTab.TIMELINE -> {
                            if (saveState.hasSaveSlots) {
                                switchSaveTab(com.nendo.argosy.ui.common.savechannel.SaveTab.SLOTS)
                            }
                        }
                        com.nendo.argosy.ui.common.savechannel.SaveTab.SLOTS -> {}
                    }
                    return InputResult.HANDLED
                }
                state.showScreenshotViewer -> {
                    moveViewerIndex(-1)
                    return InputResult.HANDLED
                }
                state.showRatingPicker -> {
                    changeRatingValue(-1)
                    return InputResult.HANDLED
                }
                state.showExtractionFailedPrompt -> {
                    moveExtractionPromptFocus(-1)
                    return InputResult.HANDLED
                }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt -> {
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
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRestoreConfirmation || saveState.showDeleteConfirmation || saveState.showRenameDialog -> {
                    return InputResult.UNHANDLED
                }
                saveState.isVisible -> {
                    when (saveState.selectedTab) {
                        com.nendo.argosy.ui.common.savechannel.SaveTab.SLOTS -> {
                            switchSaveTab(com.nendo.argosy.ui.common.savechannel.SaveTab.TIMELINE)
                        }
                        com.nendo.argosy.ui.common.savechannel.SaveTab.TIMELINE -> {
                            if (saveState.hasStates) {
                                switchSaveTab(com.nendo.argosy.ui.common.savechannel.SaveTab.STATES)
                            }
                        }
                        com.nendo.argosy.ui.common.savechannel.SaveTab.STATES -> {}
                    }
                    return InputResult.HANDLED
                }
                state.showScreenshotViewer -> {
                    moveViewerIndex(1)
                    return InputResult.HANDLED
                }
                state.showRatingPicker -> {
                    changeRatingValue(1)
                    return InputResult.HANDLED
                }
                state.showExtractionFailedPrompt -> {
                    moveExtractionPromptFocus(1)
                    return InputResult.HANDLED
                }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt -> {
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
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            if (saveState.isVisible || saveState.showRestoreConfirmation ||
                state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker ||
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions ||
                state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt ||
                state.showExtractionFailedPrompt) {
                return InputResult.UNHANDLED
            }
            onPrevGame()
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            if (saveState.isVisible || saveState.showRestoreConfirmation ||
                state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker ||
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions ||
                state.showMoreOptions || pickerState.hasAnyPickerOpen || state.showMissingDiscPrompt ||
                state.showExtractionFailedPrompt) {
                return InputResult.UNHANDLED
            }
            onNextGame()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value
            when {
                saveState.showRenameDialog -> confirmRename()
                saveState.showDeleteConfirmation -> confirmDeleteChannel()
                saveState.showRestoreConfirmation -> restoreSave(syncToServer = false)
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
            if (saveState.showRenameDialog) {
                dismissRenameDialog()
                return InputResult.UNHANDLED
            }
            if (saveState.showDeleteConfirmation) {
                dismissDeleteConfirmation()
                return InputResult.UNHANDLED
            }
            if (saveState.showRestoreConfirmation) {
                dismissRestoreConfirmation()
                return InputResult.UNHANDLED
            }
            if (saveState.isVisible) {
                dismissSaveCacheDialog()
                return InputResult.UNHANDLED
            }
            if (state.showRatingPicker) {
                dismissRatingPicker()
                return InputResult.UNHANDLED
            }
            if (state.showStatusPicker) {
                dismissStatusPicker()
                return InputResult.UNHANDLED
            }
            if (state.showMissingDiscPrompt) {
                dismissMissingDiscPrompt()
                return InputResult.UNHANDLED
            }
            if (pickerState.showCorePicker) {
                dismissCorePicker()
                return InputResult.UNHANDLED
            }
            if (pickerState.showUpdatesPicker) {
                dismissUpdatesPicker()
                return InputResult.UNHANDLED
            }
            if (state.showPlayOptions) {
                dismissPlayOptions()
                return InputResult.UNHANDLED
            }
            if (state.showMoreOptions) {
                toggleMoreOptions()
                return InputResult.UNHANDLED
            }
            if (pickerState.showEmulatorPicker) {
                dismissEmulatorPicker()
                return InputResult.UNHANDLED
            }
            if (pickerState.showSteamLauncherPicker) {
                dismissSteamLauncherPicker()
                return InputResult.UNHANDLED
            }
            if (state.showAddToCollectionModal) {
                dismissAddToCollectionModal()
                return InputResult.UNHANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation) {
                saveChannelSecondaryAction()
                return InputResult.HANDLED
            }
            toggleFavorite()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation) {
                saveChannelTertiaryAction()
                return InputResult.HANDLED
            }
            if (state.showScreenshotViewer) {
                setCurrentScreenshotAsBackground()
                return InputResult.HANDLED
            }
            if (state.downloadStatus == GameDownloadStatus.DOWNLOADED &&
                state.game?.isBuiltInEmulator == true) {
                showPlayOptions()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSelect(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            val pickerState = pickerModalDelegate.state.value

            val anyModalOpen = state.showMoreOptions || state.showPlayOptions ||
                pickerState.hasAnyPickerOpen || state.showRatingPicker ||
                state.showStatusPicker || state.showMissingDiscPrompt ||
                state.showScreenshotViewer || saveState.isVisible

            if (anyModalOpen) {
                dismissAllModals()
                return InputResult.HANDLED
            }

            toggleMoreOptions()
            return InputResult.HANDLED
        }
    }

    private fun dismissAllModals() {
        resetAllModals()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun resetAllModals() {
        pickerModalDelegate.reset()
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showPlayOptions = false,
                showRatingPicker = false,
                showStatusPicker = false,
                showMissingDiscPrompt = false,
                showScreenshotViewer = false,
                showPermissionModal = false,
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false,
                saveChannel = it.saveChannel.copy(
                    isVisible = false,
                    showRestoreConfirmation = false,
                    showRenameDialog = false,
                    showDeleteConfirmation = false
                )
            )
        }
    }

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

    private val _requestSafGrant = kotlinx.coroutines.flow.MutableStateFlow(false)
    val requestSafGrant: kotlinx.coroutines.flow.StateFlow<Boolean> = _requestSafGrant.asStateFlow()

    fun disableSaveSync() {
        viewModelScope.launch {
            preferencesRepository.setSaveSyncEnabled(false)
            _uiState.update { it.copy(showPermissionModal = false) }
            playGame()
        }
    }

    fun showAddToCollectionModal() {
        val gameId = currentGameId
        if (gameId == 0L) return
        _uiState.update { it.copy(showMoreOptions = false) }
        collectionModalDelegate.show(viewModelScope, gameId)
    }

    fun dismissAddToCollectionModal() {
        collectionModalDelegate.dismiss()
    }

    fun moveCollectionFocusUp() {
        collectionModalDelegate.moveFocusUp()
    }

    fun moveCollectionFocusDown() {
        collectionModalDelegate.moveFocusDown()
    }

    fun confirmCollectionSelection() {
        collectionModalDelegate.confirmSelection(viewModelScope)
    }

    fun toggleGameInCollection(collectionId: Long) {
        collectionModalDelegate.toggleCollection(viewModelScope, collectionId)
    }

    fun showCreateCollectionFromModal() {
        collectionModalDelegate.showCreateDialog()
    }

    fun hideCreateCollectionDialog() {
        collectionModalDelegate.hideCreateDialog()
    }

    fun createCollectionFromModal(name: String) {
        collectionModalDelegate.createAndAdd(viewModelScope, name)
    }
}
