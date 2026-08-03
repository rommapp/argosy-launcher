package com.nendo.argosy.data.remote.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomMCapabilitiesTest {

    @Test
    fun `4_8 is below the floor and gets nothing`() {
        for (version in listOf("4.8.0", "4.8.1")) {
            val caps = RomMCapabilities.from(version)
            assertFalse(version, caps.isSupportedVersion)
            assertFalse("device sync moved onto the 4.9 floor", caps.supportsDeviceSyncMode)
            assertFalse("negotiate engine is 4.9+", caps.supportsSyncNegotiate)
        }
    }

    @Test
    fun `4_9_0 is the floor and enables both device sync and negotiate`() {
        val caps = RomMCapabilities.from("4.9.0")
        assertTrue(caps.isSupportedVersion)
        assertTrue(caps.supportsDeviceSyncMode)
        assertTrue(caps.supportsSyncNegotiate)
    }

    @Test
    fun `the floor gates nothing the sync engine does not already gate`() {
        val caps = RomMCapabilities.from(RomMCapabilities.MIN_SUPPORTED_VERSION)
        assertTrue(caps.supportsSyncNegotiate)
        assertTrue(caps.supportsPlaySessionIngest)
        assertTrue(caps.supportsDeviceSyncMode)
        assertTrue(caps.trustsServerHash)
    }

    @Test
    fun `the three supported minors are all supported`() {
        for (version in listOf("4.9.2", "5.0.0", "5.1.0")) {
            assertTrue(version, RomMCapabilities.from(version).isSupportedVersion)
        }
    }

    @Test
    fun `only the 5_0 gates still discriminate above the floor`() {
        val floor = RomMCapabilities.from("4.9.2")
        assertFalse(floor.supportsDeviceAuth)
        assertFalse(floor.supportsScreenshotUpload)
        assertFalse(floor.supportsMusicApi)

        val current = RomMCapabilities.from("5.1.0")
        assertTrue(current.supportsDeviceAuth)
        assertTrue(current.supportsScreenshotUpload)
        assertTrue(current.supportsMusicApi)
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
        assertFalse(caps.isSupportedVersion)
        assertFalse(caps.supportsDeviceSyncMode)
        assertFalse(caps.supportsSyncNegotiate)
    }

    @Test
    fun `an unreadable version is not treated as supported`() {
        assertFalse(RomMCapabilities.from(null).isSupportedVersion)
        assertFalse(RomMCapabilities.from("").isSupportedVersion)
        assertFalse(RomMCapabilities.from("unknown").isSupportedVersion)
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
        assertTrue(RomMCapabilities.from("4.7.0", libretroEnabled = true).supportsLibretroThumbnails)
        assertFalse(RomMCapabilities.from("4.9.0", libretroEnabled = false).supportsLibretroThumbnails)
    }
}
