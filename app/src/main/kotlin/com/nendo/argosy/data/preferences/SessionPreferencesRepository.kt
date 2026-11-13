package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.streaming.StreamClaim
import com.nendo.argosy.data.streaming.StreamSessionManager
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
    val channelName: String? = null,
    val variantFileId: Long? = null,
    val origin: LaunchOrigin = LaunchOrigin.INTERNAL,
    val isNetplayGuest: Boolean = false,
    val streamClaim: StreamClaim? = null
) {
    val isStream: Boolean get() = emulatorPackage == StreamSessionManager.STREAM_PACKAGE
}

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
        val ACTIVE_SESSION_VARIANT_FILE_ID = stringPreferencesKey("active_session_variant_file_id")
        val ACTIVE_SESSION_LAUNCH_ORIGIN = stringPreferencesKey("active_session_launch_origin")
        val ACTIVE_SESSION_NETPLAY_GUEST = booleanPreferencesKey("active_session_netplay_guest")
        val ACTIVE_SESSION_RECOVERY_ATTEMPTS = intPreferencesKey("active_session_recovery_attempts")
        val ACTIVE_SESSION_RA_LAUNCH_SNAPSHOT = stringPreferencesKey("active_session_ra_launch_snapshot")
        val ACTIVE_SESSION_STREAM_PLATFORM = stringPreferencesKey("active_session_stream_platform")
        val ACTIVE_SESSION_STREAM_CONTAINER = stringPreferencesKey("active_session_stream_container")
        val ACTIVE_SESSION_STREAM_CLAIMED_AT = stringPreferencesKey("active_session_stream_claimed_at")
    }

    /**
     * The encoded RetroAchievements unlock snapshot taken at a session's launch. [clearActiveSession]
     * leaves it in place.
     */
    suspend fun setRaLaunchSnapshot(encoded: String?) {
        dataStore.edit { prefs ->
            if (encoded != null) prefs[Keys.ACTIVE_SESSION_RA_LAUNCH_SNAPSHOT] = encoded
            else prefs.remove(Keys.ACTIVE_SESSION_RA_LAUNCH_SNAPSHOT)
        }
    }

    suspend fun getRaLaunchSnapshot(): String? = dataStore.data.first()[Keys.ACTIVE_SESSION_RA_LAUNCH_SNAPSHOT]

    val activeSessionFlow: Flow<PersistedSession?> = dataStore.data.map { prefs ->
        prefs.toPersistedSession()
    }

    suspend fun persistActiveSession(
        gameId: Long,
        emulatorPackage: String,
        startTime: Instant,
        coreName: String?,
        isHardcore: Boolean,
        channelName: String? = null,
        variantFileId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        isNetplayGuest: Boolean = false,
        streamClaim: StreamClaim? = null
    ) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_SESSION_RECOVERY_ATTEMPTS)
            if (streamClaim != null) {
                prefs[Keys.ACTIVE_SESSION_STREAM_PLATFORM] = streamClaim.platform
                prefs[Keys.ACTIVE_SESSION_STREAM_CONTAINER] = streamClaim.containerKey
                prefs[Keys.ACTIVE_SESSION_STREAM_CLAIMED_AT] = streamClaim.claimedAt
            } else {
                prefs.removeStreamClaim()
            }
            prefs[Keys.ACTIVE_SESSION_NETPLAY_GUEST] = isNetplayGuest
            prefs[Keys.ACTIVE_SESSION_GAME_ID] = gameId.toString()
            prefs[Keys.ACTIVE_SESSION_EMULATOR] = emulatorPackage
            prefs[Keys.ACTIVE_SESSION_START_TIME] = startTime.toString()
            if (coreName != null) prefs[Keys.ACTIVE_SESSION_CORE_NAME] = coreName
            else prefs.remove(Keys.ACTIVE_SESSION_CORE_NAME)
            prefs[Keys.ACTIVE_SESSION_IS_HARDCORE] = isHardcore
            if (channelName != null) prefs[Keys.ACTIVE_SESSION_CHANNEL_NAME] = channelName
            else prefs.remove(Keys.ACTIVE_SESSION_CHANNEL_NAME)
            if (variantFileId != null) prefs[Keys.ACTIVE_SESSION_VARIANT_FILE_ID] = variantFileId.toString()
            else prefs.remove(Keys.ACTIVE_SESSION_VARIANT_FILE_ID)
            prefs[Keys.ACTIVE_SESSION_LAUNCH_ORIGIN] = origin.name
        }
    }

    /**
     * Clears the persisted session. With [startedAt], only the session that started at that
     * instant is cleared; a newer session written since is left in place.
     */
    suspend fun clearActiveSession(startedAt: Instant? = null) {
        dataStore.edit { prefs ->
            if (!prefs.isSessionStartedAt(startedAt)) return@edit
            prefs.remove(Keys.ACTIVE_SESSION_GAME_ID)
            prefs.remove(Keys.ACTIVE_SESSION_EMULATOR)
            prefs.remove(Keys.ACTIVE_SESSION_START_TIME)
            prefs.remove(Keys.ACTIVE_SESSION_CORE_NAME)
            prefs.remove(Keys.ACTIVE_SESSION_IS_HARDCORE)
            prefs.remove(Keys.ACTIVE_SESSION_CHANNEL_NAME)
            prefs.remove(Keys.ACTIVE_SESSION_VARIANT_FILE_ID)
            prefs.remove(Keys.ACTIVE_SESSION_LAUNCH_ORIGIN)
            prefs.remove(Keys.ACTIVE_SESSION_NETPLAY_GUEST)
            prefs.remove(Keys.ACTIVE_SESSION_RECOVERY_ATTEMPTS)
            prefs.removeStreamClaim()
        }
    }

    private fun MutablePreferences.removeStreamClaim() {
        remove(Keys.ACTIVE_SESSION_STREAM_PLATFORM)
        remove(Keys.ACTIVE_SESSION_STREAM_CONTAINER)
        remove(Keys.ACTIVE_SESSION_STREAM_CLAIMED_AT)
    }

    /**
     * Counts one failed save capture for the persisted session. Keeps the session for another
     * attempt and returns true until [maxAttempts] failures, then clears it and returns false.
     */
    suspend fun keepSessionForRetry(maxAttempts: Int, startedAt: Instant? = null): Boolean {
        var kept = true
        dataStore.edit { prefs ->
            if (!prefs.isSessionStartedAt(startedAt)) return@edit
            val attempts = (prefs[Keys.ACTIVE_SESSION_RECOVERY_ATTEMPTS] ?: 0) + 1
            kept = attempts < maxAttempts
            prefs[Keys.ACTIVE_SESSION_RECOVERY_ATTEMPTS] = attempts
        }
        if (!kept) clearActiveSession(startedAt)
        return kept
    }

    private fun Preferences.isSessionStartedAt(startedAt: Instant?): Boolean =
        startedAt == null || this[Keys.ACTIVE_SESSION_START_TIME] == startedAt.toString()

    suspend fun getPersistedSession(): PersistedSession? {
        return dataStore.data.first().toPersistedSession()
    }

    private fun Preferences.toPersistedSession(): PersistedSession? {
        val gameId = this[Keys.ACTIVE_SESSION_GAME_ID]?.toLongOrNull() ?: return null
        val emulator = this[Keys.ACTIVE_SESSION_EMULATOR] ?: return null
        val startTime = this[Keys.ACTIVE_SESSION_START_TIME]?.let {
            runCatching { Instant.parse(it) }.getOrNull()
        } ?: return null
        return PersistedSession(
            gameId = gameId,
            emulatorPackage = emulator,
            startTime = startTime,
            coreName = this[Keys.ACTIVE_SESSION_CORE_NAME],
            isHardcore = this[Keys.ACTIVE_SESSION_IS_HARDCORE] ?: false,
            channelName = this[Keys.ACTIVE_SESSION_CHANNEL_NAME],
            variantFileId = this[Keys.ACTIVE_SESSION_VARIANT_FILE_ID]?.toLongOrNull(),
            origin = LaunchOrigin.fromString(this[Keys.ACTIVE_SESSION_LAUNCH_ORIGIN]),
            isNetplayGuest = this[Keys.ACTIVE_SESSION_NETPLAY_GUEST] ?: false,
            streamClaim = toStreamClaim()
        )
    }

    private fun Preferences.toStreamClaim(): StreamClaim? {
        val platform = this[Keys.ACTIVE_SESSION_STREAM_PLATFORM] ?: return null
        val containerKey = this[Keys.ACTIVE_SESSION_STREAM_CONTAINER] ?: return null
        val claimedAt = this[Keys.ACTIVE_SESSION_STREAM_CLAIMED_AT] ?: return null
        return StreamClaim(platform, containerKey, claimedAt)
    }
}
