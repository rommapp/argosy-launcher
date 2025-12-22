package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundPreset
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.SoundState
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

class SoundSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(SoundState())
    val state: StateFlow<SoundState> = _state.asStateFlow()

    private val _openCustomSoundPickerEvent = MutableSharedFlow<SoundType>()
    val openCustomSoundPickerEvent: SharedFlow<SoundType> = _openCustomSoundPickerEvent.asSharedFlow()

    fun updateState(newState: SoundState) {
        _state.value = newState
    }

    fun setSoundEnabled(scope: CoroutineScope, enabled: Boolean) {
        scope.launch {
            preferencesRepository.setSoundEnabled(enabled)
            soundManager.setEnabled(enabled)
            _state.update { it.copy(enabled = enabled) }
        }
    }

    fun setSoundVolume(scope: CoroutineScope, volume: Int) {
        scope.launch {
            preferencesRepository.setSoundVolume(volume)
            soundManager.setVolume(volume)
            _state.update { it.copy(volume = volume) }
            soundManager.play(SoundType.VOLUME_PREVIEW)
        }
    }

    fun adjustSoundVolume(scope: CoroutineScope, delta: Int) {
        val volumeLevels = listOf(50, 70, 85, 95, 100)
        val current = _state.value.volume
        val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
        val newIndex = (currentIndex + delta).coerceIn(0, volumeLevels.lastIndex)
        val newVolume = volumeLevels[newIndex]
        if (newVolume != current) {
            setSoundVolume(scope, newVolume)
        }
    }

    fun showSoundPicker(type: SoundType) {
        val currentConfig = _state.value.soundConfigs[type]
        val initialIndex = if (currentConfig?.presetName != null) {
            SoundPreset.entries.indexOfFirst { it.name == currentConfig.presetName }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        _state.update {
            it.copy(
                showSoundPicker = true,
                soundPickerType = type,
                soundPickerFocusIndex = initialIndex
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissSoundPicker() {
        _state.update {
            it.copy(
                showSoundPicker = false,
                soundPickerType = null,
                soundPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveSoundPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = SoundPreset.entries.size - 1
            val newIndex = (state.soundPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(soundPickerFocusIndex = newIndex)
        }
    }

    fun previewSoundPickerSelection() {
        val focusIndex = _state.value.soundPickerFocusIndex
        val preset = SoundPreset.entries.getOrNull(focusIndex) ?: return
        if (preset != SoundPreset.SILENT && preset != SoundPreset.CUSTOM) {
            soundManager.playPreset(preset)
        }
    }

    fun confirmSoundPickerSelection(scope: CoroutineScope) {
        val state = _state.value
        val type = state.soundPickerType ?: return
        val focusIndex = state.soundPickerFocusIndex
        val preset = SoundPreset.entries.getOrNull(focusIndex) ?: return

        if (preset == SoundPreset.CUSTOM) {
            scope.launch {
                _openCustomSoundPickerEvent.emit(type)
            }
            dismissSoundPicker()
            return
        }

        val config = if (preset == SoundPreset.SILENT) {
            SoundConfig(presetName = SoundPreset.SILENT.name)
        } else {
            SoundConfig(presetName = preset.name)
        }

        scope.launch {
            preferencesRepository.setSoundConfig(type, config)
            val updatedConfigs = _state.value.soundConfigs + (type to config)
            _state.update { it.copy(soundConfigs = updatedConfigs) }
            soundManager.setSoundConfig(type, config)
        }
        dismissSoundPicker()
    }

    fun setCustomSoundFile(scope: CoroutineScope, type: SoundType, filePath: String) {
        val config = SoundConfig(customFilePath = filePath)
        scope.launch {
            preferencesRepository.setSoundConfig(type, config)
            val updatedConfigs = _state.value.soundConfigs + (type to config)
            _state.update { it.copy(soundConfigs = updatedConfigs) }
            soundManager.setSoundConfig(type, config)
        }
    }
}
