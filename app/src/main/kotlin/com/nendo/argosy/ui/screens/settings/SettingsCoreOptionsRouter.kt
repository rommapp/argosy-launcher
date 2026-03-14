package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    vm.viewModelScope.launch {
        vm.coreOptionOverrideDao.upsert(
            CoreOptionOverrideEntity(core.coreId, optionKey, newValue)
        )
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
        vm.coreOptionOverrideDao.delete(core.coreId, optionKey)
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
        vm.coreOptionOverrideDao.deleteAllForCore(core.coreId)
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
    val overrides = vm.coreOptionOverrideDao.getOverridesForCore(coreId)
        .associate { it.optionKey to it.value }
    val items = manifest.options.map { def ->
        val overrideValue = overrides[def.key]
        CoreOptionViewItem(
            key = def.key,
            displayName = def.displayName,
            description = def.description,
            values = def.values,
            currentValue = overrideValue ?: def.defaultValue,
            isOverridden = overrideValue != null,
            valueLabels = def.valueLabels
        )
    }
    return items to overrides
}
