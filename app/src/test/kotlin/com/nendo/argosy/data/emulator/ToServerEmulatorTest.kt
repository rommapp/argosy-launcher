package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class ToServerEmulatorTest {

    @Test
    fun `libretro hosts send the core slug`() {
        assertEquals("snes9x", EmulatorRegistry.toServerEmulator("retroarch_64", "snes9x"))
        assertEquals("gambatte", EmulatorRegistry.toServerEmulator("builtin", "gambatte"))
        assertEquals("pcsx_rearmed", EmulatorRegistry.toServerEmulator("retroarch", "pcsx_rearmed"))
    }

    @Test
    fun `mupen gles variants normalize to the canonical slug`() {
        assertEquals("mupen64plus_next", EmulatorRegistry.toServerEmulator("retroarch_64", "mupen64plus_next_gles3"))
        assertEquals("mupen64plus_next", EmulatorRegistry.toServerEmulator("builtin", "mupen64plus_next_gles2"))
    }

    @Test
    fun `non-libretro emulators keep their own id`() {
        assertEquals("dolphin", EmulatorRegistry.toServerEmulator("dolphin", "snes9x"))
        assertEquals("citra", EmulatorRegistry.toServerEmulator("citra", null))
    }

    @Test
    fun `libretro host with no resolved core falls back to its id`() {
        assertEquals("retroarch_64", EmulatorRegistry.toServerEmulator("retroarch_64", null))
    }
}
