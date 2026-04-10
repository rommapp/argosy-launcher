package com.nendo.argosy.data.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayPacketTest {

    @Test
    fun inputBundle_roundTrip() {
        val original = NetplayPacket.InputBundle(
            frameIndex = 0x0123456789ABCDEFL,
            ports = listOf(
                NetplayPacket.PortInputState(port = 0, bitmask = 0x0000FFFF),
                NetplayPacket.PortInputState(port = 1, bitmask = 0x00000001)
            )
        )

        val bytes = original.serialize()
        val decoded = NetplayPacket.deserialize(bytes)

        assertNotNull(decoded)
        assertTrue(decoded is NetplayPacket.InputBundle)
        decoded as NetplayPacket.InputBundle
        assertEquals(original.frameIndex, decoded.frameIndex)
        assertEquals(original.ports.size, decoded.ports.size)
        assertEquals(0, decoded.ports[0].port)
        assertEquals(0x0000FFFF, decoded.ports[0].bitmask)
        assertEquals(1, decoded.ports[1].port)
        assertEquals(0x00000001, decoded.ports[1].bitmask)
    }

    @Test
    fun inputBundle_emptyPorts_roundTrip() {
        val original = NetplayPacket.InputBundle(frameIndex = 0L, ports = emptyList())
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.InputBundle)
        assertEquals(0, (decoded as NetplayPacket.InputBundle).ports.size)
    }

    @Test
    fun guestInput_roundTrip() {
        val original = NetplayPacket.GuestInput(
            guestFrameIndex = 42L,
            portNumber = 2,
            bitmask = 0x10203040.toInt()
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.GuestInput)
        decoded as NetplayPacket.GuestInput
        assertEquals(42L, decoded.guestFrameIndex)
        assertEquals(2, decoded.portNumber)
        assertEquals(0x10203040.toInt(), decoded.bitmask)
    }

    @Test
    fun snapshotRequest_roundTrip() {
        val original = NetplayPacket.SnapshotRequest(reasonCode = 2)
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.SnapshotRequest)
        assertEquals(2, (decoded as NetplayPacket.SnapshotRequest).reasonCode)
    }

    @Test
    fun snapshotChunk_roundTrip() {
        val payload = ByteArray(1300) { (it and 0xFF).toByte() }
        val original = NetplayPacket.SnapshotChunk(
            snapshotId = 7,
            chunkIndex = 42,
            chunkTotal = 250,
            payload = payload
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.SnapshotChunk)
        decoded as NetplayPacket.SnapshotChunk
        assertEquals(7, decoded.snapshotId)
        assertEquals(42, decoded.chunkIndex)
        assertEquals(250, decoded.chunkTotal)
        assertTrue(payload.contentEquals(decoded.payload))
    }

    @Test
    fun snapshotChunk_emptyPayload_roundTrip() {
        val original = NetplayPacket.SnapshotChunk(
            snapshotId = 0,
            chunkIndex = 0,
            chunkTotal = 1,
            payload = ByteArray(0)
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.SnapshotChunk)
        assertEquals(0, (decoded as NetplayPacket.SnapshotChunk).payload.size)
    }

    @Test
    fun ping_roundTrip() {
        val original = NetplayPacket.Ping(timestampNanos = 1_234_567_890L)
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.Ping)
        assertEquals(1_234_567_890L, (decoded as NetplayPacket.Ping).timestampNanos)
    }

    @Test
    fun pong_roundTrip() {
        val original = NetplayPacket.Pong(timestampNanos = Long.MAX_VALUE)
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.Pong)
        assertEquals(Long.MAX_VALUE, (decoded as NetplayPacket.Pong).timestampNanos)
    }

    @Test
    fun sessionControlGoodbye_roundTrip() {
        val decoded = NetplayPacket.deserialize(NetplayPacket.SessionControl.Goodbye.serialize())
        assertTrue(decoded is NetplayPacket.SessionControl.Goodbye)
    }

    @Test
    fun sessionControlKick_roundTrip() {
        val decoded = NetplayPacket.deserialize(NetplayPacket.SessionControl.Kick.serialize())
        assertTrue(decoded is NetplayPacket.SessionControl.Kick)
    }

    @Test
    fun sessionControlSnapshotAck_roundTrip() {
        val original = NetplayPacket.SessionControl.SnapshotAck(
            snapshotId = 99,
            acknowledgedChunks = listOf(0, 1, 2, 5, 17, 65535)
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.SessionControl.SnapshotAck)
        decoded as NetplayPacket.SessionControl.SnapshotAck
        assertEquals(99, decoded.snapshotId)
        assertEquals(listOf(0, 1, 2, 5, 17, 65535), decoded.acknowledgedChunks)
    }

    @Test
    fun sessionControlSnapshotAck_empty_roundTrip() {
        val original = NetplayPacket.SessionControl.SnapshotAck(
            snapshotId = 3,
            acknowledgedChunks = emptyList()
        )
        val decoded = NetplayPacket.deserialize(original.serialize())
        assertTrue(decoded is NetplayPacket.SessionControl.SnapshotAck)
        assertEquals(0, (decoded as NetplayPacket.SessionControl.SnapshotAck).acknowledgedChunks.size)
    }

    @Test
    fun deserialize_emptyBytes_returnsNull() {
        assertNull(NetplayPacket.deserialize(ByteArray(0)))
    }

    @Test
    fun deserialize_unknownType_returnsNull() {
        assertNull(NetplayPacket.deserialize(byteArrayOf(0x7F, 0x00, 0x00)))
    }

    @Test
    fun typeBytes_matchSpec() {
        assertEquals(0x01.toByte(), NetplayPacket.TYPE_INPUT_BUNDLE)
        assertEquals(0x02.toByte(), NetplayPacket.TYPE_GUEST_INPUT)
        assertEquals(0x03.toByte(), NetplayPacket.TYPE_SNAPSHOT_REQUEST)
        assertEquals(0x04.toByte(), NetplayPacket.TYPE_SNAPSHOT_CHUNK)
        assertEquals(0x05.toByte(), NetplayPacket.TYPE_PING)
        assertEquals(0x06.toByte(), NetplayPacket.TYPE_PONG)
        assertEquals(0x07.toByte(), NetplayPacket.TYPE_SESSION_CONTROL)
    }

    @Test
    fun sessionControlSubtypes_matchSpec() {
        assertEquals(0x00.toByte(), NetplayPacket.SESSION_CONTROL_GOODBYE)
        assertEquals(0x01.toByte(), NetplayPacket.SESSION_CONTROL_KICK)
        assertEquals(0x02.toByte(), NetplayPacket.SESSION_CONTROL_SNAPSHOT_ACK)
    }
}
