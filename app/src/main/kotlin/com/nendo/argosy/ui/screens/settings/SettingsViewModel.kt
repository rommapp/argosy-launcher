package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.scanner.AndroidGameScanner
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.ui.screens.settings.delegates.AmbientAudioSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.EmulatorSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.PermissionsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ServerSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SoundSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SteamSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.StorageSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.ui.ModalResetSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val imageCacheManager: ImageCacheManager,
    private val syncLibraryUseCase: SyncLibraryUseCase,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val updateRepository: UpdateRepository,
    private val appInstaller: AppInstaller,
    private val soundManager: SoundFeedbackManager,
    private val pendingSaveSyncDao: PendingSaveSyncDao,
    private val retroArchConfigParser: RetroArchConfigParser,
    val displayDelegate: DisplaySettingsDelegate,
    val controlsDelegate: ControlsSettingsDelegate,
    val soundsDelegate: SoundSettingsDelegate,
    val ambientAudioDelegate: AmbientAudioSettingsDelegate,
    val emulatorDelegate: EmulatorSettingsDelegate,
    val serverDelegate: ServerSettingsDelegate,
    val storageDelegate: StorageSettingsDelegate,
    val syncDelegate: SyncSettingsDelegate,
    val steamDelegate: SteamSettingsDelegate,
    val permissionsDelegate: PermissionsSettingsDelegate,
    private val androidGameScanner: AndroidGameScanner,
    private val modalResetSignal: ModalResetSignal
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _downloadUpdateEvent = MutableSharedFlow<Unit>()
    val downloadUpdateEvent: SharedFlow<Unit> = _downloadUpdateEvent.asSharedFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    val imageCacheProgress: StateFlow<ImageCacheProgress> = imageCacheManager.progress

    val openBackgroundPickerEvent: SharedFlow<Unit> = displayDelegate.openBackgroundPickerEvent
    val openCustomSoundPickerEvent: SharedFlow<SoundType> = soundsDelegate.openCustomSoundPickerEvent
    val openAudioFilePickerEvent: SharedFlow<Unit> = ambientAudioDelegate.openAudioFilePickerEvent
    val openAudioFileBrowserEvent: SharedFlow<Unit> = ambientAudioDelegate.openAudioFileBrowserEvent
    val launchPlatformFolderPicker: SharedFlow<Long> = storageDelegate.launchPlatformFolderPicker
    val launchSavePathPicker: SharedFlow<Unit> = emulatorDelegate.launchSavePathPicker
    val openImageCachePickerEvent: SharedFlow<Unit> = syncDelegate.openImageCachePickerEvent

    private val _openLogFolderPickerEvent = MutableSharedFlow<Unit>()
    val openLogFolderPickerEvent: SharedFlow<Unit> = _openLogFolderPickerEvent.asSharedFlow()

    init {
        observeDelegateStates()
        observeDelegateEvents()
        observeModalResetSignal()
        loadSettings()
    }

    private fun observeModalResetSignal() {
        modalResetSignal.signal.onEach {
            dismissAllModals()
        }.launchIn(viewModelScope)
    }

    private fun dismissAllModals() {
        emulatorDelegate.dismissEmulatorPicker()
        emulatorDelegate.dismissSavePathModal()
        storageDelegate.closePlatformSettingsModal()
        soundsDelegate.dismissSoundPicker()
        syncDelegate.dismissRegionPicker()
        steamDelegate.dismissAddSteamGameDialog()
    }

    private fun observeDelegateStates() {
        displayDelegate.state.onEach { display ->
            _uiState.update { it.copy(display = display, colorFocusIndex = displayDelegate.colorFocusIndex) }
        }.launchIn(viewModelScope)

        controlsDelegate.state.onEach { controls ->
            _uiState.update { it.copy(controls = controls) }
        }.launchIn(viewModelScope)

        soundsDelegate.state.onEach { sounds ->
            _uiState.update { it.copy(sounds = sounds) }
        }.launchIn(viewModelScope)

        ambientAudioDelegate.state.onEach { ambientAudio ->
            _uiState.update { it.copy(ambientAudio = ambientAudio) }
        }.launchIn(viewModelScope)

        emulatorDelegate.state.onEach { emulators ->
            _uiState.update { it.copy(emulators = emulators) }
        }.launchIn(viewModelScope)

        serverDelegate.state.onEach { server ->
            _uiState.update { it.copy(server = server) }
        }.launchIn(viewModelScope)

        storageDelegate.state.onEach { storage ->
            _uiState.update { it.copy(storage = storage) }
        }.launchIn(viewModelScope)

        storageDelegate.launchFolderPicker.onEach { launch ->
            _uiState.update { it.copy(launchFolderPicker = launch) }
        }.launchIn(viewModelScope)

        storageDelegate.showMigrationDialog.onEach { show ->
            _uiState.update { it.copy(showMigrationDialog = show) }
        }.launchIn(viewModelScope)

        storageDelegate.pendingStoragePath.onEach { path ->
            _uiState.update { it.copy(pendingStoragePath = path) }
        }.launchIn(viewModelScope)

        storageDelegate.isMigrating.onEach { migrating ->
            _uiState.update { it.copy(isMigrating = migrating) }
        }.launchIn(viewModelScope)

        syncDelegate.state.onEach { syncSettings ->
            _uiState.update { it.copy(syncSettings = syncSettings) }
        }.launchIn(viewModelScope)

        steamDelegate.state.onEach { steam ->
            _uiState.update { it.copy(steam = steam) }
        }.launchIn(viewModelScope)

        androidGameScanner.progress.onEach { progress ->
            _uiState.update {
                it.copy(
                    android = AndroidSettingsState(
                        isScanning = progress.isScanning,
                        scanProgressPercent = progress.progressPercent,
                        currentApp = progress.currentApp,
                        gamesFound = progress.gamesFound,
                        lastScanGamesAdded = it.android.lastScanGamesAdded
                    )
                )
            }
        }.launchIn(viewModelScope)

        permissionsDelegate.state.onEach { permissions ->
            _uiState.update { it.copy(permissions = permissions) }
        }.launchIn(viewModelScope)
    }

    private fun observeDelegateEvents() {
        merge(
            syncDelegate.requestStoragePermissionEvent,
            steamDelegate.requestStoragePermissionEvent,
            storageDelegate.requestStoragePermissionEvent
        ).onEach {
            _requestStoragePermissionEvent.emit(Unit)
        }.launchIn(viewModelScope)

        emulatorDelegate.openUrlEvent.onEach { url ->
            _openUrlEvent.emit(url)
        }.launchIn(viewModelScope)
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val installedEmulators = emulatorDetector.detectEmulators()
            val platforms = platformDao.observePlatformsWithGames().first()

            val installedPackages = installedEmulators.map { it.def.packageName }.toSet()

            val platformConfigs = platforms
                .filter { it.slug != "steam" }
                .map { platform ->
                val defaultConfig = emulatorConfigDao.getDefaultForPlatform(platform.id)
                val available = installedEmulators.filter { platform.slug in it.def.supportedPlatforms }
                val isUserConfigured = defaultConfig != null

                val recommended = EmulatorRegistry.getRecommendedEmulators()[platform.slug] ?: emptyList()
                val downloadable = recommended
                    .mapNotNull { EmulatorRegistry.getById(it) }
                    .filter { it.packageName !in installedPackages && it.downloadUrl != null }

                val selectedEmulatorDef = defaultConfig?.packageName?.let { emulatorDetector.getByPackage(it) }
                val autoResolvedEmulator = emulatorDetector.getPreferredEmulator(platform.slug)?.def
                val effectiveEmulatorDef = selectedEmulatorDef ?: autoResolvedEmulator
                val isRetroArch = effectiveEmulatorDef?.launchConfig is LaunchConfig.RetroArch
                val availableCores = if (isRetroArch) {
                    EmulatorRegistry.getCoresForPlatform(platform.slug)
                } else {
                    emptyList()
                }

                val selectedCore = if (isRetroArch && defaultConfig?.coreName != null) {
                    defaultConfig.coreName
                } else if (isRetroArch) {
                    EmulatorRegistry.getDefaultCore(platform.slug)?.id
                } else {
                    null
                }

                val emulatorId = effectiveEmulatorDef?.id
                val savePathConfig = emulatorId?.let { SavePathRegistry.getConfig(it) }
                val showSavePath = savePathConfig != null

                val computedSavePath = when {
                    emulatorId == null -> null
                    isRetroArch -> {
                        val packageName = effectiveEmulatorDef.packageName
                        retroArchConfigParser.resolveSavePaths(
                            packageName = packageName,
                            systemName = platform.slug,
                            coreName = selectedCore
                        ).firstOrNull()
                    }
                    else -> savePathConfig?.defaultPaths?.firstOrNull()
                }

                val userSaveConfig = emulatorId?.let { emulatorDelegate.getEmulatorSaveConfig(it) }
                val isUserSavePathOverride = userSaveConfig?.isUserOverride == true
                val effectiveSavePath = when {
                    !isUserSavePathOverride -> computedSavePath
                    isRetroArch && effectiveEmulatorDef != null -> {
                        retroArchConfigParser.resolveSavePaths(
                            packageName = effectiveEmulatorDef.packageName,
                            systemName = platform.slug,
                            coreName = selectedCore,
                            basePathOverride = userSaveConfig?.savePathPattern
                        ).firstOrNull()
                    }
                    else -> userSaveConfig?.savePathPattern
                }

                PlatformEmulatorConfig(
                    platform = platform,
                    selectedEmulator = defaultConfig?.displayName,
                    selectedEmulatorPackage = defaultConfig?.packageName,
                    selectedCore = selectedCore,
                    isUserConfigured = isUserConfigured,
                    availableEmulators = available,
                    downloadableEmulators = downloadable,
                    availableCores = availableCores,
                    effectiveEmulatorIsRetroArch = isRetroArch,
                    effectiveEmulatorName = effectiveEmulatorDef?.displayName,
                    effectiveSavePath = effectiveSavePath,
                    isUserSavePathOverride = isUserSavePathOverride,
                    showSavePath = showSavePath
                )
            }

            val canAutoAssign = platformConfigs.any { !it.isUserConfigured && it.availableEmulators.isNotEmpty() }

            val connectionState = romMRepository.connectionState.value
            val connectionStatus = when {
                prefs.rommBaseUrl.isNullOrBlank() -> ConnectionStatus.NOT_CONFIGURED
                connectionState is RomMRepository.ConnectionState.Connected -> ConnectionStatus.ONLINE
                else -> ConnectionStatus.OFFLINE
            }
            val rommVersion = (connectionState as? RomMRepository.ConnectionState.Connected)?.version

            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()
            val availableSpace = gameRepository.getAvailableStorageBytes()

            displayDelegate.updateState(DisplayState(
                themeMode = prefs.themeMode,
                primaryColor = prefs.primaryColor,
                gridDensity = prefs.gridDensity,
                backgroundBlur = prefs.backgroundBlur,
                backgroundSaturation = prefs.backgroundSaturation,
                backgroundOpacity = prefs.backgroundOpacity,
                useGameBackground = prefs.useGameBackground,
                customBackgroundPath = prefs.customBackgroundPath,
                useAccentColorFooter = prefs.useAccentColorFooter,
                boxArtCornerRadius = prefs.boxArtCornerRadius,
                boxArtBorderThickness = prefs.boxArtBorderThickness,
                boxArtGlowStrength = prefs.boxArtGlowStrength,
                systemIconPosition = prefs.systemIconPosition,
                systemIconPadding = prefs.systemIconPadding,
                defaultView = prefs.defaultView
            ))

            controlsDelegate.updateState(ControlsState(
                hapticEnabled = prefs.hapticEnabled,
                hapticIntensity = prefs.hapticIntensity,
                swapAB = prefs.swapAB,
                swapXY = prefs.swapXY,
                abIconLayout = prefs.abIconLayout,
                detectedLayout = controlsDelegate.detectControllerLayout(),
                swapStartSelect = prefs.swapStartSelect,
                accuratePlayTimeEnabled = prefs.accuratePlayTimeEnabled
            ))
            controlsDelegate.refreshUsageStatsPermission()

            soundsDelegate.updateState(SoundState(
                enabled = prefs.soundEnabled,
                volume = prefs.soundVolume,
                soundConfigs = prefs.soundConfigs
            ))

            val ambientFileName = prefs.ambientAudioUri?.let { uri ->
                try {
                    android.net.Uri.parse(uri).let { parsedUri ->
                        context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    uri.substringAfterLast("/").substringBefore("?")
                }
            }
            ambientAudioDelegate.updateState(AmbientAudioState(
                enabled = prefs.ambientAudioEnabled,
                volume = prefs.ambientAudioVolume,
                audioUri = prefs.ambientAudioUri,
                audioFileName = ambientFileName
            ))

            val currentEmulatorState = emulatorDelegate.state.value
            emulatorDelegate.updateState(EmulatorState(
                platforms = platformConfigs,
                installedEmulators = installedEmulators,
                canAutoAssign = canAutoAssign,
                platformSubFocusIndex = currentEmulatorState.platformSubFocusIndex
            ))

            serverDelegate.updateState(ServerState(
                connectionStatus = connectionStatus,
                rommUrl = prefs.rommBaseUrl ?: "",
                rommUsername = prefs.rommUsername ?: "",
                rommVersion = rommVersion,
                lastRommSync = prefs.lastRommSync,
                syncScreenshotsEnabled = prefs.syncScreenshotsEnabled
            ))

            storageDelegate.updateState(StorageState(
                romStoragePath = prefs.romStoragePath ?: "",
                downloadedGamesSize = downloadedSize,
                downloadedGamesCount = downloadedCount,
                maxConcurrentDownloads = prefs.maxConcurrentDownloads,
                instantDownloadThresholdMb = prefs.instantDownloadThresholdMb,
                availableSpace = availableSpace
            ))
            storageDelegate.checkAllFilesAccess()
            storageDelegate.loadPlatformConfigs(viewModelScope)

            syncDelegate.updateState(SyncSettingsState(
                syncFilters = prefs.syncFilters,
                totalPlatforms = platforms.count { it.gameCount > 0 },
                totalGames = platforms.sumOf { it.gameCount },
                saveSyncEnabled = prefs.saveSyncEnabled,
                experimentalFolderSaveSync = prefs.experimentalFolderSaveSync,
                saveCacheLimit = prefs.saveCacheLimit,
                pendingUploadsCount = pendingSaveSyncDao.getCount(),
                imageCachePath = prefs.imageCachePath,
                defaultImageCachePath = imageCacheManager.getDefaultCachePath()
            ))

            _uiState.update {
                it.copy(
                    betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                    fileLoggingEnabled = prefs.fileLoggingEnabled,
                    fileLoggingPath = prefs.fileLoggingPath,
                    fileLogLevel = prefs.fileLogLevel
                )
            }

            soundManager.setVolume(prefs.soundVolume)

            permissionsDelegate.refreshPermissions()
        }
    }

    fun autoAssignAllEmulators() {
        emulatorDelegate.autoAssignAllEmulators(viewModelScope) { loadSettings() }
    }

    fun refreshEmulators() {
        emulatorDelegate.refreshEmulators()
        loadSettings()
    }

    fun checkStoragePermission() {
        storageDelegate.checkAllFilesAccess()
    }

    fun requestStoragePermission() {
        storageDelegate.requestAllFilesAccess(viewModelScope)
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig) {
        if (config.availableEmulators.isEmpty() && config.downloadableEmulators.isEmpty()) return
        emulatorDelegate.showEmulatorPicker(config)
    }

    fun dismissEmulatorPicker() {
        emulatorDelegate.dismissEmulatorPicker()
    }

    fun movePlatformSubFocus(delta: Int, maxIndex: Int): Boolean {
        return emulatorDelegate.movePlatformSubFocus(delta, maxIndex)
    }

    fun resetPlatformSubFocus() {
        emulatorDelegate.resetPlatformSubFocus()
    }

    fun cycleCoreForPlatform(config: PlatformEmulatorConfig, direction: Int) {
        emulatorDelegate.cycleCoreForPlatform(viewModelScope, config, direction) { loadSettings() }
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        emulatorDelegate.moveEmulatorPickerFocus(delta)
    }

    fun confirmEmulatorPickerSelection() {
        emulatorDelegate.confirmEmulatorPickerSelection(
            viewModelScope,
            onSetEmulator = { platformId, platformSlug, emulator -> setPlatformEmulator(platformId, platformSlug, emulator) },
            onLoadSettings = { loadSettings() }
        )
    }

    fun handleEmulatorPickerItemTap(index: Int) {
        emulatorDelegate.handleEmulatorPickerItemTap(
            index,
            viewModelScope,
            onSetEmulator = { platformId, platformSlug, emulator -> setPlatformEmulator(platformId, platformSlug, emulator) },
            onLoadSettings = { loadSettings() }
        )
    }

    fun setEmulatorSavePath(emulatorId: String, path: String) {
        emulatorDelegate.setEmulatorSavePath(viewModelScope, emulatorId, path) { loadSettings() }
    }

    fun resetEmulatorSavePath(emulatorId: String) {
        emulatorDelegate.resetEmulatorSavePath(viewModelScope, emulatorId) { loadSettings() }
    }

    fun showSavePathModal(config: PlatformEmulatorConfig) {
        val emulatorId = config.availableEmulators
            .find { it.def.displayName == config.selectedEmulator || it.def.displayName == config.effectiveEmulatorName }
            ?.def?.id ?: return
        emulatorDelegate.showSavePathModal(
            emulatorId = emulatorId,
            emulatorName = config.effectiveEmulatorName ?: config.selectedEmulator ?: "Unknown",
            platformName = config.platform.name,
            savePath = config.effectiveSavePath,
            isUserOverride = config.isUserSavePathOverride
        )
    }

    fun dismissSavePathModal() {
        emulatorDelegate.dismissSavePathModal()
    }

    fun moveSavePathModalFocus(delta: Int) {
        emulatorDelegate.moveSavePathModalFocus(delta)
    }

    fun moveSavePathModalButtonFocus(delta: Int) {
        emulatorDelegate.moveSavePathModalButtonFocus(delta)
    }

    fun confirmSavePathModalSelection() {
        val emulatorId = _uiState.value.emulators.savePathModalInfo?.emulatorId ?: return
        emulatorDelegate.confirmSavePathModalSelection(viewModelScope) {
            resetEmulatorSavePath(emulatorId)
        }
    }

    fun handlePlatformItemTap(index: Int) {
        val state = _uiState.value
        val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
        val actualIndex = index + focusOffset

        if (state.focusedIndex == actualIndex) {
            if (index == -1 && state.emulators.canAutoAssign) {
                autoAssignAllEmulators()
            } else {
                val config = state.emulators.platforms.getOrNull(index)
                if (config != null) {
                    showEmulatorPicker(config)
                }
            }
        } else {
            _uiState.update { it.copy(focusedIndex = actualIndex) }
        }
    }

    fun navigateToSection(section: SettingsSection) {
        _uiState.update { it.copy(currentSection = section, focusedIndex = 0) }
        when (section) {
            SettingsSection.EMULATORS -> refreshEmulators()
            SettingsSection.SERVER -> {
                serverDelegate.checkRommConnection(viewModelScope)
                syncDelegate.loadLibrarySettings(viewModelScope)
            }
            SettingsSection.SYNC_SETTINGS -> syncDelegate.loadLibrarySettings(viewModelScope)
            SettingsSection.STEAM_SETTINGS -> steamDelegate.loadSteamSettings(context, viewModelScope)
            SettingsSection.PERMISSIONS -> permissionsDelegate.refreshPermissions()
            else -> {}
        }
    }

    fun setFocusIndex(index: Int) {
        _uiState.update { it.copy(focusedIndex = index) }
    }

    fun refreshSteamSettings() {
        steamDelegate.loadSteamSettings(context, viewModelScope)
    }

    fun moveLauncherActionFocus(delta: Int) {
        val launcherIndex = getLauncherIndexFromFocus(_uiState.value)
        steamDelegate.moveLauncherActionFocus(delta, launcherIndex)
    }

    fun confirmLauncherAction() {
        val launcherIndex = getLauncherIndexFromFocus(_uiState.value)
        steamDelegate.confirmLauncherAction(context, viewModelScope, launcherIndex)
    }

    private fun getLauncherIndexFromFocus(state: SettingsUiState): Int {
        return when (state.currentSection) {
            SettingsSection.SERVER -> {
                val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                    state.server.connectionStatus == ConnectionStatus.OFFLINE
                // Android is at: 6/4/1, Steam starts at: 7/5/2
                val steamBaseIndex = when {
                    isConnected && state.syncSettings.saveSyncEnabled -> 7
                    isConnected -> 5
                    else -> 2
                }
                state.focusedIndex - steamBaseIndex
            }
            SettingsSection.STEAM_SETTINGS -> state.focusedIndex - 1
            else -> -1
        }
    }

    fun scanSteamLauncher(packageName: String) {
        steamDelegate.scanSteamLauncher(context, viewModelScope, packageName)
    }

    fun scanForAndroidGames() {
        if (_uiState.value.android.isScanning) return
        viewModelScope.launch {
            val result = androidGameScanner.scan()
            _uiState.update {
                it.copy(
                    android = it.android.copy(
                        lastScanGamesAdded = result.totalGames
                    )
                )
            }
        }
    }

    fun refreshSteamMetadata() {
        steamDelegate.refreshSteamMetadata(context, viewModelScope)
    }

    fun showAddSteamGameDialog(launcherPackage: String? = null) {
        steamDelegate.showAddSteamGameDialog(launcherPackage)
    }

    fun dismissAddSteamGameDialog() {
        steamDelegate.dismissAddSteamGameDialog()
    }

    fun setAddGameAppId(appId: String) {
        steamDelegate.setAddGameAppId(appId)
    }

    fun confirmAddSteamGame() {
        steamDelegate.confirmAddSteamGame(context, viewModelScope)
    }

    fun checkRommConnection() {
        serverDelegate.checkRommConnection(viewModelScope)
    }

    fun navigateBack(): Boolean {
        val state = _uiState.value
        return when {
            state.emulators.showSavePathModal -> {
                dismissSavePathModal()
                true
            }
            state.storage.platformSettingsModalId != null -> {
                closePlatformSettingsModal()
                true
            }
            state.steam.showAddGameDialog -> {
                dismissAddSteamGameDialog()
                true
            }
            state.sounds.showSoundPicker -> {
                dismissSoundPicker()
                true
            }
            state.syncSettings.showRegionPicker -> {
                dismissRegionPicker()
                true
            }
            state.emulators.showEmulatorPicker -> {
                dismissEmulatorPicker()
                true
            }
            state.server.rommConfiguring -> {
                cancelRommConfig()
                true
            }
            state.currentSection == SettingsSection.SYNC_FILTERS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.SYNC_SETTINGS, focusedIndex = 0) }
                true
            }
            state.currentSection == SettingsSection.SYNC_SETTINGS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = 1) }
                true
            }
            state.currentSection == SettingsSection.STEAM_SETTINGS -> {
                // Steam starts at index: 7 (with save sync) or 5 (without save sync)
                val steamIndex = if (_uiState.value.syncSettings.saveSyncEnabled) 7 else 5
                _uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = steamIndex) }
                true
            }
            state.currentSection == SettingsSection.BOX_ART -> {
                _uiState.update { it.copy(currentSection = SettingsSection.DISPLAY, focusedIndex = 3) }
                true
            }
            state.currentSection == SettingsSection.HOME_SCREEN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.DISPLAY, focusedIndex = 4) }
                true
            }
            state.currentSection != SettingsSection.MAIN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = 0) }
                true
            }
            else -> false
        }
    }

    fun moveFocus(delta: Int) {
        if (_uiState.value.emulators.showSavePathModal) {
            emulatorDelegate.moveSavePathModalFocus(delta)
            return
        }
        if (_uiState.value.storage.platformSettingsModalId != null) {
            storageDelegate.movePlatformSettingsFocus(delta)
            return
        }
        if (_uiState.value.sounds.showSoundPicker) {
            soundsDelegate.moveSoundPickerFocus(delta)
            return
        }
        if (_uiState.value.syncSettings.showRegionPicker) {
            syncDelegate.moveRegionPickerFocus(delta)
            return
        }
        if (_uiState.value.emulators.showEmulatorPicker) {
            emulatorDelegate.moveEmulatorPickerFocus(delta)
            return
        }
        _uiState.update { state ->
            val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                state.server.connectionStatus == ConnectionStatus.OFFLINE
            val maxIndex = when (state.currentSection) {
                SettingsSection.MAIN -> 7
                SettingsSection.SERVER -> if (state.server.rommConfiguring) {
                    4
                } else {
                    // Android is at index: 6/4/1 (after save games/tracking/romm)
                    // Steam starts at index: 7/5/2 (after Android)
                    val steamBaseIndex = when {
                        isConnected && state.syncSettings.saveSyncEnabled -> 7
                        isConnected -> 5
                        else -> 2
                    }
                    val launcherCount = state.steam.installedLaunchers.size
                    if (launcherCount > 0) steamBaseIndex + launcherCount else steamBaseIndex
                }
                SettingsSection.SYNC_SETTINGS -> if (state.syncSettings.saveSyncEnabled) 4 else 3
                SettingsSection.SYNC_FILTERS -> 6
                SettingsSection.STEAM_SETTINGS -> 2 + state.steam.installedLaunchers.size
                SettingsSection.STORAGE -> {
                    val baseItemCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 5 else 4
                    val platformCount = state.storage.platformConfigs.size
                    (baseItemCount + platformCount - 1).coerceAtLeast(baseItemCount - 1)
                }
                SettingsSection.DISPLAY -> 5
                SettingsSection.HOME_SCREEN -> if (state.display.useGameBackground) 4 else 5
                SettingsSection.BOX_ART -> {
                    val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
                    if (showIconPadding) 4 else 3
                }
                SettingsSection.CONTROLS -> if (state.controls.hapticEnabled) 4 else 3
                SettingsSection.SOUNDS -> {
                    // BGM first: toggle (0), volume (1), file (2) if enabled
                    val bgmItemCount = if (state.ambientAudio.enabled) 3 else 1
                    // UI Sounds after: toggle, volume, sound types if enabled
                    val uiSoundsItemCount = if (state.sounds.enabled) 2 + SoundType.entries.size else 1
                    bgmItemCount + uiSoundsItemCount - 1
                }
                SettingsSection.EMULATORS -> {
                    val platformCount = state.emulators.platforms.size
                    val autoAssignOffset = if (state.emulators.canAutoAssign) 1 else 0
                    (platformCount + autoAssignOffset - 1).coerceAtLeast(0)
                }
                SettingsSection.PERMISSIONS -> 1
                SettingsSection.ABOUT -> if (state.fileLoggingPath != null) 4 else 3
            }
            val newIndex = if (state.currentSection == SettingsSection.SERVER && state.server.rommConfiguring) {
                when {
                    delta > 0 && state.focusedIndex == 0 -> 1
                    delta > 0 && (state.focusedIndex == 1 || state.focusedIndex == 2) -> 3
                    delta < 0 && state.focusedIndex == 3 -> 1
                    delta < 0 && (state.focusedIndex == 1 || state.focusedIndex == 2) -> 0
                    else -> (state.focusedIndex + delta).coerceIn(0, maxIndex)
                }
            } else {
                (state.focusedIndex + delta).coerceIn(0, maxIndex)
            }
            state.copy(focusedIndex = newIndex)
        }
        if (_uiState.value.currentSection == SettingsSection.EMULATORS) {
            emulatorDelegate.resetPlatformSubFocus()
        }
    }

    fun moveColorFocus(delta: Int) {
        displayDelegate.moveColorFocus(delta)
        _uiState.update { it.copy(colorFocusIndex = displayDelegate.colorFocusIndex) }
    }

    fun selectFocusedColor() {
        displayDelegate.selectFocusedColor(viewModelScope)
    }

    fun setThemeMode(mode: com.nendo.argosy.data.preferences.ThemeMode) {
        displayDelegate.setThemeMode(viewModelScope, mode)
    }

    fun setPrimaryColor(color: Int?) {
        displayDelegate.setPrimaryColor(viewModelScope, color)
    }

    fun adjustHue(delta: Float) {
        displayDelegate.adjustHue(viewModelScope, delta)
    }

    fun resetToDefaultColor() {
        displayDelegate.resetToDefaultColor(viewModelScope)
    }

    fun setGridDensity(density: GridDensity) {
        displayDelegate.setGridDensity(viewModelScope, density)
    }

    fun adjustBackgroundBlur(delta: Int) {
        displayDelegate.adjustBackgroundBlur(viewModelScope, delta)
    }

    fun adjustBackgroundSaturation(delta: Int) {
        displayDelegate.adjustBackgroundSaturation(viewModelScope, delta)
    }

    fun adjustBackgroundOpacity(delta: Int) {
        displayDelegate.adjustBackgroundOpacity(viewModelScope, delta)
    }

    fun cycleBackgroundBlur() {
        val current = _uiState.value.display.backgroundBlur
        val next = if (current >= 100) 0 else current + 10
        displayDelegate.adjustBackgroundBlur(viewModelScope, next - current)
    }

    fun cycleBackgroundSaturation() {
        val current = _uiState.value.display.backgroundSaturation
        val next = if (current >= 100) 0 else current + 10
        displayDelegate.adjustBackgroundSaturation(viewModelScope, next - current)
    }

    fun cycleBackgroundOpacity() {
        val current = _uiState.value.display.backgroundOpacity
        val next = if (current >= 100) 0 else current + 10
        displayDelegate.adjustBackgroundOpacity(viewModelScope, next - current)
    }

    fun setUseGameBackground(use: Boolean) {
        displayDelegate.setUseGameBackground(viewModelScope, use)
    }

    fun setUseAccentColorFooter(use: Boolean) {
        displayDelegate.setUseAccentColorFooter(viewModelScope, use)
    }

    fun setCustomBackgroundPath(path: String?) {
        displayDelegate.setCustomBackgroundPath(viewModelScope, path)
    }

    fun openBackgroundPicker() {
        displayDelegate.openBackgroundPicker(viewModelScope)
    }

    fun navigateToBoxArt() {
        _uiState.update { it.copy(currentSection = SettingsSection.BOX_ART, focusedIndex = 0) }
    }

    fun navigateToHomeScreen() {
        _uiState.update { it.copy(currentSection = SettingsSection.HOME_SCREEN, focusedIndex = 0) }
    }

    fun cycleBoxArtCornerRadius(direction: Int = 1) {
        displayDelegate.cycleBoxArtCornerRadius(viewModelScope, direction)
    }

    fun cycleBoxArtBorderThickness(direction: Int = 1) {
        displayDelegate.cycleBoxArtBorderThickness(viewModelScope, direction)
    }

    fun cycleBoxArtGlowStrength(direction: Int = 1) {
        displayDelegate.cycleBoxArtGlowStrength(viewModelScope, direction)
    }

    fun cycleSystemIconPosition(direction: Int = 1) {
        displayDelegate.cycleSystemIconPosition(viewModelScope, direction)
    }

    fun cycleSystemIconPadding(direction: Int = 1) {
        displayDelegate.cycleSystemIconPadding(viewModelScope, direction)
    }

    fun cycleDefaultView() {
        displayDelegate.cycleDefaultView(viewModelScope)
    }

    fun cyclePrevPreviewRatio() {
        val values = BoxArtPreviewRatio.entries
        val currentIndex = values.indexOf(_uiState.value.boxArtPreviewRatio)
        val prevIndex = if (currentIndex <= 0) values.lastIndex else currentIndex - 1
        _uiState.update { it.copy(boxArtPreviewRatio = values[prevIndex]) }
    }

    fun cycleNextPreviewRatio() {
        val values = BoxArtPreviewRatio.entries
        val currentIndex = values.indexOf(_uiState.value.boxArtPreviewRatio)
        val nextIndex = if (currentIndex >= values.lastIndex) 0 else currentIndex + 1
        _uiState.update { it.copy(boxArtPreviewRatio = values[nextIndex]) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        controlsDelegate.setHapticEnabled(viewModelScope, enabled)
    }

    fun setHapticIntensity(intensity: com.nendo.argosy.data.preferences.HapticIntensity) {
        controlsDelegate.setHapticIntensity(viewModelScope, intensity)
    }

    fun cycleHapticIntensity() {
        controlsDelegate.cycleHapticIntensity(viewModelScope)
    }

    fun adjustHapticIntensity(delta: Int) {
        controlsDelegate.adjustHapticIntensity(viewModelScope, delta)
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundsDelegate.setSoundEnabled(viewModelScope, enabled)
    }

    fun setBetaUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBetaUpdatesEnabled(enabled)
            _uiState.update { it.copy(betaUpdatesEnabled = enabled) }
        }
    }

    fun setSoundVolume(volume: Int) {
        soundsDelegate.setSoundVolume(viewModelScope, volume)
    }

    fun adjustSoundVolume(delta: Int) {
        soundsDelegate.adjustSoundVolume(viewModelScope, delta)
    }

    fun showSoundPicker(type: SoundType) {
        soundsDelegate.showSoundPicker(type)
    }

    fun dismissSoundPicker() {
        soundsDelegate.dismissSoundPicker()
    }

    fun moveSoundPickerFocus(delta: Int) {
        soundsDelegate.moveSoundPickerFocus(delta)
    }

    fun previewSoundPickerSelection() {
        soundsDelegate.previewSoundPickerSelection()
    }

    fun confirmSoundPickerSelection() {
        soundsDelegate.confirmSoundPickerSelection(viewModelScope)
    }

    fun setCustomSoundFile(type: SoundType, filePath: String) {
        soundsDelegate.setCustomSoundFile(viewModelScope, type, filePath)
    }

    fun setAmbientAudioEnabled(enabled: Boolean) {
        ambientAudioDelegate.setEnabled(viewModelScope, enabled)
    }

    fun setAmbientAudioVolume(volume: Int) {
        ambientAudioDelegate.setVolume(viewModelScope, volume)
    }

    fun adjustAmbientAudioVolume(delta: Int) {
        ambientAudioDelegate.adjustVolume(viewModelScope, delta)
    }

    fun openAudioFilePicker() {
        ambientAudioDelegate.openFilePicker(viewModelScope)
    }

    fun openAudioFileBrowser() {
        ambientAudioDelegate.openFileBrowser(viewModelScope)
    }

    fun setAmbientAudioUri(uri: String?) {
        ambientAudioDelegate.setAudioUri(viewModelScope, uri)
    }

    fun setAmbientAudioFilePath(path: String?) {
        ambientAudioDelegate.setAudioFilePath(viewModelScope, path)
    }

    fun clearAmbientAudioFile() {
        ambientAudioDelegate.clearAudioFile(viewModelScope)
    }

    fun setSwapAB(enabled: Boolean) {
        controlsDelegate.setSwapAB(viewModelScope, enabled)
    }

    fun setSwapXY(enabled: Boolean) {
        controlsDelegate.setSwapXY(viewModelScope, enabled)
    }

    fun setABIconLayout(layout: String) {
        controlsDelegate.setABIconLayout(viewModelScope, layout)
    }

    fun cycleABIconLayout() {
        controlsDelegate.cycleABIconLayout(viewModelScope)
    }

    fun setSwapStartSelect(enabled: Boolean) {
        controlsDelegate.setSwapStartSelect(viewModelScope, enabled)
    }

    fun setAccuratePlayTimeEnabled(enabled: Boolean) {
        controlsDelegate.setAccuratePlayTimeEnabled(viewModelScope, enabled)
    }

    fun refreshUsageStatsPermission() {
        controlsDelegate.refreshUsageStatsPermission()
    }

    fun openUsageStatsSettings() {
        controlsDelegate.openUsageStatsSettings()
    }

    fun openStorageSettings() {
        permissionsDelegate.openStorageSettings()
    }

    fun refreshPermissions() {
        permissionsDelegate.refreshPermissions()
    }

    private fun handlePlayTimeToggle(controls: ControlsState) {
        val newEnabled = !controls.accuratePlayTimeEnabled
        if (newEnabled && !controls.hasUsageStatsPermission) {
            openUsageStatsSettings()
        } else {
            setAccuratePlayTimeEnabled(newEnabled)
        }
    }

    fun showRegionPicker() {
        syncDelegate.showRegionPicker()
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissRegionPicker() {
        syncDelegate.dismissRegionPicker()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveRegionPickerFocus(delta: Int) {
        syncDelegate.moveRegionPickerFocus(delta)
    }

    fun confirmRegionPickerSelection() {
        syncDelegate.confirmRegionPickerSelection(viewModelScope)
    }

    fun toggleRegion(region: String) {
        syncDelegate.toggleRegion(viewModelScope, region)
    }

    fun toggleRegionMode() {
        syncDelegate.toggleRegionMode(viewModelScope)
    }

    fun setExcludeBeta(exclude: Boolean) {
        syncDelegate.setExcludeBeta(viewModelScope, exclude)
    }

    fun setExcludePrototype(exclude: Boolean) {
        syncDelegate.setExcludePrototype(viewModelScope, exclude)
    }

    fun setExcludeDemo(exclude: Boolean) {
        syncDelegate.setExcludeDemo(viewModelScope, exclude)
    }

    fun setExcludeHack(exclude: Boolean) {
        syncDelegate.setExcludeHack(viewModelScope, exclude)
    }

    fun setDeleteOrphans(delete: Boolean) {
        syncDelegate.setDeleteOrphans(viewModelScope, delete)
    }

    fun toggleSyncScreenshots() {
        syncDelegate.toggleSyncScreenshots(viewModelScope, _uiState.value.server.syncScreenshotsEnabled)
    }

    fun enableSaveSync() {
        syncDelegate.enableSaveSync(viewModelScope)
    }

    fun toggleSaveSync() {
        syncDelegate.toggleSaveSync(viewModelScope)
    }

    fun toggleExperimentalFolderSaveSync() {
        syncDelegate.toggleExperimentalFolderSaveSync(viewModelScope)
    }

    fun cycleSaveCacheLimit() {
        syncDelegate.cycleSaveCacheLimit(viewModelScope)
    }

    fun onStoragePermissionResult(granted: Boolean) {
        syncDelegate.onStoragePermissionResult(viewModelScope, granted, _uiState.value.currentSection)
        steamDelegate.loadSteamSettings(context, viewModelScope)
    }

    fun runSaveSyncNow() {
        syncDelegate.runSaveSyncNow(viewModelScope)
    }

    fun openImageCachePicker() {
        syncDelegate.openImageCachePicker(viewModelScope)
    }

    fun moveImageCacheActionFocus(delta: Int) {
        syncDelegate.moveImageCacheActionFocus(delta)
    }

    fun setImageCachePath(path: String) {
        syncDelegate.onImageCachePathSelected(viewModelScope, path)
    }


    fun resetImageCacheToDefault() {
        syncDelegate.resetImageCacheToDefault(viewModelScope)
    }

    fun cycleMaxConcurrentDownloads() {
        storageDelegate.cycleMaxConcurrentDownloads(viewModelScope)
    }

    fun adjustMaxConcurrentDownloads(delta: Int) {
        storageDelegate.adjustMaxConcurrentDownloads(viewModelScope, delta)
    }

    fun cycleInstantDownloadThreshold() {
        storageDelegate.cycleInstantDownloadThreshold(viewModelScope)
    }

    fun openFolderPicker() {
        storageDelegate.openFolderPicker()
    }

    fun clearFolderPickerFlag() {
        storageDelegate.clearFolderPickerFlag()
    }

    fun setStoragePath(uriString: String) {
        storageDelegate.setStoragePath(uriString)
    }

    fun confirmMigration() {
        storageDelegate.confirmMigration(viewModelScope)
    }

    fun cancelMigration() {
        storageDelegate.cancelMigration()
    }

    fun skipMigration() {
        storageDelegate.skipMigration()
    }

    fun togglePlatformSync(platformId: Long, enabled: Boolean) {
        storageDelegate.togglePlatformSync(viewModelScope, platformId, enabled)
    }

    fun openPlatformFolderPicker(platformId: Long) {
        storageDelegate.openPlatformFolderPicker(viewModelScope, platformId)
    }

    fun setPlatformPath(platformId: Long, path: String) {
        storageDelegate.setPlatformPath(viewModelScope, platformId, path)
    }

    fun resetPlatformToGlobal(platformId: Long) {
        storageDelegate.resetPlatformToGlobal(viewModelScope, platformId)
    }

    fun syncPlatform(platformId: Long, platformName: String) {
        storageDelegate.syncPlatform(viewModelScope, platformId, platformName)
    }

    fun requestPurgePlatform(platformId: Long) {
        storageDelegate.requestPurgePlatform(platformId)
    }

    fun confirmPurgePlatform() {
        storageDelegate.confirmPurgePlatform(viewModelScope)
    }

    fun cancelPurgePlatform() {
        storageDelegate.cancelPurgePlatform()
    }

    fun confirmPlatformMigration() {
        storageDelegate.confirmPlatformMigration(viewModelScope)
    }

    fun cancelPlatformMigration() {
        storageDelegate.cancelPlatformMigration()
    }

    fun skipPlatformMigration() {
        storageDelegate.skipPlatformMigration(viewModelScope)
    }

    fun openPlatformSettingsModal(platformId: Long) {
        storageDelegate.openPlatformSettingsModal(platformId)
    }

    fun closePlatformSettingsModal() {
        storageDelegate.closePlatformSettingsModal()
    }

    fun movePlatformSettingsFocus(delta: Int) {
        storageDelegate.movePlatformSettingsFocus(delta)
    }

    fun selectPlatformSettingsOption() {
        storageDelegate.selectPlatformSettingsOption(viewModelScope)
    }

    fun openLogFolderPicker() {
        viewModelScope.launch {
            _openLogFolderPickerEvent.emit(Unit)
        }
    }

    fun setFileLoggingPath(path: String) {
        viewModelScope.launch {
            preferencesRepository.setFileLoggingPath(path)
            preferencesRepository.setFileLoggingEnabled(true)
        }
        _uiState.update { it.copy(fileLoggingEnabled = true, fileLoggingPath = path) }
    }

    fun toggleFileLogging(enabled: Boolean) {
        if (enabled && _uiState.value.fileLoggingPath == null) {
            openLogFolderPicker()
        } else {
            viewModelScope.launch {
                preferencesRepository.setFileLoggingEnabled(enabled)
            }
            _uiState.update { it.copy(fileLoggingEnabled = enabled) }
        }
    }

    fun setFileLogLevel(level: LogLevel) {
        viewModelScope.launch {
            preferencesRepository.setFileLogLevel(level)
        }
        _uiState.update { it.copy(fileLogLevel = level) }
    }

    fun cycleFileLogLevel() {
        val currentLevel = _uiState.value.fileLogLevel
        setFileLogLevel(currentLevel.next())
    }

    fun setPlatformEmulator(platformId: Long, platformSlug: String, emulator: InstalledEmulator?) {
        viewModelScope.launch {
            configureEmulatorUseCase.setForPlatform(platformId, platformSlug, emulator)
            loadSettings()
        }
    }

    fun setRomStoragePath(path: String) {
        storageDelegate.setRomStoragePath(viewModelScope, path)
    }

    fun syncRomm() {
        viewModelScope.launch {
            when (val result = syncLibraryUseCase()) {
                is SyncLibraryResult.Error -> notificationManager.showError(result.message)
                is SyncLibraryResult.Success -> loadSettings()
            }
        }
    }

    fun checkForUpdates() {
        if (com.nendo.argosy.BuildConfig.DEBUG) return

        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = it.updateCheck.copy(isChecking = true, error = null)) }

            when (val state = updateRepository.checkForUpdates()) {
                is UpdateState.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(
                            updateCheck = UpdateCheckState(
                                isChecking = false,
                                updateAvailable = true,
                                latestVersion = state.release.tagName,
                                downloadUrl = state.apkAsset.downloadUrl
                            )
                        )
                    }
                }
                is UpdateState.UpToDate -> {
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(isChecking = false, hasChecked = true, updateAvailable = false))
                    }
                }
                is UpdateState.Error -> {
                    _uiState.update {
                        it.copy(updateCheck = UpdateCheckState(isChecking = false, error = state.message))
                    }
                }
                else -> {
                    _uiState.update { it.copy(updateCheck = UpdateCheckState(isChecking = false)) }
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: android.content.Context) {
        val state = _uiState.value.updateCheck
        val url = state.downloadUrl ?: return
        val version = state.latestVersion ?: return

        if (state.isDownloading) return

        viewModelScope.launch {
            _uiState.update { it.copy(updateCheck = it.updateCheck.copy(isDownloading = true, downloadProgress = 0, error = null)) }

            try {
                val apkFile = withContext(Dispatchers.IO) {
                    downloadApk(context, url, version) { progress ->
                        _uiState.update { it.copy(updateCheck = it.updateCheck.copy(downloadProgress = progress)) }
                    }
                }

                _uiState.update {
                    it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, readyToInstall = true))
                }

                appInstaller.installApk(context, apkFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download update", e)
                _uiState.update {
                    it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, error = e.message ?: "Download failed"))
                }
            }
        }
    }

    private fun downloadApk(
        context: android.content.Context,
        url: String,
        version: String,
        onProgress: (Int) -> Unit
    ): File {
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response")
        val contentLength = body.contentLength()
        val apkFile = appInstaller.getApkCacheFile(context, version)

        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Long = 0
                var read: Int

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        val progress = ((bytesRead * 100) / contentLength).toInt()
                        onProgress(progress)
                    }
                }
            }
        }

        return apkFile
    }

    fun startRommConfig() {
        serverDelegate.startRommConfig { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun cancelRommConfig() {
        serverDelegate.cancelRommConfig { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun setRommConfigUrl(url: String) {
        serverDelegate.setRommConfigUrl(url)
    }

    fun setRommConfigUsername(username: String) {
        serverDelegate.setRommConfigUsername(username)
    }

    fun setRommConfigPassword(password: String) {
        serverDelegate.setRommConfigPassword(password)
    }

    fun clearRommFocusField() {
        serverDelegate.clearRommFocusField()
    }

    fun setRommConfigFocusIndex(index: Int) {
        _uiState.update { it.copy(focusedIndex = index) }
    }

    fun connectToRomm() {
        serverDelegate.connectToRomm(viewModelScope) { loadSettings() }
    }

    fun handleConfirm(): InputResult {
        val state = _uiState.value
        return when (state.currentSection) {
            SettingsSection.MAIN -> {
                val section = when (state.focusedIndex) {
                    0 -> SettingsSection.SERVER
                    1 -> SettingsSection.STORAGE
                    2 -> SettingsSection.DISPLAY
                    3 -> SettingsSection.CONTROLS
                    4 -> SettingsSection.SOUNDS
                    5 -> SettingsSection.EMULATORS
                    6 -> SettingsSection.PERMISSIONS
                    7 -> SettingsSection.ABOUT
                    else -> null
                }
                section?.let { navigateToSection(it) }
                InputResult.HANDLED
            }
            SettingsSection.SERVER -> {
                val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                    state.server.connectionStatus == ConnectionStatus.OFFLINE
                val isOnline = state.server.connectionStatus == ConnectionStatus.ONLINE
                if (state.server.rommConfiguring) {
                    when (state.focusedIndex) {
                        0, 1, 2 -> _uiState.update { it.copy(server = it.server.copy(rommFocusField = state.focusedIndex)) }
                        3 -> connectToRomm()
                        4 -> cancelRommConfig()
                    }
                } else {
                    // Indices: 0=RomM, 1=SyncSettings, 2=SyncLibrary, 3=AccuratePlayTime, 4=SaveCache, 5=SyncSaves, 6=Android, 7+=Steam
                    val androidBaseIndex = when {
                        isConnected && state.syncSettings.saveSyncEnabled -> 6
                        isConnected -> 4
                        else -> 1
                    }
                    val steamBaseIndex = androidBaseIndex + 1
                    val launcherCount = state.steam.installedLaunchers.size
                    val refreshIndex = steamBaseIndex + launcherCount
                    when {
                        state.focusedIndex == 0 -> startRommConfig()
                        state.focusedIndex == 1 && isConnected -> navigateToSection(SettingsSection.SYNC_SETTINGS)
                        state.focusedIndex == 2 && isConnected && isOnline -> syncRomm()
                        state.focusedIndex == 3 && isConnected -> {
                            val hasPermission = state.controls.hasUsageStatsPermission
                            if (!state.controls.accuratePlayTimeEnabled && !hasPermission) {
                                openUsageStatsSettings()
                            } else {
                                setAccuratePlayTimeEnabled(!state.controls.accuratePlayTimeEnabled)
                            }
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        state.focusedIndex == 4 && isConnected && state.syncSettings.saveSyncEnabled -> cycleSaveCacheLimit()
                        state.focusedIndex == 5 && isConnected && state.syncSettings.saveSyncEnabled && isOnline -> runSaveSyncNow()
                        state.focusedIndex == androidBaseIndex -> scanForAndroidGames()
                        state.focusedIndex >= steamBaseIndex && state.focusedIndex < refreshIndex -> {
                            if (state.steam.hasStoragePermission && !state.steam.isSyncing) {
                                confirmLauncherAction()
                            }
                        }
                        state.focusedIndex == refreshIndex && launcherCount > 0 && !state.steam.isSyncing -> {
                            refreshSteamMetadata()
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.STEAM_SETTINGS -> {
                val refreshIndex = 1 + state.steam.installedLaunchers.size
                when {
                    state.focusedIndex == 0 && !state.steam.hasStoragePermission -> {
                        viewModelScope.launch { _requestStoragePermissionEvent.emit(Unit) }
                    }
                    state.focusedIndex == refreshIndex && !state.steam.isSyncing -> {
                        refreshSteamMetadata()
                    }
                    state.focusedIndex > 0 && state.focusedIndex < refreshIndex && state.steam.hasStoragePermission && !state.steam.isSyncing -> {
                        confirmLauncherAction()
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.SYNC_SETTINGS -> {
                when (state.focusedIndex) {
                    0 -> navigateToSection(SettingsSection.SYNC_FILTERS)
                    1 -> { toggleSyncScreenshots(); return InputResult.handled(SoundType.TOGGLE) }
                    2 -> {
                        if (!state.syncSettings.isImageCacheMigrating) {
                            val actionIndex = state.syncSettings.imageCacheActionIndex
                            if (actionIndex == 0) {
                                openImageCachePicker()
                            } else {
                                resetImageCacheToDefault()
                            }
                        }
                    }
                    3 -> {
                        if (state.syncSettings.saveSyncEnabled) {
                            toggleSaveSync()
                            return InputResult.handled(SoundType.TOGGLE)
                        } else {
                            enableSaveSync()
                        }
                    }
                    4 -> {
                        if (state.syncSettings.saveSyncEnabled) {
                            toggleExperimentalFolderSaveSync()
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.SYNC_FILTERS -> {
                when (state.focusedIndex) {
                    0 -> showRegionPicker()
                    1 -> toggleRegionMode()
                    2 -> { setExcludeBeta(!state.syncSettings.syncFilters.excludeBeta); return InputResult.handled(SoundType.TOGGLE) }
                    3 -> { setExcludePrototype(!state.syncSettings.syncFilters.excludePrototype); return InputResult.handled(SoundType.TOGGLE) }
                    4 -> { setExcludeDemo(!state.syncSettings.syncFilters.excludeDemo); return InputResult.handled(SoundType.TOGGLE) }
                    5 -> { setExcludeHack(!state.syncSettings.syncFilters.excludeHack); return InputResult.handled(SoundType.TOGGLE) }
                    6 -> { setDeleteOrphans(!state.syncSettings.syncFilters.deleteOrphans); return InputResult.handled(SoundType.TOGGLE) }
                }
                InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                val hasPermissionRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                val baseItemCount = if (hasPermissionRow) 5 else 4
                val folderPickerIndex = if (hasPermissionRow) 1 else 0
                val sliderIndex = if (hasPermissionRow) 2 else 1
                val thresholdIndex = if (hasPermissionRow) 3 else 2
                when {
                    state.focusedIndex == 0 && hasPermissionRow && !state.storage.hasAllFilesAccess -> requestStoragePermission()
                    state.focusedIndex == folderPickerIndex -> openFolderPicker()
                    state.focusedIndex == sliderIndex -> cycleMaxConcurrentDownloads()
                    state.focusedIndex == thresholdIndex -> cycleInstantDownloadThreshold()
                    state.focusedIndex >= baseItemCount -> {
                        val platformIndex = state.focusedIndex - baseItemCount
                        val config = state.storage.platformConfigs.getOrNull(platformIndex)
                        config?.let { openPlatformSettingsModal(it.platformId) }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.DISPLAY -> {
                when (state.focusedIndex) {
                    0 -> {
                        val next = when (state.display.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        setThemeMode(next)
                    }
                    2 -> {
                        val next = when (state.display.gridDensity) {
                            GridDensity.COMPACT -> GridDensity.NORMAL
                            GridDensity.NORMAL -> GridDensity.SPACIOUS
                            GridDensity.SPACIOUS -> GridDensity.COMPACT
                        }
                        setGridDensity(next)
                    }
                    3 -> navigateToBoxArt()
                    4 -> navigateToHomeScreen()
                    5 -> cycleDefaultView()
                }
                InputResult.HANDLED
            }
            SettingsSection.HOME_SCREEN -> {
                val sliderOffset = if (state.display.useGameBackground) 0 else 1
                when (state.focusedIndex) {
                    0 -> {
                        setUseGameBackground(!state.display.useGameBackground)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    1 -> {
                        if (!state.display.useGameBackground) {
                            openBackgroundPicker()
                        }
                    }
                    1 + sliderOffset -> cycleBackgroundBlur()
                    2 + sliderOffset -> cycleBackgroundSaturation()
                    3 + sliderOffset -> cycleBackgroundOpacity()
                    4 + sliderOffset -> {
                        setUseAccentColorFooter(!state.display.useAccentColorFooter)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.BOX_ART -> {
                val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
                when (state.focusedIndex) {
                    0 -> cycleBoxArtCornerRadius()
                    1 -> cycleBoxArtBorderThickness()
                    2 -> cycleBoxArtGlowStrength()
                    3 -> cycleSystemIconPosition()
                    4 -> if (showIconPadding) cycleSystemIconPadding()
                }
                InputResult.HANDLED
            }
            SettingsSection.CONTROLS -> {
                val isToggle = if (state.controls.hapticEnabled) {
                    when (state.focusedIndex) {
                        0 -> { setHapticEnabled(!state.controls.hapticEnabled); true }
                        1 -> { cycleHapticIntensity(); false }
                        2 -> { setSwapAB(!state.controls.swapAB); true }
                        3 -> { setSwapXY(!state.controls.swapXY); true }
                        4 -> { setSwapStartSelect(!state.controls.swapStartSelect); true }
                        5 -> { handlePlayTimeToggle(state.controls); true }
                        else -> false
                    }
                } else {
                    when (state.focusedIndex) {
                        0 -> { setHapticEnabled(!state.controls.hapticEnabled); true }
                        1 -> { setSwapAB(!state.controls.swapAB); true }
                        2 -> { setSwapXY(!state.controls.swapXY); true }
                        3 -> { setSwapStartSelect(!state.controls.swapStartSelect); true }
                        4 -> { handlePlayTimeToggle(state.controls); true }
                        else -> false
                    }
                }
                if (isToggle) InputResult.handled(SoundType.TOGGLE) else InputResult.HANDLED
            }
            SettingsSection.SOUNDS -> {
                // BGM first: 0=toggle, 1=volume, 2=file (if enabled)
                // UI Sounds after: uiSoundsToggleIndex=toggle, +1=volume, +2+=sound types
                val bgmItemCount = if (state.ambientAudio.enabled) 3 else 1
                val uiSoundsToggleIndex = bgmItemCount
                when {
                    state.focusedIndex == 0 -> {
                        setAmbientAudioEnabled(!state.ambientAudio.enabled)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    state.focusedIndex == 2 && state.ambientAudio.enabled -> {
                        openAudioFilePicker()
                    }
                    state.focusedIndex == uiSoundsToggleIndex -> {
                        setSoundEnabled(!state.sounds.enabled)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    state.focusedIndex >= uiSoundsToggleIndex + 2 && state.sounds.enabled -> {
                        val soundIndex = state.focusedIndex - uiSoundsToggleIndex - 2
                        val soundType = SoundType.entries.getOrNull(soundIndex)
                        soundType?.let { showSoundPicker(it) }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.EMULATORS -> {
                val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
                if (state.emulators.canAutoAssign && state.focusedIndex == 0) {
                    autoAssignAllEmulators()
                } else {
                    val platformIndex = state.focusedIndex - focusOffset
                    val config = state.emulators.platforms.getOrNull(platformIndex)
                    if (config != null) {
                        showEmulatorPicker(config)
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.PERMISSIONS -> {
                when (state.focusedIndex) {
                    0 -> openStorageSettings()
                    1 -> openUsageStatsSettings()
                }
                InputResult.HANDLED
            }
            SettingsSection.ABOUT -> {
                when (state.focusedIndex) {
                    1 -> {
                        if (state.updateCheck.updateAvailable) {
                            viewModelScope.launch { _downloadUpdateEvent.emit(Unit) }
                        } else {
                            checkForUpdates()
                        }
                    }
                    2 -> {
                        setBetaUpdatesEnabled(!state.betaUpdatesEnabled)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    3 -> {
                        if (state.fileLoggingPath != null) {
                            toggleFileLogging(!state.fileLoggingEnabled)
                        } else {
                            openLogFolderPicker()
                        }
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    4 -> {
                        if (state.fileLoggingPath != null) {
                            cycleFileLogLevel()
                        }
                    }
                }
                InputResult.HANDLED
            }
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler =
        SettingsInputHandler(this, onBack)
}
