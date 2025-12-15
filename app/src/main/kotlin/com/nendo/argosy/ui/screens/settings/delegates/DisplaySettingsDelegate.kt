package com.nendo.argosy.ui.screens.settings.delegates

import androidx.core.graphics.ColorUtils
import com.nendo.argosy.data.preferences.AnimationSpeed
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UiDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
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
    private val preferencesRepository: UserPreferencesRepository
) {
    private val _state = MutableStateFlow(DisplayState())
    val state: StateFlow<DisplayState> = _state.asStateFlow()

    private val _openBackgroundPickerEvent = MutableSharedFlow<Unit>()
    val openBackgroundPickerEvent: SharedFlow<Unit> = _openBackgroundPickerEvent.asSharedFlow()

    private val colorCount = 7
    private var _colorFocusIndex = 0
    val colorFocusIndex: Int get() = _colorFocusIndex

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

    fun setAnimationSpeed(scope: CoroutineScope, speed: AnimationSpeed) {
        scope.launch {
            preferencesRepository.setAnimationSpeed(speed)
            _state.update { it.copy(animationSpeed = speed) }
        }
    }

    fun cycleAnimationSpeed(scope: CoroutineScope) {
        val next = when (_state.value.animationSpeed) {
            AnimationSpeed.SLOW -> AnimationSpeed.NORMAL
            AnimationSpeed.NORMAL -> AnimationSpeed.FAST
            AnimationSpeed.FAST -> AnimationSpeed.OFF
            AnimationSpeed.OFF -> AnimationSpeed.SLOW
        }
        setAnimationSpeed(scope, next)
    }

    fun setUiDensity(scope: CoroutineScope, density: UiDensity) {
        scope.launch {
            preferencesRepository.setUiDensity(density)
            _state.update { it.copy(uiDensity = density) }
        }
    }

    fun cycleUiDensity(scope: CoroutineScope) {
        val next = when (_state.value.uiDensity) {
            UiDensity.COMPACT -> UiDensity.NORMAL
            UiDensity.NORMAL -> UiDensity.SPACIOUS
            UiDensity.SPACIOUS -> UiDensity.COMPACT
        }
        setUiDensity(scope, next)
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
}
