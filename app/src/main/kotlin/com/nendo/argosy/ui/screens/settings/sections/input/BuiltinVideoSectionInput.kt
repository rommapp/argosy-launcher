package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
import com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex

internal class BuiltinVideoSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycleSettings(-1)

    override fun onRight(): InputResult = cycleSettings(1)

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        val videoState = state.builtinVideo
        if (!videoState.isGlobalContext || videoState.savePath.isEmpty()) return InputResult.UNHANDLED
        val maxSettingsIndex = libretroSettingsMaxFocusIndex(
            platformSlug = videoState.currentPlatformContext?.platformSlug,
            canEnableBFI = videoState.canEnableBlackFrameInsertion
        )
        val savePathIndex = maxSettingsIndex + 1
        val statePathIndex = maxSettingsIndex + 2
        return when (state.focusedIndex) {
            savePathIndex -> {
                if (videoState.isCustomSavePath) viewModel.resetBuiltinSavePath()
                InputResult.HANDLED
            }
            statePathIndex -> {
                if (videoState.isCustomStatePath) viewModel.resetBuiltinStatePath()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val videoState = state.builtinVideo
        val isGlobal = videoState.isGlobalContext
        val platformContext = videoState.currentPlatformContext
        val platformSettings = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val hasAnyOverrides = platformSettings?.hasAnyOverrides() == true

        val maxSettingsIndex = libretroSettingsMaxFocusIndex(
            platformSlug = platformContext?.platformSlug,
            canEnableBFI = videoState.canEnableBlackFrameInsertion
        )

        if (isGlobal && videoState.savePath.isNotEmpty()) {
            val savePathIndex = maxSettingsIndex + 1
            val statePathIndex = maxSettingsIndex + 2
            when (state.focusedIndex) {
                savePathIndex -> {
                    viewModel.openBuiltinSavePathBrowser()
                    return InputResult.HANDLED
                }
                statePathIndex -> {
                    viewModel.openBuiltinStatePathBrowser()
                    return InputResult.HANDLED
                }
            }
        }

        val resetAllIndex = maxSettingsIndex + 1

        if (!isGlobal && hasAnyOverrides && state.focusedIndex == resetAllIndex) {
            viewModel.resetAllPlatformLibretroSettings()
            return InputResult.HANDLED
        }

        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, videoState) ?: return InputResult.UNHANDLED
        if (isGlobal) {
            return confirmGlobal(setting, videoState)
        }
        return confirmPlatform(setting, videoState, platformSettings)
    }

    private fun confirmGlobal(
        setting: LibretroSettingDef,
        videoState: com.nendo.argosy.ui.screens.settings.BuiltinVideoState
    ): InputResult = when (setting) {
        LibretroSettingDef.Shader -> { viewModel.cycleBuiltinShader(1); InputResult.HANDLED }
        LibretroSettingDef.Filter -> {
            if (videoState.shader == "Custom") viewModel.openShaderChainConfig()
            else viewModel.cycleBuiltinFilter(1)
            InputResult.HANDLED
        }
        LibretroSettingDef.AspectRatio -> { viewModel.cycleBuiltinAspectRatio(1); InputResult.HANDLED }
        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(1); InputResult.HANDLED }
        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(1); InputResult.HANDLED }
        LibretroSettingDef.FastForwardEnabled -> {
            viewModel.setBuiltinFastForwardEnabled(!videoState.fastForwardEnabled)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(1); InputResult.HANDLED }
        LibretroSettingDef.RewindSpeed -> { viewModel.cycleBuiltinRewindSpeed(1); InputResult.HANDLED }
        LibretroSettingDef.RewindBufferDuration -> { viewModel.cycleBuiltinRewindBufferDuration(1); InputResult.HANDLED }
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
        LibretroSettingDef.AutoSaveState -> {
            viewModel.setBuiltinAutoSaveState(!videoState.autoSaveState)
            InputResult.handled(SoundType.TOGGLE)
        }
        LibretroSettingDef.AutoRestoreState -> {
            viewModel.setBuiltinAutoRestoreState(!videoState.autoRestoreState)
            InputResult.handled(SoundType.TOGGLE)
        }
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
            is LibretroSettingDef.SettingType.Cycle -> { accessor.cycle(setting, 1); InputResult.HANDLED }
            LibretroSettingDef.SettingType.Switch -> { accessor.toggle(setting); InputResult.handled(SoundType.TOGGLE) }
        }
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.isGlobalContext) return InputResult.UNHANDLED
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
        val setting = builtinVideoItemAtFocusIndex(state.focusedIndex, state.builtinVideo) ?: return InputResult.UNHANDLED
        if (setting.type !is LibretroSettingDef.SettingType.Cycle) return InputResult.UNHANDLED
        if (state.builtinVideo.isGlobalContext) {
            return cycleGlobal(setting, state, direction)
        }
        return cyclePlatform(setting, state, direction)
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
        LibretroSettingDef.Rotation -> { viewModel.cycleBuiltinRotation(direction); InputResult.HANDLED }
        LibretroSettingDef.OverscanCrop -> { viewModel.cycleBuiltinOverscanCrop(direction); InputResult.HANDLED }
        LibretroSettingDef.FastForwardSpeed -> { viewModel.cycleBuiltinFastForwardSpeed(direction); InputResult.HANDLED }
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
