package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
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
    fun `a SAVE_SYNC block in the heartbeat means the server speaks snapshots`() {
        val adapter = com.squareup.moshi.Moshi.Builder().build().adapter(RomMHeartbeatResponse::class.java)
        val alpha2 = adapter.fromJson(
            """{"SYSTEM":{"VERSION":"5.5.0-alpha.2"},"SAVE_SYNC":{"SNAPSHOT_RETENTION":10,"SNAPSHOT_BRANCH_LIFETIME_DAYS":30}}"""
        )
        val flagged = adapter.fromJson("""{"SYSTEM":{"VERSION":"5.5.0"},"SAVE_SYNC":{"SNAPSHOTS":true,"SNAPSHOT_RETENTION":10}}""")
        val absent = adapter.fromJson("""{"SYSTEM":{"VERSION":"5.4.1"}}""")
        assertTrue(alpha2!!.snapshotsEnabled)
        assertTrue(flagged!!.snapshotsEnabled)
        assertFalse(absent!!.snapshotsEnabled)
    }

    @Test
    fun `snapshots follow the heartbeat flag, not the version`() {
        assertTrue(RomMCapabilities.from("5.4.0", snapshotsEnabled = true).supportsSnapshots)
        assertTrue(RomMCapabilities.from("development", snapshotsEnabled = true).supportsSnapshots)
        assertFalse(RomMCapabilities.from("6.0.0", snapshotsEnabled = false).supportsSnapshots)
        assertFalse(RomMCapabilities.from("6.0.0").supportsSnapshots)
        assertFalse("disconnected", RomMCapabilities.NONE.supportsSnapshots)
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

    @Test
    fun `5_3_9 is below the device install gate`() {
        assertFalse(RomMCapabilities.from("5.3.9").supportsDeviceInstall)
    }

    @Test
    fun `5_4_0 and 5_4_1 support device install`() {
        for (version in listOf("5.4.0", "5.4.1")) {
            assertTrue(version, RomMCapabilities.from(version).supportsDeviceInstall)
        }
    }

    @Test
    fun `an unreadable version does not support device install`() {
        assertFalse(RomMCapabilities.from(null).supportsDeviceInstall)
        assertFalse(RomMCapabilities.from("unknown").supportsDeviceInstall)
    }

    @Test
    fun `a development server passes the device install gate in a debug build`() {
        assumeTrue(BuildConfig.DEBUG)
        val caps = RomMCapabilities.from("development")
        assertTrue(caps.supportsDeviceInstall)
        assertTrue(caps.isSupportedVersion)
    }

    @Test
    fun `a release build treats a development server as unversioned`() {
        assumeFalse(BuildConfig.DEBUG)
        assertEquals("development", RomMCapabilities.comparableVersion("development"))
        assertFalse(RomMCapabilities.from("development").supportsDeviceInstall)
    }

    @Test
    fun `a development server keeps its raw version string`() {
        assumeTrue(BuildConfig.DEBUG)
        val caps = RomMCapabilities.from("development")
        assertEquals("development", caps.serverVersion)
        assertNotEquals("development", RomMCapabilities.comparableVersion("development"))
    }

    @Test
    fun `a release version compares as itself`() {
        assertEquals("5.3.9", RomMCapabilities.comparableVersion("5.3.9"))
        assertEquals("5.4.0", RomMCapabilities.from("5.4.0").serverVersion)
    }
}
