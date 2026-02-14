package com.nendo.argosy.ui.screens.launch

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.screens.common.DiscPickerState
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.screens.common.SyncOverlayState
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LaunchViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val gameDao: GameDao,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val preferencesRepository: UserPreferencesRepository,
    private val displayAffinityHelper: DisplayAffinityHelper
) : ViewModel() {

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    val syncOverlayState: StateFlow<SyncOverlayState?> = gameLaunchDelegate.syncOverlayState
    val discPickerState: StateFlow<DiscPickerState?> = gameLaunchDelegate.discPickerState

    private val _gameTitle = MutableStateFlow("")
    val gameTitle: StateFlow<String> = _gameTitle.asStateFlow()

    private val _launchIntent = MutableStateFlow<Intent?>(null)
    val launchIntent: StateFlow<Intent?> = _launchIntent.asStateFlow()

    private val _launchOptions = MutableStateFlow<Bundle?>(null)
    val launchOptions: StateFlow<Bundle?> = _launchOptions.asStateFlow()

    private val _isSessionEnded = MutableStateFlow(false)
    val isSessionEnded: StateFlow<Boolean> = _isSessionEnded.asStateFlow()

    fun startLaunchFlow(gameId: Long, channelName: String?, discId: Long?) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId)
            _gameTitle.value = game?.title ?: "Game"

            val prefs = preferencesRepository.preferences.first()
            val options = if (prefs.appAffinityEnabled) {
                displayAffinityHelper.getActivityOptions(
                    forEmulator = true,
                    rolesSwapped = sessionStateStore.isRolesSwapped()
                )
            } else null

            gameLaunchDelegate.launchGame(
                scope = viewModelScope,
                gameId = gameId,
                discId = discId,
                channelName = channelName,
                onLaunch = { intent ->
                    _launchOptions.value = options
                    _launchIntent.value = intent
                }
            )
        }
    }

    fun handleSessionEnd(onComplete: () -> Unit) {
        gameLaunchDelegate.handleSessionEnd(
            scope = viewModelScope,
            onSyncComplete = {
                _isSessionEnded.value = true
                onComplete()
            }
        )
    }

    fun selectDisc(discPath: String) {
        gameLaunchDelegate.selectDisc(viewModelScope, discPath)
    }

    fun dismissDiscPicker() {
        gameLaunchDelegate.dismissDiscPicker()
    }

    fun clearLaunchIntent() {
        _launchIntent.value = null
        _launchOptions.value = null
    }
}
