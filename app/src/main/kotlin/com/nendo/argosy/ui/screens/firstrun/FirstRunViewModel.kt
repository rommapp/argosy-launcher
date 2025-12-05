package com.nendo.argosy.ui.screens.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirstRunStep {
    WELCOME,
    ROMM_CHOICE,
    ROMM_LOGIN,
    ROMM_SUCCESS,
    ROM_PATH,
    COMPLETE
}

data class FirstRunUiState(
    val currentStep: FirstRunStep = FirstRunStep.WELCOME,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommPassword: String = "",
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val rommGameCount: Int = 0,
    val rommPlatformCount: Int = 0,
    val romStoragePath: String = "/storage/emulated/0/ROMs",
    val skippedRomm: Boolean = false
)

@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunUiState())
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.ROMM_CHOICE
                FirstRunStep.ROMM_CHOICE -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROM_PATH
                FirstRunStep.ROM_PATH -> FirstRunStep.COMPLETE
                FirstRunStep.COMPLETE -> FirstRunStep.COMPLETE
            }
            state.copy(currentStep = nextStep)
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_CHOICE -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_CHOICE
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROM_PATH -> if (state.skippedRomm) FirstRunStep.ROMM_CHOICE else FirstRunStep.ROMM_SUCCESS
                FirstRunStep.COMPLETE -> FirstRunStep.ROM_PATH
            }
            state.copy(currentStep = prevStep)
        }
    }

    fun skipRomm() {
        _uiState.update { state ->
            state.copy(
                currentStep = FirstRunStep.ROM_PATH,
                skippedRomm = true
            )
        }
    }

    fun setRommUrl(url: String) {
        _uiState.update { it.copy(rommUrl = url, connectionError = null) }
    }

    fun setRommUsername(username: String) {
        _uiState.update { it.copy(rommUsername = username, connectionError = null) }
    }

    fun setRommPassword(password: String) {
        _uiState.update { it.copy(rommPassword = password, connectionError = null) }
    }

    fun connectToRomm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }

            val url = _uiState.value.rommUrl
            val username = _uiState.value.rommUsername
            val password = _uiState.value.rommPassword

            val connectResult = romMRepository.connect(url)
            if (connectResult is RomMResult.Error) {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectionError = "Could not connect to server: ${connectResult.message}"
                    )
                }
                return@launch
            }

            val loginResult = romMRepository.login(username, password)
            when (loginResult) {
                is RomMResult.Success -> {
                    when (val summary = romMRepository.getLibrarySummary()) {
                        is RomMResult.Success -> {
                            val (platformCount, gameCount) = summary.data
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    currentStep = FirstRunStep.ROMM_SUCCESS,
                                    rommGameCount = gameCount,
                                    rommPlatformCount = platformCount
                                )
                            }
                        }
                        is RomMResult.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    connectionError = "Failed to fetch library: ${summary.message}"
                                )
                            }
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isConnecting = false,
                            connectionError = "Login failed: ${loginResult.message}"
                        )
                    }
                }
            }
        }
    }

    fun setRomStoragePath(path: String) {
        _uiState.update { it.copy(romStoragePath = path) }
    }

    fun chooseFolder() {
        // TODO: Launch SAF folder picker - requires Activity integration
    }

    fun completeSetup() {
        viewModelScope.launch {
            preferencesRepository.setRomStoragePath(_uiState.value.romStoragePath)
            preferencesRepository.setFirstRunComplete()
        }
    }
}
