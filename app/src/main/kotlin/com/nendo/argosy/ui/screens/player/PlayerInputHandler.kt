package com.nendo.argosy.ui.screens.player

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

/**
 * The player's whole gamepad vocabulary.
 *
 * The player lives in its own activity and owns its window's keys outright, so this is the only
 * handler in play - there is no dispatcher stack behind it to leak into. That is why an open
 * overlay is answered by an early return over every direction rather than by a partial guard: the
 * first unguarded direction would otherwise walk the transport bar behind the list.
 *
 * The first press with the chrome hidden brings the chrome back and does nothing else. Acting on it
 * would mean a viewer who reached for the pad to see where they are has instead seeked.
 */
class PlayerInputHandler(
    private val viewModel: PlayerViewModel
) : InputHandler {

    private val state get() = viewModel.uiState.value
    private val chrome get() = viewModel.chrome

    override fun onUp(): InputResult = whenReady {
        if (state.overlay != PlayerOverlay.NONE) {
            chrome.moveOverlaySelection(-1)
        } else {
            chrome.setFocusRow(PlayerRow.SCRUBBER)
        }
        InputResult.HANDLED
    }

    override fun onDown(): InputResult = whenReady {
        if (state.overlay != PlayerOverlay.NONE) {
            chrome.moveOverlaySelection(1)
        } else {
            chrome.setFocusRow(PlayerRow.CONTROLS)
        }
        InputResult.HANDLED
    }

    override fun onLeft(): InputResult = whenReady {
        if (state.overlay == PlayerOverlay.NONE) horizontal(-1)
        InputResult.HANDLED
    }

    override fun onRight(): InputResult = whenReady {
        if (state.overlay == PlayerOverlay.NONE) horizontal(1)
        InputResult.HANDLED
    }

    override fun onConfirm(): InputResult = whenReady {
        if (state.autoplayCountdownSeconds != null) {
            viewModel.confirmAutoplay()
            return@whenReady InputResult.HANDLED
        }
        if (state.errorMessage != null) {
            viewModel.retry()
            return@whenReady InputResult.HANDLED
        }
        if (state.overlay != PlayerOverlay.NONE) {
            viewModel.confirmOverlaySelection()
            return@whenReady InputResult.HANDLED
        }
        when (state.focusRow) {
            PlayerRow.SCRUBBER -> viewModel.togglePlayPause()
            PlayerRow.CONTROLS -> viewModel.activateControl(state.focusedControl)
        }
        InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (state.autoplayCountdownSeconds != null) {
            viewModel.cancelAutoplay()
            return InputResult.HANDLED
        }
        if (state.overlay == PlayerOverlay.NONE) viewModel.requestExit() else chrome.closeOverlay()
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult {
        if (state.overlay == PlayerOverlay.NONE) chrome.toggle()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult = whenReady {
        if (state.overlay == PlayerOverlay.NONE && state.chapters.isNotEmpty()) {
            chrome.openOverlay(PlayerOverlay.CHAPTERS)
        }
        InputResult.HANDLED
    }

    override fun onSecondaryAction(): InputResult = whenReady {
        if (state.overlay == PlayerOverlay.NONE && state.subtitleTracks.isNotEmpty()) {
            chrome.openOverlay(PlayerOverlay.SUBTITLE_TRACKS)
        }
        InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult = shoulderSeek(-1)

    override fun onNextSection(): InputResult = shoulderSeek(1)

    override fun onPrevTrigger(): InputResult = triggerSeek(-1)

    override fun onNextTrigger(): InputResult = triggerSeek(1)

    /**
     * L1 and R1 move the film by the standard skip step on every title, chapters or not. Chapter
     * navigation lives on the chapter overlay, which onContextMenu opens.
     */
    private fun shoulderSeek(direction: Int): InputResult {
        if (state.overlay != PlayerOverlay.NONE) return InputResult.HANDLED
        chrome.show()
        viewModel.skipBy(direction)
        return InputResult.HANDLED
    }

    private fun triggerSeek(direction: Int): InputResult {
        if (state.overlay != PlayerOverlay.NONE) return InputResult.HANDLED
        chrome.show()
        viewModel.shuttleBy(direction)
        return InputResult.HANDLED
    }

    private fun horizontal(direction: Int) {
        when (state.focusRow) {
            PlayerRow.SCRUBBER -> viewModel.nudgeScrub(direction)
            PlayerRow.CONTROLS -> chrome.moveControlFocus(direction)
        }
    }

    /**
     * Swallows the press that only wakes the chrome. An overlay is exempt because it is already on
     * screen, and so is an error, which is waiting on an answer rather than hiding one.
     */
    private inline fun whenReady(block: () -> InputResult): InputResult {
        val current = state
        if (current.overlay != PlayerOverlay.NONE || current.errorMessage != null) return block()
        if (!current.isChromeVisible) {
            chrome.show()
            return InputResult.HANDLED
        }
        return block()
    }
}
