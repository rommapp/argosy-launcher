package com.nendo.argosy.ui

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.ui.components.FanMode
import com.nendo.argosy.ui.components.PerformanceMode
import com.nendo.argosy.util.PServerExecutor
import com.nendo.argosy.data.repository.GameRepository
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
    val defaultView: DefaultView = DefaultView.SHOWCASE
)

data class DrawerState(
    val rommConnected: Boolean = false,
    val rommConnecting: Boolean = false,
    val downloadCount: Int = 0
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
    val deviceSettingsEnabled: Boolean = false
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
    private val playSessionTracker: PlaySessionTracker
) : ViewModel() {

    private val contentResolver get() = application.contentResolver
    private val fanSpeedFile = File("/sys/class/gpio5_pwm2/speed")

    init {
        downloadNotificationObserver.observe(viewModelScope)
        syncNotificationObserver.observe(viewModelScope)
        scheduleDownloadValidation()
        observeFeedbackSettings(preferencesRepository)
        downloadManager.clearCompleted()
        startControllerDetectionPolling()
    }

    private fun startControllerDetectionPolling() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val result = ControllerDetector.detectFromActiveGamepad()
                android.util.Log.d("ArgosyVM", "Polling: layout=${result.layout}, device=${result.deviceName}, current=${_detectedLayout.value}")
                if (_detectedLayout.value != result.layout) {
                    android.util.Log.d("ArgosyVM", "Layout changed: ${_detectedLayout.value} -> ${result.layout}")
                    _detectedLayout.value = result.layout
                }
            }
        }
    }

    private fun scheduleDownloadValidation() {
        viewModelScope.launch {
            val ready = gameRepository.awaitStorageReady(timeoutMs = 10_000L)
            if (ready) {
                validateAndRecoverDownloads()
                syncCollectionsOnStartup()
            } else {
                android.util.Log.w("ArgosyViewModel", "Storage not ready after timeout, scheduling retry")
                kotlinx.coroutines.delay(30_000L)
                scheduleDownloadValidation()
            }
        }
    }

    private suspend fun syncCollectionsOnStartup() {
        if (romMRepository.isConnected()) {
            romMRepository.syncCollections()
        }
    }

    private suspend fun validateAndRecoverDownloads() {
        val discovered = gameRepository.discoverLocalFiles()
        if (discovered > 0) {
            android.util.Log.d("ArgosyViewModel", "Discovered $discovered local files")
        }

        val invalidated = gameRepository.validateLocalFiles()
        if (invalidated > 0) {
            android.util.Log.d("ArgosyViewModel", "Validation: $invalidated games had missing files, attempting recovery")
            val recovered = gameRepository.recoverDownloadPaths()
            android.util.Log.d("ArgosyViewModel", "Recovery: $recovered paths restored")
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

    private val _detectedLayout = MutableStateFlow(ControllerDetector.detectFromActiveGamepad().layout)

    fun refreshControllerDetection() {
        _detectedLayout.value = ControllerDetector.detectFromActiveGamepad().layout
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
        ArgosyUiState(
            isFirstRun = !prefs.firstRunComplete,
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
        downloadManager.state
    ) { connection, downloads ->
        val downloadCount = downloads.activeDownloads.size + downloads.queue.size
        DrawerState(
            rommConnected = connection is RomMRepository.ConnectionState.Connected,
            rommConnecting = connection is RomMRepository.ConnectionState.Connecting,
            downloadCount = downloadCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrawerState()
    )

    val drawerItems = listOf(
        DrawerItem(Screen.Showcase.route, "Showcase"),
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
        override fun onUp(): InputResult {
            return if (_drawerFocusIndex.value > 0) {
                _drawerFocusIndex.update { it - 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onDown(): InputResult {
            return if (_drawerFocusIndex.value < drawerItems.lastIndex) {
                _drawerFocusIndex.update { it + 1 }
                InputResult.HANDLED
            } else {
                InputResult.UNHANDLED
            }
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            onNavigate(drawerItems[_drawerFocusIndex.value].route)
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
            if (romMRepository.connectionState.value is RomMRepository.ConnectionState.Connected) {
                romMRepository.processPendingSync()
            }
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

    val quickSettingsState: StateFlow<QuickSettingsUiState> = combine(
        preferencesRepository.userPreferences,
        _deviceSettings
    ) { prefs, device ->
        QuickSettingsUiState(
            themeMode = prefs.themeMode,
            soundEnabled = prefs.soundEnabled,
            hapticEnabled = prefs.hapticEnabled,
            vibrationStrength = hapticManager.getSystemVibrationStrength(),
            vibrationSupported = hapticManager.supportsSystemVibration,
            ambientAudioEnabled = prefs.ambientAudioEnabled,
            fanMode = device.fanMode,
            fanSpeed = device.fanSpeed,
            performanceMode = device.performanceMode,
            deviceSettingsSupported = device.isSupported,
            deviceSettingsEnabled = device.hasWritePermission
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

    fun setQuickSettingsOpen(open: Boolean) {
        _isQuickSettingsOpen.value = open
        if (open) {
            _quickSettingsFocusIndex.value = 0
            loadDeviceSettings()
        }
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
        hapticManager.setSystemVibrationStrength(strength)
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
            val baseItems = if (hasVibrationSlider()) 5 else 4
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
            val index = _quickSettingsFocusIndex.value

            if (hasFanSlider() && index == 2) {
                val currentSpeed = _deviceSettings.value.fanSpeed
                val newSpeed = (currentSpeed - 1000).coerceAtLeast(25000)
                setFanSpeed(newSpeed)
                return InputResult.HANDLED
            }

            if (hasVibrationSlider() && index == offset + 2) {
                val currentStrength = hapticManager.getSystemVibrationStrength()
                val newStrength = (currentStrength - 0.1f).coerceAtLeast(0f)
                setVibrationStrength(newStrength)
                return InputResult.HANDLED
            }

            return InputResult.UNHANDLED
        }

        override fun onRight(): InputResult {
            val offset = getDeviceOffset()
            val index = _quickSettingsFocusIndex.value

            if (hasFanSlider() && index == 2) {
                val currentSpeed = _deviceSettings.value.fanSpeed
                val newSpeed = (currentSpeed + 1000).coerceAtMost(35000)
                setFanSpeed(newSpeed)
                return InputResult.HANDLED
            }

            if (hasVibrationSlider() && index == offset + 2) {
                val currentStrength = hapticManager.getSystemVibrationStrength()
                val newStrength = (currentStrength + 0.1f).coerceAtMost(1f)
                setVibrationStrength(newStrength)
                return InputResult.HANDLED
            }

            return InputResult.UNHANDLED
        }

        override fun onConfirm(): InputResult {
            val offset = getDeviceOffset()
            val vibrationOffset = if (hasVibrationSlider()) 1 else 0
            val index = _quickSettingsFocusIndex.value
            val device = _deviceSettings.value

            if (device.isSupported) {
                when {
                    index == 0 && device.hasWritePermission -> cyclePerformanceMode()
                    index == 1 && device.hasWritePermission -> cycleFanMode()
                    index == 2 && hasFanSlider() -> { /* slider - no action on confirm */ }
                    index == offset -> cycleTheme()
                    index == offset + 1 -> {
                        val enabled = toggleHaptic()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    index == offset + 2 && hasVibrationSlider() -> { /* slider - no action on confirm */ }
                    index == offset + 2 + vibrationOffset -> {
                        val enabled = toggleSound()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    index == offset + 3 + vibrationOffset -> {
                        val enabled = toggleAmbientAudio()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                }
            } else {
                when (index) {
                    0 -> cycleTheme()
                    1 -> {
                        val enabled = toggleHaptic()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    2 -> if (hasVibrationSlider()) { /* slider */ } else {
                        val enabled = toggleSound()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    3 -> {
                        val enabled = if (hasVibrationSlider()) toggleSound() else toggleAmbientAudio()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
                    4 -> if (hasVibrationSlider()) {
                        val enabled = toggleAmbientAudio()
                        return InputResult.handled(if (enabled) SoundType.TOGGLE else SoundType.SILENT)
                    }
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
}
