package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

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
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val SWAP_AB = booleanPreferencesKey("nintendo_button_layout")  // Keep old key for migration
        val SWAP_XY = booleanPreferencesKey("swap_xy")
        val AB_ICON_LAYOUT = stringPreferencesKey("ab_icon_layout")  // "auto", "xbox", "nintendo"
        val SWAP_START_SELECT = booleanPreferencesKey("swap_start_select")
        val ANIMATION_SPEED = stringPreferencesKey("animation_speed")
        val LAST_ROMM_SYNC = stringPreferencesKey("last_romm_sync")

        val SYNC_FILTER_REGIONS = stringPreferencesKey("sync_filter_regions")
        val SYNC_FILTER_REGION_MODE = stringPreferencesKey("sync_filter_region_mode")
        val SYNC_FILTER_EXCLUDE_BETA = booleanPreferencesKey("sync_filter_exclude_beta")
        val SYNC_FILTER_EXCLUDE_PROTO = booleanPreferencesKey("sync_filter_exclude_proto")
        val SYNC_FILTER_EXCLUDE_DEMO = booleanPreferencesKey("sync_filter_exclude_demo")
        val SYNC_FILTER_EXCLUDE_HACK = booleanPreferencesKey("sync_filter_exclude_hack")
        val SYNC_FILTER_DELETE_ORPHANS = booleanPreferencesKey("sync_filter_delete_orphans")
        val SYNC_SCREENSHOTS_ENABLED = booleanPreferencesKey("sync_screenshots_enabled")

        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val VISIBLE_SYSTEM_APPS = stringPreferencesKey("visible_system_apps")
        val APP_ORDER = stringPreferencesKey("app_order")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val SOUND_CONFIGS = stringPreferencesKey("sound_configs")
        val BETA_UPDATES_ENABLED = booleanPreferencesKey("beta_updates_enabled")
        val SAVE_SYNC_ENABLED = booleanPreferencesKey("save_sync_enabled")
        val EXPERIMENTAL_FOLDER_SAVE_SYNC = booleanPreferencesKey("experimental_folder_save_sync")
        val SAVE_CACHE_LIMIT = intPreferencesKey("save_cache_limit")

        val BACKGROUND_BLUR = intPreferencesKey("background_blur")
        val BACKGROUND_SATURATION = intPreferencesKey("background_saturation")
        val BACKGROUND_OPACITY = intPreferencesKey("background_opacity")
        val USE_GAME_BACKGROUND = booleanPreferencesKey("use_game_background")
        val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
    }

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            firstRunComplete = prefs[Keys.FIRST_RUN_COMPLETE] ?: false,
            rommBaseUrl = prefs[Keys.ROMM_URL],
            rommUsername = prefs[Keys.ROMM_USERNAME],
            rommToken = prefs[Keys.ROMM_TOKEN],
            romStoragePath = prefs[Keys.ROM_STORAGE_PATH],
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            primaryColor = prefs[Keys.PRIMARY_COLOR],
            secondaryColor = prefs[Keys.SECONDARY_COLOR],
            tertiaryColor = prefs[Keys.TERTIARY_COLOR],
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticIntensity = HapticIntensity.fromString(prefs[Keys.HAPTIC_INTENSITY]),
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 40,
            swapAB = prefs[Keys.SWAP_AB] ?: false,
            swapXY = prefs[Keys.SWAP_XY] ?: false,
            abIconLayout = prefs[Keys.AB_ICON_LAYOUT] ?: "auto",
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
                excludeHack = prefs[Keys.SYNC_FILTER_EXCLUDE_HACK] ?: false,
                deleteOrphans = prefs[Keys.SYNC_FILTER_DELETE_ORPHANS] ?: true
            ),
            syncScreenshotsEnabled = prefs[Keys.SYNC_SCREENSHOTS_ENABLED] ?: false,
            backgroundBlur = prefs[Keys.BACKGROUND_BLUR] ?: 40,
            backgroundSaturation = prefs[Keys.BACKGROUND_SATURATION] ?: 100,
            backgroundOpacity = prefs[Keys.BACKGROUND_OPACITY] ?: 100,
            useGameBackground = prefs[Keys.USE_GAME_BACKGROUND] ?: true,
            customBackgroundPath = prefs[Keys.CUSTOM_BACKGROUND_PATH],
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
                ?: emptyList(),
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 1,
            uiDensity = UiDensity.fromString(prefs[Keys.UI_DENSITY]),
            soundConfigs = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS]),
            betaUpdatesEnabled = prefs[Keys.BETA_UPDATES_ENABLED] ?: false,
            saveSyncEnabled = prefs[Keys.SAVE_SYNC_ENABLED] ?: false,
            experimentalFolderSaveSync = prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] ?: false,
            saveCacheLimit = prefs[Keys.SAVE_CACHE_LIMIT] ?: 10
        )
    }

    private fun parseSoundConfigs(raw: String?): Map<SoundType, SoundConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val soundType = try { SoundType.valueOf(parts[0]) } catch (e: Exception) { return@mapNotNull null }
                val value = parts[1]
                val config = when {
                    value.startsWith("custom:") -> SoundConfig(customFilePath = value.removePrefix("custom:"))
                    else -> SoundConfig(presetName = value)
                }
                soundType to config
            }
            .toMap()
    }

    private fun serializeSoundConfigs(configs: Map<SoundType, SoundConfig>): String {
        return configs.entries.joinToString(";") { (type, config) ->
            val value = when {
                config.customFilePath != null -> "custom:${config.customFilePath}"
                config.presetName != null -> config.presetName
                else -> return@joinToString ""
            }
            "${type.name}=$value"
        }
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
        dataStore.edit { prefs ->
            prefs[Keys.ROMM_URL] = baseUrl
            prefs[Keys.ROMM_TOKEN] = token
            if (username != null) prefs[Keys.ROMM_USERNAME] = username
        }
    }

    suspend fun clearRomMCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ROMM_URL)
            prefs.remove(Keys.ROMM_TOKEN)
            prefs.remove(Keys.ROMM_USERNAME)
        }
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

    suspend fun setHapticIntensity(intensity: HapticIntensity) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_INTENSITY] = intensity.name
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setSoundVolume(volume: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SOUND_VOLUME] = volume.coerceIn(0, 100)
        }
    }

    suspend fun setSwapAB(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SWAP_AB] = enabled
        }
    }

    suspend fun setSwapXY(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SWAP_XY] = enabled
        }
    }

    suspend fun setABIconLayout(layout: String) {
        dataStore.edit { prefs ->
            prefs[Keys.AB_ICON_LAYOUT] = layout
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

    suspend fun setSyncFilterExcludeHack(exclude: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SYNC_FILTER_EXCLUDE_HACK] = exclude
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

    suspend fun setMaxConcurrentDownloads(count: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5)
        }
    }

    suspend fun setUiDensity(density: UiDensity) {
        dataStore.edit { prefs ->
            prefs[Keys.UI_DENSITY] = density.name
        }
    }

    suspend fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) {
        dataStore.edit { prefs ->
            if (configs.isEmpty()) {
                prefs.remove(Keys.SOUND_CONFIGS)
            } else {
                prefs[Keys.SOUND_CONFIGS] = serializeSoundConfigs(configs)
            }
        }
    }

    suspend fun setSoundConfig(type: SoundType, config: SoundConfig?) {
        dataStore.edit { prefs ->
            val current = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS])
            val updated = if (config != null) {
                current + (type to config)
            } else {
                current - type
            }
            if (updated.isEmpty()) {
                prefs.remove(Keys.SOUND_CONFIGS)
            } else {
                prefs[Keys.SOUND_CONFIGS] = serializeSoundConfigs(updated)
            }
        }
    }

    suspend fun setBetaUpdatesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BETA_UPDATES_ENABLED] = enabled
        }
    }

    suspend fun setSaveSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVE_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setExperimentalFolderSaveSync(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] = enabled
        }
    }

    suspend fun setSaveCacheLimit(limit: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SAVE_CACHE_LIMIT] = limit
        }
    }

    suspend fun setBackgroundBlur(blur: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_BLUR] = blur.coerceIn(0, 100)
        }
    }

    suspend fun setBackgroundSaturation(saturation: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_SATURATION] = saturation.coerceIn(0, 100)
        }
    }

    suspend fun setBackgroundOpacity(opacity: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_OPACITY] = opacity.coerceIn(0, 100)
        }
    }

    suspend fun setUseGameBackground(use: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.USE_GAME_BACKGROUND] = use
        }
    }

    suspend fun setCustomBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.CUSTOM_BACKGROUND_PATH] = path
            else prefs.remove(Keys.CUSTOM_BACKGROUND_PATH)
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
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val abIconLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val lastRommSync: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val syncScreenshotsEnabled: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList(),
    val maxConcurrentDownloads: Int = 1,
    val uiDensity: UiDensity = UiDensity.NORMAL,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val betaUpdatesEnabled: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val saveCacheLimit: Int = 10,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null
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

enum class UiDensity {
    COMPACT, NORMAL, SPACIOUS;

    companion object {
        fun fromString(value: String?): UiDensity =
            entries.find { it.name == value } ?: NORMAL
    }
}

enum class HapticIntensity(val amplitude: Int) {
    LOW(50),
    MEDIUM(128),
    HIGH(255);

    companion object {
        fun fromString(value: String?): HapticIntensity =
            entries.find { it.name == value } ?: MEDIUM
    }
}
