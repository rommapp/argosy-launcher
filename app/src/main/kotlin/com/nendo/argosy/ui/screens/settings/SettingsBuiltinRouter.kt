package com.nendo.argosy.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.libretro.LibretroBuildbot
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import com.nendo.argosy.libretro.shader.ShaderChainConfig
import com.nendo.argosy.libretro.shader.ShaderChainManager
import com.nendo.argosy.libretro.shader.ShaderPreviewRenderer
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.util.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun routeSetBuiltinShader(vm: SettingsViewModel, value: String) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(shader = value)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinShader(value)
    }
}

internal fun routeSetBuiltinFramesEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(framesEnabled = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFramesEnabled(enabled)
    }
}

private val ARCHITECTURE_OPTIONS: List<String>
    get() = buildList {
        add("Universal")
        if (LibretroBuildbot.processIs64Bit) add("ARMv8 (64-bit)")
        else add("ARMv7 (32-bit)")
    }

private fun architectureDisplayToAbi(display: String): String? = when (display) {
    "ARMv7 (32-bit)" -> "armeabi-v7a"
    "ARMv8 (64-bit)" -> "arm64-v8a"
    else -> null
}

internal fun architectureAbiToDisplay(abi: String?): String = when (abi) {
    "armeabi-v7a" -> "ARMv7 (32-bit)"
    "arm64-v8a" -> "ARMv8 (64-bit)"
    else -> "Universal"
}

internal fun routeCycleBuiltinArchitecture(vm: SettingsViewModel, direction: Int) {
    val options = ARCHITECTURE_OPTIONS
    val current = vm._uiState.value.emulators.architectureDisplay
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    routeSetBuiltinArchitecture(vm, options[nextIndex])
}

internal fun routeSetBuiltinArchitecture(vm: SettingsViewModel, value: String) {
    vm._uiState.update {
        it.copy(emulators = it.emulators.copy(architectureDisplay = value))
    }
    val override = architectureDisplayToAbi(value)
    LibretroBuildbot.abiOverride = override
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setArchitectureOverride(override)
        vm.coreManager.migrateAbiIfNeeded()
    }
}

internal fun routeSetBuiltinLibretroEnabled(vm: SettingsViewModel, enabled: Boolean) {
    val newToggleIndex = if (enabled) 3 else 0
    vm._uiState.update { state ->
        val adjustedParentIndex = when {
            enabled && state.parentFocusIndex >= 2 -> state.parentFocusIndex + 1
            !enabled && state.parentFocusIndex > 2 -> state.parentFocusIndex - 1
            else -> state.parentFocusIndex
        }
        state.copy(
            emulators = state.emulators.copy(builtinLibretroEnabled = enabled),
            focusedIndex = newToggleIndex,
            parentFocusIndex = adjustedParentIndex
        )
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinLibretroEnabled(enabled)
        if (!enabled) {
            vm.configureEmulatorUseCase.clearBuiltinSelections()
        }
        vm.loadSettings()
    }
}

internal fun routeSetBuiltinFilter(vm: SettingsViewModel, value: String) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(filter = value)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFilter(value)
    }
}

internal fun routeSetBuiltinAspectRatio(vm: SettingsViewModel, value: String) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(aspectRatio = value)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinAspectRatio(value)
    }
}

internal fun routeSetBuiltinPortraitPosition(vm: SettingsViewModel, value: String) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(portraitPosition = value)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinPortraitPosition(value)
    }
}

internal fun routeSetBuiltinSkipDuplicateFrames(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(
            skipDuplicateFrames = enabled,
            blackFrameInsertion = if (enabled) false else it.builtinVideo.blackFrameInsertion
        ))
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinSkipDuplicateFrames(enabled)
        if (enabled) vm.libretroSettingsRepo.setBuiltinBlackFrameInsertion(false)
    }
}

internal fun routeSetBuiltinLowLatencyAudio(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(
            lowLatencyAudio = enabled,
            blackFrameInsertion = if (enabled) false else it.builtinVideo.blackFrameInsertion
        ))
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinLowLatencyAudio(enabled)
        if (enabled) vm.libretroSettingsRepo.setBuiltinBlackFrameInsertion(false)
    }
}

internal fun routeSetBuiltinVSync(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(vsync = enabled))
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinForceSoftwareTiming(!enabled)
    }
}

internal fun routeSetBuiltinFastForwardEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(fastForwardEnabled = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFastForwardEnabled(enabled)
    }
}

internal fun routeSetBuiltinRewindEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rewindEnabled = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinRewindEnabled(enabled)
    }
}

internal fun routeSetBuiltinAutoSaveState(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(autoSaveState = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinAutoSaveState(enabled)
    }
}

internal fun routeSetBuiltinAutoRestoreState(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(autoRestoreState = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinAutoRestoreState(enabled)
        vm.libretroSettingsRepo.setBuiltinAutoRestoreStateMode(if (enabled) "restore" else "off")
    }
}

internal fun routeSetBuiltinHwCoreSaveStates(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(hwCoreSaveStatesEnabled = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinHwCoreSaveStates(enabled)
    }
}

internal fun routeSetBuiltinSavePath(vm: SettingsViewModel, newPath: String) {
    val oldPath = vm._uiState.value.builtinVideo.savePath
    initiateBuiltinPathMigration(vm, BuiltinPathType.SAVE, oldPath, newPath)
}

internal fun routeResetBuiltinSavePath(vm: SettingsViewModel) {
    val oldPath = vm._uiState.value.builtinVideo.savePath
    val defaultPath = AppPaths.libretroSavesDir(vm.context.filesDir).absolutePath
    if (oldPath == defaultPath) return
    initiateBuiltinPathMigration(vm, BuiltinPathType.SAVE, oldPath, defaultPath)
}

internal fun routeSetBuiltinStatePath(vm: SettingsViewModel, newPath: String) {
    val oldPath = vm._uiState.value.builtinVideo.statePath
    initiateBuiltinPathMigration(vm, BuiltinPathType.STATE, oldPath, newPath)
}

internal fun routeResetBuiltinStatePath(vm: SettingsViewModel) {
    val oldPath = vm._uiState.value.builtinVideo.statePath
    val defaultPath = AppPaths.libretroStatesDir(vm.context.filesDir).absolutePath
    if (oldPath == defaultPath) return
    initiateBuiltinPathMigration(vm, BuiltinPathType.STATE, oldPath, defaultPath)
}

private fun initiateBuiltinPathMigration(
    vm: SettingsViewModel,
    pathType: BuiltinPathType,
    oldPath: String,
    newPath: String
) {
    val source = java.io.File(oldPath)
    val destination = java.io.File(newPath)
    val existingFiles = destination.walkTopDown().filter { it.isFile }.count()

    if (source.exists() && existingFiles > 0) {
        vm._uiState.update {
            it.copy(
                showBuiltinPathMigrationDialog = true,
                pendingBuiltinPathMigration = BuiltinPathMigration(
                    pathType = pathType,
                    oldPath = oldPath,
                    newPath = newPath,
                    existingFileCount = existingFiles
                )
            )
        }
    } else {
        commitBuiltinPathChange(vm, pathType, oldPath, newPath, migrate = source.exists())
    }
}

internal fun routeConfirmBuiltinPathMigration(vm: SettingsViewModel) {
    val pending = vm._uiState.value.pendingBuiltinPathMigration ?: return
    vm._uiState.update { it.copy(showBuiltinPathMigrationDialog = false, pendingBuiltinPathMigration = null) }
    commitBuiltinPathChange(vm, pending.pathType, pending.oldPath, pending.newPath, migrate = true)
}

internal fun routeSkipBuiltinPathMigration(vm: SettingsViewModel) {
    val pending = vm._uiState.value.pendingBuiltinPathMigration ?: return
    vm._uiState.update { it.copy(showBuiltinPathMigrationDialog = false, pendingBuiltinPathMigration = null) }
    commitBuiltinPathChange(vm, pending.pathType, pending.oldPath, pending.newPath, migrate = false)
}

internal fun routeCancelBuiltinPathMigration(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(showBuiltinPathMigrationDialog = false, pendingBuiltinPathMigration = null) }
}

private fun commitBuiltinPathChange(
    vm: SettingsViewModel,
    pathType: BuiltinPathType,
    oldPath: String,
    newPath: String,
    migrate: Boolean
) {
    vm.viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        if (migrate) {
            migrateFiles(java.io.File(oldPath), java.io.File(newPath))
        }
        val isCustom = when (pathType) {
            BuiltinPathType.SAVE -> {
                val defaultPath = AppPaths.libretroSavesDir(vm.context.filesDir).absolutePath
                val custom = newPath != defaultPath
                if (custom) vm.libretroSettingsRepo.setBuiltinCustomSavePath(newPath)
                else vm.libretroSettingsRepo.setBuiltinCustomSavePath(null)
                custom
            }
            BuiltinPathType.STATE -> {
                val defaultPath = AppPaths.libretroStatesDir(vm.context.filesDir).absolutePath
                val custom = newPath != defaultPath
                if (custom) vm.libretroSettingsRepo.setBuiltinCustomStatePath(newPath)
                else vm.libretroSettingsRepo.setBuiltinCustomStatePath(null)
                custom
            }
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            vm._uiState.update {
                it.copy(builtinVideo = when (pathType) {
                    BuiltinPathType.SAVE -> it.builtinVideo.copy(savePath = newPath, isCustomSavePath = isCustom)
                    BuiltinPathType.STATE -> it.builtinVideo.copy(statePath = newPath, isCustomStatePath = isCustom)
                })
            }
        }
    }
}

internal fun routeSetPlatformBuiltinSavePath(vm: SettingsViewModel, platformId: Long, newPath: String) {
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformId)
            ?: PlatformLibretroSettingsEntity(platformId = platformId)
        vm.libretroSettingsRepo.upsert(current.copy(savePath = newPath))
    }
}

internal fun routeResetPlatformBuiltinSavePath(vm: SettingsViewModel, platformId: Long) {
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformId) ?: return@launch
        val updated = current.copy(savePath = null)
        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformId)
        }
    }
}

internal fun routeSetPlatformBuiltinStatePath(vm: SettingsViewModel, platformId: Long, newPath: String) {
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformId)
            ?: PlatformLibretroSettingsEntity(platformId = platformId)
        vm.libretroSettingsRepo.upsert(current.copy(statePath = newPath))
    }
}

internal fun routeResetPlatformBuiltinStatePath(vm: SettingsViewModel, platformId: Long) {
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformId) ?: return@launch
        val updated = current.copy(statePath = null)
        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformId)
        }
    }
}

private fun migrateFiles(source: java.io.File, destination: java.io.File) {
    if (!source.exists() || source.absolutePath == destination.absolutePath) return
    destination.mkdirs()
    val files = source.listFiles() ?: return
    for (file in files) {
        val target = java.io.File(destination, file.name)
        try {
            if (file.isDirectory) {
                migrateFiles(file, target)
            } else {
                if (!file.renameTo(target)) {
                    file.copyTo(target, overwrite = true)
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("BuiltinPathMigration", "Failed to migrate ${file.name}: ${e.message}")
        }
    }
    if (source.listFiles()?.isEmpty() == true) source.delete()
}

internal fun routeSetBuiltinRumbleEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(rumbleEnabled = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinRumbleEnabled(enabled)
    }
}

internal fun routeSetBuiltinLimitHotkeysToPlayer1(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(limitHotkeysToPlayer1 = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinLimitHotkeysToPlayer1(enabled)
    }
}

internal fun routeSetBuiltinFastForwardMode(vm: SettingsViewModel, mode: com.nendo.argosy.data.local.entity.FastForwardMode) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(fastForwardMode = mode)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFastForwardMode(mode)
    }
}

internal fun routeSetBuiltinFastForwardPreservePitch(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(fastForwardPreservePitch = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFastForwardPreservePitch(enabled)
    }
}

internal fun routeSetBuiltinAnalogAsDpad(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(analogAsDpad = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinAnalogAsDpad(enabled)
    }
}

internal fun routeSetBuiltinDpadAsAnalog(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(dpadAsAnalog = enabled)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinDpadAsAnalog(enabled)
    }
}

internal fun routeShowControllerOrderModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showControllerOrderModal = true)) }
}

internal fun routeHideControllerOrderModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showControllerOrderModal = false)) }
}

internal fun routeAssignControllerToPort(vm: SettingsViewModel, port: Int, device: android.view.InputDevice) {
    vm.viewModelScope.launch {
        vm.inputConfigRepository.assignControllerToPort(port, device)
        val count = vm.inputConfigRepository.getControllerOrder().size
        vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(controllerOrderCount = count)) }
    }
}

internal fun routeClearControllerOrder(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        vm.inputConfigRepository.clearControllerOrder()
        val count = vm.inputConfigRepository.getControllerOrder().size
        vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(controllerOrderCount = count)) }
    }
}

internal fun routeShowInputMappingModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showInputMappingModal = true)) }
}

internal fun routeHideInputMappingModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showInputMappingModal = false)) }
}

internal fun routeSetTouchEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchEnabled = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsShowWhenNoGamepad(enabled) }
}

internal fun routeSetTouchOpacityLandscape(vm: SettingsViewModel, value: Float) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchOpacityLandscape = value)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsOpacityLandscape(value) }
}

internal fun routeSetTouchOpacityPortrait(vm: SettingsViewModel, value: Float) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchOpacityPortrait = value)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsOpacityPortrait(value) }
}

internal fun routeSetTouchSizeScale(vm: SettingsViewModel, value: Float) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchSizeScale = value)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsSizeScale(value) }
}

internal fun routeSetTouchHaptic(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchHaptic = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsHaptic(enabled) }
}

internal fun routeSetTouchFadeOnIdle(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchFadeOnIdle = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsFadeOnIdle(enabled) }
}

internal fun routeSetTouchSwapHanded(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchSwapHanded = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsSwapHanded(enabled) }
}

internal fun routeSetTouchLockOrientation(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchLockOrientation = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsLockOrientation(enabled) }
}

internal fun routeSetTouchMirror180(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchMirror180 = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsMirror180(enabled) }
}

internal fun routeSetTouchColouredFaceButtons(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchColouredFaceButtons = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsColouredFaceButtons(enabled) }
}

internal fun routeSetTouchGenesis6Button(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(touchGenesis6Button = enabled)) }
    vm.viewModelScope.launch { vm.libretroSettingsRepo.setTouchControlsGenesis6Button(enabled) }
}

internal fun routeShowTouchLayoutEditor(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showTouchLayoutEditorModal = true)) }
}

internal fun routeHideTouchLayoutEditor(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showTouchLayoutEditorModal = false)) }
}

internal fun routeShowHotkeysModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showHotkeysModal = true)) }
}

internal fun routeHideHotkeysModal(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(builtinControls = it.builtinControls.copy(showHotkeysModal = false)) }
}

internal fun routeSetBuiltinBlackFrameInsertion(vm: SettingsViewModel, enabled: Boolean) {
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(
            blackFrameInsertion = enabled,
            skipDuplicateFrames = if (enabled) false else it.builtinVideo.skipDuplicateFrames,
            lowLatencyAudio = if (enabled) false else it.builtinVideo.lowLatencyAudio
        ))
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinBlackFrameInsertion(enabled)
        if (enabled) {
            vm.libretroSettingsRepo.setBuiltinSkipDuplicateFrames(false)
            vm.libretroSettingsRepo.setBuiltinLowLatencyAudio(false)
        }
    }
}

internal fun routeCycleBuiltinShader(vm: SettingsViewModel, direction: Int) {
    val options = listOf("None", "Sharp", "CUT", "CUT2", "CUT3", "CRT", "LCD", "Custom")
    val current = vm._uiState.value.builtinVideo.shader
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    vm.setBuiltinShader(options[nextIndex])
}

internal fun routeCycleBuiltinFilter(vm: SettingsViewModel, direction: Int) {
    val options = listOf("Auto", "Nearest", "Bilinear")
    val current = vm._uiState.value.builtinVideo.filter
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    vm.setBuiltinFilter(options[nextIndex])
}

internal fun routeCycleBuiltinAspectRatio(vm: SettingsViewModel, direction: Int) {
    val options = listOf("Core Provided", "4:3", "16:9", "Integer", "Stretch")
    val current = vm._uiState.value.builtinVideo.aspectRatio
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    vm.setBuiltinAspectRatio(options[nextIndex])
}

internal fun routeCycleBuiltinPortraitPosition(vm: SettingsViewModel, direction: Int) {
    val options = listOf("Auto", "Top", "Center", "Bottom")
    val current = vm._uiState.value.builtinVideo.portraitPosition
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    vm.setBuiltinPortraitPosition(options[nextIndex])
}

internal fun routeCycleBuiltinFastForwardSpeed(vm: SettingsViewModel, direction: Int) {
    val options = listOf(2, 4, 8)
    val currentDisplay = vm._uiState.value.builtinVideo.fastForwardSpeed
    val current = currentDisplay.removeSuffix("x").toIntOrNull() ?: 4
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    routeSetBuiltinFastForwardSpeed(vm, options[nextIndex])
}

internal fun routeCycleBuiltinRotation(vm: SettingsViewModel, direction: Int) {
    val options = listOf(-1, 0, 90, 180, 270)
    val currentDisplay = vm._uiState.value.builtinVideo.rotation
    val current = when (currentDisplay) {
        "Auto" -> -1
        "0\u00B0" -> 0
        "90\u00B0" -> 90
        "180\u00B0" -> 180
        "270\u00B0" -> 270
        else -> -1
    }
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    routeSetBuiltinRotation(vm, options[nextIndex])
}

internal fun routeCycleBuiltinOverscanCrop(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0, 4, 8, 12, 16)
    val currentDisplay = vm._uiState.value.builtinVideo.overscanCrop
    val current = when (currentDisplay) {
        "Off" -> 0
        else -> currentDisplay.removeSuffix("px").toIntOrNull() ?: 0
    }
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    routeSetBuiltinOverscanCrop(vm, options[nextIndex])
}

private fun routeSetBuiltinFastForwardSpeed(vm: SettingsViewModel, speed: Int) {
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(fastForwardSpeed = "${speed}x")) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinFastForwardSpeed(speed)
    }
}

internal fun routeCycleBuiltinAudioVolume(vm: SettingsViewModel, direction: Int) {
    val options = listOf(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200)
    val currentDisplay = vm._uiState.value.builtinVideo.audioVolume
    val current = currentDisplay.removeSuffix("%").toIntOrNull() ?: 100
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    val next = options[nextIndex]
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(audioVolume = "${next}%")) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinAudioVolume(next)
    }
}

internal fun routeCycleBuiltinRewindSpeed(vm: SettingsViewModel, direction: Int) {
    val options = listOf(1, 2, 4)
    val currentDisplay = vm._uiState.value.builtinVideo.rewindSpeed
    val current = currentDisplay.removeSuffix("x").toIntOrNull() ?: 1
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    val next = options[nextIndex]
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rewindSpeed = "${next}x")) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinRewindSpeed(next)
    }
}

internal fun routeCycleBuiltinRewindBufferDuration(vm: SettingsViewModel, direction: Int) {
    val options = listOf(5, 15, 30, 60)
    val currentDisplay = vm._uiState.value.builtinVideo.rewindBufferDuration
    val current = currentDisplay.removeSuffix("s").toIntOrNull() ?: 15
    val currentIndex = options.indexOf(current).coerceAtLeast(0)
    val nextIndex = (currentIndex + direction + options.size) % options.size
    val next = options[nextIndex]
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rewindBufferDuration = "${next}s")) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinRewindBufferDuration(next)
    }
}

private fun routeSetBuiltinRotation(vm: SettingsViewModel, rotation: Int) {
    val display = when (rotation) {
        -1 -> "Auto"
        0 -> "0\u00B0"
        90 -> "90\u00B0"
        180 -> "180\u00B0"
        270 -> "270\u00B0"
        else -> "Auto"
    }
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(rotation = display)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinRotation(rotation)
    }
}

private fun routeSetBuiltinOverscanCrop(vm: SettingsViewModel, crop: Int) {
    val display = if (crop == 0) "Off" else "${crop}px"
    vm._uiState.update { it.copy(builtinVideo = it.builtinVideo.copy(overscanCrop = display)) }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinOverscanCrop(crop)
    }
}

internal fun routeUpdatePlatformLibretroSetting(vm: SettingsViewModel, setting: LibretroSettingDef, value: String?) {
    val platformContext = vm._uiState.value.builtinVideo.currentPlatformContext ?: return
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformContext.platformId)
            ?: PlatformLibretroSettingsEntity(platformId = platformContext.platformId)

        val updated = when (setting) {
            LibretroSettingDef.Shader -> current.copy(shader = value)
            LibretroSettingDef.Filter -> current.copy(filter = value)
            LibretroSettingDef.AspectRatio -> current.copy(aspectRatio = value)
            LibretroSettingDef.PortraitPosition -> current.copy(portraitPosition = value)
            LibretroSettingDef.Rotation -> current.copy(rotation = routeParseRotationValue(value))
            LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = routeParseOverscanValue(value))
            LibretroSettingDef.Frame -> {
                if (value != null && value != "none" && !vm._uiState.value.builtinVideo.framesEnabled) {
                    vm.setBuiltinFramesEnabled(true)
                }
                current.copy(frame = value)
            }
            LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = value?.toBooleanStrictOrNull())
            LibretroSettingDef.FastForwardEnabled -> current.copy(fastForwardEnabled = value?.toBooleanStrictOrNull())
            LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = routeParseFastForwardValue(value))
            LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = value?.toBooleanStrictOrNull())
            LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = value?.toBooleanStrictOrNull())
            LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = value?.toBooleanStrictOrNull())
            LibretroSettingDef.AudioVolume -> current.copy(audioVolume = value?.removeSuffix("%")?.toIntOrNull())
            LibretroSettingDef.VSync -> current.copy(vsync = value?.toBooleanStrictOrNull())
            LibretroSettingDef.RewindSpeed -> current.copy(rewindSpeed = value?.removeSuffix("x")?.toIntOrNull())
            LibretroSettingDef.RewindBufferDuration -> current.copy(rewindBufferDuration = value?.removeSuffix("s")?.toIntOrNull())
            LibretroSettingDef.AutoSaveState,
            LibretroSettingDef.AutoRestoreState,
            LibretroSettingDef.HwCoreSaveStates -> current
        }

        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformContext.platformId)
        }
    }
}

internal fun routeResetAllPlatformLibretroSettings(vm: SettingsViewModel) {
    val platformContext = vm._uiState.value.builtinVideo.currentPlatformContext ?: return
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformContext.platformId) ?: return@launch
        val updated = current.copy(
            shader = null, filter = null, aspectRatio = null, portraitPosition = null, rotation = null,
            overscanCrop = null, frame = null, blackFrameInsertion = null, fastForwardEnabled = null, fastForwardSpeed = null,
            rewindEnabled = null, rewindSpeed = null, rewindBufferDuration = null,
            skipDuplicateFrames = null, lowLatencyAudio = null, audioVolume = null, vsync = null
        )
        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformContext.platformId)
        }
    }
}

internal fun routeUpdatePlatformControlSetting(vm: SettingsViewModel, field: String, value: Boolean?) {
    val platformContext = vm._uiState.value.builtinVideo.currentPlatformContext ?: return
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformContext.platformId)
            ?: PlatformLibretroSettingsEntity(platformId = platformContext.platformId)
        val updated = when (field) {
            "analogAsDpad" -> current.copy(analogAsDpad = value)
            "dpadAsAnalog" -> current.copy(dpadAsAnalog = value)
            "rumbleEnabled" -> current.copy(rumbleEnabled = value)
            else -> return@launch
        }
        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformContext.platformId)
        }
    }
}

internal fun routeResetAllPlatformControlSettings(vm: SettingsViewModel) {
    val platformContext = vm._uiState.value.builtinVideo.currentPlatformContext ?: return
    vm.viewModelScope.launch {
        val current = vm.libretroSettingsRepo.getByPlatformId(platformContext.platformId) ?: return@launch
        val updated = current.copy(analogAsDpad = null, dpadAsAnalog = null, rumbleEnabled = null)
        if (updated.hasAnyOverrides()) {
            vm.libretroSettingsRepo.upsert(updated)
        } else {
            vm.libretroSettingsRepo.deleteByPlatformId(platformContext.platformId)
        }
    }
}

private fun routeParseRotationValue(value: String?): Int? {
    if (value == null) return null
    return when (value) {
        "Auto" -> -1
        "0\u00B0" -> 0
        "90\u00B0" -> 90
        "180\u00B0" -> 180
        "270\u00B0" -> 270
        else -> value.removeSuffix("\u00B0").toIntOrNull() ?: -1
    }
}

private fun routeParseOverscanValue(value: String?): Int? {
    if (value == null) return null
    return when (value) {
        "Off" -> 0
        else -> value.removeSuffix("px").toIntOrNull() ?: 0
    }
}

private fun routeParseFastForwardValue(value: String?): Int? {
    if (value == null) return null
    return value.removeSuffix("x").toIntOrNull()
}

internal fun routeLoadCoreManagementState(vm: SettingsViewModel, preserveFocus: Boolean = false) {
    vm.viewModelScope.launch {
        try {
            loadCoreManagementStateInternal(vm, preserveFocus)
        } catch (e: Exception) {
            android.util.Log.e("CoreManagement", "Failed to load core management state", e)
            vm._uiState.update { it.copy(coreManagement = CoreManagementState()) }
            vm.notificationManager.showError("Couldn't load cores. Please try again.")
        }
    }
}

private suspend fun loadCoreManagementStateInternal(vm: SettingsViewModel, preserveFocus: Boolean) {
        val currentState = vm._uiState.value.coreManagement
        val isOnline = com.nendo.argosy.util.NetworkUtils.isOnline(vm.context)
        val syncEnabledPlatforms = vm.platformRepository.getSyncEnabledPlatforms()
        val coreSelections = vm.libretroSettingsRepo.getBuiltinCoreSelections().firstOrNull() ?: emptyMap()
        val installedCoreIds = vm.getInstalledCoreIds()
        val updatableCoreIds = vm.coreManager.getCoreIdsWithUpdatesAvailable()

        val platformRows = syncEnabledPlatforms
            .filter { LibretroCoreRegistry.isPlatformSupported(it.slug) }
            .distinctBy { it.slug }
            .map { platform ->
                val availableCores = LibretroCoreRegistry.getCoresForPlatform(platform.slug)
                val selectedCoreId = coreSelections[platform.slug]
                val activeCoreId = selectedCoreId
                    ?: LibretroCoreRegistry.getDefaultCoreForPlatform(platform.slug)?.coreId

                PlatformCoreRow(
                    platformSlug = platform.slug,
                    platformName = platform.name,
                    cores = availableCores.map { core ->
                        CoreChipState(
                            coreId = core.coreId,
                            displayName = core.displayName,
                            isInstalled = core.coreId in installedCoreIds,
                            isActive = core.coreId == activeCoreId,
                            netplaySupported = core.netplaySupport == NetplaySupportLevel.SUPPORTED,
                            updateAvailable = core.coreId in updatableCoreIds
                        )
                    }
                )
            }
            .filter { it.cores.isNotEmpty() }

        val focusedPlatformIndex = if (preserveFocus) {
            currentState.focusedPlatformIndex.coerceIn(0, (platformRows.size - 1).coerceAtLeast(0))
        } else {
            0
        }
        val focusedCoreIndex = if (preserveFocus) {
            val maxCoreIndex = (platformRows.getOrNull(focusedPlatformIndex)?.cores?.size ?: 1) - 1
            currentState.focusedCoreIndex.coerceIn(0, maxCoreIndex.coerceAtLeast(0))
        } else {
            platformRows.firstOrNull()?.activeCoreIndex ?: 0
        }

        vm._uiState.update {
            it.copy(
                coreManagement = CoreManagementState(
                    platforms = platformRows,
                    focusedPlatformIndex = focusedPlatformIndex,
                    focusedCoreIndex = focusedCoreIndex,
                    isOnline = isOnline
                )
            )
        }
}

internal fun routeMoveCoreManagementPlatformFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value.coreManagement
    if (state.platforms.isEmpty()) return false
    val newIndex = (state.focusedPlatformIndex + delta).coerceIn(0, state.platforms.size - 1)
    if (newIndex == state.focusedPlatformIndex) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        return false
    }
    val newPlatform = state.platforms.getOrNull(newIndex)
    vm._uiState.update {
        it.copy(
            coreManagement = it.coreManagement.copy(
                focusedPlatformIndex = newIndex,
                focusedCoreIndex = newPlatform?.activeCoreIndex ?: 0
            )
        )
    }
    return true
}

internal fun routeMoveCoreManagementCoreFocus(vm: SettingsViewModel, delta: Int): Boolean {
    val state = vm._uiState.value.coreManagement
    val platform = state.focusedPlatform ?: return false
    if (platform.cores.isEmpty()) return false
    val newIndex = (state.focusedCoreIndex + delta).coerceIn(0, platform.cores.size - 1)
    if (newIndex == state.focusedCoreIndex) {
        vm.hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        return false
    }
    vm._uiState.update {
        it.copy(coreManagement = it.coreManagement.copy(focusedCoreIndex = newIndex))
    }
    return true
}

internal fun routeSelectCoreForPlatform(vm: SettingsViewModel) {
    val state = vm._uiState.value.coreManagement
    val platform = state.focusedPlatform ?: return
    val core = state.focusedCore ?: return

    if (!core.isInstalled) {
        if (state.isOnline) {
            routeDownloadCoreWithNotification(vm, core.coreId)
        } else {
            vm.notificationManager.showError("Cannot download while offline")
        }
        return
    }

    if (core.updateAvailable && core.isActive) {
        if (state.isOnline) {
            routeDownloadCoreWithNotification(vm, core.coreId)
        } else {
            vm.notificationManager.showError("Cannot update while offline")
        }
        return
    }

    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinCoreForPlatform(platform.platformSlug, core.coreId)
        vm.loadCoreManagementState(preserveFocus = true)
    }
}

internal fun routeDownloadCoreWithNotification(vm: SettingsViewModel, coreId: String) {
    if (vm._uiState.value.coreOptions.isDownloading) return
    vm.viewModelScope.launch {
        val coreInfo = LibretroCoreRegistry.getCoreById(coreId) ?: return@launch
        vm._uiState.update {
            it.copy(
                coreManagement = it.coreManagement.copy(isDownloading = true, downloadingCoreId = coreId),
                coreOptions = it.coreOptions.copy(isDownloading = true, downloadingCoreId = coreId)
            )
        }

        val notificationKey = "core_download_$coreId"
        vm.notificationManager.showPersistent(
            title = "Downloading ${coreInfo.displayName}",
            subtitle = "Please wait...",
            key = notificationKey
        )

        val result = vm.coreManager.downloadCoreById(coreId)

        result.fold(
            onSuccess = {
                vm.notificationManager.completePersistent(
                    key = notificationKey,
                    title = "Downloaded ${coreInfo.displayName}",
                    subtitle = "Core is now available",
                    type = NotificationType.SUCCESS
                )
                vm.emulatorDelegate.updateCoreCounts()
                vm.loadCoreManagementState(preserveFocus = true)
                refreshCoreOptionsInstallState(vm)
            },
            onFailure = { error ->
                vm.notificationManager.completePersistent(
                    key = notificationKey,
                    title = "Download failed",
                    subtitle = error.message ?: "Unknown error",
                    type = NotificationType.ERROR
                )
            }
        )

        vm._uiState.update {
            it.copy(
                coreManagement = it.coreManagement.copy(isDownloading = false, downloadingCoreId = null),
                coreOptions = it.coreOptions.copy(isDownloading = false, downloadingCoreId = null)
            )
        }
    }
}

internal fun routeOpenShaderChainConfig(vm: SettingsViewModel) {
    vm.shaderChainManager.loadChain(vm._uiState.value.builtinVideo.shaderChainJson)
    vm.loadPreviewGames()
    vm.navigateToSection(SettingsSection.SHADER_STACK)
}

internal fun routeOpenFrameConfig(vm: SettingsViewModel) {
    vm.navigateToSection(SettingsSection.FRAME_PICKER)
}

internal fun routeDownloadAndSelectFrame(vm: SettingsViewModel, frameId: String) {
    if (vm._uiState.value.frameDownloadingId != null) return
    val frame = vm.frameRegistry.findById(frameId) ?: return
    vm._uiState.update { it.copy(frameDownloadingId = frameId) }
    vm.viewModelScope.launch {
        val success = withContext(Dispatchers.IO) {
            try {
                vm.frameRegistry.ensureDirectoryExists()
                val downloadUrl = com.nendo.argosy.libretro.frame.FrameRegistry.downloadUrl(frame)
                val url = java.net.URL(downloadUrl)
                val connection = url.openConnection()
                connection.connectTimeout = 15000
                connection.readTimeout = 60000
                val bytes = connection.getInputStream().use { it.readBytes() }
                val file = java.io.File(vm.frameRegistry.getFramesDir(), "${frame.id}.png")
                file.writeBytes(bytes)
                true
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to download frame: ${frame.id}", e)
                false
            }
        }
        if (success) {
            vm.frameRegistry.invalidateInstalledCache()
            if (!vm._uiState.value.builtinVideo.framesEnabled) {
                vm.setBuiltinFramesEnabled(true)
            }
            vm.updatePlatformLibretroSetting(LibretroSettingDef.Frame, frame.id)
        }
        vm._uiState.update {
            it.copy(
                frameDownloadingId = null,
                frameInstalledRefresh = if (success) it.frameInstalledRefresh + 1 else it.frameInstalledRefresh
            )
        }
    }
}

internal fun routePersistShaderChain(vm: SettingsViewModel, config: ShaderChainConfig) {
    val json = config.toJson()
    val shaderMode = if (config.entries.isNotEmpty()) "Custom" else "None"
    vm._uiState.update {
        it.copy(builtinVideo = it.builtinVideo.copy(
            shader = shaderMode,
            shaderChainJson = json
        ))
    }
    vm.viewModelScope.launch {
        vm.libretroSettingsRepo.setBuiltinShader(shaderMode)
        vm.libretroSettingsRepo.setBuiltinShaderChain(json)
    }
}

internal suspend fun routeResolvePreviewBitmap(vm: SettingsViewModel): Bitmap? {
    val game = vm._uiState.value.previewGame ?: return null
    val imagePath = routeResolvePreviewImage(vm, game) ?: return null
    return BitmapFactory.decodeFile(imagePath)
}

private suspend fun routeResolvePreviewImage(
    vm: SettingsViewModel,
    game: com.nendo.argosy.data.local.entity.GameListItem
): String? {
    val cached = vm.displayDelegate.getFirstCachedScreenshot(game.id)
    if (cached != null) return cached

    val remoteUrls = vm.displayDelegate.getScreenshotUrls(game.id)
    if (remoteUrls.isNotEmpty()) {
        val targetIndex = if (remoteUrls.size > 1) 1 else 0
        val path = vm.imageCacheManager.cacheSingleScreenshot(
            game.id, remoteUrls[targetIndex], targetIndex
        )
        if (path != null) return path
    }

    return game.coverPath
}

internal fun routeLoadPreviewGames(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val supportedPlatforms = LibretroCoreRegistry.getSupportedPlatforms()
        val games = vm.displayDelegate.loadPreviewGames(supportedPlatforms)
        vm._uiState.update {
            it.copy(
                previewGames = games,
                previewGameIndex = 0,
                previewGame = games.firstOrNull() ?: it.previewGame
            )
        }
        if (games.isNotEmpty()) {
            vm.extractGradientForPreview()
            if (vm._uiState.value.currentSection == SettingsSection.SHADER_STACK) {
                vm.shaderChainManager.renderPreview()
            }
        }
    }
}

internal fun routeCyclePrevPreviewGame(vm: SettingsViewModel) {
    val games = vm._uiState.value.previewGames
    if (games.isEmpty()) return
    val currentIndex = vm._uiState.value.previewGameIndex
    val prevIndex = if (currentIndex <= 0) games.lastIndex else currentIndex - 1
    vm._uiState.update {
        it.copy(
            previewGameIndex = prevIndex,
            previewGame = games[prevIndex]
        )
    }
    vm.extractGradientForPreview()
}

internal fun routeCycleNextPreviewGame(vm: SettingsViewModel) {
    val games = vm._uiState.value.previewGames
    if (games.isEmpty()) return
    val currentIndex = vm._uiState.value.previewGameIndex
    val nextIndex = if (currentIndex >= games.lastIndex) 0 else currentIndex + 1
    vm._uiState.update {
        it.copy(
            previewGameIndex = nextIndex,
            previewGame = games[nextIndex]
        )
    }
    vm.extractGradientForPreview()
}

internal fun routeExtractGradientForPreview(vm: SettingsViewModel) {
    vm.viewModelScope.launch(Dispatchers.IO) {
        val coverPath = vm._uiState.value.previewGame?.coverPath ?: return@launch
        val config = vm._uiState.value.gradientConfig
        val bitmap = BitmapFactory.decodeFile(coverPath) ?: return@launch
        val result = vm.gradientColorExtractor.extractWithMetrics(bitmap, config)
        bitmap.recycle()
        withContext(Dispatchers.Main) {
            vm._uiState.update { it.copy(gradientExtractionResult = result) }
        }
    }
}

internal fun routeCyclePlatformContext(vm: SettingsViewModel, direction: Int) {
    val state = vm._uiState.value.builtinVideo
    val maxIndex = state.availablePlatforms.size
    val newIndex = (state.platformContextIndex + direction).mod(maxIndex + 1)
    val isGlobal = newIndex == 0
    val platform = if (!isGlobal) state.availablePlatforms[newIndex - 1] else null
    val platformSlug = platform?.platformSlug
    vm._uiState.update {
        val hasControlOverrides = platform?.let {
            it2 -> it.platformLibretro.platformSettings[it2.platformId]?.hasAnyControlOverrides()
        } == true
        it.copy(
            builtinVideo = it.builtinVideo.copy(platformContextIndex = newIndex),
            builtinControls = it.builtinControls.copy(
                showStickMappings = !isGlobal,
                showDpadAsAnalog = !isGlobal && platformSlug != null && PlatformWeightRegistry.hasAnalogStick(platformSlug),
                showRumble = isGlobal || (platformSlug != null && PlatformWeightRegistry.hasRumble(platformSlug)),
                showResetAll = !isGlobal && hasControlOverrides
            ),
            focusedIndex = 0
        )
    }
}

internal fun routeInitShaderChainManager(vm: SettingsViewModel): ShaderChainManager {
    return ShaderChainManager(
        shaderRegistry = vm._shaderRegistry,
        shaderDownloader = vm._shaderDownloader,
        previewRenderer = ShaderPreviewRenderer(),
        scope = vm.viewModelScope,
        previewInputProvider = { routeResolvePreviewBitmap(vm) },
        onChainChanged = { config -> routePersistShaderChain(vm, config) }
    )
}
