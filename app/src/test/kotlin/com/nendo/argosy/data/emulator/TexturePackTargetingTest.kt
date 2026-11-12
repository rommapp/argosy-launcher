package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TexturePackTargetingTest {

    private val root = File("/storage/emulated/0")
    private val roots = listOf(root.path)

    private fun target(pkg: String, slug: String, key: String, existing: Set<String> = emptySet()): File? {
        val rule = ExternalTexturePathRegistry.resolveRule(pkg, slug) ?: return null
        return ExternalTexturePathRegistry.resolveTargetDir(
            rule = rule,
            emulatorPackage = pkg,
            externalStorageRoots = roots,
            folderKey = key,
            directoryExists = { it.invariantSeparatorsPath.removePrefix("/storage/emulated/0/") in existing }
        )
    }

    @Test
    fun `official Dolphin ignores a leftover legacy folder once its package dir is active`() {
        val dir = target(
            pkg = "org.dolphinemu.dolphinemu",
            slug = "wii",
            key = "RMCE01",
            existing = setOf(
                "Android/data/org.dolphinemu.dolphinemu/files/Config",
                "dolphin-emu/Load/Textures"
            )
        )
        assertEquals(
            File(root, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/RMCE01"),
            dir
        )
    }

    @Test
    fun `official Dolphin never targets an MMJR folder`() {
        val dir = target(
            pkg = "org.dolphinemu.dolphinemu",
            slug = "gc",
            key = "GZLE01",
            existing = setOf("mmjr/Load/Textures", "mmjr2-vbi/Load/Textures")
        )
        assertEquals(
            File(root, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01"),
            dir
        )
    }

    @Test
    fun `Dolphin package variants resolve through the family prefix`() {
        assertNotNull(ExternalTexturePathRegistry.resolveRule("org.dolphinemu.dolphinemu.debug", "wii"))
        assertNull(ExternalTexturePathRegistry.resolveRule("org.dolphinemufake", "wii"))
    }

    @Test
    fun `MMJR keeps its own legacy folders`() {
        val dir = target(
            pkg = "org.dolphinemu.mmjr",
            slug = "gc",
            key = "GZLE01",
            existing = setOf("mmjr/Load/Textures")
        )
        assertEquals(File(root, "mmjr/Load/Textures/GZLE01"), dir)
    }

    @Test
    fun `standalone ARMSX2 packs land in the serial's replacements folder`() {
        assertEquals(
            File(root, "Android/data/com.armsx2/files/textures/SLUS-20312/replacements"),
            target("com.armsx2", "ps2", "SLUS-20312")
        )
        assertEquals(
            File(root, "Android/data/come.nanodata.armsx2/files/textures/SLUS-20312/replacements"),
            target("come.nanodata.armsx2", "ps2", "SLUS-20312")
        )
    }

    @Test
    fun `pack paths are taken from after the replacements folder whatever wraps it`() {
        assertEquals("a.png", TexturePackExtractor.pathAfterSegment("SLUS-20312/replacements/a.png", "replacements"))
        assertEquals("sub/a.png", TexturePackExtractor.pathAfterSegment("x/y/Replacements/sub/a.png", "replacements"))
        assertNull(TexturePackExtractor.pathAfterSegment("SLUS-20312/a.png", "replacements"))
        assertNull(TexturePackExtractor.pathAfterSegment("x/replacements", "replacements"))
    }

    @Test
    fun `PS2 serials normalize from every form the app stores`() {
        assertEquals("SLUS-20312", TitleIdExtractor.normalizePs2Serial("SLUS-20312"))
        assertEquals("SLUS-20312", TitleIdExtractor.normalizePs2Serial("slus_203.12;1"))
        assertEquals("SLUS-20312", TitleIdExtractor.normalizePs2Serial("SLUS20312"))
        assertEquals("SLUS-20152", TitleIdExtractor.normalizePs2Serial("BASLUS-20152AC04"))
        assertEquals("SCES-53202", TitleIdExtractor.normalizePs2Serial("BESCES-53202"))
        assertNull(TitleIdExtractor.normalizePs2Serial("Some Game"))
    }

    @Test
    fun `setValue leaves unrelated keys alone and matches whole key names`() {
        val input = "[Settings]\nCacheHiresTextures = False\nAspectRatio = 1\n\n[Other]\nHiresTextures = False"
        val output = EmulatorIniFile.setValue(input, "Settings", "HiresTextures", "True")
        assertEquals(
            "[Settings]\nHiresTextures = True\nCacheHiresTextures = False\nAspectRatio = 1\n\n[Other]\nHiresTextures = False",
            output
        )
    }
}
