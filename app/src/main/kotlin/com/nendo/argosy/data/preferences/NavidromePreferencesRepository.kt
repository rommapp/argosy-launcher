package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class NavidromePreferences(
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val serverVersion: String? = null
) {
    val isSignedIn: Boolean
        get() = !serverUrl.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()

    override fun toString(): String =
        "NavidromePreferences(serverUrl=$serverUrl, username=$username, serverVersion=$serverVersion)"
}

@Singleton
class NavidromePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SERVER_URL = stringPreferencesKey("navidrome_server_url")
        val USERNAME = stringPreferencesKey("navidrome_username")
        val PASSWORD = stringPreferencesKey("navidrome_password")
        val SERVER_VERSION = stringPreferencesKey("navidrome_server_version")
    }

    val preferences: Flow<NavidromePreferences> = dataStore.data.map { prefs ->
        NavidromePreferences(
            serverUrl = prefs[Keys.SERVER_URL],
            username = prefs[Keys.USERNAME],
            password = prefs[Keys.PASSWORD],
            serverVersion = prefs[Keys.SERVER_VERSION]
        )
    }

    suspend fun setServerUrl(url: String?) {
        dataStore.edit { prefs ->
            val trimmed = url?.trim()?.trimEnd('/')
            if (trimmed.isNullOrBlank()) prefs.remove(Keys.SERVER_URL)
            else prefs[Keys.SERVER_URL] = trimmed
        }
    }

    suspend fun setCredentials(username: String, password: String, serverVersion: String?) {
        dataStore.edit { prefs ->
            prefs[Keys.USERNAME] = username
            prefs[Keys.PASSWORD] = password
            if (serverVersion.isNullOrBlank()) prefs.remove(Keys.SERVER_VERSION)
            else prefs[Keys.SERVER_VERSION] = serverVersion
        }
    }

    suspend fun clearCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.USERNAME)
            prefs.remove(Keys.PASSWORD)
            prefs.remove(Keys.SERVER_VERSION)
        }
    }
}
