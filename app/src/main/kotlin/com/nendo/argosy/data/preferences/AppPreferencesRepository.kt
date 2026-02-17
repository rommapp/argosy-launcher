package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.util.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class AppPreferences(
    val firstRunComplete: Boolean = false,
    val betaUpdatesEnabled: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val secondaryHomeApps: Set<String> = emptySet(),
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
    val appAffinityEnabled: Boolean = false
)

@Singleton
class AppPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        val BETA_UPDATES_ENABLED = booleanPreferencesKey("beta_updates_enabled")
        val HIDDEN_APPS = stringPreferencesKey("hidden_apps")
        val SECONDARY_HOME_APPS = stringPreferencesKey("secondary_home_apps")
        val VISIBLE_SYSTEM_APPS = stringPreferencesKey("visible_system_apps")
        val APP_ORDER = stringPreferencesKey("app_order")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val LIBRARY_RECENT_SEARCHES = stringPreferencesKey("library_recent_searches")
        val RECOMMENDED_GAME_IDS = stringPreferencesKey("recommended_game_ids")
        val LAST_RECOMMENDATION_GENERATION = stringPreferencesKey("last_recommendation_generation")
        val RECOMMENDATION_PENALTIES = stringPreferencesKey("recommendation_penalties")
        val LAST_PENALTY_DECAY_WEEK = stringPreferencesKey("last_penalty_decay_week")
        val FILE_LOGGING_ENABLED = booleanPreferencesKey("file_logging_enabled")
        val FILE_LOGGING_PATH = stringPreferencesKey("file_logging_path")
        val FILE_LOG_LEVEL = stringPreferencesKey("file_log_level")
        val APP_AFFINITY_ENABLED = booleanPreferencesKey("app_affinity_enabled")
    }

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            firstRunComplete = prefs[Keys.FIRST_RUN_COMPLETE] ?: false,
            betaUpdatesEnabled = prefs[Keys.BETA_UPDATES_ENABLED] ?: false,
            hiddenApps = prefs[Keys.HIDDEN_APPS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            secondaryHomeApps = prefs[Keys.SECONDARY_HOME_APPS]
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
            appAffinityEnabled = true
        )
    }

    suspend fun setFirstRunComplete() {
        dataStore.edit { it[Keys.FIRST_RUN_COMPLETE] = true }
    }

    suspend fun setBetaUpdatesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BETA_UPDATES_ENABLED] = enabled }
    }

    suspend fun setHiddenApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) prefs.remove(Keys.HIDDEN_APPS)
            else prefs[Keys.HIDDEN_APPS] = apps.joinToString(",")
        }
    }

    suspend fun setSecondaryHomeApps(apps: Set<String>) {
        dataStore.edit { prefs ->
            if (apps.isEmpty()) prefs.remove(Keys.SECONDARY_HOME_APPS)
            else prefs[Keys.SECONDARY_HOME_APPS] = apps.joinToString(",")
        }
    }

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

    suspend fun setAppAffinityEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.APP_AFFINITY_ENABLED] = enabled }
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
