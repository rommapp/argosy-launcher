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
        viewModel.moveButtonFocus(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        viewModel.moveButtonFocus(1)
        return InputResult.HANDLED
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
            return InputResult.HANDLED
        }
        if (state.currentStep == FirstRunStep.PLATFORM_SELECT) {
            if (state.platformSortMenuOpen) {
                viewModel.closePlatformSortMenu()
                return InputResult.HANDLED
            }
            if (state.platformSearchActive) {
                viewModel.closePlatformSearch()
                return InputResult.HANDLED
            }
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
        return InputResult.HANDLED
    }

    override fun onMenu() = InputResult.HANDLED
    override fun onSelect() = InputResult.HANDLED
    override fun onSecondaryAction() = InputResult.HANDLED
    override fun onPrevSection() = InputResult.HANDLED
    override fun onNextSection() = InputResult.HANDLED
    override fun onPrevTrigger() = InputResult.HANDLED
    override fun onNextTrigger() = InputResult.HANDLED
    override fun onLeftStickClick() = InputResult.HANDLED
    override fun onRightStickClick() = InputResult.HANDLED
    override fun onLongConfirm() = InputResult.HANDLED
}
