package com.nendo.argosy.data.sync

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.savepath.Pcsx2CardBase
import com.nendo.argosy.data.emulator.savepath.Pcsx2DataFolder
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.EmulatorSaveRoots
import com.nendo.argosy.data.sync.platform.FolderSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.Ps2FolderCardSuperblock
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
 * Fixtures mirror an AYN Thor running ARMSX2 from the SD card: the data folder
 * `/storage/546B-6466/Android/data/com.armsx2/files` holds `PCSX2-Android.ini` naming the folder
 * card `maincard`, beside the 8 MB file cards `mcd001.ps2` and `mcd002.ps2`, while internal storage
 * holds only a stray `memcards/Shared.ps2`.
 */
class Pcsx2LegacyCardsTest {

    private lateinit var tempDir: File
    private lateinit var internal: File
    private lateinit var sd: File
    private lateinit var fal: FileAccessLayer
    private lateinit var cards: Pcsx2LegacyCards
    private lateinit var saveRoots: EmulatorSaveRoots
    private lateinit var handler: FolderSaveHandler
    private lateinit var registry: PlatformSaveHandlerRegistry

    private val context = mockk<Context>(relaxed = true)
    private val emulatorSaveConfigDao = mockk<EmulatorSaveConfigDao>(relaxed = true)
    private val config = SavePathRegistry.getConfig("armsx2_refresh") ?: error("armsx2_refresh not registered")
    private val dataPath = "Android/data/com.armsx2/files"
    private val saveId = "BASLUS-20675"

    private val ini = """
        [Folders]
        MemoryCards = memcards

        [MemoryCards]
        Slot1_Enable = true
        Slot1_Filename = maincard
        Slot2_Enable = true
        Slot2_Filename = mcd002.ps2
    """.trimIndent()

    @Before
    fun setUp() {
        tempDir = createTempDirectory("pcsx2_legacy").toFile()
        internal = File(tempDir, "emulated/0").apply { mkdirs() }
        sd = File(tempDir, "546B-6466").apply { mkdirs() }
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns internal
        every { context.filesDir } returns File(tempDir, "argosy").apply { mkdirs() }
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        coEvery { emulatorSaveConfigDao.getByEmulator(any()) } returns null
        coEvery { emulatorSaveConfigDao.getAll() } returns emptyList()

        fal = realFsFal()
        every { fal.externalStorageRoots() } returns listOf(internal.absolutePath, sd.absolutePath)
        val repository = EmulatorSaveConfigRepository(emulatorSaveConfigDao)
        saveRoots = EmulatorSaveRoots(context, fal, repository)
        cards = Pcsx2LegacyCards(fal, repository, saveRoots)
        registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            unitSaveHandler = mockk(relaxed = true),
            sigilSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
            emulatorSaveConfigRepository = repository,
        )
        handler = registry.getFolderHandler("ps2") ?: error("PS2 handler not registered")
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    private fun internalData() = File(internal, dataPath)
    private fun sdData() = File(sd, dataPath)

    private fun folderCard(dir: File): File = dir.apply {
        mkdirs()
        File(this, Ps2FolderCardSuperblock.FILE_NAME).writeBytes(Ps2FolderCardSuperblock.formatted8Mb())
    }

    private fun thorLayout() {
        val stray = folderCard(File(internalData(), "memcards/Shared.ps2"))
        File(stray, saveId).mkdirs()
        val sdData = sdData().apply { mkdirs() }
        File(sdData, Pcsx2DataFolder.INI_NAME).writeText(ini)
        val main = folderCard(File(sdData, "memcards/maincard"))
        File(main, saveId).mkdirs()
        File(sdData, "memcards/mcd001.ps2").writeBytes(ByteArray(64))
        File(sdData, "memcards/mcd002.ps2").writeBytes(ByteArray(64))
    }

    @Test
    fun `the Sigil root is the data folder holding the ini on the SD card`() = runTest {
        thorLayout()

        val root = saveRoots.rootFor(config, "ps2", SigilSaveHandler.PCSX2_STANDALONE_LAYOUT, null)

        assertEquals(sdData().absolutePath, root)
    }

    @Test
    fun `a path the user set for the emulator stays the Sigil root`() = runTest {
        thorLayout()
        val chosen = File(tempDir, "chosen/memcards").apply { mkdirs() }
        coEvery { emulatorSaveConfigDao.getByEmulator("armsx2_refresh") } returns EmulatorSaveConfigEntity(
            emulatorId = "armsx2_refresh",
            savePathPattern = chosen.absolutePath,
            isAutoDetected = false,
            isUserOverride = true
        )

        val root = saveRoots.rootFor(config, "ps2", SigilSaveHandler.PCSX2_STANDALONE_LAYOUT, null)

        assertEquals(chosen.parentFile.absolutePath, root)
    }

    @Test
    fun `an emulator outside the PCSX2 family keeps its internal root beside an SD copy`() = runTest {
        val duckstation = com.nendo.argosy.data.emulator.SavePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf("{extStorage}/Android/data/com.github.stenzek.duckstation/files/memcards"),
            saveExtensions = listOf("mcd")
        )
        val duckPath = "Android/data/com.github.stenzek.duckstation/files"
        File(internal, "$duckPath/memcards").mkdirs()
        File(sd, "$duckPath/memcards").mkdirs()
        File(sd, "$duckPath/${Pcsx2DataFolder.INI_NAME}").writeText(ini)

        val root = saveRoots.rootFor(duckstation, "psx", "duckstation", null)

        assertEquals(File(internal, duckPath).absolutePath, root)
    }

    @Test
    fun `the memory card picker lists the cards sync uses`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!

        val listed = registry.listPs2FolderMemcardsForEmulator("armsx2_refresh", null)

        assertEquals(base, registry.ps2CardBaseForEmulator("armsx2_refresh", null))
        assertEquals(listOf("maincard"), listed.map { it.name })
        assertTrue(listed.all { it.path.startsWith("${base.cardsDir}/") })
    }

    @Test
    fun `the memory card picker lists the cards in a path the user set`() = runTest {
        thorLayout()
        val chosen = File(tempDir, "chosen/memcards").apply { mkdirs() }
        val picked = folderCard(File(chosen, "picked"))
        coEvery { emulatorSaveConfigDao.getByEmulator("armsx2_refresh") } returns EmulatorSaveConfigEntity(
            emulatorId = "armsx2_refresh",
            savePathPattern = chosen.absolutePath,
            isAutoDetected = false,
            isUserOverride = true
        )

        val listed = registry.listPs2FolderMemcardsForEmulator("armsx2_refresh", null)

        assertEquals(listOf(picked.absolutePath), listed.map { it.path })
    }

    @Test
    fun `the cards folder and slot 1 card come from the SD ini`() = runTest {
        thorLayout()

        val base = cards.baseFor(config, "ps2", null)

        assertEquals("${sdData().absolutePath}/memcards", base?.cardsDir)
        assertEquals("${sdData().absolutePath}/memcards/maincard", base?.activeCard)
    }

    @Test
    fun `a path the user set for the emulator stays the cards folder`() = runTest {
        thorLayout()
        val chosen = File(tempDir, "chosen/memcards").apply { mkdirs() }
        coEvery { emulatorSaveConfigDao.getByEmulator("armsx2_refresh") } returns EmulatorSaveConfigEntity(
            emulatorId = "armsx2_refresh",
            savePathPattern = chosen.absolutePath,
            isAutoDetected = false,
            isUserOverride = true
        )

        val base = cards.baseFor(config, "ps2", null)

        assertEquals(chosen.absolutePath, base?.cardsDir)
        assertNull(base?.activeCard)
    }

    @Test
    fun `a restore lands in the slot 1 folder card, not the stray internal card`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!

        val target = cards.targetCard(base, null, saveId, handler)

        assertEquals("${sdData().absolutePath}/memcards/maincard", target)
    }

    @Test
    fun `a selected card on disk comes before slot 1`() = runTest {
        thorLayout()
        val picked = folderCard(File(sdData(), "memcards/picked"))
        val base = cards.baseFor(config, "ps2", null)!!

        val target = cards.targetCard(base, picked.absolutePath, saveId, handler)

        assertEquals(picked.absolutePath, target)
    }

    @Test
    fun `a selected card that is gone falls through to slot 1`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!

        val target = cards.targetCard(base, "${sdData().absolutePath}/memcards/gone", saveId, handler)

        assertEquals("${sdData().absolutePath}/memcards/maincard", target)
    }

    @Test
    fun `an absent slot 1 folder card is created formatted under its own name`() {
        val cardsDir = File(tempDir, "data/memcards").apply { mkdirs() }
        val base = Pcsx2CardBase(cardsDir.absolutePath, "${cardsDir.absolutePath}/maincard")

        val target = cards.targetCard(base, null, saveId, handler)

        assertEquals("${cardsDir.absolutePath}/maincard", target)
        assertTrue(Ps2FolderCardSuperblock.isFormatted(File(cardsDir, "maincard/${Ps2FolderCardSuperblock.FILE_NAME}").readBytes()))
    }

    @Test
    fun `a file card in slot 1 gets no target and the disk is left alone`() {
        val cardsDir = File(tempDir, "data/memcards").apply { mkdirs() }
        val fileCard = File(cardsDir, "mcd001.ps2").apply { writeBytes(ByteArray(64) { 7 }) }
        val base = Pcsx2CardBase(cardsDir.absolutePath, fileCard.absolutePath)

        val target = cards.targetCard(base, null, saveId, handler)

        assertNull(target)
        assertTrue(fileCard.isFile)
        assertTrue(ByteArray(64) { 7 }.contentEquals(fileCard.readBytes()))
        assertEquals(listOf("mcd001.ps2"), cardsDir.list()?.toList())
    }

    @Test
    fun `a missing file card in slot 1 is not created`() {
        val cardsDir = File(tempDir, "data/memcards").apply { mkdirs() }
        val base = Pcsx2CardBase(cardsDir.absolutePath, "${cardsDir.absolutePath}/Mcd001.ps2")

        val target = cards.targetCard(base, null, saveId, handler)

        assertNull(target)
        assertFalse(File(cardsDir, "Mcd001.ps2").exists())
    }

    @Test
    fun `with no slot 1 the one folder card is used`() {
        val cardsDir = File(tempDir, "data/memcards").apply { mkdirs() }
        val only = folderCard(File(cardsDir, "maincard"))
        val base = Pcsx2CardBase(cardsDir.absolutePath, null)

        val target = cards.targetCard(base, null, saveId, handler)

        assertEquals(only.absolutePath, target)
    }

    @Test
    fun `with no slot 1 and no folder card no Shared ps2 is invented`() {
        val cardsDir = File(tempDir, "data/memcards").apply { mkdirs() }
        File(cardsDir, "mcd001.ps2").writeBytes(ByteArray(64))
        val base = Pcsx2CardBase(cardsDir.absolutePath, null)

        val target = cards.targetCard(base, null, saveId, handler)

        assertNull(target)
        assertFalse(File(cardsDir, "Shared.ps2").exists())
        assertNull(handler.constructSavePath(cardsDir.absolutePath, saveId))
    }

    @Test
    fun `discovery and restore read the same card`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!
        val target = cards.targetCard(base, null, saveId, handler)

        val discovered = handler.findSaveFolderBySaveId(base.activeCard!!, saveId)

        assertEquals(target, discovered)
    }

    @Test
    fun `a stored path on the stray internal card is off the emulator's cards`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!

        assertFalse(cards.holds(base, null, "${internalData().absolutePath}/memcards/Shared.ps2"))
        assertTrue(cards.holds(base, null, "${sdData().absolutePath}/memcards/maincard"))
        assertTrue(cards.holds(base, "/picked/card", "/picked/card/"))
    }

    @Test
    fun `the stray internal card is left on disk`() = runTest {
        thorLayout()
        val base = cards.baseFor(config, "ps2", null)!!

        cards.targetCard(base, null, saveId, handler)

        assertTrue(File(internalData(), "memcards/Shared.ps2/$saveId").isDirectory)
    }
}
