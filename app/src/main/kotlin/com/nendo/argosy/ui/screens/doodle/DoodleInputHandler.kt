package com.nendo.argosy.ui.screens.doodle

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class DoodleInputHandler(
    private val viewModel: DoodleViewModel,
    private val onOpenKeyboard: () -> Unit,
    private val onBack: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu -> {
                viewModel.movePostMenuFocus(-1)
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.moveDiscardDialogFocus(-1)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.CANVAS -> {
                viewModel.moveCursor(0, -1)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.PALETTE -> {
                viewModel.movePaletteFocus(0, -1)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu -> {
                viewModel.movePostMenuFocus(1)
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.moveDiscardDialogFocus(1)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.CANVAS -> {
                viewModel.moveCursor(0, 1)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.PALETTE -> {
                viewModel.movePaletteFocus(0, 1)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu || state.showDiscardDialog -> InputResult.UNHANDLED
            state.currentSection == DoodleSection.CANVAS -> {
                viewModel.moveCursor(-1, 0)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.PALETTE -> {
                viewModel.movePaletteFocus(-1, 0)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.SIZE -> {
                viewModel.moveSizeFocus(-1)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu || state.showDiscardDialog -> InputResult.UNHANDLED
            state.currentSection == DoodleSection.CANVAS -> {
                viewModel.moveCursor(1, 0)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.PALETTE -> {
                viewModel.movePaletteFocus(1, 0)
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.SIZE -> {
                viewModel.moveSizeFocus(1)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu -> {
                val shouldExit = viewModel.confirmPostMenuSelection()
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                val shouldDiscard = viewModel.confirmDiscardDialogSelection()
                viewModel.hideDiscardDialog()
                if (shouldDiscard) {
                    onBack()
                }
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.CANVAS -> {
                viewModel.drawAtCursor()
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.PALETTE -> {
                viewModel.selectPaletteColor()
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.SIZE -> {
                viewModel.confirmSizeSelection()
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.CAPTION -> {
                onOpenKeyboard()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showPostMenu -> {
                viewModel.hidePostMenu()
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.hideDiscardDialog()
                InputResult.HANDLED
            }
            state.currentSection != DoodleSection.CANVAS -> {
                viewModel.setSection(DoodleSection.CANVAS)
                InputResult.HANDLED
            }
            state.hasContent -> {
                viewModel.showDiscardDialog()
                InputResult.HANDLED
            }
            else -> {
                onBack()
                InputResult.HANDLED
            }
        }
    }

    override fun onMenu(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostMenu && !state.showDiscardDialog) {
            viewModel.showPostMenu()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onSelect(): InputResult {
        return onMenu()
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostMenu && !state.showDiscardDialog && state.currentSection == DoodleSection.CANVAS) {
            viewModel.cycleTool()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostMenu && !state.showDiscardDialog) {
            viewModel.previousSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostMenu && !state.showDiscardDialog) {
            viewModel.nextSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onRightStickClick(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostMenu && !state.showDiscardDialog) {
            viewModel.cycleZoom()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
