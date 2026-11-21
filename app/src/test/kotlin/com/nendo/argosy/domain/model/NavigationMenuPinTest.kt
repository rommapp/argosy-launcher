package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BOTTOM = "local:bottom"
private const val TOP = "local:top"
private const val MONITOR = "local:monitor"
private const val UNPLUGGED = "local:unplugged"

class NavigationMenuPinTest {

    private val dualScreen = ScreenLayout(
        mapOf(BOTTOM to ScreenRole.PRIMARY, TOP to ScreenRole.PRESENTATION, MONITOR to ScreenRole.APP_TARGET)
    )

    @Test
    fun `the control pin never draws on the presentation screen`() {
        assertFalse(NavigationMenuPin.Control.drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `the presentation pin draws on whichever screen holds presentation`() {
        assertTrue(NavigationMenuPin.Presentation.drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `the presentation pin follows the role across a swap`() {
        val swapped = dualScreen.withRole(TOP, ScreenRole.PRIMARY)
        assertEquals(BOTTOM, swapped.presentationKey)
        assertTrue(NavigationMenuPin.Presentation.drawsOnPresentation(swapped.presentationKey))
    }

    @Test
    fun `a panel pin draws there while that panel holds presentation`() {
        assertTrue(NavigationMenuPin.Panel(TOP).drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `a panel pin stays on the control screen once a swap makes that panel primary`() {
        val swapped = dualScreen.withRole(TOP, ScreenRole.PRIMARY)
        assertFalse(NavigationMenuPin.Panel(TOP).drawsOnPresentation(swapped.presentationKey))
        assertTrue(NavigationMenuPin.Panel(BOTTOM).drawsOnPresentation(swapped.presentationKey))
    }

    @Test
    fun `a panel pin on the primary screen stays on the control screen`() {
        assertFalse(NavigationMenuPin.Panel(BOTTOM).drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `a panel pin on the app target stays on the control screen`() {
        assertFalse(NavigationMenuPin.Panel(MONITOR).drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `a panel pin on a screen that is off stays on the control screen`() {
        val topOff = ScreenLayout(mapOf(BOTTOM to ScreenRole.PRIMARY, TOP to ScreenRole.OFF))
        assertFalse(NavigationMenuPin.Panel(TOP).drawsOnPresentation(topOff.presentationKey))
        assertFalse(NavigationMenuPin.Presentation.drawsOnPresentation(topOff.presentationKey))
    }

    @Test
    fun `a panel pin on a detached screen stays on the control screen`() {
        assertFalse(NavigationMenuPin.Panel(UNPLUGGED).drawsOnPresentation(dualScreen.presentationKey))
    }

    @Test
    fun `with no presentation screen every pin stays on the control screen`() {
        val single = ScreenLayout(mapOf(BOTTOM to ScreenRole.PRIMARY, MONITOR to ScreenRole.APP_TARGET))
        listOf(
            NavigationMenuPin.Control,
            NavigationMenuPin.Presentation,
            NavigationMenuPin.Panel(BOTTOM),
            NavigationMenuPin.Panel(MONITOR)
        ).forEach { pin -> assertFalse(pin.drawsOnPresentation(single.presentationKey)) }
    }

    @Test
    fun `with dual screen off every pin stays on the control screen`() {
        listOf(
            NavigationMenuPin.Control,
            NavigationMenuPin.Presentation,
            NavigationMenuPin.Panel(TOP)
        ).forEach { pin -> assertFalse(pin.drawsOnPresentation(null)) }
    }

    @Test
    fun `every pin survives its token`() {
        listOf(
            NavigationMenuPin.Control,
            NavigationMenuPin.Presentation,
            NavigationMenuPin.Panel(TOP),
            NavigationMenuPin.Panel("display:3:1080x1920")
        ).forEach { pin -> assertEquals(pin, NavigationMenuPin.fromToken(pin.token)) }
    }

    @Test
    fun `an unreadable token reads as the control pin`() {
        assertEquals(NavigationMenuPin.Control, NavigationMenuPin.fromToken(null))
        assertEquals(NavigationMenuPin.Control, NavigationMenuPin.fromToken("screen:"))
        assertEquals(NavigationMenuPin.Control, NavigationMenuPin.fromToken("upper"))
    }
}
