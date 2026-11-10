package com.nendo.argosy.libretro.slot

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlotRegistryTest {

    private fun slot(index: Int, platform: String) = LiveSlot(
        slotIndex = index,
        pid = 1000 + index,
        taskId = 2000 + index,
        gameId = index.toLong(),
        variantFileId = null,
        platformSlug = platform,
        startedAtMillis = 0L,
        hardcore = false
    )

    private fun registryWith(vararg slots: LiveSlot) = SlotRegistry().apply {
        slots.forEach { put(it, mockk(relaxed = true)) }
    }

    @Test
    fun `heavy platforms weigh two and the rest one, aliases included`() {
        assertEquals(2, GameSlots.weightOf("ngc"))
        assertEquals(2, GameSlots.weightOf("ps2"))
        assertEquals(2, GameSlots.weightOf("dc"))
        assertEquals(1, GameSlots.weightOf("snes"))
        assertEquals(1, GameSlots.weightOf(null))
    }

    @Test
    fun `an empty pool hands out the first slot`() {
        assertEquals(1, SlotRegistry().freeSlotFor("snes"))
    }

    @Test
    fun `the lowest free slot is reused after a game closes`() {
        val registry = registryWith(slot(1, "snes"), slot(2, "nes"), slot(3, "gba"))
        registry.remove(2)
        assertEquals(2, registry.freeSlotFor("snes"))
    }

    @Test
    fun `five light games fill the pool`() {
        val registry = registryWith(slot(1, "snes"), slot(2, "nes"), slot(3, "gba"), slot(4, "gb"), slot(5, "genesis"))
        assertNull(registry.freeSlotFor("snes"))
    }

    @Test
    fun `a heavy game needs two free units`() {
        val registry = registryWith(slot(1, "snes"), slot(2, "nes"), slot(3, "gba"), slot(4, "gb"))
        assertEquals(5, registry.freeSlotFor("snes"))
        assertNull(registry.freeSlotFor("gc"))
    }

    @Test
    fun `two heavy games and one light one fill the pool`() {
        val registry = registryWith(slot(1, "gc"), slot(2, "ps2"))
        assertEquals(3, registry.freeSlotFor("snes"))
        assertNull(registryWith(slot(1, "gc"), slot(2, "ps2"), slot(3, "snes")).freeSlotFor("nes"))
    }
}
