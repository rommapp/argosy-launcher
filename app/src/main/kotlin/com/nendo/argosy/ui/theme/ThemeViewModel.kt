package com.nendo.argosy.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.SHADOW,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.MEDIUM,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val useAccentColorFooter: Boolean = false
)

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val themeState: StateFlow<ThemeState> = preferencesRepository.userPreferences
        .map { prefs ->
            ThemeState(
                themeMode = prefs.themeMode,
                primaryColor = prefs.primaryColor,
                secondaryColor = prefs.secondaryColor,
                tertiaryColor = prefs.tertiaryColor,
                boxArtShape = prefs.boxArtShape,
                boxArtCornerRadius = prefs.boxArtCornerRadius,
                boxArtBorderThickness = prefs.boxArtBorderThickness,
                boxArtBorderStyle = prefs.boxArtBorderStyle,
                glassBorderTintAlpha = prefs.glassBorderTint.alpha,
                boxArtGlowStrength = prefs.boxArtGlowStrength,
                boxArtOuterEffect = prefs.boxArtOuterEffect,
                boxArtOuterEffectThickness = prefs.boxArtOuterEffectThickness,
                boxArtInnerEffect = prefs.boxArtInnerEffect,
                boxArtInnerEffectThickness = prefs.boxArtInnerEffectThickness,
                gradientPreset = prefs.gradientPreset,
                systemIconPosition = prefs.systemIconPosition,
                systemIconPadding = prefs.systemIconPadding,
                useAccentColorFooter = prefs.useAccentColorFooter
            )
        }
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
}
