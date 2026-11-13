package com.nendo.argosy.ui.screens.setup

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.DeviceAuthOutcome
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMErrorKind
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.pollDeviceAuthUntilResolved
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.ui.screens.settings.components.PAIRING_CODE_LENGTH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

internal fun RomMErrorKind?.setupSignInError(message: String): SetupError = when (this) {
    RomMErrorKind.UNTRUSTED_CERTIFICATE ->
        SetupError(textRes = R.string.firstrun_romm_error_untrusted_certificate, certificate = true)
    RomMErrorKind.IDENTITY_UNAVAILABLE, RomMErrorKind.SESSION_CHANGED ->
        SetupError(textRes = R.string.firstrun_romm_error_sign_in_interrupted)
    null -> SetupError(serverMessage = message)
}

internal fun RomMResult.Error.toProbeError(): SetupError = when (kind) {
    null -> SetupError(textRes = R.string.setup_library_address_error_unreachable)
    else -> kind.setupSignInError(message)
}

/**
 * The RomM sign-in on the library page: server probe, device approval polling, pairing code
 * exchange and certificate import.
 */
class RommSignInDelegate @Inject constructor(
    private val application: Application,
    private val romMRepository: RomMRepository,
    private val userCertStore: UserCertStore
) {
    private val _state = MutableStateFlow(RommSignInState())
    val state: StateFlow<RommSignInState> = _state.asStateFlow()

    private lateinit var scope: CoroutineScope
    private var devicePollJob: Job? = null
    private var summaryJob: Job? = null

    fun bind(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            userCertStore.certs.collect { certs -> _state.update { it.copy(importedCertCount = certs.size) } }
        }
    }

    fun prefill(url: String?, signedIn: Boolean) {
        _state.update {
            it.copy(
                url = it.url.ifBlank { url.orEmpty() },
                view = if (signedIn) LibraryView.SIGNED_IN else it.view
            )
        }
        if (signedIn) refreshSummary()
    }

    /**
     * Loads the signed-in library's game and platform counts unless they are loaded or loading. A
     * first call that fails waits for the RomM connection and tries once more.
     */
    fun refreshSummary() {
        if (_state.value.summaryLoaded || summaryJob?.isActive == true) return
        summaryJob = scope.launch {
            val first = romMRepository.getLibrarySummary()
            val summary = if (first is RomMResult.Success) {
                first
            } else {
                withTimeoutOrNull(SUMMARY_CONNECT_WAIT_MS) {
                    romMRepository.initialize()
                    romMRepository.connectionState.first { it is ConnectionState.Connected }
                    romMRepository.getLibrarySummary()
                } ?: first
            }
            when (summary) {
                is RomMResult.Success -> applySummary(summary.data)
                is RomMResult.Error -> _state.update { it.copy(error = summaryError(summary.message)) }
            }
        }
    }

    fun chooseRomm() {
        _state.update { it.copy(view = LibraryView.ADDRESS, error = null) }
    }

    fun setUrl(url: String) {
        _state.update { it.copy(url = url.trim(), error = null) }
    }

    fun setPairingCode(code: String) {
        _state.update { it.copy(pairingCode = code, error = null) }
    }

    val pairingCodeComplete: Boolean
        get() = normalized(_state.value.pairingCode).length == PAIRING_CODE_LENGTH

    fun commitUrl() {
        val current = _state.value
        if (current.isConnecting) return
        val url = current.url
        if (url.isBlank()) {
            _state.update { it.copy(error = SetupError(textRes = R.string.setup_library_address_error_blank)) }
            return
        }
        _state.update { it.copy(isConnecting = true, error = null) }
        scope.launch {
            when (val result = romMRepository.probeServerVersion(url)) {
                is RomMResult.Success -> {
                    val supportsDeviceAuth = RomMCapabilities.from(result.data).supportsDeviceAuth
                    _state.update { it.copy(supportsDeviceAuth = supportsDeviceAuth) }
                    if (supportsDeviceAuth) {
                        startDevicePairing(url)
                    } else {
                        _state.update { it.copy(isConnecting = false, view = LibraryView.PAIRING) }
                    }
                }
                is RomMResult.Error -> _state.update {
                    it.copy(isConnecting = false, error = result.toProbeError())
                }
            }
        }
    }

    fun importCertificate(path: String) {
        scope.launch {
            val failed = userCertStore.importFrom(path).isFailure
            _state.update {
                it.copy(error = if (failed) SetupError(textRes = R.string.firstrun_romm_certificate_import_failed) else null)
            }
        }
    }

    fun useManualPairingCode() {
        stopPolling()
        _state.update {
            it.copy(
                view = LibraryView.PAIRING,
                deviceUserCode = null,
                deviceVerificationUrl = null,
                isConnecting = false,
                error = null
            )
        }
    }

    fun useDevicePairing() {
        val current = _state.value
        if (current.isConnecting || !current.supportsDeviceAuth) return
        startDevicePairing(current.url)
    }

    fun connectWithPairingCode() {
        val current = _state.value
        if (current.isConnecting) return
        val code = normalized(current.pairingCode)
        if (code.length != PAIRING_CODE_LENGTH) {
            _state.update { it.copy(error = SetupError(textRes = R.string.firstrun_pairing_code_error_incomplete)) }
            return
        }
        _state.update { it.copy(isConnecting = true, error = null) }
        scope.launch {
            when (val result = romMRepository.exchangePairingCode(current.url, code)) {
                is RomMResult.Success -> onAuthSuccess()
                is RomMResult.Error -> _state.update {
                    it.copy(isConnecting = false, error = result.kind.setupSignInError(result.message), pairingCode = "")
                }
            }
        }
    }

    fun useDifferentServer() {
        stopPolling()
        _state.update { it.copy(view = LibraryView.ADDRESS, error = null, isConnecting = false) }
    }

    /**
     * Steps back one view inside the library page. False when the page has no earlier view and
     * Back leaves the page.
     */
    fun back(): Boolean {
        val view = _state.value.view
        val previous = when (view) {
            LibraryView.APPROVE, LibraryView.PAIRING -> LibraryView.ADDRESS
            LibraryView.ADDRESS -> LibraryView.CHOICE
            LibraryView.CHOICE, LibraryView.SIGNED_IN -> return false
        }
        stopPolling()
        _state.update {
            it.copy(
                view = previous,
                deviceUserCode = null,
                deviceVerificationUrl = null,
                isConnecting = false,
                error = null
            )
        }
        return true
    }

    private fun startDevicePairing(url: String) {
        devicePollJob?.cancel()
        devicePollJob = scope.launch {
            _state.update { it.copy(isConnecting = true, error = null) }
            when (val init = romMRepository.beginDeviceAuth(url)) {
                is RomMResult.Success -> {
                    val data = init.data
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            view = LibraryView.APPROVE,
                            deviceUserCode = data.userCode,
                            deviceVerificationUrl = data.verificationPathComplete,
                            browserAvailable = canOpenInBrowser(data.verificationPathComplete),
                            error = null
                        )
                    }
                    pollForDeviceToken(data.deviceCode, data.interval, data.expiresIn)
                }
                is RomMResult.Error -> _state.update {
                    it.copy(
                        isConnecting = false,
                        view = LibraryView.ADDRESS,
                        error = init.kind.setupSignInError(init.message)
                    )
                }
            }
        }
    }

    private suspend fun pollForDeviceToken(deviceCode: String, interval: Int, expiresIn: Int) {
        val outcome = pollDeviceAuthUntilResolved(interval, expiresIn) {
            romMRepository.pollDeviceAuthOnce(deviceCode)
        }
        if (!currentCoroutineContext().isActive) return
        when (outcome) {
            is DeviceAuthOutcome.Approved -> {
                _state.update { it.copy(deviceUserCode = null, deviceVerificationUrl = null) }
                onAuthSuccess()
            }
            DeviceAuthOutcome.Denied ->
                failDevicePairing(SetupError(textRes = R.string.firstrun_device_pairing_error_denied))
            DeviceAuthOutcome.Expired ->
                failDevicePairing(SetupError(textRes = R.string.firstrun_device_pairing_error_expired))
            is DeviceAuthOutcome.AddedAccount ->
                failDevicePairing(SetupError(textRes = R.string.firstrun_device_pairing_error_unexpected))
            is DeviceAuthOutcome.Failed -> failDevicePairing(outcome.kind.setupSignInError(outcome.message))
        }
    }

    private fun failDevicePairing(error: SetupError) {
        romMRepository.cancelDeviceAuth()
        _state.update {
            it.copy(
                view = LibraryView.ADDRESS,
                deviceUserCode = null,
                deviceVerificationUrl = null,
                isConnecting = false,
                error = error
            )
        }
    }

    private suspend fun onAuthSuccess() {
        when (val summary = romMRepository.getLibrarySummary()) {
            is RomMResult.Success -> {
                _state.update { it.copy(isConnecting = false, view = LibraryView.SIGNED_IN, pairingCode = "") }
                applySummary(summary.data)
            }
            is RomMResult.Error -> _state.update {
                it.copy(isConnecting = false, error = summaryError(summary.message))
            }
        }
    }

    private fun applySummary(summary: Pair<Int, Int>) {
        val (platformCount, gameCount) = summary
        _state.update {
            it.copy(gameCount = gameCount, platformCount = platformCount, summaryLoaded = true, error = null)
        }
    }

    private fun summaryError(message: String): SetupError =
        SetupError(textRes = R.string.firstrun_romm_success_error_library_fetch, arg = message)

    private fun stopPolling() {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
    }

    private fun canOpenInBrowser(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        return intent.resolveActivity(application.packageManager) != null
    }

    private fun normalized(code: String): String = code.replace("-", "").replace(" ", "")

    private companion object {
        const val SUMMARY_CONNECT_WAIT_MS = 15_000L
    }
}
