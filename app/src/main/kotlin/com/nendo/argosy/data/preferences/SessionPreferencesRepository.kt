package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedSession(
    val gameId: Long,
    val emulatorPackage: String,
    val startTime: Instant,
    val coreName: String?,
    val isHardcore: Boolean,
    val channelName: String? = null
)

@Singleton
class SessionPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ACTIVE_SESSION_GAME_ID = stringPreferencesKey("active_session_game_id")
        val ACTIVE_SESSION_EMULATOR = stringPreferencesKey("active_session_emulator")
        val ACTIVE_SESSION_START_TIME = stringPreferencesKey("active_session_start_time")
        val ACTIVE_SESSION_CORE_NAME = stringPreferencesKey("active_session_core_name")
        val ACTIVE_SESSION_IS_HARDCORE = booleanPreferencesKey("active_session_is_hardcore")
        val ACTIVE_SESSION_CHANNEL_NAME = stringPreferencesKey("active_session_channel_name")
    }

    val activeSessionFlow: Flow<PersistedSession?> = dataStore.data.map { prefs ->
        val gameId = prefs[Keys.ACTIVE_SESSION_GAME_ID]?.toLongOrNull() ?: return@map null
        val emulator = prefs[Keys.ACTIVE_SESSION_EMULATOR] ?: return@map null
        val startTime = prefs[Keys.ACTIVE_SESSION_START_TIME]?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        } ?: return@map null

        PersistedSession(
            gameId = gameId,
            emulatorPackage = emulator,
            startTime = startTime,
            coreName = prefs[Keys.ACTIVE_SESSION_CORE_NAME],
            isHardcore = prefs[Keys.ACTIVE_SESSION_IS_HARDCORE] ?: false,
            channelName = prefs[Keys.ACTIVE_SESSION_CHANNEL_NAME]
        )
    }

    suspend fun persistActiveSession(
        gameId: Long,
        emulatorPackage: String,
        startTime: Instant,
        coreName: String?,
        isHardcore: Boolean,
        channelName: String? = null
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_SESSION_GAME_ID] = gameId.toString()
            prefs[Keys.ACTIVE_SESSION_EMULATOR] = emulatorPackage
            prefs[Keys.ACTIVE_SESSION_START_TIME] = startTime.toString()
            if (coreName != null) prefs[Keys.ACTIVE_SESSION_CORE_NAME] = coreName
            else prefs.remove(Keys.ACTIVE_SESSION_CORE_NAME)
            prefs[Keys.ACTIVE_SESSION_IS_HARDCORE] = isHardcore
            if (channelName != null) prefs[Keys.ACTIVE_SESSION_CHANNEL_NAME] = channelName
            else prefs.remove(Keys.ACTIVE_SESSION_CHANNEL_NAME)
        }
    }

    suspend fun clearActiveSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_SESSION_GAME_ID)
            prefs.remove(Keys.ACTIVE_SESSION_EMULATOR)
            prefs.remove(Keys.ACTIVE_SESSION_START_TIME)
            prefs.remove(Keys.ACTIVE_SESSION_CORE_NAME)
            prefs.remove(Keys.ACTIVE_SESSION_IS_HARDCORE)
            prefs.remove(Keys.ACTIVE_SESSION_CHANNEL_NAME)
        }
    }

    suspend fun getPersistedSession(): PersistedSession? {
        val prefs = dataStore.data.first()
        val gameId = prefs[Keys.ACTIVE_SESSION_GAME_ID]?.toLongOrNull() ?: return null
        val emulator = prefs[Keys.ACTIVE_SESSION_EMULATOR] ?: return null
        val startTime = prefs[Keys.ACTIVE_SESSION_START_TIME]?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        } ?: return null

        return PersistedSession(
            gameId = gameId,
            emulatorPackage = emulator,
            startTime = startTime,
            coreName = prefs[Keys.ACTIVE_SESSION_CORE_NAME],
            isHardcore = prefs[Keys.ACTIVE_SESSION_IS_HARDCORE] ?: false,
            channelName = prefs[Keys.ACTIVE_SESSION_CHANNEL_NAME]
        )
    }
}
