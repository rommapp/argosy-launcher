package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections

internal class InterfaceSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay, state.display.hasPhysicalSecondaryDisplay, state.display.dualScreenEnabled)
        return when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
            InterfaceItem.AccentColor -> { viewModel.resetToDefaultColor(); InputResult.HANDLED }
            InterfaceItem.SecondaryColor -> { viewModel.resetToDefaultSecondaryColor(); InputResult.HANDLED }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay, state.display.hasPhysicalSecondaryDisplay, state.display.dualScreenEnabled)
        if (viewModel.jumpToPrevSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay, state.display.hasPhysicalSecondaryDisplay, state.display.dualScreenEnabled)
        if (viewModel.jumpToNextSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val layoutState = InterfaceLayoutState(state.display, state.ambientAudio.enabled, state.ambientAudio.isFolder, state.sounds.enabled, state.display.hasSecondaryDisplay, state.display.hasPhysicalSecondaryDisplay, state.display.dualScreenEnabled)
        val hueStep = SettingsInputHandler.HUE_STEP
        when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
            InterfaceItem.AccentColor -> { viewModel.adjustHue(direction * hueStep); return InputResult.HANDLED }
            InterfaceItem.SecondaryColor -> { viewModel.adjustSecondaryHue(direction * hueStep); return InputResult.HANDLED }
            InterfaceItem.GridDensity -> { viewModel.cycleGridDensity(direction); return InputResult.HANDLED }
            InterfaceItem.Theme -> { viewModel.cycleThemeMode(direction); return InputResult.HANDLED }
            InterfaceItem.UiScale -> { viewModel.adjustUiScale(direction * 5); return InputResult.HANDLED }
            InterfaceItem.DimAfter -> { viewModel.adjustScreenDimmerTimeout(direction); return InputResult.HANDLED }
            InterfaceItem.DimLevel -> { viewModel.adjustScreenDimmerLevel(direction); return InputResult.HANDLED }
            InterfaceItem.DisplayRoles -> { viewModel.cycleDisplayRoleOverride(direction); return InputResult.HANDLED }
            InterfaceItem.BgmVolume -> if (state.ambientAudio.enabled) { viewModel.adjustAmbientAudioVolume(direction); return InputResult.HANDLED }
            InterfaceItem.UiSoundsVolume -> if (state.sounds.enabled) { viewModel.adjustSoundVolume(direction); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
