package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections

internal class InterfaceSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = InterfaceLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, interfaceMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, interfaceMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToPrevSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToNextSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val layoutState = layoutState()
        when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
            InterfaceItem.GridDensity -> { viewModel.cycleGridDensity(direction); return InputResult.HANDLED }
            InterfaceItem.UiScale -> { viewModel.adjustUiScale(direction * 5); return InputResult.HANDLED }
            InterfaceItem.DimAfter -> { viewModel.adjustScreenDimmerTimeout(direction); return InputResult.HANDLED }
            InterfaceItem.DimLevel -> { viewModel.adjustScreenDimmerLevel(direction); return InputResult.HANDLED }
            InterfaceItem.DisplayRoles -> { viewModel.cycleDisplayRoleOverride(direction); return InputResult.HANDLED }
            InterfaceItem.DualScreenEnabled ->
                return toggleLeftRight(direction, state.display.dualScreenEnabled) { viewModel.setDualScreenEnabled(it) }
            InterfaceItem.ScreenDimmer ->
                return toggleLeftRight(direction, state.storage.screenDimmerEnabled) { viewModel.toggleScreenDimmer() }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
