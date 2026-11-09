package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PlaySessionPullSchedulerTest {

    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val pulled = PlaySessionPull.Summary(added = 3, devices = 1)

    private val pull: PlaySessionPull = mockk {
        every { canPull } returns true
        coEvery { run() } returns pulled
    }
    private val syncPreferencesRepository: SyncPreferencesRepository = mockk {
        coEvery { getRommPlaySessionLastPull() } returns null
    }
    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val connectionManager: RomMConnectionManager = mockk {
        every { connectionState } returns this@PlaySessionPullSchedulerTest.connectionState
    }

    private fun scheduler(scope: CoroutineScope) =
        PlaySessionPullScheduler(pull, syncPreferencesRepository, connectionManager, scope) { now }

    private fun TestScope.schedulerInBackground() = scheduler(
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
    )

    @Test
    fun `a pull is due when none has succeeded yet`() {
        assertTrue(isPlaySessionPullDue(null, now))
    }

    @Test
    fun `a pull is not due inside the interval`() {
        val lastPull = now.minus(PLAY_SESSION_PULL_INTERVAL).plusSeconds(1)

        assertFalse(isPlaySessionPullDue(lastPull, now))
    }

    @Test
    fun `a pull is due once the interval has fully elapsed`() {
        assertTrue(isPlaySessionPullDue(now.minus(PLAY_SESSION_PULL_INTERVAL), now))
        assertTrue(isPlaySessionPullDue(now.minus(PLAY_SESSION_PULL_INTERVAL).minusSeconds(1), now))
    }

    @Test
    fun `a last pull stamped ahead of the clock is not due`() {
        assertFalse(isPlaySessionPullDue(now.plus(Duration.ofMinutes(1)), now))
    }

    @Test
    fun `a stale last pull runs the pull`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionLastPull() } returns now.minus(Duration.ofHours(1))

        val summary = schedulerInBackground().pullIfDue()

        assertEquals(pulled, summary)
        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a recent last pull skips the pull`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionLastPull() } returns now.minus(Duration.ofMinutes(1))

        val summary = schedulerInBackground().pullIfDue()

        assertNull(summary)
        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `no capability or connection skips the pull without reading the last pull time`() = runTest {
        every { pull.canPull } returns false

        assertNull(schedulerInBackground().pullIfDue())

        coVerify(exactly = 0) { syncPreferencesRepository.getRommPlaySessionLastPull() }
        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `connecting to RomM runs a due pull`() = runTest {
        schedulerInBackground()
        runCurrent()
        coVerify(exactly = 0) { pull.run() }

        connectionState.value = ConnectionState.Connected("4.0.0", mockk(relaxed = true))
        runCurrent()

        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a recap pull waits past the old four second window for a long pull to finish`() = runTest {
        coEvery { pull.run() } coAnswers {
            delay(12_000)
            pulled
        }

        assertTrue(schedulerInBackground().pullForRecap())
        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a recap pull ignores the cadence throttle`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionLastPull() } returns now.minus(Duration.ofMinutes(5))

        assertTrue(schedulerInBackground().pullForRecap())
        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a recap pull reuses a pull that completed moments ago`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionLastPull() } returns now.minusSeconds(30)

        assertTrue(schedulerInBackground().pullForRecap())
        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `a recap pull reports a pull that stopped early as incomplete`() = runTest {
        coEvery { pull.run() } returns PlaySessionPull.Summary(added = 1, devices = 2, stoppedBy = "Network failure")

        assertFalse(schedulerInBackground().pullForRecap())
    }

    @Test
    fun `a recap wait that runs out answers false and leaves the pull running for the next reader`() = runTest {
        coEvery { pull.run() } coAnswers {
            delay(PlaySessionPullScheduler.RECAP_PULL_TIMEOUT_MS + 10_000)
            pulled
        }
        val scheduler = schedulerInBackground()

        assertFalse(scheduler.pullForRecap())

        assertTrue(scheduler.pullForRecap())
        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a pull that throws answers false to the waiting reader`() = runTest {
        coEvery { pull.run() } throws IllegalStateException("database closed")

        assertFalse(schedulerInBackground().pullForRecap())
    }

    @Test
    fun `a recap pull without capability or connection answers false at once`() = runTest {
        every { pull.canPull } returns false

        assertFalse(schedulerInBackground().pullForRecap())

        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `requests made while a pull runs join it instead of starting another`() = runTest {
        coEvery { pull.run() } coAnswers {
            delay(2_000)
            pulled
        }
        val scheduler = schedulerInBackground()

        scheduler.requestIfDue()
        runCurrent()
        advanceTimeBy(500)
        assertTrue(scheduler.pullForRecap())

        coVerify(exactly = 1) { pull.run() }
    }
}
