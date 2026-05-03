package com.nendo.argosy.ui.screens.quaypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuayPassDetailsState(
    val avatarConfigured: Boolean = false,
    val enabled: Boolean = false
)

@HiltViewModel
class QuayPassDetailsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val state: StateFlow<QuayPassDetailsState> = preferencesRepository.userPreferences
        .map { QuayPassDetailsState(it.quayPassAvatarConfigured, it.quayPassEnabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuayPassDetailsState())

    fun enableQuayPass() {
        viewModelScope.launch {
            val prefs = preferencesRepository.userPreferences.first()
            if (prefs.quayPassAvatarConfigured) {
                preferencesRepository.setQuayPassEnabled(true)
            }
        }
    }
}
