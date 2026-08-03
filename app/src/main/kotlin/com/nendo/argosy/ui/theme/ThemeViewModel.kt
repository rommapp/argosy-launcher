package com.nendo.argosy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.ui.theme.backdrop.BackdropConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val surfaceTintBleed: Int = 0,
    val surfaceBackdrop: BackdropConfig = BackdropConfig(),
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID,
    val glassBorderTintAlpha: Float = 0f,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.MEDIUM,
    val glowColorMode: GlowColorMode = GlowColorMode.AUTO,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.SHADOW,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.MEDIUM,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val platformIndicatorStyle: PlatformIndicatorStyle = PlatformIndicatorStyle.TAB,
    val platformIndicatorContent: PlatformIndicatorContent = PlatformIndicatorContent.NAME,
    val useAccentColorFooter: Boolean = false,
    val uiScale: Int = 100,
    val gripReserveEnabled: Boolean = false,
    val gripReservePercent: Int = 20,
    val displayFontScale: Int = 100,
    val bodyFontScale: Int = 100
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientLedManager: AmbientLedManager
) : ViewModel() {

    init {
        observeThemeForLed()
    }

    private fun observeThemeForLed() {
        preferencesRepository.userPreferences
            .onEach { prefs ->
                val defaultPrimary = if (BuildConfig.DEBUG) ALauncherColors.Orange else ALauncherColors.Indigo
                val primary = prefs.primaryColor?.let { Color(it) } ?: defaultPrimary
                val secondary = prefs.secondaryColor?.let { Color(it) }
                ambientLedManager.setUiColors(primary, secondary)
            }
            .launchIn(viewModelScope)
    }

    val themeState: StateFlow<ThemeState> = preferencesRepository.userPreferences
        .map { it.toThemeState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemeState()
        )

    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    val customFonts: StateFlow<CustomFontFamilies> = preferencesRepository.userPreferences
        .map { it.displayFontPath to it.bodyFontPath }
        .distinctUntilChanged()
        .map { (displayPath, bodyPath) ->
            withContext(Dispatchers.IO) {
                CustomFontFamilies(
                    display = resolveFontFamily(FontSlot.DISPLAY, displayPath),
                    body = resolveFontFamily(FontSlot.BODY, bodyPath)
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CustomFontFamilies()
        )

    private suspend fun resolveFontFamily(slot: FontSlot, path: String?): FontFamily? {
        if (path == null) return null
        fontFamilyCache[path]?.let { return it }
        val family = CustomFontLoader.loadFamily(path)
        if (family == null) {
            preferencesRepository.setCustomFont(slot, null, null)
        } else {
            fontFamilyCache[path] = family
        }
        return family
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setCustomColors(primary: Int?, secondary: Int?, tertiary: Int?) {
        viewModelScope.launch {
            preferencesRepository.setCustomColors(primary, secondary, tertiary)
        }
    }

    fun setUiScale(scale: Int) {
        viewModelScope.launch {
            preferencesRepository.setUiScale(scale)
        }
    }
}

/**
 * Pure mapping from persisted [UserPreferences] to [ThemeState]. Lives here so
 * the secondary-display Activity (not a Hilt entry point, can't use
 * hiltViewModel()) can build its own ThemeState flow from
 * preferencesRepository.userPreferences without instantiating ThemeViewModel
 * a second time -- doing that would spin up a duplicate ambient-LED observer.
 */
fun UserPreferences.toThemeState(): ThemeState = ThemeState(
    themeMode = themeMode,
    primaryColor = primaryColor,
    secondaryColor = secondaryColor,
    tertiaryColor = tertiaryColor,
    surfaceTintBleed = surfaceTintBleed,
    surfaceBackdrop = BackdropConfig(
        enabled = backdropEnabled,
        preset = backdropPreset,
        cellSize = backdropCellSize,
        scatter = backdropScatter,
        scaleJitter = backdropScaleJitter,
        strength = backdropStrength,
        edgeStyle = backdropEdgeStyle,
        vertexIcons = backdropVertexIcons,
        seed = backdropSeed,
        motion = backdropMotion,
        motionSpeed = backdropMotionSpeed,
        driftAngle = backdropDriftAngle
    ),
    boxArtShape = boxArtShape,
    boxArtCornerRadius = boxArtCornerRadius,
    boxArtBorderThickness = boxArtBorderThickness,
    boxArtBorderStyle = boxArtBorderStyle,
    glassBorderTintAlpha = glassBorderTint.alpha,
    boxArtGlowStrength = boxArtGlowStrength,
    boxArtOuterEffect = boxArtOuterEffect,
    boxArtOuterEffectThickness = boxArtOuterEffectThickness,
    glowColorMode = glowColorMode,
    boxArtInnerEffect = boxArtInnerEffect,
    boxArtInnerEffectThickness = boxArtInnerEffectThickness,
    gradientPreset = gradientPreset,
    systemIconPosition = systemIconPosition,
    systemIconPadding = systemIconPadding,
    platformIndicatorStyle = platformIndicatorStyle,
    platformIndicatorContent = platformIndicatorContent,
    useAccentColorFooter = useAccentColorFooter,
    uiScale = uiScale,
    gripReserveEnabled = gripReserveEnabled,
    gripReservePercent = gripReservePercent,
    displayFontScale = displayFontScale,
    bodyFontScale = bodyFontScale
)
