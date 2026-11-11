package com.nendo.argosy.data.download.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRetryPolicyTest {
    @Test fun exponentialBackoffAndJitter() {
        val policy = DownloadRetryPolicy { 0.5 }
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L),
            (0..7).map { policy.delayMillis(it) })
        assertEquals(800L, DownloadRetryPolicy { 0.0 }.delayMillis(0))
        assertEquals(1200L, DownloadRetryPolicy { 1.0 }.delayMillis(0))
        assertTrue(policy.delayMillis(Int.MAX_VALUE) <= 60000L)
        assertEquals(60000L, DownloadRetryPolicy { 1.0 }.delayMillis(Int.MAX_VALUE))
    }

    @Test fun retryAfterIsAnOverflowSafeFloor() {
        val policy = DownloadRetryPolicy { 0.5 }
        assertEquals(120000L, policy.delayMillis(0, "120"))
        assertEquals(900_000L, policy.delayMillis(0, "999999999999999999999999"))
        assertEquals(900_000L, policy.delayMillis(0, "86400"))
        assertEquals(120000L, policy.delayMillis(0, "Thu, 01 Jan 1970 00:02:00 GMT", 0))
        assertEquals(1000L, policy.delayMillis(0, "invalid"))
        assertEquals(1000L, policy.delayMillis(-1, "-1"))
    }
}
