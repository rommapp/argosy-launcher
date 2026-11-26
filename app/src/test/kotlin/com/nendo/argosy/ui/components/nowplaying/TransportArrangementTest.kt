package com.nendo.argosy.ui.components.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportArrangementTest {

    private val sevenButtons = 6 * 48 + 64
    private val floor = 8
    private val cap = 24

    @Test
    fun `seven buttons fit 400dp at the floor gap`() {
        assertEquals(floor, transportGapPx(400, sevenButtons, 7, floor, cap))
        assertEquals(400, sevenButtons + floor * 6)
    }

    @Test
    fun `the gap grows with the room between the floor and the cap`() {
        assertEquals(22, transportGapPx(488, sevenButtons, 7, floor, cap))
    }

    @Test
    fun `a wide row stops spreading at the cap`() {
        assertEquals(cap, transportGapPx(960, sevenButtons, 7, floor, cap))
    }

    @Test
    fun `a row too narrow for the floor still keeps the floor`() {
        assertEquals(floor, transportGapPx(300, sevenButtons, 7, floor, cap))
    }

    @Test
    fun `a single button has no gap`() {
        assertEquals(0, transportGapPx(400, 64, 1, floor, cap))
    }
}
