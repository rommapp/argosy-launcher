package com.nendo.argosy.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.common.GradientColorExtractor
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.repository.CoreOptionsRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.LibretroSettingsRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.scanner.AndroidGameScanner
import com.nendo.argosy.data.social.SocialAuthManager
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.discord.DiscordPresenceManager
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
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
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.util.PlatformFilterLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val preferencesRepository: UserPreferencesRepository,
    internal val hapticManager: HapticFeedbackManager,
    internal val platformRepository: PlatformRepository,
    internal val libretroSettingsRepo: LibretroSettingsRepository,
    internal val touchLayoutRepository: com.nendo.argosy.data.repository.TouchLayoutRepository,
    internal val launchArgsRepo: com.nendo.argosy.data.repository.LaunchArgsRepository,
    internal val installedAppResolver: com.nendo.argosy.data.platform.InstalledAppResolver,
    internal val emulatorConfigRepo: EmulatorConfigRepository,
    internal val emulatorDetector: EmulatorDetector,
    internal val romMRepository: RomMRepository,
    internal val notificationManager: NotificationManager,
    internal val gameRepository: GameRepository,
    internal val imageCacheManager: ImageCacheManager,
    internal val syncLibraryUseCase: SyncLibraryUseCase,
    internal val platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue,
    internal val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    internal val updateRepository: UpdateRepository,
    internal val appInstaller: AppInstaller,
    internal val soundManager: SoundFeedbackManager,
    internal val saveCacheDao: SaveCacheDao,
    internal val retroArchConfigParser: RetroArchConfigParser,
    internal val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
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
    val driversDelegate: com.nendo.argosy.ui.screens.settings.delegates.DriversSettingsDelegate,
    internal val androidGameScanner: AndroidGameScanner,
    internal val modalResetSignal: ModalResetSignal,
    internal val gradientColorExtractor: GradientColorExtractor,
    internal val coreManager: LibretroCoreManager,
    internal val inputConfigRepository: com.nendo.argosy.data.repository.InputConfigRepository,
    internal val frameRegistry: com.nendo.argosy.libretro.frame.FrameRegistry,
    internal val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    internal val socialRepository: SocialRepository,
    internal val discordPresenceManager: DiscordPresenceManager,
    internal val coreOptionsRepo: CoreOptionsRepository,
    private val playStatsRepo: com.nendo.argosy.data.repository.PlayStatsRepository,
    private val biosRepository: com.nendo.argosy.data.repository.BiosRepository,
    private val savePathValidator: com.nendo.argosy.data.emulator.SavePathValidator
) : ViewModel() {

    internal val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    internal val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    internal val _downloadUpdateEvent = MutableSharedFlow<Unit>()
    val downloadUpdateEvent: SharedFlow<Unit> = _downloadUpdateEvent.asSharedFlow()

    internal val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    internal val _requestNotificationPermissionEvent = MutableSharedFlow<Unit>()
    val requestNotificationPermissionEvent: SharedFlow<Unit> = _requestNotificationPermissionEvent.asSharedFlow()

    internal val _requestScreenCapturePermissionEvent = MutableSharedFlow<Unit>()
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
    private val _launchBuiltinSavePathPicker = MutableSharedFlow<Unit>()
    val launchBuiltinSavePathPicker: SharedFlow<Unit> = _launchBuiltinSavePathPicker
    private val _launchBuiltinStatePathPicker = MutableSharedFlow<Unit>()
    val launchBuiltinStatePathPicker: SharedFlow<Unit> = _launchBuiltinStatePathPicker
    private val _launchPlatformBuiltinSavePathPicker = MutableSharedFlow<Long>()
    val launchPlatformBuiltinSavePathPicker: SharedFlow<Long> = _launchPlatformBuiltinSavePathPicker
    private val _launchPlatformBuiltinStatePathPicker = MutableSharedFlow<Long>()
    val launchPlatformBuiltinStatePathPicker: SharedFlow<Long> = _launchPlatformBuiltinStatePathPicker

    fun openBuiltinSavePathBrowser() { viewModelScope.launch { _launchBuiltinSavePathPicker.emit(Unit) } }
    fun openBuiltinStatePathBrowser() { viewModelScope.launch { _launchBuiltinStatePathPicker.emit(Unit) } }
    fun openPlatformBuiltinSavePathBrowser(platformId: Long) { viewModelScope.launch { _launchPlatformBuiltinSavePathPicker.emit(platformId) } }
    fun openPlatformBuiltinStatePathBrowser(platformId: Long) { viewModelScope.launch { _launchPlatformBuiltinStatePathPicker.emit(platformId) } }
    val resetPlatformStatePathEvent: SharedFlow<Long> = storageDelegate.resetStatePathEvent
    val openImageCachePickerEvent: SharedFlow<Unit> = syncDelegate.openImageCachePickerEvent
    val launchBiosFolderPicker: SharedFlow<Unit> = biosDelegate.launchFolderPicker
    val launchGpuDriverFilePicker: SharedFlow<Unit> = biosDelegate.launchGpuDriverFilePicker

    internal val _openLogFolderPickerEvent = MutableSharedFlow<Unit>()
    val openLogFolderPickerEvent: SharedFlow<Unit> = _openLogFolderPickerEvent.asSharedFlow()

    internal val _openDeviceSettingsEvent = MutableSharedFlow<Unit>()
    val openDeviceSettingsEvent: SharedFlow<Unit> = _openDeviceSettingsEvent.asSharedFlow()

    init {
        routeObserveDelegateStates(this)
        routeObserveDelegateEvents(this)
        routeObserveModalResetSignal(this)
        routeObserveConnectionState(this)
        observeSocialConnectionState()
        routeObservePlatformLibretroSettings(this)
        routeLoadAvailablePlatformsForLibretro(this)
        loadSettings()
        driversDelegate.loadGpuInfo()
        raDelegate.initialize(viewModelScope)
        displayDelegate.loadPreviewGame(viewModelScope)
        displayDelegate.observeScreenCapturePermission(viewModelScope)
        routeStartControllerDetectionPolling(this)

        // TODO: Remove after testing manage=true Android/data access
        storageDelegate.testManagedStorageAccess(viewModelScope)
    }

    fun cyclePlatformContext(direction: Int) = routeCyclePlatformContext(this, direction)

    internal fun loadSettings() = routeLoadSettings(this)

    fun refreshEmulators() {
        emulatorDelegate.refreshEmulators()
        loadSettings()
    }

    fun checkStoragePermission() = storageDelegate.checkAllFilesAccess()
    fun requestStoragePermission() = storageDelegate.requestAllFilesAccess(viewModelScope)

    fun reloadDrivers(force: Boolean = false) = driversDelegate.loadDrivers(viewModelScope, force)
    fun downloadDriverArtifact(artifact: com.nendo.argosy.ui.screens.settings.DriverArtifactUi) =
        driversDelegate.downloadArtifact(viewModelScope, artifact)
    fun openDriverPicker(index: Int) = driversDelegate.openPicker(index)
    fun dismissDriverPicker() = driversDelegate.dismissPicker()
    fun moveDriverPickerFocus(delta: Int) = driversDelegate.movePickerReleaseFocus(delta)
    fun downloadSelectedDriverRelease() = driversDelegate.downloadFocusedPickerRelease(viewModelScope)
    fun dismissDriverDownload() = driversDelegate.dismissActiveDownload()

    fun showEmulatorPicker(config: PlatformEmulatorConfig) = routeShowEmulatorPicker(this, config)

    fun dismissEmulatorPicker() = emulatorDelegate.dismissEmulatorPicker()

    fun handleVariantPickerItemTap(index: Int) = routeHandleVariantPickerItemTap(this, index)

    fun moveVariantPickerFocus(delta: Int) = emulatorDelegate.moveVariantPickerFocus(delta)
    fun selectVariant() = emulatorDelegate.selectVariant()
    fun confirmVariantSelection() = emulatorDelegate.selectVariant()
    fun dismissVariantPicker() = emulatorDelegate.dismissVariantPicker()
    fun navigateToBuiltinVideo() = emulatorDelegate.navigateToBuiltinVideo(viewModelScope)
    fun navigateToBuiltinControls() = emulatorDelegate.navigateToBuiltinControls(viewModelScope)
    fun navigateToCoreManagement() = emulatorDelegate.navigateToCoreManagement(viewModelScope)
    fun navigateToCoreOptions() = emulatorDelegate.navigateToCoreOptions(viewModelScope)
    fun navigateToCoreOptionsForPlatform() {
        val s = _uiState.value
        val currentSlug = s.emulators.platforms.getOrNull(s.platformDetail.platformIndex)?.platform?.slug
        val targetIndex = s.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformSlug == currentSlug }
            .takeIf { it >= 0 } ?: s.coreOptions.platformContextIndex
        _uiState.update {
            it.copy(
                platformDetail = it.platformDetail.copy(builtinEnteredFromPlatform = true),
                coreOptions = it.coreOptions.copy(
                    platformContextIndex = targetIndex,
                    selectedCoreIndex = 0
                )
            )
        }
        emulatorDelegate.navigateToCoreOptions(viewModelScope)
    }
    fun openPlatformDetailById(platformId: Long) {
        viewModelScope.launch {
            val platforms = kotlinx.coroutines.withTimeoutOrNull(5000) {
                uiState.map { it.emulators.platforms }.first { it.isNotEmpty() }
            } ?: return@launch
            val index = platforms.indexOfFirst { it.platform.id == platformId }
            if (index < 0) {
                navigateToSection(SettingsSection.PLATFORMS)
                return@launch
            }
            _uiState.update { it.copy(platformDetail = it.platformDetail.copy(enteredExternally = true)) }
            navigateToPlatformDetail(index)
        }
    }

    fun navigateToPlatformDetail(platformIndex: Int) {
        _uiState.update { it.copy(
            currentSection = SettingsSection.PLATFORM_DETAIL,
            focusedIndex = 0,
            platformDetail = it.platformDetail.copy(platformIndex = platformIndex)
        ) }
        loadPlatformDetailStats(platformIndex)
    }

    fun cyclePlatformDetail(direction: Int) {
        val platforms = _uiState.value.emulators.platforms
        if (platforms.isEmpty()) return
        val currentIndex = _uiState.value.platformDetail.platformIndex
        val newIndex = (currentIndex + direction).coerceIn(0, platforms.size - 1)
        if (newIndex == currentIndex) return
        _uiState.update { it.copy(
            focusedIndex = 0,
            platformDetail = it.platformDetail.copy(platformIndex = newIndex)
        ) }
        loadPlatformDetailStats(newIndex)
    }

    internal fun loadPlatformDetailStats(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        viewModelScope.launch {
            val platformId = config.platform.id
            val platformSlug = config.platform.slug
            val downloaded = gameRepository.countDownloadedByPlatform(platformId)
            val favorites = gameRepository.countFavoritesByPlatform(platformId)
            val totalPlayTimeMs = playStatsRepo.getTotalActivePlayMsByPlatform(platformSlug)
            val allBiosStatus = biosRepository.getStatusByPlatform()
            val biosStatus = allBiosStatus.find { it.platformSlug == platformSlug }
            val packagePathAccessible = if (config.effectiveEmulatorId == "builtin") {
                true
            } else {
                config.effectiveEmulatorPackage?.let { pkg ->
                    val emulatorId = config.effectiveEmulatorId ?: return@let null
                    savePathValidator.isPackageDataAccessible(emulatorId, pkg)
                }
            }

            _uiState.update { it.copy(
                platformDetail = it.platformDetail.copy(
                    totalGames = config.platform.gameCount,
                    downloadedGames = downloaded,
                    favorites = favorites,
                    totalPlayTimeMs = totalPlayTimeMs,
                    packagePathAccessible = packagePathAccessible,
                    biosTotal = biosStatus?.totalFiles ?: 0,
                    biosDownloaded = biosStatus?.downloadedFiles ?: 0,
                    hasBiosRequirements = (biosStatus?.totalFiles ?: 0) > 0
                )
            ) }
        }
    }

    val librarySyncProgress get() = romMRepository.syncProgress

    fun scanFilesForPlatform(platformId: Long) {
        val platformIndex = _uiState.value.platformDetail.platformIndex
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex)
        val platformName = config?.platform?.name ?: "Platform"
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = true)) }
        viewModelScope.launch {
            val invalidated = gameRepository.validateLocalFilesForPlatform(platformId)
            val discovered = gameRepository.discoverLocalFilesForPlatform(platformId)
            _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = false)) }
            loadPlatformDetailStats(platformIndex)

            val parts = mutableListOf<String>()
            if (discovered > 0) parts.add("$discovered found")
            if (invalidated > 0) parts.add("$invalidated removed")
            if (parts.isEmpty()) parts.add("No changes")

            notificationManager.show(
                title = "Scan Complete",
                subtitle = "$platformName: ${parts.joinToString(", ")}",
                type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }

    fun navigateToBuiltinVideoForPlatform(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        val ctxIndex = _uiState.value.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformId == config.platform.id }
        if (ctxIndex >= 0) {
            _uiState.update { it.copy(
                builtinVideo = it.builtinVideo.copy(platformContextIndex = ctxIndex + 1),
                platformDetail = it.platformDetail.copy(builtinEnteredFromPlatform = true)
            ) }
        }
        emulatorDelegate.navigateToBuiltinVideo(viewModelScope)
    }

    fun navigateToBuiltinControlsForPlatform(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        val ctxIndex = _uiState.value.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformId == config.platform.id }
        if (ctxIndex >= 0) {
            _uiState.update { it.copy(
                builtinVideo = it.builtinVideo.copy(platformContextIndex = ctxIndex + 1),
                platformDetail = it.platformDetail.copy(builtinEnteredFromPlatform = true)
            ) }
        }
        emulatorDelegate.navigateToBuiltinControls(viewModelScope)
    }

    fun getInstalledCoreIds(): Set<String> =
        coreManager.getInstalledCores().map { it.coreId }.toSet()

    fun downloadCore(coreId: String) = routeDownloadCore(this, coreId)
    fun downloadCoreWithNotification(coreId: String) = routeDownloadCoreWithNotification(this, coreId)
    fun deleteCore(coreId: String) = routeDeleteCore(this, coreId)

    fun cycleBuiltinArchitecture(direction: Int) = routeCycleBuiltinArchitecture(this, direction)
    fun setBuiltinShader(value: String) = routeSetBuiltinShader(this, value)
    fun setBuiltinFramesEnabled(enabled: Boolean) = routeSetBuiltinFramesEnabled(this, enabled)
    fun setBuiltinLibretroEnabled(enabled: Boolean) = routeSetBuiltinLibretroEnabled(this, enabled)
    fun setBuiltinFilter(value: String) = routeSetBuiltinFilter(this, value)
    fun setBuiltinAspectRatio(value: String) = routeSetBuiltinAspectRatio(this, value)
    fun setBuiltinSkipDuplicateFrames(enabled: Boolean) = routeSetBuiltinSkipDuplicateFrames(this, enabled)
    fun setBuiltinLowLatencyAudio(enabled: Boolean) = routeSetBuiltinLowLatencyAudio(this, enabled)
    fun setBuiltinVSync(enabled: Boolean) = routeSetBuiltinVSync(this, enabled)
    fun setBuiltinFastForwardEnabled(enabled: Boolean) = routeSetBuiltinFastForwardEnabled(this, enabled)
    fun setBuiltinRewindEnabled(enabled: Boolean) = routeSetBuiltinRewindEnabled(this, enabled)
    fun setBuiltinAutoSaveState(enabled: Boolean) = routeSetBuiltinAutoSaveState(this, enabled)
    fun setBuiltinAutoRestoreState(enabled: Boolean) = routeSetBuiltinAutoRestoreState(this, enabled)
    fun setBuiltinSavePath(path: String) = routeSetBuiltinSavePath(this, path)
    fun resetBuiltinSavePath() = routeResetBuiltinSavePath(this)
    fun setBuiltinStatePath(path: String) = routeSetBuiltinStatePath(this, path)
    fun resetBuiltinStatePath() = routeResetBuiltinStatePath(this)
    fun setPlatformBuiltinSavePath(platformId: Long, path: String) = routeSetPlatformBuiltinSavePath(this, platformId, path)
    fun resetPlatformBuiltinSavePath(platformId: Long) = routeResetPlatformBuiltinSavePath(this, platformId)
    fun setPlatformBuiltinStatePath(platformId: Long, path: String) = routeSetPlatformBuiltinStatePath(this, platformId, path)
    fun resetPlatformBuiltinStatePath(platformId: Long) = routeResetPlatformBuiltinStatePath(this, platformId)

    fun openLaunchArgsModal(platformId: Long) = routeOpenLaunchArgsModal(this, platformId)
    fun closeLaunchArgsModal() = routeCloseLaunchArgsModal(this)
    fun moveLaunchArgsFocus(delta: Int) = routeMoveLaunchArgsFocus(this, delta)
    fun cycleLaunchArgsMethod() = routeCycleLaunchArgsMethod(this)
    fun cycleLaunchArgsDataBinding(direction: Int = 1) = routeCycleLaunchArgsDataBinding(this, direction)
    fun cycleLaunchArgsExtraBinding(direction: Int = 1) = routeCycleLaunchArgsExtraBinding(this, direction)
    fun cycleLaunchArgsClipDataBinding(direction: Int = 1) = routeCycleLaunchArgsClipDataBinding(this, direction)
    fun toggleLaunchArgsFlag(flagBit: Int) = routeToggleLaunchArgsFlag(this, flagBit)
    fun cycleLaunchArgsMimeType(direction: Int = 1) = routeCycleLaunchArgsMimeType(this, direction)
    fun resetLaunchArgsFocused() = routeResetLaunchArgsFocused(this)
    fun resetAllLaunchArgs() = routeResetAllLaunchArgs(this)
    fun openLaunchArgsCustomExtras() = routeOpenLaunchArgsCustomExtras(this)
    fun closeLaunchArgsCustomExtras() = routeCloseLaunchArgsCustomExtras(this)
    fun saveLaunchArgsCustomExtras(raw: String) = routeSaveLaunchArgsCustomExtras(this, raw)

    fun openAppPickerModal(platformId: Long) = routeOpenAppPickerModal(this, platformId)
    fun closeAppPickerModal() = routeCloseAppPickerModal(this)
    fun moveAppPickerFocus(delta: Int) = routeMoveAppPickerFocus(this, delta)
    fun confirmAppPickerSelection() = routeConfirmAppPickerSelection(this)
    fun setBuiltinRumbleEnabled(enabled: Boolean) = routeSetBuiltinRumbleEnabled(this, enabled)
    fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) = routeSetBuiltinLimitHotkeysToPlayer1(this, enabled)
    fun setBuiltinFastForwardMode(mode: com.nendo.argosy.data.local.entity.FastForwardMode) = routeSetBuiltinFastForwardMode(this, mode)
    fun setBuiltinFastForwardPreservePitch(enabled: Boolean) = routeSetBuiltinFastForwardPreservePitch(this, enabled)
    fun setBuiltinAnalogAsDpad(enabled: Boolean) = routeSetBuiltinAnalogAsDpad(this, enabled)
    fun setBuiltinDpadAsAnalog(enabled: Boolean) = routeSetBuiltinDpadAsAnalog(this, enabled)
    fun showControllerOrderModal() = routeShowControllerOrderModal(this)
    fun hideControllerOrderModal() = routeHideControllerOrderModal(this)
    fun assignControllerToPort(port: Int, device: android.view.InputDevice) = routeAssignControllerToPort(this, port, device)
    fun clearControllerOrder() = routeClearControllerOrder(this)
    fun getControllerOrder() = inputConfigRepository.observeControllerOrder()
    fun showInputMappingModal() = routeShowInputMappingModal(this)
    fun hideInputMappingModal() = routeHideInputMappingModal(this)
    fun setTouchEnabled(enabled: Boolean) = routeSetTouchEnabled(this, enabled)
    fun setTouchOpacityLandscape(value: Float) = routeSetTouchOpacityLandscape(this, value)
    fun setTouchOpacityPortrait(value: Float) = routeSetTouchOpacityPortrait(this, value)
    fun setTouchSizeScale(value: Float) = routeSetTouchSizeScale(this, value)
    fun setTouchHaptic(enabled: Boolean) = routeSetTouchHaptic(this, enabled)
    fun setTouchFadeOnIdle(enabled: Boolean) = routeSetTouchFadeOnIdle(this, enabled)
    fun setTouchSwapHanded(enabled: Boolean) = routeSetTouchSwapHanded(this, enabled)
    fun setTouchLockOrientation(enabled: Boolean) = routeSetTouchLockOrientation(this, enabled)
    fun setTouchMirror180(enabled: Boolean) = routeSetTouchMirror180(this, enabled)
    fun setTouchColouredFaceButtons(enabled: Boolean) = routeSetTouchColouredFaceButtons(this, enabled)
    fun setTouchGenesis6Button(enabled: Boolean) = routeSetTouchGenesis6Button(this, enabled)
    fun showTouchLayoutEditor() = routeShowTouchLayoutEditor(this)
    fun hideTouchLayoutEditor() = routeHideTouchLayoutEditor(this)
    fun getConnectedControllers() = inputConfigRepository.getConnectedControllers()

    suspend fun getControllerMapping(
        controller: com.nendo.argosy.data.repository.ControllerInfo,
        platformId: String? = null
    ): Pair<Map<com.nendo.argosy.data.repository.InputSource, Int>, String?> {
        val device = android.view.InputDevice.getDevice(controller.deviceId)
            ?: return Pair(emptyMap(), null)
        val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(device, platformId)
        val entity = inputConfigRepository.observeControllerMappings().first()
            .find { it.controllerId == controller.controllerId && it.platformId == platformId }
            ?: inputConfigRepository.observeControllerMappings().first()
                .find { it.controllerId == controller.controllerId && it.platformId == null }
        return Pair(mapping, entity?.presetName)
    }

    suspend fun saveControllerMapping(
        controller: com.nendo.argosy.data.repository.ControllerInfo,
        mapping: Map<com.nendo.argosy.data.repository.InputSource, Int>,
        presetName: String?,
        isAutoDetected: Boolean,
        platformId: String? = null
    ) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.saveExtendedMapping(device, mapping, presetName, isAutoDetected, platformId)
    }

    suspend fun applyControllerPreset(controller: com.nendo.argosy.data.repository.ControllerInfo, presetName: String) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.applyPreset(device, presetName)
    }

    fun showHotkeysModal() = routeShowHotkeysModal(this)
    fun hideHotkeysModal() = routeHideHotkeysModal(this)
    fun observeHotkeys() = inputConfigRepository.observeHotkeys()

    suspend fun saveHotkey(action: com.nendo.argosy.data.local.entity.HotkeyAction, keyCodes: List<Int>) {
        inputConfigRepository.setHotkey(action, keyCodes)
    }

    suspend fun clearHotkey(action: com.nendo.argosy.data.local.entity.HotkeyAction) {
        inputConfigRepository.deleteHotkey(action)
    }

    suspend fun setHotkeyHoldMs(
        action: com.nendo.argosy.data.local.entity.HotkeyAction,
        holdMs: Long
    ) {
        inputConfigRepository.setHotkeyHoldMs(action, holdMs)
    }

    suspend fun saveCoreControlHotkey(
        coreId: String,
        retropadId: Int,
        mode: com.nendo.argosy.data.local.entity.CoreInputMode,
        keyCodes: List<Int>
    ) {
        inputConfigRepository.setCoreControlHotkey(
            id = null,
            keyCodes = keyCodes,
            retropadId = retropadId,
            mode = mode,
            coreId = coreId
        )
    }

    suspend fun deleteCoreBind(id: Long) {
        inputConfigRepository.deleteHotkeyById(id)
    }

    fun setBuiltinBlackFrameInsertion(enabled: Boolean) = routeSetBuiltinBlackFrameInsertion(this, enabled)
    fun cycleBuiltinShader(direction: Int) = routeCycleBuiltinShader(this, direction)

    internal val _shaderRegistry by lazy {
        com.nendo.argosy.libretro.shader.ShaderRegistry(context)
    }
    internal val _shaderDownloader by lazy {
        com.nendo.argosy.libretro.shader.ShaderDownloader(_shaderRegistry.getCatalogDir())
    }

    fun getFrameRegistry(): com.nendo.argosy.libretro.frame.FrameRegistry = frameRegistry

    val shaderChainManager by lazy { routeInitShaderChainManager(this) }

    fun getShaderRegistry(): com.nendo.argosy.libretro.shader.ShaderRegistry = _shaderRegistry
    fun openShaderChainConfig() = routeOpenShaderChainConfig(this)
    fun openFrameConfig() = routeOpenFrameConfig(this)
    fun downloadAndSelectFrame(frameId: String) = routeDownloadAndSelectFrame(this, frameId)

    fun addShaderToStack(id: String, name: String) = shaderChainManager.addShaderToStack(id, name)
    fun removeShaderFromStack() = shaderChainManager.removeShaderFromStack()
    fun reorderShaderInStack(direction: Int) = shaderChainManager.reorderShaderInStack(direction)
    fun selectShaderInStack(index: Int) = shaderChainManager.selectShaderInStack(index)
    fun cycleShaderTab(direction: Int) = shaderChainManager.cycleShaderTab(direction)
    fun showShaderPicker() = shaderChainManager.showShaderPicker()
    fun dismissShaderPicker() = shaderChainManager.dismissShaderPicker()
    fun setShaderPickerFocusIndex(index: Int) = shaderChainManager.setShaderPickerFocusIndex(index)
    fun moveShaderPickerFocus(delta: Int) = shaderChainManager.moveShaderPickerFocus(delta)
    fun jumpShaderPickerSection(direction: Int) = shaderChainManager.jumpShaderPickerSection(direction)
    fun confirmShaderPickerSelection() = shaderChainManager.confirmShaderPickerSelection()
    fun moveShaderParamFocus(delta: Int) = shaderChainManager.moveShaderParamFocus(delta)
    fun adjustShaderParam(direction: Int) = shaderChainManager.adjustShaderParam(direction)
    fun resetShaderParam() = shaderChainManager.resetShaderParam()

    fun cycleBuiltinFilter(direction: Int) = routeCycleBuiltinFilter(this, direction)
    fun cycleBuiltinAspectRatio(direction: Int) = routeCycleBuiltinAspectRatio(this, direction)
    fun cycleBuiltinFastForwardSpeed(direction: Int) = routeCycleBuiltinFastForwardSpeed(this, direction)
    fun cycleBuiltinRotation(direction: Int) = routeCycleBuiltinRotation(this, direction)
    fun cycleBuiltinOverscanCrop(direction: Int) = routeCycleBuiltinOverscanCrop(this, direction)
    fun cycleBuiltinRewindSpeed(direction: Int) = routeCycleBuiltinRewindSpeed(this, direction)
    fun cycleBuiltinRewindBufferDuration(direction: Int) = routeCycleBuiltinRewindBufferDuration(this, direction)
    fun updatePlatformLibretroSetting(setting: LibretroSettingDef, value: String?) = routeUpdatePlatformLibretroSetting(this, setting, value)
    fun resetAllPlatformLibretroSettings() = routeResetAllPlatformLibretroSettings(this)
    fun updatePlatformControlSetting(field: String, value: Boolean?) = routeUpdatePlatformControlSetting(this, field, value)
    fun resetAllPlatformControlSettings() = routeResetAllPlatformControlSettings(this)
    fun loadCoreManagementState(preserveFocus: Boolean = false) = routeLoadCoreManagementState(this, preserveFocus)
    fun moveCoreManagementPlatformFocus(delta: Int): Boolean = routeMoveCoreManagementPlatformFocus(this, delta)
    fun moveCoreManagementCoreFocus(delta: Int): Boolean = routeMoveCoreManagementCoreFocus(this, delta)
    fun selectCoreForPlatform() = routeSelectCoreForPlatform(this)

    fun loadCoreOptionsState() = routeLoadCoreOptionsState(this)
    fun cycleCoreOptionsPlatformContext(direction: Int) = routeCycleCoreOptionsPlatformContext(this, direction)
    fun cycleCoreSelector(direction: Int) = routeCycleCoreSelector(this, direction)
    fun cycleCoreOptionValue(optionKey: String, direction: Int) = routeCycleCoreOptionValue(this, optionKey, direction)
    fun resetCoreOption(optionKey: String) = routeResetCoreOption(this, optionKey)
    fun resetAllCoreOptions() = routeResetAllCoreOptions(this)

    fun movePlatformSubFocus(delta: Int, maxIndex: Int): Boolean =
        emulatorDelegate.movePlatformSubFocus(delta, maxIndex)
    fun resetPlatformSubFocus() = emulatorDelegate.resetPlatformSubFocus()
    fun cycleCoreForPlatform(config: PlatformEmulatorConfig, direction: Int) =
        emulatorDelegate.cycleCoreForPlatform(viewModelScope, config, direction) { loadSettings() }
    fun changeExtensionForPlatform(config: PlatformEmulatorConfig, extension: String) =
        emulatorDelegate.changeExtensionForPlatform(viewModelScope, config.platform.id, extension) { loadSettings() }
    fun toggleLegacyMode(config: PlatformEmulatorConfig) =
        emulatorDelegate.toggleLegacyMode(viewModelScope, config) { loadSettings() }
    fun cycleDisplayTarget(config: PlatformEmulatorConfig, direction: Int) =
        emulatorDelegate.cycleDisplayTarget(viewModelScope, config, direction) { loadSettings() }

    fun cycleExtensionForPlatform(config: PlatformEmulatorConfig, direction: Int) =
        routeCycleExtensionForPlatform(this, config, direction)

    fun moveEmulatorPickerFocus(delta: Int) = emulatorDelegate.moveEmulatorPickerFocus(delta)

    fun confirmEmulatorPickerSelection() = routeConfirmEmulatorPickerSelection(this)
    fun handleEmulatorPickerItemTap(index: Int) = routeHandleEmulatorPickerItemTap(this, index)

    fun setEmulatorSavePath(emulatorId: String, path: String) =
        emulatorDelegate.setEmulatorSavePath(viewModelScope, emulatorId, path) { loadSettings() }
    fun resetEmulatorSavePath(emulatorId: String) =
        emulatorDelegate.resetEmulatorSavePath(viewModelScope, emulatorId) { loadSettings() }

    fun showSavePathModal(config: PlatformEmulatorConfig) = routeShowSavePathModal(this, config)

    fun dismissSavePathModal() = emulatorDelegate.dismissSavePathModal()
    fun toggleSavesBesideRom() = emulatorDelegate.toggleSavesBesideRom(viewModelScope)
    fun moveSavePathModalFocus(delta: Int) = emulatorDelegate.moveSavePathModalFocus(delta)
    fun moveSavePathModalButtonFocus(delta: Int) = emulatorDelegate.moveSavePathModalButtonFocus(delta)

    fun confirmSavePathModalSelection() = routeConfirmSavePathModalSelection(this)

    fun openMemcardPicker(config: PlatformEmulatorConfig) {
        val emulatorId = config.effectiveEmulatorId ?: return
        emulatorDelegate.showMemcardPicker(
            scope = viewModelScope,
            emulatorId = emulatorId,
            emulatorName = config.effectiveEmulatorName ?: emulatorId,
            emulatorPackage = config.effectiveEmulatorPackage,
            platformName = config.platform.name
        )
    }
    fun dismissMemcardPicker() = emulatorDelegate.dismissMemcardPicker()
    fun moveMemcardPickerFocus(delta: Int) = emulatorDelegate.moveMemcardPickerFocus(delta)
    fun confirmMemcardSelection(cardPath: String) =
        emulatorDelegate.confirmMemcardSelection(viewModelScope, cardPath) { loadSettings() }
    fun handleMemcardPickerItemTap(index: Int) {
        val info = uiState.value.emulators.memcardPickerInfo ?: return
        val card = info.cards.getOrNull(index) ?: return
        confirmMemcardSelection(card.path)
    }
    fun resetMemcardSelection(emulatorId: String) =
        emulatorDelegate.clearMemcardSelection(viewModelScope, emulatorId) { loadSettings() }
    fun forceCheckEmulatorUpdates() = routeForceCheckEmulatorUpdates(this)
    fun triggerEmulatorUpdate(emulatorId: String) = emulatorDelegate.triggerUpdateForEmulator(emulatorId, viewModelScope)
    fun selectUpdateModalVariant() = emulatorDelegate.selectUpdateModalVariant()
    fun moveUpdateModalFocus(delta: Int) = emulatorDelegate.moveUpdateModalFocus(delta)
    fun dismissUpdateModal() = emulatorDelegate.dismissUpdateModal()
    fun handlePlatformItemTap(index: Int) = routeHandlePlatformItemTap(this, index)

    fun navigateToSection(section: SettingsSection) = routeNavigateToSection(this, section)

    fun setFocusIndex(index: Int) { _uiState.update { it.copy(focusedIndex = index) } }

    fun moveFocusWrapped(delta: Int, maxIndex: Int) {
        _uiState.update {
            it.copy(focusedIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                it.focusedIndex, delta, maxIndex, it.controls.menuWrapMode
            ))
        }
    }

    fun refreshSteamSettings() = steamDelegate.loadSteamSettings(context, viewModelScope)

    fun moveLauncherActionFocus(delta: Int) = routeMoveLauncherActionFocus(this, delta)
    fun confirmLauncherAction() = routeConfirmLauncherAction(this)

    fun scanForAndroidGames() = routeScanForAndroidGames(this)

    // Steam integration (new flow)
    fun connectToSteam() = steamDelegate.connectToSteam(context, viewModelScope)
    fun startSteamQrAuth() = steamDelegate.startQrAuth()
    fun cancelSteamQrAuth() = steamDelegate.cancelQrAuth()
    fun syncSteamLibrary() = steamDelegate.syncLibrary(context, viewModelScope)
    fun forceSyncSteamLibrary() = steamDelegate.forceSyncLibraryWithOverwrite(context, viewModelScope)
    fun disconnectSteam() = steamDelegate.disconnectSteam(context, viewModelScope)
    fun resetSteamLibrary() = steamDelegate.resetLibrary(viewModelScope)
    fun showAddSteamGameDialog() = steamDelegate.showAddSteamGameDialog()
    @Suppress("UNUSED_PARAMETER")
    fun showAddSteamGameDialog(launcherPackage: String?) = steamDelegate.showAddSteamGameDialog()
    fun dismissAddSteamGameDialog() = steamDelegate.dismissAddSteamGameDialog()
    fun setAddGameAppId(appId: String) = steamDelegate.setAddGameAppId(appId)
    fun confirmAddSteamGame() = steamDelegate.confirmAddSteamGame(context, viewModelScope)
    fun cycleSteamInstallVolume(direction: Int = 1) = steamDelegate.cycleSteamInstallVolume(viewModelScope, direction)
    fun openSteamInstallPathPicker() =
        storageDelegate.openPlatformFolderPicker(viewModelScope, com.nendo.argosy.data.platform.LocalPlatformIds.STEAM)
    fun resetSteamInstallPath() =
        storageDelegate.resetPlatformToGlobal(viewModelScope, com.nendo.argosy.data.platform.LocalPlatformIds.STEAM)

    // Legacy Steam methods (used by GameDataSection/routers)
    fun scanSteamLauncher(packageName: String) = steamDelegate.scanSteamLauncher(context, viewModelScope, packageName)
    fun installSteamLauncher(emulatorId: String) = steamDelegate.installSteamLauncher(emulatorId, viewModelScope)
    fun refreshSteamMetadata() {}
    fun moveSteamVariantFocus(delta: Int) {}
    fun confirmSteamVariantSelection() {}
    fun dismissSteamVariantPicker() {}
    @Suppress("UNUSED_PARAMETER")
    fun handleSteamVariantItemTap(index: Int) {}
    fun checkRommConnection() = serverDelegate.checkRommConnection(viewModelScope)

    fun navigateBack(): Boolean = routeNavigateBack(this)

    fun moveFocus(delta: Int) = routeMoveFocus(this, delta)

    fun moveColorFocus(delta: Int) = routeMoveColorFocus(this, delta)

    fun selectFocusedColor() = displayDelegate.selectFocusedColor(viewModelScope)
    fun setThemeMode(mode: com.nendo.argosy.data.preferences.ThemeMode) = displayDelegate.setThemeMode(viewModelScope, mode)

    fun cycleThemeMode(direction: Int = 1) = routeCycleThemeMode(this, direction)

    fun setPrimaryColor(color: Int?) = displayDelegate.setPrimaryColor(viewModelScope, color)
    fun adjustHue(delta: Float) = displayDelegate.adjustHue(viewModelScope, delta)
    fun resetToDefaultColor() = displayDelegate.resetToDefaultColor(viewModelScope)
    fun setSecondaryColor(color: Int?) = displayDelegate.setSecondaryColor(viewModelScope, color)
    fun adjustSecondaryHue(delta: Float) = displayDelegate.adjustSecondaryHue(viewModelScope, delta)
    fun resetToDefaultSecondaryColor() = displayDelegate.resetToDefaultSecondaryColor(viewModelScope)
    fun setGridDensity(density: GridDensity) = displayDelegate.setGridDensity(viewModelScope, density)

    fun cycleGridDensity(direction: Int = 1) = routeCycleGridDensity(this, direction)

    fun setUiScale(scale: Int) = displayDelegate.setUiScale(viewModelScope, scale)

    fun adjustUiScale(delta: Int) = routeAdjustUiScale(this, delta)

    fun cycleUiScale() = displayDelegate.cycleUiScale(viewModelScope)

    fun adjustBackgroundBlur(delta: Int) = routeAdjustBackgroundBlur(this, delta)
    fun adjustBackgroundSaturation(delta: Int) = routeAdjustBackgroundSaturation(this, delta)
    fun adjustBackgroundOpacity(delta: Int) = routeAdjustBackgroundOpacity(this, delta)
    fun cycleBackgroundBlur() = routeCycleBackgroundBlur(this)
    fun cycleBackgroundSaturation() = routeCycleBackgroundSaturation(this)
    fun cycleBackgroundOpacity() = routeCycleBackgroundOpacity(this)

    fun setUseGameBackground(use: Boolean) = displayDelegate.setUseGameBackground(viewModelScope, use)
    fun setUseAccentColorFooter(use: Boolean) = displayDelegate.setUseAccentColorFooter(viewModelScope, use)
    fun setCustomBackgroundPath(path: String?) = displayDelegate.setCustomBackgroundPath(viewModelScope, path)
    fun openBackgroundPicker() = displayDelegate.openBackgroundPicker(viewModelScope)

    fun navigateToBoxArt() = routeNavigateToBoxArt(this)
    fun navigateToHomeScreen() = routeNavigateToHomeScreen(this)
    fun navigateToAmbientLed() = routeNavigateToAmbientLed(this)

    fun cycleBoxArtShape(direction: Int = 1) = displayDelegate.cycleBoxArtShape(viewModelScope, direction)
    fun cycleBoxArtCornerRadius(direction: Int = 1) = displayDelegate.cycleBoxArtCornerRadius(viewModelScope, direction)
    fun cycleBoxArtBorderThickness(direction: Int = 1) = displayDelegate.cycleBoxArtBorderThickness(viewModelScope, direction)
    fun cycleBoxArtBorderStyle(direction: Int = 1) = displayDelegate.cycleBoxArtBorderStyle(viewModelScope, direction)
    fun cycleGlassBorderTint(direction: Int = 1) = displayDelegate.cycleGlassBorderTint(viewModelScope, direction)

    fun cycleGradientPreset(direction: Int = 1) = routeCycleGradientPreset(this, direction)
    fun toggleGradientAdvancedMode() = routeToggleGradientAdvancedMode(this)

    fun cycleBoxArtGlowStrength(direction: Int = 1) = displayDelegate.cycleBoxArtGlowStrength(viewModelScope, direction)
    fun cycleBoxArtOuterEffect(direction: Int = 1) = displayDelegate.cycleBoxArtOuterEffect(viewModelScope, direction)
    fun cycleBoxArtOuterEffectThickness(direction: Int = 1) = displayDelegate.cycleBoxArtOuterEffectThickness(viewModelScope, direction)
    fun cycleGlowColorMode(direction: Int = 1) = displayDelegate.cycleGlowColorMode(viewModelScope, direction)
    fun cycleSystemIconPosition(direction: Int = 1) = displayDelegate.cycleSystemIconPosition(viewModelScope, direction)
    fun cycleSystemIconPadding(direction: Int = 1) = displayDelegate.cycleSystemIconPadding(viewModelScope, direction)
    fun cyclePlatformIndicatorStyle(direction: Int = 1) = displayDelegate.cyclePlatformIndicatorStyle(viewModelScope, direction)
    fun cyclePlatformIndicatorContent(direction: Int = 1) = displayDelegate.cyclePlatformIndicatorContent(viewModelScope, direction)
    fun cycleBoxArtInnerEffect(direction: Int = 1) = displayDelegate.cycleBoxArtInnerEffect(viewModelScope, direction)
    fun cycleBoxArtInnerEffectThickness(direction: Int = 1) = displayDelegate.cycleBoxArtInnerEffectThickness(viewModelScope, direction)
    fun cycleDefaultView() = displayDelegate.cycleDefaultView(viewModelScope)
    fun setVideoWallpaperEnabled(enabled: Boolean) = displayDelegate.setVideoWallpaperEnabled(viewModelScope, enabled)
    fun cycleVideoWallpaperDelay() = displayDelegate.cycleVideoWallpaperDelay(viewModelScope)
    fun setVideoWallpaperMuted(muted: Boolean) = displayDelegate.setVideoWallpaperMuted(viewModelScope, muted)
    fun setAmbientLedEnabled(enabled: Boolean) = displayDelegate.setAmbientLedEnabled(viewModelScope, enabled)
    fun setAmbientLedBrightness(brightness: Int) = displayDelegate.setAmbientLedBrightness(viewModelScope, brightness)
    fun adjustAmbientLedBrightness(delta: Int) = displayDelegate.adjustAmbientLedBrightness(viewModelScope, delta)
    fun cycleAmbientLedBrightness() = displayDelegate.cycleAmbientLedBrightness(viewModelScope)
    fun setAmbientLedAudioBrightness(enabled: Boolean) = displayDelegate.setAmbientLedAudioBrightness(viewModelScope, enabled)
    fun setAmbientLedAudioColors(enabled: Boolean) = displayDelegate.setAmbientLedAudioColors(viewModelScope, enabled)
    fun cycleAmbientLedColorMode(direction: Int = 1) = displayDelegate.cycleAmbientLedColorMode(viewModelScope, direction)
    fun setAmbientLedCoverArtEnabled(enabled: Boolean) = displayDelegate.setAmbientLedCoverArtEnabled(viewModelScope, enabled)
    fun setAmbientLedCustomColor(enabled: Boolean) = displayDelegate.setAmbientLedCustomColor(viewModelScope, enabled)
    fun setAmbientLedCustomColorHue(hue: Int) = displayDelegate.setAmbientLedCustomColorHue(viewModelScope, hue)
    fun adjustAmbientLedCustomColorHue(delta: Int) = displayDelegate.adjustAmbientLedCustomColorHue(viewModelScope, delta)
    fun cycleAmbientLedTransitionMs(direction: Int) = displayDelegate.cycleAmbientLedTransitionMs(viewModelScope, direction)
    fun cycleAmbientLedTransitionMsWrap() = displayDelegate.cycleAmbientLedTransitionMsWrap(viewModelScope)
    fun setAmbientLedScreenEnabled(enabled: Boolean) = displayDelegate.setAmbientLedScreenEnabled(viewModelScope, enabled)
    fun setInstalledOnlyHome(enabled: Boolean) = displayDelegate.setInstalledOnlyHome(viewModelScope, enabled)

    fun loadPreviewGames() = routeLoadPreviewGames(this)
    fun cyclePrevPreviewGame() = routeCyclePrevPreviewGame(this)
    fun cycleNextPreviewGame() = routeCycleNextPreviewGame(this)
    fun extractGradientForPreview() = routeExtractGradientForPreview(this)

    fun cycleGradientSampleGrid(direction: Int) = routeCycleGradientSampleGrid(this, direction)
    fun cycleGradientRadius(direction: Int) = routeCycleGradientRadius(this, direction)
    fun cycleGradientMinSaturation(direction: Int) = routeCycleGradientMinSaturation(this, direction)
    fun cycleGradientMinValue(direction: Int) = routeCycleGradientMinValue(this, direction)
    fun cycleGradientHueDistance(direction: Int) = routeCycleGradientHueDistance(this, direction)
    fun cycleGradientSaturationBump(direction: Int) = routeCycleGradientSaturationBump(this, direction)
    fun cycleGradientValueClamp(direction: Int) = routeCycleGradientValueClamp(this, direction)

    fun setHapticEnabled(enabled: Boolean) = controlsDelegate.setHapticEnabled(viewModelScope, enabled)

    fun cycleVibrationStrength() = controlsDelegate.adjustVibrationStrength(0.1f)

    fun adjustVibrationStrength(delta: Float) = routeAdjustVibrationStrength(this, delta)

    fun setSoundEnabled(enabled: Boolean) = soundsDelegate.setSoundEnabled(viewModelScope, enabled)

    fun setBetaUpdatesEnabled(enabled: Boolean) = routeSetBetaUpdatesEnabled(this, enabled)
    fun setAppAffinityEnabled(enabled: Boolean) = routeSetAppAffinityEnabled(this, enabled)

    fun setDualScreenEnabled(enabled: Boolean) = routeSetDualScreenEnabled(this, enabled)

    fun cycleDisplayRoleOverride(direction: Int = 1) = routeCycleDisplayRoleOverride(this, direction)

    fun setSoundVolume(volume: Int) = soundsDelegate.setSoundVolume(viewModelScope, volume)

    fun adjustSoundVolume(delta: Int) = routeAdjustSoundVolume(this, delta)
    fun cycleSoundVolume() = routeCycleSoundVolume(this)

    fun showSoundPicker(type: SoundType) = soundsDelegate.showSoundPicker(type)
    fun dismissSoundPicker() = soundsDelegate.dismissSoundPicker()
    fun moveSoundPickerFocus(delta: Int) = soundsDelegate.moveSoundPickerFocus(delta)
    fun previewSoundPickerSelection() = soundsDelegate.previewSoundPickerSelection()
    fun confirmSoundPickerSelection() = soundsDelegate.confirmSoundPickerSelection(viewModelScope)
    fun setCustomSoundFile(type: SoundType, filePath: String) = soundsDelegate.setCustomSoundFile(viewModelScope, type, filePath)
    fun setAmbientAudioEnabled(enabled: Boolean) = ambientAudioDelegate.setEnabled(viewModelScope, enabled)
    fun setAmbientAudioVolume(volume: Int) = ambientAudioDelegate.setVolume(viewModelScope, volume)

    fun adjustAmbientAudioVolume(delta: Int) = routeAdjustAmbientAudioVolume(this, delta)
    fun cycleAmbientAudioVolume() = routeCycleAmbientAudioVolume(this)

    fun openAudioFilePicker() = ambientAudioDelegate.openFilePicker(viewModelScope)
    fun openAudioFileBrowser() = ambientAudioDelegate.openFileBrowser(viewModelScope)
    fun setAmbientAudioUri(uri: String?) = ambientAudioDelegate.setAudioSource(viewModelScope, uri)
    fun setAmbientAudioFilePath(path: String?) = ambientAudioDelegate.setAudioSource(viewModelScope, path)
    fun setAmbientAudioShuffle(shuffle: Boolean) = ambientAudioDelegate.setShuffle(viewModelScope, shuffle)
    fun clearAmbientAudioFile() = ambientAudioDelegate.clearAudioFile(viewModelScope)
    fun setSwapAB(enabled: Boolean) = controlsDelegate.setSwapAB(viewModelScope, enabled)
    fun setSwapXY(enabled: Boolean) = controlsDelegate.setSwapXY(viewModelScope, enabled)
    fun cycleControllerLayout() = controlsDelegate.cycleControllerLayout(viewModelScope)
    fun refreshDetectedLayout() = controlsDelegate.refreshDetectedLayout()
    fun setSwapStartSelect(enabled: Boolean) = controlsDelegate.setSwapStartSelect(viewModelScope, enabled)
    fun cycleSelectLCombo() = controlsDelegate.cycleSelectLCombo(viewModelScope)
    fun cycleSelectRCombo() = controlsDelegate.cycleSelectRCombo(viewModelScope)
    fun cycleMenuWrapMode() = controlsDelegate.cycleMenuWrapMode(viewModelScope)
    fun setAccuratePlayTimeEnabled(enabled: Boolean) = controlsDelegate.setAccuratePlayTimeEnabled(viewModelScope, enabled)
    fun refreshUsageStatsPermission() = controlsDelegate.refreshUsageStatsPermission()
    fun openUsageStatsSettings() = controlsDelegate.openUsageStatsSettings()
    fun openStorageSettings() = permissionsDelegate.openStorageSettings()
    fun openNotificationSettings() = permissionsDelegate.openNotificationSettings()
    fun openWriteSettings() = permissionsDelegate.openWriteSettings()
    fun openDisplayOverlaySettings() = permissionsDelegate.openDisplayOverlaySettings()

    fun requestScreenCapturePermission() = routeRequestScreenCapturePermission(this)
    fun refreshPermissions() = permissionsDelegate.refreshPermissions()
    internal fun handlePlayTimeToggle(controls: ControlsState) = routeHandlePlayTimeToggle(this, controls)

    fun showSyncFiltersModal() = routeShowSyncFiltersModal(this)
    fun dismissSyncFiltersModal() = routeDismissSyncFiltersModal(this)

    fun moveSyncFiltersModalFocus(delta: Int) = syncDelegate.moveSyncFiltersModalFocus(delta)
    fun confirmSyncFiltersModalSelection() = syncDelegate.confirmSyncFiltersModalSelection(viewModelScope)

    fun showPlatformFiltersModal() = routeShowPlatformFiltersModal(this)
    fun dismissPlatformFiltersModal() = routeDismissPlatformFiltersModal(this)

    fun platformFiltersUp() = syncDelegate.platformFiltersUp()
    fun platformFiltersDown() = syncDelegate.platformFiltersDown()
    fun platformFiltersLeft() = syncDelegate.platformFiltersLeft()
    fun platformFiltersRight() = syncDelegate.platformFiltersRight()
    fun platformFiltersConfirm() = syncDelegate.platformFiltersConfirm(viewModelScope)
    fun platformFiltersBack() = syncDelegate.platformFiltersBack()
    fun openPlatformSearch() = syncDelegate.openPlatformSearch()
    fun closePlatformSearch() = syncDelegate.closePlatformSearch()
    fun openPlatformSortMenu() = syncDelegate.openPlatformSortMenu()
    fun closePlatformSortMenu() = syncDelegate.closePlatformSortMenu()
    fun togglePlatformSyncEnabled(platformId: Long) = syncDelegate.togglePlatformSyncEnabled(viewModelScope, platformId)

    fun setPlatformFilterSortMode(mode: PlatformFilterLogic.SortMode) = syncDelegate.setPlatformFilterSortMode(mode)
    fun setPlatformFilterSearchQuery(query: String) = syncDelegate.setPlatformFilterSearchQuery(query)
    fun cyclePlatformFilterMode() = syncDelegate.cyclePlatformFilterMode()

    fun showRegionPicker() = routeShowRegionPicker(this)
    fun dismissRegionPicker() = routeDismissRegionPicker(this)

    fun moveRegionPickerFocus(delta: Int) = syncDelegate.moveRegionPickerFocus(delta)
    fun confirmRegionPickerSelection() = syncDelegate.confirmRegionPickerSelection(viewModelScope)
    fun toggleRegion(region: String) = syncDelegate.toggleRegion(viewModelScope, region)
    fun toggleRegionMode() = syncDelegate.toggleRegionMode(viewModelScope)
    fun setExcludeBeta(exclude: Boolean) = syncDelegate.setExcludeBeta(viewModelScope, exclude)
    fun setExcludePrototype(exclude: Boolean) = syncDelegate.setExcludePrototype(viewModelScope, exclude)
    fun setExcludeDemo(exclude: Boolean) = syncDelegate.setExcludeDemo(viewModelScope, exclude)
    fun setExcludeHack(exclude: Boolean) = syncDelegate.setExcludeHack(viewModelScope, exclude)
    fun setDeleteOrphans(delete: Boolean) = syncDelegate.setDeleteOrphans(viewModelScope, delete)

    fun toggleSyncScreenshots() = routeToggleSyncScreenshots(this)

    fun enableSaveSync() = syncDelegate.enableSaveSync(viewModelScope)
    fun toggleSaveSync() = syncDelegate.toggleSaveSync(viewModelScope)
    fun cycleSaveCacheLimit() = syncDelegate.cycleSaveCacheLimit(viewModelScope)

    fun onStoragePermissionResult(granted: Boolean) = routeOnStoragePermissionResult(this, granted)

    fun onNotificationPermissionResult(granted: Boolean) = syncDelegate.onNotificationPermissionResult(viewModelScope, granted)
    fun runSaveSyncNow() = syncDelegate.runSaveSyncNow(viewModelScope)

    fun requestResetSaveCache() = syncDelegate.requestResetSaveCache()
    fun confirmResetSaveCache() = syncDelegate.confirmResetSaveCache(viewModelScope)
    fun cancelResetSaveCache() = syncDelegate.cancelResetSaveCache()

    fun requestClearPathCache() = syncDelegate.requestClearPathCache()
    fun confirmClearPathCache() = syncDelegate.confirmClearPathCache(viewModelScope)
    fun cancelClearPathCache() = syncDelegate.cancelClearPathCache()

    fun requestSyncSaves() = syncDelegate.requestSyncSaves()
    fun confirmSyncSaves() = syncDelegate.confirmSyncSaves(viewModelScope)
    fun cancelSyncSaves() = syncDelegate.cancelSyncSaves()
    fun moveSyncConfirmFocus(delta: Int) = syncDelegate.moveSyncConfirmFocus(delta)

    fun openImageCachePicker() = syncDelegate.openImageCachePicker(viewModelScope)
    fun moveImageCacheActionFocus(delta: Int) = syncDelegate.moveImageCacheActionFocus(delta)
    fun setImageCachePath(path: String) = syncDelegate.onImageCachePathSelected(viewModelScope, path)
    fun resetImageCacheToDefault() = syncDelegate.resetImageCacheToDefault(viewModelScope)

    fun validateImageCache() = routeValidateImageCache(this)
    fun validateDownloads() = routeValidateDownloads(this)

    fun toggleWeeklyIntegrityCheck(enabled: Boolean) {
        _uiState.update { it.copy(storage = it.storage.copy(weeklyIntegrityCheckEnabled = enabled)) }
        viewModelScope.launch {
            preferencesRepository.setWeeklyIntegrityCheckEnabled(enabled)
        }
    }

    fun cycleMaxConcurrentDownloads() = storageDelegate.cycleMaxConcurrentDownloads(viewModelScope)

    fun adjustMaxConcurrentDownloads(delta: Int) = routeAdjustMaxConcurrentDownloads(this, delta)

    fun cycleInstantDownloadThreshold(direction: Int = 1) = storageDelegate.cycleInstantDownloadThreshold(viewModelScope, direction)
    fun toggleScreenDimmer() = storageDelegate.toggleScreenDimmer(viewModelScope)
    fun cycleScreenDimmerTimeout() = storageDelegate.cycleScreenDimmerTimeout(viewModelScope)

    fun adjustScreenDimmerTimeout(delta: Int) = routeAdjustScreenDimmerTimeout(this, delta)

    fun cycleScreenDimmerLevel() = storageDelegate.cycleScreenDimmerLevel(viewModelScope)

    fun adjustScreenDimmerLevel(delta: Int) = routeAdjustScreenDimmerLevel(this, delta)

    fun openFolderPicker() = storageDelegate.openFolderPicker()
    fun clearFolderPickerFlag() = storageDelegate.clearFolderPickerFlag()
    fun setStoragePath(uriString: String) = storageDelegate.setStoragePath(uriString)
    fun confirmMigration() = storageDelegate.confirmMigration(viewModelScope)
    fun cancelMigration() = storageDelegate.cancelMigration()
    fun skipMigration() = storageDelegate.skipMigration()
    fun confirmBuiltinPathMigration() = routeConfirmBuiltinPathMigration(this)
    fun cancelBuiltinPathMigration() = routeCancelBuiltinPathMigration(this)
    fun skipBuiltinPathMigration() = routeSkipBuiltinPathMigration(this)
    fun togglePlatformSync(platformId: Long, enabled: Boolean) =
        storageDelegate.togglePlatformSync(viewModelScope, platformId, enabled)
    fun enablePlatformAndReload(platformId: Long) {
        storageDelegate.togglePlatformSync(viewModelScope, platformId, true)
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            loadSettings()
        }
    }
    fun openPlatformFolderPicker(platformId: Long) = storageDelegate.openPlatformFolderPicker(viewModelScope, platformId)
    fun setPlatformPath(platformId: Long, path: String) =
        storageDelegate.setPlatformPath(viewModelScope, platformId, path)
    fun resetPlatformToGlobal(platformId: Long) = storageDelegate.resetPlatformToGlobal(viewModelScope, platformId)
    fun syncPlatform(platformId: Long, platformName: String) = storageDelegate.syncPlatform(viewModelScope, platformId, platformName)
    fun openPlatformSavePathPicker(platformId: Long) = storageDelegate.emitSavePathPicker(viewModelScope, platformId)

    fun setPlatformSavePath(platformId: Long, basePath: String) = routeSetPlatformSavePath(this, platformId, basePath)
    fun resetPlatformSavePath(platformId: Long) = routeResetPlatformSavePath(this, platformId)
    fun setPlatformStatePath(platformId: Long, basePath: String) = routeSetPlatformStatePath(this, platformId, basePath)
    fun resetPlatformStatePath(platformId: Long) = routeResetPlatformStatePath(this, platformId)

    fun togglePlatformsExpanded() = storageDelegate.togglePlatformsExpanded()

    fun jumpToNextSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean =
        routeJumpToNextSection(this, sections)
    fun jumpToPrevSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean =
        routeJumpToPrevSection(this, sections)

    fun requestPurgePlatform(platformId: Long) = storageDelegate.requestPurgePlatform(platformId)
    fun confirmPurgePlatform() = storageDelegate.confirmPurgePlatform(viewModelScope)
    fun cancelPurgePlatform() = storageDelegate.cancelPurgePlatform()
    fun requestPurgeAll() = storageDelegate.requestPurgeAll()
    fun confirmPurgeAll() = storageDelegate.confirmPurgeAll(viewModelScope)
    fun cancelPurgeAll() = storageDelegate.cancelPurgeAll()
    fun confirmPlatformMigration() = storageDelegate.confirmPlatformMigration(viewModelScope)
    fun cancelPlatformMigration() = storageDelegate.cancelPlatformMigration()
    fun skipPlatformMigration() = storageDelegate.skipPlatformMigration(viewModelScope)
    fun openPlatformSettingsModal(platformId: Long) = storageDelegate.openPlatformSettingsModal(platformId)
    fun closePlatformSettingsModal() = storageDelegate.closePlatformSettingsModal()
    fun movePlatformSettingsFocus(delta: Int) = storageDelegate.movePlatformSettingsFocus(delta)
    fun movePlatformSettingsButtonFocus(delta: Int) = storageDelegate.movePlatformSettingsButtonFocus(delta)
    fun selectPlatformSettingsOption() = storageDelegate.selectPlatformSettingsOption(viewModelScope)

    fun openLogFolderPicker() = routeOpenLogFolderPicker(this)
    fun setFileLoggingPath(path: String) = routeSetFileLoggingPath(this, path)
    fun toggleFileLogging(enabled: Boolean) = routeToggleFileLogging(this, enabled)
    fun setFileLogLevel(level: LogLevel) = routeSetFileLogLevel(this, level)
    fun cycleFileLogLevel(direction: Int = 1) = routeCycleFileLogLevel(this, direction)
    fun setSaveDebugLoggingEnabled(enabled: Boolean) = routeSetSaveDebugLoggingEnabled(this, enabled)

    fun setPlatformEmulator(platformId: Long, platformSlug: String, emulator: InstalledEmulator?) =
        routeSetPlatformEmulator(this, platformId, platformSlug, emulator)

    fun setRomStoragePath(path: String) = storageDelegate.setRomStoragePath(viewModelScope, path)

    fun syncRomm() = routeSyncRomm(this)

    fun checkForUpdates() = routeCheckForUpdates(this)
    fun downloadAndInstallUpdate(context: android.content.Context) = routeDownloadAndInstallUpdate(this, context)

    fun writeSystemizeScript() {
        _uiState.update { it.copy(systemizeResult = com.nendo.argosy.util.SystemizeScript.write(context)) }
    }

    fun dismissSystemizeDialog() {
        _uiState.update { it.copy(systemizeResult = null) }
    }

    fun startRommConfig() = routeStartRommConfig(this)
    fun cancelRommConfig() = routeCancelRommConfig(this)

    fun setRommConfigUrl(url: String) = serverDelegate.setRommConfigUrl(url)
    fun setRommConfigUsername(username: String) = serverDelegate.setRommConfigUsername(username)
    fun setRommConfigPassword(password: String) = serverDelegate.setRommConfigPassword(password)
    fun setRommConfigPairingCode(code: String) = serverDelegate.setRommConfigPairingCode(code)
    fun setRommAuthMethod(method: RomMAuthMethod) = serverDelegate.setRommAuthMethod(method)
    fun showRommScanner() = serverDelegate.showScanner()
    fun dismissRommScanner() = serverDelegate.dismissScanner()
    fun handleRommScanResult(origin: String, code: String) = serverDelegate.handleScanResult(origin, code, viewModelScope) { loadSettings() }
    fun clearRommFocusField() = serverDelegate.clearRommFocusField()

    fun setRommConfigFocusIndex(index: Int) = setFocusIndex(index)
    fun connectToRomm() = routeConnectToRomm(this)
    fun showRALoginForm() = routeShowRALoginForm(this)
    fun hideRALoginForm() = routeHideRALoginForm(this)

    fun setRALoginUsername(username: String) = raDelegate.setLoginUsername(username)
    fun setRALoginPassword(password: String) = raDelegate.setLoginPassword(password)
    fun clearRAFocusField() = raDelegate.clearFocusField()

    fun loginToRA() = routeLoginToRA(this)
    fun logoutFromRA() = routeLogoutFromRA(this)
    fun setRAProxyEnabled(enabled: Boolean) = routeSetRAProxyEnabled(this, enabled)
    fun setRAProxyAddress(address: String) = raDelegate.setProxyAddress(viewModelScope, address)

    fun handleConfirm(): InputResult = routeConfirm(this)

    fun downloadAllBios() = biosDelegate.downloadAllBios(viewModelScope)
    fun distributeAllBios() = biosDelegate.distributeAllBios(viewModelScope)

    fun openBiosFolderPicker() = biosDelegate.openFolderPicker(viewModelScope)
    fun toggleBiosPlatformExpanded(index: Int) = biosDelegate.togglePlatformExpanded(index)
    fun downloadBiosForPlatform(platformSlug: String) =
        biosDelegate.downloadBiosForPlatform(platformSlug, viewModelScope) {
            loadPlatformDetailStats(_uiState.value.platformDetail.platformIndex)
        }
    fun downloadSingleBios(rommId: Long) = biosDelegate.downloadSingleBios(rommId, viewModelScope)
    fun onBiosFolderSelected(path: String) = biosDelegate.onBiosFolderSelected(path, viewModelScope)

    private var pendingBiosCopyPlatformSlug: String? = null
    val hasPendingBiosCopy: Boolean get() = pendingBiosCopyPlatformSlug != null

    fun requestRemoveLocalFiles() {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = true)) }
    }

    fun dismissRemoveConfirm() {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = false)) }
    }

    fun confirmRemoveLocalFiles(platformId: Long) {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = false)) }
        val platformIndex = _uiState.value.platformDetail.platformIndex
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        viewModelScope.launch {
            val deleted = gameRepository.deleteLocalFilesForPlatform(platformId)
            if (deleted > 0) {
                notificationManager.show(
                    title = "Files Removed",
                    subtitle = "$deleted file${if (deleted > 1) "s" else ""} removed from ${config.platform.name}",
                    type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                    duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
                )
            }
            loadPlatformDetailStats(platformIndex)
        }
    }

    fun resetPlatformRomPath(platformId: Long) =
        storageDelegate.resetPlatformToGlobal(viewModelScope, platformId)

    fun launchSavePathPicker(platformId: Long) {
        storageDelegate.emitSavePathPicker(viewModelScope, platformId)
    }

    fun launchStatePathPicker(platformId: Long) {
        storageDelegate.emitStatePathPicker(viewModelScope, platformId)
    }

    fun launchBiosCopyPicker(platformSlug: String) {
        pendingBiosCopyPlatformSlug = platformSlug
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun onBiosCopyFolderSelected(path: String) {
        val slug = pendingBiosCopyPlatformSlug ?: return
        pendingBiosCopyPlatformSlug = null
        viewModelScope.launch {
            val copied = biosRepository.copyBiosForPlatformTo(slug, path)
            if (copied > 0) {
                notificationManager.show(
                    title = "BIOS Copied",
                    subtitle = "$copied file${if (copied > 1) "s" else ""} copied",
                    type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                    duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
                )
            }
        }
    }

    fun resetBiosToDefault() = biosDelegate.resetBiosToDefault(viewModelScope)
    fun moveBiosActionFocus(delta: Int) = biosDelegate.moveActionFocus(delta)

    fun moveBiosPathActionFocus(delta: Int): Boolean = routeMoveBiosPathActionFocus(this, delta)

    fun resetBiosPathActionFocus() = biosDelegate.resetBiosPathActionFocus()

    fun moveBiosPlatformSubFocus(delta: Int): Boolean = routeMoveBiosPlatformSubFocus(this, delta)

    fun resetBiosPlatformSubFocus() = biosDelegate.resetPlatformSubFocus()
    fun dismissDistributeResultModal() = biosDelegate.dismissDistributeResultModal()
    fun installGpuDriver() = biosDelegate.installGpuDriver(viewModelScope)
    fun openGpuDriverFilePicker() = biosDelegate.openGpuDriverFilePicker(viewModelScope)
    fun installGpuDriverFromFile(filePath: String) = biosDelegate.installGpuDriverFromFile(filePath, viewModelScope)
    fun dismissGpuDriverPrompt() = biosDelegate.dismissGpuDriverPrompt()
    fun moveGpuDriverPromptFocus(delta: Int) = biosDelegate.moveGpuDriverPromptFocus(delta)

    override fun onCleared() {
        super.onCleared()
        shaderChainManager.destroy()
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler =
        SettingsInputHandler(this, onBack)

    // --- Social ---

    private fun observeSocialConnectionState() {
        socialRepository.connectionState.onEach { state ->
            when (state) {
                is SocialConnectionState.Disconnected -> {
                    val prefs = preferencesRepository.userPreferences.first()
                    if (prefs.isSocialLinked) {
                        _uiState.update { it.copy(social = it.social.copy(
                            authStatus = SocialAuthStatus.CONNECTING
                        )) }
                    } else {
                        _uiState.update { it.copy(social = SocialState(
                            authStatus = SocialAuthStatus.NOT_LINKED
                        )) }
                    }
                }
                is SocialConnectionState.Connecting -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.CONNECTING
                    )) }
                }
                is SocialConnectionState.Connected -> {
                    val prefs = preferencesRepository.userPreferences.first()
                    _uiState.update { it.copy(social = SocialState(
                        authStatus = SocialAuthStatus.CONNECTED,
                        username = state.user.username,
                        displayName = state.user.displayName,
                        avatarColor = state.user.avatarColor,
                        onlineStatusEnabled = prefs.socialOnlineStatusEnabled,
                        showNowPlaying = prefs.socialShowNowPlaying,
                        notifyFriendOnline = prefs.socialNotifyFriendOnline,
                        notifyFriendPlaying = prefs.socialNotifyFriendPlaying,
                        suppressNotificationsInGame = prefs.socialSuppressNotificationsInGame,
                        discordLinked = socialRepository.discordLinked.value,
                        discordUsername = socialRepository.discordUsername.value,
                        discordRichPresenceEnabled = prefs.discordRichPresenceEnabled,
                        discordPresenceState = discordPresenceManager.state.value
                    )) }
                }
                is SocialConnectionState.AwaitingAuth -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.AWAITING_AUTH,
                        qrUrl = state.qrUrl,
                        loginCode = state.loginCode
                    )) }
                }
                is SocialConnectionState.Failed -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.ERROR,
                        errorMessage = state.reason
                    )) }
                }
            }
        }.launchIn(viewModelScope)

        socialRepository.discordLinked.onEach { linked ->
            _uiState.update { it.copy(social = it.social.copy(
                discordLinked = linked,
                discordUsername = socialRepository.discordUsername.value
            )) }
        }.launchIn(viewModelScope)

        discordPresenceManager.state.onEach { presenceState ->
            _uiState.update { it.copy(social = it.social.copy(
                discordPresenceState = presenceState
            )) }
        }.launchIn(viewModelScope)
    }

    internal fun handleSocialConfirm(state: SettingsUiState): InputResult {
        return when (state.social.authStatus) {
            SocialAuthStatus.NOT_LINKED -> {
                startSocialAuth()
                InputResult.HANDLED
            }
            SocialAuthStatus.AWAITING_AUTH -> {
                cancelSocialAuth()
                InputResult.HANDLED
            }
            SocialAuthStatus.CONNECTED -> {
                val layoutState = com.nendo.argosy.ui.screens.settings.sections.SocialLayoutState(isConnected = true)
                when (com.nendo.argosy.ui.screens.settings.sections.socialItemAtFocusIndex(state.focusedIndex, layoutState)) {
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.OnlineStatus -> {
                        setSocialOnlineStatus(!state.social.onlineStatusEnabled)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.ShowNowPlaying -> {
                        if (state.social.onlineStatusEnabled) setSocialShowNowPlaying(!state.social.showNowPlaying)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.NotifyFriendOnline -> {
                        if (state.social.onlineStatusEnabled) setSocialNotifyFriendOnline(!state.social.notifyFriendOnline)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.NotifyFriendPlaying -> {
                        if (state.social.onlineStatusEnabled) setSocialNotifyFriendPlaying(!state.social.notifyFriendPlaying)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.SuppressInGame -> {
                        if (state.social.onlineStatusEnabled) setSocialSuppressNotificationsInGame(!state.social.suppressNotificationsInGame)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.Unlink -> {
                        logoutSocial()
                        InputResult.HANDLED
                    }
                    else -> InputResult.HANDLED
                }
            }
            else -> InputResult.UNHANDLED
        }
    }

    fun startSocialAuth() {
        viewModelScope.launch {
            _uiState.update { it.copy(social = it.social.copy(
                authStatus = SocialAuthStatus.CONNECTING
            )) }

            val result = socialRepository.startAuth()

            when (result) {
                is SocialAuthManager.AuthResult.Success -> {
                    _uiState.update { it.copy(social = SocialState(
                        authStatus = SocialAuthStatus.CONNECTED,
                        username = result.user.username,
                        displayName = result.user.displayName,
                        avatarColor = result.user.avatarColor,
                        onlineStatusEnabled = true,
                        showNowPlaying = true,
                        discordRichPresenceEnabled = true
                    )) }
                }
                is SocialAuthManager.AuthResult.Error -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.ERROR,
                        errorMessage = result.message
                    )) }
                }
            }
        }

        viewModelScope.launch {
            socialRepository.authState.collect { state ->
                when (state) {
                    is SocialAuthManager.AuthState.AwaitingLogin -> {
                        _uiState.update { it.copy(social = it.social.copy(
                            authStatus = SocialAuthStatus.AWAITING_AUTH,
                            qrUrl = state.qrUrl,
                            loginCode = state.loginCode
                        )) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelSocialAuth() {
        socialRepository.cancelAuth()
        _uiState.update { it.copy(social = SocialState(
            authStatus = SocialAuthStatus.NOT_LINKED
        )) }
    }

    fun logoutSocial() {
        viewModelScope.launch {
            socialRepository.logout()
            _uiState.update { it.copy(social = SocialState(
                authStatus = SocialAuthStatus.NOT_LINKED
            )) }
        }
    }

    fun setSocialOnlineStatus(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialOnlineStatusEnabled(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                onlineStatusEnabled = enabled
            )) }
        }
    }

    fun setSocialShowNowPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialShowNowPlaying(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                showNowPlaying = enabled
            )) }
        }
    }

    fun setSocialNotifyFriendOnline(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendOnline(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                notifyFriendOnline = enabled
            )) }
        }
    }

    fun setSocialNotifyFriendPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendPlaying(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                notifyFriendPlaying = enabled
            )) }
        }
    }

    fun setSocialSuppressNotificationsInGame(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialSuppressNotificationsInGame(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                suppressNotificationsInGame = enabled
            )) }
        }
    }

    fun setDiscordRichPresence(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDiscordRichPresenceEnabled(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                discordRichPresenceEnabled = enabled
            )) }
        }
    }
}
