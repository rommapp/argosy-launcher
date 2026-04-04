package com.nendo.argosy.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.LaunchMethod
import com.nendo.argosy.data.emulator.RomPathFormat
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import kotlinx.coroutines.launch

/**
 * Fresh-launch flag mask defaults per LaunchConfig variant. Mirrors the per-variant flag sets in
 * GameLauncher's commandFor* helpers and must stay in sync with them.
 */
internal fun defaultFlagsMaskFor(launchConfig: LaunchConfig): Int = when (launchConfig) {
    is LaunchConfig.FileUri -> Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    is LaunchConfig.FilePathExtra -> Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    is LaunchConfig.Custom -> Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TASK or
        Intent.FLAG_ACTIVITY_NO_HISTORY
    is LaunchConfig.RetroArch -> Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    is LaunchConfig.CustomScheme -> Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TASK or
        Intent.FLAG_ACTIVITY_NO_HISTORY
    is LaunchConfig.Vita3K -> Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_CLEAR_TASK or
        Intent.FLAG_ACTIVITY_NO_HISTORY
    is LaunchConfig.ScummVM -> Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    is LaunchConfig.BuiltIn -> Intent.FLAG_ACTIVITY_NEW_TASK
}

/** The emulator's default MIME type, when the launch uses a data URI. null for extras-only. */
internal fun defaultMimeTypeFor(launchConfig: LaunchConfig): String? = when (launchConfig) {
    is LaunchConfig.FileUri -> "*/*"
    is LaunchConfig.Custom -> launchConfig.mimeTypeOverride ?: "*/*".takeIf { true }
    else -> null
}

/** Whether the emulator's launch config has a ROM path that could sensibly be reformatted. */
internal fun supportsRomPathFormatFor(launchConfig: LaunchConfig): Boolean = when (launchConfig) {
    is LaunchConfig.FileUri,
    is LaunchConfig.FilePathExtra,
    is LaunchConfig.Custom -> true
    else -> false
}

/** Whether the emulator's launch config emits a data URI (MIME type is only meaningful then). */
internal fun supportsMimeTypeFor(launchConfig: LaunchConfig): Boolean = when (launchConfig) {
    is LaunchConfig.FileUri -> true
    is LaunchConfig.Custom -> launchConfig.useFileUri || launchConfig.useShellLaunch ||
        launchConfig.mimeTypeOverride != null || run {
            // Custom + ACTION_VIEW emits a data URI; ACTION_MAIN does not.
            false // caller supplies emulator.launchAction; handled at open time
        }
    is LaunchConfig.CustomScheme,
    is LaunchConfig.ScummVM -> true // opaque scheme, not user-tunable
    else -> false
}

internal fun routeOpenLaunchArgsModal(vm: SettingsViewModel, platformId: Long) {
    vm.viewModelScope.launch {
        val config = vm._uiState.value.emulators.platforms.find { it.platform.id == platformId }
            ?: return@launch
        val emulatorId = config.effectiveEmulatorId ?: return@launch
        val emulatorDef = vm.emulatorDetector.getByPackage(config.effectiveEmulatorPackage ?: return@launch)
            ?: return@launch

        // Don't open for built-in; it doesn't go through the launch args pipeline.
        if (emulatorDef.launchConfig is LaunchConfig.BuiltIn) return@launch

        val override = vm.emulatorLaunchArgsDao.getByPlatformAndEmulator(platformId, emulatorId)
        val state = LaunchArgsModalState(
            platformId = platformId,
            platformName = config.platform.name,
            emulatorId = emulatorId,
            emulatorName = emulatorDef.displayName,
            focusIndex = 0,
            override = override,
            defaultLaunchMethod = LaunchMethod.INTENT.name,
            defaultRomPathFormat = RomPathFormat.AUTO.name,
            defaultFlagsMask = defaultFlagsMaskFor(emulatorDef.launchConfig),
            defaultMimeType = defaultMimeTypeFor(emulatorDef.launchConfig),
            supportsRomPathFormat = supportsRomPathFormatFor(emulatorDef.launchConfig),
            supportsMimeType = emulatorDef.launchAction == Intent.ACTION_VIEW &&
                supportsRomPathFormatFor(emulatorDef.launchConfig)
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
    // Cycle: null (default) -> INTENT -> SHELL -> null
    val next = when (current) {
        null -> LaunchMethod.INTENT.name
        LaunchMethod.INTENT.name -> LaunchMethod.SHELL.name
        LaunchMethod.SHELL.name -> null
        else -> null
    }
    persistLaunchArgsField(vm, state) { it.copy(launchMethod = next) }
}

internal fun routeCycleLaunchArgsRomPathFormat(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val current = state.override?.romPathFormat
    val next = when (current) {
        null -> RomPathFormat.ABSOLUTE_PATH.name
        RomPathFormat.ABSOLUTE_PATH.name -> RomPathFormat.FILE_PROVIDER.name
        RomPathFormat.FILE_PROVIDER.name -> RomPathFormat.DOCUMENT_URI.name
        RomPathFormat.DOCUMENT_URI.name -> null
        else -> null
    }
    persistLaunchArgsField(vm, state) { it.copy(romPathFormat = next) }
}

internal fun routeToggleLaunchArgsFlag(vm: SettingsViewModel, flagBit: Int) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    // Initialize override mask from the base mask if not already overridden.
    val currentMask = state.override?.intentFlagsMask ?: state.defaultFlagsMask
    val toggledMask = currentMask xor flagBit
    // If the result equals the base mask, the override becomes redundant -> clear to null.
    val nextMask: Int? = if (toggledMask == state.defaultFlagsMask) null else toggledMask
    persistLaunchArgsField(vm, state) { it.copy(intentFlagsMask = nextMask) }
}

internal fun routeCycleLaunchArgsMimeType(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val presets = listOf(null, "application/octet-stream", "application/x-iso9660-image")
    val currentIndex = presets.indexOf(state.override?.mimeType)
    val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % presets.size
    persistLaunchArgsField(vm, state) { it.copy(mimeType = presets[nextIndex]) }
}

internal fun routeResetLaunchArgsFocused(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    val override = state.override ?: return
    val rows = launchArgsModalRows(state)
    val focused = rows.getOrNull(state.focusIndex) ?: return
    val cleared = when (focused) {
        is LaunchArgsRow.LaunchMethod -> override.copy(launchMethod = null)
        is LaunchArgsRow.RomPathFormat -> override.copy(romPathFormat = null)
        is LaunchArgsRow.Flag -> override.copy(intentFlagsMask = null)
        is LaunchArgsRow.MimeType -> override.copy(mimeType = null)
    }
    persistLaunchArgsField(vm, state) { cleared }
}

internal fun routeResetAllLaunchArgs(vm: SettingsViewModel) {
    val state = vm._uiState.value.emulators.launchArgsModalState ?: return
    vm.viewModelScope.launch {
        vm.emulatorLaunchArgsDao.deleteByPlatformAndEmulator(state.platformId, state.emulatorId)
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
            vm.emulatorLaunchArgsDao.upsert(updated)
            val persisted = vm.emulatorLaunchArgsDao.getByPlatformAndEmulator(state.platformId, state.emulatorId)
            vm.emulatorDelegate.updateLaunchArgsOverride(persisted)
        } else {
            vm.emulatorLaunchArgsDao.deleteByPlatformAndEmulator(state.platformId, state.emulatorId)
            vm.emulatorDelegate.updateLaunchArgsOverride(null)
        }
    }
}

/** Visible rows in the Launch Args modal, used for focus management. */
internal sealed class LaunchArgsRow {
    data object LaunchMethod : LaunchArgsRow()
    data object RomPathFormat : LaunchArgsRow()
    data class Flag(val label: String, val bit: Int) : LaunchArgsRow()
    data object MimeType : LaunchArgsRow()
}

internal fun launchArgsModalRows(state: LaunchArgsModalState): List<LaunchArgsRow> = buildList {
    add(LaunchArgsRow.LaunchMethod)
    if (state.supportsRomPathFormat) {
        add(LaunchArgsRow.RomPathFormat)
    }
    add(LaunchArgsRow.Flag("New task", Intent.FLAG_ACTIVITY_NEW_TASK))
    add(LaunchArgsRow.Flag("Clear task", Intent.FLAG_ACTIVITY_CLEAR_TASK))
    add(LaunchArgsRow.Flag("No history", Intent.FLAG_ACTIVITY_NO_HISTORY))
    add(LaunchArgsRow.Flag("Single top", Intent.FLAG_ACTIVITY_SINGLE_TOP))
    add(LaunchArgsRow.Flag("Grant read URI", Intent.FLAG_GRANT_READ_URI_PERMISSION))
    add(LaunchArgsRow.Flag("Clear top", Intent.FLAG_ACTIVITY_CLEAR_TOP))
    if (state.supportsMimeType) {
        add(LaunchArgsRow.MimeType)
    }
}
