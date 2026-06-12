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
    fun `resolvePath without package leaves package placeholder unexpanded`() {
        val config = SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf("/storage/Android/data/{package}/files/save"),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true,
        )
        val out = SavePathRegistry.resolvePath(config, "switch")
        assertEquals(listOf("/storage/Android/data/{package}/files/save"), out)
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
    fun `resolvePath does not expand core when usesCore is false`() {
        val config = SavePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf("/storage/PSP/{core}/SAVEDATA"),
            saveExtensions = listOf("*"),
            usesCore = false,
        )
        val out = SavePathRegistry.resolvePath(config, "psp")
        assertEquals(listOf("/storage/PSP/{core}/SAVEDATA"), out)
    }

    @Test
    fun `resolvePathWithPackage leaves package literal when config does not use template`() {
        val config = SavePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf("/storage/Android/data/{package}/files/memcards"),
            saveExtensions = listOf("mcd"),
            usesPackageTemplate = false,
        )
        val out = SavePathRegistry.resolvePathWithPackage(config, "com.example.app")
        assertEquals(listOf("/storage/Android/data/{package}/files/memcards"), out)
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
    fun `getConfig returns null for an unsupported emulator`() {
        val cfg = SavePathRegistry.getConfig("redream")
        assertNull("redream is marked supported=false", cfg)
    }

    @Test
    fun `getConfigIncludingUnsupported returns config even when supported is false`() {
        val cfg = SavePathRegistry.getConfigIncludingUnsupported("redream")
        assertNotNull(cfg)
        assertFalse(cfg!!.supported)
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
