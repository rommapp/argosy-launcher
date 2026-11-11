package com.nendo.argosy.ui.components.connections

import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.hardware.WifiSecurity
import com.nendo.argosy.ui.components.TextEntryFocus

data class WifiCurrentUi(val ssid: String, val signalLevel: Int, val band: WifiBand?)

data class WifiNearbyUi(val ssid: String, val signalLevel: Int, val security: WifiSecurity) {
    val secured: Boolean get() = security != WifiSecurity.OPEN
}

data class WifiSavedUi(val networkId: Int, val ssid: String)

data class BluetoothPairedUi(
    val address: String,
    val name: String,
    val connected: Boolean,
    val kind: BluetoothDeviceKind,
    val batteryPercent: Int?
)

data class BluetoothNearbyUi(val address: String, val name: String)

data class WifiJoinDraft(
    val ssid: String,
    val security: WifiSecurity,
    val password: String = "",
    val focus: TextEntryFocus = TextEntryFocus(),
    val busy: Boolean = false,
    val failed: Boolean = false
) {
    val canSubmit: Boolean get() = !busy && password.length >= MIN_PASSPHRASE_LENGTH

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}

enum class ConnectionsList { WIFI, BLUETOOTH }

data class QuickConnectionsState(
    val loaded: Boolean = false,
    val switchable: Boolean = false,
    val wifiEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val current: WifiCurrentUi? = null,
    val nearby: List<WifiNearbyUi> = emptyList(),
    val scanning: Boolean = false,
    val saved: List<WifiSavedUi> = emptyList(),
    val joiningSsid: String? = null,
    val failedSsid: String? = null,
    val bluetoothPermitted: Boolean = true,
    val paired: List<BluetoothPairedUi> = emptyList(),
    val canForgetDevices: Boolean = false,
    val discovering: Boolean = false,
    val searched: Boolean = false,
    val nearbyControllers: List<BluetoothNearbyUi> = emptyList(),
    val pairingAddress: String? = null,
    val pairFailedAddress: String? = null,
    val focusIndex: Int = 0,
    val join: WifiJoinDraft? = null,
    val list: ConnectionsList = ConnectionsList.WIFI
)

sealed class QuickConnectionsRow(val key: String, val focusable: Boolean) {
    data object WifiStatus : QuickConnectionsRow("wifiStatus", focusable = false)
    data object BluetoothStatus : QuickConnectionsRow("bluetoothStatus", focusable = false)
    data object WifiToggle : QuickConnectionsRow("wifiToggle", focusable = true)
    data object ListTabs : QuickConnectionsRow("listTabs", focusable = true)
    data object WifiOffPlaceholder : QuickConnectionsRow("wifiOff", focusable = false)
    data object BluetoothOffPlaceholder : QuickConnectionsRow("bluetoothOff", focusable = false)
    data class CurrentNetwork(val network: WifiCurrentUi) : QuickConnectionsRow("wifiCurrent", focusable = false)
    data object NearbyHeader : QuickConnectionsRow("nearbyHeader", focusable = false)
    data object NearbyPlaceholder : QuickConnectionsRow("nearbyPlaceholder", focusable = false)
    data class Nearby(val network: WifiNearbyUi) : QuickConnectionsRow("nearby_${network.ssid}", focusable = true)
    data object SavedHeader : QuickConnectionsRow("savedHeader", focusable = false)
    data class Saved(val network: WifiSavedUi) : QuickConnectionsRow("saved_${network.networkId}", focusable = true)
    data object BluetoothToggle : QuickConnectionsRow("bluetoothToggle", focusable = true)
    data object BluetoothPermission : QuickConnectionsRow("bluetoothPermission", focusable = true)
    data object PairController : QuickConnectionsRow("pairController", focusable = true)
    data object NearbyControllersHeader : QuickConnectionsRow("nearbyControllersHeader", focusable = false)
    data object NearbyControllersPlaceholder : QuickConnectionsRow("nearbyControllersPlaceholder", focusable = false)
    data class NearbyController(val device: BluetoothNearbyUi) :
        QuickConnectionsRow("nearbyController_${device.address}", focusable = true)
    data object PairedHeader : QuickConnectionsRow("pairedHeader", focusable = false)
    data object PairedPlaceholder : QuickConnectionsRow("pairedPlaceholder", focusable = false)
    data class Paired(val device: BluetoothPairedUi) : QuickConnectionsRow("paired_${device.address}", focusable = true)
}

/**
 * Every row the Connections page draws, in order. Without the root route only the two status rows
 * appear and none takes focus.
 */
fun quickConnectionsRows(state: QuickConnectionsState): List<QuickConnectionsRow> = buildList {
    if (!state.switchable) {
        add(QuickConnectionsRow.WifiStatus)
        add(QuickConnectionsRow.BluetoothStatus)
        return@buildList
    }
    add(QuickConnectionsRow.WifiToggle)
    add(QuickConnectionsRow.BluetoothToggle)
    add(QuickConnectionsRow.ListTabs)
    when (state.list) {
        ConnectionsList.WIFI -> addWifiRows(state)
        ConnectionsList.BLUETOOTH -> addBluetoothRows(state)
    }
}

private fun MutableList<QuickConnectionsRow>.addWifiRows(state: QuickConnectionsState) {
    if (!state.wifiEnabled) {
        add(QuickConnectionsRow.WifiOffPlaceholder)
        return
    }
    state.current?.let { add(QuickConnectionsRow.CurrentNetwork(it)) }
    add(QuickConnectionsRow.NearbyHeader)
    if (state.nearby.isEmpty()) add(QuickConnectionsRow.NearbyPlaceholder)
    state.nearby.forEach { add(QuickConnectionsRow.Nearby(it)) }
    if (state.saved.isNotEmpty()) {
        add(QuickConnectionsRow.SavedHeader)
        state.saved.forEach { add(QuickConnectionsRow.Saved(it)) }
    }
}

private fun MutableList<QuickConnectionsRow>.addBluetoothRows(state: QuickConnectionsState) {
    when {
        !state.bluetoothEnabled -> add(QuickConnectionsRow.BluetoothOffPlaceholder)
        !state.bluetoothPermitted -> add(QuickConnectionsRow.BluetoothPermission)
        else -> {
            add(QuickConnectionsRow.PairController)
            if (state.discovering || state.searched) {
                add(QuickConnectionsRow.NearbyControllersHeader)
                if (state.nearbyControllers.isEmpty()) add(QuickConnectionsRow.NearbyControllersPlaceholder)
                state.nearbyControllers.forEach { add(QuickConnectionsRow.NearbyController(it)) }
            }
            add(QuickConnectionsRow.PairedHeader)
            if (state.paired.isEmpty()) add(QuickConnectionsRow.PairedPlaceholder)
            state.paired.forEach { add(QuickConnectionsRow.Paired(it)) }
        }
    }
}

fun quickConnectionsFocusableRows(state: QuickConnectionsState): List<QuickConnectionsRow> =
    quickConnectionsRows(state).filter { it.focusable }

fun quickConnectionsFocusIndex(state: QuickConnectionsState): Int {
    val count = quickConnectionsFocusableRows(state).size
    return if (count == 0) -1 else state.focusIndex.coerceIn(0, count - 1)
}

fun quickConnectionsFocusedRow(state: QuickConnectionsState): QuickConnectionsRow? =
    quickConnectionsFocusableRows(state).getOrNull(quickConnectionsFocusIndex(state))
