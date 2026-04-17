package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class FirstRunInputHandler(
    private val viewModel: FirstRunViewModel,
    private val onComplete: () -> Unit,
    private val onRequestStorage: () -> Unit,
    private val onRequestNotifications: () -> Unit,
    private val onRequestOverlay: () -> Unit,
    private val onRequestUsageStats: () -> Unit,
    private val onChooseFolder: () -> Unit,
    private val onChooseImageCacheFolder: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        return if (viewModel.moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onLeft(): InputResult {
        return if (viewModel.moveButtonFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onRight(): InputResult {
        return if (viewModel.moveButtonFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentStep == FirstRunStep.COMPLETE) {
            viewModel.completeSetup(onDone = onComplete)
            return InputResult.HANDLED
        }
        viewModel.handleConfirm(
            onRequestStorage = onRequestStorage,
            onRequestNotifications = onRequestNotifications,
            onRequestOverlay = onRequestOverlay,
            onRequestUsageStats = onRequestUsageStats,
            onChooseFolder = onChooseFolder,
            onChooseImageCacheFolder = onChooseImageCacheFolder
        )
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentStep == FirstRunStep.WELCOME) {
            return InputResult.UNHANDLED
        }
        if (state.currentStep == FirstRunStep.PLATFORM_SELECT) {
            // Platform selection commits toggles to the DB as the user navigates,
            // so "back" is interpreted as "done" rather than "undo".
            viewModel.proceedFromPlatformSelect()
            return InputResult.HANDLED
        }
        viewModel.previousStep()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentStep == FirstRunStep.PLATFORM_SELECT) {
            viewModel.toggleAllPlatforms()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
