package com.nendo.argosy.hardware

/**
 * The LED brightness levels a d-pad press moves between, in percent: every percent to 10, every
 * 5 to 50, then every 10 to 100. A level between two steps moves to the next step in that direction.
 */
object LedBrightnessSteps {
    const val MAX = 100

    private val levels: List<Int> = (0..10).toList() + (15..50 step 5) + (60..MAX step 10)

    /**
     * The level one step from [current] in the sign of [direction], or [current] at either end.
     */
    fun step(current: Int, direction: Int): Int = when {
        direction > 0 -> levels.firstOrNull { it > current } ?: current
        direction < 0 -> levels.lastOrNull { it < current } ?: current
        else -> current
    }
}
