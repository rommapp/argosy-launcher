package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.DisplaysItem
import com.nendo.argosy.ui.screens.settings.sections.DisplaysLayoutState
import com.nendo.argosy.ui.screens.settings.sections.displaysFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.displaysItemAtFocusIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplaysMirrorModeTest {

    @Test
    fun `dual screen switch remains reachable while display controls are disabled`() {
        val state = DisplaysLayoutState(
            display = DisplayState(hasPhysicalSecondaryDisplay = true),
            hasPhysicalSecondaryDisplay = true
        )

        val index = displaysFocusIndexOf(DisplaysItem.DualScreenEnabled, state)

        assertEquals(DisplaysItem.DualScreenEnabled, displaysItemAtFocusIndex(index, state))
        listOf(
            DisplaysItem.DisplayRoles,
            DisplaysItem.ScreenLayout,
            DisplaysItem.GamesTarget,
            DisplaysItem.AppsTarget,
            DisplaysItem.ByPlatformAndApp
        ).forEach { assertFalse(it.visibleWhen(state)) }
    }

    @Test
    fun `display controls return when dual screen mode is enabled`() {
        val state = DisplaysLayoutState(
            display = DisplayState(
                hasSecondaryDisplay = true,
                hasPhysicalSecondaryDisplay = true,
                dualScreenEnabled = true
            ),
            hasPhysicalSecondaryDisplay = true,
            dualScreenEnabled = true
        )

        listOf(
            DisplaysItem.DisplayRoles,
            DisplaysItem.ScreenLayout,
            DisplaysItem.GamesTarget,
            DisplaysItem.AppsTarget,
            DisplaysItem.ByPlatformAndApp
        ).forEach { assertTrue(it.visibleWhen(state)) }
    }
}
