package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class WiiFolderHandlerTest {

    private lateinit var tempDir: File
    private lateinit var base: File
    private lateinit var handler: FolderSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val splitId = "00010000/524d4745"
    private val flatId = "00010000524D4745"
    private val lowId = "524d4745"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("wii_handler").toFile()
        base = File(tempDir, "Wii/title/00010000").apply { mkdirs() }
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = SaveArchiver(androidDataAccessor, fal),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            unitSaveHandler = mockk(relaxed = true),
            sigilSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("wii") ?: error("Wii handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `split flat and bare ids all construct the title folder under the category base`() {
        val expected = "${base.path}/$lowId"
        assertEquals(expected, handler.constructSavePath(base.path, splitId))
        assertEquals(expected, handler.constructSavePath(base.path, flatId))
        assertEquals(expected, handler.constructSavePath(base.path, lowId))
    }

    @Test
    fun `split and bare ids find the folder dolphin wrote`() {
        val title = dolphinSave(File(base, lowId))

        assertEquals(title.path, handler.findSaveFolderBySaveId(base.path, splitId))
        assertEquals(title.path, handler.findSaveFolderBySaveId(base.path, lowId))
        assertTrue(handler.isEntryForSaveId(lowId, splitId))
    }

    @Test
    fun `a channel id resolves under its own category beside the disc base`() {
        val channel = dolphinSave(File(base.parentFile, "00010001/48414141"))

        assertEquals(channel.path, handler.constructSavePath(base.path, "00010001/48414141"))
        assertEquals(channel.path, handler.findSaveFolderBySaveId(base.path, "00010001/48414141"))
    }

    @Test
    fun `an override set above the title folder still finds the save`() {
        val title = dolphinSave(File(base, lowId))

        assertEquals(title.path, handler.findSaveFolderBySaveId(base.parentFile.path, splitId))
        assertEquals(title.path, handler.findSaveFolderBySaveId(File(tempDir, "Wii").path, splitId))
        assertEquals(title.path, handler.findSaveFolderBySaveId(tempDir.path, splitId))
    }

    @Test
    fun `an override on the RetroArch User folder finds the dolphin core's save`() {
        val user = File(tempDir, "RetroArch/saves/dolphin-emu/User")
        val title = dolphinSave(File(user, "Wii/title/00010000/$lowId"))

        assertEquals(title.path, handler.findSaveFolderBySaveId(user.path, splitId))
        assertEquals(title.path, handler.findSaveFolderBySaveId(user.parentFile.path, splitId))
    }

    @Test
    fun `another title's folder is not this save`() {
        dolphinSave(File(base, "534d4e45"))

        assertNull(handler.findSaveFolderBySaveId(base.path, splitId))
    }

    @Test
    fun `an uploaded title folder restores into place with a split id`() = runTest {
        val source = dolphinSave(File(tempDir, "device/$lowId"))
        val ctx = saveContext()

        val prepared = handler.prepareForUpload(source.absolutePath, ctx) ?: error("prepareForUpload returned null")
        assertEquals(FolderSaveHandler.ArchiveRootMatch.EXACT, handler.matchArchive(prepared.file, splitId))

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)
        assertEquals("${base.path}/$lowId", result.targetPath)
        assertEquals(listOf<Byte>(7, 7, 7), File(base, "$lowId/data/GameData.bin").readBytes().toList())
        assertFalse(File(base, "00010000").exists())
    }

    private fun dolphinSave(title: File): File {
        File(title, "content").mkdirs()
        File(title, "content/title.tmd").writeBytes(byteArrayOf(1))
        File(title, "data").mkdirs()
        File(title, "data/GameData.bin").writeBytes(byteArrayOf(7, 7, 7))
        File(title, "data/banner.bin").writeBytes(byteArrayOf(2))
        return title
    }

    private fun saveContext() = SaveContext(
        config = SavePathConfig(
            emulatorId = "dolphin_wii",
            defaultPaths = listOf(base.path),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
        ),
        romPath = null,
        saveId = splitId,
        emulatorPackage = "org.dolphinemu.dolphinemu",
        gameId = 1L,
        gameTitle = "Super Mario Galaxy",
        platformSlug = "wii",
        emulatorId = "dolphin_wii",
        localSavePath = null,
    )
}
