package com.nendo.argosy.ui.components.connections

import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.hardware.WifiSecurity
import com.nendo.argosy.ui.components.TextEntryFocus

data class WifiCurrentUi(val ssid: String?, val signalLevel: Int, val band: WifiBand?)

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

/**
 * FULL switches and edits through the root route; STANDARD uses only public Android APIs.
 */
enum class RadioTier { FULL, STANDARD }

enum class RadioPermission { GRANTED, ASKABLE, DENIED }

enum class BluetoothEnableRoute { DIRECT, SYSTEM_PROMPT }

enum class OutsideLaunch { BLUETOOTH_ENABLE_PROMPT, BLUETOOTH_SETTINGS, WIFI_PANEL, APP_PERMISSIONS }

data class ConnectionsPermissionRequest(val radio: ConnectionsList, val permissions: List<String>)

data class QuickConnectionsState(
    val loaded: Boolean = false,
    val wifiTier: RadioTier = RadioTier.STANDARD,
    val bluetoothTier: RadioTier = RadioTier.STANDARD,
    val wifiEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val current: WifiCurrentUi? = null,
    val nearby: List<WifiNearbyUi> = emptyList(),
    val scanning: Boolean = false,
    val lastScanAt: Long? = null,
    val saved: List<WifiSavedUi> = emptyList(),
    val joiningSsid: String? = null,
    val failedSsid: String? = null,
    val wifiPermission: RadioPermission = RadioPermission.ASKABLE,
    val bluetoothPermission: RadioPermission = RadioPermission.GRANTED,
    val paired: List<BluetoothPairedUi> = emptyList(),
    val canForgetDevice: Boolean = false,
    val discovering: Boolean = false,
    val searched: Boolean = false,
    val nearbyControllers: List<BluetoothNearbyUi> = emptyList(),
    val pairingAddress: String? = null,
    val pairFailedAddress: String? = null,
    val justAddedKey: String? = null,
    val focusIndices: Map<ConnectionsList, Int> = emptyMap(),
    val join: WifiJoinDraft? = null,
    val list: ConnectionsList = ConnectionsList.WIFI
) {
    val focusIndex: Int get() = focusIndices[list] ?: 0
    val canToggleWifi: Boolean get() = wifiTier == RadioTier.FULL
    val canJoinWifi: Boolean get() = wifiTier == RadioTier.FULL
    val canForgetWifi: Boolean get() = wifiTier == RadioTier.FULL
    val canReadSavedWifi: Boolean get() = wifiTier == RadioTier.FULL
    val canReadNearbyWifi: Boolean get() = wifiTier == RadioTier.FULL || wifiPermission == RadioPermission.GRANTED
    val canReadCurrentSsid: Boolean get() = canReadNearbyWifi
    val canToggleBluetoothOff: Boolean get() = bluetoothTier == RadioTier.FULL
    val bluetoothEnableRoute: BluetoothEnableRoute
        get() = if (bluetoothTier == RadioTier.FULL) BluetoothEnableRoute.DIRECT else BluetoothEnableRoute.SYSTEM_PROMPT

    fun canSwitchBluetoothDirectly(on: Boolean): Boolean =
        if (on) bluetoothEnableRoute == BluetoothEnableRoute.DIRECT else canToggleBluetoothOff

    fun withFocus(index: Int): QuickConnectionsState = copy(focusIndices = focusIndices + (list to index))
}

fun bluetoothRowKey(address: String): String = "bt_$address"

fun wifiRowKey(ssid: String): String = "wifi_$ssid"

sealed class QuickConnectionsRow(val key: String, val focusable: Boolean) {
    data object ListTabs : QuickConnectionsRow("listTabs", focusable = true)
    data object WifiToggle : QuickConnectionsRow("wifiToggle", focusable = true)
    data object WifiOff : QuickConnectionsRow("wifiOff", focusable = false)
    data object WifiPermission : QuickConnectionsRow("wifiPermission", focusable = true)
    data object SavedHeader : QuickConnectionsRow("savedHeader", focusable = false)
    data class ConnectedNetwork(val network: WifiCurrentUi) :
        QuickConnectionsRow(wifiRowKey(network.ssid.orEmpty()), focusable = true)
    data class Saved(val network: WifiSavedUi, val connected: Boolean, val inRange: Boolean?) :
        QuickConnectionsRow(wifiRowKey(network.ssid), focusable = true)
    data object NearbyHeader : QuickConnectionsRow("nearbyHeader", focusable = false)
    data object ScanNetworks : QuickConnectionsRow("scanNetworks", focusable = true)
    data object NearbyPlaceholder : QuickConnectionsRow("nearbyPlaceholder", focusable = false)
    data class Nearby(val network: WifiNearbyUi) : QuickConnectionsRow(wifiRowKey(network.ssid), focusable = true)
    data object BluetoothToggle : QuickConnectionsRow("bluetoothToggle", focusable = true)
    data object BluetoothOff : QuickConnectionsRow("bluetoothOff", focusable = false)
    data object BluetoothPermission : QuickConnectionsRow("bluetoothPermission", focusable = true)
    data object PairedHeader : QuickConnectionsRow("pairedHeader", focusable = false)
    data object PairedPlaceholder : QuickConnectionsRow("pairedPlaceholder", focusable = false)
    data class Paired(val device: BluetoothPairedUi) :
        QuickConnectionsRow(bluetoothRowKey(device.address), focusable = true)
    data object PairController : QuickConnectionsRow("pairController", focusable = true)
    data object ForgetDeviceHint : QuickConnectionsRow("forgetDeviceHint", focusable = false)
    data object FoundHeader : QuickConnectionsRow("foundHeader", focusable = false)
    data object FoundPlaceholder : QuickConnectionsRow("foundPlaceholder", focusable = false)
    data class NearbyController(val device: BluetoothNearbyUi) :
        QuickConnectionsRow(bluetoothRowKey(device.address), focusable = true)
}

/**
 * Every row the Connections page draws for the open tab, in order, behind the tabs row. A found
 * controller and the paired device it becomes share one key, as do a nearby network and the saved
 * one it becomes.
 */
fun quickConnectionsRows(state: QuickConnectionsState): List<QuickConnectionsRow> {
    val base = buildList {
        add(QuickConnectionsRow.ListTabs)
        when (state.list) {
            ConnectionsList.WIFI -> addWifiRows(state)
            ConnectionsList.BLUETOOTH -> addBluetoothRows(state)
        }
    }
    if (state.canForgetDevice || base.none { it is QuickConnectionsRow.Paired }) return base
    val hintAt = base.indexOf(QuickConnectionsRow.PairController) + 1
    return base.toMutableList().apply { add(hintAt, QuickConnectionsRow.ForgetDeviceHint) }
}

fun QuickConnectionsRow.isGroupHeader(): Boolean = when (this) {
    QuickConnectionsRow.SavedHeader, QuickConnectionsRow.NearbyHeader,
    QuickConnectionsRow.PairedHeader, QuickConnectionsRow.FoundHeader -> true
    else -> false
}

private fun MutableList<QuickConnectionsRow>.addWifiRows(state: QuickConnectionsState) {
    add(QuickConnectionsRow.WifiToggle)
    if (!state.wifiEnabled) {
        add(QuickConnectionsRow.WifiOff)
        return
    }
    val kept = if (state.canReadSavedWifi) keptWifiRows(state) else emptyList()
    if (kept.isNotEmpty()) {
        add(QuickConnectionsRow.SavedHeader)
        addAll(kept)
    }
    val keptKeys = kept.mapTo(mutableSetOf()) { it.key }
    add(QuickConnectionsRow.NearbyHeader)
    if (!state.canReadNearbyWifi) {
        add(QuickConnectionsRow.WifiPermission)
        return
    }
    add(QuickConnectionsRow.ScanNetworks)
    val currentSsid = state.current?.ssid
    val nearby = state.nearby.filter { it.ssid != currentSsid && wifiRowKey(it.ssid) !in keptKeys }
    if (nearby.isEmpty() && !state.scanning) add(QuickConnectionsRow.NearbyPlaceholder)
    nearby.forEach { add(QuickConnectionsRow.Nearby(it)) }
}

private fun keptWifiRows(state: QuickConnectionsState): List<QuickConnectionsRow> {
    val currentSsid = state.current?.ssid
    val inRangeSsids = state.nearby.mapTo(mutableSetOf()) { it.ssid }
    val saved = state.saved.distinctBy { it.ssid }
    val currentSaved = saved.firstOrNull { it.ssid == currentSsid }
    return buildList {
        val current = state.current
        when {
            currentSaved != null -> add(QuickConnectionsRow.Saved(currentSaved, connected = true, inRange = true))
            current != null && currentSsid != null -> add(QuickConnectionsRow.ConnectedNetwork(current))
        }
        saved.filter { it.ssid != currentSsid }.forEach { network ->
            val inRange = if (state.lastScanAt == null) null else network.ssid in inRangeSsids
            add(QuickConnectionsRow.Saved(network, connected = false, inRange = inRange))
        }
    }
}

private fun MutableList<QuickConnectionsRow>.addBluetoothRows(state: QuickConnectionsState) {
    add(QuickConnectionsRow.BluetoothToggle)
    if (!state.bluetoothEnabled) {
        add(QuickConnectionsRow.BluetoothOff)
        return
    }
    add(QuickConnectionsRow.PairedHeader)
    if (state.bluetoothPermission != RadioPermission.GRANTED) {
        add(QuickConnectionsRow.BluetoothPermission)
        return
    }
    if (state.paired.isEmpty()) add(QuickConnectionsRow.PairedPlaceholder)
    state.paired.forEach { add(QuickConnectionsRow.Paired(it)) }
    add(QuickConnectionsRow.PairController)
    if (state.discovering || state.searched) {
        add(QuickConnectionsRow.FoundHeader)
        val pairedAddresses = state.paired.mapTo(mutableSetOf()) { it.address }
        val found = state.nearbyControllers.filter { it.address !in pairedAddresses }
        if (found.isEmpty()) add(QuickConnectionsRow.FoundPlaceholder)
        found.forEach { add(QuickConnectionsRow.NearbyController(it)) }
    }
}

private fun clampFocus(index: Int, count: Int): Int = if (count == 0) -1 else index.coerceIn(0, count - 1)

fun quickConnectionsFocusableRows(state: QuickConnectionsState): List<QuickConnectionsRow> =
    quickConnectionsRows(state).filter { it.focusable }

fun quickConnectionsFocusIndex(state: QuickConnectionsState): Int =
    clampFocus(state.focusIndex, quickConnectionsFocusableRows(state).size)

fun quickConnectionsFocusedRow(state: QuickConnectionsState): QuickConnectionsRow? =
    quickConnectionsFocusableRows(state).getOrNull(quickConnectionsFocusIndex(state))

sealed interface WifiStatus {
    data object Standard : WifiStatus
    data object NotConnected : WifiStatus
    data object Connected : WifiStatus
    data class Network(val ssid: String, val band: WifiBand?) : WifiStatus
}

/**
 * What the Wi-Fi switch row says under its label, or null when it says nothing. Standard is the
 * explanation that the switch opens the Wi-Fi panel.
 */
fun wifiStatusOf(state: QuickConnectionsState): WifiStatus? {
    if (!state.canToggleWifi) return WifiStatus.Standard
    if (!state.wifiEnabled) return null
    if (!state.wifiConnected) return WifiStatus.NotConnected
    val current = state.current ?: return WifiStatus.Connected
    val ssid = current.ssid?.takeIf { state.canReadCurrentSsid } ?: return WifiStatus.Connected
    return WifiStatus.Network(ssid, current.band)
}

sealed interface BluetoothStatus {
    data object Standard : BluetoothStatus
    data object On : BluetoothStatus
    data object NoneConnected : BluetoothStatus
    data class OneConnected(val name: String) : BluetoothStatus
    data class ManyConnected(val count: Int) : BluetoothStatus
}

/**
 * What the Bluetooth switch row says under its label, or null when it says nothing. Standard is
 * the explanation that switching off happens in Android Bluetooth settings.
 */
fun bluetoothStatusOf(state: QuickConnectionsState): BluetoothStatus? {
    if (!state.bluetoothEnabled) return null
    if (!state.canToggleBluetoothOff) return BluetoothStatus.Standard
    if (state.bluetoothPermission != RadioPermission.GRANTED) return BluetoothStatus.On
    val connected = state.paired.filter { it.connected }
    return when (connected.size) {
        0 -> BluetoothStatus.NoneConnected
        1 -> BluetoothStatus.OneConnected(connected.single().name)
        else -> BluetoothStatus.ManyConnected(connected.size)
    }
}

enum class ConnectionsRowAction {
    JOIN, JOIN_OUTSIDE, RETRY_JOIN, PAIR, RETRY_PAIR, STOP_SEARCH, FORGET_NETWORK, FORGET_DEVICE
}

/**
 * The action [row] offers while it has focus, or null when it offers none.
 */
fun quickConnectionsRowAction(state: QuickConnectionsState, row: QuickConnectionsRow): ConnectionsRowAction? =
    when (row) {
        is QuickConnectionsRow.Saved -> ConnectionsRowAction.FORGET_NETWORK.takeIf { state.canForgetWifi }
        is QuickConnectionsRow.Paired -> ConnectionsRowAction.FORGET_DEVICE.takeIf { state.canForgetDevice }
        is QuickConnectionsRow.Nearby -> when {
            state.joiningSsid == row.network.ssid || state.justAddedKey == row.key -> null
            state.failedSsid == row.network.ssid -> ConnectionsRowAction.RETRY_JOIN
            state.canJoinWifi -> ConnectionsRowAction.JOIN
            else -> ConnectionsRowAction.JOIN_OUTSIDE
        }
        is QuickConnectionsRow.NearbyController -> when {
            state.pairingAddress == row.device.address || state.justAddedKey == row.key -> null
            state.pairFailedAddress == row.device.address -> ConnectionsRowAction.RETRY_PAIR
            else -> ConnectionsRowAction.PAIR
        }
        QuickConnectionsRow.PairController -> ConnectionsRowAction.STOP_SEARCH.takeIf { state.discovering }
        else -> null
    }

fun quickConnectionsCanForget(state: QuickConnectionsState, row: QuickConnectionsRow): Boolean =
    when (quickConnectionsRowAction(state, row)) {
        ConnectionsRowAction.FORGET_NETWORK, ConnectionsRowAction.FORGET_DEVICE -> true
        else -> false
    }
