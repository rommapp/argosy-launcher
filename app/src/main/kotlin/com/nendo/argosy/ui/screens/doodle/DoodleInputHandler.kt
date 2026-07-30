package com.nendo.argosy.ui.screens.doodle

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
class DoodleInputHandler(
    private val viewModel: DoodleViewModel,
    private val onNavigateBack: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker && !state.gamePickerSearchFocused -> {
                if (state.gamePickerFocusIndex == 0) {
                    viewModel.focusGamePickerSearch()
                } else {
                    viewModel.moveGamePickerFocus(-1)
                }
                InputResult.HANDLED
            }
            state.showGamePicker -> InputResult.HANDLED
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
            state.showGamePicker && state.gamePickerSearchFocused -> {
                viewModel.focusGamePickerList()
                InputResult.HANDLED
            }
            state.showGamePicker -> {
                viewModel.moveGamePickerFocus(1)
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
            state.showGamePicker || state.showDiscardDialog -> InputResult.HANDLED
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
            state.showGamePicker || state.showDiscardDialog -> InputResult.HANDLED
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
            state.showGamePicker && !state.gamePickerSearchFocused -> {
                viewModel.selectGame()
                InputResult.HANDLED
            }
            state.showGamePicker -> InputResult.HANDLED
            state.showDiscardDialog -> {
                val shouldDiscard = viewModel.confirmDiscardDialogSelection()
                viewModel.hideDiscardDialog()
                if (shouldDiscard) {
                    onNavigateBack()
                }
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.CANVAS -> {
                if (state.isDrawing) {
                    viewModel.stopDrawing()
                } else {
                    viewModel.drawAtCursor()
                }
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
            state.currentSection == DoodleSection.UNDO -> {
                viewModel.undo()
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.REDO -> {
                viewModel.redo()
                InputResult.HANDLED
            }
            state.currentSection == DoodleSection.GAME -> {
                viewModel.showGamePicker()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker -> {
                viewModel.hideGamePicker()
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.hideDiscardDialog()
                InputResult.HANDLED
            }
            state.isDrawing -> {
                viewModel.cancelDrawing()
                InputResult.HANDLED
            }
            state.hasContent -> {
                viewModel.showDiscardDialog()
                InputResult.HANDLED
            }
            else -> {
                onNavigateBack()
                InputResult.HANDLED
            }
        }
    }

    override fun onMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.showDiscardDialog || state.showGamePicker) return InputResult.HANDLED
        if (state.hasContent) {
            viewModel.done()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onSelect(): InputResult {
        return onMenu()
    }

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (state.showDiscardDialog || state.showGamePicker) return InputResult.HANDLED
        return when (state.currentSection) {
            DoodleSection.CANVAS -> {
                viewModel.cycleTool()
                InputResult.HANDLED
            }
            DoodleSection.GAME -> {
                if (state.linkedGameTitle != null) {
                    viewModel.clearLinkedGame()
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.showDiscardDialog || state.showGamePicker) return InputResult.HANDLED
        viewModel.previousSection()
        return InputResult.HANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (state.showDiscardDialog || state.showGamePicker) return InputResult.HANDLED
        viewModel.nextSection()
        return InputResult.HANDLED
    }

    override fun onRightStickClick(): InputResult {
        val state = viewModel.uiState.value
        if (state.showDiscardDialog || state.showGamePicker) return InputResult.HANDLED
        viewModel.cycleZoom()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult = swallowWhenModalOpen()
    override fun onLongConfirm(): InputResult = swallowWhenModalOpen()
    override fun onLeftStickClick(): InputResult = swallowWhenModalOpen()
    override fun onPrevTrigger(): InputResult = swallowWhenModalOpen()
    override fun onNextTrigger(): InputResult = swallowWhenModalOpen()

    private fun swallowWhenModalOpen(): InputResult {
        val state = viewModel.uiState.value
        return if (state.showDiscardDialog || state.showGamePicker) {
            InputResult.HANDLED
        } else {
            InputResult.UNHANDLED
        }
    }
}
