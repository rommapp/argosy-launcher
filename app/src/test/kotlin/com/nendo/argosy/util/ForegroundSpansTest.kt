package com.nendo.argosy.util

import com.nendo.argosy.util.PermissionHelper.ActivityTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundSpansTest {

    private fun spans(vararg transitions: ActivityTransition) =
        PermissionHelper.spansFromTransitions(transitions.toList(), fromMs = 100L, toMs = 200L)

    private fun resumed(at: Long, activity: String = GAME) = ActivityTransition(at, activity, true)
    private fun paused(at: Long, activity: String = GAME) = ActivityTransition(at, activity, false)

    @Test
    fun `an activity resumed before the window and never paused covers the whole window`() {
        assertEquals(listOf(100L..200L), spans(resumed(50)))
    }

    @Test
    fun `an activity resumed inside the window starts its span there`() {
        assertEquals(listOf(110L..200L), spans(resumed(110)))
    }

    @Test
    fun `leaving and returning splits the window`() {
        assertEquals(listOf(100L..130L, 160L..200L), spans(resumed(50), paused(130), resumed(160)))
    }

    @Test
    fun `a span that ended before the window contributes nothing`() {
        assertEquals(emptyList<LongRange>(), spans(resumed(10), paused(40)))
    }

    @Test
    fun `a second activity pausing does not close the span while the first is still resumed`() {
        assertEquals(
            listOf(100L..180L),
            spans(resumed(50, GAME), resumed(60, COMPANION), paused(120, COMPANION), paused(180, GAME))
        )
    }

    @Test
    fun `overlapping activities keep one span until the last one pauses`() {
        assertEquals(
            listOf(110L..170L),
            spans(resumed(110, GAME), resumed(120, COMPANION), paused(150, GAME), paused(170, COMPANION))
        )
    }

    @Test
    fun `a pause with no matching resume does not close a span`() {
        assertEquals(listOf(100L..200L), spans(resumed(50, GAME), paused(130, COMPANION)))
    }

    private companion object {
        const val GAME = "com.example.GameActivity"
        const val COMPANION = "com.example.CompanionActivity"
    }
}
