package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.ui.screens.settings.DisplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceGripReserveLayoutTest {

    private fun display(mode: GripReserveMode) = DisplayState(gripReserveMode = mode)

    private fun focusableItems(mode: GripReserveMode): List<ControllerGripItem?> {
        val state = display(mode)
        return (0..controllerGripMaxFocusIndex(state)).map { controllerGripItemAtFocusIndex(it, state) }
    }

    @Test
    fun `off shows the mode row alone`() {
        val items = focusableItems(GripReserveMode.OFF)

        assertEquals(listOf<ControllerGripItem?>(ControllerGripItem.Mode), items)
    }

    @Test
    fun `the reserved height row appears for on and auto but never for off`() {
        assertFalse(focusableItems(GripReserveMode.OFF).contains(ControllerGripItem.ReservedHeight))
        assertTrue(focusableItems(GripReserveMode.ON).contains(ControllerGripItem.ReservedHeight))
        assertTrue(focusableItems(GripReserveMode.AUTO).contains(ControllerGripItem.ReservedHeight))
    }

    @Test
    fun `the controllers row appears only for auto`() {
        assertFalse(focusableItems(GripReserveMode.OFF).contains(ControllerGripItem.Controllers))
        assertFalse(focusableItems(GripReserveMode.ON).contains(ControllerGripItem.Controllers))
        assertTrue(focusableItems(GripReserveMode.AUTO).contains(ControllerGripItem.Controllers))
    }

    @Test
    fun `the mode row is always reachable`() {
        GripReserveMode.entries.forEach { mode ->
            assertTrue(focusableItems(mode).contains(ControllerGripItem.Mode))
        }
    }

    @Test
    fun `auto adds exactly one row over on`() {
        assertEquals(
            controllerGripMaxFocusIndex(display(GripReserveMode.ON)) + 1,
            controllerGripMaxFocusIndex(display(GripReserveMode.AUTO))
        )
    }

    @Test
    fun `the reserved height row shifts up when the controllers row is hidden`() {
        assertEquals(
            controllerGripFocusIndexOf(ControllerGripItem.ReservedHeight, display(GripReserveMode.AUTO)) - 1,
            controllerGripFocusIndexOf(ControllerGripItem.ReservedHeight, display(GripReserveMode.ON))
        )
    }
}
