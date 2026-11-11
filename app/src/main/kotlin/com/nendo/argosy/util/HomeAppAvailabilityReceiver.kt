package com.nendo.argosy.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeAppAvailabilityReceiver : BroadcastReceiver() {

    @Inject lateinit var homeAppAvailability: HomeAppAvailability

    @Inject lateinit var accessibilityRebind: com.nendo.argosy.hardware.AccessibilityRebind

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED && intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                homeAppAvailability.applyAtStartup()
                if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) accessibilityRebind.recoverAfterUpdate()
            } finally {
                pending.finish()
            }
        }
    }
}
