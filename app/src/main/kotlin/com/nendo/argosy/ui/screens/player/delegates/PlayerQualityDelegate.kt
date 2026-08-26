package com.nendo.argosy.ui.screens.player.delegates

import com.nendo.argosy.ui.screens.player.PlayerOverlay
import com.nendo.argosy.ui.screens.player.PlayerUiState
import com.nendo.argosy.ui.screens.player.QualityWheel
import com.nendo.argosy.ui.screens.player.clampBitrateToLadder
import com.nendo.argosy.ui.screens.player.indexOfValue
import com.nendo.argosy.ui.screens.player.qualityWheelOptions
import com.nendo.argosy.ui.screens.player.valueFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Drives the quality wheels: resolution, frame rate and bit rate, minus any wheel the source
 * leaves nothing to choose on - the wheel index walks [PlayerUiState.qualityWheels], not the enum.
 *
 * The wheels edit a draft rather than the stream, because every applied change costs a
 * renegotiation and a picture stall; turning a wheel three notches must cost one reload, not three.
 * Applying commits the draft for this viewing only - the saved preference in settings is what the
 * next playback starts from, since a one-off drop on a weak connection is not a statement about how
 * everything should look from now on.
 *
 * Rotating the resolution wheel re-derives the bitrate ladder and re-fits the bitrate draft to it;
 * the frame-rate wheel affects nothing but itself.
 */
class PlayerQualityDelegate(
    private val state: MutableStateFlow<PlayerUiState>,
    private val openOverlay: (PlayerOverlay) -> Unit,
    private val closeOverlay: () -> Unit,
    private val reload: () -> Unit
) {

    fun openWheels() {
        if (state.value.qualityWheels.isEmpty()) return
        state.update {
            it.copy(qualityDraft = it.activeQuality, qualityWheelIndex = 0)
        }
        openOverlay(PlayerOverlay.QUALITY)
    }

    /**
     * Moves the highlight between the wheels actually on screen, wrapping at the ends as list
     * navigation does everywhere else. Only the values inside a wheel clamp.
     */
    fun moveWheelFocus(delta: Int) {
        state.update {
            val wheels = it.qualityWheels
            if (wheels.isEmpty()) it
            else it.copy(qualityWheelIndex = (it.qualityWheelIndex + delta).mod(wheels.size))
        }
    }

    fun focusWheel(wheel: QualityWheel) {
        state.update {
            val index = it.qualityWheels.indexOf(wheel)
            if (index < 0) it else it.copy(qualityWheelIndex = index)
        }
    }

    /**
     * Steps the focused wheel one notch, clamping at the ends: at the top entry up does nothing
     * and at the bottom entry down does nothing. A wheel is a value range with a floor and a
     * ceiling, so this is the deliberate exception to the wrap-with-.mod() convention; do not
     * "correct" it back to wrapping.
     */
    fun rotateFocusedWheel(delta: Int) {
        val current = state.value
        val draft = current.qualityDraft ?: return
        val wheel = current.qualityWheels.getOrNull(current.qualityWheelIndex) ?: return
        val options = qualityWheelOptions(wheel, current.sourceVideo, draft)
        if (options.size <= 1) return
        val index = (options.indexOfValue(draft.valueFor(wheel)) + delta)
            .coerceIn(0, options.lastIndex)
        setWheelValue(wheel, options[index].value)
    }

    fun setWheelValue(wheel: QualityWheel, value: Int?) {
        state.update { current ->
            val draft = current.qualityDraft ?: return@update current
            val next = when (wheel) {
                QualityWheel.RESOLUTION ->
                    clampBitrateToLadder(draft.copy(maxHeight = value), current.sourceVideo)
                QualityWheel.FRAMERATE -> draft.copy(maxFramerate = value)
                QualityWheel.BITRATE -> draft.copy(maxBitrateKbps = value)
            }
            current.copy(qualityDraft = next)
        }
    }

    /**
     * Commits the draft. A draft equal to what is already in force closes the picker without
     * touching the stream, so opening the wheels and backing out through Apply costs nothing.
     */
    fun applyDraft() {
        val current = state.value
        val draft = current.qualityDraft
        closeOverlay()
        if (draft == null || draft == current.activeQuality) return
        state.update { it.copy(sessionQuality = draft) }
        reload()
    }
}
