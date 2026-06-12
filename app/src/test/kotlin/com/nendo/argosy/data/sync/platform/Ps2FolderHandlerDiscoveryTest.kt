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

        assertEquals(save.absolutePath, result)
    }

    @Test
    fun `normalizes serial without BA prefix`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertEquals(save.absolutePath, result)
    }

    @Test
    fun `normalizes serial without dash`() {
        val card = File(tempDir, "Mcd001.ps2").apply { mkdirs() }
        val save = File(card, "BASLUS-21050").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS21050")

        assertEquals(save.absolutePath, result)
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

        assertEquals(save.absolutePath, result)
    }

    @Test
    fun `returns null when no card contains the save`() {
        File(tempDir, "Mcd001.ps2").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(tempDir.absolutePath, "SLUS-21050")

        assertNull(result)
    }
}
