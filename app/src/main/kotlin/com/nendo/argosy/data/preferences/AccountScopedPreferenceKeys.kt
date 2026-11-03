package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.Preferences

object AccountScopedPreferenceKeys {

    private val RETROACHIEVEMENTS = setOf(
        "ra_username",
        "ra_token",
        "ra_web_api_key",
        "ra_proxy_enabled",
        "ra_proxy_address"
    )

    private val SYNC_WATERMARKS_AND_FILTERS = setOf(
        "last_romm_sync",
        "romm_changes_watermark",
        "last_favorites_sync",
        "last_favorites_check",
        "last_negotiate_at",
        "last_state_validation",
        "sync_resume_generation",
        "sync_resume_completed",
        "sync_filter_regions",
        "sync_filter_region_mode",
        "sync_filter_exclude_beta",
        "sync_filter_exclude_proto",
        "sync_filter_exclude_demo",
        "sync_filter_exclude_hack",
        "sync_filter_exclude_unofficial"
    )

    private val REGION_PRIORITY = setOf(
        "region_priority"
    )

    private val DOWNLOAD_CATEGORIES = setOf(
        "download_category_defaults",
        "download_category_platform_overrides"
    )

    private val SOCIAL = setOf(
        "social_session_token",
        "social_user_id",
        "social_username",
        "social_display_name",
        "social_avatar_color",
        "social_avatar_doodle",
        "social_avatar_use_doodle",
        "social_online_status_enabled",
        "social_show_now_playing",
        "social_notify_friend_online",
        "social_notify_friend_playing",
        "social_suppress_notifications_in_game",
        "social_hidden_game_ids",
        "social_last_play_session_sync",
        "discord_rich_presence_enabled"
    )

    private val QUAYPASS = setOf(
        "quaypass_enabled",
        "quaypass_avatar_sync_pending",
        "quaypass_message_sync_pending",
        "quaypass_greeting",
        "quaypass_ticket_balance",
        "quaypass_pending_friend_requests",
        "quaypass_client_install_id",
        "quaypass_credential",
        "quaypass_credential_expires_at",
        "quaypass_install_key_alias",
        "quaypass_install_social_user_id"
    )

    /**
     * Look and feel, which follows the person rather than the device. Deliberately excludes the
     * hardware-shaped keys that share DisplayPreferencesRepository with them: ambient LED, the
     * dual-screen and display-role keys, the screen dimmer and ui_scale all describe the unit
     * and must not fork per account.
     */
    private val INTERFACE = setOf(
        "theme_mode",
        "primary_color",
        "secondary_color",
        "tertiary_color",
        "surface_tint_bleed",
        "backdrop_enabled",
        "backdrop_preset",
        "backdrop_cell_size",
        "backdrop_scatter",
        "backdrop_scale_jitter",
        "backdrop_strength",
        "backdrop_edge_style",
        "backdrop_vertex_icons",
        "backdrop_seed",
        "backdrop_motion",
        "backdrop_motion_speed",
        "backdrop_drift_angle",
        "font_display_path",
        "font_display_name",
        "font_display_scale",
        "font_body_path",
        "font_body_name",
        "font_body_scale",
        "background_blur",
        "background_saturation",
        "background_opacity",
        "use_game_background",
        "custom_background_path",
        "home_background_mode",
        "home_layout_config",
        "presentation_style",
        "presentation_background_blur",
        "use_accent_color_footer",
        "icon_pack",
        "installed_only_home",
        "recent_new_games_limit",
        "video_wallpaper_enabled",
        "video_wallpaper_muted",
        "video_wallpaper_delay_seconds",
        "box_art_shape",
        "box_art_corner_radius",
        "box_art_border_thickness",
        "box_art_border_style",
        "box_art_glow_strength",
        "box_art_inner_effect",
        "box_art_inner_effect_thickness",
        "box_art_outer_effect",
        "box_art_outer_effect_thickness",
        "glass_border_tint",
        "glow_color_mode",
        "gradient_preset",
        "gradient_advanced_mode",
        "system_icon_position",
        "system_icon_padding",
        "platform_indicator_style",
        "platform_indicator_content",
        "default_view",
        "ui_density",
        "sound_enabled",
        "sound_volume",
        "sound_configs",
        "haptic_enabled",
        "haptic_strength",
        "game_detail_theme",
        "ambient_audio_enabled",
        "ambient_audio_volume",
        "ambient_audio_uri",
        "ambient_audio_shuffle",
        "ambient_audio_play_in_background"
    )

    private val RECAPS = setOf(
        "pre_launch_recaps_enabled",
        "monthly_recap_enabled",
        "last_monthly_recap_shown",
        "pre_launch_recap_baseline"
    )

    val PER_ACCOUNT: Set<String> =
        RETROACHIEVEMENTS + SYNC_WATERMARKS_AND_FILTERS + REGION_PRIORITY + DOWNLOAD_CATEGORIES +
            SOCIAL + QUAYPASS + INTERFACE + RECAPS

    fun isPerAccount(key: Preferences.Key<*>): Boolean = key.name in PER_ACCOUNT
}
