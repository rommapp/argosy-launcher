package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RunPlayTimeTickTest {
    @Test
    fun `a fresh run waits a full second`() {
        assertEquals(1_000L, untilRunPlayTimeChanges(0L))
    }

    @Test
    fun `under an hour waits for the rest of the second`() {
        assertEquals(250L, untilRunPlayTimeChanges(105_750L))
    }

    @Test
    fun `the last second before an hour still ticks by the second`() {
        assertEquals(500L, untilRunPlayTimeChanges(3_599_500L))
    }

    @Test
    fun `from an hour on waits for the rest of the minute`() {
        assertEquals(45_000L, untilRunPlayTimeChanges(3_615_000L))
    }

    @Test
    fun `exactly on an hour waits a full minute`() {
        assertEquals(60_000L, untilRunPlayTimeChanges(3_600_000L))
    }
}
