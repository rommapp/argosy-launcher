package com.nendo.argosy.data.preferences

enum class SettingsBackupType { BOOLEAN, INT, LONG, FLOAT, STRING }

data class SettingsBackupKey(val name: String, val type: SettingsBackupType)

/**
 * Which DataStore keys a settings backup carries.
 *
 * A key absent from [EXPORTED] is neither exported nor imported. SettingsBackupKeysTest records
 * each excluded key under the group that keeps it out.
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
        SettingsBackupKey("hide_system_navigation_bar", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("hide_system_status_bar", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("lock_screen_art", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("icon_pack", SettingsBackupType.STRING),
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
        SettingsBackupKey("box_art_cache_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("library_box_art_3d", SettingsBackupType.BOOLEAN)
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
        SettingsBackupKey("presentation_style", SettingsBackupType.STRING),
        SettingsBackupKey("presentation_background_blur", SettingsBackupType.INT),
        SettingsBackupKey("default_view", SettingsBackupType.STRING),
        SettingsBackupKey("installed_only_home", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("recent_new_games_limit", SettingsBackupType.INT),
        SettingsBackupKey("show_status_clock", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("show_status_battery", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("show_status_network", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("library_default_platform", SettingsBackupType.STRING),
        SettingsBackupKey("library_default_players", SettingsBackupType.STRING),
        SettingsBackupKey("library_layout", SettingsBackupType.STRING),
        SettingsBackupKey("library_list_style", SettingsBackupType.STRING),
        SettingsBackupKey("library_default_sort", SettingsBackupType.STRING),
        SettingsBackupKey("library_default_sort_desc", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("library_default_source", SettingsBackupType.STRING),
        SettingsBackupKey("sort_favorites_first", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("sort_installed_first", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("region_priority", SettingsBackupType.STRING)
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
        SettingsBackupKey("haptic_strength", SettingsBackupType.FLOAT),
        SettingsBackupKey("ambient_audio_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("ambient_audio_volume", SettingsBackupType.INT),
        SettingsBackupKey("ambient_audio_shuffle", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("ambient_audio_play_in_background", SettingsBackupType.BOOLEAN)
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
        SettingsBackupKey("select_swap_mode", SettingsBackupType.STRING),
        SettingsBackupKey("open_navigation_key", SettingsBackupType.INT),
        SettingsBackupKey("open_quick_panel_key", SettingsBackupType.INT),
        SettingsBackupKey("quick_panel_in_emulators", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("nav_ring_routes", SettingsBackupType.STRING),
        SettingsBackupKey("quick_navigation", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("nav_bar_auto_hide", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("ignore_controller_touchpad", SettingsBackupType.BOOLEAN),
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
        SettingsBackupKey("builtin_prefer_newer_server_save", SettingsBackupType.BOOLEAN),
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
        SettingsBackupKey("builtin_fast_forward_preserve_pitch", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_dual_screen_match_size", SettingsBackupType.BOOLEAN)
    )

    private val BUILTIN_INPUT = listOf(
        SettingsBackupKey("builtin_analog_as_dpad", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_dpad_as_analog", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_rumble_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_motion_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_gyro_aim_invert_x", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_gyro_aim_invert_y", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("builtin_gyro_aim_sensitivity", SettingsBackupType.INT),
        SettingsBackupKey("builtin_select_as_back", SettingsBackupType.STRING)
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
        SettingsBackupKey("auto_start_downloads", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("instant_download_threshold_mb", SettingsBackupType.INT),
        SettingsBackupKey("stage_downloads_internally", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("folder_name_from_rom", SettingsBackupType.BOOLEAN),
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
        SettingsBackupKey("sync_filter_exclude_unofficial", SettingsBackupType.BOOLEAN),
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

    private val SESSIONS = listOf(
        SettingsBackupKey("close_emulator_on_session_end", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("pre_launch_recaps_enabled", SettingsBackupType.BOOLEAN),
        SettingsBackupKey("monthly_recap_enabled", SettingsBackupType.BOOLEAN)
    )

    val EXPORTED: List<SettingsBackupKey> =
        APPEARANCE + BACKDROP + BOX_ART + BACKGROUND + HOME_AND_LIBRARY + APP_SHELVES +
            FEEDBACK + NAVIGATION + BUILTIN_GENERAL + BUILTIN_VIDEO + BUILTIN_AUDIO +
            BUILTIN_MOTION + BUILTIN_INPUT + BUILTIN_TOUCH + BUILTIN_HUD + BUILTIN_SPEEDRUN +
            DOWNLOADS_AND_CACHES + SYNC_FILTERS + MEDIA_PLAYBACK + UPDATES + SESSIONS

    val BY_NAME: Map<String, SettingsBackupKey> = EXPORTED.associateBy { it.name }
}
