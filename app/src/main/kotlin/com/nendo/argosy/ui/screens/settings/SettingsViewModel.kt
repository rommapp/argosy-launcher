package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.BitmapFactory
import com.nendo.argosy.data.cache.GradientColorExtractor
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientPreset
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
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.ui.screens.settings.delegates.AmbientAudioSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.BiosSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.EmulatorSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.PermissionsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.RASettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ServerSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SoundSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SteamSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.StorageSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.ui.screens.settings.sections.aboutMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.boxArtMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.controlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.emulatorsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.homeScreenMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.permissionsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageMaxFocusIndex
import com.nendo.argosy.ui.ModalResetSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private val hapticManager: HapticFeedbackManager,
    private val platformDao: PlatformDao,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
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
    val raDelegate: RASettingsDelegate,
    val permissionsDelegate: PermissionsSettingsDelegate,
    val biosDelegate: BiosSettingsDelegate,
    private val androidGameScanner: AndroidGameScanner,
    private val modalResetSignal: ModalResetSignal,
    private val gradientColorExtractor: GradientColorExtractor,
    private val coreManager: LibretroCoreManager,
    private val inputConfigRepository: com.nendo.argosy.data.repository.InputConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _downloadUpdateEvent = MutableSharedFlow<Unit>()
    val downloadUpdateEvent: SharedFlow<Unit> = _downloadUpdateEvent.asSharedFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    private val _requestScreenCapturePermissionEvent = MutableSharedFlow<Unit>()
    val requestScreenCapturePermissionEvent: SharedFlow<Unit> = _requestScreenCapturePermissionEvent.asSharedFlow()

    val imageCacheProgress: StateFlow<ImageCacheProgress> = imageCacheManager.progress

    val openBackgroundPickerEvent: SharedFlow<Unit> = displayDelegate.openBackgroundPickerEvent
    val openCustomSoundPickerEvent: SharedFlow<SoundType> = soundsDelegate.openCustomSoundPickerEvent
    val openAudioFilePickerEvent: SharedFlow<Unit> = ambientAudioDelegate.openAudioFilePickerEvent
    val openAudioFileBrowserEvent: SharedFlow<Unit> = ambientAudioDelegate.openAudioFileBrowserEvent
    val launchPlatformFolderPicker: SharedFlow<Long> = storageDelegate.launchPlatformFolderPicker
    val launchSavePathPicker: SharedFlow<Unit> = emulatorDelegate.launchSavePathPicker
    val builtinNavigationEvent = emulatorDelegate.builtinNavigationEvent
    val launchPlatformSavePathPicker: SharedFlow<Long> = storageDelegate.launchSavePathPicker
    val resetPlatformSavePathEvent: SharedFlow<Long> = storageDelegate.resetSavePathEvent
    val launchPlatformStatePathPicker: SharedFlow<Long> = storageDelegate.launchStatePathPicker
    val resetPlatformStatePathEvent: SharedFlow<Long> = storageDelegate.resetStatePathEvent
    val openImageCachePickerEvent: SharedFlow<Unit> = syncDelegate.openImageCachePickerEvent
    val launchBiosFolderPicker: SharedFlow<Unit> = biosDelegate.launchFolderPicker

    private val _openLogFolderPickerEvent = MutableSharedFlow<Unit>()
    val openLogFolderPickerEvent: SharedFlow<Unit> = _openLogFolderPickerEvent.asSharedFlow()

    private val _openDeviceSettingsEvent = MutableSharedFlow<Unit>()
    val openDeviceSettingsEvent: SharedFlow<Unit> = _openDeviceSettingsEvent.asSharedFlow()

    init {
        observeDelegateStates()
        observeDelegateEvents()
        observeModalResetSignal()
        observeConnectionState()
        observePlatformLibretroSettings()
        loadAvailablePlatformsForLibretro()
        loadSettings()
        raDelegate.initialize(viewModelScope)
        displayDelegate.loadPreviewGame(viewModelScope)
        displayDelegate.observeScreenCapturePermission(viewModelScope)
        startControllerDetectionPolling()

        // TODO: Remove after testing manage=true Android/data access
        storageDelegate.testManagedStorageAccess(viewModelScope)
    }

    private fun startControllerDetectionPolling() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                controlsDelegate.refreshDetectedLayout()
            }
        }
    }

    private fun observeConnectionState() {
        romMRepository.connectionState.onEach { connectionState ->
            val status = when (connectionState) {
                is RomMRepository.ConnectionState.Connected -> ConnectionStatus.ONLINE
                else -> {
                    val prefs = preferencesRepository.userPreferences.first()
                    if (prefs.rommBaseUrl.isNullOrBlank()) ConnectionStatus.NOT_CONFIGURED
                    else ConnectionStatus.OFFLINE
                }
            }
            val version = (connectionState as? RomMRepository.ConnectionState.Connected)?.version
            serverDelegate.updateState(_uiState.value.server.copy(
                connectionStatus = status,
                rommVersion = version
            ))
        }.launchIn(viewModelScope)
    }

    private fun observePlatformLibretroSettings() {
        platformLibretroSettingsDao.observeAll().onEach { settingsList ->
            val settingsMap = settingsList.associateBy { it.platformId }
            _uiState.update { current ->
                current.copy(
                    platformLibretro = current.platformLibretro.copy(
                        platformSettings = settingsMap
                    )
                )
            }
        }.launchIn(viewModelScope)
    }

    private fun loadAvailablePlatformsForLibretro() {
        viewModelScope.launch {
            val platforms = platformDao.getAllPlatformsOrdered()
                .filter { it.syncEnabled && LibretroCoreRegistry.isPlatformSupported(it.slug) }
                .map { PlatformContext(it.id, it.name, it.slug) }
            _uiState.update {
                it.copy(builtinVideo = it.builtinVideo.copy(availablePlatforms = platforms))
            }
        }
    }

    fun cyclePlatformContext(direction: Int) {
        val state = _uiState.value.builtinVideo
        val maxIndex = state.availablePlatforms.size
        val newIndex = (state.platformContextIndex + direction).mod(maxIndex + 1)
        _uiState.update {
            it.copy(
                builtinVideo = it.builtinVideo.copy(platformContextIndex = newIndex),
                focusedIndex = 0
            )
        }
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
            _uiState.update { current ->
                val newConfig = if (display.gradientPreset != GradientPreset.CUSTOM &&
                    current.display.gradientPreset != display.gradientPreset) {
                    display.gradientPreset.toConfig()
                } else {
                    current.gradientConfig
                }
                current.copy(
                    display = display,
                    colorFocusIndex = displayDelegate.colorFocusIndex,
                    gradientConfig = newConfig
                )
            }
        }.launchIn(viewModelScope)

        displayDelegate.previewGame.onEach { previewGame ->
            _uiState.update { it.copy(previewGame = previewGame) }
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

        emulatorDelegate.observeCoreUpdateCount().onEach { count ->
            emulatorDelegate.updateCoreUpdatesAvailable(count)
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

        raDelegate.state.onEach { ra ->
            _uiState.update { it.copy(retroAchievements = ra) }
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

        biosDelegate.state.onEach { bios ->
            _uiState.update { it.copy(bios = bios) }
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

                val storedCore = defaultConfig?.coreName
                val defaultCore = EmulatorRegistry.getDefaultCore(platform.slug)?.id
                val selectedCore = when {
                    !isRetroArch -> null
                    storedCore != null && availableCores.any { it.id == storedCore } -> storedCore
                    else -> defaultCore ?: availableCores.firstOrNull()?.id
                }

                val emulatorId = effectiveEmulatorDef?.id
                val emulatorPackage = effectiveEmulatorDef?.packageName
                val savePathConfig = emulatorPackage?.let { SavePathRegistry.getConfigByPackage(it) }
                    ?: emulatorId?.let { SavePathRegistry.getConfig(it) }
                val showSavePath = savePathConfig != null
                val effectiveSaveConfigId = savePathConfig?.emulatorId

                val computedSavePath = when {
                    savePathConfig == null -> null
                    isRetroArch -> {
                        retroArchConfigParser.resolveSavePaths(
                            packageName = emulatorPackage ?: "com.retroarch",
                            systemName = platform.slug,
                            coreName = selectedCore
                        ).firstOrNull()
                    }
                    else -> savePathConfig.defaultPaths.firstOrNull()
                }

                val userSaveConfig = effectiveSaveConfigId?.let { emulatorDelegate.getEmulatorSaveConfig(it) }
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

                val extensionOptions = EmulatorRegistry.getExtensionOptionsForPlatform(platform.slug)
                val selectedExtension = emulatorDelegate.getPreferredExtension(platform.id)

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
                    effectiveEmulatorId = emulatorId,
                    effectiveEmulatorPackage = effectiveEmulatorDef?.packageName,
                    effectiveEmulatorName = effectiveEmulatorDef?.displayName,
                    effectiveSavePath = effectiveSavePath,
                    isUserSavePathOverride = isUserSavePathOverride,
                    showSavePath = showSavePath,
                    extensionOptions = extensionOptions,
                    selectedExtension = selectedExtension
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
                secondaryColor = prefs.secondaryColor,
                gridDensity = prefs.gridDensity,
                backgroundBlur = prefs.backgroundBlur,
                backgroundSaturation = prefs.backgroundSaturation,
                backgroundOpacity = prefs.backgroundOpacity,
                useGameBackground = prefs.useGameBackground,
                customBackgroundPath = prefs.customBackgroundPath,
                useAccentColorFooter = prefs.useAccentColorFooter,
                boxArtShape = prefs.boxArtShape,
                boxArtCornerRadius = prefs.boxArtCornerRadius,
                boxArtBorderThickness = prefs.boxArtBorderThickness,
                boxArtBorderStyle = prefs.boxArtBorderStyle,
                glassBorderTint = prefs.glassBorderTint,
                boxArtGlowStrength = prefs.boxArtGlowStrength,
                boxArtOuterEffect = prefs.boxArtOuterEffect,
                boxArtOuterEffectThickness = prefs.boxArtOuterEffectThickness,
                boxArtInnerEffect = prefs.boxArtInnerEffect,
                boxArtInnerEffectThickness = prefs.boxArtInnerEffectThickness,
                gradientPreset = prefs.gradientPreset,
                gradientAdvancedMode = prefs.gradientAdvancedMode,
                systemIconPosition = prefs.systemIconPosition,
                systemIconPadding = prefs.systemIconPadding,
                defaultView = prefs.defaultView,
                videoWallpaperEnabled = prefs.videoWallpaperEnabled,
                videoWallpaperDelaySeconds = prefs.videoWallpaperDelaySeconds,
                videoWallpaperMuted = prefs.videoWallpaperMuted,
                uiScale = prefs.uiScale,
                ambientLedEnabled = prefs.ambientLedEnabled,
                ambientLedBrightness = prefs.ambientLedBrightness,
                ambientLedAudioBrightness = prefs.ambientLedAudioBrightness,
                ambientLedAudioColors = prefs.ambientLedAudioColors,
                ambientLedColorMode = prefs.ambientLedColorMode,
                ambientLedAvailable = displayDelegate.isAmbientLedAvailable(),
                hasScreenCapturePermission = displayDelegate.hasScreenCapturePermission()
            ))

            val detectionResult = ControllerDetector.detectFromActiveGamepad()
            val detectedLayoutName = when (detectionResult.layout) {
                DetectedLayout.XBOX -> "Xbox"
                DetectedLayout.NINTENDO -> "Nintendo"
                null -> null
            }
            controlsDelegate.updateState(ControlsState(
                hapticEnabled = prefs.hapticEnabled,
                vibrationStrength = controlsDelegate.getVibrationStrength(),
                vibrationSupported = controlsDelegate.supportsSystemVibration,
                controllerLayout = prefs.controllerLayout,
                detectedLayout = detectedLayoutName,
                detectedDeviceName = detectionResult.deviceName,
                swapAB = prefs.swapAB,
                swapXY = prefs.swapXY,
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
            emulatorDelegate.updateCoreCounts()

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
                availableSpace = availableSpace,
                screenDimmerEnabled = prefs.screenDimmerEnabled,
                screenDimmerTimeoutMinutes = prefs.screenDimmerTimeoutMinutes,
                screenDimmerLevel = prefs.screenDimmerLevel
            ))
            storageDelegate.checkAllFilesAccess()
            val platformEmulatorInfoMap = platformConfigs.associate { config ->
                val statePath = if (config.effectiveEmulatorIsRetroArch) {
                    config.effectiveEmulatorPackage?.let { pkg ->
                        retroArchConfigParser.resolveStatePaths(
                            packageName = pkg,
                            coreName = config.selectedCore
                        ).firstOrNull()
                    }
                } else null

                config.platform.id to StorageSettingsDelegate.PlatformEmulatorInfo(
                    supportsStatePath = config.effectiveEmulatorIsRetroArch,
                    emulatorId = config.effectiveEmulatorId,
                    effectiveSavePath = config.effectiveSavePath,
                    isUserSavePathOverride = config.isUserSavePathOverride,
                    effectiveStatePath = statePath,
                    isUserStatePathOverride = false
                )
            }
            storageDelegate.setPendingEmulatorInfo(platformEmulatorInfoMap)
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

            val builtinSettings = preferencesRepository.getBuiltinEmulatorSettings().first()
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val refreshRate = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
            _uiState.update {
                it.copy(
                    betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                    fileLoggingEnabled = prefs.fileLoggingEnabled,
                    fileLoggingPath = prefs.fileLoggingPath,
                    fileLogLevel = prefs.fileLogLevel,
                    builtinVideo = it.builtinVideo.copy(
                        shader = builtinSettings.shader,
                        filter = builtinSettings.filter,
                        aspectRatio = builtinSettings.aspectRatio,
                        skipDuplicateFrames = builtinSettings.skipDuplicateFrames,
                        blackFrameInsertion = builtinSettings.blackFrameInsertion,
                        displayRefreshRate = refreshRate,
                        fastForwardSpeed = builtinSettings.fastForwardSpeedDisplay,
                        rotation = builtinSettings.rotationDisplay,
                        overscanCrop = builtinSettings.overscanCropDisplay,
                        lowLatencyAudio = builtinSettings.lowLatencyAudio,
                        rewindEnabled = builtinSettings.rewindEnabled
                    ),
                    builtinControls = BuiltinControlsState(
                        rumbleEnabled = builtinSettings.rumbleEnabled,
                        limitHotkeysToPlayer1 = builtinSettings.limitHotkeysToPlayer1,
                        analogAsDpad = builtinSettings.analogAsDpad,
                        dpadAsAnalog = builtinSettings.dpadAsAnalog
                    )
                )
            }

            soundManager.setVolume(prefs.soundVolume)

            permissionsDelegate.refreshPermissions()
            biosDelegate.init(viewModelScope)
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

    fun navigateToBuiltinVideo() {
        emulatorDelegate.navigateToBuiltinVideo(viewModelScope)
    }

    fun navigateToBuiltinControls() {
        emulatorDelegate.navigateToBuiltinControls(viewModelScope)
    }

    fun navigateToCoreManagement() {
        emulatorDelegate.navigateToCoreManagement(viewModelScope)
    }

    fun getInstalledCoreIds(): Set<String> {
        return coreManager.getInstalledCores().map { it.coreId }.toSet()
    }

    fun downloadCore(coreId: String) {
        viewModelScope.launch {
            coreManager.downloadCoreById(coreId)
            emulatorDelegate.updateCoreCounts()
        }
    }

    fun deleteCore(coreId: String) {
        viewModelScope.launch {
            coreManager.deleteCore(coreId)
            emulatorDelegate.updateCoreCounts()
        }
    }

    fun setBuiltinShader(value: String) {
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(shader = value)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinShader(value)
        }
    }

    fun setBuiltinFilter(value: String) {
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(filter = value)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinFilter(value)
        }
    }

    fun setBuiltinAspectRatio(value: String) {
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(aspectRatio = value)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinAspectRatio(value)
        }
    }

    fun setBuiltinSkipDuplicateFrames(enabled: Boolean) {
        _uiState.update {
            it.copy(builtinVideo = it.builtinVideo.copy(
                skipDuplicateFrames = enabled,
                blackFrameInsertion = if (enabled) false else it.builtinVideo.blackFrameInsertion
            ))
        }
        viewModelScope.launch {
            preferencesRepository.setBuiltinSkipDuplicateFrames(enabled)
            if (enabled) preferencesRepository.setBuiltinBlackFrameInsertion(false)
        }
    }

    fun setBuiltinLowLatencyAudio(enabled: Boolean) {
        _uiState.update {
            it.copy(builtinVideo = it.builtinVideo.copy(
                lowLatencyAudio = enabled,
                blackFrameInsertion = if (enabled) false else it.builtinVideo.blackFrameInsertion
            ))
        }
        viewModelScope.launch {
            preferencesRepository.setBuiltinLowLatencyAudio(enabled)
            if (enabled) preferencesRepository.setBuiltinBlackFrameInsertion(false)
        }
    }

    fun setBuiltinRewindEnabled(enabled: Boolean) {
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rewindEnabled = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinRewindEnabled(enabled)
        }
    }

    fun setBuiltinRumbleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(rumbleEnabled = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinRumbleEnabled(enabled)
        }
    }

    fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(limitHotkeysToPlayer1 = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinLimitHotkeysToPlayer1(enabled)
        }
    }

    fun setBuiltinAnalogAsDpad(enabled: Boolean) {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(analogAsDpad = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinAnalogAsDpad(enabled)
        }
    }

    fun setBuiltinDpadAsAnalog(enabled: Boolean) {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(dpadAsAnalog = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinDpadAsAnalog(enabled)
        }
    }

    fun showControllerOrderModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showControllerOrderModal = true)) }
    }

    fun hideControllerOrderModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showControllerOrderModal = false)) }
    }

    fun assignControllerToPort(port: Int, device: android.view.InputDevice) {
        viewModelScope.launch {
            inputConfigRepository.assignControllerToPort(port, device)
            updateControllerOrderCount()
        }
    }

    fun clearControllerOrder() {
        viewModelScope.launch {
            inputConfigRepository.clearControllerOrder()
            updateControllerOrderCount()
        }
    }

    fun getControllerOrder() = inputConfigRepository.observeControllerOrder()

    private suspend fun updateControllerOrderCount() {
        val count = inputConfigRepository.getControllerOrder().size
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(controllerOrderCount = count)) }
    }

    fun showInputMappingModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showInputMappingModal = true)) }
    }

    fun hideInputMappingModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showInputMappingModal = false)) }
    }

    fun getConnectedControllers() = inputConfigRepository.getConnectedControllers()

    suspend fun getControllerMapping(controller: com.nendo.argosy.data.repository.ControllerInfo): Pair<Map<com.nendo.argosy.data.repository.InputSource, Int>, String?> {
        val device = android.view.InputDevice.getDevice(controller.deviceId)
            ?: return Pair(emptyMap(), null)
        val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(device)
        val entity = inputConfigRepository.observeControllerMappings().first()
            .find { it.controllerId == controller.controllerId }
        return Pair(mapping, entity?.presetName)
    }

    suspend fun saveControllerMapping(
        controller: com.nendo.argosy.data.repository.ControllerInfo,
        mapping: Map<com.nendo.argosy.data.repository.InputSource, Int>,
        presetName: String?,
        isAutoDetected: Boolean
    ) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.saveExtendedMapping(device, mapping, presetName, isAutoDetected)
    }

    suspend fun applyControllerPreset(controller: com.nendo.argosy.data.repository.ControllerInfo, presetName: String) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.applyPreset(device, presetName)
    }

    fun showHotkeysModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showHotkeysModal = true)) }
    }

    fun hideHotkeysModal() {
        _uiState.update { it.copy(builtinControls = it.builtinControls.copy(showHotkeysModal = false)) }
    }

    fun observeHotkeys() = inputConfigRepository.observeHotkeys()

    suspend fun saveHotkey(action: com.nendo.argosy.data.local.entity.HotkeyAction, keyCodes: List<Int>) {
        inputConfigRepository.setHotkey(action, keyCodes)
    }

    suspend fun clearHotkey(action: com.nendo.argosy.data.local.entity.HotkeyAction) {
        inputConfigRepository.deleteHotkey(action)
    }

    fun setBuiltinBlackFrameInsertion(enabled: Boolean) {
        _uiState.update {
            it.copy(builtinVideo = it.builtinVideo.copy(
                blackFrameInsertion = enabled,
                skipDuplicateFrames = if (enabled) false else it.builtinVideo.skipDuplicateFrames,
                lowLatencyAudio = if (enabled) false else it.builtinVideo.lowLatencyAudio
            ))
        }
        viewModelScope.launch {
            preferencesRepository.setBuiltinBlackFrameInsertion(enabled)
            if (enabled) {
                preferencesRepository.setBuiltinSkipDuplicateFrames(false)
                preferencesRepository.setBuiltinLowLatencyAudio(false)
            }
        }
    }

    fun cycleBuiltinShader(direction: Int) {
        val options = listOf("None", "Sharp", "CUT", "CUT2", "CUT3", "CRT", "LCD")
        val current = _uiState.value.builtinVideo.shader
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinShader(options[nextIndex])
    }

    fun cycleBuiltinFilter(direction: Int) {
        val options = listOf("Auto", "Nearest", "Bilinear")
        val current = _uiState.value.builtinVideo.filter
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinFilter(options[nextIndex])
    }

    fun cycleBuiltinAspectRatio(direction: Int) {
        val options = listOf("Core Provided", "4:3", "16:9", "Integer", "Stretch")
        val current = _uiState.value.builtinVideo.aspectRatio
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinAspectRatio(options[nextIndex])
    }

    fun cycleBuiltinFastForwardSpeed(direction: Int) {
        val options = listOf(2, 4, 8)
        val currentDisplay = _uiState.value.builtinVideo.fastForwardSpeed
        val current = currentDisplay.removeSuffix("x").toIntOrNull() ?: 4
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinFastForwardSpeed(options[nextIndex])
    }

    fun cycleBuiltinRotation(direction: Int) {
        val options = listOf(-1, 0, 90, 180, 270)
        val currentDisplay = _uiState.value.builtinVideo.rotation
        val current = when (currentDisplay) {
            "Auto" -> -1
            "0°" -> 0
            "90°" -> 90
            "180°" -> 180
            "270°" -> 270
            else -> -1
        }
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinRotation(options[nextIndex])
    }

    fun cycleBuiltinOverscanCrop(direction: Int) {
        val options = listOf(0, 4, 8, 12, 16)
        val currentDisplay = _uiState.value.builtinVideo.overscanCrop
        val current = when (currentDisplay) {
            "Off" -> 0
            else -> currentDisplay.removeSuffix("px").toIntOrNull() ?: 0
        }
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinOverscanCrop(options[nextIndex])
    }

    private fun setBuiltinFastForwardSpeed(speed: Int) {
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(fastForwardSpeed = "${speed}x")) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinFastForwardSpeed(speed)
        }
    }

    private fun setBuiltinRotation(rotation: Int) {
        val display = when (rotation) {
            -1 -> "Auto"
            0 -> "0°"
            90 -> "90°"
            180 -> "180°"
            270 -> "270°"
            else -> "Auto"
        }
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rotation = display)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinRotation(rotation)
        }
    }

    private fun setBuiltinOverscanCrop(crop: Int) {
        val display = if (crop == 0) "Off" else "${crop}px"
        _uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(overscanCrop = display)) }
        viewModelScope.launch {
            preferencesRepository.setBuiltinOverscanCrop(crop)
        }
    }

    fun updatePlatformLibretroSetting(setting: LibretroSettingDef, value: String?) {
        val platformContext = _uiState.value.builtinVideo.currentPlatformContext ?: return
        viewModelScope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformContext.platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformContext.platformId)

            val updated = when (setting) {
                LibretroSettingDef.Shader -> current.copy(shader = value)
                LibretroSettingDef.Filter -> current.copy(filter = value)
                LibretroSettingDef.AspectRatio -> current.copy(aspectRatio = value)
                LibretroSettingDef.Rotation -> current.copy(rotation = parseRotationValue(value))
                LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = parseOverscanValue(value))
                LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = value?.toBooleanStrictOrNull())
                LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = parseFastForwardValue(value))
                LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = value?.toBooleanStrictOrNull())
                LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = value?.toBooleanStrictOrNull())
                LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = value?.toBooleanStrictOrNull())
            }

            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformContext.platformId)
            }
        }
    }

    fun resetAllPlatformLibretroSettings() {
        val platformContext = _uiState.value.builtinVideo.currentPlatformContext ?: return
        viewModelScope.launch {
            platformLibretroSettingsDao.deleteByPlatformId(platformContext.platformId)
        }
    }

    private fun parseRotationValue(value: String?): Int? {
        if (value == null) return null
        return when (value) {
            "Auto" -> -1
            "0°" -> 0
            "90°" -> 90
            "180°" -> 180
            "270°" -> 270
            else -> value.removeSuffix("°").toIntOrNull() ?: -1
        }
    }

    private fun parseOverscanValue(value: String?): Int? {
        if (value == null) return null
        return when (value) {
            "Off" -> 0
            else -> value.removeSuffix("px").toIntOrNull() ?: 0
        }
    }

    private fun parseFastForwardValue(value: String?): Int? {
        if (value == null) return null
        return value.removeSuffix("x").toIntOrNull()
    }

    fun loadCoreManagementState(preserveFocus: Boolean = false) {
        viewModelScope.launch {
            val currentState = _uiState.value.coreManagement
            val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(context)
            val syncEnabledPlatforms = platformDao.getSyncEnabledPlatforms()
            val coreSelections = preferencesRepository.getBuiltinCoreSelections().first()
            val installedCoreIds = getInstalledCoreIds()

            val platformRows = syncEnabledPlatforms
                .filter { LibretroCoreRegistry.isPlatformSupported(it.slug) }
                .map { platform ->
                    val availableCores = LibretroCoreRegistry.getCoresForPlatform(platform.slug)
                    val selectedCoreId = coreSelections[platform.slug]
                    val activeCoreId = selectedCoreId
                        ?: LibretroCoreRegistry.getDefaultCoreForPlatform(platform.slug)?.coreId

                    PlatformCoreRow(
                        platformSlug = platform.slug,
                        platformName = platform.name,
                        cores = availableCores.map { core ->
                            CoreChipState(
                                coreId = core.coreId,
                                displayName = core.displayName,
                                isInstalled = core.coreId in installedCoreIds,
                                isActive = core.coreId == activeCoreId
                            )
                        }
                    )
                }

            val focusedPlatformIndex = if (preserveFocus) {
                currentState.focusedPlatformIndex.coerceIn(0, (platformRows.size - 1).coerceAtLeast(0))
            } else {
                0
            }
            val focusedCoreIndex = if (preserveFocus) {
                val maxCoreIndex = (platformRows.getOrNull(focusedPlatformIndex)?.cores?.size ?: 1) - 1
                currentState.focusedCoreIndex.coerceIn(0, maxCoreIndex.coerceAtLeast(0))
            } else {
                platformRows.firstOrNull()?.activeCoreIndex ?: 0
            }

            _uiState.update {
                it.copy(
                    coreManagement = CoreManagementState(
                        platforms = platformRows,
                        focusedPlatformIndex = focusedPlatformIndex,
                        focusedCoreIndex = focusedCoreIndex,
                        isOnline = isOnline
                    )
                )
            }
        }
    }

    fun moveCoreManagementPlatformFocus(delta: Int): Boolean {
        val state = _uiState.value.coreManagement
        val newIndex = (state.focusedPlatformIndex + delta).coerceIn(0, state.platforms.size - 1)
        if (newIndex == state.focusedPlatformIndex) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        val newPlatform = state.platforms.getOrNull(newIndex)
        _uiState.update {
            it.copy(
                coreManagement = it.coreManagement.copy(
                    focusedPlatformIndex = newIndex,
                    focusedCoreIndex = newPlatform?.activeCoreIndex ?: 0
                )
            )
        }
        return true
    }

    fun moveCoreManagementCoreFocus(delta: Int): Boolean {
        val state = _uiState.value.coreManagement
        val platform = state.focusedPlatform ?: return false
        val newIndex = (state.focusedCoreIndex + delta).coerceIn(0, platform.cores.size - 1)
        if (newIndex == state.focusedCoreIndex) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        _uiState.update {
            it.copy(coreManagement = it.coreManagement.copy(focusedCoreIndex = newIndex))
        }
        return true
    }

    fun selectCoreForPlatform() {
        val state = _uiState.value.coreManagement
        val platform = state.focusedPlatform ?: return
        val core = state.focusedCore ?: return

        if (!core.isInstalled) {
            if (state.isOnline) {
                downloadCoreWithNotification(core.coreId)
            } else {
                notificationManager.showError("Cannot download while offline")
            }
            return
        }

        viewModelScope.launch {
            preferencesRepository.setBuiltinCoreForPlatform(platform.platformSlug, core.coreId)
            loadCoreManagementState(preserveFocus = true)
        }
    }

    private fun downloadCoreWithNotification(coreId: String) {
        viewModelScope.launch {
            val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return@launch
            _uiState.update {
                it.copy(coreManagement = it.coreManagement.copy(isDownloading = true, downloadingCoreId = coreId))
            }

            val notificationKey = "core_download_$coreId"
            notificationManager.showPersistent(
                title = "Downloading ${coreInfo.displayName}",
                subtitle = "Please wait...",
                key = notificationKey
            )

            val result = coreManager.downloadCoreById(coreId)

            result.fold(
                onSuccess = {
                    notificationManager.completePersistent(
                        key = notificationKey,
                        title = "Downloaded ${coreInfo.displayName}",
                        subtitle = "Core is now available",
                        type = NotificationType.SUCCESS
                    )
                    emulatorDelegate.updateCoreCounts()
                    loadCoreManagementState(preserveFocus = true)
                },
                onFailure = { error ->
                    notificationManager.completePersistent(
                        key = notificationKey,
                        title = "Download failed",
                        subtitle = error.message ?: "Unknown error",
                        type = NotificationType.ERROR
                    )
                }
            )

            _uiState.update {
                it.copy(coreManagement = it.coreManagement.copy(isDownloading = false, downloadingCoreId = null))
            }
        }
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

    fun changeExtensionForPlatform(config: PlatformEmulatorConfig, extension: String) {
        emulatorDelegate.changeExtensionForPlatform(viewModelScope, config.platform.id, extension) { loadSettings() }
    }

    fun cycleExtensionForPlatform(config: PlatformEmulatorConfig, direction: Int) {
        val options = config.extensionOptions
        if (options.isEmpty()) return

        val currentExtension = config.selectedExtension.orEmpty()
        val currentIndex = options.indexOfFirst { it.extension == currentExtension }.coerceAtLeast(0)
        val newIndex = (currentIndex + direction).coerceIn(0, options.size - 1)
        if (newIndex == currentIndex) return

        val newExtension = options[newIndex].extension

        val updatedPlatforms = _uiState.value.emulators.platforms.map {
            if (it.platform.id == config.platform.id) it.copy(selectedExtension = newExtension.ifEmpty { null })
            else it
        }
        _uiState.update { it.copy(emulators = it.emulators.copy(platforms = updatedPlatforms)) }

        viewModelScope.launch {
            configureEmulatorUseCase.setExtensionForPlatform(config.platform.id, newExtension.ifEmpty { null })
        }
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
        val installedEmulator = config.availableEmulators
            .find { it.def.displayName == config.selectedEmulator || it.def.displayName == config.effectiveEmulatorName }
            ?: return
        val emulatorId = SavePathRegistry.resolveConfigIdForPackage(installedEmulator.def.packageName)
            ?: installedEmulator.def.id
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
        val currentState = _uiState.value
        val parentIndex = if (currentState.currentSection == SettingsSection.MAIN) {
            currentState.focusedIndex
        } else {
            currentState.parentFocusIndex
        }
        _uiState.update { it.copy(currentSection = section, focusedIndex = 0, parentFocusIndex = parentIndex) }
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
            state.syncSettings.showPlatformFiltersModal -> {
                dismissPlatformFiltersModal()
                true
            }
            state.syncSettings.showSyncFiltersModal -> {
                dismissSyncFiltersModal()
                true
            }
            state.emulators.showEmulatorPicker -> {
                dismissEmulatorPicker()
                true
            }
            state.bios.showDistributeResultModal -> {
                dismissDistributeResultModal()
                true
            }
            state.builtinControls.showControllerOrderModal -> {
                hideControllerOrderModal()
                true
            }
            state.builtinControls.showInputMappingModal -> {
                hideInputMappingModal()
                true
            }
            state.builtinControls.showHotkeysModal -> {
                hideHotkeysModal()
                true
            }
            state.server.rommConfiguring -> {
                cancelRommConfig()
                true
            }
            state.currentSection == SettingsSection.SYNC_SETTINGS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = 1) }
                true
            }
            state.currentSection == SettingsSection.STEAM_SETTINGS -> {
                val steamIndex = if (_uiState.value.syncSettings.saveSyncEnabled) 7 else 5
                _uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = steamIndex) }
                true
            }
            state.retroAchievements.showLoginForm -> {
                hideRALoginForm()
                true
            }
            state.currentSection == SettingsSection.RETRO_ACHIEVEMENTS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = state.parentFocusIndex) }
                true
            }
            state.currentSection == SettingsSection.BOX_ART -> {
                _uiState.update { it.copy(currentSection = SettingsSection.INTERFACE, focusedIndex = 5) }
                true
            }
            state.currentSection == SettingsSection.HOME_SCREEN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.INTERFACE, focusedIndex = 6) }
                true
            }
            state.currentSection == SettingsSection.BUILTIN_VIDEO -> {
                _uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 0) }
                true
            }
            state.currentSection == SettingsSection.BUILTIN_CONTROLS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 1) }
                true
            }
            state.currentSection == SettingsSection.CORE_MANAGEMENT -> {
                _uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 2) }
                true
            }
            state.currentSection != SettingsSection.MAIN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = state.parentFocusIndex) }
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
        if (_uiState.value.currentSection == SettingsSection.CORE_MANAGEMENT) {
            moveCoreManagementPlatformFocus(delta)
            return
        }
        _uiState.update { state ->
            val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                state.server.connectionStatus == ConnectionStatus.OFFLINE
            val maxIndex = when (state.currentSection) {
                SettingsSection.MAIN -> mainSettingsMaxFocusIndex()
                SettingsSection.SERVER -> if (state.server.rommConfiguring) {
                    4
                } else {
                    val steamBaseIndex = when {
                        isConnected && state.syncSettings.saveSyncEnabled -> 7
                        isConnected -> 5
                        else -> 2
                    }
                    val launcherCount = state.steam.installedLaunchers.size
                    if (launcherCount > 0) steamBaseIndex + launcherCount else steamBaseIndex
                }
                SettingsSection.SYNC_SETTINGS -> 3
                SettingsSection.STEAM_SETTINGS -> 2 + state.steam.installedLaunchers.size
                SettingsSection.RETRO_ACHIEVEMENTS -> if (state.retroAchievements.showLoginForm) 3 else 0
                SettingsSection.STORAGE -> storageMaxFocusIndex(
                    state.storage.platformsExpanded,
                    state.storage.platformConfigs.size
                )
                SettingsSection.INTERFACE -> interfaceMaxFocusIndex(
                    InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
                )
                SettingsSection.HOME_SCREEN -> homeScreenMaxFocusIndex(state.display)
                SettingsSection.BOX_ART -> boxArtMaxFocusIndex(state.display)
                SettingsSection.CONTROLS -> controlsMaxFocusIndex(state.controls)
                SettingsSection.EMULATORS -> emulatorsMaxFocusIndex(
                    state.emulators.canAutoAssign,
                    state.emulators.platforms.size
                )
                SettingsSection.BUILTIN_VIDEO -> builtinVideoMaxFocusIndex(state.builtinVideo, state.platformLibretro.platformSettings)
                SettingsSection.BUILTIN_CONTROLS -> builtinControlsMaxFocusIndex(state.builtinControls)
                SettingsSection.CORE_MANAGEMENT -> (state.coreManagement.platforms.size - 1).coerceAtLeast(0)
                SettingsSection.BIOS -> {
                    // Summary card (0), Directory (1), platforms start at 2
                    val bios = state.bios
                    val platformCount = bios.platformGroups.size
                    val expandedItems = if (bios.expandedPlatformIndex >= 0) {
                        bios.platformGroups.getOrNull(bios.expandedPlatformIndex)?.firmwareItems?.size ?: 0
                    } else 0
                    (1 + platformCount + expandedItems).coerceAtLeast(1)
                }
                SettingsSection.PERMISSIONS -> permissionsMaxFocusIndex(state.permissions)
                SettingsSection.ABOUT -> aboutMaxFocusIndex(state.fileLoggingPath != null)
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
        if (_uiState.value.currentSection == SettingsSection.BIOS) {
            biosDelegate.resetPlatformSubFocus()
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

    fun cycleThemeMode(direction: Int = 1) {
        val modes = com.nendo.argosy.data.preferences.ThemeMode.entries
        val current = uiState.value.display.themeMode
        val currentIndex = modes.indexOf(current)
        val nextIndex = (currentIndex + direction).mod(modes.size)
        setThemeMode(modes[nextIndex])
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

    fun setSecondaryColor(color: Int?) {
        displayDelegate.setSecondaryColor(viewModelScope, color)
    }

    fun adjustSecondaryHue(delta: Float) {
        displayDelegate.adjustSecondaryHue(viewModelScope, delta)
    }

    fun resetToDefaultSecondaryColor() {
        displayDelegate.resetToDefaultSecondaryColor(viewModelScope)
    }

    fun setGridDensity(density: GridDensity) {
        displayDelegate.setGridDensity(viewModelScope, density)
    }

    fun cycleGridDensity(direction: Int = 1) {
        val densities = GridDensity.entries
        val current = uiState.value.display.gridDensity
        val currentIndex = densities.indexOf(current)
        val nextIndex = (currentIndex + direction).mod(densities.size)
        setGridDensity(densities[nextIndex])
    }

    fun setUiScale(scale: Int) {
        displayDelegate.setUiScale(viewModelScope, scale)
    }

    fun adjustUiScale(delta: Int) {
        val current = uiState.value.display.uiScale
        val wouldBe = (current + delta).coerceIn(75, 150)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        displayDelegate.adjustUiScale(viewModelScope, delta)
    }

    fun cycleUiScale() {
        displayDelegate.cycleUiScale(viewModelScope)
    }

    fun adjustBackgroundBlur(delta: Int) {
        val current = uiState.value.display.backgroundBlur
        val wouldBe = (current + delta).coerceIn(0, 100)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        displayDelegate.adjustBackgroundBlur(viewModelScope, delta)
    }

    fun adjustBackgroundSaturation(delta: Int) {
        val current = uiState.value.display.backgroundSaturation
        val wouldBe = (current + delta).coerceIn(0, 100)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        displayDelegate.adjustBackgroundSaturation(viewModelScope, delta)
    }

    fun adjustBackgroundOpacity(delta: Int) {
        val current = uiState.value.display.backgroundOpacity
        val wouldBe = (current + delta).coerceIn(0, 100)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
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
        loadPreviewGames()
    }

    fun navigateToHomeScreen() {
        _uiState.update { it.copy(currentSection = SettingsSection.HOME_SCREEN, focusedIndex = 0) }
    }

    fun cycleBoxArtShape(direction: Int = 1) {
        displayDelegate.cycleBoxArtShape(viewModelScope, direction)
    }

    fun cycleBoxArtCornerRadius(direction: Int = 1) {
        displayDelegate.cycleBoxArtCornerRadius(viewModelScope, direction)
    }

    fun cycleBoxArtBorderThickness(direction: Int = 1) {
        displayDelegate.cycleBoxArtBorderThickness(viewModelScope, direction)
    }

    fun cycleBoxArtBorderStyle(direction: Int = 1) {
        displayDelegate.cycleBoxArtBorderStyle(viewModelScope, direction)
    }

    fun cycleGlassBorderTint(direction: Int = 1) {
        displayDelegate.cycleGlassBorderTint(viewModelScope, direction)
    }

    fun cycleGradientPreset(direction: Int = 1) {
        val current = _uiState.value.display.gradientPreset
        val next = when (current) {
            GradientPreset.VIBRANT -> if (direction > 0) GradientPreset.BALANCED else GradientPreset.SUBTLE
            GradientPreset.BALANCED -> if (direction > 0) GradientPreset.SUBTLE else GradientPreset.VIBRANT
            GradientPreset.SUBTLE -> if (direction > 0) GradientPreset.VIBRANT else GradientPreset.BALANCED
            GradientPreset.CUSTOM -> GradientPreset.BALANCED
        }
        _uiState.update { it.copy(gradientConfig = next.toConfig()) }
        displayDelegate.setGradientPreset(viewModelScope, next)
        extractGradientForPreview()
    }

    fun toggleGradientAdvancedMode() {
        displayDelegate.toggleGradientAdvancedMode(viewModelScope)
        extractGradientForPreview()
    }

    fun cycleBoxArtGlowStrength(direction: Int = 1) {
        displayDelegate.cycleBoxArtGlowStrength(viewModelScope, direction)
    }

    fun cycleBoxArtOuterEffect(direction: Int = 1) {
        displayDelegate.cycleBoxArtOuterEffect(viewModelScope, direction)
    }

    fun cycleBoxArtOuterEffectThickness(direction: Int = 1) {
        displayDelegate.cycleBoxArtOuterEffectThickness(viewModelScope, direction)
    }

    fun cycleSystemIconPosition(direction: Int = 1) {
        displayDelegate.cycleSystemIconPosition(viewModelScope, direction)
    }

    fun cycleSystemIconPadding(direction: Int = 1) {
        displayDelegate.cycleSystemIconPadding(viewModelScope, direction)
    }

    fun cycleBoxArtInnerEffect(direction: Int = 1) {
        displayDelegate.cycleBoxArtInnerEffect(viewModelScope, direction)
    }

    fun cycleBoxArtInnerEffectThickness(direction: Int = 1) {
        displayDelegate.cycleBoxArtInnerEffectThickness(viewModelScope, direction)
    }

    fun cycleDefaultView() {
        displayDelegate.cycleDefaultView(viewModelScope)
    }

    fun setVideoWallpaperEnabled(enabled: Boolean) {
        displayDelegate.setVideoWallpaperEnabled(viewModelScope, enabled)
    }

    fun cycleVideoWallpaperDelay() {
        displayDelegate.cycleVideoWallpaperDelay(viewModelScope)
    }

    fun setVideoWallpaperMuted(muted: Boolean) {
        displayDelegate.setVideoWallpaperMuted(viewModelScope, muted)
    }

    fun setAmbientLedEnabled(enabled: Boolean) {
        displayDelegate.setAmbientLedEnabled(viewModelScope, enabled)
    }

    fun setAmbientLedBrightness(brightness: Int) {
        displayDelegate.setAmbientLedBrightness(viewModelScope, brightness)
    }

    fun adjustAmbientLedBrightness(delta: Int) {
        displayDelegate.adjustAmbientLedBrightness(viewModelScope, delta)
    }

    fun cycleAmbientLedBrightness() {
        displayDelegate.cycleAmbientLedBrightness(viewModelScope)
    }

    fun setAmbientLedAudioBrightness(enabled: Boolean) {
        displayDelegate.setAmbientLedAudioBrightness(viewModelScope, enabled)
    }

    fun setAmbientLedAudioColors(enabled: Boolean) {
        displayDelegate.setAmbientLedAudioColors(viewModelScope, enabled)
    }

    fun cycleAmbientLedColorMode(direction: Int = 1) {
        displayDelegate.cycleAmbientLedColorMode(viewModelScope, direction)
    }

    fun loadPreviewGames() {
        viewModelScope.launch {
            val games = displayDelegate.loadPreviewGames()
            _uiState.update {
                it.copy(
                    previewGames = games,
                    previewGameIndex = 0,
                    previewGame = games.firstOrNull() ?: it.previewGame
                )
            }
            if (games.isNotEmpty()) {
                extractGradientForPreview()
            }
        }
    }

    fun cyclePrevPreviewGame() {
        val games = _uiState.value.previewGames
        if (games.isEmpty()) return
        val currentIndex = _uiState.value.previewGameIndex
        val prevIndex = if (currentIndex <= 0) games.lastIndex else currentIndex - 1
        _uiState.update {
            it.copy(
                previewGameIndex = prevIndex,
                previewGame = games[prevIndex]
            )
        }
        extractGradientForPreview()
    }

    fun cycleNextPreviewGame() {
        val games = _uiState.value.previewGames
        if (games.isEmpty()) return
        val currentIndex = _uiState.value.previewGameIndex
        val nextIndex = if (currentIndex >= games.lastIndex) 0 else currentIndex + 1
        _uiState.update {
            it.copy(
                previewGameIndex = nextIndex,
                previewGame = games[nextIndex]
            )
        }
        extractGradientForPreview()
    }

    fun extractGradientForPreview() {
        viewModelScope.launch(Dispatchers.IO) {
            val coverPath = _uiState.value.previewGame?.coverPath ?: return@launch
            val config = _uiState.value.gradientConfig
            val bitmap = BitmapFactory.decodeFile(coverPath) ?: return@launch
            val result = gradientColorExtractor.extractWithMetrics(bitmap, config)
            bitmap.recycle()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(gradientExtractionResult = result) }
            }
        }
    }

    private inline fun updateGradientConfig(update: GradientExtractionConfig.() -> GradientExtractionConfig) {
        _uiState.update { it.copy(gradientConfig = it.gradientConfig.update()) }
        extractGradientForPreview()
    }

    fun cycleGradientSampleGrid(direction: Int) {
        val options = listOf(8 to 12, 10 to 15, 12 to 18, 16 to 24)
        val current = _uiState.value.gradientConfig.let { it.samplesX to it.samplesY }
        val currentIdx = options.indexOf(current).coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        val (x, y) = options[nextIdx]
        updateGradientConfig { copy(samplesX = x, samplesY = y) }
    }

    fun cycleGradientRadius(direction: Int) {
        val options = listOf(1, 2, 3, 4)
        val current = _uiState.value.gradientConfig.radius
        val currentIdx = options.indexOf(current).coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(radius = options[nextIdx]) }
    }

    fun cycleGradientMinSaturation(direction: Int) {
        val options = listOf(0.20f, 0.25f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f)
        val current = _uiState.value.gradientConfig.minSaturation
        val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(minSaturation = options[nextIdx]) }
    }

    fun cycleGradientMinValue(direction: Int) {
        val options = listOf(0.10f, 0.15f, 0.20f, 0.25f)
        val current = _uiState.value.gradientConfig.minValue
        val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(minValue = options[nextIdx]) }
    }

    fun cycleGradientHueDistance(direction: Int) {
        val options = listOf(20, 30, 40, 50, 60)
        val current = _uiState.value.gradientConfig.minHueDistance
        val currentIdx = options.indexOf(current).coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(minHueDistance = options[nextIdx]) }
    }

    fun cycleGradientSaturationBump(direction: Int) {
        val options = listOf(0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.55f)
        val current = _uiState.value.gradientConfig.saturationBump
        val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(saturationBump = options[nextIdx]) }
    }

    fun cycleGradientValueClamp(direction: Int) {
        val options = listOf(0.70f, 0.75f, 0.80f, 0.85f, 0.90f)
        val current = _uiState.value.gradientConfig.valueClamp
        val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
        val nextIdx = (currentIdx + direction).mod(options.size)
        updateGradientConfig { copy(valueClamp = options[nextIdx]) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        controlsDelegate.setHapticEnabled(viewModelScope, enabled)
    }

    fun cycleVibrationStrength() {
        controlsDelegate.adjustVibrationStrength(0.1f)
    }

    fun adjustVibrationStrength(delta: Float) {
        val current = uiState.value.controls.vibrationStrength
        val wouldBe = (current + delta).coerceIn(0f, 1f)
        if (wouldBe == current && delta != 0f) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        controlsDelegate.adjustVibrationStrength(delta)
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
        val volumeLevels = listOf(50, 70, 85, 95, 100)
        val current = uiState.value.sounds.volume
        val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        val newIndex = (currentIndex + delta).coerceIn(0, volumeLevels.lastIndex)
        if (newIndex == currentIndex && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        soundsDelegate.adjustSoundVolume(viewModelScope, delta)
    }

    fun cycleSoundVolume() {
        val volumeLevels = listOf(50, 70, 85, 95, 100)
        val current = uiState.value.sounds.volume
        val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + 1) % volumeLevels.size
        soundsDelegate.setSoundVolume(viewModelScope, volumeLevels[nextIndex])
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
        val volumeLevels = listOf(2, 5, 10, 20, 35)
        val current = uiState.value.ambientAudio.volume
        val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        val newIndex = (currentIndex + delta).coerceIn(0, volumeLevels.lastIndex)
        if (newIndex == currentIndex && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        ambientAudioDelegate.adjustVolume(viewModelScope, delta)
    }

    fun cycleAmbientAudioVolume() {
        val volumeLevels = listOf(2, 5, 10, 20, 35)
        val current = uiState.value.ambientAudio.volume
        val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + 1) % volumeLevels.size
        ambientAudioDelegate.setVolume(viewModelScope, volumeLevels[nextIndex])
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

    fun cycleControllerLayout() {
        controlsDelegate.cycleControllerLayout(viewModelScope)
    }

    fun refreshDetectedLayout() {
        controlsDelegate.refreshDetectedLayout()
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

    fun openNotificationSettings() {
        permissionsDelegate.openNotificationSettings()
    }

    fun openWriteSettings() {
        permissionsDelegate.openWriteSettings()
    }

    fun requestScreenCapturePermission() {
        viewModelScope.launch {
            _requestScreenCapturePermissionEvent.emit(Unit)
        }
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

    fun showSyncFiltersModal() {
        syncDelegate.showSyncFiltersModal()
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissSyncFiltersModal() {
        syncDelegate.dismissSyncFiltersModal()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveSyncFiltersModalFocus(delta: Int) {
        syncDelegate.moveSyncFiltersModalFocus(delta)
    }

    fun confirmSyncFiltersModalSelection() {
        syncDelegate.confirmSyncFiltersModalSelection(viewModelScope)
    }

    fun showPlatformFiltersModal() {
        syncDelegate.showPlatformFiltersModal(viewModelScope)
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissPlatformFiltersModal() {
        syncDelegate.dismissPlatformFiltersModal()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun movePlatformFiltersModalFocus(delta: Int) {
        syncDelegate.movePlatformFiltersModalFocus(delta)
    }

    fun confirmPlatformFiltersModalSelection() {
        syncDelegate.confirmPlatformFiltersModalSelection(viewModelScope)
    }

    fun togglePlatformSyncEnabled(platformId: Long) {
        syncDelegate.togglePlatformSyncEnabled(viewModelScope, platformId)
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

    private fun setValidatingCache(validating: Boolean) {
        _uiState.update { it.copy(storage = it.storage.copy(isValidatingCache = validating)) }
    }

    fun validateImageCache() {
        if (_uiState.value.storage.isValidatingCache) return
        setValidatingCache(true)

        val key = "cache_validation"
        viewModelScope.launch {
            try {
                notificationManager.showPersistent(
                    title = "Validating Image Cache",
                    subtitle = "Starting...",
                    key = key,
                    progress = NotificationProgress(0, 100)
                )

                val result = imageCacheManager.validateAndCleanCache { phase, current, total ->
                    val progress = if (total > 0) (current * 100) / total else 0
                    notificationManager.updatePersistent(
                        key = key,
                        subtitle = phase,
                        progress = NotificationProgress(progress, 100)
                    )
                }

                val (message, type) = if (result.deletedFiles > 0 || result.clearedPaths > 0) {
                    "Cleaned ${result.deletedFiles} files, cleared ${result.clearedPaths} paths" to NotificationType.SUCCESS
                } else {
                    "Image cache is healthy" to NotificationType.SUCCESS
                }
                notificationManager.completePersistent(key, message, type = type)
            } finally {
                setValidatingCache(false)
            }
        }
    }

    fun cycleMaxConcurrentDownloads() {
        storageDelegate.cycleMaxConcurrentDownloads(viewModelScope)
    }

    fun adjustMaxConcurrentDownloads(delta: Int) {
        val current = uiState.value.storage.maxConcurrentDownloads
        val wouldBe = (current + delta).coerceIn(1, 5)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        storageDelegate.adjustMaxConcurrentDownloads(viewModelScope, delta)
    }

    fun cycleInstantDownloadThreshold() {
        storageDelegate.cycleInstantDownloadThreshold(viewModelScope)
    }

    fun toggleScreenDimmer() {
        storageDelegate.toggleScreenDimmer(viewModelScope)
    }

    fun cycleScreenDimmerTimeout() {
        storageDelegate.cycleScreenDimmerTimeout(viewModelScope)
    }

    fun adjustScreenDimmerTimeout(delta: Int) {
        val current = uiState.value.storage.screenDimmerTimeoutMinutes
        val wouldBe = (current + delta).coerceIn(1, 5)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        storageDelegate.adjustScreenDimmerTimeout(viewModelScope, delta)
    }

    fun cycleScreenDimmerLevel() {
        storageDelegate.cycleScreenDimmerLevel(viewModelScope)
    }

    fun adjustScreenDimmerLevel(delta: Int) {
        val current = uiState.value.storage.screenDimmerLevel
        val wouldBe = (current + delta * 10).coerceIn(40, 70)
        if (wouldBe == current && delta != 0) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        storageDelegate.adjustScreenDimmerLevel(viewModelScope, delta)
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

    fun openPlatformSavePathPicker(platformId: Long) {
        storageDelegate.emitSavePathPicker(viewModelScope, platformId)
    }

    fun setPlatformSavePath(platformId: Long, basePath: String) {
        val storageConfig = _uiState.value.storage.platformConfigs.find { it.platformId == platformId }
        val emulatorId = storageConfig?.emulatorId ?: return
        val evaluatedPath = computeEvaluatedSavePath(platformId, basePath)
        emulatorDelegate.setEmulatorSavePath(viewModelScope, emulatorId, basePath) {
            storageDelegate.updatePlatformSavePath(platformId, evaluatedPath, true)
        }
    }

    fun resetPlatformSavePath(platformId: Long) {
        val storageConfig = _uiState.value.storage.platformConfigs.find { it.platformId == platformId }
        val emulatorId = storageConfig?.emulatorId ?: return
        emulatorDelegate.resetEmulatorSavePath(viewModelScope, emulatorId) {
            val defaultPath = computeEvaluatedSavePath(platformId, null)
            storageDelegate.updatePlatformSavePath(platformId, defaultPath, false)
        }
    }

    fun setPlatformStatePath(platformId: Long, basePath: String) {
        val evaluatedPath = computeEvaluatedStatePath(platformId, basePath)
        storageDelegate.updatePlatformStatePath(platformId, evaluatedPath, true)
    }

    fun resetPlatformStatePath(platformId: Long) {
        val defaultPath = computeEvaluatedStatePath(platformId, null)
        storageDelegate.updatePlatformStatePath(platformId, defaultPath, false)
    }

    private fun computeEvaluatedSavePath(platformId: Long, basePathOverride: String?): String? {
        val emulatorConfig = emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
            ?: return basePathOverride
        if (!emulatorConfig.effectiveEmulatorIsRetroArch) return basePathOverride

        val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride
        val systemName = emulatorConfig.platform.slug
        val coreName = emulatorConfig.selectedCore

        return retroArchConfigParser.resolveSavePaths(
            packageName = packageName,
            systemName = systemName,
            coreName = coreName,
            basePathOverride = basePathOverride
        ).firstOrNull()
    }

    private fun computeEvaluatedStatePath(platformId: Long, basePathOverride: String?): String? {
        val emulatorConfig = emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
            ?: return basePathOverride
        if (!emulatorConfig.effectiveEmulatorIsRetroArch) return basePathOverride

        val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride
        val coreName = emulatorConfig.selectedCore

        return retroArchConfigParser.resolveStatePaths(
            packageName = packageName,
            coreName = coreName,
            basePathOverride = basePathOverride
        ).firstOrNull()
    }

    fun togglePlatformsExpanded() {
        storageDelegate.togglePlatformsExpanded()
    }

    fun jumpToStorageNextSection() {
        val state = _uiState.value
        val storage = state.storage
        val expandedPlatforms = if (storage.platformsExpanded) storage.platformConfigs.size else 0
        val sectionStarts = listOf(
            0,  // DOWNLOADS
            2,  // FILE LOCATIONS
            5,  // PLATFORM STORAGE
            6 + expandedPlatforms  // SAVE DATA
        )
        val currentSection = sectionStarts.lastOrNull { it <= state.focusedIndex } ?: 0
        val nextSectionStart = sectionStarts.firstOrNull { it > currentSection } ?: sectionStarts.last()
        _uiState.update { it.copy(focusedIndex = nextSectionStart) }
    }

    fun jumpToStoragePrevSection() {
        val state = _uiState.value
        val storage = state.storage
        val expandedPlatforms = if (storage.platformsExpanded) storage.platformConfigs.size else 0
        val sectionStarts = listOf(
            0,  // DOWNLOADS
            2,  // FILE LOCATIONS
            5,  // PLATFORM STORAGE
            6 + expandedPlatforms  // SAVE DATA
        )
        val currentSectionIdx = sectionStarts.indexOfLast { it <= state.focusedIndex }.coerceAtLeast(0)
        val prevSectionStart = if (state.focusedIndex == sectionStarts[currentSectionIdx] && currentSectionIdx > 0) {
            sectionStarts[currentSectionIdx - 1]
        } else {
            sectionStarts[currentSectionIdx]
        }
        _uiState.update { it.copy(focusedIndex = prevSectionStart) }
    }

    fun jumpToNextSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean {
        val currentFocus = _uiState.value.focusedIndex
        val nextSection = sections.firstOrNull { it.focusStartIndex > currentFocus }
        if (nextSection != null) {
            _uiState.update { it.copy(focusedIndex = nextSection.focusStartIndex) }
            return true
        }
        return false
    }

    fun jumpToPrevSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean {
        val currentFocus = _uiState.value.focusedIndex
        val currentSectionIdx = sections.indexOfLast { it.focusStartIndex <= currentFocus }
        if (currentSectionIdx <= 0) return false
        val prevSection = if (currentFocus == sections[currentSectionIdx].focusStartIndex) {
            sections[currentSectionIdx - 1]
        } else {
            sections[currentSectionIdx]
        }
        _uiState.update { it.copy(focusedIndex = prevSection.focusStartIndex) }
        return true
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

    fun requestPurgeAll() {
        storageDelegate.requestPurgeAll()
    }

    fun confirmPurgeAll() {
        storageDelegate.confirmPurgeAll(viewModelScope)
    }

    fun cancelPurgeAll() {
        storageDelegate.cancelPurgeAll()
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

    fun movePlatformSettingsButtonFocus(delta: Int) {
        storageDelegate.movePlatformSettingsButtonFocus(delta)
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

    fun showRALoginForm() {
        raDelegate.showLoginForm { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun hideRALoginForm() {
        raDelegate.hideLoginForm { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun setRALoginUsername(username: String) {
        raDelegate.setLoginUsername(username)
    }

    fun setRALoginPassword(password: String) {
        raDelegate.setLoginPassword(password)
    }

    fun clearRAFocusField() {
        raDelegate.clearFocusField()
    }

    fun loginToRA() {
        raDelegate.login(viewModelScope) { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun logoutFromRA() {
        raDelegate.logout(viewModelScope) { _uiState.update { it.copy(focusedIndex = 0) } }
    }

    fun handleConfirm(): InputResult {
        val state = _uiState.value
        return when (state.currentSection) {
            SettingsSection.MAIN -> {
                if (state.focusedIndex == 0) {
                    viewModelScope.launch { _openDeviceSettingsEvent.emit(Unit) }
                } else {
                    val section = when (state.focusedIndex) {
                        1 -> SettingsSection.SERVER
                        2 -> SettingsSection.RETRO_ACHIEVEMENTS
                        3 -> SettingsSection.STORAGE
                        4 -> SettingsSection.INTERFACE
                        5 -> SettingsSection.CONTROLS
                        6 -> SettingsSection.EMULATORS
                        7 -> SettingsSection.BIOS
                        8 -> SettingsSection.PERMISSIONS
                        9 -> SettingsSection.ABOUT
                        else -> null
                    }
                    section?.let { navigateToSection(it) }
                }
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
                    val androidBaseIndex = when {
                        isConnected && state.syncSettings.saveSyncEnabled -> 7
                        isConnected -> 5
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
                        state.focusedIndex == 4 && isConnected -> {
                            toggleSaveSync()
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        state.focusedIndex == 5 && isConnected && state.syncSettings.saveSyncEnabled -> cycleSaveCacheLimit()
                        state.focusedIndex == 6 && isConnected && state.syncSettings.saveSyncEnabled && isOnline -> runSaveSyncNow()
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
            SettingsSection.RETRO_ACHIEVEMENTS -> {
                val ra = state.retroAchievements
                if (ra.showLoginForm) {
                    when (state.focusedIndex) {
                        0, 1 -> raDelegate.setFocusField(state.focusedIndex)
                        2 -> loginToRA()
                        3 -> hideRALoginForm()
                    }
                } else if (ra.isLoggedIn) {
                    if (state.focusedIndex == 0) logoutFromRA()
                } else {
                    if (state.focusedIndex == 0) showRALoginForm()
                }
                InputResult.HANDLED
            }
            SettingsSection.SYNC_SETTINGS -> {
                when (state.focusedIndex) {
                    0 -> showPlatformFiltersModal()
                    1 -> showSyncFiltersModal()
                    2 -> { toggleSyncScreenshots(); return InputResult.handled(SoundType.TOGGLE) }
                    3 -> {
                        if (!state.syncSettings.isImageCacheMigrating) {
                            val actionIndex = state.syncSettings.imageCacheActionIndex
                            if (actionIndex == 0) {
                                openImageCachePicker()
                            } else {
                                resetImageCacheToDefault()
                            }
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                val platformsExpandIndex = 5

                when (state.focusedIndex) {
                    0 -> cycleMaxConcurrentDownloads()
                    1 -> cycleInstantDownloadThreshold()
                    2 -> openFolderPicker()
                    3 -> openImageCachePicker()
                    4 -> validateImageCache()
                    platformsExpandIndex -> togglePlatformsExpanded()
                    else -> {
                        if (state.focusedIndex > platformsExpandIndex) {
                            val platformIndex = state.focusedIndex - platformsExpandIndex - 1
                            val config = state.storage.platformConfigs.getOrNull(platformIndex)
                            config?.let { openPlatformSettingsModal(it.platformId) }
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.INTERFACE -> {
                val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
                when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
                    InterfaceItem.Theme -> {
                        val next = when (state.display.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        setThemeMode(next)
                    }
                    InterfaceItem.GridDensity -> {
                        val next = when (state.display.gridDensity) {
                            GridDensity.COMPACT -> GridDensity.NORMAL
                            GridDensity.NORMAL -> GridDensity.SPACIOUS
                            GridDensity.SPACIOUS -> GridDensity.COMPACT
                        }
                        setGridDensity(next)
                    }
                    InterfaceItem.UiScale -> cycleUiScale()
                    InterfaceItem.BoxArt -> navigateToBoxArt()
                    InterfaceItem.HomeScreen -> navigateToHomeScreen()
                    InterfaceItem.ScreenDimmer -> toggleScreenDimmer()
                    InterfaceItem.DimAfter -> cycleScreenDimmerTimeout()
                    InterfaceItem.DimLevel -> cycleScreenDimmerLevel()
                    InterfaceItem.AmbientLed -> setAmbientLedEnabled(!state.display.ambientLedEnabled)
                    InterfaceItem.AmbientLedBrightness -> cycleAmbientLedBrightness()
                    InterfaceItem.AmbientLedAudioBrightness -> setAmbientLedAudioBrightness(!state.display.ambientLedAudioBrightness)
                    InterfaceItem.AmbientLedAudioColors -> setAmbientLedAudioColors(!state.display.ambientLedAudioColors)
                    InterfaceItem.AmbientLedColorMode -> cycleAmbientLedColorMode()
                    InterfaceItem.BgmToggle -> {
                        val newEnabled = !state.ambientAudio.enabled
                        setAmbientAudioEnabled(newEnabled)
                        return InputResult.handled(if (newEnabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    InterfaceItem.BgmVolume -> cycleAmbientAudioVolume()
                    InterfaceItem.BgmFile -> openAudioFilePicker()
                    InterfaceItem.UiSoundsToggle -> {
                        val newEnabled = !state.sounds.enabled
                        setSoundEnabled(newEnabled)
                        if (newEnabled) {
                            soundManager.setEnabled(true)
                            soundManager.play(SoundType.TOGGLE)
                        }
                        return InputResult.handled(SoundType.SILENT)
                    }
                    InterfaceItem.UiSoundsVolume -> cycleSoundVolume()
                    is InterfaceItem.SoundTypeItem -> {
                        val soundItem = interfaceItemAtFocusIndex(state.focusedIndex, layoutState) as InterfaceItem.SoundTypeItem
                        showSoundPicker(soundItem.soundType)
                    }
                    else -> {}
                }
                InputResult.HANDLED
            }
            SettingsSection.HOME_SCREEN -> {
                when (homeScreenItemAtFocusIndex(state.focusedIndex, state.display)) {
                    HomeScreenItem.GameArtwork -> {
                        setUseGameBackground(!state.display.useGameBackground)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    HomeScreenItem.CustomImage -> openBackgroundPicker()
                    HomeScreenItem.Blur -> cycleBackgroundBlur()
                    HomeScreenItem.Saturation -> cycleBackgroundSaturation()
                    HomeScreenItem.Opacity -> cycleBackgroundOpacity()
                    HomeScreenItem.VideoWallpaper -> {
                        setVideoWallpaperEnabled(!state.display.videoWallpaperEnabled)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    HomeScreenItem.VideoDelay -> cycleVideoWallpaperDelay()
                    HomeScreenItem.VideoMuted -> {
                        setVideoWallpaperMuted(!state.display.videoWallpaperMuted)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    HomeScreenItem.AccentFooter -> {
                        setUseAccentColorFooter(!state.display.useAccentColorFooter)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    else -> {}
                }
                InputResult.HANDLED
            }
            SettingsSection.BOX_ART -> {
                val borderStyle = state.display.boxArtBorderStyle
                val showGlassTint = borderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GLASS
                val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
                val showOuterThickness = state.display.boxArtOuterEffect != com.nendo.argosy.data.preferences.BoxArtOuterEffect.OFF
                val showInnerThickness = state.display.boxArtInnerEffect != com.nendo.argosy.data.preferences.BoxArtInnerEffect.OFF
                var idx = 3
                val glassTintIdx = if (showGlassTint) idx++ else -1
                val iconPosIdx = idx++
                val iconPadIdx = if (showIconPadding) idx++ else -1
                val outerEffectIdx = idx++
                val outerThicknessIdx = if (showOuterThickness) idx++ else -1
                val innerEffectIdx = idx++
                val innerThicknessIdx = if (showInnerThickness) idx++ else -1
                when (state.focusedIndex) {
                    0 -> cycleBoxArtCornerRadius()
                    1 -> cycleBoxArtBorderThickness()
                    2 -> cycleBoxArtBorderStyle()
                    glassTintIdx -> cycleGlassBorderTint()
                    iconPosIdx -> cycleSystemIconPosition()
                    iconPadIdx -> cycleSystemIconPadding()
                    outerEffectIdx -> cycleBoxArtOuterEffect()
                    outerThicknessIdx -> cycleBoxArtOuterEffectThickness()
                    innerEffectIdx -> cycleBoxArtInnerEffect()
                    innerThicknessIdx -> cycleBoxArtInnerEffectThickness()
                }
                InputResult.HANDLED
            }
            SettingsSection.CONTROLS -> {
                val showVibrationSlider = state.controls.hapticEnabled && state.controls.vibrationSupported
                if (showVibrationSlider) {
                    when (state.focusedIndex) {
                        0 -> {
                            val newEnabled = !state.controls.hapticEnabled
                            setHapticEnabled(newEnabled)
                            return InputResult.handled(if (newEnabled) SoundType.TOGGLE else SoundType.SILENT)
                        }
                        1 -> cycleVibrationStrength()
                        2 -> cycleControllerLayout()
                        3 -> { setSwapAB(!state.controls.swapAB); return InputResult.handled(SoundType.TOGGLE) }
                        4 -> { setSwapXY(!state.controls.swapXY); return InputResult.handled(SoundType.TOGGLE) }
                        5 -> { setSwapStartSelect(!state.controls.swapStartSelect); return InputResult.handled(SoundType.TOGGLE) }
                    }
                } else {
                    when (state.focusedIndex) {
                        0 -> {
                            val newEnabled = !state.controls.hapticEnabled
                            setHapticEnabled(newEnabled)
                            return InputResult.handled(if (newEnabled) SoundType.TOGGLE else SoundType.SILENT)
                        }
                        1 -> cycleControllerLayout()
                        2 -> { setSwapAB(!state.controls.swapAB); return InputResult.handled(SoundType.TOGGLE) }
                        3 -> { setSwapXY(!state.controls.swapXY); return InputResult.handled(SoundType.TOGGLE) }
                        4 -> { setSwapStartSelect(!state.controls.swapStartSelect); return InputResult.handled(SoundType.TOGGLE) }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.EMULATORS -> {
                // Built-in items: 0=Video, 1=Audio, 2=Cores
                // Then: AutoAssign (if canAutoAssign), then platforms
                val builtinCount = 3
                val autoAssignIndex = if (state.emulators.canAutoAssign) builtinCount else -1
                val platformStartIndex = builtinCount + (if (state.emulators.canAutoAssign) 1 else 0)

                when {
                    state.focusedIndex == 0 -> navigateToBuiltinVideo()
                    state.focusedIndex == 1 -> navigateToBuiltinControls()
                    state.focusedIndex == 2 -> navigateToCoreManagement()
                    state.focusedIndex == autoAssignIndex -> autoAssignAllEmulators()
                    state.focusedIndex >= platformStartIndex -> {
                        val platformIndex = state.focusedIndex - platformStartIndex
                        val config = state.emulators.platforms.getOrNull(platformIndex)
                        if (config != null) {
                            showEmulatorPicker(config)
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.BIOS -> {
                when (state.focusedIndex) {
                    0 -> {
                        val actionIndex = state.bios.actionIndex
                        if (actionIndex == 0 && state.bios.missingFiles > 0) {
                            downloadAllBios()
                        } else if (actionIndex == 1 && state.bios.downloadedFiles > 0) {
                            distributeAllBios()
                        }
                    }
                    1 -> openBiosFolderPicker()
                    else -> {
                        val bios = state.bios
                        val focusMapping = buildBiosFocusMapping(bios.platformGroups, bios.expandedPlatformIndex)
                        val (platformIndex, isChildItem) = focusMapping.getPlatformAndChildInfo(state.focusedIndex)

                        if (platformIndex >= 0 && platformIndex < bios.platformGroups.size) {
                            val group = bios.platformGroups[platformIndex]
                            if (isChildItem) {
                                val childIndex = focusMapping.getChildIndex(state.focusedIndex, platformIndex)
                                val firmware = group.firmwareItems.getOrNull(childIndex)
                                if (firmware != null && !firmware.isDownloaded) {
                                    downloadSingleBios(firmware.rommId)
                                }
                            } else {
                                if (bios.platformSubFocusIndex == 1 && !group.isComplete) {
                                    downloadBiosForPlatform(group.platformSlug)
                                } else {
                                    toggleBiosPlatformExpanded(platformIndex)
                                }
                            }
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.PERMISSIONS -> {
                when (state.focusedIndex) {
                    0 -> openStorageSettings()
                    1 -> openUsageStatsSettings()
                    2 -> openNotificationSettings()
                    3 -> openWriteSettings()
                    4 -> requestScreenCapturePermission()
                }
                InputResult.HANDLED
            }
            SettingsSection.ABOUT -> {
                when (state.focusedIndex) {
                    0 -> {
                        if (state.updateCheck.updateAvailable) {
                            viewModelScope.launch { _downloadUpdateEvent.emit(Unit) }
                        } else {
                            checkForUpdates()
                        }
                    }
                    1 -> {
                        setBetaUpdatesEnabled(!state.betaUpdatesEnabled)
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    2 -> {
                        if (state.fileLoggingPath != null) {
                            toggleFileLogging(!state.fileLoggingEnabled)
                        } else {
                            openLogFolderPicker()
                        }
                        return InputResult.handled(SoundType.TOGGLE)
                    }
                    3 -> {
                        if (state.fileLoggingPath != null) {
                            cycleFileLogLevel()
                        }
                    }
                }
                InputResult.HANDLED
            }
            SettingsSection.BUILTIN_VIDEO -> InputResult.HANDLED
            SettingsSection.BUILTIN_CONTROLS -> InputResult.HANDLED
            SettingsSection.CORE_MANAGEMENT -> {
                selectCoreForPlatform()
                InputResult.HANDLED
            }
        }
    }

    fun downloadAllBios() {
        biosDelegate.downloadAllBios(viewModelScope)
    }

    fun distributeAllBios() {
        biosDelegate.distributeAllBios(viewModelScope)
    }

    fun openBiosFolderPicker() {
        biosDelegate.openFolderPicker(viewModelScope)
    }

    fun toggleBiosPlatformExpanded(index: Int) {
        biosDelegate.togglePlatformExpanded(index)
    }

    fun downloadBiosForPlatform(platformSlug: String) {
        biosDelegate.downloadBiosForPlatform(platformSlug, viewModelScope)
    }

    fun downloadSingleBios(rommId: Long) {
        biosDelegate.downloadSingleBios(rommId, viewModelScope)
    }

    fun onBiosFolderSelected(path: String) {
        biosDelegate.onBiosFolderSelected(path, viewModelScope)
    }

    fun moveBiosActionFocus(delta: Int) {
        biosDelegate.moveActionFocus(delta)
    }

    fun moveBiosPlatformSubFocus(delta: Int): Boolean {
        val state = _uiState.value
        val focusIndex = state.focusedIndex
        if (focusIndex < 2) return false

        val bios = state.bios
        val focusMapping = buildBiosFocusMapping(bios.platformGroups, bios.expandedPlatformIndex)
        val (platformIndex, isChildItem) = focusMapping.getPlatformAndChildInfo(focusIndex)

        if (isChildItem || platformIndex < 0 || platformIndex >= bios.platformGroups.size) return false

        val group = bios.platformGroups[platformIndex]
        return biosDelegate.movePlatformSubFocus(delta, !group.isComplete)
    }

    fun resetBiosPlatformSubFocus() {
        biosDelegate.resetPlatformSubFocus()
    }

    fun dismissDistributeResultModal() {
        biosDelegate.dismissDistributeResultModal()
    }

    private fun buildBiosFocusMapping(
        platformGroups: List<BiosPlatformGroup>,
        expandedIndex: Int
    ): BiosFocusMappingHelper = BiosFocusMappingHelper(platformGroups, expandedIndex)

    private class BiosFocusMappingHelper(
        private val platformGroups: List<BiosPlatformGroup>,
        private val expandedIndex: Int
    ) {
        fun getPlatformAndChildInfo(focusIndex: Int): Pair<Int, Boolean> {
            if (focusIndex < 2) return -1 to false

            var currentFocus = 2
            for ((index, group) in platformGroups.withIndex()) {
                if (currentFocus == focusIndex) return index to false
                currentFocus++

                if (index == expandedIndex) {
                    for (i in group.firmwareItems.indices) {
                        if (currentFocus == focusIndex) return index to true
                        currentFocus++
                    }
                }
            }
            return -1 to false
        }

        fun getChildIndex(focusIndex: Int, platformIndex: Int): Int {
            if (platformIndex != expandedIndex) return -1

            var currentFocus = 2
            for ((index, group) in platformGroups.withIndex()) {
                currentFocus++
                if (index == expandedIndex) {
                    for (i in group.firmwareItems.indices) {
                        if (currentFocus == focusIndex) return i
                        currentFocus++
                    }
                }
            }
            return -1
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler =
        SettingsInputHandler(this, onBack)
}
