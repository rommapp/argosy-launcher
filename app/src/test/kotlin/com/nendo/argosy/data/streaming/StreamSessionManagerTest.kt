package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMStreamClaimRequest
import com.nendo.argosy.data.remote.romm.RomMStreamLaunching
import com.nendo.argosy.data.remote.romm.RomMStreamSaveAndExitResponse
import com.nendo.argosy.data.remote.romm.RomMStreamSessionStatus
import com.nendo.argosy.data.remote.romm.RomMStreamSlotCapabilities
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class StreamSessionManagerTest {

    private val api: RomMApi = mockk(relaxed = true)
    private val connectionManager: RomMConnectionManager = mockk {
        every { getApi() } returns api
        every { getBaseUrl() } returns "https://romm.example/"
    }
    private val configStore: StreamingConfigStore = mockk(relaxed = true)
    private val saveSync: SaveSyncRepository = mockk(relaxed = true)

    private val target = StreamTarget(
        gameId = 11,
        rommId = 42,
        title = "Chrono Cross",
        platform = "psx",
        containerLabel = "Living room PC",
        capabilities = RomMStreamSlotCapabilities(maxSlots = 0, hasAutosave = true, autosaveSlot = 10),
        supportsLiveStates = true,
        discs = emptyList()
    )

    private val json = "application/json".toMediaType()

    private fun TestScope.manager(scope: CoroutineScope): StreamSessionManager {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return StreamSessionManager(
            connectionManager = connectionManager,
            configStore = configStore,
            saveSyncRepository = dagger.Lazy { saveSync },
            scope = scope,
            io = dispatcher,
            clock = { testScheduler.currentTime }
        )
    }

    private fun TestScope.managerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun launched() {
        coEvery { api.claimStreamSession(any()) } returns Response.success(
            RomMStreamLaunching(platform = "psx", container = "box-1", claimedAt = "2026-11-13T09:00:00+00:00")
        )
        coEvery { api.getStreamSessionStatus("psx", any()) } returns Response.success(
            RomMStreamSessionStatus(status = RomMStreamSessionStatus.STATUS_ACTIVE, host = "/stream/psx/room")
        )
        coEvery { api.heartbeatStreamSession("psx", any(), any()) } returns Response.success(
            RomMStreamSessionStatus(status = RomMStreamSessionStatus.STATUS_ACTIVE)
        )
        coEvery { api.saveAndExitStreamSession("psx", any(), any(), any()) } returns Response.success(
            RomMStreamSaveAndExitResponse(status = "ok", saved = true, platform = "psx", released = true)
        )
    }

    @Test
    fun `a claim answered 403 hides streaming for the server and starts nothing`() = runTest {
        coEvery { api.claimStreamSession(any()) } returns Response.error(403, """{"detail":"Forbidden"}""".toResponseBody(json))
        val scope = managerScope()
        val manager = manager(scope)

        val outcome = manager.claim(target, stateId = null, saveId = null, cardImport = null)

        assertEquals(StreamClaimOutcome.StartRefused, outcome)
        verify(exactly = 1) { configStore.markStartRefused() }
        assertNull(manager.session.value)
        scope.cancel()
    }

    @Test
    fun `a 409 body is read into the occupied message`() = runTest {
        val body = """{"detail":{"message":"Session in use","draining":false,"rom_name":"Vagrant Story","claimed_at":"2026-11-13T08:00:00+00:00"}}"""
        coEvery { api.claimStreamSession(any()) } returns Response.error(409, body.toResponseBody(json))
        val scope = managerScope()

        val outcome = manager(scope).claim(target, null, null, null)

        assertEquals(StreamClaimOutcome.Busy("Vagrant Story", "2026-11-13T08:00:00+00:00", draining = false), outcome)
        scope.cancel()
    }

    @Test
    fun `a 428 body is read into the memory card decision`() = runTest {
        val body = """{"detail":{"code":"memory_card_import_required","outcome":"found","summary":{"file_count":2,"total_bytes":8192,"game_codes":["SLUS-00001"]}}}"""
        coEvery { api.claimStreamSession(any()) } returns Response.error(428, body.toResponseBody(json))
        val scope = managerScope()

        val outcome = manager(scope).claim(target, null, null, null)

        assertEquals(StreamClaimOutcome.CardDecision(false, 2, listOf("SLUS-00001"), null), outcome)
        scope.cancel()
    }

    @Test
    fun `the claim carries the picks and the card answer`() = runTest {
        launched()
        val sent = slot<RomMStreamClaimRequest>()
        coEvery { api.claimStreamSession(capture(sent)) } returns Response.success(
            RomMStreamLaunching(platform = "psx", container = "box-1", claimedAt = "2026-11-13T09:00:00+00:00")
        )
        val scope = managerScope()

        manager(scope).claim(target, stateId = 7, saveId = null, cardImport = "adopt")

        assertEquals(RomMStreamClaimRequest(romId = 42, stateId = 7, saveId = null, cardImport = "adopt"), sent.captured)
        scope.cancel()
    }

    @Test
    fun `a launched claim finds its room and beats every 30 seconds`() = runTest {
        launched()
        val scope = managerScope()
        val manager = manager(scope)

        manager.claim(target, null, null, null)
        runCurrent()

        assertEquals("https://romm.example/stream/psx/room", manager.session.value?.roomUrl)
        assertEquals(StreamPhase.PLAYING, manager.session.value?.phase)
        coVerify(exactly = 0) { api.heartbeatStreamSession(any(), any(), any()) }
        advanceTimeBy(StreamSessionManager.HEARTBEAT_MS)
        runCurrent()
        coVerify(exactly = 1) { api.heartbeatStreamSession("psx", "box-1", "2026-11-13T09:00:00+00:00") }
        advanceTimeBy(StreamSessionManager.HEARTBEAT_MS)
        runCurrent()
        coVerify(exactly = 2) { api.heartbeatStreamSession("psx", "box-1", "2026-11-13T09:00:00+00:00") }
        scope.cancel()
    }

    @Test
    fun `leaving the screen keeps beating through the grace, then saves and exits`() = runTest {
        launched()
        val scope = managerScope()
        val manager = manager(scope)
        manager.claim(target, null, null, null)
        runCurrent()

        manager.onPlayerHidden()
        advanceTimeBy(StreamGraceTimer.DEFAULT_GRACE_MS - 1)
        runCurrent()
        coVerify(exactly = 0) { api.saveAndExitStreamSession(any(), any(), any(), any()) }
        coVerify(atLeast = 9) { api.heartbeatStreamSession(any(), any(), any()) }

        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 1) { api.saveAndExitStreamSession("psx", any(), "box-1", "2026-11-13T09:00:00+00:00") }
        assertEquals(StreamEndReason.GRACE_EXPIRED, manager.session.value?.endReason)
        scope.cancel()
    }

    @Test
    fun `coming back inside the grace keeps the stream`() = runTest {
        launched()
        val scope = managerScope()
        val manager = manager(scope)
        manager.claim(target, null, null, null)
        runCurrent()

        manager.onPlayerHidden()
        advanceTimeBy(StreamGraceTimer.DEFAULT_GRACE_MS / 2)
        manager.onPlayerShown()
        advanceTimeBy(StreamGraceTimer.DEFAULT_GRACE_MS)
        runCurrent()

        coVerify(exactly = 0) { api.saveAndExitStreamSession(any(), any(), any(), any()) }
        assertEquals(StreamPhase.PLAYING, manager.session.value?.phase)
        scope.cancel()
    }

    @Test
    fun `a stream records and uploads no play session, and fetches the game's saves after teardown`() = runTest {
        launched()
        val scope = managerScope()
        val manager = manager(scope)
        manager.claim(target, null, null, null)
        runCurrent()
        advanceTimeBy(StreamSessionManager.HEARTBEAT_MS * 3)
        runCurrent()

        manager.saveAndExit()
        runCurrent()
        advanceTimeBy(StreamSessionManager.SAVE_FETCH_DELAY_MS)
        runCurrent()

        coVerify(exactly = 0) { api.ingestPlaySessions(any()) }
        coVerify(exactly = 0) { api.sendActivityHeartbeat(any()) }
        coVerify(exactly = 1) { saveSync.checkSavesForGame(11, 42) }
        assertEquals(StreamEndReason.SAVED_AND_EXITED, manager.session.value?.endReason)
        scope.cancel()
    }

    @Test
    fun `RomM ending the session ends it here without a save and exit`() = runTest {
        launched()
        coEvery { api.heartbeatStreamSession("psx", any(), any()) } returns Response.success(
            RomMStreamSessionStatus(status = RomMStreamSessionStatus.STATUS_ENDED)
        )
        val scope = managerScope()
        val manager = manager(scope)
        manager.claim(target, null, null, null)
        runCurrent()

        advanceTimeBy(StreamSessionManager.HEARTBEAT_MS)
        runCurrent()

        assertEquals(StreamEndReason.ENDED_BY_SERVER, manager.session.value?.endReason)
        coVerify(exactly = 0) { api.saveAndExitStreamSession(any(), any(), any(), any()) }
        assertTrue(manager.session.value?.phase == StreamPhase.ENDED)
        scope.cancel()
    }
}
