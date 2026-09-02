package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.ArchiveRoot
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
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

/**
 * A 3DS save unit is the title `data` directory plus the extdata directory the low title id
 * derives, and some titles keep every byte of progress in the latter. Fantasy Life
 * (`00040000/00113200`) writes `extdata/00000000/00001132/user/fl_ext0.fsd` and nothing under
 * `title/00040000/00113200/data`, which is the shape most of these fixtures reproduce.
 */
class N3dsExtdataUnitTest {

    private lateinit var tempDir: File
    private lateinit var handler: FolderSaveHandler
    private lateinit var archiver: SaveArchiver
    private lateinit var sdRoot: File

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val id0 = "abcdef0123456789abcdef0123456789"
    private val id1 = "fedcba9876543210fedcba9876543210"
    private val baseCategory = "00040000"
    private val updateCategory = "0004000e"
    private val lowId = "00113200"
    private val extdataId = "00001132"
    private val saveId = "$baseCategory/$lowId"

    private val config = SavePathConfig(
        emulatorId = "azahar",
        defaultPaths = emptyList(),
        saveExtensions = listOf("*"),
        usesFolderBasedSaves = true
    )

    @Before
    fun setUp() {
        tempDir = createTempDirectory("n3ds_extdata").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        archiver = SaveArchiver(androidDataAccessor, fal)
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = archiver,
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("3ds") ?: error("3DS handler not registered")
        sdRoot = File(tempDir, "source/Nintendo 3DS").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun titleData(root: File, category: String = baseCategory): File =
        File(root, "$id0/$id1/title/$category/$lowId/data")

    private fun extdata(root: File): File =
        File(root, "$id0/$id1/extdata/00000000/$extdataId")

    private fun writeTitleData(root: File, category: String = baseCategory, bytes: ByteArray = byteArrayOf(1, 2, 3)): File =
        titleData(root, category).apply {
            mkdirs()
            File(this, "00000001").writeBytes(bytes)
        }

    private fun writeExtdata(root: File, bytes: ByteArray = byteArrayOf(9, 8, 7)): File =
        extdata(root).apply {
            File(this, "user").mkdirs()
            File(this, "user/fl_ext0.fsd").writeBytes(bytes)
        }

    private fun saveContext(localSavePath: String) = SaveContext(
        config = config,
        romPath = null,
        saveId = saveId,
        emulatorPackage = "io.github.lime3ds.android",
        gameId = 1L,
        gameTitle = "Fantasy Life",
        platformSlug = "3ds",
        emulatorId = "azahar",
        localSavePath = localSavePath
    )

    @Test
    fun `a title that keeps its progress only in extdata is discovered`() {
        val ext = writeExtdata(sdRoot)

        assertEquals(ext.absolutePath, handler.findSaveFolderBySaveId(sdRoot.absolutePath, saveId))
    }

    @Test
    fun `the extdata id is the low title id shifted right by eight bits`() {
        val otherRoot = File(tempDir, "other/Nintendo 3DS")
        val ext = File(otherRoot, "$id0/$id1/extdata/00000000/0000175e").apply { mkdirs() }
        File(ext, "progress.bin").writeBytes(byteArrayOf(1))

        assertEquals(ext.absolutePath, handler.findSaveFolderBySaveId(otherRoot.absolutePath, "0004000000175E00"))
    }

    @Test
    fun `the title data directory stays the resolved path when both trees exist`() {
        val data = writeTitleData(sdRoot)
        val ext = writeExtdata(sdRoot)

        assertEquals(data.absolutePath, handler.findSaveFolderBySaveId(sdRoot.absolutePath, saveId))
        assertEquals(
            listOf(data.absolutePath, ext.absolutePath),
            handler.findAllSaveFoldersBySaveId(sdRoot.absolutePath, saveId)
        )
    }

    @Test
    fun `neither tree present is no save`() {
        File(sdRoot, "$id0/$id1/title/$baseCategory/$lowId").mkdirs()
        File(sdRoot, "$id0/$id1/extdata/00000000").mkdirs()

        assertNull(handler.findSaveFolderBySaveId(sdRoot.absolutePath, saveId))
        assertTrue(handler.findAllSaveFoldersBySaveId(sdRoot.absolutePath, saveId).isEmpty())
    }

    @Test
    fun `the base category wins over a newer update tree carrying the same low id`() {
        val base = writeTitleData(sdRoot, baseCategory).also {
            File(it, "00000001").setLastModified(1_000_000L)
        }
        writeTitleData(sdRoot, updateCategory).also {
            File(it, "00000001").setLastModified(System.currentTimeMillis())
        }

        assertEquals(base.absolutePath, handler.findSaveFolderBySaveId(sdRoot.absolutePath, saveId))
    }

    @Test
    fun `source paths from either component name the whole unit`() = runTest {
        val data = writeTitleData(sdRoot)
        val ext = writeExtdata(sdRoot)
        val expected = listOf(data.absolutePath, ext.absolutePath)

        assertEquals(expected, handler.sourcePathsFor(data.absolutePath, saveContext(data.absolutePath)))
        assertEquals(expected, handler.sourcePathsFor(ext.absolutePath, saveContext(ext.absolutePath)))
    }

    @Test
    fun `both trees round-trip under data and extdata roots`() = runTest {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(1, 2, 3))
        writeExtdata(sdRoot, bytes = byteArrayOf(9, 8, 7))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = titleData(destRoot)

        val prepared = handler.prepareForUpload(data.absolutePath, saveContext(data.absolutePath))
            ?: error("prepareForUpload returned null")
        assertEquals(setOf("data", "extdata"), archiver.peekRootEntryNames(prepared.file))

        val result = handler.extractDownload(prepared.file, saveContext(destData.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(listOf<Byte>(1, 2, 3), File(destData, "00000001").readBytes().toList())
        assertEquals(listOf<Byte>(9, 8, 7), File(extdata(destRoot), "user/fl_ext0.fsd").readBytes().toList())
    }

    @Test
    fun `an extdata-only archive restores into the extdata tree whichever component is the target`() = runTest {
        val ext = writeExtdata(sdRoot, bytes = byteArrayOf(4, 5, 6))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = titleData(destRoot)

        val prepared = handler.prepareForUpload(ext.absolutePath, saveContext(ext.absolutePath))
            ?: error("prepareForUpload returned null")
        assertEquals(setOf("extdata"), archiver.peekRootEntryNames(prepared.file))

        val result = handler.extractDownload(prepared.file, saveContext(destData.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(listOf<Byte>(4, 5, 6), File(extdata(destRoot), "user/fl_ext0.fsd").readBytes().toList())
        assertFalse("extdata contents must not land in the title tree", File(destData, "user").exists())
    }

    @Test
    fun `a legacy archive rooted at data alone restores exactly as before`() = runTest {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(7, 7, 7))
        val legacy = File(tempDir, "legacy.zip")
        assertTrue(archiver.zipFolder(data, legacy))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = titleData(destRoot)

        val result = handler.extractDownload(legacy, saveContext(destData.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(destData.absolutePath, result.targetPath)
        assertEquals(listOf<Byte>(7, 7, 7), File(destData, "00000001").readBytes().toList())
        assertFalse("a data-only archive creates no extdata", extdata(destRoot).exists())
    }

    @Test
    fun `a legacy archive restored over an extdata-resolved path still lands in the title tree`() = runTest {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(2, 2))
        val legacy = File(tempDir, "legacy.zip")
        assertTrue(archiver.zipFolder(data, legacy))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destExt = writeExtdata(destRoot)

        val result = handler.extractDownload(legacy, saveContext(destExt.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(listOf<Byte>(2, 2), File(titleData(destRoot), "00000001").readBytes().toList())
        assertFalse("title data must not be unpacked into extdata", File(destExt, "00000001").exists())
    }

    @Test
    fun `a legacy data archive restored over a unit with extdata leaves the extdata untouched`() = runTest {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(1, 1))
        val legacy = File(tempDir, "legacy.zip")
        assertTrue(archiver.zipFolder(data, legacy))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = writeTitleData(destRoot, bytes = byteArrayOf(0, 0))
        val destExt = writeExtdata(destRoot, bytes = byteArrayOf(6, 6, 6))
        File(destExt, "user/extra.bin").writeBytes(byteArrayOf(4))

        val result = handler.extractDownload(legacy, saveContext(destData.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(listOf<Byte>(1, 1), File(destData, "00000001").readBytes().toList())
        assertEquals(listOf<Byte>(6, 6, 6), File(destExt, "user/fl_ext0.fsd").readBytes().toList())
        assertEquals(listOf<Byte>(4), File(destExt, "user/extra.bin").readBytes().toList())
    }

    @Test
    fun `a component the archive carries is replaced rather than overlaid`() = runTest {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(1))
        val prepared = handler.prepareForUpload(data.absolutePath, saveContext(data.absolutePath))
            ?: error("prepareForUpload returned null")
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = writeTitleData(destRoot, bytes = byteArrayOf(0))
        File(destData, "stale.bin").writeBytes(byteArrayOf(9))

        val result = handler.extractDownload(prepared.file, saveContext(destData.absolutePath))

        assertTrue(result.error ?: "", result.success)
        assertEquals(listOf<Byte>(1), File(destData, "00000001").readBytes().toList())
        assertFalse("stale files in a replaced component must go", File(destData, "stale.bin").exists())
    }

    @Test
    fun `the pre-restore clear keeps components the archive does not carry`() {
        val data = writeTitleData(sdRoot)
        val ext = writeExtdata(sdRoot)
        val unit = listOf(data.absolutePath, ext.absolutePath)

        assertEquals(listOf(data.absolutePath), handler.pathsClearedBeforeRestore(unit, setOf("data")))
        assertEquals(listOf(ext.absolutePath), handler.pathsClearedBeforeRestore(unit, setOf("extdata")))
        assertEquals(unit, handler.pathsClearedBeforeRestore(unit, setOf("data", "extdata")))
        assertTrue("an unread archive defers the clear to placement", handler.pathsClearedBeforeRestore(unit, null).isEmpty())
    }

    @Test
    fun `placing a cached archive goes through the same root mapping`() {
        val data = writeTitleData(sdRoot, bytes = byteArrayOf(3, 3))
        writeExtdata(sdRoot, bytes = byteArrayOf(5, 5))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = titleData(destRoot).apply { mkdirs() }
        val zip = File(tempDir, "cached.zip")
        val roots = handler.namedArchiveRoots(data.absolutePath, saveId) ?: error("3DS must name its roots")
        assertTrue(archiver.zipNamedFolders(roots, zip))

        assertTrue(handler.placeArchive(zip, destData, saveId))

        assertEquals(listOf<Byte>(3, 3), File(destData, "00000001").readBytes().toList())
        assertEquals(listOf<Byte>(5, 5), File(extdata(destRoot), "user/fl_ext0.fsd").readBytes().toList())
    }

    @Test
    fun `an archive carrying a root the layout cannot place is refused`() = runTest {
        val staging = File(tempDir, "staging/elsewhere").apply { mkdirs() }
        File(staging, "loose.bin").writeBytes(byteArrayOf(1))
        val data = writeTitleData(sdRoot)
        val zip = File(tempDir, "mixed.zip")
        assertTrue(archiver.zipNamedFolders(listOf(ArchiveRoot("data", data), ArchiveRoot("elsewhere", staging)), zip))
        val destRoot = File(tempDir, "dest/Nintendo 3DS")
        val destData = titleData(destRoot)

        val result = handler.extractDownload(zip, saveContext(destData.absolutePath))

        assertFalse(result.success)
        assertFalse("nothing is placed from a refused archive", File(destData, "00000001").exists())
    }
}
