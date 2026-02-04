package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.biosSections
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.sections.homeScreenSections
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections

class SettingsInputHandler(
    private val viewModel: SettingsViewModel,
    private val onBackNavigation: () -> Unit
) : InputHandler {

    companion object {
        private const val SLIDER_STEP = 10
        private const val HUE_STEP = 10f
        private const val EMULATORS_BUILTIN_COUNT = 3  // Video, Audio, Cores
    }

    private fun getEmulatorsPlatformIndex(focusedIndex: Int, canAutoAssign: Boolean): Int {
        val platformStartIndex = EMULATORS_BUILTIN_COUNT + (if (canAutoAssign) 1 else 0)
        return if (focusedIndex >= platformStartIndex) focusedIndex - platformStartIndex else -1
    }

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
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(-1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
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
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

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
        if (state.emulators.showEmulatorPicker) {
            viewModel.moveEmulatorPickerFocus(1)
            return InputResult.HANDLED
        }
        if (state.currentSection == SettingsSection.EMULATORS) {
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
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
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

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
        if (state.emulators.showEmulatorPicker) return InputResult.HANDLED

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
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

        if (state.currentSection == SettingsSection.BOX_ART) {
            val borderStyle = state.display.boxArtBorderStyle
            val showGlassTint = borderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GLASS
            val showGradient = borderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GRADIENT
            val showAdvancedMode = state.display.gradientAdvancedMode
            val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
            val showOuterThickness = state.display.boxArtOuterEffect != com.nendo.argosy.data.preferences.BoxArtOuterEffect.OFF
            val showGlowIntensity = state.display.boxArtOuterEffect == com.nendo.argosy.data.preferences.BoxArtOuterEffect.GLOW
            val showInnerThickness = state.display.boxArtInnerEffect != com.nendo.argosy.data.preferences.BoxArtInnerEffect.OFF
            var idx = 4
            val glassTintIdx = if (showGlassTint) idx++ else -1
            val gradientPresetIdx = if (showGradient) idx++ else -1
            val gradientAdvancedIdx = if (showGradient) idx++ else -1
            val sampleGridIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val sampleRadiusIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val minSatIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val minBrightIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val hueDistIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val satBoostIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val brightClampIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val iconPosIdx = idx++
            val iconPadIdx = if (showIconPadding) idx++ else -1
            val outerEffectIdx = idx++
            val outerThicknessIdx = if (showOuterThickness) idx++ else -1
            val glowIntensityIdx = if (showGlowIntensity) idx++ else -1
            val innerEffectIdx = idx++
            val innerThicknessIdx = if (showInnerThickness) idx++ else -1
            when (state.focusedIndex) {
                0 -> viewModel.cycleBoxArtShape(-1)
                1 -> viewModel.cycleBoxArtCornerRadius(-1)
                2 -> viewModel.cycleBoxArtBorderThickness(-1)
                3 -> viewModel.cycleBoxArtBorderStyle(-1)
                glassTintIdx -> viewModel.cycleGlassBorderTint(-1)
                gradientPresetIdx -> viewModel.cycleGradientPreset(-1)
                gradientAdvancedIdx -> viewModel.toggleGradientAdvancedMode()
                sampleGridIdx -> viewModel.cycleGradientSampleGrid(-1)
                sampleRadiusIdx -> viewModel.cycleGradientRadius(-1)
                minSatIdx -> viewModel.cycleGradientMinSaturation(-1)
                minBrightIdx -> viewModel.cycleGradientMinValue(-1)
                hueDistIdx -> viewModel.cycleGradientHueDistance(-1)
                satBoostIdx -> viewModel.cycleGradientSaturationBump(-1)
                brightClampIdx -> viewModel.cycleGradientValueClamp(-1)
                iconPosIdx -> viewModel.cycleSystemIconPosition(-1)
                iconPadIdx -> viewModel.cycleSystemIconPadding(-1)
                outerEffectIdx -> viewModel.cycleBoxArtOuterEffect(-1)
                outerThicknessIdx -> viewModel.cycleBoxArtOuterEffectThickness(-1)
                glowIntensityIdx -> viewModel.cycleBoxArtGlowStrength(-1)
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

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.controls.vibrationSupported && state.focusedIndex == 1) {
            viewModel.adjustVibrationStrength(-0.1f)
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
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
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
                        LibretroSettingDef.Filter -> { viewModel.cycleBuiltinFilter(-1); return InputResult.HANDLED }
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
                    accessor.cycle(setting, -1)
                    return InputResult.HANDLED
                }
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

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
        if (state.emulators.showEmulatorPicker) return InputResult.HANDLED

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
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

        if (state.currentSection == SettingsSection.BOX_ART) {
            val borderStyle = state.display.boxArtBorderStyle
            val showGlassTint = borderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GLASS
            val showGradient = borderStyle == com.nendo.argosy.data.preferences.BoxArtBorderStyle.GRADIENT
            val showAdvancedMode = state.display.gradientAdvancedMode
            val showIconPadding = state.display.systemIconPosition != com.nendo.argosy.data.preferences.SystemIconPosition.OFF
            val showOuterThickness = state.display.boxArtOuterEffect != com.nendo.argosy.data.preferences.BoxArtOuterEffect.OFF
            val showGlowIntensity = state.display.boxArtOuterEffect == com.nendo.argosy.data.preferences.BoxArtOuterEffect.GLOW
            val showInnerThickness = state.display.boxArtInnerEffect != com.nendo.argosy.data.preferences.BoxArtInnerEffect.OFF
            var idx = 4
            val glassTintIdx = if (showGlassTint) idx++ else -1
            val gradientPresetIdx = if (showGradient) idx++ else -1
            val gradientAdvancedIdx = if (showGradient) idx++ else -1
            val sampleGridIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val sampleRadiusIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val minSatIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val minBrightIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val hueDistIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val satBoostIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val brightClampIdx = if (showGradient && showAdvancedMode) idx++ else -1
            val iconPosIdx = idx++
            val iconPadIdx = if (showIconPadding) idx++ else -1
            val outerEffectIdx = idx++
            val outerThicknessIdx = if (showOuterThickness) idx++ else -1
            val glowIntensityIdx = if (showGlowIntensity) idx++ else -1
            val innerEffectIdx = idx++
            val innerThicknessIdx = if (showInnerThickness) idx++ else -1
            when (state.focusedIndex) {
                0 -> viewModel.cycleBoxArtShape(1)
                1 -> viewModel.cycleBoxArtCornerRadius(1)
                2 -> viewModel.cycleBoxArtBorderThickness(1)
                3 -> viewModel.cycleBoxArtBorderStyle(1)
                glassTintIdx -> viewModel.cycleGlassBorderTint(1)
                gradientPresetIdx -> viewModel.cycleGradientPreset(1)
                gradientAdvancedIdx -> viewModel.toggleGradientAdvancedMode()
                sampleGridIdx -> viewModel.cycleGradientSampleGrid(1)
                sampleRadiusIdx -> viewModel.cycleGradientRadius(1)
                minSatIdx -> viewModel.cycleGradientMinSaturation(1)
                minBrightIdx -> viewModel.cycleGradientMinValue(1)
                hueDistIdx -> viewModel.cycleGradientHueDistance(1)
                satBoostIdx -> viewModel.cycleGradientSaturationBump(1)
                brightClampIdx -> viewModel.cycleGradientValueClamp(1)
                iconPosIdx -> viewModel.cycleSystemIconPosition(1)
                iconPadIdx -> viewModel.cycleSystemIconPadding(1)
                outerEffectIdx -> viewModel.cycleBoxArtOuterEffect(1)
                outerThicknessIdx -> viewModel.cycleBoxArtOuterEffectThickness(1)
                glowIntensityIdx -> viewModel.cycleBoxArtGlowStrength(1)
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

        if (state.currentSection == SettingsSection.CONTROLS && state.controls.hapticEnabled && state.controls.vibrationSupported && state.focusedIndex == 1) {
            viewModel.adjustVibrationStrength(0.1f)
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
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
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
                        LibretroSettingDef.Filter -> { viewModel.cycleBuiltinFilter(1); return InputResult.HANDLED }
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
                    accessor.cycle(setting, 1)
                    return InputResult.HANDLED
                }
            }
        }

        return InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (hasAlertDialogOpen(state)) return InputResult.UNHANDLED
        if (hasBuiltinControlsModalOpen(state)) return InputResult.UNHANDLED

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

        if (state.syncSettings.showPlatformFiltersModal) {
            viewModel.confirmPlatformFiltersModalSelection()
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

        if (state.currentSection == SettingsSection.INTERFACE) {
            val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
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
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config != null && config.hasInstalledEmulators && config.showSavePath) {
                val subFocus = state.emulators.platformSubFocusIndex
                if (subFocus == 1) {
                    viewModel.showSavePathModal(config)
                    return InputResult.HANDLED
                }
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
                            viewModel.cycleBuiltinFilter(1)
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
                    }
                } else {
                    val accessor = PlatformLibretroSettingsAccessor(
                        platformSettings = platformSettings,
                        globalState = videoState,
                        onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
                    )
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
            val platformIndex = getEmulatorsPlatformIndex(state.focusedIndex, state.emulators.canAutoAssign)
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config?.showSavePath == true && config.hasInstalledEmulators) {
                viewModel.showSavePathModal(config)
                return InputResult.HANDLED
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

    override fun onMenu(): InputResult = InputResult.UNHANDLED

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        when (state.currentSection) {
            SettingsSection.BOX_ART -> {
                viewModel.cycleBoxArtShape(-1)
                return InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                viewModel.jumpToStoragePrevSection()
                return InputResult.HANDLED
            }
            SettingsSection.INTERFACE -> {
                val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
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
            SettingsSection.BOX_ART -> {
                viewModel.cycleBoxArtShape(1)
                return InputResult.HANDLED
            }
            SettingsSection.STORAGE -> {
                viewModel.jumpToStorageNextSection()
                return InputResult.HANDLED
            }
            SettingsSection.INTERFACE -> {
                val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.sounds.enabled)
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
        return InputResult.UNHANDLED
    }

    override fun onNextTrigger(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.BOX_ART) {
            viewModel.cycleNextPreviewGame()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onLeftStickClick(): InputResult = InputResult.UNHANDLED

    override fun onRightStickClick(): InputResult = InputResult.UNHANDLED
}
