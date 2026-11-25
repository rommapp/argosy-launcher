package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

class LedBrightnessStepsTest {

    private fun walk(from: Int, direction: Int): List<Int> =
        generateSequence(from) { LedBrightnessSteps.step(it, direction).takeIf { next -> next != it } }.toList()

    @Test
    fun `up from zero goes 1 percent to 10, 5 percent to 50, then 10 percent`() {
        assertEquals(
            (0..10).toList() + listOf(15, 20, 25, 30, 35, 40, 45, 50, 60, 70, 80, 90, 100),
            walk(0, 1)
        )
    }

    @Test
    fun `down from full retraces the same levels`() {
        assertEquals(walk(0, 1).reversed(), walk(100, -1))
    }

    @Test
    fun `a level between steps moves to the neighbouring step`() {
        assertEquals(60, LedBrightnessSteps.step(53, 1))
        assertEquals(50, LedBrightnessSteps.step(53, -1))
        assertEquals(15, LedBrightnessSteps.step(12, 1))
        assertEquals(10, LedBrightnessSteps.step(12, -1))
    }

    @Test
    fun `the ends hold`() {
        assertEquals(0, LedBrightnessSteps.step(0, -1))
        assertEquals(100, LedBrightnessSteps.step(100, 1))
    }
}
