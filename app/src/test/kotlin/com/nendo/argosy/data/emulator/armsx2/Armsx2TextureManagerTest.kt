package com.nendo.argosy.data.emulator.armsx2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Armsx2TextureManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var systemDir: File
    private lateinit var manager: Armsx2TextureManager

    @Before
    fun setup() {
        systemDir = tempFolder.newFolder("system")
        manager = Armsx2TextureManager()
    }

    @Test
    fun `normalizeSerial handles all PS2 serial variants into canonical PCSX2 format`() {
        assertEquals("SLUS-20002", manager.normalizeSerial("SLUS-20002"))
        assertEquals("SLUS-20002", manager.normalizeSerial("slus-20002"))
        assertEquals("SLUS-20002", manager.normalizeSerial("SLUS20002"))
        assertEquals("SLUS-20002", manager.normalizeSerial("SLUS_200.02"))
        assertEquals("SLUS-20002", manager.normalizeSerial("SLUS_200.02;1"))
        assertEquals("SLUS-20002", manager.normalizeSerial("BASLUS-20002"))
        assertEquals("SLUS-20152", manager.normalizeSerial("BASLUS-20152AC04"))
        assertEquals("SCES-51234", manager.normalizeSerial("SCES-51234"))
        assertEquals("SLPM-65001", manager.normalizeSerial("slpm65001"))
        assertEquals("", manager.normalizeSerial(""))
    }

    @Test
    fun `resolveSerialFromFileName extracts serial correctly`() {
        assertEquals("SLUS-20312", manager.resolveSerialFromFileName("Final Fantasy X [SLUS-20312].iso"))
        assertEquals("SCES-51719", manager.resolveSerialFromFileName("Gran Turismo 4 (SCES-51719).bin"))
        assertEquals("SLUS-20002", manager.resolveSerialFromFileName("SLUS_200.02.iso"))
        assertNull(manager.resolveSerialFromFileName("GenericGameWithoutSerial.iso"))
    }

    @Test
    fun `directory structuring strictly follows PCSX2 upstream hierarchy`() {
        val serial = "SLUS-20002"

        val baseDir = manager.getTexturesBaseDir(systemDir)
        assertEquals(File(systemDir, "pcsx2/textures"), baseDir)

        val gameDir = manager.getGameTexturesDir(systemDir, serial)
        assertEquals(File(systemDir, "pcsx2/textures/SLUS-20002"), gameDir)

        val replacementsDir = manager.getReplacementsDir(systemDir, serial)
        assertEquals(File(systemDir, "pcsx2/textures/SLUS-20002/replacements"), replacementsDir)

        assertFalse(replacementsDir.exists())
        val created = manager.ensureReplacementsDir(systemDir, serial)
        assertTrue(created.exists())
        assertTrue(created.isDirectory)
    }

    @Test
    fun `hasReplacements returns true only when texture files are present`() {
        val serial = "SLUS-20002"
        assertFalse(manager.hasReplacements(systemDir, serial))

        val replacementsDir = manager.ensureReplacementsDir(systemDir, serial)
        assertFalse("Empty directory has no replacements", manager.hasReplacements(systemDir, serial))

        val dummyTexture = File(replacementsDir, "0123456789abcdef-00000000.png")
        dummyTexture.writeBytes(byteArrayOf(1, 2, 3))

        assertTrue("Replacements found after adding texture file", manager.hasReplacements(systemDir, serial))
    }

    @Test
    fun `isTextureReplacementEnabled and setTextureReplacementEnabled manage INI safely`() {
        assertNull(manager.isTextureReplacementEnabled(systemDir))

        manager.setTextureReplacementEnabled(systemDir, true)
        assertEquals(true, manager.isTextureReplacementEnabled(systemDir))

        manager.setTextureReplacementEnabled(systemDir, false)
        assertEquals(false, manager.isTextureReplacementEnabled(systemDir))

        val iniFile = manager.getIniFile(systemDir)
        assertTrue(iniFile.exists())
        val text = iniFile.readText()
        assertTrue(text.contains("[EmuCore/GS]"))
        assertTrue(text.contains("LoadTextureReplacements = false"))
    }

    @Test
    fun `configureTexturesForGame respects explicit user preference`() {
        val serial = "SLUS-20002"

        // Explicit user preference: true
        manager.configureTexturesForGame(systemDir, serial, userPreference = true)
        assertEquals(true, manager.isTextureReplacementEnabled(systemDir))

        // Explicit user preference: false
        manager.configureTexturesForGame(systemDir, serial, userPreference = false)
        assertEquals(false, manager.isTextureReplacementEnabled(systemDir))
    }

    @Test
    fun `configureTexturesForGame does NOT overwrite user manual configuration when userPreference is null`() {
        val serial = "SLUS-20002"
        val replacementsDir = manager.ensureReplacementsDir(systemDir, serial)
        File(replacementsDir, "tex.png").writeBytes(byteArrayOf(1))

        // User manually disabled texture replacements in INI
        manager.setTextureReplacementEnabled(systemDir, false)
        assertEquals(false, manager.isTextureReplacementEnabled(systemDir))

        // On game launch (userPreference = null), even though textures exist, Argosy must NOT force enable it!
        manager.configureTexturesForGame(systemDir, serial, userPreference = null)
        assertEquals("User's manual setting of false must be preserved", false, manager.isTextureReplacementEnabled(systemDir))
    }

    @Test
    fun `configureTexturesForGame auto-enables textures when unset in INI and textures exist`() {
        val serial = "SLUS-20002"
        val replacementsDir = manager.ensureReplacementsDir(systemDir, serial)
        File(replacementsDir, "tex.png").writeBytes(byteArrayOf(1))

        assertNull(manager.isTextureReplacementEnabled(systemDir))

        manager.configureTexturesForGame(systemDir, serial, userPreference = null)
        assertEquals("Should auto-enable when unset and textures exist", true, manager.isTextureReplacementEnabled(systemDir))
    }

    @Test
    fun `configureTexturesForGame does not enable textures when unset in INI and no textures exist`() {
        val serial = "SLUS-20002"

        assertNull(manager.isTextureReplacementEnabled(systemDir))

        manager.configureTexturesForGame(systemDir, serial, userPreference = null)
        assertNull("Should remain unset when no textures exist", manager.isTextureReplacementEnabled(systemDir))
    }
}
