package com.nendo.argosy.data.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDefinitionsTest {

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
}
