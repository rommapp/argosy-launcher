package com.nendo.argosy.ui.screens.settings.delegates

import androidx.core.graphics.ColorUtils
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.domain.model.GripAutoControllers
import com.nendo.argosy.data.preferences.BackdropEdgeStyle
import com.nendo.argosy.data.preferences.BackdropMotion
import com.nendo.argosy.data.preferences.BackdropPreset
import com.nendo.argosy.data.preferences.BackdropVertexIcon
import com.nendo.argosy.ui.theme.AccentHue
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MAX_PERCENT
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MIN_PERCENT
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
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.CustomGridShapeStore
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.HomeTileRepository
import com.nendo.argosy.domain.model.CustomGridConfig
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsPreviewGame
import com.nendo.argosy.ui.screens.settings.toSettingsPreviewGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class DisplaySettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val ledController: LEDController,
    private val screenCaptureManager: ScreenCaptureManager,
    private val customGridShapeStore: CustomGridShapeStore,
    private val homeTileRepository: HomeTileRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    private val _state = MutableStateFlow(DisplayState())
    val state: StateFlow<DisplayState> = _state.asStateFlow()

    private val scrollReflowLock = Mutex()

    private val _openBackgroundPickerEvent = MutableSharedFlow<Unit>()
    val openBackgroundPickerEvent: SharedFlow<Unit> = _openBackgroundPickerEvent.asSharedFlow()

    private val _previewGame = MutableStateFlow<SettingsPreviewGame?>(null)
    val previewGame: StateFlow<SettingsPreviewGame?> = _previewGame.asStateFlow()

    private val colorCount = 7
    private var _colorFocusIndex = 0
    val colorFocusIndex: Int get() = _colorFocusIndex

    fun loadPreviewGame(scope: CoroutineScope) {
        scope.launch {
            val first = gameRepository.getFirstGameWithCover()
            _previewGame.value = first?.let { listOf(it).toPreviewGames().firstOrNull() }
        }
    }

    suspend fun loadPreviewGames(platformSlugs: Set<String>? = null): List<SettingsPreviewGame> {
        if (platformSlugs != null && platformSlugs.isNotEmpty()) {
            val filtered = gameRepository.getRecentlyPlayedOnPlatforms(platformSlugs.toList(), 10)
            if (filtered.isNotEmpty()) return filtered.toPreviewGames()
        }
        return gameRepository.getRecentlyPlayedWithCovers(10).toPreviewGames()
    }

    private suspend fun List<GameListItem>.toPreviewGames(): List<SettingsPreviewGame> {
        val art = gameRepository.getArt(map { it.id })
        return map { it.toSettingsPreviewGame(art[it.id]?.coverPath) }
    }

    suspend fun getFirstCachedScreenshot(gameId: Long): String? {
        val validPaths = gameRepository.getScreenshots(gameId)
            .mapNotNull { row -> row.cachedPath?.takeIf { it.startsWith("/") && java.io.File(it).exists() } }
        return validPaths.getOrNull(1) ?: validPaths.firstOrNull()
    }

    suspend fun getScreenshotUrls(gameId: Long): List<String> =
        gameRepository.getScreenshots(gameId).map { it.sourceUrl }

    fun updateState(newState: DisplayState) {
        _state.value = newState
    }

    fun setThemeMode(scope: CoroutineScope, mode: ThemeMode) {
        scope.launch {
            preferencesRepository.setThemeMode(mode)
            _state.update { it.copy(themeMode = mode) }
        }
    }

    fun setPrimaryColor(scope: CoroutineScope, color: Int?) {
        scope.launch {
            preferencesRepository.setPrimaryColor(color)
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
        setPrimaryColor(scope, AccentHue.shifted(_state.value.primaryColor, delta))
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

    fun setBackdropMotion(scope: CoroutineScope, motion: BackdropMotion) =
        updateBackdrop(scope, { preferencesRepository.setBackdropMotion(motion) }) { it.copy(motion = motion) }

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

    fun setLibraryLayout(scope: CoroutineScope, layout: com.nendo.argosy.data.preferences.LibraryLayout) {
        scope.launch {
            preferencesRepository.setLibraryLayout(layout)
            _state.update { it.copy(libraryLayout = layout) }
        }
    }

    fun setLibraryBoxArt3d(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setLibraryBoxArt3d(enabled)
            _state.update { it.copy(libraryBoxArt3d = enabled) }
        }
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

    fun setGripReserveMode(scope: CoroutineScope, mode: GripReserveMode) {
        scope.launch {
            preferencesRepository.setGripReserveMode(mode)
            _state.update { it.copy(gripReserveMode = mode) }
        }
    }

    fun setGripReservePercent(scope: CoroutineScope, percent: Int) {
        val newValue = percent.coerceIn(GRIP_RESERVE_MIN_PERCENT, GRIP_RESERVE_MAX_PERCENT)
        scope.launch {
            preferencesRepository.setGripReservePercent(newValue)
            _state.update { it.copy(gripReservePercent = newValue) }
        }
    }

    fun adjustGripReservePercent(scope: CoroutineScope, delta: Int) {
        val current = _state.value.gripReservePercent
        val newValue = (current + delta).coerceIn(GRIP_RESERVE_MIN_PERCENT, GRIP_RESERVE_MAX_PERCENT)
        if (newValue != current) {
            setGripReservePercent(scope, newValue)
        }
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

    fun customGridShape(): CustomGridShape =
        customGridShapeStore.shapeFor(_state.value.homeLayout.customGrid)

    fun setHomeLayout(scope: CoroutineScope, settings: com.nendo.argosy.domain.model.HomeLayoutSettings) {
        val previous = _state.value.homeLayout.customGrid
        val next = settings.customGrid
        if (previous.columns == next.columns && previous.rows == next.rows) {
            storeHomeLayout(scope, settings)
            return
        }
        val from = arrangedFrom(previous)
        val to = customGridShapeStore.layoutFor(next)
        val arrangement = to.scrollArrangement ?: from?.scrollArrangement
        storeHomeLayout(scope, settings.copy(customGrid = next.copy(scrollArrangement = arrangement)))
        reflowScrollGrid(scope, from, to)
    }

    private fun storeHomeLayout(scope: CoroutineScope, settings: com.nendo.argosy.domain.model.HomeLayoutSettings) {
        _state.update { it.copy(homeLayout = settings) }
        scope.launch { preferencesRepository.setHomeLayout(settings) }
    }

    private fun arrangedFrom(previous: CustomGridConfig): CustomGridLayout? =
        previous.scrollArrangement?.layout
            ?: customGridShapeStore.layoutFor(previous).takeIf { it.scrollAxis != null }

    private fun reflowScrollGrid(scope: CoroutineScope, from: CustomGridLayout?, to: CustomGridLayout) {
        if (from == null || !from.needsScrollReflowTo(to)) return
        scope.launch {
            scrollReflowLock.withLock {
                homeTileRepository.reflowScroll(syncPreferencesRepository.getRommUserId(), from, to)
            }
        }
    }

    fun setPresentationStyle(scope: CoroutineScope, style: com.nendo.argosy.domain.model.PresentationStyle) {
        _state.update { it.copy(presentationStyle = style) }
        scope.launch { preferencesRepository.setPresentationStyle(style) }
    }

    fun setUseAccentColorFooter(scope: CoroutineScope, use: Boolean) {
        scope.launch {
            preferencesRepository.setUseAccentColorFooter(use)
            _state.update { it.copy(useAccentColorFooter = use) }
        }
    }

    fun setCompactFooter(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setCompactFooter(enabled)
            _state.update { it.copy(compactFooter = enabled) }
        }
    }

    fun setLockScreenArt(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setLockScreenArt(enabled)
            _state.update { it.copy(lockScreenArt = enabled) }
        }
    }

    fun showGripControllerModal() {
        _state.update { it.copy(showGripControllerModal = true) }
    }

    fun hideGripControllerModal() {
        _state.update { it.copy(showGripControllerModal = false) }
    }

    fun addGripAutoController(scope: CoroutineScope, controllerId: String, controllerName: String) {
        updateGripAutoControllers(scope) { it.with(controllerId, controllerName) }
    }

    fun removeGripAutoController(scope: CoroutineScope, controllerId: String) {
        updateGripAutoControllers(scope) { it.without(controllerId) }
    }

    private fun updateGripAutoControllers(
        scope: CoroutineScope,
        transform: (GripAutoControllers) -> GripAutoControllers
    ) {
        scope.launch {
            val updated = transform(_state.value.gripAutoControllers)
            preferencesRepository.setGripAutoControllers(updated)
            _state.update { it.copy(gripAutoControllers = updated) }
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

    fun setBoxArtShape(scope: CoroutineScope, next: BoxArtShape) {
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

    fun setBoxArtBorderThickness(scope: CoroutineScope, next: BoxArtBorderThickness) {
        scope.launch {
            preferencesRepository.setBoxArtBorderThickness(next)
            _state.update { it.copy(boxArtBorderThickness = next) }
        }
    }

    fun setBoxArtBorderStyle(scope: CoroutineScope, next: BoxArtBorderStyle) {
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

    fun setBoxArtOuterEffect(scope: CoroutineScope, next: BoxArtOuterEffect) {
        scope.launch {
            preferencesRepository.setBoxArtOuterEffect(next)
            _state.update { it.copy(boxArtOuterEffect = next) }
        }
    }

    fun setBoxArtOuterEffectThickness(scope: CoroutineScope, next: BoxArtOuterEffectThickness) {
        scope.launch {
            preferencesRepository.setBoxArtOuterEffectThickness(next)
            _state.update { it.copy(boxArtOuterEffectThickness = next) }
        }
    }

    fun setGlowColorMode(scope: CoroutineScope, next: GlowColorMode) {
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

    fun setSystemIconPadding(scope: CoroutineScope, next: SystemIconPadding) {
        scope.launch {
            preferencesRepository.setSystemIconPadding(next)
            _state.update { it.copy(systemIconPadding = next) }
        }
    }

    fun setPlatformIndicatorStyle(scope: CoroutineScope, next: PlatformIndicatorStyle) {
        scope.launch {
            preferencesRepository.setPlatformIndicatorStyle(next)
            _state.update { it.copy(platformIndicatorStyle = next) }
        }
    }

    fun setPlatformIndicatorContent(scope: CoroutineScope, next: PlatformIndicatorContent) {
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

    fun setBoxArtInnerEffectThickness(scope: CoroutineScope, next: BoxArtInnerEffectThickness) {
        scope.launch {
            preferencesRepository.setBoxArtInnerEffectThickness(next)
            _state.update { it.copy(boxArtInnerEffectThickness = next) }
        }
    }

    fun setLibraryDefaultSortIndex(scope: CoroutineScope, index: Int) {
        val option = com.nendo.argosy.data.model.SortOption.entries
            .getOrElse(index / 2) { com.nendo.argosy.data.model.SortOption.TITLE }
        val descending = index % 2 == 1
        scope.launch {
            preferencesRepository.setLibraryDefaultSort(option.name, descending)
            _state.update { it.copy(libraryDefaultSort = option.name, libraryDefaultSortDescending = descending) }
        }
    }

    fun cycleLibraryDefaultSort(scope: CoroutineScope, direction: Int) {
        val entries = com.nendo.argosy.data.model.SortOption.entries
        val current = entries.firstOrNull { it.name == _state.value.libraryDefaultSort }
            ?: com.nendo.argosy.data.model.SortOption.TITLE
        val descending = _state.value.libraryDefaultSortDescending ?: current.defaultDescending
        val total = entries.size * 2
        val next = ((entries.indexOf(current) * 2 + (if (descending) 1 else 0)) + direction).mod(total)
        setLibraryDefaultSortIndex(scope, next)
    }

    fun setSortInstalledFirst(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSortInstalledFirst(enabled)
            _state.update { it.copy(sortInstalledFirst = enabled) }
        }
    }

    fun setSortFavoritesFirst(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSortFavoritesFirst(enabled)
            _state.update { it.copy(sortFavoritesFirst = enabled) }
        }
    }

    fun setLibraryDefaultSource(scope: CoroutineScope, source: String) {
        scope.launch {
            preferencesRepository.setLibraryDefaultSource(source)
            _state.update { it.copy(libraryDefaultSource = source) }
        }
    }

    fun setLibraryDefaultPlatform(scope: CoroutineScope, platformId: Long?) {
        scope.launch {
            preferencesRepository.setLibraryDefaultPlatformId(platformId)
            _state.update { it.copy(libraryDefaultPlatformId = platformId) }
        }
    }

    fun cycleLibraryDefaultPlatform(scope: CoroutineScope, direction: Int, tokens: List<Long?>) {
        if (tokens.isEmpty()) return
        val current = tokens.indexOf(_state.value.libraryDefaultPlatformId).coerceAtLeast(0)
        setLibraryDefaultPlatform(scope, tokens[(current + direction).mod(tokens.size)])
    }

    fun toggleLibraryDefaultRegion(scope: CoroutineScope, region: String) {
        val current = _state.value.libraryDefaultRegions
        val updated = if (region in current) current - region else current + region
        _state.update { it.copy(libraryDefaultRegions = updated) }
        scope.launch { preferencesRepository.setLibraryDefaultRegions(updated) }
    }

    fun setLibraryDefaultPlayers(
        scope: CoroutineScope,
        bucket: com.nendo.argosy.domain.model.PlayerCountBucket?
    ) {
        scope.launch {
            preferencesRepository.setLibraryDefaultPlayers(bucket)
            _state.update { it.copy(libraryDefaultPlayers = bucket) }
        }
    }

    fun cycleLibraryDefaultPlayers(scope: CoroutineScope, direction: Int) {
        val tokens = listOf(null) + com.nendo.argosy.domain.model.PlayerCountBucket.entries
        val current = tokens.indexOf(_state.value.libraryDefaultPlayers).coerceAtLeast(0)
        setLibraryDefaultPlayers(scope, tokens[(current + direction).mod(tokens.size)])
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

    fun setShowStatusClock(scope: CoroutineScope, show: Boolean) {
        scope.launch {
            preferencesRepository.setShowStatusClock(show)
            _state.update { it.copy(showStatusClock = show) }
        }
    }

    fun setShowStatusBattery(scope: CoroutineScope, show: Boolean) {
        scope.launch {
            preferencesRepository.setShowStatusBattery(show)
            _state.update { it.copy(showStatusBattery = show) }
        }
    }

    fun setShowStatusNetwork(scope: CoroutineScope, show: Boolean) {
        scope.launch {
            preferencesRepository.setShowStatusNetwork(show)
            _state.update { it.copy(showStatusNetwork = show) }
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
