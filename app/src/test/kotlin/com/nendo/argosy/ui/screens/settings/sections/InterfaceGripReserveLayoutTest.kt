package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.ui.screens.settings.DisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceGripReserveLayoutTest {

    private fun layout(enabled: Boolean) =
        InterfaceLayoutState(DisplayState(gripReserveEnabled = enabled))

    private fun focusableItems(enabled: Boolean): List<InterfaceItem?> {
        val state = layout(enabled)
        return (0..interfaceMaxFocusIndex(state)).map { interfaceItemAtFocusIndex(it, state) }
    }

    @Test
    fun `percent row is not reachable while the grip reserve is off`() {
        assertFalse(focusableItems(enabled = false).contains(InterfaceItem.GripReservePercent))
    }

    @Test
    fun `percent row is reachable once the grip reserve is on`() {
        assertTrue(focusableItems(enabled = true).contains(InterfaceItem.GripReservePercent))
    }

    @Test
    fun `the toggle itself is always reachable`() {
        assertTrue(focusableItems(enabled = false).contains(InterfaceItem.GripReserve))
        assertTrue(focusableItems(enabled = true).contains(InterfaceItem.GripReserve))
    }

    @Test
    fun `enabling the grip reserve adds exactly one focusable row`() {
        assertEquals(
            interfaceMaxFocusIndex(layout(enabled = false)) + 1,
            interfaceMaxFocusIndex(layout(enabled = true))
        )
    }

    @Test
    fun `percent row sits directly beneath its toggle`() {
        val state = layout(enabled = true)
        assertEquals(
            interfaceFocusIndexOf(InterfaceItem.GripReserve, state) + 1,
            interfaceFocusIndexOf(InterfaceItem.GripReservePercent, state)
        )
    }

    @Test
    fun `rows after the percent row shift up when it is hidden`() {
        assertEquals(
            interfaceFocusIndexOf(InterfaceItem.HomeScreen, layout(enabled = true)) - 1,
            interfaceFocusIndexOf(InterfaceItem.HomeScreen, layout(enabled = false))
        )
    }
}
