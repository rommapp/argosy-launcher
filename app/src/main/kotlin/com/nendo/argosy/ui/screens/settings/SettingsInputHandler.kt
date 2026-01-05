package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit
) : InputHandler {

    companion object {
        private const val SLIDER_STEP = 10
        private const val HUE_STEP = 10f
    }

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value

        if (state.emulators.showSavePathModal) {
            viewModel.moveSavePathModalFocus(-1)
            return InputResult.HANDLED
        }
        if (state.storage.platformSettingsModalId != null) {
            viewModel.movePlatformSettingsFocus(-1)
            return InputResult.HANDLED
        }
        if (state.sounds.showSoundPicker) {
            viewModel.moveSoundPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showRegionPicker) {
            viewModel.moveRegionPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showSyncFiltersModal) {
            viewModel.moveSyncFiltersModalFocus(-1)
            return InputResult.HANDLED
        }
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config != null && config.hasInstalledEmulators && config.showSavePath &&
                state.emulators.platformSubFocusIndex == 1
            ) {
                viewModel.movePlatformSubFocus(-1, 1)
                return InputResult.HANDLED
            }
        }
        viewModel.moveFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value

        if (state.emulators.showSavePathModal) {
            viewModel.moveSavePathModalFocus(1)
            return InputResult.HANDLED
        }
        if (state.storage.platformSettingsModalId != null) {
            viewModel.movePlatformSettingsFocus(1)
            return InputResult.HANDLED
        }
        if (state.sounds.showSoundPicker) {
            viewModel.moveSoundPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showRegionPicker) {
            viewModel.moveRegionPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showSyncFiltersModal) {
            viewModel.moveSyncFiltersModalFocus(1)
            return InputResult.HANDLED
        }
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config != null && config.hasInstalledEmulators && config.showSavePath &&
                state.emulators.platformSubFocusIndex == 0
            ) {
                viewModel.movePlatformSubFocus(1, 1)
                return InputResult.HANDLED
            }
        }
        viewModel.moveFocus(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value

        if (state.emulators.showSavePathModal) {
            viewModel.moveSavePathModalButtonFocus(1)
            return InputResult.HANDLED
        }
        if (state.storage.platformSettingsModalId != null) {
            val focusIdx = state.storage.platformSettingsFocusIndex
            if (focusIdx in 1..3) {
                viewModel.movePlatformSettingsButtonFocus(1)
            }
            return InputResult.HANDLED
        }
        if (state.sounds.showSoundPicker ||
            state.syncSettings.showRegionPicker ||
            state.syncSettings.showSyncFiltersModal ||
            state.emulators.showEmulatorPicker) {
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.DISPLAY) {
            when (state.focusedIndex) {
                1 -> { viewModel.adjustHue(-HUE_STEP); return InputResult.HANDLED }
                7 -> { viewModel.adjustScreenDimmerTimeout(-1); return InputResult.HANDLED }
                8 -> { viewModel.adjustScreenDimmerLevel(-1); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.HOME_SCREEN) {
            val sliderOffset = if (state.display.useGameBackground) 0 else 1
            when (state.focusedIndex) {
                1 + sliderOffset -> { viewModel.adjustBackgroundBlur(-SLIDER_STEP); return InputResult.HANDLED }
                2 + sliderOffset -> { viewModel.adjustBackgroundSaturation(-SLIDER_STEP); return InputResult.HANDLED }
                3 + sliderOffset -> { viewModel.adjustBackgroundOpacity(-SLIDER_STEP); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.BOX_ART) {
            val showGlassTint = state.display.boxArtBorderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GLASS
            val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
            val showOuterThickness = state.display.boxArtOuterEffect != com.nendo.argosy.data.preferences.BoxArtOuterEffect.OFF
            val showInnerThickness = state.display.boxArtInnerEffect != com.nendo.argosy.data.preferences.BoxArtInnerEffect.OFF
            var idx = 3
            val glassTintIdx = if (showGlassTint) idx++ else -1
            val iconPosIdx = idx++
            val iconPadIdx = if (showIconPadding) idx++ else -1
            val outerEffectIdx = idx++
            val outerThicknessIdx = if (showOuterThickness) idx++ else -1
            val innerEffectIdx = idx++
            val innerThicknessIdx = if (showInnerThickness) idx++ else -1
            when (state.focusedIndex) {
                0 -> viewModel.cycleBoxArtCornerRadius(-1)
                1 -> viewModel.cycleBoxArtBorderThickness(-1)
                2 -> viewModel.cycleBoxArtBorderStyle(-1)
                glassTintIdx -> viewModel.cycleGlassBorderTint(-1)
                iconPosIdx -> viewModel.cycleSystemIconPosition(-1)
                iconPadIdx -> viewModel.cycleSystemIconPadding(-1)
                outerEffectIdx -> viewModel.cycleBoxArtOuterEffect(-1)
                outerThicknessIdx -> viewModel.cycleBoxArtOuterEffectThickness(-1)
                innerEffectIdx -> viewModel.cycleBoxArtInnerEffect(-1)
                innerThicknessIdx -> viewModel.cycleBoxArtInnerEffectThickness(-1)
            }
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.STORAGE) {
            if (state.focusedIndex == 0) {
                viewModel.adjustMaxConcurrentDownloads(-1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.focusedIndex == 1) {
            viewModel.adjustHapticIntensity(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SOUNDS) {
            val bgmVolumeIndex = 1
            val uiSoundsToggleIndex = if (state.ambientAudio.enabled) 3 else 1
            val uiSoundsVolumeIndex = uiSoundsToggleIndex + 1
            when (state.focusedIndex) {
                bgmVolumeIndex -> if (state.ambientAudio.enabled) {
                    viewModel.adjustAmbientAudioVolume(-1)
                    return InputResult.HANDLED
                }
                uiSoundsVolumeIndex -> if (state.sounds.enabled) {
                    viewModel.adjustSoundVolume(-1)
                    return InputResult.HANDLED
                }
            }
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
                    isConnected && state.syncSettings.saveSyncEnabled -> 6
                    isConnected -> 4
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
            if (config?.showExtensionSelection == true) {
                viewModel.cycleExtensionForPlatform(config, -1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.SYNC_SETTINGS && state.focusedIndex == 2) {
            viewModel.moveImageCacheActionFocus(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BIOS) {
            when (state.focusedIndex) {
                0 -> {
                    viewModel.moveBiosActionFocus(-1)
                    return InputResult.HANDLED
                }
                else -> {
                    if (state.focusedIndex >= 2 && viewModel.moveBiosPlatformSubFocus(-1)) {
                        return InputResult.HANDLED
                    }
                }
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value

        if (state.emulators.showSavePathModal) {
            viewModel.moveSavePathModalButtonFocus(-1)
            return InputResult.HANDLED
        }
        if (state.storage.platformSettingsModalId != null) {
            val focusIdx = state.storage.platformSettingsFocusIndex
            if (focusIdx in 1..3) {
                viewModel.movePlatformSettingsButtonFocus(-1)
            }
            return InputResult.HANDLED
        }
        if (state.sounds.showSoundPicker ||
            state.syncSettings.showRegionPicker ||
            state.syncSettings.showSyncFiltersModal ||
            state.emulators.showEmulatorPicker) {
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.DISPLAY) {
            when (state.focusedIndex) {
                1 -> { viewModel.adjustHue(HUE_STEP); return InputResult.HANDLED }
                7 -> { viewModel.adjustScreenDimmerTimeout(1); return InputResult.HANDLED }
                8 -> { viewModel.adjustScreenDimmerLevel(1); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.HOME_SCREEN) {
            val sliderOffset = if (state.display.useGameBackground) 0 else 1
            when (state.focusedIndex) {
                1 + sliderOffset -> { viewModel.adjustBackgroundBlur(SLIDER_STEP); return InputResult.HANDLED }
                2 + sliderOffset -> { viewModel.adjustBackgroundSaturation(SLIDER_STEP); return InputResult.HANDLED }
                3 + sliderOffset -> { viewModel.adjustBackgroundOpacity(SLIDER_STEP); return InputResult.HANDLED }
            }
        }

        if (state.currentSection == SettingsSection.BOX_ART) {
            val showGlassTint = state.display.boxArtBorderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GLASS
            val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
            val showOuterThickness = state.display.boxArtOuterEffect != com.nendo.argosy.data.preferences.BoxArtOuterEffect.OFF
            val showInnerThickness = state.display.boxArtInnerEffect != com.nendo.argosy.data.preferences.BoxArtInnerEffect.OFF
            var idx = 3
            val glassTintIdx = if (showGlassTint) idx++ else -1
            val iconPosIdx = idx++
            val iconPadIdx = if (showIconPadding) idx++ else -1
            val outerEffectIdx = idx++
            val outerThicknessIdx = if (showOuterThickness) idx++ else -1
            val innerEffectIdx = idx++
            val innerThicknessIdx = if (showInnerThickness) idx++ else -1
            when (state.focusedIndex) {
                0 -> viewModel.cycleBoxArtCornerRadius(1)
                1 -> viewModel.cycleBoxArtBorderThickness(1)
                2 -> viewModel.cycleBoxArtBorderStyle(1)
                glassTintIdx -> viewModel.cycleGlassBorderTint(1)
                iconPosIdx -> viewModel.cycleSystemIconPosition(1)
                iconPadIdx -> viewModel.cycleSystemIconPadding(1)
                outerEffectIdx -> viewModel.cycleBoxArtOuterEffect(1)
                outerThicknessIdx -> viewModel.cycleBoxArtOuterEffectThickness(1)
                innerEffectIdx -> viewModel.cycleBoxArtInnerEffect(1)
                innerThicknessIdx -> viewModel.cycleBoxArtInnerEffectThickness(1)
            }
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.STORAGE) {
            if (state.focusedIndex == 0) {
                viewModel.adjustMaxConcurrentDownloads(1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.focusedIndex == 1) {
            viewModel.adjustHapticIntensity(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SOUNDS) {
            val bgmVolumeIndex = 1
            val uiSoundsToggleIndex = if (state.ambientAudio.enabled) 3 else 1
            val uiSoundsVolumeIndex = uiSoundsToggleIndex + 1
            when (state.focusedIndex) {
                bgmVolumeIndex -> if (state.ambientAudio.enabled) {
                    viewModel.adjustAmbientAudioVolume(1)
                    return InputResult.HANDLED
                }
                uiSoundsVolumeIndex -> if (state.sounds.enabled) {
                    viewModel.adjustSoundVolume(1)
                    return InputResult.HANDLED
                }
            }
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
                    isConnected && state.syncSettings.saveSyncEnabled -> 6
                    isConnected -> 4
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
            if (config?.showExtensionSelection == true) {
                viewModel.cycleExtensionForPlatform(config, 1)
                return InputResult.HANDLED
            }
        }

        if (state.currentSection == SettingsSection.SYNC_SETTINGS && state.focusedIndex == 2) {
            viewModel.moveImageCacheActionFocus(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BIOS) {
            when (state.focusedIndex) {
                0 -> {
                    viewModel.moveBiosActionFocus(1)
                    return InputResult.HANDLED
                }
                else -> {
                    if (state.focusedIndex >= 2 && viewModel.moveBiosPlatformSubFocus(1)) {
                        return InputResult.HANDLED
                    }
                }
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value

        if (state.storage.platformSettingsModalId != null) {
            viewModel.selectPlatformSettingsOption()
            return InputResult.HANDLED
        }

        if (state.sounds.showSoundPicker) {
            viewModel.confirmSoundPickerSelection()
            return InputResult.HANDLED
        }

        if (state.syncSettings.showRegionPicker) {
            viewModel.confirmRegionPickerSelection()
            return InputResult.handled(SoundType.TOGGLE)
        }

        if (state.syncSettings.showSyncFiltersModal) {
            viewModel.confirmSyncFiltersModalSelection()
            return InputResult.handled(SoundType.TOGGLE)
        }

        if (state.emulators.showEmulatorPicker) {
            viewModel.confirmEmulatorPickerSelection()
            return InputResult.HANDLED
        }

        if (state.emulators.showSavePathModal) {
            viewModel.confirmSavePathModalSelection()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.DISPLAY && state.focusedIndex == 1) {
            viewModel.resetToDefaultColor()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config != null && config.hasInstalledEmulators && config.showSavePath) {
                val subFocus = state.emulators.platformSubFocusIndex
                if (subFocus == 1) {
                    viewModel.showSavePathModal(config)
                    return InputResult.HANDLED
                }
            }
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

        if (state.emulators.showSavePathModal ||
            state.storage.platformSettingsModalId != null ||
            state.syncSettings.showSyncFiltersModal ||
            state.syncSettings.showRegionPicker ||
            state.emulators.showEmulatorPicker) {
            return InputResult.HANDLED
        }

        if (state.sounds.showSoundPicker) {
            viewModel.previewSoundPickerSelection()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SYNC_SETTINGS) {
            viewModel.showSyncFiltersModal()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.EMULATORS) {
            val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
            val platformIndex = state.focusedIndex - focusOffset
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config?.showSavePath == true && config.hasInstalledEmulators) {
                viewModel.showSavePathModal(config)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult = InputResult.UNHANDLED

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.BOX_ART) {
            viewModel.cyclePrevPreviewRatio()
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.STORAGE) {
            viewModel.jumpToStoragePrevSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.BOX_ART) {
            viewModel.cycleNextPreviewRatio()
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.STORAGE) {
            viewModel.jumpToStorageNextSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onLeftStickClick(): InputResult = InputResult.UNHANDLED

    override fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
