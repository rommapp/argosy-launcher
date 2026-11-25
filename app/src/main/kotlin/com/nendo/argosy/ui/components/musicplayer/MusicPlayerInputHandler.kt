package com.nendo.argosy.ui.components.musicplayer

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class MusicPlayerInputHandler(
    private val viewModel: MusicPlayerViewModel,
    private val onOpenRommSignIn: (() -> Unit)?,
    private val onOpenNowPlaying: (() -> Unit)? = null
) : InputHandler {

    private val browseOpen: Boolean get() = viewModel.uiState.value.browse != null

    override fun onUp(): InputResult = move(-1)

    override fun onDown(): InputResult = move(1)

    private fun move(delta: Int): InputResult {
        val moved = if (browseOpen) viewModel.moveBrowseFocus(delta) else viewModel.moveRow(delta)
        return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private val volumeFocused: Boolean
        get() = viewModel.uiState.value.focusedRow == MusicPlayerRow.VOLUME

    private fun horizontal(delta: Int): InputResult {
        if (browseOpen) return InputResult.handled(SoundType.SILENT)
        val moved = viewModel.moveHorizontal(delta)
        return when {
            moved -> InputResult.HANDLED
            volumeFocused -> InputResult.handled(SoundType.BOUNDARY)
            else -> InputResult.handled(SoundType.SILENT)
        }
    }

    override fun onConfirm(): InputResult {
        if (browseOpen) {
            viewModel.confirmBrowse()
            return InputResult.HANDLED
        }
        if (volumeFocused) return InputResult.toggled(viewModel.toggleMusicEnabled())
        if (viewModel.uiState.value.focusedRow == MusicPlayerRow.NOW_PLAYING) {
            val open = onOpenNowPlaying ?: return InputResult.handled(SoundType.BOUNDARY)
            open()
            return InputResult.HANDLED
        }
        return if (viewModel.confirmRow()) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    override fun onSecondaryAction(): InputResult {
        if (!viewModel.needsRommSignIn) return InputResult.UNHANDLED
        val signIn = onOpenRommSignIn ?: return InputResult.handled(SoundType.BOUNDARY)
        signIn()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (!browseOpen) {
            return if (viewModel.leaveTrackList()) InputResult.HANDLED else InputResult.UNHANDLED
        }
        viewModel.closeBrowse()
        return InputResult.handled(SoundType.CLOSE_MODAL)
    }
}
