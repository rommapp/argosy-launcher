package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.components.QuayPassAnnouncementModal
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.QuayPassAnnouncementInputHandler
import com.nendo.argosy.ui.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuayPassAnnouncementViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val sessionDismissed = MutableStateFlow(false)

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

    val inputDispatcher = LocalInputDispatcher.current
    var focusIndex by remember { mutableStateOf(1) }

    val handler = remember(viewModel) {
        QuayPassAnnouncementInputHandler(
            getFocusIndex = { focusIndex },
            onFocusChange = { focusIndex = it },
            onLearnMore = {
                viewModel.dismiss()
                onNavigate(Screen.QuayPassDetails.route)
            },
            onDismiss = { viewModel.dismiss() }
        )
    }

    LaunchedEffect(Unit) {
        focusIndex = 1
        inputDispatcher.pushModal(handler)
    }

    DisposableEffect(Unit) {
        onDispose { inputDispatcher.popModal() }
    }

    QuayPassAnnouncementModal(
        focusedButton = focusIndex,
        onLearnMore = {
            viewModel.dismiss()
            onNavigate(Screen.QuayPassDetails.route)
        },
        onDismiss = { viewModel.dismiss() }
    )
}
