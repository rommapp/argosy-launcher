package com.nendo.argosy.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionHelper @Inject constructor() {

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun isPackageInForeground(context: Context, packageName: String, withinMs: Long = 5000): Boolean {
        if (!hasUsageStatsPermission(context)) {
            android.util.Log.d("PermissionHelper", "isPackageInForeground: no usage stats permission")
            return false
        }
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - withinMs

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastTransition: Int? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName) {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        lastTransition = event.eventType
                    }
                }
            }
        }

        val result = when (lastTransition) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> true
            UsageEvents.Event.MOVE_TO_BACKGROUND -> false
            else -> {
                // No transition events in window -- the app may have been in
                // foreground since before our window started (long gameplay).
                // Fall back: assume still in foreground if we have no evidence
                // of a background transition. The caller should use a large
                // enough window to capture the initial launch.
                android.util.Log.d("PermissionHelper", "isPackageInForeground($packageName): no transition events in ${withinMs}ms window, assuming foreground")
                true
            }
        }

        android.util.Log.d("PermissionHelper", "isPackageInForeground($packageName): lastTransition=$lastTransition, result=$result")
        return result
    }
}
