package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavePathRegistryTest {

    @Test
    fun `resolvePathWithPackage expands package placeholder`() {
        val config = SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf("/storage/Android/data/{package}/files/save"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true,
        )
        val out = SavePathRegistry.resolvePathWithPackage(config, "dev.eden.eden_emulator")
        assertEquals(listOf("/storage/Android/data/dev.eden.eden_emulator/files/save"), out)
    }

    @Test
    fun `resolvePathWithPackage falls back to registry package when caller passes null`() {
        val config = SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf("/storage/Android/data/{package}/files/save"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true,
        )
        val out = SavePathRegistry.resolvePathWithPackage(config, null)
        assertEquals(1, out.size)
        assertFalse("Registry fallback should expand {package}", out[0].contains("{package}"))
        assertTrue("Should use eden's base package", out[0].contains("dev.eden.eden_emulator"))
    }

    @Test
    fun `resolvePath fills the package placeholder from the emulator's registered package`() {
        val config = SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf("/storage/Android/data/{package}/files/save"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true,
        )
        val out = SavePathRegistry.resolvePath(config, "switch")
        assertEquals(listOf("/storage/Android/data/dev.eden.eden_emulator/files/save"), out)
    }

    @Test
    fun `resolvePath expands core placeholder when usesCore is true`() {
        val config = SavePathConfig(
            emulatorId = "retroarch_64",
            defaultPaths = listOf("/storage/RetroArch/saves/{core}"),
            saveExtensions = listOf("srm"),
            usesCore = true,
        )
        val out = SavePathRegistry.resolvePath(config, "snes")
        assertTrue("Must include expanded path", out.any { !it.contains("{core}") })
        assertTrue("Must include the core-less fallback", out.any { it.endsWith("/saves") })
    }

    @Test
    fun `resolvePath drops a path whose core placeholder it cannot fill`() {
        val config = SavePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf("/storage/PSP/{core}/SAVEDATA"),
            saveExtensions = listOf("*"),
            usesCore = false,
        )
        val out = SavePathRegistry.resolvePath(config, "psp")
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `resolvePathWithPackage drops a package path when the config does not use the template`() {
        val config = SavePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf("/storage/Android/data/{package}/files/memcards"),
            saveExtensions = listOf("mcd"),
            usesPackageTemplate = false,
        )
        val out = SavePathRegistry.resolvePathWithPackage(config, "com.example.app")
        assertEquals(emptyList<String>(), out)
    }

    @Test
    fun `getConfigForPlatform falls back to base emulator config when no platform variant exists`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "snes")
        assertNotNull(config)
        assertEquals("retroarch_64", config!!.emulatorId)
    }

    @Test
    fun `getConfigForPlatform prefers platform-keyed variant over base config`() {
        val variant = SavePathRegistry.getConfigForPlatform("retroarch_64", "psp")
        assertNotNull(variant)
        assertEquals("retroarch_64_psp", variant!!.emulatorId)
        assertTrue(variant.usesFolderBasedSaves)
    }

    @Test
    fun `getConfigForPlatform routes built-in GameCube to the GCI folder config`() {
        val config = SavePathRegistry.getConfigForPlatform("argosy", "gc")
        assertNotNull(config)
        assertEquals("argosy", config!!.emulatorId)
        assertTrue("built-in GameCube must use GCI folder handling", config.usesGciFormat)
        assertEquals(listOf("gci"), config.saveExtensions)
    }

    @Test
    fun `getConfigForPlatform canonicalizes ngc alias to the built-in GameCube GCI config`() {
        val config = SavePathRegistry.getConfigForPlatform("argosy", "ngc")
        assertNotNull(config)
        assertTrue("ngc alias must resolve to the GCI config", config!!.usesGciFormat)
    }

    @Test
    fun `getConfigForPlatform still resolves retroarch GameCube via raw slug fallback`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch", "ngc")
        assertNotNull(config)
        assertEquals("retroarch_ngc", config!!.emulatorId)
    }

    @Test
    fun `retroarch Wii resolves the dolphin core's NAND title folder`() {
        val config = SavePathRegistry.getConfigForPlatform("retroarch_64", "wii")
        assertNotNull(config)
        assertEquals("retroarch_64_wii", config!!.emulatorId)
        assertTrue(config.defaultPaths.all { it.endsWith("/User/Wii/title/00010000") })
    }

    @Test
    fun `getConfig returns null for an unsupported emulator`() {
        val cfg = SavePathRegistry.getConfig("redream")
        assertNull("redream is marked supported=false", cfg)
    }

    @Test
    fun `getConfigForPlatform returns null for an unsupported emulator`() {
        assertNull(SavePathRegistry.getConfigForPlatform("redream", "dreamcast"))
        assertNull(SavePathRegistry.getConfigForPlatformByPackage("io.recompiled.redream", "dreamcast"))
    }

    @Test
    fun `getConfigIncludingUnsupported returns config even when supported is false`() {
        val cfg = SavePathRegistry.getConfigIncludingUnsupported("redream")
        assertNotNull(cfg)
        assertFalse(cfg!!.isUsable)
    }

    @Test
    fun `duckstation needs root and is unusable without the root daemon`() {
        val cfg = SavePathRegistry.getConfigIncludingUnsupported("duckstation")
        assertNotNull(cfg)
        assertTrue(cfg!!.requiresRoot)
        assertFalse(cfg.isUsable)
        assertNull(SavePathRegistry.getConfig("duckstation"))
    }

    @Test
    fun `getConfigByPackage resolves a mapped emulator package`() {
        val cfg = SavePathRegistry.getConfigByPackage("org.dolphinemu.dolphinemu")
        assertNotNull(cfg)
        assertEquals("dolphin", cfg!!.emulatorId)
    }

    @Test
    fun `resolveConfigIdForPackage falls back to prefix match`() {
        val id = SavePathRegistry.resolveConfigIdForPackage("org.dolphinemu.dolphinemu.beta")
        assertEquals("dolphin", id)
    }

    @Test
    fun `resolveConfigIdForPackage prefers exact match over prefix`() {
        val id = SavePathRegistry.resolveConfigIdForPackage("org.dolphinemu.mmjr")
        assertEquals("dolphin_mmjr", id)
    }

    @Test
    fun `getConfigByPackage returns null for unknown package`() {
        assertNull(SavePathRegistry.getConfigByPackage("com.nonexistent.emulator"))
    }

    @Test
    fun `canSyncWithSettings returns false when saveSync disabled regardless of emulator`() {
        assertFalse(SavePathRegistry.canSyncWithSettings("eden", saveSyncEnabled = false))
        assertFalse(SavePathRegistry.canSyncWithSettings("retroarch_64", saveSyncEnabled = false))
    }

    @Test
    fun `canSyncWithSettings returns true for a supported emulator when sync enabled`() {
        assertTrue(SavePathRegistry.canSyncWithSettings("eden", saveSyncEnabled = true))
    }

    @Test
    fun `canSyncWithSettings returns false for unsupported emulator even when sync enabled`() {
        assertFalse(SavePathRegistry.canSyncWithSettings("redream", saveSyncEnabled = true))
    }
}
