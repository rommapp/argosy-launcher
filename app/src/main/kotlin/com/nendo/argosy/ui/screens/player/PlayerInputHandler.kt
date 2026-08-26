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
 * The first press with the chrome hidden brings the chrome back and, for everything but Confirm,
 * does nothing else: acting on it would mean a viewer who reached for the pad to see where they are
 * has instead seeked. Confirm is the exception - it means pause wherever the chrome is, so it
 * toggles playback and reveals the chrome in the same press.
 */
class PlayerInputHandler(
    private val viewModel: PlayerViewModel
) : InputHandler {

    private val state get() = viewModel.uiState.value
    private val chrome get() = viewModel.chrome

    override fun onUp(): InputResult {
        if (state.showExitConfirm) return InputResult.HANDLED
        return whenReady {
            when {
                state.overlay == PlayerOverlay.QUALITY -> viewModel.quality.rotateFocusedWheel(-1)
                state.overlay != PlayerOverlay.NONE -> chrome.moveOverlaySelection(-1)
                else -> chrome.setFocusRow(PlayerRow.SCRUBBER)
            }
            InputResult.HANDLED
        }
    }

    override fun onDown(): InputResult {
        if (state.showExitConfirm) return InputResult.HANDLED
        return whenReady {
            when {
                state.overlay == PlayerOverlay.QUALITY -> viewModel.quality.rotateFocusedWheel(1)
                state.overlay != PlayerOverlay.NONE -> chrome.moveOverlaySelection(1)
                else -> chrome.setFocusRow(PlayerRow.CONTROLS)
            }
            InputResult.HANDLED
        }
    }

    override fun onLeft(): InputResult {
        if (state.showExitConfirm) {
            viewModel.moveExitConfirmFocus(-1)
            return InputResult.HANDLED
        }
        return whenReady {
            when (state.overlay) {
                PlayerOverlay.QUALITY -> viewModel.quality.moveWheelFocus(-1)
                PlayerOverlay.NONE -> horizontal(-1)
                else -> Unit
            }
            InputResult.HANDLED
        }
    }

    override fun onRight(): InputResult {
        if (state.showExitConfirm) {
            viewModel.moveExitConfirmFocus(1)
            return InputResult.HANDLED
        }
        return whenReady {
            when (state.overlay) {
                PlayerOverlay.QUALITY -> viewModel.quality.moveWheelFocus(1)
                PlayerOverlay.NONE -> horizontal(1)
                else -> Unit
            }
            InputResult.HANDLED
        }
    }

    override fun onConfirm(): InputResult {
        if (state.controlsLocked) return InputResult.HANDLED
        if (state.showExitConfirm) {
            viewModel.confirmExitSelection()
            return InputResult.HANDLED
        }
        if (state.autoplayCountdownSeconds != null) {
            viewModel.confirmAutoplay()
            return InputResult.HANDLED
        }
        if (state.errorMessage != null) {
            viewModel.retry()
            return InputResult.HANDLED
        }
        if (state.overlay != PlayerOverlay.NONE) {
            viewModel.confirmOverlaySelection()
            return InputResult.HANDLED
        }
        if (!state.isChromeVisible) {
            chrome.show()
            viewModel.togglePlayPause()
            return InputResult.HANDLED
        }
        when (state.focusRow) {
            PlayerRow.SCRUBBER -> viewModel.togglePlayPause()
            PlayerRow.CONTROLS -> viewModel.activateControl(state.focusedControl)
        }
        return InputResult.HANDLED
    }

    /**
     * B puts things away one layer at a time before it means leave: the countdown, the exit
     * confirmation, an open list, then the chrome itself, and only on a bare picture does it ask to
     * exit. An error is not a layer to put away - the chrome does not draw over one - so with an
     * error on screen B asks to exit directly.
     */
    override fun onBack(): InputResult {
        if (state.controlsLocked) {
            viewModel.registerLockedBackPress()
            return InputResult.HANDLED
        }
        if (state.autoplayCountdownSeconds != null) {
            viewModel.cancelAutoplay()
            return InputResult.HANDLED
        }
        if (state.showExitConfirm) {
            viewModel.dismissExitConfirm()
            return InputResult.HANDLED
        }
        when {
            state.overlay != PlayerOverlay.NONE -> chrome.closeOverlay()
            state.isChromeVisible && state.errorMessage == null -> chrome.hide()
            else -> viewModel.requestExit()
        }
        return InputResult.HANDLED
    }

    override fun onMenu(): InputResult {
        if (state.controlsLocked) return InputResult.HANDLED
        if (state.overlay == PlayerOverlay.NONE && !state.showExitConfirm) chrome.toggle()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        if (state.showExitConfirm) return InputResult.HANDLED
        return whenReady {
            if (state.overlay == PlayerOverlay.NONE && state.chapters.isNotEmpty()) {
                chrome.openOverlay(PlayerOverlay.CHAPTERS)
            }
            InputResult.HANDLED
        }
    }

    override fun onSecondaryAction(): InputResult {
        if (state.showExitConfirm) return InputResult.HANDLED
        return whenReady {
            if (state.overlay == PlayerOverlay.NONE && state.subtitleTracks.isNotEmpty()) {
                chrome.openOverlay(PlayerOverlay.SUBTITLE_TRACKS)
            }
            InputResult.HANDLED
        }
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
        if (state.controlsLocked) return InputResult.HANDLED
        if (state.overlay != PlayerOverlay.NONE || state.showExitConfirm) return InputResult.HANDLED
        chrome.show()
        viewModel.skipBy(direction)
        return InputResult.HANDLED
    }

    private fun triggerSeek(direction: Int): InputResult {
        if (state.controlsLocked) return InputResult.HANDLED
        if (state.overlay != PlayerOverlay.NONE || state.showExitConfirm) return InputResult.HANDLED
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
     * screen, and so is an error, which is waiting on an answer rather than hiding one. A locked
     * player swallows everything outright - the lock's whole promise is that a press neither acts
     * nor wakes the chrome.
     */
    private inline fun whenReady(block: () -> InputResult): InputResult {
        val current = state
        if (current.controlsLocked) return InputResult.HANDLED
        if (current.overlay != PlayerOverlay.NONE || current.errorMessage != null) return block()
        if (!current.isChromeVisible) {
            chrome.show()
            return InputResult.HANDLED
        }
        return block()
    }
}
