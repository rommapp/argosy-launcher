package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(interfaceFocusIndexOf(InterfaceItem.UiScale, layoutState) + 1, index)
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
    fun `presentation shows only while dual screen has a second display`() {
        val off = InterfaceLayoutState(display = DisplayState(dualScreenEnabled = false, hasSecondaryDisplay = true))
        val noScreen = InterfaceLayoutState(display = DisplayState(dualScreenEnabled = true, hasSecondaryDisplay = false))
        val on = InterfaceLayoutState(display = DisplayState(dualScreenEnabled = true, hasSecondaryDisplay = true))

        assertFalse(InterfaceItem.Presentation.visibleWhen(off))
        assertFalse(InterfaceItem.Presentation.visibleWhen(noScreen))
        assertTrue(InterfaceItem.Presentation.visibleWhen(on))
        val index = interfaceFocusIndexOf(InterfaceItem.Presentation, on)
        assertEquals(InterfaceItem.Presentation, interfaceItemAtFocusIndex(index, on))
    }

    @Test
    fun `the screen reads general, then screens, then the status bar, then recaps, then notifications`() {
        val order = listOf(
            InterfaceItem.Language,
            InterfaceItem.ControllerGrip,
            InterfaceItem.HomeScreen,
            InterfaceItem.BoxArt,
            InterfaceItem.StatusClock,
            InterfaceItem.StatusNetwork,
            InterfaceItem.PreLaunchRecaps,
            InterfaceItem.MonthlyRecap,
            InterfaceItem.Notifications,
            InterfaceItem.NotifyDownloadsFinished,
            InterfaceItem.NotifyBattery,
            InterfaceItem.NotifyCharging,
            InterfaceItem.NotifyControllers
        ).map { interfaceFocusIndexOf(it, layoutState) }

        assertEquals(order.sorted(), order)
        assertEquals(5, interfaceSections(layoutState).size)
    }

    @Test
    fun `the downloads, charging and controller rows show only while notifications are on, battery always`() {
        val off = InterfaceLayoutState(display = DisplayState(notificationsEnabled = false))

        assertTrue(InterfaceItem.NotifyDownloadsFinished.visibleWhen(layoutState))
        assertFalse(InterfaceItem.NotifyDownloadsFinished.visibleWhen(off))
        assertTrue(InterfaceItem.NotifyCharging.visibleWhen(layoutState))
        assertFalse(InterfaceItem.NotifyCharging.visibleWhen(off))
        assertTrue(InterfaceItem.NotifyControllers.visibleWhen(layoutState))
        assertFalse(InterfaceItem.NotifyControllers.visibleWhen(off))
        assertTrue(InterfaceItem.NotifyBattery.visibleWhen(layoutState))
        assertTrue(InterfaceItem.NotifyBattery.visibleWhen(off))
        assertEquals(interfaceMaxFocusIndex(layoutState) - 3, interfaceMaxFocusIndex(off))
        val batteryIndex = interfaceFocusIndexOf(InterfaceItem.NotifyBattery, off)
        assertEquals(InterfaceItem.NotifyBattery, interfaceItemAtFocusIndex(batteryIndex, off))
    }

    @Test
    fun `the system bar rows follow compact footer in the general group`() {
        val order = listOf(
            InterfaceItem.CompactFooter,
            InterfaceItem.HideSystemNavigationBar,
            InterfaceItem.HideSystemStatusBar,
            InterfaceItem.QuickPanelInEmulators,
            InterfaceItem.ControllerGrip
        ).map { interfaceFocusIndexOf(it, layoutState) }

        assertEquals(order.first() + 1, order[1])
        assertEquals(order[1] + 1, order[2])
        assertEquals(order[2] + 1, order[3])
        assertEquals(order[3] + 1, order[4])
        assertEquals(InterfaceItem.CompactFooter.section, InterfaceItem.HideSystemNavigationBar.section)
        assertEquals(InterfaceItem.CompactFooter.section, InterfaceItem.HideSystemStatusBar.section)
        assertEquals(InterfaceItem.CompactFooter.section, InterfaceItem.QuickPanelInEmulators.section)
    }

    @Test
    fun `the controller grip submenu row is reachable like its siblings`() {
        val index = interfaceFocusIndexOf(InterfaceItem.ControllerGrip, layoutState)
        assertTrue(index > 0)
        assertEquals(InterfaceItem.ControllerGrip, interfaceItemAtFocusIndex(index, layoutState))
    }
}
