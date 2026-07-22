package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val surfaceTintBleed: Int = 0,
    val backdropEnabled: Boolean = false,
    val backdropPreset: BackdropPreset = BackdropPreset.PLATFORMS,
    val backdropCellSize: Int = ComponentDefaults.SurfaceBackdrop.cellSizeDefaultDp,
    val backdropScatter: Int = 200,
    val backdropScaleJitter: Int = 100,
    val backdropStrength: Int = 20,
    val backdropEdgeStyle: BackdropEdgeStyle = BackdropEdgeStyle.CONNECTIONS,
    val backdropVertexIcons: BackdropVertexIcon = BackdropVertexIcon.NONE,
    val backdropSeed: Long = 0L,
    val backdropMotion: BackdropMotion = BackdropMotion.SWAY,
    val backdropMotionSpeed: Int = 75,
    val backdropDriftAngle: Float = 45f,
    val displayFontPath: String? = null,
    val displayFontName: String? = null,
    val bodyFontPath: String? = null,
    val bodyFontName: String? = null,
    val displayFontScale: Int = 100,
    val bodyFontScale: Int = 100,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val uiScale: Int = 100,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val homeBackgroundMode: HomeBackgroundMode = HomeBackgroundMode.GAME_ART,
    val useAccentColorFooter: Boolean = false,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.GLASS,
    val glassBorderTint: GlassBorderTint = GlassBorderTint.TINT_20,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.THIN,
    val glowColorMode: GlowColorMode = GlowColorMode.AUTO,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.GLASS,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.THICK,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val gradientAdvancedMode: Boolean = false,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val platformIndicatorStyle: PlatformIndicatorStyle = PlatformIndicatorStyle.TAB,
    val platformIndicatorContent: PlatformIndicatorContent = PlatformIndicatorContent.NAME,
    val defaultView: DefaultView = DefaultView.HOME,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelaySeconds: Int = 3,
    val videoWallpaperMuted: Boolean = false,
    val ambientLedEnabled: Boolean = false,
    val ambientLedBrightness: Int = 100,
    val ambientLedAudioBrightness: Boolean = true,
    val ambientLedAudioColors: Boolean = false,
    val ambientLedColorMode: AmbientLedColorMode = AmbientLedColorMode.DOMINANT_3,
    val ambientLedCoverArtEnabled: Boolean = true,
    val ambientLedCustomColor: Boolean = false,
    val ambientLedCustomColorHue: Int = 200,
    val ambientLedTransitionMs: Int = 250,
    val ambientLedScreenEnabled: Boolean = false,
    val ambientLedAchievementFlash: Boolean = true,
    val screenDimmerEnabled: Boolean = true,
    val screenDimmerTimeoutMinutes: Int = 2,
    val screenDimmerLevel: Int = 50,
    val dualScreenEnabled: Boolean = false,
    val displayRoleOverride: DisplayRoleOverride = DisplayRoleOverride.AUTO,
    val dualScreenInputFocus: DualScreenInputFocus = DualScreenInputFocus.AUTO,
    val installedOnlyHome: Boolean = false
)

@Singleton
class DisplayPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PRIMARY_COLOR = intPreferencesKey("primary_color")
        val SECONDARY_COLOR = intPreferencesKey("secondary_color")
        val TERTIARY_COLOR = intPreferencesKey("tertiary_color")
        val SURFACE_TINT_BLEED = intPreferencesKey("surface_tint_bleed")
        val BACKDROP_ENABLED = booleanPreferencesKey("backdrop_enabled")
        val BACKDROP_PRESET = stringPreferencesKey("backdrop_preset")
        val BACKDROP_CELL_SIZE = intPreferencesKey("backdrop_cell_size")
        val BACKDROP_SCATTER = intPreferencesKey("backdrop_scatter")
        val BACKDROP_SCALE_JITTER = intPreferencesKey("backdrop_scale_jitter")
        val BACKDROP_STRENGTH = intPreferencesKey("backdrop_strength")
        val BACKDROP_EDGE_STYLE = stringPreferencesKey("backdrop_edge_style")
        val BACKDROP_VERTEX_ICONS = stringPreferencesKey("backdrop_vertex_icons")
        val BACKDROP_SEED = longPreferencesKey("backdrop_seed")
        val BACKDROP_MOTION = stringPreferencesKey("backdrop_motion")
        val BACKDROP_MOTION_SPEED = intPreferencesKey("backdrop_motion_speed")
        val BACKDROP_DRIFT_ANGLE = floatPreferencesKey("backdrop_drift_angle")
        val FONT_DISPLAY_PATH = stringPreferencesKey("font_display_path")
        val FONT_DISPLAY_NAME = stringPreferencesKey("font_display_name")
        val FONT_BODY_PATH = stringPreferencesKey("font_body_path")
        val FONT_BODY_NAME = stringPreferencesKey("font_body_name")
        val FONT_DISPLAY_SCALE = intPreferencesKey("font_display_scale")
        val FONT_BODY_SCALE = intPreferencesKey("font_body_scale")
        val UI_DENSITY = stringPreferencesKey("ui_density")
        val UI_SCALE = intPreferencesKey("ui_scale")
        val BACKGROUND_BLUR = intPreferencesKey("background_blur")
        val BACKGROUND_SATURATION = intPreferencesKey("background_saturation")
        val BACKGROUND_OPACITY = intPreferencesKey("background_opacity")
        val USE_GAME_BACKGROUND = booleanPreferencesKey("use_game_background")
        val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
        val HOME_BACKGROUND_MODE = stringPreferencesKey("home_background_mode")
        val USE_ACCENT_COLOR_FOOTER = booleanPreferencesKey("use_accent_color_footer")
        val BOX_ART_SHAPE = stringPreferencesKey("box_art_shape")
        val BOX_ART_CORNER_RADIUS = stringPreferencesKey("box_art_corner_radius")
        val BOX_ART_BORDER_THICKNESS = stringPreferencesKey("box_art_border_thickness")
        val BOX_ART_BORDER_STYLE = stringPreferencesKey("box_art_border_style")
        val GLASS_BORDER_TINT = stringPreferencesKey("glass_border_tint")
        val BOX_ART_GLOW_STRENGTH = stringPreferencesKey("box_art_glow_strength")
        val BOX_ART_OUTER_EFFECT = stringPreferencesKey("box_art_outer_effect")
        val BOX_ART_OUTER_EFFECT_THICKNESS = stringPreferencesKey("box_art_outer_effect_thickness")
        val GLOW_COLOR_MODE = stringPreferencesKey("glow_color_mode")
        val BOX_ART_INNER_EFFECT = stringPreferencesKey("box_art_inner_effect")
        val BOX_ART_INNER_EFFECT_THICKNESS = stringPreferencesKey("box_art_inner_effect_thickness")
        val GRADIENT_PRESET = stringPreferencesKey("gradient_preset")
        val GRADIENT_ADVANCED_MODE = booleanPreferencesKey("gradient_advanced_mode")
        val SYSTEM_ICON_POSITION = stringPreferencesKey("system_icon_position")
        val SYSTEM_ICON_PADDING = stringPreferencesKey("system_icon_padding")
        val PLATFORM_INDICATOR_STYLE = stringPreferencesKey("platform_indicator_style")
        val PLATFORM_INDICATOR_CONTENT = stringPreferencesKey("platform_indicator_content")
        val DEFAULT_VIEW = stringPreferencesKey("default_view")
        val VIDEO_WALLPAPER_ENABLED = booleanPreferencesKey("video_wallpaper_enabled")
        val VIDEO_WALLPAPER_DELAY_SECONDS = intPreferencesKey("video_wallpaper_delay_seconds")
        val VIDEO_WALLPAPER_MUTED = booleanPreferencesKey("video_wallpaper_muted")
        val AMBIENT_LED_ENABLED = booleanPreferencesKey("ambient_led_enabled")
        val AMBIENT_LED_BRIGHTNESS = intPreferencesKey("ambient_led_brightness")
        val AMBIENT_LED_AUDIO_BRIGHTNESS = booleanPreferencesKey("ambient_led_audio_brightness")
        val AMBIENT_LED_AUDIO_COLORS = booleanPreferencesKey("ambient_led_audio_colors")
        val AMBIENT_LED_COLOR_MODE = stringPreferencesKey("ambient_led_color_mode")
        val AMBIENT_LED_COVER_ART_ENABLED = booleanPreferencesKey("ambient_led_cover_art_enabled")
        val AMBIENT_LED_CUSTOM_COLOR = booleanPreferencesKey("ambient_led_custom_color")
        val AMBIENT_LED_CUSTOM_COLOR_HUE = intPreferencesKey("ambient_led_custom_color_hue")
        val AMBIENT_LED_TRANSITION_MS = intPreferencesKey("ambient_led_transition_ms")
        val AMBIENT_LED_SCREEN_ENABLED = booleanPreferencesKey("ambient_led_screen_enabled")
        val AMBIENT_LED_ACHIEVEMENT_FLASH = booleanPreferencesKey("ambient_led_achievement_flash")
        val SCREEN_DIMMER_ENABLED = booleanPreferencesKey("screen_dimmer_enabled")
        val SCREEN_DIMMER_TIMEOUT_MINUTES = intPreferencesKey("screen_dimmer_timeout_minutes")
        val SCREEN_DIMMER_LEVEL = intPreferencesKey("screen_dimmer_level")
        val DUAL_SCREEN_ENABLED = booleanPreferencesKey("dual_screen_enabled")
        val DISPLAY_ROLE_OVERRIDE = stringPreferencesKey("display_role_override")
        val DUAL_SCREEN_INPUT_FOCUS = stringPreferencesKey("dual_screen_input_focus")
        val INSTALLED_ONLY_HOME = booleanPreferencesKey("installed_only_home")
    }

    val preferences: Flow<DisplayPreferences> = dataStore.data.map { prefs ->
        DisplayPreferences(
            themeMode = ThemeMode.fromString(prefs[Keys.THEME_MODE]),
            primaryColor = prefs[Keys.PRIMARY_COLOR],
            secondaryColor = prefs[Keys.SECONDARY_COLOR],
            tertiaryColor = prefs[Keys.TERTIARY_COLOR],
            surfaceTintBleed = prefs[Keys.SURFACE_TINT_BLEED] ?: 0,
            backdropEnabled = prefs[Keys.BACKDROP_ENABLED] ?: false,
            backdropPreset = BackdropPreset.fromString(prefs[Keys.BACKDROP_PRESET]),
            backdropCellSize = prefs[Keys.BACKDROP_CELL_SIZE] ?: ComponentDefaults.SurfaceBackdrop.cellSizeDefaultDp,
            backdropScatter = prefs[Keys.BACKDROP_SCATTER] ?: 200,
            backdropScaleJitter = prefs[Keys.BACKDROP_SCALE_JITTER] ?: 100,
            backdropStrength = prefs[Keys.BACKDROP_STRENGTH] ?: 20,
            backdropEdgeStyle = BackdropEdgeStyle.fromString(prefs[Keys.BACKDROP_EDGE_STYLE]),
            backdropVertexIcons = BackdropVertexIcon.fromString(prefs[Keys.BACKDROP_VERTEX_ICONS]),
            backdropSeed = prefs[Keys.BACKDROP_SEED] ?: 0L,
            backdropMotion = BackdropMotion.fromString(prefs[Keys.BACKDROP_MOTION]),
            backdropMotionSpeed = prefs[Keys.BACKDROP_MOTION_SPEED] ?: 75,
            backdropDriftAngle = prefs[Keys.BACKDROP_DRIFT_ANGLE] ?: 45f,
            displayFontPath = prefs[Keys.FONT_DISPLAY_PATH],
            displayFontName = prefs[Keys.FONT_DISPLAY_NAME],
            bodyFontPath = prefs[Keys.FONT_BODY_PATH],
            bodyFontName = prefs[Keys.FONT_BODY_NAME],
            displayFontScale = prefs[Keys.FONT_DISPLAY_SCALE] ?: 100,
            bodyFontScale = prefs[Keys.FONT_BODY_SCALE] ?: 100,
            gridDensity = GridDensity.fromString(prefs[Keys.UI_DENSITY]),
            uiScale = prefs[Keys.UI_SCALE] ?: 100,
            backgroundBlur = prefs[Keys.BACKGROUND_BLUR] ?: 40,
            backgroundSaturation = prefs[Keys.BACKGROUND_SATURATION] ?: 100,
            backgroundOpacity = prefs[Keys.BACKGROUND_OPACITY] ?: 100,
            useGameBackground = prefs[Keys.USE_GAME_BACKGROUND] ?: true,
            customBackgroundPath = prefs[Keys.CUSTOM_BACKGROUND_PATH],
            homeBackgroundMode = HomeBackgroundMode.fromString(prefs[Keys.HOME_BACKGROUND_MODE]),
            useAccentColorFooter = prefs[Keys.USE_ACCENT_COLOR_FOOTER] ?: false,
            boxArtShape = BoxArtShape.fromString(prefs[Keys.BOX_ART_SHAPE]),
            boxArtCornerRadius = BoxArtCornerRadius.fromString(prefs[Keys.BOX_ART_CORNER_RADIUS]),
            boxArtBorderThickness = BoxArtBorderThickness.fromString(prefs[Keys.BOX_ART_BORDER_THICKNESS]),
            boxArtBorderStyle = BoxArtBorderStyle.fromString(prefs[Keys.BOX_ART_BORDER_STYLE]),
            glassBorderTint = GlassBorderTint.fromString(prefs[Keys.GLASS_BORDER_TINT]),
            boxArtGlowStrength = BoxArtGlowStrength.fromString(prefs[Keys.BOX_ART_GLOW_STRENGTH]),
            boxArtOuterEffect = BoxArtOuterEffect.fromString(prefs[Keys.BOX_ART_OUTER_EFFECT]),
            boxArtOuterEffectThickness = BoxArtOuterEffectThickness.fromString(prefs[Keys.BOX_ART_OUTER_EFFECT_THICKNESS]),
            glowColorMode = GlowColorMode.fromString(prefs[Keys.GLOW_COLOR_MODE]),
            boxArtInnerEffect = BoxArtInnerEffect.fromString(prefs[Keys.BOX_ART_INNER_EFFECT]),
            boxArtInnerEffectThickness = BoxArtInnerEffectThickness.fromString(prefs[Keys.BOX_ART_INNER_EFFECT_THICKNESS]),
            gradientPreset = GradientPreset.fromString(prefs[Keys.GRADIENT_PRESET]),
            gradientAdvancedMode = prefs[Keys.GRADIENT_ADVANCED_MODE] ?: false,
            systemIconPosition = SystemIconPosition.fromString(prefs[Keys.SYSTEM_ICON_POSITION]),
            systemIconPadding = SystemIconPadding.fromString(prefs[Keys.SYSTEM_ICON_PADDING]),
            // Migrate legacy systemIconPosition=OFF (pre-style enum) to platformIndicatorStyle=OFF.
            platformIndicatorStyle = if (prefs[Keys.SYSTEM_ICON_POSITION] == "OFF" && prefs[Keys.PLATFORM_INDICATOR_STYLE] == null) {
                PlatformIndicatorStyle.OFF
            } else {
                PlatformIndicatorStyle.fromString(prefs[Keys.PLATFORM_INDICATOR_STYLE])
            },
            platformIndicatorContent = PlatformIndicatorContent.fromString(prefs[Keys.PLATFORM_INDICATOR_CONTENT]),
            defaultView = DefaultView.fromString(prefs[Keys.DEFAULT_VIEW]),
            videoWallpaperEnabled = prefs[Keys.VIDEO_WALLPAPER_ENABLED] ?: false,
            videoWallpaperDelaySeconds = prefs[Keys.VIDEO_WALLPAPER_DELAY_SECONDS] ?: 3,
            videoWallpaperMuted = prefs[Keys.VIDEO_WALLPAPER_MUTED] ?: false,
            ambientLedEnabled = prefs[Keys.AMBIENT_LED_ENABLED] ?: false,
            ambientLedBrightness = prefs[Keys.AMBIENT_LED_BRIGHTNESS] ?: 100,
            ambientLedAudioBrightness = prefs[Keys.AMBIENT_LED_AUDIO_BRIGHTNESS] ?: true,
            ambientLedAudioColors = prefs[Keys.AMBIENT_LED_AUDIO_COLORS] ?: false,
            ambientLedColorMode = AmbientLedColorMode.fromString(prefs[Keys.AMBIENT_LED_COLOR_MODE]),
            ambientLedCoverArtEnabled = prefs[Keys.AMBIENT_LED_COVER_ART_ENABLED] ?: true,
            ambientLedCustomColor = prefs[Keys.AMBIENT_LED_CUSTOM_COLOR] ?: false,
            ambientLedCustomColorHue = prefs[Keys.AMBIENT_LED_CUSTOM_COLOR_HUE] ?: 200,
            ambientLedTransitionMs = prefs[Keys.AMBIENT_LED_TRANSITION_MS] ?: 250,
            ambientLedScreenEnabled = prefs[Keys.AMBIENT_LED_SCREEN_ENABLED] ?: false,
            ambientLedAchievementFlash = prefs[Keys.AMBIENT_LED_ACHIEVEMENT_FLASH] ?: true,
            screenDimmerEnabled = prefs[Keys.SCREEN_DIMMER_ENABLED] ?: true,
            screenDimmerTimeoutMinutes = prefs[Keys.SCREEN_DIMMER_TIMEOUT_MINUTES] ?: 2,
            screenDimmerLevel = prefs[Keys.SCREEN_DIMMER_LEVEL] ?: 50,
            dualScreenEnabled = prefs[Keys.DUAL_SCREEN_ENABLED] ?: DisplayAffinityHelper.isKnownDualScreenDevice(),
            displayRoleOverride = DisplayRoleOverride.fromString(prefs[Keys.DISPLAY_ROLE_OVERRIDE]),
            dualScreenInputFocus = DualScreenInputFocus.fromString(prefs[Keys.DUAL_SCREEN_INPUT_FOCUS]),
            installedOnlyHome = prefs[Keys.INSTALLED_ONLY_HOME] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
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

    suspend fun setSurfaceTintBleed(bleed: Int) {
        dataStore.edit { it[Keys.SURFACE_TINT_BLEED] = bleed.coerceIn(0, 100) }
    }

    suspend fun setBackdropEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKDROP_ENABLED] = enabled }
    }

    /** Applies a preset as a saved layer config: writes the preset plus its default edge and vertex layers. */
    suspend fun setBackdropPreset(preset: BackdropPreset, edgeStyle: BackdropEdgeStyle, vertexIcons: BackdropVertexIcon) {
        dataStore.edit {
            it[Keys.BACKDROP_PRESET] = preset.name
            it[Keys.BACKDROP_EDGE_STYLE] = edgeStyle.name
            it[Keys.BACKDROP_VERTEX_ICONS] = vertexIcons.name
        }
    }

    suspend fun setBackdropCellSize(sizeDp: Int) {
        dataStore.edit {
            it[Keys.BACKDROP_CELL_SIZE] = sizeDp.coerceIn(
                ComponentDefaults.SurfaceBackdrop.cellSizeMinDp,
                ComponentDefaults.SurfaceBackdrop.cellSizeMaxDp
            )
        }
    }

    suspend fun setBackdropScatter(scatter: Int) {
        dataStore.edit { it[Keys.BACKDROP_SCATTER] = scatter.coerceIn(0, 200) }
    }

    suspend fun setBackdropScaleJitter(jitter: Int) {
        dataStore.edit { it[Keys.BACKDROP_SCALE_JITTER] = jitter.coerceIn(0, 200) }
    }

    suspend fun setBackdropStrength(strength: Int) {
        dataStore.edit { it[Keys.BACKDROP_STRENGTH] = strength.coerceIn(10, 100) }
    }

    suspend fun setBackdropEdgeStyle(style: BackdropEdgeStyle) {
        dataStore.edit { it[Keys.BACKDROP_EDGE_STYLE] = style.name }
    }

    suspend fun setBackdropVertexIcons(icons: BackdropVertexIcon) {
        dataStore.edit { it[Keys.BACKDROP_VERTEX_ICONS] = icons.name }
    }

    suspend fun setBackdropSeed(seed: Long) {
        dataStore.edit { it[Keys.BACKDROP_SEED] = seed }
    }

    suspend fun setBackdropMotion(motion: BackdropMotion) {
        dataStore.edit { it[Keys.BACKDROP_MOTION] = motion.name }
    }

    suspend fun setBackdropMotionSpeed(speed: Int) {
        dataStore.edit { it[Keys.BACKDROP_MOTION_SPEED] = speed.coerceIn(25, 200) }
    }

    suspend fun setBackdropDriftAngle(angle: Float) {
        dataStore.edit { it[Keys.BACKDROP_DRIFT_ANGLE] = angle.mod(360f) }
    }

    suspend fun setCustomFont(slot: FontSlot, path: String?, name: String?) {
        val (pathKey, nameKey) = when (slot) {
            FontSlot.DISPLAY -> Keys.FONT_DISPLAY_PATH to Keys.FONT_DISPLAY_NAME
            FontSlot.BODY -> Keys.FONT_BODY_PATH to Keys.FONT_BODY_NAME
        }
        dataStore.edit { prefs ->
            if (path != null) prefs[pathKey] = path else prefs.remove(pathKey)
            if (name != null) prefs[nameKey] = name else prefs.remove(nameKey)
        }
    }

    suspend fun setFontScale(slot: FontSlot, scale: Int) {
        val key = when (slot) {
            FontSlot.DISPLAY -> Keys.FONT_DISPLAY_SCALE
            FontSlot.BODY -> Keys.FONT_BODY_SCALE
        }
        dataStore.edit { it[key] = scale.coerceIn(50, 150) }
    }

    suspend fun setGridDensity(density: GridDensity) {
        dataStore.edit { it[Keys.UI_DENSITY] = density.name }
    }

    suspend fun setUiScale(scale: Int) {
        dataStore.edit { it[Keys.UI_SCALE] = scale.coerceIn(50, 150) }
    }

    suspend fun setBackgroundBlur(blur: Int) {
        dataStore.edit { it[Keys.BACKGROUND_BLUR] = blur.coerceIn(0, 100) }
    }

    suspend fun setBackgroundSaturation(saturation: Int) {
        dataStore.edit { it[Keys.BACKGROUND_SATURATION] = saturation.coerceIn(0, 100) }
    }

    suspend fun setBackgroundOpacity(opacity: Int) {
        dataStore.edit { it[Keys.BACKGROUND_OPACITY] = opacity.coerceIn(0, 100) }
    }

    suspend fun setUseGameBackground(use: Boolean) {
        dataStore.edit { it[Keys.USE_GAME_BACKGROUND] = use }
    }

    suspend fun setCustomBackgroundPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.CUSTOM_BACKGROUND_PATH] = path
            else prefs.remove(Keys.CUSTOM_BACKGROUND_PATH)
        }
    }

    suspend fun setHomeBackgroundMode(mode: HomeBackgroundMode) {
        dataStore.edit { it[Keys.HOME_BACKGROUND_MODE] = mode.name }
    }

    suspend fun setUseAccentColorFooter(use: Boolean) {
        dataStore.edit { it[Keys.USE_ACCENT_COLOR_FOOTER] = use }
    }

    suspend fun setBoxArtShape(shape: BoxArtShape) {
        dataStore.edit { it[Keys.BOX_ART_SHAPE] = shape.name }
    }

    suspend fun setBoxArtCornerRadius(radius: BoxArtCornerRadius) {
        dataStore.edit { it[Keys.BOX_ART_CORNER_RADIUS] = radius.name }
    }

    suspend fun setBoxArtBorderThickness(thickness: BoxArtBorderThickness) {
        dataStore.edit { it[Keys.BOX_ART_BORDER_THICKNESS] = thickness.name }
    }

    suspend fun setBoxArtBorderStyle(style: BoxArtBorderStyle) {
        dataStore.edit { it[Keys.BOX_ART_BORDER_STYLE] = style.name }
    }

    suspend fun setGlassBorderTint(tint: GlassBorderTint) {
        dataStore.edit { it[Keys.GLASS_BORDER_TINT] = tint.name }
    }

    suspend fun setBoxArtGlowStrength(strength: BoxArtGlowStrength) {
        dataStore.edit { it[Keys.BOX_ART_GLOW_STRENGTH] = strength.name }
    }

    suspend fun setBoxArtOuterEffect(effect: BoxArtOuterEffect) {
        dataStore.edit { it[Keys.BOX_ART_OUTER_EFFECT] = effect.name }
    }

    suspend fun setBoxArtOuterEffectThickness(thickness: BoxArtOuterEffectThickness) {
        dataStore.edit { it[Keys.BOX_ART_OUTER_EFFECT_THICKNESS] = thickness.name }
    }

    suspend fun setGlowColorMode(mode: GlowColorMode) {
        dataStore.edit { it[Keys.GLOW_COLOR_MODE] = mode.name }
    }

    suspend fun setBoxArtInnerEffect(effect: BoxArtInnerEffect) {
        dataStore.edit { it[Keys.BOX_ART_INNER_EFFECT] = effect.name }
    }

    suspend fun setBoxArtInnerEffectThickness(thickness: BoxArtInnerEffectThickness) {
        dataStore.edit { it[Keys.BOX_ART_INNER_EFFECT_THICKNESS] = thickness.name }
    }

    suspend fun setGradientPreset(preset: GradientPreset) {
        dataStore.edit { it[Keys.GRADIENT_PRESET] = preset.name }
    }

    suspend fun setGradientAdvancedMode(enabled: Boolean) {
        dataStore.edit { it[Keys.GRADIENT_ADVANCED_MODE] = enabled }
    }

    suspend fun setSystemIconPosition(position: SystemIconPosition) {
        dataStore.edit { it[Keys.SYSTEM_ICON_POSITION] = position.name }
    }

    suspend fun setSystemIconPadding(padding: SystemIconPadding) {
        dataStore.edit { it[Keys.SYSTEM_ICON_PADDING] = padding.name }
    }

    suspend fun setPlatformIndicatorStyle(style: PlatformIndicatorStyle) {
        dataStore.edit { it[Keys.PLATFORM_INDICATOR_STYLE] = style.name }
    }

    suspend fun setPlatformIndicatorContent(content: PlatformIndicatorContent) {
        dataStore.edit { it[Keys.PLATFORM_INDICATOR_CONTENT] = content.name }
    }

    suspend fun setDefaultView(view: DefaultView) {
        dataStore.edit { it[Keys.DEFAULT_VIEW] = view.name }
    }

    suspend fun setVideoWallpaperEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VIDEO_WALLPAPER_ENABLED] = enabled }
    }

    suspend fun setVideoWallpaperDelaySeconds(seconds: Int) {
        dataStore.edit { it[Keys.VIDEO_WALLPAPER_DELAY_SECONDS] = seconds.coerceIn(0, 10) }
    }

    suspend fun setVideoWallpaperMuted(muted: Boolean) {
        dataStore.edit { it[Keys.VIDEO_WALLPAPER_MUTED] = muted }
    }

    suspend fun setAmbientLedEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_ENABLED] = enabled }
    }

    suspend fun setAmbientLedBrightness(brightness: Int) {
        dataStore.edit { it[Keys.AMBIENT_LED_BRIGHTNESS] = brightness.coerceIn(0, 100) }
    }

    suspend fun setAmbientLedAudioBrightness(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_AUDIO_BRIGHTNESS] = enabled }
    }

    suspend fun setAmbientLedAudioColors(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_AUDIO_COLORS] = enabled }
    }

    suspend fun setAmbientLedColorMode(mode: AmbientLedColorMode) {
        dataStore.edit { it[Keys.AMBIENT_LED_COLOR_MODE] = mode.name }
    }

    suspend fun setAmbientLedCoverArtEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_COVER_ART_ENABLED] = enabled }
    }

    suspend fun setAmbientLedCustomColor(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_CUSTOM_COLOR] = enabled }
    }

    suspend fun setAmbientLedCustomColorHue(hue: Int) {
        dataStore.edit { it[Keys.AMBIENT_LED_CUSTOM_COLOR_HUE] = hue.coerceIn(0, 360) }
    }

    suspend fun setAmbientLedTransitionMs(ms: Int) {
        dataStore.edit { it[Keys.AMBIENT_LED_TRANSITION_MS] = ms }
    }

    suspend fun setAmbientLedScreenEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_SCREEN_ENABLED] = enabled }
    }

    suspend fun setAmbientLedAchievementFlash(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_LED_ACHIEVEMENT_FLASH] = enabled }
    }

    suspend fun setScreenDimmerEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SCREEN_DIMMER_ENABLED] = enabled }
    }

    suspend fun setScreenDimmerTimeoutMinutes(minutes: Int) {
        dataStore.edit { it[Keys.SCREEN_DIMMER_TIMEOUT_MINUTES] = minutes.coerceIn(1, 5) }
    }

    suspend fun setScreenDimmerLevel(level: Int) {
        dataStore.edit { it[Keys.SCREEN_DIMMER_LEVEL] = level.coerceIn(40, 70) }
    }

    suspend fun setDualScreenEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DUAL_SCREEN_ENABLED] = enabled }
    }

    suspend fun setDisplayRoleOverride(override: DisplayRoleOverride) {
        dataStore.edit { it[Keys.DISPLAY_ROLE_OVERRIDE] = override.name }
    }

    suspend fun setDualScreenInputFocus(focus: DualScreenInputFocus) {
        dataStore.edit { it[Keys.DUAL_SCREEN_INPUT_FOCUS] = focus.name }
    }

    suspend fun setInstalledOnlyHome(enabled: Boolean) {
        dataStore.edit { it[Keys.INSTALLED_ONLY_HOME] = enabled }
    }
}
