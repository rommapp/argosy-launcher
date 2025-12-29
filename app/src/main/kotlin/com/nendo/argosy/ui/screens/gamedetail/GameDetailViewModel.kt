package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.update.ApkInstallManager
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
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
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.download.DownloadResult
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.domain.usecase.save.CheckSaveSyncPermissionUseCase
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.domain.usecase.game.LaunchWithSyncUseCase
import com.nendo.argosy.ui.common.savechannel.SaveChannelDelegate
import kotlinx.coroutines.delay
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.GameNavigationContext
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameUpdateBus
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
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorResolver: EmulatorResolver,
    private val downloadManager: DownloadManager,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val gameNavigationContext: GameNavigationContext,
    private val launchGameUseCase: LaunchGameUseCase,
    private val launchWithSyncUseCase: LaunchWithSyncUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val romMRepository: RomMRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameActions: GameActionsDelegate,
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
    private val modalResetSignal: ModalResetSignal
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

    private var backgroundRepairPending = false

    override fun onCleared() {
        super.onCleared()
        imageCacheManager.resumeBackgroundCaching()
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

    fun loadGame(gameId: Long) {
        currentGameId = gameId
        pageLoadTime = System.currentTimeMillis()
        imageCacheManager.pauseBackgroundCaching()
        viewModelScope.launch {
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

            val selectedCoreId = gameSpecificConfig?.coreName
                ?: platformDefaultConfig?.coreName
                ?: EmulatorRegistry.getDefaultCore(game.platformSlug)?.id
            val selectedCoreName = if (isRetroArch) {
                EmulatorRegistry.getCoresForPlatform(game.platformSlug)
                    .find { it.id == selectedCoreId }?.displayName
            } else null

            val isSteamGame = game.source == GameSource.STEAM
            val isAndroidApp = game.source == GameSource.ANDROID_APP || game.platformSlug == "android"
            val steamLauncherName = if (isSteamGame) {
                game.steamLauncher?.let { SteamLaunchers.getByPackage(it)?.displayName } ?: "Auto"
            } else null
            val fileExists = gameRepository.checkGameFileExists(gameId)

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

            _uiState.update { state ->
                state.copy(
                    game = game.toGameDetailUi(
                        platformName = platform?.name ?: "Unknown",
                        emulatorName = emulatorName,
                        canPlay = canPlay,
                        isRetroArch = isRetroArch,
                        selectedCoreName = selectedCoreName,
                        achievements = cachedAchievements,
                        canManageSaves = canManageSaves,
                        steamLauncherName = steamLauncherName
                    ),
                    isLoading = false,
                    downloadStatus = downloadStatus,
                    downloadProgress = if (downloadStatus == GameDownloadStatus.DOWNLOADED) 1f else 0f,
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex,
                    discs = discsUi,
                    availableCores = if (isRetroArch) EmulatorRegistry.getCoresForPlatform(game.platformSlug) else emptyList(),
                    selectedCoreId = selectedCoreId,
                    saveChannel = state.saveChannel.copy(activeChannel = game.activeSaveChannel),
                    saveStatusInfo = saveStatusInfo
                )
            }

            if (game.rommId != null) {
                refreshUserPropsInBackground(gameId)
                refreshAchievementsInBackground(game.rommId, gameId)
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
                activeSaveTimestamp = activeSaveTimestamp,
                lastSyncTime = syncEntity.lastSyncedAt
            )
        } else {
            SaveStatusInfo(
                status = SaveSyncStatus.NO_SAVE,
                channelName = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp,
                lastSyncTime = null
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

    fun onResume() {
        val session = playSessionTracker.activeSession.value ?: return
        if (_uiState.value.isSyncing) return

        val emulatorId = emulatorResolver.resolveEmulatorId(session.emulatorPackage) ?: return

        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            if (!SavePathRegistry.canSyncWithSettings(
                    emulatorId,
                    prefs.saveSyncEnabled,
                    prefs.experimentalFolderSaveSync
                )
            ) {
                playSessionTracker.endSession()
                return@launch
            }

            val game = gameDao.getById(session.gameId)
            val channelName = game?.activeSaveChannel
            _uiState.update {
                it.copy(
                    isSyncing = true,
                    syncProgress = SyncProgress.PostSession.Uploading(channelName)
                )
            }

            val syncStartTime = System.currentTimeMillis()

            playSessionTracker.endSession()

            val elapsed = System.currentTimeMillis() - syncStartTime
            val minDisplayTime = 2000L
            if (elapsed < minDisplayTime) {
                delay(minDisplayTime - elapsed)
            }

            _uiState.update { it.copy(isSyncing = false, syncProgress = SyncProgress.Idle) }
        }
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
            GameDownloadStatus.PAUSED -> {
                // Already in progress or paused
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
        if (_uiState.value.isSyncing) return

        viewModelScope.launch {
            val canResume = playSessionTracker.canResumeSession(currentGameId)
            if (canResume) {
                val game = _uiState.value.game ?: return@launch
                when (val result = launchGameUseCase(currentGameId, discId, forResume = true)) {
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
                        notificationManager.showError("No compatible RetroArch core installed for ${result.platformSlug}")
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
                    is LaunchResult.NoAndroidApp -> {
                        notificationManager.showError("Android app not installed: ${result.packageName}")
                    }
                }
                return@launch
            }

            val permissionResult = checkSaveSyncPermissionUseCase()
            if (permissionResult is CheckSaveSyncPermissionUseCase.Result.MissingPermission) {
                _uiState.update { it.copy(showPermissionModal = true) }
                return@launch
            }

            val game = _uiState.value.game ?: return@launch

            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, game.platformId, game.platformSlug)
            val prefs = preferencesRepository.preferences.first()
            val canSync = emulatorId != null && SavePathRegistry.canSyncWithSettings(
                emulatorId,
                prefs.saveSyncEnabled,
                prefs.experimentalFolderSaveSync
            )

            val syncStartTime = if (canSync) {
                _uiState.update {
                    it.copy(
                        isSyncing = true,
                        syncProgress = SyncProgress.PreLaunch.CheckingSave(null)
                    )
                }
                System.currentTimeMillis()
            } else null

            launchWithSyncUseCase.invokeWithProgress(currentGameId).collect { progress ->
                if (canSync && progress != SyncProgress.Skipped && progress != SyncProgress.Idle) {
                    _uiState.update { it.copy(isSyncing = true, syncProgress = progress) }
                }
            }

            syncStartTime?.let { startTime ->
                val elapsed = System.currentTimeMillis() - startTime
                val minDisplayTime = 1500L
                if (elapsed < minDisplayTime) {
                    delay(minDisplayTime - elapsed)
                }
            }

            _uiState.update { it.copy(isSyncing = false, syncProgress = SyncProgress.Idle) }

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
                    notificationManager.showError("No compatible RetroArch core installed for ${result.platformSlug}")
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
                is LaunchResult.NoAndroidApp -> {
                    notificationManager.showError("Android app not installed: ${result.packageName}")
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
            val isAndroidApp = it.game?.isAndroidApp == true
            val canTrackProgress = isRommGame || isAndroidApp
            val canManageSaves = it.game?.canManageSaves == true
            val isRetroArch = it.game?.isRetroArchEmulator == true
            val isMultiDisc = it.game?.isMultiDisc == true
            val isSteamGame = it.game?.isSteamGame == true
            val isEmulatedGame = !isSteamGame && !isAndroidApp

            var optionCount = 1  // Base: Hide (always present)
            if (canManageSaves) optionCount++  // Manage Cached Saves
            if (canTrackProgress) optionCount += 4  // Rate + Difficulty + Completion + Refresh
            if (isSteamGame || isEmulatedGame) optionCount++  // Emulator/Launcher (not for Android)
            if (isRetroArch && isEmulatedGame) optionCount++  // Change Core (emulated only)
            if (isMultiDisc) optionCount++  // Select Disc
            if (isDownloaded || isAndroidApp) optionCount++  // Delete/Uninstall

            val maxIndex = optionCount - 1
            val newIndex = (it.moreOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun selectOptionAtIndex(index: Int, onBack: () -> Unit) {
        _uiState.update { it.copy(moreOptionsFocusIndex = index) }
        confirmOptionSelection(onBack)
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
        val index = state.moreOptionsFocusIndex

        var currentIdx = 0
        val saveCacheIdx = if (canManageSaves) currentIdx++ else -1
        val rateIdx = if (canTrackProgress) currentIdx++ else -1
        val difficultyIdx = if (canTrackProgress) currentIdx++ else -1
        val completionIdx = if (canTrackProgress) currentIdx++ else -1
        val emulatorOrLauncherIdx = if (isSteamGame || isEmulatedGame) currentIdx++ else -1
        val coreIdx = if (isRetroArch && isEmulatedGame) currentIdx++ else -1
        val discIdx = if (isMultiDisc) currentIdx++ else -1
        val refreshIdx = if (canTrackProgress) currentIdx++ else -1
        val deleteIdx = if (isDownloaded || isAndroidApp) currentIdx++ else -1
        val hideIdx = currentIdx

        when (index) {
            saveCacheIdx -> showSaveCacheDialog()
            rateIdx -> showRatingPicker(RatingType.OPINION)
            difficultyIdx -> showRatingPicker(RatingType.DIFFICULTY)
            completionIdx -> showStatusPicker()
            emulatorOrLauncherIdx -> if (isSteamGame) showSteamLauncherPicker() else showEmulatorPicker()
            coreIdx -> showCorePicker()
            discIdx -> showDiscPicker()
            refreshIdx -> refreshAndroidOrRommData()
            deleteIdx -> {
                toggleMoreOptions()
                if (isAndroidApp) uninstallAndroidApp() else deleteLocalFile()
            }
            hideIdx -> { hideGame(); onBack() }
            else -> toggleMoreOptions()
        }
    }

    fun showEmulatorPicker() {
        val game = _uiState.value.game ?: return
        val available = emulatorDetector.getInstalledForPlatform(game.platformSlug)
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

            configureEmulatorUseCase.setForGame(gameId, game.platformId, game.platformSlug, emulator)

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

    fun showSteamLauncherPicker() {
        val game = _uiState.value.game ?: return
        if (!game.isSteamGame) return

        val available = SteamLaunchers.getInstalled(context)
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showSteamLauncherPicker = true,
                availableSteamLaunchers = available,
                steamLauncherPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissSteamLauncherPicker() {
        _uiState.update { it.copy(showSteamLauncherPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveSteamLauncherPickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.availableSteamLaunchers.size
            val newIndex = (state.steamLauncherPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(steamLauncherPickerFocusIndex = newIndex)
        }
    }

    fun selectSteamLauncher(launcher: SteamLauncher?) {
        viewModelScope.launch {
            val gameId = currentGameId
            if (launcher == null) {
                gameDao.updateSteamLauncher(gameId, null, false)
            } else {
                gameDao.updateSteamLauncher(gameId, launcher.packageName, true)
            }
            _uiState.update { it.copy(showSteamLauncherPicker = false) }
            loadGame(gameId)
        }
    }

    fun confirmSteamLauncherSelection() {
        val state = _uiState.value
        val index = state.steamLauncherPickerFocusIndex
        if (index == 0) {
            selectSteamLauncher(null)
        } else {
            val launcher = state.availableSteamLaunchers.getOrNull(index - 1)
            selectSteamLauncher(launcher)
        }
    }

    fun showCorePicker() {
        val game = _uiState.value.game ?: return
        if (!game.isRetroArchEmulator) return
        val cores = EmulatorRegistry.getCoresForPlatform(game.platformSlug)
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

    fun showStatusPicker() {
        val game = _uiState.value.game ?: return
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showStatusPicker = true,
                statusPickerValue = game.status
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissStatusPicker() {
        _uiState.update { it.copy(showStatusPicker = false) }
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

    fun confirmStatus() {
        val state = _uiState.value
        val value = state.statusPickerValue

        viewModelScope.launch {
            val result = romMRepository.updateUserStatus(currentGameId, value)

            when (result) {
                is com.nendo.argosy.data.remote.romm.RomMResult.Success -> {
                    loadGame(currentGameId)
                }
                is com.nendo.argosy.data.remote.romm.RomMResult.Error -> {
                    notificationManager.showError(result.message)
                }
            }
            _uiState.update { it.copy(showStatusPicker = false) }
        }
    }

    fun hideGame() {
        viewModelScope.launch {
            gameActions.hideGame(currentGameId)
        }
    }

    fun showSaveCacheDialog() {
        _uiState.update { it.copy(showMoreOptions = false) }
        viewModelScope.launch {
            val game = gameDao.getById(currentGameId) ?: return@launch
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(currentGameId, game.platformId, game.platformSlug)
            val emulatorId = emulatorPackage?.let { emulatorResolver.resolveEmulatorId(it) }
            val savePath = emulatorId?.let { computeEffectiveSavePath(it, game.platformSlug) }
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

    fun showResetConfirmation() {
        saveChannelDelegate.showResetConfirmation()
    }

    fun dismissResetConfirmation() {
        saveChannelDelegate.dismissResetConfirmation()
    }

    fun confirmReset() {
        saveChannelDelegate.confirmReset(viewModelScope, ::handleSaveStatusChanged)
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
            _launchEvents.emit(LaunchEvent.Launch(intent))
        }
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
            return when {
                saveState.showRenameDialog -> InputResult.UNHANDLED
                saveState.showRestoreConfirmation -> InputResult.UNHANDLED
                saveState.showDeleteConfirmation -> InputResult.UNHANDLED
                saveState.showResetConfirmation -> InputResult.UNHANDLED
                saveState.isVisible -> {
                    moveSaveCacheFocus(-1)
                    InputResult.HANDLED
                }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showStatusPicker -> {
                    changeStatusValue(-1)
                    InputResult.HANDLED
                }
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
                state.showSteamLauncherPicker -> {
                    moveSteamLauncherPickerFocus(-1)
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
            val saveState = state.saveChannel
            return when {
                saveState.showRenameDialog -> InputResult.UNHANDLED
                saveState.showRestoreConfirmation -> InputResult.UNHANDLED
                saveState.showDeleteConfirmation -> InputResult.UNHANDLED
                saveState.showResetConfirmation -> InputResult.UNHANDLED
                saveState.isVisible -> {
                    moveSaveCacheFocus(1)
                    InputResult.HANDLED
                }
                state.showScreenshotViewer -> InputResult.UNHANDLED
                state.showRatingPicker -> InputResult.UNHANDLED
                state.showStatusPicker -> {
                    changeStatusValue(1)
                    InputResult.HANDLED
                }
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
                state.showSteamLauncherPicker -> {
                    moveSteamLauncherPickerFocus(1)
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
            val saveState = state.saveChannel
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
            val saveState = state.saveChannel
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
            val saveState = state.saveChannel
            if (saveState.isVisible || saveState.showRestoreConfirmation ||
                state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker ||
                state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt) {
                return InputResult.UNHANDLED
            }
            onPrevGame()
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible || saveState.showRestoreConfirmation ||
                state.showScreenshotViewer || state.showRatingPicker || state.showStatusPicker ||
                state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt) {
                return InputResult.UNHANDLED
            }
            onNextGame()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            when {
                saveState.showRenameDialog -> confirmRename()
                saveState.showDeleteConfirmation -> confirmDeleteChannel()
                saveState.showResetConfirmation -> confirmReset()
                saveState.showRestoreConfirmation -> restoreSave(syncToServer = false)
                saveState.isVisible -> confirmSaveCacheSelection()
                state.showScreenshotViewer -> closeScreenshotViewer()
                isInScreenshotsSection() && state.game?.screenshots?.isNotEmpty() == true -> openScreenshotViewer()
                state.showRatingPicker -> confirmRating()
                state.showStatusPicker -> confirmStatus()
                state.showMissingDiscPrompt -> repairAndPlay()
                state.showCorePicker -> confirmCoreSelection()
                state.showDiscPicker -> confirmDiscSelection()
                state.showEmulatorPicker -> confirmEmulatorSelection()
                state.showSteamLauncherPicker -> confirmSteamLauncherSelection()
                state.showMoreOptions -> confirmOptionSelection(onBack)
                else -> primaryAction()
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            when {
                saveState.showRenameDialog -> dismissRenameDialog()
                saveState.showDeleteConfirmation -> dismissDeleteConfirmation()
                saveState.showResetConfirmation -> dismissResetConfirmation()
                saveState.showRestoreConfirmation -> dismissRestoreConfirmation()
                saveState.isVisible -> dismissSaveCacheDialog()
                state.showScreenshotViewer -> closeScreenshotViewer()
                state.showRatingPicker -> dismissRatingPicker()
                state.showStatusPicker -> dismissStatusPicker()
                state.showMissingDiscPrompt -> dismissMissingDiscPrompt()
                state.showCorePicker -> dismissCorePicker()
                state.showDiscPicker -> dismissDiscPicker()
                state.showEmulatorPicker -> dismissEmulatorPicker()
                state.showSteamLauncherPicker -> dismissSteamLauncherPicker()
                state.showMoreOptions -> toggleMoreOptions()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.showRenameDialog) {
                dismissRenameDialog()
                return InputResult.UNHANDLED
            }
            if (saveState.showDeleteConfirmation) {
                dismissDeleteConfirmation()
                return InputResult.UNHANDLED
            }
            if (saveState.showResetConfirmation) {
                dismissResetConfirmation()
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
            if (state.showSteamLauncherPicker) {
                dismissSteamLauncherPicker()
                return InputResult.UNHANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation && !saveState.showResetConfirmation) {
                saveChannelSecondaryAction()
                return InputResult.HANDLED
            }
            toggleFavorite()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel
            if (saveState.isVisible && !saveState.showRestoreConfirmation && !saveState.showRenameDialog && !saveState.showDeleteConfirmation && !saveState.showResetConfirmation) {
                saveChannelTertiaryAction()
                return InputResult.HANDLED
            }
            if (state.showScreenshotViewer) {
                setCurrentScreenshotAsBackground()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSelect(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel

            val anyModalOpen = state.showMoreOptions || state.showEmulatorPicker ||
                state.showSteamLauncherPicker || state.showCorePicker || state.showRatingPicker ||
                state.showStatusPicker || state.showDiscPicker || state.showMissingDiscPrompt ||
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
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showEmulatorPicker = false,
                showSteamLauncherPicker = false,
                showCorePicker = false,
                showRatingPicker = false,
                showStatusPicker = false,
                showDiscPicker = false,
                showMissingDiscPrompt = false,
                showScreenshotViewer = false,
                showPermissionModal = false,
                saveChannel = it.saveChannel.copy(
                    isVisible = false,
                    showRestoreConfirmation = false,
                    showRenameDialog = false,
                    showDeleteConfirmation = false,
                    showResetConfirmation = false
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

    fun disableSaveSync() {
        viewModelScope.launch {
            preferencesRepository.setSaveSyncEnabled(false)
            _uiState.update { it.copy(showPermissionModal = false) }
            playGame()
        }
    }
}
