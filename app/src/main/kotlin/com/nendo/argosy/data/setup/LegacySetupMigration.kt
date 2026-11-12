package com.nendo.argosy.data.setup

import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import javax.inject.Inject

class LegacySetupMigration @Inject constructor(
    private val appPreferences: AppPreferencesRepository,
    private val syncPreferences: SyncPreferencesRepository,
    private val storagePreferences: StoragePreferencesRepository
) {
    suspend fun run() {
        if (appPreferences.isLegacySetupMigrated()) return
        val finished = finishedBeforeUpdate()
        if (finished) {
            syncPreferences.confirmExistingSetupValues()
            storagePreferences.confirmExistingRomStoragePath()
        }
        appPreferences.finishLegacySetupMigration(finishedBeforeUpdate = finished)
    }

    private suspend fun finishedBeforeUpdate(): Boolean =
        appPreferences.isLegacyFirstRunComplete() ||
            (syncPreferences.hasRomMCredentials() && storagePreferences.hasRomStoragePath())
}
