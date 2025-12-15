package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.HapticIntensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedIconLayout
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.screens.settings.ControlsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ControlsSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val hapticManager: HapticFeedbackManager
) {
    private val _state = MutableStateFlow(ControlsState())
    val state: StateFlow<ControlsState> = _state.asStateFlow()

    fun updateState(newState: ControlsState) {
        _state.value = newState
    }

    fun setHapticEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setHapticEnabled(enabled)
            _state.update { it.copy(hapticEnabled = enabled) }
        }
    }

    fun setHapticIntensity(scope: CoroutineScope, intensity: HapticIntensity) {
        scope.launch {
            preferencesRepository.setHapticIntensity(intensity)
            _state.update { it.copy(hapticIntensity = intensity) }
        }
    }

    fun cycleHapticIntensity(scope: CoroutineScope) {
        adjustHapticIntensity(scope, 1)
    }

    fun adjustHapticIntensity(scope: CoroutineScope, delta: Int) {
        val current = _state.value.hapticIntensity
        val currentIndex = current.ordinal
        val newIndex = (currentIndex + delta).coerceIn(0, HapticIntensity.entries.lastIndex)
        if (newIndex != currentIndex) {
            val newIntensity = HapticIntensity.entries[newIndex]
            hapticManager.setIntensity(newIntensity.amplitude)
            setHapticIntensity(scope, newIntensity)
            scope.launch {
                delay(100)
                hapticManager.vibrate(HapticPattern.INTENSITY_PREVIEW)
            }
        }
    }

    fun setSwapAB(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapAB(enabled)
            _state.update { it.copy(swapAB = enabled) }
        }
    }

    fun setSwapXY(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapXY(enabled)
            _state.update { it.copy(swapXY = enabled) }
        }
    }

    fun setABIconLayout(scope: CoroutineScope, layout: String) {
        scope.launch {
            preferencesRepository.setABIconLayout(layout)
            _state.update { it.copy(abIconLayout = layout) }
        }
    }

    fun cycleABIconLayout(scope: CoroutineScope) {
        val current = _state.value.abIconLayout
        val next = when (current) {
            "auto" -> "xbox"
            "xbox" -> "nintendo"
            else -> "auto"
        }
        setABIconLayout(scope, next)
    }

    fun detectControllerLayout(): String? {
        return when (ControllerDetector.detectFromActiveGamepad()) {
            DetectedIconLayout.XBOX -> "xbox"
            DetectedIconLayout.NINTENDO -> "nintendo"
            null -> null
        }
    }

    fun setSwapStartSelect(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapStartSelect(enabled)
            _state.update { it.copy(swapStartSelect = enabled) }
        }
    }
}
