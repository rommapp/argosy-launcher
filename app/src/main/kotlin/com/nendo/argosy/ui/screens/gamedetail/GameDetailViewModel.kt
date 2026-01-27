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
    private val modalResetSignal: ModalResetSignal,
    private val gameLauncher: GameLauncher,
    private val collectionDao: com.nendo.argosy.data.local.dao.CollectionDao,
    private val addGameToCollectionUseCase: com.nendo.argosy.domain.usecase.collection.AddGameToCollectionUseCase,
    private val removeGameFromCollectionUseCase: com.nendo.argosy.domain.usecase.collection.RemoveGameFromCollectionUseCase,
    private val createCollectionUseCase: com.nendo.argosy.domain.usecase.collection.CreateCollectionUseCase,
    private val saveCacheManager: com.nendo.argosy.data.repository.SaveCacheManager,
    private val raRepository: com.nendo.argosy.data.repository.RetroAchievementsRepository
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
    private var gameFilesObserverJob: kotlinx.coroutines.Job? = null
    private var testAchievementJob: kotlinx.coroutines.Job? = null

    override fun onCleared() {
        super.onCleared()
        imageCacheManager.resumeBackgroundCaching()
        testAchievementJob?.cancel()
    }

    fun startTestAchievementMode() {
        testAchievementJob?.cancel()
        testAchievementJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                triggerTestAchievement()
            }
        }
    }

    fun stopTestAchievementMode() {
        testAchievementJob?.cancel()
        testAchievementJob = null
    }

    private fun triggerTestAchievement() {
        val achievements = _uiState.value.game?.achievements ?: return
        if (achievements.isEmpty()) return

        val randomAch = achievements.random()

        _uiState.update {
            it.copy(
                testAchievement = TestAchievementUi(
                    id = randomAch.raId,
                    title = randomAch.title,
                    description = randomAch.description,
                    points = randomAch.points,
                    badgeUrl = randomAch.badgeUrl,
                    isHardcore = listOf(true, false).random()
                )
            )
        }
    }

    fun dismissTestAchievement() {
        _uiState.update { it.copy(testAchievement = null) }
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
                    siblingGameIds = siblingIds,
                    currentGameIndex = currentIndex,
                    availableCores = if (isRetroArch) EmulatorRegistry.getCoresForPlatform(game.platformSlug) else emptyList(),
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
        if (_uiState.value.isSyncing) return

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

            val canResume = playSessionTracker.canResumeSession(currentGameId)
            if (canResume) {
                when (val result = launchGameUseCase(currentGameId, discId, forResume = true)) {
                    is LaunchResult.Success -> {
                        soundManager.play(SoundType.LAUNCH_GAME)
                        _launchEvents.emit(LaunchEvent.Launch(result.intent))
                    }
                    is LaunchResult.SelectDisc -> {
                        _uiState.update {
                            it.copy(
                                showDiscPicker = true,
                                discPickerOptions = result.discs
                            )
                        }
                    }
                    is LaunchResult.NoEmulator -> {
                        notificationManager.showError("No emulator installed for ${currentGame.platformName}")
                    }
                    is LaunchResult.NoRomFile -> {
                        notificationManager.showError("ROM file not found. Download required.")
                    }
                    is LaunchResult.NoSteamLauncher -> {
                        notificationManager.showError("Steam launcher not installed")
                    }
                    is LaunchResult.NoCore -> {
                        notificationManager.showError("No core available for ${result.platformSlug}")
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

            val emulatorId = emulatorResolver.getEmulatorIdForGame(currentGameId, currentGame.platformId, currentGame.platformSlug)
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
                is LaunchResult.SelectDisc -> {
                    _uiState.update {
                        it.copy(
                            showDiscPicker = true,
                            discPickerOptions = result.discs
                        )
                    }
                }
                is LaunchResult.NoEmulator -> {
                    notificationManager.showError("No emulator installed for ${currentGame.platformName}")
                }
                is LaunchResult.NoRomFile -> {
                    notificationManager.showError("ROM file not found. Download required.")
                }
                is LaunchResult.NoSteamLauncher -> {
                    notificationManager.showError("Steam launcher not installed")
                }
                is LaunchResult.NoCore -> {
                    notificationManager.showError("No core available for ${result.platformSlug}")
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

    fun showPlayOptions() {
        viewModelScope.launch {
            val hasCasualSaves = saveCacheManager.getCachesForGameOnce(currentGameId)
                .any { !it.isHardcore }
            val hasHardcoreSave = saveCacheManager.hasHardcoreSlot(currentGameId)
            val isRALoggedIn = raRepository.isLoggedIn()
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)

            _uiState.update {
                it.copy(
                    showPlayOptions = true,
                    playOptionsFocusIndex = 0,
                    hasCasualSaves = hasCasualSaves,
                    hasHardcoreSave = hasHardcoreSave,
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
            if (state.isRALoggedIn) optionCount++  // New Game (Hardcore)
            if (state.hasHardcoreSave && state.isRALoggedIn) optionCount++  // Resume Hardcore

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
        val newCasualIdx = currentIdx++
        val newHardcoreIdx = if (state.isRALoggedIn) currentIdx++ else -1
        val resumeHardcoreIdx = if (state.hasHardcoreSave && state.isRALoggedIn) currentIdx else -1

        val action = when (focusIndex) {
            resumeIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.Resume
            newCasualIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewCasual
            newHardcoreIdx -> {
                if (!state.isOnline) return
                com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.NewHardcore
            }
            resumeHardcoreIdx -> com.nendo.argosy.ui.screens.gamedetail.modals.PlayOptionAction.ResumeHardcore
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
        val result = launchGameUseCase(currentGameId)
        when (result) {
            is LaunchResult.Success -> {
                val intentWithMode = result.intent.apply {
                    putExtra(com.nendo.argosy.libretro.LaunchMode.EXTRA_LAUNCH_MODE, launchMode.name)
                }
                soundManager.play(SoundType.LAUNCH_GAME)
                _launchEvents.emit(LaunchEvent.Launch(intentWithMode))
            }
            is LaunchResult.SelectDisc -> {
                _uiState.update {
                    it.copy(
                        showDiscPicker = true,
                        discPickerOptions = result.discs,
                        discPickerFocusIndex = 0
                    )
                }
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
        val index = state.moreOptionsFocusIndex

        var currentIdx = 0
        val saveCacheIdx = if (canManageSaves) currentIdx++ else -1
        val ratingsStatusIdx = if (canTrackProgress) currentIdx++ else -1
        val emulatorOrLauncherIdx = if (isSteamGame || isEmulatedGame) currentIdx++ else -1
        val coreIdx = if (isRetroArch && isEmulatedGame) currentIdx++ else -1
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
        viewModelScope.launch {
            if (emulatorDetector.installedEmulators.value.isEmpty()) {
                emulatorDetector.detectEmulators()
            }
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

    fun showDiscPicker() {
        toggleMoreOptions()
        playGame()
    }

    fun dismissDiscPicker() {
        _uiState.update { it.copy(showDiscPicker = false, discPickerOptions = emptyList(), discPickerFocusIndex = 0) }
    }

    fun navigateDiscPicker(direction: Int) {
        val state = _uiState.value
        val maxIndex = state.discPickerOptions.size - 1
        val newIndex = (state.discPickerFocusIndex + direction).coerceIn(0, maxIndex)
        _uiState.update { it.copy(discPickerFocusIndex = newIndex) }
    }

    fun selectFocusedDisc() {
        val state = _uiState.value
        val disc = state.discPickerOptions.getOrNull(state.discPickerFocusIndex) ?: return
        selectDisc(disc.filePath)
    }

    fun selectDisc(discPath: String) {
        dismissDiscPicker()
        viewModelScope.launch {
            val result = launchGameUseCase(currentGameId, selectedDiscPath = discPath)
            when (result) {
                is LaunchResult.Success -> {
                    soundManager.play(SoundType.LAUNCH_GAME)
                    _launchEvents.emit(LaunchEvent.Launch(result.intent))
                }
                is LaunchResult.Error -> {
                    notificationManager.showError(result.message)
                }
                else -> {
                    notificationManager.showError("Failed to launch disc")
                }
            }
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

    fun showUpdatesPicker() {
        val state = _uiState.value
        if (state.updateFiles.isEmpty() && state.dlcFiles.isEmpty()) return
        _uiState.update {
            it.copy(
                showMoreOptions = false,
                showUpdatesPicker = true,
                updatesPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissUpdatesPicker() {
        _uiState.update { it.copy(showUpdatesPicker = false) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveUpdatesPickerFocus(delta: Int) {
        _uiState.update { state ->
            val allFiles = state.updateFiles + state.dlcFiles
            val maxIndex = (allFiles.size - 1).coerceAtLeast(0)
            val newIndex = (state.updatesPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(updatesPickerFocusIndex = newIndex)
        }
    }

    private fun confirmUpdatesSelection() {
        val state = _uiState.value
        val allFiles = state.updateFiles + state.dlcFiles
        val focusedFile = allFiles.getOrNull(state.updatesPickerFocusIndex)

        if (focusedFile != null && !focusedFile.isDownloaded && focusedFile.gameFileId != null) {
            downloadUpdateFile(focusedFile)
        }
    }

    fun installAllUpdatesAndDlc() {
        _uiState.update { it.copy(showUpdatesPicker = false) }
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
                state.showPermissionModal -> InputResult.UNHANDLED
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
                    navigateDiscPicker(-1)
                    InputResult.HANDLED
                }
                state.showUpdatesPicker -> {
                    moveUpdatesPickerFocus(-1)
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
                state.showPermissionModal -> InputResult.UNHANDLED
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
                    navigateDiscPicker(1)
                    InputResult.HANDLED
                }
                state.showUpdatesPicker -> {
                    moveUpdatesPickerFocus(1)
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
                state.showExtractionFailedPrompt -> {
                    moveExtractionPromptFocus(-1)
                    return InputResult.HANDLED
                }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || state.showEmulatorPicker || state.showCorePicker || state.showMissingDiscPrompt -> {
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
                state.showExtractionFailedPrompt -> {
                    moveExtractionPromptFocus(1)
                    return InputResult.HANDLED
                }
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions || state.showMoreOptions || state.showEmulatorPicker || state.showCorePicker || state.showMissingDiscPrompt -> {
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
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions ||
                state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt ||
                state.showExtractionFailedPrompt) {
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
                state.showAddToCollectionModal || state.showRatingsStatusMenu || state.showPlayOptions ||
                state.showMoreOptions || state.showEmulatorPicker ||
                state.showCorePicker || state.showDiscPicker || state.showMissingDiscPrompt ||
                state.showUpdatesPicker || state.showExtractionFailedPrompt) {
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
                state.showPermissionModal -> return InputResult.UNHANDLED
                state.showRatingPicker -> confirmRating()
                state.showStatusPicker -> confirmStatus()
                state.showMissingDiscPrompt -> repairAndPlay()
                state.showExtractionFailedPrompt -> confirmExtractionPromptSelection()
                state.showCorePicker -> confirmCoreSelection()
                state.showDiscPicker -> selectFocusedDisc()
                state.showUpdatesPicker -> confirmUpdatesSelection()
                state.showEmulatorPicker -> confirmEmulatorSelection()
                state.showSteamLauncherPicker -> confirmSteamLauncherSelection()
                state.showAddToCollectionModal -> confirmCollectionSelection()
                state.showRatingsStatusMenu -> confirmRatingsStatusSelection()
                state.showPlayOptions -> confirmPlayOptionSelection()
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
                state.showExtractionFailedPrompt -> dismissExtractionPrompt()
                state.showCorePicker -> dismissCorePicker()
                state.showDiscPicker -> dismissDiscPicker()
                state.showUpdatesPicker -> dismissUpdatesPicker()
                state.showEmulatorPicker -> dismissEmulatorPicker()
                state.showSteamLauncherPicker -> dismissSteamLauncherPicker()
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
            if (state.showUpdatesPicker) {
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
            if (state.showEmulatorPicker) {
                dismissEmulatorPicker()
                return InputResult.UNHANDLED
            }
            if (state.showSteamLauncherPicker) {
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
            if (state.downloadStatus == GameDownloadStatus.DOWNLOADED &&
                state.game?.isBuiltInEmulator == true &&
                state.game.achievements.isNotEmpty()) {
                showPlayOptions()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }

        override fun onSelect(): InputResult {
            val state = _uiState.value
            val saveState = state.saveChannel

            val anyModalOpen = state.showMoreOptions || state.showPlayOptions ||
                state.showEmulatorPicker || state.showSteamLauncherPicker ||
                state.showCorePicker || state.showRatingPicker ||
                state.showStatusPicker || state.showUpdatesPicker ||
                state.showMissingDiscPrompt || state.showScreenshotViewer || saveState.isVisible

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
                showPlayOptions = false,
                showEmulatorPicker = false,
                showSteamLauncherPicker = false,
                showCorePicker = false,
                showRatingPicker = false,
                showStatusPicker = false,
                showUpdatesPicker = false,
                showMissingDiscPrompt = false,
                showScreenshotViewer = false,
                showPermissionModal = false,
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false,
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

    fun showAddToCollectionModal() {
        viewModelScope.launch {
            val gameId = currentGameId
            if (gameId == 0L) return@launch

            val allCollections = collectionDao.getAllCollections()
            val gameCollectionIds = collectionDao.getCollectionIdsForGame(gameId)

            val collectionItems = allCollections.map { collection ->
                CollectionItemUi(
                    id = collection.id,
                    name = collection.name,
                    isInCollection = collection.id in gameCollectionIds
                )
            }

            _uiState.update {
                it.copy(
                    showMoreOptions = false,
                    showAddToCollectionModal = true,
                    collections = collectionItems,
                    collectionModalFocusIndex = 0
                )
            }
        }
    }

    fun dismissAddToCollectionModal() {
        _uiState.update {
            it.copy(
                showAddToCollectionModal = false,
                showCreateCollectionDialog = false
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveCollectionFocusUp() {
        _uiState.update {
            val minIndex = 0
            it.copy(collectionModalFocusIndex = (it.collectionModalFocusIndex - 1).coerceAtLeast(minIndex))
        }
    }

    fun moveCollectionFocusDown() {
        _uiState.update {
            val filtered = it.collections.filter { c -> c.name.isNotBlank() }
            val maxIndex = filtered.size
            it.copy(collectionModalFocusIndex = (it.collectionModalFocusIndex + 1).coerceAtMost(maxIndex))
        }
    }

    fun confirmCollectionSelection() {
        val state = _uiState.value
        val index = state.collectionModalFocusIndex
        val filtered = state.collections.filter { it.name.isNotBlank() }

        if (index == filtered.size) {
            _uiState.update { it.copy(showCreateCollectionDialog = true) }
            return
        }

        val collection = filtered.getOrNull(index) ?: return
        toggleGameInCollection(collection.id)
    }

    fun toggleGameInCollection(collectionId: Long) {
        val gameId = currentGameId
        if (gameId == 0L) return

        viewModelScope.launch {
            val collection = _uiState.value.collections.find { it.id == collectionId } ?: return@launch

            if (collection.isInCollection) {
                removeGameFromCollectionUseCase(collectionId, gameId)
            } else {
                addGameToCollectionUseCase(gameId, collectionId)
            }

            val updatedCollections = _uiState.value.collections.map {
                if (it.id == collectionId) it.copy(isInCollection = !it.isInCollection) else it
            }
            _uiState.update { it.copy(collections = updatedCollections) }
        }
    }

    fun showCreateCollectionFromModal() {
        _uiState.update { it.copy(showCreateCollectionDialog = true) }
    }

    fun hideCreateCollectionDialog() {
        _uiState.update { it.copy(showCreateCollectionDialog = false) }
    }

    fun createCollectionFromModal(name: String) {
        viewModelScope.launch {
            createCollectionUseCase(name)

            val gameId = currentGameId
            val allCollections = collectionDao.getAllCollections()
            val gameCollectionIds = collectionDao.getCollectionIdsForGame(gameId)

            val collectionItems = allCollections.map { collection ->
                CollectionItemUi(
                    id = collection.id,
                    name = collection.name,
                    isInCollection = collection.id in gameCollectionIds
                )
            }

            _uiState.update {
                it.copy(
                    showCreateCollectionDialog = false,
                    collections = collectionItems
                )
            }
        }
    }
}
