package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsItemAtFocusIndex

internal class BuiltinControlsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        return when (builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)) {
            BuiltinControlsItem.ResetAllToGlobal -> {
                viewModel.resetAllPlatformControlSettings()
                InputResult.handled(SoundType.SELECT)
            }
            BuiltinControlsItem.Rumble -> {
                if (state.builtinVideo.isGlobalContext) {
                    viewModel.setBuiltinRumbleEnabled(!state.builtinControls.rumbleEnabled)
                } else {
                    val ps = state.builtinVideo.currentPlatformContext?.let {
                        state.platformLibretro.platformSettings[it.platformId]
                    }
                    val effective = ps?.rumbleEnabled ?: state.builtinControls.rumbleEnabled
                    viewModel.updatePlatformControlSetting("rumbleEnabled", !effective)
                }
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.Motion -> {
                if (state.builtinVideo.isGlobalContext) {
                    viewModel.setBuiltinMotionEnabled(!state.builtinControls.motionEnabled)
                } else {
                    val ps = state.builtinVideo.currentPlatformContext?.let {
                        state.platformLibretro.platformSettings[it.platformId]
                    }
                    val effective = ps?.motionEnabled ?: state.builtinControls.motionEnabled
                    viewModel.updatePlatformControlSetting("motionEnabled", !effective)
                }
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.LimitHotkeysToPlayer1 -> {
                viewModel.setBuiltinLimitHotkeysToPlayer1(!state.builtinControls.limitHotkeysToPlayer1)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.ToggleFastForward -> {
                val next = if (state.builtinControls.fastForwardMode == com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE) {
                    com.nendo.argosy.data.local.entity.FastForwardMode.HOLD
                } else {
                    com.nendo.argosy.data.local.entity.FastForwardMode.TOGGLE
                }
                viewModel.setBuiltinFastForwardMode(next)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.PreserveFastForwardPitch -> {
                viewModel.setBuiltinFastForwardPreservePitch(!state.builtinControls.fastForwardPreservePitch)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.AnalogAsDpad -> {
                if (state.builtinVideo.isGlobalContext) {
                    viewModel.setBuiltinAnalogAsDpad(!state.builtinControls.analogAsDpad)
                } else {
                    val pc = state.builtinVideo.currentPlatformContext
                    val ps = pc?.let { state.platformLibretro.platformSettings[it.platformId] }
                    val platformHasAnalog = pc != null && PlatformWeightRegistry.hasAnalogStick(pc.platformSlug)
                    val effective = ps?.analogAsDpad ?: !platformHasAnalog
                    viewModel.updatePlatformControlSetting("analogAsDpad", !effective)
                }
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.DpadAsAnalog -> {
                if (state.builtinVideo.isGlobalContext) {
                    viewModel.setBuiltinDpadAsAnalog(!state.builtinControls.dpadAsAnalog)
                } else {
                    val ps = state.builtinVideo.currentPlatformContext?.let {
                        state.platformLibretro.platformSettings[it.platformId]
                    }
                    val effective = ps?.dpadAsAnalog ?: false
                    viewModel.updatePlatformControlSetting("dpadAsAnalog", !effective)
                }
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.ControllerOrder -> {
                viewModel.showControllerOrderModal()
                InputResult.handled(SoundType.SELECT)
            }
            BuiltinControlsItem.InputMapping -> {
                viewModel.showInputMappingModal()
                InputResult.handled(SoundType.SELECT)
            }
            BuiltinControlsItem.Hotkeys -> {
                viewModel.showHotkeysModal()
                InputResult.handled(SoundType.SELECT)
            }
            BuiltinControlsItem.SpeedrunStartOnReset -> {
                viewModel.setSpeedrunStartOnReset(!state.builtinControls.speedrunStartOnReset)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.SpeedrunPanelSide -> {
                viewModel.setSpeedrunPanelSide(
                    if (state.builtinControls.speedrunPanelSide == "Left") "Right" else "Left"
                )
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.SpeedrunPanelWidth -> {
                viewModel.adjustSpeedrunPanelWidth(5)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.GyroAimSensitivity -> {
                viewModel.stepBuiltinGyroAimSensitivity(1)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.GyroAimInvertX -> {
                viewModel.setBuiltinGyroAimInvertX(!state.builtinControls.gyroAimInvertX)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.GyroAimInvertY -> {
                viewModel.setBuiltinGyroAimInvertY(!state.builtinControls.gyroAimInvertY)
                InputResult.handled(SoundType.TOGGLE)
            }
            is BuiltinControlsItem.ControllerType -> InputResult.handled(SoundType.SILENT)
            else -> InputResult.UNHANDLED
        }
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value
        return when (val item = builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)) {
            is BuiltinControlsItem.ControllerType -> viewModel.stepControllerType(item.port, -1)
            BuiltinControlsItem.SelectAsBack -> stepSelectAsBack(-1)
            BuiltinControlsItem.SpeedrunPanelWidth -> {
                viewModel.adjustSpeedrunPanelWidth(-5)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.GyroAimSensitivity -> {
                viewModel.stepBuiltinGyroAimSensitivity(-1)
                InputResult.handled(SoundType.TOGGLE)
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        return when (val item = builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)) {
            is BuiltinControlsItem.ControllerType -> viewModel.stepControllerType(item.port, 1)
            BuiltinControlsItem.SelectAsBack -> stepSelectAsBack(1)
            BuiltinControlsItem.SpeedrunPanelWidth -> {
                viewModel.adjustSpeedrunPanelWidth(5)
                InputResult.handled(SoundType.TOGGLE)
            }
            BuiltinControlsItem.GyroAimSensitivity -> {
                viewModel.stepBuiltinGyroAimSensitivity(1)
                InputResult.handled(SoundType.TOGGLE)
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun stepSelectAsBack(direction: Int): InputResult {
        val modes = com.nendo.argosy.data.local.entity.SelectAsBackMode.entries
        val current = modes.indexOf(viewModel.uiState.value.builtinControls.selectAsBack)
        val next = (current + direction).coerceIn(0, modes.lastIndex)
        if (next == current) return InputResult.HANDLED
        viewModel.setBuiltinSelectAsBack(modes[next])
        return InputResult.handled(SoundType.TOGGLE)
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (state.builtinVideo.isGlobalContext) return InputResult.UNHANDLED
        val item = builtinControlsItemAtFocusIndex(state.focusedIndex, state.builtinControls)
        val platformContext = state.builtinVideo.currentPlatformContext
        val ps = platformContext?.let { state.platformLibretro.platformSettings[it.platformId] }
        val field = when (item) {
            BuiltinControlsItem.Rumble -> "rumbleEnabled"
            BuiltinControlsItem.Motion -> "motionEnabled"
            BuiltinControlsItem.AnalogAsDpad -> "analogAsDpad"
            BuiltinControlsItem.DpadAsAnalog -> "dpadAsAnalog"
            else -> null
        }
        if (field != null) {
            val hasOverride = when (item) {
                BuiltinControlsItem.Rumble -> ps?.rumbleEnabled != null
                BuiltinControlsItem.Motion -> ps?.motionEnabled != null
                BuiltinControlsItem.AnalogAsDpad -> ps?.analogAsDpad != null
                BuiltinControlsItem.DpadAsAnalog -> ps?.dpadAsAnalog != null
                else -> false
            }
            if (hasOverride) {
                viewModel.updatePlatformControlSetting(field, null)
                return InputResult.HANDLED
            }
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
}
