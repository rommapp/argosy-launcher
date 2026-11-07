package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.BuildConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
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
    private lateinit var fetcher: SystemDataFetcher
    private lateinit var systemDir: File
    private val requestedUrls = mutableListOf<String>()

    @Before
    fun setup() {
        val context = mockk<Context>(relaxed = true)
        fetcher = mockk()
        manager = CoreSystemDataManager(context, fetcher)
        systemDir = tempFolder.newFolder("system")
    }

    private fun serveArmsx2(failing: Set<String> = emptySet()) {
        every { fetcher.fetch<Any?>(any(), any()) } answers {
            val url = firstArg<String>()
            requestedUrls += url
            if (failing.any { url.endsWith(it) }) throw java.io.IOException("offline")
            secondArg<(java.io.InputStream) -> Any?>().invoke(ByteArrayInputStream(url.toByteArray()))
        }
    }

    private val armsx2Base =
        "https://raw.githubusercontent.com/ARMSX2/ARMSX2/0076b5c389ea7296331ad2d96193c6e8d5d314fa"

    @Test
    fun `armsx2 fetches the overrides and font from the pinned commit and the PCSX2 patches archive`() = runTest {
        serveArmsx2()

        assertTrue(manager.ensureCoreSystemData("armsx2", systemDir))

        assertEquals(
            listOf(
                "$armsx2Base/bin/resources-overlay/armsx2_overrides.yaml",
                "$armsx2Base/bin/resources/fonts/Roboto-Regular.ttf",
                "https://github.com/PCSX2/pcsx2_patches/releases/download/latest/patches.zip"
            ),
            requestedUrls
        )
        val overrides = File(systemDir, "pcsx2/resources/armsx2_overrides.yaml")
        val font = File(systemDir, "pcsx2/resources/fonts/Roboto-Regular.ttf")
        assertEquals("$armsx2Base/bin/resources-overlay/armsx2_overrides.yaml", overrides.readText())
        assertTrue(font.exists())
        assertTrue(File(systemDir, "pcsx2/resources/patches.zip").exists())
        assertFalse(manager.needsSystemData("armsx2", systemDir))
    }

    @Test
    fun `armsx2 never writes shaders or a GameIndex`() = runTest {
        serveArmsx2()

        manager.ensureCoreSystemData("armsx2", systemDir)

        val written = File(systemDir, "pcsx2").walkTopDown().filter { it.isFile }.map { it.name }.toList()
        assertTrue(written.none { it.contains("GameIndex", ignoreCase = true) })
        assertTrue(written.none { it.endsWith(".glsl") || it.endsWith(".hlsl") || it.endsWith(".spv") })
        assertTrue(requestedUrls.none { it.contains("shaders") || it.contains("GameIndex") })
    }

    @Test
    fun `a failed armsx2 fetch does not abort the launch and leaves the marker stale`() = runTest {
        serveArmsx2(failing = setOf("Roboto-Regular.ttf"))

        assertTrue(manager.ensureCoreSystemData("armsx2", systemDir))

        assertTrue(manager.needsSystemData("armsx2", systemDir))
        assertFalse(File(systemDir, "pcsx2/resources/fonts/Roboto-Regular.ttf.part").exists())
    }

    @Test
    fun `a failed armsx2 fetch is not retried on the next launch of the same session`() = runTest {
        serveArmsx2(failing = setOf("armsx2_overrides.yaml", "Roboto-Regular.ttf"))
        manager.ensureCoreSystemData("armsx2", systemDir)
        val firstAttempt = requestedUrls.size
        requestedUrls.clear()

        assertTrue(manager.ensureCoreSystemData("armsx2", systemDir))

        assertEquals(1, firstAttempt)
        assertTrue(requestedUrls.isEmpty())
    }

    @Test
    fun `armsx2 skips the network once its marker is current`() = runTest {
        writeMarker("pcsx2/resources", BuildConfig.ARMSX2_SYS_VERSION.toString())
        serveArmsx2()

        assertTrue(manager.ensureCoreSystemData("armsx2", systemDir))

        assertTrue(requestedUrls.isEmpty())
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
