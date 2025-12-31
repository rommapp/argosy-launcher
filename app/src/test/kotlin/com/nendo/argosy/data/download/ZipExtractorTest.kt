package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ZipExtractorTest {

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
}
