package com.nendo.argosy.ui.components.connections

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.hardware.BluetoothPairing
import com.nendo.argosy.hardware.NearbyBluetoothDevice
import com.nendo.argosy.hardware.PairedBluetoothDevice
import com.nendo.argosy.hardware.RadioController
import com.nendo.argosy.hardware.WifiConnection
import com.nendo.argosy.hardware.WifiScanResult
import com.nendo.argosy.hardware.WifiSecurity
import com.nendo.argosy.hardware.WifiShellParser
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.InputResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val RADIO_SETTLE_MS = listOf(1_000L, 2_000L, 3_000L)
private val SCAN_POLL_MS = listOf(1_500L, 2_500L, 3_000L)
private val JOIN_SETTLE_MS = listOf(2_000L, 3_000L, 5_000L)
private const val FORGET_SETTLE_MS = 1_000L

private data class WifiSnapshot(
    val current: WifiCurrentUi?,
    val saved: List<WifiSavedUi>
)

/**
 * State and actions of the quick panel's Connections page. Every radio and shell call runs on
 * [deviceDispatcher].
 */
class QuickConnectionsController(
    private val radioController: RadioController,
    private val scope: CoroutineScope,
    private val deviceDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) {
    private val _state = MutableStateFlow(QuickConnectionsState())
    val state: StateFlow<QuickConnectionsState> = _state.asStateFlow()

    private val _bluetoothPermissionRequests = MutableSharedFlow<List<String>>(extraBufferCapacity = 1)
    val bluetoothPermissionRequests: SharedFlow<List<String>> = _bluetoothPermissionRequests.asSharedFlow()

    private var scanJob: Job? = null
    private var bluetoothWatch: AutoCloseable? = null
    private var searchAfterPermission = false

    private val pairingListener = object : BluetoothPairing.Listener {
        override fun onFound(device: NearbyBluetoothDevice) {
            _state.update { state ->
                if (state.nearbyControllers.any { it.address == device.address }) return@update state
                state.copy(nearbyControllers = state.nearbyControllers + BluetoothNearbyUi(device.address, device.name))
            }
        }

        override fun onDiscoveryFinished() {
            _state.update { it.copy(discovering = false) }
        }

        override fun onBondChanged(address: String, bonded: Boolean, failed: Boolean) {
            when {
                bonded -> {
                    _state.update { state ->
                        state.copy(
                            nearbyControllers = state.nearbyControllers.filterNot { it.address == address },
                            pairingAddress = state.pairingAddress.takeUnless { it == address }
                        )
                    }
                    scope.launch(deviceDispatcher) { readBluetooth() }
                }
                failed -> _state.update { state ->
                    state.copy(
                        pairingAddress = state.pairingAddress.takeUnless { it == address },
                        pairFailedAddress = address
                    )
                }
            }
        }
    }

    fun onPageShown() {
        _state.update { it.copy(focusIndex = 0, join = null) }
        if (bluetoothWatch == null) bluetoothWatch = radioController.bluetooth.watch(pairingListener)
        scope.launch(deviceDispatcher) {
            readAll()
            if (_state.value.switchable && _state.value.wifiEnabled) startScan()
        }
    }

    fun onPageHidden() {
        scanJob?.cancel()
        scanJob = null
        stopSearch()
        bluetoothWatch?.close()
        bluetoothWatch = null
        _state.update { it.copy(join = null, scanning = false, searched = false, nearbyControllers = emptyList()) }
    }

    fun setFocus(index: Int) {
        _state.update { it.copy(focusIndex = index) }
    }

    fun showList(list: ConnectionsList) {
        if (list != ConnectionsList.BLUETOOTH) stopSearch()
        _state.update { it.copy(list = list) }
    }

    fun moveFocus(delta: Int, wrapMode: MenuWrapMode): InputResult {
        val snapshot = _state.value
        val current = quickConnectionsFocusIndex(snapshot)
        if (current < 0) return InputResult.handled(SoundType.BOUNDARY)
        val maxIndex = quickConnectionsFocusableRows(snapshot).lastIndex
        val next = computeWrappedIndex(current, delta, maxIndex, wrapMode)
        _state.update { it.copy(focusIndex = next) }
        return if (next != current) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun setWifiEnabled(enabled: Boolean) {
        if (!_state.value.switchable) return
        _state.update {
            it.copy(
                wifiEnabled = enabled,
                wifiConnected = false,
                current = null,
                nearby = if (enabled) it.nearby else emptyList()
            )
        }
        if (!enabled) scanJob?.cancel()
        switchRadio(rescan = enabled) { radioController.setWifiEnabled(enabled) }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        if (!_state.value.switchable) return
        if (!enabled) stopSearch()
        _state.update {
            it.copy(
                bluetoothEnabled = enabled,
                paired = if (enabled) it.paired else emptyList(),
                searched = it.searched && enabled,
                nearbyControllers = if (enabled) it.nearbyControllers else emptyList()
            )
        }
        switchRadio(rescan = false) { radioController.setBluetoothEnabled(enabled) }
    }

    fun requestBluetoothPermission() {
        _bluetoothPermissionRequests.tryEmit(BluetoothPairing.requiredPermissions())
    }

    fun onBluetoothPermissionResult() {
        scope.launch(deviceDispatcher) {
            readBluetooth()
            if (searchAfterPermission && radioController.bluetooth.missingPermissions().isEmpty()) {
                searchAfterPermission = false
                startSearch()
            }
            searchAfterPermission = false
        }
    }

    fun toggleSearch() {
        if (_state.value.discovering) {
            stopSearch()
            return
        }
        val missing = radioController.bluetooth.missingPermissions()
        if (missing.isNotEmpty()) {
            searchAfterPermission = true
            _bluetoothPermissionRequests.tryEmit(missing)
            return
        }
        scope.launch(deviceDispatcher) { startSearch() }
    }

    fun pair(device: BluetoothNearbyUi) {
        if (_state.value.pairingAddress != null) return
        _state.update { it.copy(pairingAddress = device.address, pairFailedAddress = null, discovering = false) }
        scope.launch(deviceDispatcher) {
            if (!radioController.bluetooth.pair(device.address)) {
                _state.update { it.copy(pairingAddress = null, pairFailedAddress = device.address) }
            }
        }
    }

    fun forgetDevice(device: BluetoothPairedUi) {
        if (!_state.value.canForgetDevices) return
        _state.update { state -> state.copy(paired = state.paired.filterNot { it.address == device.address }) }
        scope.launch(deviceDispatcher) {
            radioController.bluetooth.forget(device.address)
            delay(FORGET_SETTLE_MS)
            readBluetooth()
        }
    }

    private fun startSearch() {
        val started = radioController.bluetooth.startDiscovery()
        _state.update {
            it.copy(
                discovering = started,
                searched = true,
                nearbyControllers = if (started) emptyList() else it.nearbyControllers,
                pairFailedAddress = null
            )
        }
    }

    private fun stopSearch() {
        if (!_state.value.discovering) return
        _state.update { it.copy(discovering = false) }
        scope.launch(deviceDispatcher) { radioController.bluetooth.cancelDiscovery() }
    }

    fun selectNearby(network: WifiNearbyUi) {
        if (!_state.value.switchable || _state.value.joiningSsid != null) return
        if (network.security.needsPassphrase) {
            _state.update { it.copy(join = WifiJoinDraft(ssid = network.ssid, security = network.security)) }
        } else {
            join(network.ssid, network.security, passphrase = null)
        }
    }

    fun setJoinPassword(password: String) {
        _state.update { state -> state.copy(join = state.join?.copy(password = password, failed = false)) }
    }

    fun setJoinFocus(row: TextEntryRow, buttonIndex: Int? = null) {
        _state.update { state ->
            val draft = state.join ?: return@update state
            state.copy(
                join = draft.copy(
                    focus = draft.focus.copy(row = row, buttonIndex = buttonIndex ?: draft.focus.buttonIndex)
                )
            )
        }
    }

    fun confirmJoinFocus() {
        val draft = _state.value.join ?: return
        when {
            draft.focus.row == TextEntryRow.FIELD -> setJoinFocus(TextEntryRow.BUTTONS, TEXT_ENTRY_CONFIRM_BUTTON)
            draft.focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> dismissJoin()
            else -> submitJoin()
        }
    }

    fun submitJoin() {
        val draft = _state.value.join ?: return
        if (!draft.canSubmit) return
        join(draft.ssid, draft.security, draft.password)
    }

    fun dismissJoin() {
        _state.update { state -> if (state.join?.busy == true) state else state.copy(join = null) }
    }

    fun forget(network: WifiSavedUi) {
        if (!_state.value.switchable) return
        _state.update { state -> state.copy(saved = state.saved.filterNot { it.networkId == network.networkId }) }
        scope.launch(deviceDispatcher) {
            radioController.wifi.forget(network.networkId)
            readWifi()
        }
    }

    private fun join(ssid: String, security: WifiSecurity, passphrase: String?) {
        _state.update { state ->
            state.copy(joiningSsid = ssid, failedSsid = null, join = state.join?.copy(busy = true, failed = false))
        }
        scope.launch(deviceDispatcher) {
            val accepted = radioController.wifi.join(ssid, security, passphrase)
            if (!accepted) {
                _state.update { state ->
                    state.copy(
                        joiningSsid = null,
                        failedSsid = ssid,
                        join = state.join?.copy(busy = false, failed = true)
                    )
                }
                return@launch
            }
            _state.update { it.copy(join = null) }
            try {
                for (wait in JOIN_SETTLE_MS) {
                    delay(wait)
                    readWifi()
                    if (_state.value.current?.ssid == ssid) break
                }
            } finally {
                _state.update { it.copy(joiningSsid = null) }
            }
        }
    }

    private fun switchRadio(rescan: Boolean, apply: () -> Boolean) {
        scope.launch(deviceDispatcher) {
            apply()
            RADIO_SETTLE_MS.forEach { wait ->
                delay(wait)
                readAll()
            }
            if (rescan && _state.value.wifiEnabled) startScan()
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch(deviceDispatcher) {
            _state.update { it.copy(scanning = true) }
            try {
                radioController.wifi.startScan()
                SCAN_POLL_MS.forEach { wait ->
                    delay(wait)
                    val results = radioController.wifi.scanResults()
                    _state.update { state -> state.copy(nearby = nearbyFrom(results, state.current?.ssid)) }
                }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    private fun readAll() {
        val status = radioController.status()
        val switchable = radioController.canToggle
        _state.update {
            it.copy(
                loaded = true,
                switchable = switchable,
                wifiEnabled = status.wifiEnabled,
                wifiConnected = status.wifiConnected,
                bluetoothEnabled = status.bluetoothEnabled
            )
        }
        if (!switchable) return
        readWifi()
        readBluetooth()
    }

    private fun readWifi() {
        val enabled = radioController.status().wifiEnabled
        val snapshot = if (enabled) {
            WifiSnapshot(
                current = radioController.wifi.connection()?.toUi(),
                saved = radioController.wifi.savedNetworks().map { WifiSavedUi(it.networkId, it.ssid) }
            )
        } else {
            WifiSnapshot(current = null, saved = emptyList())
        }
        _state.update { state ->
            state.copy(
                wifiEnabled = enabled,
                wifiConnected = snapshot.current != null,
                current = snapshot.current,
                saved = snapshot.saved,
                nearby = state.nearby.filterNot { it.ssid == snapshot.current?.ssid }
            )
        }
    }

    private fun readBluetooth() {
        val permitted = radioController.hasBluetoothPermission()
        val enabled = radioController.status().bluetoothEnabled
        val paired = if (enabled && permitted) radioController.pairedDevices().map { it.toUi() } else emptyList()
        _state.update {
            it.copy(
                bluetoothEnabled = enabled,
                bluetoothPermitted = permitted,
                paired = paired,
                canForgetDevices = radioController.bluetooth.canForget
            )
        }
    }

    private fun nearbyFrom(results: List<WifiScanResult>, connectedSsid: String?): List<WifiNearbyUi> = results
        .filter { it.ssid != connectedSsid }
        .mapNotNull { result ->
            val security = result.security ?: return@mapNotNull null
            WifiNearbyUi(
                ssid = result.ssid,
                signalLevel = WifiShellParser.signalLevel(result.rssi),
                security = security
            )
        }

    private fun WifiConnection.toUi(): WifiCurrentUi = WifiCurrentUi(
        ssid = ssid,
        signalLevel = WifiShellParser.signalLevel(rssi),
        band = WifiShellParser.bandOf(frequencyMhz)
    )

    private fun PairedBluetoothDevice.toUi(): BluetoothPairedUi = BluetoothPairedUi(
        address = address,
        name = name,
        connected = connected,
        kind = kind,
        batteryPercent = batteryPercent
    )
}
