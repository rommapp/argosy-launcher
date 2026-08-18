package com.nendo.argosy.ui.input

import com.nendo.argosy.core.input.SoundType

/**
 * Turns a handled input into what the user feels and hears.
 *
 * Held apart from [InputDispatcher] because the companion display routes its own key events and
 * never passes through the dispatcher: with the feedback living inside it, the second screen was
 * silent and still. Both paths use this instead, so a press means the same thing on either display.
 *
 * Repeated boundary presses latch to silence, so holding a direction against the end of a list
 * buzzes once rather than on every repeat.
 */
class InputFeedbackPlayer(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {

    private var boundaryLatched = false

    fun play(event: GamepadEvent, result: InputResult) {
        if (!result.handled) return

        when (event) {
            GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right -> {
                val override = latchBoundary(result.soundOverride)
                if (override != SoundType.SILENT) {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                }
                soundManager?.play(override ?: SoundType.NAVIGATE)
            }
            GamepadEvent.PrevSection, GamepadEvent.NextSection,
            GamepadEvent.PrevTrigger, GamepadEvent.NextTrigger -> {
                val override = latchBoundary(result.soundOverride)
                if (override != SoundType.SILENT) {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                }
                soundManager?.play(override ?: SoundType.SECTION_CHANGE)
            }
            GamepadEvent.Confirm, GamepadEvent.LongConfirm -> {
                hapticManager?.vibrate(HapticPattern.SELECTION)
                soundManager?.play(result.soundOverride ?: SoundType.SELECT)
            }
            GamepadEvent.Back -> {
                soundManager?.play(result.soundOverride ?: SoundType.BACK)
            }
            else -> Unit
        }
    }

    private fun latchBoundary(override: SoundType?): SoundType? {
        if (override != SoundType.BOUNDARY) {
            boundaryLatched = false
            return override
        }
        if (boundaryLatched) return SoundType.SILENT
        boundaryLatched = true
        return override
    }
}
