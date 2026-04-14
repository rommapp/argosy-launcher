package com.nendo.argosy.data.netplay

import com.swordfish.libretrodroid.GLRetroView
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayPeerDriverTest {

    private val peer = InetSocketAddress("127.0.0.1", 50001)

    private lateinit var retroView: GLRetroView
    private lateinit var transport: NetplayTransport
    private lateinit var incomingFlow: MutableSharedFlow<NetplayTransport.Incoming>
    private lateinit var sentPackets: MutableList<NetplayPacket>
    private lateinit var fakeOps: FakeLibretroNetplayOps

    private fun buildDriver(
        scope: CoroutineScope,
        localPort: Int = 0,
        remotePort: Int = 1,
        inputDelay: Int = 0,
        serializeBytes: ByteArray = byteArrayOf(1, 2, 3, 4),
        desyncCheckInterval: Int = 0
    ): NetplayPeerDriver {
        retroView = mockk(relaxed = true)
        every { retroView.serializeState() } returns serializeBytes
        every { retroView.unserializeState(any()) } returns true

        sentPackets = mutableListOf()
        incomingFlow = MutableSharedFlow(extraBufferCapacity = 256)
        fakeOps = FakeLibretroNetplayOps()

        transport = mockk(relaxed = true)
        every { transport.incomingPackets } returns incomingFlow.asSharedFlow() as SharedFlow<NetplayTransport.Incoming>
        coEvery { transport.send(any(), any()) } answers {
            sentPackets += secondArg<NetplayPacket>()
        }
        coEvery { transport.close() } just Runs

        return NetplayPeerDriver(
            retroView = retroView,
            transport = transport,
            initialPeerAddress = peer,
            peerUserId = "peer",
            localPort = localPort,
            remotePort = remotePort,
            scope = scope,
            onSessionEnd = {},
            inputDelay = inputDelay,
            desyncCheckInterval = desyncCheckInterval,
            libretroOps = fakeOps,
            framePeriodNanos = 0L
        )
    }

    private suspend fun makeReady(driver: NetplayPeerDriver) {
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = -1L,
                    playerPort = 1,
                    bitmask = 0,
                    senderFrame = 0L
                )
            )
        )
        driver.tick()
        sentPackets.clear()
        fakeOps.stepCount = 0
        fakeOps.setCalls.clear()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `tick sends FrameInput and steps simulation`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        val inputBits = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A)
        fakeOps.setFakeInputBitmask(0, inputBits)

        driver.tick()
        testScheduler.runCurrent()

        val frameInputs = sentPackets.filterIsInstance<NetplayPacket.FrameInput>()
        assertEquals(1, frameInputs.size)
        assertEquals(0L, frameInputs[0].frameIndex)
        assertEquals(0, frameInputs[0].playerPort)
        assertEquals(inputBits, frameInputs[0].bitmask)
        assertEquals(1, fakeOps.stepCount)

        driver.stop()
    }

    @Test
    fun `consecutive ticks advance frame counter`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        driver.tick()
        driver.tick()
        driver.tick()
        testScheduler.runCurrent()

        val frameInputs = sentPackets.filterIsInstance<NetplayPacket.FrameInput>()
        assertEquals(3, frameInputs.size)
        assertEquals(0L, frameInputs[0].frameIndex)
        assertEquals(1L, frameInputs[1].frameIndex)
        assertEquals(2L, frameInputs[2].frameIndex)
        assertEquals(3, fakeOps.stepCount)
        assertEquals(3L, driver.currentFrame)

        driver.stop()
    }

    @Test
    fun `remote FrameInput is applied to remote port`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        val remoteBitmask = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_B)
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = 0L,
                    playerPort = 1,
                    bitmask = remoteBitmask
                )
            )
        )
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        assertTrue(
            "expected setInputPortState(1, $remoteBitmask) to be called",
            fakeOps.setCalls.contains(1 to remoteBitmask)
        )
        assertEquals(1, fakeOps.stepCount)

        driver.stop()
    }

    @Test
    fun `prediction uses last confirmed remote input`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        val remoteBitmask = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_RIGHT)
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = 0L,
                    playerPort = 1,
                    bitmask = remoteBitmask
                )
            )
        )
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        fakeOps.setCalls.clear()
        driver.tick()
        testScheduler.runCurrent()

        assertTrue(
            "frame 1 should predict remote input = last confirmed ($remoteBitmask)",
            fakeOps.setCalls.contains(1 to remoteBitmask)
        )

        driver.stop()
    }

    @Test
    fun `input delay shifts local input forward`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            inputDelay = 2
        )
        testScheduler.runCurrent()
        makeReady(driver)

        val inputBits = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A)
        fakeOps.setFakeInputBitmask(0, inputBits)

        driver.tick()
        testScheduler.runCurrent()

        val frameInputs = sentPackets.filterIsInstance<NetplayPacket.FrameInput>()
        assertEquals(1, frameInputs.size)
        assertEquals(2L, frameInputs[0].frameIndex)
        assertEquals(inputBits, frameInputs[0].bitmask)

        assertTrue(
            "frame 0 local input should be 0 (delay not yet elapsed)",
            fakeOps.setCalls.contains(0 to 0)
        )

        driver.stop()
    }

    @Test
    fun `rollback replays when prediction is wrong`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        driver.tick()
        testScheduler.runCurrent()

        val correctBitmask = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_UP)
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = 0L,
                    playerPort = 1,
                    bitmask = correctBitmask
                )
            )
        )
        testScheduler.runCurrent()

        val stepsBefore = fakeOps.stepCount
        driver.tick()
        testScheduler.runCurrent()

        val stepsAfter = fakeOps.stepCount
        val replaySteps = stepsAfter - stepsBefore
        assertTrue(
            "expected replay steps (at least 2: rollback replay of frame 0 + normal frame 1), got $replaySteps",
            replaySteps >= 2
        )

        driver.stop()
    }

    @Test
    fun `redundant inputs in FrameInput are stored`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        val bitmask0 = 1
        val bitmask1 = 2
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = 1L,
                    playerPort = 1,
                    bitmask = bitmask1,
                    redundant = listOf(0L to bitmask0)
                )
            )
        )
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        assertTrue(
            "frame 0 remote input should come from redundant data",
            fakeOps.setCalls.contains(1 to bitmask0)
        )

        driver.stop()
    }

    @Test
    fun `incoming Ping produces Pong response`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val pingTimestamp = 12345L
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.Ping(timestampNanos = pingTimestamp)
            )
        )
        driver.tick()
        testScheduler.runCurrent()

        val pongs = sentPackets.filterIsInstance<NetplayPacket.Pong>()
        assertEquals(1, pongs.size)
        assertEquals(pingTimestamp, pongs[0].timestampNanos)

        driver.stop()
    }

    @Test
    fun `goodbye triggers session end`() = runTest(UnconfinedTestDispatcher()) {
        var endedReason: String? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())

        retroView = mockk(relaxed = true)
        every { retroView.serializeState() } returns byteArrayOf(1, 2, 3)
        every { retroView.unserializeState(any()) } returns true

        sentPackets = mutableListOf()
        incomingFlow = MutableSharedFlow(extraBufferCapacity = 256)
        fakeOps = FakeLibretroNetplayOps()

        transport = mockk(relaxed = true)
        every { transport.incomingPackets } returns incomingFlow.asSharedFlow() as SharedFlow<NetplayTransport.Incoming>
        coEvery { transport.send(any(), any()) } answers {
            sentPackets += secondArg<NetplayPacket>()
        }
        coEvery { transport.close() } just Runs

        val driver = NetplayPeerDriver(
            retroView = retroView,
            transport = transport,
            initialPeerAddress = peer,
            peerUserId = "peer",
            localPort = 0,
            remotePort = 1,
            scope = scope,
            onSessionEnd = { endedReason = it },
            desyncCheckInterval = 0,
            libretroOps = fakeOps,
            framePeriodNanos = 0L
        )

        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.SessionControl.Goodbye
            )
        )
        driver.tick()
        testScheduler.runCurrent()

        assertEquals("peer_goodbye", endedReason)

        driver.stop()
    }

    @Test
    fun `snapshot request triggers chunked snapshot send`() = runTest(UnconfinedTestDispatcher()) {
        val largeState = ByteArray(3200) { (it and 0xFF).toByte() }
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            serializeBytes = largeState
        )
        testScheduler.runCurrent()
        makeReady(driver)

        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.SnapshotRequest(reasonCode = 1)
            )
        )
        driver.tick()
        testScheduler.runCurrent()

        val chunks = sentPackets.filterIsInstance<NetplayPacket.SnapshotChunk>()
        assertTrue("expected at least 3 chunks for 3200-byte state", chunks.size >= 3)
        val reassembled = chunks.sortedBy { it.chunkIndex }.fold(ByteArray(0)) { acc, c -> acc + c.payload }
        assertEquals(largeState.size, reassembled.size)
        assertTrue(largeState.contentEquals(reassembled))

        driver.stop()
    }

    @Test
    fun `snapshot chunk reassembly applies state`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        testScheduler.runCurrent()
        makeReady(driver)

        val snapshotBytes = ByteArray(100) { it.toByte() }
        val chunk = NetplayPacket.SnapshotChunk(
            snapshotId = 1,
            chunkIndex = 0,
            chunkTotal = 1,
            payload = snapshotBytes,
            frameIndex = 42L
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = chunk))
        driver.tick()
        testScheduler.runCurrent()

        io.mockk.verify { retroView.unserializeState(snapshotBytes) }
        assertEquals(1L, driver.currentFrame)

        driver.stop()
    }

    @Test
    fun `FrameInput packet serialization round-trip`() {
        val original = NetplayPacket.FrameInput(
            frameIndex = 42L,
            playerPort = 1,
            bitmask = 0xFF,
            redundant = listOf(41L to 0xFE, 40L to 0xFD)
        )
        val bytes = original.serialize()
        val decoded = NetplayPacket.deserialize(bytes)
        assertTrue(decoded is NetplayPacket.FrameInput)
        decoded as NetplayPacket.FrameInput
        assertEquals(42L, decoded.frameIndex)
        assertEquals(1, decoded.playerPort)
        assertEquals(0xFF, decoded.bitmask)
        assertEquals(2, decoded.redundant.size)
        assertEquals(41L, decoded.redundant[0].first)
        assertEquals(0xFE, decoded.redundant[0].second)
        assertEquals(40L, decoded.redundant[1].first)
        assertEquals(0xFD, decoded.redundant[1].second)
    }

    @Test
    fun `DesyncCheck packet serialization round-trip`() {
        val original = NetplayPacket.DesyncCheck(
            frameIndex = 100L,
            stateHash = 0xDEADBEEFL
        )
        val bytes = original.serialize()
        val decoded = NetplayPacket.deserialize(bytes)
        assertTrue(decoded is NetplayPacket.DesyncCheck)
        decoded as NetplayPacket.DesyncCheck
        assertEquals(100L, decoded.frameIndex)
        assertEquals(0xDEADBEEFL, decoded.stateHash)
    }

    @Test
    fun `FrameInput with no redundant data round-trips`() {
        val original = NetplayPacket.FrameInput(
            frameIndex = 0L,
            playerPort = 0,
            bitmask = 0
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.FrameInput)
        decoded as NetplayPacket.FrameInput
        assertEquals(0, decoded.redundant.size)
    }

    @Test
    fun `symmetric driver pair both use FrameInput`() = runTest(UnconfinedTestDispatcher()) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val hostDriver = buildDriver(scope, localPort = 0, remotePort = 1)
        testScheduler.runCurrent()
        makeReady(hostDriver)

        hostDriver.tick()
        testScheduler.runCurrent()

        val hostFrameInputs = sentPackets.filterIsInstance<NetplayPacket.FrameInput>()
        assertEquals(1, hostFrameInputs.size)
        assertEquals(0, hostFrameInputs[0].playerPort)

        hostDriver.stop()

        val guestDriver = buildDriver(scope, localPort = 1, remotePort = 0)
        testScheduler.runCurrent()
        makeReady(guestDriver)

        guestDriver.tick()
        testScheduler.runCurrent()

        val guestFrameInputs = sentPackets.filterIsInstance<NetplayPacket.FrameInput>()
        assertTrue(guestFrameInputs.any { it.playerPort == 1 })

        guestDriver.stop()
    }

    private suspend fun emitRemoteFrameInput(senderFrame: Long, frameIndex: Long = senderFrame, bitmask: Int = 0) {
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.FrameInput(
                    frameIndex = frameIndex,
                    playerPort = 1,
                    bitmask = bitmask,
                    senderFrame = senderFrame
                )
            )
        )
    }

    @Test
    fun `catchup activates when behind by threshold for 500ms`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        emitRemoteFrameInput(senderFrame = 0)
        testScheduler.runCurrent()
        driver.tick()
        testScheduler.runCurrent()

        emitRemoteFrameInput(senderFrame = 10, frameIndex = 10)
        testScheduler.runCurrent()

        fakeOps.stepCount = 0
        driver.tick()
        testScheduler.runCurrent()

        assertEquals(
            "before observation window elapses, only 1 frame should execute",
            1, fakeOps.stepCount
        )

        // Simulate 500ms+ passing by calling tick many times
        // (framePeriodNanos=0 so each tick passes the timer gate)
        // The observation needs real nanoTime to elapse, so we
        // advance the remote peer further to sustain the gap
        Thread.sleep(510)
        emitRemoteFrameInput(senderFrame = 20, frameIndex = 20)
        testScheduler.runCurrent()

        fakeOps.stepCount = 0
        driver.tick()
        testScheduler.runCurrent()

        assertTrue(
            "after 500ms observation, catch-up should execute multiple frames per tick, got ${fakeOps.stepCount}",
            fakeOps.stepCount > 1
        )

        driver.stop()
    }

    @Test
    fun `catchup exits when gap closes`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        emitRemoteFrameInput(senderFrame = 0)
        testScheduler.runCurrent()
        driver.tick()
        testScheduler.runCurrent()

        emitRemoteFrameInput(senderFrame = 10, frameIndex = 10)
        testScheduler.runCurrent()

        // Wait for observation window
        Thread.sleep(510)
        emitRemoteFrameInput(senderFrame = 10, frameIndex = 11)
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        // Now bring remote peer close to local frame
        val currentFrame = driver.currentFrame
        emitRemoteFrameInput(senderFrame = currentFrame + 1, frameIndex = currentFrame + 1)
        testScheduler.runCurrent()

        fakeOps.stepCount = 0
        driver.tick()
        testScheduler.runCurrent()

        assertEquals(
            "after gap closes, should execute exactly 1 frame per tick",
            1, fakeOps.stepCount
        )

        driver.stop()
    }

    @Test
    fun `hard stall activates at 30-frame gap ahead`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        emitRemoteFrameInput(senderFrame = 0)
        testScheduler.runCurrent()

        // Run enough ticks to get well ahead of remotePeerFrame (stuck at 0)
        for (i in 0 until 35) {
            driver.tick()
            testScheduler.runCurrent()
        }

        assertTrue(
            "driver should have advanced past 30",
            driver.currentFrame > 30
        )

        fakeOps.stepCount = 0
        driver.tick()
        testScheduler.runCurrent()

        assertEquals(
            "hard stall: no frame execution when 30+ frames ahead",
            0, fakeOps.stepCount
        )

        driver.stop()
    }

    @Test
    fun `no leash pause at small gap`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        emitRemoteFrameInput(senderFrame = 0)
        testScheduler.runCurrent()

        // Run a few ticks -- gap stays small (remotePeerFrame=0, currentFrame grows)
        for (i in 0 until 5) {
            driver.tick()
            testScheduler.runCurrent()
        }

        fakeOps.stepCount = 0
        driver.tick()
        testScheduler.runCurrent()

        assertEquals(
            "at small gap (<30), should still execute frames normally",
            1, fakeOps.stepCount
        )

        driver.stop()
    }
}
