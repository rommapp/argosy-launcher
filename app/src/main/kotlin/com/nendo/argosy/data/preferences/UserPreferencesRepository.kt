package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.util.LogLevel
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
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val SWAP_AB = booleanPreferencesKey("nintendo_button_layout")  // Keep old key for migration
        val SWAP_XY = booleanPreferencesKey("swap_xy")
        val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")  // "auto", "xbox", "nintendo" - display only
        val SWAP_START_SELECT = booleanPreferencesKey("swap_start_select")
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

        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val VISIBLE_SYSTEM_APPS = stringPreferencesKey("visible_system_apps")
        val APP_ORDER = stringPreferencesKey("app_order")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val INSTANT_DOWNLOAD_THRESHOLD_MB = intPreferencesKey("instant_download_threshold_mb")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val SOUND_CONFIGS = stringPreferencesKey("sound_configs")
        val BETA_UPDATES_ENABLED = booleanPreferencesKey("beta_updates_enabled")
        val SAVE_SYNC_ENABLED = booleanPreferencesKey("save_sync_enabled")
        val EXPERIMENTAL_FOLDER_SAVE_SYNC = booleanPreferencesKey("experimental_folder_save_sync")
        val STATE_CACHE_ENABLED = booleanPreferencesKey("state_cache_enabled")
        val SAVE_CACHE_LIMIT = intPreferencesKey("save_cache_limit")

        val BACKGROUND_BLUR = intPreferencesKey("background_blur")
        val BACKGROUND_SATURATION = intPreferencesKey("background_saturation")
        val BACKGROUND_OPACITY = intPreferencesKey("background_opacity")
        val USE_GAME_BACKGROUND = booleanPreferencesKey("use_game_background")
        val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
        val USE_ACCENT_COLOR_FOOTER = booleanPreferencesKey("use_accent_color_footer")

        val FILE_LOGGING_ENABLED = booleanPreferencesKey("file_logging_enabled")
        val FILE_LOGGING_PATH = stringPreferencesKey("file_logging_path")
        val FILE_LOG_LEVEL = stringPreferencesKey("file_log_level")

        val BOX_ART_SHAPE = stringPreferencesKey("box_art_shape")
        val BOX_ART_CORNER_RADIUS = stringPreferencesKey("box_art_corner_radius")
        val BOX_ART_BORDER_THICKNESS = stringPreferencesKey("box_art_border_thickness")
        val BOX_ART_BORDER_STYLE = stringPreferencesKey("box_art_border_style")
        val GLASS_BORDER_TINT = stringPreferencesKey("glass_border_tint")
        val BOX_ART_GLOW_STRENGTH = stringPreferencesKey("box_art_glow_strength")
        val BOX_ART_OUTER_EFFECT = stringPreferencesKey("box_art_outer_effect")
        val BOX_ART_OUTER_EFFECT_THICKNESS = stringPreferencesKey("box_art_outer_effect_thickness")
        val BOX_ART_INNER_EFFECT = stringPreferencesKey("box_art_inner_effect")
        val BOX_ART_INNER_EFFECT_THICKNESS = stringPreferencesKey("box_art_inner_effect_thickness")
        val GRADIENT_PRESET = stringPreferencesKey("gradient_preset")
        val GRADIENT_ADVANCED_MODE = booleanPreferencesKey("gradient_advanced_mode")
        val SYSTEM_ICON_POSITION = stringPreferencesKey("system_icon_position")
        val SYSTEM_ICON_PADDING = stringPreferencesKey("system_icon_padding")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val RECOMMENDED_GAME_IDS = stringPreferencesKey("recommended_game_ids")
        val LAST_RECOMMENDATION_GENERATION = stringPreferencesKey("last_recommendation_generation")
        val RECOMMENDATION_PENALTIES = stringPreferencesKey("recommendation_penalties")
        val LAST_PENALTY_DECAY_WEEK = stringPreferencesKey("last_penalty_decay_week")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val LIBRARY_RECENT_SEARCHES = stringPreferencesKey("library_recent_searches")
        val ACCURATE_PLAY_TIME_ENABLED = booleanPreferencesKey("accurate_play_time_enabled")

        val AMBIENT_AUDIO_ENABLED = booleanPreferencesKey("ambient_audio_enabled")
        val AMBIENT_AUDIO_VOLUME = intPreferencesKey("ambient_audio_volume")
        val AMBIENT_AUDIO_URI = stringPreferencesKey("ambient_audio_uri")
        val IMAGE_CACHE_PATH = stringPreferencesKey("image_cache_path")

        val SCREEN_DIMMER_ENABLED = booleanPreferencesKey("screen_dimmer_enabled")
        val SCREEN_DIMMER_TIMEOUT_MINUTES = intPreferencesKey("screen_dimmer_timeout_minutes")
        val SCREEN_DIMMER_LEVEL = intPreferencesKey("screen_dimmer_level")
        val CUSTOM_BIOS_PATH = stringPreferencesKey("custom_bios_path")

        val VIDEO_WALLPAPER_ENABLED = booleanPreferencesKey("video_wallpaper_enabled")
        val VIDEO_WALLPAPER_DELAY_SECONDS = intPreferencesKey("video_wallpaper_delay_seconds")
        val VIDEO_WALLPAPER_MUTED = booleanPreferencesKey("video_wallpaper_muted")
        val UI_SCALE = intPreferencesKey("ui_scale")

        val AMBIENT_LED_ENABLED = booleanPreferencesKey("ambient_led_enabled")
        val AMBIENT_LED_BRIGHTNESS = intPreferencesKey("ambient_led_brightness")
        val AMBIENT_LED_AUDIO_BRIGHTNESS = booleanPreferencesKey("ambient_led_audio_brightness")
        val AMBIENT_LED_AUDIO_COLORS = booleanPreferencesKey("ambient_led_audio_colors")
        val AMBIENT_LED_COLOR_MODE = stringPreferencesKey("ambient_led_color_mode")

        val BUILTIN_SHADER = stringPreferencesKey("builtin_shader")
        val BUILTIN_FILTER = stringPreferencesKey("builtin_filter")
        val BUILTIN_ASPECT_RATIO = stringPreferencesKey("builtin_aspect_ratio")
        val BUILTIN_SKIP_DUPLICATE_FRAMES = booleanPreferencesKey("builtin_skip_duplicate_frames")
        val BUILTIN_LOW_LATENCY_AUDIO = booleanPreferencesKey("builtin_low_latency_audio")
        val BUILTIN_RUMBLE_ENABLED = booleanPreferencesKey("builtin_rumble_enabled")
        val BUILTIN_BLACK_FRAME_INSERTION = booleanPreferencesKey("builtin_black_frame_insertion")
        val BUILTIN_CORE_SELECTIONS = stringPreferencesKey("builtin_core_selections")
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
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 40,
            swapAB = prefs[Keys.SWAP_AB] ?: false,
            swapXY = prefs[Keys.SWAP_XY] ?: false,
            controllerLayout = prefs[Keys.CONTROLLER_LAYOUT] ?: "auto",
            swapStartSelect = prefs[Keys.SWAP_START_SELECT] ?: false,
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
            backgroundBlur = prefs[Keys.BACKGROUND_BLUR] ?: 40,
            backgroundSaturation = prefs[Keys.BACKGROUND_SATURATION] ?: 100,
            backgroundOpacity = prefs[Keys.BACKGROUND_OPACITY] ?: 100,
            useGameBackground = prefs[Keys.USE_GAME_BACKGROUND] ?: true,
            customBackgroundPath = prefs[Keys.CUSTOM_BACKGROUND_PATH],
            useAccentColorFooter = prefs[Keys.USE_ACCENT_COLOR_FOOTER] ?: false,
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
            instantDownloadThresholdMb = prefs[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] ?: 50,
            gridDensity = GridDensity.fromString(prefs[Keys.UI_DENSITY]),
            soundConfigs = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS]),
            betaUpdatesEnabled = prefs[Keys.BETA_UPDATES_ENABLED] ?: false,
            saveSyncEnabled = prefs[Keys.SAVE_SYNC_ENABLED] ?: false,
            experimentalFolderSaveSync = prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] ?: false,
            stateCacheEnabled = prefs[Keys.STATE_CACHE_ENABLED] ?: true,
            saveCacheLimit = prefs[Keys.SAVE_CACHE_LIMIT] ?: 10,
            fileLoggingEnabled = prefs[Keys.FILE_LOGGING_ENABLED] ?: false,
            fileLoggingPath = prefs[Keys.FILE_LOGGING_PATH],
            fileLogLevel = LogLevel.fromString(prefs[Keys.FILE_LOG_LEVEL]),
            boxArtShape = BoxArtShape.fromString(prefs[Keys.BOX_ART_SHAPE]),
            boxArtCornerRadius = BoxArtCornerRadius.fromString(prefs[Keys.BOX_ART_CORNER_RADIUS]),
            boxArtBorderThickness = BoxArtBorderThickness.fromString(prefs[Keys.BOX_ART_BORDER_THICKNESS]),
            boxArtBorderStyle = BoxArtBorderStyle.fromString(prefs[Keys.BOX_ART_BORDER_STYLE]),
            glassBorderTint = GlassBorderTint.fromString(prefs[Keys.GLASS_BORDER_TINT]),
            boxArtGlowStrength = BoxArtGlowStrength.fromString(prefs[Keys.BOX_ART_GLOW_STRENGTH]),
            boxArtOuterEffect = BoxArtOuterEffect.fromString(prefs[Keys.BOX_ART_OUTER_EFFECT]),
            boxArtOuterEffectThickness = BoxArtOuterEffectThickness.fromString(prefs[Keys.BOX_ART_OUTER_EFFECT_THICKNESS]),
            boxArtInnerEffect = BoxArtInnerEffect.fromString(prefs[Keys.BOX_ART_INNER_EFFECT]),
            boxArtInnerEffectThickness = BoxArtInnerEffectThickness.fromString(prefs[Keys.BOX_ART_INNER_EFFECT_THICKNESS]),
            gradientPreset = GradientPreset.fromString(prefs[Keys.GRADIENT_PRESET]),
            gradientAdvancedMode = prefs[Keys.GRADIENT_ADVANCED_MODE] ?: false,
            systemIconPosition = SystemIconPosition.fromString(prefs[Keys.SYSTEM_ICON_POSITION]),
            systemIconPadding = SystemIconPadding.fromString(prefs[Keys.SYSTEM_ICON_PADDING]),
            defaultView = DefaultView.fromString(prefs[Keys.DEFAULT_VIEW]),
            recommendedGameIds = prefs[Keys.RECOMMENDED_GAME_IDS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList(),
            lastRecommendationGeneration = prefs[Keys.LAST_RECOMMENDATION_GENERATION]?.let { Instant.parse(it) },
            recommendationPenalties = parseRecommendationPenalties(prefs[Keys.RECOMMENDATION_PENALTIES]),
            lastPenaltyDecayWeek = prefs[Keys.LAST_PENALTY_DECAY_WEEK],
            lastSeenVersion = prefs[Keys.LAST_SEEN_VERSION],
            libraryRecentSearches = prefs[Keys.LIBRARY_RECENT_SEARCHES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            accuratePlayTimeEnabled = prefs[Keys.ACCURATE_PLAY_TIME_ENABLED] ?: false,
            ambientAudioEnabled = prefs[Keys.AMBIENT_AUDIO_ENABLED] ?: false,
            ambientAudioVolume = prefs[Keys.AMBIENT_AUDIO_VOLUME] ?: 50,
            ambientAudioUri = prefs[Keys.AMBIENT_AUDIO_URI],
            imageCachePath = prefs[Keys.IMAGE_CACHE_PATH],
            screenDimmerEnabled = prefs[Keys.SCREEN_DIMMER_ENABLED] ?: true,
            screenDimmerTimeoutMinutes = prefs[Keys.SCREEN_DIMMER_TIMEOUT_MINUTES] ?: 2,
            screenDimmerLevel = prefs[Keys.SCREEN_DIMMER_LEVEL] ?: 50,
            customBiosPath = prefs[Keys.CUSTOM_BIOS_PATH],
            videoWallpaperEnabled = prefs[Keys.VIDEO_WALLPAPER_ENABLED] ?: false,
            videoWallpaperDelaySeconds = prefs[Keys.VIDEO_WALLPAPER_DELAY_SECONDS] ?: 3,
            videoWallpaperMuted = prefs[Keys.VIDEO_WALLPAPER_MUTED] ?: false,
            uiScale = prefs[Keys.UI_SCALE] ?: 100,
            ambientLedEnabled = prefs[Keys.AMBIENT_LED_ENABLED] ?: false,
            ambientLedBrightness = prefs[Keys.AMBIENT_LED_BRIGHTNESS] ?: 100,
            ambientLedAudioBrightness = prefs[Keys.AMBIENT_LED_AUDIO_BRIGHTNESS] ?: true,
            ambientLedAudioColors = prefs[Keys.AMBIENT_LED_AUDIO_COLORS] ?: false,
            ambientLedColorMode = AmbientLedColorMode.fromString(prefs[Keys.AMBIENT_LED_COLOR_MODE])
        )
    }

    private fun parseRecommendationPenalties(raw: String?): Map<Long, Float> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val gameId = parts[0].toLongOrNull()
                    val penalty = parts[1].toFloatOrNull()
                    if (gameId != null && penalty != null) gameId to penalty else null
                } else null
            }
            .toMap()
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

    suspend fun setSecondaryColor(color: Int?) {
        dataStore.edit { prefs ->
            if (color != null) prefs[Keys.SECONDARY_COLOR] = color
            else prefs.remove(Keys.SECONDARY_COLOR)
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTIC_ENABLED] = enabled
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

    suspend fun setControllerLayout(layout: String) {
        dataStore.edit { prefs ->
            prefs[Keys.CONTROLLER_LAYOUT] = layout
        }
    }

    suspend fun setSwapStartSelect(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SWAP_START_SELECT] = enabled
        }
    }

    suspend fun setLastRommSyncTime(time: Instant) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_ROMM_SYNC] = time.toString()
        }
    }

    suspend fun setLastFavoritesSyncTime(time: Instant) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_FAVORITES_SYNC] = time.toString()
        }
    }

    suspend fun setLastFavoritesCheckTime(time: Instant) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_FAVORITES_CHECK] = time.toString()
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

    suspend fun setInstantDownloadThresholdMb(value: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.INSTANT_DOWNLOAD_THRESHOLD_MB] = value
        }
    }

    suspend fun setGridDensity(density: GridDensity) {
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
            if (enabled) {
                prefs[Keys.EXPERIMENTAL_FOLDER_SAVE_SYNC] = true
            }
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

    suspend fun setUseAccentColorFooter(use: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.USE_ACCENT_COLOR_FOOTER] = use
        }
    }

    suspend fun setCustomBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.CUSTOM_BACKGROUND_PATH] = path
            else prefs.remove(Keys.CUSTOM_BACKGROUND_PATH)
        }
    }

    suspend fun setFileLoggingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.FILE_LOGGING_ENABLED] = enabled
        }
    }

    suspend fun setFileLoggingPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.FILE_LOGGING_PATH] = path
            else prefs.remove(Keys.FILE_LOGGING_PATH)
        }
    }

    suspend fun setFileLogLevel(level: LogLevel) {
        dataStore.edit { prefs ->
            prefs[Keys.FILE_LOG_LEVEL] = level.name
        }
    }

    suspend fun setBoxArtShape(shape: BoxArtShape) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_SHAPE] = shape.name
        }
    }

    suspend fun setBoxArtCornerRadius(radius: BoxArtCornerRadius) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_CORNER_RADIUS] = radius.name
        }
    }

    suspend fun setBoxArtBorderThickness(thickness: BoxArtBorderThickness) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_BORDER_THICKNESS] = thickness.name
        }
    }

    suspend fun setBoxArtBorderStyle(style: BoxArtBorderStyle) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_BORDER_STYLE] = style.name
        }
    }

    suspend fun setGlassBorderTint(tint: GlassBorderTint) {
        dataStore.edit { prefs ->
            prefs[Keys.GLASS_BORDER_TINT] = tint.name
        }
    }

    suspend fun setBoxArtGlowStrength(strength: BoxArtGlowStrength) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_GLOW_STRENGTH] = strength.name
        }
    }

    suspend fun setBoxArtOuterEffect(effect: BoxArtOuterEffect) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_OUTER_EFFECT] = effect.name
        }
    }

    suspend fun setBoxArtOuterEffectThickness(thickness: BoxArtOuterEffectThickness) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_OUTER_EFFECT_THICKNESS] = thickness.name
        }
    }

    suspend fun setBoxArtInnerEffect(effect: BoxArtInnerEffect) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_INNER_EFFECT] = effect.name
        }
    }

    suspend fun setBoxArtInnerEffectThickness(thickness: BoxArtInnerEffectThickness) {
        dataStore.edit { prefs ->
            prefs[Keys.BOX_ART_INNER_EFFECT_THICKNESS] = thickness.name
        }
    }

    suspend fun setGradientPreset(preset: GradientPreset) {
        dataStore.edit { prefs ->
            prefs[Keys.GRADIENT_PRESET] = preset.name
        }
    }

    suspend fun setGradientAdvancedMode(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.GRADIENT_ADVANCED_MODE] = enabled
        }
    }

    suspend fun setSystemIconPosition(position: SystemIconPosition) {
        dataStore.edit { prefs ->
            prefs[Keys.SYSTEM_ICON_POSITION] = position.name
        }
    }

    suspend fun setSystemIconPadding(padding: SystemIconPadding) {
        dataStore.edit { prefs ->
            prefs[Keys.SYSTEM_ICON_PADDING] = padding.name
        }
    }

    suspend fun setDefaultView(view: DefaultView) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_VIEW] = view.name
        }
    }

    suspend fun setRecommendations(gameIds: List<Long>, timestamp: Instant) {
        dataStore.edit { prefs ->
            if (gameIds.isEmpty()) {
                prefs.remove(Keys.RECOMMENDED_GAME_IDS)
            } else {
                prefs[Keys.RECOMMENDED_GAME_IDS] = gameIds.joinToString(",")
            }
            prefs[Keys.LAST_RECOMMENDATION_GENERATION] = timestamp.toString()
        }
    }

    suspend fun clearRecommendations() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.RECOMMENDED_GAME_IDS)
            prefs.remove(Keys.LAST_RECOMMENDATION_GENERATION)
        }
    }

    suspend fun setRecommendationPenalties(penalties: Map<Long, Float>, weekKey: String) {
        dataStore.edit { prefs ->
            val filtered = penalties.filter { it.value > 0f }
            if (filtered.isEmpty()) {
                prefs.remove(Keys.RECOMMENDATION_PENALTIES)
            } else {
                prefs[Keys.RECOMMENDATION_PENALTIES] = filtered.entries.joinToString(",") { "${it.key}:${it.value}" }
            }
            prefs[Keys.LAST_PENALTY_DECAY_WEEK] = weekKey
        }
    }

    suspend fun setLastSeenVersion(version: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_SEEN_VERSION] = version
        }
    }

    suspend fun addLibraryRecentSearch(query: String) {
        if (query.isBlank()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.LIBRARY_RECENT_SEARCHES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val updated = listOf(query) + current.filter { it != query }
            prefs[Keys.LIBRARY_RECENT_SEARCHES] = updated.take(10).joinToString(",")
        }
    }

    suspend fun setAccuratePlayTimeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCURATE_PLAY_TIME_ENABLED] = enabled
        }
    }

    suspend fun setAmbientAudioEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_AUDIO_ENABLED] = enabled
        }
    }

    suspend fun setAmbientAudioVolume(volume: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_AUDIO_VOLUME] = volume.coerceIn(0, 100)
        }
    }

    suspend fun setAmbientAudioUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.AMBIENT_AUDIO_URI] = uri
            else prefs.remove(Keys.AMBIENT_AUDIO_URI)
        }
    }

    suspend fun setImageCachePath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.IMAGE_CACHE_PATH] = path
            else prefs.remove(Keys.IMAGE_CACHE_PATH)
        }
    }

    suspend fun setScreenDimmerEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCREEN_DIMMER_ENABLED] = enabled
        }
    }

    suspend fun setScreenDimmerTimeoutMinutes(minutes: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SCREEN_DIMMER_TIMEOUT_MINUTES] = minutes.coerceIn(1, 5)
        }
    }

    suspend fun setScreenDimmerLevel(level: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SCREEN_DIMMER_LEVEL] = level.coerceIn(40, 70)
        }
    }

    suspend fun setCustomBiosPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) {
                prefs[Keys.CUSTOM_BIOS_PATH] = path
            } else {
                prefs.remove(Keys.CUSTOM_BIOS_PATH)
            }
        }
    }

    suspend fun setVideoWallpaperEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_WALLPAPER_ENABLED] = enabled
        }
    }

    suspend fun setVideoWallpaperDelaySeconds(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_WALLPAPER_DELAY_SECONDS] = seconds.coerceIn(0, 10)
        }
    }

    suspend fun setVideoWallpaperMuted(muted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.VIDEO_WALLPAPER_MUTED] = muted
        }
    }

    suspend fun setUiScale(scale: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.UI_SCALE] = scale.coerceIn(75, 150)
        }
    }

    suspend fun setAmbientLedEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_LED_ENABLED] = enabled
        }
    }

    suspend fun setAmbientLedBrightness(brightness: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_LED_BRIGHTNESS] = brightness.coerceIn(0, 100)
        }
    }

    suspend fun setAmbientLedAudioBrightness(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_LED_AUDIO_BRIGHTNESS] = enabled
        }
    }

    suspend fun setAmbientLedAudioColors(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_LED_AUDIO_COLORS] = enabled
        }
    }

    suspend fun setAmbientLedColorMode(mode: AmbientLedColorMode) {
        dataStore.edit { prefs ->
            prefs[Keys.AMBIENT_LED_COLOR_MODE] = mode.name
        }
    }

    suspend fun setBuiltinShader(shader: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_SHADER] = shader
        }
    }

    suspend fun setBuiltinFilter(filter: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_FILTER] = filter
        }
    }

    suspend fun setBuiltinSkipDuplicateFrames(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_SKIP_DUPLICATE_FRAMES] = enabled
        }
    }

    suspend fun setBuiltinLowLatencyAudio(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_LOW_LATENCY_AUDIO] = enabled
        }
    }

    suspend fun setBuiltinRumbleEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_RUMBLE_ENABLED] = enabled
        }
    }

    suspend fun setBuiltinBlackFrameInsertion(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_BLACK_FRAME_INSERTION] = enabled
        }
    }

    suspend fun setBuiltinCoreForPlatform(platformSlug: String, coreId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.BUILTIN_CORE_SELECTIONS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.associate {
                    val parts = it.split(":")
                    parts[0] to parts.getOrElse(1) { "" }
                }
                ?.toMutableMap()
                ?: mutableMapOf()

            current[platformSlug] = coreId

            prefs[Keys.BUILTIN_CORE_SELECTIONS] = current.entries
                .joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    fun getBuiltinCoreSelections(): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.BUILTIN_CORE_SELECTIONS]
            ?.split(",")
            ?.filter { it.isNotBlank() && it.contains(":") }
            ?.associate {
                val parts = it.split(":")
                parts[0] to parts.getOrElse(1) { "" }
            }
            ?: emptyMap()
    }

    fun getBuiltinEmulatorSettings(): Flow<BuiltinEmulatorSettings> = dataStore.data.map { prefs ->
        BuiltinEmulatorSettings(
            shader = prefs[Keys.BUILTIN_SHADER] ?: "None",
            filter = prefs[Keys.BUILTIN_FILTER] ?: "Auto",
            aspectRatio = prefs[Keys.BUILTIN_ASPECT_RATIO] ?: "Core Provided",
            skipDuplicateFrames = prefs[Keys.BUILTIN_SKIP_DUPLICATE_FRAMES] ?: false,
            lowLatencyAudio = prefs[Keys.BUILTIN_LOW_LATENCY_AUDIO] ?: true,
            rumbleEnabled = prefs[Keys.BUILTIN_RUMBLE_ENABLED] ?: true,
            blackFrameInsertion = prefs[Keys.BUILTIN_BLACK_FRAME_INSERTION] ?: false
        )
    }

    suspend fun setBuiltinAspectRatio(aspectRatio: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BUILTIN_ASPECT_RATIO] = aspectRatio
        }
    }
}

data class BuiltinEmulatorSettings(
    val shader: String = "None",
    val filter: String = "Auto",
    val aspectRatio: String = "Core Provided",
    val skipDuplicateFrames: Boolean = false,
    val lowLatencyAudio: Boolean = true,
    val rumbleEnabled: Boolean = true,
    val blackFrameInsertion: Boolean = false
) {
    val shaderConfig: com.swordfish.libretrodroid.ShaderConfig
        get() = when (shader) {
            "CRT" -> com.swordfish.libretrodroid.ShaderConfig.CRT
            "LCD" -> com.swordfish.libretrodroid.ShaderConfig.LCD
            "Sharp" -> com.swordfish.libretrodroid.ShaderConfig.Sharp
            "CUT" -> com.swordfish.libretrodroid.ShaderConfig.CUT()
            "CUT2" -> com.swordfish.libretrodroid.ShaderConfig.CUT2()
            "CUT3" -> com.swordfish.libretrodroid.ShaderConfig.CUT3()
            else -> com.swordfish.libretrodroid.ShaderConfig.Default
        }

    val filterMode: Int
        get() = when (filter) {
            "Nearest" -> 0
            "Bilinear" -> 1
            else -> -1  // Auto
        }

    val isIntegerScaling: Boolean
        get() = aspectRatio == "Integer"
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
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val controllerLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val lastRommSync: Instant? = null,
    val lastFavoritesSync: Instant? = null,
    val lastFavoritesCheck: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val syncScreenshotsEnabled: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList(),
    val maxConcurrentDownloads: Int = 1,
    val instantDownloadThresholdMb: Int = 50,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val betaUpdatesEnabled: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val stateCacheEnabled: Boolean = true,
    val saveCacheLimit: Int = 10,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val useAccentColorFooter: Boolean = false,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.GLASS,
    val glassBorderTint: GlassBorderTint = GlassBorderTint.TINT_20,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.THIN,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.GLASS,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.THICK,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val gradientAdvancedMode: Boolean = false,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val defaultView: DefaultView = DefaultView.HOME,
    val recommendedGameIds: List<Long> = emptyList(),
    val lastRecommendationGeneration: Instant? = null,
    val recommendationPenalties: Map<Long, Float> = emptyMap(),
    val lastPenaltyDecayWeek: String? = null,
    val lastSeenVersion: String? = null,
    val libraryRecentSearches: List<String> = emptyList(),
    val accuratePlayTimeEnabled: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val ambientAudioVolume: Int = 50,
    val ambientAudioUri: String? = null,
    val imageCachePath: String? = null,
    val screenDimmerEnabled: Boolean = true,
    val screenDimmerTimeoutMinutes: Int = 2,
    val screenDimmerLevel: Int = 50,
    val customBiosPath: String? = null,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelaySeconds: Int = 3,
    val videoWallpaperMuted: Boolean = false,
    val uiScale: Int = 100,
    val ambientLedEnabled: Boolean = false,
    val ambientLedBrightness: Int = 100,
    val ambientLedAudioBrightness: Boolean = true,
    val ambientLedAudioColors: Boolean = false,
    val ambientLedColorMode: AmbientLedColorMode = AmbientLedColorMode.DOMINANT_3
)

enum class ThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

enum class GridDensity {
    COMPACT, NORMAL, SPACIOUS;

    companion object {
        fun fromString(value: String?): GridDensity =
            entries.find { it.name == value } ?: NORMAL
    }
}

enum class BoxArtCornerRadius(val dp: Int) {
    NONE(0), SMALL(4), MEDIUM(8), LARGE(12), EXTRA_LARGE(16);

    companion object {
        fun fromString(value: String?): BoxArtCornerRadius =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtBorderThickness(val dp: Int) {
    NONE(0), THIN(1), MEDIUM(2), THICK(4);

    companion object {
        fun fromString(value: String?): BoxArtBorderThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtBorderStyle {
    SOLID, GLASS, GRADIENT;

    companion object {
        fun fromString(value: String?): BoxArtBorderStyle =
            entries.find { it.name == value } ?: SOLID
    }
}

enum class GlassBorderTint(val alpha: Float) {
    OFF(0f), TINT_5(0.05f), TINT_10(0.10f), TINT_15(0.15f), TINT_20(0.20f), TINT_25(0.25f);

    companion object {
        fun fromString(value: String?): GlassBorderTint =
            entries.find { it.name == value } ?: OFF
    }
}

enum class BoxArtGlowStrength(val alpha: Float, val isShadow: Boolean = false) {
    OFF(0f),
    LOW(0.2f),
    MEDIUM(0.4f),
    HIGH(0.6f),
    SHADOW_SMALL(0.15f, isShadow = true),
    SHADOW_LARGE(0.25f, isShadow = true);

    companion object {
        fun fromString(value: String?): BoxArtGlowStrength =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class SystemIconPosition {
    OFF, TOP_LEFT, TOP_RIGHT;

    companion object {
        fun fromString(value: String?): SystemIconPosition =
            entries.find { it.name == value } ?: TOP_LEFT
    }
}

enum class SystemIconPadding(val dp: Int) {
    SMALL(4), MEDIUM(8), LARGE(12);

    companion object {
        fun fromString(value: String?): SystemIconPadding =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class DefaultView {
    HOME, LIBRARY;

    companion object {
        fun fromString(value: String?): DefaultView = when (value) {
            "HOME", "SHOWCASE" -> HOME
            "LIBRARY" -> LIBRARY
            else -> HOME
        }
    }
}

enum class BoxArtInnerEffect {
    OFF, GLOW, SHADOW, GLASS, SHINE;

    companion object {
        fun fromString(value: String?): BoxArtInnerEffect =
            entries.find { it.name == value } ?: SHADOW
    }
}

enum class BoxArtInnerEffectThickness(val px: Float) {
    THIN(6f), MEDIUM(16f), THICK(24f);

    companion object {
        fun fromString(value: String?): BoxArtInnerEffectThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtOuterEffect {
    OFF, GLOW, SHADOW, SHINE;

    companion object {
        fun fromString(value: String?): BoxArtOuterEffect =
            entries.find { it.name == value } ?: GLOW
    }
}

enum class BoxArtOuterEffectThickness(val px: Float) {
    THIN(8f), MEDIUM(16f), THICK(24f);

    companion object {
        fun fromString(value: String?): BoxArtOuterEffectThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtShape(val aspectRatio: Float, val displayName: String) {
    TALL(2f / 3f, "2:3"),
    STANDARD(3f / 4f, "3:4"),
    SQUARE(1f, "1:1");

    companion object {
        fun fromString(value: String?): BoxArtShape =
            entries.find { it.name == value } ?: STANDARD
    }
}

enum class AmbientLedColorMode(val displayName: String) {
    DOMINANT_3("Dominant Colors"),
    VIBRANT_MUTED("Vibrant & Muted"),
    HUE_FAMILIES("Hue Families");

    companion object {
        fun fromString(value: String?): AmbientLedColorMode =
            entries.find { it.name == value } ?: DOMINANT_3
    }
}
