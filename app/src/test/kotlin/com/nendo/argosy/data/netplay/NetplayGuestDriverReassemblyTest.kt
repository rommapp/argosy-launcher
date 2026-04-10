package com.nendo.argosy.data.netplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayGuestDriverReassemblyTest {

    @Test
    fun reassemblesChunksInOrder() {
        val total = 4
        val chunks = List(total) { idx ->
            NetplayPacket.SnapshotChunk(
                snapshotId = 7,
                chunkIndex = idx,
                chunkTotal = total,
                payload = byteArrayOf((idx * 4).toByte(), (idx * 4 + 1).toByte(), (idx * 4 + 2).toByte(), (idx * 4 + 3).toByte())
            )
        }
        val buffer = NetplayGuestDriver.ReassemblyBuffer(7, total)
        chunks.forEach(buffer::addChunk)
        assertTrue(buffer.isComplete())
        val assembled = buffer.assemble()
        assertNotNull(assembled)
        val expected = ByteArray(16) { it.toByte() }
        assertArrayEquals(expected, assembled)
    }

    @Test
    fun reassemblesChunksOutOfOrder() {
        val total = 3
        val chunks = listOf(
            NetplayPacket.SnapshotChunk(1, 2, total, byteArrayOf(6, 7, 8)),
            NetplayPacket.SnapshotChunk(1, 0, total, byteArrayOf(0, 1, 2)),
            NetplayPacket.SnapshotChunk(1, 1, total, byteArrayOf(3, 4, 5))
        )
        val buffer = NetplayGuestDriver.ReassemblyBuffer(1, total)
        chunks.forEach(buffer::addChunk)
        assertTrue(buffer.isComplete())
        val assembled = buffer.assemble()
        assertNotNull(assembled)
        val expected = ByteArray(9) { it.toByte() }
        assertArrayEquals(expected, assembled)
    }

    @Test
    fun incompleteBufferReturnsNull() {
        val buffer = NetplayGuestDriver.ReassemblyBuffer(3, 4)
        buffer.addChunk(NetplayPacket.SnapshotChunk(3, 0, 4, byteArrayOf(1)))
        buffer.addChunk(NetplayPacket.SnapshotChunk(3, 2, 4, byteArrayOf(3)))
        assertFalse(buffer.isComplete())
    }

    @Test
    fun duplicateChunksAreIgnored() {
        val buffer = NetplayGuestDriver.ReassemblyBuffer(5, 2)
        val chunk = NetplayPacket.SnapshotChunk(5, 0, 2, byteArrayOf(9))
        buffer.addChunk(chunk)
        val firstProgress = buffer.lastProgressNanos
        Thread.sleep(2)
        buffer.addChunk(NetplayPacket.SnapshotChunk(5, 0, 2, byteArrayOf(42)))
        // duplicate index ignored so lastProgressNanos should not advance
        assertTrue(buffer.lastProgressNanos == firstProgress)
        buffer.addChunk(NetplayPacket.SnapshotChunk(5, 1, 2, byteArrayOf(11)))
        assertTrue(buffer.isComplete())
        val assembled = buffer.assemble()
        assertArrayEquals(byteArrayOf(9, 11), assembled)
    }
}
