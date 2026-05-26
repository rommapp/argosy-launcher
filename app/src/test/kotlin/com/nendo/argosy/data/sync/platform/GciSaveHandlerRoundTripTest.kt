package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.SaveFixtures
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class GciSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var baseDir: File
    private lateinit var romFile: File
    private lateinit var handler: GciSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val gameId = "GZLE"
    private val makerCode = "01"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("gci_roundtrip").toFile()
        baseDir = File(tempDir, "memcards").apply { mkdirs() }
        romFile = SaveFixtures.gameCubeRom(File(tempDir, "rom.iso"), gameId = gameId, makerCode = makerCode)
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        handler = GciSaveHandler(context, fal, archiver)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `multi-gci bundle round-trips into Region Card layout`() = runTest {
        val cardDir = File(baseDir, "USA/Card A").apply { mkdirs() }
        val saveA = SaveFixtures.gciSave(
            File(cardDir, "$makerCode-$gameId-ZELDA_DATA.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_DATA",
            payload = ByteArray(1024) { it.toByte() },
        )
        val saveB = SaveFixtures.gciSave(
            File(cardDir, "$makerCode-$gameId-ZELDA_BACK.gci"),
            gameId = gameId,
            makerCode = makerCode,
            internalFilename = "ZELDA_BACK",
            payload = ByteArray(512) { (it * 3).toByte() },
        )
        val originalA = saveA.readBytes()
        val originalB = saveB.readBytes()

        val ctx = saveContext()
        val prepared = handler.prepareForUpload(saveA.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        assertTrue("Bundle file should exist", prepared.file.exists())
        assertTrue(prepared.isTemporary)

        saveA.delete()
        saveB.delete()
        assertTrue("Cleared source GCIs before extract", baseDir.walkTopDown().none { it.isFile && it.extension.equals("gci", ignoreCase = true) })

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)

        val restoredA = File(cardDir, "$makerCode-$gameId-ZELDA_DATA.gci")
        val restoredB = File(cardDir, "$makerCode-$gameId-ZELDA_BACK.gci")
        assertTrue("Save A missing after extract", restoredA.exists())
        assertTrue("Save B missing after extract", restoredB.exists())
        assertEquals(originalA.toList(), restoredA.readBytes().toList())
        assertEquals(originalB.toList(), restoredB.readBytes().toList())
    }

    private fun saveContext() = SaveContext(
        config = SavePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(baseDir.absolutePath),
            saveExtensions = listOf("gci"),
            usesGciFormat = true,
        ),
        romPath = romFile.absolutePath,
        saveId = gameId,
        emulatorPackage = "org.dolphinemu.dolphinemu",
        gameId = 1L,
        gameTitle = "Zelda",
        platformSlug = "ngc",
        emulatorId = "dolphin",
        localSavePath = null,
        coreName = null,
    )
}
