package com.nendo.argosy.ui.screens.settings.delegates

import android.app.Application
import com.nendo.argosy.data.preferences.HapticIntensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class ControlsSettingsDelegate @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val hapticManager: HapticFeedbackManager,
    private val permissionHelper: PermissionHelper
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

    fun setControllerLayout(scope: CoroutineScope, layout: String) {
        scope.launch {
            preferencesRepository.setControllerLayout(layout)
            _state.update { it.copy(controllerLayout = layout) }
        }
    }

    fun cycleControllerLayout(scope: CoroutineScope) {
        val current = _state.value.controllerLayout
        val next = when (current) {
            "auto" -> "xbox"
            "xbox" -> "nintendo"
            else -> "auto"
        }
        setControllerLayout(scope, next)
    }

    fun refreshDetectedLayout() {
        val result = ControllerDetector.detectFromActiveGamepad()
        val layoutName = when (result.layout) {
            DetectedLayout.XBOX -> "Xbox"
            DetectedLayout.NINTENDO -> "Nintendo"
            null -> null
        }
        _state.update {
            it.copy(
                detectedLayout = layoutName,
                detectedDeviceName = result.deviceName
            )
        }
    }

    fun detectControllerLayout(): String? {
        val result = ControllerDetector.detectFromActiveGamepad()
        return when (result.layout) {
            DetectedLayout.XBOX -> "xbox"
            DetectedLayout.NINTENDO -> "nintendo"
            null -> null
        }
    }

    fun setSwapStartSelect(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSwapStartSelect(enabled)
            _state.update { it.copy(swapStartSelect = enabled) }
        }
    }

    fun refreshUsageStatsPermission() {
        val hasPermission = permissionHelper.hasUsageStatsPermission(application)
        _state.update { it.copy(hasUsageStatsPermission = hasPermission) }
    }

    fun setAccuratePlayTimeEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setAccuratePlayTimeEnabled(enabled)
            _state.update { it.copy(accuratePlayTimeEnabled = enabled) }
        }
    }

    fun openUsageStatsSettings() {
        permissionHelper.openUsageStatsSettings(application)
    }
}
