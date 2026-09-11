package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.PendingRomMPlaySession
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PlaySessionBackfillTest {

    private val playSessionDao: PlaySessionDao = mockk {
        coEvery { relinkOrphans(any()) } returns 0
    }
    private val uploader: RomMPlaySessionUploader = mockk {
        every { canUpload } returns true
    }
    private val syncPreferencesRepository: SyncPreferencesRepository = mockk {
        coEvery { getRommUserId() } returns 7L
        coEvery { getRommPlaySessionBackfillDone() } returns null
        coEvery { setRommPlaySessionBackfillDone(any()) } returns Unit
        coEvery { setRommPlaySessionLastUpload(any()) } returns Unit
    }
    private val connectionManager: RomMConnectionManager = mockk {
        every { connectionState } returns MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { getDeviceId() } returns "dev-1"
    }
    private val pull: PlaySessionPull = mockk {
        coEvery { run() } returns PlaySessionPull.Summary(0, 0)
    }
    private val backfill = PlaySessionBackfill(playSessionDao, uploader, syncPreferencesRepository, connectionManager, pull)

    private fun pending(id: Long) = PendingRomMPlaySession(
        session = PlaySessionEntity(
            id = id,
            userId = null,
            gameId = 1L,
            igdbId = null,
            gameTitle = "Test Game",
            platformSlug = "snes",
            startTime = Instant.parse("2026-05-15T12:00:00Z"),
            endTime = Instant.parse("2026-05-15T12:30:00Z"),
            deviceId = "device-x",
            deviceManufacturer = "Acme",
            deviceModel = "ZX-90",
            ownerUserId = 7L
        ),
        rommId = 99L
    )

    @Test
    fun `walks pending rows by id cursor until none remain and sums the batches`() = runTest {
        val first = (1L..100L).map { pending(it) }
        val second = (101L..130L).map { pending(it) }
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns first
        coEvery { playSessionDao.getPendingForRomM(7L, 100L, 100) } returns second
        coEvery { playSessionDao.getPendingForRomM(7L, 130L, 100) } returns emptyList()
        coEvery { uploader.upload(first) } returns RomMPlaySessionUploader.UploadResult.Success(90, 8, 2)
        coEvery { uploader.upload(second) } returns RomMPlaySessionUploader.UploadResult.Success(30, 0, 0)

        val summary = backfill.run()

        assertEquals(PlaySessionBackfill.Summary(sent = 120, duplicates = 8, failed = 2), summary)
    }

    @Test
    fun `refused rows advance the cursor instead of being reselected`() = runTest {
        val batch = listOf(pending(5L), pending(6L))
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns batch
        coEvery { playSessionDao.getPendingForRomM(7L, 6L, 100) } returns emptyList()
        coEvery { uploader.upload(batch) } returns RomMPlaySessionUploader.UploadResult.Success(0, 0, 2)

        val summary = backfill.run()

        assertEquals(PlaySessionBackfill.Summary(sent = 0, duplicates = 0, failed = 2), summary)
        coVerify(exactly = 1) { uploader.upload(any()) }
    }

    @Test
    fun `an upload error stops the run and is reported`() = runTest {
        val batch = listOf(pending(1L))
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns batch
        coEvery { uploader.upload(batch) } returns RomMPlaySessionUploader.UploadResult.Error("Ingest returned 500")

        val summary = backfill.run()

        assertEquals("Ingest returned 500", summary.stoppedBy)
        coVerify(exactly = 0) { playSessionDao.getPendingForRomM(7L, 1L, 100) }
    }

    @Test
    fun `orphaned sessions are relinked for the owner before pending rows are read`() = runTest {
        coEvery { playSessionDao.relinkOrphans(7L) } returns 3
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns emptyList()

        backfill.run()

        coVerifyOrder {
            playSessionDao.relinkOrphans(7L)
            playSessionDao.getPendingForRomM(7L, 0L, 100)
        }
    }

    @Test
    fun `nothing pending uploads nothing`() = runTest {
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns emptyList()

        val summary = backfill.run()

        assertEquals(PlaySessionBackfill.Summary(0, 0, 0), summary)
        coVerify(exactly = 0) { uploader.upload(any()) }
    }

    @Test
    fun `first connect runs the backfill and records the user and device it ran for`() = runTest {
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns emptyList()

        backfill.runOnceAfterConnect()

        coVerify(exactly = 1) { syncPreferencesRepository.setRommPlaySessionBackfillDone("7:dev-1") }
    }

    @Test
    fun `first connect pulls the other devices' sessions once the backfill is recorded`() = runTest {
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns emptyList()

        backfill.runOnceAfterConnect()

        coVerify(exactly = 1) { pull.run() }
    }

    @Test
    fun `a recorded run for the same user and device is not repeated`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionBackfillDone() } returns "7:dev-1"

        backfill.runOnceAfterConnect()

        coVerify(exactly = 0) { playSessionDao.getPendingForRomM(any(), any(), any()) }
        coVerify(exactly = 0) { syncPreferencesRepository.setRommPlaySessionBackfillDone(any()) }
        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `a recorded run for another device runs again`() = runTest {
        coEvery { syncPreferencesRepository.getRommPlaySessionBackfillDone() } returns "7:dev-0"
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns emptyList()

        backfill.runOnceAfterConnect()

        coVerify(exactly = 1) { syncPreferencesRepository.setRommPlaySessionBackfillDone("7:dev-1") }
    }

    @Test
    fun `a run that stopped early is not recorded as done`() = runTest {
        val batch = listOf(pending(1L))
        coEvery { playSessionDao.getPendingForRomM(7L, 0L, 100) } returns batch
        coEvery { uploader.upload(batch) } returns RomMPlaySessionUploader.UploadResult.Error("offline")

        backfill.runOnceAfterConnect()

        coVerify(exactly = 0) { syncPreferencesRepository.setRommPlaySessionBackfillDone(any()) }
        coVerify(exactly = 0) { pull.run() }
    }

    @Test
    fun `no device id yet defers the run without recording it`() = runTest {
        every { connectionManager.getDeviceId() } returns null

        backfill.runOnceAfterConnect()

        coVerify(exactly = 0) { playSessionDao.getPendingForRomM(any(), any(), any()) }
        coVerify(exactly = 0) { syncPreferencesRepository.setRommPlaySessionBackfillDone(any()) }
    }
}
