package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.util.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class AppPreferences(
    val firstRunComplete: Boolean = false,
    val betaUpdatesEnabled: Boolean = false,
    val closeEmulatorOnSessionEnd: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val secondaryHomeApps: List<String> = emptyList(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList(),
    val lastSeenVersion: String? = null,
    val libraryRecentSearches: List<String> = emptyList(),
    val recommendedGameIds: List<Long> = emptyList(),
    val lastRecommendationGeneration: Instant? = null,
    val recommendationPenalties: Map<Long, Float> = emptyMap(),
    val lastPenaltyDecayWeek: String? = null,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val appDisplayTargets: Map<String, String> = emptyMap(),
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val preLaunchRecapsEnabled: Boolean = true,
    val monthlyRecapEnabled: Boolean = true,
    val lastMonthlyRecapShown: YearMonth? = null,
    val preLaunchRecapBaseline: Instant? = null,
    val notificationsEnabled: Boolean = true,
    val notifyDownloadsFinished: Boolean = true,
    val notifyBattery: Boolean = true,
    val notifyCharging: Boolean = true,
    val notifyControllers: Boolean = true
)

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        val BETA_UPDATES_ENABLED = booleanPreferencesKey("beta_updates_enabled")
        val CLOSE_EMULATOR_ON_SESSION_END = booleanPreferencesKey("close_emulator_on_session_end")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val SECONDARY_HOME_APPS = stringPreferencesKey("secondary_home_apps")
        val VISIBLE_SYSTEM_APPS = stringPreferencesKey("visible_system_apps")
        val APP_ORDER = stringPreferencesKey("app_order")
        val PLATFORM_ORDER_CUSTOMISED = booleanPreferencesKey("platform_order_customised")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val LIBRARY_RECENT_SEARCHES = stringPreferencesKey("library_recent_searches")
        val RECOMMENDED_GAME_IDS = stringPreferencesKey("recommended_game_ids")
        val LAST_RECOMMENDATION_GENERATION = stringPreferencesKey("last_recommendation_generation")
        val RECOMMENDATION_PENALTIES = stringPreferencesKey("recommendation_penalties")
        val LAST_PENALTY_DECAY_WEEK = stringPreferencesKey("last_penalty_decay_week")
        val FILE_LOGGING_ENABLED = booleanPreferencesKey("file_logging_enabled")
        val FILE_LOGGING_PATH = stringPreferencesKey("file_logging_path")
        val FILE_LOG_LEVEL = stringPreferencesKey("file_log_level")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val APP_DISPLAY_TARGETS = stringPreferencesKey("app_display_targets")
        val SIBLING_PICK_SEEDED_OWNERS = stringPreferencesKey("sibling_pick_seeded_owners")
        val SIBLING_FULL_PASS_DONE = booleanPreferencesKey("sibling_full_pass_done")
        val VARIANT_FILE_CLEANUP_DONE = booleanPreferencesKey("variant_file_cleanup_done")
        val PRE_LAUNCH_RECAPS_ENABLED = booleanPreferencesKey("pre_launch_recaps_enabled")
        val MONTHLY_RECAP_ENABLED = booleanPreferencesKey("monthly_recap_enabled")
        val LAST_MONTHLY_RECAP_SHOWN = stringPreferencesKey("last_monthly_recap_shown")
        val PRE_LAUNCH_RECAP_BASELINE = stringPreferencesKey("pre_launch_recap_baseline")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_DOWNLOADS_FINISHED = booleanPreferencesKey("notify_downloads_finished")
        val NOTIFY_BATTERY = booleanPreferencesKey("notify_battery")
        val NOTIFY_CHARGING = booleanPreferencesKey("notify_charging")
        val NOTIFY_CONTROLLERS = booleanPreferencesKey("notify_controllers")
    }

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            firstRunComplete = prefs[Keys.FIRST_RUN_COMPLETE] ?: false,
            betaUpdatesEnabled = prefs[Keys.BETA_UPDATES_ENABLED] ?: false,
            closeEmulatorOnSessionEnd = prefs[Keys.CLOSE_EMULATOR_ON_SESSION_END] ?: false,
            hiddenApps = prefs[Keys.HIDDEN_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            secondaryHomeApps = parseDockPins(prefs[Keys.SECONDARY_HOME_APPS]),
            visibleSystemApps = prefs[Keys.VISIBLE_SYSTEM_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            appOrder = prefs[Keys.APP_ORDER]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            lastSeenVersion = prefs[Keys.LAST_SEEN_VERSION],
            libraryRecentSearches = prefs[Keys.LIBRARY_RECENT_SEARCHES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            recommendedGameIds = prefs[Keys.RECOMMENDED_GAME_IDS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList(),
            lastRecommendationGeneration = prefs[Keys.LAST_RECOMMENDATION_GENERATION]?.let { Instant.parse(it) },
            recommendationPenalties = parseRecommendationPenalties(prefs[Keys.RECOMMENDATION_PENALTIES]),
            lastPenaltyDecayWeek = prefs[Keys.LAST_PENALTY_DECAY_WEEK],
            fileLoggingEnabled = prefs[Keys.FILE_LOGGING_ENABLED] ?: false,
            fileLoggingPath = prefs[Keys.FILE_LOGGING_PATH],
            fileLogLevel = LogLevel.fromString(prefs[Keys.FILE_LOG_LEVEL]),
            appDisplayTargets = parseAppDisplayTargets(prefs[Keys.APP_DISPLAY_TARGETS]),
            appLanguage = AppLanguage.fromString(prefs[Keys.APP_LANGUAGE]),
            preLaunchRecapsEnabled = prefs[Keys.PRE_LAUNCH_RECAPS_ENABLED] ?: true,
            monthlyRecapEnabled = prefs[Keys.MONTHLY_RECAP_ENABLED] ?: true,
            lastMonthlyRecapShown = prefs[Keys.LAST_MONTHLY_RECAP_SHOWN]
                ?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
            preLaunchRecapBaseline = prefs[Keys.PRE_LAUNCH_RECAP_BASELINE]
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            notifyDownloadsFinished = prefs[Keys.NOTIFY_DOWNLOADS_FINISHED] ?: true,
            notifyBattery = prefs[Keys.NOTIFY_BATTERY] ?: true,
            notifyCharging = prefs[Keys.NOTIFY_CHARGING] ?: true,
            notifyControllers = prefs[Keys.NOTIFY_CONTROLLERS] ?: true
        )
    }.flowOn(Dispatchers.Default)

    suspend fun setFirstRunComplete() {
        dataStore.edit { it[Keys.FIRST_RUN_COMPLETE] = true }
    }

    suspend fun setBetaUpdatesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BETA_UPDATES_ENABLED] = enabled }
    }

    suspend fun setCloseEmulatorOnSessionEnd(enabled: Boolean) {
        dataStore.edit { it[Keys.CLOSE_EMULATOR_ON_SESSION_END] = enabled }
    }

    suspend fun setHiddenApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) prefs.remove(Keys.HIDDEN_APPS)
            else prefs[Keys.HIDDEN_APPS] = apps.joinToString(",")
        }
    }

    suspend fun toggleDockPin(packageName: String): Boolean {
        require(packageName.isNotBlank())
        var pinned = false
        dataStore.edit { prefs ->
            val current = parseDockPins(prefs[Keys.SECONDARY_HOME_APPS])
            pinned = packageName !in current
            val pins = if (pinned) current + packageName else current - packageName
            if (pins.isEmpty()) prefs.remove(Keys.SECONDARY_HOME_APPS)
            else prefs[Keys.SECONDARY_HOME_APPS] = pins.joinToString(",")
        }
        return pinned
    }

    suspend fun reorderDockPins(expected: List<String>, order: List<String>): Boolean {
        var accepted = false
        dataStore.edit { prefs ->
            val current = parseDockPins(prefs[Keys.SECONDARY_HOME_APPS])
            if (current == expected && order.size == current.size && order.toSet() == current.toSet()) {
                accepted = true
                if (order != current) prefs[Keys.SECONDARY_HOME_APPS] = order.joinToString(",")
            }
        }
        return accepted
    }

    private fun parseDockPins(value: String?): List<String> =
        value?.split(",")?.filter { it.isNotBlank() }?.distinct() ?: emptyList()

    suspend fun setVisibleSystemApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) prefs.remove(Keys.VISIBLE_SYSTEM_APPS)
            else prefs[Keys.VISIBLE_SYSTEM_APPS] = apps.joinToString(",")
        }
    }

    suspend fun setAppOrder(order: List<String>) {
        dataStore.edit { prefs ->
            if (order.isEmpty()) prefs.remove(Keys.APP_ORDER)
            else prefs[Keys.APP_ORDER] = order.joinToString(",")
        }
    }

    /**
     * Whether the user has arranged the platform order themselves. Once they have, the built-in
     * order stops being reapplied at startup, or their arrangement would not survive a relaunch.
     */
    suspend fun isPlatformOrderCustomised(): Boolean =
        dataStore.data.first()[Keys.PLATFORM_ORDER_CUSTOMISED] ?: false

    suspend fun setPlatformOrderCustomised() {
        dataStore.edit { it[Keys.PLATFORM_ORDER_CUSTOMISED] = true }
    }

    suspend fun isSiblingPickSeedDone(ownerUserId: Long): Boolean =
        ownerUserId in parseOwnerIds(dataStore.data.first()[Keys.SIBLING_PICK_SEEDED_OWNERS])

    suspend fun setSiblingPickSeedDone(ownerUserId: Long) {
        dataStore.edit { prefs ->
            val seeded = parseOwnerIds(prefs[Keys.SIBLING_PICK_SEEDED_OWNERS]) + ownerUserId
            prefs[Keys.SIBLING_PICK_SEEDED_OWNERS] = seeded.sorted().joinToString(",")
        }
    }

    private fun parseOwnerIds(raw: String?): Set<Long> =
        raw?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet().orEmpty()

    suspend fun isVariantFileCleanupDone(): Boolean =
        dataStore.data.first()[Keys.VARIANT_FILE_CLEANUP_DONE] ?: false

    suspend fun setVariantFileCleanupDone() {
        dataStore.edit { it[Keys.VARIANT_FILE_CLEANUP_DONE] = true }
    }

    suspend fun isSiblingFullPassDone(): Boolean =
        dataStore.data.first()[Keys.SIBLING_FULL_PASS_DONE] ?: false

    suspend fun setSiblingFullPassDone() {
        dataStore.edit { it[Keys.SIBLING_FULL_PASS_DONE] = true }
    }

    suspend fun setLastSeenVersion(version: String) {
        dataStore.edit { it[Keys.LAST_SEEN_VERSION] = version }
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

    suspend fun setRecommendations(gameIds: List<Long>, timestamp: Instant) {
        dataStore.edit { prefs ->
            if (gameIds.isEmpty()) prefs.remove(Keys.RECOMMENDED_GAME_IDS)
            else prefs[Keys.RECOMMENDED_GAME_IDS] = gameIds.joinToString(",")
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
            if (filtered.isEmpty()) prefs.remove(Keys.RECOMMENDATION_PENALTIES)
            else prefs[Keys.RECOMMENDATION_PENALTIES] = filtered.entries.joinToString(",") { "${it.key}:${it.value}" }
            prefs[Keys.LAST_PENALTY_DECAY_WEEK] = weekKey
        }
    }

    suspend fun setFileLoggingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.FILE_LOGGING_ENABLED] = enabled }
    }

    suspend fun setFileLoggingPath(path: String?) {
        dataStore.edit { prefs ->
            if (path != null) prefs[Keys.FILE_LOGGING_PATH] = path
            else prefs.remove(Keys.FILE_LOGGING_PATH)
        }
    }

    suspend fun setFileLogLevel(level: LogLevel) {
        dataStore.edit { it[Keys.FILE_LOG_LEVEL] = level.name }
    }

    suspend fun setAppLanguage(tag: String) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = tag }
    }

    suspend fun setPreLaunchRecapsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PRE_LAUNCH_RECAPS_ENABLED] = enabled }
    }

    suspend fun setMonthlyRecapEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MONTHLY_RECAP_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setNotifyDownloadsFinished(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_DOWNLOADS_FINISHED] = enabled }
    }

    suspend fun setNotifyBattery(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_BATTERY] = enabled }
    }

    suspend fun setNotifyCharging(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_CHARGING] = enabled }
    }

    suspend fun setNotifyControllers(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_CONTROLLERS] = enabled }
    }

    suspend fun setLastMonthlyRecapShown(month: YearMonth) {
        dataStore.edit { it[Keys.LAST_MONTHLY_RECAP_SHOWN] = month.toString() }
    }

    suspend fun setPreLaunchRecapBaseline(at: Instant) {
        dataStore.edit { it[Keys.PRE_LAUNCH_RECAP_BASELINE] = at.toString() }
    }

    suspend fun setAppDisplayTarget(packageName: String, screenKey: String?) {
        dataStore.edit { prefs ->
            val current = parseAppDisplayTargets(prefs[Keys.APP_DISPLAY_TARGETS]).toMutableMap()
            if (screenKey.isNullOrBlank()) current.remove(packageName) else current[packageName] = screenKey
            if (current.isEmpty()) {
                prefs.remove(Keys.APP_DISPLAY_TARGETS)
            } else {
                prefs[Keys.APP_DISPLAY_TARGETS] = current.entries.joinToString(",") { "${it.key}=${it.value}" }
            }
        }
    }

    private fun parseAppDisplayTargets(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(",")
            .mapNotNull { entry ->
                val packageName = entry.substringBefore('=', "")
                val screenKey = entry.substringAfter('=', "")
                if (packageName.isNotBlank() && screenKey.isNotBlank()) packageName to screenKey else null
            }
            .toMap()
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
}
