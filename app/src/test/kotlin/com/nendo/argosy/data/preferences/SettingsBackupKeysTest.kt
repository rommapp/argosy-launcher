package com.nendo.argosy.data.preferences

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the settings backup to the classification documented on [SettingsBackupKeys].
 *
 * The excluded names live here rather than beside the allowlist so the production file can keep
 * describing exclusion by reason instead of by list. This is the audit: every DataStore key the
 * app declares is either exported or recorded below under the group that keeps it out, and a key
 * added later belongs to neither until somebody decides which.
 */
class SettingsBackupKeysTest {

    private val credentialsAndIdentity = setOf(
        "romm_token", "romm_url", "romm_username", "romm_user_id", "romm_device_id",
        "romm_device_client_version", "ra_token", "ra_username", "ra_proxy_enabled",
        "ra_proxy_address", "social_session_token", "social_user_id", "social_username",
        "social_display_name", "jellyfin_access_token", "jellyfin_device_id",
        "jellyfin_server_url", "jellyfin_user_id", "jellyfin_user_name", "quaypass_credential",
        "quaypass_credential_expires_at", "quaypass_client_install_id",
        "quaypass_install_key_alias", "quaypass_install_social_user_id"
    )

    private val presenceSharingAndServiceAccounts = setOf(
        "discord_rich_presence_enabled", "jellyfin_share_media_presence",
        "social_online_status_enabled", "social_show_now_playing", "social_notify_friend_online",
        "social_notify_friend_playing", "social_suppress_notifications_in_game",
        "social_hidden_game_ids", "social_avatar_color", "social_avatar_doodle",
        "social_avatar_use_doodle", "quaypass_enabled", "quaypass_greeting",
        "quaypass_ticket_balance", "quaypass_pending_friend_requests",
        "quaypass_avatar_sync_pending", "quaypass_message_sync_pending"
    )

    private val syncWatermarksAndResume = setOf(
        "last_romm_sync", "last_favorites_sync", "last_favorites_check", "last_negotiate_at",
        "last_state_validation", "sync_resume_completed", "sync_resume_generation",
        "social_last_play_session_sync", "romm_last_play_session_sync",
        "emulator_update_last_check"
    )

    private val oneShotAndVersionMarkers = setOf(
        "builtin_migration_v2", "first_run_complete", "save_sync_local_rekey_done",
        "save_path_cache_purged", "per_account_prefs_adopted_by", "last_seen_version",
        "last_integrity_check_time"
    )

    private val liveSessionState = setOf(
        "active_session_channel_name", "active_session_core_name", "active_session_emulator",
        "active_session_game_id", "active_session_is_hardcore", "active_session_start_time",
        "active_session_variant_file_id"
    )

    private val saveAndStateSafetyGates = setOf(
        "save_sync_enabled", "save_watcher_enabled", "state_cache_enabled", "secure_saves",
        "weekly_integrity_check_enabled", "sync_filter_delete_orphans"
    )

    private val filesystemLocations = setOf(
        "rom_storage_path", "media_storage_path", "music_storage_path", "image_cache_path",
        "custom_bios_path", "custom_background_path", "builtin_custom_save_path",
        "builtin_custom_state_path", "font_body_path", "font_body_name", "font_display_path",
        "font_display_name", "android_data_saf_uri", "gamenative_sync_dir", "file_logging_path",
        "ambient_audio_uri", "steam_install_volume"
    )

    private val hardwareAndUnit = setOf(
        "ambient_led_achievement_flash", "ambient_led_audio_brightness", "ambient_led_audio_colors",
        "ambient_led_brightness", "ambient_led_color_mode", "ambient_led_cover_art_enabled",
        "ambient_led_custom_color", "ambient_led_custom_color_hue", "ambient_led_enabled",
        "ambient_led_screen_enabled", "ambient_led_transition_ms", "display_role_override",
        "dual_screen_enabled", "dual_screen_input_focus", "screen_dimmer_enabled",
        "screen_dimmer_level", "screen_dimmer_timeout_minutes", "ui_scale",
        "builtin_architecture_override", "grip_auto_controllers", "app_affinity_enabled"
    )

    private val derivedAndGenerated = setOf(
        "recommendation_penalties", "recommended_game_ids", "last_recommendation_generation",
        "last_penalty_decay_week", "storage_attribution_snapshot", "library_recent_searches",
        "platform_order_customised"
    )

    private val diagnostics = setOf(
        "file_logging_enabled", "file_log_level", "save_debug_logging_enabled"
    )

    private val excluded: Set<String> =
        credentialsAndIdentity + presenceSharingAndServiceAccounts + syncWatermarksAndResume +
            oneShotAndVersionMarkers + liveSessionState + saveAndStateSafetyGates +
            filesystemLocations + hardwareAndUnit + derivedAndGenerated + diagnostics

    private val credentialShapes = listOf(
        "token", "credential", "password", "username", "user_name", "user_id", "device_id",
        "install_", "server_url", "active_session", "quaypass_", "romm_", "_path", "_uri",
        "migration", "first_run", "snapshot", "recommend"
    )

    private val declarationPattern = Regex(
        "(string|boolean|int|long|float|double|stringSet)PreferencesKey\\(\"([A-Za-z0-9_]+)\"\\)"
    )

    private fun declaredKeys(): Map<String, String> {
        val roots = listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
        val root = roots.firstOrNull { it.isDirectory }
        assertTrue("Could not locate the main source tree", root != null)
        return root!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { declarationPattern.findAll(it.readText()) }
            .associate { it.groupValues[2] to it.groupValues[1] }
    }

    private fun registryType(type: SettingsBackupType): String = when (type) {
        SettingsBackupType.BOOLEAN -> "boolean"
        SettingsBackupType.INT -> "int"
        SettingsBackupType.LONG -> "long"
        SettingsBackupType.FLOAT -> "float"
        SettingsBackupType.STRING -> "string"
    }

    @Test
    fun `every declared preference key is classified`() {
        val exported = SettingsBackupKeys.BY_NAME.keys
        val unclassified = declaredKeys().keys - exported - excluded
        assertTrue(
            "New preference keys are neither exported nor recorded as excluded: " +
                "${unclassified.sorted()}. Add each to SettingsBackupKeys.EXPORTED or to the " +
                "group in this test that keeps it out.",
            unclassified.isEmpty()
        )
    }

    @Test
    fun `no key is both exported and excluded`() {
        val overlap = SettingsBackupKeys.BY_NAME.keys.intersect(excluded)
        assertTrue("Keys claimed by both lists: ${overlap.sorted()}", overlap.isEmpty())
    }

    @Test
    fun `every exported key exists and matches its declared type`() {
        val declared = declaredKeys()
        val wrong = SettingsBackupKeys.EXPORTED.filter { entry ->
            declared[entry.name] != registryType(entry.type)
        }
        assertTrue(
            "Exported keys missing from the source or declared with another type: " +
                "${wrong.map { it.name }.sorted()}",
            wrong.isEmpty()
        )
    }

    @Test
    fun `no exported key looks like a credential or a device identifier`() {
        val offenders = SettingsBackupKeys.EXPORTED
            .map { it.name }
            .filter { name -> credentialShapes.any { name.contains(it) } }
        assertTrue("Excluded key shapes present in the allowlist: $offenders", offenders.isEmpty())
    }

    @Test
    fun `exported names are unique`() {
        val names = SettingsBackupKeys.EXPORTED.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertEquals(names.size, SettingsBackupKeys.BY_NAME.size)
    }
}
