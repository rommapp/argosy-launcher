package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncPreferences(
    val rommBaseUrl: String? = null,
    val rommUsername: String? = null,
    val rommUserId: Long? = null,
    val rommAvatarPath: String? = null,
    val rommToken: String? = null,
    val rommDeviceId: String? = null,
    val rommDeviceClientVersion: String? = null,
    val raUsername: String? = null,
    val raToken: String? = null,
    val raWebApiKey: String? = null,
    val raProxyEnabled: Boolean = false,
    val raProxyAddress: String = "",
    val lastRommSync: Instant? = null,
    val rommChangesWatermark: Instant? = null,
    val lastFavoritesSync: Instant? = null,
    val lastFavoritesCheck: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val regionPriority: List<String> = SyncFilterPreferences.ALL_KNOWN_REGIONS,
    val syncScreenshotsEnabled: Boolean = false,
    val uploadScreenshotsEnabled: Boolean = true,
    val boxArtCacheEnabled: Boolean = true,
    val saveSyncEnabled: Boolean = false,
    val saveSyncAnswered: Boolean = false,
    val librarySourceConfirmed: Boolean = false,
    val secureSaves: Boolean = true,
    val stateCacheEnabled: Boolean = true,
    val saveCacheLimit: Int = 10,
    val saveDebugLoggingEnabled: Boolean = false,
    val imageCachePath: String? = null,
    val socialSessionToken: String? = null,
    val socialUserId: String? = null,
    val socialUsername: String? = null,
    val socialDisplayName: String? = null,
    val socialAvatarColor: String? = null,
    val socialAvatarDoodle: String? = null,
    val socialAvatarUseDoodle: Boolean = false,
    val socialOnlineStatusEnabled: Boolean = true,
    val socialShowNowPlaying: Boolean = true,
    val socialNotifyFriendOnline: Boolean = true,
    val socialNotifyFriendPlaying: Boolean = true,
    val discordRichPresenceEnabled: Boolean = true,
    val socialSuppressNotificationsInGame: Boolean = false,
    val lastPlaySessionSync: Instant? = null,
    val lastStateValidation: Instant? = null,
    val quayPassEnabled: Boolean = false,
    val quayPassAvatarSyncPending: Boolean = false,
    val quayPassMessageSyncPending: Boolean = false,
    val quayPassGreeting: String? = null,
    val quayPassTicketBalance: Int = 0
)

@Singleton
class SyncPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        const val MIGRATION_NOTICES_KEPT = 200
    }

    private object Keys {
        val ROMM_URL = stringPreferencesKey("romm_url")
        val ROMM_USERNAME = stringPreferencesKey("romm_username")
        val ROMM_USER_ID = longPreferencesKey("romm_user_id")
        val ROMM_AVATAR_PATH = stringPreferencesKey("romm_avatar_path")
        val ROMM_TOKEN = stringPreferencesKey("romm_token")
        val ROMM_DEVICE_ID = stringPreferencesKey("romm_device_id")
        val ROMM_DEVICE_CLIENT_VERSION = stringPreferencesKey("romm_device_client_version")
        val ROMM_SNAPSHOTS_SERVER = stringPreferencesKey("romm_snapshots_server")
        val ROMM_SNAPSHOTS_SUPPORTED = booleanPreferencesKey("romm_snapshots_supported")
        val RA_USERNAME = stringPreferencesKey("ra_username")
        val RA_TOKEN = stringPreferencesKey("ra_token")
        val RA_WEB_API_KEY = stringPreferencesKey("ra_web_api_key")
        val RA_PROXY_ENABLED = booleanPreferencesKey("ra_proxy_enabled")
        val RA_PROXY_ADDRESS = stringPreferencesKey("ra_proxy_address")
        val LAST_ROMM_SYNC = stringPreferencesKey("last_romm_sync")
        val ROMM_CHANGES_WATERMARK = stringPreferencesKey("romm_changes_watermark")
        val LAST_ROMM_FULL_SYNC = stringPreferencesKey("last_romm_full_sync")
        val LAST_FAVORITES_SYNC = stringPreferencesKey("last_favorites_sync")
        val LAST_FAVORITES_CHECK = stringPreferencesKey("last_favorites_check")
        val SYNC_RESUME_GENERATION = stringPreferencesKey("sync_resume_generation")
        val SYNC_RESUME_COMPLETED = stringPreferencesKey("sync_resume_completed")
        val SYNC_FILTER_REGIONS = stringPreferencesKey("sync_filter_regions")
        val SYNC_FILTER_REGION_MODE = stringPreferencesKey("sync_filter_region_mode")
        val SYNC_FILTER_EXCLUDE_BETA = booleanPreferencesKey("sync_filter_exclude_beta")
        val SYNC_FILTER_EXCLUDE_PROTO = booleanPreferencesKey("sync_filter_exclude_proto")
        val SYNC_FILTER_EXCLUDE_DEMO = booleanPreferencesKey("sync_filter_exclude_demo")
        val SYNC_FILTER_EXCLUDE_HACK = booleanPreferencesKey("sync_filter_exclude_hack")
        val SYNC_FILTER_EXCLUDE_UNOFFICIAL = booleanPreferencesKey("sync_filter_exclude_unofficial")
        val SYNC_FILTER_DELETE_ORPHANS = booleanPreferencesKey("sync_filter_delete_orphans")
        val REGION_PRIORITY = stringPreferencesKey("region_priority")
        val SYNC_SCREENSHOTS_ENABLED = booleanPreferencesKey("sync_screenshots_enabled")
        val UPLOAD_SCREENSHOTS_ENABLED = booleanPreferencesKey("upload_screenshots_enabled")
        val BOX_ART_CACHE_ENABLED = booleanPreferencesKey("box_art_cache_enabled")
        val SAVE_SYNC_ENABLED = booleanPreferencesKey("save_sync_enabled")
        val SAVE_SYNC_ANSWERED = booleanPreferencesKey("save_sync_answered")
        val ROMM_LIBRARY_CONFIRMED = booleanPreferencesKey("romm_library_confirmed")
        val SECURE_SAVES = booleanPreferencesKey("secure_saves")
        val STATE_CACHE_ENABLED = booleanPreferencesKey("state_cache_enabled")
        val SAVE_CACHE_LIMIT = intPreferencesKey("save_cache_limit")
        val SAVE_DEBUG_LOGGING_ENABLED = booleanPreferencesKey("save_debug_logging_enabled")
        val IMAGE_CACHE_PATH = stringPreferencesKey("image_cache_path")
        val SOCIAL_SESSION_TOKEN = stringPreferencesKey("social_session_token")
        val SOCIAL_USER_ID = stringPreferencesKey("social_user_id")
        val SOCIAL_USERNAME = stringPreferencesKey("social_username")
        val SOCIAL_DISPLAY_NAME = stringPreferencesKey("social_display_name")
        val SOCIAL_AVATAR_COLOR = stringPreferencesKey("social_avatar_color")
        val SOCIAL_AVATAR_DOODLE = stringPreferencesKey("social_avatar_doodle")
        val SOCIAL_AVATAR_USE_DOODLE = booleanPreferencesKey("social_avatar_use_doodle")
        val SOCIAL_ONLINE_STATUS_ENABLED = booleanPreferencesKey("social_online_status_enabled")
        val SOCIAL_SHOW_NOW_PLAYING = booleanPreferencesKey("social_show_now_playing")
        val SOCIAL_NOTIFY_FRIEND_ONLINE = booleanPreferencesKey("social_notify_friend_online")
        val SOCIAL_NOTIFY_FRIEND_PLAYING = booleanPreferencesKey("social_notify_friend_playing")
        val DISCORD_RICH_PRESENCE_ENABLED = booleanPreferencesKey("discord_rich_presence_enabled")
        val SOCIAL_SUPPRESS_NOTIFICATIONS_IN_GAME = booleanPreferencesKey("social_suppress_notifications_in_game")
        val LAST_STATE_VALIDATION = stringPreferencesKey("last_state_validation")
        val SOCIAL_LAST_PLAY_SESSION_SYNC = stringPreferencesKey("social_last_play_session_sync")
        val ROMM_PLAY_SESSION_BACKFILL_DONE = stringPreferencesKey("romm_play_session_backfill_done")
        val ROMM_PLAY_SESSION_LAST_UPLOAD = stringPreferencesKey("romm_play_session_last_upload")
        val ROMM_PLAY_SESSION_LAST_PULL = stringPreferencesKey("romm_play_session_last_pull")
        val SOCIAL_HIDDEN_GAME_IDS = stringPreferencesKey("social_hidden_game_ids")
        val SAVE_SYNC_LOCAL_REKEY_DONE = booleanPreferencesKey("save_sync_local_rekey_done")
        val SAVE_PATH_CACHE_PURGED = booleanPreferencesKey("save_path_cache_purged")
        val RA_SIGN_IN_NOTICE_SHOWN = booleanPreferencesKey("ra_sign_in_notice_shown")
        val SAVE_MIGRATION_NOTICES_SHOWN = stringPreferencesKey("save_migration_notices_shown")
        val SIBLING_SPLIT_REPAIR_DONE = booleanPreferencesKey("sibling_split_repair_finished")
        val SIBLING_CONFIG_CARRY_OVER_DONE = booleanPreferencesKey("sibling_config_carry_over_done")
        val QUAYPASS_ENABLED = booleanPreferencesKey("quaypass_enabled")
        val QUAYPASS_AVATAR_SYNC_PENDING = booleanPreferencesKey("quaypass_avatar_sync_pending")
        val QUAYPASS_MESSAGE_SYNC_PENDING = booleanPreferencesKey("quaypass_message_sync_pending")
        val QUAYPASS_GREETING = stringPreferencesKey("quaypass_greeting")
        val QUAYPASS_TICKET_BALANCE = intPreferencesKey("quaypass_ticket_balance")
        val QUAYPASS_PENDING_FRIEND_REQUESTS = stringPreferencesKey("quaypass_pending_friend_requests")
        val LAST_NEGOTIATE_AT = stringPreferencesKey("last_negotiate_at")
        val DOWNLOAD_CATEGORY_DEFAULTS = stringPreferencesKey("download_category_defaults")
        val DOWNLOAD_CATEGORY_PLATFORM_OVERRIDES = stringPreferencesKey("download_category_platform_overrides")
    }

    val downloadCategoryDefaults: Flow<Map<String, Boolean>> =
        dataStore.data.map { DownloadDefaults.deserialize(it[Keys.DOWNLOAD_CATEGORY_DEFAULTS]) }

    val downloadCategoryPlatformOverrides: Flow<Map<String, Map<String, Boolean>>> =
        dataStore.data.map {
            DownloadDefaults.deserializeOverrides(it[Keys.DOWNLOAD_CATEGORY_PLATFORM_OVERRIDES])
        }

    suspend fun setDownloadCategoryDefault(categoryKey: String, include: Boolean) {
        dataStore.edit { prefs ->
            val current = DownloadDefaults.deserialize(prefs[Keys.DOWNLOAD_CATEGORY_DEFAULTS])
            prefs[Keys.DOWNLOAD_CATEGORY_DEFAULTS] =
                DownloadDefaults.serialize(current + (categoryKey to include))
        }
    }

    suspend fun setDownloadCategoryPlatformOverride(
        platformSlug: String,
        categoryKey: String,
        include: Boolean?
    ) {
        dataStore.edit { prefs ->
            val all = DownloadDefaults
                .deserializeOverrides(prefs[Keys.DOWNLOAD_CATEGORY_PLATFORM_OVERRIDES])
                .toMutableMap()
            val platform = (all[platformSlug] ?: emptyMap()).toMutableMap()
            if (include == null) platform.remove(categoryKey) else platform[categoryKey] = include
            if (platform.isEmpty()) all.remove(platformSlug) else all[platformSlug] = platform
            prefs[Keys.DOWNLOAD_CATEGORY_PLATFORM_OVERRIDES] =
                DownloadDefaults.serializeOverrides(all)
        }
    }

    suspend fun getEffectiveDownloadDefaults(platformSlug: String): Map<String, Boolean> {
        val global = downloadCategoryDefaults.first()
        val override = downloadCategoryPlatformOverrides.first()[platformSlug] ?: emptyMap()
        return DownloadDefaults.resolve(global, override)
    }

    /**
     * Whether the server last connected to supported snapshots, or null when [serverKey] is not
     * that server or no server was ever reached.
     */
    suspend fun getRommSnapshotSupport(serverKey: String): Boolean? =
        dataStore.data.map { prefs ->
            prefs[Keys.ROMM_SNAPSHOTS_SUPPORTED]?.takeIf { prefs[Keys.ROMM_SNAPSHOTS_SERVER] == serverKey }
        }.first()

    suspend fun setRommSnapshotSupport(serverKey: String, supported: Boolean) {
        dataStore.edit {
            it[Keys.ROMM_SNAPSHOTS_SERVER] = serverKey
            it[Keys.ROMM_SNAPSHOTS_SUPPORTED] = supported
        }
    }

    suspend fun getLastNegotiateAt(): Instant? =
        dataStore.data.map { it[Keys.LAST_NEGOTIATE_AT]?.let(Instant::parse) }.first()

    suspend fun setLastNegotiateAt(time: Instant) {
        dataStore.edit { it[Keys.LAST_NEGOTIATE_AT] = time.toString() }
    }

    suspend fun isSaveSyncLocalRekeyDone(): Boolean =
        dataStore.data.map { it[Keys.SAVE_SYNC_LOCAL_REKEY_DONE] ?: false }.first()

    suspend fun setSaveSyncLocalRekeyDone() {
        dataStore.edit { it[Keys.SAVE_SYNC_LOCAL_REKEY_DONE] = true }
    }

    suspend fun isSavePathCachePurged(): Boolean =
        dataStore.data.map { it[Keys.SAVE_PATH_CACHE_PURGED] ?: false }.first()

    suspend fun setSavePathCachePurged() {
        dataStore.edit { it[Keys.SAVE_PATH_CACHE_PURGED] = true }
    }

    suspend fun claimRaSignInNotice(): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            if (prefs[Keys.RA_SIGN_IN_NOTICE_SHOWN] != true) {
                prefs[Keys.RA_SIGN_IN_NOTICE_SHOWN] = true
                claimed = true
            }
        }
        return claimed
    }

    suspend fun isSaveMigrationNoticeShown(cacheId: Long): Boolean =
        dataStore.data.map { cacheId in shownMigrationNotices(it[Keys.SAVE_MIGRATION_NOTICES_SHOWN]) }.first()

    suspend fun markSaveMigrationNoticeShown(cacheId: Long) {
        dataStore.edit { prefs ->
            val shown = shownMigrationNotices(prefs[Keys.SAVE_MIGRATION_NOTICES_SHOWN]) - cacheId
            prefs[Keys.SAVE_MIGRATION_NOTICES_SHOWN] = (shown + cacheId).takeLast(MIGRATION_NOTICES_KEPT).joinToString(",")
        }
    }

    private fun shownMigrationNotices(stored: String?): List<Long> =
        stored?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()

    suspend fun isSiblingSplitRepairDone(): Boolean =
        dataStore.data.map { it[Keys.SIBLING_SPLIT_REPAIR_DONE] ?: false }.first()

    suspend fun setSiblingSplitRepairDone() {
        dataStore.edit { it[Keys.SIBLING_SPLIT_REPAIR_DONE] = true }
    }

    suspend fun isSiblingConfigCarryOverDone(): Boolean =
        dataStore.data.map { it[Keys.SIBLING_CONFIG_CARRY_OVER_DONE] ?: false }.first()

    suspend fun setSiblingConfigCarryOverDone() {
        dataStore.edit { it[Keys.SIBLING_CONFIG_CARRY_OVER_DONE] = true }
    }

    suspend fun setQuayPassEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.QUAYPASS_ENABLED] = enabled }
    }

    suspend fun setQuayPassAvatarSyncPending(pending: Boolean) {
        dataStore.edit { it[Keys.QUAYPASS_AVATAR_SYNC_PENDING] = pending }
    }

    suspend fun setQuayPassMessageSyncPending(pending: Boolean) {
        dataStore.edit { it[Keys.QUAYPASS_MESSAGE_SYNC_PENDING] = pending }
    }

    suspend fun setQuayPassGreeting(greeting: String) {
        dataStore.edit {
            val trimmed = greeting.trim()
            if (trimmed.isEmpty()) it.remove(Keys.QUAYPASS_GREETING)
            else it[Keys.QUAYPASS_GREETING] = trimmed
            it[Keys.QUAYPASS_MESSAGE_SYNC_PENDING] = true
        }
    }

    suspend fun setQuayPassGreetingFromServer(greeting: String) {
        dataStore.edit {
            val trimmed = greeting.trim()
            if (trimmed.isEmpty()) it.remove(Keys.QUAYPASS_GREETING)
            else it[Keys.QUAYPASS_GREETING] = trimmed
            it[Keys.QUAYPASS_MESSAGE_SYNC_PENDING] = false
        }
    }

    suspend fun setQuayPassTicketBalance(balance: Int) {
        dataStore.edit { it[Keys.QUAYPASS_TICKET_BALANCE] = balance.coerceAtLeast(0) }
    }

    fun quayPassPendingFriendRequests(): Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[Keys.QUAYPASS_PENDING_FRIEND_REQUESTS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun addQuayPassPendingFriendRequest(accountId: String) {
        dataStore.edit { prefs ->
            val existing = prefs[Keys.QUAYPASS_PENDING_FRIEND_REQUESTS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            prefs[Keys.QUAYPASS_PENDING_FRIEND_REQUESTS] = (existing + accountId).joinToString(",")
        }
    }

    suspend fun setQuayPassPendingFriendRequests(accountIds: Set<String>) {
        dataStore.edit { prefs ->
            if (accountIds.isEmpty()) prefs.remove(Keys.QUAYPASS_PENDING_FRIEND_REQUESTS)
            else prefs[Keys.QUAYPASS_PENDING_FRIEND_REQUESTS] = accountIds.joinToString(",")
        }
    }

    val preferences: Flow<SyncPreferences> = dataStore.data.map { prefs ->
        SyncPreferences(
            rommBaseUrl = prefs[Keys.ROMM_URL],
            rommUsername = prefs[Keys.ROMM_USERNAME],
            rommUserId = prefs[Keys.ROMM_USER_ID],
            rommAvatarPath = prefs[Keys.ROMM_AVATAR_PATH],
            rommToken = prefs[Keys.ROMM_TOKEN],
            rommDeviceId = prefs[Keys.ROMM_DEVICE_ID],
            rommDeviceClientVersion = prefs[Keys.ROMM_DEVICE_CLIENT_VERSION],
            raUsername = prefs[Keys.RA_USERNAME],
            raToken = prefs[Keys.RA_TOKEN],
            raWebApiKey = prefs[Keys.RA_WEB_API_KEY],
            raProxyEnabled = prefs[Keys.RA_PROXY_ENABLED] ?: false,
            raProxyAddress = prefs[Keys.RA_PROXY_ADDRESS] ?: "",
            lastRommSync = prefs[Keys.LAST_ROMM_SYNC]?.let { Instant.parse(it) },
            rommChangesWatermark = prefs[Keys.ROMM_CHANGES_WATERMARK]?.let { Instant.parse(it) },
            lastFavoritesSync = prefs[Keys.LAST_FAVORITES_SYNC]?.let { Instant.parse(it) },
            lastFavoritesCheck = prefs[Keys.LAST_FAVORITES_CHECK]?.let { Instant.parse(it) },
            syncFilters = syncFiltersFrom(prefs),
            regionPriority = resolveRegionPriority(prefs),
            syncScreenshotsEnabled = prefs[Keys.SYNC_SCREENSHOTS_ENABLED] ?: false,
            boxArtCacheEnabled = prefs[Keys.BOX_ART_CACHE_ENABLED] ?: true,
            uploadScreenshotsEnabled = prefs[Keys.UPLOAD_SCREENSHOTS_ENABLED] ?: true,
            saveSyncEnabled = prefs[Keys.SAVE_SYNC_ENABLED] ?: false,
            saveSyncAnswered = prefs[Keys.SAVE_SYNC_ANSWERED] ?: false,
            librarySourceConfirmed = prefs[Keys.ROMM_LIBRARY_CONFIRMED] ?: false,
            secureSaves = prefs[Keys.SECURE_SAVES] ?: true,
            stateCacheEnabled = prefs[Keys.STATE_CACHE_ENABLED] ?: true,
            saveCacheLimit = prefs[Keys.SAVE_CACHE_LIMIT] ?: 10,
            saveDebugLoggingEnabled = prefs[Keys.SAVE_DEBUG_LOGGING_ENABLED] ?: false,
            imageCachePath = prefs[Keys.IMAGE_CACHE_PATH],
            socialSessionToken = prefs[Keys.SOCIAL_SESSION_TOKEN],
            socialUserId = prefs[Keys.SOCIAL_USER_ID],
            socialUsername = prefs[Keys.SOCIAL_USERNAME],
            socialDisplayName = prefs[Keys.SOCIAL_DISPLAY_NAME],
            socialAvatarColor = prefs[Keys.SOCIAL_AVATAR_COLOR],
            socialAvatarDoodle = prefs[Keys.SOCIAL_AVATAR_DOODLE],
            socialAvatarUseDoodle = prefs[Keys.SOCIAL_AVATAR_USE_DOODLE] ?: false,
            socialOnlineStatusEnabled = prefs[Keys.SOCIAL_ONLINE_STATUS_ENABLED] ?: true,
            socialShowNowPlaying = prefs[Keys.SOCIAL_SHOW_NOW_PLAYING] ?: true,
            socialNotifyFriendOnline = prefs[Keys.SOCIAL_NOTIFY_FRIEND_ONLINE] ?: true,
            socialNotifyFriendPlaying = prefs[Keys.SOCIAL_NOTIFY_FRIEND_PLAYING] ?: true,
            discordRichPresenceEnabled = prefs[Keys.DISCORD_RICH_PRESENCE_ENABLED] ?: true,
            socialSuppressNotificationsInGame = prefs[Keys.SOCIAL_SUPPRESS_NOTIFICATIONS_IN_GAME] ?: false,
            lastPlaySessionSync = prefs[Keys.SOCIAL_LAST_PLAY_SESSION_SYNC]?.let { Instant.parse(it) },
            lastStateValidation = prefs[Keys.LAST_STATE_VALIDATION]?.let { Instant.parse(it) },
            quayPassEnabled = prefs[Keys.QUAYPASS_ENABLED] ?: false,
            quayPassAvatarSyncPending = prefs[Keys.QUAYPASS_AVATAR_SYNC_PENDING] ?: false,
            quayPassMessageSyncPending = prefs[Keys.QUAYPASS_MESSAGE_SYNC_PENDING] ?: false,
            quayPassGreeting = prefs[Keys.QUAYPASS_GREETING],
            quayPassTicketBalance = prefs[Keys.QUAYPASS_TICKET_BALANCE] ?: 0
        )
    }.flowOn(Dispatchers.Default)

    suspend fun setRommConfig(url: String?, username: String?) {
        dataStore.edit { prefs ->
            if (url != null) prefs[Keys.ROMM_URL] = url else prefs.remove(Keys.ROMM_URL)
            if (username != null) prefs[Keys.ROMM_USERNAME] = username else prefs.remove(Keys.ROMM_USERNAME)
        }
    }

    /**
     * The device id and the play-session pull time are dropped only when the identity changes.
     * [baseUrl] names the address in use, and one instance answers on more than one address, so
     * a URL change on its own says nothing about which server the device is registered with.
     */
    suspend fun setRomMCredentials(
        baseUrl: String,
        token: String,
        username: String? = null,
        userId: Long? = null
    ) {
        dataStore.edit { prefs ->
            val previousUserId = prefs[Keys.ROMM_USER_ID]
            prefs[Keys.ROMM_URL] = baseUrl
            prefs[Keys.ROMM_TOKEN] = token
            if (username != null) prefs[Keys.ROMM_USERNAME] = username
            if (userId != null) prefs[Keys.ROMM_USER_ID] = userId
            val userChanged = previousUserId != null && userId != null && previousUserId != userId
            if (userChanged) {
                prefs.remove(Keys.ROMM_DEVICE_ID)
                prefs.remove(Keys.ROMM_DEVICE_CLIENT_VERSION)
                prefs.remove(Keys.ROMM_PLAY_SESSION_LAST_PULL)
            }
        }
    }

    suspend fun clearRomMCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ROMM_URL)
            prefs.remove(Keys.ROMM_TOKEN)
            prefs.remove(Keys.ROMM_USERNAME)
            prefs.remove(Keys.ROMM_USER_ID)
            prefs.remove(Keys.ROMM_AVATAR_PATH)
            prefs.remove(Keys.ROMM_DEVICE_ID)
            prefs.remove(Keys.ROMM_DEVICE_CLIENT_VERSION)
            prefs.remove(Keys.ROMM_PLAY_SESSION_BACKFILL_DONE)
            prefs.remove(Keys.ROMM_PLAY_SESSION_LAST_UPLOAD)
            prefs.remove(Keys.ROMM_PLAY_SESSION_LAST_PULL)
        }
    }

    suspend fun setRomMAvatarPath(path: String?) {
        dataStore.edit { prefs ->
            val trimmed = path?.trim().orEmpty()
            if (trimmed.isEmpty()) prefs.remove(Keys.ROMM_AVATAR_PATH) else prefs[Keys.ROMM_AVATAR_PATH] = trimmed
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

    /**
     * Signing in as a different RetroAchievements user drops the stored web API key.
     */
    suspend fun setRACredentials(username: String, token: String) {
        dataStore.edit { prefs ->
            val previous = prefs[Keys.RA_USERNAME]
            if (previous != null && !previous.equals(username, ignoreCase = true)) {
                prefs.remove(Keys.RA_WEB_API_KEY)
            }
            prefs[Keys.RA_USERNAME] = username
            prefs[Keys.RA_TOKEN] = token
        }
    }

    suspend fun clearRACredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.RA_USERNAME)
            prefs.remove(Keys.RA_TOKEN)
            prefs.remove(Keys.RA_WEB_API_KEY)
        }
    }

    suspend fun setRAWebApiKey(key: String?) {
        dataStore.edit { prefs ->
            val trimmed = key?.trim().orEmpty()
            if (trimmed.isEmpty()) prefs.remove(Keys.RA_WEB_API_KEY) else prefs[Keys.RA_WEB_API_KEY] = trimmed
        }
    }

    suspend fun setRAProxy(enabled: Boolean, address: String) {
        dataStore.edit { prefs ->
            prefs[Keys.RA_PROXY_ENABLED] = enabled
            prefs[Keys.RA_PROXY_ADDRESS] = address
        }
    }

    suspend fun setRommChangesWatermark(time: Instant) {
        dataStore.edit { it[Keys.ROMM_CHANGES_WATERMARK] = time.toString() }
    }

    suspend fun setLastRommSyncTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_ROMM_SYNC] = time.toString() }
    }

    suspend fun getLastRommFullSyncTime(): Instant? =
        dataStore.data.map { it[Keys.LAST_ROMM_FULL_SYNC]?.let(Instant::parse) }.first()

    suspend fun setLastRommFullSyncTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_ROMM_FULL_SYNC] = time.toString() }
    }

    suspend fun getSyncResumeGeneration(): Instant? =
        dataStore.data.map { it[Keys.SYNC_RESUME_GENERATION]?.let(Instant::parse) }.first()

    suspend fun getSyncResumeCompletedPlatformIds(): Set<Long> =
        dataStore.data.map { prefs ->
            prefs[Keys.SYNC_RESUME_COMPLETED]
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?.toSet()
                ?: emptySet()
        }.first()

    suspend fun startSyncGeneration(time: Instant) {
        dataStore.edit {
            it[Keys.SYNC_RESUME_GENERATION] = time.toString()
            it[Keys.SYNC_RESUME_COMPLETED] = ""
        }
    }

    suspend fun addSyncResumeCompletedPlatform(platformId: Long) {
        dataStore.edit { prefs ->
            val existing = prefs[Keys.SYNC_RESUME_COMPLETED]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            prefs[Keys.SYNC_RESUME_COMPLETED] = (existing + platformId.toString()).joinToString(",")
        }
    }

    suspend fun clearSyncResume() {
        dataStore.edit {
            it.remove(Keys.SYNC_RESUME_GENERATION)
            it.remove(Keys.SYNC_RESUME_COMPLETED)
        }
    }

    suspend fun setLastStateValidationTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_STATE_VALIDATION] = time.toString() }
    }

    suspend fun setLastFavoritesSyncTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_FAVORITES_SYNC] = time.toString() }
    }

    suspend fun setLastFavoritesCheckTime(time: Instant) {
        dataStore.edit { it[Keys.LAST_FAVORITES_CHECK] = time.toString() }
    }

    /**
     * The mode key is written only when the user toggles the mode, so a curated region list can
     * exist without one. Those lists were built against the old include-by-default behaviour, and
     * reading them under the new blacklist default would invert the user's choice and drop exactly
     * the regions they kept. A stored list therefore still means include; only an absent list takes
     * the new default.
     */
    private fun legacyRegionMode(storedRegions: String?): RegionFilterMode =
        if (storedRegions == null) {
            SyncFilterPreferences.DEFAULT_REGION_MODE
        } else {
            RegionFilterMode.INCLUDE
        }

    private fun syncFiltersFrom(prefs: Preferences): SyncFilterPreferences = SyncFilterPreferences(
        enabledRegions = prefs[Keys.SYNC_FILTER_REGIONS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: SyncFilterPreferences.DEFAULT_REGIONS,
        regionMode = prefs[Keys.SYNC_FILTER_REGION_MODE]
            ?.let { RegionFilterMode.valueOf(it) }
            ?: legacyRegionMode(prefs[Keys.SYNC_FILTER_REGIONS]),
        excludeBeta = prefs[Keys.SYNC_FILTER_EXCLUDE_BETA] ?: true,
        excludePrototype = prefs[Keys.SYNC_FILTER_EXCLUDE_PROTO] ?: true,
        excludeDemo = prefs[Keys.SYNC_FILTER_EXCLUDE_DEMO] ?: true,
        excludeHack = prefs[Keys.SYNC_FILTER_EXCLUDE_HACK] ?: false,
        excludeUnofficial = prefs[Keys.SYNC_FILTER_EXCLUDE_UNOFFICIAL] ?: false,
        deleteOrphans = prefs[Keys.SYNC_FILTER_DELETE_ORPHANS] ?: true
    )

    /**
     * Region order, most preferred first, covering every known region. Collecting it stores the
     * one-time seed when the account has none yet.
     */
    val regionPriority: Flow<List<String>> = dataStore.data
        .onStart { seedRegionPriorityIfAbsent() }
        .map { resolveRegionPriority(it) }
        .distinctUntilChanged()

    /**
     * Region order, most preferred first, covering every known region. The first call on an
     * account without a stored order seeds it from the include-mode sync filter order; later
     * filter changes never alter it.
     */
    suspend fun getRegionPriority(): List<String> {
        seedRegionPriorityIfAbsent()
        return resolveRegionPriority(dataStore.data.first())
    }

    suspend fun setRegionPriority(order: List<String>) {
        dataStore.edit { it[Keys.REGION_PRIORITY] = RegionPriority.serialize(order) }
    }

    private suspend fun seedRegionPriorityIfAbsent() {
        if (dataStore.data.first()[Keys.REGION_PRIORITY] != null) return
        dataStore.edit { seedRegionPriority(it) }
    }

    private fun seedRegionPriority(prefs: MutablePreferences) {
        if (prefs[Keys.REGION_PRIORITY] != null) return
        prefs[Keys.REGION_PRIORITY] = RegionPriority.serialize(RegionPriority.seed(syncFiltersFrom(prefs)))
    }

    private fun resolveRegionPriority(prefs: Preferences): List<String> =
        prefs[Keys.REGION_PRIORITY]?.let(RegionPriority::deserialize)
            ?: RegionPriority.seed(syncFiltersFrom(prefs))

    suspend fun setSyncFilterRegions(regions: List<String>) {
        dataStore.edit {
            seedRegionPriority(it)
            it[Keys.SYNC_FILTER_REGIONS] = regions.joinToString(",")
        }
    }

    suspend fun setSyncFilterRegionMode(mode: RegionFilterMode) {
        dataStore.edit {
            seedRegionPriority(it)
            it[Keys.SYNC_FILTER_REGION_MODE] = mode.name
        }
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

    suspend fun setSyncFilterExcludeUnofficial(exclude: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_EXCLUDE_UNOFFICIAL] = exclude }
    }

    suspend fun setSyncFilterDeleteOrphans(delete: Boolean) {
        dataStore.edit { it[Keys.SYNC_FILTER_DELETE_ORPHANS] = delete }
    }

    suspend fun setBoxArtCacheEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BOX_ART_CACHE_ENABLED] = enabled }
    }

    suspend fun setSyncScreenshotsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYNC_SCREENSHOTS_ENABLED] = enabled }
    }

    suspend fun setUploadScreenshotsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.UPLOAD_SCREENSHOTS_ENABLED] = enabled }
    }

    suspend fun setSaveSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SAVE_SYNC_ENABLED] = enabled }
    }

    suspend fun answerSaveSync(enabled: Boolean) {
        dataStore.edit {
            it[Keys.SAVE_SYNC_ENABLED] = enabled
            it[Keys.SAVE_SYNC_ANSWERED] = true
        }
    }

    suspend fun confirmRommLibrary() {
        dataStore.edit { it[Keys.ROMM_LIBRARY_CONFIRMED] = true }
    }

    /**
     * Marks the values an install finished setup with as confirmed: the save sync answer always,
     * the RomM library only while credentials are stored.
     */
    suspend fun hasRomMCredentials(): Boolean {
        val prefs = dataStore.data.first()
        return !prefs[Keys.ROMM_URL].isNullOrBlank() && !prefs[Keys.ROMM_TOKEN].isNullOrBlank()
    }

    suspend fun confirmExistingSetupValues() {
        dataStore.edit { prefs ->
            prefs[Keys.SAVE_SYNC_ANSWERED] = true
            if (!prefs[Keys.ROMM_URL].isNullOrBlank() && !prefs[Keys.ROMM_TOKEN].isNullOrBlank()) {
                prefs[Keys.ROMM_LIBRARY_CONFIRMED] = true
            }
        }
    }

    suspend fun isSecureSaves(): Boolean =
        dataStore.data.map { it[Keys.SECURE_SAVES] ?: true }.first()

    /**
     * RomM user id of the signed-in account, or null when no account is paired. Read once at the
     * start of an operation and passed down; queue and cache rows are stamped from it so deferred
     * work stays bound to the account that created it.
     */
    suspend fun getRommUserId(): Long? =
        dataStore.data.map { it[Keys.ROMM_USER_ID] }.first()

    suspend fun setSecureSaves(enabled: Boolean) {
        dataStore.edit { it[Keys.SECURE_SAVES] = enabled }
    }

    suspend fun setStateCacheEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.STATE_CACHE_ENABLED] = enabled }
    }

    suspend fun setSaveCacheLimit(limit: Int) {
        dataStore.edit { it[Keys.SAVE_CACHE_LIMIT] = limit }
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

    suspend fun setSocialCredentials(
        sessionToken: String,
        userId: String,
        username: String,
        displayName: String?,
        avatarColor: String?
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.SOCIAL_SESSION_TOKEN] = sessionToken
            prefs[Keys.SOCIAL_USER_ID] = userId
            prefs[Keys.SOCIAL_USERNAME] = username
            if (displayName != null) prefs[Keys.SOCIAL_DISPLAY_NAME] = displayName
            else prefs.remove(Keys.SOCIAL_DISPLAY_NAME)
            if (avatarColor != null) prefs[Keys.SOCIAL_AVATAR_COLOR] = avatarColor
            else prefs.remove(Keys.SOCIAL_AVATAR_COLOR)
        }
    }

    suspend fun clearSocialCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SOCIAL_SESSION_TOKEN)
            prefs.remove(Keys.SOCIAL_USER_ID)
            prefs.remove(Keys.SOCIAL_USERNAME)
            prefs.remove(Keys.SOCIAL_DISPLAY_NAME)
            prefs.remove(Keys.SOCIAL_AVATAR_COLOR)
        }
    }

    suspend fun setSocialAvatarDoodle(doodle: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SOCIAL_AVATAR_DOODLE] = doodle
            prefs[Keys.SOCIAL_AVATAR_USE_DOODLE] = true
            prefs[Keys.QUAYPASS_AVATAR_SYNC_PENDING] = true
        }
    }

    suspend fun setSocialAvatarUseDoodle(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SOCIAL_AVATAR_USE_DOODLE] = enabled
            prefs[Keys.QUAYPASS_AVATAR_SYNC_PENDING] = true
        }
    }

    suspend fun setSocialAvatarFromServer(doodle: String?) {
        dataStore.edit { prefs ->
            if (doodle.isNullOrEmpty()) {
                prefs[Keys.SOCIAL_AVATAR_USE_DOODLE] = false
            } else {
                prefs[Keys.SOCIAL_AVATAR_DOODLE] = doodle
                prefs[Keys.SOCIAL_AVATAR_USE_DOODLE] = true
            }
            prefs[Keys.QUAYPASS_AVATAR_SYNC_PENDING] = false
        }
    }

    suspend fun setSocialOnlineStatusEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOCIAL_ONLINE_STATUS_ENABLED] = enabled }
    }

    suspend fun setSocialShowNowPlaying(enabled: Boolean) {
        dataStore.edit { it[Keys.SOCIAL_SHOW_NOW_PLAYING] = enabled }
    }

    suspend fun setSocialNotifyFriendOnline(enabled: Boolean) {
        dataStore.edit { it[Keys.SOCIAL_NOTIFY_FRIEND_ONLINE] = enabled }
    }

    suspend fun setSocialNotifyFriendPlaying(enabled: Boolean) {
        dataStore.edit { it[Keys.SOCIAL_NOTIFY_FRIEND_PLAYING] = enabled }
    }

    suspend fun setDiscordRichPresenceEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.DISCORD_RICH_PRESENCE_ENABLED] = enabled }
    }

    suspend fun setSocialSuppressNotificationsInGame(enabled: Boolean) {
        dataStore.edit { it[Keys.SOCIAL_SUPPRESS_NOTIFICATIONS_IN_GAME] = enabled }
    }

    suspend fun setLastPlaySessionSyncTime(time: Instant) {
        dataStore.edit { it[Keys.SOCIAL_LAST_PLAY_SESSION_SYNC] = time.toString() }
    }

    /**
     * Which "user:device" pair the one-time RomM play-session backfill has completed for, or null.
     * Compared as a whole so a new account or a re-registered device runs it again.
     */
    suspend fun getRommPlaySessionBackfillDone(): String? =
        dataStore.data.map { it[Keys.ROMM_PLAY_SESSION_BACKFILL_DONE] }.first()

    suspend fun setRommPlaySessionBackfillDone(scopeKey: String) {
        dataStore.edit { it[Keys.ROMM_PLAY_SESSION_BACKFILL_DONE] = scopeKey }
    }

    suspend fun getRommPlaySessionLastUpload(): Instant? =
        dataStore.data.map { it[Keys.ROMM_PLAY_SESSION_LAST_UPLOAD] }.first()?.let { Instant.parse(it) }

    suspend fun setRommPlaySessionLastUpload(time: Instant) {
        dataStore.edit { it[Keys.ROMM_PLAY_SESSION_LAST_UPLOAD] = time.toString() }
    }

    suspend fun getRommPlaySessionLastPull(): Instant? =
        dataStore.data.map { it[Keys.ROMM_PLAY_SESSION_LAST_PULL] }.first()?.let { Instant.parse(it) }

    suspend fun setRommPlaySessionLastPull(time: Instant) {
        dataStore.edit { it[Keys.ROMM_PLAY_SESSION_LAST_PULL] = time.toString() }
    }

    fun hiddenGameIds(): Flow<Set<Int>> = dataStore.data.map { prefs ->
        prefs[Keys.SOCIAL_HIDDEN_GAME_IDS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    /**
     * The IGDB ids of games marked Private, in the shape play-time queries take to leave them
     * out of every total.
     */
    suspend fun privateIgdbIds(): List<Long> = hiddenGameIds().first().map(Int::toLong)

    suspend fun setHiddenGameIds(ids: Set<Int>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(Keys.SOCIAL_HIDDEN_GAME_IDS)
            else prefs[Keys.SOCIAL_HIDDEN_GAME_IDS] = ids.joinToString(",")
        }
    }
}
