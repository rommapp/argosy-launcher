package com.nendo.argosy.ui.components

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.WifiSecurity
import com.nendo.argosy.ui.components.connections.BluetoothNearbyUi
import com.nendo.argosy.ui.components.connections.BluetoothPairedUi
import com.nendo.argosy.ui.components.connections.ConnectionsList
import com.nendo.argosy.ui.components.connections.QuickConnectionsController
import com.nendo.argosy.ui.components.connections.QuickConnectionsRow
import com.nendo.argosy.ui.components.connections.QuickConnectionsState
import com.nendo.argosy.ui.components.connections.WifiNearbyUi
import com.nendo.argosy.ui.components.connections.WifiSavedUi
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusIndex
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusableRows
import com.nendo.argosy.ui.components.connections.quickConnectionsRows
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsConnectionsTest {

    private val rootState = QuickConnectionsState(
        loaded = true,
        switchable = true,
        wifiEnabled = true,
        bluetoothEnabled = true,
        nearby = listOf(WifiNearbyUi("cafe", signalLevel = 3, security = WifiSecurity.OPEN)),
        saved = listOf(WifiSavedUi(networkId = 0, ssid = "perseus")),
        paired = listOf(
            BluetoothPairedUi("00:11", "Pad", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = 80)
        )
    )

    @Test
    fun `connections page is in the rail on every device`() {
        assertTrue(QuickSettingsPage.CONNECTIONS in quickSettingsVisiblePages(QuickSettingsState()))
    }

    @Test
    fun `connections page sits after performance`() {
        val order = QuickSettingsPage.entries
        assertEquals(order.indexOf(QuickSettingsPage.PERFORMANCE) + 1, order.indexOf(QuickSettingsPage.CONNECTIONS))
    }

    @Test
    fun `quick page holds no connection rows`() {
        val keys = quickSettingsVisibleItems(QuickSettingsPage.QUICK, QuickSettingsState()).map { it.key }
        assertFalse(keys.any { it == "wifi" || it == "bluetooth" })
    }

    @Test
    fun `without the root route the page shows only status rows and none take focus`() {
        val state = QuickConnectionsState(loaded = true, switchable = false, wifiEnabled = true, bluetoothEnabled = true)

        assertEquals(
            listOf(QuickConnectionsRow.WifiStatus, QuickConnectionsRow.BluetoothStatus),
            quickConnectionsRows(state)
        )
        assertTrue(quickConnectionsFocusableRows(state).isEmpty())
        assertEquals(-1, quickConnectionsFocusIndex(state))
    }

    @Test
    fun `moving focus on a page with no focusable rows stays put`() = runTest {
        val controller = QuickConnectionsController(mockk(relaxed = true), backgroundScope)

        val result = controller.moveFocus(1, MenuWrapMode.HARD_STOP)

        assertTrue(result.handled)
        assertEquals(SoundType.BOUNDARY, result.soundOverride)
        assertEquals(0, controller.state.value.focusIndex)
    }

    @Test
    fun `the wifi list shows networks and no bluetooth devices`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.Nearby(rootState.nearby.single()),
                QuickConnectionsRow.Saved(rootState.saved.single())
            ),
            quickConnectionsFocusableRows(rootState)
        )
    }

    @Test
    fun `the bluetooth list offers pairing above paired devices and no networks`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.PairController,
                QuickConnectionsRow.Paired(rootState.paired.single())
            ),
            quickConnectionsFocusableRows(rootState.copy(list = ConnectionsList.BLUETOOTH))
        )
    }

    @Test
    fun `a search lists the controllers it found between the pair row and paired devices`() {
        val found = BluetoothNearbyUi(address = "AA:BB", name = "SNES Controller")
        val state = rootState.copy(list = ConnectionsList.BLUETOOTH, searched = true, nearbyControllers = listOf(found))

        assertEquals(
            listOf(
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.PairController,
                QuickConnectionsRow.NearbyController(found),
                QuickConnectionsRow.Paired(rootState.paired.single())
            ),
            quickConnectionsFocusableRows(state)
        )
    }

    @Test
    fun `a radio switched off shows its off line in place of its list`() {
        val off = rootState.copy(wifiEnabled = false, bluetoothEnabled = false)

        assertEquals(
            QuickConnectionsRow.WifiOffPlaceholder,
            quickConnectionsRows(off).last()
        )
        assertEquals(
            QuickConnectionsRow.BluetoothOffPlaceholder,
            quickConnectionsRows(off.copy(list = ConnectionsList.BLUETOOTH)).last()
        )
    }

    @Test
    fun `the bluetooth permission request shows only on the bluetooth list`() {
        val unpermitted = rootState.copy(bluetoothPermitted = false)

        assertFalse(QuickConnectionsRow.BluetoothPermission in quickConnectionsRows(unpermitted))
        val bluetoothRows = quickConnectionsRows(unpermitted.copy(list = ConnectionsList.BLUETOOTH))
        assertTrue(QuickConnectionsRow.BluetoothPermission in bluetoothRows)
        assertFalse(bluetoothRows.any { it is QuickConnectionsRow.Paired || it == QuickConnectionsRow.PairedHeader })
    }

    @Test
    fun `focus clamps to the rows that exist`() {
        val shrunk = rootState.copy(focusIndex = 10)

        assertEquals(quickConnectionsFocusableRows(shrunk).lastIndex, quickConnectionsFocusIndex(shrunk))
    }
}
