package com.nendo.argosy.ui.screens.player.delegates

import com.nendo.argosy.ui.screens.player.PlayerOverlay
import com.nendo.argosy.ui.screens.player.PlayerRow
import com.nendo.argosy.ui.screens.player.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CHROME_HIDE_DELAY_MS = 4_000L

/**
 * Everything about what the player is showing rather than what it is playing: whether the chrome is
 * up, which band and which button hold the highlight, and which list is open over the picture.
 *
 * Split out because none of it touches the stream. The chrome comes and goes several times a minute
 * while a playback runs unchanged underneath it, and keeping the two apart means a change to how a
 * list scrolls cannot reach the code that negotiates a stream.
 */
class PlayerChromeDelegate(
    private val state: MutableStateFlow<PlayerUiState>,
    private val scope: CoroutineScope
) {
    private var hideJob: Job? = null

    fun show() {
        state.update { it.copy(isChromeVisible = true) }
        restartTimer()
    }

    fun toggle() {
        if (state.value.isChromeVisible) hide() else show()
    }

    fun hide() {
        hideJob?.cancel()
        hideJob = null
        state.update { it.copy(isChromeVisible = false, scrubTargetMs = null) }
    }

    /**
     * The chrome only withdraws while something is happening behind it. A paused player, an open
     * list or a scrub in progress all keep it up, because in each case the viewer is looking at the
     * chrome rather than at the film.
     */
    fun restartTimer() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(CHROME_HIDE_DELAY_MS)
            val current = state.value
            if (current.isPlaying && current.overlay == PlayerOverlay.NONE && !current.isScrubbing) {
                state.update { it.copy(isChromeVisible = false) }
            }
        }
    }

    fun cancelTimer() {
        hideJob?.cancel()
        hideJob = null
    }

    fun moveControlFocus(delta: Int) {
        val controls = state.value.controls
        if (controls.isEmpty()) return
        state.update { it.copy(controlIndex = (it.controlIndex + delta).mod(controls.size)) }
        show()
    }

    fun setControlIndex(index: Int) {
        state.update { it.copy(controlIndex = index, focusRow = PlayerRow.CONTROLS) }
        show()
    }

    fun setFocusRow(row: PlayerRow) {
        state.update { it.copy(focusRow = row) }
        show()
    }

    /**
     * A list opens on the row that is already in force, so the first thing the viewer sees is what
     * they are currently listening to, reading or watching rather than the top of an alphabet.
     */
    fun openOverlay(overlay: PlayerOverlay) {
        val current = state.value
        val index = when (overlay) {
            PlayerOverlay.AUDIO_TRACKS -> current.audioTracks
                .indexOfFirst { it.streamIndex == current.selectedAudioStreamIndex }
                .coerceAtLeast(0)
            PlayerOverlay.SUBTITLE_TRACKS -> subtitleRow(current)
            PlayerOverlay.CHAPTERS -> current.chapters
                .indexOfLast { it.startMs <= current.positionMs }
                .coerceAtLeast(0)
            else -> 0
        }
        cancelTimer()
        state.update { it.copy(overlay = overlay, overlayIndex = index, isChromeVisible = true) }
    }

    fun openResumePrompt() {
        cancelTimer()
        state.update {
            it.copy(
                isLoading = false,
                overlay = PlayerOverlay.RESUME,
                overlayIndex = 0,
                isChromeVisible = true
            )
        }
    }

    fun closeOverlay() {
        state.update {
            it.copy(overlay = PlayerOverlay.NONE, overlayIndex = 0, subtitleNotice = null)
        }
        restartTimer()
    }

    fun moveOverlaySelection(delta: Int) {
        val size = state.value.overlayItemCount
        if (size <= 0) return
        state.update { it.copy(overlayIndex = (it.overlayIndex + delta).mod(size)) }
    }

    fun setOverlayIndex(index: Int) {
        state.update { it.copy(overlayIndex = index) }
    }

    private fun subtitleRow(current: PlayerUiState): Int {
        val selected = current.selectedSubtitleStreamIndex ?: return 0
        val position = current.subtitleTracks.indexOfFirst { it.streamIndex == selected }
        return if (position < 0) 0 else position + 1
    }
}
