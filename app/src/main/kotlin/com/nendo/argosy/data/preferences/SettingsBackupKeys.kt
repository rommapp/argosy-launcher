package com.nendo.argosy.data.preferences

enum class SettingsBackupType { BOOLEAN, INT, LONG, FLOAT, STRING }

data class SettingsBackupKey(val name: String, val type: SettingsBackupType)

/**
 * Which DataStore keys a settings backup carries.
 *
 * Membership is opt-in and by name. A key absent from [EXPORTED] is not exported and is ignored on
 * import, so a setting added elsewhere and never classified here simply does not travel. That is
 * the failure worth having: a forgotten key costs a user one trip back through a settings screen,
 * where a forgotten key on a denylist would ship in every backup file until somebody noticed.
 *
 * Every key the app declares today has been classified. The groups below are the reasons a key is
 * held out, described by what they are rather than by listing their members, because the list is
 * what goes stale. A key answering to any of these descriptions does not belong in [EXPORTED] even
 * when it sits beside one that does.
 *
 * - Credentials and identity. Tokens, session tokens, account and user ids, device ids, install
 *   key aliases, server addresses. A backup file is meant to be readable, moved between devices
 *   and pasted into a bug report. An identity that copies is cloned rather than restored, and the
 *   install-scoped keys are identity even though none of them reads as a password.
 * - Presence, sharing and service-account preferences. What the device tells other people about
 *   what is being played or watched, the notifications that ride on a friends list, and the
 *   toggles that only mean anything while signed into a service this file deliberately does not
 *   sign you into. A backup is shareable, and applying someone else's should never quietly change
 *   what your device broadcasts.
 * - Sync watermarks and resume state. These record work already done against one library on one
 *   device. Importing one tells a fresh install it has already synced, and the work it skips is
 *   the work it has never done.
 * - One-shot device migrations, first-run and version markers. They gate work that must run
 *   exactly once per device. Marking one done skips a migration the target still needs; clearing
 *   one re-runs a migration already applied, and some of those delete rows.
 * - Live session state. It describes a session belonging to another device, and a restore that
 *   claims a session is in progress leaves the target reconciling against nothing.
 * - Save and state safety gates. The switches that decide whether saves and states sync, are
 *   watched, are isolated, or are checked for integrity. Every one of them fails quietly when
 *   wrong: an imported "off" stops protecting data without saying so, and the cost of setting it
 *   by hand is one toggle.
 * - Filesystem locations. Not secret, and among the things users most want carried, but a path
 *   that does not exist on the target is worse than an unset one, and the save and state paths
 *   feed resolvers where a wrong answer misplaces files rather than failing. These need per-key
 *   verification on import rather than a blanket copy.
 * - Hardware and unit description. Display roles, dual-screen arrangement, ambient LED, the
 *   screen dimmer, scaling tied to the panel, architecture overrides, bound controller ids. These
 *   describe the device in hand, not the person holding it, and the same values on other hardware
 *   are wrong rather than merely unhelpful.
 * - Derived caches and generated state. Recommendations, penalties, storage snapshots, search
 *   history, cached counters. All of it rebuilds from the library, and an imported copy describes
 *   another device's library until it does.
 * - Pointers into a local library. Anything holding a row id. Local ids are per-install, so the
 *   same number names a different row on the target.
 * - Diagnostics and debug switches. File logging and its verbosity, save-path debug tracing.
 *   They describe one investigation on one device and are meant to be turned on deliberately.
 */
object SettingsBackupKeys {

    private val APPEARANCE = listOf(
        SettingsBackupKey("theme_mode", SettingsBackupType.STRING),
        SettingsBackupKey("primary_color", SettingsBackupType.INT),
        SettingsBackupKey("secondary_color", SettingsBackupType.INT),
        SettingsBackupKey("tertiary_color", SettingsBackupType.INT),
        SettingsBackupKey("surface_tint_bleed", SettingsBackupType.INT),
        SettingsBackupKey("gradient_preset", SettingsBackupType.STRING),
        SettingsBackupKey("gradient_advanced_mode", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("glow_color_mode", SettingsBackupType.STRING),
        SettingsBackupKey("glass_border_tint", SettingsBackupType.STRING),
        SettingsBackupKey("ui_density", SettingsBackupType.STRING),
        SettingsBackupKey("font_display_scale", SettingsBackupType.INT),
        SettingsBackupKey("font_body_scale", SettingsBackupType.INT),
        SettingsBackupKey("game_detail_theme", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("use_accent_color_footer", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("compact_footer", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("system_icon_position", SettingsBackupType.STRING),
        SettingsBackupKey("system_icon_padding", SettingsBackupType.STRING),
        SettingsBackupKey("platform_indicator_style", SettingsBackupType.STRING),
        SettingsBackupKey("platform_indicator_content", SettingsBackupType.STRING)
    )

    private val BACKDROP = listOf(
        SettingsBackupKey("backdrop_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("backdrop_preset", SettingsBackupType.STRING),
        SettingsBackupKey("backdrop_cell_size", SettingsBackupType.INT),
        SettingsBackupKey("backdrop_scatter", SettingsBackupType.INT),
        SettingsBackupKey("backdrop_scale_jitter", SettingsBackupType.INT),
        SettingsBackupKey("backdrop_strength", SettingsBackupType.INT),
        SettingsBackupKey("backdrop_edge_style", SettingsBackupType.STRING),
        SettingsBackupKey("backdrop_vertex_icons", SettingsBackupType.STRING),
        SettingsBackupKey("backdrop_seed", SettingsBackupType.LONG),
        SettingsBackupKey("backdrop_motion", SettingsBackupType.STRING),
        SettingsBackupKey("backdrop_motion_speed", SettingsBackupType.INT),
        SettingsBackupKey("backdrop_drift_angle", SettingsBackupType.FLOAT)
    )

    private val BOX_ART = listOf(
        SettingsBackupKey("box_art_shape", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_corner_radius", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_border_thickness", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_border_style", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_glow_strength", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_inner_effect", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_inner_effect_thickness", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_outer_effect", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_outer_effect_thickness", SettingsBackupType.STRING),
        SettingsBackupKey("box_art_cache_enabled", SettingsBackupType.BOOLEAN)
    )

    private val BACKGROUND = listOf(
        SettingsBackupKey("background_blur", SettingsBackupType.INT),
        SettingsBackupKey("background_saturation", SettingsBackupType.INT),
        SettingsBackupKey("background_opacity", SettingsBackupType.INT),
        SettingsBackupKey("use_game_background", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("home_background_mode", SettingsBackupType.STRING),
        SettingsBackupKey("video_wallpaper_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("video_wallpaper_muted", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("video_wallpaper_delay_seconds", SettingsBackupType.INT)
    )

    private val HOME_AND_LIBRARY = listOf(
        SettingsBackupKey("home_layout_config", SettingsBackupType.STRING),
        SettingsBackupKey("default_view", SettingsBackupType.STRING),
        SettingsBackupKey("installed_only_home", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("library_default_platform", SettingsBackupType.STRING),
        SettingsBackupKey("library_default_sort", SettingsBackupType.STRING),
        SettingsBackupKey("library_default_sort_desc", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("library_default_source", SettingsBackupType.STRING),
        SettingsBackupKey("sort_favorites_first", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sort_installed_first", SettingsBackupType.BOOLEAN)
    )

    /**
     * App shelves are lists of package names. A package the target does not have is inert rather
     * than broken, which is what makes them safe to carry despite naming things outside Argosy.
     */
    private val APP_SHELVES = listOf(
        SettingsBackupKey("app_order", SettingsBackupType.STRING),
        SettingsBackupKey("hidden_apps", SettingsBackupType.STRING),
        SettingsBackupKey("visible_system_apps", SettingsBackupType.STRING),
        SettingsBackupKey("secondary_home_apps", SettingsBackupType.STRING)
    )

    private val FEEDBACK = listOf(
        SettingsBackupKey("sound_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sound_volume", SettingsBackupType.INT),
        SettingsBackupKey("sound_configs", SettingsBackupType.STRING),
        SettingsBackupKey("haptic_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("ambient_audio_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("ambient_audio_volume", SettingsBackupType.INT),
        SettingsBackupKey("ambient_audio_shuffle", SettingsBackupType.BOOLEAN)
    )

    private val NAVIGATION = listOf(
        SettingsBackupKey("app_language", SettingsBackupType.STRING),
        SettingsBackupKey("menu_wrap_mode", SettingsBackupType.STRING),
        SettingsBackupKey("controller_layout", SettingsBackupType.STRING),
        SettingsBackupKey("nintendo_button_layout", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("swap_xy", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("swap_start_select", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("select_l_combo", SettingsBackupType.STRING),
        SettingsBackupKey("select_r_combo", SettingsBackupType.STRING),
        SettingsBackupKey("grip_reserve_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("grip_reserve_mode", SettingsBackupType.STRING),
        SettingsBackupKey("grip_reserve_percent", SettingsBackupType.INT)
    )

    private val BUILTIN_GENERAL = listOf(
        SettingsBackupKey("builtin_libretro_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_core_selections", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_ingame_menu_two_column", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_limit_hotkeys_to_player1", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_default_to_hardcore", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_default_to_hardcore_mode", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_auto_save_state", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_auto_restore_state", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_auto_restore_state_mode", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_hw_core_save_states", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_VIDEO = listOf(
        SettingsBackupKey("builtin_aspect_ratio", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_filter", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_shader", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_shader_chain", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_rotation", SettingsBackupType.INT),
        SettingsBackupKey("builtin_portrait_position", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_overscan_crop", SettingsBackupType.INT),
        SettingsBackupKey("builtin_black_frame_insertion", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_skip_duplicate_frames", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_force_software_timing", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_frames_enabled", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_AUDIO = listOf(
        SettingsBackupKey("builtin_audio_volume", SettingsBackupType.INT),
        SettingsBackupKey("builtin_low_latency_audio", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_MOTION = listOf(
        SettingsBackupKey("builtin_rewind_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_rewind_buffer_duration", SettingsBackupType.INT),
        SettingsBackupKey("builtin_rewind_speed", SettingsBackupType.INT),
        SettingsBackupKey("builtin_fast_forward_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_fast_forward_mode", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_fast_forward_speed", SettingsBackupType.INT),
        SettingsBackupKey("builtin_fast_forward_preserve_pitch", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_INPUT = listOf(
        SettingsBackupKey("builtin_analog_as_dpad", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_dpad_as_analog", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_rumble_enabled", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_TOUCH = listOf(
        SettingsBackupKey("builtin_touch_allow_long_press_edit", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_coloured_face_buttons", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_fade_on_idle", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_genesis_6_button", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_haptic", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_lock_orientation", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_mirror_180", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_opacity_landscape", SettingsBackupType.FLOAT),
        SettingsBackupKey("builtin_touch_opacity_portrait", SettingsBackupType.FLOAT),
        SettingsBackupKey("builtin_touch_show_when_no_gamepad", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_touch_size_scale", SettingsBackupType.FLOAT),
        SettingsBackupKey("builtin_touch_swap_handed", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_HUD = listOf(
        SettingsBackupKey("builtin_hud_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_hud_corner", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_hud_show_battery", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_hud_show_clock", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_hud_show_fps", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_hud_show_last_save", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_hud_show_playtime", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_SPEEDRUN = listOf(
        SettingsBackupKey("builtin_speedrun_panel_side", SettingsBackupType.STRING),
        SettingsBackupKey("builtin_speedrun_panel_width", SettingsBackupType.INT),
        SettingsBackupKey("builtin_speedrun_start_on_reset", SettingsBackupType.BOOLEAN)
    )

    private val DOWNLOADS_AND_CACHES = listOf(
        SettingsBackupKey("max_concurrent_downloads", SettingsBackupType.INT),
        SettingsBackupKey("instant_download_threshold_mb", SettingsBackupType.INT),
        SettingsBackupKey("stage_downloads_internally", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("download_category_defaults", SettingsBackupType.STRING),
        SettingsBackupKey("download_category_platform_overrides", SettingsBackupType.STRING),
        SettingsBackupKey("save_cache_limit", SettingsBackupType.INT)
    )

    /**
     * Which roms a sync brings in, keyed by region and release kind rather than by anything local.
     * Deliberately excludes the orphan-cleanup switch, which decides whether a sync may delete
     * rows and belongs with the save and state safety gates.
     */
    private val SYNC_FILTERS = listOf(
        SettingsBackupKey("sync_filter_regions", SettingsBackupType.STRING),
        SettingsBackupKey("sync_filter_region_mode", SettingsBackupType.STRING),
        SettingsBackupKey("sync_filter_exclude_beta", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sync_filter_exclude_proto", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sync_filter_exclude_demo", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sync_filter_exclude_hack", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sync_screenshots_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("upload_screenshots_enabled", SettingsBackupType.BOOLEAN)
    )

    /**
     * How media plays, which survives a re-login because none of it names the server or the
     * account. The identity half of the media settings is excluded with the other credentials.
     */
    private val MEDIA_PLAYBACK = listOf(
        SettingsBackupKey("jellyfin_audio_language", SettingsBackupType.STRING),
        SettingsBackupKey("jellyfin_subtitle_language", SettingsBackupType.STRING),
        SettingsBackupKey("jellyfin_subtitle_mode", SettingsBackupType.STRING),
        SettingsBackupKey("jellyfin_burn_in_image_subtitles", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("jellyfin_confirm_player_exit", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("jellyfin_download_quality", SettingsBackupType.STRING),
        SettingsBackupKey("jellyfin_max_streaming_bitrate", SettingsBackupType.STRING)
    )

    private val UPDATES = listOf(
        SettingsBackupKey("beta_updates_enabled", SettingsBackupType.BOOLEAN)
    )

    val EXPORTED: List<SettingsBackupKey> =
        APPEARANCE + BACKDROP + BOX_ART + BACKGROUND + HOME_AND_LIBRARY + APP_SHELVES +
            FEEDBACK + NAVIGATION + BUILTIN_GENERAL + BUILTIN_VIDEO + BUILTIN_AUDIO +
            BUILTIN_MOTION + BUILTIN_INPUT + BUILTIN_TOUCH + BUILTIN_HUD + BUILTIN_SPEEDRUN +
            DOWNLOADS_AND_CACHES + SYNC_FILTERS + MEDIA_PLAYBACK + UPDATES

    val BY_NAME: Map<String, SettingsBackupKey> = EXPORTED.associateBy { it.name }
}
