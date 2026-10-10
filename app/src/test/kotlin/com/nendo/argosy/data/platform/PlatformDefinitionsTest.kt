package com.nendo.argosy.data.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDefinitionsTest {

    @Test
    fun `aliased platforms keep their own short names instead of the parent's`() {
        assertEquals("Win 3.x", PlatformDefinitions.getAliasDisplayName("win3x")?.second)
        assertEquals("Win 9x", PlatformDefinitions.getAliasDisplayName("win9x")?.second)
        assertEquals("Famicom", PlatformDefinitions.getAliasDisplayName("famicom")?.second)
        assertEquals("SFC", PlatformDefinitions.getAliasDisplayName("sfam")?.second)
        assertEquals("windows", PlatformDefinitions.getCanonicalSlug("win3x"))
        assertEquals("snes", PlatformDefinitions.getCanonicalSlug("sfam"))
    }

    @Test
    fun `resolveImportSlug remaps RomM pico folder to pico8 by name`() {
        assertEquals("pico8", PlatformDefinitions.resolveImportSlug("pico", "PICO-8"))
        assertEquals("pico8", PlatformDefinitions.resolveImportSlug("pico", "Pico-8"))
        assertEquals("pico8", PlatformDefinitions.resolveImportSlug("pico", "pico 8"))
        assertEquals("pico8", PlatformDefinitions.resolveImportSlug("PICO", "PICO-8"))
    }

    @Test
    fun `resolveImportSlug leaves Sega Pico alone`() {
        assertEquals("pico", PlatformDefinitions.resolveImportSlug("pico", "Pico"))
        assertEquals("pico", PlatformDefinitions.resolveImportSlug("pico", "Sega Pico"))
        assertEquals("pico", PlatformDefinitions.resolveImportSlug("pico", null))
    }

    @Test
    fun `resolveImportSlug only touches the ambiguous pico slug`() {
        assertEquals("pico-8", PlatformDefinitions.resolveImportSlug("pico-8", "PICO-8"))
        assertEquals("psx", PlatformDefinitions.resolveImportSlug("psx", "PlayStation"))
        assertEquals("snes", PlatformDefinitions.resolveImportSlug("snes", "Super Nintendo"))
    }

    @Test
    fun `pico8 resolves to its own emulators, sega pico stays distinct`() {
        assertEquals("pico8", PlatformDefinitions.getCanonicalSlug(PlatformDefinitions.resolveImportSlug("pico", "PICO-8")))
        assertEquals("pico8", PlatformDefinitions.getCanonicalSlug("pico-8"))
        assertEquals("pico8", PlatformDefinitions.getCanonicalSlug("pico8"))
        assertEquals("pico", PlatformDefinitions.getCanonicalSlug("pico"))
    }

    @Test
    fun `suffixed slug resolves to the longest known parent containing separators`() {
        assertEquals("neogeocd", PlatformDefinitions.getCanonicalSlug("neo-geo-cd-hacks"))
        assertEquals("wsc", PlatformDefinitions.getCanonicalSlug("wonderswan-color-hacks"))
        assertEquals("NGCD Hacks" to "NGCD Hacks", PlatformDefinitions.deriveDisplayName("neo-geo-cd-hacks"))
        assertEquals("WSC Hacks" to "WSC Hacks", PlatformDefinitions.deriveDisplayName("wonderswan-color-hacks"))
    }

    @Test
    fun `suffixed slug with a single-token parent keeps resolving to that parent`() {
        assertEquals("snes", PlatformDefinitions.getCanonicalSlug("snes-hacks"))
        assertEquals("3ds", PlatformDefinitions.getCanonicalSlug("3ds-staging"))
        assertEquals("SNES Hacks" to "SNES Hacks", PlatformDefinitions.deriveDisplayName("snes-hacks"))
        assertEquals("3DS Staging" to "3DS Staging", PlatformDefinitions.deriveDisplayName("3ds-staging"))
    }

    @Test
    fun `unsuffixed known slug is its own platform`() {
        assertEquals("wsc", PlatformDefinitions.getCanonicalSlug("wonderswan-color"))
        assertEquals("neogeocd", PlatformDefinitions.getCanonicalSlug("neo-geo-cd"))
        assertNull(PlatformDefinitions.deriveDisplayName("wonderswan-color"))
        assertNull(PlatformDefinitions.deriveDisplayName("snes"))
    }

    @Test
    fun `suffixed slug with an unknown parent is left unresolved`() {
        assertEquals("notaplatform-hacks", PlatformDefinitions.getCanonicalSlug("notaplatform-hacks"))
        assertNull(PlatformDefinitions.deriveDisplayName("notaplatform-hacks"))
    }
}
