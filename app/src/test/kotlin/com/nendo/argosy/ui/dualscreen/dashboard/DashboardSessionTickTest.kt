package com.nendo.argosy.ui.dualscreen.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardSessionTickTest {
    @Test
    fun `a fresh session waits a full minute`() {
        assertEquals(60_000L, untilNextMinute(0L))
    }

    @Test
    fun `a session part way through a minute waits for the rest of it`() {
        assertEquals(15_000L, untilNextMinute(105_000L))
    }

    @Test
    fun `a session exactly on a minute waits a full minute`() {
        assertEquals(60_000L, untilNextMinute(120_000L))
    }
}
