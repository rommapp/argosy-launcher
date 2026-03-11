package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.AmbientLedItem
import com.nendo.argosy.ui.screens.settings.sections.ambientLedItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.ambientLedSections

internal class AmbientLedSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (viewModel.jumpToPrevSection(ambientLedSections(state.display))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (viewModel.jumpToNextSection(ambientLedSections(state.display))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val hueStep = SettingsInputHandler.HUE_STEP
        when (ambientLedItemAtFocusIndex(state.focusedIndex, state.display)) {
            AmbientLedItem.Brightness -> {
                viewModel.adjustAmbientLedBrightness(direction * 5)
                return InputResult.HANDLED
            }
            AmbientLedItem.CustomColorHue -> {
                viewModel.adjustAmbientLedCustomColorHue((direction * hueStep).toInt())
                return InputResult.HANDLED
            }
            AmbientLedItem.TransitionSpeed -> {
                viewModel.cycleAmbientLedTransitionMs(direction)
                return InputResult.HANDLED
            }
            AmbientLedItem.ScreenColorMode -> {
                viewModel.cycleAmbientLedColorMode(direction)
                return InputResult.HANDLED
            }
            else -> return InputResult.UNHANDLED
        }
    }
}
