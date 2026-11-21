package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PizzaBoyDsRegistryTest {

    private val pkg = "com.pizzaemulators.pbds"

    @Test
    fun `Pizza Boy Double Slice is a DS emulator`() {
        assertEquals("pizza_boy_ds", EmulatorRegistry.getByPackage(pkg)?.id)
        assertTrue(EmulatorRegistry.getForPlatform("nds").any { it.id == "pizza_boy_ds" })
    }

    @Test
    fun `it launches its MainActivity with the rom in rom_uri like the other Pizza Boy builds`() {
        val emulator = EmulatorRegistry.getByPackage(pkg)
        assertEquals(android.content.Intent.ACTION_MAIN, emulator?.launchAction)
        val config = emulator?.launchConfig as LaunchConfig.Custom
        assertEquals("$pkg.MainActivity", config.activityClass)
        assertEquals(ExtraValue.FileUriString, config.intentExtras["rom_uri"])
    }

    @Test
    fun `its saves resolve to the sav files in its own data folder`() {
        val saves = SavePathRegistry.getConfigByPackage(pkg)
        assertEquals("pizza_boy_ds", saves?.emulatorId)
        assertTrue(saves!!.defaultPaths.any { it.endsWith("Android/data/$pkg/files/save") })
        assertEquals(listOf("sav"), saves.saveExtensions)
    }
}
