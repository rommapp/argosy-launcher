package com.nendo.argosy.ui.components.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSizingTest {

    @Test
    fun `the Nova docks a compact sheet of 32 dp keys at 47_5 percent`() {
        val sizing = keyboardSizing(640f, 480f, forceFull = false)
        assertEquals(KeyboardPlacement.DOCKED, sizing.placement)
        assertTrue(sizing.compact)
        assertEquals(32f, sizing.keyHeightDp, 0f)
        assertEquals(40f, sizing.echoHeightDp, 0f)
        assertEquals(228f, sizing.sheetHeightDp, 0f)
        assertEquals(0.475f, sizing.sheetHeightDp / 480f, 0.001f)
        assertEquals(640f, sizing.sheetWidthDp, 0f)
    }

    @Test
    fun `the Odin3 docks a compact sheet of 32 dp keys at 48_7 percent`() {
        val sizing = keyboardSizing(833f, 468f, forceFull = false)
        assertEquals(KeyboardPlacement.DOCKED, sizing.placement)
        assertTrue(sizing.compact)
        assertEquals(32f, sizing.keyHeightDp, 0f)
        assertEquals(0.487f, sizing.sheetHeightDp / 468f, 0.001f)
        assertEquals(833f, sizing.sheetWidthDp, 0f)
    }

    @Test
    fun `a TV docks a full-width two-line sheet of 32 dp keys at 47 percent`() {
        val sizing = keyboardSizing(960f, 540f, forceFull = false)
        assertEquals(KeyboardPlacement.DOCKED, sizing.placement)
        assertFalse(sizing.compact)
        assertEquals(32f, sizing.keyHeightDp, 0f)
        assertEquals(52f, sizing.echoHeightDp, 0f)
        assertEquals(252f, sizing.sheetHeightDp, 0f)
        assertEquals(47, Math.round(sizing.sheetHeightDp / 540f * 100f))
        assertEquals(960f, sizing.sheetWidthDp, 0f)
    }

    @Test
    fun `a portrait phone docks a sheet of 40 dp keys`() {
        val sizing = keyboardSizing(412f, 915f, forceFull = false)
        assertEquals(KeyboardPlacement.DOCKED, sizing.placement)
        assertFalse(sizing.compact)
        assertEquals(40f, sizing.keyHeightDp, 0f)
        assertEquals(292f, sizing.sheetHeightDp, 0f)
        assertEquals(412f, sizing.sheetWidthDp, 0f)
    }

    @Test
    fun `a 455 dp tall window gets the full-screen keyboard`() {
        val sizing = keyboardSizing(800f, 455f, forceFull = false)
        assertEquals(KeyboardPlacement.FULL_SCREEN, sizing.placement)
        assertFalse(sizing.compact)
        assertEquals(52f, sizing.keyHeightDp, 0f)
        assertEquals(112f, sizing.echoHeightDp, 0f)
        assertEquals(455f, sizing.sheetHeightDp, 0f)
        assertEquals(800f, sizing.sheetWidthDp, 0f)
    }

    @Test
    fun `a 456 dp tall window still docks at exactly half the height`() {
        val sizing = keyboardSizing(800f, 456f, forceFull = false)
        assertEquals(KeyboardPlacement.DOCKED, sizing.placement)
        assertEquals(228f, sizing.sheetHeightDp, 0f)
    }

    @Test
    fun `forcing full screen fills the window even when a docked sheet fits`() {
        val sizing = keyboardSizing(960f, 540f, forceFull = true)
        assertEquals(KeyboardPlacement.FULL_SCREEN, sizing.placement)
        assertEquals(960f, sizing.sheetWidthDp, 0f)
        assertEquals(540f, sizing.sheetHeightDp, 0f)
    }
}
