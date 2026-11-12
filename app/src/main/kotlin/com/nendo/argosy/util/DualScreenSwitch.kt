package com.nendo.argosy.util

import android.content.Context
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository

/**
 * Turns dual-screen mode on or off everywhere it is read: the stored preference, the live display
 * helper and the session store the companion checks at start. Turning it on also clears the
 * single-screen fallback latch.
 */
suspend fun applyDualScreenEnabled(
    context: Context,
    preferencesRepository: UserPreferencesRepository,
    displayAffinityHelper: DisplayAffinityHelper,
    enabled: Boolean
) {
    preferencesRepository.setDualScreenEnabled(enabled)
    displayAffinityHelper.dualScreenEnabled = enabled
    val sessionStore = SessionStateStore(context)
    sessionStore.setDualScreenEnabled(enabled)
    if (enabled) {
        sessionStore.setSecondaryDisplayUsable(true)
        displayAffinityHelper.secondaryDisplayUsable = true
    }
}
