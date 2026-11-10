package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivilegedAccessPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val MAGISK_APPROVED = booleanPreferencesKey("performance_root_granted")
        val SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_access_enabled")
        val PROMPT_ANSWERED = booleanPreferencesKey("privileged_access_prompt_answered")
    }

    suspend fun isMagiskApproved(): Boolean = dataStore.data.first()[Keys.MAGISK_APPROVED] ?: false

    suspend fun setMagiskApproved(approved: Boolean) {
        dataStore.edit { it[Keys.MAGISK_APPROVED] = approved }
    }

    suspend fun isShizukuEnabled(): Boolean = dataStore.data.first()[Keys.SHIZUKU_ENABLED] ?: false

    suspend fun setShizukuEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SHIZUKU_ENABLED] = enabled }
    }

    suspend fun isPromptAnswered(): Boolean = dataStore.data.first()[Keys.PROMPT_ANSWERED] ?: false

    suspend fun setPromptAnswered() {
        dataStore.edit { it[Keys.PROMPT_ANSWERED] = true }
    }
}
