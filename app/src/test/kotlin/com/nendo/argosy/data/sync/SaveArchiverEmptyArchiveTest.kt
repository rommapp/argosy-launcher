package com.nendo.argosy.data.sync

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * A zip holding no files is 22 bytes of end-of-directory record and nothing else. Cached as a
 * save it can never be uploaded, so it fails every sync forever with no way for the user to
 * clear it. An archive that captured nothing is a failure to archive, not an empty save.
 */
class SaveArchiverEmptyArchiveTest {

    private lateinit var tempDir: File
    private lateinit var saveArchiver: SaveArchiver
    private lateinit var target: File

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("empty_archive").toFile()
        saveArchiver = SaveArchiver(androidDataAccessor, realFsFal())
        target = File(tempDir, "out.zip")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun folder(name: String) = File(tempDir, name).apply { mkdirs() }

    @Test
    fun `an empty folder does not become an archive`() {
        val empty = folder("BASLUS-20152AC04")

        assertFalse(saveArchiver.zipFolder(empty, target))
        assertFalse("a refused archive must not be left behind", target.exists())
    }

    @Test
    fun `a folder of empty folders does not become an archive`() {
        val outer = folder("save")
        File(outer, "nested").mkdirs()
        File(outer, "nested/deeper").mkdirs()

        assertFalse(saveArchiver.zipFolder(outer, target))
        assertFalse(target.exists())
    }

    @Test
    fun `a folder holding a file still archives`() {
        val real = folder("BASLUS-20152AC04")
        File(real, "icon.sys").writeText("save data")

        assertTrue(saveArchiver.zipFolder(real, target))
        assertTrue(target.length() > 22)
    }

    @Test
    fun `a nested file is enough to archive`() {
        val outer = folder("save")
        File(outer, "data").mkdirs()
        File(outer, "data/00000001").writeText("real save bytes")

        assertTrue(saveArchiver.zipFolder(outer, target))
    }

    @Test
    fun `multi folder archiving refuses when every folder is empty`() {
        val a = folder("ULUS10064DATA00")
        val b = folder("ULUS10064SETTINGS")

        assertFalse(saveArchiver.zipFolders(listOf(a, b), target))
        assertFalse(target.exists())
    }

    @Test
    fun `multi folder archiving proceeds when one folder has content`() {
        val a = folder("ULUS10064DATA00").also { File(it, "save.bin").writeText("bytes") }
        val b = folder("ULUS10064SETTINGS")

        assertTrue(saveArchiver.zipFolders(listOf(a, b), target))
    }

    @Test
    fun `a folder whose files cannot be read does not become an archive`() {
        val unreadable = folder("BASLUS-20152AC04")
        File(unreadable, "icon.sys").writeText("save data")

        val fal = realFsFal()
        every { fal.getInputStream(any()) } returns null
        val archiver = SaveArchiver(androidDataAccessor, fal)

        assertFalse("unreadable is not empty, and must not archive as such", archiver.zipFolder(unreadable, target))
        assertFalse(target.exists())
    }
}
