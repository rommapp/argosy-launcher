package com.nendo.argosy.ui.input

import com.nendo.argosy.data.preferences.UserPreferences

fun applyFeedbackPreferences(
    prefs: UserPreferences,
    hapticManager: HapticFeedbackManager,
    soundManager: SoundFeedbackManager
) {
    hapticManager.setEnabled(prefs.hapticEnabled)
    hapticManager.setStrength(prefs.hapticStrength)
    soundManager.setEnabled(prefs.soundEnabled)
    soundManager.setVolume(prefs.soundVolume)
    soundManager.setSoundConfigs(prefs.soundConfigs)
}
