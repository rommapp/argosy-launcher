package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nendo.argosy.data.local.ALauncherDatabase
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

class DebugSeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeedEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
        fun romMRepository(): RomMRepository
        fun database(): ALauncherDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        val offlineDemo = intent.action == "com.nendo.argosy.DEBUG_SEED_OFFLINE_DEMO"
        val url = intent.getStringExtra("url")
        val token = intent.getStringExtra("token")
        if (!offlineDemo && (url == null || token == null)) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SeedEntryPoint::class.java
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (offlineDemo) {
                    val count = seedOfflineDemo(context, entryPoint.database())
                    pending.resultData = "Offline demo ready: $count entries"
                    Log.i("ArgosyDemoSeed", pending.resultData)
                } else {
                    entryPoint.romMRepository().connectWithToken(requireNotNull(url), requireNotNull(token))
                }
                entryPoint.userPreferencesRepository().setFirstRunComplete()
                pending.resultCode = 1
            } catch (error: Exception) {
                pending.resultCode = -1
                pending.resultData = if (offlineDemo) error.message else "Debug seed failed"
                Log.e("ArgosyDemoSeed", pending.resultData ?: "Debug seed failed")
            } finally {
                pending.finish()
            }
        }
    }
}
