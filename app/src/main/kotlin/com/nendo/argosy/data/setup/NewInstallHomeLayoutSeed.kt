package com.nendo.argosy.data.setup

import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Gives an install whose setup flow has never finished the Columns home layout, unless a layout is
 * already stored. Runs after the legacy setup migration, which marks upgraders finished.
 */
class NewInstallHomeLayoutSeed @Inject constructor(
    private val appPreferences: AppPreferencesRepository,
    private val displayPreferences: DisplayPreferencesRepository
) {
    suspend fun run() {
        if (appPreferences.preferences.first().setupFlowFinished) return
        displayPreferences.setHomeLayoutIfAbsent(HomeLayoutSettings(selected = HomeLayoutKind.COLUMNS))
    }
}
