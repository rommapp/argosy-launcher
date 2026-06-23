package com.nendo.argosy.ui.screens.launch

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.repository.GameRepository
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
    private val savedStateHandle: SavedStateHandle,
    private val gameRepository: GameRepository,
    private val gameLaunchDelegate: GameLaunchDelegate,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val displayAffinityHelper: DisplayAffinityHelper
) : ViewModel() {

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    val syncOverlayState: StateFlow<SyncOverlayState?> = gameLaunchDelegate.syncOverlayState
    val discPickerState: StateFlow<DiscPickerState?> = gameLaunchDelegate.discPickerState
    val memcardPickerState: StateFlow<com.nendo.argosy.ui.screens.common.MemcardPickerState?> =
        gameLaunchDelegate.memcardPickerState

    private val _gameTitle = MutableStateFlow("")
    val gameTitle: StateFlow<String> = _gameTitle.asStateFlow()

    private val _launchIntent = MutableStateFlow<Intent?>(null)
    val launchIntent: StateFlow<Intent?> = _launchIntent.asStateFlow()

    private var hasLaunchedEmulator: Boolean
        get() = savedStateHandle["hasLaunchedEmulator"] ?: false
        set(value) { savedStateHandle["hasLaunchedEmulator"] = value }

    private var emulatorLaunchTime: Long
        get() = savedStateHandle["emulatorLaunchTime"] ?: 0L
        set(value) { savedStateHandle["emulatorLaunchTime"] = value }

    private var wasBackgroundedSinceLaunch: Boolean
        get() = savedStateHandle["wasBackgroundedSinceLaunch"] ?: false
        set(value) { savedStateHandle["wasBackgroundedSinceLaunch"] = value }

    fun markBackgrounded() {
        if (hasLaunchedEmulator) wasBackgroundedSinceLaunch = true
    }

    private val _launchOptions = MutableStateFlow<Bundle?>(null)
    val launchOptions: StateFlow<Bundle?> = _launchOptions.asStateFlow()

    private val _isSessionEnded = MutableStateFlow(false)
    val isSessionEnded: StateFlow<Boolean> = _isSessionEnded.asStateFlow()

    val hasActiveSession: StateFlow<Boolean> = playSessionTracker.hasActiveSession

    fun startLaunchFlow(gameId: Long, channelName: String?, discId: Long?) {
        if (hasLaunchedEmulator) {
            android.util.Log.d("LaunchViewModel", "startLaunchFlow skipped: already launched this gameId=$gameId (route restore)")
            _isSessionEnded.value = true
            return
        }
        viewModelScope.launch {
            val game = gameRepository.getById(gameId)
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
                    hasLaunchedEmulator = true
                    emulatorLaunchTime = System.currentTimeMillis()
                    _launchOptions.value = options
                    _launchIntent.value = intent
                },
                onLaunchFailed = {
                    _isSessionEnded.value = true
                }
            )
        }
    }

    fun handleSessionEnd(onComplete: () -> Unit, force: Boolean = false) {
        if (!hasLaunchedEmulator) return
        if (!force && !wasBackgroundedSinceLaunch) return
        gameLaunchDelegate.endSessionInBackground()
        _isSessionEnded.value = true
        onComplete()
    }

    fun selectDisc(discPath: String) {
        gameLaunchDelegate.selectDisc(viewModelScope, discPath)
    }

    fun dismissDiscPicker() {
        gameLaunchDelegate.dismissDiscPicker()
    }

    fun selectMemcard(cardPath: String) {
        gameLaunchDelegate.selectMemcard(viewModelScope, cardPath)
    }

    fun dismissMemcardPicker() {
        gameLaunchDelegate.dismissMemcardPicker()
    }

    fun clearLaunchIntent() {
        _launchIntent.value = null
        _launchOptions.value = null
    }
}
