package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.GlobalLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.isAtSegmentEnd
import com.nendo.argosy.ui.screens.settings.libretro.isSegmented
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.GPU_DRIVER_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.GRAPHICS_BACKEND_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.sections.BuiltinGraphicsRow
import com.nendo.argosy.ui.screens.settings.sections.builtinGpuDriverBlock
import com.nendo.argosy.ui.screens.settings.sections.builtinGraphicsRowAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinGraphicsRowHasOverride
import com.nendo.argosy.ui.screens.settings.sections.builtinGraphicsRows
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoShowsResetAll

internal class BuiltinVideoSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycleSettings(-1)

    override fun onRight(): InputResult = cycleSettings(1)

    override fun onSecondaryAction(): InputResult = resetFocusedSettingOverride()

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val videoState = state.builtinVideo
        builtinGraphicsRowAtFocusIndex(state.focusedIndex, videoState)?.let { return confirmGraphics(it, videoState) }
        val isGlobal = videoState.isGlobalContext
        val platformContext = videoState.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val maxSettingsIndex = libretroSettingsMaxFocusIndex(
            platformSlug = platformContext?.platformSlug,
            canEnableBFI = videoState.canEnableBlackFrameInsertion
        )

        val resetAllIndex = builtinGraphicsRows(videoState).size + maxSettingsIndex + 1
        val showsResetAll = builtinVideoShowsResetAll(videoState, state.platformLibretro.platformSettings)
        if (showsResetAll && state.focusedIndex == resetAllIndex) {
            viewModel.resetAllPlatformLibretroSettings()
            return InputResult.HANDLED
        }

        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        if (isGlobal) {
            return confirmGlobal(setting, videoState)
        }
        return confirmPlatform(setting, videoState, platformSettings)
    }

    private fun confirmGraphics(row: BuiltinGraphicsRow, videoState: BuiltinVideoState): InputResult =
        when (row) {
            BuiltinGraphicsRow.BACKEND -> {
                viewModel.requestEnumPicker(GRAPHICS_BACKEND_PICKER_KEY)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            BuiltinGraphicsRow.GPU_DRIVER -> if (builtinGpuDriverBlock(videoState) == null) {
                viewModel.requestEnumPicker(GPU_DRIVER_PICKER_KEY)
                InputResult.handled(SoundType.OPEN_MODAL)
            } else {
                InputResult.handled(SoundType.SILENT)
            }
        }

    private fun cycleGraphics(row: BuiltinGraphicsRow, videoState: BuiltinVideoState, direction: Int): InputResult =
        when (row) {
            BuiltinGraphicsRow.BACKEND -> {
                viewModel.cycleGraphicsBackend(direction)
                InputResult.HANDLED
            }
            BuiltinGraphicsRow.GPU_DRIVER -> if (builtinGpuDriverBlock(videoState) == null) {
                viewModel.cycleGpuDriver(direction)
                InputResult.HANDLED
            } else {
                InputResult.handled(SoundType.BOUNDARY)
            }
        }

    private fun resetGraphics(row: BuiltinGraphicsRow, state: SettingsUiState): InputResult {
        if (!builtinGraphicsRowHasOverride(row, state)) return InputResult.UNHANDLED
        when (row) {
            BuiltinGraphicsRow.BACKEND -> viewModel.resetGraphicsBackend()
            BuiltinGraphicsRow.GPU_DRIVER -> viewModel.resetGpuDriver()
        }
        return InputResult.HANDLED
    }

    private fun confirmGlobal(
        setting: LibretroSettingDef,
        videoState: com.nendo.argosy.ui.screens.settings.BuiltinVideoState
    ): InputResult = when (setting) {
        LibretroSettingDef.Shader,
        LibretroSettingDef.AspectRatio,
        LibretroSettingDef.Rotation,
        LibretroSettingDef.OverscanCrop,
        LibretroSettingDef.AudioVolume,
        LibretroSettingDef.AudioLatency -> {
            viewModel.requestEnumPicker(setting.key)
            InputResult.handled(SoundType.OPEN_MODAL)
        }
        LibretroSettingDef.PortraitPosition,
        LibretroSettingDef.FastForwardSpeed,
        LibretroSettingDef.RewindSpeed,
        LibretroSettingDef.RewindBufferDuration -> InputResult.handled(SoundType.SILENT)
        LibretroSettingDef.Filter -> {
            if (videoState.shader == "Custom") {
                viewModel.openShaderChainConfig()
                InputResult.HANDLED
            } else {
                InputResult.handled(SoundType.SILENT)
            }
        }
        LibretroSettingDef.FastForwardEnabled -> {
            viewModel.setBuiltinFastForwardEnabled(!videoState.fastForwardEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.BlackFrameInsertion -> {
            viewModel.setBuiltinBlackFrameInsertion(!videoState.blackFrameInsertion)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.RewindEnabled -> {
            viewModel.setBuiltinRewindEnabled(!videoState.rewindEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.SkipDuplicateFrames -> {
            viewModel.setBuiltinSkipDuplicateFrames(!videoState.skipDuplicateFrames)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.LowLatencyAudio -> {
            viewModel.setBuiltinLowLatencyAudio(!videoState.lowLatencyAudio)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.VSync -> {
            viewModel.setBuiltinVSync(!videoState.vsync)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.Frame -> {
            viewModel.setBuiltinFramesEnabled(!videoState.framesEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.AutoSaveState,
        LibretroSettingDef.AutoRestoreState,
        LibretroSettingDef.PreferNewerServerSave,
        LibretroSettingDef.HwCoreSaveStates -> InputResult.UNHANDLED
    }

    private fun confirmPlatform(
        setting: LibretroSettingDef,
        videoState: com.nendo.argosy.ui.screens.settings.BuiltinVideoState,
        platformSettings: com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity?
    ): InputResult {
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
        return when (setting.type) {
            is LibretroSettingDef.SettingType.Cycle -> if (setting.isSegmented) {
                InputResult.handled(SoundType.SILENT)
            } else {
                viewModel.requestEnumPicker(setting.key)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            LibretroSettingDef.SettingType.Switch -> { accessor.toggle(setting); InputResult.handled(SoundType.TOGGLE) }
        }
    }

    private fun resetFocusedSettingOverride(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.isGlobalContext) return InputResult.UNHANDLED
        builtinGraphicsRowAtFocusIndex(state.focusedIndex, state.builtinVideo)?.let { return resetGraphics(it, state) }
        val videoState = state.builtinVideo
        val platformContext = videoState.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        val accessor = PlatformLibretroSettingsAccessor(
            platformSettings = platformSettings,
            globalState = videoState,
            onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
        )
        if (accessor.hasOverride(setting)) {
            accessor.reset(setting)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
            viewModel.cyclePlatformContext(-1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.availablePlatforms.isNotEmpty()) {
            viewModel.cyclePlatformContext(1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycleSettings(direction: Int): InputResult {
        val state = viewModel.uiState.value
        builtinGraphicsRowAtFocusIndex(state.focusedIndex, state.builtinVideo)
            ?.let { return cycleGraphics(it, state.builtinVideo, direction) }
        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, state.builtinVideo) ?: return InputResult.UNHANDLED
        if (setting.type !is LibretroSettingDef.SettingType.Cycle) return InputResult.UNHANDLED
        if (readerFor(state).isAtSegmentEnd(setting, direction)) return InputResult.handled(SoundType.BOUNDARY)
        if (state.builtinVideo.isGlobalContext) {
            return cycleGlobal(setting, state, direction)
        }
        return cyclePlatform(setting, state, direction)
    }

    private fun readerFor(state: SettingsUiState): LibretroSettingsAccessor {
        val videoState = state.builtinVideo
        if (videoState.isGlobalContext) {
            return GlobalLibretroSettingsAccessor(videoState, onCycle = { _, _ -> }, onToggle = { _, _ -> })
        }
        val platformSettings = videoState.currentPlatformContext
            ?.let { state.platformLibretro.platformSettings[it.platformId] }
        return PlatformLibretroSettingsAccessor(platformSettings, videoState, onUpdate = { _, _ -> })
    }

    private fun cycleGlobal(
        setting: LibretroSettingDef,
        state: com.nendo.argosy.ui.screens.settings.SettingsUiState,
        direction: Int
    ): InputResult = when (setting) {
        LibretroSettingDef.Shader -> { viewModel.cycleBuiltinShader(direction); InputResult.HANDLED }
        LibretroSettingDef.Filter -> {
            if (state.builtinVideo.shader != "Custom") viewModel.cycleBuiltinFilter(direction)
            InputResult.HANDLED
        }
        LibretroSettingDef.AspectRatio -> { viewModel.cycleBuiltinAspectRatio(direction); InputResult.HANDLED }
        LibretroSettingDef.PortraitPosition -> { viewModel.cycleBuiltinPortraitPosition(direction); InputResult.HANDLED }
        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(direction); InputResult.HANDLED }
        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(direction); InputResult.HANDLED }
        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(direction); InputResult.HANDLED }
        LibretroSettingDef.AudioVolume -> { viewModel.cycleBuiltinAudioVolume(direction); InputResult.HANDLED }
        LibretroSettingDef.AudioLatency -> { viewModel.cycleBuiltinAudioLatency(direction); InputResult.HANDLED }
        LibretroSettingDef.RewindSpeed -> { viewModel.cycleBuiltinRewindSpeed(direction); InputResult.HANDLED }
        LibretroSettingDef.RewindBufferDuration -> { viewModel.cycleBuiltinRewindBufferDuration(direction); InputResult.HANDLED }
        else -> InputResult.UNHANDLED
    }

    private fun cyclePlatform(
        setting: LibretroSettingDef,
        state: com.nendo.argosy.ui.screens.settings.SettingsUiState,
        direction: Int
    ): InputResult {
        val platformContext = state.builtinVideo.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val accessor = PlatformLibretroSettingsAccessor(
            platformSettings = platformSettings,
            globalState = state.builtinVideo,
            onUpdate = { s, v -> viewModel.updatePlatformLibretroSetting(s, v) }
        )
        if (!accessor.isActionItem(setting)) {
            accessor.cycle(setting, direction)
        }
        return InputResult.HANDLED
    }
}
