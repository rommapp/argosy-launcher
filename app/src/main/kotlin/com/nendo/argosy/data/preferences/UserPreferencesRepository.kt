package com.nendo.argosy.data.preferences

import android.util.Log
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

private const val TAG = "Preferences"

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        val ROMM_URL = stringPreferencesKey("romm_url")
        val ROMM_USERNAME = stringPreferencesKey("romm_username")
        val ROMM_TOKEN = stringPreferencesKey("romm_token")
        val ROM_STORAGE_PATH = stringPreferencesKey("rom_storage_path")

        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_COLOR = intPreferencesKey("primary_color")
        val SECONDARY_COLOR = intPreferencesKey("secondary_color")
        val TERTIARY_COLOR = intPreferencesKey("tertiary_color")

        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val NINTENDO_BUTTON_LAYOUT = booleanPreferencesKey("nintendo_button_layout")
        val SWAP_START_SELECT = booleanPreferencesKey("swap_start_select")
        val ANIMATION_SPEED = stringPreferencesKey("animation_speed")
        val LAST_ROMM_SYNC = stringPreferencesKey("last_romm_sync")

        val SYNC_FILTER_REGIONS = stringPreferencesKey("sync_filter_regions")
        val SYNC_FILTER_REGION_MODE = stringPreferencesKey("sync_filter_region_mode")
        val SYNC_FILTER_EXCLUDE_BETA = booleanPreferencesKey("sync_filter_exclude_beta")
        val SYNC_FILTER_EXCLUDE_PROTO = booleanPreferencesKey("sync_filter_exclude_proto")
        val SYNC_FILTER_EXCLUDE_DEMO = booleanPreferencesKey("sync_filter_exclude_demo")
        val SYNC_FILTER_DELETE_ORPHANS = booleanPreferencesKey("sync_filter_delete_orphans")
        val SYNC_SCREENSHOTS_ENABLED = booleanPreferencesKey("sync_screenshots_enabled")

        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val VISIBLE_SYSTEM_APPS = stringPreferencesKey("visible_system_apps")
        val APP_ORDER = stringPreferencesKey("app_order")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val url = prefs[Keys.ROMM_URL]
        val token = prefs[Keys.ROMM_TOKEN]
        val username = prefs[Keys.ROMM_USERNAME]
        Log.d(TAG, "Reading prefs: url=${url?.take(30)}, hasToken=${token != null}, username=$username")
        UserPreferences(
            firstRunComplete = prefs[Keys.FIRST_RUN_COMPLETE] ?: false,
            rommBaseUrl = url,
            rommUsername = username,
            rommToken = token,
            romStoragePath = prefs[Keys.ROM_STORAGE_PATH],
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            primaryColor = prefs[Keys.PRIMARY_COLOR],
            secondaryColor = prefs[Keys.SECONDARY_COLOR],
            tertiaryColor = prefs[Keys.TERTIARY_COLOR],
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            nintendoButtonLayout = prefs[Keys.NINTENDO_BUTTON_LAYOUT] ?: false,
            swapStartSelect = prefs[Keys.SWAP_START_SELECT] ?: false,
            animationSpeed = AnimationSpeed.fromString(prefs[Keys.ANIMATION_SPEED]),
            lastRommSync = prefs[Keys.LAST_ROMM_SYNC]?.let { Instant.parse(it) },
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
                deleteOrphans = prefs[Keys.SYNC_FILTER_DELETE_ORPHANS] ?: true
            ),
            syncScreenshotsEnabled = prefs[Keys.SYNC_SCREENSHOTS_ENABLED] ?: false,
            hiddenApps = prefs[Keys.HIDDEN_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            visibleSystemApps = prefs[Keys.VISIBLE_SYSTEM_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            appOrder = prefs[Keys.APP_ORDER]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    val preferences: Flow<UserPreferences> = userPreferences

    suspend fun setFirstRunComplete() {
        dataStore.edit { prefs ->
            prefs[Keys.FIRST_RUN_COMPLETE] = true
        }
    }

    suspend fun setRommConfig(url: String?, username: String?) {
        dataStore.edit { prefs ->
            if (url != null) prefs[Keys.ROMM_URL] = url else prefs.remove(Keys.ROMM_URL)
            if (username != null) prefs[Keys.ROMM_USERNAME] = username else prefs.remove(Keys.ROMM_USERNAME)
        }
    }

    suspend fun setRomMCredentials(baseUrl: String, token: String, username: String? = null) {
        Log.d(TAG, "Saving credentials: url=${baseUrl.take(30)}, tokenLength=${token.length}, username=$username")
        dataStore.edit { prefs ->
            prefs[Keys.ROMM_URL] = baseUrl
            prefs[Keys.ROMM_TOKEN] = token
            if (username != null) prefs[Keys.ROMM_USERNAME] = username
        }
        Log.d(TAG, "Credentials saved successfully")
    }

    suspend fun clearRomMCredentials() {
        Log.d(TAG, "CLEARING credentials")
        dataStore.edit { prefs ->
            prefs.remove(Keys.ROMM_URL)
            prefs.remove(Keys.ROMM_TOKEN)
            prefs.remove(Keys.ROMM_USERNAME)
        }
        Log.d(TAG, "Credentials cleared")
    }

    suspend fun setRomStoragePath(path: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ROM_STORAGE_PATH] = path
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setCustomColors(primary: Int?, secondary: Int?, tertiary: Int?) {
        dataStore.edit { prefs ->
            if (primary != null) prefs[Keys.PRIMARY_COLOR] = primary else prefs.remove(Keys.PRIMARY_COLOR)
            if (secondary != null) prefs[Keys.SECONDARY_COLOR] = secondary else prefs.remove(Keys.SECONDARY_COLOR)
            if (tertiary != null) prefs[Keys.TERTIARY_COLOR] = tertiary else prefs.remove(Keys.TERTIARY_COLOR)
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setNintendoButtonLayout(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NINTENDO_BUTTON_LAYOUT] = enabled
        }
    }

    suspend fun setSwapStartSelect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SWAP_START_SELECT] = enabled
        }
    }

    suspend fun setAnimationSpeed(speed: AnimationSpeed) {
        dataStore.edit { prefs ->
            prefs[Keys.ANIMATION_SPEED] = speed.name
        }
    }

    suspend fun setLastRommSyncTime(time: Instant) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_ROMM_SYNC] = time.toString()
        }
    }

    suspend fun setSyncFilterRegions(regions: Set<String>) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_REGIONS] = regions.joinToString(",")
        }
    }

    suspend fun setSyncFilterRegionMode(mode: RegionFilterMode) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_REGION_MODE] = mode.name
        }
    }

    suspend fun setSyncFilterExcludeBeta(exclude: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_EXCLUDE_BETA] = exclude
        }
    }

    suspend fun setSyncFilterExcludePrototype(exclude: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_EXCLUDE_PROTO] = exclude
        }
    }

    suspend fun setSyncFilterExcludeDemo(exclude: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_EXCLUDE_DEMO] = exclude
        }
    }

    suspend fun setSyncFilterDeleteOrphans(delete: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_DELETE_ORPHANS] = delete
        }
    }

    suspend fun setSyncScreenshotsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_SCREENSHOTS_ENABLED] = enabled
        }
    }

    suspend fun setHiddenApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) {
                prefs.remove(Keys.HIDDEN_APPS)
            } else {
                prefs[Keys.HIDDEN_APPS] = apps.joinToString(",")
            }
        }
    }

    suspend fun setVisibleSystemApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) {
                prefs.remove(Keys.VISIBLE_SYSTEM_APPS)
            } else {
                prefs[Keys.VISIBLE_SYSTEM_APPS] = apps.joinToString(",")
            }
        }
    }

    suspend fun setAppOrder(order: List<String>) {
        dataStore.edit { prefs ->
            if (order.isEmpty()) {
                prefs.remove(Keys.APP_ORDER)
            } else {
                prefs[Keys.APP_ORDER] = order.joinToString(",")
            }
        }
    }
}

data class UserPreferences(
    val firstRunComplete: Boolean = false,
    val rommBaseUrl: String? = null,
    val rommUsername: String? = null,
    val rommToken: String? = null,
    val romStoragePath: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val hapticEnabled: Boolean = true,
    val nintendoButtonLayout: Boolean = false,
    val swapStartSelect: Boolean = false,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val lastRommSync: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val syncScreenshotsEnabled: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList()
)

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

enum class AnimationSpeed {
    SLOW, NORMAL, FAST, OFF;

    companion object {
        fun fromString(value: String?): AnimationSpeed =
            entries.find { it.name == value } ?: NORMAL
    }
}
