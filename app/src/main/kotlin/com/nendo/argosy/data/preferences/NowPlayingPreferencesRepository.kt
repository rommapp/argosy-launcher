package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class NowPlayingStyle(val token: String) {
    RECORD("record"),
    CD("cd"),
    COVER("cover");

    val spins: Boolean get() = this != COVER

    companion object {
        fun fromToken(token: String?): NowPlayingStyle = entries.firstOrNull { it.token == token } ?: RECORD
    }
}

enum class NowPlayingLayout(val token: String) {
    SHELF("shelf"),
    SIMPLE("simple");

    companion object {
        fun fromToken(token: String?): NowPlayingLayout = entries.firstOrNull { it.token == token } ?: SHELF
    }
}

data class NowPlayingPreferences(
    val style: NowPlayingStyle = NowPlayingStyle.RECORD,
    val spin: Boolean = true,
    val layout: NowPlayingLayout = NowPlayingLayout.SHELF
)

@Singleton
class NowPlayingPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val STYLE = stringPreferencesKey("now_playing_style")
        val SPIN = booleanPreferencesKey("now_playing_spin")
        val LAYOUT = stringPreferencesKey("now_playing_layout")
    }

    val preferences: Flow<NowPlayingPreferences> = dataStore.data
        .map { prefs ->
            NowPlayingPreferences(
                style = NowPlayingStyle.fromToken(prefs[Keys.STYLE]),
                spin = prefs[Keys.SPIN] ?: true,
                layout = NowPlayingLayout.fromToken(prefs[Keys.LAYOUT])
            )
        }
        .distinctUntilChanged()

    suspend fun setStyle(style: NowPlayingStyle) {
        dataStore.edit { it[Keys.STYLE] = style.token }
    }

    suspend fun setSpin(spin: Boolean) {
        dataStore.edit { it[Keys.SPIN] = spin }
    }

    suspend fun setLayout(layout: NowPlayingLayout) {
        dataStore.edit { it[Keys.LAYOUT] = layout.token }
    }
}
