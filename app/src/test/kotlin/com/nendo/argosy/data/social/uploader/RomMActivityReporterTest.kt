package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RomMActivityReporterTest {

    private val api: RomMApi = mockk()
    private val connectionManager: RomMConnectionManager = mockk {
        every { getApi() } returns api
        every { getDeviceId() } returns "dev-1"
        every { isConnected() } returns true
        coEvery { reregisterDevice() } returns Unit
    }
    private val gameDao: GameDao = mockk()
    private val reporter = RomMActivityReporter(connectionManager, gameDao)

    private fun game(rommId: Long?) = GameEntity(
        id = 1L,
        title = "Test Game",
        sortTitle = "test game",
        platformId = 1L,
        platformSlug = "snes",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = GameSource.LOCAL_ONLY
    )

    private fun ok(): Response<ResponseBody> = Response.success(ResponseBody.create(null, ""))

    private fun rejected(code: Int, detail: String = "nope"): Response<ResponseBody> =
        Response.error(code, ResponseBody.create(null, "{\"detail\":\"$detail\"}"))

    private val heartbeat = RomMActivityReporter.HEARTBEAT_INTERVAL_MS
    private val retry = RomMActivityReporter.RETRY_INTERVAL_MS
    private val maxRetries = RomMActivityReporter.MAX_RETRIES

    @Test
    fun `successful ticks repeat on the heartbeat interval`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } answers { ok() }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(heartbeat * 3 + 1)

        coVerify(exactly = 4) { api.sendActivityHeartbeat(any()) }
        assertTrue(job.isActive)
        job.cancel()
    }

    @Test
    fun `failed tick retries on the short interval and gives up after the budget`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } answers { rejected(500) }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry * maxRetries + 1)

        coVerify(exactly = maxRetries + 1) { api.sendActivityHeartbeat(any()) }
        advanceUntilIdle()
        assertTrue(job.isCompleted)
        coVerify(exactly = maxRetries + 1) { api.sendActivityHeartbeat(any()) }
    }

    @Test
    fun `retries do not wait the full heartbeat interval`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } answers { rejected(500) }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry + 1)

        coVerify(exactly = 2) { api.sendActivityHeartbeat(any()) }
        job.cancel()
    }

    @Test
    fun `a success after failures restores the heartbeat cadence and the retry budget`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        var calls = 0
        coEvery { api.sendActivityHeartbeat(any()) } answers {
            calls++
            if (calls <= 2) rejected(503) else ok()
        }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry * 2 + 1)
        coVerify(exactly = 3) { api.sendActivityHeartbeat(any()) }

        advanceTimeBy(retry)
        coVerify(exactly = 3) { api.sendActivityHeartbeat(any()) }

        advanceTimeBy(heartbeat - retry)
        coVerify(exactly = 4) { api.sendActivityHeartbeat(any()) }

        coEvery { api.sendActivityHeartbeat(any()) } answers { rejected(503) }
        advanceTimeBy(heartbeat + retry * (maxRetries - 1) + 1)
        coVerify(exactly = 4 + maxRetries) { api.sendActivityHeartbeat(any()) }
        assertTrue(job.isActive)
        advanceTimeBy(retry)
        advanceUntilIdle()
        coVerify(exactly = 4 + maxRetries + 1) { api.sendActivityHeartbeat(any()) }
        assertTrue(job.isCompleted)
    }

    @Test
    fun `exception counts as a failed tick and is retried`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } throws java.io.IOException("offline")

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry + 1)

        coVerify(exactly = 2) { api.sendActivityHeartbeat(any()) }
        job.cancel()
    }

    @Test
    fun `unregistered device triggers one re-registration before the next retry`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } answers {
            rejected(404, "Device dev-1 not found for this user")
        }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry * maxRetries + 1)
        advanceUntilIdle()

        coVerify(exactly = 1) { connectionManager.reregisterDevice() }
        coVerifyOrder {
            api.sendActivityHeartbeat(any())
            connectionManager.reregisterDevice()
            api.sendActivityHeartbeat(any())
        }
        assertTrue(job.isCompleted)
    }

    @Test
    fun `a 404 for an unknown rom does not touch device registration`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        coEvery { api.sendActivityHeartbeat(any()) } answers { rejected(404, "ROM 99 not found") }

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry + 1)

        coVerify(exactly = 0) { connectionManager.reregisterDevice() }
        job.cancel()
    }

    @Test
    fun `game without a rom id sends nothing and returns at once`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = null)

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceUntilIdle()

        coVerify(exactly = 0) { api.sendActivityHeartbeat(any()) }
        assertTrue(job.isCompleted)
    }

    @Test
    fun `game whose rom left the library sends nothing`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = -5L)

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceUntilIdle()

        coVerify(exactly = 0) { api.sendActivityHeartbeat(any()) }
        assertFalse(job.isActive)
    }

    @Test
    fun `disconnected server is retried rather than ending the loop`() = runTest {
        coEvery { gameDao.getById(1L) } returns game(rommId = 99L)
        every { connectionManager.isConnected() } returns false

        val job = launch { reporter.runHeartbeatLoop(1L) }
        advanceTimeBy(retry + 1)
        assertTrue(job.isActive)

        every { connectionManager.isConnected() } returns true
        coEvery { api.sendActivityHeartbeat(any()) } answers { ok() }
        advanceTimeBy(retry)

        coVerify(exactly = 1) { api.sendActivityHeartbeat(any()) }
        job.cancel()
    }
}
