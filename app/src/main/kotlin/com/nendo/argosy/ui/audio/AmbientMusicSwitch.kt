package com.nendo.argosy.ui.audio

import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.MusicRepeatMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmbientMusicSwitch @Inject constructor(
    private val controlsPreferences: ControlsPreferencesRepository,
    private val ambientAudioManager: AmbientAudioManager
) {
    suspend fun ensureEnabled() {
        if (controlsPreferences.preferences.first().ambientAudioEnabled) return
        controlsPreferences.setAmbientAudioEnabled(true)
        ambientAudioManager.setEnabled(true)
    }

    suspend fun setShuffle(shuffle: Boolean) {
        ambientAudioManager.setShuffle(shuffle)
        controlsPreferences.setAmbientAudioShuffle(shuffle)
    }

    suspend fun setRepeat(mode: MusicRepeatMode) {
        ambientAudioManager.setRepeat(mode)
        controlsPreferences.setAmbientAudioRepeat(mode)
    }

    suspend fun setPlayInBackground(enabled: Boolean) {
        ambientAudioManager.setPlayInBackground(enabled)
        controlsPreferences.setAmbientAudioPlayInBackground(enabled)
    }
}
