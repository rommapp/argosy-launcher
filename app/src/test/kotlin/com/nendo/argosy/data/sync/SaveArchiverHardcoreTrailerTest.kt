package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SaveArchiverHardcoreTrailerTest {

    private lateinit var tempDir: File
    private lateinit var archiver: SaveArchiver

    @Before
    fun setUp() {
        tempDir = createTempDirectory("hc_trailer_test").toFile()
        archiver = SaveArchiver(
            androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true),
            fal = mockk<FileAccessLayer>(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `plain file has no trailer`() {
        val file = File(tempDir, "plain.sav").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        assertFalse(archiver.hasHardcoreTrailer(file))
        assertNull(archiver.readHardcoreTrailer(file))
    }

    @Test
    fun `appendHardcoreTrailer + readHardcoreTrailer round-trip`() {
        val file = File(tempDir, "save.bin").apply { writeBytes(ByteArray(256) { it.toByte() }) }
        val originalLen = file.length()

        assertTrue(archiver.appendHardcoreTrailer(file))
        assertTrue("File should grow after trailer append", file.length() > originalLen)

        val trailer = archiver.readHardcoreTrailer(file)
        assertNotNull("Trailer must be readable after append", trailer)
        assertEquals(1, trailer!!.version)
        assertTrue(archiver.hasHardcoreTrailer(file))
    }

    @Test
    fun `readBytesWithoutTrailer strips the appended trailer cleanly`() {
        val original = ByteArray(256) { it.toByte() }
        val file = File(tempDir, "save.bin").apply { writeBytes(original) }
        archiver.appendHardcoreTrailer(file)

        val stripped = archiver.readBytesWithoutTrailer(file)
        assertNotNull(stripped)
        assertEquals(original.toList(), stripped!!.toList())
    }

    @Test
    fun `readBytesWithoutTrailer on plain file returns full content`() {
        val original = ByteArray(128) { (it * 2).toByte() }
        val file = File(tempDir, "plain.sav").apply { writeBytes(original) }

        val read = archiver.readBytesWithoutTrailer(file)
        assertNotNull(read)
        assertEquals(original.toList(), read!!.toList())
    }
}
