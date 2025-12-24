package com.nendo.argosy.util

import android.app.AppOpsManager
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
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        val match = stats.find { it.packageName == packageName }
        val result = match != null && match.lastTimeUsed >= startTime
        android.util.Log.d("PermissionHelper", "isPackageInForeground($packageName): found=${match != null}, lastUsed=${match?.lastTimeUsed}, startTime=$startTime, result=$result")
        return result
    }
}
