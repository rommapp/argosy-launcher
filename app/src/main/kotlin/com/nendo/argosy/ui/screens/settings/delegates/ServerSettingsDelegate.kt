package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.remote.romm.DeviceAuthOutcome
import com.nendo.argosy.data.sync.AccountRemovalResult
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.pollDeviceAuthUntilResolved
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.local.entity.serverInstanceKey
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.RomMAddressAction
import com.nendo.argosy.ui.screens.settings.RomMAddressEditor
import com.nendo.argosy.ui.screens.settings.RomMAddressMenu
import com.nendo.argosy.ui.screens.settings.RomMAddressRole
import com.nendo.argosy.ui.screens.settings.RomMAddressRow
import com.nendo.argosy.ui.screens.settings.RomMAddressVerification
import com.nendo.argosy.ui.screens.settings.RomMAddressVerifyPrompt
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.ServerState
import com.nendo.argosy.ui.screens.settings.rommAddressActions
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
    private val accountRepository: RomMAccountRepository,
    private val userCertStore: com.nendo.argosy.data.remote.ssl.UserCertStore,
    private val notificationManager: NotificationManager,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var devicePollJob: Job? = null

    /**
     * A failure the user can act on reads as app-authored text, not as the server's.
     *
     * An untrusted chain arrives as a validator message naming neither the cause nor the remedy,
     * so passing it through leaves the reader with nothing connecting the failure to the
     * certificate they need to import.
     */
    private fun RomMResult.Error.describe(): String = when (kind) {
        com.nendo.argosy.data.remote.romm.RomMErrorKind.UNTRUSTED_CERTIFICATE ->
            context.getString(R.string.settings_romm_config_error_untrusted_certificate)
        null -> message
    }

    fun importCertificate(scope: CoroutineScope, path: String) {
        scope.launch {
            val result = userCertStore.importFrom(path)
            _state.update {
                it.copy(
                    rommConfigError = if (result.isSuccess) {
                        null
                    } else {
                        context.getString(R.string.settings_romm_config_certificate_import_failed)
                    },
                    importedCertCount = userCertStore.certs.value.size
                )
            }
        }
    }

    fun refreshCertificateCount() {
        _state.update { it.copy(importedCertCount = userCertStore.certs.value.size) }
    }

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

    /**
     * A signed-in account opens the form as its address cards; with no account row it is the
     * pairing form. The first row is the address the session is on, so a second stored address
     * lands in the second row whichever column it came from.
     */
    fun startRommConfig(scope: CoroutineScope, hasCamera: Boolean, onFocusReset: () -> Unit) {
        scope.launch {
            val account = accountRepository.activeAccount()
            val rows = account?.let { addressRowsFrom(it) }.orEmpty()
            _state.update {
                it.copy(
                    rommConfiguring = true,
                    rommEditingAddresses = account != null,
                    rommAuthMethod = defaultAuthMethod(),
                    rommConfigUrl = rows.firstOrNull()?.text ?: it.rommUrl,
                    rommAddressRows = rows,
                    rommAddressMenu = null,
                    rommAddressEditor = null,
                    rommAddressVerifyPrompt = null,
                    rommAddressVerifyFocusIndex = 0,
                    rommConfigPairingCode = "",
                    rommHasCamera = hasCamera,
                    rommConfigError = null,
                    rommDevicePairing = false,
                    rommDeviceUserCode = null,
                    rommDeviceVerificationUrl = null,
                    importedCertCount = userCertStore.certs.value.size
                )
            }
            onFocusReset()
        }
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
                rommEditingAddresses = false,
                rommConfigUrl = "",
                rommAddressRows = emptyList(),
                rommAddressMenu = null,
                rommAddressEditor = null,
                rommAddressVerifyPrompt = null,
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
        val state = _state.value
        if (state.rommEditingAddresses && state.rommAddressRows.isNotEmpty()) {
            setAddressText(0, url)
        } else {
            _state.update { it.copy(rommConfigUrl = url) }
        }
    }

    fun setAddressText(row: Int, text: String) {
        updateRows { rows -> rows.mapIndexed { i, r -> if (i == row) r.copy(text = text) else r } }
    }

    fun openAddressMenu(row: Int) {
        val state = _state.value
        if (!state.rommEditingAddresses || state.rommAddressRows.getOrNull(row) == null) return
        if (state.rommAddressEditor != null || state.rommAddressVerifyPrompt != null) return
        _state.update { it.copy(rommAddressMenu = RomMAddressMenu(row)) }
    }

    fun moveAddressMenuFocus(delta: Int) {
        _state.update { state ->
            val menu = state.rommAddressMenu ?: return@update state
            val count = rommAddressActions(state.rommAddressRows, menu.row).size
            if (count == 0) {
                state
            } else {
                state.copy(rommAddressMenu = menu.copy(focusIndex = (menu.focusIndex + delta).mod(count)))
            }
        }
    }

    fun closeAddressMenu() {
        _state.update { it.copy(rommAddressMenu = null) }
    }

    fun selectAddressAction(scope: CoroutineScope, index: Int? = null) {
        val state = _state.value
        val menu = state.rommAddressMenu ?: return
        val action = rommAddressActions(state.rommAddressRows, menu.row).getOrNull(index ?: menu.focusIndex) ?: return
        _state.update { it.copy(rommAddressMenu = null) }
        when (action) {
            RomMAddressAction.EDIT -> openAddressEditor(menu.row)
            RomMAddressAction.VERIFY -> verifyAddress(scope, menu.row)
            RomMAddressAction.USE_AS_LOCAL, RomMAddressAction.USE_AS_REMOTE -> swapAddressRoles(scope)
            RomMAddressAction.REMOVE -> removeAddressRow(scope)
        }
    }

    /**
     * Reveals the second address and opens its editor. It starts as the local address because
     * the existing single address is the one already reaching the server from wherever the
     * device is; the action menu flips that if the user knows better. A row the editor never
     * saves is dropped again when the editor closes.
     */
    fun addAddress() {
        val state = _state.value
        val rows = state.rommAddressRows
        if (!state.rommEditingAddresses || rows.size != 1 || state.rommAddressEditor != null) return
        updateRows {
            listOf(
                rows[0].copy(role = RomMAddressRole.REMOTE),
                RomMAddressRow(text = "", stored = "", role = RomMAddressRole.LOCAL)
            )
        }
        openAddressEditor(1)
    }

    fun openAddressEditor(row: Int) {
        val state = _state.value
        if (!state.rommEditingAddresses || state.rommAddressRows.getOrNull(row) == null) return
        _state.update { it.copy(rommAddressMenu = null, rommAddressEditor = RomMAddressEditor(row)) }
    }

    /**
     * Abandons the draft: the row's text goes back to what its column stores, and a row that was
     * never committed disappears. Refused while a save is in flight so the write that lands
     * afterwards still has a row to report into.
     */
    fun closeAddressEditor() {
        val state = _state.value
        val editor = state.rommAddressEditor ?: return
        if (state.rommAddressVerifyPrompt != null) return
        if (state.rommAddressRows.getOrNull(editor.row)?.saving == true) return
        _state.update { it.copy(rommAddressEditor = null) }
        updateRows { rows -> rows.filter { it.stored.isNotBlank() }.map { it.copy(text = it.stored) } }
    }

    fun moveAddressEditorRow(row: TextEntryRow) {
        _state.update { state ->
            val editor = state.rommAddressEditor ?: return@update state
            state.copy(rommAddressEditor = editor.copy(focus = editor.focus.copy(row = row)))
        }
    }

    fun moveAddressEditorButton(delta: Int) {
        _state.update { state ->
            val editor = state.rommAddressEditor ?: return@update state
            if (editor.focus.row != TextEntryRow.BUTTONS) return@update state
            val next = (editor.focus.buttonIndex + delta).coerceIn(TEXT_ENTRY_CANCEL_BUTTON, TEXT_ENTRY_CONFIRM_BUTTON)
            state.copy(rommAddressEditor = editor.copy(focus = editor.focus.copy(buttonIndex = next)))
        }
    }

    fun confirmAddressEditor(scope: CoroutineScope) {
        val editor = _state.value.rommAddressEditor ?: return
        when {
            editor.focus.row == TextEntryRow.FIELD -> moveAddressEditorRow(TextEntryRow.BUTTONS)
            editor.focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> closeAddressEditor()
            else -> saveAddress(scope, editor.row)
        }
    }

    /**
     * Probes the stored address without writing anything; the card subtitle carries the
     * outcome and a failure is announced rather than asked about.
     */
    fun verifyAddress(scope: CoroutineScope, row: Int) {
        val current = _state.value.rommAddressRows.getOrNull(row) ?: return
        if (current.saving || current.stored.isBlank()) return
        scope.launch {
            setSaving(row, true)
            when (val result = romMRepository.validateAddress(current.stored)) {
                is RomMResult.Success -> {
                    setVerification(row, RomMAddressVerification.VERIFIED)
                    notificationManager.showSuccess(NotificationText.Res(R.string.settings_romm_config_notif_verified))
                }
                is RomMResult.Error -> {
                    setVerification(row, RomMAddressVerification.UNVERIFIED)
                    notificationManager.showError(
                        NotificationText.Res(
                            R.string.settings_romm_config_notif_verify_failed,
                            listOf(current.stored, result.describe())
                        )
                    )
                }
            }
            setSaving(row, false)
        }
    }

    /**
     * Collapses back to one address: the local column is cleared and the remaining row is
     * rebuilt from what the account still stores, so a role flip that was never saved cannot
     * leave a card showing an address that no column holds.
     */
    fun removeAddressRow(scope: CoroutineScope) {
        val state = _state.value
        if (!state.rommEditingAddresses || state.rommAddressRows.size != 2 || state.rommAddressVerifyPrompt != null) return
        if (state.rommAddressRows.any { it.saving }) return
        val localRow = state.rommAddressRows.indexOfFirst { it.role == RomMAddressRole.LOCAL }
        if (localRow < 0) return
        scope.launch {
            setSaving(localRow, true)
            val account = accountRepository.activeAccount()
            if (account == null) {
                setSaving(localRow, false)
                notificationManager.showError(NotificationText.Res(R.string.settings_romm_config_notif_save_failed))
                return@launch
            }
            accountRepository.setLanAddressForInstance(account.baseUrl, null)
            val refreshed = accountRepository.activeAccount() ?: account
            updateRows { addressRowsFrom(refreshed) }
            notificationManager.show(NotificationText.Res(R.string.settings_romm_config_notif_removed))
            reconnectNow()
        }
    }

    /**
     * Flips which row is local. The stored values follow their rows into the other column, with
     * the one exception that a row never committed cannot empty the remote column: that column
     * keeps its value until a real save replaces it.
     */
    fun swapAddressRoles(scope: CoroutineScope) {
        val state = _state.value
        if (!state.rommEditingAddresses || state.rommAddressRows.size != 2 || state.rommAddressVerifyPrompt != null) return
        if (state.rommAddressRows.any { it.saving }) return
        val swapped = state.rommAddressRows.map {
            it.copy(
                role = when (it.role) {
                    RomMAddressRole.LOCAL -> RomMAddressRole.REMOTE
                    RomMAddressRole.REMOTE -> RomMAddressRole.LOCAL
                }
            )
        }
        updateRows { swapped }
        val newLocal = swapped.first { it.role == RomMAddressRole.LOCAL }
        val newRemote = swapped.first { it.role == RomMAddressRole.REMOTE }
        if (newLocal.stored.isBlank() && newRemote.stored.isBlank()) return
        scope.launch {
            val account = accountRepository.activeAccount() ?: return@launch
            accountRepository.setLanAddressForInstance(account.baseUrl, newLocal.stored.takeIf { it.isNotBlank() })
            if (newRemote.stored.isNotBlank()) accountRepository.setWanAddress(account.id, newRemote.stored)
            reconnectNow()
        }
    }

    /**
     * Verifies the typed address against the signed-in server before storing it in the column
     * the row's role names. A probe failure asks rather than refuses: a remote address is
     * routinely unreachable from inside its own network. A successful save closes the editor.
     */
    fun saveAddress(scope: CoroutineScope, row: Int) {
        val state = _state.value
        if (!state.rommEditingAddresses || state.rommAddressVerifyPrompt != null) return
        val current = state.rommAddressRows.getOrNull(row) ?: return
        if (current.saving) return
        val typed = RomMConnectionManager.withDefaultScheme(current.text)
        if (typed.isBlank()) {
            notificationManager.showError(NotificationText.Res(R.string.settings_romm_config_notif_address_required))
            return
        }
        scope.launch {
            setSaving(row, true)
            when (val result = romMRepository.validateAddress(typed)) {
                is RomMResult.Success -> {
                    if (commitAddress(row, result.data, RomMAddressVerification.VERIFIED)) {
                        notificationManager.showSuccess(NotificationText.Res(R.string.settings_romm_config_notif_saved))
                        reconnectNow()
                    }
                }
                is RomMResult.Error -> {
                    setSaving(row, false)
                    _state.update {
                        it.copy(
                            rommAddressVerifyPrompt = RomMAddressVerifyPrompt(row, typed, result.describe()),
                            rommAddressVerifyFocusIndex = 0
                        )
                    }
                }
            }
        }
    }

    fun moveAddressVerifyFocus(delta: Int) {
        _state.update {
            it.copy(rommAddressVerifyFocusIndex = (it.rommAddressVerifyFocusIndex + delta).coerceIn(0, 1))
        }
    }

    fun keepUnverifiedAddress(scope: CoroutineScope) {
        val prompt = _state.value.rommAddressVerifyPrompt ?: return
        _state.update { it.copy(rommAddressVerifyPrompt = null) }
        scope.launch {
            setSaving(prompt.row, true)
            if (commitAddress(prompt.row, prompt.url, RomMAddressVerification.UNVERIFIED)) {
                notificationManager.show(
                    title = NotificationText.Res(R.string.settings_romm_config_notif_saved_unverified),
                    type = NotificationType.WARNING
                )
            }
        }
    }

    /**
     * Drops the prompt and returns to the editor with the draft intact, so the address can be
     * corrected rather than retyped.
     */
    fun cancelUnverifiedAddress() {
        if (_state.value.rommAddressVerifyPrompt == null) return
        _state.update { it.copy(rommAddressVerifyPrompt = null) }
    }

    private fun addressRowsFrom(account: RomMAccountEntity): List<RomMAddressRow> {
        val remote = RomMAddressRow(text = account.baseUrl, stored = account.baseUrl, role = RomMAddressRole.REMOTE)
        val localUrl = account.lanBaseUrl?.takeIf { it.isNotBlank() } ?: return markInUse(listOf(remote))
        val local = RomMAddressRow(text = localUrl, stored = localUrl, role = RomMAddressRole.LOCAL)
        val rows = markInUse(listOf(local, remote))
        return rows.sortedByDescending { it.inUse }
    }

    private fun markInUse(rows: List<RomMAddressRow>): List<RomMAddressRow> {
        val inUseKey = serverInstanceKey(romMRepository.getBaseUrl())
        return rows.map { it.copy(inUse = it.stored.isNotBlank() && serverInstanceKey(it.stored) == inUseKey) }
    }

    private suspend fun commitAddress(row: Int, url: String, verification: RomMAddressVerification): Boolean {
        val role = _state.value.rommAddressRows.getOrNull(row)?.role
        val account = accountRepository.activeAccount()
        if (role == null || account == null) {
            setSaving(row, false)
            notificationManager.showError(NotificationText.Res(R.string.settings_romm_config_notif_save_failed))
            return false
        }
        when (role) {
            RomMAddressRole.REMOTE -> accountRepository.setWanAddress(account.id, url)
            RomMAddressRole.LOCAL -> accountRepository.setLanAddressForInstance(account.baseUrl, url)
        }
        updateRows { rows ->
            rows.mapIndexed { i, r ->
                if (i == row) r.copy(saving = false, text = url, stored = url, verification = verification) else r
            }
        }
        _state.update { it.copy(rommAddressEditor = null) }
        return true
    }

    private suspend fun reconnectNow() {
        val result = romMRepository.reconnectWithStoredAddresses()
        if (result is RomMResult.Success) {
            _state.update { it.copy(rommUrl = result.data) }
        }
        updateRows { rows -> markInUse(rows) }
    }

    private fun updateRows(transform: (List<RomMAddressRow>) -> List<RomMAddressRow>) {
        _state.update {
            val rows = transform(it.rommAddressRows)
            it.copy(rommAddressRows = rows, rommConfigUrl = rows.firstOrNull()?.text ?: it.rommConfigUrl)
        }
    }

    private fun setSaving(row: Int, saving: Boolean) {
        updateRows { rows -> rows.mapIndexed { i, r -> if (i == row) r.copy(saving = saving) else r } }
    }

    private fun setVerification(row: Int, verification: RomMAddressVerification) {
        updateRows { rows -> rows.mapIndexed { i, r -> if (i == row) r.copy(verification = verification) else r } }
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

    fun confirmRommSignOut(
        scope: CoroutineScope,
        discardUnflushed: Boolean = false,
        onSignedOut: suspend () -> Unit
    ) {
        if (_state.value.rommSigningOut) return
        scope.launch {
            _state.update {
                it.copy(
                    showRommSignOutConfirm = false,
                    rommSignOutBlockedBy = null,
                    rommSigningOut = true
                )
            }
            try {
                val result = romMRepository.signOut(discardUnflushed)
                if (result is AccountRemovalResult.SwitchInProgress) {
                    _state.update {
                        it.copy(
                            rommSigningOut = false,
                            rommSignOutBlockedBy = context.getString(
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
                            rommSignOutBlockedBy = result.pending.describe()
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
                _state.update { it.copy(rommSigningOut = false, rommSignOutBlockedBy = e.message) }
            }
        }
    }

    fun dismissRommSignOutBlocked() {
        _state.update { it.copy(rommSignOutBlockedBy = null) }
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
                    _state.update { it.copy(rommConnecting = false, rommConfigError = result.describe()) }
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
                    _state.update { it.copy(rommConnecting = false, rommConfigError = init.describe()) }
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
                    it.copy(rommConnecting = false, rommConfigError = result.describe())
                }
            }
        }
    }

}
