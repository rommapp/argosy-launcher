package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.fixtures.SaveFixtures
import com.nendo.argosy.data.sync.fixtures.realFsFal
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

class RetroArchSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var handler: RetroArchSaveHandler

    private val context = mockk<Context>(relaxed = true)
    private val resolver = mockk<RetroArchPathResolver>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("retroarch_roundtrip").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        handler = RetroArchSaveHandler(context, realFsFal(), resolver)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `single-file save round-trips with identical bytes`() = runTest {
        val source = SaveFixtures.singleFile(File(tempDir, "source"), bytes = ByteArray(2048) { (it % 251).toByte() })
        val destPath = File(tempDir, "dest/game.srm").absolutePath
        val ctx = saveContext(localSavePath = destPath)

        val prepared = handler.prepareForUpload(source.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        assertTrue(prepared.file.exists())
        assertEquals("Single-file path should not produce a temp", false, prepared.isTemporary)

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)
        assertEquals(destPath, result.targetPath)
        assertEquals(source.readBytes().toList(), File(destPath).readBytes().toList())
    }

    private fun saveContext(localSavePath: String?) = SaveContext(
        config = SavePathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf("{extStorage}/RetroArch/saves/{core}"),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true,
        ),
        romPath = null,
        saveId = null,
        emulatorPackage = "com.retroarch",
        gameId = 1L,
        gameTitle = "Test",
        platformSlug = "snes",
        emulatorId = "retroarch",
        localSavePath = localSavePath,
        coreName = "snes9x",
    )
}
