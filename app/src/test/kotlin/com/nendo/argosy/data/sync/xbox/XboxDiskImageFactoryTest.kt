package com.nendo.argosy.data.sync.xbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class XboxDiskImageFactoryTest {

    @Test
    fun `a generated image is readable by the reader that syncs saves`() {
        val image = generate()

        val qcow2 = Qcow2Image.open(image)
        assertNotNull(qcow2)
        assertEquals(TOTAL_SECTORS * 512, qcow2!!.virtualSize)
        assertFalse(qcow2.isDirty)
    }

    @Test
    fun `the data partition is formatted and empty`() {
        val hdd = XboxHddImage.open(fakeAccess(generate()), IMAGE_PATH, writable = false)

        assertNotNull(hdd)
        assertEquals(emptyList<String>(), hdd!!.listSaveIds())
        assertFalse(hdd.hasSave("4D530064"))
    }

    @Test
    fun `every standard partition carries a FATX superblock`() {
        val qcow2 = Qcow2Image.open(generate())!!
        val magic = ByteArray(4)

        for (lba in listOf(0x400L, 0x177400L, 0x2EE400L, 0x465400L, 0x55F400L)) {
            qcow2.read(lba * 512, magic, 0, 4)
            assertEquals("FATX at sector $lba", "FATX", String(magic, Charsets.US_ASCII))
        }
    }

    @Test
    fun `the image stays sparse`() {
        val bytes = ByteArrayOutputStream()
        XboxDiskImageFactory.write(bytes, TOTAL_SECTORS)

        assertTrue(
            "a blank 8GB drive should not need megabytes on disk, got ${bytes.size()}",
            bytes.size() < 4 * 1024 * 1024
        )
    }

    @Test
    fun `refuses a disk too small to hold the data partition`() {
        assertTrue(
            runCatching { XboxDiskImageFactory.write(ByteArrayOutputStream(), 1024) }.isFailure
        )
    }

    private fun generate(): MemorySeekableFile {
        val bytes = ByteArrayOutputStream()
        XboxDiskImageFactory.write(bytes, TOTAL_SECTORS)
        val raw = bytes.toByteArray()
        val file = MemorySeekableFile(raw.size)
        file.write(0, raw, 0, raw.size)
        return file
    }

    private fun fakeAccess(file: MemorySeekableFile): com.nendo.argosy.data.storage.FileAccessLayer {
        val fal = io.mockk.mockk<com.nendo.argosy.data.storage.FileAccessLayer>()
        io.mockk.every { fal.openSeekable(any(), any()) } returns file
        return fal
    }

    private companion object {
        const val TOTAL_SECTORS = 0x1000000L
        const val IMAGE_PATH = "/x1box/hdd.img"
    }
}
