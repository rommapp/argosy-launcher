package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickMenuPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val TOP_NEW_NOT_INTERESTED = stringSetPreferencesKey("quick_menu_top_new_not_interested")
    }

    suspend fun getNotInterestedGameIds(): Set<Long> =
        dataStore.data.first()[Keys.TOP_NEW_NOT_INTERESTED].orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    suspend fun addNotInterested(gameIds: Set<Long>) {
        if (gameIds.isEmpty()) return
        dataStore.edit { prefs ->
            prefs[Keys.TOP_NEW_NOT_INTERESTED] =
                prefs[Keys.TOP_NEW_NOT_INTERESTED].orEmpty() + gameIds.map { it.toString() }
        }
    }
}
