package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single [DataStore] every preferences repository injects, presenting one merged view over
 * the device-global `settings` file and the active account's own file.
 *
 * Swapping the injected instance on a switch is not possible: every repository captures
 * `dataStore.data` in a `val` at construction, so a replaced instance would leave every existing
 * flow serving the account that was live at startup. Instead the instance is permanent and [data]
 * re-derives itself from the active account, which makes those construction-time captures
 * re-emit the incoming account's values with no repository change at all.
 *
 * Writes are split by [AccountScopedPreferenceKeys]: the device-global part lands in `settings`
 * and the per-account part in the account file. With no account paired both parts go to
 * `settings`, which is exactly the pre-account behaviour, so first run and the setup wizard read
 * and write the same file they always did.
 */
class AccountScopedDataStore(
    private val globalStore: DataStore<Preferences>,
    private val registry: AccountPreferenceStoreRegistry
) : DataStore<Preferences> {

    private val writeMutex = Mutex()

    private val activeAccount: Flow<Long?> = globalStore.data
        .map { it[ACTIVE_ACCOUNT] }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val data: Flow<Preferences> = activeAccount.flatMapLatest { rommUserId ->
        if (rommUserId == null) {
            globalStore.data
        } else {
            flow {
                val accountStore = registry.storeFor(rommUserId)
                emitAll(
                    combine(globalStore.data, accountStore.data) { global, account ->
                        merge(global, account)
                    }
                )
            }
        }
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = writeMutex.withLock {
        val rommUserId = globalStore.data.first()[ACTIVE_ACCOUNT]
        val accountStore = rommUserId?.let { registry.storeFor(it) }
        val before = if (accountStore == null) {
            globalStore.data.first()
        } else {
            merge(globalStore.data.first(), accountStore.data.first())
        }
        val after = transform(before)
        if (accountStore == null) {
            globalStore.updateData { current -> applyInto(current, after) { true } }
        } else {
            globalStore.updateData { current ->
                applyInto(current, after) { !AccountScopedPreferenceKeys.isPerAccount(it) }
            }
            accountStore.updateData { current ->
                applyInto(current, after) { AccountScopedPreferenceKeys.isPerAccount(it) }
            }
        }
        after
    }

    private fun merge(global: Preferences, account: Preferences): Preferences {
        val merged = global.toMutablePreferences()
        global.asMap().keys
            .filter { AccountScopedPreferenceKeys.isPerAccount(it) }
            .forEach { merged.removeScoped(it) }
        account.asMap().forEach { (key, value) ->
            if (AccountScopedPreferenceKeys.isPerAccount(key)) merged.putScoped(key, value)
        }
        return merged.toPreferences()
    }

    private fun applyInto(
        current: Preferences,
        desired: Preferences,
        owns: (Preferences.Key<*>) -> Boolean
    ): Preferences {
        val next = current.toMutablePreferences()
        current.asMap().keys.filter(owns).forEach { next.removeScoped(it) }
        desired.asMap().forEach { (key, value) -> if (owns(key)) next.putScoped(key, value) }
        return next.toPreferences()
    }

    companion object {
        private val ACTIVE_ACCOUNT = longPreferencesKey("romm_user_id")
    }
}

@Suppress("UNCHECKED_CAST")
internal fun MutablePreferences.putScoped(key: Preferences.Key<*>, value: Any) {
    this[key as Preferences.Key<Any>] = value
}

@Suppress("UNCHECKED_CAST")
internal fun MutablePreferences.removeScoped(key: Preferences.Key<*>) {
    remove(key as Preferences.Key<Any>)
}

@Suppress("UNCHECKED_CAST")
internal fun Preferences.containsScoped(key: Preferences.Key<*>): Boolean =
    contains(key as Preferences.Key<Any>)
