package com.nendo.argosy.ui.input

/**
 * Edge detector for one two-axis directional input. [update] returns the direction entered on
 * this sample, or null when the sample changes nothing or returns to neutral. A direction is
 * entered at [ENTER_THRESHOLD] and left only below [EXIT_THRESHOLD].
 */
class AxisDirectionTracker {
    var direction: GamepadEvent? = null
        private set

    fun update(x: Float, y: Float): GamepadEvent? {
        val previous = direction
        if (previous != null && stillHeld(previous, x, y)) return null

        val next = when {
            y <= -ENTER_THRESHOLD -> GamepadEvent.Up
            y >= ENTER_THRESHOLD -> GamepadEvent.Down
            x <= -ENTER_THRESHOLD -> GamepadEvent.Left
            x >= ENTER_THRESHOLD -> GamepadEvent.Right
            else -> null
        }
        direction = next
        return next
    }

    fun reset() {
        direction = null
    }

    private fun stillHeld(held: GamepadEvent, x: Float, y: Float): Boolean = when (held) {
        GamepadEvent.Up -> y <= -EXIT_THRESHOLD
        GamepadEvent.Down -> y >= EXIT_THRESHOLD
        GamepadEvent.Left -> x <= -EXIT_THRESHOLD
        GamepadEvent.Right -> x >= EXIT_THRESHOLD
        else -> false
    }

    companion object {
        const val ENTER_THRESHOLD = 0.5f
        const val EXIT_THRESHOLD = 0.3f
    }
}
