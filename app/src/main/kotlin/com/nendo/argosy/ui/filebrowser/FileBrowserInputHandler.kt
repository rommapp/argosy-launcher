package com.nendo.argosy.ui.filebrowser

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class FileBrowserInputHandler(
    private val viewModel: FileBrowserViewModel,
    private val onDismiss: () -> Unit,
    private val onRequestPermission: () -> Unit,
    private val onUseCurrentFolder: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        return if (viewModel.moveFocus(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocus(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
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
        if (!viewModel.hasPermission()) {
            onRequestPermission()
        } else {
            viewModel.confirmFocusedItem()
        }
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (!viewModel.hasPermission() || viewModel.isAtVolumeRoot()) {
            onDismiss()
        } else {
            viewModel.goUp()
        }
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        onUseCurrentFolder()
        return InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult {
        viewModel.showCreateFolderDialog()
        return InputResult.HANDLED
    }
}
