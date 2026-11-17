package com.nendo.argosy.ui.components

import com.nendo.argosy.hardware.PowerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsPowerTest {

    @Test
    fun `root wins over the accessibility service`() {
        assertEquals(PowerRoute.ROOT, PowerRoute.select(rootAvailable = true, accessibilityBound = true))
    }

    @Test
    fun `root alone selects root`() {
        assertEquals(PowerRoute.ROOT, PowerRoute.select(rootAvailable = true, accessibilityBound = false))
    }

    @Test
    fun `the accessibility service alone selects accessibility`() {
        assertEquals(PowerRoute.ACCESSIBILITY, PowerRoute.select(rootAvailable = false, accessibilityBound = true))
    }

    @Test
    fun `neither selects none`() {
        assertEquals(PowerRoute.NONE, PowerRoute.select(rootAvailable = false, accessibilityBound = false))
    }

    @Test
    fun `accessibility sleep needs the lock screen action of Android 9`() {
        assertTrue(PowerRoute.sleepAvailable(PowerRoute.ROOT, sdkInt = 26))
        assertFalse(PowerRoute.sleepAvailable(PowerRoute.ACCESSIBILITY, sdkInt = 27))
        assertTrue(PowerRoute.sleepAvailable(PowerRoute.ACCESSIBILITY, sdkInt = 28))
        assertFalse(PowerRoute.sleepAvailable(PowerRoute.NONE, sdkInt = 35))
    }

    private fun powerItems(route: PowerRoute, sleep: Boolean = route != PowerRoute.NONE): List<QuickSettingsItem> =
        quickSettingsVisibleItems(
            QuickSettingsPage.POWER,
            QuickSettingsState(powerRoute = route, powerSleepAvailable = sleep)
        ).filterNot { it is QuickSettingsItem.Header }

    @Test
    fun `root shows sleep, restart and power off`() {
        assertEquals(
            listOf(
                QuickSettingsItem.PowerSleep,
                QuickSettingsItem.PowerRestart,
                QuickSettingsItem.PowerOff,
                QuickSettingsItem.OpenGames
            ),
            powerItems(PowerRoute.ROOT)
        )
    }

    @Test
    fun `accessibility shows sleep and one row for the system power menu`() {
        assertEquals(
            listOf(QuickSettingsItem.PowerSleep, QuickSettingsItem.PowerMenu, QuickSettingsItem.OpenGames),
            powerItems(PowerRoute.ACCESSIBILITY)
        )
    }

    @Test
    fun `accessibility without the lock screen action drops sleep`() {
        assertEquals(
            listOf(QuickSettingsItem.PowerMenu, QuickSettingsItem.OpenGames),
            powerItems(PowerRoute.ACCESSIBILITY, sleep = false)
        )
    }

    @Test
    fun `no route shows the notice and the open games only`() {
        val state = QuickSettingsState(powerRoute = PowerRoute.NONE)
        assertEquals(listOf(QuickSettingsItem.PowerAccess, QuickSettingsItem.OpenGames), powerItems(PowerRoute.NONE))
        assertEquals(listOf(QuickSettingsItem.PowerAccess), quickSettingsFocusableItems(QuickSettingsPage.POWER, state))
    }

    @Test
    fun `open games never take focus`() {
        val state = QuickSettingsState(powerRoute = PowerRoute.ROOT, powerSleepAvailable = true)
        assertFalse(QuickSettingsItem.OpenGames in quickSettingsFocusableItems(QuickSettingsPage.POWER, state))
    }

    @Test
    fun `power is the last page and activity follows quick`() {
        val order = QuickSettingsPage.entries
        assertEquals(QuickSettingsPage.POWER, order.last())
        assertEquals(order.indexOf(QuickSettingsPage.QUICK) + 1, order.indexOf(QuickSettingsPage.ACTIVITY))
    }

    @Test
    fun `both pages are in the rail with nothing in progress`() {
        val pages = quickSettingsVisiblePages(QuickSettingsState())
        assertTrue(QuickSettingsPage.ACTIVITY in pages)
        assertTrue(QuickSettingsPage.POWER in pages)
    }
}
