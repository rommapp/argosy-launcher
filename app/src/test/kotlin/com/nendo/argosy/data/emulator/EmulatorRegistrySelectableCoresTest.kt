package com.nendo.argosy.data.emulator

import com.nendo.argosy.libretro.LibretroCoreRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Core pickers must offer cores that the chosen emulator can actually run:
 * built-in games get [LibretroCoreRegistry] cores, external RetroArch gets the RetroArch list.
 * Regression guard for mixed built-in and external core catalogues.
 */
class EmulatorRegistrySelectableCoresTest {

    @Test
    fun `built-in mode offers only registered built-in cores`() {
        val cores = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = true)
        val ids = cores.map { it.id }.toSet()

        assertTrue("built-in psx must offer pcsx_rearmed", "pcsx_rearmed" in ids)
        assertTrue("built-in psx must offer mednafen_psx_hw", "mednafen_psx_hw" in ids)
        assertFalse("swanstation is a RetroArch core, not a built-in one", "swanstation" in ids)
        assertFalse("mednafen_psx (sw RA core) is not a built-in core", "mednafen_psx" in ids)
        assertEquals(
            mapOf(
                "pcsx_rearmed" to "PCSX ReARMed",
                "mednafen_psx_hw" to "Beetle PSX HW"
            ),
            cores.associate { it.id to it.displayName }
        )
    }

    @Test
    fun `external mode offers the RetroArch cores`() {
        val ids = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = false).map { it.id }.toSet()

        assertTrue("external psx offers swanstation", "swanstation" in ids)
        assertTrue("external psx offers pcsx_rearmed", "pcsx_rearmed" in ids)
    }

    @Test
    fun `built-in and external core lists differ for psx`() {
        val builtIn = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = true).map { it.id }.toSet()
        val external = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = false).map { it.id }.toSet()
        assertTrue("swanstation only belongs to external", "swanstation" in external && "swanstation" !in builtIn)
    }

    @Test
    fun `default built-in psx core is pcsx_rearmed`() {
        assertEquals(
            "pcsx_rearmed",
            EmulatorRegistry.getDefaultSelectableCore("psx", isBuiltIn = true)?.id
        )
    }

    @Test
    fun `platform slug aliases resolve to the same built-in cores`() {
        val bySlug = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = true).map { it.id }.toSet()
        val byAlias = EmulatorRegistry.getSelectableCores("ps1", isBuiltIn = true).map { it.id }.toSet()
        assertEquals(bySlug, byAlias)
    }

    @Test
    fun `stored external core falls back to built-in default`() {
        val selection = EmulatorRegistry.resolveCoreSelection(
            "psx",
            isBuiltIn = true,
            storedCoreId = "swanstation"
        )

        assertEquals("pcsx_rearmed", selection.selectedCore?.id)
        assertFalse(selection.availableCores.any { it.id == "swanstation" })
    }

    @Test
    fun `stored external core remains selected for external retroarch`() {
        val selection = EmulatorRegistry.resolveCoreSelection(
            "psx",
            isBuiltIn = false,
            storedCoreId = "swanstation"
        )

        assertEquals("swanstation", selection.selectedCore?.id)
        assertEquals("SwanStation", selection.selectedCore?.displayName)
    }

    @Test
    fun `built-in mode excludes external-only cores across platforms`() {
        val externalOnlyCores = mapOf(
            "nes" to "mesen",
            "snes" to "snes9x2010",
            "nds" to "desmume",
            "saturn" to "yabause"
        )

        externalOnlyCores.forEach { (platform, coreId) ->
            val builtInIds = EmulatorRegistry.getSelectableCores(platform, isBuiltIn = true)
                .map { it.id }
            val externalIds = EmulatorRegistry.getSelectableCores(platform, isBuiltIn = false)
                .map { it.id }

            assertFalse("$coreId must not be offered by built-in $platform", coreId in builtInIds)
            assertTrue("$coreId must remain available to external $platform", coreId in externalIds)
        }
    }

    @Test
    fun `built-in mode uses registered display names across platforms`() {
        val expectedNames = mapOf(
            "n64" to ("mupen64plus_next_gles3" to "Mupen64+ GLES3"),
            "lynx" to ("mednafen_lynx" to "Beetle Lynx"),
            "wonderswan" to ("mednafen_wswan" to "Beetle WS")
        )

        expectedNames.forEach { (platform, expected) ->
            val core = EmulatorRegistry.getSelectableCores(platform, isBuiltIn = true)
                .first { it.id == expected.first }
            assertEquals(expected.second, core.displayName)
        }
    }

    @Test
    fun `built-in resolver always selects from its available catalogue`() {
        LibretroCoreRegistry.getSupportedPlatforms().forEach { platform ->
            val selection = EmulatorRegistry.resolveCoreSelection(
                platform,
                isBuiltIn = true,
                storedCoreId = "external_only_core"
            )

            assertTrue(
                "$platform selected a core outside its built-in catalogue",
                selection.selectedCore == null || selection.selectedCore in selection.availableCores
            )
        }
    }
}
