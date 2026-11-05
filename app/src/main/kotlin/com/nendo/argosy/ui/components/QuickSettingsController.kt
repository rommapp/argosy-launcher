package com.nendo.argosy.ui.components

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.DevicePerformance
import com.nendo.argosy.hardware.DevicePerformanceResolver
import com.nendo.argosy.hardware.DisplayRefreshController
import com.nendo.argosy.hardware.FanController
import com.nendo.argosy.hardware.HardwareStatsReader
import com.nendo.argosy.hardware.PerformanceMode
import com.nendo.argosy.hardware.VolumeController
import com.nendo.argosy.ui.components.friends.QuickFriendsController
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.theme.AccentHue
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.stepOption
import com.nendo.argosy.ui.screens.settings.sections.input.toggleLeftRight
import com.nendo.argosy.util.PServerExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val LEVEL_STEP = 0.05f
private const val DEFAULT_BRIGHTNESS = 0.5f
private const val VOLUME_ECHO_GUARD_MS = 250L
private const val BRIGHTNESS_ECHO_GUARD_MS = 750L
private const val DEVICE_SETTLE_MS = 100L
private const val HARDWARE_POLL_MS = 1000L
private const val SETTING_FAN_MODE = "fan_mode"
private const val SETTING_FAN_SPEED = "fan_speed"

private data class DeviceSettings(
    val fanMode: FanMode = FanMode.SMART,
    val fanSpeed: Int = FanController.SPORT_DUTY,
    val fanSupported: Boolean = false,
    val performanceModes: List<PerformanceMode> = emptyList(),
    val performanceMode: PerformanceMode? = null,
    val refreshRates: List<Int?> = emptyList(),
    val refreshRateHz: Int? = null,
    val isSupported: Boolean = false,
    val hasWritePermission: Boolean = false
)

private data class DisplayLevels(
    val volume: Float,
    val brightness: Float,
    val secondaryBrightness: Float?
)

private data class DualScreenFlags(
    val active: Boolean = false,
    val rolesSwapped: Boolean = false
)

private data class DeviceReadings(
    val settings: DeviceSettings,
    val levels: DisplayLevels,
    val screens: DualScreenFlags,
    val hardware: HardwareStatsUi?
)

/**
 * State, actions and gamepad input of the quick settings panel. [state] is the single source the
 * panel renders and the input handler reads.
 */
class QuickSettingsController(
    private val preferencesRepository: UserPreferencesRepository,
    private val brightnessController: BrightnessController,
    private val volumeController: VolumeController,
    private val performanceResolver: DevicePerformanceResolver,
    private val refreshController: DisplayRefreshController,
    private val hardwareStatsReader: HardwareStatsReader,
    socialRepository: SocialRepository,
    private val hapticManager: HapticFeedbackManager,
    private val soundManager: SoundFeedbackManager,
    private val quickFriends: QuickFriendsController,
    private val wrapMode: () -> MenuWrapMode,
    private val scope: CoroutineScope,
    private val deviceDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex.asStateFlow()

    private val _page = MutableStateFlow(QuickSettingsPage.QUICK)
    val page: StateFlow<QuickSettingsPage> = _page.asStateFlow()

    private val device = MutableStateFlow(DeviceSettings())
    private var performance: DevicePerformance? = null
    private val dualScreen = MutableStateFlow(DualScreenFlags())
    private val levels = MutableStateFlow(
        DisplayLevels(
            volume = QuickSettingsState().systemVolume,
            brightness = DEFAULT_BRIGHTNESS,
            secondaryBrightness = null
        )
    )
    private val hardware = MutableStateFlow<HardwareStatsUi?>(null)
    @Volatile private var volumeInputTimestamp = 0L
    @Volatile private var brightnessInputTimestamp = 0L

    init {
        refreshDisplayLevels()
        pollHardwareWhileShown()
    }

    private fun pollHardwareWhileShown() {
        scope.launch {
            combine(_isOpen, _page) { open, page -> open && page == QuickSettingsPage.PERFORMANCE }
                .distinctUntilChanged()
                .collectLatest { shown ->
                    if (!shown) return@collectLatest
                    var previous: HardwareStatsUi? = null
                    while (true) {
                        val sample = withContext(deviceDispatcher) { hardwareStatsReader.read() }
                        val next = HardwareStatsUi.from(sample, previous).takeUnless { it.isEmpty }
                        previous = next
                        hardware.update { next }
                        delay(HARDWARE_POLL_MS)
                    }
                }
        }
    }

    private val hudEnabled = preferencesRepository.getBuiltinEmulatorSettings()
        .map { it.hudEnabled }
        .distinctUntilChanged()

    val state: StateFlow<QuickSettingsState> = combine(
        preferencesRepository.userPreferences,
        hudEnabled,
        socialRepository.connectionState,
        combine(device, levels, dualScreen, hardware, ::DeviceReadings)
    ) { prefs, hud, social, (deviceSettings, displayLevels, screens, hardwareStats) ->
        QuickSettingsState(
            themeMode = prefs.themeMode,
            primaryColor = prefs.primaryColor,
            soundEnabled = prefs.soundEnabled,
            hapticEnabled = prefs.hapticEnabled,
            vibrationStrength = prefs.hapticStrength,
            swapAB = prefs.swapAB,
            swapXY = prefs.swapXY,
            swapStartSelect = prefs.swapStartSelect,
            fanMode = deviceSettings.fanMode,
            fanSpeed = deviceSettings.fanSpeed,
            fanSupported = deviceSettings.fanSupported,
            performanceModes = deviceSettings.performanceModes,
            performanceMode = deviceSettings.performanceMode,
            refreshRates = deviceSettings.refreshRates,
            refreshRateHz = deviceSettings.refreshRateHz,
            deviceSettingsSupported = deviceSettings.isSupported,
            deviceSettingsEnabled = deviceSettings.hasWritePermission,
            systemVolume = displayLevels.volume,
            screenBrightness = displayLevels.brightness,
            secondaryBrightness = displayLevels.secondaryBrightness,
            isDualScreenActive = screens.active,
            isRolesSwapped = screens.rolesSwapped,
            hudEnabled = hud,
            isSocialLinked = prefs.isSocialLinked,
            isSocialConnected = social is SocialConnectionState.Connected,
            quayPassEnabled = prefs.quayPassEnabled,
            hardwareStats = hardwareStats
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = QuickSettingsState()
    )

    fun setOpen(open: Boolean) {
        _isOpen.update { open }
        if (open) {
            _focusIndex.update { 0 }
            quickFriends.resetFocus()
            loadDeviceSettings()
            refreshDisplayLevels()
        } else {
            quickFriends.dismissModal()
        }
    }

    fun setDualScreen(active: Boolean, rolesSwapped: Boolean) {
        dualScreen.update { DualScreenFlags(active = active, rolesSwapped = rolesSwapped) }
    }

    fun refreshDisplayLevels() {
        scope.launch(deviceDispatcher) {
            val read = readDisplayLevels(levels.value.brightness)
            val now = System.currentTimeMillis()
            val volumeFresh = now - volumeInputTimestamp > VOLUME_ECHO_GUARD_MS
            val brightnessFresh = now - brightnessInputTimestamp > BRIGHTNESS_ECHO_GUARD_MS
            levels.update { current ->
                read.copy(
                    volume = if (volumeFresh) read.volume else current.volume,
                    brightness = if (brightnessFresh) read.brightness else current.brightness,
                    secondaryBrightness = current.secondaryBrightness
                )
            }
            if (!_isOpen.value) return@launch
            val secondary = brightnessController.secondaryBrightness()
            levels.update { it.copy(secondaryBrightness = secondary) }
        }
    }

    private fun readDisplayLevels(fallbackBrightness: Float): DisplayLevels {
        val brightness = brightnessController.getBrightness()
        return DisplayLevels(
            volume = volumeController.getVolume().primary,
            brightness = brightness.primary ?: fallbackBrightness,
            secondaryBrightness = null
        )
    }

    fun activePage(): QuickSettingsPage = quickSettingsEffectivePage(_page.value, state.value)

    fun focusItem(item: QuickSettingsItem) {
        val index = quickSettingsFocusIndexOf(item, state.value)
        if (index >= 0) _focusIndex.update { index }
    }

    fun selectPage(page: QuickSettingsPage) {
        if (page == activePage()) return
        _page.update { page }
        _focusIndex.update { 0 }
        quickFriends.resetFocus()
    }

    fun cyclePage(delta: Int): InputResult {
        val pages = quickSettingsVisiblePages(state.value)
        if (pages.size < 2) return InputResult.handled(SoundType.BOUNDARY)
        val index = pages.indexOf(activePage()).coerceAtLeast(0)
        selectPage(pages[(index + delta).mod(pages.size)])
        return InputResult.HANDLED
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundManager.setEnabled(enabled)
        scope.launch { preferencesRepository.setSoundEnabled(enabled) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        hapticManager.setEnabled(enabled)
        if (enabled) hapticManager.vibrate(HapticPattern.TOGGLE_ON)
        scope.launch { preferencesRepository.setHapticEnabled(enabled) }
    }

    fun setVibrationStrength(strength: Float) {
        val coerced = strength.coerceIn(0f, 1f)
        hapticManager.setStrength(coerced)
        hapticManager.vibrate(HapticPattern.STRENGTH_PREVIEW)
        scope.launch { preferencesRepository.setHapticStrength(coerced) }
    }

    fun setSwapAB(enabled: Boolean) {
        scope.launch { preferencesRepository.setSwapAB(enabled) }
    }

    fun setSwapXY(enabled: Boolean) {
        scope.launch { preferencesRepository.setSwapXY(enabled) }
    }

    fun setSwapStartSelect(enabled: Boolean) {
        scope.launch { preferencesRepository.setSwapStartSelect(enabled) }
    }

    fun setHudEnabled(enabled: Boolean) {
        scope.launch { preferencesRepository.setHudEnabled(enabled) }
    }

    fun swapDisplays() {
        DualScreenManagerHolder.instance?.swapRoles()
    }

    fun toggleQuayPass() {
        scope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            preferencesRepository.setQuayPassEnabled(!prefs.quayPassEnabled)
        }
    }

    fun setSystemVolume(volume: Float) {
        val steps = volumeController.maxVolume
        setVolumeStep((volume.coerceIn(0f, 1f) * steps).roundToInt(), steps)
    }

    private fun setVolumeStep(step: Int, steps: Int) {
        val level = if (steps > 0) step.toFloat() / steps else 0f
        volumeInputTimestamp = System.currentTimeMillis()
        levels.update { it.copy(volume = level) }
        volumeController.setPrimaryVolume(level)
    }

    private fun stepVolume(current: Float, delta: Int): InputResult {
        val steps = volumeController.maxVolume
        val step = (current * steps).roundToInt()
        val next = (step + delta).coerceIn(0, steps)
        if (next == step) return InputResult.handled(SoundType.BOUNDARY)
        setVolumeStep(next, steps)
        return InputResult.HANDLED
    }

    fun setScreenBrightness(brightness: Float) {
        val coerced = brightness.coerceIn(0f, 1f)
        brightnessInputTimestamp = System.currentTimeMillis()
        levels.update { it.copy(brightness = coerced) }
        scope.launch(deviceDispatcher) {
            brightnessInputTimestamp = System.currentTimeMillis()
            if (!brightnessController.setPrimaryBrightness(coerced)) {
                brightnessController.getBrightness().primary?.let { stored ->
                    levels.update { it.copy(brightness = stored) }
                }
            }
        }
    }

    fun setSecondaryBrightness(brightness: Float) {
        val coerced = brightness.coerceIn(0f, 1f)
        levels.update { it.copy(secondaryBrightness = coerced) }
        scope.launch(deviceDispatcher) {
            if (!brightnessController.setSecondaryBrightness(coerced)) {
                val stored = brightnessController.secondaryBrightness()
                levels.update { it.copy(secondaryBrightness = stored) }
            }
        }
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        if (!state.value.deviceSettingsEnabled) return
        device.update { it.copy(performanceMode = mode) }
        scope.launch(deviceDispatcher) {
            val backend = performance ?: return@launch
            if (backend.applyMode(mode) && backend.controlsFan) {
                delay(DEVICE_SETTLE_MS)
                fanModeFor(mode)?.let { PServerExecutor.setSystemSetting(SETTING_FAN_MODE, it.value) }
                delay(DEVICE_SETTLE_MS)
            }
            readDeviceSettings()
        }
    }

    fun setFanMode(mode: FanMode) {
        if (!state.value.deviceSettingsEnabled) return
        device.update { it.copy(fanMode = mode) }
        scope.launch(deviceDispatcher) {
            if (!PServerExecutor.setSystemSetting(SETTING_FAN_MODE, mode.value)) readDeviceSettings()
        }
    }

    fun setFanSpeed(duty: Int) {
        if (!state.value.deviceSettingsEnabled) return
        val clamped = clampFanDuty(duty)
        device.update { it.copy(fanSpeed = clamped) }
        scope.launch(deviceDispatcher) {
            if (!PServerExecutor.setSystemSetting(SETTING_FAN_SPEED, clamped)) readDeviceSettings()
        }
    }

    fun setRefreshRate(hz: Int?) {
        if (!state.value.deviceSettingsEnabled) return
        device.update { it.copy(refreshRateHz = hz) }
        scope.launch(deviceDispatcher) {
            if (!refreshController.setRateHz(hz)) readDeviceSettings()
        }
    }

    private fun fanModeFor(mode: PerformanceMode): FanMode? = when (mode) {
        PerformanceMode.STANDARD -> FanMode.SMART
        PerformanceMode.HIGH -> FanMode.SPORT
        PerformanceMode.MAX -> FanMode.CUSTOM
        else -> null
    }

    private fun loadDeviceSettings() {
        scope.launch(deviceDispatcher) {
            if (performance == null) performance = performanceResolver.resolve()
            if (performance == null) {
                device.update { DeviceSettings(isSupported = false) }
                return@launch
            }
            readDeviceSettings()
        }
    }

    private fun readDeviceSettings() {
        val backend = performance ?: return
        val fan = if (backend.controlsFan) {
            FanMode.fromValue(PServerExecutor.getSystemSetting(SETTING_FAN_MODE, 0)) to
                clampFanDuty(PServerExecutor.getSystemSetting(SETTING_FAN_SPEED, FanController.SPORT_DUTY))
        } else {
            null
        }
        val rates = if (backend.controlsRefreshRate) refreshController.supportedRatesHz() else emptyList()
        val settings = DeviceSettings(
            fanMode = fan?.first ?: FanMode.SMART,
            fanSpeed = fan?.second ?: FanController.SPORT_DUTY,
            fanSupported = fan != null,
            performanceModes = backend.modes,
            performanceMode = backend.currentMode(),
            refreshRates = if (rates.size > 1) listOf<Int?>(null) + rates else emptyList(),
            refreshRateHz = if (rates.size > 1) refreshController.currentRateHz() else null,
            isSupported = true,
            hasWritePermission = backend.canWrite
        )
        device.update { settings }
    }

    private fun clampFanDuty(duty: Int): Int =
        duty.coerceIn(FanController.CUSTOM_DUTY_MIN, FanController.CUSTOM_DUTY_MAX)

    private fun focusedItem(state: QuickSettingsState): QuickSettingsItem? =
        quickSettingsItemAtFocusIndex(activePage(), _focusIndex.value, state)

    private fun moveFocus(delta: Int): InputResult {
        val snapshot = state.value
        val maxIndex = quickSettingsMaxFocusIndex(activePage(), snapshot)
        val current = _focusIndex.value.coerceIn(0, maxIndex)
        val next = computeWrappedIndex(current, delta, maxIndex, wrapMode())
        _focusIndex.update { next }
        return if (next != current) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    private fun stepLevel(current: Float, delta: Int, apply: (Float) -> Unit): InputResult {
        val next = (current + LEVEL_STEP * delta).coerceIn(0f, 1f)
        if (next == current) return InputResult.handled(SoundType.BOUNDARY)
        apply(next)
        return InputResult.HANDLED
    }

    private var lastCustomAccent: Int? = null

    fun setAccentEnabled(enabled: Boolean) {
        val current = state.value.primaryColor
        if (!enabled && current != null) lastCustomAccent = current
        val next = if (enabled) lastCustomAccent ?: AccentHue.colorAt(AccentHue.hueOf(null)) else null
        scope.launch { preferencesRepository.setPrimaryColor(next) }
    }

    fun setAccentHue(fraction: Float) {
        if (state.value.primaryColor == null) return
        scope.launch { preferencesRepository.setPrimaryColor(AccentHue.colorAt(fraction.coerceIn(0f, 1f) * 360f)) }
    }

    private fun stepAccent(state: QuickSettingsState, delta: Int): InputResult {
        val color = state.primaryColor ?: return InputResult.handled(SoundType.BOUNDARY)
        scope.launch { preferencesRepository.setPrimaryColor(AccentHue.shifted(color, AccentHue.STEP * delta)) }
        return InputResult.HANDLED
    }

    private fun stepHaptic(state: QuickSettingsState, delta: Int): InputResult {
        if (!state.hapticEnabled) return InputResult.handled(SoundType.BOUNDARY)
        val atEdge = (delta < 0 && state.vibrationStrength <= 0f) || (delta > 0 && state.vibrationStrength >= 1f)
        if (atEdge) return InputResult.handled(SoundType.BOUNDARY)
        scope.launch {
            val strength = preferencesRepository.adjustHapticStrength(ControlsPreferences.HAPTIC_STRENGTH_STEP * delta)
            hapticManager.setStrength(strength)
            hapticManager.vibrate(HapticPattern.STRENGTH_PREVIEW)
        }
        return InputResult.HANDLED
    }

    private fun stepFanSpeed(state: QuickSettingsState, delta: Int): InputResult {
        val next = clampFanDuty(state.fanSpeed + FanController.CUSTOM_DUTY_STEP * delta)
        if (next == state.fanSpeed) return InputResult.handled(SoundType.BOUNDARY)
        setFanSpeed(next)
        return InputResult.HANDLED
    }

    private fun adjust(delta: Int): InputResult {
        val snapshot = state.value
        return when (focusedItem(snapshot)) {
            QuickSettingsItem.Theme ->
                stepOption(QUICK_THEME_ORDER, snapshot.themeMode, delta, ::setThemeMode)
            QuickSettingsItem.Accent -> stepAccent(snapshot, delta)
            QuickSettingsItem.ScreenBrightness ->
                stepLevel(snapshot.screenBrightness, delta, ::setScreenBrightness)
            QuickSettingsItem.SecondScreenBrightness ->
                stepLevel(snapshot.secondaryBrightness ?: 0f, delta, ::setSecondaryBrightness)
            QuickSettingsItem.SwapDisplays ->
                toggleLeftRight(delta, snapshot.isRolesSwapped) { swapDisplays() }
            QuickSettingsItem.SystemVolume ->
                stepVolume(snapshot.systemVolume, delta)
            QuickSettingsItem.UISounds ->
                toggleLeftRight(delta, snapshot.soundEnabled, ::setSoundEnabled)
            QuickSettingsItem.Haptic -> stepHaptic(snapshot, delta)
            QuickSettingsItem.SwapAB ->
                toggleLeftRight(delta, snapshot.swapAB, ::setSwapAB)
            QuickSettingsItem.SwapXY ->
                toggleLeftRight(delta, snapshot.swapXY, ::setSwapXY)
            QuickSettingsItem.SwapStartSelect ->
                toggleLeftRight(delta, snapshot.swapStartSelect, ::setSwapStartSelect)
            QuickSettingsItem.Performance -> stepOption(
                snapshot.performanceModes,
                snapshot.performanceMode ?: PerformanceMode.BALANCED,
                delta,
                ::setPerformanceMode
            )
            QuickSettingsItem.Refresh ->
                stepOption(snapshot.refreshRates, snapshot.refreshRateHz, delta, ::setRefreshRate)
            QuickSettingsItem.Fan ->
                stepOption(FanMode.entries, snapshot.fanMode, delta, ::setFanMode)
            QuickSettingsItem.FanSpeed -> stepFanSpeed(snapshot, delta)
            QuickSettingsItem.HudOverlay ->
                toggleLeftRight(delta, snapshot.hudEnabled, ::setHudEnabled)
            else -> InputResult.UNHANDLED
        }
    }

    private fun confirm(onOpenDeviceAccess: (() -> Unit)?): InputResult {
        val snapshot = state.value
        return when (focusedItem(snapshot)) {
            QuickSettingsItem.UISounds -> {
                val enabled = !snapshot.soundEnabled
                setSoundEnabled(enabled)
                InputResult.toggled(enabled, if (enabled) SoundType.TOGGLE else SoundType.SILENT)
            }
            QuickSettingsItem.Accent -> {
                val enabled = snapshot.primaryColor == null
                setAccentEnabled(enabled)
                InputResult.toggled(enabled, if (enabled) SoundType.TOGGLE else SoundType.SILENT)
            }
            QuickSettingsItem.Haptic -> {
                val enabled = !snapshot.hapticEnabled
                setHapticEnabled(enabled)
                InputResult.toggled(enabled, if (enabled) SoundType.TOGGLE else SoundType.SILENT)
            }
            QuickSettingsItem.SwapAB -> {
                val enabled = !snapshot.swapAB
                setSwapAB(enabled)
                InputResult.toggled(enabled)
            }
            QuickSettingsItem.SwapXY -> {
                val enabled = !snapshot.swapXY
                setSwapXY(enabled)
                InputResult.toggled(enabled)
            }
            QuickSettingsItem.SwapStartSelect -> {
                val enabled = !snapshot.swapStartSelect
                setSwapStartSelect(enabled)
                InputResult.toggled(enabled)
            }
            QuickSettingsItem.HudOverlay -> {
                val enabled = !snapshot.hudEnabled
                setHudEnabled(enabled)
                InputResult.toggled(enabled)
            }
            QuickSettingsItem.SwapDisplays -> {
                swapDisplays()
                InputResult.handled(SoundType.TOGGLE)
            }
            QuickSettingsItem.DeviceAccess -> {
                if (onOpenDeviceAccess == null) return InputResult.handled(SoundType.BOUNDARY)
                onOpenDeviceAccess()
                InputResult.HANDLED
            }
            else -> InputResult.handled(SoundType.SILENT)
        }
    }

    fun createInputHandler(
        onDismiss: () -> Unit,
        onOpenDeviceAccess: (() -> Unit)?
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult = moveFocus(-1)

        override fun onDown(): InputResult = moveFocus(1)

        override fun onLeft(): InputResult = adjust(-1)

        override fun onRight(): InputResult = adjust(1)

        override fun onPrevSection(): InputResult = cyclePage(-1)

        override fun onNextSection(): InputResult = cyclePage(1)

        override fun onConfirm(): InputResult = confirm(onOpenDeviceAccess)

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
