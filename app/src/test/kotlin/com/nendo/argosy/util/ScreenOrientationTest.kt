package com.nendo.argosy.util

import android.content.pm.ActivityInfo
import com.nendo.argosy.data.preferences.ScreenOrientationMode
import com.nendo.argosy.ui.components.QuickSettingsItem
import com.nendo.argosy.ui.components.QuickSettingsPage
import com.nendo.argosy.ui.components.QuickSettingsState
import com.nendo.argosy.ui.components.quickSettingsFocusableItems
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenOrientationTest {

    @Test
    fun `auto leaves the window to its own default`() {
        assertNull(ScreenOrientationMode.AUTO.fixedRequest())
    }

    @Test
    fun `fixed modes follow the sensor within their axis`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, ScreenOrientationMode.LANDSCAPE.fixedRequest())
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, ScreenOrientationMode.PORTRAIT.fixedRequest())
    }

    @Test
    fun `a stored token reads back as its mode and anything else as auto`() {
        ScreenOrientationMode.entries.forEach { assertEquals(it, ScreenOrientationMode.fromString(it.name)) }
        assertEquals(ScreenOrientationMode.AUTO, ScreenOrientationMode.fromString(null))
        assertEquals(ScreenOrientationMode.AUTO, ScreenOrientationMode.fromString("sideways"))
    }

    @Test
    fun `the interface orientation row closes the general group and is reachable`() {
        val layoutState = InterfaceLayoutState(display = DisplayState())
        val index = interfaceFocusIndexOf(InterfaceItem.ScreenOrientation, layoutState)
        assertEquals(interfaceFocusIndexOf(InterfaceItem.ControllerGrip, layoutState) + 1, index)
        assertEquals(InterfaceItem.ScreenOrientation, interfaceItemAtFocusIndex(index, layoutState))
        assertEquals(InterfaceItem.ControllerGrip.section, InterfaceItem.ScreenOrientation.section)
    }

    @Test
    fun `the quick panel offers orientation on a single-screen device`() {
        assertTrue(QuickSettingsItem.ScreenOrientation in quickSettingsFocusableItems(QuickSettingsPage.QUICK, QuickSettingsState()))
    }
}
