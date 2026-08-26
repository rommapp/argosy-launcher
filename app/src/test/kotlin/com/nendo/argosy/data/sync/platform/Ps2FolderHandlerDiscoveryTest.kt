package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class Ps2FolderHandlerDiscoveryTest {

    private lateinit var tempDir: File
    private lateinit var handler: PlatformSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("ps2_discovery").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
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
        handler = registry.getFolderHandler("ps2") ?: error("PS2 handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `finds save folder inside a single folder card`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `normalizes serial without BA prefix`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `normalizes serial without dash`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `returns null when same save lives in two cards (ambiguous)`() {
        val cardA = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        File(cardA, "BASLUS-21050").mkdirs()
        val cardB = File(tempDir, "Mcd002.ps2").apply { mkdirs() }
        File(cardB, "BASLUS-21050").mkdirs()

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertNull("Ambiguous match must not pick a card", result)
    }

    @Test
    fun `treats basePath itself as a card when it ends in dot ps2`() {
        val card = File(tempDir, "Single.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(card.absolutePath, "SLUS-21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `finds EU save when saveId already carries the BE prefix`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BESCES-53133").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "BESCES-53133")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `derives BE prefix from a bare EU serial`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BESCES-53133").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SCES-53133")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `derives BI prefix from a bare JP serial`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BISLPS-25088").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLPS-25088")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `constructSavePath preserves the sigil-derived EU folder name`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }

        val result = handler.constructSavePath(tempDir.absolutePath, "BESCES-53133")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `constructSavePath derives territory prefix for a bare EU serial`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }

        val result = handler.constructSavePath(tempDir.absolutePath, "SCES-53133")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `a game's sibling entries are all part of one save`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val data = File(card, "BASLUS-20152AC04").apply { mkdirs() }
        val system = File(card, "BASLUS-20152SYS").apply { mkdirs() }
        File(card, "BASLUS-21050").apply { mkdirs() }

        val entries = handler.findAllSaveFoldersBySaveId(card.absolutePath, "BASLUS-20152")

        assertEquals(
            listOf(data.absolutePath, system.absolutePath).sorted(),
            entries.sorted()
        )
    }

    @Test
    fun `a save id carrying a stale region prefix still finds the entry`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-20152AC04").apply { mkdirs() }

        val entries = handler.findAllSaveFoldersBySaveId(card.absolutePath, "BISLUS-20152A")

        assertEquals(listOf(save.absolutePath), entries)
    }

    @Test
    fun `a card without the dot ps2 suffix is recognised by its superblock`() {
        val card = File(tempDir, "test").apply { mkdirs() }
        File(card, "_pcsx2_superblock").writeText("")
        File(card, "BASLUS-20152AC04").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-20152")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `finds a card nested one level below the base`() {
        val card = File(tempDir, "memcards/MemoryCard").apply { mkdirs() }
        File(card, "_pcsx2_superblock").writeText("")
        File(card, "BASLUS-21050").mkdirs()

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `constructSavePath resolves a card nested one level below the base`() {
        val card = File(tempDir, "memcards/MemoryCard").apply { mkdirs() }
        File(card, "_pcsx2_superblock").writeText("")

        val result = handler.constructSavePath(tempDir.absolutePath, "BASLUS-21050")

        assertEquals(card.absolutePath, result)
    }

    @Test
    fun `a card directly under the base is preferred over a nested one`() {
        val direct = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        File(direct, "BASLUS-21050").mkdirs()
        val nested = File(tempDir, "memcards/MemoryCard").apply { mkdirs() }
        File(nested, "_pcsx2_superblock").writeText("")
        File(nested, "BASLUS-21050").mkdirs()

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertEquals(direct.absolutePath, result)
    }

    @Test
    fun `discovery does not descend more than one level`() {
        val card = File(tempDir, "files/memcards/MemoryCard").apply { mkdirs() }
        File(card, "_pcsx2_superblock").writeText("")
        File(card, "BASLUS-21050").mkdirs()

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertNull("Only one level of descent is allowed", result)
    }

    @Test
    fun `returns null when no card contains the save`() {
        File(tempDir, "Mcd001.ps2").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertNull(result)
    }
}
