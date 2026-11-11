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
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens
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
private val SUCCESS_HOLD_MS = ComponentDefaults.Connections.successHoldMs.toLong()
private val SUCCESS_TINT_MS = MotionTokens.Tween.longMs.toLong()

private data class WifiSnapshot(
    val enabled: Boolean,
    val connected: Boolean,
    val current: WifiCurrentUi?,
    val saved: List<WifiSavedUi>,
    val permitted: Boolean
)

private fun permissionOf(granted: Boolean, previous: RadioPermission): RadioPermission = when {
    granted -> RadioPermission.GRANTED
    previous == RadioPermission.DENIED -> RadioPermission.DENIED
    else -> RadioPermission.ASKABLE
}

/**
 * State and actions of a Connections list, behind both the quick panel's page and the Network
 * and Bluetooth settings section. Every radio and shell call runs on [deviceDispatcher]. Actions
 * Android keeps for itself arrive on [outsideLaunches] for the host to open; [forgetHold] carries
 * the gamepad hold that forgets the focused device or network.
 */
class QuickConnectionsController(
    private val radioController: RadioController,
    private val scope: CoroutineScope,
    private val deviceDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) {
    private val _state = MutableStateFlow(QuickConnectionsState())
    val state: StateFlow<QuickConnectionsState> = _state.asStateFlow()

    val forgetHold = GamepadHoldTracker()

    private val _permissionRequests = MutableSharedFlow<ConnectionsPermissionRequest>(extraBufferCapacity = 1)
    val permissionRequests: SharedFlow<ConnectionsPermissionRequest> = _permissionRequests.asSharedFlow()

    private val _outsideLaunches = MutableSharedFlow<OutsideLaunch>(extraBufferCapacity = 1)
    val outsideLaunches: SharedFlow<OutsideLaunch> = _outsideLaunches.asSharedFlow()

    private var scanJob: Job? = null
    private var bluetoothWatch: AutoCloseable? = null
    private var searchAfterPermission = false
    private var shown = false

    private val pairingListener = object : BluetoothPairing.Listener {
        override fun onFound(device: NearbyBluetoothDevice) {
            updateKeepingFocus { state ->
                if (state.nearbyControllers.any { it.address == device.address }) return@updateKeepingFocus state
                state.copy(nearbyControllers = state.nearbyControllers + BluetoothNearbyUi(device.address, device.name))
            }
        }

        override fun onDiscoveryFinished() {
            _state.update { it.copy(discovering = false) }
        }

        override fun onBondChanged(address: String, bonded: Boolean, failed: Boolean) {
            when {
                bonded -> landPairedDevice(address)
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
        shown = true
        forgetHold.forceRelease()
        _state.update { it.copy(focusIndices = emptyMap(), join = null) }
        if (bluetoothWatch == null) bluetoothWatch = radioController.bluetooth.watch(pairingListener)
        scope.launch(deviceDispatcher) {
            readAll()
            scanIfReadable()
        }
    }

    fun onResumed() {
        if (!shown) return
        scope.launch(deviceDispatcher) {
            readAll()
            if (!_state.value.scanning) scanIfReadable()
        }
    }

    fun onPageHidden() {
        shown = false
        forgetHold.forceRelease()
        scanJob?.cancel()
        scanJob = null
        stopSearch()
        bluetoothWatch?.close()
        bluetoothWatch = null
        _state.update { it.copy(join = null, scanning = false, searched = false, nearbyControllers = emptyList()) }
    }

    fun setFocus(index: Int) {
        forgetHold.forceRelease()
        _state.update { it.withFocus(index) }
    }

    fun showList(list: ConnectionsList) {
        if (list != ConnectionsList.BLUETOOTH) stopSearch()
        forgetHold.forceRelease()
        _state.update { it.copy(list = list) }
    }

    fun moveFocus(delta: Int, wrapMode: MenuWrapMode): InputResult {
        forgetHold.forceRelease()
        val snapshot = _state.value
        val current = quickConnectionsFocusIndex(snapshot)
        if (current < 0) return InputResult.handled(SoundType.BOUNDARY)
        val maxIndex = quickConnectionsFocusableRows(snapshot).lastIndex
        val next = computeWrappedIndex(current, delta, maxIndex, wrapMode)
        _state.update { it.withFocus(next) }
        return if (next != current) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun holdForget(isRepeat: Boolean) {
        forgetHold.onConfirmKeyDown(isRepeat = isRepeat)
    }

    fun releaseForget(): Boolean {
        if (!forgetHold.isHeld) return false
        forgetHold.forceRelease()
        return true
    }

    fun setWifiEnabled(enabled: Boolean) {
        if (!_state.value.canToggleWifi) return
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
        if (!_state.value.canSwitchBluetoothDirectly(on = enabled)) return
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

    fun requestBluetoothOn() {
        val launch = if (radioController.hasBluetoothPermission()) {
            OutsideLaunch.BLUETOOTH_ENABLE_PROMPT
        } else {
            OutsideLaunch.BLUETOOTH_SETTINGS
        }
        _outsideLaunches.tryEmit(launch)
    }

    fun openBluetoothSettings() {
        _outsideLaunches.tryEmit(OutsideLaunch.BLUETOOTH_SETTINGS)
    }

    fun openWifiPanel() {
        _outsideLaunches.tryEmit(OutsideLaunch.WIFI_PANEL)
    }

    fun requestPermission(radio: ConnectionsList) {
        val state = _state.value
        val permission = when (radio) {
            ConnectionsList.WIFI -> state.wifiPermission
            ConnectionsList.BLUETOOTH -> state.bluetoothPermission
        }
        if (permission == RadioPermission.DENIED) {
            _outsideLaunches.tryEmit(OutsideLaunch.APP_PERMISSIONS)
            return
        }
        val permissions = when (radio) {
            ConnectionsList.WIFI -> radioController.wifiPermissions()
            ConnectionsList.BLUETOOTH -> BluetoothPairing.requiredPermissions()
        }
        _permissionRequests.tryEmit(ConnectionsPermissionRequest(radio, permissions))
    }

    /**
     * Records the answer to a request from [permissionRequests]. [permanentlyDenied] is true when
     * Android will not show its prompt again, which turns the notice into a link to app settings.
     */
    fun onPermissionResult(radio: ConnectionsList, permanentlyDenied: Boolean) {
        when (radio) {
            ConnectionsList.WIFI -> {
                val granted = radioController.hasWifiPermission()
                _state.update {
                    it.copy(wifiPermission = permissionOf(granted, previousFor(permanentlyDenied, it.wifiPermission)))
                }
                scope.launch(deviceDispatcher) {
                    applyWifi(fetchWifi())
                    scanIfReadable()
                }
            }
            ConnectionsList.BLUETOOTH -> {
                val granted = radioController.hasBluetoothPermission()
                _state.update {
                    it.copy(
                        bluetoothPermission = permissionOf(granted, previousFor(permanentlyDenied, it.bluetoothPermission))
                    )
                }
                scope.launch(deviceDispatcher) {
                    readBluetooth()
                    val search = searchAfterPermission && radioController.bluetooth.missingPermissions().isEmpty()
                    searchAfterPermission = false
                    if (search) startSearch()
                }
            }
        }
    }

    private fun previousFor(permanentlyDenied: Boolean, previous: RadioPermission): RadioPermission =
        if (permanentlyDenied) RadioPermission.DENIED else previous

    fun toggleSearch() {
        if (_state.value.discovering) {
            stopSearch()
            return
        }
        val missing = radioController.bluetooth.missingPermissions()
        if (missing.isNotEmpty()) {
            searchAfterPermission = true
            _permissionRequests.tryEmit(ConnectionsPermissionRequest(ConnectionsList.BLUETOOTH, missing))
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
        if (!_state.value.canForgetDevice) return
        updateKeepingFocus { state -> state.copy(paired = state.paired.filterNot { it.address == device.address }) }
        scope.launch(deviceDispatcher) {
            radioController.bluetooth.forget(device.address)
            delay(FORGET_SETTLE_MS)
            readBluetooth()
        }
    }

    private fun landPairedDevice(address: String) {
        val key = bluetoothRowKey(address)
        _state.update { state ->
            state.copy(pairingAddress = state.pairingAddress.takeUnless { it == address }, justAddedKey = key)
        }
        scope.launch(deviceDispatcher) {
            delay(SUCCESS_HOLD_MS)
            val paired = readPaired()
            updateKeepingFocus { state ->
                state.copy(paired = paired, nearbyControllers = state.nearbyControllers.filterNot { it.address == address })
            }
            clearJustAdded(key)
        }
    }

    private suspend fun clearJustAdded(key: String) {
        delay(SUCCESS_TINT_MS)
        _state.update { if (it.justAddedKey == key) it.copy(justAddedKey = null) else it }
    }

    private fun startSearch() {
        val started = radioController.bluetooth.startDiscovery()
        updateKeepingFocus {
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

    fun scanNetworks() {
        if (_state.value.scanning) return
        scanIfReadable()
    }

    fun selectNearby(network: WifiNearbyUi) {
        if (!_state.value.canJoinWifi || _state.value.joiningSsid != null) return
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
        if (!_state.value.canForgetWifi) return
        updateKeepingFocus { state -> state.copy(saved = state.saved.filterNot { it.networkId == network.networkId }) }
        scope.launch(deviceDispatcher) {
            radioController.wifi.forget(network.networkId)
            applyWifi(fetchWifi())
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
            val joined = try {
                settleJoin(ssid)
            } finally {
                _state.update { it.copy(joiningSsid = null) }
            }
            if (!joined) {
                _state.update { it.copy(failedSsid = ssid) }
                return@launch
            }
            val key = wifiRowKey(ssid)
            _state.update { it.copy(justAddedKey = key) }
            delay(SUCCESS_HOLD_MS)
            applyWifi(fetchWifi())
            clearJustAdded(key)
        }
    }

    private suspend fun settleJoin(ssid: String): Boolean {
        for (wait in JOIN_SETTLE_MS) {
            delay(wait)
            val snapshot = fetchWifi()
            if (snapshot.current?.ssid == ssid) return true
            applyWifi(snapshot)
        }
        return false
    }

    private fun switchRadio(rescan: Boolean, apply: () -> Boolean) {
        scope.launch(deviceDispatcher) {
            apply()
            RADIO_SETTLE_MS.forEach { wait ->
                delay(wait)
                readAll()
            }
            if (rescan) scanIfReadable()
        }
    }

    private fun scanIfReadable() {
        val state = _state.value
        if (state.wifiEnabled && state.canReadNearbyWifi) startScan()
    }

    private fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch(deviceDispatcher) {
            _state.update { it.copy(scanning = true) }
            try {
                val full = _state.value.wifiTier == RadioTier.FULL
                if (full) radioController.wifi.startScan() else radioController.startPublicWifiScan()
                SCAN_POLL_MS.forEach { wait ->
                    delay(wait)
                    val results = if (full) radioController.wifi.scanResults() else radioController.publicWifiScanResults()
                    updateKeepingFocus { state ->
                        state.copy(
                            nearby = keepingOrder(state.nearby, nearbyFrom(results)),
                            lastScanAt = System.currentTimeMillis()
                        )
                    }
                }
            } finally {
                _state.update { it.copy(scanning = false) }
            }
        }
    }

    private fun readAll() {
        val status = radioController.status()
        val tier = if (radioController.canToggle) RadioTier.FULL else RadioTier.STANDARD
        _state.update {
            it.copy(
                loaded = true,
                wifiTier = tier,
                bluetoothTier = tier,
                wifiEnabled = status.wifiEnabled,
                wifiConnected = status.wifiConnected,
                bluetoothEnabled = status.bluetoothEnabled
            )
        }
        applyWifi(fetchWifi())
        readBluetooth()
    }

    private fun fetchWifi(): WifiSnapshot {
        val status = radioController.status()
        val permitted = radioController.hasWifiPermission()
        if (!status.wifiEnabled) {
            return WifiSnapshot(enabled = false, connected = false, current = null, saved = emptyList(), permitted)
        }
        val full = _state.value.wifiTier == RadioTier.FULL
        val connection = if (full) radioController.wifi.connection() else radioController.publicWifiConnection()
        val saved = if (full) {
            radioController.wifi.savedNetworks().map { WifiSavedUi(it.networkId, it.ssid) }
        } else {
            emptyList()
        }
        return WifiSnapshot(
            enabled = true,
            connected = connection != null || status.wifiConnected,
            current = connection?.toUi(),
            saved = saved,
            permitted = permitted
        )
    }

    private fun applyWifi(snapshot: WifiSnapshot) {
        updateKeepingFocus { state ->
            state.copy(
                wifiEnabled = snapshot.enabled,
                wifiConnected = snapshot.connected,
                current = snapshot.current,
                saved = snapshot.saved,
                nearby = if (snapshot.enabled) state.nearby else emptyList(),
                wifiPermission = permissionOf(snapshot.permitted, state.wifiPermission)
            )
        }
    }

    private fun readPaired(): List<BluetoothPairedUi> {
        val enabled = radioController.status().bluetoothEnabled
        return if (enabled && radioController.hasBluetoothPermission()) {
            radioController.pairedDevices().map { it.toUi() }
        } else {
            emptyList()
        }
    }

    private fun readBluetooth() {
        val permitted = radioController.hasBluetoothPermission()
        val enabled = radioController.status().bluetoothEnabled
        val paired = readPaired()
        val canForget = radioController.bluetooth.canForget
        updateKeepingFocus {
            it.copy(
                bluetoothEnabled = enabled,
                bluetoothPermission = permissionOf(permitted, it.bluetoothPermission),
                paired = paired,
                canForgetDevice = canForget
            )
        }
    }

    private fun updateKeepingFocus(transform: (QuickConnectionsState) -> QuickConnectionsState) {
        _state.update { old ->
            val focusedKey = quickConnectionsFocusedRow(old)?.key
            val next = transform(old)
            if (focusedKey == null || next.list != old.list) return@update next
            val index = quickConnectionsFocusableRows(next).indexOfFirst { it.key == focusedKey }
            if (index < 0) next else next.withFocus(index)
        }
    }

    private fun keepingOrder(previous: List<WifiNearbyUi>, fresh: List<WifiNearbyUi>): List<WifiNearbyUi> {
        val freshBySsid = fresh.associateBy { it.ssid }
        val previousSsids = previous.map { it.ssid }.toSet()
        return previous.mapNotNull { freshBySsid[it.ssid] } + fresh.filter { it.ssid !in previousSsids }
    }

    private fun nearbyFrom(results: List<WifiScanResult>): List<WifiNearbyUi> = results.mapNotNull { result ->
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
