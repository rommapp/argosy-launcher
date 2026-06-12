package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomMCapabilitiesTest {

    @Test
    fun `4_8_0 enables device sync but not the negotiate engine`() {
        val caps = RomMCapabilities.from("4.8.0")
        assertTrue("device sync endpoints shipped in 4.8", caps.supportsDeviceSyncMode)
        assertFalse("negotiate engine is 4.9+", caps.supportsSyncNegotiate)
    }

    @Test
    fun `4_8_1 enables device sync but not the negotiate engine`() {
        val caps = RomMCapabilities.from("4.8.1")
        assertTrue(caps.supportsDeviceSyncMode)
        assertFalse(caps.supportsSyncNegotiate)
    }

    @Test
    fun `4_9_0 enables both device sync and negotiate`() {
        val caps = RomMCapabilities.from("4.9.0")
        assertTrue(caps.supportsDeviceSyncMode)
        assertTrue(caps.supportsSyncNegotiate)
    }

    @Test
    fun `4_9_0 alpha prerelease enables both`() {
        val caps = RomMCapabilities.from("4.9.0-alpha.8")
        assertTrue(caps.supportsDeviceSyncMode)
        assertTrue(caps.supportsSyncNegotiate)
    }

    @Test
    fun `4_7_0 enables neither device sync nor negotiate`() {
        val caps = RomMCapabilities.from("4.7.0")
        assertFalse(caps.supportsDeviceSyncMode)
        assertFalse(caps.supportsSyncNegotiate)
    }

    @Test
    fun `future major enables both`() {
        val caps = RomMCapabilities.from("4.10.0")
        assertTrue(caps.supportsDeviceSyncMode)
        assertTrue(caps.supportsSyncNegotiate)
    }

    @Test
    fun `null version returns NONE with everything disabled`() {
        val caps = RomMCapabilities.from(null)
        assertEquals(RomMCapabilities.NONE, caps)
        assertFalse(caps.supportsDeviceSyncMode)
        assertFalse(caps.supportsSyncNegotiate)
    }

    @Test
    fun `blank and unknown versions return NONE`() {
        assertEquals(RomMCapabilities.NONE, RomMCapabilities.from(""))
        assertEquals(RomMCapabilities.NONE, RomMCapabilities.from("unknown"))
    }

    @Test
    fun `libretro thumbnails honors explicit flag over version default`() {
        assertTrue(RomMCapabilities.from("4.8.0", libretroEnabled = true).supportsLibretroThumbnails)
        assertFalse(RomMCapabilities.from("4.9.0", libretroEnabled = false).supportsLibretroThumbnails)
    }
}
