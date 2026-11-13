package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokeDaisyRegistryTest {

    private val pokeDaisy = EmulatorRegistry.getByPackage("com.pokedaisy.app")!!

    @Test
    fun `PokeDaisy is offered on every handheld platform it plays`() {
        for (platform in listOf("gb", "gbc", "gba", "nds")) {
            assertTrue(platform, EmulatorRegistry.getForPlatform(platform).any { it.id == "pokedaisy" })
            assertEquals(platform, "pokedaisy", EmulatorRegistry.getRecommendedEmulators()[platform]?.last())
        }
    }

    @Test
    fun `PokeDaisy launches its frontend activity with the rom path in rom`() {
        assertEquals(android.content.Intent.ACTION_MAIN, pokeDaisy.launchAction)
        val config = pokeDaisy.launchConfig as LaunchConfig.Custom
        assertEquals("com.pokedaisy.app.LaunchActivity", config.activityClass)
        assertEquals(ExtraValue.FilePath, config.intentExtras["rom"])
    }

    @Test
    fun `only emulators that draw their own second screen claim both screens`() {
        assertTrue(pokeDaisy.drawsBothScreens)
        assertTrue(EmulatorRegistry.getById("melondualds")!!.drawsBothScreens)
        assertFalse(EmulatorRegistry.getById("melonds")!!.drawsBothScreens)
        assertFalse(EmulatorRegistry.getById("pizza_boy_gba")!!.drawsBothScreens)
    }

    @Test
    fun `game boy saves live in the data folder and ds saves beside the rom`() {
        for (platform in listOf("gb", "gbc", "gba")) {
            val config = SavePathRegistry.getConfigForPlatformByPackage("com.pokedaisy.app", platform)!!
            assertEquals("pokedaisy", config.emulatorId)
            assertFalse(platform, config.savesBesideRom)
            assertTrue(config.defaultPaths.single().endsWith("Android/data/com.pokedaisy.app/files/saves"))
        }
        val ds = SavePathRegistry.getConfigForPlatformByPackage("com.pokedaisy.app", "nds")!!
        assertEquals("pokedaisy", ds.emulatorId)
        assertTrue(ds.savesBesideRom)
    }
}
