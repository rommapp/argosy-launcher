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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress

@OptIn(ExperimentalCoroutinesApi::class)
class NetplayHostDriverIntegrationTest {

    private val peer = InetSocketAddress("127.0.0.1", 50001)

    private lateinit var retroView: GLRetroView
    private lateinit var transport: NetplayTransport
    private lateinit var inputShadow: NetplayInputShadow
    private lateinit var incomingFlow: MutableSharedFlow<NetplayTransport.Incoming>
    private lateinit var sentPackets: MutableList<NetplayPacket>
    private lateinit var fakeOps: FakeLibretroNetplayOps

    private fun buildDriver(
        scope: CoroutineScope,
        serializeBytes: ByteArray = byteArrayOf(1, 2, 3, 4)
    ): NetplayHostDriver {
        retroView = mockk(relaxed = true)
        every { retroView.serializeState() } returns serializeBytes

        sentPackets = mutableListOf()
        incomingFlow = MutableSharedFlow(extraBufferCapacity = 256)
        fakeOps = FakeLibretroNetplayOps()

        transport = mockk(relaxed = true)
        every { transport.incomingPackets } returns incomingFlow.asSharedFlow() as SharedFlow<NetplayTransport.Incoming>
        coEvery { transport.send(any(), any()) } answers {
            sentPackets += secondArg<NetplayPacket>()
        }
        coEvery { transport.close() } just Runs

        inputShadow = NetplayInputShadow()

        return NetplayHostDriver(
            retroView = retroView,
            transport = transport,
            peerAddress = peer,
            peerUserId = "guest",
            localPort = 0,
            guestPort = 1,
            inputShadow = inputShadow,
            scope = scope,
            onSessionEnd = {},
            libretroOps = fakeOps
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `tick produces InputBundle with current frame and port bitmasks`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        inputShadow.onKeyDown(0, NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A)
        inputShadow.onKeyDown(0, NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_RIGHT)

        driver.tick()
        testScheduler.runCurrent()

        val bundles = sentPackets.filterIsInstance<NetplayPacket.InputBundle>()
        assertEquals(1, bundles.size)
        val bundle = bundles[0]
        assertEquals(0L, bundle.frameIndex)

        val localState = bundle.ports.find { it.port == 0 }
        assertNotNull(localState)
        val expectedLocalBits =
            (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A) or
            (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_RIGHT)
        assertEquals(expectedLocalBits, localState!!.bitmask)

        val guestState = bundle.ports.find { it.port == 1 }
        assertNotNull(guestState)
        assertEquals(0, guestState!!.bitmask)

        assertTrue(
            "expected setInputPortState(0, $expectedLocalBits) to be called",
            fakeOps.setCalls.contains(0 to expectedLocalBits)
        )
        assertTrue(
            "expected setInputPortState(1, 0) to be called",
            fakeOps.setCalls.contains(1 to 0)
        )
        assertEquals(1, fakeOps.stepCount)

        driver.stop()
    }

    @Test
    fun `consecutive ticks advance the frame counter`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        driver.tick()
        driver.tick()
        driver.tick()
        testScheduler.runCurrent()

        val bundles = sentPackets.filterIsInstance<NetplayPacket.InputBundle>()
        assertEquals(3, bundles.size)
        assertEquals(0L, bundles[0].frameIndex)
        assertEquals(1L, bundles[1].frameIndex)
        assertEquals(2L, bundles[2].frameIndex)
        assertEquals(3, fakeOps.stepCount)

        driver.stop()
    }

    @Test
    fun `guest input for correct port updates next tick's bundle`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val guestBitmask = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_B)
        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.GuestInput(
                    guestFrameIndex = 0L,
                    portNumber = 1,
                    bitmask = guestBitmask
                )
            )
        )
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        val bundle = sentPackets.filterIsInstance<NetplayPacket.InputBundle>().single()
        val guestState = bundle.ports.find { it.port == 1 }!!
        assertEquals(guestBitmask, guestState.bitmask)
        assertTrue(fakeOps.setCalls.contains(1 to guestBitmask))

        driver.stop()
    }

    @Test
    fun `guest input for wrong port is dropped`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.GuestInput(
                    guestFrameIndex = 0L,
                    portNumber = 2,
                    bitmask = 0xFF
                )
            )
        )
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        val bundle = sentPackets.filterIsInstance<NetplayPacket.InputBundle>().single()
        val guestState = bundle.ports.find { it.port == 1 }!!
        assertEquals(0, guestState.bitmask)

        driver.stop()
    }

    @Test
    fun `snapshot request triggers chunked snapshot send`() = runTest(UnconfinedTestDispatcher()) {
        val largeState = ByteArray(3200) { (it and 0xFF).toByte() }
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            serializeBytes = largeState
        )

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
        val firstChunk = chunks.first()
        assertEquals(chunks.size, firstChunk.chunkTotal)
        val allSameSnapshot = chunks.all { it.snapshotId == firstChunk.snapshotId }
        assertTrue("all chunks should share a snapshotId", allSameSnapshot)
        val reassembled = chunks.sortedBy { it.chunkIndex }.fold(ByteArray(0)) { acc, c -> acc + c.payload }
        assertEquals(largeState.size, reassembled.size)
        assertTrue(largeState.contentEquals(reassembled))

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
    fun `snapshot ack clears pending chunks`() = runTest(UnconfinedTestDispatcher()) {
        val smallState = ByteArray(40) { it.toByte() }
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            serializeBytes = smallState
        )

        incomingFlow.emit(
            NetplayTransport.Incoming(
                source = peer,
                packet = NetplayPacket.SnapshotRequest(reasonCode = 1)
            )
        )
        driver.tick()
        testScheduler.runCurrent()

        val chunks = sentPackets.filterIsInstance<NetplayPacket.SnapshotChunk>()
        val snapshotId = chunks.first().snapshotId
        val ackAll = NetplayPacket.SessionControl.SnapshotAck(
            snapshotId = snapshotId,
            acknowledgedChunks = chunks.map { it.chunkIndex }
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = ackAll))
        driver.tick()
        testScheduler.runCurrent()

        val sendCountsByType = sentPackets.filterIsInstance<NetplayPacket.SnapshotChunk>()
            .groupingBy { it.chunkIndex }
            .eachCount()
        sendCountsByType.values.forEach { count ->
            assertEquals("each chunk should have been sent exactly once before ack", 1, count)
        }

        driver.stop()
    }

    @Test
    fun `goodbye control message triggers session end callback`() = runTest(UnconfinedTestDispatcher()) {
        var endedReason: String? = null
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())

        retroView = mockk(relaxed = true)
        every { retroView.serializeState() } returns byteArrayOf(1, 2, 3)

        sentPackets = mutableListOf()
        incomingFlow = MutableSharedFlow(extraBufferCapacity = 256)
        fakeOps = FakeLibretroNetplayOps()

        transport = mockk(relaxed = true)
        every { transport.incomingPackets } returns incomingFlow.asSharedFlow() as SharedFlow<NetplayTransport.Incoming>
        coEvery { transport.send(any(), any()) } answers {
            sentPackets += secondArg<NetplayPacket>()
        }
        coEvery { transport.close() } just Runs

        val driver = NetplayHostDriver(
            retroView = retroView,
            transport = transport,
            peerAddress = peer,
            peerUserId = "guest",
            localPort = 0,
            guestPort = 1,
            inputShadow = NetplayInputShadow(),
            scope = scope,
            onSessionEnd = { endedReason = it },
            libretroOps = fakeOps
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
}
