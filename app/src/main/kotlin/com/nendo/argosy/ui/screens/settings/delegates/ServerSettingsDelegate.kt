package com.nendo.argosy.ui.screens.settings.delegates

import android.util.Log
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ServerSettingsDelegate"

class ServerSettingsDelegate @Inject constructor(
    private val romMRepository: RomMRepository
) {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

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

    fun startRommConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                rommConfiguring = true,
                rommConfigUrl = it.rommUrl,
                rommConfigUsername = it.rommUsername,
                rommConfigPassword = "",
                rommConfigError = null
            )
        }
        onFocusReset()
    }

    fun cancelRommConfig(onFocusReset: () -> Unit) {
        _state.update {
            it.copy(
                rommConfiguring = false,
                rommConfigUrl = "",
                rommConfigUsername = "",
                rommConfigPassword = "",
                rommConfigError = null
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

    fun clearRommFocusField() {
        _state.update { it.copy(rommFocusField = null) }
    }

    fun setRommFocusField(index: Int) {
        _state.update { it.copy(rommFocusField = index) }
    }

    fun connectToRomm(scope: CoroutineScope, onSuccess: suspend () -> Unit) {
        val state = _state.value
        if (state.rommConfigUrl.isBlank()) return

        scope.launch {
            _state.update { it.copy(rommConnecting = true, rommConfigError = null) }

            when (val result = romMRepository.connect(state.rommConfigUrl)) {
                is RomMResult.Success -> {
                    if (state.rommConfigUsername.isNotBlank() && state.rommConfigPassword.isNotBlank()) {
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
                    } else {
                        _state.update {
                            it.copy(rommConnecting = false, rommConfigError = "Username and password required")
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
}
