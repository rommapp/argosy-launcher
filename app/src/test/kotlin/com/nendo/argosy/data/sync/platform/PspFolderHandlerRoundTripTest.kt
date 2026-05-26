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

class PspFolderHandlerRoundTripTest {

    private lateinit var tempDir: File
    private lateinit var handler: PlatformSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val discId = "ULUS10064"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("psp_roundtrip").toFile()
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
        handler = registry.getFolderHandler("psp")
            ?: error("PSP folder handler not registered")
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `prefix-grouped siblings round-trip and preserve every sibling`() = runTest {
        val parent = SaveFixtures.pspPrefixedSiblings(
            File(tempDir, "source/SAVEDATA"),
            discId = discId,
            suffixes = listOf("DATA00", "SETTINGS", "SYSTEM"),
        )
        val destParent = File(tempDir, "dest/SAVEDATA")
        val ctx = saveContext(localSavePath = destParent.absolutePath)

        val prepared = handler.prepareForUpload(parent.absolutePath, ctx)
            ?: error("prepareForUpload returned null")
        assertTrue(prepared.file.exists())
        assertTrue(prepared.isTemporary)

        val result = handler.extractDownload(prepared.file, ctx)
        assertTrue("Extract reported failure: ${result.error}", result.success)

        val restoredSiblings = destParent.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(discId) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        assertEquals(
            listOf("${discId}DATA00", "${discId}SETTINGS", "${discId}SYSTEM"),
            restoredSiblings,
        )

        listOf("DATA00", "SETTINGS", "SYSTEM").forEach { suffix ->
            val src = File(parent, "$discId$suffix/save.bin")
            val dst = File(destParent, "$discId$suffix/save.bin")
            assertEquals("Bytes differ for $suffix", src.readBytes().toList(), dst.readBytes().toList())
        }
    }

    private fun saveContext(localSavePath: String) = SaveContext(
        config = SavePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf("{anyStorage}/PSP/SAVEDATA"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
        ),
        romPath = null,
        saveId = discId,
        emulatorPackage = "org.ppsspp.ppsspp",
        gameId = 1L,
        gameTitle = "Test",
        platformSlug = "psp",
        emulatorId = "ppsspp",
        localSavePath = localSavePath,
    )
}
