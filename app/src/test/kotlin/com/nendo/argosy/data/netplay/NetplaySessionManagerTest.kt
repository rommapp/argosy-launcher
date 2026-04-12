package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.NetplayGuestLeftPayload
import com.nendo.argosy.data.social.NetplayJoinDeclinedPayload
import com.nendo.argosy.data.social.NetplayJoinRequestedPayload
import com.nendo.argosy.data.social.NetplayHandshakeFailedPayload
import com.nendo.argosy.data.social.NetplayKickedPayload
import com.nendo.argosy.data.social.NetplayOpenPayload
import com.nendo.argosy.data.social.NetplayReadyPayload
import com.nendo.argosy.data.social.NetplayReserveRequestPayload
import com.nendo.argosy.data.social.NetplaySessionEndedPayload
import com.nendo.argosy.data.social.NetplaySessionMode
import com.nendo.argosy.data.social.NetplaySessionState
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class NetplaySessionManagerTest {

    private fun fakeService(
        incoming: MutableSharedFlow<ArgosSocialService.IncomingMessage>,
        openResult: Boolean = true
    ): ArgosSocialService {
        val svc = mockk<ArgosSocialService>(relaxed = true)
        every { svc.incomingMessages } returns incoming
        every { svc.sendNetplayOpen(any()) } returns openResult
        every { svc.sendNetplayJoinRequest(any()) } returns true
        every { svc.sendNetplayJoinResponse(any()) } returns true
        every { svc.sendNetplayClose(any()) } returns true
        every { svc.sendNetplayLeave(any()) } returns true
        every { svc.sendNetplayReserve(any()) } returns true
        return svc
    }

    private fun fakeHandshake(): NetplayHandshake {
        return NetplayHandshake(
            candidateGatherer = mockk(relaxed = true),
            socialService = mockk(relaxed = true)
        )
    }

    private fun samplePayload() = NetplayOpenPayload(
        gameIgdbId = null,
        gameTitle = "Super Mario",
        coreId = "fceumm",
        romHashPrefix = "abc",
        coreHash = "def"
    )

    private fun readyMessage(sessionId: String = "sess-1"): ArgosSocialService.IncomingMessage.NetplayReady {
        val key = ByteArray(32) { it.toByte() }
        return ArgosSocialService.IncomingMessage.NetplayReady(
            NetplayReadyPayload(
                sessionId = sessionId,
                sessionKey = Base64.getEncoder().encodeToString(key),
                protocolVersion = 1
            )
        )
    }

    private suspend fun openAndWait(
        manager: NetplaySessionManager,
        incoming: MutableSharedFlow<ArgosSocialService.IncomingMessage>,
        scope: CoroutineScope,
        sessionId: String = "sess-1"
    ) {
        val openJob = scope.launch {
            manager.openServer(samplePayload())
        }
        delay(50)
        incoming.emit(readyMessage(sessionId))
        openJob.join()
    }

    @Test
    fun openServerFailsOnSendFailure() = runTest {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming, openResult = false),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )

        val result = manager.openServer(
            NetplayOpenPayload(
                gameIgdbId = null,
                gameTitle = "Test",
                coreId = "fceumm",
                romHashPrefix = "abc",
                coreHash = "def"
            )
        )
        assertTrue(result.isFailure)
        assertEquals(NetplaySessionState.Error("send_failed"), manager.sessionState.value)
        manager.shutdown()
    }

    @Test
    fun openServerTransitionsToWaitingOnReady() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )

        val openJob = launch {
            val result = manager.openServer(
                NetplayOpenPayload(
                    gameIgdbId = null,
                    gameTitle = "Super Mario",
                    coreId = "fceumm",
                    romHashPrefix = "abc",
                    coreHash = "def"
                )
            )
            assertTrue(result.isSuccess)
        }

        // let the manager start collecting and the open call begin awaiting ready
        delay(50)

        val key = ByteArray(32) { it.toByte() }
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayReady(
                NetplayReadyPayload(
                    sessionId = "sess-1",
                    sessionKey = Base64.getEncoder().encodeToString(key),
                    protocolVersion = 1
                )
            )
        )
        openJob.join()
        val state = manager.sessionState.value
        assertTrue("expected Waiting, got $state", state is NetplaySessionState.Waiting)
        assertEquals("sess-1", (state as NetplaySessionState.Waiting).sessionId)
        manager.shutdown()
    }

    @Test
    fun joinSessionFailsOnSendFailure() = runTest {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = mockk<ArgosSocialService>(relaxed = true) {
            every { incomingMessages } returns incoming
            every { sendNetplayJoinRequest(any()) } returns false
        }
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.joinSession("sess-x", "host-user")
        assertEquals(NetplaySessionState.Error("send_failed"), manager.sessionState.value)
        manager.shutdown()
    }

    @Test
    fun joinRequestInPrivateModeQueuesWithoutAutoAccept() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)
        assertTrue(manager.sessionState.value is NetplaySessionState.Waiting)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload(
                    sessionId = "sess-1",
                    fromUserId = "guest-42",
                    fromUsername = "guest42"
                )
            )
        )
        advanceUntilIdle()

        val state = manager.sessionState.value
        assertTrue("expected Waiting, got $state", state is NetplaySessionState.Waiting)
        val queue = manager.joinRequestQueue.value
        assertEquals(1, queue.size)
        assertEquals("guest-42", queue[0].fromUserId)
        assertEquals("guest42", queue[0].fromUsername)
        manager.shutdown()
    }

    @Test
    fun declineJoinSendsResponseAndRemovesFromQueue() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-42", "guest42")
            )
        )
        advanceUntilIdle()

        val responseSlot = slot<com.nendo.argosy.data.social.NetplayJoinResponsePayload>()
        every { svc.sendNetplayJoinResponse(capture(responseSlot)) } returns true

        manager.declineJoin("guest-42", reason = "busy")
        advanceUntilIdle()

        assertEquals("sess-1", responseSlot.captured.sessionId)
        assertEquals("guest-42", responseSlot.captured.guestId)
        assertEquals(false, responseSlot.captured.accept)
        assertEquals("busy", responseSlot.captured.reason)
        assertTrue(manager.sessionState.value is NetplaySessionState.Waiting)
        assertTrue(manager.joinRequestQueue.value.isEmpty())
        manager.shutdown()
    }

    @Test
    fun closeServerFromWaitingSendsCloseAndReturnsIdle() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val rules = mockk<NetplaySessionRules>(relaxed = true)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            sessionRules = rules,
            scope = scope
        )
        openAndWait(manager, incoming, this)

        manager.closeServer()
        advanceUntilIdle()

        verify { svc.sendNetplayClose("sess-1") }
        verify { rules.release() }
        assertEquals(NetplaySessionState.Idle, manager.sessionState.value)
        assertTrue(manager.currentDriver() == null)
        manager.shutdown()
    }

    @Test
    fun leaveSessionFromOpeningSendsLeaveAndReturnsIdle() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this, sessionId = "sess-leave")

        manager.leaveSession()
        advanceUntilIdle()

        verify { svc.sendNetplayLeave("sess-leave") }
        assertEquals(NetplaySessionState.Idle, manager.sessionState.value)
        manager.shutdown()
    }

    @Test
    fun kickedMessageTearsDownAndReturnsIdle() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val rules = mockk<NetplaySessionRules>(relaxed = true)
        val retroView = mockk<GLRetroView>(relaxed = true)
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = retroView,
            sessionRules = rules,
            scope = scope
        )
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayKicked(
                NetplayKickedPayload(sessionId = "sess-1", reason = "host_kicked")
            )
        )
        advanceUntilIdle()

        assertEquals(NetplaySessionState.Idle, manager.sessionState.value)
        verify { rules.release() }
        manager.shutdown()
    }

    @Test
    fun sessionEndedMessageReturnsIdle() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplaySessionEnded(
                NetplaySessionEndedPayload(sessionId = "sess-1", reason = "expired")
            )
        )
        advanceUntilIdle()

        assertEquals(NetplaySessionState.Idle, manager.sessionState.value)
        manager.shutdown()
    }

    @Test
    fun handshakeFailedMessageSetsErrorState() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayHandshakeFailed(
                NetplayHandshakeFailedPayload(sessionId = "sess-1", reason = "candidate_pair_failed")
            )
        )
        advanceUntilIdle()

        val state = manager.sessionState.value
        assertTrue("expected Error, got $state", state is NetplaySessionState.Error)
        assertEquals("candidate_pair_failed", (state as NetplaySessionState.Error).reason)
        manager.shutdown()
    }

    @Test
    fun joinDeclinedMessageSetsErrorState() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        // Let the inbound collector subscribe before emitting.
        advanceUntilIdle()
        delay(50)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinDeclined(
                NetplayJoinDeclinedPayload(sessionId = "sess-1", reason = "host_busy")
            )
        )
        advanceUntilIdle()

        val state = manager.sessionState.value
        assertTrue("expected Error, got $state", state is NetplaySessionState.Error)
        assertEquals("host_busy", (state as NetplaySessionState.Error).reason)
        manager.shutdown()
    }

    @Test
    fun reserveSessionSendsReserveWhenActive() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        val reserveSlot = slot<NetplayReserveRequestPayload>()
        every { svc.sendNetplayReserve(capture(reserveSlot)) } returns true

        val ok = manager.reserveSession("friend-7")
        assertTrue(ok)
        assertEquals("sess-1", reserveSlot.captured.sessionId)
        assertEquals("friend-7", reserveSlot.captured.reservedForUserId)
        manager.shutdown()
    }

    @Test
    fun reserveSessionReturnsFalseWhenIdle() = runTest {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        val ok = manager.reserveSession("friend-7")
        assertTrue(!ok)
        verify(exactly = 0) { svc.sendNetplayReserve(any()) }
        manager.shutdown()
    }

    @Test
    fun openServerRejectsSecondOpenWhileActive() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        val second = manager.openServer(samplePayload())
        assertTrue(second.isFailure)
        assertTrue(manager.sessionState.value is NetplaySessionState.Waiting)
        manager.shutdown()
    }

    @Test
    fun joinSessionRejectsWhenNotIdle() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        manager.joinSession("sess-other", "host-user")
        advanceUntilIdle()
        assertTrue(manager.sessionState.value is NetplaySessionState.Waiting)
        verify(exactly = 0) { svc.sendNetplayJoinRequest(any()) }
        manager.shutdown()
    }

    @Test
    fun acceptJoinIgnoredWhenNotInWaiting() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )

        manager.acceptJoin("guest-x")
        advanceUntilIdle()
        verify(exactly = 0) { svc.sendNetplayJoinResponse(any()) }
        assertEquals(NetplaySessionState.Idle, manager.sessionState.value)
        manager.shutdown()
    }

    @Test
    fun guestLeftMessageRevertsToWaitingAndEmitsEvent() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        val receivedEvents = mutableListOf<NetplaySessionManager.GuestLeftEvent>()
        val collectorJob = launch { manager.guestLeftEvents.collect { receivedEvents += it } }
        delay(50)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayGuestLeft(
                NetplayGuestLeftPayload(
                    sessionId = "sess-1",
                    guestId = "guest-42",
                    reason = "guest_left"
                )
            )
        )
        advanceUntilIdle()

        val state = manager.sessionState.value
        assertTrue("expected Waiting, got $state", state is NetplaySessionState.Waiting)
        assertEquals("sess-1", (state as NetplaySessionState.Waiting).sessionId)
        assertEquals(1, receivedEvents.size)
        assertEquals("guest-42", receivedEvents[0].guestUserId)

        collectorJob.cancel()
        manager.shutdown()
    }

    @Test
    fun openServerRejectsMismatchedProtocolVersion() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )

        val openJob = launch { manager.openServer(samplePayload()) }
        delay(50)

        val key = ByteArray(32) { it.toByte() }
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayReady(
                NetplayReadyPayload(
                    sessionId = "sess-1",
                    sessionKey = Base64.getEncoder().encodeToString(key),
                    protocolVersion = 99
                )
            )
        )
        openJob.join()

        val state = manager.sessionState.value
        assertTrue("expected Error, got $state", state is NetplaySessionState.Error)
        assertEquals("protocol_version_mismatch", (state as NetplaySessionState.Error).reason)
        verify { svc.sendNetplayHandshakeTelemetry(match { it.outcome == "protocol_version_mismatch" }) }
        manager.shutdown()
    }

    @Test
    fun openServerRateLimitedErrorPropagatesAsErrorState() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )

        val openJob = launch { manager.openServer(samplePayload()) }
        delay(50)
        incoming.emit(
            ArgosSocialService.IncomingMessage.Error(
                code = "rate_limited",
                message = "Too many opens"
            )
        )
        openJob.join()

        val state = manager.sessionState.value
        assertTrue("expected Error, got $state", state is NetplaySessionState.Error)
        assertEquals("rate_limited", (state as NetplaySessionState.Error).reason)
        manager.shutdown()
    }

    @Test
    fun shutdownCancelsInboundCollectionAndClearsDriver() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val manager = NetplaySessionManager(
            socialService = fakeService(incoming),
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        openAndWait(manager, incoming, this)

        manager.shutdown()
        advanceUntilIdle()

        // After shutdown, inbound messages should no longer mutate state.
        val stateBefore = manager.sessionState.value
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayKicked(NetplayKickedPayload("sess-1", null))
        )
        advanceUntilIdle()
        assertEquals(stateBefore, manager.sessionState.value)
    }

    @Test
    fun acceptFirstDeclineSecondInPrivateMode() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-A", "guestA")
            )
        )
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-B", "guestB")
            )
        )
        advanceUntilIdle()

        assertEquals(2, manager.joinRequestQueue.value.size)
        assertEquals("guest-A", manager.joinRequestQueue.value[0].fromUserId)
        assertEquals("guest-B", manager.joinRequestQueue.value[1].fromUserId)

        manager.acceptJoin("guest-A")
        advanceUntilIdle()

        assertTrue(manager.joinRequestQueue.value.isEmpty())
        verify { svc.sendNetplayJoinResponse(match { it.guestId == "guest-A" && it.accept }) }
        verify { svc.sendNetplayJoinResponse(match { it.guestId == "guest-B" && !it.accept && it.reason == "session_busy" }) }
        manager.shutdown()
    }

    @Test
    fun declineFirstShowsSecondInQueue() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-A", "guestA")
            )
        )
        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-B", "guestB")
            )
        )
        advanceUntilIdle()

        manager.declineJoin("guest-A", "host_declined")
        advanceUntilIdle()

        val queue = manager.joinRequestQueue.value
        assertEquals(1, queue.size)
        assertEquals("guest-B", queue[0].fromUserId)
        manager.shutdown()
    }

    @Test
    fun joinRequestDuringHandshakingIsAutoDeclined() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-A", "guestA")
            )
        )
        advanceUntilIdle()
        manager.acceptJoin("guest-A")
        advanceUntilIdle()

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-B", "guestB")
            )
        )
        advanceUntilIdle()

        assertTrue(manager.joinRequestQueue.value.isEmpty())
        verify { svc.sendNetplayJoinResponse(match { it.guestId == "guest-B" && !it.accept && it.reason == "session_busy" }) }
        manager.shutdown()
    }

    @Test
    fun openModeAutoAcceptsFirstRequest() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.OPEN
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-A", "guestA")
            )
        )
        advanceUntilIdle()

        verify { svc.sendNetplayJoinResponse(match { it.guestId == "guest-A" && it.accept }) }
        assertTrue(manager.joinRequestQueue.value.isEmpty())
        manager.shutdown()
    }

    @Test
    fun privateModeDoesNotAutoAccept() = runTest(StandardTestDispatcher()) {
        val incoming = MutableSharedFlow<ArgosSocialService.IncomingMessage>(replay = 0, extraBufferCapacity = 16)
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val svc = fakeService(incoming)
        val manager = NetplaySessionManager(
            socialService = svc,
            handshake = fakeHandshake(),
            retroView = mockk(relaxed = true),
            scope = scope
        )
        manager.sessionMode = NetplaySessionMode.PRIVATE
        openAndWait(manager, incoming, this)

        incoming.emit(
            ArgosSocialService.IncomingMessage.NetplayJoinRequested(
                NetplayJoinRequestedPayload("sess-1", "guest-A", "guestA")
            )
        )
        advanceUntilIdle()

        verify(exactly = 0) { svc.sendNetplayJoinResponse(match { it.guestId == "guest-A" && it.accept }) }
        assertEquals(1, manager.joinRequestQueue.value.size)
        assertTrue(manager.sessionState.value is NetplaySessionState.Waiting)
        manager.shutdown()
    }
}
