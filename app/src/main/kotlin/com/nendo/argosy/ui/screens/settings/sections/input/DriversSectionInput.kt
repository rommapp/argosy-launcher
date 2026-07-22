package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

internal class DriversSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onUp(): InputResult {
        return if (viewModel.moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val focused = state.focusedIndex
        if (focused in state.drivers.groups.indices) {
            viewModel.openDriverPicker(focused)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult = InputResult.UNHANDLED
}
