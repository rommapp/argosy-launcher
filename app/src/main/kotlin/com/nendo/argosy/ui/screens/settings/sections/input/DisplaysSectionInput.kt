package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.DisplaysItem
import com.nendo.argosy.ui.screens.settings.sections.DisplaysLayoutState
import com.nendo.argosy.ui.screens.settings.sections.displaysItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.displaysMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.displaysSections

internal class DisplaysSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = DisplaysLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, displaysMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, displaysMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToPrevSection(displaysSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToNextSection(displaysSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val layoutState = layoutState()
        when (displaysItemAtFocusIndex(state.focusedIndex, layoutState)) {
            DisplaysItem.DimAfter -> { viewModel.adjustScreenDimmerTimeout(direction); return InputResult.HANDLED }
            DisplaysItem.DimLevel -> { viewModel.adjustScreenDimmerLevel(direction); return InputResult.HANDLED }
            DisplaysItem.DisplayRoles -> { viewModel.cycleDisplayRoleOverride(direction); return InputResult.HANDLED }
            DisplaysItem.DualScreenEnabled ->
                return toggleLeftRight(direction, state.display.dualScreenEnabled) { viewModel.setDualScreenEnabled(it) }
            DisplaysItem.ScreenDimmer ->
                return toggleLeftRight(direction, state.storage.screenDimmerEnabled) { viewModel.toggleScreenDimmer() }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
