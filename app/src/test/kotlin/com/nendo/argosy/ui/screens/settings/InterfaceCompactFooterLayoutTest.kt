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
    fun `rows after compact footer shift down by one`() {
        val compactIndex = interfaceFocusIndexOf(InterfaceItem.CompactFooter, layoutState)
        val laterRows = listOf(
            InterfaceItem.HomeScreen,
            InterfaceItem.LibraryView,
            InterfaceItem.BoxArt
        )
        laterRows.forEachIndexed { offset, item ->
            assertEquals(compactIndex + 1 + offset, interfaceFocusIndexOf(item, layoutState))
        }
    }

    @Test
    fun `every interface row stays reachable by focus index`() {
        assertEquals(InterfaceItem.ALL.size - 1, interfaceMaxFocusIndex(layoutState))
        InterfaceItem.ALL.forEach { item ->
            val index = interfaceFocusIndexOf(item, layoutState)
            assertEquals(item, interfaceItemAtFocusIndex(index, layoutState))
        }
    }
}
