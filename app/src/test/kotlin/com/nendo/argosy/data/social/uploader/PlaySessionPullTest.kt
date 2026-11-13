package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlayRecordDeletionDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.PlayRecordDeletionEntity
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.model.PlayRecordCutoffs
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
    private val playRecordDeletionDao: PlayRecordDeletionDao = mockk {
        coEvery { getForOwner(any()) } returns emptyList()
    }
    private val gameDao: GameDao = mockk {
        coEvery { getByRommId(any()) } returns null
    }
    private val syncPreferencesRepository: SyncPreferencesRepository = mockk {
        coEvery { getRommUserId() } returns 7L
        coEvery { setRommPlaySessionLastPull(any()) } returns Unit
        coEvery { getRommPlaySessionLastPull() } returns null
    }
    private val api: RomMApi = mockk {
        coEvery { getPlaySessions(null, null, any(), any(), any(), any()) } returns Response.success(emptyList())
    }
    private val connectionManager: RomMConnectionManager = mockk {
        every { getCapabilities() } returns mockk<RomMCapabilities> {
            every { supportsPlaySessionIngest } returns true
            every { supportsStreaming } returns true
        }
        every { isConnected() } returns true
        every { getApi() } returns api
        every { getDeviceId() } returns "dev-this"
    }
    private val pull = PlaySessionPull(
        playSessionDao,
        playRecordDeletionDao,
        gameDao,
        syncPreferencesRepository,
        connectionManager
    )

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

        val added = pull.insertMissing(
            listOf(remote(1), remote(2), remote(3)),
            otherDevice,
            7L,
            PlayRecordCutoffs.NONE,
            unresolved
        )

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

        pull.insertMissing(listOf(remote(5)), otherDevice, 7L, PlayRecordCutoffs.NONE, unresolved)

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

        val added = pull.insertMissing(
            listOf(remote(1).copy(romId = null)),
            otherDevice,
            7L,
            PlayRecordCutoffs.NONE,
            unresolved
        )

        assertEquals(0, added)
        coVerify(exactly = 0) { playSessionDao.insert(any()) }
    }

    @Test
    fun `a session of a deleted game that started before the cutoff is not stored again`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        val cutoffs = PlayRecordCutoffs(mapOf(-99L to Instant.parse("2026-05-15T12:00:01Z")))

        val added = pull.insertMissing(listOf(remote(1)), otherDevice, 7L, cutoffs, unresolved)

        assertEquals(0, added)
        coVerify(exactly = 0) { playSessionDao.insert(any()) }
    }

    @Test
    fun `a session of a deleted game that started after the cutoff is stored`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        val cutoffs = PlayRecordCutoffs(mapOf(-99L to Instant.parse("2026-05-15T11:59:59Z")))

        val added = pull.insertMissing(listOf(remote(1)), otherDevice, 7L, cutoffs, unresolved)

        assertEquals(1, added)
        coVerify(exactly = 1) { playSessionDao.insert(any()) }
    }

    @Test
    fun `a cutoff on another game does not hold back this game's sessions`() = runTest {
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        val cutoffs = PlayRecordCutoffs(mapOf(-98L to Instant.parse("2026-06-01T00:00:00Z")))

        val added = pull.insertMissing(listOf(remote(1)), otherDevice, 7L, cutoffs, unresolved)

        assertEquals(1, added)
    }

    @Test
    fun `run reads the account's cutoffs once and skips the deleted game's older sessions`() = runTest {
        coEvery { api.getDevices() } returns Response.success(listOf(otherDevice))
        coEvery { api.getPlaySessions(null, "dev-other", null, null, 200, 0) } returns
            Response.success(listOf(remote(1), remote(2, romId = 98L)))
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        coEvery { api.getRom(any()) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))
        coEvery { playRecordDeletionDao.getForOwner(7L) } returns listOf(
            PlayRecordDeletionEntity(gameId = -99L, ownerUserId = 7L, deletedBefore = Instant.parse("2026-06-01T00:00:00Z"))
        )

        val summary = pull.run()

        assertEquals(1, summary.added)
        val inserted = mutableListOf<PlaySessionEntity>()
        coVerify(exactly = 1) { playSessionDao.insert(capture(inserted)) }
        assertEquals(-98L, inserted.single().gameId)
        coVerify(exactly = 1) { playRecordDeletionDao.getForOwner(7L) }
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
    fun `sessions with no device are stored under the streaming token`() = runTest {
        coEvery { api.getDevices() } returns Response.success(emptyList())
        coEvery { api.getPlaySessions(null, null, null, null, 200, 0) } returns
            Response.success(listOf(remote(1).copy(deviceId = null)))
        coEvery { playSessionDao.getHeldRommSessionIds(any()) } returns emptyList()
        coEvery { api.getRom(any()) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))
        val captured = slot<PlaySessionEntity>()
        coEvery { playSessionDao.insert(capture(captured)) } returns 1L

        val summary = pull.run()

        assertEquals(PlaySessionPull.Summary(added = 1, devices = 0), summary)
        assertEquals(PlaySessionPull.ROMM_STREAMING_DEVICE_ID, captured.captured.deviceId)
        assertEquals(1L, captured.captured.rommSessionId)
        assertNull(captured.captured.userId)
    }

    @Test
    fun `the streaming pass ignores device-bound sessions in the all-devices page`() = runTest {
        coEvery { api.getDevices() } returns Response.success(emptyList())
        coEvery { api.getPlaySessions(null, null, null, null, 200, 0) } returns
            Response.success(listOf(remote(1), remote(2).copy(deviceId = null), remote(3).copy(deviceId = "dev-this")))
        coEvery { playSessionDao.getHeldRommSessionIds(listOf(2L)) } returns emptyList()
        coEvery { api.getRom(any()) } returns Response.error(404, okhttp3.ResponseBody.create(null, ""))

        val summary = pull.run()

        assertEquals(1, summary.added)
        val inserted = mutableListOf<PlaySessionEntity>()
        coVerify(exactly = 1) { playSessionDao.insert(capture(inserted)) }
        assertEquals(listOf(2L), inserted.map { it.rommSessionId })
        coVerify(exactly = 1) { playSessionDao.getHeldRommSessionIds(listOf(2L)) }
    }

    @Test
    fun `a streaming session whose server id is already held is not inserted again`() = runTest {
        coEvery { api.getDevices() } returns Response.success(emptyList())
        coEvery { api.getPlaySessions(null, null, null, null, 200, 0) } returns
            Response.success(listOf(remote(3).copy(deviceId = null)))
        coEvery { playSessionDao.getHeldRommSessionIds(listOf(3L)) } returns listOf(3L)

        val summary = pull.run()

        assertEquals(PlaySessionPull.Summary(added = 0, devices = 0), summary)
        coVerify(exactly = 0) { playSessionDao.insert(any()) }
    }

    @Test
    fun `the streaming pass asks only for sessions newer than the latest streaming one held`() = runTest {
        val since = Instant.parse("2026-05-10T00:00:00Z")
        coEvery { api.getDevices() } returns Response.success(emptyList())
        coEvery {
            playSessionDao.getLatestRommStartForDevice(PlaySessionPull.ROMM_STREAMING_DEVICE_ID, 7L)
        } returns since
        coEvery { api.getPlaySessions(null, null, since.toString(), null, 200, 0) } returns Response.success(emptyList())

        val summary = pull.run()

        assertNull(summary.stoppedBy)
        coVerify(exactly = 1) { api.getPlaySessions(null, null, since.toString(), null, 200, 0) }
        coVerify(exactly = 0) { api.getPlaySessions(null, null, null, any(), any(), any()) }
    }

    @Test
    fun `a server without streaming gets no device-less pass`() = runTest {
        every { connectionManager.getCapabilities() } returns mockk<RomMCapabilities> {
            every { supportsPlaySessionIngest } returns true
            every { supportsStreaming } returns false
        }
        coEvery { api.getDevices() } returns Response.success(emptyList())

        val summary = pull.run()

        assertNull(summary.stoppedBy)
        coVerify(exactly = 0) { api.getPlaySessions(any(), null, any(), any(), any(), any()) }
    }

    @Test
    fun `with no streamed session held the pass starts a day before the previous pull`() = runTest {
        val lastPull = Instant.parse("2026-11-12T10:00:00Z")
        val floor = Instant.parse("2026-11-11T10:00:00Z")
        coEvery { syncPreferencesRepository.getRommPlaySessionLastPull() } returns lastPull
        coEvery { api.getDevices() } returns Response.success(emptyList())
        coEvery { api.getPlaySessions(null, null, floor.toString(), null, 200, 0) } returns Response.success(emptyList())

        pull.run()

        coVerify(exactly = 1) { api.getPlaySessions(null, null, floor.toString(), null, 200, 0) }
        coVerify(exactly = 0) { api.getPlaySessions(null, null, null, any(), any(), any()) }
    }

    @Test
    fun `the streaming floor takes the later of the newest stream and a day before the last pull`() {
        val lastPull = Instant.parse("2026-11-12T10:00:00Z")
        val dayBefore = Instant.parse("2026-11-11T10:00:00Z")
        val older = Instant.parse("2026-11-01T00:00:00Z")
        val newer = Instant.parse("2026-11-12T09:00:00Z")

        assertNull(PlaySessionPull.streamingFloor(null, null))
        assertEquals(older, PlaySessionPull.streamingFloor(older, null))
        assertEquals(dayBefore, PlaySessionPull.streamingFloor(null, lastPull))
        assertEquals(dayBefore, PlaySessionPull.streamingFloor(older, lastPull))
        assertEquals(newer, PlaySessionPull.streamingFloor(newer, lastPull))
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
