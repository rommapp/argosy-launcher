package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.PendingRomMPlaySession
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestPayload
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestResponse
import com.nendo.argosy.data.remote.romm.RomMPlaySessionIngestResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class RomMPlaySessionUploaderTest {

    private val api: RomMApi = mockk()
    private val connectionManager: RomMConnectionManager = mockk {
        every { getApi() } returns api
        every { getDeviceId() } returns "dev-1"
        every { isConnected() } returns true
        every { getCapabilities() } returns RomMCapabilities.from("4.9.0")
    }
    private val playSessionDao: PlaySessionDao = mockk {
        coEvery { setRommSessionId(any(), any()) } returns Unit
    }
    private val uploader = RomMPlaySessionUploader(connectionManager, playSessionDao)

    private fun pending(
        id: Long,
        rommId: Long = 99L,
        start: Instant = Instant.parse("2026-05-15T12:00:00Z"),
        end: Instant = Instant.parse("2026-05-15T12:30:00Z")
    ) = PendingRomMPlaySession(
        session = PlaySessionEntity(
            id = id,
            userId = null,
            gameId = 1L,
            igdbId = null,
            gameTitle = "Test Game",
            platformSlug = "snes",
            startTime = start,
            endTime = end,
            deviceId = "device-x",
            deviceManufacturer = "Acme",
            deviceModel = "ZX-90",
            activePlayMs = 1_000L,
            ownerUserId = 7L
        ),
        rommId = rommId
    )

    private fun respond(vararg results: RomMPlaySessionIngestResult) {
        coEvery { api.ingestPlaySessions(any()) } returns Response.success(
            RomMPlaySessionIngestResponse(results = results.toList())
        )
    }

    @Test
    fun `created result writes the server id onto the matching row`() = runTest {
        respond(RomMPlaySessionIngestResult(index = 0, status = "created", id = 501L))

        val result = uploader.upload(listOf(pending(id = 10L)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 1, duplicates = 0, failed = 0), result)
        coVerify(exactly = 1) { playSessionDao.setRommSessionId(10L, 501L) }
    }

    @Test
    fun `results are matched by index not by position`() = runTest {
        respond(
            RomMPlaySessionIngestResult(index = 1, status = "created", id = 502L),
            RomMPlaySessionIngestResult(index = 0, status = "created", id = 501L)
        )

        uploader.upload(listOf(pending(id = 10L), pending(id = 11L)))

        coVerify(exactly = 1) { playSessionDao.setRommSessionId(10L, 501L) }
        coVerify(exactly = 1) { playSessionDao.setRommSessionId(11L, 502L) }
    }

    @Test
    fun `duplicate with id stores that id`() = runTest {
        respond(RomMPlaySessionIngestResult(index = 0, status = "duplicate", id = 77L))

        val result = uploader.upload(listOf(pending(id = 10L)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 0, duplicates = 1, failed = 0), result)
        coVerify(exactly = 1) { playSessionDao.setRommSessionId(10L, 77L) }
    }

    @Test
    fun `duplicate without id still marks the row acknowledged`() = runTest {
        respond(RomMPlaySessionIngestResult(index = 0, status = "duplicate"))

        val result = uploader.upload(listOf(pending(id = 10L)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 0, duplicates = 1, failed = 0), result)
        coVerify(exactly = 1) {
            playSessionDao.setRommSessionId(10L, PlaySessionEntity.ROMM_SESSION_ID_UNKNOWN)
        }
    }

    @Test
    fun `error result leaves its row unstamped and counts as failed`() = runTest {
        respond(
            RomMPlaySessionIngestResult(index = 0, status = "error", detail = "end_time is too far in the future"),
            RomMPlaySessionIngestResult(index = 1, status = "created", id = 502L)
        )

        val result = uploader.upload(listOf(pending(id = 10L), pending(id = 11L)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 1, duplicates = 0, failed = 1), result)
        coVerify(exactly = 0) { playSessionDao.setRommSessionId(10L, any()) }
        coVerify(exactly = 1) { playSessionDao.setRommSessionId(11L, 502L) }
    }

    @Test
    fun `row the server does not answer for counts as failed and stays unstamped`() = runTest {
        respond(RomMPlaySessionIngestResult(index = 0, status = "created", id = 501L))

        val result = uploader.upload(listOf(pending(id = 10L), pending(id = 11L)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 1, duplicates = 0, failed = 1), result)
        coVerify(exactly = 0) { playSessionDao.setRommSessionId(11L, any()) }
    }

    @Test
    fun `sub-second session is neither sent nor stamped and does not shift indices`() = runTest {
        val t = Instant.parse("2026-05-15T12:00:00Z")
        val payload = slot<RomMPlaySessionIngestPayload>()
        coEvery { api.ingestPlaySessions(capture(payload)) } returns Response.success(
            RomMPlaySessionIngestResponse(
                results = listOf(RomMPlaySessionIngestResult(index = 0, status = "created", id = 501L))
            )
        )

        val result = uploader.upload(listOf(pending(id = 10L, start = t, end = t), pending(id = 11L)))

        assertEquals(1, payload.captured.sessions.size)
        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 1, duplicates = 0, failed = 0), result)
        coVerify(exactly = 0) { playSessionDao.setRommSessionId(10L, any()) }
        coVerify(exactly = 1) { playSessionDao.setRommSessionId(11L, 501L) }
    }

    @Test
    fun `batch of only dropped sessions sends nothing and stamps nothing`() = runTest {
        val t = Instant.parse("2026-05-15T12:00:00Z")

        val result = uploader.upload(listOf(pending(id = 10L, start = t, end = t)))

        assertEquals(RomMPlaySessionUploader.UploadResult.Success(sent = 0, duplicates = 0, failed = 0), result)
        coVerify(exactly = 0) { api.ingestPlaySessions(any()) }
        coVerify(exactly = 0) { playSessionDao.setRommSessionId(any(), any()) }
    }

    @Test
    fun `payload carries the device id and the rom id`() = runTest {
        val payload = slot<RomMPlaySessionIngestPayload>()
        coEvery { api.ingestPlaySessions(capture(payload)) } returns Response.success(
            RomMPlaySessionIngestResponse(
                results = listOf(RomMPlaySessionIngestResult(index = 0, status = "created", id = 501L))
            )
        )

        uploader.upload(listOf(pending(id = 10L, rommId = 42L)))

        assertEquals("dev-1", payload.captured.deviceId)
        assertEquals(42L, payload.captured.sessions.single().romId)
    }

    @Test
    fun `rejected batch stamps nothing`() = runTest {
        coEvery { api.ingestPlaySessions(any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "boom")
        )

        val result = uploader.upload(listOf(pending(id = 10L)))

        assertTrue(result is RomMPlaySessionUploader.UploadResult.Error)
        coVerify(exactly = 0) { playSessionDao.setRommSessionId(any(), any()) }
    }

    @Test
    fun `missing device id skips the upload`() = runTest {
        every { connectionManager.getDeviceId() } returns null

        val result = uploader.upload(listOf(pending(id = 10L)))

        assertTrue(result is RomMPlaySessionUploader.UploadResult.Skipped)
        coVerify(exactly = 0) { api.ingestPlaySessions(any()) }
    }
}
