package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game-detail core picker must offer cores that the chosen emulator can actually run:
 * built-in games get [LibretroCoreRegistry] cores, external RetroArch gets the RetroArch list.
 * Regression guard for a RetroArch-only core being pinned on built-in.
 */
class EmulatorRegistrySelectableCoresTest {

    @Test
    fun `built-in mode offers only registered built-in cores`() {
        val ids = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = true).map { it.id }.toSet()

        assertTrue("built-in psx must offer pcsx_rearmed", "pcsx_rearmed" in ids)
        assertTrue("built-in psx must offer mednafen_psx_hw", "mednafen_psx_hw" in ids)
        assertTrue("built-in psx must offer swanstation", "swanstation" in ids)
        assertFalse("mednafen_psx (sw RA core) is not a built-in core", "mednafen_psx" in ids)
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
        assertTrue("mednafen_psx only belongs to external", "mednafen_psx" in external && "mednafen_psx" !in builtIn)
    }

    @Test
    fun `default built-in psx core is beetle psx hw`() {
        assertEquals(
            "mednafen_psx_hw",
            EmulatorRegistry.getDefaultSelectableCore("psx", isBuiltIn = true)?.id
        )
    }

    @Test
    fun `external mode offers dolphin for gamecube and wii`() {
        val gc = EmulatorRegistry.getSelectableCores("gc", isBuiltIn = false).map { it.id }
        val wii = EmulatorRegistry.getSelectableCores("wii", isBuiltIn = false).map { it.id }
        val alias = EmulatorRegistry.getSelectableCores("gamecube", isBuiltIn = false).map { it.id }

        assertEquals(listOf("dolphin"), gc)
        assertEquals(listOf("dolphin"), wii)
        assertEquals(gc, alias)
    }

    @Test
    fun `every platform the retroarch def claims can resolve a selectable core`() {
        val retroArch = EmulatorRegistry.getById("retroarch")
        requireNotNull(retroArch) { "retroarch def must exist" }

        val unresolvable = retroArch.supportedPlatforms.filter { slug ->
            EmulatorRegistry.getDefaultSelectableCore(slug, isBuiltIn = false) == null &&
                EmulatorRegistry.getDefaultCore(slug) == null
        }.toSet()

        assertEquals(
            "no core resolves for external RetroArch on these platforms",
            emptySet<String>(),
            unresolvable
        )
    }

    @Test
    fun `platform slug aliases resolve to the same built-in cores`() {
        val bySlug = EmulatorRegistry.getSelectableCores("psx", isBuiltIn = true).map { it.id }.toSet()
        val byAlias = EmulatorRegistry.getSelectableCores("ps1", isBuiltIn = true).map { it.id }.toSet()
        assertEquals(bySlug, byAlias)
    }
}
