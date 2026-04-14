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
class NetplayGuestDriverIntegrationTest {

    private val peer = InetSocketAddress("127.0.0.1", 50002)

    private lateinit var retroView: GLRetroView
    private lateinit var transport: NetplayTransport
    private lateinit var incomingFlow: MutableSharedFlow<NetplayTransport.Incoming>
    private lateinit var sentPackets: MutableList<NetplayPacket>
    private lateinit var fakeOps: FakeLibretroNetplayOps

    private fun buildDriver(
        scope: CoroutineScope,
        catchupThreshold: Int = NetplayGuestDriver.DEFAULT_CATCHUP_THRESHOLD,
        unserializeSucceeds: Boolean = true
    ): NetplayGuestDriver {
        retroView = mockk(relaxed = true)
        every { retroView.unserializeState(any()) } returns unserializeSucceeds

        sentPackets = mutableListOf()
        incomingFlow = MutableSharedFlow(extraBufferCapacity = 256)
        fakeOps = FakeLibretroNetplayOps()

        transport = mockk(relaxed = true)
        every { transport.incomingPackets } returns incomingFlow.asSharedFlow() as SharedFlow<NetplayTransport.Incoming>
        coEvery { transport.send(any(), any()) } answers {
            sentPackets += secondArg<NetplayPacket>()
        }
        coEvery { transport.close() } just Runs

        return NetplayGuestDriver(
            retroView = retroView,
            transport = transport,
            initialPeerAddress = peer,
            peerUserId = "host",
            localPort = 1,
            hostPort = 0,
            scope = scope,
            onSessionEnd = {},
            catchupThresholdFrames = catchupThreshold,
            libretroOps = fakeOps,
            framePeriodNanos = 0L
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `tick applies queued input bundle and calls libretro ops`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val bundle = NetplayPacket.InputBundle(
            frameIndex = 0L,
            ports = listOf(
                NetplayPacket.PortInputState(port = 0, bitmask = (1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A)),
                NetplayPacket.PortInputState(port = 1, bitmask = 0)
            )
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = bundle))
        testScheduler.runCurrent()

        driver.tick()
        driver.tick()
        testScheduler.runCurrent()

        val hostBit = 1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_A
        assertTrue(
            "expected setInputPortState(0, $hostBit) to have been called",
            fakeOps.setCalls.contains(0 to hostBit)
        )
        assertTrue(
            "expected setInputPortState(1, 0) to have been called",
            fakeOps.setCalls.contains(1 to 0)
        )
        assertTrue("expected at least 2 steps, got ${fakeOps.stepCount}", fakeOps.stepCount >= 2)

        driver.stop()
    }

    @Test
    fun `tick advances frame counter when bundle applied`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        for (i in 0 until 3) {
            val bundle = NetplayPacket.InputBundle(
                frameIndex = i.toLong(),
                ports = listOf(
                    NetplayPacket.PortInputState(port = 0, bitmask = 0),
                    NetplayPacket.PortInputState(port = 1, bitmask = 0)
                )
            )
            incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = bundle))
        }
        testScheduler.runCurrent()

        driver.tick()
        driver.tick()
        driver.tick()
        testScheduler.runCurrent()

        assertEquals(3, fakeOps.stepCount)

        driver.stop()
    }

    @Test
    fun `local input change produces GuestInput packet`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val expected = 1 shl NetplayInputShadow.RETRO_DEVICE_ID_JOYPAD_START
        fakeOps.setFakeInputBitmask(0, expected)
        driver.tick()
        testScheduler.runCurrent()

        val guestInputs = sentPackets.filterIsInstance<NetplayPacket.GuestInput>()
        assertEquals(1, guestInputs.size)
        assertEquals(1, guestInputs[0].portNumber)
        assertEquals(expected, guestInputs[0].bitmask)

        driver.stop()
    }

    @Test
    fun `periodic re-assert sends GuestInput every 10 frames without change`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        // Emit one bundle per tick so currentFrame advances continuously
        // (matching the real runtime where the host is streaming bundles).
        for (i in 0 until 25) {
            val bundle = NetplayPacket.InputBundle(
                frameIndex = i.toLong(),
                ports = listOf(
                    NetplayPacket.PortInputState(port = 0, bitmask = 0),
                    NetplayPacket.PortInputState(port = 1, bitmask = 0)
                )
            )
            incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = bundle))
            testScheduler.runCurrent()
            driver.tick()
            testScheduler.runCurrent()
        }

        val guestInputs = sentPackets.filterIsInstance<NetplayPacket.GuestInput>()
        assertTrue(
            "expected periodic re-assert to fire at least twice across 25 advancing frames, got ${guestInputs.size}",
            guestInputs.size >= 2
        )

        driver.stop()
    }

    @Test
    fun `catchup threshold triggers snapshot request`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            catchupThreshold = 5
        )

        val farFutureBundle = NetplayPacket.InputBundle(
            frameIndex = 100L,
            ports = listOf(
                NetplayPacket.PortInputState(port = 0, bitmask = 0),
                NetplayPacket.PortInputState(port = 1, bitmask = 0)
            )
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = farFutureBundle))
        testScheduler.runCurrent()

        driver.tick()
        testScheduler.runCurrent()

        val snapshotRequests = sentPackets.filterIsInstance<NetplayPacket.SnapshotRequest>()
        assertEquals(1, snapshotRequests.size)

        driver.stop()
    }

    @Test
    fun `snapshot chunk reassembly jumps sim to host frame`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            catchupThreshold = 5
        )

        val bundle = NetplayPacket.InputBundle(
            frameIndex = 50L,
            ports = listOf(
                NetplayPacket.PortInputState(port = 0, bitmask = 0),
                NetplayPacket.PortInputState(port = 1, bitmask = 0)
            )
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = bundle))
        driver.tick()
        testScheduler.runCurrent()

        val snapshotBytes = ByteArray(100) { it.toByte() }
        val chunk = NetplayPacket.SnapshotChunk(
            snapshotId = 1,
            chunkIndex = 0,
            chunkTotal = 1,
            payload = snapshotBytes
        )
        incomingFlow.emit(NetplayTransport.Incoming(source = peer, packet = chunk))
        driver.tick()
        testScheduler.runCurrent()

        io.mockk.verify { retroView.unserializeState(snapshotBytes) }

        driver.stop()
    }

    @Test
    fun `incoming Ping produces Pong response`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val pingTimestamp = 98765L
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
    fun `applyInitialSnapshot calls unserializeState`() = runTest(UnconfinedTestDispatcher()) {
        val driver = buildDriver(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))

        val snapshot = ByteArray(64) { (it * 3).toByte() }
        val result = driver.applyInitialSnapshot(snapshot)

        assertTrue(result)
        io.mockk.verify { retroView.unserializeState(snapshot) }

        driver.stop()
    }
}
