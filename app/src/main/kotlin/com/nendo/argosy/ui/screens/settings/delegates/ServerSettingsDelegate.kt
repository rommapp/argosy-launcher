package com.nendo.argosy.ui.screens.settings.delegates

import android.util.Log
import com.nendo.argosy.data.remote.romm.DeviceAuthPoll
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ServerSettingsDelegate"

class ServerSettingsDelegate @Inject constructor(
    private val romMRepository: RomMRepository
) {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var devicePollJob: Job? = null

    fun updateState(newState: ServerState) {
        _state.value = newState
    }

    fun checkRommConnection(scope: CoroutineScope) {
        val url = _state.value.rommUrl
        if (url.isBlank()) {
            _state.update { it.copy(connectionStatus = ConnectionStatus.NOT_CONFIGURED) }
            return
        }

        scope.launch {
            _state.update { it.copy(connectionStatus = ConnectionStatus.CHECKING) }
            try {
                val result = romMRepository.getLibrarySummary()
                val status = if (result is RomMResult.Success) {
                    ConnectionStatus.ONLINE
                } else {
                    ConnectionStatus.OFFLINE
                }
                _state.update { it.copy(connectionStatus = status) }
            } catch (e: Exception) {
                Log.e(TAG, "checkRommConnection: failed", e)
                _state.update { it.copy(connectionStatus = ConnectionStatus.OFFLINE) }
            }
        }
    }

    fun startRommConfig(hasCamera: Boolean, onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                rommConfiguring = true,
                rommAuthMethod = RomMAuthMethod.DEVICE,
                rommConfigUrl = it.rommUrl,
                rommConfigUsername = it.rommUsername,
                rommConfigPassword = "",
                rommConfigPairingCode = "",
                rommHasCamera = hasCamera,
                rommConfigError = null,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null
            )
        }
        onFocusReset()
    }

    fun cancelRommConfig(onFocusReset: () -> Unit) {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(
                rommConfiguring = false,
                rommConfigUrl = "",
                rommConfigUsername = "",
                rommConfigPassword = "",
                rommConfigPairingCode = "",
                rommConfigError = null,
                rommConnecting = false,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null
            )
        }
        onFocusReset()
    }

    fun setRommConfigUrl(url: String) {
        _state.update { it.copy(rommConfigUrl = url) }
    }

    fun setRommConfigUsername(username: String) {
        _state.update { it.copy(rommConfigUsername = username) }
    }

    fun setRommConfigPassword(password: String) {
        _state.update { it.copy(rommConfigPassword = password) }
    }

    fun setRommConfigPairingCode(code: String) {
        _state.update { it.copy(rommConfigPairingCode = code) }
    }

    fun setRommAuthMethod(method: RomMAuthMethod) {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(
                rommAuthMethod = method,
                rommConfigError = null,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null
            )
        }
    }

    fun showScanner() {
        _state.update { it.copy(rommShowScanner = true) }
    }

    fun dismissScanner() {
        _state.update { it.copy(rommShowScanner = false) }
    }

    fun handleScanResult(origin: String, code: String, scope: CoroutineScope, onSuccess: suspend () -> Unit) {
        _state.update {
            it.copy(
                rommShowScanner = false,
                rommConfigUrl = origin,
                rommConfigPairingCode = code,
                rommAuthMethod = RomMAuthMethod.PAIRING_CODE
            )
        }
        connectToRomm(scope, onSuccess)
    }

    fun clearRommFocusField() {
        _state.update { it.copy(rommFocusField = null) }
    }

    fun setRommFocusField(index: Int) {
        _state.update { it.copy(rommFocusField = index) }
    }

    /** Probes the entered URL and auto-selects the version-appropriate auth method, mirroring the first-run wizard. */
    fun commitRommUrl(scope: CoroutineScope) {
        val state = _state.value
        if (state.rommConnecting || state.rommConfigUrl.isBlank()) return
        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }
            when (val result = romMRepository.connect(state.rommConfigUrl)) {
                is RomMResult.Success -> {
                    val method = if (romMRepository.isVersionAtLeast(RomMCapabilities.DEVICE_AUTH_MIN_VERSION)) {
                        RomMAuthMethod.DEVICE
                    } else {
                        RomMAuthMethod.PAIRING_CODE
                    }
                    _state.update {
                        it.copy(rommConnecting = false, rommAuthMethod = method, rommConfigError = null)
                    }
                }
                is RomMResult.Error -> {
                    _state.update { it.copy(rommConnecting = false, rommConfigError = result.message) }
                }
            }
        }
    }

    fun connectToRomm(scope: CoroutineScope, onSuccess: suspend () -> Unit) {
        val state = _state.value
        if (state.rommConfigUrl.isBlank()) return

        if (state.rommAuthMethod == RomMAuthMethod.DEVICE) {
            startDevicePairing(scope, onSuccess)
            return
        }

        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }

            when (state.rommAuthMethod) {
                RomMAuthMethod.PAIRING_CODE -> connectWithPairingCode(state, onSuccess)
                RomMAuthMethod.PASSWORD -> connectWithPassword(state, onSuccess)
                RomMAuthMethod.DEVICE -> {}
            }
        }
    }

    private fun startDevicePairing(scope: CoroutineScope, onSuccess: suspend () -> Unit) {
        devicePollJob?.cancel()
        devicePollJob = scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }
            when (val init = romMRepository.beginDeviceAuth(_state.value.rommConfigUrl)) {
                is RomMResult.Success -> {
                    val data = init.data
                    _state.update {
                        it.copy(
                            rommConnecting = false,
                            rommDevicePairing = true,
                            rommDeviceUserCode = data.userCode,
                            rommDeviceVerificationUrl = data.verificationPathComplete,
                            rommConfigError = null
                        )
                    }
                    pollForToken(data.deviceCode, data.interval, data.expiresIn, onSuccess)
                }
                is RomMResult.Error -> {
                    _state.update { it.copy(rommConnecting = false, rommConfigError = init.message) }
                }
            }
        }
    }

    private suspend fun pollForToken(
        deviceCode: String,
        interval: Int,
        expiresIn: Int,
        onSuccess: suspend () -> Unit
    ) {
        var intervalMs = interval.coerceAtLeast(1) * 1000L
        var elapsedMs = 0L
        val deadlineMs = expiresIn.coerceAtLeast(1) * 1000L
        while (currentCoroutineContext().isActive && elapsedMs < deadlineMs) {
            delay(intervalMs)
            elapsedMs += intervalMs
            when (val poll = romMRepository.pollDeviceAuthOnce(deviceCode)) {
                is DeviceAuthPoll.Approved -> {
                    _state.update {
                        it.copy(
                            rommDevicePairing = false,
                            rommDeviceUserCode = null,
                            rommDeviceVerificationUrl = null,
                            rommConfiguring = false,
                            connectionStatus = ConnectionStatus.ONLINE,
                            rommUrl = it.rommConfigUrl,
                            rommUsername = "",
                            rommConfigError = null
                        )
                    }
                    onSuccess()
                    return
                }
                DeviceAuthPoll.Pending -> {}
                DeviceAuthPoll.SlowDown -> intervalMs += 5000L
                DeviceAuthPoll.Denied -> { failPairing("Pairing was denied on the server"); return }
                DeviceAuthPoll.Expired -> { failPairing("Pairing code expired, start again"); return }
                is DeviceAuthPoll.Failed -> { failPairing(poll.message); return }
            }
        }
        if (currentCoroutineContext().isActive) failPairing("Pairing code expired, start again")
    }

    private fun failPairing(message: String) {
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null,
                rommConnecting = false,
                rommConfigError = message
            )
        }
    }

    private suspend fun connectWithPairingCode(state: ServerState, onSuccess: suspend () -> Unit) {
        val code = state.rommConfigPairingCode.replace("-", "").replace(" ", "")
        if (code.length != 8) {
            _state.update {
                it.copy(rommConnecting = false, rommConfigError = "Enter the full 8-character pairing code")
            }
            return
        }

        when (val result = romMRepository.exchangePairingCode(state.rommConfigUrl, code)) {
            is RomMResult.Success -> {
                _state.update {
                    it.copy(
                        rommConnecting = false,
                        rommConfiguring = false,
                        connectionStatus = ConnectionStatus.ONLINE,
                        rommUrl = state.rommConfigUrl,
                        rommUsername = ""
                    )
                }
                onSuccess()
            }
            is RomMResult.Error -> {
                _state.update {
                    it.copy(rommConnecting = false, rommConfigError = result.message)
                }
            }
        }
    }

    private suspend fun connectWithPassword(state: ServerState, onSuccess: suspend () -> Unit) {
        if (state.rommConfigUsername.isBlank() || state.rommConfigPassword.isBlank()) {
            _state.update {
                it.copy(rommConnecting = false, rommConfigError = "Username and password required")
            }
            return
        }

        when (val result = romMRepository.connect(state.rommConfigUrl)) {
            is RomMResult.Success -> {
                when (val loginResult = romMRepository.login(state.rommConfigUsername, state.rommConfigPassword)) {
                    is RomMResult.Success -> {
                        _state.update {
                            it.copy(
                                rommConnecting = false,
                                rommConfiguring = false,
                                connectionStatus = ConnectionStatus.ONLINE,
                                rommUrl = state.rommConfigUrl,
                                rommUsername = state.rommConfigUsername
                            )
                        }
                        onSuccess()
                    }
                    is RomMResult.Error -> {
                        _state.update {
                            it.copy(rommConnecting = false, rommConfigError = loginResult.message)
                        }
                    }
                }
            }
            is RomMResult.Error -> {
                _state.update {
                    it.copy(rommConnecting = false, rommConfigError = result.message)
                }
            }
        }
    }
}
