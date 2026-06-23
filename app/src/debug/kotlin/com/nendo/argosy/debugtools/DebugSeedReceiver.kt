package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Debug-only test seam: seeds RomM config and skips first-run. Trigger: adb shell am broadcast -n com.nendo.argosy.debug/com.nendo.argosy.debugtools.DebugSeedReceiver --es url <URL> --es token <RAW_TOKEN> */
class DebugSeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeedEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
        fun romMRepository(): RomMRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val token = intent.getStringExtra("token") ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SeedEntryPoint::class.java
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                entryPoint.romMRepository().connectWithToken(url, token)
                entryPoint.userPreferencesRepository().setFirstRunComplete()
            } finally {
                pending.finish()
            }
        }
    }
}
