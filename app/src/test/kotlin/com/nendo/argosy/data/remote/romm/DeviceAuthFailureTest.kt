package com.nendo.argosy.data.remote.romm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAuthFailureTest {
    @Test
    fun `terminal failures retain structured reasons`() = runTest {
        for (kind in listOf(RomMErrorKind.SESSION_CHANGED, RomMErrorKind.IDENTITY_UNAVAILABLE)) {
            val outcome = pollDeviceAuthUntilResolved(1, 300) {
                DeviceAuthPoll.Failed(kind.name, retryable = false, kind = kind)
            } as DeviceAuthOutcome.Failed
            assertEquals(kind, outcome.kind)
        }
    }

    @Test
    fun `exhausted retries retain the final failure reason`() = runTest {
        var polls = 0
        val outcome = pollDeviceAuthUntilResolved(1, 300) {
            polls++
            DeviceAuthPoll.Failed("retry", kind = RomMErrorKind.IDENTITY_UNAVAILABLE)
        } as DeviceAuthOutcome.Failed
        assertEquals(5, polls)
        assertEquals(RomMErrorKind.IDENTITY_UNAVAILABLE, outcome.kind)
    }

    @Test
    fun `cancellation is not converted into pairing failure`() = runTest {
        val result = runCatching {
            pollDeviceAuthUntilResolved(1, 300) { throw CancellationException("cancelled") }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
