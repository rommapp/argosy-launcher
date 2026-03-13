package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem
import com.nendo.argosy.ui.screens.settings.sections.createEmulatorsLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.emulatorsItemAtFocusIndex

internal class EmulatorsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun getItemAtFocus(state: SettingsUiState): EmulatorsItem? {
        val layoutInfo = createEmulatorsLayoutInfo(
            platforms = state.emulators.platforms,
            canAutoAssign = state.emulators.canAutoAssign,
            builtinLibretroEnabled = state.emulators.builtinLibretroEnabled
        )
        return emulatorsItemAtFocusIndex(state.focusedIndex, layoutInfo)
    }

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        val item = getItemAtFocus(state)
        if (item is EmulatorsItem.PlatformItem) {
            val config = item.config
            if (config.hasInstalledEmulators && config.showSavePath &&
                state.emulators.platformSubFocusIndex == 1
            ) {
                viewModel.movePlatformSubFocus(-1, 1)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value
        val item = getItemAtFocus(state)
        if (item is EmulatorsItem.PlatformItem) {
            val config = item.config
            if (config.hasInstalledEmulators && config.showSavePath &&
                state.emulators.platformSubFocusIndex == 0
            ) {
                viewModel.movePlatformSubFocus(1, 1)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    override fun onLeft(): InputResult = cycleCoreOrExtension(-1)

    override fun onRight(): InputResult = cycleCoreOrExtension(1)

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        return when (val item = getItemAtFocus(state)) {
            EmulatorsItem.BuiltinVideo -> { viewModel.navigateToBuiltinVideo(); InputResult.HANDLED }
            EmulatorsItem.BuiltinControls -> { viewModel.navigateToBuiltinControls(); InputResult.HANDLED }
            EmulatorsItem.BuiltinCores -> { viewModel.navigateToCoreManagement(); InputResult.HANDLED }
            EmulatorsItem.BuiltinCoreOptions -> { viewModel.navigateToCoreOptions(); InputResult.HANDLED }
            EmulatorsItem.BuiltinToggle -> {
                viewModel.setBuiltinLibretroEnabled(!state.emulators.builtinLibretroEnabled)
                InputResult.handled(SoundType.TOGGLE)
            }
            EmulatorsItem.CheckForUpdates -> { viewModel.forceCheckEmulatorUpdates(); InputResult.HANDLED }
            EmulatorsItem.AutoAssign -> { viewModel.autoAssignAllEmulators(); InputResult.HANDLED }
            is EmulatorsItem.PlatformItem -> {
                val config = item.config
                if (config.hasInstalledEmulators && config.showSavePath &&
                    state.emulators.platformSubFocusIndex == 1
                ) {
                    viewModel.showSavePathModal(config)
                } else {
                    viewModel.showEmulatorPicker(config)
                }
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        val item = getItemAtFocus(state)
        if (item is EmulatorsItem.PlatformItem) {
            val config = item.config
            if (config.showSavePath && config.hasInstalledEmulators) {
                viewModel.showSavePathModal(config)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult = cycleDisplayTarget(-1)

    override fun onNextSection(): InputResult = cycleDisplayTarget(1)

    private fun cycleCoreOrExtension(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val item = getItemAtFocus(state)
        if (item is EmulatorsItem.PlatformItem) {
            val config = item.config
            if (config.showCoreSelection) {
                viewModel.cycleCoreForPlatform(config, direction)
                return InputResult.HANDLED
            }
            if (config.showExtensionSelection) {
                viewModel.cycleExtensionForPlatform(config, direction)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    private fun cycleDisplayTarget(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val item = getItemAtFocus(state)
        if (item is EmulatorsItem.PlatformItem && item.config.showDisplayTargetOption) {
            viewModel.cycleDisplayTarget(item.config, direction)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
