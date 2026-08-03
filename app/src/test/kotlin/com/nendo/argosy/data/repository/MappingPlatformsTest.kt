package com.nendo.argosy.data.repository

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingPlatformsTest {

    @Test
    fun `every label refers to a button the platform actually exposes`() {
        MappingPlatforms.ALL.forEach { platform ->
            platform.buttonLabels.keys.forEach { button ->
                assertTrue(
                    "${platform.id} labels button $button but does not expose it",
                    button in platform.buttons
                )
            }
        }
    }

    @Test
    fun `platform ids are unique`() {
        val ids = MappingPlatforms.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `hotkey blocking buttons are exposed by their platform`() {
        MappingPlatforms.ALL.forEach { platform ->
            assertTrue(
                "${platform.id} blocks a button it does not expose",
                platform.buttons.containsAll(platform.hotkeyBlockingButtons)
            )
        }
    }

    @Test
    fun `gba duplicate turbo controls yield to configured hotkeys`() {
        val gba = MappingPlatforms.profileForSlug("gba")

        listOf(RetroButton.X, RetroButton.Y, RetroButton.L2, RetroButton.R2).forEach { button ->
            assertTrue(button in gba.buttons)
            assertFalse(button in gba.hotkeyBlockingButtons)
        }
        assertFalse(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_L2, "gba"))
        assertFalse(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_R2, "gba"))
        assertTrue(RetroButton.L3 in gba.hotkeyBlockingButtons)
        assertTrue(RetroButton.R3 in gba.hotkeyBlockingButtons)
    }

    @Test
    fun `n64 alternate C button route yields while Z retains priority`() {
        val n64 = MappingPlatforms.profileForSlug("n64")

        listOf(RetroButton.A, RetroButton.X, RetroButton.R2).forEach { button ->
            assertTrue(button in n64.buttons)
            assertFalse(button in n64.hotkeyBlockingButtons)
        }
        assertFalse(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_R2, "n64"))
        assertTrue(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_L2, "n64"))
    }

    @Test
    fun `ds screen swap yields while microphone retains priority`() {
        val ds = MappingPlatforms.profileForSlug("nds")

        assertTrue(RetroButton.R2 in ds.buttons)
        assertFalse(RetroButton.R2 in ds.hotkeyBlockingButtons)
        assertFalse(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_R2, "nds"))
        assertTrue(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_L2, "nds"))
    }

    @Test
    fun `native trigger controls retain gameplay priority`() {
        val psx = MappingPlatforms.profileForSlug("psx")

        assertTrue(RetroButton.L2 in psx.hotkeyBlockingButtons)
        assertTrue(RetroButton.R2 in psx.hotkeyBlockingButtons)
        assertTrue(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_L2, "psx"))
        assertTrue(InputPresets.keyMapsToConsoleButton(KeyEvent.KEYCODE_BUTTON_R2, "psx"))
    }

    @Test
    fun `n64 exposes Z and C-buttons mode and drops the unused thumbsticks`() {
        val n64 = MappingPlatforms.profileForSlug("n64")
        assertEquals("Z", n64.buttonLabels[RetroButton.L2])
        assertTrue(RetroButton.R2 in n64.buttons)
        assertFalse(RetroButton.L3 in n64.buttons)
        assertFalse(RetroButton.R3 in n64.buttons)
        assertEquals("A / C-Down (C mode)", n64.buttonLabels[RetroButton.B])
        assertEquals("B / C-Left (C mode)", n64.buttonLabels[RetroButton.Y])
    }

    @Test
    fun `gamecube puts Z on R and the triggers on L2 R2`() {
        val gc = MappingPlatforms.profileForSlug("gc")
        assertEquals("Z", gc.buttonLabels[RetroButton.R])
        assertEquals("L", gc.buttonLabels[RetroButton.L2])
        assertEquals("R", gc.buttonLabels[RetroButton.R2])
    }

    @Test
    fun `psp does not advertise shoulder buttons the core has no descriptors for`() {
        val psp = MappingPlatforms.profileForSlug("psp")
        assertFalse(RetroButton.L2 in psp.buttons)
        assertFalse(RetroButton.R2 in psp.buttons)
        assertFalse(RetroButton.L3 in psp.buttons)
        assertFalse(RetroButton.R3 in psp.buttons)
    }

    @Test
    fun `consoles with distinct hardware no longer share a family profile`() {
        assertEquals("vb", MappingPlatforms.profileForSlug("vb").id)
        assertEquals("sms", MappingPlatforms.profileForSlug("sms").id)
        assertEquals("sms", MappingPlatforms.profileForSlug("gg").id)
        assertEquals("lynx", MappingPlatforms.profileForSlug("lynx").id)
        assertEquals("ngp", MappingPlatforms.profileForSlug("ngpc").id)
        assertEquals("wonderswan", MappingPlatforms.profileForSlug("wsc").id)
        assertEquals("atari-7800", MappingPlatforms.profileForSlug("atari7800").id)
        assertEquals("dreamcast", MappingPlatforms.profileForSlug("dreamcast").id)
        assertEquals("psp", MappingPlatforms.profileForSlug("psp").id)
    }

    @Test
    fun `pc engine numbering matches the core descriptors`() {
        val pce = MappingPlatforms.profileForSlug("tg16")
        assertEquals("I", pce.buttonLabels[RetroButton.A])
        assertEquals("II", pce.buttonLabels[RetroButton.B])
    }

    @Test
    fun `genesis exposes the mode button`() {
        val genesis = MappingPlatforms.profileForSlug("genesis")
        assertEquals("Mode", genesis.buttonLabels[RetroButton.SELECT])
    }

    @Test
    fun `neogeo does not expose select`() {
        assertFalse(RetroButton.SELECT in MappingPlatforms.profileForSlug("neogeo").buttons)
    }

    @Test
    fun `platforms needing L2 R2 expose them so the shoulder filter allows the keycodes`() {
        listOf("n64", "gc", "dreamcast", "saturn", "psx", "nds", "wii").forEach { slug ->
            val buttons = MappingPlatforms.profileForSlug(slug).buttons
            assertTrue(
                "$slug should expose L2/R2",
                RetroButton.L2 in buttons || RetroButton.R2 in buttons
            )
        }
    }

    @Test
    fun `two-button consoles do not expose shoulders`() {
        listOf("nes", "gb", "sms", "ngp").forEach { slug ->
            val buttons = MappingPlatforms.profileForSlug(slug).buttons
            assertFalse("$slug should not expose L", RetroButton.L in buttons)
            assertFalse("$slug should not expose R", RetroButton.R in buttons)
        }
    }

    @Test
    fun `each computer keeps the fire button its own core reads`() {
        assertEquals("Fire", MappingPlatforms.profileForSlug("c64").buttonLabels[RetroButton.B])
        assertEquals("Fire / Red", MappingPlatforms.profileForSlug("amiga").buttonLabels[RetroButton.B])
        assertEquals("1", MappingPlatforms.profileForSlug("msx").buttonLabels[RetroButton.A])
        assertEquals("2", MappingPlatforms.profileForSlug("msx").buttonLabels[RetroButton.B])
        assertEquals("5", MappingPlatforms.profileForSlug("msx").buttonLabels[RetroButton.START])
        assertEquals("6", MappingPlatforms.profileForSlug("msx").buttonLabels[RetroButton.SELECT])
        assertFalse(RetroButton.L in MappingPlatforms.profileForSlug("msx").buttons)
        assertEquals("Fire", MappingPlatforms.profileForSlug("zx").buttonLabels[RetroButton.A])
        assertEquals("Up", MappingPlatforms.profileForSlug("zx").buttonLabels[RetroButton.B])
    }

    @Test
    fun `consoles that borrowed the nes profile now carry their own buttons`() {
        assertEquals("Button 1", MappingPlatforms.profileForSlug("coleco").buttonLabels[RetroButton.A])
        assertEquals("Action", MappingPlatforms.profileForSlug("odyssey2").buttonLabels[RetroButton.B])
        assertEquals("Pull", MappingPlatforms.profileForSlug("channelf").buttonLabels[RetroButton.X])
        assertEquals("C", MappingPlatforms.profileForSlug("pokemini").buttonLabels[RetroButton.R])
        assertEquals("O", MappingPlatforms.profileForSlug("pico8").buttonLabels[RetroButton.A])
        assertEquals("Disk Side Change", MappingPlatforms.profileForSlug("fds").buttonLabels[RetroButton.L])
    }

    @Test
    fun `wii profiles match the dolphin device each one names`() {
        val wii = MappingPlatforms.profileForSlug("wii")
        assertEquals("1", wii.buttonLabels[RetroButton.X])
        assertEquals("+", wii.buttonLabels[RetroButton.START])
        assertEquals("C", MappingPlatforms.WII_NUNCHUK.buttonLabels[RetroButton.X])
        assertEquals("ZL", MappingPlatforms.WII_CLASSIC.buttonLabels[RetroButton.L])
        assertEquals("L", MappingPlatforms.WII_CLASSIC.buttonLabels[RetroButton.L2])
    }

    @Test
    fun `fbneo and mame number their classic pad differently from button five`() {
        val fbneo = MappingPlatforms.profileForSlug("fbneo")
        val mame = MappingPlatforms.profileForSlug("mame")
        listOf(fbneo, mame).forEach {
            assertEquals("Button 1", it.buttonLabels[RetroButton.B])
            assertEquals("Button 4", it.buttonLabels[RetroButton.X])
        }
        assertEquals("Button 5", fbneo.buttonLabels[RetroButton.R])
        assertEquals("Button 7", fbneo.buttonLabels[RetroButton.R2])
        assertEquals("Button 5", mame.buttonLabels[RetroButton.L])
        assertEquals("Button 7", mame.buttonLabels[RetroButton.L2])
    }

    @Test
    fun `presentation controls do not shadow a single-button hotkey`() {
        listOf("lynx", "wonderswan").forEach { slug ->
            val profile = MappingPlatforms.profileForSlug(slug)
            assertFalse(
                "$slug rotate-screen should not block hotkeys",
                RetroButton.SELECT in profile.hotkeyBlockingButtons
            )
        }
        assertFalse(RetroButton.R3 in MappingPlatforms.profileForSlug("wii").hotkeyBlockingButtons)
    }
}
