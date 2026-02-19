package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.sections.AboutItem
import com.nendo.argosy.ui.screens.settings.sections.BoxArtItem
import com.nendo.argosy.ui.screens.settings.sections.ControlsItem
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.aboutItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.boxArtItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.controlsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.createStorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.aboutMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.boxArtMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.controlsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.emulatorsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.homeScreenMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.permissionsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageMaxFocusIndex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun routeConfirm(vm: SettingsViewModel): InputResult {
    val state = vm._uiState.value
    return when (state.currentSection) {
        SettingsSection.MAIN -> {
            val item = mainSettingsItemAtFocusIndex(state.focusedIndex, state.emulators.builtinLibretroEnabled)
            when (item) {
                MainSettingsItem.DeviceSettings -> vm.viewModelScope.launch { vm._openDeviceSettingsEvent.emit(Unit) }
                MainSettingsItem.GameData -> vm.navigateToSection(SettingsSection.SERVER)
                MainSettingsItem.RetroAchievements -> vm.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS)
                MainSettingsItem.Storage -> vm.navigateToSection(SettingsSection.STORAGE)
                MainSettingsItem.Interface -> vm.navigateToSection(SettingsSection.INTERFACE)
                MainSettingsItem.Controls -> vm.navigateToSection(SettingsSection.CONTROLS)
                MainSettingsItem.Emulators -> vm.navigateToSection(SettingsSection.EMULATORS)
                MainSettingsItem.Bios -> vm.navigateToSection(SettingsSection.BIOS)
                MainSettingsItem.Permissions -> vm.navigateToSection(SettingsSection.PERMISSIONS)
                MainSettingsItem.About -> vm.navigateToSection(SettingsSection.ABOUT)
                null -> {}
            }
            InputResult.HANDLED
        }
        SettingsSection.SERVER -> {
            routeServerConfirm(vm, state)
        }
        SettingsSection.STEAM_SETTINGS -> {
            val refreshIndex = 1 + state.steam.installedLaunchers.size
            when {
                state.focusedIndex == 0 && !state.steam.hasStoragePermission -> {
                    vm.viewModelScope.launch { vm._requestStoragePermissionEvent.emit(Unit) }
                }
                state.focusedIndex == refreshIndex && !state.steam.isSyncing -> {
                    vm.refreshSteamMetadata()
                }
                state.focusedIndex > 0 && state.focusedIndex < refreshIndex && state.steam.hasStoragePermission && !state.steam.isSyncing -> {
                    vm.confirmLauncherAction()
                }
            }
            InputResult.HANDLED
        }
        SettingsSection.RETRO_ACHIEVEMENTS -> {
            val ra = state.retroAchievements
            if (ra.showLoginForm) {
                when (state.focusedIndex) {
                    0, 1 -> vm.raDelegate.setFocusField(state.focusedIndex)
                    2 -> vm.loginToRA()
                    3 -> vm.hideRALoginForm()
                }
            } else if (ra.isLoggedIn) {
                if (state.focusedIndex == 0) vm.logoutFromRA()
            } else {
                if (state.focusedIndex == 0) vm.showRALoginForm()
            }
            InputResult.HANDLED
        }
        SettingsSection.SYNC_SETTINGS -> {
            when (state.focusedIndex) {
                0 -> vm.showPlatformFiltersModal()
                1 -> vm.showSyncFiltersModal()
                2 -> { vm.toggleSyncScreenshots(); return InputResult.handled(SoundType.TOGGLE) }
                3 -> {
                    if (!state.syncSettings.isImageCacheMigrating) {
                        if (state.syncSettings.imageCacheActionIndex == 0) {
                            vm.openImageCachePicker()
                        } else {
                            vm.resetImageCacheToDefault()
                        }
                    }
                }
            }
            InputResult.HANDLED
        }
        SettingsSection.STORAGE -> routeStorageConfirm(vm, state)
        SettingsSection.INTERFACE -> routeInterfaceConfirm(vm, state)
        SettingsSection.HOME_SCREEN -> routeHomeScreenConfirm(vm, state)
        SettingsSection.BOX_ART -> routeBoxArtConfirm(vm, state)
        SettingsSection.CONTROLS -> routeControlsConfirm(vm, state)
        SettingsSection.EMULATORS -> routeEmulatorsConfirm(vm, state)
        SettingsSection.BIOS -> routeBiosConfirm(vm, state)
        SettingsSection.PERMISSIONS -> routePermissionsConfirm(vm, state)
        SettingsSection.ABOUT -> routeAboutConfirm(vm, state)
        SettingsSection.BUILTIN_VIDEO -> InputResult.HANDLED
        SettingsSection.BUILTIN_CONTROLS -> InputResult.HANDLED
        SettingsSection.SHADER_STACK -> InputResult.HANDLED
        SettingsSection.FRAME_PICKER -> routeFramePickerConfirm(vm, state)
        SettingsSection.CORE_MANAGEMENT -> {
            vm.selectCoreForPlatform()
            InputResult.HANDLED
        }
    }
}

private fun routeServerConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
        state.server.connectionStatus == ConnectionStatus.OFFLINE
    val isOnline = state.server.connectionStatus == ConnectionStatus.ONLINE
    if (state.server.rommConfiguring) {
        when (state.focusedIndex) {
            0, 1, 2 -> vm._uiState.update { it.copy(server = it.server.copy(rommFocusField = state.focusedIndex)) }
            3 -> vm.connectToRomm()
            4 -> vm.cancelRommConfig()
        }
    } else {
        val androidBaseIndex = when {
            isConnected && state.syncSettings.saveSyncEnabled -> 9
            isConnected -> 7
            else -> 1
        }
        val steamBaseIndex = androidBaseIndex + 1
        val launcherCount = state.steam.installedLaunchers.size
        val notInstalledCount = state.steam.notInstalledLaunchers.size
        val refreshIndex = steamBaseIndex + launcherCount
        when {
            state.focusedIndex == 0 -> vm.startRommConfig()
            state.focusedIndex == 1 && isConnected -> vm.navigateToSection(SettingsSection.SYNC_SETTINGS)
            state.focusedIndex == 2 && isConnected && isOnline -> vm.syncRomm()
            state.focusedIndex == 3 && isConnected -> {
                val hasPermission = state.controls.hasUsageStatsPermission
                if (!state.controls.accuratePlayTimeEnabled && !hasPermission) {
                    vm.openUsageStatsSettings()
                } else {
                    vm.setAccuratePlayTimeEnabled(!state.controls.accuratePlayTimeEnabled)
                }
                return InputResult.handled(SoundType.TOGGLE)
            }
            state.focusedIndex == 4 && isConnected -> {
                vm.toggleSaveSync()
                return InputResult.handled(SoundType.TOGGLE)
            }
            state.focusedIndex == 5 && isConnected && state.syncSettings.saveSyncEnabled -> vm.cycleSaveCacheLimit()
            state.focusedIndex == 6 && isConnected && state.syncSettings.saveSyncEnabled && isOnline -> vm.requestSyncSaves()
            state.focusedIndex == androidBaseIndex - 2 && isConnected -> vm.requestClearPathCache()
            state.focusedIndex == androidBaseIndex - 1 && isConnected -> vm.requestResetSaveCache()
            state.focusedIndex == androidBaseIndex -> vm.scanForAndroidGames()
            launcherCount == 0 && state.focusedIndex >= steamBaseIndex &&
                state.focusedIndex < steamBaseIndex + notInstalledCount -> {
                val installIndex = state.focusedIndex - steamBaseIndex
                val launcher = state.steam.notInstalledLaunchers.getOrNull(installIndex)
                if (launcher != null && state.steam.downloadingLauncherId == null) {
                    vm.installSteamLauncher(launcher.emulatorId)
                }
            }
            state.focusedIndex >= steamBaseIndex && state.focusedIndex < refreshIndex -> {
                if (state.steam.hasStoragePermission && !state.steam.isSyncing) {
                    vm.confirmLauncherAction()
                }
            }
            state.focusedIndex == refreshIndex && launcherCount > 0 && !state.steam.isSyncing -> {
                vm.refreshSteamMetadata()
            }
        }
    }
    return InputResult.HANDLED
}

private fun routeStorageConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val info = createStorageLayoutInfo(
        state.storage.platformConfigs, state.storage.platformsExpanded
    )
    when (val item = storageItemAtFocusIndex(state.focusedIndex, info)) {
        StorageItem.MaxDownloads -> vm.cycleMaxConcurrentDownloads()
        StorageItem.Threshold -> vm.cycleInstantDownloadThreshold()
        StorageItem.GlobalRomPath -> vm.openFolderPicker()
        StorageItem.ImageCache -> vm.openImageCachePicker()
        StorageItem.ValidateCache -> vm.validateImageCache()
        StorageItem.PlatformsExpand -> vm.togglePlatformsExpanded()
        is StorageItem.PlatformItem -> vm.openPlatformSettingsModal(item.config.platformId)
        StorageItem.PurgeAll -> vm.requestPurgeAll()
        else -> {}
    }
    return InputResult.HANDLED
}

private fun routeInterfaceConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
    when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
        InterfaceItem.Theme -> {
            val next = when (state.display.themeMode) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            vm.setThemeMode(next)
        }
        InterfaceItem.GridDensity -> {
            val next = when (state.display.gridDensity) {
                GridDensity.COMPACT -> GridDensity.NORMAL
                GridDensity.NORMAL -> GridDensity.SPACIOUS
                GridDensity.SPACIOUS -> GridDensity.COMPACT
            }
            vm.setGridDensity(next)
        }
        InterfaceItem.UiScale -> vm.cycleUiScale()
        InterfaceItem.BoxArt -> vm.navigateToBoxArt()
        InterfaceItem.HomeScreen -> vm.navigateToHomeScreen()
        InterfaceItem.DisplayRoles -> vm.cycleDisplayRoleOverride()
        InterfaceItem.ScreenDimmer -> vm.toggleScreenDimmer()
        InterfaceItem.DimAfter -> vm.cycleScreenDimmerTimeout()
        InterfaceItem.DimLevel -> vm.cycleScreenDimmerLevel()
        InterfaceItem.AmbientLed -> vm.setAmbientLedEnabled(!state.display.ambientLedEnabled)
        InterfaceItem.AmbientLedBrightness -> vm.cycleAmbientLedBrightness()
        InterfaceItem.AmbientLedAudioBrightness -> vm.setAmbientLedAudioBrightness(!state.display.ambientLedAudioBrightness)
        InterfaceItem.AmbientLedAudioColors -> vm.setAmbientLedAudioColors(!state.display.ambientLedAudioColors)
        InterfaceItem.AmbientLedColorMode -> vm.cycleAmbientLedColorMode()
        InterfaceItem.BgmToggle -> {
            val newEnabled = !state.ambientAudio.enabled
            vm.setAmbientAudioEnabled(newEnabled)
            return InputResult.handled(if (newEnabled) SoundType.TOGGLE else SoundType.SILENT)
        }
        InterfaceItem.BgmVolume -> vm.cycleAmbientAudioVolume()
        InterfaceItem.BgmFile -> vm.openAudioFileBrowser()
        InterfaceItem.BgmShuffle -> {
            vm.setAmbientAudioShuffle(!state.ambientAudio.shuffle)
            return InputResult.handled(SoundType.TOGGLE)
        }
        InterfaceItem.UiSoundsToggle -> {
            val newEnabled = !state.sounds.enabled
            vm.setSoundEnabled(newEnabled)
            if (newEnabled) {
                vm.soundManager.setEnabled(true)
                vm.soundManager.play(SoundType.TOGGLE)
            }
            return InputResult.handled(SoundType.SILENT)
        }
        InterfaceItem.UiSoundsVolume -> vm.cycleSoundVolume()
        is InterfaceItem.SoundTypeItem -> {
            val soundItem = interfaceItemAtFocusIndex(state.focusedIndex, layoutState) as InterfaceItem.SoundTypeItem
            vm.showSoundPicker(soundItem.soundType)
        }
        else -> {}
    }
    return InputResult.HANDLED
}

private fun routeHomeScreenConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    when (homeScreenItemAtFocusIndex(state.focusedIndex, state.display)) {
        HomeScreenItem.GameArtwork -> {
            vm.setUseGameBackground(!state.display.useGameBackground)
            return InputResult.handled(SoundType.TOGGLE)
        }
        HomeScreenItem.CustomImage -> vm.openBackgroundPicker()
        HomeScreenItem.Blur -> vm.cycleBackgroundBlur()
        HomeScreenItem.Saturation -> vm.cycleBackgroundSaturation()
        HomeScreenItem.Opacity -> vm.cycleBackgroundOpacity()
        HomeScreenItem.VideoWallpaper -> {
            vm.setVideoWallpaperEnabled(!state.display.videoWallpaperEnabled)
            return InputResult.handled(SoundType.TOGGLE)
        }
        HomeScreenItem.VideoDelay -> vm.cycleVideoWallpaperDelay()
        HomeScreenItem.VideoMuted -> {
            vm.setVideoWallpaperMuted(!state.display.videoWallpaperMuted)
            return InputResult.handled(SoundType.TOGGLE)
        }
        HomeScreenItem.AccentFooter -> {
            vm.setUseAccentColorFooter(!state.display.useAccentColorFooter)
            return InputResult.handled(SoundType.TOGGLE)
        }
        else -> {}
    }
    return InputResult.HANDLED
}

private fun routeBoxArtConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    when (boxArtItemAtFocusIndex(state.focusedIndex, state.display)) {
        BoxArtItem.Shape -> vm.cycleBoxArtShape()
        BoxArtItem.CornerRadius -> vm.cycleBoxArtCornerRadius()
        BoxArtItem.BorderThickness -> vm.cycleBoxArtBorderThickness()
        BoxArtItem.BorderStyle -> vm.cycleBoxArtBorderStyle()
        BoxArtItem.GlassTint -> vm.cycleGlassBorderTint()
        BoxArtItem.GradientPresetItem -> vm.cycleGradientPreset()
        BoxArtItem.GradientAdvanced -> vm.toggleGradientAdvancedMode()
        BoxArtItem.SampleGrid -> vm.cycleGradientSampleGrid(1)
        BoxArtItem.SampleRadius -> vm.cycleGradientRadius(1)
        BoxArtItem.MinSaturation -> vm.cycleGradientMinSaturation(1)
        BoxArtItem.MinBrightness -> vm.cycleGradientMinValue(1)
        BoxArtItem.HueDistance -> vm.cycleGradientHueDistance(1)
        BoxArtItem.SaturationBoost -> vm.cycleGradientSaturationBump(1)
        BoxArtItem.BrightnessClamp -> vm.cycleGradientValueClamp(1)
        BoxArtItem.IconPos -> vm.cycleSystemIconPosition()
        BoxArtItem.IconPad -> vm.cycleSystemIconPadding()
        BoxArtItem.OuterEffect -> vm.cycleBoxArtOuterEffect()
        BoxArtItem.OuterThickness -> vm.cycleBoxArtOuterEffectThickness()
        BoxArtItem.GlowIntensity -> vm.cycleBoxArtGlowStrength()
        BoxArtItem.GlowColor -> vm.cycleGlowColorMode()
        BoxArtItem.InnerEffect -> vm.cycleBoxArtInnerEffect()
        BoxArtItem.InnerThickness -> vm.cycleBoxArtInnerEffectThickness()
        else -> {}
    }
    return InputResult.HANDLED
}

private fun routeControlsConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    when (controlsItemAtFocusIndex(state.focusedIndex, state.controls)) {
        ControlsItem.HapticFeedback -> {
            val newEnabled = !state.controls.hapticEnabled
            vm.setHapticEnabled(newEnabled)
            return InputResult.handled(if (newEnabled) SoundType.TOGGLE else SoundType.SILENT)
        }
        ControlsItem.VibrationStrength -> vm.cycleVibrationStrength()
        ControlsItem.ControllerLayout -> vm.cycleControllerLayout()
        ControlsItem.SwapAB -> { vm.setSwapAB(!state.controls.swapAB); return InputResult.handled(SoundType.TOGGLE) }
        ControlsItem.SwapXY -> { vm.setSwapXY(!state.controls.swapXY); return InputResult.handled(SoundType.TOGGLE) }
        ControlsItem.SwapStartSelect -> { vm.setSwapStartSelect(!state.controls.swapStartSelect); return InputResult.handled(SoundType.TOGGLE) }
        ControlsItem.InputFocus -> { vm.cycleDualScreenInputFocus(); return InputResult.HANDLED }
        null -> {}
    }
    return InputResult.HANDLED
}

private fun routeEmulatorsConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val builtinEnabled = state.emulators.builtinLibretroEnabled
    val builtinItemCount = if (builtinEnabled) 4 else 1
    val autoAssignIndex = if (state.emulators.canAutoAssign) builtinItemCount else -1
    val platformStartIndex = builtinItemCount + (if (state.emulators.canAutoAssign) 1 else 0)

    when {
        builtinEnabled && state.focusedIndex == 0 -> vm.navigateToBuiltinVideo()
        builtinEnabled && state.focusedIndex == 1 -> vm.navigateToBuiltinControls()
        builtinEnabled && state.focusedIndex == 2 -> vm.navigateToCoreManagement()
        state.focusedIndex == autoAssignIndex -> vm.autoAssignAllEmulators()
        state.focusedIndex >= platformStartIndex -> {
            val platformIndex = state.focusedIndex - platformStartIndex
            val config = state.emulators.platforms.getOrNull(platformIndex)
            if (config != null) {
                vm.showEmulatorPicker(config)
            }
        }
    }
    return InputResult.HANDLED
}

private fun routeBiosConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    when (state.focusedIndex) {
        0 -> {
            val actionIndex = state.bios.actionIndex
            if (actionIndex == 0 && state.bios.missingFiles > 0) {
                vm.downloadAllBios()
            } else if (actionIndex == 1 && state.bios.downloadedFiles > 0) {
                vm.distributeAllBios()
            }
        }
        1 -> {
            if (!state.bios.isBiosMigrating) {
                if (state.bios.biosPathActionIndex == 0) {
                    vm.openBiosFolderPicker()
                } else {
                    vm.resetBiosToDefault()
                }
            }
        }
        else -> {
            val bios = state.bios
            val focusMapping = vm.buildBiosFocusMapping(bios.platformGroups, bios.expandedPlatformIndex)
            val (platformIndex, isChildItem) = focusMapping.getPlatformAndChildInfo(state.focusedIndex)

            if (platformIndex >= 0 && platformIndex < bios.platformGroups.size) {
                val group = bios.platformGroups[platformIndex]
                if (isChildItem) {
                    val childIndex = focusMapping.getChildIndex(state.focusedIndex, platformIndex)
                    val firmware = group.firmwareItems.getOrNull(childIndex)
                    if (firmware != null && !firmware.isDownloaded) {
                        vm.downloadSingleBios(firmware.rommId)
                    }
                } else {
                    if (bios.platformSubFocusIndex == 1 && !group.isComplete) {
                        vm.downloadBiosForPlatform(group.platformSlug)
                    } else {
                        vm.toggleBiosPlatformExpanded(platformIndex)
                    }
                }
            }
        }
    }
    return InputResult.HANDLED
}

private fun routePermissionsConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val perms = state.permissions
    val baseIndex = 3
    val writeSettingsIndex = if (perms.isWriteSettingsRelevant) baseIndex else -1
    val screenCaptureIndex = if (perms.isScreenCaptureRelevant) {
        if (perms.isWriteSettingsRelevant) baseIndex + 1 else baseIndex
    } else -1
    val displayOverlayIndex = baseIndex +
        (if (perms.isWriteSettingsRelevant) 1 else 0) +
        (if (perms.isScreenCaptureRelevant) 1 else 0)

    when (state.focusedIndex) {
        0 -> vm.openStorageSettings()
        1 -> vm.openUsageStatsSettings()
        2 -> vm.openNotificationSettings()
        writeSettingsIndex -> vm.openWriteSettings()
        screenCaptureIndex -> vm.requestScreenCapturePermission()
        displayOverlayIndex -> vm.openDisplayOverlaySettings()
    }
    return InputResult.HANDLED
}

private fun routeAboutConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val hasLogPath = state.fileLoggingPath != null
    when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath)) {
        AboutItem.CheckUpdates -> {
            if (state.updateCheck.updateAvailable) {
                vm.viewModelScope.launch { vm._downloadUpdateEvent.emit(Unit) }
            } else {
                vm.checkForUpdates()
            }
        }
        AboutItem.BetaUpdates -> {
            vm.setBetaUpdatesEnabled(!state.betaUpdatesEnabled)
            return InputResult.handled(SoundType.TOGGLE)
        }
        AboutItem.FileLogging -> {
            if (hasLogPath) {
                vm.toggleFileLogging(!state.fileLoggingEnabled)
            } else {
                vm.openLogFolderPicker()
            }
            return InputResult.handled(SoundType.TOGGLE)
        }
        AboutItem.LogLevel -> vm.cycleFileLogLevel()
        AboutItem.SaveDebugLogging -> {
            vm.setSaveDebugLoggingEnabled(!state.saveDebugLoggingEnabled)
            return InputResult.handled(SoundType.TOGGLE)
        }
        AboutItem.AppAffinity -> {
            vm.setAppAffinityEnabled(!state.appAffinityEnabled)
            return InputResult.handled(SoundType.TOGGLE)
        }
        else -> {}
    }
    return InputResult.HANDLED
}

private fun routeFramePickerConfirm(vm: SettingsViewModel, state: SettingsUiState): InputResult {
    val registry = vm.getFrameRegistry()
    val allFrames = registry.getAllFrames()
    val installedIds = registry.getInstalledIds()
    when (state.focusedIndex) {
        0 -> vm.updatePlatformLibretroSetting(LibretroSettingDef.Frame, null)
        1 -> vm.updatePlatformLibretroSetting(LibretroSettingDef.Frame, "none")
        else -> {
            val frameIndex = state.focusedIndex - 2
            if (frameIndex in allFrames.indices) {
                val frame = allFrames[frameIndex]
                if (frame.id in installedIds) {
                    vm.updatePlatformLibretroSetting(LibretroSettingDef.Frame, frame.id)
                } else {
                    vm.downloadAndSelectFrame(frame.id)
                }
            }
        }
    }
    return InputResult.HANDLED
}

internal fun routeNavigateBack(vm: SettingsViewModel): Boolean {
    val state = vm._uiState.value
    return when {
        state.emulators.showSavePathModal -> { vm.dismissSavePathModal(); true }
        state.storage.platformSettingsModalId != null -> { vm.closePlatformSettingsModal(); true }
        state.steam.showAddGameDialog -> { vm.dismissAddSteamGameDialog(); true }
        state.sounds.showSoundPicker -> { vm.dismissSoundPicker(); true }
        state.syncSettings.showRegionPicker -> { vm.dismissRegionPicker(); true }
        state.syncSettings.showPlatformFiltersModal -> { vm.dismissPlatformFiltersModal(); true }
        state.syncSettings.showSyncFiltersModal -> { vm.dismissSyncFiltersModal(); true }
        state.syncSettings.showForceSyncConfirm -> { vm.cancelSyncSaves(); true }
        state.emulators.showEmulatorPicker -> { vm.dismissEmulatorPicker(); true }
        state.bios.showDistributeResultModal -> { vm.dismissDistributeResultModal(); true }
        state.builtinControls.showControllerOrderModal -> { vm.hideControllerOrderModal(); true }
        state.builtinControls.showInputMappingModal -> { vm.hideInputMappingModal(); true }
        state.builtinControls.showHotkeysModal -> { vm.hideHotkeysModal(); true }
        state.server.rommConfiguring -> { vm.cancelRommConfig(); true }
        state.currentSection == SettingsSection.SYNC_SETTINGS -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = 1) }; true
        }
        state.currentSection == SettingsSection.STEAM_SETTINGS -> {
            val steamIndex = if (vm._uiState.value.syncSettings.saveSyncEnabled) 9 else 7
            vm._uiState.update { it.copy(currentSection = SettingsSection.SERVER, focusedIndex = steamIndex) }; true
        }
        state.retroAchievements.showLoginForm -> { vm.hideRALoginForm(); true }
        state.currentSection == SettingsSection.RETRO_ACHIEVEMENTS -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = state.parentFocusIndex) }; true
        }
        state.currentSection == SettingsSection.BOX_ART -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.INTERFACE, focusedIndex = 5) }; true
        }
        state.currentSection == SettingsSection.HOME_SCREEN -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.INTERFACE, focusedIndex = 6) }; true
        }
        state.currentSection == SettingsSection.SHADER_STACK -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.BUILTIN_VIDEO, focusedIndex = 1) }; true
        }
        state.currentSection == SettingsSection.FRAME_PICKER -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.BUILTIN_VIDEO, focusedIndex = 2) }; true
        }
        state.currentSection == SettingsSection.BUILTIN_VIDEO -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 0) }; true
        }
        state.currentSection == SettingsSection.BUILTIN_CONTROLS -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 1) }; true
        }
        state.currentSection == SettingsSection.CORE_MANAGEMENT -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.EMULATORS, focusedIndex = 2) }; true
        }
        state.currentSection != SettingsSection.MAIN -> {
            vm._uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = state.parentFocusIndex) }; true
        }
        else -> false
    }
}

internal fun routeMoveFocus(vm: SettingsViewModel, delta: Int) {
    if (vm._uiState.value.emulators.showSavePathModal) {
        vm.emulatorDelegate.moveSavePathModalFocus(delta); return
    }
    if (vm._uiState.value.storage.platformSettingsModalId != null) {
        vm.storageDelegate.movePlatformSettingsFocus(delta); return
    }
    if (vm._uiState.value.sounds.showSoundPicker) {
        vm.soundsDelegate.moveSoundPickerFocus(delta); return
    }
    if (vm._uiState.value.syncSettings.showRegionPicker) {
        vm.syncDelegate.moveRegionPickerFocus(delta); return
    }
    if (vm._uiState.value.emulators.showEmulatorPicker) {
        vm.emulatorDelegate.moveEmulatorPickerFocus(delta); return
    }
    if (vm._uiState.value.currentSection == SettingsSection.CORE_MANAGEMENT) {
        vm.moveCoreManagementPlatformFocus(delta); return
    }
    vm._uiState.update { state ->
        val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
            state.server.connectionStatus == ConnectionStatus.OFFLINE
        val maxIndex = computeMaxFocusIndex(vm, state, isConnected)
        val newIndex = if (state.currentSection == SettingsSection.SERVER && state.server.rommConfiguring) {
            when {
                delta > 0 && state.focusedIndex == 0 -> 1
                delta > 0 && (state.focusedIndex == 1 || state.focusedIndex == 2) -> 3
                delta < 0 && state.focusedIndex == 3 -> 1
                delta < 0 && (state.focusedIndex == 1 || state.focusedIndex == 2) -> 0
                else -> (state.focusedIndex + delta).coerceIn(0, maxIndex)
            }
        } else {
            (state.focusedIndex + delta).coerceIn(0, maxIndex)
        }
        state.copy(focusedIndex = newIndex)
    }
    if (vm._uiState.value.currentSection == SettingsSection.EMULATORS) {
        vm.emulatorDelegate.resetPlatformSubFocus()
    }
    if (vm._uiState.value.currentSection == SettingsSection.BIOS) {
        vm.biosDelegate.resetPlatformSubFocus()
        vm.biosDelegate.resetBiosPathActionFocus()
    }
}

private fun computeMaxFocusIndex(
    vm: SettingsViewModel,
    state: SettingsUiState,
    isConnected: Boolean
): Int = when (state.currentSection) {
    SettingsSection.MAIN -> mainSettingsMaxFocusIndex(state.emulators.builtinLibretroEnabled)
    SettingsSection.SERVER -> if (state.server.rommConfiguring) {
        4
    } else {
        val steamBaseIndex = when {
            isConnected && state.syncSettings.saveSyncEnabled -> 10
            isConnected -> 8
            else -> 2
        }
        val launcherCount = state.steam.installedLaunchers.size
        val notInstalledCount = state.steam.notInstalledLaunchers.size
        if (launcherCount > 0) steamBaseIndex + launcherCount
        else (steamBaseIndex + notInstalledCount - 1).coerceAtLeast(steamBaseIndex)
    }
    SettingsSection.SYNC_SETTINGS -> 3
    SettingsSection.STEAM_SETTINGS -> 1 + state.steam.installedLaunchers.size
    SettingsSection.RETRO_ACHIEVEMENTS -> if (state.retroAchievements.showLoginForm) 3 else 0
    SettingsSection.STORAGE -> storageMaxFocusIndex(
        state.storage.platformsExpanded,
        state.storage.platformConfigs.size
    )
    SettingsSection.INTERFACE -> interfaceMaxFocusIndex(
        InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay)
    )
    SettingsSection.HOME_SCREEN -> homeScreenMaxFocusIndex(state.display)
    SettingsSection.BOX_ART -> boxArtMaxFocusIndex(state.display)
    SettingsSection.CONTROLS -> controlsMaxFocusIndex(state.controls)
    SettingsSection.EMULATORS -> emulatorsMaxFocusIndex(
        state.emulators.canAutoAssign,
        state.emulators.platforms.size,
        state.emulators.builtinLibretroEnabled
    )
    SettingsSection.BUILTIN_VIDEO -> builtinVideoMaxFocusIndex(state.builtinVideo, state.platformLibretro.platformSettings)
    SettingsSection.BUILTIN_CONTROLS -> builtinControlsMaxFocusIndex(
        state.builtinControls,
        state.builtinVideo,
        state.platformLibretro.platformSettings
    )
    SettingsSection.CORE_MANAGEMENT -> (state.coreManagement.platforms.size - 1).coerceAtLeast(0)
    SettingsSection.SHADER_STACK -> com.nendo.argosy.ui.screens.settings.sections.shaderStackMaxFocusIndex(vm.shaderChainManager.shaderStack)
    SettingsSection.FRAME_PICKER -> com.nendo.argosy.ui.screens.settings.sections.framePickerMaxFocusIndex(vm.getFrameRegistry())
    SettingsSection.BIOS -> {
        val bios = state.bios
        val platformCount = bios.platformGroups.size
        val expandedItems = if (bios.expandedPlatformIndex >= 0) {
            bios.platformGroups.getOrNull(bios.expandedPlatformIndex)?.firmwareItems?.size ?: 0
        } else 0
        (1 + platformCount + expandedItems).coerceAtLeast(1)
    }
    SettingsSection.PERMISSIONS -> permissionsMaxFocusIndex(state.permissions)
    SettingsSection.ABOUT -> aboutMaxFocusIndex(state.fileLoggingPath != null)
}
