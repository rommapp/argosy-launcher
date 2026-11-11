package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.launcher.GameNativeSyncFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class StoragePreferences(
    val romStoragePath: String? = null,
    val romStoragePathConfirmed: Boolean = false,
    val musicStoragePath: String? = null,
    val mediaStoragePath: String? = null,
    val maxConcurrentDownloads: Int = 1,
    val allowRemoteInstalls: Boolean = true,
    val autoStartDownloads: Boolean = true,
    val instantDownloadThresholdMb: Int = 50,
    val stageDownloadsInternally: Boolean = true,
    val folderNameFromRom: Boolean = false,
    val customBiosPath: String? = null,
    val weeklyIntegrityCheckEnabled: Boolean = true,
    val lastIntegrityCheckTime: Long? = null,
    val gameNativeSyncDirs: Map<GameNativeSyncFolder, String> = emptyMap()
)

@Singleton
class StoragePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ROM_STORAGE_PATH = stringPreferencesKey("rom_storage_path")
        val ROM_STORAGE_PATH_CONFIRMED = booleanPreferencesKey("rom_storage_path_confirmed")
        val MUSIC_STORAGE_PATH = stringPreferencesKey("music_storage_path")
        val MEDIA_STORAGE_PATH = stringPreferencesKey("media_storage_path")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val ALLOW_REMOTE_INSTALLS = booleanPreferencesKey("allow_remote_installs")
        val AUTO_START_DOWNLOADS = booleanPreferencesKey("auto_start_downloads")
        val INSTANT_DOWNLOAD_THRESHOLD_MB = intPreferencesKey("instant_download_threshold_mb")
        val STAGE_DOWNLOADS_INTERNALLY = booleanPreferencesKey("stage_downloads_internally")
        val FOLDER_NAME_FROM_ROM = booleanPreferencesKey("folder_name_from_rom")
        val CUSTOM_BIOS_PATH = stringPreferencesKey("custom_bios_path")
        val WEEKLY_INTEGRITY_CHECK = booleanPreferencesKey("weekly_integrity_check_enabled")
        val LAST_INTEGRITY_CHECK = longPreferencesKey("last_integrity_check_time")
        val GAMENATIVE_SYNC_DIR = stringPreferencesKey("gamenative_sync_dir")
        val GAMENATIVE_SYNC_DIR_BY_FOLDER = GameNativeSyncFolder.entries.associateWith {
            stringPreferencesKey("gamenative_sync_dir_${it.slug}")
        }
    }

    val preferences: Flow<StoragePreferences> = dataStore.data.map { prefs ->
        StoragePreferences(
            romStoragePath = prefs[Keys.ROM_STORAGE_PATH],
            romStoragePathConfirmed = prefs[Keys.ROM_STORAGE_PATH_CONFIRMED] ?: false,
            musicStoragePath = prefs[Keys.MUSIC_STORAGE_PATH],
            mediaStoragePath = prefs[Keys.MEDIA_STORAGE_PATH],
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 1,
            allowRemoteInstalls = prefs[Keys.ALLOW_REMOTE_INSTALLS] ?: true,
            autoStartDownloads = prefs[Keys.AUTO_START_DOWNLOADS] ?: true,
            instantDownloadThresholdMb = prefs[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] ?: 50,
            stageDownloadsInternally = prefs[Keys.STAGE_DOWNLOADS_INTERNALLY] ?: true,
            folderNameFromRom = prefs[Keys.FOLDER_NAME_FROM_ROM] ?: false,
            customBiosPath = prefs[Keys.CUSTOM_BIOS_PATH],
            weeklyIntegrityCheckEnabled = prefs[Keys.WEEKLY_INTEGRITY_CHECK] ?: true,
            lastIntegrityCheckTime = prefs[Keys.LAST_INTEGRITY_CHECK],
            gameNativeSyncDirs = Keys.GAMENATIVE_SYNC_DIR_BY_FOLDER.mapNotNull { (folder, key) ->
                prefs[key]?.takeIf { it.isNotBlank() }?.let { folder to it }
            }.toMap()
        )
    }

    suspend fun setRomStoragePath(path: String) {
        dataStore.edit { it[Keys.ROM_STORAGE_PATH] = path }
    }

    suspend fun confirmRomStoragePath(path: String) {
        dataStore.edit {
            it[Keys.ROM_STORAGE_PATH] = path
            it[Keys.ROM_STORAGE_PATH_CONFIRMED] = true
        }
    }

    suspend fun hasRomStoragePath(): Boolean =
        !dataStore.data.first()[Keys.ROM_STORAGE_PATH].isNullOrBlank()

    suspend fun confirmExistingRomStoragePath() {
        dataStore.edit { prefs ->
            if (!prefs[Keys.ROM_STORAGE_PATH].isNullOrBlank()) prefs[Keys.ROM_STORAGE_PATH_CONFIRMED] = true
        }
    }

    suspend fun setMusicStoragePath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.MUSIC_STORAGE_PATH] = path
            else prefs.remove(Keys.MUSIC_STORAGE_PATH)
        }
    }

    suspend fun setMediaStoragePath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.MEDIA_STORAGE_PATH] = path
            else prefs.remove(Keys.MEDIA_STORAGE_PATH)
        }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5) }
    }

    suspend fun setAllowRemoteInstalls(enabled: Boolean) {
        dataStore.edit { it[Keys.ALLOW_REMOTE_INSTALLS] = enabled }
    }

    suspend fun setAutoStartDownloads(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_START_DOWNLOADS] = enabled }
    }

    suspend fun setInstantDownloadThresholdMb(value: Int) {
        dataStore.edit { it[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] = value }
    }

    suspend fun setStageDownloadsInternally(enabled: Boolean) {
        dataStore.edit { it[Keys.STAGE_DOWNLOADS_INTERNALLY] = enabled }
    }

    suspend fun setFolderNameFromRom(enabled: Boolean) {
        dataStore.edit { it[Keys.FOLDER_NAME_FROM_ROM] = enabled }
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

    suspend fun setLastIntegrityCheckTime(timeMs: Long) {
        dataStore.edit { it[Keys.LAST_INTEGRITY_CHECK] = timeMs }
    }

    suspend fun setGameNativeSyncDir(folder: GameNativeSyncFolder, path: String?) {
        val key = Keys.GAMENATIVE_SYNC_DIR_BY_FOLDER.getValue(folder)
        dataStore.edit { prefs ->
            if (path != null) prefs[key] = path
            else prefs.remove(key)
        }
    }

    /**
     * Seeds gog, epic and amazon from the single pre-per-store folder, never steam, then drops the
     * legacy key. Idempotent: a per-store folder cleared afterwards stays cleared.
     */
    suspend fun migrateLegacyGameNativeSyncDir() {
        dataStore.edit { prefs ->
            val legacy = prefs[Keys.GAMENATIVE_SYNC_DIR] ?: return@edit
            if (legacy.isNotBlank()) {
                for (folder in LEGACY_CARRY_FORWARD_FOLDERS) {
                    val key = Keys.GAMENATIVE_SYNC_DIR_BY_FOLDER.getValue(folder)
                    if (prefs[key] == null) prefs[key] = legacy
                }
            }
            prefs.remove(Keys.GAMENATIVE_SYNC_DIR)
        }
    }

    private companion object {
        val LEGACY_CARRY_FORWARD_FOLDERS = listOf(
            GameNativeSyncFolder.GOG,
            GameNativeSyncFolder.EPIC,
            GameNativeSyncFolder.AMAZON
        )
    }
}
