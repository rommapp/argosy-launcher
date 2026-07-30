package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `parseSlotNumber inverts fileName for the discoverable slots`() {
        val rom = "Sonic Advance"
        for (slot in listOf(LibretroStateSlots.AUTO_SLOT, 0, 1, 5, LibretroStateSlots.MAX_SLOT)) {
            assertEquals(slot, LibretroStateSlots.parseSlotNumber(rom, LibretroStateSlots.fileName(rom, slot)))
        }
        // Multi-digit numbered slots parse back to their value.
        assertEquals(12, LibretroStateSlots.parseSlotNumber(rom, "$rom.state12"))
    }

    @Test
    fun `parseSlotNumber ignores the live-only resume and quick-ring states`() {
        val rom = "Sonic Advance"
        assertNull(LibretroStateSlots.parseSlotNumber(rom, LibretroStateSlots.fileName(rom, LibretroStateSlots.RESUME_SLOT)))
        assertNull(LibretroStateSlots.parseSlotNumber(rom, LibretroStateSlots.fileName(rom, LibretroStateSlots.QUICK_SLOT_BASE)))
        assertNull(
            LibretroStateSlots.parseSlotNumber(
                rom,
                LibretroStateSlots.fileName(rom, LibretroStateSlots.QUICK_SLOT_BASE + LibretroStateSlots.QUICK_RING_SIZE - 1)
            )
        )
    }

    @Test
    fun `parseSlotNumber rejects files that do not belong to the rom`() {
        val rom = "Sonic Advance"
        assertNull(LibretroStateSlots.parseSlotNumber(rom, "Sonic Advance 2.state"))
        assertNull(LibretroStateSlots.parseSlotNumber(rom, "$rom.srm"))
        assertNull(LibretroStateSlots.parseSlotNumber(rom, "$rom.state.png"))
        assertNull(LibretroStateSlots.parseSlotNumber(rom, "OtherGame.state1"))
    }

    @Test
    fun `parseSlotNumber matches the rom name case-insensitively`() {
        assertEquals(0, LibretroStateSlots.parseSlotNumber("Sonic Advance", "sonic advance.state"))
        assertEquals(LibretroStateSlots.AUTO_SLOT, LibretroStateSlots.parseSlotNumber("Sonic Advance", "SONIC ADVANCE.state.auto"))
    }
}
