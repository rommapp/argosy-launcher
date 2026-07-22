package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundPreset
import com.nendo.argosy.core.input.SoundType
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

    private val _openMusicBrowserSfxEvent = MutableSharedFlow<SoundType>()
    val openMusicBrowserSfxEvent: SharedFlow<SoundType> = _openMusicBrowserSfxEvent.asSharedFlow()

    fun updateState(newState: SoundState) {
        _state.value = newState
    }

    fun setMusicApiSupported(supported: Boolean) {
        _state.update { it.copy(musicApiSupported = supported) }
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
        adjustInList(_state.value.volume, VolumeLevels.UI_SOUNDS, delta)?.let { setSoundVolume(scope, it) }
    }

    fun showSoundPicker(type: SoundType) {
        val currentState = _state.value
        val currentConfig = currentState.soundConfigs[type]
        val presets = currentState.presets
        val initialIndex = when {
            currentConfig?.isRommSource == true ->
                presets.indexOf(SoundPreset.ROMM_MUSIC).coerceAtLeast(0)
            currentConfig?.customFilePath != null ->
                presets.indexOf(SoundPreset.CUSTOM).coerceAtLeast(0)
            currentConfig?.presetName == SoundPreset.SILENT.name ->
                presets.indexOf(SoundPreset.SILENT).coerceAtLeast(0)
            else -> presets.indexOf(SoundPreset.DEFAULT).coerceAtLeast(0)
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
            val maxIndex = state.presets.size - 1
            val newIndex = (state.soundPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(soundPickerFocusIndex = newIndex)
        }
    }

    fun resetSoundToDefault(scope: CoroutineScope, type: SoundType) {
        scope.launch {
            preferencesRepository.setSoundConfig(type, null)
            _state.update { it.copy(soundConfigs = it.soundConfigs - type) }
            soundManager.clearSoundConfig(type)
        }
    }

    fun confirmSoundPickerSelectionAt(scope: CoroutineScope, index: Int) {
        _state.update { it.copy(soundPickerFocusIndex = index) }
        confirmSoundPickerSelection(scope)
    }

    fun confirmSoundPickerSelection(scope: CoroutineScope) {
        val state = _state.value
        val type = state.soundPickerType ?: return
        val focusIndex = state.soundPickerFocusIndex
        val preset = state.presets.getOrNull(focusIndex) ?: return

        if (preset == SoundPreset.CUSTOM) {
            scope.launch {
                _openCustomSoundPickerEvent.emit(type)
            }
            dismissSoundPicker()
            return
        }

        if (preset == SoundPreset.ROMM_MUSIC) {
            scope.launch {
                _openMusicBrowserSfxEvent.emit(type)
            }
            dismissSoundPicker()
            return
        }

        if (preset == SoundPreset.DEFAULT) {
            scope.launch {
                preferencesRepository.setSoundConfig(type, null)
                _state.update { it.copy(soundConfigs = it.soundConfigs - type) }
                soundManager.clearSoundConfig(type)
            }
            dismissSoundPicker()
            return
        }

        val config = SoundConfig(presetName = SoundPreset.SILENT.name)

        scope.launch {
            preferencesRepository.setSoundConfig(type, config)
            val updatedConfigs = _state.value.soundConfigs + (type to config)
            _state.update { it.copy(soundConfigs = updatedConfigs) }
            soundManager.setSoundConfig(type, config)
        }
        dismissSoundPicker()
    }

    fun setCustomSoundFile(scope: CoroutineScope, type: SoundType, filePath: String, fromRomm: Boolean = false) {
        val config = SoundConfig(
            presetName = if (fromRomm) SoundConfig.ROMM_SOURCE else null,
            customFilePath = filePath
        )
        scope.launch {
            preferencesRepository.setSoundConfig(type, config)
            val updatedConfigs = _state.value.soundConfigs + (type to config)
            _state.update { it.copy(soundConfigs = updatedConfigs) }
            soundManager.setSoundConfig(type, config)
        }
    }
}
