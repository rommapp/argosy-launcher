package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.CoreOptionItem
import com.nendo.argosy.ui.screens.settings.sections.coreOptionsItemAtFocusIndex

internal class CoreOptionsSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = handleCycle(-1)

    override fun onRight(): InputResult = handleCycle(1)

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        return when (val item = coreOptionsItemAtFocusIndex(state.focusedIndex, state.coreOptions)) {
            is CoreOptionItem.CoreSelector -> {
                viewModel.cycleCoreSelector(1)
                InputResult.HANDLED
            }
            is CoreOptionItem.Option -> {
                viewModel.cycleCoreOptionValue(item.optionKey, 1)
                InputResult.HANDLED
            }
            is CoreOptionItem.NotInstalledNotice -> {
                val core = state.coreOptions.selectedCore
                if (core != null) viewModel.downloadCore(core.coreId)
                InputResult.HANDLED
            }
            is CoreOptionItem.ResetAll -> {
                viewModel.resetAllCoreOptions()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        val item = coreOptionsItemAtFocusIndex(state.focusedIndex, state.coreOptions)
        if (item is CoreOptionItem.Option && item.isOverridden) {
            viewModel.resetCoreOption(item.optionKey)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.coreOptions.availablePlatforms.isNotEmpty()) {
            viewModel.cycleCoreOptionsPlatformContext(-1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.coreOptions.availablePlatforms.isNotEmpty()) {
            viewModel.cycleCoreOptionsPlatformContext(1)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun handleCycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (val item = coreOptionsItemAtFocusIndex(state.focusedIndex, state.coreOptions)) {
            is CoreOptionItem.CoreSelector -> {
                viewModel.cycleCoreSelector(direction)
                InputResult.HANDLED
            }
            is CoreOptionItem.Option -> {
                viewModel.cycleCoreOptionValue(item.optionKey, direction)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }
}
