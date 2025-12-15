package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
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

class EmulatorSettingsDelegate @Inject constructor(
    private val emulatorDetector: EmulatorDetector,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(EmulatorState())
    val state: StateFlow<EmulatorState> = _state.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    fun updateState(newState: EmulatorState) {
        _state.value = newState
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig) {
        _state.update {
            it.copy(
                showEmulatorPicker = true,
                emulatorPickerInfo = EmulatorPickerInfo(
                    platformId = config.platform.id,
                    platformName = config.platform.name,
                    installedEmulators = config.availableEmulators,
                    downloadableEmulators = config.downloadableEmulators,
                    selectedEmulatorName = config.selectedEmulator
                ),
                emulatorPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissEmulatorPicker() {
        _state.update {
            it.copy(
                showEmulatorPicker = false,
                emulatorPickerInfo = null,
                emulatorPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _state.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            val hasInstalled = info.installedEmulators.isNotEmpty()
            val totalItems = (if (hasInstalled) 1 else 0) + info.installedEmulators.size + info.downloadableEmulators.size
            val maxIndex = (totalItems - 1).coerceAtLeast(0)
            val newIndex = (state.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulatorPickerFocusIndex = newIndex)
        }
    }

    fun confirmEmulatorPickerSelection(
        scope: CoroutineScope,
        onSetEmulator: suspend (String, InstalledEmulator?) -> Unit,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val state = _state.value
            val info = state.emulatorPickerInfo ?: return@launch
            val index = state.emulatorPickerFocusIndex
            val hasInstalled = info.installedEmulators.isNotEmpty()

            if (hasInstalled) {
                when {
                    index == 0 -> {
                        onSetEmulator(info.platformId, null)
                        dismissEmulatorPicker()
                        onLoadSettings()
                    }
                    index <= info.installedEmulators.size -> {
                        val emulator = info.installedEmulators[index - 1]
                        onSetEmulator(info.platformId, emulator)
                        dismissEmulatorPicker()
                        onLoadSettings()
                    }
                    else -> {
                        val downloadIndex = index - 1 - info.installedEmulators.size
                        val emulator = info.downloadableEmulators.getOrNull(downloadIndex)
                        emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
                    }
                }
            } else {
                val emulator = info.downloadableEmulators.getOrNull(index)
                emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
            }
        }
    }

    fun cycleCoreForPlatform(
        scope: CoroutineScope,
        config: PlatformEmulatorConfig,
        direction: Int,
        onLoadSettings: suspend () -> Unit
    ) {
        if (config.availableCores.isEmpty()) return
        scope.launch {
            val currentIndex = config.selectedCore?.let { selectedId ->
                config.availableCores.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
            } ?: -1
            val nextIndex = (currentIndex + direction + config.availableCores.size) % config.availableCores.size
            val nextCore = config.availableCores[nextIndex]
            configureEmulatorUseCase.setCoreForPlatform(config.platform.id, nextCore.id)
            onLoadSettings()
        }
    }

    fun setPlatformEmulator(
        scope: CoroutineScope,
        platformId: String,
        emulator: InstalledEmulator?,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            configureEmulatorUseCase.setForPlatform(platformId, emulator)
            onLoadSettings()
        }
    }

    fun autoAssignAllEmulators(
        scope: CoroutineScope,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val platforms = _state.value.platforms
            for (config in platforms) {
                if (!config.isUserConfigured && config.availableEmulators.isNotEmpty()) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.id)
                    if (preferred != null) {
                        configureEmulatorUseCase.setForPlatform(config.platform.id, preferred)
                    }
                }
            }
            onLoadSettings()
        }
    }

    fun refreshEmulators() {
        // EmulatorDetector will refresh on next detectEmulators() call
        // No explicit refresh needed - the ViewModel calls loadSettings() after this
    }
}
