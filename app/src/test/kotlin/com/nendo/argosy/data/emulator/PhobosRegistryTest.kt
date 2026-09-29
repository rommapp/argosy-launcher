package com.nendo.argosy.data.emulator

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhobosRegistryTest {

    @Test
    fun `Phobos is recognised by its package`() {
        assertEquals("phobos", EmulatorRegistry.getByPackage("com.phobos.emulator")?.id)
    }

    @Test
    fun `Phobos is offered on the platforms it runs, not on PC Engine CD`() {
        listOf(
            "nes", "fds", "snes", "n64", "n64dd", "gb", "gbc", "gba",
            "sg1000", "sms", "genesis", "scd", "gg", "psx",
            "tg16", "supergrafx", "neogeo", "neogeocd", "ngp", "ngpc",
            "atari2600", "coleco", "wonderswan", "wsc", "msx", "msx2", "zx"
        ).forEach { platform ->
            assertTrue(platform, EmulatorRegistry.getForPlatform(platform).any { it.id == "phobos" })
        }
        assertFalse(EmulatorRegistry.getForPlatform("tgcd").any { it.id == "phobos" })
    }

    @Test
    fun `Phobos is launched at its activity with the platform slug`() {
        val phobos = EmulatorRegistry.getById("phobos")!!
        assertEquals(Intent.ACTION_VIEW, phobos.launchAction)
        val config = phobos.launchConfig as LaunchConfig.Custom
        assertEquals("com.phobos.emulator.MainActivity", config.activityClass)
        assertEquals(ExtraValue.Platform, config.intentExtras["platform"])
    }
}
