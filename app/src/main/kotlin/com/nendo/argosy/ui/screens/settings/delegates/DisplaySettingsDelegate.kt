package com.nendo.argosy.ui.screens.settings.delegates

import androidx.core.graphics.ColorUtils
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BackdropEdgeStyle
import com.nendo.argosy.data.preferences.BackdropMotion
import com.nendo.argosy.data.preferences.BackdropPreset
import com.nendo.argosy.data.preferences.BackdropVertexIcon
import com.nendo.argosy.ui.theme.backdrop.BackdropConfig
import com.nendo.argosy.ui.theme.backdrop.defaultEdgeStyle
import com.nendo.argosy.ui.theme.backdrop.defaultVertexIcons
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import java.security.SecureRandom
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
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.HomeBackgroundMode
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

    fun setSurfaceTintBleed(scope: CoroutineScope, bleed: Int) {
        val clamped = bleed.coerceIn(0, 100)
        scope.launch {
            preferencesRepository.setSurfaceTintBleed(clamped)
            _state.update { it.copy(surfaceTintBleed = clamped) }
        }
    }

    fun adjustSurfaceTintBleed(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceTintBleed
        val newValue = (current + delta).coerceIn(0, 100)
        if (newValue != current) {
            setSurfaceTintBleed(scope, newValue)
        }
    }

    fun cycleSurfaceTintBleed(scope: CoroutineScope) {
        val next = (_state.value.surfaceTintBleed + 10) % 110
        setSurfaceTintBleed(scope, next)
    }

    private fun updateBackdrop(scope: CoroutineScope, write: suspend () -> Unit, transform: (BackdropConfig) -> BackdropConfig) {
        scope.launch {
            write()
            _state.update { it.copy(surfaceBackdrop = transform(it.surfaceBackdrop)) }
        }
    }

    fun setBackdropEnabled(scope: CoroutineScope, enabled: Boolean) =
        updateBackdrop(scope, { preferencesRepository.setBackdropEnabled(enabled) }) { it.copy(enabled = enabled) }

    fun setBackdropPreset(scope: CoroutineScope, preset: BackdropPreset) {
        val edge = preset.defaultEdgeStyle
        val vertex = preset.defaultVertexIcons
        updateBackdrop(scope, { preferencesRepository.setBackdropPreset(preset, edge, vertex) }) {
            it.copy(preset = preset, edgeStyle = edge, vertexIcons = vertex)
        }
    }

    fun cycleBackdropPreset(scope: CoroutineScope, direction: Int = 1) =
        setBackdropPreset(scope, cycleEnum(_state.value.surfaceBackdrop.preset, direction))

    fun setBackdropCellSize(scope: CoroutineScope, sizeDp: Int) {
        val clamped = sizeDp.coerceIn(CELL_SIZE_MIN, CELL_SIZE_MAX)
        updateBackdrop(scope, { preferencesRepository.setBackdropCellSize(clamped) }) { it.copy(cellSize = clamped) }
    }

    fun adjustBackdropCellSize(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceBackdrop.cellSize
        val newValue = (current + delta).coerceIn(CELL_SIZE_MIN, CELL_SIZE_MAX)
        if (newValue != current) setBackdropCellSize(scope, newValue)
    }

    fun cycleBackdropCellSize(scope: CoroutineScope) {
        val current = _state.value.surfaceBackdrop.cellSize
        setBackdropCellSize(scope, if (current >= CELL_SIZE_MAX) CELL_SIZE_MIN else current + CELL_SIZE_STEP)
    }

    fun setBackdropScatter(scope: CoroutineScope, scatter: Int) {
        val clamped = scatter.coerceIn(0, 200)
        updateBackdrop(scope, { preferencesRepository.setBackdropScatter(clamped) }) { it.copy(scatter = clamped) }
    }

    fun adjustBackdropScatter(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceBackdrop.scatter
        val newValue = (current + delta).coerceIn(0, 200)
        if (newValue != current) setBackdropScatter(scope, newValue)
    }

    fun cycleBackdropScatter(scope: CoroutineScope) {
        val current = _state.value.surfaceBackdrop.scatter
        setBackdropScatter(scope, if (current >= 200) 0 else current + 10)
    }

    fun setBackdropScaleJitter(scope: CoroutineScope, jitter: Int) {
        val clamped = jitter.coerceIn(0, 200)
        updateBackdrop(scope, { preferencesRepository.setBackdropScaleJitter(clamped) }) { it.copy(scaleJitter = clamped) }
    }

    fun adjustBackdropScaleJitter(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceBackdrop.scaleJitter
        val newValue = (current + delta).coerceIn(0, 200)
        if (newValue != current) setBackdropScaleJitter(scope, newValue)
    }

    fun cycleBackdropScaleJitter(scope: CoroutineScope) {
        val current = _state.value.surfaceBackdrop.scaleJitter
        setBackdropScaleJitter(scope, if (current >= 200) 0 else current + 10)
    }

    fun setBackdropStrength(scope: CoroutineScope, strength: Int) {
        val clamped = strength.coerceIn(10, 100)
        updateBackdrop(scope, { preferencesRepository.setBackdropStrength(clamped) }) { it.copy(strength = clamped) }
    }

    fun adjustBackdropStrength(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceBackdrop.strength
        val newValue = (current + delta).coerceIn(10, 100)
        if (newValue != current) setBackdropStrength(scope, newValue)
    }

    fun cycleBackdropStrength(scope: CoroutineScope) {
        val current = _state.value.surfaceBackdrop.strength
        setBackdropStrength(scope, if (current >= 100) 10 else current + 10)
    }

    fun setBackdropEdgeStyle(scope: CoroutineScope, style: BackdropEdgeStyle) =
        updateBackdrop(scope, { preferencesRepository.setBackdropEdgeStyle(style) }) { it.copy(edgeStyle = style) }

    fun cycleBackdropEdgeStyle(scope: CoroutineScope, direction: Int = 1) =
        setBackdropEdgeStyle(scope, cycleEnum(_state.value.surfaceBackdrop.edgeStyle, direction))

    fun setBackdropVertexIcons(scope: CoroutineScope, icons: BackdropVertexIcon) =
        updateBackdrop(scope, { preferencesRepository.setBackdropVertexIcons(icons) }) { it.copy(vertexIcons = icons) }

    fun cycleBackdropVertexIcons(scope: CoroutineScope, direction: Int = 1) =
        setBackdropVertexIcons(scope, cycleEnum(_state.value.surfaceBackdrop.vertexIcons, direction))

    fun setBackdropMotion(scope: CoroutineScope, motion: BackdropMotion) =
        updateBackdrop(scope, { preferencesRepository.setBackdropMotion(motion) }) { it.copy(motion = motion) }

    fun cycleBackdropMotion(scope: CoroutineScope, direction: Int = 1) =
        setBackdropMotion(scope, cycleEnum(_state.value.surfaceBackdrop.motion, direction))

    fun setBackdropMotionSpeed(scope: CoroutineScope, speed: Int) {
        val clamped = speed.coerceIn(MOTION_SPEED_MIN, MOTION_SPEED_MAX)
        updateBackdrop(scope, { preferencesRepository.setBackdropMotionSpeed(clamped) }) { it.copy(motionSpeed = clamped) }
    }

    fun adjustBackdropMotionSpeed(scope: CoroutineScope, delta: Int) {
        val current = _state.value.surfaceBackdrop.motionSpeed
        val newValue = (current + delta).coerceIn(MOTION_SPEED_MIN, MOTION_SPEED_MAX)
        if (newValue != current) setBackdropMotionSpeed(scope, newValue)
    }

    fun cycleBackdropMotionSpeed(scope: CoroutineScope) {
        val current = _state.value.surfaceBackdrop.motionSpeed
        setBackdropMotionSpeed(
            scope,
            if (current >= MOTION_SPEED_MAX) MOTION_SPEED_MIN else current + MOTION_SPEED_STEP
        )
    }

    fun setBackdropDriftAngle(scope: CoroutineScope, angle: Float) {
        val wrapped = angle.mod(360f)
        updateBackdrop(scope, { preferencesRepository.setBackdropDriftAngle(wrapped) }) { it.copy(driftAngle = wrapped) }
    }

    fun adjustBackdropDriftAngle(scope: CoroutineScope, deltaDegrees: Float) =
        setBackdropDriftAngle(scope, _state.value.surfaceBackdrop.driftAngle + deltaDegrees)

    fun reshuffleBackdropSeed(scope: CoroutineScope) {
        val seed = SecureRandom().nextLong()
        updateBackdrop(scope, { preferencesRepository.setBackdropSeed(seed) }) { it.copy(seed = seed) }
    }

    private fun fontScaleOf(slot: FontSlot): Int = when (slot) {
        FontSlot.DISPLAY -> _state.value.displayFontScale
        FontSlot.BODY -> _state.value.bodyFontScale
    }

    fun setFontScale(scope: CoroutineScope, slot: FontSlot, scale: Int) {
        val clamped = scale.coerceIn(50, 150)
        scope.launch {
            preferencesRepository.setFontScale(slot, clamped)
            _state.update {
                when (slot) {
                    FontSlot.DISPLAY -> it.copy(displayFontScale = clamped)
                    FontSlot.BODY -> it.copy(bodyFontScale = clamped)
                }
            }
        }
    }

    fun adjustFontScale(scope: CoroutineScope, slot: FontSlot, delta: Int) {
        val current = fontScaleOf(slot)
        val newValue = (current + delta).coerceIn(50, 150)
        if (newValue != current) {
            setFontScale(scope, slot, newValue)
        }
    }

    fun cycleFontScale(scope: CoroutineScope, slot: FontSlot) {
        val next = (fontScaleOf(slot) - 50 + 5).mod(105) + 50
        setFontScale(scope, slot, next)
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
        val newValue = scale.coerceIn(50, 150)
        scope.launch {
            preferencesRepository.setUiScale(newValue)
            _state.update { it.copy(uiScale = newValue) }
        }
    }

    fun adjustUiScale(scope: CoroutineScope, delta: Int) {
        val current = _state.value.uiScale
        val newValue = (current + delta).coerceIn(50, 150)
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

    fun setHomeBackgroundMode(scope: CoroutineScope, mode: HomeBackgroundMode) {
        scope.launch {
            preferencesRepository.setHomeBackgroundMode(mode)
            _state.update { it.copy(homeBackgroundMode = mode) }
        }
    }

    fun cycleHomeBackgroundMode(scope: CoroutineScope, direction: Int = 1) =
        setHomeBackgroundMode(scope, cycleEnum(_state.value.homeBackgroundMode, direction))

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
        val corners = SystemIconPosition.CORNERS
        val current = _state.value.systemIconPosition
        val idx = corners.indexOf(current).coerceAtLeast(0)
        val next = corners[(idx + direction).mod(corners.size)]
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

    fun cyclePlatformIndicatorStyle(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.platformIndicatorStyle, direction)
        scope.launch {
            preferencesRepository.setPlatformIndicatorStyle(next)
            _state.update { it.copy(platformIndicatorStyle = next) }
        }
    }

    fun cyclePlatformIndicatorContent(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleEnum(_state.value.platformIndicatorContent, direction)
        scope.launch {
            preferencesRepository.setPlatformIndicatorContent(next)
            _state.update { it.copy(platformIndicatorContent = next) }
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

    fun cycleVideoWallpaperDelay(scope: CoroutineScope, direction: Int = 1) {
        val next = cycleInList(_state.value.videoWallpaperDelaySeconds, VIDEO_DELAY_SECONDS, direction)
        scope.launch {
            preferencesRepository.setVideoWallpaperDelaySeconds(next)
            _state.update { it.copy(videoWallpaperDelaySeconds = next) }
        }
    }

    companion object {
        val VIDEO_DELAY_SECONDS = listOf(0, 1, 3, 5, 10)
        const val MOTION_SPEED_MIN = 25
        const val MOTION_SPEED_MAX = 200
        const val MOTION_SPEED_STEP = 25
        const val CELL_SIZE_MIN = ComponentDefaults.SurfaceBackdrop.cellSizeMinDp
        const val CELL_SIZE_MAX = ComponentDefaults.SurfaceBackdrop.cellSizeMaxDp
        const val CELL_SIZE_STEP = ComponentDefaults.SurfaceBackdrop.cellSizeStepDp
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

    fun setAmbientLedCoverArtEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedCoverArtEnabled(enabled)
            _state.update { it.copy(ambientLedCoverArtEnabled = enabled) }
        }
    }

    fun setAmbientLedCustomColor(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedCustomColor(enabled)
            _state.update { it.copy(ambientLedCustomColor = enabled) }
        }
    }

    fun setAmbientLedCustomColorHue(scope: CoroutineScope, hue: Int) {
        scope.launch {
            val clamped = hue.coerceIn(0, 360)
            preferencesRepository.setAmbientLedCustomColorHue(clamped)
            _state.update { it.copy(ambientLedCustomColorHue = clamped) }
        }
    }

    fun adjustAmbientLedCustomColorHue(scope: CoroutineScope, delta: Int) {
        setAmbientLedCustomColorHue(scope, _state.value.ambientLedCustomColorHue + delta)
    }

    private val transitionSteps = listOf(0, 100, 250, 500, 1000)

    fun setAmbientLedTransitionMs(scope: CoroutineScope, ms: Int) {
        scope.launch {
            preferencesRepository.setAmbientLedTransitionMs(ms)
            _state.update { it.copy(ambientLedTransitionMs = ms) }
        }
    }

    fun cycleAmbientLedTransitionMs(scope: CoroutineScope, direction: Int) {
        val current = _state.value.ambientLedTransitionMs
        val currentIndex = transitionSteps.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction).coerceIn(0, transitionSteps.lastIndex)
        setAmbientLedTransitionMs(scope, transitionSteps[nextIndex])
    }

    fun cycleAmbientLedTransitionMsWrap(scope: CoroutineScope) {
        val current = _state.value.ambientLedTransitionMs
        val currentIndex = transitionSteps.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + 1) % transitionSteps.size
        setAmbientLedTransitionMs(scope, transitionSteps[nextIndex])
    }

    fun setAmbientLedScreenEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedScreenEnabled(enabled)
            _state.update { it.copy(ambientLedScreenEnabled = enabled) }
        }
    }

    fun setAmbientLedAchievementFlash(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAmbientLedAchievementFlash(enabled)
            _state.update { it.copy(ambientLedAchievementFlash = enabled) }
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
