package com.nendo.argosy.ui.screens.settings.delegates

import android.app.Application
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.screens.settings.ControlsState
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
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

    fun getVibrationStrength(): Float = hapticManager.getSystemVibrationStrength()

    fun setVibrationStrength(strength: Float) {
        hapticManager.setSystemVibrationStrength(strength)
        _state.update { it.copy(vibrationStrength = strength) }
        hapticManager.vibrate(HapticPattern.STRENGTH_PREVIEW)
    }

    fun adjustVibrationStrength(delta: Float) {
        val current = _state.value.vibrationStrength
        val newStrength = (current + delta).coerceIn(0f, 1f)
        if (newStrength != current) {
            setVibrationStrength(newStrength)
        }
    }

    val supportsSystemVibration: Boolean
        get() = hapticManager.supportsSystemVibration

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

    fun cycleControllerLayout(scope: CoroutineScope, direction: Int = 1) {
        val index = LAYOUT_CYCLE.indexOf(_state.value.controllerLayout).coerceAtLeast(0)
        setControllerLayout(scope, LAYOUT_CYCLE[(index + direction).mod(LAYOUT_CYCLE.size)])
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

    fun setSelectLCombo(scope: CoroutineScope, value: String) {
        scope.launch {
            preferencesRepository.setSelectLCombo(value)
            _state.update { it.copy(selectLCombo = value) }
        }
    }

    fun setSelectRCombo(scope: CoroutineScope, value: String) {
        scope.launch {
            preferencesRepository.setSelectRCombo(value)
            _state.update { it.copy(selectRCombo = value) }
        }
    }

    fun cycleSelectLCombo(scope: CoroutineScope, direction: Int = 1) =
        setSelectLCombo(scope, cycleComboValue(_state.value.selectLCombo, direction))

    fun cycleSelectRCombo(scope: CoroutineScope, direction: Int = 1) =
        setSelectRCombo(scope, cycleComboValue(_state.value.selectRCombo, direction))

    companion object {
        val LAYOUT_CYCLE = listOf("auto", "xbox", "nintendo")
        val COMBO_CYCLE = listOf("quick_menu", "quick_settings", "none")

        fun cycleComboValue(current: String, direction: Int = 1): String {
            val index = COMBO_CYCLE.indexOf(current).coerceAtLeast(0)
            return COMBO_CYCLE[(index + direction).mod(COMBO_CYCLE.size)]
        }

        fun comboDisplayName(value: String): String = when (value) {
            "quick_menu" -> "Quick Menu"
            "quick_settings" -> "Quick Settings"
            else -> "None"
        }

        fun layoutDisplayName(value: String): String = when (value) {
            "nintendo" -> "Nintendo"
            "xbox" -> "Xbox"
            else -> "Auto"
        }
    }

    fun setMenuWrapMode(scope: CoroutineScope, mode: MenuWrapMode) {
        scope.launch {
            preferencesRepository.setMenuWrapMode(mode)
            _state.update { it.copy(menuWrapMode = mode) }
        }
    }

    fun cycleMenuWrapMode(scope: CoroutineScope, direction: Int = 1) {
        val current = _state.value.menuWrapMode
        setMenuWrapMode(scope, MenuWrapMode.entries[(current.ordinal + direction).mod(MenuWrapMode.entries.size)])
    }

    fun refreshUsageStatsPermission() {
        val hasPermission = permissionHelper.hasUsageStatsPermission(application)
        _state.update { it.copy(hasUsageStatsPermission = hasPermission) }
    }

    fun openUsageStatsSettings() {
        permissionHelper.openUsageStatsSettings(application)
    }
}
