package com.nendo.argosy.ui.components

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.PairedBluetoothDevice
import com.nendo.argosy.hardware.RadioController
import com.nendo.argosy.hardware.RadioStatus
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.hardware.WifiSecurity
import com.nendo.argosy.ui.components.connections.BluetoothNearbyUi
import com.nendo.argosy.ui.components.connections.BluetoothPairedUi
import com.nendo.argosy.ui.components.connections.ConnectionsList
import com.nendo.argosy.ui.components.connections.QuickConnectionsController
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.QuickConnectionsRow
import com.nendo.argosy.ui.components.connections.QuickConnectionsState
import com.nendo.argosy.ui.components.connections.RadioPermission
import com.nendo.argosy.ui.components.connections.RadioTier
import com.nendo.argosy.ui.components.connections.WifiCurrentUi
import com.nendo.argosy.ui.components.connections.WifiNearbyUi
import com.nendo.argosy.ui.components.connections.WifiSavedUi
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusIndex
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusableRows
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusedRow
import com.nendo.argosy.ui.components.connections.quickConnectionsRows
import com.nendo.argosy.ui.input.InputResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsConnectionsTest {

    private val home = WifiCurrentUi(ssid = "perseus", signalLevel = 4, band = WifiBand.GHZ_5)
    private val cafe = WifiNearbyUi("cafe", signalLevel = 3, security = WifiSecurity.OPEN)
    private val studio = WifiNearbyUi("studio", signalLevel = 2, security = WifiSecurity.WPA2)
    private val savedHome = WifiSavedUi(networkId = 0, ssid = "perseus")
    private val savedOffice = WifiSavedUi(networkId = 1, ssid = "office")
    private val pad = BluetoothPairedUi("00:11", "Pad", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = 80)

    private val fullState = QuickConnectionsState(
        loaded = true,
        wifiTier = RadioTier.FULL,
        bluetoothTier = RadioTier.FULL,
        wifiEnabled = true,
        wifiConnected = true,
        bluetoothEnabled = true,
        current = home,
        nearby = listOf(cafe, WifiNearbyUi("perseus", signalLevel = 4, security = WifiSecurity.WPA2)),
        lastScanAt = 1L,
        saved = listOf(savedHome, savedOffice),
        paired = listOf(pad),
        canForgetDevice = true
    )

    private val standardState = fullState.copy(
        wifiTier = RadioTier.STANDARD,
        bluetoothTier = RadioTier.STANDARD,
        saved = emptyList(),
        wifiPermission = RadioPermission.GRANTED,
        canForgetDevice = false
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
    fun `the tabs row is the first focus stop on both tabs`() {
        assertEquals(QuickConnectionsRow.ListTabs, quickConnectionsFocusedRow(fullState))
        assertEquals(
            QuickConnectionsRow.ListTabs,
            quickConnectionsFocusedRow(fullState.copy(list = ConnectionsList.BLUETOOTH))
        )
    }

    @Test
    fun `full wifi leads with saved networks, current first, then the scan row and nearby networks`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.Saved(savedHome, connected = true, inRange = true),
                QuickConnectionsRow.Saved(savedOffice, connected = false, inRange = false),
                QuickConnectionsRow.ScanNetworks,
                QuickConnectionsRow.Nearby(cafe)
            ),
            quickConnectionsFocusableRows(fullState)
        )
    }

    @Test
    fun `a connected network missing from the saved list still leads the saved group`() {
        val state = fullState.copy(saved = listOf(savedOffice))

        assertEquals(
            QuickConnectionsRow.ConnectedNetwork(home),
            quickConnectionsFocusableRows(state)[2]
        )
    }

    @Test
    fun `saved networks say nothing about range before the first scan`() {
        val row = quickConnectionsFocusableRows(fullState.copy(lastScanAt = null))
            .filterIsInstance<QuickConnectionsRow.Saved>()
            .single { it.network == savedOffice }

        assertNull(row.inRange)
    }

    @Test
    fun `full bluetooth lists paired devices, then the pair row, then found controllers`() {
        val found = BluetoothNearbyUi(address = "AA:BB", name = "SNES Controller")
        val state = fullState.copy(list = ConnectionsList.BLUETOOTH, searched = true, nearbyControllers = listOf(found))

        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.Paired(pad),
                QuickConnectionsRow.PairController,
                QuickConnectionsRow.NearbyController(found)
            ),
            quickConnectionsFocusableRows(state)
        )
    }

    @Test
    fun `standard wifi has no saved group and keeps nearby networks focusable`() {
        val state = standardState.copy(nearby = listOf(cafe, studio))

        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.ScanNetworks,
                QuickConnectionsRow.Nearby(cafe),
                QuickConnectionsRow.Nearby(studio)
            ),
            quickConnectionsFocusableRows(state)
        )
        assertFalse(QuickConnectionsRow.SavedHeader in quickConnectionsRows(state))
    }

    @Test
    fun `standard wifi without the permission asks in place of the nearby list`() {
        val state = standardState.copy(wifiPermission = RadioPermission.ASKABLE)

        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.WifiToggle, QuickConnectionsRow.WifiPermission),
            quickConnectionsFocusableRows(state)
        )
        assertEquals(
            listOf(QuickConnectionsRow.NearbyHeader, QuickConnectionsRow.WifiPermission),
            quickConnectionsRows(state).takeLast(2)
        )
        assertFalse(state.canReadCurrentSsid)
    }

    @Test
    fun `full wifi reads nearby networks without the location permission`() {
        val state = fullState.copy(wifiPermission = RadioPermission.DENIED)

        assertTrue(state.canReadNearbyWifi)
        assertTrue(QuickConnectionsRow.ScanNetworks in quickConnectionsFocusableRows(state))
    }

    @Test
    fun `standard bluetooth keeps paired devices and pairing but offers no forget`() {
        val state = standardState.copy(list = ConnectionsList.BLUETOOTH)

        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.Paired(pad),
                QuickConnectionsRow.PairController
            ),
            quickConnectionsFocusableRows(state)
        )
        assertFalse(state.canSwitchBluetoothDirectly(on = true))
        assertFalse(state.canSwitchBluetoothDirectly(on = false))
    }

    @Test
    fun `bluetooth without the permission shows one notice under the paired header`() {
        val state = fullState.copy(list = ConnectionsList.BLUETOOTH, bluetoothPermission = RadioPermission.ASKABLE)
        val rows = quickConnectionsRows(state)

        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.BluetoothToggle, QuickConnectionsRow.BluetoothPermission),
            quickConnectionsFocusableRows(state)
        )
        assertEquals(QuickConnectionsRow.PairedHeader, rows[rows.indexOf(QuickConnectionsRow.BluetoothPermission) - 1])
    }

    @Test
    fun `a radio switched off shows its empty state and only the tabs and switch take focus`() {
        val off = fullState.copy(wifiEnabled = false, bluetoothEnabled = false)

        assertEquals(QuickConnectionsRow.WifiOff, quickConnectionsRows(off).last())
        assertEquals(listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.WifiToggle), quickConnectionsFocusableRows(off))
        val bluetoothOff = off.copy(list = ConnectionsList.BLUETOOTH)
        assertEquals(QuickConnectionsRow.BluetoothOff, quickConnectionsRows(bluetoothOff).last())
        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.BluetoothToggle),
            quickConnectionsFocusableRows(bluetoothOff)
        )
    }

    @Test
    fun `the forget hint follows the paired list on a device that cannot forget, wherever focus is`() {
        val bluetooth = standardState.copy(list = ConnectionsList.BLUETOOTH)

        assertTrue(QuickConnectionsRow.ForgetDeviceHint in quickConnectionsRows(bluetooth.withFocus(2)))
        assertEquals(quickConnectionsRows(bluetooth.withFocus(2)), quickConnectionsRows(bluetooth.withFocus(1)))
        assertFalse(QuickConnectionsRow.ForgetDeviceHint in quickConnectionsRows(bluetooth.copy(canForgetDevice = true)))
        assertFalse(QuickConnectionsRow.ForgetDeviceHint in quickConnectionsRows(bluetooth.copy(paired = emptyList())))
    }

    @Test
    fun `a found controller and the paired device it becomes share one row key`() {
        val found = BluetoothNearbyUi(address = "AA:BB", name = "Pro Controller")
        val paired = BluetoothPairedUi("AA:BB", "Pro Controller", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = null)
        val searching = fullState.copy(list = ConnectionsList.BLUETOOTH, searched = true, nearbyControllers = listOf(found))
        val landed = searching.copy(paired = listOf(pad, paired))

        assertEquals(QuickConnectionsRow.NearbyController(found).key, QuickConnectionsRow.Paired(paired).key)
        val landedRows = quickConnectionsRows(landed)
        assertEquals(landedRows.size, landedRows.map { it.key }.toSet().size)
        assertTrue(QuickConnectionsRow.Paired(paired) in landedRows)
        assertFalse(landedRows.any { it is QuickConnectionsRow.NearbyController })
    }

    @Test
    fun `a nearby network and the saved one it becomes share one row key`() {
        val joined = WifiSavedUi(networkId = 2, ssid = "cafe")
        val state = fullState.copy(saved = fullState.saved + joined)
        val rows = quickConnectionsRows(state)

        assertEquals(QuickConnectionsRow.Nearby(cafe).key, QuickConnectionsRow.Saved(joined, connected = false, inRange = true).key)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        assertFalse(QuickConnectionsRow.Nearby(cafe) in rows)
    }

    @Test
    fun `each tab keeps its own focus`() {
        val wifiFocused = fullState.withFocus(3)
        val bluetooth = wifiFocused.copy(list = ConnectionsList.BLUETOOTH)

        assertEquals(0, bluetooth.focusIndex)
        assertEquals(3, bluetooth.copy(list = ConnectionsList.WIFI).focusIndex)
    }

    @Test
    fun `focus clamps to the rows that exist`() {
        val shrunk = fullState.withFocus(10)

        assertEquals(quickConnectionsFocusableRows(shrunk).lastIndex, quickConnectionsFocusIndex(shrunk))
    }

    @Test
    fun `moving focus up from the tabs row stops at the boundary`() = runTest {
        val controller = QuickConnectionsController(mockk(relaxed = true), backgroundScope)

        val result = controller.moveFocus(-1, MenuWrapMode.OFF)

        assertTrue(result.handled)
        assertEquals(SoundType.BOUNDARY, result.soundOverride)
        assertEquals(0, controller.state.value.focusIndex)
    }

    @Test
    fun `the triggers switch tabs from any row`() = runTest {
        val controller = QuickConnectionsController(mockk(relaxed = true), backgroundScope)
        val handler = QuickConnectionsInputHandler(controller) { MenuWrapMode.OFF }

        assertEquals(InputResult.HANDLED, handler.onNextTrigger())
        assertEquals(ConnectionsList.BLUETOOTH, controller.state.value.list)
        assertEquals(SoundType.BOUNDARY, handler.onNextTrigger().soundOverride)
        assertEquals(InputResult.HANDLED, handler.onPrevTrigger())
        assertEquals(ConnectionsList.WIFI, controller.state.value.list)
    }

    @Test
    fun `holding the context button arms the forget hold only on a device that can be forgotten`() = runTest {
        val forgettable = bluetoothController(canForget = true)
        val handler = QuickConnectionsInputHandler(forgettable) { MenuWrapMode.OFF }
        focusPairedRow(forgettable)

        assertEquals(InputResult.HANDLED, handler.onContextMenu())
        assertTrue(forgettable.forgetHold.isHeld)
        assertEquals(InputResult.HANDLED, handler.onBack())
        assertFalse(forgettable.forgetHold.isHeld)

        val locked = bluetoothController(canForget = false)
        val lockedHandler = QuickConnectionsInputHandler(locked) { MenuWrapMode.OFF }
        focusPairedRow(locked)

        assertFalse(lockedHandler.onContextMenu().handled)
        assertFalse(locked.forgetHold.isHeld)
    }

    @Test
    fun `moving focus releases a forget hold`() = runTest {
        val controller = bluetoothController(canForget = true)
        val handler = QuickConnectionsInputHandler(controller) { MenuWrapMode.OFF }
        focusPairedRow(controller)
        handler.onContextMenu()

        handler.onDown()

        assertFalse(controller.forgetHold.isHeld)
    }

    private fun kotlinx.coroutines.test.TestScope.bluetoothController(canForget: Boolean): QuickConnectionsController {
        val radio = mockk<RadioController>(relaxed = true)
        every { radio.canToggle } returns true
        every { radio.status() } returns RadioStatus(bluetoothEnabled = true)
        every { radio.hasBluetoothPermission() } returns true
        every { radio.pairedDevices() } returns listOf(
            PairedBluetoothDevice("00:11", "Pad", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = 80)
        )
        every { radio.bluetooth.canForget } returns canForget
        val controller = QuickConnectionsController(radio, backgroundScope, UnconfinedTestDispatcher(testScheduler))
        controller.onPageShown()
        controller.showList(ConnectionsList.BLUETOOTH)
        return controller
    }

    private fun focusPairedRow(controller: QuickConnectionsController) {
        val index = quickConnectionsFocusableRows(controller.state.value).indexOfFirst { it is QuickConnectionsRow.Paired }
        assertTrue(index >= 0)
        controller.setFocus(index)
        assertNotNull(quickConnectionsFocusedRow(controller.state.value) as? QuickConnectionsRow.Paired)
    }
}
