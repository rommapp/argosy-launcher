package com.nendo.argosy.data.emulator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two questions, deliberately different answers. Asking whether an emulator may be handed BIOS
 * is open, so a user can install files for a platform nobody enumerated. Asking whether the app
 * may write or sweep on its own is not, because those directories belong to the user.
 */
class BiosPathConfigScopeTest {

    private val retroArch = BiosPathConfig(
        emulatorId = "retroarch",
        defaultPaths = listOf("/sdcard/RetroArch/system"),
        supportedPlatforms = emptySet(),
        acceptsAnyPlatform = true
    )

    private val builtin = BiosPathConfig(
        emulatorId = "argosy.builtin.libretro",
        defaultPaths = emptyList(),
        supportedPlatforms = emptySet(),
        acceptsAnyPlatform = true
    )

    private val declared = BiosPathConfig(
        emulatorId = "duckstation",
        defaultPaths = listOf("/sdcard/duckstation/bios"),
        supportedPlatforms = setOf("psx")
    )

    @Test
    fun `an open emulator may be handed any platform when asked`() {
        assertTrue(retroArch.supports("coleco"))
    }

    /**
     * RetroArch's system directory is the user's, shared with their own BIOS. Fanning every
     * platform into it unprompted, or sweeping it on a platform toggle, reaches files Argosy
     * never wrote.
     */
    @Test
    fun `an open emulator with a user directory is not written unprompted`() {
        assertFalse(retroArch.actsUnprompted("coleco"))
    }

    @Test
    fun `the app's own directory is written unprompted for any platform`() {
        assertTrue(builtin.supports("coleco"))
        assertTrue(builtin.actsUnprompted("coleco"))
    }

    @Test
    fun `a declared platform is written unprompted`() {
        assertTrue(declared.supports("psx"))
        assertTrue(declared.actsUnprompted("psx"))
    }

    @Test
    fun `an undeclared platform reaches a closed emulator by neither route`() {
        assertFalse(declared.supports("coleco"))
        assertFalse(declared.actsUnprompted("coleco"))
    }
}
