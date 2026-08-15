package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceCompactFooterLayoutTest {

    private val layoutState = InterfaceLayoutState(display = DisplayState())

    @Test
    fun `compact footer row sits directly after ui scale`() {
        assertEquals(
            InterfaceItem.ALL.indexOf(InterfaceItem.UiScale) + 1,
            InterfaceItem.ALL.indexOf(InterfaceItem.CompactFooter)
        )
    }

    @Test
    fun `compact footer row is focusable and reachable at its focus index`() {
        assertTrue(InterfaceItem.CompactFooter.isFocusable)
        val index = interfaceFocusIndexOf(InterfaceItem.CompactFooter, layoutState)
        assertEquals(1, index)
        assertEquals(InterfaceItem.CompactFooter, interfaceItemAtFocusIndex(index, layoutState))
    }

    @Test
    fun `the rows after compact footer keep their order behind it`() {
        val compactIndex = interfaceFocusIndexOf(InterfaceItem.CompactFooter, layoutState)
        val laterRows = listOf(
            InterfaceItem.HomeScreen,
            InterfaceItem.LibraryView,
            InterfaceItem.BoxArt
        ).map { interfaceFocusIndexOf(it, layoutState) }

        assertTrue("all sit after compact footer", laterRows.all { it > compactIndex })
        assertEquals("and in the order they are declared", laterRows.sorted(), laterRows)
    }

    @Test
    fun `every visible interface row stays reachable by focus index`() {
        val visible = InterfaceItem.ALL.filter { it.visibleWhen(layoutState) && it.isFocusable }
        assertEquals(visible.size - 1, interfaceMaxFocusIndex(layoutState))
        visible.forEach { item ->
            val index = interfaceFocusIndexOf(item, layoutState)
            assertEquals(item, interfaceItemAtFocusIndex(index, layoutState))
        }
    }

    @Test
    fun `the controller grip submenu row is reachable like its siblings`() {
        val index = interfaceFocusIndexOf(InterfaceItem.ControllerGrip, layoutState)
        assertTrue(index > 0)
        assertEquals(InterfaceItem.ControllerGrip, interfaceItemAtFocusIndex(index, layoutState))
    }
}
