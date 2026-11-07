package com.nendo.argosy.data.emulator.armsx2

import com.nendo.argosy.data.emulator.BuiltinTexturePathRegistry
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Armsx2TextureManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val manager = Armsx2TextureManager()

    private fun iniWith(text: String): File {
        val systemDir = tempFolder.newFolder("system")
        manager.getIniFile(systemDir).apply {
            parentFile?.mkdirs()
            writeText(text)
        }
        return systemDir
    }

    @Test
    fun `the default the core wrote on its first run is turned on`() {
        val systemDir = iniWith("[UI]\nSettingsVersion = 1\n\n[EmuCore/GS]\nLoadTextureReplacements = false\nVsyncEnable = false\n")

        assertTrue(manager.enableForBuiltinCore(systemDir))

        assertEquals(true, manager.isTextureReplacementEnabled(systemDir))
        val text = manager.getIniFile(systemDir).readText()
        assertTrue(text.contains("SettingsVersion = 1"))
        assertTrue(text.contains("VsyncEnable = false"))
    }

    @Test
    fun `an INI the core never wrote is not created, because the core would discard it`() {
        val systemDir = tempFolder.newFolder("fresh")

        assertFalse(manager.enableForBuiltinCore(systemDir))

        assertFalse(manager.getIniFile(systemDir).exists())
        assertNull(manager.isTextureReplacementEnabled(systemDir))
    }

    @Test
    fun `the built-in rule targets the folder PCSX2 scans`() {
        val rule = BuiltinTexturePathRegistry.resolveRule("armsx2", "ps2")
        assertNotNull(rule)
        val systemDir = File("/system")
        assertEquals(
            File(systemDir, "pcsx2/textures/SLUS-20312/replacements"),
            BuiltinTexturePathRegistry.resolveTargetDir(rule!!, File("/saves"), systemDir, "SLUS-20312")
        )
    }

    @Test
    fun `the standalone app's settings are edited in place and never created`() {
        val root = tempFolder.newFolder("storage")
        val fal = mockk<FileAccessLayer>(relaxed = true) {
            every { isRestrictedPath(any()) } returns false
        }

        assertFalse(manager.enableForStandaloneApp("com.armsx2", root.absolutePath, fal))
        val ini = File(root, "Android/data/com.armsx2/files/PCSX2-Android.ini")
        assertFalse(ini.exists())

        ini.parentFile?.mkdirs()
        ini.writeText("[EmuCore/GS]\nLoadTextureReplacements = false\n")
        assertTrue(manager.enableForStandaloneApp("com.armsx2", root.absolutePath, fal))
        assertTrue(ini.readText().contains("LoadTextureReplacements = true"))
    }
}
