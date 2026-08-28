package com.nendo.argosy.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns one preferences file per RomM account and hands out its [DataStore].
 *
 * A `preferencesDataStore` delegate is one instance per file and a second instance over the same
 * file throws, so every account store is created exactly once here and cached for the life of the
 * process. Files are named by RomM user id, which is the account key everywhere else.
 *
 * The first account to ask for a store inherits the per-account values that the pre-multi-account
 * device wrote into `settings`, so an upgrading install keeps its RA login, its sync watermarks
 * and its social link instead of coming up blank. That adoption happens once for one account; the
 * device-global store records which, and every later account starts empty.
 */
class AccountPreferenceStoreRegistry(
    private val context: Context,
    private val globalStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private val stores = mutableMapOf<Long, DataStore<Preferences>>()

    suspend fun storeFor(rommUserId: Long): DataStore<Preferences> = mutex.withLock {
        val cached = stores[rommUserId]
        if (cached != null) {
            cached
        } else {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = preferencesCorruptionHandler(),
                scope = scope
            ) {
                context.preferencesDataStoreFile(fileNameFor(rommUserId))
            }
            adoptLegacyValues(rommUserId, store)
            stores[rommUserId] = store
            store
        }
    }

    /**
     * Empties a removed account's store. The instance is kept so the file is never opened twice.
     */
    suspend fun clearFor(rommUserId: Long) {
        storeFor(rommUserId).edit { it.clear() }
    }

    private suspend fun adoptLegacyValues(rommUserId: Long, store: DataStore<Preferences>) {
        val global = globalStore.data.first()
        if (global[ADOPTED_BY] != null) return
        store.updateData { current ->
            val merged = current.toMutablePreferences()
            global.asMap().forEach { (key, value) ->
                if (AccountScopedPreferenceKeys.isPerAccount(key) && !merged.containsScoped(key)) {
                    merged.putScoped(key, value)
                }
            }
            merged.toPreferences()
        }
        globalStore.edit { it[ADOPTED_BY] = rommUserId }
    }

    private fun fileNameFor(rommUserId: Long) = "account_$rommUserId"

    companion object {
        private val ADOPTED_BY = longPreferencesKey("per_account_prefs_adopted_by")
    }
}
