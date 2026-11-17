package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.SaveFixtures
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class WiiUFolderHandlerTest {

    private lateinit var tempDir: File
    private lateinit var base: File
    private lateinit var handler: FolderSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val splitId = "00050000/1012bd00"
    private val flatId = "000500001012BD00"
    private val lowId = "1012bd00"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("wiiu_handler").toFile()
        base = File(tempDir, "mlc01/usr/save/00050000").apply { mkdirs() }
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
            emulatorSaveConfigRepository = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("wiiu") ?: error("Wii U handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `split flat and low ids all construct the title folder under the category base`() {
        val expected = "${base.path}/$lowId"
        assertEquals(expected, handler.constructSavePath(base.path, splitId))
        assertEquals(expected, handler.constructSavePath(base.path, flatId))
        assertEquals(expected, handler.constructSavePath(base.path, lowId))
    }

    @Test
    fun `a demo id resolves under its own category beside the base`() {
        val demo = cemuSave(File(base.parentFile, "00050002/$lowId"))

        assertEquals(demo.path, handler.constructSavePath(base.path, "00050002/$lowId"))
        assertEquals(demo.path, handler.findSaveFolderBySaveId(base.path, "00050002$lowId"))
        assertNull(handler.findSaveFolderBySaveId(base.path, splitId))
    }

    @Test
    fun `split id finds the existing title folder`() {
        val title = cemuSave(File(base, lowId))

        assertEquals(title.path, handler.findSaveFolderBySaveId(base.path, splitId))
        assertEquals(title.path, handler.findSaveFolderBySaveId(base.path, flatId))
    }

    @Test
    fun `another title's folder is not this save`() {
        cemuSave(File(base, "10143500"))

        assertNull(handler.findSaveFolderBySaveId(base.path, splitId))
    }

    @Test
    fun `uploaded title folder restores byte-identical with a split id`() = runTest {
        val source = cemuSave(File(tempDir, "device/$lowId"))
        val ctx = saveContext(localSavePath = null)

        val prepared = handler.prepareForUpload(source.absolutePath, ctx) ?: error("prepareForUpload returned null")
        assertEquals(FolderSaveHandler.ArchiveRootMatch.EXACT, handler.matchArchive(prepared.file, splitId))

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)
        assertEquals("${base.path}/$lowId", result.targetPath)
        assertSameTree(source, File(result.targetPath!!))
        assertFalse(File(base, "00050000").exists())
    }

    @Test
    fun `archive of the user folder lands under the title's user folder`() = runTest {
        val archive = zipOf(
            "80000001/config.sav" to byteArrayOf(1, 2),
            "80000001/gen0.sav" to byteArrayOf(3, 4, 5),
            "common/dummy.sav" to byteArrayOf(6)
        )

        assertEquals(FolderSaveHandler.ArchiveRootMatch.UNIDENTIFIED, handler.matchArchive(archive, splitId))

        val result = handler.extractDownload(archive, saveContext(localSavePath = null))
        assertTrue("Extract reported failure: ${result.error}", result.success)
        val user = File(base, "$lowId/user")
        assertEquals(listOf<Byte>(3, 4, 5), File(user, "80000001/gen0.sav").readBytes().toList())
        assertEquals(listOf<Byte>(1, 2), File(user, "80000001/config.sav").readBytes().toList())
        assertEquals(listOf<Byte>(6), File(user, "common/dummy.sav").readBytes().toList())
    }

    @Test
    fun `archive of another title is refused`() = runTest {
        val other = cemuSave(File(tempDir, "device/10143500"))
        val prepared = handler.prepareForUpload(other.absolutePath, saveContext(localSavePath = null))
            ?: error("prepareForUpload returned null")

        assertNull(handler.matchArchive(prepared.file, splitId))
        assertFalse(handler.extractDownload(prepared.file, saveContext(localSavePath = null)).success)
        assertFalse(File(base, lowId).exists())
    }

    @Test
    fun `archive mixing another title with common is refused`() = runTest {
        val archive = zipOf(
            "10143500/user/80000001/gen0.sav" to byteArrayOf(1),
            "common/dummy.sav" to byteArrayOf(2)
        )

        assertNull(handler.matchArchive(archive, splitId))
        assertFalse(handler.extractDownload(archive, saveContext(localSavePath = null)).success)
        assertFalse(File(base, lowId).exists())
    }

    private fun cemuSave(title: File): File {
        File(title, "meta").mkdirs()
        File(title, "meta/meta.xml").writeText("<menu/>")
        File(title, "user/80000001").mkdirs()
        File(title, "user/80000001/gen0.sav").writeBytes(ByteArray(64) { it.toByte() })
        File(title, "user/common").mkdirs()
        File(title, "user/common/dummy.sav").writeBytes(byteArrayOf(9, 9))
        return title
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val zip = File(tempDir, "server_${System.nanoTime()}.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return zip
    }

    private fun assertSameTree(expected: File, actual: File) {
        val expectedTree = SaveFixtures.fileTree(expected)
        val actualTree = SaveFixtures.fileTree(actual)
        assertEquals(expectedTree.map { it.first }, actualTree.map { it.first })
        expectedTree.zip(actualTree).forEach { (want, got) ->
            assertEquals("Bytes differ for ${want.first}", want.second.toList(), got.second.toList())
        }
    }

    private fun saveContext(localSavePath: String?) = SaveContext(
        config = SavePathConfig(
            emulatorId = "cemu_dualscreen",
            defaultPaths = listOf(base.path),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
        ),
        romPath = null,
        saveId = splitId,
        emulatorPackage = "info.cemu.cemu",
        gameId = 1L,
        gameTitle = "Pikmin 3",
        platformSlug = "wiiu",
        emulatorId = "cemu_dualscreen",
        localSavePath = localSavePath,
    )
}
