package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel

internal class DriversSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onUp(): InputResult {
        viewModel.moveFocus(-1)
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        viewModel.moveFocus(1)
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value.drivers
        if (state.expandedGroupIndex < 0) return InputResult.UNHANDLED
        viewModel.driversDelegate.moveReleaseFocus(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value.drivers
        if (state.expandedGroupIndex < 0) return InputResult.UNHANDLED
        viewModel.driversDelegate.moveReleaseFocus(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        val drivers = state.drivers
        if (drivers.activeDownload != null) {
            viewModel.driversDelegate.dismissActiveDownload()
            return InputResult.HANDLED
        }
        val focused = state.focusedIndex
        if (focused !in drivers.groups.indices) return InputResult.HANDLED
        if (drivers.expandedGroupIndex == focused) {
            viewModel.downloadFocusedDriverArtifact()
        } else {
            viewModel.driversDelegate.toggleGroupExpanded(focused)
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val drivers = viewModel.uiState.value.drivers
        if (drivers.activeDownload != null) {
            viewModel.driversDelegate.dismissActiveDownload()
            return InputResult.HANDLED
        }
        if (drivers.expandedGroupIndex >= 0) {
            viewModel.driversDelegate.toggleGroupExpanded(drivers.expandedGroupIndex)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
