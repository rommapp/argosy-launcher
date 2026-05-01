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
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
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
    val useAccentColorFooter: Boolean = false,
    val uiScale: Int = 100
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
    useAccentColorFooter = useAccentColorFooter,
    uiScale = uiScale
)
