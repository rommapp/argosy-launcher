package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.remote.navidrome.NavidromeApiClient
import com.nendo.argosy.data.remote.navidrome.NavidromeApiException
import com.nendo.argosy.data.remote.navidrome.NavidromeCredentials
import com.nendo.argosy.ui.screens.settings.NavidromeConnectionStatus
import com.nendo.argosy.ui.screens.settings.NavidromeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nendo.argosy.util.ServerAddress
import javax.inject.Inject

/**
 * Owns the Navidrome settings state. Credentials reach the preferences only after the server
 * answers a ping with status ok for them; a refused or unreachable sign-in stores nothing.
 */
class NavidromeSettingsDelegate @Inject constructor(
    private val preferences: NavidromePreferencesRepository,
    private val api: NavidromeApiClient,
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(NavidromeState())
    val state: StateFlow<NavidromeState> = _state.asStateFlow()

    suspend fun hydrate() {
        val prefs = preferences.preferences.first()
        _state.update {
            it.copy(
                serverUrl = prefs.serverUrl.orEmpty(),
                configUrl = if (it.configuring) it.configUrl else prefs.serverUrl.orEmpty(),
                isSignedIn = prefs.isSignedIn,
                userName = prefs.username.orEmpty(),
                serverVersion = prefs.serverVersion
            )
        }
    }

    fun startServerConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(configuring = true, configUrl = it.serverUrl, configError = null, configFocusField = null)
        }
        onFocusReset()
    }

    fun setConfigUrl(url: String) {
        _state.update { it.copy(configUrl = url, configError = null) }
    }

    fun setConfigFocusField(field: Int?) {
        _state.update { it.copy(configFocusField = field) }
    }

    fun clearConfigFocusField() {
        _state.update { it.copy(configFocusField = null) }
    }

    fun cancelServerConfig(onFocusReset: () -> Unit) {
        _state.update { it.copy(configuring = false, configError = null, configFocusField = null) }
        onFocusReset()
    }

    fun commitServerConfig(scope: CoroutineScope, onFocusReset: () -> Unit) {
        val url = ServerAddress.withDefaultScheme(_state.value.configUrl).trimEnd('/')
        if (url.isBlank()) {
            _state.update {
                it.copy(configError = context.getString(R.string.settings_navidrome_config_error_empty))
            }
            return
        }
        scope.launch {
            val changed = url != _state.value.serverUrl
            preferences.setServerUrl(url)
            if (changed) preferences.clearCredentials()
            _state.update {
                it.copy(
                    serverUrl = url,
                    configuring = false,
                    configUrl = url,
                    configError = null,
                    configFocusField = null,
                    isSignedIn = if (changed) false else it.isSignedIn,
                    userName = if (changed) "" else it.userName,
                    serverVersion = if (changed) null else it.serverVersion,
                    signInError = null,
                    connectionStatus = if (changed) NavidromeConnectionStatus.UNKNOWN else it.connectionStatus,
                    connectionError = null
                )
            }
            onFocusReset()
        }
    }

    fun showLoginForm(onFocusReset: () -> Unit) {
        if (!_state.value.hasServer) return
        _state.update {
            it.copy(
                showLoginForm = true,
                loginUsername = it.userName,
                loginPassword = "",
                loginFocusField = null,
                isSigningIn = false,
                signInError = null
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
                loginFocusField = null,
                isSigningIn = false,
                signInError = null
            )
        }
        onFocusReset()
    }

    fun setLoginUsername(username: String) {
        _state.update { it.copy(loginUsername = username, signInError = null) }
    }

    fun setLoginPassword(password: String) {
        _state.update { it.copy(loginPassword = password, signInError = null) }
    }

    fun setLoginFocusField(field: Int?) {
        _state.update { it.copy(loginFocusField = field) }
    }

    fun clearLoginFocusField() {
        _state.update { it.copy(loginFocusField = null) }
    }

    fun submitSignIn(scope: CoroutineScope, onFocusReset: () -> Unit) {
        val state = _state.value
        if (state.serverUrl.isBlank() || state.isSigningIn) return
        val username = state.loginUsername.trim()
        val password = state.loginPassword
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(signInError = context.getString(R.string.settings_navidrome_signin_error_empty)) }
            return
        }
        _state.update { it.copy(isSigningIn = true, signInError = null) }
        val credentials = NavidromeCredentials(state.serverUrl, username, password)
        scope.launch {
            api.ping(credentials)
                .onSuccess { info ->
                    preferences.setCredentials(username, password, info.serverVersion)
                    _state.update {
                        it.copy(
                            isSignedIn = true,
                            userName = username,
                            serverVersion = info.serverVersion,
                            showLoginForm = false,
                            loginUsername = "",
                            loginPassword = "",
                            loginFocusField = null,
                            isSigningIn = false,
                            signInError = null,
                            connectionStatus = NavidromeConnectionStatus.CONNECTED,
                            connectionError = null
                        )
                    }
                    onFocusReset()
                }
                .onFailure { error ->
                    _state.update { it.copy(isSigningIn = false, signInError = describe(error)) }
                }
        }
    }

    fun checkConnection(scope: CoroutineScope) {
        if (_state.value.connectionStatus == NavidromeConnectionStatus.CHECKING) return
        scope.launch {
            val prefs = preferences.preferences.first()
            val serverUrl = prefs.serverUrl
            val username = prefs.username
            val password = prefs.password
            if (serverUrl.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
                _state.update { it.copy(connectionStatus = NavidromeConnectionStatus.UNKNOWN, connectionError = null) }
                return@launch
            }
            _state.update { it.copy(connectionStatus = NavidromeConnectionStatus.CHECKING, connectionError = null) }
            api.ping(NavidromeCredentials(serverUrl, username, password))
                .onSuccess { info ->
                    if (info.serverVersion != prefs.serverVersion) {
                        preferences.setCredentials(username, password, info.serverVersion)
                    }
                    _state.update {
                        it.copy(
                            connectionStatus = NavidromeConnectionStatus.CONNECTED,
                            connectionError = null,
                            serverVersion = info.serverVersion
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(connectionStatus = NavidromeConnectionStatus.FAILED, connectionError = describe(error))
                    }
                }
        }
    }

    fun requestSignOut() {
        _state.update { it.copy(showSignOutConfirm = true) }
    }

    fun cancelSignOut() {
        _state.update { it.copy(showSignOutConfirm = false) }
    }

    fun confirmSignOut(scope: CoroutineScope) {
        _state.update { it.copy(showSignOutConfirm = false) }
        scope.launch {
            preferences.clearCredentials()
            _state.update {
                it.copy(
                    isSignedIn = false,
                    userName = "",
                    serverVersion = null,
                    signInError = null,
                    connectionStatus = NavidromeConnectionStatus.UNKNOWN,
                    connectionError = null
                )
            }
        }
    }

    private fun describe(error: Throwable): String = when (error) {
        is NavidromeApiException -> error.serverMessage
            ?: context.getString(R.string.settings_navidrome_signin_error_rejected)
        else -> context.getString(R.string.settings_navidrome_signin_error_unreachable)
    }
}
