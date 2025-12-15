package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        viewModel.moveFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        viewModel.moveFocus(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value

        if (state.currentSection == SettingsSection.DISPLAY) {
            val sliderOffset = if (state.display.useGameBackground) 0 else 1
            when (state.focusedIndex) {
                1 -> { viewModel.adjustHue(-10f); return InputResult.HANDLED }
                5 + sliderOffset -> { viewModel.adjustBackgroundBlur(-10); return InputResult.HANDLED }
                6 + sliderOffset -> { viewModel.adjustBackgroundSaturation(-10); return InputResult.HANDLED }
                7 + sliderOffset -> { viewModel.adjustBackgroundOpacity(-10); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.STORAGE && state.focusedIndex == 1) {
            viewModel.cycleMaxConcurrentDownloads()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.focusedIndex == 1) {
            viewModel.adjustHapticIntensity(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SOUNDS && state.sounds.enabled && state.focusedIndex == 1) {
            viewModel.adjustSoundVolume(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SERVER) {
            if (state.server.rommConfiguring && state.focusedIndex == 2) {
                viewModel.setRommConfigFocusIndex(1)
                return InputResult.HANDLED
            }
            if (!state.server.rommConfiguring) {
                val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                    state.server.connectionStatus == ConnectionStatus.OFFLINE
                val steamBaseIndex = when {
                    isConnected && state.syncSettings.saveSyncEnabled -> 5
                    isConnected -> 3
                    else -> 1
                }
                val launcherIndex = state.focusedIndex - steamBaseIndex
                if (launcherIndex >= 0 && launcherIndex < state.steam.installedLaunchers.size) {
                    viewModel.moveLauncherActionFocus(-1)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.STEAM_SETTINGS) {
            val launcherIndex = state.focusedIndex - 1
            if (launcherIndex >= 0 && launcherIndex < state.steam.installedLaunchers.size) {
                viewModel.moveLauncherActionFocus(-1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config?.showCoreSelection == true) {
                viewModel.cycleCoreForPlatform(config, -1)
                return InputResult.HANDLED
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value

        if (state.currentSection == SettingsSection.DISPLAY) {
            val sliderOffset = if (state.display.useGameBackground) 0 else 1
            when (state.focusedIndex) {
                1 -> { viewModel.adjustHue(10f); return InputResult.HANDLED }
                5 + sliderOffset -> { viewModel.adjustBackgroundBlur(10); return InputResult.HANDLED }
                6 + sliderOffset -> { viewModel.adjustBackgroundSaturation(10); return InputResult.HANDLED }
                7 + sliderOffset -> { viewModel.adjustBackgroundOpacity(10); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.STORAGE && state.focusedIndex == 1) {
            viewModel.cycleMaxConcurrentDownloads()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.focusedIndex == 1) {
            viewModel.adjustHapticIntensity(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SOUNDS && state.sounds.enabled && state.focusedIndex == 1) {
            viewModel.adjustSoundVolume(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SERVER) {
            if (state.server.rommConfiguring && state.focusedIndex == 1) {
                viewModel.setRommConfigFocusIndex(2)
                return InputResult.HANDLED
            }
            if (!state.server.rommConfiguring) {
                val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                    state.server.connectionStatus == ConnectionStatus.OFFLINE
                val steamBaseIndex = when {
                    isConnected && state.syncSettings.saveSyncEnabled -> 5
                    isConnected -> 3
                    else -> 1
                }
                val launcherIndex = state.focusedIndex - steamBaseIndex
                if (launcherIndex >= 0 && launcherIndex < state.steam.installedLaunchers.size) {
                    viewModel.moveLauncherActionFocus(1)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.STEAM_SETTINGS) {
            val launcherIndex = state.focusedIndex - 1
            if (launcherIndex >= 0 && launcherIndex < state.steam.installedLaunchers.size) {
                viewModel.moveLauncherActionFocus(1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config?.showCoreSelection == true) {
                viewModel.cycleCoreForPlatform(config, 1)
                return InputResult.HANDLED
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value

        if (state.sounds.showSoundPicker) {
            viewModel.confirmSoundPickerSelection()
            return InputResult.HANDLED
        }

        if (state.syncSettings.showRegionPicker) {
            viewModel.confirmRegionPickerSelection()
            return InputResult.handled(SoundType.TOGGLE)
        }

        if (state.emulators.showEmulatorPicker) {
            viewModel.confirmEmulatorPickerSelection()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.DISPLAY && state.focusedIndex == 1) {
            viewModel.resetToDefaultColor()
            return InputResult.HANDLED
        }

        return viewModel.handleConfirm()
    }

    override fun onBack(): InputResult {
        return if (!viewModel.navigateBack()) {
            onBackNavigation()
            InputResult.HANDLED
        } else {
            InputResult.HANDLED
        }
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.sounds.showSoundPicker) {
            viewModel.previewSoundPickerSelection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult = InputResult.UNHANDLED
}
