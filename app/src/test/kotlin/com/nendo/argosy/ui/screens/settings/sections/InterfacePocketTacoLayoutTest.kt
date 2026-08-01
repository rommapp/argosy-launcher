package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.ui.screens.settings.DisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfacePocketTacoLayoutTest {

    private fun layout(enabled: Boolean) =
        InterfaceLayoutState(DisplayState(pocketTacoEnabled = enabled))

    private fun focusableItems(enabled: Boolean): List<InterfaceItem?> {
        val state = layout(enabled)
        return (0..interfaceMaxFocusIndex(state)).map { interfaceItemAtFocusIndex(it, state) }
    }

    @Test
    fun `percent row is not reachable while pocket taco is off`() {
        assertFalse(focusableItems(enabled = false).contains(InterfaceItem.PocketTacoPercent))
    }

    @Test
    fun `percent row is reachable once pocket taco is on`() {
        assertTrue(focusableItems(enabled = true).contains(InterfaceItem.PocketTacoPercent))
    }

    @Test
    fun `the toggle itself is always reachable`() {
        assertTrue(focusableItems(enabled = false).contains(InterfaceItem.PocketTaco))
        assertTrue(focusableItems(enabled = true).contains(InterfaceItem.PocketTaco))
    }

    @Test
    fun `enabling pocket taco adds exactly one focusable row`() {
        assertEquals(
            interfaceMaxFocusIndex(layout(enabled = false)) + 1,
            interfaceMaxFocusIndex(layout(enabled = true))
        )
    }

    @Test
    fun `percent row sits directly beneath its toggle`() {
        val state = layout(enabled = true)
        assertEquals(
            interfaceFocusIndexOf(InterfaceItem.PocketTaco, state) + 1,
            interfaceFocusIndexOf(InterfaceItem.PocketTacoPercent, state)
        )
    }

    @Test
    fun `rows after the percent row shift up when it is hidden`() {
        assertEquals(
            interfaceFocusIndexOf(InterfaceItem.StartupView, layout(enabled = true)) - 1,
            interfaceFocusIndexOf(InterfaceItem.StartupView, layout(enabled = false))
        )
    }
}
