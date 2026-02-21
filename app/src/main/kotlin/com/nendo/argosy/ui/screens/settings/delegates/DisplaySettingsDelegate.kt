package com.nendo.argosy.ui.screens.settings.delegates

import androidx.core.graphics.ColorUtils
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.GlassBorderTint
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.screens.settings.DisplayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class DisplaySettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val ledController: LEDController,
    private val screenCaptureManager: ScreenCaptureManager
) {
    private val _state = MutableStateFlow(DisplayState())
    val state: StateFlow<DisplayState> = _state.asStateFlow()

    private val _openBackgroundPickerEvent = MutableSharedFlow<Unit>()
    val openBackgroundPickerEvent: SharedFlow<Unit> = _openBackgroundPickerEvent.asSharedFlow()

    private val _previewGame = MutableStateFlow<GameListItem?>(null)
    val previewGame: StateFlow<GameListItem?> = _previewGame.asStateFlow()

    private val colorCount = 7
    private var _colorFocusIndex = 0
    val colorFocusIndex: Int get() = _colorFocusIndex

    fun loadPreviewGame(scope: CoroutineScope) {
        scope.launch {
            _previewGame.value = gameRepository.getFirstGameWithCover()
        }
    }

    suspend fun loadPreviewGames(platformSlugs: Set<String>? = null): List<GameListItem> {
        if (platformSlugs != null && platformSlugs.isNotEmpty()) {
            val filtered = gameRepository.getRecentlyPlayedOnPlatforms(platformSlugs.toList(), 10)
            if (filtered.isNotEmpty()) return filtered
        }
        return gameRepository.getRecentlyPlayedWithCovers(10)
    }

    suspend fun getFirstCachedScreenshot(gameId: Long): String? {
        val paths = gameRepository.getCachedScreenshotPaths(gameId) ?: return null
        val validPaths = paths.split(",").filter { it.startsWith("/") && java.io.File(it).exists() }
        return when {
            validPaths.size > 1 -> validPaths[1]
            validPaths.isNotEmpty() -> validPaths[0]
            else -> null
        }
    }

    suspend fun getScreenshotUrls(gameId: Long): List<String> {
        val raw = gameRepository.getScreenshotPaths(gameId) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun updateState(newState: DisplayState) {
        _state.value = newState
    }

    fun setThemeMode(scope: CoroutineScope, mode: ThemeMode) {
        scope.launch {
            preferencesRepository.setThemeMode(mode)
            _state.update { it.copy(themeMode = mode) }
        }
    }

    fun cycleThemeMode(scope: CoroutineScope) {
        val next = when (_state.value.themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(scope, next)
    }

    fun setPrimaryColor(scope: CoroutineScope, color: Int?) {
        scope.launch {
            preferencesRepository.setCustomColors(color, null, null)
            _state.update { it.copy(primaryColor = color) }
        }
    }

    fun moveColorFocus(delta: Int): Int {
        _colorFocusIndex = (_colorFocusIndex + delta).coerceIn(0, colorCount - 1)
        return _colorFocusIndex
    }

    fun selectFocusedColor(scope: CoroutineScope) {
        val colors = listOf<Int?>(
            null,
            0xFF9575CD.toInt(),
            0xFF4DB6AC.toInt(),
            0xFFFFB74D.toInt(),
            0xFF81C784.toInt(),
            0xFFF06292.toInt(),
            0xFF64B5F6.toInt()
        )
        val color = colors.getOrNull(_colorFocusIndex)
        setPrimaryColor(scope, color)
    }

    fun adjustHue(scope: CoroutineScope, delta: Float) {
        val currentColor = _state.value.primaryColor
        val currentHue = if (currentColor != null) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(currentColor, hsl)
            hsl[0]
        } else {
            180f
        }
        val newHue = (currentHue + delta).mod(360f)
        val newColor = ColorUtils.HSLToColor(floatArrayOf(newHue, 0.7f, 0.5f))
        setPrimaryColor(scope, newColor)
    }

    fun resetToDefaultColor(scope: CoroutineScope) {
        setPrimaryColor(scope, null)
    }

    fun setSecondaryColor(scope: CoroutineScope, color: Int?) {
        scope.launch {
            preferencesRepository.setSecondaryColor(color)
            _state.update { it.copy(secondaryColor = color) }
        }
    }

    fun resetToDefaultSecondaryColor(scope: CoroutineScope) {
        setSecondaryColor(scope, null)
    }

    fun adjustSecondaryHue(scope: CoroutineScope, delta: Float) {
        val currentColor = _state.value.secondaryColor
        val currentHue = if (currentColor != null) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(currentColor, hsl)
            hsl[0]
        } else {
            val primaryColor = _state.value.primaryColor
            if (primaryColor != null) {
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(primaryColor, hsl)
                hsl[0]
            } else {
                180f
            }
        }
        val newHue = (currentHue + delta).mod(360f)
        val newColor = ColorUtils.HSLToColor(floatArrayOf(newHue, 0.7f, 0.5f))
        setSecondaryColor(scope, newColor)
    }

    fun setGridDensity(scope: CoroutineScope, density: GridDensity) {
        scope.launch {
            preferencesRepository.setGridDensity(density)
            _state.update { it.copy(gridDensity = density) }
        }
    }

    fun cycleGridDensity(scope: CoroutineScope) {
        val next = when (_state.value.gridDensity) {
            GridDensity.COMPACT -> GridDensity.NORMAL
            GridDensity.NORMAL -> GridDensity.SPACIOUS
            GridDensity.SPACIOUS -> GridDensity.COMPACT
        }
        setGridDensity(scope, next)
    }

    fun setUiScale(scope: CoroutineScope, scale: Int) {
        val newValue = scale.coerceIn(75, 150)
        scope.launch {
            preferencesRepository.setUiScale(newValue)
            _state.update { it.copy(uiScale = newValue) }
        }
    }

    fun adjustUiScale(scope: CoroutineScope, delta: Int) {
        val current = _state.value.uiScale
        val newValue = (current + delta).coerceIn(75, 150)
        if (newValue != current) {
            setUiScale(scope, newValue)
        }
    }

    fun cycleUiScale(scope: CoroutineScope) {
        val current = _state.value.uiScale
        val newValue = if (current >= 150) 75 else current + 5
        setUiScale(scope, newValue)
    }

    fun adjustBackgroundBlur(scope: CoroutineScope, delta: Int) {
        val current = _state.value.backgroundBlur
        val newValue = (current + delta).coerceIn(0, 100)
        if (newValue != current) {
            scope.launch {
                preferencesRepository.setBackgroundBlur(newValue)
                _state.update { it.copy(backgroundBlur = newValue) }
            }
        }
    }

    fun adjustBackgroundSaturation(scope: CoroutineScope, delta: Int) {
        val current = _state.value.backgroundSaturation
        val newValue = (current + delta).coerceIn(0, 100)
        if (newValue != current) {
            scope.launch {
                preferencesRepository.setBackgroundSaturation(newValue)
                _state.update { it.copy(backgroundSaturation = newValue) }
            }
        }
    }

    fun adjustBackgroundOpacity(scope: CoroutineScope, delta: Int) {
        val current = _state.value.backgroundOpacity
        val newValue = (current + delta).coerceIn(0, 100)
        if (newValue != current) {
            scope.launch {
                preferencesRepository.setBackgroundOpacity(newValue)
                _state.update { it.copy(backgroundOpacity = newValue) }
            }
        }
    }

    fun setUseGameBackground(scope: CoroutineScope, use: Boolean) {
        scope.launch {
            preferencesRepository.setUseGameBackground(use)
            _state.update { it.copy(useGameBackground = use) }
        }
    }

    fun setUseAccentColorFooter(scope: CoroutineScope, use: Boolean) {
        scope.launch {
            preferencesRepository.setUseAccentColorFooter(use)
            _state.update { it.copy(useAccentColorFooter = use) }
        }
    }

    fun setCustomBackgroundPath(scope: CoroutineScope, path: String?) {
        scope.launch {
            preferencesRepository.setCustomBackgroundPath(path)
            _state.update { it.copy(customBackgroundPath = path) }
        }
    }

    fun openBackgroundPicker(scope: CoroutineScope) {
        scope.launch {
            _openBackgroundPickerEvent.emit(Unit)
        }
    }

    fun cycleBoxArtShape(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtShape, direction)
        scope.launch {
            preferencesRepository.setBoxArtShape(next)
            _state.update { it.copy(boxArtShape = next) }
        }
    }

    fun cycleBoxArtCornerRadius(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtCornerRadius, direction)
        scope.launch {
            preferencesRepository.setBoxArtCornerRadius(next)
            _state.update { it.copy(boxArtCornerRadius = next) }
        }
    }

    fun cycleBoxArtBorderThickness(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtBorderThickness, direction)
        scope.launch {
            preferencesRepository.setBoxArtBorderThickness(next)
            _state.update { it.copy(boxArtBorderThickness = next) }
        }
    }

    fun cycleBoxArtBorderStyle(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtBorderStyle, direction)
        scope.launch {
            preferencesRepository.setBoxArtBorderStyle(next)
            _state.update { it.copy(boxArtBorderStyle = next) }
        }
    }

    fun cycleGlassBorderTint(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.glassBorderTint, direction)
        scope.launch {
            preferencesRepository.setGlassBorderTint(next)
            _state.update { it.copy(glassBorderTint = next) }
        }
    }

    fun cycleBoxArtGlowStrength(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtGlowStrength, direction)
        scope.launch {
            preferencesRepository.setBoxArtGlowStrength(next)
            _state.update { it.copy(boxArtGlowStrength = next) }
        }
    }

    fun cycleBoxArtOuterEffect(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtOuterEffect, direction)
        scope.launch {
            preferencesRepository.setBoxArtOuterEffect(next)
            _state.update { it.copy(boxArtOuterEffect = next) }
        }
    }

    fun cycleBoxArtOuterEffectThickness(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtOuterEffectThickness, direction)
        scope.launch {
            preferencesRepository.setBoxArtOuterEffectThickness(next)
            _state.update { it.copy(boxArtOuterEffectThickness = next) }
        }
    }

    fun cycleGlowColorMode(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.glowColorMode, direction)
        scope.launch {
            preferencesRepository.setGlowColorMode(next)
            _state.update { it.copy(glowColorMode = next) }
        }
    }

    fun cycleSystemIconPosition(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.systemIconPosition, direction)
        scope.launch {
            preferencesRepository.setSystemIconPosition(next)
            _state.update { it.copy(systemIconPosition = next) }
        }
    }

    fun cycleSystemIconPadding(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.systemIconPadding, direction)
        scope.launch {
            preferencesRepository.setSystemIconPadding(next)
            _state.update { it.copy(systemIconPadding = next) }
        }
    }

    fun cycleBoxArtInnerEffect(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtInnerEffect, direction)
        scope.launch {
            preferencesRepository.setBoxArtInnerEffect(next)
            _state.update { it.copy(boxArtInnerEffect = next) }
        }
    }

    fun cycleBoxArtInnerEffectThickness(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.boxArtInnerEffectThickness, direction)
        scope.launch {
            preferencesRepository.setBoxArtInnerEffectThickness(next)
            _state.update { it.copy(boxArtInnerEffectThickness = next) }
        }
    }

    fun cycleDefaultView(scope: CoroutineScope) {
        val current = _state.value.defaultView
        val next = when (current) {
            DefaultView.HOME -> DefaultView.LIBRARY
            DefaultView.LIBRARY -> DefaultView.HOME
        }
        scope.launch {
            preferencesRepository.setDefaultView(next)
            _state.update { it.copy(defaultView = next) }
        }
    }

    fun setGradientPreset(scope: CoroutineScope, preset: GradientPreset) {
        _state.update { it.copy(gradientPreset = preset) }
        scope.launch {
            preferencesRepository.setGradientPreset(preset)
        }
    }

    fun toggleGradientAdvancedMode(scope: CoroutineScope) {
        val newAdvanced = !_state.value.gradientAdvancedMode
        scope.launch {
            preferencesRepository.setGradientAdvancedMode(newAdvanced)
            _state.update { it.copy(gradientAdvancedMode = newAdvanced) }
        }
    }

    fun setVideoWallpaperEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setVideoWallpaperEnabled(enabled)
            _state.update { it.copy(videoWallpaperEnabled = enabled) }
        }
    }

    fun cycleVideoWallpaperDelay(scope: CoroutineScope) {
        val options = listOf(0, 1, 3, 5, 10)
        val next = cycleInList(_state.value.videoWallpaperDelaySeconds, options)
        scope.launch {
            preferencesRepository.setVideoWallpaperDelaySeconds(next)
            _state.update { it.copy(videoWallpaperDelaySeconds = next) }
        }
    }

    fun setVideoWallpaperMuted(scope: CoroutineScope, muted: Boolean) {
        scope.launch {
            preferencesRepository.setVideoWallpaperMuted(muted)
            _state.update { it.copy(videoWallpaperMuted = muted) }
        }
    }

    fun isAmbientLedAvailable(): Boolean = ledController.isAvailable

    fun setAmbientLedEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedEnabled(enabled)
            _state.update { it.copy(ambientLedEnabled = enabled) }
        }
    }

    fun setAmbientLedBrightness(scope: CoroutineScope, brightness: Int) {
        scope.launch {
            val clamped = brightness.coerceIn(0, 100)
            preferencesRepository.setAmbientLedBrightness(clamped)
            _state.update { it.copy(ambientLedBrightness = clamped) }
        }
    }

    fun adjustAmbientLedBrightness(scope: CoroutineScope, delta: Int) {
        setAmbientLedBrightness(scope, _state.value.ambientLedBrightness + delta)
    }

    fun cycleAmbientLedBrightness(scope: CoroutineScope) {
        val current = _state.value.ambientLedBrightness
        val next = (current + 10) % 110
        setAmbientLedBrightness(scope, next)
    }

    fun setAmbientLedAudioBrightness(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedAudioBrightness(enabled)
            _state.update { it.copy(ambientLedAudioBrightness = enabled) }
        }
    }

    fun setAmbientLedAudioColors(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedAudioColors(enabled)
            _state.update { it.copy(ambientLedAudioColors = enabled) }
        }
    }

    fun cycleAmbientLedColorMode(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.ambientLedColorMode, direction)
        scope.launch {
            preferencesRepository.setAmbientLedColorMode(next)
            _state.update { it.copy(ambientLedColorMode = next) }
        }
    }

    fun setInstalledOnlyHome(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setInstalledOnlyHome(enabled)
            _state.update { it.copy(installedOnlyHome = enabled) }
        }
    }

    fun hasScreenCapturePermission(): Boolean = screenCaptureManager.hasPermission.value

    fun observeScreenCapturePermission(scope: CoroutineScope) {
        scope.launch {
            screenCaptureManager.hasPermission.collect { hasPermission ->
                _state.update { it.copy(hasScreenCapturePermission = hasPermission) }
            }
        }
    }
}
