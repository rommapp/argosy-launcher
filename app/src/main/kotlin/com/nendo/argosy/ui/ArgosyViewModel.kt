package com.nendo.argosy.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.hardware.input.InputManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.emulator.EmulatorUpdateManager
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.sync.ConflictInfo
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.data.sync.SyncQueueManager
import com.nendo.argosy.ui.components.SaveConflictInfo
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.VolumeController
import com.nendo.argosy.ui.components.FanMode
import com.nendo.argosy.ui.components.PerformanceMode
import com.nendo.argosy.util.PServerExecutor
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.libretro.LibretroMigrationUseCase
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.notification.DownloadNotificationObserver
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.SyncNotificationObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ArgosyUiState(
    val isFirstRun: Boolean = true,
    val isLoading: Boolean = true,
    val abIconsSwapped: Boolean = false,
    val xyIconsSwapped: Boolean = false,
    val swapStartSelect: Boolean = false,
    val defaultView: DefaultView = DefaultView.HOME
)

data class DrawerState(
    val rommConnected: Boolean = false,
    val rommConnecting: Boolean = false,
    val downloadCount: Int = 0,
    val emulatorUpdatesAvailable: Int = 0
)

data class QuickSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,
    val vibrationSupported: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val fanMode: FanMode = FanMode.SMART,
    val fanSpeed: Int = 25000,
    val performanceMode: PerformanceMode = PerformanceMode.STANDARD,
    val deviceSettingsSupported: Boolean = false,
    val deviceSettingsEnabled: Boolean = false,
    val systemVolume: Float = 1f,
    val secondaryVolume: Float? = null,
    val screenBrightness: Float = 0.5f,
    val secondaryBrightness: Float? = null
)

data class ScreenDimmerPreferences(
    val enabled: Boolean = true,
    val timeoutMinutes: Int = 2,
    val level: Int = 30
)

data class DrawerItem(
    val route: String,
    val label: String
)

@HiltViewModel
class ArgosyViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    val gamepadInputHandler: GamepadInputHandler,
    val hapticManager: HapticFeedbackManager,
    val soundManager: SoundFeedbackManager,
    val notificationManager: NotificationManager,
    downloadNotificationObserver: DownloadNotificationObserver,
    syncNotificationObserver: SyncNotificationObserver,
    private val gameRepository: GameRepository,
    private val romMRepository: RomMRepository,
    private val downloadManager: DownloadManager,
    private val modalResetSignal: ModalResetSignal,
    private val playSessionTracker: PlaySessionTracker,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val libretroMigrationUseCase: LibretroMigrationUseCase,
    private val emulatorUpdateManager: EmulatorUpdateManager,
    private val syncCoordinator: com.nendo.argosy.data.sync.SyncCoordinator,
    private val syncQueueManager: SyncQueueManager,
    private val brightnessController: BrightnessController,
    private val volumeController: VolumeController
) : ViewModel() {

    private val contentResolver get() = application.contentResolver
    private val fanSpeedFile = File("/sys/class/gpio5_pwm2/speed")

    private val _backgroundConflictInfo = MutableStateFlow<ConflictInfo?>(null)
    val backgroundConflictInfo: StateFlow<ConflictInfo?> = _backgroundConflictInfo.asStateFlow()

    private val _backgroundConflictButtonIndex = MutableStateFlow(0)
    val backgroundConflictButtonIndex: StateFlow<Int> = _backgroundConflictButtonIndex.asStateFlow()

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshAudioVisualSettings()
        }
    }

    private val _detectedLayout = MutableStateFlow(ControllerDetector.detectFromActiveGamepad().layout)

    private val inputManager = application.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshControllerDetection()
        override fun onInputDeviceChanged(deviceId: Int) = refreshControllerDetection()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshControllerDetection()
    }

    init {
        downloadNotificationObserver.observe(viewModelScope)
        syncNotificationObserver.observe(viewModelScope)
        scheduleStartupTasks()
        observeFeedbackSettings(preferencesRepository)
        downloadManager.clearCompleted()
        initControllerDetection()
        observeSaveConflicts()
        observeBackgroundSyncConflicts()
        observeConnectionForSync()
        registerSettingsObserver()
    }

    override fun onCleared() {
        super.onCleared()
        application.contentResolver.unregisterContentObserver(settingsObserver)
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
    }

    private fun registerSettingsObserver() {
        application.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            settingsObserver
        )
    }

    private fun observeConnectionForSync() {
        viewModelScope.launch {
            var wasConnected = false
            romMRepository.connectionState.collect { state ->
                val isConnected = state is RomMRepository.ConnectionState.Connected
                if (isConnected && !wasConnected) {
                    syncCoordinator.processQueue()
                }
                wasConnected = isConnected
            }
        }
    }

    private fun observeSaveConflicts() {
        viewModelScope.launch {
            playSessionTracker.conflictEvents.collect { event ->
                val game = gameDao.getById(event.gameId)
                _saveConflictInfo.value = SaveConflictInfo(
                    gameId = event.gameId,
                    gameName = game?.title ?: "Unknown Game",
                    emulatorId = event.emulatorId,
                    localTimestamp = event.localTimestamp,
                    serverTimestamp = event.serverTimestamp
                )
                _saveConflictButtonIndex.value = 0
            }
        }
    }

    private fun observeBackgroundSyncConflicts() {
        viewModelScope.launch {
            syncQueueManager.pendingConflicts.collect { conflicts ->
                if (conflicts.isNotEmpty()) {
                    _backgroundConflictInfo.value = conflicts.first()
                    _backgroundConflictButtonIndex.value = 0
                } else {
                    _backgroundConflictInfo.value = null
                }
            }
        }
    }

    private fun initControllerDetection() {
        // One-time detection at startup
        refreshControllerDetection()
        // Register listener for device changes (connect/disconnect/mode change)
        inputManager.registerInputDeviceListener(inputDeviceListener, null)
    }

    fun refreshControllerDetection() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = ControllerDetector.detectFromActiveGamepad()
            if (_detectedLayout.value != result.layout) {
                Log.d("ArgosyVM", "Layout changed: ${_detectedLayout.value} -> ${result.layout} (${result.source})")
                _detectedLayout.value = result.layout
            }
        }
    }

    private fun scheduleStartupTasks() {
        viewModelScope.launch {
            val ready = gameRepository.awaitStorageReady(timeoutMs = 10_000L)
            if (ready) {
                syncCollectionsOnStartup()
                runBuiltinEmulatorMigration()
                emulatorUpdateManager.checkIfNeeded()
            } else {
                android.util.Log.w("ArgosyViewModel", "Storage not ready after timeout, scheduling retry")
                kotlinx.coroutines.delay(30_000L)
                scheduleStartupTasks()
            }
        }
    }

    private suspend fun runBuiltinEmulatorMigration() {
        val result = libretroMigrationUseCase.runMigrationIfNeeded()
        when (result) {
            is com.nendo.argosy.domain.usecase.libretro.MigrationResult.Success -> {
                if (result.coresDownloaded.isNotEmpty()) {
                    notificationManager.show(
                        title = "Built-in Emulator Ready",
                        subtitle = "Downloaded ${result.coresDownloaded.size} cores",
                        type = com.nendo.argosy.ui.notification.NotificationType.INFO,
                        duration = com.nendo.argosy.ui.notification.NotificationDuration.MEDIUM
                    )
                }
            }
            else -> { /* No notification needed */ }
        }
    }

    private suspend fun syncCollectionsOnStartup() {
        if (romMRepository.isConnected()) {
            romMRepository.syncCollections()
        }
    }

    private fun observeFeedbackSettings(preferencesRepository: UserPreferencesRepository) {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                hapticManager.setEnabled(prefs.hapticEnabled)
                soundManager.setEnabled(prefs.soundEnabled)
                soundManager.setVolume(prefs.soundVolume)
                soundManager.setSoundConfigs(prefs.soundConfigs)
            }
        }
    }

    val uiState: StateFlow<ArgosyUiState> = combine(
        preferencesRepository.userPreferences,
        _detectedLayout
    ) { prefs, detectedLayout ->
        val isNintendoLayout = when (prefs.controllerLayout) {
            "nintendo" -> true
            "xbox" -> false
            else -> detectedLayout == DetectedLayout.NINTENDO
        }
        val hasExistingConfig = prefs.rommBaseUrl != null || prefs.romStoragePath != null
        ArgosyUiState(
            isFirstRun = !prefs.firstRunComplete && !hasExistingConfig,
            isLoading = false,
            abIconsSwapped = isNintendoLayout xor prefs.swapAB,
            xyIconsSwapped = isNintendoLayout xor prefs.swapXY,
            swapStartSelect = prefs.swapStartSelect,
            defaultView = prefs.defaultView
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArgosyUiState()
    )

    val drawerUiState: StateFlow<DrawerState> = combine(
        romMRepository.connectionState,
        downloadManager.state,
        emulatorUpdateManager.updateCount
    ) { connection, downloads, emulatorUpdates ->
        val downloadCount = downloads.activeDownloads.size + downloads.queue.size
        DrawerState(
            rommConnected = connection is RomMRepository.ConnectionState.Connected,
            rommConnecting = connection is RomMRepository.ConnectionState.Connecting,
            downloadCount = downloadCount,
            emulatorUpdatesAvailable = emulatorUpdates
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrawerState()
    )

    val drawerItems = listOf(
        DrawerItem(Screen.Home.route, "Home"),
        DrawerItem(Screen.Collections.route, "Collections"),
        DrawerItem(Screen.Library.route, "Library"),
        DrawerItem(Screen.Downloads.route, "Downloads"),
        DrawerItem(Screen.Apps.route, "Apps"),
        DrawerItem(Screen.Settings.route, "Settings")
    )

    private val _drawerFocusIndex = MutableStateFlow(0)
    val drawerFocusIndex: StateFlow<Int> = _drawerFocusIndex.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
    }

    fun resetAllModals() {
        _isDrawerOpen.value = false
        _isQuickSettingsOpen.value = false
        _saveConflictInfo.value = null
        modalResetSignal.emit()
    }

    fun initDrawerFocus(currentRoute: String?, parentRoute: String? = null) {
        var index = drawerItems.indexOfFirst { it.route == currentRoute }
        if (index < 0 && parentRoute != null) {
            index = drawerItems.indexOfFirst { it.route == parentRoute }
        }
        _drawerFocusIndex.value = if (index >= 0) index else 0
    }

    fun createDrawerInputHandler(
        onNavigate: (String) -> Unit,
        onDismiss: () -> Unit
    ): InputHandler = object : InputHandler {
        private val hasUpdateFooter: Boolean
            get() = drawerUiState.value.emulatorUpdatesAvailable > 0

        private val footerIndex: Int
            get() = drawerItems.size

        override fun onUp(): InputResult {
            return if (_drawerFocusIndex.value > 0) {
                _drawerFocusIndex.update { it - 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            val maxIndex = if (hasUpdateFooter) footerIndex else drawerItems.lastIndex
            return if (_drawerFocusIndex.value < maxIndex) {
                _drawerFocusIndex.update { it + 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            val currentIndex = _drawerFocusIndex.value
            Log.d("ArgosyViewModel", "Drawer onConfirm: index=$currentIndex, footerIndex=$footerIndex, hasUpdateFooter=$hasUpdateFooter")
            if (hasUpdateFooter && currentIndex == footerIndex) {
                Log.d("ArgosyViewModel", "Navigating to emulators section via footer")
                onNavigate(Screen.Settings.createRoute(section = "emulators"))
            } else if (currentIndex < drawerItems.size) {
                Log.d("ArgosyViewModel", "Navigating to drawer item: ${drawerItems[currentIndex].route}")
                onNavigate(drawerItems[currentIndex].route)
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }

        override fun onMenu(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
    }

    fun onDrawerOpened() {
        viewModelScope.launch {
            romMRepository.checkConnection()
            syncCoordinator.processQueue()
            downloadManager.recheckStorageAndResume()
        }
    }

    // Quick Settings
    private val _isQuickSettingsOpen = MutableStateFlow(false)
    val isQuickSettingsOpen: StateFlow<Boolean> = _isQuickSettingsOpen.asStateFlow()

    private val _quickSettingsFocusIndex = MutableStateFlow(0)
    val quickSettingsFocusIndex: StateFlow<Int> = _quickSettingsFocusIndex.asStateFlow()

    private data class DeviceSettingsState(
        val fanMode: FanMode = FanMode.SMART,
        val fanSpeed: Int = 25000,
        val performanceMode: PerformanceMode = PerformanceMode.STANDARD,
        val isSupported: Boolean = false,
        val hasWritePermission: Boolean = false
    )

    private val _deviceSettings = MutableStateFlow(DeviceSettingsState())
    private val _vibrationStrength = MutableStateFlow(hapticManager.getSystemVibrationStrength())

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _systemVolume = MutableStateFlow(getInitialPrimaryVolume())
    private val _secondaryVolume = MutableStateFlow(getInitialSecondaryVolume())
    private val _screenBrightness = MutableStateFlow(getInitialPrimaryBrightness())
    private val _secondaryBrightness = MutableStateFlow(getInitialSecondaryBrightness())

    private fun getInitialPrimaryVolume(): Float {
        return volumeController.getVolume().primary
    }

    private fun getInitialSecondaryVolume(): Float? {
        if (!volumeController.isMultiDisplaySupported) return null
        return volumeController.getVolume().secondary
    }

    private fun getInitialPrimaryBrightness(): Float {
        return brightnessController.getBrightness().primary
            ?: brightnessController.getSystemBrightnessSync()
            ?: 0.5f
    }

    private fun getInitialSecondaryBrightness(): Float? {
        if (!brightnessController.isMultiDisplaySupported) return null
        return brightnessController.getBrightness().secondary
    }

    private data class AudioVisualState(
        val volume: Float,
        val secondaryVolume: Float?,
        val brightness: Float,
        val secondaryBrightness: Float?
    )

    val quickSettingsState: StateFlow<QuickSettingsUiState> = combine(
        preferencesRepository.userPreferences,
        _deviceSettings,
        _vibrationStrength,
        combine(_systemVolume, _secondaryVolume, _screenBrightness, _secondaryBrightness) { v, sv, b, sb ->
            AudioVisualState(v, sv, b, sb)
        }
    ) { prefs, device, vibrationStrength, av ->
        QuickSettingsUiState(
            themeMode = prefs.themeMode,
            soundEnabled = prefs.soundEnabled,
            hapticEnabled = prefs.hapticEnabled,
            vibrationStrength = vibrationStrength,
            vibrationSupported = hapticManager.supportsSystemVibration,
            ambientAudioEnabled = prefs.ambientAudioEnabled,
            fanMode = device.fanMode,
            fanSpeed = device.fanSpeed,
            performanceMode = device.performanceMode,
            deviceSettingsSupported = device.isSupported,
            deviceSettingsEnabled = device.hasWritePermission,
            systemVolume = av.volume,
            secondaryVolume = av.secondaryVolume,
            screenBrightness = av.brightness,
            secondaryBrightness = av.secondaryBrightness
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuickSettingsUiState()
    )

    val screenDimmerPreferences: StateFlow<ScreenDimmerPreferences> = preferencesRepository.userPreferences
        .map { prefs ->
            ScreenDimmerPreferences(
                enabled = prefs.screenDimmerEnabled,
                timeoutMinutes = prefs.screenDimmerTimeoutMinutes,
                level = prefs.screenDimmerLevel
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScreenDimmerPreferences()
        )

    val isEmulatorRunning: StateFlow<Boolean> = playSessionTracker.activeSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _saveConflictInfo = MutableStateFlow<SaveConflictInfo?>(null)
    val saveConflictInfo: StateFlow<SaveConflictInfo?> = _saveConflictInfo.asStateFlow()

    private val _saveConflictButtonIndex = MutableStateFlow(0)
    val saveConflictButtonIndex: StateFlow<Int> = _saveConflictButtonIndex.asStateFlow()

    fun dismissSaveConflict() {
        _saveConflictInfo.value = null
        _saveConflictButtonIndex.value = 0
    }

    fun moveSaveConflictFocus(direction: Int) {
        val newIndex = (_saveConflictButtonIndex.value + direction).coerceIn(0, 1)
        _saveConflictButtonIndex.value = newIndex
    }

    fun forceUploadConflictSave() {
        val info = _saveConflictInfo.value ?: return
        viewModelScope.launch {
            saveSyncRepository.uploadSave(
                gameId = info.gameId,
                emulatorId = info.emulatorId,
                channelName = null,
                forceOverwrite = true
            )
        }
        dismissSaveConflict()
    }

    fun resolveBackgroundConflict(resolution: ConflictResolution) {
        val info = _backgroundConflictInfo.value ?: return
        syncQueueManager.resolveConflict(info.gameId, resolution)
    }

    fun moveBackgroundConflictFocus(direction: Int) {
        val newIndex = (_backgroundConflictButtonIndex.value + direction).coerceIn(0, 2)
        _backgroundConflictButtonIndex.value = newIndex
    }

    fun setQuickSettingsOpen(open: Boolean) {
        _isQuickSettingsOpen.value = open
        if (open) {
            _quickSettingsFocusIndex.value = 0
            loadDeviceSettings()
            refreshAudioVisualSettings()
        }
    }

    private fun refreshAudioVisualSettings() {
        val volume = volumeController.getVolume()
        _systemVolume.value = volume.primary
        _secondaryVolume.value = volume.secondary

        val brightness = brightnessController.getBrightness()
        brightness.primary?.let { _screenBrightness.value = it }
        brightness.secondary?.let { _secondaryBrightness.value = it }
    }

    private fun loadDeviceSettings() {
        viewModelScope.launch {
            val isSupported = fanSpeedFile.exists()
            val pserverAvailable = PServerExecutor.isAvailable

            if (!isSupported) {
                _deviceSettings.value = DeviceSettingsState(isSupported = false)
                return@launch
            }

            val fanModeValue = PServerExecutor.getSystemSetting("fan_mode", 0)
            val fanSpeedValue = PServerExecutor.getSystemSetting("fan_speed", 25000)
            val perfModeValue = PServerExecutor.getSystemSetting("performance_mode", 0)

            _deviceSettings.value = DeviceSettingsState(
                fanMode = FanMode.fromValue(fanModeValue),
                fanSpeed = fanSpeedValue,
                performanceMode = PerformanceMode.fromValue(perfModeValue),
                isSupported = true,
                hasWritePermission = pserverAvailable
            )
        }
    }

    fun cycleTheme() {
        viewModelScope.launch {
            val current = quickSettingsState.value.themeMode
            val next = when (current) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            preferencesRepository.setThemeMode(next)
        }
    }

    fun toggleSound(): Boolean {
        val current = quickSettingsState.value.soundEnabled
        val newState = !current
        soundManager.setEnabled(newState)
        viewModelScope.launch {
            preferencesRepository.setSoundEnabled(newState)
        }
        return newState
    }

    fun toggleHaptic(): Boolean {
        val current = quickSettingsState.value.hapticEnabled
        val newState = !current
        hapticManager.setEnabled(newState)
        if (newState) {
            hapticManager.vibrate(HapticPattern.SELECTION)
        }
        viewModelScope.launch {
            preferencesRepository.setHapticEnabled(newState)
        }
        return newState
    }

    fun setVibrationStrength(strength: Float) {
        val coercedStrength = strength.coerceIn(0f, 1f)
        hapticManager.setSystemVibrationStrength(coercedStrength)
        _vibrationStrength.value = coercedStrength
        hapticManager.vibrate(HapticPattern.STRENGTH_PREVIEW)
    }

    fun toggleAmbientAudio(): Boolean {
        val current = quickSettingsState.value.ambientAudioEnabled
        val newState = !current
        viewModelScope.launch {
            preferencesRepository.setAmbientAudioEnabled(newState)
        }
        return newState
    }

    fun setSystemVolume(volume: Float) {
        val coercedVolume = volume.coerceIn(0f, 1f)
        _systemVolume.value = coercedVolume
        volumeController.setPrimaryVolume(coercedVolume)
    }

    fun setSecondaryVolume(volume: Float) {
        if (!volumeController.isMultiDisplaySupported) return
        val coercedVolume = volume.coerceIn(0f, 1f)
        _secondaryVolume.value = coercedVolume
        volumeController.setSecondaryVolume(coercedVolume)
    }

    fun setScreenBrightness(brightness: Float) {
        val coercedBrightness = brightness.coerceIn(0f, 1f)
        _screenBrightness.value = coercedBrightness
        brightnessController.setPrimaryBrightness(coercedBrightness)
    }

    fun setSecondaryBrightness(brightness: Float) {
        if (!brightnessController.isMultiDisplaySupported) return
        val coercedBrightness = brightness.coerceIn(0f, 1f)
        _secondaryBrightness.value = coercedBrightness
        brightnessController.setSecondaryBrightness(coercedBrightness)
    }

    fun cycleFanMode() {
        val current = _deviceSettings.value.fanMode
        val cycleOrder = listOf(
            FanMode.QUIET,
            FanMode.SMART,
            FanMode.SPORT,
            FanMode.CUSTOM
        )
        val nextIndex = (cycleOrder.indexOf(current) + 1).mod(cycleOrder.size)
        setFanMode(cycleOrder[nextIndex])
    }

    private fun setFanMode(mode: FanMode) {
        viewModelScope.launch {
            if (PServerExecutor.setSystemSetting("fan_mode", mode.value)) {
                _deviceSettings.update { it.copy(fanMode = mode) }
            }
        }
    }

    fun setFanSpeed(speed: Int) {
        viewModelScope.launch {
            val clampedSpeed = speed.coerceIn(25000, 35000)
            if (PServerExecutor.setSystemSetting("fan_speed", clampedSpeed)) {
                _deviceSettings.update { it.copy(fanSpeed = clampedSpeed) }
            }
        }
    }

    fun cyclePerformanceMode() {
        val current = _deviceSettings.value.performanceMode
        val modes = PerformanceMode.entries
        val nextIndex = (modes.indexOf(current) + 1).mod(modes.size)
        val next = modes[nextIndex]
        setPerformanceMode(next)
    }

    private fun setPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch {
            if (PServerExecutor.setSystemSetting("performance_mode", mode.value)) {
                delay(100)
                val fanMode = when (mode) {
                    PerformanceMode.STANDARD -> FanMode.SMART
                    PerformanceMode.HIGH -> FanMode.SPORT
                    PerformanceMode.MAX -> FanMode.CUSTOM
                }
                PServerExecutor.setSystemSetting("fan_mode", fanMode.value)
                delay(100)
                refreshDeviceSettings()
            }
        }
    }

    private fun refreshDeviceSettings() {
        val fanModeValue = PServerExecutor.getSystemSetting("fan_mode", 0)
        val fanSpeedValue = PServerExecutor.getSystemSetting("fan_speed", 25000)
        val perfModeValue = PServerExecutor.getSystemSetting("performance_mode", 0)
        _deviceSettings.update {
            it.copy(
                fanMode = FanMode.fromValue(fanModeValue),
                fanSpeed = fanSpeedValue,
                performanceMode = PerformanceMode.fromValue(perfModeValue)
            )
        }
    }

    fun createQuickSettingsInputHandler(
        onDismiss: () -> Unit
    ): InputHandler = object : InputHandler {
        private fun hasFanSlider(): Boolean {
            val device = _deviceSettings.value
            return device.isSupported && device.hasWritePermission && device.fanMode == FanMode.CUSTOM
        }

        private fun hasVibrationSlider(): Boolean {
            return hapticManager.supportsSystemVibration && quickSettingsState.value.hapticEnabled
        }

        private fun getMaxIndex(): Int {
            val hasSecondaryVolume = volumeController.isMultiDisplaySupported
            val hasSecondaryBrightness = brightnessController.isMultiDisplaySupported
            val baseItems = 6 +
                (if (hasVibrationSlider()) 1 else 0) +
                (if (hasSecondaryVolume) 1 else 0) +
                (if (hasSecondaryBrightness) 1 else 0)
            val deviceItems = when {
                !_deviceSettings.value.isSupported -> 0
                hasFanSlider() -> 3
                else -> 2
            }
            return baseItems + deviceItems - 1
        }

        private fun getDeviceOffset(): Int = when {
            !_deviceSettings.value.isSupported -> 0
            hasFanSlider() -> 3
            else -> 2
        }

        override fun onUp(): InputResult {
            return if (_quickSettingsFocusIndex.value > 0) {
                _quickSettingsFocusIndex.update { it - 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            return if (_quickSettingsFocusIndex.value < getMaxIndex()) {
                _quickSettingsFocusIndex.update { it + 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult {
            val offset = getDeviceOffset()
            val secondaryVolumeOffset = if (volumeController.isMultiDisplaySupported) 1 else 0
            val secondaryBrightnessOffset = if (brightnessController.isMultiDisplaySupported) 1 else 0
            val avOffset = secondaryVolumeOffset + secondaryBrightnessOffset
            val vibrationOffset = if (hasVibrationSlider()) 1 else 0
            val index = _quickSettingsFocusIndex.value

            // Fan speed slider (index 2 when fan slider visible)
            if (hasFanSlider() && index == 2) {
                val currentSpeed = _deviceSettings.value.fanSpeed
                val newSpeed = (currentSpeed - 1000).coerceAtLeast(25000)
                setFanSpeed(newSpeed)
                return InputResult.HANDLED
            }

            // Primary volume (offset + 1)
            if (index == offset + 1) {
                val currentVolume = _systemVolume.value
                val newVolume = (currentVolume - 0.05f).coerceAtLeast(0f)
                setSystemVolume(newVolume)
                return InputResult.HANDLED
            }

            // Secondary volume (offset + 2, only if multi-display)
            if (index == offset + 2 && volumeController.isMultiDisplaySupported) {
                val currentVolume = _secondaryVolume.value ?: 0.5f
                val newVolume = (currentVolume - 0.05f).coerceAtLeast(0f)
                setSecondaryVolume(newVolume)
                return InputResult.HANDLED
            }

            // Primary brightness (offset + 2 + secondaryVolumeOffset)
            if (index == offset + 2 + secondaryVolumeOffset) {
                val currentBrightness = _screenBrightness.value
                val newBrightness = (currentBrightness - 0.05f).coerceAtLeast(0f)
                setScreenBrightness(newBrightness)
                return InputResult.HANDLED
            }

            // Secondary brightness (offset + 3 + secondaryVolumeOffset, only if multi-display)
            if (index == offset + 3 + secondaryVolumeOffset && brightnessController.isMultiDisplaySupported) {
                val currentBrightness = _secondaryBrightness.value ?: 0.5f
                val newBrightness = (currentBrightness - 0.05f).coerceAtLeast(0f)
                setSecondaryBrightness(newBrightness)
                return InputResult.HANDLED
            }

            // Vibration strength (offset + 4 + avOffset, only if vibration slider visible)
            if (hasVibrationSlider() && index == offset + 4 + avOffset) {
                val currentStrength = hapticManager.getSystemVibrationStrength()
                val newStrength = (currentStrength - 0.1f).coerceAtLeast(0f)
                setVibrationStrength(newStrength)
                return InputResult.HANDLED
            }

            return InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            val offset = getDeviceOffset()
            val secondaryVolumeOffset = if (volumeController.isMultiDisplaySupported) 1 else 0
            val secondaryBrightnessOffset = if (brightnessController.isMultiDisplaySupported) 1 else 0
            val avOffset = secondaryVolumeOffset + secondaryBrightnessOffset
            val index = _quickSettingsFocusIndex.value

            // Fan speed slider (index 2 when fan slider visible)
            if (hasFanSlider() && index == 2) {
                val currentSpeed = _deviceSettings.value.fanSpeed
                val newSpeed = (currentSpeed + 1000).coerceAtMost(35000)
                setFanSpeed(newSpeed)
                return InputResult.HANDLED
            }

            // Primary volume (offset + 1)
            if (index == offset + 1) {
                val currentVolume = _systemVolume.value
                val newVolume = (currentVolume + 0.05f).coerceAtMost(1f)
                setSystemVolume(newVolume)
                return InputResult.HANDLED
            }

            // Secondary volume (offset + 2, only if multi-display)
            if (index == offset + 2 && volumeController.isMultiDisplaySupported) {
                val currentVolume = _secondaryVolume.value ?: 0.5f
                val newVolume = (currentVolume + 0.05f).coerceAtMost(1f)
                setSecondaryVolume(newVolume)
                return InputResult.HANDLED
            }

            // Primary brightness (offset + 2 + secondaryVolumeOffset)
            if (index == offset + 2 + secondaryVolumeOffset) {
                val currentBrightness = _screenBrightness.value
                val newBrightness = (currentBrightness + 0.05f).coerceAtMost(1f)
                setScreenBrightness(newBrightness)
                return InputResult.HANDLED
            }

            // Secondary brightness (offset + 3 + secondaryVolumeOffset, only if multi-display)
            if (index == offset + 3 + secondaryVolumeOffset && brightnessController.isMultiDisplaySupported) {
                val currentBrightness = _secondaryBrightness.value ?: 0.5f
                val newBrightness = (currentBrightness + 0.05f).coerceAtMost(1f)
                setSecondaryBrightness(newBrightness)
                return InputResult.HANDLED
            }

            // Vibration strength (offset + 4 + avOffset, only if vibration slider visible)
            if (hasVibrationSlider() && index == offset + 4 + avOffset) {
                val currentStrength = hapticManager.getSystemVibrationStrength()
                val newStrength = (currentStrength + 0.1f).coerceAtMost(1f)
                setVibrationStrength(newStrength)
                return InputResult.HANDLED
            }

            return InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            val offset = getDeviceOffset()
            val secondaryVolumeOffset = if (volumeController.isMultiDisplaySupported) 1 else 0
            val secondaryBrightnessOffset = if (brightnessController.isMultiDisplaySupported) 1 else 0
            val avOffset = secondaryVolumeOffset + secondaryBrightnessOffset
            val vibrationOffset = if (hasVibrationSlider()) 1 else 0
            val index = _quickSettingsFocusIndex.value
            val device = _deviceSettings.value

            // Device items (Performance, Fan, Fan Speed slider)
            if (device.isSupported) {
                when (index) {
                    0 -> if (device.hasWritePermission) cyclePerformanceMode()
                    1 -> if (device.hasWritePermission) cycleFanMode()
                    2 -> if (hasFanSlider()) { /* slider - no action */ }
                }
            }

            when (index) {
                // Theme
                offset -> cycleTheme()
                // Haptic toggle
                offset + 3 + avOffset -> {
                    val enabled = toggleHaptic()
                    return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                }
                // Sound toggle
                offset + 4 + avOffset + vibrationOffset -> {
                    val enabled = toggleSound()
                    return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                }
                // BGM toggle
                offset + 5 + avOffset + vibrationOffset -> {
                    val enabled = toggleAmbientAudio()
                    return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                }
            }

            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }

        override fun onRightStickClick(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
    }

    data class PendingLaunch(
        val gameId: Long,
        val channelName: String? = null,
        val discId: Long? = null
    )

    private val _isTransitioningToGame = MutableStateFlow(false)
    val isTransitioningToGame: StateFlow<Boolean> = _isTransitioningToGame.asStateFlow()

    private val _returningFromGame = MutableStateFlow(false)
    val returningFromGame: StateFlow<Boolean> = _returningFromGame.asStateFlow()

    private val _pendingLaunch = MutableStateFlow<PendingLaunch?>(null)
    val pendingLaunch: StateFlow<PendingLaunch?> = _pendingLaunch.asStateFlow()

    fun initiateGameLaunch(gameId: Long, channelName: String? = null, discId: Long? = null) {
        _isTransitioningToGame.value = true
        _pendingLaunch.value = PendingLaunch(gameId, channelName, discId)
    }

    fun consumePendingLaunch(): PendingLaunch? {
        val launch = _pendingLaunch.value
        _pendingLaunch.value = null
        _isTransitioningToGame.value = false
        return launch
    }

    fun setReturningFromGame() {
        _returningFromGame.value = true
    }

    fun clearReturningFlag() {
        _returningFromGame.value = false
    }
}
