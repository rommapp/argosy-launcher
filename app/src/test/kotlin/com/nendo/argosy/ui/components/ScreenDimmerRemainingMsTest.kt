package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

private const val TIMEOUT_MS = 120_000L

class ScreenDimmerRemainingMsTest {

    @Test
    fun `activity just now waits the full timeout`() {
        assertEquals(TIMEOUT_MS, screenDimmerRemainingMs(lastActivityAtMs = 5_000L, nowMs = 5_000L, timeoutMs = TIMEOUT_MS))
    }

    @Test
    fun `elapsed idle time comes off the wait`() {
        assertEquals(
            TIMEOUT_MS - 45_000L,
            screenDimmerRemainingMs(lastActivityAtMs = 5_000L, nowMs = 50_000L, timeoutMs = TIMEOUT_MS)
        )
    }

    @Test
    fun `idle past the timeout dims without waiting`() {
        assertEquals(0L, screenDimmerRemainingMs(lastActivityAtMs = 5_000L, nowMs = 5_000L + TIMEOUT_MS, timeoutMs = TIMEOUT_MS))
        assertEquals(0L, screenDimmerRemainingMs(lastActivityAtMs = 5_000L, nowMs = 900_000L, timeoutMs = TIMEOUT_MS))
    }
}
