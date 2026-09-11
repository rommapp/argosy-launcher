package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDevice
import com.nendo.argosy.data.remote.romm.RomMPlaySession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.time.Instant

class PlaySessionPullTest {

    private val playSessionDao: PlaySessionDao = mockk {
        coEvery { insert(any()) } returns 1L
        coEvery { getLatestRommStartForDevice(any(), any()) } returns null
    }
    private val gameDao: GameDao = mockk {
        coEvery { getByRommId(any()) } returns null
    }
    private val syncPreferencesRepository: SyncPreferencesRepository = mockk {
        coEvery { getRommUserId() } returns 7L
        coEvery { setRommPlaySessionLastPull(any()) } returns Unit
    }
    private val api: RomMApi = mockk()
    private val connectionManager: RomMConnectionManager = mockk {
        every { getCapabilities() } returns mockk<RomMCapabilities> {
            every { supportsPlaySessionIngest } returns true
        }
        every { isConnected() } returns true
        every { getApi() } returns api
        every { getDeviceId() } returns "dev-this"
    }
    private val pull = PlaySessionPull(playSessionDao, gameDao, syncPreferencesRepository, connectionManager)

    private val otherDevice = RomMDevice(
        id = "dev-other",
        name = "Acme ZX-90",
        platform = "android",
        client = "argosy",
        clientVersion = "2.15.0"
    )

    private fun remote(id: Long, romId: Long = 99L) = RomMPlaySession(
        id = id,
        deviceId = "dev-other",
        romId = romId,
        startTime = "2026-05-15T12:00:00Z",
        endTime = "2026-05-15T12:30:00Z",
        durationMs = 1_500_000L
    )

    private val unresolved: suspend (Long) -> PulledGameRef = { PulledGameRef.unresolved(it) }

    @Test
    fun `sessions whose server id is already held are not inserted again`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(listOf(1L, 2L, 3L)) } returns listOf(2L)

        val added = pull.insertMissing(listOf(remote(1), remote(2), remote(3)), otherDevice, 7L, unresolved)

        assertEquals(2, added)
        val inserted = mutableListOf<PlaySessionEntity>()
        coVerify(exactly = 2) { playSessionDao.insert(capture(inserted)) }
        assertEquals(listOf(1L, 3L), inserted.map { it.rommSessionId })
    }

    @Test
    fun `a pulled row is owned, stamped with the server id and never queued for either upload`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        val captured = slot<PlaySessionEntity>()
        coEvery { playSessionDao.insert(capture(captured)) } returns 1L

        pull.insertMissing(listOf(remote(5)), otherDevice, 7L, unresolved)

        val row = captured.captured
        assertEquals(7L, row.ownerUserId)
        assertEquals(5L, row.rommSessionId)
        assertNull(row.userId)
        assertEquals("dev-other", row.deviceId)
        assertEquals("Acme ZX-90", row.deviceModel)
        assertEquals(1_500_000L, row.activePlayMs)
        assertEquals(300_000L, row.standbyMs)
        assertEquals(-99L, row.gameId)
    }

    @Test
    fun `a session without a rom is skipped`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()

        val added = pull.insertMissing(listOf(remote(1).copy(romId = null)), otherDevice, 7L, unresolved)

        assertEquals(0, added)
        coVerify(exactly = 0) { playSessionDao.insert(any()) }
    }

    @Test
    fun `run skips this device and pages the other devices until a short page`() = runTest {
        coEvery { api.getDevices() } returns Response.success(
            listOf(otherDevice.copy(id = "dev-this"), otherDevice)
        )
        val firstPage = (1L..200L).map { remote(it) }
        val secondPage = listOf(remote(201L))
        coEvery { api.getPlaySessions(null, "dev-other", null, null, 200, 0) } returns Response.success(firstPage)
        coEvery { api.getPlaySessions(null, "dev-other", null, null, 200, 200) } returns Response.success(secondPage)
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        coEvery { api.getRom(99L) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))

        val summary = pull.run()

        assertEquals(PlaySessionPull.Summary(added = 201, devices = 1), summary)
        coVerify(exactly = 0) { api.getPlaySessions(any(), "dev-this", any(), any(), any(), any()) }
        coVerify(exactly = 1) { api.getRom(99L) }
    }

    @Test
    fun `run asks only for sessions newer than the latest one held for that device`() = runTest {
        val since = Instant.parse("2026-05-01T00:00:00Z")
        coEvery { api.getDevices() } returns Response.success(listOf(otherDevice))
        coEvery { playSessionDao.getLatestRommStartForDevice("dev-other", 7L) } returns since
        coEvery { api.getPlaySessions(null, "dev-other", since.toString(), null, 200, 0) } returns Response.success(emptyList())

        val summary = pull.run()

        assertEquals(PlaySessionPull.Summary(added = 0, devices = 1), summary)
        coVerify(exactly = 1) { syncPreferencesRepository.setRommPlaySessionLastPull(any()) }
    }

    @Test
    fun `a page that fails stops the run and is reported`() = runTest {
        coEvery { api.getDevices() } returns Response.success(listOf(otherDevice))
        coEvery { api.getPlaySessions(null, "dev-other", null, null, 200, 0) } returns
            Response.error(500, okhttp3.ResponseBody.create(null, ""))

        val summary = pull.run()

        assertEquals("Play sessions returned 500", summary.stoppedBy)
        coVerify(exactly = 0) { syncPreferencesRepository.setRommPlaySessionLastPull(any()) }
    }

    @Test
    fun `without the ingest capability nothing is pulled`() = runTest {
        every { connectionManager.getCapabilities() } returns mockk<RomMCapabilities> {
            every { supportsPlaySessionIngest } returns false
        }

        val summary = pull.run()

        assertEquals(0, summary.added)
        coVerify(exactly = 0) { api.getDevices() }
    }
}
