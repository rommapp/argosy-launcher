package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.BuildConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CoreSystemDataManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var manager: CoreSystemDataManager
    private lateinit var systemDir: File

    @Before
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        manager = CoreSystemDataManager(context)
        systemDir = tempFolder.newFolder("system")
    }

    @Test
    fun `needsSystemData returns true for ppsspp when assets directory missing`() {
        assertTrue(manager.needsSystemData("ppsspp", systemDir))
    }

    @Test
    fun `needsSystemData returns false for ppsspp when version marker is current`() {
        writeMarker("PPSSPP", BuildConfig.PPSSPP_SYS_VERSION.toString())
        assertFalse(manager.needsSystemData("ppsspp", systemDir))
    }

    @Test
    fun `needsSystemData returns true for ppsspp when version marker is stale`() {
        writeMarker("PPSSPP", (BuildConfig.PPSSPP_SYS_VERSION - 1).toString())
        assertTrue(manager.needsSystemData("ppsspp", systemDir))
    }

    @Test
    fun `needsSystemData returns true for ppsspp when version marker is unparseable`() {
        writeMarker("PPSSPP", "garbage")
        assertTrue(manager.needsSystemData("ppsspp", systemDir))
    }

    @Test
    fun `needsSystemData returns true for dolphin when assets directory missing`() {
        assertTrue(manager.needsSystemData("dolphin", systemDir))
    }

    @Test
    fun `needsSystemData returns false for dolphin when version marker is current`() {
        writeMarker("dolphin-emu/Sys", BuildConfig.DOLPHIN_SYS_VERSION.toString())
        assertFalse(manager.needsSystemData("dolphin", systemDir))
    }

    @Test
    fun `needsSystemData returns false for cores without system data requirements`() {
        assertFalse(manager.needsSystemData("snes9x", systemDir))
        assertFalse(manager.needsSystemData("mednafen_psx_hw", systemDir))
        assertFalse(manager.needsSystemData("", systemDir))
    }

    @Test
    fun `ppsspp and dolphin staleness checks are independent`() {
        writeMarker("PPSSPP", BuildConfig.PPSSPP_SYS_VERSION.toString())
        assertFalse(manager.needsSystemData("ppsspp", systemDir))
        assertTrue(manager.needsSystemData("dolphin", systemDir))
    }

    @Test
    fun `version marker path matches the directory the libretro core reads from`() {
        writeMarker("PPSSPP", BuildConfig.PPSSPP_SYS_VERSION.toString())
        val marker = File(systemDir, "PPSSPP/.argosy_version")
        assertTrue("Marker must live under PPSSPP/", marker.exists())
        assertEquals(BuildConfig.PPSSPP_SYS_VERSION.toString(), marker.readText().trim())
    }

    private fun writeMarker(subPath: String, contents: String) {
        val dir = File(systemDir, subPath).apply { mkdirs() }
        File(dir, ".argosy_version").writeText(contents)
    }
}
