package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class StoragePreferences(
    val romStoragePath: String? = null,
    val maxConcurrentDownloads: Int = 1,
    val instantDownloadThresholdMb: Int = 50,
    val customBiosPath: String? = null,
    val weeklyIntegrityCheckEnabled: Boolean = true
)

@Singleton
class StoragePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ROM_STORAGE_PATH = stringPreferencesKey("rom_storage_path")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val INSTANT_DOWNLOAD_THRESHOLD_MB = intPreferencesKey("instant_download_threshold_mb")
        val CUSTOM_BIOS_PATH = stringPreferencesKey("custom_bios_path")
        val WEEKLY_INTEGRITY_CHECK = booleanPreferencesKey("weekly_integrity_check_enabled")
    }

    val preferences: Flow<StoragePreferences> = dataStore.data.map { prefs ->
        StoragePreferences(
            romStoragePath = prefs[Keys.ROM_STORAGE_PATH],
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 1,
            instantDownloadThresholdMb = prefs[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] ?: 50,
            customBiosPath = prefs[Keys.CUSTOM_BIOS_PATH],
            weeklyIntegrityCheckEnabled = prefs[Keys.WEEKLY_INTEGRITY_CHECK] ?: true
        )
    }

    suspend fun setRomStoragePath(path: String) {
        dataStore.edit { it[Keys.ROM_STORAGE_PATH] = path }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5) }
    }

    suspend fun setInstantDownloadThresholdMb(value: Int) {
        dataStore.edit { it[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] = value }
    }

    suspend fun setCustomBiosPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.CUSTOM_BIOS_PATH] = path
            else prefs.remove(Keys.CUSTOM_BIOS_PATH)
        }
    }

    suspend fun setWeeklyIntegrityCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WEEKLY_INTEGRITY_CHECK] = enabled }
    }
}
