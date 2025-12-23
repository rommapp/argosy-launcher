package com.nendo.argosy.ui.filebrowser

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class FileBrowserInputHandler(
    private val viewModel: FileBrowserViewModel,
    private val onDismiss: () -> Unit
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
        viewModel.switchPane(-1)
        return InputResult.HANDLED
    }

    override fun onRight(): InputResult {
        viewModel.switchPane(1)
        return InputResult.HANDLED
    }

    override fun onConfirm(): InputResult {
        viewModel.confirmFocusedItem()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (viewModel.isAtVolumeRoot()) {
            onDismiss()
        } else {
            viewModel.goUp()
        }
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        viewModel.selectCurrentDirectory()
        return InputResult.HANDLED
    }
}
