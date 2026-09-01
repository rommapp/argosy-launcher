package com.nendo.argosy.data.sync.xbox

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Qcow2ImageTest {

    @Test
    fun `rejects a file that is not qcow2`() {
        val file = MemorySeekableFile(256)
        file.write(0, "not an image at all, just bytes".toByteArray(), 0, 30)

        assertNull(Qcow2Image.open(file))
    }

    @Test
    fun `rejects an image flagged corrupt`() {
        val fixture = Qcow2Fixture(virtualSize = 1 shl 20)
        fixture.incompatibleFeatures = 2
        fixture.writeGuest(0, ByteArray(16) { 1 })

        assertNull(Qcow2Image.open(fixture.build()))
    }

    @Test
    fun `reads a span crossing a cluster boundary`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 20)
        val first = ByteArray(0x10000) { 0xAA.toByte() }
        val second = ByteArray(0x10000) { 0xBB.toByte() }
        fixture.writeGuest(0, first)
        fixture.writeGuest(0x10000, second)

        val image = Qcow2Image.open(fixture.build())!!
        val out = ByteArray(8)
        image.read(0xFFFC, out, 0, 8)

        assertArrayEquals(
            byteArrayOf(
                0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(),
                0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte(), 0xBB.toByte()
            ),
            out
        )
    }

    @Test
    fun `unallocated clusters read as zero`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 30)
        fixture.writeGuest(0, ByteArray(16) { 0x7F })

        val image = Qcow2Image.open(fixture.build())!!
        assertFalse(image.isAllocated(0x20000000))

        val out = ByteArray(64) { 0x55 }
        image.read(0x20000000, out, 0, 64)
        assertArrayEquals(ByteArray(64), out)
    }

    @Test
    fun `writes into an allocated cluster round trip`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 20)
        fixture.writeGuest(0, ByteArray(0x10000))

        val image = Qcow2Image.open(fixture.build())!!
        val payload = "argosy".toByteArray()
        image.write(0x40, payload, 0, payload.size)

        val out = ByteArray(payload.size)
        image.read(0x40, out, 0, out.size)
        assertArrayEquals(payload, out)
    }

    @Test
    fun `refuses to write an unallocated cluster`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 30)
        fixture.writeGuest(0, ByteArray(16))

        val image = Qcow2Image.open(fixture.build())!!
        val failure = runCatching { image.write(0x20000000, ByteArray(4), 0, 4) }.exceptionOrNull()

        assertTrue(failure is Qcow2Image.UnallocatedClusterException)
    }

    @Test
    fun `refuses to write an image left dirty`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 20)
        fixture.incompatibleFeatures = 1
        fixture.writeGuest(0, ByteArray(0x10000))

        val image = Qcow2Image.open(fixture.build())!!
        assertTrue(image.isDirty)
        assertTrue(runCatching { image.write(0, ByteArray(4), 0, 4) }.isFailure)
    }

    @Test
    fun `reads outside the virtual size fail`() {
        val fixture = Qcow2Fixture(virtualSize = 1L shl 20)
        fixture.writeGuest(0, ByteArray(16))

        val image = Qcow2Image.open(fixture.build())!!
        assertEquals(1L shl 20, image.virtualSize)
        assertTrue(runCatching { image.read(image.virtualSize - 2, ByteArray(8), 0, 8) }.isFailure)
    }
}
