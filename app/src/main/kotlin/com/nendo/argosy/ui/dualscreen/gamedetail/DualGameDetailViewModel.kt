package com.nendo.argosy.ui.dualscreen.gamedetail

import android.content.Context
import com.nendo.argosy.R
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.DownloadQueueRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.steam.SteamDownloadState
import com.nendo.argosy.ui.common.appId
import com.nendo.argosy.ui.common.displayTitleId
import com.nendo.argosy.ui.common.isAndroidApp
import com.nendo.argosy.ui.common.isRommGame
import com.nendo.argosy.ui.common.isSteamGame
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileType
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileVersionSort
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.common.savechannel.SaveHistoryItem
import com.nendo.argosy.ui.common.savechannel.SaveSlotItem
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MEDIA_GRID_COLUMNS = 3
const val STATE_GRID_COLUMNS = 3

class DualGameDetailViewModel(
    private val gameRepository: GameRepository,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val prefetchGameSaveDataUseCase:
        com.nendo.argosy.domain.usecase.sync.PrefetchGameSaveDataUseCase,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    // TODO: replace with EmulatorConfigRepository once it exists (Agent B settings refactor).
    private val emulatorConfigDao: EmulatorConfigDao,
    private val downloadQueueRepository: DownloadQueueRepository,
    private val steamRepository: SteamRepository,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry,
    private val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager? = null,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
    private val sessionStateStore: SessionStateStore,
    private val preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    private val resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualGameDetailUiState())
    val uiState: StateFlow<DualGameDetailUiState> = _uiState.asStateFlow()

    private val _selectedScreenshotIndex = MutableStateFlow(-1)
    val selectedScreenshotIndex: StateFlow<Int> =
        _selectedScreenshotIndex.asStateFlow()

    private val _selectedOptionIndex = MutableStateFlow(0)
    val selectedOptionIndex: StateFlow<Int> =
        _selectedOptionIndex.asStateFlow()

    private val _saveSlots = MutableStateFlow<List<SaveSlotItem>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlotItem>> = _saveSlots.asStateFlow()

    private var _rawEntries: List<SaveEntryData> = emptyList()

    private val _savesLoading = MutableStateFlow(true)
    val savesLoading: StateFlow<Boolean> = _savesLoading.asStateFlow()

    private val _savesApplying = MutableStateFlow(false)
    val savesApplying: StateFlow<Boolean> = _savesApplying.asStateFlow()

    private val _savesSyncing = MutableStateFlow(false)
    val savesSyncing: StateFlow<Boolean> = _savesSyncing.asStateFlow()

    private val _saveHistory = MutableStateFlow<List<SaveHistoryItem>>(emptyList())
    val saveHistory: StateFlow<List<SaveHistoryItem>> = _saveHistory.asStateFlow()

    private val _selectedSlotIndex = MutableStateFlow(0)
    val selectedSlotIndex: StateFlow<Int> = _selectedSlotIndex.asStateFlow()

    private val _selectedHistoryIndex = MutableStateFlow(0)
    val selectedHistoryIndex: StateFlow<Int> = _selectedHistoryIndex.asStateFlow()

    private val _stateEntries = MutableStateFlow<List<UnifiedStateEntry>>(emptyList())
    val stateEntries: StateFlow<List<UnifiedStateEntry>> = _stateEntries.asStateFlow()

    private val _selectedStateIndex = MutableStateFlow(0)
    val selectedStateIndex: StateFlow<Int> = _selectedStateIndex.asStateFlow()

    private val _activeModal =
        MutableStateFlow(ActiveModal.NONE)
    val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

    private val _ratingPickerValue = MutableStateFlow(0)
    val ratingPickerValue: StateFlow<Int> =
        _ratingPickerValue.asStateFlow()

    private val _statusPickerValue = MutableStateFlow<String?>(null)
    val statusPickerValue: StateFlow<String?> =
        _statusPickerValue.asStateFlow()

    private val _visibleOptions =
        MutableStateFlow<List<GameDetailOption>>(emptyList())
    val visibleOptions: StateFlow<List<GameDetailOption>> =
        _visibleOptions.asStateFlow()

    private val _emulatorPickerList =
        MutableStateFlow<List<InstalledEmulator>>(emptyList())

    private val _collectionItems =
        MutableStateFlow<List<DualCollectionItem>>(emptyList())
    val collectionItems: StateFlow<List<DualCollectionItem>> =
        _collectionItems.asStateFlow()


    private val _emulatorPickerFocusIndex = MutableStateFlow(0)
    val emulatorPickerFocusIndex: StateFlow<Int> = _emulatorPickerFocusIndex.asStateFlow()

    private val _corePickerList = MutableStateFlow<List<RetroArchCore>>(emptyList())

    private val _corePickerFocusIndex = MutableStateFlow(0)
    val corePickerFocusIndex: StateFlow<Int> = _corePickerFocusIndex.asStateFlow()

    private val _savePathPickerFocusIndex = MutableStateFlow(0)
    val savePathPickerFocusIndex: StateFlow<Int> = _savePathPickerFocusIndex.asStateFlow()

    private val _displayTargetPickerFocusIndex = MutableStateFlow(0)
    val displayTargetPickerFocusIndex: StateFlow<Int> = _displayTargetPickerFocusIndex.asStateFlow()

    private val _memcardPickerList = MutableStateFlow<List<com.nendo.argosy.data.sync.platform.MemcardInfo>>(emptyList())
    val memcardPickerList: StateFlow<List<com.nendo.argosy.data.sync.platform.MemcardInfo>> = _memcardPickerList.asStateFlow()

    private val _memoryCardPickerFocusIndex = MutableStateFlow(0)
    val memoryCardPickerFocusIndex: StateFlow<Int> = _memoryCardPickerFocusIndex.asStateFlow()

    private val _variantPickerList = MutableStateFlow<List<GameFileEntity>>(emptyList())

    private val _variantPickerFocusIndex = MutableStateFlow(0)
    val variantPickerFocusIndex: StateFlow<Int> = _variantPickerFocusIndex.asStateFlow()

    private val _collectionPickerFocusIndex = MutableStateFlow(0)
    val collectionPickerFocusIndex: StateFlow<Int> = _collectionPickerFocusIndex.asStateFlow()

    private val _discPickerOptions = MutableStateFlow<List<DiscOption>>(emptyList())
    val discPickerOptions: StateFlow<List<DiscOption>> = _discPickerOptions.asStateFlow()

    private val _discPickerFocusIndex = MutableStateFlow(0)
    val discPickerFocusIndex: StateFlow<Int> = _discPickerFocusIndex.asStateFlow()

    private val _steamInstallOptions =
        MutableStateFlow<List<com.nendo.argosy.data.launcher.SteamLaunchers.MarkOption>>(emptyList())
    val steamInstallOptions: StateFlow<List<com.nendo.argosy.data.launcher.SteamLaunchers.MarkOption>> =
        _steamInstallOptions.asStateFlow()

    private val _steamInstallFocusIndex = MutableStateFlow(0)
    val steamInstallFocusIndex: StateFlow<Int> = _steamInstallFocusIndex.asStateFlow()

    private var downloadObserverJob: Job? = null
    private var steamDownloadObserverJob: Job? = null
    private var ratingDebounceJob: Job? = null
    private var difficultyDebounceJob: Job? = null
    private var statusDebounceJob: Job? = null

    val focusedSlotChannelName: String?
        get() {
            val slot = _saveSlots.value.getOrNull(_selectedSlotIndex.value)
            return if (slot?.isCreateAction == true) null else slot?.channelName
        }

    fun adjustRatingInline(delta: Int) {
        val current = _uiState.value.rating ?: 0
        val next = (current + delta).coerceIn(0, 10)
        _uiState.update { it.copy(rating = next.takeIf { v -> v > 0 }) }
        ratingDebounceJob?.cancel()
        ratingDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameRepository.updateUserRating(gameId, next)
        }
    }

    fun adjustDifficultyInline(delta: Int) {
        val current = _uiState.value.userDifficulty
        val next = (current + delta).coerceIn(0, 10)
        _uiState.update { it.copy(userDifficulty = next) }
        difficultyDebounceJob?.cancel()
        difficultyDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameRepository.updateUserDifficulty(gameId, next)
        }
    }

    fun cycleStatusInline(delta: Int) {
        val current = _uiState.value.status
        val next = if (delta > 0) CompletionStatus.cycleNext(current)
            else CompletionStatus.cyclePrev(current)
        _uiState.update { it.copy(status = next) }
        statusDebounceJob?.cancel()
        statusDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameRepository.updateStatus(gameId, next)
        }
    }

    fun openRatingPicker() {
        _ratingPickerValue.value = _uiState.value.rating ?: 0
        _activeModal.value = ActiveModal.RATING
    }

    fun openDifficultyPicker() {
        _ratingPickerValue.value = _uiState.value.userDifficulty
        _activeModal.value = ActiveModal.DIFFICULTY
    }

    fun openStatusPicker() {
        _statusPickerValue.value =
            _uiState.value.status
                ?: CompletionStatus.entries.first().apiValue
        _activeModal.value = ActiveModal.STATUS
    }

    fun adjustPickerValue(delta: Int) {
        _ratingPickerValue.update { (it + delta).coerceIn(0, 10) }
    }

    fun setPickerValue(value: Int) {
        _ratingPickerValue.value = value.coerceIn(0, 10)
    }

    fun moveStatusSelection(delta: Int) {
        val entries = CompletionStatus.entries
        val current = CompletionStatus.fromApiValue(
            _statusPickerValue.value
        ) ?: entries.first()
        val next = entries[
            (current.ordinal + delta).mod(entries.size)
        ]
        _statusPickerValue.value = next.apiValue
    }

    fun setStatusSelection(apiValue: String) {
        _statusPickerValue.value = apiValue
    }

    fun confirmPicker() {
        val gameId = _uiState.value.gameId
        if (gameId < 0) return
        when (_activeModal.value) {
            ActiveModal.RATING -> {
                val value = _ratingPickerValue.value
                _uiState.update { it.copy(rating = value.takeIf { v -> v > 0 }) }
                viewModelScope.launch {
                    gameRepository.updateUserRating(gameId, value)
                }
            }
            ActiveModal.DIFFICULTY -> {
                val value = _ratingPickerValue.value
                _uiState.update { it.copy(userDifficulty = value) }
                viewModelScope.launch {
                    gameRepository.updateUserDifficulty(gameId, value)
                }
            }
            ActiveModal.STATUS -> {
                val value = _statusPickerValue.value
                _uiState.update { it.copy(status = value) }
                viewModelScope.launch {
                    gameRepository.updateStatus(gameId, value)
                }
            }
            ActiveModal.EMULATOR, ActiveModal.CORE, ActiveModal.COLLECTION,
            ActiveModal.SAVE_PATH, ActiveModal.DISPLAY_TARGET,
            ActiveModal.MEMORY_CARD,
            ActiveModal.SAVE_NAME,
            ActiveModal.DISC_PICKER, ActiveModal.VARIANT_PICKER,
            ActiveModal.STEAM_INSTALL -> return
            ActiveModal.FILE_PICKER -> {}
            ActiveModal.NONE -> return
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun dismissPicker() {
        _activeModal.value = ActiveModal.NONE
    }

    fun loadGame(gameId: Long) {
        viewModelScope.launch { prefetchGameSaveDataUseCase(gameId) }
        viewModelScope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val platform = platformRepository.getById(game.platformId)
            val platformName = platform?.getDisplayName() ?: game.platformSlug

            val remoteUrls = game.screenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val cachedPaths = game.cachedScreenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val screenshots = remoteUrls.mapIndexed { index, url ->
                cachedPaths.getOrNull(index)
                    ?.takeIf { it.startsWith("/") }
                    ?: url
            }

            val isDownloaded = when {
                game.source == GameSource.ANDROID_APP -> true
                game.steamAppId != null && game.isExternallyManaged -> true
                game.steamAppId != null && game.localPath != null ->
                    downloadFileStatusRepository.isDownloadComplete(game.localPath)
                else -> game.localPath != null
            }
            val isPlayable = when {
                game.source == GameSource.ANDROID_APP -> true
                game.steamAppId != null -> isDownloaded && run {
                    val launcher = game.steamLauncher
                        ?.let { com.nendo.argosy.data.launcher.SteamLaunchers.getByPackage(it) }
                        ?: com.nendo.argosy.data.launcher.SteamLaunchers.getPreferred(context)
                    launcher?.isInstalled(context) == true
                }
                else -> isDownloaded
            }

            val gameSpecificConfig = emulatorConfigDao.getByGameId(game.id)
            val platformDefaultConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
            val configuredEmulatorPackage = gameSpecificConfig?.packageName ?: platformDefaultConfig?.packageName
            val configuredEmulatorName = gameSpecificConfig?.displayName ?: platformDefaultConfig?.displayName

            val emulatorContext = resolveGameEmulatorContext(
                gameId = game.id,
                platformId = game.platformId,
                platformSlug = game.platformSlug
            )
            val effectiveSavePackage = emulatorContext.effectivePackage
            val saveConfig = emulatorContext.saveConfig
            val hasFileBasedSaves = emulatorContext.supportsPerGameSavePath

            val ps2Memcards = emulatorContext.memcards
            val selectedMemcardPath = emulatorConfigDao.getSelectedMemcardForGame(game.id)
            val hasMultipleMemcards = emulatorContext.showMemcardRow
            val selectedMemcardName = ps2Memcards.find { it.path == selectedMemcardPath }?.name

            val emulatorDef = effectiveSavePackage?.let { EmulatorRegistry.getByPackage(it) }
            val isBuiltInEmulator = emulatorDef?.launchConfig is LaunchConfig.BuiltIn
            val platformCores = EmulatorRegistry.getSelectableCores(game.platformSlug, isBuiltInEmulator)
            val isCoreSelectable = emulatorDef?.launchConfig?.isCoreSelectable ?: true
            val hasMultipleCores = isCoreSelectable && platformCores.size > 1

            val selectedCoreId = if (isBuiltInEmulator) {
                builtinCoreResolver.resolveCoreId(
                    gameId = game.id,
                    platformId = game.platformId,
                    platformSlug = game.platformSlug
                )
            } else {
                gameSpecificConfig?.coreName
                    ?: platformDefaultConfig?.coreName
                    ?: EmulatorRegistry.getDefaultSelectableCore(game.platformSlug, isBuiltInEmulator)?.id
            }
            val selectedCoreName = if (hasMultipleCores) {
                platformCores.find { it.id == selectedCoreId }?.displayName
            } else null

            val downloadedVariants = excludePrimaryFile(gameRepository.getVariantsForGame(game.id), game)
                .filter { it.localPath != null }
            val hasMultipleVariants = downloadedVariants.isNotEmpty()
            val selectedVariantName = if (hasMultipleVariants && game.activeVariantFileId != null) {
                downloadedVariants.find { it.id == game.activeVariantFileId }?.fileName
            } else null

            val activeSave = activeSaveRepository.getActiveRow(game.id)
            val activeChannel = activeSave?.channelName
            val activeSaveTimestamp = activeSave?.cachedAt?.toEpochMilli()

            val newState = DualGameDetailUiState(
                gameId = game.id,
                title = game.title,
                coverPath = game.coverPath,
                backgroundPath = game.backgroundPath,
                platformName = platformName,
                developer = game.developer,
                releaseYear = game.releaseYear,
                description = game.description,
                playTimeMinutes = game.playTimeMinutes,
                lastPlayedAt = game.lastPlayed?.toEpochMilli() ?: 0,
                status = game.status,
                rating = game.userRating.takeIf { r -> r > 0 },
                isPlayable = isPlayable,
                userDifficulty = game.userDifficulty,
                screenshots = screenshots,
                currentTab = DualGameDetailTab.OPTIONS,
                availableTabs = DualGameDetailTab.entries.filterNot {
                    it == DualGameDetailTab.SAVES && !sessionStateStore.isSaveSyncEnabled()
                },
                isFavorite = game.isFavorite,
                isLoading = false,
                achievementCount = game.achievementCount,
                earnedAchievementCount = game.earnedAchievementCount,
                isRommGame = game.isRommGame,
                isSteamGame = game.isSteamGame,
                steamAppId = game.steamAppId,
                isAndroidApp = game.isAndroidApp,
                isDownloaded = isDownloaded,
                platformSlug = game.platformSlug,
                platformId = game.platformId,
                emulatorName = configuredEmulatorName,
                isBuiltInEmulator = isBuiltInEmulator,
                hasMultipleCores = hasMultipleCores,
                selectedCoreName = selectedCoreName,
                selectedCoreId = selectedCoreId,
                hasFileBasedSaves = hasFileBasedSaves,
                savePathOverride = gameSpecificConfig?.savePath?.takeIf { it.isNotBlank() },
                hasSecondaryDisplay = displayAffinityHelper.hasSecondaryDisplay,
                displayTargetName = gameSpecificConfig?.displayTarget,
                platformDisplayTargetName = platformDefaultConfig?.displayTarget,
                hasMultipleMemcards = hasMultipleMemcards,
                selectedMemcardName = selectedMemcardName,
                hasMultipleVariants = hasMultipleVariants,
                selectedVariantName = selectedVariantName,
                activeChannel = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp,
                isMultiDisc = game.isMultiDisc,
                isHidden = gameRepository.isGameHidden(game.id),
                titleId = game.displayTitleId
            )
            val sameGame = _uiState.value.gameId == game.id
            _uiState.value = newState
            _memcardPickerList.value = ps2Memcards
            _visibleOptions.value = newState.visibleOptions()

            _selectedScreenshotIndex.value = when {
                screenshots.isEmpty() -> -1
                sameGame -> _selectedScreenshotIndex.value.coerceIn(0, screenshots.size - 1)
                else -> 0
            }
            _selectedOptionIndex.value = if (sameGame) {
                _selectedOptionIndex.value.coerceIn(0, (_visibleOptions.value.size - 1).coerceAtLeast(0))
            } else 0
            observeDownloads(game.id)
            if (game.steamAppId != null) {
                observeSteamDownloads(game.id, game.steamAppId)
            }
        }
    }

    private fun observeDownloads(gameId: Long) {
        downloadObserverJob?.cancel()
        downloadObserverJob = viewModelScope.launch {
            downloadQueueRepository.observeActiveDownloads().collect { entities ->
                val entity = entities.find { it.gameId == gameId }
                val progress = if (entity != null && entity.totalBytes > 0) {
                    (entity.bytesDownloaded.toFloat() / entity.totalBytes).coerceIn(0f, 1f)
                } else if (entity != null) 0f else null
                val state = entity?.state

                val wasActive = _uiState.value.downloadState != null
                _uiState.update { it.copy(downloadProgress = progress, downloadState = state) }

                if (wasActive && entity == null) {
                    val game = gameRepository.getById(gameId) ?: return@collect
                    val isNowPlayable = when {
                        game.source == GameSource.ANDROID_APP -> true
                        game.steamAppId != null && game.localPath != null ->
                            downloadFileStatusRepository.isDownloadComplete(game.localPath)
                        else -> game.localPath != null
                    }
                    _uiState.update {
                        it.copy(
                            isPlayable = isNowPlayable,
                            isDownloaded = isNowPlayable
                        )
                    }
                    _visibleOptions.value = _uiState.value.visibleOptions()
                    _selectedOptionIndex.value = _selectedOptionIndex.value
                        .coerceIn(0, (_visibleOptions.value.size - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun observeSteamDownloads(gameId: Long, steamAppId: Long) {
        steamDownloadObserverJob?.cancel()
        val scm = steamContentManager
        if (scm != null) {
            steamDownloadObserverJob = viewModelScope.launch {
                scm.downloadState.collect { steamState ->
                    val stateAppId = steamState.appId
                    if (stateAppId != null && stateAppId != steamAppId) return@collect
                    val activeDl = scm.activeDownload.value
                    val progress = activeDl?.progress ?: when (steamState) {
                        is SteamDownloadState.Paused -> steamState.progress
                        else -> 0f
                    }
                    val uiState = steamStateToUiState(steamState)
                    if (uiState != null) {
                        _uiState.update { it.copy(downloadProgress = progress, downloadState = uiState) }
                    } else {
                        if (_uiState.value.downloadState != null) {
                            val game = gameRepository.getById(gameId) ?: return@collect
                            val isNowDownloaded = game.steamAppId != null && game.localPath != null &&
                                downloadFileStatusRepository.isDownloadComplete(game.localPath)
                            _uiState.update {
                                it.copy(
                                    downloadProgress = null,
                                    downloadState = null,
                                    isPlayable = isNowDownloaded,
                                    isDownloaded = isNowDownloaded
                                )
                            }
                            _visibleOptions.value = _uiState.value.visibleOptions()
                        }
                    }
                }
            }
        } else {
            steamDownloadObserverJob = viewModelScope.launch {
                steamRepository.observeDownloadByAppId(steamAppId).collect { entity ->
                    if (entity != null) {
                        val progress = if (entity.totalBytes > 0) {
                            (entity.bytesDownloaded.toFloat() / entity.totalBytes).coerceIn(0f, 1f)
                        } else 0f
                        _uiState.update {
                            it.copy(downloadProgress = progress, downloadState = entity.state)
                        }
                    } else if (_uiState.value.downloadState != null && _uiState.value.isSteamGame) {
                        val game = gameRepository.getById(gameId) ?: return@collect
                        val isNowDownloaded = game.steamAppId != null && game.localPath != null &&
                            downloadFileStatusRepository.isDownloadComplete(game.localPath)
                        _uiState.update {
                            it.copy(
                                downloadProgress = null,
                                downloadState = null,
                                isPlayable = isNowDownloaded,
                                isDownloaded = isNowDownloaded
                            )
                        }
                        _visibleOptions.value = _uiState.value.visibleOptions()
                    }
                }
            }
        }
    }

    private fun steamStateToUiState(state: SteamDownloadState): String? =
        when (state) {
            is SteamDownloadState.Preparing,
            is SteamDownloadState.Connecting,
            is SteamDownloadState.FetchingManifest -> "QUEUED"
            is SteamDownloadState.Validating,
            is SteamDownloadState.Moving,
            is SteamDownloadState.Cleaning -> "EXTRACTING"
            is SteamDownloadState.Downloading -> "DOWNLOADING"
            is SteamDownloadState.Paused -> "PAUSED"
            is SteamDownloadState.Completed,
            is SteamDownloadState.Failed,
            is SteamDownloadState.Idle -> null
        }

    fun onDeleteStarted() {
        _uiState.update {
            it.copy(
                isPlayable = false,
                isDownloaded = false,
                isDeleting = true,
                downloadProgress = null,
                downloadState = null
            )
        }
        _visibleOptions.value = _uiState.value.visibleOptions()
    }

    fun loadUnifiedSaves(
        entries: List<SaveEntryData>,
        activeChannel: String?,
        activeSaveTimestamp: Long?
    ) {
        _rawEntries = entries

        val channelGroups = entries.groupBy { it.channelName }
        val slotItems = mutableListOf<SaveSlotItem>()

        val autoSaves = channelGroups[null] ?: emptyList()
        slotItems.add(
            SaveSlotItem(
                channelName = null,
                displayName = context.getString(R.string.dual_detail_save_slot_auto),
                isActive = activeChannel == null,
                saveCount = autoSaves.size,
                latestTimestamp = autoSaves.maxByOrNull { it.timestamp }
                    ?.timestamp
            )
        )

        channelGroups.filterKeys { it != null }
            .forEach { (name, saves) ->
                slotItems.add(
                    SaveSlotItem(
                        channelName = name,
                        displayName = name!!,
                        isActive = name == activeChannel,
                        saveCount = saves.size,
                        latestTimestamp = saves.maxByOrNull { it.timestamp }
                            ?.timestamp
                    )
                )
            }

        if (activeChannel != null && slotItems.none { it.channelName.equals(activeChannel, ignoreCase = true) }) {
            slotItems.add(
                SaveSlotItem(
                    channelName = activeChannel,
                    displayName = activeChannel,
                    isActive = true,
                    saveCount = 0,
                    latestTimestamp = null
                )
            )
        }

        slotItems.add(
            SaveSlotItem(
                channelName = null,
                displayName = "+ New Slot",
                isActive = false,
                saveCount = 0,
                latestTimestamp = null,
                isCreateAction = true
            )
        )

        val preservedSlotName = _saveSlots.value
            .getOrNull(_selectedSlotIndex.value)?.channelName
        _saveSlots.value = slotItems
        val restoredIndex = if (preservedSlotName != null) {
            slotItems.indexOfFirst {
                !it.isCreateAction && it.channelName == preservedSlotName
            }.takeIf { it >= 0 }
        } else null
        _selectedSlotIndex.value = restoredIndex ?: 0

        val channelEntries = entries.filter { it.channelName == activeChannel }
        val hasSynced = channelEntries.any { it.source == "BOTH" || it.source == "SERVER" }
        val hasLocal = channelEntries.any { it.source == "LOCAL" || it.source == "BOTH" }
        val statusName = when {
            channelEntries.isEmpty() -> null
            hasSynced -> "SYNCED"
            hasLocal -> "LOCAL_ONLY"
            else -> null
        }

        _uiState.update {
            it.copy(
                activeChannel = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp,
                saveSyncStatusName = statusName
            )
        }
        updateHistoryForFocusedSlot(activeSaveTimestamp, preserveSelection = restoredIndex != null)
        _savesLoading.value = false
        _savesApplying.value = false
    }

    private fun updateHistoryForFocusedSlot(
        activeSaveTimestamp: Long? = _uiState.value.activeSaveTimestamp,
        preserveSelection: Boolean = false
    ) {
        val slot = _saveSlots.value.getOrNull(_selectedSlotIndex.value)
        if (slot == null || slot.isCreateAction) {
            _saveHistory.value = emptyList()
            return
        }
        val previousSelected = if (preserveSelection) {
            _saveHistory.value.getOrNull(_selectedHistoryIndex.value)
                ?.takeIf { it.channelName == slot.channelName }
        } else null
        val channelName = slot.channelName
        val activeChannel = _uiState.value.activeChannel
        val isActiveChannel = channelName == activeChannel
        val filtered = _rawEntries
            .filter { it.channelName == channelName }
            .sortedByDescending { it.timestamp }
        _saveHistory.value = filtered.mapIndexed { i, entry ->
            val isApplied = isActiveChannel && if (activeSaveTimestamp != null) {
                entry.timestamp == activeSaveTimestamp
            } else {
                i == 0
            }
            SaveHistoryItem(
                cacheId = entry.localCacheId ?: -1,
                timestamp = entry.timestamp,
                size = entry.size,
                channelName = entry.channelName,
                isLocal = entry.source != "SERVER",
                isSynced = entry.source == "BOTH" || entry.source == "SERVER",
                isActiveRestorePoint = isApplied,
                isLatest = i == 0,
                isHardcore = entry.isHardcore,
                isRollback = entry.isRollback
            )
        }
        _selectedHistoryIndex.value = previousSelected?.let { prev ->
            _saveHistory.value.indexOfFirst { it.timestamp == prev.timestamp }
                .takeIf { i -> i >= 0 }
        } ?: 0
    }

    fun reloadSaves() {
        _savesLoading.value = true
    }

    fun setSyncing(syncing: Boolean) {
        _savesSyncing.value = syncing
    }

    fun setActiveChannel(channelName: String?) {
        _savesApplying.value = true
        _uiState.update {
            it.copy(activeChannel = channelName, activeSaveTimestamp = null)
        }
        _saveSlots.update { slots ->
            slots.map { slot ->
                if (slot.isCreateAction) slot
                else slot.copy(isActive = slot.channelName == channelName)
            }
        }
    }

    fun setActiveRestorePoint(channelName: String?, timestamp: Long) {
        _savesApplying.value = true
        _uiState.update {
            it.copy(
                activeChannel = channelName,
                activeSaveTimestamp = timestamp
            )
        }
        _saveSlots.update { slots ->
            slots.map { slot ->
                if (slot.isCreateAction) slot
                else slot.copy(isActive = slot.channelName == channelName)
            }
        }
        updateHistoryForFocusedSlot(timestamp, preserveSelection = true)
    }

    fun focusSlotsColumn() {
        _uiState.update { it.copy(saveFocusColumn = SaveFocusColumn.SLOTS) }
    }

    fun focusHistoryColumn() {
        if (_saveHistory.value.isEmpty()) return
        _uiState.update { it.copy(saveFocusColumn = SaveFocusColumn.HISTORY) }
        if (_selectedHistoryIndex.value < 0) _selectedHistoryIndex.value = 0
    }

    fun moveSlotSelection(delta: Int) {
        val max = (_saveSlots.value.size - 1).coerceAtLeast(0)
        _selectedSlotIndex.update { (it + delta).coerceIn(0, max) }
        updateHistoryForFocusedSlot()
    }

    fun moveHistorySelection(delta: Int) {
        val max = (_saveHistory.value.size - 1).coerceAtLeast(0)
        _selectedHistoryIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun loadStateEntries(entries: List<UnifiedStateEntry>) {
        val previous = _stateEntries.value.getOrNull(_selectedStateIndex.value)
        _stateEntries.value = entries
        _selectedStateIndex.value = previous?.let { prev ->
            entries.indexOfFirst { it.slotNumber == prev.slotNumber && it.timestamp == prev.timestamp }
                .takeIf { i -> i >= 0 }
        } ?: _selectedStateIndex.value.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    }

    fun moveStateSelection(delta: Int) {
        val max = (_stateEntries.value.size - 1).coerceAtLeast(0)
        _selectedStateIndex.update { (it + delta).coerceIn(0, max) }
    }

    /**
     * Walks the states tab the way it is drawn: the auto state is a row above a grid of numbered
     * slots, so up and down cross whole grid rows and only left and right step one slot.
     */
    fun moveStateGrid(dx: Int, dy: Int) {
        val entries = _stateEntries.value
        if (entries.isEmpty()) return
        val autoCount = entries.count { it.slotNumber < 0 }
        val current = _selectedStateIndex.value

        if (current < autoCount) {
            if (dy > 0 && entries.size > autoCount) _selectedStateIndex.value = autoCount
            return
        }

        val position = current - autoCount
        val lastPosition = entries.size - autoCount - 1
        val column = position % STATE_GRID_COLUMNS

        val target = when {
            dx < 0 -> if (column > 0) position - 1 else position
            dx > 0 -> if (column < STATE_GRID_COLUMNS - 1 && position < lastPosition) {
                position + 1
            } else {
                position
            }
            dy < 0 -> if (position < STATE_GRID_COLUMNS) -1 else position - STATE_GRID_COLUMNS
            dy > 0 -> (position + STATE_GRID_COLUMNS).coerceAtMost(lastPosition)
            else -> position
        }

        _selectedStateIndex.value = if (target < 0) {
            (autoCount - 1).coerceAtLeast(0)
        } else {
            target + autoCount
        }
    }

    fun getFocusedStateEntry(): UnifiedStateEntry? {
        return _stateEntries.value.getOrNull(_selectedStateIndex.value)
    }

    fun stateMenuActions(): List<DualStateMenuAction> {
        val entry = getFocusedStateEntry() ?: return emptyList()
        return buildList {
            if (entry.canRestore) add(DualStateMenuAction.COPY_TO)
            if (!entry.isEmpty) add(DualStateMenuAction.DELETE)
        }
    }

    /**
     * Whether the states tab is currently showing something that owns input: the slot menu, one of
     * its confirmations, or the grid standing in as a copy destination picker.
     */
    fun stateOverlayActive(): Boolean {
        val state = _uiState.value
        return state.stateMenuVisible ||
            state.statePrompt != null ||
            state.stateCopySourceSlot != null
    }

    fun moveStateMenuFocus(delta: Int) {
        val max = (stateMenuActions().size - 1).coerceAtLeast(0)
        _uiState.update {
            it.copy(stateMenuFocusIndex = (it.stateMenuFocusIndex + delta).coerceIn(0, max))
        }
    }

    fun moveStatePromptFocus(delta: Int) {
        _uiState.update {
            it.copy(statePromptFocusIndex = (it.statePromptFocusIndex + delta).coerceIn(0, 1))
        }
    }

    fun setStateMenuFocus(index: Int) {
        _uiState.update { it.copy(stateMenuFocusIndex = index) }
    }

    fun setStatePromptFocus(index: Int) {
        _uiState.update { it.copy(statePromptFocusIndex = index) }
    }

    fun openStateMenu() {
        if (stateMenuActions().isEmpty()) return
        _uiState.update { it.copy(stateMenuVisible = true, stateMenuFocusIndex = 0) }
    }

    /**
     * Back out of one layer of the states tab: a confirmation, then the menu, then copy mode.
     * Returns whether anything was dismissed, so the caller knows not to leave the screen.
     */
    fun dismissStateOverlay(): Boolean {
        val state = _uiState.value
        return when {
            state.statePrompt != null -> {
                _uiState.update { it.copy(statePrompt = null, statePromptFocusIndex = 0) }
                true
            }
            state.stateMenuVisible -> {
                _uiState.update { it.copy(stateMenuVisible = false) }
                true
            }
            state.stateCopySourceSlot != null -> {
                _uiState.update { it.copy(stateCopySourceSlot = null) }
                true
            }
            else -> false
        }
    }

    fun confirmStateOverlay() {
        val state = _uiState.value
        when {
            state.statePrompt != null -> confirmStatePrompt()
            state.stateMenuVisible -> confirmStateMenu()
            state.stateCopySourceSlot != null -> placeStateCopy()
            else -> openStateMenu()
        }
    }

    fun tapStateEntry(index: Int) {
        _selectedStateIndex.value = index
        confirmStateOverlay()
    }

    private fun confirmStateMenu() {
        val action = stateMenuActions().getOrNull(_uiState.value.stateMenuFocusIndex)
        val entry = getFocusedStateEntry()
        _uiState.update { it.copy(stateMenuVisible = false) }
        if (action == null || entry == null) return
        when (action) {
            DualStateMenuAction.COPY_TO ->
                _uiState.update { it.copy(stateCopySourceSlot = entry.slotNumber) }
            DualStateMenuAction.DELETE -> promptStateDelete()
        }
    }

    fun promptStateDelete() {
        val entry = getFocusedStateEntry() ?: return
        if (entry.isEmpty) return
        _uiState.update {
            it.copy(
                stateMenuVisible = false,
                statePrompt = DualStatePrompt.DELETE,
                statePromptSlot = entry.slotNumber,
                statePromptFocusIndex = 0
            )
        }
    }

    private fun placeStateCopy() {
        val sourceSlot = _uiState.value.stateCopySourceSlot ?: return
        val target = getFocusedStateEntry() ?: return
        if (target.slotNumber == sourceSlot) return
        if (!target.isEmpty) {
            _uiState.update {
                it.copy(
                    statePrompt = DualStatePrompt.OVERWRITE,
                    statePromptSlot = target.slotNumber,
                    statePromptFocusIndex = 0
                )
            }
            return
        }
        runStateCopy(sourceSlot, target.slotNumber)
    }

    private fun confirmStatePrompt() {
        val state = _uiState.value
        val prompt = state.statePrompt ?: return
        val confirmed = state.statePromptFocusIndex == 1
        _uiState.update { it.copy(statePrompt = null, statePromptFocusIndex = 0) }
        if (!confirmed) return
        when (prompt) {
            DualStatePrompt.DELETE -> stateDirectAction("STATE_DELETE", state.statePromptSlot.toString())
            DualStatePrompt.OVERWRITE -> {
                val sourceSlot = state.stateCopySourceSlot ?: return
                runStateCopy(sourceSlot, state.statePromptSlot)
            }
        }
    }

    private fun runStateCopy(sourceSlot: Int, targetSlot: Int) {
        _uiState.update { it.copy(stateCopySourceSlot = null) }
        stateDirectAction("STATE_COPY", "$sourceSlot:$targetSlot")
    }

    private fun stateDirectAction(type: String, payload: String) {
        val gameId = _uiState.value.gameId
        if (gameId < 0) return
        DualScreenManagerHolder.instance?.handleDirectAction(type, gameId, payload)
    }

    fun setTab(tab: DualGameDetailTab) {
        if (tab !in _uiState.value.availableTabs) return
        _uiState.update {
            it.copy(
                currentTab = tab,
                stateMenuVisible = false,
                stateCopySourceSlot = null,
                statePrompt = null
            )
        }
        resetSelectionForTab(tab)
    }

    fun nextTab() {
        val entries = _uiState.value.availableTabs
        val current = _uiState.value.currentTab
        val currentIdx = entries.indexOf(current).coerceAtLeast(0)
        val next = entries[(currentIdx + 1) % entries.size]
        setTab(next)
    }

    fun previousTab() {
        val entries = _uiState.value.availableTabs
        val current = _uiState.value.currentTab
        val currentIdx = entries.indexOf(current).coerceAtLeast(0)
        val prev = entries[
            if (currentIdx == 0) entries.size - 1
            else currentIdx - 1
        ]
        setTab(prev)
    }

    fun moveSelectionUp() {
        when (_uiState.value.currentTab) {
            DualGameDetailTab.SAVES -> {
                if (_uiState.value.saveFocusColumn == SaveFocusColumn.SLOTS) {
                    moveSlotSelection(-1)
                } else {
                    moveHistorySelection(-1)
                }
            }
            DualGameDetailTab.STATES -> moveStateGrid(0, -1)
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.update { idx ->
                    (idx - 1).coerceAtLeast(0)
                }
            }
            DualGameDetailTab.MEDIA -> {
                val screenshots = _uiState.value.screenshots
                if (screenshots.isEmpty()) return
                _selectedScreenshotIndex.update { idx ->
                    (idx - MEDIA_GRID_COLUMNS).coerceAtLeast(0)
                }
            }
        }
    }

    fun moveSelectionDown() {
        when (_uiState.value.currentTab) {
            DualGameDetailTab.SAVES -> {
                if (_uiState.value.saveFocusColumn == SaveFocusColumn.SLOTS) {
                    moveSlotSelection(1)
                } else {
                    moveHistorySelection(1)
                }
            }
            DualGameDetailTab.STATES -> moveStateGrid(0, 1)
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.update { idx ->
                    (idx + 1).coerceAtMost(
                        (_visibleOptions.value.size - 1).coerceAtLeast(0)
                    )
                }
            }
            DualGameDetailTab.MEDIA -> {
                val screenshots = _uiState.value.screenshots
                if (screenshots.isEmpty()) return
                _selectedScreenshotIndex.update { idx ->
                    (idx + MEDIA_GRID_COLUMNS)
                        .coerceAtMost(screenshots.size - 1)
                }
            }
        }
    }

    fun moveSelectionLeft() {
        if (_uiState.value.currentTab == DualGameDetailTab.STATES) {
            moveStateGrid(-1, 0)
            return
        }
        if (_uiState.value.currentTab != DualGameDetailTab.MEDIA) return
        val screenshots = _uiState.value.screenshots
        if (screenshots.isEmpty()) return
        _selectedScreenshotIndex.update { idx ->
            (idx - 1).coerceAtLeast(0)
        }
    }

    fun moveSelectionRight() {
        if (_uiState.value.currentTab == DualGameDetailTab.STATES) {
            moveStateGrid(1, 0)
            return
        }
        if (_uiState.value.currentTab != DualGameDetailTab.MEDIA) return
        val screenshots = _uiState.value.screenshots
        if (screenshots.isEmpty()) return
        _selectedScreenshotIndex.update { idx ->
            (idx + 1).coerceAtMost(screenshots.size - 1)
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        if (state.gameId < 0) return
        val newFavorite = !state.isFavorite
        _uiState.update { it.copy(isFavorite = newFavorite) }
        viewModelScope.launch {
            gameRepository.updateFavoriteWithSync(state.gameId, newFavorite)
        }
    }

    fun getGameDetailIntent(
        gameId: Long
    ): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://game/$gameId")
            setPackage(context.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        val options =
            displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getSelectedScreenshotPath(): String? {
        val idx = _selectedScreenshotIndex.value
        val screenshots = _uiState.value.screenshots
        return screenshots.getOrNull(idx)
    }

    fun setScreenshotIndex(index: Int) {
        _selectedScreenshotIndex.value = index
    }

    private fun resetSelectionForTab(tab: DualGameDetailTab) {
        when (tab) {
            DualGameDetailTab.SAVES -> {
                _selectedSlotIndex.value =
                    if (_saveSlots.value.isNotEmpty()) 0 else 0
                _selectedHistoryIndex.value = 0
                _uiState.update {
                    it.copy(saveFocusColumn = SaveFocusColumn.SLOTS)
                }
                updateHistoryForFocusedSlot()
            }
            DualGameDetailTab.STATES -> {
                _selectedStateIndex.value = 0
            }
            DualGameDetailTab.MEDIA -> {
                _selectedScreenshotIndex.value =
                    if (_uiState.value.screenshots.isNotEmpty()) 0 else -1
            }
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.value = 0
            }
        }
    }

    fun openEmulatorPicker(emulators: List<InstalledEmulator>) {
        _emulatorPickerList.value = emulators
        _emulatorPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.EMULATOR
    }

    fun confirmEmulatorByIndex(index: Int) {
        val emulators = _emulatorPickerList.value
        val selected = if (index == 0) null else emulators.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            configureEmulatorUseCase.setForGame(state.gameId, state.platformId, state.platformSlug, selected)
            val builtinEnabled = preferencesRepository.userPreferences.first().builtinLibretroEnabled
            val resolvedDef = selected?.def
                ?: com.nendo.argosy.data.emulator.getSharedEmulatorDetector(context)
                    .getPreferredEmulator(state.platformSlug, builtinEnabled)?.def
            val isBuiltIn = resolvedDef?.launchConfig is LaunchConfig.BuiltIn
            _uiState.update {
                it.copy(
                    emulatorName = selected?.def?.displayName,
                    isBuiltInEmulator = isBuiltIn,
                    savePathOverride = null,
                    displayTargetName = null
                )
            }
        }
        _activeModal.value = ActiveModal.NONE
    }

    /**
     * Opens the core picker for the game's resolved emulator mode and returns the display
     * names of the backing core list, in the same order, for the upper-screen modal. The
     * returned names are the only ones that may be shown: confirm indexes back into the
     * list stored here.
     */
    fun openCorePicker(): List<String> {
        val state = _uiState.value
        val cores = EmulatorRegistry.getSelectableCores(state.platformSlug, state.isBuiltInEmulator)
        _corePickerList.value = cores
        _corePickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.CORE
        return cores.map { it.displayName }
    }

    fun confirmCoreByIndex(index: Int) {
        val cores = _corePickerList.value
        val selectedCore = if (index == 0) null else cores.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            val coreId = selectedCore?.id
            configureEmulatorUseCase.setCoreForGame(state.gameId, coreId)
            _uiState.update {
                it.copy(
                    selectedCoreName = selectedCore?.displayName,
                    selectedCoreId = coreId
                )
            }
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun moveCorePickerFocus(delta: Int) {
        val total = _corePickerList.value.size + 1
        val max = (total - 1).coerceAtLeast(0)
        _corePickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun openSavePathPicker() {
        _savePathPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.SAVE_PATH
    }

    fun moveSavePathPickerFocus(delta: Int) {
        val max = if (_uiState.value.savePathOverride != null) 1 else 0
        _savePathPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun confirmSavePathByIndex(index: Int) {
        val state = _uiState.value
        if (index == 0 && state.savePathOverride != null) {
            viewModelScope.launch {
                configureEmulatorUseCase.clearSavePathForGame(state.gameId)
                _uiState.update { it.copy(savePathOverride = null) }
            }
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun openDisplayTargetPicker() {
        _displayTargetPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.DISPLAY_TARGET
    }

    fun moveDisplayTargetPickerFocus(delta: Int) {
        val max = EmulatorDisplayTarget.entries.size
        _displayTargetPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun confirmDisplayTargetByIndex(index: Int) {
        val selected = if (index == 0) null else EmulatorDisplayTarget.entries.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            configureEmulatorUseCase.setDisplayTargetForGame(state.gameId, selected?.name)
            _uiState.update { it.copy(displayTargetName = selected?.name) }
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun openMemoryCardPicker() {
        _memoryCardPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.MEMORY_CARD
    }

    fun moveMemoryCardPickerFocus(delta: Int) {
        val max = _memcardPickerList.value.size
        _memoryCardPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun confirmMemoryCardByIndex(index: Int) {
        val selected = if (index == 0) null else _memcardPickerList.value.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            if (selected != null) {
                configureEmulatorUseCase.setMemcardForGame(state.gameId, selected.path)
            } else {
                configureEmulatorUseCase.clearMemcardForGame(state.gameId)
            }
            _uiState.update { it.copy(selectedMemcardName = selected?.name) }
        }
        _activeModal.value = ActiveModal.NONE
    }

    suspend fun getDownloadedVariants(): List<GameFileEntity> {
        val gameId = _uiState.value.gameId
        val game = gameRepository.getById(gameId) ?: return emptyList()
        return excludePrimaryFile(gameRepository.getVariantsForGame(gameId), game)
            .filter { it.localPath != null }
    }

    private fun excludePrimaryFile(variants: List<GameFileEntity>, game: GameEntity): List<GameFileEntity> {
        val primaryFileName = game.rommFileName ?: game.localPath?.substringAfterLast('/')
        return variants.filterNot { primaryFileName != null && it.fileName == primaryFileName }
    }

    fun openVariantPicker(variants: List<GameFileEntity>) {
        _variantPickerList.value = variants
        _variantPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.VARIANT_PICKER
    }

    fun confirmVariantByIndex(index: Int) {
        val variants = _variantPickerList.value
        val selectedVariant = if (index == 0) null else variants.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            gameRepository.setActiveVariant(state.gameId, selectedVariant?.id)
            _uiState.update {
                it.copy(selectedVariantName = selectedVariant?.fileName)
            }
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun moveVariantPickerFocus(delta: Int) {
        val total = _variantPickerList.value.size + 1
        val max = (total - 1).coerceAtLeast(0)
        _variantPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun openCollectionModal() {
        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() && it.isUserCreated }
            val memberIds = collectionRepository.getCollectionIdsForGame(gameId)
            _collectionItems.value = allCollections.map {
                DualCollectionItem(
                    it.id, it.name, memberIds.contains(it.id)
                )
            }
            _collectionPickerFocusIndex.value = 0
            _activeModal.value = ActiveModal.COLLECTION
        }
    }

    fun toggleCollection(collectionId: Long) {
        val gameId = _uiState.value.gameId
        val item = _collectionItems.value.find { it.id == collectionId }
            ?: return
        viewModelScope.launch {
            if (item.isInCollection) {
                collectionRepository.removeGameFromCollection(collectionId, gameId)
            } else {
                collectionRepository.addGameToCollection(
                    CollectionGameEntity(collectionId, gameId)
                )
            }
            _collectionItems.update { list ->
                list.map {
                    if (it.id == collectionId)
                        it.copy(isInCollection = !it.isInCollection)
                    else it
                }
            }
        }
    }

    fun createAndAddToCollection(name: String) {
        val gameId = _uiState.value.gameId
        if (gameId < 0) return
        viewModelScope.launch {
            val collectionId = collectionRepository.insertCollection(
                CollectionEntity(name = name)
            )
            collectionRepository.addGameToCollection(
                CollectionGameEntity(
                    collectionId = collectionId,
                    gameId = gameId
                )
            )
            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() && it.isUserCreated }
            val memberIds = collectionRepository.getCollectionIdsForGame(gameId)
            _collectionItems.value = allCollections.map {
                DualCollectionItem(
                    it.id, it.name, memberIds.contains(it.id)
                )
            }
        }
    }

    fun dismissCollectionModal() {
        _activeModal.value = ActiveModal.NONE
    }


    fun moveEmulatorPickerFocus(delta: Int) {
        val total = _emulatorPickerList.value.size + 1
        val max = (total - 1).coerceAtLeast(0)
        _emulatorPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun moveCollectionPickerFocus(delta: Int) {
        val total = _collectionItems.value.size + 1
        val max = (total).coerceAtLeast(0)
        _collectionPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun openDiscPicker(discs: List<DiscOption>) {
        _discPickerOptions.value = discs
        _discPickerFocusIndex.value = 0
        _activeModal.value = ActiveModal.DISC_PICKER
    }

    fun moveDiscPickerFocus(delta: Int) {
        val max = (_discPickerOptions.value.size - 1).coerceAtLeast(0)
        _discPickerFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun confirmDiscSelection(): DiscOption? {
        val disc = _discPickerOptions.value.getOrNull(_discPickerFocusIndex.value)
        _activeModal.value = ActiveModal.NONE
        _discPickerOptions.value = emptyList()
        _discPickerFocusIndex.value = 0
        return disc
    }

    fun dismissDiscPicker() {
        _activeModal.value = ActiveModal.NONE
        _discPickerOptions.value = emptyList()
        _discPickerFocusIndex.value = 0
    }

    fun steamMarkOptions(): List<com.nendo.argosy.data.launcher.SteamLaunchers.MarkOption> =
        com.nendo.argosy.data.launcher.SteamLaunchers.getMarkOptions(context)

    fun openSteamInstallModal(options: List<com.nendo.argosy.data.launcher.SteamLaunchers.MarkOption>) {
        _steamInstallOptions.value = options
        _steamInstallFocusIndex.value = 0
        _activeModal.value = ActiveModal.STEAM_INSTALL
    }

    fun moveSteamInstallFocus(delta: Int) {
        val max = _steamInstallOptions.value.size
        _steamInstallFocusIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun dismissSteamInstallModal() {
        _activeModal.value = ActiveModal.NONE
        _steamInstallOptions.value = emptyList()
        _steamInstallFocusIndex.value = 0
    }

}
