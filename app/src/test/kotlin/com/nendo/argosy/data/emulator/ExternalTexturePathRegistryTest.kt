package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExternalTexturePathRegistryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolves external target path for standalone Dolphin`() {
        val extRoot = tempFolder.newFolder("storage_emulated_0")
        val dolphinRule = ExternalTexturePathRegistry.resolveRule(
            emulatorPackage = "org.dolphinemu.dolphinemu",
            platformSlug = "gc"
        )
        assertNotNull(dolphinRule)
        assertEquals(ExternalTextureBaseRoot.PACKAGE_DATA, dolphinRule!!.baseRoot)
        assertEquals(TextureFolderKeyStrategy.GAME_ID, dolphinRule.folderKeyStrategy)

        val targetDir = ExternalTexturePathRegistry.resolveTargetDir(
            rule = dolphinRule,
            emulatorPackage = "org.dolphinemu.dolphinemu",
            externalStorageRoots = listOf(extRoot.absolutePath),
            folderKey = "GZLE01"
        )
        assertEquals(
            File(extRoot, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01"),
            targetDir
        )
    }

    @Test
    fun `resolves external target path for standalone Dolphin legacy dir when fallback exists on disk`() {
        val extRoot = tempFolder.newFolder("storage_emulated_fallback")
        val legacyDir = File(extRoot, "dolphin-emu/Load/Textures")
        legacyDir.mkdirs()

        val dolphinRule = ExternalTexturePathRegistry.resolveRule(
            emulatorPackage = "org.dolphinemu.dolphinemu",
            platformSlug = "gc"
        )
        assertNotNull(dolphinRule)

        val targetDir = ExternalTexturePathRegistry.resolveTargetDir(
            rule = dolphinRule!!,
            emulatorPackage = "org.dolphinemu.dolphinemu",
            externalStorageRoots = listOf(extRoot.absolutePath),
            folderKey = "GZLE01",
            directoryExists = { it.exists() }
        )
        assertEquals(
            File(extRoot, "dolphin-emu/Load/Textures/GZLE01"),
            targetDir
        )
    }

    @Test
    fun `resolves external target path for standalone PPSSPP and DuckStation`() {
        val extRoot = tempFolder.newFolder("storage_emulated_ext")

        val ppssppRule = ExternalTexturePathRegistry.resolveRule(
            emulatorPackage = "org.ppsspp.ppsspp",
            platformSlug = "psp"
        )
        assertNotNull(ppssppRule)
        assertNotNull(ExternalTexturePathRegistry.resolveRule("org.ppsspp.ppssppgold", "psp"))
        assertNotNull(ExternalTexturePathRegistry.resolveRule("org.ppsspp.ppsspplegacy", "psp"))
        val ppssppTarget = ExternalTexturePathRegistry.resolveTargetDir(
            rule = ppssppRule!!,
            emulatorPackage = "org.ppsspp.ppsspp",
            externalStorageRoots = listOf(extRoot.absolutePath),
            folderKey = "ULUS10041"
        )
        assertEquals(File(extRoot, "PSP/TEXTURES/ULUS10041"), ppssppTarget)

        val duckRule = ExternalTexturePathRegistry.resolveRule(
            emulatorPackage = "com.github.stenzek.duckstation",
            platformSlug = "psx"
        )
        assertNotNull(duckRule)
        val duckTarget = ExternalTexturePathRegistry.resolveTargetDir(
            rule = duckRule!!,
            emulatorPackage = "com.github.stenzek.duckstation",
            externalStorageRoots = listOf(extRoot.absolutePath),
            folderKey = "SLUS00067"
        )
        assertEquals(
            File(extRoot, "Android/data/com.github.stenzek.duckstation/files/textures/SLUS00067"),
            duckTarget
        )
    }

    @Test
    fun `resolves external target path for AetherSX2`() {
        val extRoot = tempFolder.newFolder("storage_aethersx2")
        val aetherRule = ExternalTexturePathRegistry.resolveRule(
            emulatorPackage = "xyz.aethersx2.android",
            platformSlug = "ps2"
        )
        assertNotNull(aetherRule)
        val target = ExternalTexturePathRegistry.resolveTargetDir(
            rule = aetherRule!!,
            emulatorPackage = "xyz.aethersx2.android",
            externalStorageRoots = listOf(extRoot.absolutePath),
            folderKey = "SLUS-20002"
        )
        assertEquals(
            File(extRoot, "Android/data/xyz.aethersx2.android/files/textures/SLUS-20002"),
            target
        )
    }

    @Test
    fun `returns null when no external rule matches package or platform`() {
        assertNull(ExternalTexturePathRegistry.resolveRule("com.retroarch", "nes"))
        assertNull(ExternalTexturePathRegistry.resolveRule("org.dolphinemu.dolphinemu", "nes"))
        assertNull(ExternalTexturePathRegistry.resolveRule(null, "gc"))
    }
}
