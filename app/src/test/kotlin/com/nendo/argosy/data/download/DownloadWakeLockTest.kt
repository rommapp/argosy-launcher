package com.nendo.argosy.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadWakeLockTest {
    @Test
    fun `waiting and backoff release until actual work resumes`() = runTest {
        val lock = FakeLock()
        val renewal = DownloadWakeLock(backgroundScope, 100, lock::acquire, lock::release)
        renewal.update(listOf(DownloadState.DOWNLOADING), false)
        runCurrent()
        assertTrue(lock.held)

        for (state in listOf(DownloadState.WAITING_FOR_NETWORK, DownloadState.RETRYING)) {
            renewal.update(listOf(state), false)
            assertFalse(lock.held)
            advanceTimeBy(1_000)
            runCurrent()
            assertFalse(lock.held)
            assertEquals(1, lock.acquisitions)
        }

        renewal.update(listOf(DownloadState.DOWNLOADING), false)
        runCurrent()
        assertTrue(lock.held)
        assertEquals(2, lock.acquisitions)
        renewal.stop()
    }

    @Test
    fun `another active ROM or busy media retains renewal`() = runTest {
        val lock = FakeLock()
        val renewal = DownloadWakeLock(backgroundScope, 100, lock::acquire, lock::release)
        for (state in listOf(DownloadState.DOWNLOADING, DownloadState.EXTRACTING, DownloadState.MOVING)) {
            renewal.update(listOf(DownloadState.WAITING_FOR_NETWORK, state), false)
            runCurrent()
            assertTrue(lock.held)
        }
        renewal.update(listOf(DownloadState.RETRYING), true)
        advanceTimeBy(100)
        runCurrent()
        assertTrue(lock.held)
        assertEquals(2, lock.acquisitions)
        renewal.stop()
    }

    @Test
    fun `repeated snapshots keep a single renewal loop`() = runTest {
        val lock = FakeLock()
        val renewal = DownloadWakeLock(backgroundScope, 100, lock::acquire, lock::release)
        repeat(20) { renewal.update(listOf(DownloadState.DOWNLOADING), false) }
        runCurrent()
        assertEquals(1, lock.acquisitions)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, lock.acquisitions)
        renewal.stop()
        advanceTimeBy(1_000)
        runCurrent()
        assertFalse(lock.held)
        assertEquals(2, lock.acquisitions)
    }

    @Test
    fun `cancelled old loop cannot release a new lease`() = runTest {
        val lock = FakeLock()
        val renewal = DownloadWakeLock(backgroundScope, 100, lock::acquire, lock::release)
        renewal.update(listOf(DownloadState.DOWNLOADING), false)
        runCurrent()
        renewal.stop()
        renewal.update(listOf(DownloadState.DOWNLOADING), false)
        runCurrent()
        assertTrue(lock.held)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(3, lock.acquisitions)
        renewal.stop()
    }

    @Test
    fun `scope cancellation releases and prevents reacquisition`() = runTest {
        val scope = CoroutineScope(coroutineContext + Job())
        val lock = FakeLock()
        val renewal = DownloadWakeLock(scope, 100, lock::acquire, lock::release)
        renewal.update(listOf(DownloadState.DOWNLOADING), false)
        runCurrent()
        scope.cancel()
        runCurrent()
        assertFalse(lock.held)
        renewal.update(listOf(DownloadState.DOWNLOADING), true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, lock.acquisitions)
    }

    private class FakeLock {
        var held = false
        var acquisitions = 0

        fun acquire() {
            held = true
            acquisitions++
        }

        fun release() {
            held = false
        }
    }
}
