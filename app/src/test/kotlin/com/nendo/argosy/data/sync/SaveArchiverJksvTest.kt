package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.fixtures.SaveFixtures
import io.mockk.every
import io.mockk.mockk
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory

class SaveArchiverJksvTest {

    private lateinit var tempDir: File
    private lateinit var archiver: SaveArchiver

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_archiver_jksv").toFile()
        val fal = mockk<FileAccessLayer>(relaxed = true)
        every { fal.listFilesUnion(any()) } answers {
            File(firstArg<String>()).listFiles()?.map {
                FileInfo(it.absolutePath, it.name, it.isDirectory, it.isFile, it.length(), it.lastModified())
            } ?: emptyList()
        }
        every { fal.getInputStream(any()) } answers {
            val f = File(firstArg<String>())
            if (f.exists() && f.canRead()) f.inputStream() else null
        }
        every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `peekRootFolderName returns the single common root when all entries share it`() {
        val zip = makeZip("single_root.zip", listOf(
            "01007EF00011E000/data.sav" to "x",
            "01007EF00011E000/option.sav" to "y",
        ))
        assertEquals("01007EF00011E000", archiver.peekRootFolderName(zip))
    }

    @Test
    fun `peekRootFolderName returns null when first entry has no slash`() {
        val zip = makeZip("flat.zip", listOf("flat_entry.bin" to "x"))
        assertNull(archiver.peekRootFolderName(zip))
    }

    @Test
    fun `peekRootFolderName returns null when first entry has empty root segment`() {
        val zip = makeZip("empty_root.zip", listOf("/abc.bin" to "x"))
        assertNull(archiver.peekRootFolderName(zip))
    }

    @Test
    fun `isJksvFormat detects nx_save_meta at zip root`() {
        val meta = File(tempDir, ".nx_save_meta.bin").apply { writeBytes(SaveFixtures.jksvMetaBytes("01007EF00011E000")) }
        val data = File(tempDir, "data.sav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val zip = File(tempDir, "jksv.zip")
        assertTrue(archiver.zipFiles(listOf(meta, data), zip))

        assertTrue(archiver.isJksvFormat(zip))
    }

    @Test
    fun `isJksvFormat returns false for a non-JKSV zip`() {
        val zip = makeZip("plain.zip", listOf("save.bin" to "data"))
        assertFalse(archiver.isJksvFormat(zip))
    }

    @Test
    fun `parseTitleIdFromJksvMeta extracts the titleId at offset 5`() {
        val titleId = "01007EF00011E000"
        val meta = File(tempDir, ".nx_save_meta.bin").apply { writeBytes(SaveFixtures.jksvMetaBytes(titleId)) }
        val zip = File(tempDir, "jksv.zip")
        assertTrue(archiver.zipFiles(listOf(meta), zip))

        assertEquals(titleId, archiver.parseTitleIdFromJksvMeta(zip))
    }

    @Test
    fun `parseTitleIdFromJksvMeta returns null when magic is wrong`() {
        val bad = ByteArray(13).also {
            byteArrayOf(0x42, 0x41, 0x44, 0x21).copyInto(it, 0)
        }
        val meta = File(tempDir, ".nx_save_meta.bin").apply { writeBytes(bad) }
        val zip = File(tempDir, "bad.zip")
        assertTrue(archiver.zipFiles(listOf(meta), zip))

        assertNull(archiver.parseTitleIdFromJksvMeta(zip))
    }

    @Test
    fun `parseTitleIdFromJksvMeta returns null when the parsed id does not start with 01`() {
        val nonTitle = "FF007EF00011E000"
        val meta = File(tempDir, ".nx_save_meta.bin").apply { writeBytes(SaveFixtures.jksvMetaBytes(nonTitle)) }
        val zip = File(tempDir, "non_title.zip")
        assertTrue(archiver.zipFiles(listOf(meta), zip))

        assertNull(archiver.parseTitleIdFromJksvMeta(zip))
    }

    @Test
    fun `parseTitleIdFromJksvMeta returns null when zip has no meta entry`() {
        val zip = makeZip("no_meta.zip", listOf("data.sav" to "x"))
        assertNull(archiver.parseTitleIdFromJksvMeta(zip))
    }

    private fun makeZip(name: String, entries: List<Pair<String, String>>): File {
        val zip = File(tempDir, name)
        ZipArchiveOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
            entries.forEach { (entryName, content) ->
                zos.putArchiveEntry(ZipArchiveEntry(entryName))
                zos.write(content.toByteArray())
                zos.closeArchiveEntry()
            }
        }
        return zip
    }
}
