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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SwitchSaveHandlerResolveTargetTest {

    private lateinit var tempDir: File
    private lateinit var handler: SwitchSaveHandler
    private lateinit var archiver: SaveArchiver

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val profileParser = mockk<SwitchProfileParser>(relaxed = true)

    private val titleId = "01007EF00011E000"
    private val deviceTitleId = "01006F8002326000"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("switch_resolve_target").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        val fal = realFsFal()
        archiver = SaveArchiver(androidDataAccessor, fal)
        handler = SwitchSaveHandler(context, fal, archiver, profileParser)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `argosy-native zip uses zip root titleId and active profile`() {
        val source = SaveFixtures.switchTitleFolder(File(tempDir, "source"), titleId)
        val zip = File(tempDir, "save.zip")
        assertTrue(archiver.zipFolder(source, zip))

        val basePath = File(tempDir, "base").apply { mkdirs() }
        val profileDir = File(basePath, "0000000000000000/FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(profileDir, "marker.bin").writeBytes(byteArrayOf(1))

        val target = handler.resolveSaveTargetPath(zip, config(), emulatorPackage = null, basePathOverride = basePath.absolutePath)

        assertEquals("${profileDir.absolutePath}/$titleId", target)
    }

    @Test
    fun `device-save titleId routes to zero profile under basePath`() {
        val source = SaveFixtures.switchTitleFolder(File(tempDir, "source"), deviceTitleId)
        val zip = File(tempDir, "device.zip")
        assertTrue(archiver.zipFolder(source, zip))

        val basePath = File(tempDir, "base").apply { mkdirs() }

        val target = handler.resolveSaveTargetPath(zip, config(), emulatorPackage = null, basePathOverride = basePath.absolutePath)

        val expected = "${basePath.absolutePath}/0000000000000000/00000000000000000000000000000000/$deviceTitleId"
        assertEquals(expected, target)
        assertTrue("Zero-profile folder must be created on disk", File(expected).parentFile.isDirectory)
    }

    @Test
    fun `JKSV-format zip parses titleId from nx_save_meta when zip root is not hex`() {
        val stage = File(tempDir, "stage").apply { mkdirs() }
        val meta = File(stage, ".nx_save_meta.bin").apply { writeBytes(SaveFixtures.jksvMetaBytes(titleId)) }
        val data = File(stage, "data.sav").apply { writeBytes(ByteArray(256)) }
        val zip = File(tempDir, "jksv.zip")
        assertTrue(archiver.zipFiles(listOf(meta, data), zip))

        val basePath = File(tempDir, "base").apply { mkdirs() }
        val profileDir = File(basePath, "0000000000000000/FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(profileDir, "marker.bin").writeBytes(byteArrayOf(1))

        val target = handler.resolveSaveTargetPath(zip, config(), emulatorPackage = null, basePathOverride = basePath.absolutePath)

        assertNotNull("JKSV fallback should succeed", target)
        assertTrue("Resolved path should end with the parsed titleId", target!!.endsWith("/$titleId"))
    }

    @Test
    fun `returns null when zip has neither hex root nor JKSV meta`() {
        val source = File(tempDir, "source/garbage").apply { mkdirs() }
        File(source, "data.sav").writeBytes(ByteArray(64))
        val zip = File(tempDir, "garbage.zip")
        assertTrue(archiver.zipFolder(source, zip))

        val basePath = File(tempDir, "base").apply { mkdirs() }

        val target = handler.resolveSaveTargetPath(zip, config(), emulatorPackage = null, basePathOverride = basePath.absolutePath)

        assertNull(target)
    }

    @Test
    fun `titleId from zip is uppercased even when zip root folder is lowercase`() {
        val lower = titleId.lowercase()
        val source = SaveFixtures.switchTitleFolder(File(tempDir, "source"), lower)
        val zip = File(tempDir, "save.zip")
        assertTrue(archiver.zipFolder(source, zip))

        val basePath = File(tempDir, "base").apply { mkdirs() }
        val profileDir = File(basePath, "0000000000000000/FEDCBA9876543210FEDCBA9876543210").apply { mkdirs() }
        File(profileDir, "marker.bin").writeBytes(byteArrayOf(1))

        val target = handler.resolveSaveTargetPath(zip, config(), emulatorPackage = null, basePathOverride = basePath.absolutePath)

        assertNotNull(target)
        assertTrue(
            "Output titleId must be uppercase regardless of input case",
            target!!.endsWith("/$titleId"),
        )
    }

    private fun config() = SavePathConfig(
        emulatorId = "eden",
        defaultPaths = listOf("/storage/Android/data/{package}/files/nand/user/save"),
        saveExtensions = listOf("*"),
        usesFolderBasedSaves = true,
        usesPackageTemplate = true,
    )
}
