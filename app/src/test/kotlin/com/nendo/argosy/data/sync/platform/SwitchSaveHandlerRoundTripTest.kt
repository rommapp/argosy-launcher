package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SwitchProfileParser
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

class SwitchSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var cacheDir: File
    private lateinit var handler: SwitchSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val profileParser = mockk<SwitchProfileParser>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val titleId = "01007EF00011E000"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("switch_roundtrip").toFile()
        cacheDir = File(tempDir, "cache").apply { mkdirs() }
        every { context.cacheDir } returns cacheDir
        every { context.filesDir } returns tempDir
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        handler = SwitchSaveHandler(context, fal, archiver, profileParser)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `argosy-native folder round-trips with identical file tree`() = runTest {
        val source = SaveFixtures.switchTitleFolder(File(tempDir, "source"), titleId)
        val ctx = saveContext(localSavePath = File(tempDir, "extracted/$titleId").absolutePath)

        val prepared = handler.prepareForUpload(source.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        assertTrue(prepared.file.exists())
        assertTrue(prepared.isTemporary)

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)

        val extracted = File(result.targetPath!!)
        val sourceTree = SaveFixtures.fileTree(source)
        val extractedTree = SaveFixtures.fileTree(extracted)
        assertEquals(sourceTree.map { it.first }, extractedTree.map { it.first })
        sourceTree.zip(extractedTree).forEach { (src, ext) ->
            assertEquals("Bytes differ for ${src.first}", src.second.toList(), ext.second.toList())
        }
    }

    @Test
    fun `JKSV-format folder round-trips with sentinel preserved`() = runTest {
        val source = SaveFixtures.switchJksvFolder(File(tempDir, "source"), titleId)
        val ctx = saveContext(localSavePath = File(tempDir, "extracted/$titleId").absolutePath)

        val prepared = handler.prepareForUpload(source.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)

        val extracted = File(result.targetPath!!)
        val sourceTree = SaveFixtures.fileTree(source).map { it.first }.toSet()
        val extractedTree = SaveFixtures.fileTree(extracted).map { it.first }.toSet()
        assertEquals(sourceTree, extractedTree)
    }

    private fun saveContext(localSavePath: String?) = SaveContext(
        config = SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf("{extStorage}/Android/data/{package}/files/nand/user/save"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true,
        ),
        romPath = null,
        saveId = titleId,
        emulatorPackage = "dev.eden.eden_emulator",
        gameId = 1L,
        gameTitle = "BOTW",
        platformSlug = "switch",
        emulatorId = "eden",
        localSavePath = localSavePath,
        coreName = null,
    )
}
