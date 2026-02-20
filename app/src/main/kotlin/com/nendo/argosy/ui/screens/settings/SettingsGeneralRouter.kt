package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.domain.usecase.sync.SyncLibraryResult
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.settings.sections.GameDataItem
import com.nendo.argosy.ui.screens.settings.sections.buildGameDataItemsFromState
import com.nendo.argosy.ui.screens.settings.sections.gameDataItemAtFocusIndex
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Navigation ---

internal fun routeNavigateToSection(vm: SettingsViewModel, section: SettingsSection) {
    val currentState = vm._uiState.value
    val parentIndex = if (currentState.currentSection == SettingsSection.MAIN) {
        currentState.focusedIndex
    } else {
        currentState.parentFocusIndex
    }
    vm._uiState.update { it.copy(currentSection = section, focusedIndex = 0, parentFocusIndex = parentIndex) }
    when (section) {
        SettingsSection.EMULATORS -> vm.refreshEmulators()
        SettingsSection.SERVER -> {
            vm.serverDelegate.checkRommConnection(vm.viewModelScope)
            vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
        }
        SettingsSection.SYNC_SETTINGS -> vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
        SettingsSection.STEAM_SETTINGS -> vm.steamDelegate.loadSteamSettings(vm.context, vm.viewModelScope)
        SettingsSection.PERMISSIONS -> vm.permissionsDelegate.refreshPermissions()
        SettingsSection.SHADER_STACK -> vm.shaderChainManager.loadChain(vm._uiState.value.builtinVideo.shaderChainJson)
        else -> {}
    }
}

internal fun routeNavigateToBoxArt(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(currentSection = SettingsSection.BOX_ART, focusedIndex = 0) }
    vm.loadPreviewGames()
}

internal fun routeNavigateToHomeScreen(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(currentSection = SettingsSection.HOME_SCREEN, focusedIndex = 0) }
}

// --- Emulator methods ---

internal fun routeShowEmulatorPicker(vm: SettingsViewModel, config: PlatformEmulatorConfig) {
    if (config.availableEmulators.isEmpty() && config.downloadableEmulators.isEmpty()) return
    vm.emulatorDelegate.showEmulatorPicker(config, vm.viewModelScope)
}

internal fun routeHandleVariantPickerItemTap(vm: SettingsViewModel, index: Int) {
    vm._uiState.update { state ->
        state.copy(emulators = state.emulators.copy(variantPickerFocusIndex = index))
    }
}

internal fun routeDownloadCore(vm: SettingsViewModel, coreId: String) {
    vm.viewModelScope.launch {
        vm.coreManager.downloadCoreById(coreId)
        vm.emulatorDelegate.updateCoreCounts()
    }
}

internal fun routeDeleteCore(vm: SettingsViewModel, coreId: String) {
    vm.viewModelScope.launch {
        vm.coreManager.deleteCore(coreId)
        vm.emulatorDelegate.updateCoreCounts()
    }
}

internal fun routeCycleExtensionForPlatform(vm: SettingsViewModel, config: PlatformEmulatorConfig, direction: Int) {
    val options = config.extensionOptions
    if (options.isEmpty()) return

    val currentExtension = config.selectedExtension.orEmpty()
    val currentIndex = options.indexOfFirst { it.extension == currentExtension }.coerceAtLeast(0)
    val newIndex = (currentIndex + direction).coerceIn(0, options.size - 1)
    if (newIndex == currentIndex) return

    val newExtension = options[newIndex].extension

    val updatedPlatforms = vm._uiState.value.emulators.platforms.map {
        if (it.platform.id == config.platform.id) it.copy(selectedExtension = newExtension.ifEmpty { null })
        else it
    }
    vm._uiState.update { it.copy(emulators = it.emulators.copy(platforms = updatedPlatforms)) }

    vm.viewModelScope.launch {
        vm.configureEmulatorUseCase.setExtensionForPlatform(config.platform.id, newExtension.ifEmpty { null })
    }
}

internal fun routeConfirmEmulatorPickerSelection(vm: SettingsViewModel) {
    vm.emulatorDelegate.confirmEmulatorPickerSelection(
        vm.viewModelScope,
        onSetEmulator = { platformId, platformSlug, emulator -> vm.setPlatformEmulator(platformId, platformSlug, emulator) },
        onLoadSettings = { vm.loadSettings() }
    )
}

internal fun routeHandleEmulatorPickerItemTap(vm: SettingsViewModel, index: Int) {
    vm.emulatorDelegate.handleEmulatorPickerItemTap(
        index,
        vm.viewModelScope,
        onSetEmulator = { platformId, platformSlug, emulator -> vm.setPlatformEmulator(platformId, platformSlug, emulator) },
        onLoadSettings = { vm.loadSettings() }
    )
}

internal fun routeShowSavePathModal(vm: SettingsViewModel, config: PlatformEmulatorConfig) {
    val installedEmulator = config.availableEmulators
        .find { it.def.displayName == config.selectedEmulator || it.def.displayName == config.effectiveEmulatorName }
        ?: return
    val emulatorId = SavePathRegistry.resolveConfigIdForPackage(installedEmulator.def.packageName)
        ?: installedEmulator.def.id
    vm.emulatorDelegate.showSavePathModal(
        emulatorId = emulatorId,
        emulatorName = config.effectiveEmulatorName ?: config.selectedEmulator ?: "Unknown",
        platformName = config.platform.name,
        savePath = config.effectiveSavePath,
        isUserOverride = config.isUserSavePathOverride
    )
}

internal fun routeConfirmSavePathModalSelection(vm: SettingsViewModel) {
    val emulatorId = vm._uiState.value.emulators.savePathModalInfo?.emulatorId ?: return
    vm.emulatorDelegate.confirmSavePathModalSelection(vm.viewModelScope) {
        vm.resetEmulatorSavePath(emulatorId)
    }
}

internal fun routeHandlePlatformItemTap(vm: SettingsViewModel, index: Int) {
    val state = vm._uiState.value
    val focusOffset = if (state.emulators.canAutoAssign) 1 else 0
    val actualIndex = index + focusOffset

    if (state.focusedIndex == actualIndex) {
        if (index == -1 && state.emulators.canAutoAssign) {
            vm.autoAssignAllEmulators()
        } else {
            val config = state.emulators.platforms.getOrNull(index)
            if (config != null) {
                vm.showEmulatorPicker(config)
            }
        }
    } else {
        vm._uiState.update { it.copy(focusedIndex = actualIndex) }
    }
}

internal fun routeForceCheckEmulatorUpdates(vm: SettingsViewModel) {
    Log.d("SettingsViewModel", "forceCheckEmulatorUpdates called")
    vm.emulatorDelegate.forceCheckEmulatorUpdates()
}

internal fun routeSetPlatformEmulator(vm: SettingsViewModel, platformId: Long, platformSlug: String, emulator: InstalledEmulator?) {
    vm.viewModelScope.launch {
        vm.configureEmulatorUseCase.setForPlatform(platformId, platformSlug, emulator)
        vm.loadSettings()
    }
}

// --- Display & Theme ---

internal fun routeCycleThemeMode(vm: SettingsViewModel, direction: Int) {
    val modes = com.nendo.argosy.data.preferences.ThemeMode.entries
    val current = vm.uiState.value.display.themeMode
    val currentIndex = modes.indexOf(current)
    val nextIndex = (currentIndex + direction).mod(modes.size)
    vm.setThemeMode(modes[nextIndex])
}

internal fun routeCycleGridDensity(vm: SettingsViewModel, direction: Int) {
    val densities = GridDensity.entries
    val current = vm.uiState.value.display.gridDensity
    val currentIndex = densities.indexOf(current)
    val nextIndex = (currentIndex + direction).mod(densities.size)
    vm.setGridDensity(densities[nextIndex])
}

internal fun routeAdjustUiScale(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.display.uiScale
    val wouldBe = (current + delta).coerceIn(75, 150)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustUiScale(vm.viewModelScope, delta)
}

internal fun routeAdjustBackgroundBlur(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.display.backgroundBlur
    val wouldBe = (current + delta).coerceIn(0, 100)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustBackgroundBlur(vm.viewModelScope, delta)
}

internal fun routeAdjustBackgroundSaturation(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.display.backgroundSaturation
    val wouldBe = (current + delta).coerceIn(0, 100)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustBackgroundSaturation(vm.viewModelScope, delta)
}

internal fun routeAdjustBackgroundOpacity(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.display.backgroundOpacity
    val wouldBe = (current + delta).coerceIn(0, 100)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustBackgroundOpacity(vm.viewModelScope, delta)
}

internal fun routeCycleBackgroundBlur(vm: SettingsViewModel) {
    val current = vm._uiState.value.display.backgroundBlur
    val next = if (current >= 100) 0 else current + 10
    vm.displayDelegate.adjustBackgroundBlur(vm.viewModelScope, next - current)
}

internal fun routeCycleBackgroundSaturation(vm: SettingsViewModel) {
    val current = vm._uiState.value.display.backgroundSaturation
    val next = if (current >= 100) 0 else current + 10
    vm.displayDelegate.adjustBackgroundSaturation(vm.viewModelScope, next - current)
}

internal fun routeCycleBackgroundOpacity(vm: SettingsViewModel) {
    val current = vm._uiState.value.display.backgroundOpacity
    val next = if (current >= 100) 0 else current + 10
    vm.displayDelegate.adjustBackgroundOpacity(vm.viewModelScope, next - current)
}

internal fun routeMoveColorFocus(vm: SettingsViewModel, delta: Int) {
    vm.displayDelegate.moveColorFocus(delta)
    vm._uiState.update { it.copy(colorFocusIndex = vm.displayDelegate.colorFocusIndex) }
}

internal fun routeCycleGradientPreset(vm: SettingsViewModel, direction: Int) {
    val current = vm._uiState.value.display.gradientPreset
    val next = when (current) {
        GradientPreset.VIBRANT -> if (direction > 0) GradientPreset.BALANCED else GradientPreset.SUBTLE
        GradientPreset.BALANCED -> if (direction > 0) GradientPreset.SUBTLE else GradientPreset.VIBRANT
        GradientPreset.SUBTLE -> if (direction > 0) GradientPreset.VIBRANT else GradientPreset.BALANCED
        GradientPreset.CUSTOM -> GradientPreset.BALANCED
    }
    vm._uiState.update { it.copy(gradientConfig = next.toConfig()) }
    vm.displayDelegate.setGradientPreset(vm.viewModelScope, next)
    vm.extractGradientForPreview()
}

internal fun routeToggleGradientAdvancedMode(vm: SettingsViewModel) {
    vm.displayDelegate.toggleGradientAdvancedMode(vm.viewModelScope)
    vm.extractGradientForPreview()
}

internal fun routeCycleDisplayRoleOverride(vm: SettingsViewModel, direction: Int) {
    val entries = com.nendo.argosy.data.preferences.DisplayRoleOverride.entries
    val current = vm._uiState.value.display.displayRoleOverride
    val next = entries[(current.ordinal + direction + entries.size) % entries.size]
    vm.viewModelScope.launch {
        vm.preferencesRepository.setDisplayRoleOverride(next)
        val sessionStore = com.nendo.argosy.data.preferences.SessionStateStore(vm.context)
        sessionStore.setDisplayRoleOverride(next.name)
        vm.displayDelegate.updateState(vm._uiState.value.display.copy(displayRoleOverride = next))
    }
}

internal fun routeCycleDualScreenInputFocus(vm: SettingsViewModel, direction: Int) {
    val entries = com.nendo.argosy.data.preferences.DualScreenInputFocus.entries
    val current = vm._uiState.value.controls.dualScreenInputFocus
    val next = entries[(current.ordinal + direction + entries.size) % entries.size]
    vm.viewModelScope.launch {
        vm.preferencesRepository.setDualScreenInputFocus(next)
        val sessionStore = com.nendo.argosy.data.preferences.SessionStateStore(vm.context)
        sessionStore.setDualScreenInputFocus(next.name)
        vm.controlsDelegate.updateState(vm._uiState.value.controls.copy(dualScreenInputFocus = next))
        vm.displayDelegate.updateState(vm._uiState.value.display.copy(dualScreenInputFocus = next))
    }
}

// --- Gradient cycle methods ---

private inline fun updateGradientConfig(vm: SettingsViewModel, update: GradientExtractionConfig.() -> GradientExtractionConfig) {
    vm._uiState.update { it.copy(gradientConfig = it.gradientConfig.update()) }
    vm.extractGradientForPreview()
}

internal fun routeCycleGradientSampleGrid(vm: SettingsViewModel, direction: Int) {
    val options = listOf(8 to 12, 10 to 15, 12 to 18, 16 to 24)
    val current = vm._uiState.value.gradientConfig.let { it.samplesX to it.samplesY }
    val currentIdx = options.indexOf(current).coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    val (x, y) = options[nextIdx]
    updateGradientConfig(vm) { copy(samplesX = x, samplesY = y) }
}

internal fun routeCycleGradientRadius(vm: SettingsViewModel, direction: Int) {
    val options = listOf(1, 2, 3, 4)
    val current = vm._uiState.value.gradientConfig.radius
    val currentIdx = options.indexOf(current).coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(radius = options[nextIdx]) }
}

internal fun routeCycleGradientMinSaturation(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0.20f, 0.25f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f)
    val current = vm._uiState.value.gradientConfig.minSaturation
    val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(minSaturation = options[nextIdx]) }
}

internal fun routeCycleGradientMinValue(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0.10f, 0.15f, 0.20f, 0.25f)
    val current = vm._uiState.value.gradientConfig.minValue
    val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(minValue = options[nextIdx]) }
}

internal fun routeCycleGradientHueDistance(vm: SettingsViewModel, direction: Int) {
    val options = listOf(20, 30, 40, 50, 60)
    val current = vm._uiState.value.gradientConfig.minHueDistance
    val currentIdx = options.indexOf(current).coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(minHueDistance = options[nextIdx]) }
}

internal fun routeCycleGradientSaturationBump(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.55f)
    val current = vm._uiState.value.gradientConfig.saturationBump
    val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(saturationBump = options[nextIdx]) }
}

internal fun routeCycleGradientValueClamp(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0.70f, 0.75f, 0.80f, 0.85f, 0.90f)
    val current = vm._uiState.value.gradientConfig.valueClamp
    val currentIdx = options.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }.coerceAtLeast(0)
    val nextIdx = (currentIdx + direction).mod(options.size)
    updateGradientConfig(vm) { copy(valueClamp = options[nextIdx]) }
}

// --- Controls & Haptic ---

internal fun routeAdjustVibrationStrength(vm: SettingsViewModel, delta: Float) {
    val current = vm.uiState.value.controls.vibrationStrength
    val wouldBe = (current + delta).coerceIn(0f, 1f)
    if (wouldBe == current && delta != 0f) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.controlsDelegate.adjustVibrationStrength(delta)
}

// --- Sound & Volume ---

internal fun routeAdjustSoundVolume(vm: SettingsViewModel, delta: Int) {
    val volumeLevels = listOf(50, 70, 85, 95, 100)
    val current = vm.uiState.value.sounds.volume
    val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
    val newIndex = (currentIndex + delta).coerceIn(0, volumeLevels.lastIndex)
    if (newIndex == currentIndex && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.soundsDelegate.adjustSoundVolume(vm.viewModelScope, delta)
}

internal fun routeCycleSoundVolume(vm: SettingsViewModel) {
    val volumeLevels = listOf(50, 70, 85, 95, 100)
    val current = vm.uiState.value.sounds.volume
    val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
    val nextIndex = (currentIndex + 1) % volumeLevels.size
    vm.soundsDelegate.setSoundVolume(vm.viewModelScope, volumeLevels[nextIndex])
}

internal fun routeAdjustAmbientAudioVolume(vm: SettingsViewModel, delta: Int) {
    val volumeLevels = listOf(2, 5, 10, 20, 35)
    val current = vm.uiState.value.ambientAudio.volume
    val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
    val newIndex = (currentIndex + delta).coerceIn(0, volumeLevels.lastIndex)
    if (newIndex == currentIndex && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.ambientAudioDelegate.adjustVolume(vm.viewModelScope, delta)
}

internal fun routeCycleAmbientAudioVolume(vm: SettingsViewModel) {
    val volumeLevels = listOf(2, 5, 10, 20, 35)
    val current = vm.uiState.value.ambientAudio.volume
    val currentIndex = volumeLevels.indexOfFirst { it >= current }.takeIf { it >= 0 } ?: 0
    val nextIndex = (currentIndex + 1) % volumeLevels.size
    vm.ambientAudioDelegate.setVolume(vm.viewModelScope, volumeLevels[nextIndex])
}

// --- Sync modal methods ---

internal fun routeShowSyncFiltersModal(vm: SettingsViewModel) {
    vm.syncDelegate.showSyncFiltersModal()
    vm.soundManager.play(SoundType.OPEN_MODAL)
}

internal fun routeDismissSyncFiltersModal(vm: SettingsViewModel) {
    vm.syncDelegate.dismissSyncFiltersModal()
    vm.soundManager.play(SoundType.CLOSE_MODAL)
}

internal fun routeShowPlatformFiltersModal(vm: SettingsViewModel) {
    vm.syncDelegate.showPlatformFiltersModal(vm.viewModelScope)
    vm.soundManager.play(SoundType.OPEN_MODAL)
}

internal fun routeDismissPlatformFiltersModal(vm: SettingsViewModel) {
    vm.syncDelegate.dismissPlatformFiltersModal()
    vm.soundManager.play(SoundType.CLOSE_MODAL)
}

internal fun routeShowRegionPicker(vm: SettingsViewModel) {
    vm.syncDelegate.showRegionPicker()
    vm.soundManager.play(SoundType.OPEN_MODAL)
}

internal fun routeDismissRegionPicker(vm: SettingsViewModel) {
    vm.syncDelegate.dismissRegionPicker()
    vm.soundManager.play(SoundType.CLOSE_MODAL)
}

internal fun routeToggleSyncScreenshots(vm: SettingsViewModel) {
    vm.syncDelegate.toggleSyncScreenshots(vm.viewModelScope, vm._uiState.value.server.syncScreenshotsEnabled)
}

internal fun routeOnStoragePermissionResult(vm: SettingsViewModel, granted: Boolean) {
    vm.syncDelegate.onStoragePermissionResult(vm.viewModelScope, granted, vm._uiState.value.currentSection)
    vm.steamDelegate.loadSteamSettings(vm.context, vm.viewModelScope)
}

// --- Storage adjustment methods ---

internal fun routeAdjustMaxConcurrentDownloads(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.storage.maxConcurrentDownloads
    val wouldBe = (current + delta).coerceIn(1, 5)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.storageDelegate.adjustMaxConcurrentDownloads(vm.viewModelScope, delta)
}

internal fun routeAdjustScreenDimmerTimeout(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.storage.screenDimmerTimeoutMinutes
    val wouldBe = (current + delta).coerceIn(1, 5)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.storageDelegate.adjustScreenDimmerTimeout(vm.viewModelScope, delta)
}

internal fun routeAdjustScreenDimmerLevel(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.storage.screenDimmerLevel
    val wouldBe = (current + delta * 10).coerceIn(40, 70)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.storageDelegate.adjustScreenDimmerLevel(vm.viewModelScope, delta)
}

// --- Platform save/state path ---

internal fun routeSetPlatformSavePath(vm: SettingsViewModel, platformId: Long, basePath: String) {
    val storageConfig = vm._uiState.value.storage.platformConfigs.find { it.platformId == platformId }
    val emulatorId = storageConfig?.emulatorId ?: return
    val evaluatedPath = routeComputeEvaluatedSavePath(vm, platformId, basePath)
    vm.emulatorDelegate.setEmulatorSavePath(vm.viewModelScope, emulatorId, basePath) {
        vm.storageDelegate.updatePlatformSavePath(platformId, evaluatedPath, true)
    }
}

internal fun routeResetPlatformSavePath(vm: SettingsViewModel, platformId: Long) {
    val storageConfig = vm._uiState.value.storage.platformConfigs.find { it.platformId == platformId }
    val emulatorId = storageConfig?.emulatorId ?: return
    vm.emulatorDelegate.resetEmulatorSavePath(vm.viewModelScope, emulatorId) {
        val defaultPath = routeComputeEvaluatedSavePath(vm, platformId, null)
        vm.storageDelegate.updatePlatformSavePath(platformId, defaultPath, false)
    }
}

internal fun routeSetPlatformStatePath(vm: SettingsViewModel, platformId: Long, basePath: String) {
    val evaluatedPath = routeComputeEvaluatedStatePath(vm, platformId, basePath)
    vm.storageDelegate.updatePlatformStatePath(platformId, evaluatedPath, true)
}

internal fun routeResetPlatformStatePath(vm: SettingsViewModel, platformId: Long) {
    val defaultPath = routeComputeEvaluatedStatePath(vm, platformId, null)
    vm.storageDelegate.updatePlatformStatePath(platformId, defaultPath, false)
}

private fun routeComputeEvaluatedSavePath(vm: SettingsViewModel, platformId: Long, basePathOverride: String?): String? {
    val emulatorConfig = vm.emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
        ?: return basePathOverride
    if (!emulatorConfig.effectiveEmulatorIsRetroArch) return basePathOverride

    val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride
    val systemName = emulatorConfig.platform.slug
    val coreName = emulatorConfig.selectedCore

    return vm.retroArchConfigParser.resolveSavePaths(
        packageName = packageName,
        systemName = systemName,
        coreName = coreName,
        basePathOverride = basePathOverride
    ).firstOrNull()
}

private fun routeComputeEvaluatedStatePath(vm: SettingsViewModel, platformId: Long, basePathOverride: String?): String? {
    val emulatorConfig = vm.emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
        ?: return basePathOverride
    if (!emulatorConfig.effectiveEmulatorIsRetroArch) return basePathOverride

    val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride
    val coreName = emulatorConfig.selectedCore

    return vm.retroArchConfigParser.resolveStatePaths(
        packageName = packageName,
        coreName = coreName,
        basePathOverride = basePathOverride
    ).firstOrNull()
}

// --- Section jump ---

internal fun routeJumpToNextSection(vm: SettingsViewModel, sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean {
    val currentFocus = vm._uiState.value.focusedIndex
    val nextSection = sections.firstOrNull { it.focusStartIndex > currentFocus }
    if (nextSection != null) {
        vm._uiState.update { it.copy(focusedIndex = nextSection.focusStartIndex) }
        return true
    }
    return false
}

internal fun routeJumpToPrevSection(vm: SettingsViewModel, sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean {
    val currentFocus = vm._uiState.value.focusedIndex
    val currentSectionIdx = sections.indexOfLast { it.focusStartIndex <= currentFocus }
    if (currentSectionIdx <= 0) return false
    val prevSection = if (currentFocus == sections[currentSectionIdx].focusStartIndex) {
        sections[currentSectionIdx - 1]
    } else {
        sections[currentSectionIdx]
    }
    vm._uiState.update { it.copy(focusedIndex = prevSection.focusStartIndex) }
    return true
}

// --- Cache validation ---

internal fun routeValidateImageCache(vm: SettingsViewModel) {
    if (vm._uiState.value.storage.isValidatingCache) return
    vm._uiState.update { it.copy(storage = it.storage.copy(isValidatingCache = true)) }

    val key = "cache_validation"
    vm.viewModelScope.launch {
        try {
            vm.notificationManager.showPersistent(
                title = "Validating Image Cache",
                subtitle = "Starting...",
                key = key,
                progress = NotificationProgress(0, 100)
            )

            val result = vm.imageCacheManager.validateAndCleanCache { phase, current, total ->
                val progress = if (total > 0) (current * 100) / total else 0
                vm.notificationManager.updatePersistent(
                    key = key,
                    subtitle = phase,
                    progress = NotificationProgress(progress, 100)
                )
            }

            val (message, type) = if (result.deletedFiles > 0 || result.clearedPaths > 0) {
                "Cleaned ${result.deletedFiles} files, cleared ${result.clearedPaths} paths" to NotificationType.SUCCESS
            } else {
                "Image cache is healthy" to NotificationType.SUCCESS
            }
            vm.notificationManager.completePersistent(key, message, type = type)
        } finally {
            vm._uiState.update { it.copy(storage = it.storage.copy(isValidatingCache = false)) }
        }
    }
}

// --- Scan & Sync ---

internal fun routeScanForAndroidGames(vm: SettingsViewModel) {
    if (vm._uiState.value.android.isScanning) return
    vm.viewModelScope.launch {
        val result = vm.androidGameScanner.scan()
        vm._uiState.update {
            it.copy(android = it.android.copy(lastScanGamesAdded = result.totalGames))
        }
    }
}

internal fun routeSyncRomm(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        when (val result = vm.syncLibraryUseCase()) {
            is SyncLibraryResult.Error -> vm.notificationManager.showError(result.message)
            is SyncLibraryResult.Success -> vm.loadSettings()
        }
    }
}

// --- Server / RA connection ---

internal fun routeStartRommConfig(vm: SettingsViewModel) {
    vm.serverDelegate.startRommConfig { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

internal fun routeCancelRommConfig(vm: SettingsViewModel) {
    vm.serverDelegate.cancelRommConfig { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

internal fun routeConnectToRomm(vm: SettingsViewModel) {
    vm.serverDelegate.connectToRomm(vm.viewModelScope) { vm.loadSettings() }
}

internal fun routeShowRALoginForm(vm: SettingsViewModel) {
    vm.raDelegate.showLoginForm { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

internal fun routeHideRALoginForm(vm: SettingsViewModel) {
    vm.raDelegate.hideLoginForm { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

internal fun routeLoginToRA(vm: SettingsViewModel) {
    vm.raDelegate.login(vm.viewModelScope) { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

internal fun routeLogoutFromRA(vm: SettingsViewModel) {
    vm.raDelegate.logout(vm.viewModelScope) { vm._uiState.update { it.copy(focusedIndex = 0) } }
}

// --- Steam ---

internal fun routeGetLauncherIndexFromFocus(state: SettingsUiState): Int {
    return when (state.currentSection) {
        SettingsSection.SERVER -> {
            val items = buildGameDataItemsFromState(state)
            val item = gameDataItemAtFocusIndex(state.focusedIndex, items)
            if (item is GameDataItem.InstalledLauncher) {
                state.steam.installedLaunchers.indexOf(item.data)
            } else -1
        }
        SettingsSection.STEAM_SETTINGS -> state.focusedIndex - 1
        else -> -1
    }
}

internal fun routeMoveLauncherActionFocus(vm: SettingsViewModel, delta: Int) {
    val launcherIndex = routeGetLauncherIndexFromFocus(vm._uiState.value)
    vm.steamDelegate.moveLauncherActionFocus(delta, launcherIndex)
}

internal fun routeConfirmLauncherAction(vm: SettingsViewModel) {
    val launcherIndex = routeGetLauncherIndexFromFocus(vm._uiState.value)
    vm.steamDelegate.confirmLauncherAction(vm.context, vm.viewModelScope, launcherIndex)
}

// --- Bios focus mapping ---

internal class BiosFocusMappingHelper(
    private val platformGroups: List<BiosPlatformGroup>,
    private val expandedIndex: Int
) {
    fun getPlatformAndChildInfo(focusIndex: Int): Pair<Int, Boolean> {
        if (focusIndex < 2) return -1 to false

        var currentFocus = 2
        for ((index, group) in platformGroups.withIndex()) {
            if (currentFocus == focusIndex) return index to false
            currentFocus++

            if (index == expandedIndex) {
                for (i in group.firmwareItems.indices) {
                    if (currentFocus == focusIndex) return index to true
                    currentFocus++
                }
            }
        }
        return -1 to false
    }

    fun getChildIndex(focusIndex: Int, platformIndex: Int): Int {
        if (platformIndex != expandedIndex) return -1

        var currentFocus = 2
        for ((index, group) in platformGroups.withIndex()) {
            currentFocus++
            if (index == expandedIndex) {
                for (i in group.firmwareItems.indices) {
                    if (currentFocus == focusIndex) return i
                    currentFocus++
                }
            }
        }
        return -1
    }
}

internal fun routeBuildBiosFocusMapping(
    platformGroups: List<BiosPlatformGroup>,
    expandedIndex: Int
): BiosFocusMappingHelper = BiosFocusMappingHelper(platformGroups, expandedIndex)

internal fun routeMoveBiosPlatformSubFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value
    val focusIndex = state.focusedIndex
    if (focusIndex < 2) return false

    val bios = state.bios
    val focusMapping = routeBuildBiosFocusMapping(bios.platformGroups, bios.expandedPlatformIndex)
    val (platformIndex, isChildItem) = focusMapping.getPlatformAndChildInfo(focusIndex)

    if (isChildItem || platformIndex < 0 || platformIndex >= bios.platformGroups.size) return false

    val group = bios.platformGroups[platformIndex]
    return vm.biosDelegate.movePlatformSubFocus(delta, !group.isComplete)
}

internal fun routeMoveBiosPathActionFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val hasCustomPath = vm._uiState.value.bios.customBiosPath != null
    return vm.biosDelegate.moveBiosPathActionFocus(delta, hasCustomPath)
}

// --- Misc ---

internal fun routeRequestScreenCapturePermission(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        vm._requestScreenCapturePermissionEvent.emit(Unit)
    }
}

internal fun routeHandlePlayTimeToggle(vm: SettingsViewModel, controls: ControlsState) {
    val newEnabled = !controls.accuratePlayTimeEnabled
    if (newEnabled && !controls.hasUsageStatsPermission) {
        vm.openUsageStatsSettings()
    } else {
        vm.setAccuratePlayTimeEnabled(newEnabled)
    }
}
