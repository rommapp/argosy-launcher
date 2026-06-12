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

class FolderSaveHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var handler: FolderSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("folder_roundtrip").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val archiver = SaveArchiver(androidDataAccessor, fal)
        handler = FolderSaveHandler(context, fal, archiver, platformSlug = "3ds")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `flat folder round-trips with identical file tree`() = runTest {
        val source = SaveFixtures.flatFolder(File(tempDir, "source/savefolder"), fileCount = 5)
        val ctx = saveContext(localSavePath = File(tempDir, "dest/savefolder").absolutePath)

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

    private fun saveContext(localSavePath: String?) = SaveContext(
        config = SavePathConfig(
            emulatorId = "azahar",
            defaultPaths = listOf("{extStorage}/Android/data/io.github.lime3ds.android/files/sdmc/Nintendo 3DS"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
        ),
        romPath = null,
        saveId = "savefolder",
        emulatorPackage = "io.github.lime3ds.android",
        gameId = 1L,
        gameTitle = "Test",
        platformSlug = "3ds",
        emulatorId = "azahar",
        localSavePath = localSavePath,
    )
}
