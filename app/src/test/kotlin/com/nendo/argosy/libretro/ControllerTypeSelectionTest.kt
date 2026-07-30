package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerTypeSelectionTest {

    @Test
    fun `round trips a multi port selection`() {
        val selections = mapOf(0 to 769, 2 to 1)
        val encoded = ControllerTypeSelection.encode(selections)
        assertEquals("0:769,2:1", encoded)
        assertEquals(selections, ControllerTypeSelection.decode(encoded))
    }

    @Test
    fun `encodes an empty selection as null so the column clears`() {
        assertNull(ControllerTypeSelection.encode(emptyMap()))
    }

    @Test
    fun `decodes null and blank as no selection`() {
        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode(null))
        assertEquals(emptyMap<Int, Int>(), ControllerTypeSelection.decode("  "))
    }

    @Test
    fun `keeps the readable pairs of a partly malformed value`() {
        assertEquals(
            mapOf(1 to 4),
            ControllerTypeSelection.decode("junk,1:4,2:,:9,-1:3")
        )
    }
}
