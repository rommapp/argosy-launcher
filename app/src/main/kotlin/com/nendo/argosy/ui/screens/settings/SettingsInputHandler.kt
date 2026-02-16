package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.AboutItem
import com.nendo.argosy.ui.screens.settings.sections.ControlsItem
import com.nendo.argosy.ui.screens.settings.sections.aboutItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.controlsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.biosSections
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.createEmulatorsLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.emulatorsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.sections.homeScreenSections
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections
import com.nendo.argosy.ui.screens.settings.sections.BoxArtItem
import com.nendo.argosy.ui.screens.settings.sections.boxArtItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.StorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageSections

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit
) : InputHandler {

    companion object {
        private const val SLIDER_STEP = 10
        private const val HUE_STEP = 10f
    }

    private fun getEmulatorsItemAtFocus(state: SettingsUiState): EmulatorsItem? {
        val layoutInfo = createEmulatorsLayoutInfo(
            platforms = state.emulators.platforms,
            canAutoAssign = state.emulators.canAutoAssign,
            builtinLibretroEnabled = state.emulators.builtinLibretroEnabled
        )
        return emulatorsItemAtFocusIndex(state.focusedIndex, layoutInfo)
    }

    private fun getStorageLayoutInfo(state: SettingsUiState): StorageLayoutInfo =
        createStorageLayoutInfo(state.storage.platformConfigs, state.storage.platformsExpanded)

    private fun hasAlertDialogOpen(state: SettingsUiState): Boolean =
        state.steam.showAddGameDialog

    private fun hasBuiltinControlsModalOpen(state: SettingsUiState): Boolean =
        state.builtinControls.showControllerOrderModal ||
            state.builtinControls.showInputMappingModal ||
            state.builtinControls.showHotkeysModal

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED
        if (state.bios.showGpuDriverPrompt && state.bios.gpuDriverInfo?.isInstalling != true) {
            viewModel.moveGpuDriverPromptFocus(-1)
            return InputResult.HANDLED
        }

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
        if (state.syncSettings.showPlatformFiltersModal) {
            viewModel.movePlatformFiltersModalFocus(-1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showSyncFiltersModal) {
            viewModel.moveSyncFiltersModalFocus(-1)
            return InputResult.HANDLED
        }
        if (state.emulators.showVariantPicker) {
            viewModel.moveVariantPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.moveShaderPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.SHADER_STACK) {
            viewModel.moveShaderParamFocus(-1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val item = getEmulatorsItemAtFocus(state)
            if (item is EmulatorsItem.PlatformItem) {
                val config = item.config
                if (config.hasInstalledEmulators && config.showSavePath &&
                    state.emulators.platformSubFocusIndex == 1
                ) {
                    viewModel.movePlatformSubFocus(-1, 1)
                    return InputResult.HANDLED
                }
            }
        }
        viewModel.moveFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED
        if (state.bios.showGpuDriverPrompt && state.bios.gpuDriverInfo?.isInstalling != true) {
            viewModel.moveGpuDriverPromptFocus(1)
            return InputResult.HANDLED
        }

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
        if (state.syncSettings.showPlatformFiltersModal) {
            viewModel.movePlatformFiltersModalFocus(1)
            return InputResult.HANDLED
        }
        if (state.syncSettings.showSyncFiltersModal) {
            viewModel.moveSyncFiltersModalFocus(1)
            return InputResult.HANDLED
        }
        if (state.emulators.showVariantPicker) {
            viewModel.moveVariantPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(1)
            return InputResult.HANDLED
        }
        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.moveShaderPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.SHADER_STACK) {
            viewModel.moveShaderParamFocus(1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val item = getEmulatorsItemAtFocus(state)
            if (item is EmulatorsItem.PlatformItem) {
                val config = item.config
                if (config.hasInstalledEmulators && config.showSavePath &&
                    state.emulators.platformSubFocusIndex == 0
                ) {
                    viewModel.movePlatformSubFocus(1, 1)
                    return InputResult.HANDLED
                }
            }
        }
        viewModel.moveFocus(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

        if (state.bios.showGpuDriverPrompt) return InputResult.HANDLED
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
        if (state.sounds.showSoundPicker) return InputResult.HANDLED
        if (state.syncSettings.showRegionPicker) return InputResult.HANDLED
        if (state.syncSettings.showPlatformFiltersModal) return InputResult.HANDLED
        if (state.syncSettings.showSyncFiltersModal) return InputResult.HANDLED
        if (state.syncSettings.showForceSyncConfirm) {
            viewModel.moveSyncConfirmFocus(-1)
            return InputResult.HANDLED
        }
        if (state.emulators.showVariantPicker) return InputResult.HANDLED
        if (state.emulators.showEmulatorPicker) return InputResult.HANDLED
        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.jumpShaderPickerSection(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
            when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
                InterfaceItem.AccentColor -> { viewModel.adjustHue(-HUE_STEP); return InputResult.HANDLED }
                InterfaceItem.SecondaryColor -> { viewModel.adjustSecondaryHue(-HUE_STEP); return InputResult.HANDLED }
                InterfaceItem.GridDensity -> { viewModel.cycleGridDensity(-1); return InputResult.HANDLED }
                InterfaceItem.Theme -> { viewModel.cycleThemeMode(-1); return InputResult.HANDLED }
                InterfaceItem.UiScale -> { viewModel.adjustUiScale(-5); return InputResult.HANDLED }
                InterfaceItem.DimAfter -> { viewModel.adjustScreenDimmerTimeout(-1); return InputResult.HANDLED }
                InterfaceItem.DimLevel -> { viewModel.adjustScreenDimmerLevel(-1); return InputResult.HANDLED }
                InterfaceItem.AmbientLedBrightness -> { viewModel.adjustAmbientLedBrightness(-5); return InputResult.HANDLED }
                InterfaceItem.AmbientLedColorMode -> { viewModel.cycleAmbientLedColorMode(-1); return InputResult.HANDLED }
                InterfaceItem.DisplayRoles -> { viewModel.cycleDisplayRoleOverride(-1); return InputResult.HANDLED }
                InterfaceItem.BgmVolume -> if (state.ambientAudio.enabled) { viewModel.adjustAmbientAudioVolume(-1); return InputResult.HANDLED }
                InterfaceItem.UiSoundsVolume -> if (state.sounds.enabled) { viewModel.adjustSoundVolume(-1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.HOME_SCREEN) {
            when (homeScreenItemAtFocusIndex(state.focusedIndex, state.display)) {
                HomeScreenItem.Blur -> { viewModel.adjustBackgroundBlur(-SLIDER_STEP); return InputResult.HANDLED }
                HomeScreenItem.Saturation -> { viewModel.adjustBackgroundSaturation(-SLIDER_STEP); return InputResult.HANDLED }
                HomeScreenItem.Opacity -> { viewModel.adjustBackgroundOpacity(-SLIDER_STEP); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.ABOUT) {
            val hasLogPath = state.fileLoggingPath != null
            when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath)) {
                AboutItem.LogLevel -> { viewModel.cycleFileLogLevel(-1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.BOX_ART) {
            when (boxArtItemAtFocusIndex(state.focusedIndex, state.display)) {
                BoxArtItem.Shape -> viewModel.cycleBoxArtShape(-1)
                BoxArtItem.CornerRadius -> viewModel.cycleBoxArtCornerRadius(-1)
                BoxArtItem.BorderThickness -> viewModel.cycleBoxArtBorderThickness(-1)
                BoxArtItem.BorderStyle -> viewModel.cycleBoxArtBorderStyle(-1)
                BoxArtItem.GlassTint -> viewModel.cycleGlassBorderTint(-1)
                BoxArtItem.GradientPresetItem -> viewModel.cycleGradientPreset(-1)
                BoxArtItem.GradientAdvanced -> viewModel.toggleGradientAdvancedMode()
                BoxArtItem.SampleGrid -> viewModel.cycleGradientSampleGrid(-1)
                BoxArtItem.SampleRadius -> viewModel.cycleGradientRadius(-1)
                BoxArtItem.MinSaturation -> viewModel.cycleGradientMinSaturation(-1)
                BoxArtItem.MinBrightness -> viewModel.cycleGradientMinValue(-1)
                BoxArtItem.HueDistance -> viewModel.cycleGradientHueDistance(-1)
                BoxArtItem.SaturationBoost -> viewModel.cycleGradientSaturationBump(-1)
                BoxArtItem.BrightnessClamp -> viewModel.cycleGradientValueClamp(-1)
                BoxArtItem.IconPos -> viewModel.cycleSystemIconPosition(-1)
                BoxArtItem.IconPad -> viewModel.cycleSystemIconPadding(-1)
                BoxArtItem.OuterEffect -> viewModel.cycleBoxArtOuterEffect(-1)
                BoxArtItem.OuterThickness -> viewModel.cycleBoxArtOuterEffectThickness(-1)
                BoxArtItem.GlowIntensity -> viewModel.cycleBoxArtGlowStrength(-1)
                BoxArtItem.GlowColor -> viewModel.cycleGlowColorMode(-1)
                BoxArtItem.InnerEffect -> viewModel.cycleBoxArtInnerEffect(-1)
                BoxArtItem.InnerThickness -> viewModel.cycleBoxArtInnerEffectThickness(-1)
                else -> {}
            }
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.STORAGE) {
            when (storageItemAtFocusIndex(state.focusedIndex, getStorageLayoutInfo(state))) {
                StorageItem.MaxDownloads -> { viewModel.adjustMaxConcurrentDownloads(-1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.CONTROLS) {
            when (controlsItemAtFocusIndex(state.focusedIndex, state.controls)) {
                ControlsItem.VibrationStrength -> if (state.controls.hapticEnabled && state.controls.vibrationSupported) {
                    viewModel.adjustVibrationStrength(-0.1f); return InputResult.HANDLED
                }
                ControlsItem.InputFocus -> { viewModel.cycleDualScreenInputFocus(-1); return InputResult.HANDLED }
                else -> {}
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
                    isConnected && state.syncSettings.saveSyncEnabled -> 10
                    isConnected -> 8
                    else -> 2
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
            val item = getEmulatorsItemAtFocus(state)
            if (item is EmulatorsItem.PlatformItem) {
                val config = item.config
                if (config.showCoreSelection) {
                    viewModel.cycleCoreForPlatform(config, -1)
                    return InputResult.HANDLED
                }
                if (config.showExtensionSelection) {
                    viewModel.cycleExtensionForPlatform(config, -1)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.SYNC_SETTINGS && state.focusedIndex == 3) {
            viewModel.moveImageCacheActionFocus(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BIOS) {
            when (state.focusedIndex) {
                0 -> {
                    viewModel.moveBiosActionFocus(-1)
                    return InputResult.HANDLED
                }
                1 -> {
                    if (viewModel.moveBiosPathActionFocus(-1)) {
                        return InputResult.HANDLED
                    }
                }
                else -> {
                    if (state.focusedIndex >= 2 && viewModel.moveBiosPlatformSubFocus(-1)) {
                        return InputResult.HANDLED
                    }
                }
            }
        }

        if (state.currentSection == SettingsSection.CORE_MANAGEMENT) {
            viewModel.moveCoreManagementCoreFocus(-1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BUILTIN_VIDEO) {
            val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, state.builtinVideo)
            if (setting != null && setting.type is LibretroSettingDef.SettingType.Cycle) {
                if (state.builtinVideo.isGlobalContext) {
                    when (setting) {
                        LibretroSettingDef.Shader -> { viewModel.cycleBuiltinShader(-1); return InputResult.HANDLED }
                        LibretroSettingDef.Filter -> {
                            if (state.builtinVideo.shader != "Custom") {
                                viewModel.cycleBuiltinFilter(-1)
                            }
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.AspectRatio -> { viewModel.cycleBuiltinAspectRatio(-1); return InputResult.HANDLED }
                        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(-1); return InputResult.HANDLED }
                        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(-1); return InputResult.HANDLED }
                        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(-1); return InputResult.HANDLED }
                        else -> {}
                    }
                } else {
                    val platformContext = state.builtinVideo.currentPlatformContext
                    val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
                    val accessor = PlatformLibretroSettingsAccessor(
                        platformSettings = platformSettings,
                        globalState = state.builtinVideo,
                        onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
                    )
                    if (!accessor.isActionItem(setting)) {
                        accessor.cycle(setting, -1)
                    }
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.SHADER_STACK && !viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.adjustShaderParam(-1)
            return InputResult.HANDLED
        }

        return InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

        if (state.bios.showGpuDriverPrompt) return InputResult.HANDLED
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
        if (state.sounds.showSoundPicker) return InputResult.HANDLED
        if (state.syncSettings.showRegionPicker) return InputResult.HANDLED
        if (state.syncSettings.showPlatformFiltersModal) return InputResult.HANDLED
        if (state.syncSettings.showSyncFiltersModal) return InputResult.HANDLED
        if (state.syncSettings.showForceSyncConfirm) {
            viewModel.moveSyncConfirmFocus(1)
            return InputResult.HANDLED
        }
        if (state.emulators.showVariantPicker) return InputResult.HANDLED
        if (state.emulators.showEmulatorPicker) return InputResult.HANDLED
        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.jumpShaderPickerSection(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
            when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
                InterfaceItem.AccentColor -> { viewModel.adjustHue(HUE_STEP); return InputResult.HANDLED }
                InterfaceItem.SecondaryColor -> { viewModel.adjustSecondaryHue(HUE_STEP); return InputResult.HANDLED }
                InterfaceItem.GridDensity -> { viewModel.cycleGridDensity(1); return InputResult.HANDLED }
                InterfaceItem.Theme -> { viewModel.cycleThemeMode(1); return InputResult.HANDLED }
                InterfaceItem.UiScale -> { viewModel.adjustUiScale(5); return InputResult.HANDLED }
                InterfaceItem.DimAfter -> { viewModel.adjustScreenDimmerTimeout(1); return InputResult.HANDLED }
                InterfaceItem.DimLevel -> { viewModel.adjustScreenDimmerLevel(1); return InputResult.HANDLED }
                InterfaceItem.AmbientLedBrightness -> { viewModel.adjustAmbientLedBrightness(5); return InputResult.HANDLED }
                InterfaceItem.AmbientLedColorMode -> { viewModel.cycleAmbientLedColorMode(1); return InputResult.HANDLED }
                InterfaceItem.DisplayRoles -> { viewModel.cycleDisplayRoleOverride(1); return InputResult.HANDLED }
                InterfaceItem.BgmVolume -> if (state.ambientAudio.enabled) { viewModel.adjustAmbientAudioVolume(1); return InputResult.HANDLED }
                InterfaceItem.UiSoundsVolume -> if (state.sounds.enabled) { viewModel.adjustSoundVolume(1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.HOME_SCREEN) {
            when (homeScreenItemAtFocusIndex(state.focusedIndex, state.display)) {
                HomeScreenItem.Blur -> { viewModel.adjustBackgroundBlur(SLIDER_STEP); return InputResult.HANDLED }
                HomeScreenItem.Saturation -> { viewModel.adjustBackgroundSaturation(SLIDER_STEP); return InputResult.HANDLED }
                HomeScreenItem.Opacity -> { viewModel.adjustBackgroundOpacity(SLIDER_STEP); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.ABOUT) {
            val hasLogPath = state.fileLoggingPath != null
            when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath)) {
                AboutItem.LogLevel -> { viewModel.cycleFileLogLevel(1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.BOX_ART) {
            when (boxArtItemAtFocusIndex(state.focusedIndex, state.display)) {
                BoxArtItem.Shape -> viewModel.cycleBoxArtShape(1)
                BoxArtItem.CornerRadius -> viewModel.cycleBoxArtCornerRadius(1)
                BoxArtItem.BorderThickness -> viewModel.cycleBoxArtBorderThickness(1)
                BoxArtItem.BorderStyle -> viewModel.cycleBoxArtBorderStyle(1)
                BoxArtItem.GlassTint -> viewModel.cycleGlassBorderTint(1)
                BoxArtItem.GradientPresetItem -> viewModel.cycleGradientPreset(1)
                BoxArtItem.GradientAdvanced -> viewModel.toggleGradientAdvancedMode()
                BoxArtItem.SampleGrid -> viewModel.cycleGradientSampleGrid(1)
                BoxArtItem.SampleRadius -> viewModel.cycleGradientRadius(1)
                BoxArtItem.MinSaturation -> viewModel.cycleGradientMinSaturation(1)
                BoxArtItem.MinBrightness -> viewModel.cycleGradientMinValue(1)
                BoxArtItem.HueDistance -> viewModel.cycleGradientHueDistance(1)
                BoxArtItem.SaturationBoost -> viewModel.cycleGradientSaturationBump(1)
                BoxArtItem.BrightnessClamp -> viewModel.cycleGradientValueClamp(1)
                BoxArtItem.IconPos -> viewModel.cycleSystemIconPosition(1)
                BoxArtItem.IconPad -> viewModel.cycleSystemIconPadding(1)
                BoxArtItem.OuterEffect -> viewModel.cycleBoxArtOuterEffect(1)
                BoxArtItem.OuterThickness -> viewModel.cycleBoxArtOuterEffectThickness(1)
                BoxArtItem.GlowIntensity -> viewModel.cycleBoxArtGlowStrength(1)
                BoxArtItem.GlowColor -> viewModel.cycleGlowColorMode(1)
                BoxArtItem.InnerEffect -> viewModel.cycleBoxArtInnerEffect(1)
                BoxArtItem.InnerThickness -> viewModel.cycleBoxArtInnerEffectThickness(1)
                else -> {}
            }
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.STORAGE) {
            when (storageItemAtFocusIndex(state.focusedIndex, getStorageLayoutInfo(state))) {
                StorageItem.MaxDownloads -> { viewModel.adjustMaxConcurrentDownloads(1); return InputResult.HANDLED }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.CONTROLS) {
            when (controlsItemAtFocusIndex(state.focusedIndex, state.controls)) {
                ControlsItem.VibrationStrength -> if (state.controls.hapticEnabled && state.controls.vibrationSupported) {
                    viewModel.adjustVibrationStrength(0.1f); return InputResult.HANDLED
                }
                ControlsItem.InputFocus -> { viewModel.cycleDualScreenInputFocus(1); return InputResult.HANDLED }
                else -> {}
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
                    isConnected && state.syncSettings.saveSyncEnabled -> 10
                    isConnected -> 8
                    else -> 2
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
            val item = getEmulatorsItemAtFocus(state)
            if (item is EmulatorsItem.PlatformItem) {
                val config = item.config
                if (config.showCoreSelection) {
                    viewModel.cycleCoreForPlatform(config, 1)
                    return InputResult.HANDLED
                }
                if (config.showExtensionSelection) {
                    viewModel.cycleExtensionForPlatform(config, 1)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.SYNC_SETTINGS && state.focusedIndex == 3) {
            viewModel.moveImageCacheActionFocus(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BIOS) {
            when (state.focusedIndex) {
                0 -> {
                    viewModel.moveBiosActionFocus(1)
                    return InputResult.HANDLED
                }
                1 -> {
                    if (viewModel.moveBiosPathActionFocus(1)) {
                        return InputResult.HANDLED
                    }
                }
                else -> {
                    if (state.focusedIndex >= 2 && viewModel.moveBiosPlatformSubFocus(1)) {
                        return InputResult.HANDLED
                    }
                }
            }
        }

        if (state.currentSection == SettingsSection.CORE_MANAGEMENT) {
            viewModel.moveCoreManagementCoreFocus(1)
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.BUILTIN_VIDEO) {
            val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, state.builtinVideo)
            if (setting != null && setting.type is LibretroSettingDef.SettingType.Cycle) {
                if (state.builtinVideo.isGlobalContext) {
                    when (setting) {
                        LibretroSettingDef.Shader -> { viewModel.cycleBuiltinShader(1); return InputResult.HANDLED }
                        LibretroSettingDef.Filter -> {
                            if (state.builtinVideo.shader != "Custom") {
                                viewModel.cycleBuiltinFilter(1)
                            }
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.AspectRatio -> { viewModel.cycleBuiltinAspectRatio(1); return InputResult.HANDLED }
                        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(1); return InputResult.HANDLED }
                        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(1); return InputResult.HANDLED }
                        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(1); return InputResult.HANDLED }
                        else -> {}
                    }
                } else {
                    val platformContext = state.builtinVideo.currentPlatformContext
                    val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
                    val accessor = PlatformLibretroSettingsAccessor(
                        platformSettings = platformSettings,
                        globalState = state.builtinVideo,
                        onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
                    )
                    if (!accessor.isActionItem(setting)) {
                        accessor.cycle(setting, 1)
                    }
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.SHADER_STACK && !viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.adjustShaderParam(1)
            return InputResult.HANDLED
        }

        return InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

        if (state.bios.showGpuDriverPrompt && state.bios.gpuDriverInfo?.isInstalling != true) {
            when (state.bios.gpuDriverPromptFocusIndex) {
                0 -> viewModel.installGpuDriver()
                1 -> viewModel.openGpuDriverFilePicker()
                2 -> viewModel.dismissGpuDriverPrompt()
            }
            return InputResult.HANDLED
        }

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

        if (state.syncSettings.showForceSyncConfirm) {
            if (state.syncSettings.syncConfirmButtonIndex == 0) {
                viewModel.cancelSyncSaves()
            } else {
                viewModel.confirmSyncSaves()
            }
            return InputResult.HANDLED
        }

        if (state.syncSettings.showPlatformFiltersModal) {
            viewModel.confirmPlatformFiltersModalSelection()
            return InputResult.handled(SoundType.TOGGLE)
        }

        if (state.emulators.showVariantPicker) {
            viewModel.selectVariant()
            return InputResult.HANDLED
        }
        if (state.emulators.showEmulatorPicker) {
            viewModel.confirmEmulatorPickerSelection()
            return InputResult.HANDLED
        }

        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.confirmShaderPickerSelection()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SHADER_STACK &&
            viewModel.shaderChainManager.shaderStack.selectedShaderParams.isNotEmpty()
        ) {
            viewModel.resetShaderParam()
            return InputResult.HANDLED
        }

        if (state.emulators.showSavePathModal) {
            viewModel.confirmSavePathModalSelection()
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
            when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
                InterfaceItem.AccentColor -> {
                    viewModel.resetToDefaultColor()
                    return InputResult.HANDLED
                }
                InterfaceItem.SecondaryColor -> {
                    viewModel.resetToDefaultSecondaryColor()
                    return InputResult.HANDLED
                }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.EMULATORS) {
            when (val item = getEmulatorsItemAtFocus(state)) {
                EmulatorsItem.BuiltinVideo -> {
                    viewModel.navigateToBuiltinVideo()
                    return InputResult.HANDLED
                }
                EmulatorsItem.BuiltinControls -> {
                    viewModel.navigateToBuiltinControls()
                    return InputResult.HANDLED
                }
                EmulatorsItem.BuiltinCores -> {
                    viewModel.navigateToCoreManagement()
                    return InputResult.HANDLED
                }
                EmulatorsItem.BuiltinToggle -> {
                    viewModel.setBuiltinLibretroEnabled(!state.emulators.builtinLibretroEnabled)
                    return InputResult.handled(SoundType.TOGGLE)
                }
                EmulatorsItem.CheckForUpdates -> {
                    viewModel.forceCheckEmulatorUpdates()
                    return InputResult.HANDLED
                }
                EmulatorsItem.AutoAssign -> {
                    viewModel.autoAssignAllEmulators()
                    return InputResult.HANDLED
                }
                is EmulatorsItem.PlatformItem -> {
                    val config = item.config
                    if (config.hasInstalledEmulators && config.showSavePath &&
                        state.emulators.platformSubFocusIndex == 1
                    ) {
                        viewModel.showSavePathModal(config)
                    } else {
                        viewModel.showEmulatorPicker(config)
                    }
                    return InputResult.HANDLED
                }
                else -> {}
            }
        }

        if (state.currentSection == SettingsSection.BUILTIN_VIDEO) {
            val videoState = state.builtinVideo
            val isGlobal = videoState.isGlobalContext
            val platformContext = videoState.currentPlatformContext
            val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
            val hasAnyOverrides = platformSettings?.hasAnyOverrides() == true

            val maxSettingsIndex = libretroSettingsMaxFocusIndex(
                platformSlug = platformContext?.platformSlug,
                canEnableBFI = videoState.canEnableBlackFrameInsertion
            )
            val resetAllIndex = maxSettingsIndex + 1

            if (!isGlobal && hasAnyOverrides && state.focusedIndex == resetAllIndex) {
                viewModel.resetAllPlatformLibretroSettings()
                return InputResult.HANDLED
            }

            val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState)
            if (setting != null) {
                if (isGlobal) {
                    when (setting) {
                        LibretroSettingDef.Shader -> {
                            viewModel.cycleBuiltinShader(1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.Filter -> {
                            if (state.builtinVideo.shader == "Custom") {
                                viewModel.openShaderChainConfig()
                            } else {
                                viewModel.cycleBuiltinFilter(1)
                            }
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.AspectRatio -> {
                            viewModel.cycleBuiltinAspectRatio(1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.Rotation -> {
                            viewModel.cycleBuiltinRotation(1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.OverscanCrop -> {
                            viewModel.cycleBuiltinOverscanCrop(1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.FastForwardSpeed -> {
                            viewModel.cycleBuiltinFastForwardSpeed(1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.BlackFrameInsertion -> {
                            viewModel.setBuiltinBlackFrameInsertion(!videoState.blackFrameInsertion)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        LibretroSettingDef.RewindEnabled -> {
                            viewModel.setBuiltinRewindEnabled(!videoState.rewindEnabled)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        LibretroSettingDef.SkipDuplicateFrames -> {
                            viewModel.setBuiltinSkipDuplicateFrames(!videoState.skipDuplicateFrames)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        LibretroSettingDef.LowLatencyAudio -> {
                            viewModel.setBuiltinLowLatencyAudio(!videoState.lowLatencyAudio)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        LibretroSettingDef.ForceSoftwareTiming -> {
                            viewModel.setBuiltinForceSoftwareTiming(!videoState.forceSoftwareTiming)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                        LibretroSettingDef.Frame -> {
                            viewModel.setBuiltinFramesEnabled(!videoState.framesEnabled)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                    }
                } else {
                    val accessor = PlatformLibretroSettingsAccessor(
                        platformSettings = platformSettings,
                        globalState = videoState,
                        onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) },
                        onActionCallback = { s ->
                            when (s) {
                                LibretroSettingDef.Frame -> viewModel.openFrameConfig()
                                LibretroSettingDef.Filter -> viewModel.openShaderChainConfig()
                                else -> {}
                            }
                        }
                    )
                    if (accessor.isActionItem(setting)) {
                        accessor.onAction(setting)
                        return InputResult.HANDLED
                    }
                    when (setting.type) {
                        is LibretroSettingDef.SettingType.Cycle -> {
                            accessor.cycle(setting, 1)
                            return InputResult.HANDLED
                        }
                        LibretroSettingDef.SettingType.Switch -> {
                            accessor.toggle(setting)
                            return InputResult.handled(SoundType.TOGGLE)
                        }
                    }
                }
            }
        }

        if (state.currentSection == SettingsSection.BUILTIN_CONTROLS) {
            val controlsResetAllIndex = builtinControlsMaxFocusIndex(
                state.builtinControls, state.builtinVideo, state.platformLibretro.platformSettings
            )
            val controlsBaseMax = builtinControlsMaxFocusIndex(state.builtinControls)
            if (state.focusedIndex == controlsResetAllIndex && controlsResetAllIndex > controlsBaseMax) {
                viewModel.resetAllPlatformControlSettings()
                return InputResult.handled(SoundType.SELECT)
            }

            when (builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)) {
                BuiltinControlsItem.Rumble -> {
                    if (state.builtinVideo.isGlobalContext) {
                        viewModel.setBuiltinRumbleEnabled(!state.builtinControls.rumbleEnabled)
                    } else {
                        val ps = state.builtinVideo.currentPlatformContext?.let {
                            state.platformLibretro.platformSettings[it.platformId]
                        }
                        val effective = ps?.rumbleEnabled ?: state.builtinControls.rumbleEnabled
                        viewModel.updatePlatformControlSetting("rumbleEnabled", !effective)
                    }
                    return InputResult.handled(SoundType.TOGGLE)
                }
                BuiltinControlsItem.LimitHotkeysToPlayer1 -> {
                    viewModel.setBuiltinLimitHotkeysToPlayer1(!state.builtinControls.limitHotkeysToPlayer1)
                    return InputResult.handled(SoundType.TOGGLE)
                }
                BuiltinControlsItem.AnalogAsDpad -> {
                    if (state.builtinVideo.isGlobalContext) {
                        viewModel.setBuiltinAnalogAsDpad(!state.builtinControls.analogAsDpad)
                    } else {
                        val pc = state.builtinVideo.currentPlatformContext
                        val ps = pc?.let { state.platformLibretro.platformSettings[it.platformId] }
                        val platformHasAnalog = pc != null && PlatformWeightRegistry.hasAnalogStick(pc.platformSlug)
                        val effective = ps?.analogAsDpad
                            ?: !platformHasAnalog
                        viewModel.updatePlatformControlSetting("analogAsDpad", !effective)
                    }
                    return InputResult.handled(SoundType.TOGGLE)
                }
                BuiltinControlsItem.DpadAsAnalog -> {
                    if (state.builtinVideo.isGlobalContext) {
                        viewModel.setBuiltinDpadAsAnalog(!state.builtinControls.dpadAsAnalog)
                    } else {
                        val ps = state.builtinVideo.currentPlatformContext?.let {
                            state.platformLibretro.platformSettings[it.platformId]
                        }
                        val effective = ps?.dpadAsAnalog ?: false
                        viewModel.updatePlatformControlSetting("dpadAsAnalog", !effective)
                    }
                    return InputResult.handled(SoundType.TOGGLE)
                }
                BuiltinControlsItem.ControllerOrder -> {
                    viewModel.showControllerOrderModal()
                    return InputResult.handled(SoundType.SELECT)
                }
                BuiltinControlsItem.InputMapping -> {
                    viewModel.showInputMappingModal()
                    return InputResult.handled(SoundType.SELECT)
                }
                BuiltinControlsItem.Hotkeys -> {
                    viewModel.showHotkeysModal()
                    return InputResult.handled(SoundType.SELECT)
                }
                else -> {}
            }
        }

        return viewModel.handleConfirm()
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

        if (state.bios.showGpuDriverPrompt && state.bios.gpuDriverInfo?.isInstalling != true) {
            viewModel.dismissGpuDriverPrompt()
            return InputResult.HANDLED
        }

        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.dismissShaderPicker()
            return InputResult.HANDLED
        }

        if (state.emulators.showVariantPicker) {
            viewModel.dismissVariantPicker()
            return InputResult.HANDLED
        }

        return if (!viewModel.navigateBack()) {
            onBackNavigation()
            InputResult.HANDLED
        } else {
            InputResult.HANDLED
        }
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

        if (state.emulators.showSavePathModal ||
            state.storage.platformSettingsModalId != null ||
            state.syncSettings.showPlatformFiltersModal ||
            state.syncSettings.showSyncFiltersModal ||
            state.syncSettings.showRegionPicker ||
            state.emulators.showEmulatorPicker ||
            state.emulators.showVariantPicker ||
            viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            return InputResult.HANDLED
        }

        if (state.currentSection == SettingsSection.SHADER_STACK) {
            viewModel.showShaderPicker()
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
            val item = getEmulatorsItemAtFocus(state)
            if (item is EmulatorsItem.PlatformItem) {
                val config = item.config
                if (config.showSavePath && config.hasInstalledEmulators) {
                    viewModel.showSavePathModal(config)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.BUILTIN_VIDEO && !state.builtinVideo.isGlobalContext) {
            val videoState = state.builtinVideo
            val platformContext = videoState.currentPlatformContext
            val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
            val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState)
            if (setting != null) {
                val accessor = PlatformLibretroSettingsAccessor(
                    platformSettings = platformSettings,
                    globalState = videoState,
                    onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
                )
                if (accessor.hasOverride(setting)) {
                    accessor.reset(setting)
                    return InputResult.HANDLED
                }
            }
        }

        if (state.currentSection == SettingsSection.BUILTIN_CONTROLS && !state.builtinVideo.isGlobalContext) {
            val item = builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)
            val platformContext = state.builtinVideo.currentPlatformContext
            val ps = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
            val field = when (item) {
                BuiltinControlsItem.Rumble -> "rumbleEnabled"
                BuiltinControlsItem.AnalogAsDpad -> "analogAsDpad"
                BuiltinControlsItem.DpadAsAnalog -> "dpadAsAnalog"
                else -> null
            }
            if (field != null) {
                val hasOverride = when (item) {
                    BuiltinControlsItem.Rumble -> ps?.rumbleEnabled != null
                    BuiltinControlsItem.AnalogAsDpad -> ps?.analogAsDpad != null
                    BuiltinControlsItem.DpadAsAnalog -> ps?.dpadAsAnalog != null
                    else -> false
                }
                if (hasOverride) {
                    viewModel.updatePlatformControlSetting(field, null)
                    return InputResult.HANDLED
                }
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (viewModel.shaderChainManager.shaderStack.showShaderPicker) return InputResult.HANDLED
        if (state.currentSection == SettingsSection.SHADER_STACK && viewModel.shaderChainManager.shaderStack.entries.isNotEmpty()) {
            viewModel.removeShaderFromStack()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult = InputResult.UNHANDLED

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        when (state.currentSection) {
            SettingsSection.SHADER_STACK -> {
                if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
                    viewModel.jumpShaderPickerSection(-1)
                    return InputResult.HANDLED
                } else if (viewModel.shaderChainManager.shaderStack.entries.isNotEmpty()) {
                    viewModel.cycleShaderTab(-1)
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BOX_ART -> {
                viewModel.cycleBoxArtShape(-1)
                return InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                viewModel.jumpToPrevSection(storageSections(getStorageLayoutInfo(state)))
                return InputResult.HANDLED
            }
            SettingsSection.INTERFACE -> {
                val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
                if (viewModel.jumpToPrevSection(interfaceSections(layoutState))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.HOME_SCREEN -> {
                if (viewModel.jumpToPrevSection(homeScreenSections(state.display))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BIOS -> {
                if (viewModel.jumpToPrevSection(biosSections(state.bios.platformGroups, state.bios.expandedPlatformIndex))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BUILTIN_VIDEO -> {
                if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
                    viewModel.cyclePlatformContext(-1)
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BUILTIN_CONTROLS -> {
                if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
                    viewModel.cyclePlatformContext(-1)
                    return InputResult.HANDLED
                }
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        when (state.currentSection) {
            SettingsSection.SHADER_STACK -> {
                if (viewModel.shaderChainManager.shaderStack.showShaderPicker) {
                    viewModel.jumpShaderPickerSection(1)
                    return InputResult.HANDLED
                } else if (viewModel.shaderChainManager.shaderStack.entries.isNotEmpty()) {
                    viewModel.cycleShaderTab(1)
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BOX_ART -> {
                viewModel.cycleBoxArtShape(1)
                return InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                viewModel.jumpToNextSection(storageSections(getStorageLayoutInfo(state)))
                return InputResult.HANDLED
            }
            SettingsSection.INTERFACE -> {
                val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
                if (viewModel.jumpToNextSection(interfaceSections(layoutState))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.HOME_SCREEN -> {
                if (viewModel.jumpToNextSection(homeScreenSections(state.display))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BIOS -> {
                if (viewModel.jumpToNextSection(biosSections(state.bios.platformGroups, state.bios.expandedPlatformIndex))) {
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BUILTIN_VIDEO -> {
                if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
                    viewModel.cyclePlatformContext(1)
                    return InputResult.HANDLED
                }
            }
            SettingsSection.BUILTIN_CONTROLS -> {
                if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
                    viewModel.cyclePlatformContext(1)
                    return InputResult.HANDLED
                }
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevTrigger(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.BOX_ART) {
            viewModel.cyclePrevPreviewGame()
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.SHADER_STACK && !viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.reorderShaderInStack(-1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextTrigger(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.BOX_ART) {
            viewModel.cycleNextPreviewGame()
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.SHADER_STACK && !viewModel.shaderChainManager.shaderStack.showShaderPicker) {
            viewModel.reorderShaderInStack(1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onLeftStickClick(): InputResult = InputResult.UNHANDLED

    override fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
