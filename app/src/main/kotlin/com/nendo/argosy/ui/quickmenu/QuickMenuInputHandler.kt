package com.nendo.argosy.ui.quickmenu

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class QuickMenuInputHandler(
    private val viewModel: QuickMenuViewModel,
    private val onGameSelect: (Long) -> Unit,
    private val onDismiss: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused) {
            if (state.focusedContentIndex == 0 || state.selectedOrb == QuickMenuOrb.RANDOM) {
                viewModel.exitContent()
            } else {
                viewModel.moveContentUp()
            }
        }
        return InputResult.HANDLED
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (!state.contentFocused) {
            viewModel.enterContent()
        } else {
            viewModel.moveContentDown()
        }
        return InputResult.HANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (!state.contentFocused) {
            viewModel.moveOrbLeft()
        }
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (!state.contentFocused) {
            viewModel.moveOrbRight()
        }
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused) {
            viewModel.getSelectedGameId()?.let { gameId ->
                viewModel.hide()
                onGameSelect(gameId)
            }
        } else {
            viewModel.enterContent()
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused) {
            viewModel.exitContent()
        } else {
            viewModel.hide()
            onDismiss()
        }
        return InputResult.HANDLED
    }

    override fun onLeftStickClick(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused) {
            viewModel.exitContent()
        } else {
            viewModel.hide()
            onDismiss()
        }
        return InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused && state.selectedOrb == QuickMenuOrb.RANDOM) {
            viewModel.rerollRandom()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED

        if (state.contentFocused && state.selectedOrb == QuickMenuOrb.RANDOM) {
            viewModel.rerollRandom()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onMenu(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED
        return InputResult.HANDLED
    }

    override fun onSelect(): InputResult {
        val state = viewModel.uiState.value
        if (!state.isVisible) return InputResult.UNHANDLED
        return InputResult.HANDLED
    }
}
