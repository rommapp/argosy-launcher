package com.nendo.argosy

import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivityPreferencesObserver(
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientAudioManager: AmbientAudioManager,
    private val sessionStateStore: SessionStateStore,
    private val dualScreenManager: DualScreenManager,
    private val hasWindowFocus: () -> Boolean,
) {

    private var previousHomeApps: Set<String>? = null
    private var previousPrimaryColor: Int? = null

    fun collectIn(scope: CoroutineScope, onInputFocusChanged: (String) -> Unit) {
        scope.launch {
            preferencesRepository.preferences.collect { prefs ->
                Logger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.fileLoggingEnabled,
                    level = prefs.fileLogLevel
                )
                SaveDebugLogger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.saveDebugLoggingEnabled
                )
                ambientAudioManager.setEnabled(prefs.ambientAudioEnabled)
                ambientAudioManager.setVolume(prefs.ambientAudioVolume)
                ambientAudioManager.setShuffle(prefs.ambientAudioShuffle)
                ambientAudioManager.setAudioSource(prefs.ambientAudioUri)
                if (prefs.ambientAudioEnabled &&
                    prefs.ambientAudioUri != null &&
                    hasWindowFocus()
                ) {
                    ambientAudioManager.fadeIn()
                }

                if (previousHomeApps != null &&
                    prefs.secondaryHomeApps != previousHomeApps
                ) {
                    dualScreenManager.updateHomeApps(
                        prefs.secondaryHomeApps
                    )
                }
                previousHomeApps = prefs.secondaryHomeApps

                if (prefs.primaryColor != previousPrimaryColor) {
                    sessionStateStore.setPrimaryColor(prefs.primaryColor)
                }
                previousPrimaryColor = prefs.primaryColor

                sessionStateStore.setInputSwapPreferences(
                    swapAB = prefs.swapAB,
                    swapXY = prefs.swapXY,
                    swapStartSelect = prefs.swapStartSelect
                )

                val focusName = prefs.dualScreenInputFocus.name
                onInputFocusChanged(focusName)
                sessionStateStore.setDualScreenInputFocus(focusName)
            }
        }
    }
}
