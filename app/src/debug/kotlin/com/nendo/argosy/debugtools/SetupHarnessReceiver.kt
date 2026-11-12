package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only seam that removes the setup confirmation markers, leaving every confirmed value in
 * place, so setup reopens on an install that already has data. Logged under `SetupHarness`.
 *
 * `am broadcast -n com.nendo.argosy.debug/com.nendo.argosy.debugtools.SetupHarnessReceiver`
 */
class SetupHarnessReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        fun dataStore(): DataStore<Preferences>
    }

    override fun onReceive(context: Context, intent: Intent) {
        val dataStore = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HarnessEntryPoint::class.java
        ).dataStore()
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                dataStore.edit { prefs ->
                    MARKERS.forEach { prefs.remove(booleanPreferencesKey(it)) }
                    STRING_MARKERS.forEach { prefs.remove(stringPreferencesKey(it)) }
                }
                android.util.Log.i(TAG, "cleared ${(MARKERS + STRING_MARKERS).joinToString()}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SetupHarness"
        val MARKERS = listOf(
            "romm_library_confirmed",
            "rom_storage_path_confirmed",
            "save_sync_answered",
            "default_home_declined",
            "setup_flow_finished"
        )
        val STRING_MARKERS = listOf("setup_pages_seen")
    }
}
