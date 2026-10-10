package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerTypeSelectionTest {

    @Test
    fun `round trips a multi port selection for one core`() {
        val selections = mapOf(0 to 769, 2 to 1)
        val encoded = ControllerTypeSelection.update(null, "dolphin", legacyApplies = true, selections)
        assertEquals("dolphin:0:769,dolphin:2:1", encoded)
        assertEquals(selections, ControllerTypeSelection.decode(encoded, "dolphin", legacyApplies = true))
    }

    @Test
    fun `a choice made on one core never reaches another core of the platform`() {
        val encoded = ControllerTypeSelection.update(null, "pcsx_rearmed", legacyApplies = false, mapOf(0 to 517))

        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode(encoded, "mednafen_psx_hw", legacyApplies = false))
        assertEquals(mapOf(0 to 517), ControllerTypeSelection.decode(encoded, "pcsx_rearmed", legacyApplies = false))
    }

    @Test
    fun `writing one core keeps every other core's entries`() {
        val pcsx = ControllerTypeSelection.update(null, "pcsx_rearmed", legacyApplies = false, mapOf(0 to 517))
        val both = ControllerTypeSelection.update(pcsx, "mednafen_psx_hw", legacyApplies = false, mapOf(1 to 1))

        assertEquals(mapOf(0 to 517), ControllerTypeSelection.decode(both, "pcsx_rearmed", legacyApplies = false))
        assertEquals(mapOf(1 to 1), ControllerTypeSelection.decode(both, "mednafen_psx_hw", legacyApplies = false))
    }

    @Test
    fun `untagged entries belong to a platform's only core and are folded in on the next write`() {
        val legacy = "0:769"

        assertEquals(mapOf(0 to 769), ControllerTypeSelection.decode(legacy, "dolphin", legacyApplies = true))
        assertEquals(
            "dolphin:0:769,dolphin:1:1",
            ControllerTypeSelection.update(legacy, "dolphin", legacyApplies = true, mapOf(0 to 769, 1 to 1))
        )
    }

    @Test
    fun `untagged entries are ignored and kept on a platform with several cores`() {
        val legacy = "0:517"

        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode(legacy, "mednafen_psx_hw", legacyApplies = false))
        assertEquals(
            "0:517,mednafen_psx_hw:0:1",
            ControllerTypeSelection.update(legacy, "mednafen_psx_hw", legacyApplies = false, mapOf(0 to 1))
        )
    }

    @Test
    fun `clearing the only core's choices clears the column`() {
        assertNull(ControllerTypeSelection.update("dolphin:0:769", "dolphin", legacyApplies = true, emptyMap()))
    }

    @Test
    fun `decodes null and blank as no selection`() {
        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode(null, "dolphin", legacyApplies = true))
        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode("  ", "dolphin", legacyApplies = true))
    }

    @Test
    fun `keeps the readable entries of a partly malformed value`() {
        assertEquals(
            mapOf(1 to 4, 3 to 5),
            ControllerTypeSelection.decode("junk,1:4,2:,:9,-1:3,dolphin:3:5,:3:6", "dolphin", legacyApplies = true)
        )
    }
}
