package com.nendo.argosy.ui.input

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
class InputDispatcher(
    private val hapticManager: HapticFeedbackManager? = null,
    private val soundManager: SoundFeedbackManager? = null
) {
    private var activeHandler: InputHandler? = null
    private var pendingEvent: GamepadEvent? = null
    private var inputBlockedUntil: Long = 0L

    fun setActiveScreen(handler: InputHandler?) {
        activeHandler = handler
        handler?.let { processPendingEvent() }
    }

    private fun processPendingEvent() {
        pendingEvent?.let { event ->
            pendingEvent = null
            dispatch(event)
        }
    }

    fun blockInputFor(durationMs: Long) {
        inputBlockedUntil = System.currentTimeMillis() + durationMs
    }

    fun dispatch(event: GamepadEvent): Boolean {
        if (System.currentTimeMillis() < inputBlockedUntil) {
            return true
        }

        val handler = activeHandler
        if (handler == null) {
            pendingEvent = event
            return false
        }

        pendingEvent = null
        val result = dispatchToHandler(event, handler)
        playFeedback(event, result)
        return result.handled
    }

    private fun playFeedback(event: GamepadEvent, result: InputResult) {
        when (event) {
            GamepadEvent.Up, GamepadEvent.Down, GamepadEvent.Left, GamepadEvent.Right -> {
                if (result.handled) {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                    soundManager?.play(result.soundOverride ?: SoundType.NAVIGATE)
                } else {
                    hapticManager?.vibrate(HapticPattern.BOUNDARY_HIT)
                    soundManager?.play(SoundType.BOUNDARY)
                }
            }
            GamepadEvent.PrevSection, GamepadEvent.NextSection -> {
                if (result.handled) {
                    hapticManager?.vibrate(HapticPattern.FOCUS_CHANGE)
                    soundManager?.play(result.soundOverride ?: SoundType.SECTION_CHANGE)
                } else {
                    hapticManager?.vibrate(HapticPattern.BOUNDARY_HIT)
                    soundManager?.play(SoundType.BOUNDARY)
                }
            }
            GamepadEvent.Confirm -> {
                if (result.handled) {
                    hapticManager?.vibrate(HapticPattern.SELECTION)
                    soundManager?.play(result.soundOverride ?: SoundType.SELECT)
                }
            }
            GamepadEvent.Back -> {
                if (result.handled) {
                    soundManager?.play(result.soundOverride ?: SoundType.BACK)
                }
            }
            else -> {}
        }
    }

    private fun dispatchToHandler(event: GamepadEvent, handler: InputHandler): InputResult {
        return when (event) {
            GamepadEvent.Up -> handler.onUp()
            GamepadEvent.Down -> handler.onDown()
            GamepadEvent.Left -> handler.onLeft()
            GamepadEvent.Right -> handler.onRight()
            GamepadEvent.Confirm -> handler.onConfirm()
            GamepadEvent.Back -> handler.onBack()
            GamepadEvent.Menu -> handler.onMenu()
            GamepadEvent.SecondaryAction -> handler.onSecondaryAction()
            GamepadEvent.ContextMenu -> handler.onContextMenu()
            GamepadEvent.PrevSection -> handler.onPrevSection()
            GamepadEvent.NextSection -> handler.onNextSection()
            GamepadEvent.Select -> handler.onSelect()
        }
    }
}

val LocalInputDispatcher = staticCompositionLocalOf<InputDispatcher> {
    error("No InputDispatcher provided")
}

val LocalNintendoLayout = staticCompositionLocalOf { false }

val LocalSwapStartSelect = staticCompositionLocalOf { false }
