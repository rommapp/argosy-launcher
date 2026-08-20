package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
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
                if (state.coreOptions.coresForCurrentPlatform.isEmpty()) return InputResult.HANDLED
                viewModel.requestEnumPicker(item.key)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            is CoreOptionItem.Option -> {
                if (item.values.size > 1) {
                    viewModel.requestEnumPicker(item.optionKey)
                    InputResult.handled(SoundType.OPEN_MODAL)
                } else {
                    InputResult.HANDLED
                }
            }
            is CoreOptionItem.DownloadCore -> {
                val core = state.coreOptions.selectedCore
                if (core != null) viewModel.downloadCoreWithNotification(core.coreId)
                InputResult.HANDLED
            }
            is CoreOptionItem.DeleteCore -> {
                val core = state.coreOptions.selectedCore
                if (core != null) viewModel.requestDeleteCore(core.coreId)
                InputResult.handled(SoundType.OPEN_MODAL)
            }
            is CoreOptionItem.ResetAll -> {
                viewModel.resetAllCoreOptions()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onSecondaryAction(): InputResult {
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
