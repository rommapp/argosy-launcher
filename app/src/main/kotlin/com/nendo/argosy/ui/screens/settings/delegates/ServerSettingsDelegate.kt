package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.DeviceAuthOutcome
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.pollDeviceAuthUntilResolved
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "ServerSettingsDelegate"

class ServerSettingsDelegate @Inject constructor(
    private val romMRepository: RomMRepository,
    @ApplicationContext private val context: Context
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
                rommAuthMethod = defaultAuthMethod(),
                rommConfigUrl = it.rommUrl,
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

    private fun defaultAuthMethod(): RomMAuthMethod =
        if (romMRepository.isConnected() && !romMRepository.isVersionAtLeast(RomMCapabilities.DEVICE_AUTH_MIN_VERSION)) {
            RomMAuthMethod.PAIRING_CODE
        } else {
            RomMAuthMethod.DEVICE
        }

    fun cancelRommConfig(onFocusReset: () -> Unit) {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(
                rommConfiguring = false,
                rommConfigUrl = "",
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

    fun requestRommSignOut(scope: CoroutineScope, pendingUploads: suspend () -> Int) {
        scope.launch {
            val pending = pendingUploads()
            _state.update {
                it.copy(showRommSignOutConfirm = true, rommSignOutPendingUploads = pending)
            }
        }
    }

    fun cancelRommSignOut() {
        _state.update { it.copy(showRommSignOutConfirm = false) }
    }

    fun confirmRommSignOut(scope: CoroutineScope, onSignedOut: suspend () -> Unit) {
        if (_state.value.rommSigningOut) return
        scope.launch {
            _state.update { it.copy(showRommSignOutConfirm = false, rommSigningOut = true) }
            try {
                val result = romMRepository.signOut()
                if (result is AccountRemovalResult.SwitchInProgress) {
                    _state.update {
                        it.copy(
                            rommSigningOut = false,
                            rommConfigError = context.getString(
                                R.string.settings_server_delegate_signout_switch_in_progress
                            )
                        )
                    }
                    return@launch
                }
                if (result is AccountRemovalResult.Refused) {
                    _state.update {
                        it.copy(
                            rommSigningOut = false,
                            rommConfigError = context.getString(
                                R.string.settings_server_delegate_signout_refused,
                                result.pending.describe()
                            )
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        rommSigningOut = false,
                        rommUrl = "",
                        rommUsername = "",
                        rommVersion = null,
                        connectionStatus = ConnectionStatus.NOT_CONFIGURED
                    )
                }
                onSignedOut()
            } catch (e: Exception) {
                Log.e(TAG, "confirmRommSignOut: failed", e)
                _state.update { it.copy(rommSigningOut = false, rommConfigError = e.message) }
            }
        }
    }

    /** Probes the entered URL and auto-selects the version-appropriate auth method, mirroring the first-run wizard. */
    fun commitRommUrl(scope: CoroutineScope) {
        val state = _state.value
        if (state.rommConnecting || state.rommConfigUrl.isBlank()) return
        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }
            when (val result = romMRepository.probeServerVersion(state.rommConfigUrl)) {
                is RomMResult.Success -> {
                    val method = if (RomMCapabilities.from(result.data).supportsDeviceAuth) {
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
            connectWithPairingCode(state, onSuccess)
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
        val outcome = pollDeviceAuthUntilResolved(interval, expiresIn) {
            romMRepository.pollDeviceAuthOnce(deviceCode)
        }
        if (!currentCoroutineContext().isActive) return
        when (outcome) {
            is DeviceAuthOutcome.Approved -> {
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
            }
            DeviceAuthOutcome.Denied ->
                failPairing(context.getString(R.string.settings_server_delegate_pairing_denied))
            DeviceAuthOutcome.Expired ->
                failPairing(context.getString(R.string.settings_server_delegate_pairing_expired))
            is DeviceAuthOutcome.AddedAccount ->
                failPairing(context.getString(R.string.settings_server_delegate_pairing_unexpected_result))
            is DeviceAuthOutcome.Failed -> failPairing(outcome.message)
        }
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
                it.copy(
                    rommConnecting = false,
                    rommConfigError = context.getString(R.string.settings_server_delegate_pairing_code_incomplete)
                )
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

}
