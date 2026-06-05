package com.nendo.argosy.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.emulator.LaunchMethod
import com.nendo.argosy.data.emulator.RomBindingFormat
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import kotlinx.coroutines.launch

internal fun routeOpenLaunchArgsModal(vm: SettingsViewModel, platformId: Long) {
    vm.viewModelScope.launch {
        val config = vm._uiState.value.emulators.platforms.find { it.platform.id == platformId }
            ?: return@launch
        val emulatorId = config.effectiveEmulatorId ?: return@launch
        val emulatorDef = vm.emulatorDetector.getByPackage(config.effectiveEmulatorPackage ?: return@launch)
            ?: return@launch

        val launchConfig = emulatorDef.launchConfig
        if (launchConfig.isInProcess) return@launch

        val override = vm.launchArgsRepo.getByPlatformAndEmulator(platformId, emulatorId)
        val bindings = launchConfig.bindingDefaults(emulatorDef)
        val state = LaunchArgsModalState(
            platformId = platformId,
            platformName = config.platform.name,
            emulatorId = emulatorId,
            emulatorName = emulatorDef.displayName,
            focusIndex = 0,
            override = override,
            defaultLaunchMethod = emulatorDef.defaultLaunchMethod.name,
            defaultFlagsMask = launchConfig.defaultIntentFlags,
            defaultMimeType = launchConfig.defaultMimeType,
            defaultDataBinding = bindings.data,
            defaultExtraBinding = bindings.extras,
            defaultClipDataBinding = bindings.clipData,
            dataBindingLocked = bindings.dataLocked,
            extraBindingLocked = bindings.extrasLocked
        )
        vm.emulatorDelegate.showLaunchArgsModal(state)
    }
}

internal fun routeCloseLaunchArgsModal(vm: SettingsViewModel) {
    vm.emulatorDelegate.dismissLaunchArgsModal()
}

internal fun routeMoveLaunchArgsFocus(vm: SettingsViewModel, delta: Int) {
    vm.emulatorDelegate.moveLaunchArgsFocus(delta)
}

internal fun routeCycleLaunchArgsMethod(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val current = state.override?.launchMethod
    val next = when (current) {
        null -> LaunchMethod.INTENT.name
        LaunchMethod.INTENT.name -> LaunchMethod.SHELL.name
        LaunchMethod.SHELL.name -> null
        else -> null
    }
    persistLaunchArgsField(vm, state) { it.copy(launchMethod = next) }
}

private val BINDING_CYCLE = listOf(
    null,
    RomBindingFormat.NONE.name,
    RomBindingFormat.ABSOLUTE_PATH.name,
    RomBindingFormat.FILE_PROVIDER.name,
    RomBindingFormat.DOCUMENT_URI.name
)

private fun cycleBindingValue(current: String?, direction: Int): String? {
    val idx = BINDING_CYCLE.indexOf(current).coerceAtLeast(0)
    val next = (idx + direction + BINDING_CYCLE.size) % BINDING_CYCLE.size
    return BINDING_CYCLE[next]
}

internal fun routeCycleLaunchArgsDataBinding(vm: SettingsViewModel, direction: Int = 1) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val next = cycleBindingValue(state.override?.dataBinding, direction)
    persistLaunchArgsField(vm, state) { it.copy(dataBinding = next) }
}

internal fun routeCycleLaunchArgsExtraBinding(vm: SettingsViewModel, direction: Int = 1) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val next = cycleBindingValue(state.override?.extraBinding, direction)
    persistLaunchArgsField(vm, state) { it.copy(extraBinding = next) }
}

internal fun routeCycleLaunchArgsClipDataBinding(vm: SettingsViewModel, direction: Int = 1) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val next = cycleBindingValue(state.override?.clipDataBinding, direction)
    persistLaunchArgsField(vm, state) { it.copy(clipDataBinding = next) }
}

internal fun routeToggleLaunchArgsFlag(vm: SettingsViewModel, flagBit: Int) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val currentMask = state.override?.intentFlagsMask ?: state.defaultFlagsMask
    val toggledMask = currentMask xor flagBit
    val nextMask: Int? = if (toggledMask == state.defaultFlagsMask) null else toggledMask
    persistLaunchArgsField(vm, state) { it.copy(intentFlagsMask = nextMask) }
}

internal fun routeCycleLaunchArgsMimeType(vm: SettingsViewModel, direction: Int = 1) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val presets = listOf(null, "application/octet-stream", "application/x-iso9660-image")
    val currentIndex = presets.indexOf(state.override?.mimeType).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + presets.size) % presets.size
    persistLaunchArgsField(vm, state) { it.copy(mimeType = presets[nextIndex]) }
}

internal fun routeResetLaunchArgsFocused(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val override = state.override ?: return
    val rows = launchArgsModalRows(state)
    val focused = rows.getOrNull(state.focusIndex) ?: return
    val cleared = when (focused) {
        is LaunchArgsRow.DataBinding -> override.copy(dataBinding = null)
        is LaunchArgsRow.ExtraBinding -> override.copy(extraBinding = null)
        is LaunchArgsRow.ClipDataBinding -> override.copy(clipDataBinding = null)
        is LaunchArgsRow.Flag -> override.copy(intentFlagsMask = null)
        is LaunchArgsRow.MimeType -> override.copy(mimeType = null)
        is LaunchArgsRow.CustomExtras -> override.copy(customExtras = null)
        is LaunchArgsRow.LockedBinding -> return
    }
    persistLaunchArgsField(vm, state) { cleared }
}

internal fun routeOpenLaunchArgsCustomExtras(vm: SettingsViewModel) {
    vm.emulatorDelegate.setLaunchArgsCustomExtrasInput(true)
}

internal fun routeCloseLaunchArgsCustomExtras(vm: SettingsViewModel) {
    vm.emulatorDelegate.setLaunchArgsCustomExtrasInput(false)
}

internal fun routeSaveLaunchArgsCustomExtras(vm: SettingsViewModel, raw: String) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val value = raw.trim().ifBlank { null }
    persistLaunchArgsField(vm, state) { it.copy(customExtras = value) }
    vm.emulatorDelegate.setLaunchArgsCustomExtrasInput(false)
}

internal fun routeResetAllLaunchArgs(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    vm.viewModelScope.launch {
        vm.launchArgsRepo.deleteByPlatformAndEmulator(state.platformId, state.emulatorId)
        vm.emulatorDelegate.updateLaunchArgsOverride(null)
    }
}

private fun persistLaunchArgsField(
    vm: SettingsViewModel,
    state: LaunchArgsModalState,
    transform: (EmulatorLaunchArgsEntity) -> EmulatorLaunchArgsEntity
) {
    vm.viewModelScope.launch {
        val base = state.override ?: EmulatorLaunchArgsEntity(
            platformId = state.platformId,
            emulatorId = state.emulatorId
        )
        val updated = transform(base)
        if (updated.hasAnyOverride()) {
            vm.launchArgsRepo.upsert(updated)
            val persisted = vm.launchArgsRepo.getByPlatformAndEmulator(state.platformId, state.emulatorId)
            vm.emulatorDelegate.updateLaunchArgsOverride(persisted)
        } else {
            vm.launchArgsRepo.deleteByPlatformAndEmulator(state.platformId, state.emulatorId)
            vm.emulatorDelegate.updateLaunchArgsOverride(null)
        }
    }
}

internal fun routeOpenAppPickerModal(vm: SettingsViewModel, platformId: Long) {
    vm.viewModelScope.launch {
        val config = vm._uiState.value.emulators.platforms.find { it.platform.id == platformId }
            ?: return@launch
        val apps = vm.installedAppResolver.getLaunchableUserApps()
        vm.emulatorDelegate.showAppPickerModal(
            AppPickerModalState(
                platformId = platformId,
                platformName = config.platform.name,
                platformSlug = config.platform.slug,
                apps = apps,
                focusIndex = 0
            )
        )
    }
}

internal fun routeCloseAppPickerModal(vm: SettingsViewModel) {
    vm.emulatorDelegate.dismissAppPickerModal()
}

internal fun routeMoveAppPickerFocus(vm: SettingsViewModel, delta: Int) {
    vm.emulatorDelegate.moveAppPickerFocus(delta)
}

internal fun routeConfirmAppPickerSelection(vm: SettingsViewModel) {
    val modal = vm._uiState.value.emulators.appPickerModalState ?: return
    val selected = modal.apps.getOrNull(modal.focusIndex) ?: return
    vm.viewModelScope.launch {
        vm.configureEmulatorUseCase.setAdHocForPlatform(
            platformId = modal.platformId,
            packageName = selected.packageName,
            displayName = selected.displayName
        )
        vm.emulatorDelegate.dismissAppPickerModal()
        vm.loadSettings()
    }
}

internal sealed class LaunchArgsRow {
    open val interactive: Boolean get() = true

    data object DataBinding : LaunchArgsRow()
    data object ExtraBinding : LaunchArgsRow()
    data object ClipDataBinding : LaunchArgsRow()
    data class LockedBinding(val label: String, val value: String) : LaunchArgsRow() {
        override val interactive: Boolean get() = false
    }
    data class Flag(val label: String, val bit: Int) : LaunchArgsRow()
    data object MimeType : LaunchArgsRow()
    data object CustomExtras : LaunchArgsRow()
}

internal fun launchArgsModalRows(state: LaunchArgsModalState): List<LaunchArgsRow> = buildList {
    if (state.dataBindingLocked) {
        add(LaunchArgsRow.LockedBinding("Data URI", state.defaultDataBinding))
    } else {
        add(LaunchArgsRow.DataBinding)
    }
    if (state.extraBindingLocked) {
        add(LaunchArgsRow.LockedBinding("Extras", state.defaultExtraBinding))
    } else {
        add(LaunchArgsRow.ExtraBinding)
    }
    add(LaunchArgsRow.ClipDataBinding)
    add(LaunchArgsRow.Flag("New task", Intent.FLAG_ACTIVITY_NEW_TASK))
    add(LaunchArgsRow.Flag("Clear task", Intent.FLAG_ACTIVITY_CLEAR_TASK))
    add(LaunchArgsRow.Flag("No history", Intent.FLAG_ACTIVITY_NO_HISTORY))
    add(LaunchArgsRow.Flag("Single top", Intent.FLAG_ACTIVITY_SINGLE_TOP))
    add(LaunchArgsRow.Flag("Grant read URI", Intent.FLAG_GRANT_READ_URI_PERMISSION))
    add(LaunchArgsRow.Flag("Clear top", Intent.FLAG_ACTIVITY_CLEAR_TOP))
    add(LaunchArgsRow.MimeType)
    add(LaunchArgsRow.CustomExtras)
}
