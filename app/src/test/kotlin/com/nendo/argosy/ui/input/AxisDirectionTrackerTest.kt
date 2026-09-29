package com.nendo.argosy.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AxisDirectionTrackerTest {

    private fun edgesForX(vararg samples: Float): List<GamepadEvent> {
        val tracker = AxisDirectionTracker()
        return samples.asList().mapNotNull { tracker.update(it, 0f) }
    }

    @Test
    fun `a flick to full deflection and back emits exactly one Right`() {
        assertEquals(listOf(GamepadEvent.Right), edgesForX(0f, 1f, 0f))
    }

    @Test
    fun `a sample dipping between the exit and enter thresholds does not re-fire`() {
        assertEquals(listOf(GamepadEvent.Right), edgesForX(0f, 0.6f, 0.45f, 0.6f, 0f))
    }

    @Test
    fun `a hat press and release emits exactly one Right`() {
        assertEquals(listOf(GamepadEvent.Right), edgesForX(0f, 1f, 1f, 0f))
    }

    @Test
    fun `a direction is left only below the exit threshold`() {
        assertEquals(listOf(GamepadEvent.Right, GamepadEvent.Right), edgesForX(0.6f, 0.29f, 0.6f))
    }

    @Test
    fun `rolling straight from one direction to another emits the new direction`() {
        val tracker = AxisDirectionTracker()
        assertEquals(GamepadEvent.Right, tracker.update(1f, 0f))
        assertEquals(GamepadEvent.Up, tracker.update(0f, -1f))
        assertNull(tracker.update(0f, 0f))
    }

    @Test
    fun `vertical deflection wins while neither axis is held`() {
        val tracker = AxisDirectionTracker()
        assertEquals(GamepadEvent.Down, tracker.update(0.8f, 0.8f))
    }

    @Test
    fun `a held direction survives a stronger deflection on the other axis`() {
        val tracker = AxisDirectionTracker()
        assertEquals(GamepadEvent.Right, tracker.update(1f, 0f))
        assertNull(tracker.update(0.6f, -1f))
        assertEquals(GamepadEvent.Right, tracker.direction)
    }

    @Test
    fun `reset forgets the held direction so the next deflection is an edge again`() {
        val tracker = AxisDirectionTracker()
        assertEquals(GamepadEvent.Right, tracker.update(1f, 0f))
        tracker.reset()
        assertEquals(GamepadEvent.Right, tracker.update(1f, 0f))
    }
}
