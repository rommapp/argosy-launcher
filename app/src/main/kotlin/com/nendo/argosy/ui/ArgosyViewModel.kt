package com.nendo.argosy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.notification.DownloadNotificationObserver
import com.nendo.argosy.ui.notification.NotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArgosyUiState(
    val isFirstRun: Boolean = true,
    val isLoading: Boolean = true,
    val nintendoButtonLayout: Boolean = false,
    val swapStartSelect: Boolean = false
)

data class DrawerState(
    val rommConnected: Boolean = false,
    val rommConnecting: Boolean = false,
    val downloadCount: Int = 0
)

data class DrawerItem(
    val route: String,
    val label: String
)

@HiltViewModel
class ArgosyViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    val gamepadInputHandler: GamepadInputHandler,
    val hapticManager: HapticFeedbackManager,
    val notificationManager: NotificationManager,
    downloadNotificationObserver: DownloadNotificationObserver,
    private val gameRepository: GameRepository,
    private val romMRepository: RomMRepository,
    downloadManager: DownloadManager
) : ViewModel() {

    init {
        downloadNotificationObserver.observe(viewModelScope)
        validateLocalFilesAtStartup()
    }

    private fun validateLocalFilesAtStartup() {
        viewModelScope.launch {
            val invalidated = gameRepository.validateLocalFiles()
            if (invalidated > 0) {
                android.util.Log.d("ArgosyViewModel", "Startup validation: $invalidated games had missing files")
            }
        }
    }

    val uiState: StateFlow<ArgosyUiState> = preferencesRepository.userPreferences
        .map { prefs ->
            ArgosyUiState(
                isFirstRun = !prefs.firstRunComplete,
                isLoading = false,
                nintendoButtonLayout = prefs.nintendoButtonLayout,
                swapStartSelect = prefs.swapStartSelect
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArgosyUiState()
        )

    val drawerState: StateFlow<DrawerState> = combine(
        romMRepository.connectionState,
        downloadManager.state
    ) { connection, downloads ->
        val downloadCount = (if (downloads.activeDownload != null) 1 else 0) + downloads.queue.size
        DrawerState(
            rommConnected = connection is RomMRepository.ConnectionState.Connected,
            rommConnecting = connection is RomMRepository.ConnectionState.Connecting,
            downloadCount = downloadCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrawerState()
    )

    val drawerItems = listOf(
        DrawerItem(Screen.Home.route, "Home"),
        DrawerItem(Screen.Library.route, "Library"),
        DrawerItem(Screen.Downloads.route, "Downloads"),
        DrawerItem(Screen.Apps.route, "Apps"),
        DrawerItem(Screen.Settings.route, "Settings")
    )

    private val _drawerFocusIndex = MutableStateFlow(0)
    val drawerFocusIndex: StateFlow<Int> = _drawerFocusIndex.asStateFlow()

    fun initDrawerFocus(currentRoute: String?, parentRoute: String? = null) {
        var index = drawerItems.indexOfFirst { it.route == currentRoute }
        if (index < 0 && parentRoute != null) {
            index = drawerItems.indexOfFirst { it.route == parentRoute }
        }
        _drawerFocusIndex.value = if (index >= 0) index else 0
    }

    fun createDrawerInputHandler(
        onNavigate: (String) -> Unit,
        onDismiss: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            if (_drawerFocusIndex.value > 0) {
                _drawerFocusIndex.update { it - 1 }
                return true
            }
            return false
        }

        override fun onDown(): Boolean {
            if (_drawerFocusIndex.value < drawerItems.lastIndex) {
                _drawerFocusIndex.update { it + 1 }
                return true
            }
            return false
        }

        override fun onLeft(): Boolean = false
        override fun onRight(): Boolean = false

        override fun onConfirm(): Boolean {
            onNavigate(drawerItems[_drawerFocusIndex.value].route)
            return true
        }

        override fun onBack(): Boolean {
            onDismiss()
            return true
        }

        override fun onMenu(): Boolean {
            onDismiss()
            return true
        }
    }

    fun onDrawerOpened() {
        viewModelScope.launch {
            romMRepository.checkConnection()
            if (romMRepository.connectionState.value is RomMRepository.ConnectionState.Connected) {
                romMRepository.processPendingSync()
            }
        }
    }
}
