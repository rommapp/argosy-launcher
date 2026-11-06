package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Test

class GyroAimTrackerTest {

    private val tolerance = 0.0001f
    private val range = GyroAimTracker.BASE_RANGE_RADIANS

    private fun tracker(invertX: Boolean = false, invertY: Boolean = false, sensitivity: Int = 100) =
        GyroAimTracker().apply { configure(invertX, invertY, sensitivity) }

    @Test
    fun `turning left and tilting up aim left and up`() {
        val t = tracker()

        t.rotate(aboutScreenX = range / 2, aboutScreenY = range / 2, dtSeconds = 1f)

        assertEquals(-0.5f, t.stick().first, tolerance)
        assertEquals(-0.5f, t.stick().second, tolerance)
    }

    @Test
    fun `inverting each axis flips only that axis`() {
        val x = tracker(invertX = true)
        val y = tracker(invertY = true)

        x.rotate(range / 2, range / 2, 1f)
        y.rotate(range / 2, range / 2, 1f)

        assertEquals(0.5f, x.stick().first, tolerance)
        assertEquals(-0.5f, x.stick().second, tolerance)
        assertEquals(-0.5f, y.stick().first, tolerance)
        assertEquals(0.5f, y.stick().second, tolerance)
    }

    @Test
    fun `doubling sensitivity reaches the same stick position with half the turn`() {
        val t = tracker(sensitivity = 200)

        t.rotate(0f, range / 4, 1f)

        assertEquals(-0.5f, t.stick().first, tolerance)
    }

    @Test
    fun `the aim stops at the edge of the stick`() {
        val t = tracker()

        t.rotate(0f, range * 10, 1f)

        assertEquals(-1f, t.stick().first, tolerance)
    }

    @Test
    fun `recentering puts the aim back in the middle`() {
        val t = tracker()
        t.rotate(range / 3, range / 3, 1f)

        t.recenter()

        assertEquals(0f to 0f, t.stick())
    }

    @Test
    fun `a stick hold sets where gyro aiming continues from`() {
        val t = tracker()

        t.holdAt(0.25f, -0.5f)

        assertEquals(0.25f, t.stick().first, tolerance)
        assertEquals(-0.5f, t.stick().second, tolerance)
    }
}
