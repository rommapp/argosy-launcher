package com.nendo.argosy.ui.screens.settings.delegates

import android.util.Log
import com.nendo.argosy.data.repository.RALoginResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.ui.screens.settings.RASettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RASettingsDelegate"

class RASettingsDelegate @Inject constructor(
    private val raRepository: RetroAchievementsRepository
) {
    private val _state = MutableStateFlow(RASettingsState())
    val state: StateFlow<RASettingsState> = _state.asStateFlow()

    fun updateState(newState: RASettingsState) {
        _state.value = newState
    }

    fun initialize(scope: CoroutineScope) {
        scope.launch {
            val credentials = raRepository.getCredentials()
            _state.update {
                it.copy(
                    isLoggedIn = credentials != null,
                    username = credentials?.username
                )
            }
        }

        scope.launch {
            raRepository.observePendingCount().collect { count ->
                _state.update { it.copy(pendingAchievementsCount = count) }
            }
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

    fun login(scope: CoroutineScope, onFocusReset: () -> Unit) {
        val state = _state.value
        if (state.loginUsername.isBlank() || state.loginPassword.isBlank()) {
            _state.update { it.copy(loginError = "Username and password required") }
            return
        }

        scope.launch {
            _state.update { it.copy(isLoggingIn = true, loginError = null) }

            when (val result = raRepository.login(state.loginUsername, state.loginPassword)) {
                is RALoginResult.Success -> {
                    Log.d(TAG, "RA login successful for ${result.username}")
                    _state.update {
                        it.copy(
                            isLoggingIn = false,
                            isLoggedIn = true,
                            username = result.username,
                            showLoginForm = false,
                            loginUsername = "",
                            loginPassword = ""
                        )
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
                    username = null
                )
            }
            onFocusReset()
        }
    }
}
