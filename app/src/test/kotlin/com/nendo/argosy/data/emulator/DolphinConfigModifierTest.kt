package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DolphinConfigModifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `injectHiresTexturesSetting handles null or blank input`() {
        val result = DolphinConfigModifier.injectHiresTexturesSetting(null)
        assertTrue(result.contains("[Settings]"))
        assertTrue(result.contains("HiresTextures = True"))
        assertFalse(result.contains("CacheHiresTextures = True"))
    }

    @Test
    fun `injectHiresTexturesSetting updates existing False value in Settings section`() {
        val input = """
            [Core]
            CPUThread = True

            [Settings]
            AspectRatio = 0
            HiresTextures = False
            CacheHiresTextures = False

            [Enhancements]
            MaxAnisotropy = 2
        """.trimIndent()

        val result = DolphinConfigModifier.injectHiresTexturesSetting(input)
        assertTrue(result.contains("HiresTextures = True"))
        assertFalse(result.lines().any { it.trim() == "HiresTextures = False" })
        assertFalse(result.contains("CacheHiresTextures = True"))
        assertTrue(result.lines().any { it.trim() == "CacheHiresTextures = False" })
        assertTrue(result.contains("[Enhancements]"))
        assertTrue(result.contains("MaxAnisotropy = 2"))
    }

    @Test
    fun `injectHiresTexturesSetting inserts into existing Settings section when missing`() {
        val input = """
            [Settings]
            AspectRatio = 0

            [Enhancements]
            MaxAnisotropy = 2
        """.trimIndent()

        val result = DolphinConfigModifier.injectHiresTexturesSetting(input)
        val lines = result.lines()
        val settingsIdx = lines.indexOfFirst { it.trim() == "[Settings]" }
        val hiresIdx = lines.indexOfFirst { it.trim() == "HiresTextures = True" }
        val enhancementsIdx = lines.indexOfFirst { it.trim() == "[Enhancements]" }

        assertTrue(settingsIdx != -1)
        assertTrue(hiresIdx > settingsIdx)
        assertTrue(hiresIdx < enhancementsIdx)
    }

    @Test
    fun `findConfigDir correctly resolves Config directory from Load Textures path`() {
        val baseDir = tempFolder.newFolder("dolphin-emu")
        val texturesDir = File(baseDir, "Load/Textures/GZLE01")
        texturesDir.mkdirs()

        val configDir = DolphinConfigModifier.findConfigDir(texturesDir)
        assertNotNull(configDir)
        assertEquals(File(baseDir, "Config"), configDir)
    }

    @Test
    fun `enableCustomTextures creates GFX ini file with HiresTextures`() {
        val baseDir = tempFolder.newFolder("dolphin_root")
        val texturesDir = File(baseDir, "Load/Textures/GZLE01")
        texturesDir.mkdirs()

        val success = DolphinConfigModifier.enableCustomTextures(texturesDir)
        assertTrue(success)

        val gfxFile = File(baseDir, "Config/GFX.ini")
        assertTrue(gfxFile.isFile)
        val text = gfxFile.readText()
        assertTrue(text.contains("[Settings]"))
        assertTrue(text.contains("HiresTextures = True"))
        assertFalse(text.contains("CacheHiresTextures = True"))
    }

    @Test
    fun `enableCustomTextures creates backup file and preserves non-texture settings`() {
        val baseDir = tempFolder.newFolder("dolphin_root_bak")
        val texturesDir = File(baseDir, "Load/Textures/GZLE01").apply { mkdirs() }
        val configDir = File(baseDir, "Config").apply { mkdirs() }
        val gfxFile = File(configDir, "GFX.ini")
        val originalContent = """
            [Hardware]
            VSync = True

            [Settings]
            AspectRatio = 1
            HiresTextures = False
            CacheHiresTextures = False

            [Enhancements]
            PostProcessingShader = crt-lottes
        """.trimIndent()
        gfxFile.writeText(originalContent)

        val success = DolphinConfigModifier.enableCustomTextures(texturesDir)
        assertTrue(success)

        val bakFile = File(configDir, "GFX.ini.argosy_bak")
        assertTrue("Backup file must be created", bakFile.isFile)
        assertEquals(originalContent, bakFile.readText())

        val updatedContent = gfxFile.readText()
        assertTrue(updatedContent.contains("HiresTextures = True"))
        assertFalse(updatedContent.contains("CacheHiresTextures = True"))
        assertFalse(updatedContent.lines().any { it.trim() == "HiresTextures = False" })
        assertTrue(updatedContent.lines().any { it.trim() == "CacheHiresTextures = False" })
        assertTrue(updatedContent.contains("VSync = True"))
        assertTrue(updatedContent.contains("AspectRatio = 1"))
        assertTrue(updatedContent.contains("PostProcessingShader = crt-lottes"))
    }

    @Test
    fun `enableCustomTextures aborts without overwriting when reading existing file fails`() {
        val baseDir = tempFolder.newFolder("dolphin_root_err")
        val texturesDir = File(baseDir, "Load/Textures/GZLE01").apply { mkdirs() }
        val configDir = File(baseDir, "Config").apply { mkdirs() }
        val gfxFile = File(configDir, "GFX.ini")
        val preciousConfig = "[Crucial]\nDoNotWipe = True\n"
        gfxFile.writeText(preciousConfig)

        val fal = mockk<FileAccessLayer> {
            every { isRestrictedPath(any()) } returns true
            every { exists(any()) } returns true
            every { readBytes(any()) } throws RuntimeException("I/O failure simulation")
        }

        val success = DolphinConfigModifier.enableCustomTextures(texturesDir, fal)
        assertFalse("Must abort when read fails", success)
        assertEquals("Original configuration must remain untouched", preciousConfig, gfxFile.readText())
    }
}
