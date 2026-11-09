package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PerformancePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PERFORMANCE_MODE = stringPreferencesKey("performance_saved_mode")
        val PERFORMANCE_ROOT_GRANTED = booleanPreferencesKey("performance_root_granted")
    }

    suspend fun getPerformanceMode(): String? = dataStore.data.first()[Keys.PERFORMANCE_MODE]

    suspend fun setPerformanceMode(token: String) {
        dataStore.edit { it[Keys.PERFORMANCE_MODE] = token }
    }

    suspend fun isRootGranted(): Boolean = dataStore.data.first()[Keys.PERFORMANCE_ROOT_GRANTED] ?: false

    suspend fun setRootGranted(granted: Boolean) {
        dataStore.edit { it[Keys.PERFORMANCE_ROOT_GRANTED] = granted }
    }
}
