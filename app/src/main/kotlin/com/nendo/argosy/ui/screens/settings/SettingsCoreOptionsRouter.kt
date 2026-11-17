package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.libretro.GraphicsBackendRegistry
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import com.nendo.argosy.ui.screens.settings.search.SettingsSearchCoreOption
import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import com.nendo.argosy.ui.screens.settings.sections.coreOptionsFocusIndexOfOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun routeLoadCoreOptionsState(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val platforms = vm._uiState.value.builtinVideo.availablePlatforms
        if (platforms.isEmpty()) return@launch

        val state = vm._uiState.value.coreOptions
        val index = state.platformContextIndex.coerceIn(platforms.indices)
        val platform = platforms[index]
        val cores = buildCoreContexts(vm, platform.platformSlug)
        val coreIndex = state.selectedCoreIndex.coerceIn(0, (cores.size - 1).coerceAtLeast(0))
        val optionItems = loadOptionsForCore(vm, cores.getOrNull(coreIndex)?.coreId)

        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    availablePlatforms = platforms,
                    platformContextIndex = index,
                    coresForCurrentPlatform = cores,
                    selectedCoreIndex = coreIndex,
                    options = optionItems.first,
                    overrides = optionItems.second
                ),
                focusedIndex = 0
            )
        }
    }
}

internal fun routeCycleCoreOptionsPlatformContext(vm: SettingsViewModel, direction: Int) {
    val state = vm._uiState.value.coreOptions
    val platforms = state.availablePlatforms
    if (platforms.isEmpty()) return

    val newIndex = (state.platformContextIndex + direction).mod(platforms.size)
    val platform = platforms[newIndex]

    vm.viewModelScope.launch {
        val cores = buildCoreContexts(vm, platform.platformSlug)
        val optionItems = loadOptionsForCore(vm, cores.firstOrNull()?.coreId)

        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    platformContextIndex = newIndex,
                    coresForCurrentPlatform = cores,
                    selectedCoreIndex = 0,
                    options = optionItems.first,
                    overrides = optionItems.second
                ),
                focusedIndex = 0
            )
        }
    }
}

internal fun routeCycleCoreSelector(vm: SettingsViewModel, direction: Int) {
    val state = vm._uiState.value.coreOptions
    val cores = state.coresForCurrentPlatform
    if (cores.isEmpty()) return

    val newIndex = (state.selectedCoreIndex + direction).mod(cores.size)

    vm.viewModelScope.launch {
        val optionItems = loadOptionsForCore(vm, cores[newIndex].coreId)
        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    selectedCoreIndex = newIndex,
                    options = optionItems.first,
                    overrides = optionItems.second
                ),
                focusedIndex = 0
            )
        }
    }
}

internal fun routeCycleCoreOptionValue(vm: SettingsViewModel, optionKey: String, direction: Int) {
    val state = vm._uiState.value.coreOptions
    val core = state.selectedCore ?: return
    val option = state.options.find { it.key == optionKey } ?: return
    val values = option.values
    if (values.isEmpty()) return

    val currentIndex = values.indexOf(option.currentValue).coerceAtLeast(0)
    val newIndex = (currentIndex + direction).mod(values.size)
    val newValue = values[newIndex]
    val defaultValue = CoreOptionManifestRegistry.getManifest(core.coreId)
        ?.options?.firstOrNull { it.key == optionKey }?.defaultValue

    vm.viewModelScope.launch {
        if (newValue == defaultValue) {
            vm.coreOptionsRepo.delete(core.coreId, optionKey)
        } else {
            vm.coreOptionsRepo.upsert(
                CoreOptionOverrideEntity(core.coreId, optionKey, newValue)
            )
        }
        val optionItems = loadOptionsForCore(vm, core.coreId)
        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    options = optionItems.first,
                    overrides = optionItems.second
                )
            )
        }
    }
}

internal fun routeResetCoreOption(vm: SettingsViewModel, optionKey: String) {
    val core = vm._uiState.value.coreOptions.selectedCore ?: return
    vm.viewModelScope.launch {
        vm.coreOptionsRepo.delete(core.coreId, optionKey)
        val optionItems = loadOptionsForCore(vm, core.coreId)
        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    options = optionItems.first,
                    overrides = optionItems.second
                )
            )
        }
    }
}

internal fun routeResetAllCoreOptions(vm: SettingsViewModel) {
    val core = vm._uiState.value.coreOptions.selectedCore ?: return
    vm.viewModelScope.launch {
        vm.coreOptionsRepo.deleteAllForCore(core.coreId)
        val optionItems = loadOptionsForCore(vm, core.coreId)
        vm._uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    options = optionItems.first,
                    overrides = optionItems.second
                )
            )
        }
    }
}

internal suspend fun refreshCoreOptionsInstallState(vm: SettingsViewModel) {
    val state = vm._uiState.value.coreOptions
    val platforms = state.availablePlatforms
    if (platforms.isEmpty()) return

    val platform = platforms[state.platformContextIndex.coerceIn(platforms.indices)]
    val cores = buildCoreContexts(vm, platform.platformSlug)
    val coreIndex = state.selectedCoreIndex.coerceIn(0, (cores.size - 1).coerceAtLeast(0))
    val optionItems = loadOptionsForCore(vm, cores.getOrNull(coreIndex)?.coreId)

    vm._uiState.update {
        it.copy(
            coreOptions = it.coreOptions.copy(
                coresForCurrentPlatform = cores,
                selectedCoreIndex = coreIndex,
                options = optionItems.first,
                overrides = optionItems.second
            )
        )
    }
}

internal suspend fun loadDownloadedCoreOptionsForSearch(vm: SettingsViewModel): List<SettingsSearchCoreOption> {
    val reachableCores = vm._uiState.value.builtinVideo.availablePlatforms
        .flatMap { LibretroCoreRegistry.getCoresForPlatform(it.platformSlug) }
        .distinctBy { it.coreId }
    return withContext(Dispatchers.IO) {
        val installed = vm.getInstalledCoreIds()
        reachableCores
            .filter { it.coreId in installed && CoreOptionManifestRegistry.hasManifest(it.coreId) }
            .flatMap { core ->
                loadOptionsForCore(vm, core.coreId).first.map { option ->
                    SettingsSearchCoreOption(
                        coreId = core.coreId,
                        coreName = core.displayName,
                        optionKey = option.key,
                        optionName = option.displayName
                    )
                }
            }
    }
}

internal fun routeOpenCoreOption(vm: SettingsViewModel, coreId: String, optionKey: String) {
    val platforms = vm._uiState.value.builtinVideo.availablePlatforms
    val platformIndex = platforms.indexOfFirst { platform ->
        LibretroCoreRegistry.getCoresForPlatform(platform.platformSlug).any { it.coreId == coreId }
    }
    if (platformIndex < 0) return
    routeNavigateToSection(vm, SettingsSection.BUILTIN_EMULATOR)
    vm.setFocusIndex(BuiltinEmulatorItem.CORE_OPTIONS.focusIndex)
    routeNavigateToSection(vm, SettingsSection.CORE_OPTIONS)
    vm.viewModelScope.launch {
        val cores = buildCoreContexts(vm, platforms[platformIndex].platformSlug)
        val coreIndex = cores.indexOfFirst { it.coreId == coreId }.coerceAtLeast(0)
        val optionItems = loadOptionsForCore(vm, coreId)
        vm._uiState.update { state ->
            val coreOptions = state.coreOptions.copy(
                availablePlatforms = platforms,
                platformContextIndex = platformIndex,
                coresForCurrentPlatform = cores,
                selectedCoreIndex = coreIndex,
                options = optionItems.first,
                overrides = optionItems.second
            )
            val focus = if (state.currentSection == SettingsSection.CORE_OPTIONS) {
                coreOptionsFocusIndexOfOption(coreOptions, optionKey).coerceAtLeast(0)
            } else {
                state.focusedIndex
            }
            state.copy(coreOptions = coreOptions, focusedIndex = focus)
        }
    }
}

private fun buildCoreContexts(
    vm: SettingsViewModel,
    platformSlug: String
): List<CoreOptionsCoreContext> {
    val registryCores = LibretroCoreRegistry.getCoresForPlatform(platformSlug)
    return registryCores.map { core ->
        CoreOptionsCoreContext(
            coreId = core.coreId,
            displayName = core.displayName,
            isInstalled = vm.coreManager.isCoreInstalled(core.coreId)
        )
    }
}

private suspend fun loadOptionsForCore(
    vm: SettingsViewModel,
    coreId: String?
): Pair<List<CoreOptionViewItem>, Map<String, String>> {
    if (coreId == null) return emptyList<CoreOptionViewItem>() to emptyMap()
    val manifest = CoreOptionManifestRegistry.getManifest(coreId)
        ?: return emptyList<CoreOptionViewItem>() to emptyMap()
    val overrides = vm.coreOptionsRepo.getOverridesForCore(coreId)
        .associate { it.optionKey to it.value }
    val current = manifest.options.associate { def ->
        def.key to (overrides[def.key]?.let { def.resolveStored(it) } ?: def.defaultValue)
    }
    val launched = if (vm.vulkanDriverRepository.vulkanSupported()) {
        current
    } else {
        GraphicsBackendRegistry.withoutVulkan(coreId, current)
    }
    val items = manifest.options.filter { def -> def.appliesWith(launched::get) }.map { def ->
        val overrideValue = overrides[def.key]
        CoreOptionViewItem(
            key = def.key,
            displayName = def.displayName,
            description = def.description,
            values = def.values,
            currentValue = overrideValue?.let { def.resolveStored(it) } ?: def.defaultValue,
            isOverridden = overrideValue != null,
            valueLabels = def.valueLabels
        )
    }
    return items to overrides
}
