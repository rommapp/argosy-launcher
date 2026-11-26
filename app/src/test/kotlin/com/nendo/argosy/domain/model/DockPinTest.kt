package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BOTTOM = "local:bottom"
private const val TOP = "local:top"

class DockPinTest {

    private val dualScreen = ScreenLayout(mapOf(BOTTOM to ScreenRole.PRIMARY, TOP to ScreenRole.PRESENTATION))

    @Test
    fun `the control pin keeps the dock on the control screen in every arrangement`() {
        listOf(true, false).forEach { presentationUp ->
            listOf(dualScreen.presentationKey, null).forEach { key ->
                assertFalse(DockPin.CONTROL.drawsOnPresentation(presentationUp, key))
            }
        }
    }

    @Test
    fun `the presentation pin draws there while the presentation surface is up`() {
        assertTrue(DockPin.PRESENTATION.drawsOnPresentation(true, dualScreen.presentationKey))
    }

    @Test
    fun `the presentation pin follows the role across a swap`() {
        val swapped = dualScreen.withRole(TOP, ScreenRole.PRIMARY)
        assertEquals(BOTTOM, swapped.presentationKey)
        assertTrue(DockPin.PRESENTATION.drawsOnPresentation(true, swapped.presentationKey))
    }

    @Test
    fun `the presentation pin falls back to the control screen while the presentation surface is down`() {
        assertFalse(DockPin.PRESENTATION.drawsOnPresentation(false, dualScreen.presentationKey))
    }

    @Test
    fun `the presentation pin falls back to the control screen with no presentation screen`() {
        val single = ScreenLayout(mapOf(BOTTOM to ScreenRole.PRIMARY, TOP to ScreenRole.OFF))
        assertFalse(DockPin.PRESENTATION.drawsOnPresentation(true, single.presentationKey))
        assertFalse(DockPin.PRESENTATION.drawsOnPresentation(false, null))
    }

    @Test
    fun `every pin survives its token`() {
        DockPin.entries.forEach { pin -> assertEquals(pin, DockPin.fromToken(pin.token)) }
    }

    @Test
    fun `an unreadable token reads as the control pin`() {
        assertEquals(DockPin.CONTROL, DockPin.fromToken(null))
        assertEquals(DockPin.CONTROL, DockPin.fromToken(""))
        assertEquals(DockPin.CONTROL, DockPin.fromToken("screen:local:top"))
    }
}
