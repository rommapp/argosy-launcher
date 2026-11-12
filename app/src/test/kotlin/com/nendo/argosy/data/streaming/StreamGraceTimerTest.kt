package com.nendo.argosy.data.streaming

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamGraceTimerTest {

    private val graceMs = StreamGraceTimer.DEFAULT_GRACE_MS

    @Test
    fun `the grace is five minutes`() {
        assertEquals(5 * 60 * 1000L, graceMs)
    }

    @Test
    fun `an armed timer expires once the full grace passes`() = runTest {
        var expired = 0
        val timer = StreamGraceTimer(this) { expired++ }
        timer.arm()
        advanceTimeBy(graceMs - 1)
        runCurrent()
        assertEquals(0, expired)
        assertTrue(timer.isArmed)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, expired)
    }

    @Test
    fun `disarming before the grace ends never expires`() = runTest {
        var expired = 0
        val timer = StreamGraceTimer(this) { expired++ }
        timer.arm()
        advanceTimeBy(graceMs - 1_000)
        timer.disarm()
        advanceTimeBy(graceMs * 2)
        runCurrent()
        assertEquals(0, expired)
        assertFalse(timer.isArmed)
    }

    @Test
    fun `arming again restarts the grace`() = runTest {
        var expired = 0
        val timer = StreamGraceTimer(this) { expired++ }
        timer.arm()
        advanceTimeBy(graceMs - 1_000)
        timer.arm()
        advanceTimeBy(graceMs - 1_000)
        runCurrent()
        assertEquals(0, expired)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, expired)
    }
}
