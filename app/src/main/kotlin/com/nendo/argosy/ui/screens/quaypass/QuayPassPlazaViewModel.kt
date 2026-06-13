package com.nendo.argosy.ui.screens.quaypass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.QuayPassRepository
import com.nendo.argosy.data.quaypass.QuayPassService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuayPassPlazaViewModel @Inject constructor(
    private val repository: QuayPassRepository,
    private val service: QuayPassService,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val encounters: StateFlow<List<QuayPassEncounterEntity>> =
        repository.observeEncounters()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ticketBalance: StateFlow<Int> =
        preferencesRepository.userPreferences
            .map { it.quayPassTicketBalance }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isServiceRunning: StateFlow<Boolean> = service.isRunning

    fun markAllSeen() {
        viewModelScope.launch { repository.markAllSeen() }
    }

    fun delete(fingerprint: String) {
        viewModelScope.launch { repository.deleteEncounter(fingerprint) }
    }
}
