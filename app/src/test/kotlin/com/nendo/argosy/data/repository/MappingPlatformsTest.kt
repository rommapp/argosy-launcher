package com.nendo.argosy.data.repository

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
    fun `n64 exposes Z and C-buttons mode and drops the unused thumbsticks`() {
        val n64 = MappingPlatforms.profileForSlug("n64")
        assertEquals("Z", n64.buttonLabels[RetroButton.L2])
        assertTrue(RetroButton.R2 in n64.buttons)
        assertFalse(RetroButton.L3 in n64.buttons)
        assertFalse(RetroButton.R3 in n64.buttons)
        assertEquals("A", n64.buttonLabels[RetroButton.B])
        assertEquals("B", n64.buttonLabels[RetroButton.Y])
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
}
