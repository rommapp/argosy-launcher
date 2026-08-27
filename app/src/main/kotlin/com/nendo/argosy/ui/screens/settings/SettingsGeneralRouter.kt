package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MAX_PERCENT
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MIN_PERCENT
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationProgress
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.ui.screens.settings.sections.BiosItem
import com.nendo.argosy.ui.screens.settings.sections.biosItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.SteamItem
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.createStorageCachesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.createStorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storageGamesFocusIndexOfPlatform
import com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.storageCachesFocusIndexOfSteam
import com.nendo.argosy.ui.screens.settings.sections.storageFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.storageMediaVisibleLive
import com.nendo.argosy.ui.screens.settings.sections.storageSteamVisibleLive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Navigation ---

/**
 * Enters [section] as a child of whatever is on screen, remembering where focus was so Back
 * can return to it. Every route into a sub-screen goes through here; a destination that
 * declares its own parent would be wrong the moment a second route into it exists.
 */
internal fun routePushSection(vm: SettingsViewModel, section: SettingsSection, entryFocus: Int = 0) {
    vm._uiState.update { it.pushedSection(section, entryFocus) }
}

/**
 * Opens [section] as the root of the stack, for deep links that land mid-tree. Back from a
 * screen reached this way leaves settings rather than surfacing a parent the user never
 * opened.
 */
internal fun routeStartAtSection(vm: SettingsViewModel, section: SettingsSection) {
    vm._uiState.update {
        it.copy(backStack = emptyList(), currentSection = section, focusedIndex = 0)
    }
    routeApplySectionEntry(vm, section)
}

internal fun routePopSection(vm: SettingsViewModel): Boolean {
    val state = vm._uiState.value
    val parent = state.backStack.lastOrNull() ?: return false
    routeApplySectionExit(vm, state.currentSection)
    val restoredFocus = routeReresolveParentFocus(vm, state, parent)
    vm._uiState.update { it.poppedSection(restoredFocus) ?: it }
    vm._uiState.update {
        it.copy(focusedIndex = it.focusedIndex.coerceIn(0, routeMaxFocusIndexOf(vm, it)))
    }
    return true
}

/**
 * A remembered index is a position, and a position stops meaning the same row when the parent
 * list can reorder or lose entries while a child is open. Those parents are re-resolved by
 * identity instead; every other parent restores the index it was left on.
 */
private fun routeReresolveParentFocus(
    vm: SettingsViewModel,
    state: SettingsUiState,
    parent: SettingsNavEntry
): Int? = when {
    state.currentSection == SettingsSection.PLATFORM_DETAIL &&
        parent.section == SettingsSection.PLATFORMS -> {
        val platformId = state.emulators.platforms
            .getOrNull(state.platformDetail.platformIndex)?.platform?.id
        vm.focusIndexForPlatform(platformId)
    }
    state.currentSection == SettingsSection.STORAGE_PLATFORM_GAMES &&
        parent.section == SettingsSection.STORAGE_GAMES ->
        storageGamesFocusIndexOfPlatform(
            state.storagePlatformGames.selectedPlatformId,
            createStorageGamesLayoutInfo(state)
        ).coerceAtLeast(0)
    else -> null
}

/**
 * Work a section needs done when it is left, as opposed to when its parent is re-entered.
 * Pops only -- re-entering the parent from somewhere else must not run any of it.
 */
private fun routeApplySectionExit(vm: SettingsViewModel, section: SettingsSection) {
    when (section) {
        SettingsSection.SHADER_STACK -> routeFlushShaderChain(vm)
        SettingsSection.STEAM_SETTINGS -> vm.cancelSteamQrAuth()
        else -> {}
    }
}

internal fun routeNavigateToSection(vm: SettingsViewModel, section: SettingsSection) {
    routePushSection(vm, section)
    routeApplySectionEntry(vm, section)
}

private fun routeApplySectionEntry(vm: SettingsViewModel, section: SettingsSection) {
    when (section) {
        SettingsSection.ACCOUNTS -> {
            vm.accountsDelegate.resetRowActionFocus()
            vm.accountsDelegate.dismissNotice()
        }
        SettingsSection.PLATFORMS -> vm.refreshEmulators()
        SettingsSection.ROMM -> {
            vm.serverDelegate.checkRommConnection(vm.viewModelScope)
            vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
        }
        SettingsSection.SAVES -> vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
        SettingsSection.SYNC_SETTINGS -> vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
        SettingsSection.STORAGE -> {
            vm.attributionDelegate.latchSteamTileVisible(storageSteamVisibleLive(vm._uiState.value))
            vm.attributionDelegate.latchMediaTileVisible(storageMediaVisibleLive(vm._uiState.value))
            vm.attributionDelegate.refreshOnOpen()
            vm._uiState.update { state ->
                state.copy(
                    focusedIndex = storageFocusIndexOf(StorageItem.GamesTile, createStorageLayoutInfo(state))
                )
            }
        }
        SettingsSection.STEAM_SETTINGS -> vm.steamDelegate.loadSteamSettings(vm.context, vm.viewModelScope)
        SettingsSection.JELLYFIN -> {
            vm.jellyfinDelegate.refreshMediaDirPath(vm.viewModelScope)
            vm.refreshJellyfinConnection()
        }
        SettingsSection.PERMISSIONS -> vm.permissionsDelegate.refreshPermissions()
        SettingsSection.SHADER_STACK -> vm.shaderChainManager.loadChain(
            routeResolveShaderChainSettingsScope(vm._uiState.value).chainJson
        )
        SettingsSection.DRIVERS -> vm.driversDelegate.loadDrivers(vm.viewModelScope)
        else -> {}
    }
}

internal fun routeNavigateToBoxArt(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.BOX_ART)
    vm.loadPreviewGames()
}

internal fun routeNavigateToControllerGrip(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.CONTROLLER_GRIP)
}

internal fun routeNavigateToHomeScreen(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.HOME_SCREEN)
}

internal fun routeNavigateToLibraryView(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.LIBRARY_VIEW)
}

internal fun routeNavigateToAmbientLed(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.AMBIENT_LED)
}

internal fun routeNavigateToThemeSounds(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.THEME_SOUNDS)
}

internal fun routeNavigateToThemeMusic(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.THEME_MUSIC)
}

internal fun routeNavigateToStorageGames(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.STORAGE_GAMES)
}

internal fun routeNavigateToStorageMedia(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.STORAGE_MEDIA)
}

internal fun routeNavigateToStorageCaches(vm: SettingsViewModel, entryFocus: Int) {
    vm.syncDelegate.loadLibrarySettings(vm.viewModelScope)
    vm.storageCachesDelegate.refreshOnOpen(vm.viewModelScope)
    routePushSection(vm, SettingsSection.STORAGE_CACHES)
    if (entryFocus == CACHES_ENTRY_STEAM) {
        vm._uiState.update { state ->
            val steamFocus = storageCachesFocusIndexOfSteam(createStorageCachesLayoutInfo(state))
            state.copy(focusedIndex = steamFocus.coerceAtLeast(0))
        }
    }
}

internal fun routeNavigateToThemeBackdrop(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.THEME_BACKDROP)
}

// --- Emulator methods ---

internal fun routeShowEmulatorPicker(vm: SettingsViewModel, config: PlatformEmulatorConfig) {
    if (config.availableEmulators.isEmpty() && config.downloadableEmulators.isEmpty()) return
    vm.emulatorDelegate.showEmulatorPicker(config, vm.viewModelScope)
}

internal fun routeHandleVariantPickerItemTap(vm: SettingsViewModel, index: Int) {
    if (vm._uiState.value.emulators.variantPickerFocusIndex == index) {
        vm.confirmVariantSelection()
    } else {
        vm._uiState.update { state ->
            state.copy(emulators = state.emulators.copy(variantPickerFocusIndex = index))
        }
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
        refreshCoreOptionsInstallState(vm)
        vm.loadCoreManagementState(preserveFocus = true)
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
        onLoadSettings = { vm.loadSettings() },
        onOpenAppPicker = { platformId -> vm.openAppPickerModal(platformId) }
    )
}

internal fun routeHandleEmulatorPickerItemTap(vm: SettingsViewModel, index: Int) {
    vm.emulatorDelegate.handleEmulatorPickerItemTap(
        index,
        vm.viewModelScope,
        onSetEmulator = { platformId, platformSlug, emulator -> vm.setPlatformEmulator(platformId, platformSlug, emulator) },
        onLoadSettings = { vm.loadSettings() },
        onOpenAppPicker = { platformId -> vm.openAppPickerModal(platformId) }
    )
}

internal fun routeShowSavePathModal(vm: SettingsViewModel, config: PlatformEmulatorConfig) {
    val installedEmulator = config.availableEmulators
        .find { it.def.displayName == config.selectedEmulator || it.def.displayName == config.effectiveEmulatorName }
        ?: return
    val emulatorId = vm.savePathAuthority.configIdFor(
        com.nendo.argosy.data.emulator.savepath.SavePathRequest(
            platformSlug = config.platform.slug,
            emulatorId = installedEmulator.def.id,
            emulatorPackage = installedEmulator.def.packageName
        )
    ) ?: return
    vm.emulatorDelegate.showSavePathModal(
        scope = vm.viewModelScope,
        emulatorId = emulatorId,
        emulatorName = config.effectiveEmulatorName ?: config.selectedEmulator
            ?: vm.context.getString(R.string.settings_shell_router_emulator_name_fallback),
        platformName = config.platform.name,
        savePath = config.effectiveSavePath,
        isUserOverride = config.isUserSavePathOverride,
        platformSlug = config.platform.slug
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
    if (state.focusedIndex == index) {
        val config = state.emulators.platforms.getOrNull(index)
        if (config != null) {
            vm.showEmulatorPicker(config)
        }
    } else {
        vm._uiState.update { it.copy(focusedIndex = index) }
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
    val wouldBe = (current + delta).coerceIn(50, 150)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustUiScale(vm.viewModelScope, delta)
}

internal fun routeAdjustGripReservePercent(vm: SettingsViewModel, delta: Int) {
    val current = vm.uiState.value.display.gripReservePercent
    val wouldBe = (current + delta).coerceIn(GRIP_RESERVE_MIN_PERCENT, GRIP_RESERVE_MAX_PERCENT)
    if (wouldBe == current && delta != 0) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
    }
    vm.displayDelegate.adjustGripReservePercent(vm.viewModelScope, delta)
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

internal fun routeSetGradientPreset(vm: SettingsViewModel, preset: GradientPreset) {
    vm._uiState.update { it.copy(gradientConfig = preset.toConfig()) }
    vm.displayDelegate.setGradientPreset(vm.viewModelScope, preset)
    vm.extractGradientForPreview()
}

internal fun routeToggleGradientAdvancedMode(vm: SettingsViewModel) {
    vm.displayDelegate.toggleGradientAdvancedMode(vm.viewModelScope)
    vm.extractGradientForPreview()
}

internal fun routeSetDualScreenEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setDualScreenEnabled(enabled)
        vm.displayAffinityHelper.dualScreenEnabled = enabled
        val sessionStore = com.nendo.argosy.data.preferences.SessionStateStore(vm.context)
        sessionStore.setDualScreenEnabled(enabled)
        if (enabled) {
            sessionStore.setSecondaryDisplayUsable(true)
            vm.displayAffinityHelper.secondaryDisplayUsable = true
        }
        val hasSecondary = vm.displayAffinityHelper.hasSecondaryDisplay
        vm.displayDelegate.updateState(vm._uiState.value.display.copy(
            dualScreenEnabled = enabled,
            hasSecondaryDisplay = hasSecondary
        ))
        vm.controlsDelegate.updateState(vm._uiState.value.controls.copy(
            hasSecondaryDisplay = hasSecondary
        ))
    }
}

internal fun routeCycleDisplayRoleOverride(vm: SettingsViewModel, direction: Int) {
    val entries = com.nendo.argosy.data.preferences.DisplayRoleOverride.entries
    val current = vm._uiState.value.display.displayRoleOverride
    val currentSwapped = com.nendo.argosy.DualScreenManagerHolder.instance?.isRolesSwapped?.value
        ?: overrideResolvesToSwapped(vm, current)
    val next = (1..entries.size)
        .asSequence()
        .map { step -> entries[(current.ordinal + direction * step).mod(entries.size)] }
        .firstOrNull { overrideResolvesToSwapped(vm, it) != currentSwapped }
        ?: return
    routeSetDisplayRoleOverride(vm, next)
}

internal fun routeSetDisplayRoleOverride(
    vm: SettingsViewModel,
    next: com.nendo.argosy.data.preferences.DisplayRoleOverride
) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setDisplayRoleOverride(next)
        val sessionStore = com.nendo.argosy.data.preferences.SessionStateStore(vm.context)
        sessionStore.setDisplayRoleOverride(next.name)
        vm.displayDelegate.updateState(vm._uiState.value.display.copy(displayRoleOverride = next))

        val dsm = com.nendo.argosy.DualScreenManagerHolder.instance ?: return@launch
        val resolver = com.nendo.argosy.util.DisplayRoleResolver(
            vm.displayAffinityHelper, sessionStore
        )
        val newSwapped = resolver.isSwapped
        if (newSwapped != dsm.isRolesSwapped.value) {
            dsm.setRolesSwapped(newSwapped)
            dsm.sessionStateStore.setRolesSwapped(newSwapped)
            dsm.onRoleSwapped?.invoke(newSwapped)
            dsm.companionHost?.onRoleSwapped(newSwapped)
        }
    }
}

private fun overrideResolvesToSwapped(
    vm: SettingsViewModel,
    value: com.nendo.argosy.data.preferences.DisplayRoleOverride
): Boolean = when (value) {
    com.nendo.argosy.data.preferences.DisplayRoleOverride.SWAPPED -> true
    com.nendo.argosy.data.preferences.DisplayRoleOverride.STANDARD -> false
    com.nendo.argosy.data.preferences.DisplayRoleOverride.AUTO ->
        vm.displayAffinityHelper.secondaryDisplayType ==
            com.nendo.argosy.util.SecondaryDisplayType.EXTERNAL
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
    val current = vm._uiState.value.server.syncScreenshotsEnabled
    vm.syncDelegate.toggleSyncScreenshots(vm.viewModelScope, current)
    vm.serverDelegate.updateState(vm._uiState.value.server.copy(syncScreenshotsEnabled = !current))
}

internal fun routeToggleBoxArtCache(vm: SettingsViewModel) {
    val current = vm._uiState.value.server.boxArtCacheEnabled
    vm.syncDelegate.toggleBoxArtCache(vm.viewModelScope, current)
    vm.serverDelegate.updateState(vm._uiState.value.server.copy(boxArtCacheEnabled = !current))
}

internal fun routeToggleUploadScreenshots(vm: SettingsViewModel) {
    val server = vm._uiState.value.server
    if (!server.screenshotUploadSupported) return
    vm.syncDelegate.toggleUploadScreenshots(vm.viewModelScope, server.uploadScreenshotsEnabled) { newValue ->
        vm.serverDelegate.updateState(vm._uiState.value.server.copy(uploadScreenshotsEnabled = newValue))
    }
}

internal fun routeOnMediaPermissionResult(vm: SettingsViewModel, granted: Boolean) {
    vm.syncDelegate.onMediaPermissionResult(vm.viewModelScope, granted) { newValue ->
        vm.serverDelegate.updateState(vm._uiState.value.server.copy(uploadScreenshotsEnabled = newValue))
    }
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
    vm.emulatorDelegate.setEmulatorSavePath(vm.viewModelScope, emulatorId, basePath) { resolvedPath ->
        val evaluatedPath = routeComputeEvaluatedSavePath(vm, platformId, resolvedPath)
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
    val storageConfig = vm._uiState.value.storage.platformConfigs.find { it.platformId == platformId }
    val emulatorId = storageConfig?.emulatorId ?: return
    val evaluatedPath = routeComputeEvaluatedStatePath(vm, platformId, basePath)
    vm.emulatorDelegate.setEmulatorStatePath(vm.viewModelScope, emulatorId, basePath) {
        vm.storageDelegate.updatePlatformStatePath(platformId, evaluatedPath, true)
    }
}

internal fun routeResetPlatformStatePath(vm: SettingsViewModel, platformId: Long) {
    val storageConfig = vm._uiState.value.storage.platformConfigs.find { it.platformId == platformId }
    val emulatorId = storageConfig?.emulatorId ?: return
    vm.emulatorDelegate.resetEmulatorStatePath(vm.viewModelScope, emulatorId) {
        val defaultPath = routeComputeEvaluatedStatePath(vm, platformId, null)
        vm.storageDelegate.updatePlatformStatePath(platformId, defaultPath, false)
    }
}

private fun routeComputeEvaluatedSavePath(vm: SettingsViewModel, platformId: Long, basePathOverride: String?): String? {
    val emulatorConfig = vm.emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
        ?: return basePathOverride
    if (!emulatorConfig.effectiveEmulatorIsRetroArch) {
        if (basePathOverride != null) return basePathOverride
        val emulatorId = emulatorConfig.effectiveEmulatorId ?: return null
        return vm.savePathAuthority.configFor(
            com.nendo.argosy.data.emulator.savepath.SavePathRequest(
                platformSlug = emulatorConfig.platform.slug,
                emulatorId = emulatorId,
                emulatorPackage = emulatorConfig.effectiveEmulatorPackage
            )
        )?.let {
            com.nendo.argosy.data.emulator.SavePathRegistry
                .resolvePathWithPackage(it, emulatorConfig.effectiveEmulatorPackage)
                .firstOrNull()
        }
    }

    val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride

    if (basePathOverride == null) {
        val raConfig = vm.retroArchConfigParser.parse(packageName)
        if (raConfig?.savefilesInContentDir == true) {
            return vm.context.getString(R.string.settings_shell_router_content_dir_save)
        }
    }

    // No specific ROM is in scope at the settings screen, so the content-dir sort suffix
    // cannot be computed here -- resolveSavePaths will skip it when contentDirName is null.
    val coreName = emulatorConfig.selectedCore

    return vm.retroArchConfigParser.resolveSavePaths(
        packageName = packageName,
        contentDirName = null,
        coreName = coreName,
        basePathOverride = basePathOverride
    ).firstOrNull()
}

private fun routeComputeEvaluatedStatePath(vm: SettingsViewModel, platformId: Long, basePathOverride: String?): String? {
    val emulatorConfig = vm.emulatorDelegate.state.value.platforms.find { it.platform.id == platformId }
        ?: return basePathOverride
    if (!emulatorConfig.effectiveEmulatorIsRetroArch) {
        if (basePathOverride != null) return basePathOverride
        val emulatorId = emulatorConfig.effectiveEmulatorId ?: return null
        return com.nendo.argosy.data.emulator.StatePathRegistry.getConfig(emulatorId)
            ?.let {
                com.nendo.argosy.data.emulator.StatePathRegistry
                    .resolvePath(it, emulatorConfig.platform.slug)
                    .firstOrNull()
            }
    }

    val packageName = emulatorConfig.effectiveEmulatorPackage ?: return basePathOverride

    if (basePathOverride == null) {
        val raStateConfig = vm.retroArchConfigParser.parseStateConfig(packageName)
        if (raStateConfig?.savestatesInContentDir == true) {
            return vm.context.getString(R.string.settings_shell_router_content_dir_state)
        }
    }

    val coreName = emulatorConfig.selectedCore

    return vm.retroArchConfigParser.resolveStatePaths(
        packageName = packageName,
        contentDirName = null,
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
                title = NotificationText.Res(R.string.notif_settings_cache_validate_title),
                subtitle = NotificationText.Res(R.string.notif_settings_cache_validate_starting),
                key = key,
                progress = NotificationProgress(0, 100)
            )

            val result = vm.imageCacheManager.validateAndCleanCache(force = true) { phase, current, total ->
                val progress = if (total > 0) (current * 100) / total else 0
                vm.notificationManager.updatePersistent(
                    key = key,
                    subtitle = NotificationText.Raw(phase),
                    progress = NotificationProgress(progress, 100)
                )
            }

            val (message, type) = if (result.deletedFiles > 0 || result.clearedPaths > 0) {
                NotificationText.Res(
                    R.string.notif_settings_cache_validate_cleaned,
                    listOf(result.deletedFiles, result.clearedPaths)
                ) to NotificationType.SUCCESS
            } else {
                NotificationText.Res(R.string.notif_settings_cache_validate_healthy) to NotificationType.SUCCESS
            }
            vm.notificationManager.completePersistent(key, message, type = type)
        } finally {
            vm._uiState.update { it.copy(storage = it.storage.copy(isValidatingCache = false)) }
        }
    }
}

// --- Download validation ---

internal fun routeValidateDownloads(vm: SettingsViewModel) {
    if (vm._uiState.value.storage.isValidatingDownloads) return
    vm._uiState.update { it.copy(storage = it.storage.copy(isValidatingDownloads = true)) }

    val key = "download_validation"
    vm.viewModelScope.launch {
        try {
            vm.notificationManager.showPersistent(
                title = NotificationText.Res(R.string.notif_settings_downloads_validate_title),
                subtitle = NotificationText.Res(R.string.notif_settings_downloads_validate_checking),
                key = key,
                progress = NotificationProgress(0, 100)
            )

            val invalidated = vm.gameRepository.validateLocalFiles()

            vm.notificationManager.updatePersistent(
                key = key,
                subtitle = NotificationText.Res(R.string.notif_settings_downloads_validate_discovering),
                progress = NotificationProgress(50, 100)
            )

            val discovered = vm.gameRepository.discoverLocalFiles()

            val message = NotificationText.Res(
                R.string.notif_settings_downloads_validate_result,
                listOf(invalidated, discovered)
            )
            vm.notificationManager.completePersistent(key, message, type = NotificationType.SUCCESS)
        } finally {
            vm._uiState.update { it.copy(storage = it.storage.copy(isValidatingDownloads = false)) }
        }
    }
}

// --- Scan & Sync ---

internal fun routeSyncRomm(vm: SettingsViewModel) {
    vm.platformSyncQueue.enqueueLibrary(initializeFirst = false) {
        vm.viewModelScope.launch { vm.loadSettings() }
    }
}

// --- Server / RA connection ---

internal fun routeStartRommConfig(vm: SettingsViewModel) {
    val hasCamera = com.nendo.argosy.ui.components.deviceHasCamera(vm.context)
    vm.serverDelegate.startRommConfig(hasCamera) { vm._uiState.update { it.copy(focusedIndex = 0) } }
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

internal fun routeSetRAProxyEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.raDelegate.setProxyEnabled(vm.viewModelScope, enabled)
    if (!enabled) {
        vm._uiState.update {
            val proxyToggleIndex = if (it.retroAchievements.isLoggedIn) 2 else 1
            if (it.focusedIndex > proxyToggleIndex) it.copy(focusedIndex = proxyToggleIndex) else it
        }
    }
}

// --- Steam ---

internal fun routeConfirmLauncherAction(vm: SettingsViewModel) {
    val state = vm._uiState.value
    if (state.currentSection != SettingsSection.STEAM_SETTINGS) return
    val item = steamItemAtFocusIndex(state.focusedIndex, state.steam)
    if (item !is SteamItem.InstalledLauncher) return
    vm.showAddSteamGameDialog(item.data.packageName)
}

// --- Bios focus helpers ---

internal fun routeMoveBiosPlatformSubFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value
    val bios = state.bios
    val item = biosItemAtFocusIndex(state.focusedIndex, bios.platformGroups, bios.expandedPlatformIndex)

    if (item !is BiosItem.Platform) return false

    return vm.biosDelegate.movePlatformSubFocus(delta, hasDownloadButton = true)
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

