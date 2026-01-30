package com.nendo.argosy.data.download

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class ZipExtractorTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = createTempDirectory("zip_extractor_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isNswPlatform returns true for switch`() {
        assertTrue(ZipExtractor.isNswPlatform("switch"))
    }

    @Test
    fun `isNswPlatform returns true for nsw`() {
        assertTrue(ZipExtractor.isNswPlatform("nsw"))
    }

    @Test
    fun `isNswPlatform returns true case insensitive`() {
        assertTrue(ZipExtractor.isNswPlatform("Switch"))
        assertTrue(ZipExtractor.isNswPlatform("NSW"))
        assertTrue(ZipExtractor.isNswPlatform("SWITCH"))
    }

    @Test
    fun `isNswPlatform returns false for other platforms`() {
        assertFalse(ZipExtractor.isNswPlatform("psx"))
        assertFalse(ZipExtractor.isNswPlatform("vita"))
        assertFalse(ZipExtractor.isNswPlatform("wiiu"))
    }

    @Test
    fun `hasUpdateSupport returns true for switch`() {
        assertTrue(ZipExtractor.hasUpdateSupport("switch"))
        assertTrue(ZipExtractor.hasUpdateSupport("nsw"))
    }

    @Test
    fun `hasUpdateSupport returns true for vita`() {
        assertTrue(ZipExtractor.hasUpdateSupport("vita"))
        assertTrue(ZipExtractor.hasUpdateSupport("psvita"))
    }

    @Test
    fun `hasUpdateSupport returns true for wiiu`() {
        assertTrue(ZipExtractor.hasUpdateSupport("wiiu"))
    }

    @Test
    fun `hasUpdateSupport returns true for wii`() {
        assertTrue(ZipExtractor.hasUpdateSupport("wii"))
    }

    @Test
    fun `hasUpdateSupport returns false for disc-only platforms`() {
        assertFalse(ZipExtractor.hasUpdateSupport("psx"))
        assertFalse(ZipExtractor.hasUpdateSupport("saturn"))
        assertFalse(ZipExtractor.hasUpdateSupport("dreamcast"))
    }

    @Test
    fun `getPlatformConfig returns correct config for switch`() {
        val config = ZipExtractor.getPlatformConfig("switch")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("xci"))
        assertTrue(config.gameExtensions.contains("nsp"))
    }

    @Test
    fun `getPlatformConfig returns correct config for vita`() {
        val config = ZipExtractor.getPlatformConfig("vita")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("vpk"))
    }

    @Test
    fun `getPlatformConfig returns correct config for wiiu`() {
        val config = ZipExtractor.getPlatformConfig("wiiu")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("wua"))
        assertTrue(config.gameExtensions.contains("wud"))
    }

    @Test
    fun `getPlatformConfig returns correct config for wii`() {
        val config = ZipExtractor.getPlatformConfig("wii")
        assertNotNull(config)
        assertEquals("update", config!!.updateFolder)
        assertEquals("dlc", config.dlcFolder)
        assertTrue(config.gameExtensions.contains("wbfs"))
        assertTrue(config.gameExtensions.contains("iso"))
    }

    @Test
    fun `getPlatformConfig returns null for unknown platform`() {
        assertNull(ZipExtractor.getPlatformConfig("gba"))
        assertNull(ZipExtractor.getPlatformConfig("nes"))
        assertNull(ZipExtractor.getPlatformConfig("psx"))
    }

    @Test
    fun `getPlatformConfig is case insensitive`() {
        assertNotNull(ZipExtractor.getPlatformConfig("Switch"))
        assertNotNull(ZipExtractor.getPlatformConfig("VITA"))
        assertNotNull(ZipExtractor.getPlatformConfig("WiiU"))
    }

    @Test
    fun `isZipFile returns false for non-existent file`() {
        val fakeFile = File("/non/existent/path/file.zip")
        assertFalse(ZipExtractor.isZipFile(fakeFile))
    }

    @Test
    fun `nsw config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("switch")!!
        val expectedExtensions = setOf("xci", "nsp", "nca", "nro")
        assertEquals(expectedExtensions, config.gameExtensions)
    }

    @Test
    fun `vita config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("vita")!!
        assertTrue(config.gameExtensions.contains("vpk"))
        assertTrue(config.gameExtensions.contains("zip"))
    }

    @Test
    fun `wii config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("wii")!!
        val expectedExtensions = setOf("wbfs", "iso", "ciso", "wia", "rvz")
        assertEquals(expectedExtensions, config.gameExtensions)
    }

    @Test
    fun `wiiu config has all expected game extensions`() {
        val config = ZipExtractor.getPlatformConfig("wiiu")!!
        assertTrue(config.gameExtensions.contains("wua"))
        assertTrue(config.gameExtensions.contains("wud"))
        assertTrue(config.gameExtensions.contains("wux"))
        assertTrue(config.gameExtensions.contains("wup"))
        assertTrue(config.gameExtensions.contains("rpx"))
    }

    @Test
    fun `update extensions match dlc extensions for all platforms`() {
        val platforms = listOf("switch", "vita", "wiiu", "wii")
        for (platform in platforms) {
            val config = ZipExtractor.getPlatformConfig(platform)!!
            assertEquals(
                "Platform $platform should have matching update and dlc extensions",
                config.updateExtensions,
                config.dlcExtensions
            )
        }
    }

    @Test
    fun `organizeNswSingleFile creates game folder and moves file`() {
        val platformDir = File(tempDir, "switch").apply { mkdirs() }
        val romFile = File(platformDir, "game.xci").apply { writeText("test content") }

        val result = ZipExtractor.organizeNswSingleFile(romFile, "Test Game", platformDir)

        assertEquals("Test Game", result.parentFile?.name)
        assertEquals("Test Game.xci", result.name)
        assertTrue(result.exists())
        assertFalse(romFile.exists())
    }

    @Test
    fun `organizeNswSingleFile preserves original filename when it starts with title`() {
        val platformDir = File(tempDir, "switch").apply { mkdirs() }
        val romFile = File(platformDir, "Test Game [v1.0].xci").apply { writeText("test") }

        val result = ZipExtractor.organizeNswSingleFile(romFile, "Test Game", platformDir)

        assertEquals("Test Game [v1.0].xci", result.name)
    }

    @Test
    fun `getUpdatesFolder returns folder when it exists`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val updateFolder = File(gameFolder, "update").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getUpdatesFolder(romFile.absolutePath, "switch")

        assertNotNull(result)
        assertEquals(updateFolder.absolutePath, result!!.absolutePath)
    }

    @Test
    fun `getUpdatesFolder returns null when folder does not exist`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getUpdatesFolder(romFile.absolutePath, "switch")

        assertNull(result)
    }

    @Test
    fun `getDlcFolder returns folder when it exists`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val dlcFolder = File(gameFolder, "dlc").apply { mkdirs() }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.getDlcFolder(romFile.absolutePath, "switch")

        assertNotNull(result)
        assertEquals(dlcFolder.absolutePath, result!!.absolutePath)
    }

    @Test
    fun `getDlcFolder returns null for unsupported platform`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        File(gameFolder, "dlc").apply { mkdirs() }
        val romFile = File(gameFolder, "game.iso").apply { writeText("test") }

        val result = ZipExtractor.getDlcFolder(romFile.absolutePath, "psx")

        assertNull(result)
    }

    @Test
    fun `listUpdateFiles returns nsp files from update folder`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val updateFolder = File(gameFolder, "update").apply { mkdirs() }
        File(updateFolder, "update_v1.nsp").apply { writeText("update1") }
        File(updateFolder, "update_v2.nsp").apply { writeText("update2") }
        File(updateFolder, "readme.txt").apply { writeText("ignored") }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.listUpdateFiles(romFile.absolutePath, "switch")

        assertEquals(2, result.size)
        assertTrue(result.all { it.extension == "nsp" })
    }

    @Test
    fun `listDlcFiles returns dlc files sorted by name`() {
        val gameFolder = File(tempDir, "Test Game").apply { mkdirs() }
        val dlcFolder = File(gameFolder, "dlc").apply { mkdirs() }
        File(dlcFolder, "dlc_b.nsp").apply { writeText("dlc2") }
        File(dlcFolder, "dlc_a.nsp").apply { writeText("dlc1") }
        val romFile = File(gameFolder, "game.xci").apply { writeText("test") }

        val result = ZipExtractor.listDlcFiles(romFile.absolutePath, "switch")

        assertEquals(2, result.size)
        assertEquals("dlc_a.nsp", result[0].name)
        assertEquals("dlc_b.nsp", result[1].name)
    }

    @Test
    fun `isZipFile returns true for valid zip`() {
        val zipFile = File(tempDir, "test.zip")
        createTestZip(zipFile, mapOf("test.txt" to "content"))

        assertTrue(ZipExtractor.isZipFile(zipFile))
    }

    @Test
    fun `isZipFile returns false for non-zip file`() {
        val textFile = File(tempDir, "test.txt").apply { writeText("not a zip") }

        assertFalse(ZipExtractor.isZipFile(textFile))
    }

    @Test
    fun `extractFolderRom extracts files and sets primary file`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("game.xci" to "game content"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Test Game", tempDir)

        assertNotNull(result.primaryFile)
        assertEquals("game.xci", result.primaryFile?.name)
        assertTrue(result.gameFolder.exists())
        assertEquals("Test Game", result.gameFolder.name)
    }

    @Test
    fun `extractFolderRom generates m3u for multiple disc files`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Game (Disc 1).bin" to "disc1",
            "Game (Disc 1).cue" to "cue1",
            "Game (Disc 2).bin" to "disc2",
            "Game (Disc 2).cue" to "cue2"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc Game", tempDir)

        assertNotNull(result.m3uFile)
        assertEquals("Multi Disc Game.m3u", result.m3uFile?.name)
        val m3uContent = result.m3uFile?.readText() ?: ""
        assertTrue(m3uContent.contains("Game (Disc 1)"))
        assertTrue(m3uContent.contains("Game (Disc 2)"))
    }

    @Test
    fun `extractFolderRom uses existing m3u from zip`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Game (Disc 1).chd" to "disc1",
            "Game (Disc 2).chd" to "disc2",
            "Game.m3u" to "Game (Disc 1).chd\nGame (Disc 2).chd"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc Game", tempDir)

        assertNotNull(result.m3uFile)
        assertEquals("Game.m3u", result.m3uFile?.name)
    }

    @Test
    fun `extractFolderRom does not generate m3u for single disc file`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("Game.chd" to "single disc"))

        val result = ZipExtractor.extractFolderRom(zipFile, "Single Disc Game", tempDir)

        assertNull(result.m3uFile)
        assertEquals(1, result.discFiles.size)
    }

    @Test
    fun `extractFolderRom preserves subfolder structure`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "game.xci" to "main game",
            "update/update_v1.nsp" to "update",
            "dlc/dlc_pack.nsp" to "dlc"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "NSW Game", tempDir)

        val updateFolder = File(result.gameFolder, "update")
        val dlcFolder = File(result.gameFolder, "dlc")
        assertTrue(updateFolder.exists())
        assertTrue(dlcFolder.exists())
        assertTrue(File(updateFolder, "update_v1.nsp").exists())
        assertTrue(File(dlcFolder, "dlc_pack.nsp").exists())
    }

    @Test
    fun `extractFolderRom ignores disc files in subfolders for m3u`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "main.chd" to "main game",
            "extras/bonus.chd" to "bonus content"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Game With Extras", tempDir)

        assertNull(result.m3uFile)
        assertEquals(1, result.discFiles.size)
        assertEquals("main.chd", result.discFiles[0].name)
    }

    @Test
    fun `launchPath returns m3u path when present`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf(
            "Disc 1.chd" to "disc1",
            "Disc 2.chd" to "disc2"
        ))

        val result = ZipExtractor.extractFolderRom(zipFile, "Multi Disc", tempDir)

        assertTrue(result.launchPath.endsWith(".m3u"))
    }

    @Test
    fun `launchPath returns primary file when no m3u`() {
        val zipFile = File(tempDir, "game.zip")
        createTestZip(zipFile, mapOf("game.nsp" to "switch game"))

        val result = ZipExtractor.extractFolderRom(zipFile, "NSW Game", tempDir)

        assertTrue(result.launchPath.endsWith(".nsp"))
    }

    @Test
    fun `shouldExtractArchive returns false for single file at root`() {
        val zipFile = File(tempDir, "mame_rom.zip")
        createTestZip(zipFile, mapOf("game.bin" to "arcade rom content"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive returns true for multiple files at root`() {
        val zipFile = File(tempDir, "multi_disc.zip")
        createTestZip(zipFile, mapOf(
            "game.cue" to "cue file",
            "game.bin" to "bin file"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive returns true for file in subfolder`() {
        val zipFile = File(tempDir, "nsw_game.zip")
        createTestZip(zipFile, mapOf("update/patch.nsp" to "update content"))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive returns true for mixed root and subfolder files`() {
        val zipFile = File(tempDir, "nsw_complete.zip")
        createTestZip(zipFile, mapOf(
            "game.xci" to "main game",
            "update/update_v1.nsp" to "update",
            "dlc/bonus.nsp" to "dlc"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive returns false for non-zip file`() {
        val textFile = File(tempDir, "not_a_zip.txt").apply { writeText("plain text") }

        assertFalse(ZipExtractor.shouldExtractArchive(textFile))
    }

    @Test
    fun `shouldExtractArchive returns false for non-existent file`() {
        val fakeFile = File(tempDir, "does_not_exist.zip")

        assertFalse(ZipExtractor.shouldExtractArchive(fakeFile))
    }

    @Test
    fun `shouldExtractArchive returns false for empty zip`() {
        val zipFile = File(tempDir, "empty.zip")
        createTestZip(zipFile, emptyMap())

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive handles MAME style single rom correctly`() {
        val zipFile = File(tempDir, "pacman.zip")
        createTestZip(zipFile, mapOf("pacman.rom" to "arcade rom data"))

        assertFalse(ZipExtractor.shouldExtractArchive(zipFile))
    }

    @Test
    fun `shouldExtractArchive handles multi-disc PSX game correctly`() {
        val zipFile = File(tempDir, "ff7.zip")
        createTestZip(zipFile, mapOf(
            "Final Fantasy VII (Disc 1).chd" to "disc1",
            "Final Fantasy VII (Disc 2).chd" to "disc2",
            "Final Fantasy VII (Disc 3).chd" to "disc3"
        ))

        assertTrue(ZipExtractor.shouldExtractArchive(zipFile))
    }

    private fun createTestZip(zipFile: File, entries: Map<String, String>) {
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray())
                zos.closeEntry()
            }
        }
    }
}
