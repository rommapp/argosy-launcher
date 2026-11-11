package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.R
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
import com.nendo.argosy.ui.components.connections.BluetoothStatus
import com.nendo.argosy.ui.components.connections.ConnectionsList
import com.nendo.argosy.ui.components.connections.ConnectionsRowAction
import com.nendo.argosy.ui.components.connections.QuickConnectionsController
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.QuickConnectionsRow
import com.nendo.argosy.ui.components.connections.QuickConnectionsState
import com.nendo.argosy.ui.components.connections.RadioPermission
import com.nendo.argosy.ui.components.connections.RadioTier
import com.nendo.argosy.ui.components.connections.WifiCurrentUi
import com.nendo.argosy.ui.components.connections.WifiNearbyUi
import com.nendo.argosy.ui.components.connections.WifiSavedUi
import com.nendo.argosy.ui.components.connections.WifiStatus
import com.nendo.argosy.ui.components.connections.bluetoothStatusOf
import com.nendo.argosy.ui.components.connections.quickConnectionsCanForget
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusIndex
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusableRows
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusedRow
import com.nendo.argosy.ui.components.connections.quickConnectionsRowAction
import com.nendo.argosy.ui.components.connections.quickConnectionsRows
import com.nendo.argosy.ui.components.connections.wifiStatusOf
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.input.NetworkSectionInput
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSettingsSectionTest {

    private val home = WifiCurrentUi(ssid = "perseus", signalLevel = 4, band = WifiBand.GHZ_5)
    private val cafe = WifiNearbyUi("cafe", signalLevel = 3, security = WifiSecurity.OPEN)
    private val savedHome = WifiSavedUi(networkId = 0, ssid = "perseus")
    private val savedOffice = WifiSavedUi(networkId = 1, ssid = "office")
    private val pad = BluetoothPairedUi("00:11", "Pad", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = 80)
    private val found = BluetoothNearbyUi(address = "AA:BB", name = "Pro Controller")

    private val full = QuickConnectionsState(
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
        canForgetDevice = true,
        searched = true,
        nearbyControllers = listOf(found)
    )

    private val fullBluetooth = full.copy(list = ConnectionsList.BLUETOOTH)

    private val standard = full.copy(
        wifiTier = RadioTier.STANDARD,
        bluetoothTier = RadioTier.STANDARD,
        saved = emptyList(),
        wifiPermission = RadioPermission.GRANTED,
        canForgetDevice = false,
        searched = false,
        nearbyControllers = emptyList()
    )

    private val standardBluetooth = standard.copy(list = ConnectionsList.BLUETOOTH)

    @Test
    fun `full tier wifi tab shows the tabs, the switch, saved and nearby networks`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.SavedHeader,
                QuickConnectionsRow.Saved(savedHome, connected = true, inRange = true),
                QuickConnectionsRow.Saved(savedOffice, connected = false, inRange = false),
                QuickConnectionsRow.NearbyHeader,
                QuickConnectionsRow.ScanNetworks,
                QuickConnectionsRow.Nearby(cafe)
            ),
            quickConnectionsRows(full)
        )
    }

    @Test
    fun `full tier bluetooth tab shows the tabs, the switch, paired and found devices`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.PairedHeader,
                QuickConnectionsRow.Paired(pad),
                QuickConnectionsRow.PairController,
                QuickConnectionsRow.FoundHeader,
                QuickConnectionsRow.NearbyController(found)
            ),
            quickConnectionsRows(fullBluetooth)
        )
    }

    @Test
    fun `full tier offers forget on saved and paired rows and join and pair on the rest`() {
        assertEquals(
            ConnectionsRowAction.FORGET_NETWORK,
            quickConnectionsRowAction(full, QuickConnectionsRow.Saved(savedOffice, connected = false, inRange = false))
        )
        assertEquals(ConnectionsRowAction.FORGET_DEVICE, quickConnectionsRowAction(full, QuickConnectionsRow.Paired(pad)))
        assertEquals(ConnectionsRowAction.JOIN, quickConnectionsRowAction(full, QuickConnectionsRow.Nearby(cafe)))
        assertEquals(ConnectionsRowAction.PAIR, quickConnectionsRowAction(full, QuickConnectionsRow.NearbyController(found)))
        assertEquals(null, quickConnectionsRowAction(full, QuickConnectionsRow.PairController))
        assertEquals(
            ConnectionsRowAction.STOP_SEARCH,
            quickConnectionsRowAction(full.copy(discovering = true), QuickConnectionsRow.PairController)
        )
    }

    @Test
    fun `standard tier wifi tab drops saved networks and joins outside`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.WifiToggle,
                QuickConnectionsRow.NearbyHeader,
                QuickConnectionsRow.ScanNetworks,
                QuickConnectionsRow.Nearby(cafe)
            ),
            quickConnectionsRows(standard)
        )
        assertEquals(ConnectionsRowAction.JOIN_OUTSIDE, quickConnectionsRowAction(standard, QuickConnectionsRow.Nearby(cafe)))
        assertEquals(WifiStatus.Standard, wifiStatusOf(standard))
    }

    @Test
    fun `standard tier bluetooth tab adds the forget line under pairing`() {
        assertEquals(
            listOf(
                QuickConnectionsRow.ListTabs,
                QuickConnectionsRow.BluetoothToggle,
                QuickConnectionsRow.PairedHeader,
                QuickConnectionsRow.Paired(pad),
                QuickConnectionsRow.PairController,
                QuickConnectionsRow.ForgetDeviceHint
            ),
            quickConnectionsRows(standardBluetooth)
        )
        assertFalse(quickConnectionsCanForget(standardBluetooth, QuickConnectionsRow.Paired(pad)))
        assertEquals(BluetoothStatus.Standard, bluetoothStatusOf(standardBluetooth))
    }

    @Test
    fun `without permission each tab asks in place of its list`() {
        val asking = standard.copy(wifiPermission = RadioPermission.ASKABLE, bluetoothPermission = RadioPermission.ASKABLE)

        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.WifiToggle, QuickConnectionsRow.WifiPermission),
            quickConnectionsFocusableRows(asking)
        )
        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.BluetoothToggle, QuickConnectionsRow.BluetoothPermission),
            quickConnectionsFocusableRows(asking.copy(list = ConnectionsList.BLUETOOTH))
        )
    }

    @Test
    fun `a radio switched off leaves the tabs, its switch and its note`() {
        val off = full.copy(wifiEnabled = false, bluetoothEnabled = false)

        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.WifiToggle, QuickConnectionsRow.WifiOff),
            quickConnectionsRows(off)
        )
        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.BluetoothToggle, QuickConnectionsRow.BluetoothOff),
            quickConnectionsRows(off.copy(list = ConnectionsList.BLUETOOTH))
        )
        assertEquals(1, networkSettingsSections(off).size)
    }

    @Test
    fun `the wifi tab rail holds wifi, saved networks and nearby networks`() {
        val rows = quickConnectionsRows(full)
        val sections = networkSettingsSections(full)

        assertEquals(
            listOf(QuickConnectionsRow.ListTabs, QuickConnectionsRow.SavedHeader, QuickConnectionsRow.NearbyHeader),
            sections.map { rows[it.listStartIndex] }
        )
        assertEquals(
            listOf(
                R.string.settings_network_group_wifi,
                R.string.settings_network_group_saved,
                R.string.settings_network_group_nearby
            ),
            sections.map { networkGroupTitleRes(rows[it.listStartIndex], ConnectionsList.WIFI) }
        )
        assertEquals(listOf(0, 2, 4), sections.map { it.focusStartIndex })
        assertEquals(listOf(1, 3, 5), sections.map { it.focusEndIndex })
    }

    @Test
    fun `the bluetooth tab rail holds bluetooth, paired devices and found nearby`() {
        val rows = quickConnectionsRows(fullBluetooth)
        val sections = networkSettingsSections(fullBluetooth)

        assertEquals(
            listOf(
                R.string.settings_network_group_bluetooth,
                R.string.settings_network_group_paired,
                R.string.settings_network_group_found
            ),
            sections.map { networkGroupTitleRes(rows[it.listStartIndex], ConnectionsList.BLUETOOTH) }
        )
        assertEquals(listOf(0, 2, 4), sections.map { it.focusStartIndex })
        assertEquals(listOf(1, 3, 4), sections.map { it.focusEndIndex })
    }

    @Test
    fun `a found group holding only its placeholder gets no rail section`() {
        val searching = fullBluetooth.copy(nearbyControllers = emptyList(), discovering = true)

        assertTrue(QuickConnectionsRow.FoundPlaceholder in quickConnectionsRows(searching))
        assertEquals(2, networkSettingsSections(searching).size)
    }

    @Test
    fun `the row list never changes with focus on either tab`() {
        assertEquals(quickConnectionsRows(full.withFocus(0)), quickConnectionsRows(full.withFocus(5)))
        assertEquals(quickConnectionsRows(fullBluetooth.withFocus(0)), quickConnectionsRows(fullBluetooth.withFocus(4)))
    }

    @Test
    fun `movement is claimed even where the connections handler declines it`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        controller.showList(ConnectionsList.BLUETOOTH)
        controller.setFocus(pairedIndex(controller))

        val left = input.onLeft()
        val right = input.onRight()

        assertTrue(left.handled)
        assertEquals(SoundType.SILENT, left.soundOverride)
        assertTrue(right.handled)
        assertTrue(input.onUp().handled)
        assertTrue(input.onDown().handled)
        assertTrue(input.onConfirm().handled)
    }

    @Test
    fun `the triggers switch tabs from any row and stop at either end`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        input.onDown()
        assertEquals(QuickConnectionsRow.WifiToggle, quickConnectionsFocusedRow(controller.state.value))

        assertEquals(InputResult.HANDLED, input.onNextTrigger())
        assertEquals(ConnectionsList.BLUETOOTH, controller.state.value.list)
        assertEquals(SoundType.BOUNDARY, input.onNextTrigger().soundOverride)
        assertEquals(ConnectionsList.BLUETOOTH, controller.state.value.list)

        assertEquals(InputResult.HANDLED, input.onPrevTrigger())
        assertEquals(ConnectionsList.WIFI, controller.state.value.list)
    }

    @Test
    fun `left and right on the tabs row switch tabs`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        assertEquals(QuickConnectionsRow.ListTabs, quickConnectionsFocusedRow(controller.state.value))

        assertEquals(InputResult.HANDLED, input.onRight())
        assertEquals(ConnectionsList.BLUETOOTH, controller.state.value.list)
        assertEquals(QuickConnectionsRow.ListTabs, quickConnectionsFocusedRow(controller.state.value))

        assertEquals(InputResult.HANDLED, input.onLeft())
        assertEquals(ConnectionsList.WIFI, controller.state.value.list)
    }

    @Test
    fun `down from the bluetooth switch stays inside the bluetooth tab`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        controller.showList(ConnectionsList.BLUETOOTH)

        input.onDown()
        assertEquals(QuickConnectionsRow.BluetoothToggle, quickConnectionsFocusedRow(controller.state.value))
        input.onDown()

        assertTrue(quickConnectionsFocusedRow(controller.state.value) is QuickConnectionsRow.Paired)
    }

    @Test
    fun `the bumpers jump between the open tab's groups and stop at the last one`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        controller.showList(ConnectionsList.BLUETOOTH)

        assertEquals(InputResult.HANDLED, input.onNextSection())
        assertTrue(quickConnectionsFocusedRow(controller.state.value) is QuickConnectionsRow.Paired)
        assertFalse(input.onNextSection().handled)
        assertEquals(ConnectionsList.BLUETOOTH, controller.state.value.list)
        assertEquals(InputResult.HANDLED, input.onPrevSection())
        assertEquals(0, quickConnectionsFocusIndex(controller.state.value))
        assertFalse(input.onPrevSection().handled)
    }

    @Test
    fun `back is not forwarded, so it leaves a forget hold to the settings screen`() = runTest {
        val controller = settingsController()
        val input = sectionInput(controller)
        controller.showList(ConnectionsList.BLUETOOTH)
        controller.setFocus(pairedIndex(controller))

        assertEquals(InputResult.HANDLED, input.onContextMenu())
        assertTrue(controller.forgetHold.isHeld)
        assertFalse(input.onBack().handled)
        assertTrue(controller.forgetHold.isHeld)
    }

    private fun sectionInput(controller: QuickConnectionsController): NetworkSectionInput =
        NetworkSectionInput(QuickConnectionsInputHandler(controller) { MenuWrapMode.OFF }, controller)

    private fun TestScope.settingsController(): QuickConnectionsController {
        val radio = mockk<RadioController>(relaxed = true)
        every { radio.canToggle } returns true
        every { radio.status() } returns RadioStatus(bluetoothEnabled = true)
        every { radio.hasBluetoothPermission() } returns true
        every { radio.pairedDevices() } returns listOf(
            PairedBluetoothDevice("00:11", "Pad", connected = true, kind = BluetoothDeviceKind.CONTROLLER, batteryPercent = 80)
        )
        every { radio.bluetooth.canForget } returns true
        val controller = QuickConnectionsController(radio, backgroundScope, UnconfinedTestDispatcher(testScheduler))
        controller.onPageShown()
        return controller
    }

    private fun pairedIndex(controller: QuickConnectionsController): Int {
        val index = quickConnectionsFocusableRows(controller.state.value).indexOfFirst { it is QuickConnectionsRow.Paired }
        assertTrue(index >= 0)
        return index
    }
}
