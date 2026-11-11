package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.RALoginResult
import com.nendo.argosy.data.repository.RAWebApiKeyCheck
import com.nendo.argosy.data.repository.RAWebApiKeyRepository
import com.nendo.argosy.data.repository.RAWebKeyHealth
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.LibretroSettingsRepository
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.screens.settings.RASettingsState
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyAction
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyEditor
import com.nendo.argosy.ui.screens.settings.RAWebApiKeyStatus
import com.nendo.argosy.ui.util.ClipboardTextReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "RASettingsDelegate"

class RASettingsDelegate @Inject constructor(
    private val raRepository: RetroAchievementsRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val libretroSettingsRepo: LibretroSettingsRepository,
    private val webApiKeyRepository: RAWebApiKeyRepository,
    private val keyHealth: RAWebKeyHealth,
    private val clipboard: ClipboardTextReader,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(RASettingsState())
    val state: StateFlow<RASettingsState> = _state.asStateFlow()

    fun updateState(newState: RASettingsState) {
        _state.value = newState
    }

    fun initialize(scope: CoroutineScope) {
        scope.launch {
            val credentials = raRepository.getCredentials()
            val prefs = prefsRepository.userPreferences.first()
            val builtinPrefs = prefsRepository.getBuiltinEmulatorSettings().first()
            val canPush = raRepository.hasWritableRetroArchConfig()
            _state.update {
                it.copy(
                    isLoggedIn = credentials != null,
                    username = credentials?.username,
                    proxyEnabled = prefs.raProxyEnabled,
                    proxyAddress = prefs.raProxyAddress,
                    canPushToRetroArch = canPush,
                    defaultToHardcore = builtinPrefs.defaultToHardcore
                ).withStoredKey(prefs.raWebApiKey?.takeIf { key -> key.isNotBlank() })
            }
        }

        scope.launch {
            raRepository.observePendingCount().collect { count ->
                _state.update { it.copy(pendingAchievementsCount = count) }
            }
        }

        scope.launch {
            combine(
                prefsRepository.userPreferences.map { it.raWebApiKey },
                keyHealth.rejectedKey
            ) { storedKey, rejectedKey -> RAWebKeyHealth.isRejected(storedKey, rejectedKey) }
                .distinctUntilChanged()
                .collect { rejected -> _state.update { it.copy(webApiKeyRejected = rejected) } }
        }
    }

    fun showLoginForm(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                showLoginForm = true,
                loginUsername = "",
                loginPassword = "",
                loginError = null
            )
        }
        onFocusReset()
    }

    fun hideLoginForm(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                showLoginForm = false,
                loginUsername = "",
                loginPassword = "",
                loginError = null
            )
        }
        onFocusReset()
    }

    fun setLoginUsername(username: String) {
        _state.update { it.copy(loginUsername = username) }
    }

    fun setLoginPassword(password: String) {
        _state.update { it.copy(loginPassword = password) }
    }

    fun clearFocusField() {
        _state.update { it.copy(focusField = null) }
    }

    fun setFocusField(index: Int) {
        _state.update { it.copy(focusField = index) }
    }

    fun setProxyEnabled(scope: CoroutineScope, enabled: Boolean) {
        _state.update { it.copy(proxyEnabled = enabled) }
        scope.launch { prefsRepository.setRAProxy(enabled, _state.value.proxyAddress) }
    }

    fun setProxyAddress(scope: CoroutineScope, address: String) {
        _state.update { it.copy(proxyAddress = address) }
        scope.launch { prefsRepository.setRAProxy(_state.value.proxyEnabled, address) }
    }

    fun setDefaultToHardcore(scope: CoroutineScope, mode: String) {
        _state.update { it.copy(defaultToHardcore = mode) }
        scope.launch { libretroSettingsRepo.setBuiltinDefaultToHardcore(mode) }
    }

    fun login(scope: CoroutineScope, onFocusReset: () -> Unit) {
        val state = _state.value
        if (state.loginUsername.isBlank() || state.loginPassword.isBlank()) {
            _state.update {
                it.copy(loginError = context.getString(R.string.settings_ra_delegate_login_error_required))
            }
            return
        }

        scope.launch {
            _state.update { it.copy(isLoggingIn = true, loginError = null) }

            when (val result = raRepository.login(state.loginUsername, state.loginPassword)) {
                is RALoginResult.Success -> {
                    Log.d(TAG, "RA login successful for ${result.username}")
                    val storedKey = webApiKeyRepository.storedKey()
                    _state.update {
                        it.copy(
                            isLoggingIn = false,
                            isLoggedIn = true,
                            username = result.username,
                            showLoginForm = false,
                            loginUsername = "",
                            loginPassword = ""
                        ).withStoredKey(storedKey)
                    }
                    onFocusReset()
                }
                is RALoginResult.Error -> {
                    _state.update {
                        it.copy(isLoggingIn = false, loginError = result.message)
                    }
                }
            }
        }
    }

    fun logout(scope: CoroutineScope, onFocusReset: () -> Unit) {
        scope.launch {
            _state.update { it.copy(isLoggingOut = true) }
            raRepository.logout()
            _state.update {
                it.copy(
                    isLoggingOut = false,
                    isLoggedIn = false,
                    username = null,
                    webApiKeyEditor = null
                ).withStoredKey(null)
            }
            onFocusReset()
        }
    }

    fun pushToRetroArch(scope: CoroutineScope, onResult: (Int) -> Unit) {
        scope.launch { onResult(raRepository.pushCredentialsToRetroArch()) }
    }

    fun openWebApiKeyEditor() {
        _state.update {
            it.copy(webApiKeyEditor = RAWebApiKeyEditor(actions = RAWebApiKeyEditor.actionsFor(it.hasWebApiKey)))
        }
    }

    fun closeWebApiKeyEditor() {
        _state.update { state ->
            if (state.webApiKeyEditor?.status == RAWebApiKeyStatus.VALIDATING) state
            else state.copy(webApiKeyEditor = null)
        }
    }

    fun setWebApiKeyDraft(text: String) {
        updateEditor { editor ->
            if (editor.status == RAWebApiKeyStatus.VALIDATING) editor
            else editor.copy(draft = text, status = RAWebApiKeyStatus.IDLE)
        }
    }

    fun moveWebApiKeyRow(row: TextEntryRow) {
        updateEditor { it.copy(row = row) }
    }

    fun moveWebApiKeyButton(delta: Int) {
        updateEditor { editor ->
            if (editor.row != TextEntryRow.BUTTONS) editor
            else editor.copy(buttonIndex = (editor.buttonIndex + delta).mod(editor.actions.size))
        }
    }

    /**
     * The A press inside the editor: on the field it reopens the keyboard, on a button it runs it.
     */
    fun confirmWebApiKeyEditor(scope: CoroutineScope, onSaved: () -> Unit, onCleared: () -> Unit) {
        val editor = _state.value.webApiKeyEditor ?: return
        val action = editor.focusedAction
        if (action == null) {
            updateEditor { it.copy(keyboardRequest = it.keyboardRequest + 1) }
            return
        }
        runWebApiKeyAction(scope, action, onSaved, onCleared)
    }

    fun runWebApiKeyAction(
        scope: CoroutineScope,
        action: RAWebApiKeyAction,
        onSaved: () -> Unit,
        onCleared: () -> Unit
    ) {
        val editor = _state.value.webApiKeyEditor ?: return
        if (editor.status == RAWebApiKeyStatus.VALIDATING) return
        when (action) {
            RAWebApiKeyAction.PASTE -> pasteWebApiKey()
            RAWebApiKeyAction.CLEAR -> clearWebApiKey(scope, onCleared)
            RAWebApiKeyAction.CANCEL -> closeWebApiKeyEditor()
            RAWebApiKeyAction.SAVE -> saveWebApiKey(scope, onSaved)
        }
    }

    private fun pasteWebApiKey() {
        val text = clipboard.read()?.trim()?.takeIf { it.isNotEmpty() }
        updateEditor { editor ->
            if (text == null) editor.copy(status = RAWebApiKeyStatus.CLIPBOARD_EMPTY)
            else editor.copy(draft = text, status = RAWebApiKeyStatus.IDLE)
        }
    }

    private fun saveWebApiKey(scope: CoroutineScope, onSaved: () -> Unit) {
        val draft = _state.value.webApiKeyEditor?.draft?.trim().orEmpty()
        if (draft.isEmpty()) return
        updateEditor { it.copy(status = RAWebApiKeyStatus.VALIDATING) }
        scope.launch {
            when (webApiKeyRepository.validateAndSave(draft)) {
                RAWebApiKeyCheck.SAVED -> {
                    _state.update { it.copy(webApiKeyEditor = null).withStoredKey(draft) }
                    onSaved()
                }
                RAWebApiKeyCheck.REJECTED, RAWebApiKeyCheck.NOT_SIGNED_IN, RAWebApiKeyCheck.EMPTY ->
                    updateEditor { it.copy(status = RAWebApiKeyStatus.REJECTED) }
                RAWebApiKeyCheck.UNREACHABLE ->
                    updateEditor { it.copy(status = RAWebApiKeyStatus.UNREACHABLE) }
            }
        }
    }

    private fun clearWebApiKey(scope: CoroutineScope, onCleared: () -> Unit) {
        scope.launch {
            webApiKeyRepository.clear()
            _state.update { it.copy(webApiKeyEditor = null).withStoredKey(null) }
            onCleared()
        }
    }

    private fun updateEditor(transform: (RAWebApiKeyEditor) -> RAWebApiKeyEditor) {
        _state.update { state ->
            val editor = state.webApiKeyEditor ?: return@update state
            state.copy(webApiKeyEditor = transform(editor))
        }
    }

    private fun RASettingsState.withStoredKey(key: String?): RASettingsState = copy(
        hasWebApiKey = key != null,
        webApiKeySuffix = RAWebApiKeyRepository.lastCharsOf(key)
    )
}
