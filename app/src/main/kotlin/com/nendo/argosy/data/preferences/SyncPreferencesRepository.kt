package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncPreferences(
    val rommBaseUrl: String? = null,
    val rommUsername: String? = null,
    val rommToken: String? = null,
    val rommDeviceId: String? = null,
    val rommDeviceClientVersion: String? = null,
    val raUsername: String? = null,
    val raToken: String? = null,
    val lastRommSync: Instant? = null,
    val lastFavoritesSync: Instant? = null,
    val lastFavoritesCheck: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val syncScreenshotsEnabled: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val stateCacheEnabled: Boolean = true,
    val saveCacheLimit: Int = 10,
    val saveWatcherEnabled: Boolean = false,
    val saveDebugLoggingEnabled: Boolean = false,
    val imageCachePath: String? = null,
    val androidDataSafUri: String? = null
)

@Singleton
class SyncPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ROMM_URL = stringPreferencesKey("romm_url")
        val ROMM_USERNAME = stringPreferencesKey("romm_username")
        val ROMM_TOKEN = stringPreferencesKey("romm_token")
        val ROMM_DEVICE_ID = stringPreferencesKey("romm_device_id")
        val ROMM_DEVICE_CLIENT_VERSION = stringPreferencesKey("romm_device_client_version")
        val RA_USERNAME = stringPreferencesKey("ra_username")
        val RA_TOKEN = stringPreferencesKey("ra_token")
        val LAST_ROMM_SYNC = stringPreferencesKey("last_romm_sync")
        val LAST_FAVORITES_SYNC = stringPreferencesKey("last_favorites_sync")
        val LAST_FAVORITES_CHECK = stringPreferencesKey("last_favorites_check")
        val SYNC_FILTER_REGIONS = stringPreferencesKey("sync_filter_regions")
        val SYNC_FILTER_REGION_MODE = stringPreferencesKey("sync_filter_region_mode")
        val SYNC_FILTER_EXCLUDE_BETA = booleanPreferencesKey("sync_filter_exclude_beta")
        val SYNC_FILTER_EXCLUDE_PROTO = booleanPreferencesKey("sync_filter_exclude_proto")
        val SYNC_FILTER_EXCLUDE_DEMO = booleanPreferencesKey("sync_filter_exclude_demo")
        val SYNC_FILTER_EXCLUDE_HACK = booleanPreferencesKey("sync_filter_exclude_hack")
        val SYNC_FILTER_DELETE_ORPHANS = booleanPreferencesKey("sync_filter_delete_orphans")
        val SYNC_SCREENSHOTS_ENABLED = booleanPreferencesKey("sync_screenshots_enabled")
        val SAVE_SYNC_ENABLED = booleanPreferencesKey("save_sync_enabled")
        val EXPERIMENTAL_FOLDER_SAVE_SYNC = booleanPreferencesKey("experimental_folder_save_sync")
        val STATE_CACHE_ENABLED = booleanPreferencesKey("state_cache_enabled")
        val SAVE_CACHE_LIMIT = intPreferencesKey("save_cache_limit")
        val SAVE_WATCHER_ENABLED = booleanPreferencesKey("save_watcher_enabled")
        val SAVE_DEBUG_LOGGING_ENABLED = booleanPreferencesKey("save_debug_logging_enabled")
        val IMAGE_CACHE_PATH = stringPreferencesKey("image_cache_path")
        val ANDROID_DATA_SAF_URI = stringPreferencesKey("android_data_saf_uri")
    }

    val preferences: Flow<SyncPreferences> = dataStore.data.map { prefs ->
        SyncPreferences(
            rommBaseUrl = prefs[Keys.ROMM_URL],
            rommUsername = prefs[Keys.ROMM_USERNAME],
            rommToken = prefs[Keys.ROMM_TOKEN],
            rommDeviceId = prefs[Keys.ROMM_DEVICE_ID],
            rommDeviceClientVersion = prefs[Keys.ROMM_DEVICE_CLIENT_VERSION],
            raUsername = prefs[Keys.RA_USERNAME],
            raToken = prefs[Keys.RA_TOKEN],
            lastRommSync = prefs[Keys.LAST_ROMM_SYNC]?.let { Instant.parse(it) },
            lastFavoritesSync = prefs[Keys.LAST_FAVORITES_SYNC]?.let { Instant.parse(it) },
            lastFavoritesCheck = prefs[Keys.LAST_FAVORITES_CHECK]?.let { Instant.parse(it) },
            syncFilters = SyncFilterPreferences(
                enabledRegions = prefs[Keys.SYNC_FILTER_REGIONS]
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    ?.toSet()
                    ?: SyncFilterPreferences.DEFAULT_REGIONS,
                regionMode = prefs[Keys.SYNC_FILTER_REGION_MODE]
                    ?.let { RegionFilterMode.valueOf(it) }
                    ?: RegionFilterMode.INCLUDE,
                excludeBeta = prefs[Keys.SYNC_FILTER_EXCLUDE_BETA] ?: true,
                excludePrototype = prefs[Keys.SYNC_FILTER_EXCLUDE_PROTO] ?: true,
                excludeDemo = prefs[Keys.SYNC_FILTER_EXCLUDE_DEMO] ?: true,
                excludeHack = prefs[Keys.SYNC_FILTER_EXCLUDE_HACK] ?: false,
                deleteOrphans = prefs[Keys.SYNC_FILTER_DELETE_ORPHANS] ?: true
            ),
            syncScreenshotsEnabled = prefs[Keys.SYNC_SCREENSHOTS_ENABLED] ?: false,
            saveSyncEnabled = prefs[Keys.SAVE_SYNC_ENABLED] ?: false,
            experimentalFolderSaveSync = prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] ?: false,
            stateCacheEnabled = prefs[Keys.STATE_CACHE_ENABLED] ?: true,
            saveCacheLimit = prefs[Keys.SAVE_CACHE_LIMIT] ?: 10,
            saveWatcherEnabled = prefs[Keys.SAVE_WATCHER_ENABLED] ?: false,
            saveDebugLoggingEnabled = prefs[Keys.SAVE_DEBUG_LOGGING_ENABLED] ?: false,
            imageCachePath = prefs[Keys.IMAGE_CACHE_PATH],
            androidDataSafUri = prefs[Keys.ANDROID_DATA_SAF_URI]
        )
    }

    fun saveWatcherEnabled(): Flow<Boolean> = dataStore.data.map {
        it[Keys.SAVE_WATCHER_ENABLED] ?: false
    }

    suspend fun setRommConfig(url: String?, username: String?) {
        dataStore.edit { prefs ->
            if (url != null) prefs[Keys.ROMM_URL] = url else prefs.remove(Keys.ROMM_URL)
            if (username != null) prefs[Keys.ROMM_USERNAME] = username else prefs.remove(Keys.ROMM_USERNAME)
        }
    }

    suspend fun setRomMCredentials(baseUrl: String, token: String, username: String? = null) {
        dataStore.edit { prefs ->
            val previousUrl = prefs[Keys.ROMM_URL]
            prefs[Keys.ROMM_URL] = baseUrl
            prefs[Keys.ROMM_TOKEN] = token
            if (username != null) prefs[Keys.ROMM_USERNAME] = username
            if (previousUrl != null && previousUrl != baseUrl) {
                prefs.remove(Keys.ROMM_DEVICE_ID)
                prefs.remove(Keys.ROMM_DEVICE_CLIENT_VERSION)
            }
        }
    }

    suspend fun clearRomMCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ROMM_URL)
            prefs.remove(Keys.ROMM_TOKEN)
            prefs.remove(Keys.ROMM_USERNAME)
            prefs.remove(Keys.ROMM_DEVICE_ID)
            prefs.remove(Keys.ROMM_DEVICE_CLIENT_VERSION)
        }
    }

    suspend fun setRommDeviceId(deviceId: String, clientVersion: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ROMM_DEVICE_ID] = deviceId
            prefs[Keys.ROMM_DEVICE_CLIENT_VERSION] = clientVersion
        }
    }

    suspend fun clearRommDeviceId() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ROMM_DEVICE_ID)
            prefs.remove(Keys.ROMM_DEVICE_CLIENT_VERSION)
        }
    }

    suspend fun setRACredentials(username: String, token: String) {
        dataStore.edit { prefs ->
            prefs[Keys.RA_USERNAME] = username
            prefs[Keys.RA_TOKEN] = token
        }
    }

    suspend fun clearRACredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.RA_USERNAME)
            prefs.remove(Keys.RA_TOKEN)
        }
    }

    suspend fun setLastRommSyncTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_ROMM_SYNC] = time.toString() }
    }

    suspend fun setLastFavoritesSyncTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_FAVORITES_SYNC] = time.toString() }
    }

    suspend fun setLastFavoritesCheckTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_FAVORITES_CHECK] = time.toString() }
    }

    suspend fun setSyncFilterRegions(regions: Set<String>) {
        dataStore.edit { it[Keys.SYNC_FILTER_REGIONS] = regions.joinToString(",") }
    }

    suspend fun setSyncFilterRegionMode(mode: RegionFilterMode) {
        dataStore.edit { it[Keys.SYNC_FILTER_REGION_MODE] = mode.name }
    }

    suspend fun setSyncFilterExcludeBeta(exclude: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_EXCLUDE_BETA] = exclude }
    }

    suspend fun setSyncFilterExcludePrototype(exclude: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_EXCLUDE_PROTO] = exclude }
    }

    suspend fun setSyncFilterExcludeDemo(exclude: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_EXCLUDE_DEMO] = exclude }
    }

    suspend fun setSyncFilterExcludeHack(exclude: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_EXCLUDE_HACK] = exclude }
    }

    suspend fun setSyncFilterDeleteOrphans(delete: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_DELETE_ORPHANS] = delete }
    }

    suspend fun setSyncScreenshotsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_SCREENSHOTS_ENABLED] = enabled }
    }

    suspend fun setSaveSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVE_SYNC_ENABLED] = enabled
            if (enabled) {
                prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] = true
            }
        }
    }

    suspend fun setExperimentalFolderSaveSync(enabled: Boolean) {
        dataStore.edit { it[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] = enabled }
    }

    suspend fun setStateCacheEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.STATE_CACHE_ENABLED] = enabled }
    }

    suspend fun setSaveCacheLimit(limit: Int) {
        dataStore.edit { it[Keys.SAVE_CACHE_LIMIT] = limit }
    }

    suspend fun setSaveWatcherEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SAVE_WATCHER_ENABLED] = enabled }
    }

    suspend fun setSaveDebugLoggingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SAVE_DEBUG_LOGGING_ENABLED] = enabled }
    }

    suspend fun setImageCachePath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.IMAGE_CACHE_PATH] = path
            else prefs.remove(Keys.IMAGE_CACHE_PATH)
        }
    }

    suspend fun setAndroidDataSafUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.ANDROID_DATA_SAF_URI] = uri
            else prefs.remove(Keys.ANDROID_DATA_SAF_URI)
        }
    }
}
