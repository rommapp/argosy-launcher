package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Test

class LibretroStateSlotsTest {

    @Test
    fun `slot numbers map to the canonical flat file names`() {
        val rom = "Sonic Advance"
        assertEquals("Sonic Advance.state.auto", LibretroStateSlots.fileName(rom, LibretroStateSlots.AUTO_SLOT))
        assertEquals("Sonic Advance.state.resume", LibretroStateSlots.fileName(rom, LibretroStateSlots.RESUME_SLOT))
        assertEquals("Sonic Advance.state", LibretroStateSlots.fileName(rom, 0))
        assertEquals("Sonic Advance.state5", LibretroStateSlots.fileName(rom, 5))
        assertEquals("Sonic Advance.state.q0", LibretroStateSlots.fileName(rom, LibretroStateSlots.QUICK_SLOT_BASE))
        assertEquals(
            "Sonic Advance.state.q9",
            LibretroStateSlots.fileName(rom, LibretroStateSlots.QUICK_SLOT_BASE + LibretroStateSlots.QUICK_RING_SIZE - 1)
        )
    }
}
