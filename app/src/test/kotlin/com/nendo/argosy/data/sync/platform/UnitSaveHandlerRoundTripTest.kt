package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.ResolvedSaveUnit
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.sigil.SigilSaveMember
import com.nendo.sigil.SigilSaveUnit
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory

class UnitSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var handler: UnitSaveHandler
    private lateinit var archiver: SaveArchiver

    private val context = mockk<Context>(relaxed = true)
    private val resolver = mockk<SaveUnitResolver>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val retroArchHandler = mockk<RetroArchSaveHandler>(relaxed = true)
    private val defaultHandler = mockk<DefaultSaveHandler>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("unit_roundtrip").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        handler = UnitSaveHandler(context, fal, archiver, resolver, gameDao, retroArchHandler, defaultHandler)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `two members bundle flat and land back under their relative paths`() = runTest {
        val root = File(tempDir, "saves").apply { mkdirs() }
        val srm = File(root, "Crystal.srm").apply { writeBytes(ByteArray(8192) { it.toByte() }) }
        val rtc = File(root, "Crystal.rtc").apply { writeBytes(ByteArray(48) { (it * 3).toByte() }) }
        val unit = unitOf(root, listOf("Crystal.srm" to "Crystal.srm", "Crystal.rtc" to "Crystal.rtc"))
        coEvery { resolver.resolveForMember(srm.absolutePath, "mgba", "gbc", "Crystal.gbc", any(), false) } returns unit

        val prepared = handler.prepareForUpload(srm.absolutePath, saveContext(srm.absolutePath))
            ?: error("prepareForUpload returned null")
        assertTrue(prepared.isTemporary)
        assertEquals(listOf(srm.absolutePath, rtc.absolutePath), prepared.originalPaths)
        ZipFile(prepared.file).use { zf ->
            assertEquals(listOf("Crystal.srm", "Crystal.rtc"), zf.entries().toList().map { it.name })
        }

        val other = File(tempDir, "other").apply { mkdirs() }
        val target = File(other, "Crystal.srm").absolutePath
        coEvery {
            resolver.placeBundle(listOf("Crystal.srm", "Crystal.rtc"), target, "mgba", "gbc", "Crystal.gbc", any())
        } returns mapOf("Crystal.srm" to target, "Crystal.rtc" to File(other, "Crystal.rtc").absolutePath)

        val result = handler.extractDownload(prepared.file, saveContext(target))
        assertTrue("extract failed: ${result.error}", result.success)
        assertEquals(target, result.targetPath)
        assertEquals(srm.readBytes().toList(), File(target).readBytes().toList())
        assertEquals(rtc.readBytes().toList(), File(other, "Crystal.rtc").readBytes().toList())
    }

    @Test
    fun `subfolder members bundle by bare name and extract into their folders`() = runTest {
        val root = File(tempDir, "saves").apply { mkdirs() }
        val fs = File(root, "fbneo/mslug.fs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        File(root, "fbneo/mslug.nv").writeBytes(byteArrayOf(9, 9))
        val unit = unitOf(root, listOf("fbneo/mslug.fs" to "mslug.fs", "fbneo/mslug.nv" to "mslug.nv"))
        coEvery { resolver.resolveForMember(fs.absolutePath, "fbneo", "arcade", "mslug.zip", any(), false) } returns unit

        val prepared = handler.prepareForUpload(fs.absolutePath, saveContext(fs.absolutePath, "fbneo", "arcade", "mslug.zip"))
            ?: error("prepareForUpload returned null")
        ZipFile(prepared.file).use { zf ->
            assertEquals(listOf("mslug.fs", "mslug.nv"), zf.entries().toList().map { it.name })
        }

        val other = File(tempDir, "other").apply { mkdirs() }
        val target = File(other, "fbneo/mslug.fs").absolutePath
        coEvery {
            resolver.placeBundle(listOf("mslug.fs", "mslug.nv"), target, "fbneo", "arcade", "mslug.zip", any())
        } returns mapOf("mslug.fs" to target, "mslug.nv" to File(other, "fbneo/mslug.nv").absolutePath)

        val result = handler.extractDownload(prepared.file, saveContext(target, "fbneo", "arcade", "mslug.zip"))
        assertTrue("extract failed: ${result.error}", result.success)
        assertEquals(listOf<Byte>(9, 9), File(other, "fbneo/mslug.nv").readBytes().toList())
    }

    @Test
    fun `built-in bundle resolves against the extracted entry, not the archive`() = runTest {
        val roms = File(tempDir, "roms").apply { mkdirs() }
        val archive = File(roms, "Crystal.zip")
        ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("Crystal (USA).gbc"))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        val root = File(tempDir, "saves").apply { mkdirs() }
        val srm = File(root, "Crystal (USA).srm").apply { writeBytes(ByteArray(64) { it.toByte() }) }
        File(root, "Crystal (USA).rtc").writeBytes(byteArrayOf(4, 2))
        File(root, "Crystal.srm").writeBytes(byteArrayOf(9))
        val unit = unitOf(root, listOf("Crystal (USA).srm" to "Crystal (USA).srm", "Crystal (USA).rtc" to "Crystal (USA).rtc"))
        coEvery {
            resolver.resolveForMember(srm.absolutePath, "mgba", "gbc", "Crystal (USA).gbc", any(), false)
        } returns unit
        val builtin = { path: String -> saveContext(path, rom = "Crystal.zip").copy(romPath = archive.absolutePath, emulatorId = EmulatorRegistry.BUILTIN_ID) }

        val prepared = handler.prepareForUpload(srm.absolutePath, builtin(srm.absolutePath))
            ?: error("prepareForUpload returned null")
        ZipFile(prepared.file).use { zf ->
            assertEquals(listOf("Crystal (USA).srm", "Crystal (USA).rtc"), zf.entries().toList().map { it.name })
        }

        val other = File(tempDir, "other").apply { mkdirs() }
        val target = File(other, "Crystal (USA).srm").absolutePath
        coEvery {
            resolver.placeBundle(listOf("Crystal (USA).srm", "Crystal (USA).rtc"), target, "mgba", "gbc", "Crystal (USA).gbc", any())
        } returns mapOf("Crystal (USA).srm" to target, "Crystal (USA).rtc" to File(other, "Crystal (USA).rtc").absolutePath)

        val result = handler.extractDownload(prepared.file, builtin(target))
        assertTrue("extract failed: ${result.error}", result.success)
        assertEquals(srm.readBytes().toList(), File(target).readBytes().toList())
        assertEquals(listOf<Byte>(4, 2), File(other, "Crystal (USA).rtc").readBytes().toList())
    }

    @Test
    fun `single member defers to the legacy handler`() = runTest {
        val root = File(tempDir, "saves").apply { mkdirs() }
        val srm = File(root, "Tetris.srm").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val unit = unitOf(root, listOf("Tetris.srm" to "Tetris.srm"))
        coEvery { resolver.resolveForMember(any(), any(), any(), any(), any(), any()) } returns unit
        val expected = PreparedSave(srm, isTemporary = false, listOf(srm.absolutePath))
        coEvery { retroArchHandler.prepareForUpload(srm.absolutePath, any()) } returns expected

        val prepared = handler.prepareForUpload(srm.absolutePath, saveContext(srm.absolutePath))
        assertEquals(expected, prepared)
    }

    @Test
    fun `a save that is itself a zip is not a bundle`() = runTest {
        val root = File(tempDir, "saves").apply { mkdirs() }
        val pure = File(root, "Doom.pure.zip")
        archiver.zipFiles(listOf(File(root, "SAVE.DAT").apply { writeBytes(byteArrayOf(7)) }), pure)
        coEvery { resolver.placeBundle(any(), any(), any(), any(), any(), any()) } returns null

        val result = handler.extractBundle(pure, saveContext(pure.absolutePath, "dosbox_pure", "dos", "Doom.zip"))
        assertNull(result)
    }

    private fun unitOf(root: File, members: List<Pair<String, String>>): ResolvedSaveUnit {
        val list = members.map { (path, entry) -> SigilSaveMember(path, entry, 0, true) }
        val shape = if (list.size > 1) SigilSaveUnit.Shape.Multi.code else SigilSaveUnit.Shape.Single.code
        val unit = SigilSaveUnit("key", shape, list, emptyList(), emptyList(), "artifact", "", "")
        return ResolvedSaveUnit(root.absolutePath, "layout", unit)
    }

    private fun saveContext(
        localSavePath: String,
        coreName: String = "mgba",
        platform: String = "gbc",
        rom: String = "Crystal.gbc"
    ) = SaveContext(
        config = SavePathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf("{extStorage}/RetroArch/saves/{core}"),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true,
        ),
        romPath = "/roms/$rom",
        saveId = null,
        emulatorPackage = "com.retroarch",
        gameId = 1L,
        gameTitle = "Test",
        platformSlug = platform,
        emulatorId = "retroarch",
        localSavePath = localSavePath,
        coreName = coreName,
    )
}
