package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundClockTest {

    private var now = 0L
    private val clock = ForegroundClock { now }

    @Test
    fun `counts only the time between a resume and a pause`() {
        clock.reset(running = true)
        now = 10_000
        clock.pause()
        now = 25_000
        clock.resume()
        now = 30_000
        clock.pause()
        now = 90_000

        assertEquals(15_000, clock.elapsedMs())
    }

    @Test
    fun `a second resume while running does not restart the count`() {
        clock.reset(running = false)
        clock.resume()
        now = 4_000
        clock.resume()
        now = 9_000

        assertEquals(9_000, clock.elapsedMs())
    }

    @Test
    fun `a pause while paused adds nothing`() {
        clock.reset(running = true)
        now = 3_000
        clock.pause()
        now = 8_000
        clock.pause()

        assertEquals(3_000, clock.elapsedMs())
    }

    @Test
    fun `reset drops the previous session and starts paused or running`() {
        clock.reset(running = true)
        now = 50_000
        clock.reset(running = false)
        now = 60_000
        assertEquals(0, clock.elapsedMs())

        clock.reset(running = true)
        now = 62_000
        assertEquals(2_000, clock.elapsedMs())
    }

    @Test
    fun `active time is the smaller of foreground and screen-on time`() {
        assertEquals(30_000L to 70_000L, splitActivePlayTime(100_000, 30_000, 80_000))
        assertEquals(20_000L to 80_000L, splitActivePlayTime(100_000, 90_000, 20_000))
        assertEquals(50_000L to 50_000L, splitActivePlayTime(100_000, 50_000, 50_000))
    }

    @Test
    fun `an unmeasured value counts as the whole session`() {
        assertEquals(40_000L to 60_000L, splitActivePlayTime(100_000, 40_000, -1))
        assertEquals(100_000L to 0L, splitActivePlayTime(100_000, -1, -1))
    }

    @Test
    fun `active time never exceeds the session`() {
        assertEquals(100_000L to 0L, splitActivePlayTime(100_000, 150_000, 150_000))
    }
}
