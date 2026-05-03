package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.QuayPassAnnouncementModal
import com.nendo.argosy.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuayPassAnnouncementViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val sessionDismissed = MutableStateFlow(false)

    /**
     * v1 PoC: shows on every cold start when social-linked + QuayPass off.
     * Within a session, dismissing suppresses until next cold start.
     * `quayPassAnnouncementSeen` pref is written but not read for suppression yet;
     * flipping to one-time-only is a single condition change here.
     */
    val shouldShow: StateFlow<Boolean> = combine(
        preferencesRepository.userPreferences,
        sessionDismissed
    ) { prefs, dismissed ->
        prefs.isSocialLinked && !prefs.quayPassEnabled && !dismissed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        sessionDismissed.value = true
        viewModelScope.launch {
            preferencesRepository.setQuayPassAnnouncementSeen(true)
        }
    }
}

@Composable
fun QuayPassAnnouncementHost(
    onNavigate: (String) -> Unit,
    viewModel: QuayPassAnnouncementViewModel = hiltViewModel()
) {
    val show by viewModel.shouldShow.collectAsState()
    if (!show) return
    QuayPassAnnouncementModal(
        onLearnMore = {
            viewModel.dismiss()
            onNavigate(Screen.QuayPassDetails.route)
        },
        onDismiss = {
            viewModel.dismiss()
        }
    )
}
