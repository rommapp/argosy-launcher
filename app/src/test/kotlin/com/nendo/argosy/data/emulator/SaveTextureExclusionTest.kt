package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.SaveArchiver
import io.mockk.every
import io.mockk.mockk
import org.apache.commons.compress.archivers.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveTextureExclusionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fal: FileAccessLayer
    private lateinit var androidDataAccessor: AndroidDataAccessor
    private lateinit var saveArchiver: SaveArchiver

    private fun listLocalFiles(path: String): List<FileInfo> {
        val dir = File(path)
        return dir.listFiles()?.map { child ->
            FileInfo(
                path = child.absolutePath,
                name = child.name,
                isDirectory = child.isDirectory,
                isFile = child.isFile,
                size = if (child.isFile) child.length() else 0L,
                lastModified = child.lastModified()
            )
        } ?: emptyList()
    }

    @Before
    fun setup() {
        fal = mockk(relaxed = true)
        androidDataAccessor = mockk(relaxed = true)

        every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        every { fal.isFile(any()) } answers { File(firstArg<String>()).isFile }
        every { fal.delete(any()) } answers { File(firstArg<String>()).delete() }
        every { fal.deleteRecursively(any()) } answers { File(firstArg<String>()).deleteRecursively() }
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { fal.getInputStream(any()) } answers {
            val f = File(firstArg<String>())
            if (f.isFile) f.inputStream() else null
        }
        every { fal.listFiles(any()) } answers { listLocalFiles(firstArg()) }
        every { fal.listFilesUnion(any()) } answers { listLocalFiles(firstArg()) }

        saveArchiver = SaveArchiver(androidDataAccessor, fal)
    }

    @Test
    fun `hashing and archiving save folder ignores Dolphin Load Textures files and empty Load dir`() {
        val dolphinRoot = tempFolder.newFolder("dolphin-emu")
        val saveFile = File(dolphinRoot, "User/GC/USA/Card A/01-GZLE-gczelda.gci").apply {
            parentFile?.mkdirs()
            writeText("actual_save_bytes")
        }

        val hashBeforeTextures = saveArchiver.calculateFolderAsZipHash(dolphinRoot)

        val textureFile1 = File(dolphinRoot, "User/Load/Textures/GZLE01/tex1.png").apply {
            parentFile?.mkdirs()
            writeText("large_hd_texture_payload_1")
        }
        val textureFile2 = File(dolphinRoot, "User/Load/Textures/GZLE01/sub/tex2.dds").apply {
            parentFile?.mkdirs()
            writeText("large_hd_texture_payload_2")
        }

        val hashAfterTextures = saveArchiver.calculateFolderAsZipHash(dolphinRoot)
        assertEquals(
            "Installing textures inside User/Load/Textures must not change the save hash",
            hashBeforeTextures,
            hashAfterTextures
        )

        val archiveZip = File(tempFolder.root, "save_bundle.zip")
        assertTrue(saveArchiver.zipFolder(dolphinRoot, archiveZip))
        val allZipEntries = ZipFile.builder().setFile(archiveZip).get().use { zf ->
            zf.entries.asSequence().map { it.name }.toList()
        }
        assertTrue(allZipEntries.any { it.endsWith("01-GZLE-gczelda.gci") })
        assertFalse(
            "Save archive must not contain Load/Textures or empty Load/ entries",
            allZipEntries.any { it.contains("Load") }
        )
        assertTrue(saveFile.exists())
        assertTrue(textureFile1.exists())
        assertTrue(textureFile2.exists())
    }

    @Test
    fun `deleting save directory via FileAccessLayer before restore preserves installed texture packs`() {
        val savesDir = tempFolder.newFolder("saves")
        val saveDataFile = File(savesDir, "User/GC/USA/Card A/01-GZLE-gczelda.gci").apply {
            parentFile?.mkdirs()
            writeText("old_save")
        }
        val textureFile = File(savesDir, "User/Load/Textures/GZLE01/tex1.png").apply {
            parentFile?.mkdirs()
            writeText("custom_texture")
        }
        val pspSaveFile = File(savesDir, "PSP/SAVEDATA/ULUS10041DATA00/DATA.BIN").apply {
            parentFile?.mkdirs()
            writeText("psp_save")
        }
        val pspTextureFile = File(savesDir, "PSP/TEXTURES/ULUS10041/ui.png").apply {
            parentFile?.mkdirs()
            writeText("psp_texture")
        }

        val pspRootResult = saveArchiver.deleteDirectoryPreservingTextures(
            File(savesDir, "PSP").absolutePath
        )
        assertTrue(pspRootResult.allNonTextureDeleted)
        assertTrue(pspRootResult.preservedTextures)
        assertFalse("PSP save file should be deleted when wiping PSP root", pspSaveFile.exists())
        assertTrue("PSP texture pack must be preserved when wiping PSP root directly", pspTextureFile.exists())

        val result = saveArchiver.deleteDirectoryPreservingTextures(savesDir.absolutePath)
        assertTrue(result.allNonTextureDeleted)
        assertTrue(result.preservedTextures)

        assertFalse("Save file should be deleted during pre-restore wipe", saveDataFile.exists())
        assertTrue("Dolphin texture pack must be preserved during save wipe", textureFile.exists())
        assertTrue("PPSSPP texture pack must be preserved during save wipe", pspTextureFile.exists())
    }

    @Test
    fun `moveStagedDirectoryPreservingTextures aborts and returns false if non-texture deletion fails`() {
        val targetDir = tempFolder.newFolder("target_saves")
        val lockedSave = File(targetDir, "locked.sav").apply {
            writeText("locked_save")
        }
        val stagedDir = tempFolder.newFolder("staged_saves").apply {
            File(this, "new.sav").writeText("new_save")
        }

        every { fal.delete(lockedSave.absolutePath) } returns false
        every { fal.exists(lockedSave.absolutePath) } returns true

        val success = saveArchiver.moveStagedDirectoryPreservingTextures(stagedDir, targetDir.absolutePath)
        assertFalse("Move must abort when non-texture file deletion fails", success)
    }
}
