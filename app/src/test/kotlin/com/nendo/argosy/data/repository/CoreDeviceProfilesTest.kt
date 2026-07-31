package com.nendo.argosy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The device ids are Dolphin's own, transcribed from its libretro Input.cpp. A wrong id here sends
 * a port to another controller's button set, which is how the Wii mapping broke in the first place.
 */
class CoreDeviceProfilesTest {

    private val wiimote = 1
    private val wiimoteSideways = (2 shl 8) or 1
    private val wiimoteNunchuk = (3 shl 8) or 1
    private val classicController = (4 shl 8) or 1
    private val classicControllerPro = (5 shl 8) or 1

    @Test
    fun `each dolphin wii device selects its own profile`() {
        assertEquals("wii", CoreDeviceProfiles.profileIdFor("dolphin", "wii", wiimote))
        assertEquals("wii", CoreDeviceProfiles.profileIdFor("dolphin", "wii", wiimoteSideways))
        assertEquals("wii-nunchuk", CoreDeviceProfiles.profileIdFor("dolphin", "wii", wiimoteNunchuk))
        assertEquals("wii-classic", CoreDeviceProfiles.profileIdFor("dolphin", "wii", classicController))
        assertEquals("wii-classic-pro", CoreDeviceProfiles.profileIdFor("dolphin", "wii", classicControllerPro))
    }

    @Test
    fun `gamecube content is not redirected by a wii device pairing`() {
        assertNull(CoreDeviceProfiles.profileIdFor("dolphin", "gc", wiimoteNunchuk))
    }

    @Test
    fun `an unrecorded core or device keeps the platform profile`() {
        assertNull(CoreDeviceProfiles.profileIdFor("snes9x", "snes", 1))
        assertNull(CoreDeviceProfiles.profileIdFor("dolphin", "wii", 9999))
        assertNull(CoreDeviceProfiles.profileIdFor(null, "wii", wiimoteNunchuk))
        assertNull(CoreDeviceProfiles.profileIdFor("dolphin", "wii", null))
    }

    @Test
    fun `fbneo pad devices subclass analog rather than joypad`() {
        assertEquals("arcade6", CoreDeviceProfiles.profileIdFor("fbneo", "fbneo", 5))
        assertEquals("arcade-6panel", CoreDeviceProfiles.profileIdFor("fbneo", "fbneo", (1 shl 8) or 5))
        assertEquals("arcade-modern", CoreDeviceProfiles.profileIdFor("fbneo", "fbneo", (2 shl 8) or 5))
    }

    @Test
    fun `mame pad devices subclass joypad`() {
        assertEquals("arcade-mame", CoreDeviceProfiles.profileIdFor("mame2003_plus", "mame", 1))
        assertEquals(
            "arcade-mame-fightstick",
            CoreDeviceProfiles.profileIdFor("mame2003_plus", "mame", (1 shl 8) or 1)
        )
        assertEquals(
            "arcade-mame-8button",
            CoreDeviceProfiles.profileIdFor("mame2003_plus", "mame", (2 shl 8) or 1)
        )
        assertEquals(
            "arcade-mame-6button",
            CoreDeviceProfiles.profileIdFor("mame2003_plus", "mame", (3 shl 8) or 1)
        )
    }

    @Test
    fun `saturn control pad is plain joypad and the 3d pad subclasses analog`() {
        assertEquals("saturn", CoreDeviceProfiles.profileIdFor("mednafen_saturn", "saturn", 1))
        assertEquals(
            "saturn-3d",
            CoreDeviceProfiles.profileIdFor("mednafen_saturn", "saturn", (1 shl 8) or 5)
        )
    }

    @Test
    fun `the same device id means different things in different cores`() {
        val shared = (1 shl 8) or 5
        assertEquals("arcade-6panel", CoreDeviceProfiles.profileIdFor("fbneo", "fbneo", shared))
        assertEquals("saturn-3d", CoreDeviceProfiles.profileIdFor("mednafen_saturn", "saturn", shared))
    }
}
