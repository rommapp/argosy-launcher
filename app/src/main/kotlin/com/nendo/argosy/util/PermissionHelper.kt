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
        val last = lastForegroundTimestamp(context, packageName) ?: run {
            android.util.Log.d("PermissionHelper", "isPackageInForeground($packageName): no usage data, assuming backgrounded")
            return false
        }
        val age = System.currentTimeMillis() - last
        val fresh = age <= withinMs
        android.util.Log.d(
            "PermissionHelper",
            "isPackageInForeground($packageName): lastTimeUsed ${age}ms ago, window=${withinMs}ms, fresh=$fresh"
        )
        return fresh
    }

    fun lastForegroundTimestamp(context: Context, packageName: String): Long? {
        if (!hasUsageStatsPermission(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 24 * 60 * 60 * 1000L,
            now
        ) ?: return null
        val target = stats.firstOrNull { it.packageName == packageName } ?: return null
        val last = target.lastTimeUsed
        return if (last <= 0) null else last
    }

    fun currentForegroundPackage(context: Context, lookbackMs: Long = 5 * 60 * 1000L): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - lookbackMs, now)
        val event = UsageEvents.Event()
        var pkg: String? = null
        var ts = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && event.timeStamp > ts) {
                ts = event.timeStamp
                pkg = event.packageName
            }
        }
        return pkg
    }

    data class SessionDurations(
        val foregroundMs: Long,
        val screenOnMs: Long
    ) {
        val hasData: Boolean get() = foregroundMs >= 0 && screenOnMs >= 0
    }

    fun getSessionDurations(context: Context, packageName: String, fromMs: Long, toMs: Long): SessionDurations {
        if (!hasUsageStatsPermission(context)) return SessionDurations(-1, -1)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()

        // Assume active at window start: the launcher just launched the emulator
        // with the screen on, so both are true at fromMs. If the first event is a
        // transition away, the interval [fromMs, event] is correctly captured.
        var foregroundMs = 0L
        var lastForegroundTime: Long? = fromMs
        var hasAppEvents = false

        var screenOnMs = 0L
        var lastScreenOnTime: Long? = fromMs
        var hasScreenEvents = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // Screen interactive/non-interactive (API 29+)
            when (event.eventType) {
                SCREEN_INTERACTIVE -> {
                    hasScreenEvents = true
                    if (lastScreenOnTime == null) {
                        lastScreenOnTime = event.timeStamp
                    }
                }
                SCREEN_NON_INTERACTIVE -> {
                    hasScreenEvents = true
                    if (lastScreenOnTime != null) {
                        screenOnMs += (event.timeStamp - lastScreenOnTime).coerceAtLeast(0)
                        lastScreenOnTime = null
                    }
                }
            }

            // App foreground/background
            if (event.packageName == packageName) {
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        hasAppEvents = true
                        if (lastForegroundTime == null) {
                            lastForegroundTime = event.timeStamp
                        }
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        hasAppEvents = true
                        if (lastForegroundTime != null) {
                            foregroundMs += (event.timeStamp - lastForegroundTime).coerceAtLeast(0)
                            lastForegroundTime = null
                        }
                    }
                }
            }
        }

        // Close open intervals at the end of the window
        if (lastForegroundTime != null) {
            foregroundMs += (toMs - lastForegroundTime).coerceAtLeast(0)
        }
        if (lastScreenOnTime != null) {
            screenOnMs += (toMs - lastScreenOnTime).coerceAtLeast(0)
        }

        // -1 signals "no data" so the caller can fall back to wall-clock time.
        // With the fromMs initialization, no app events means emulator was in
        // foreground the entire window (foregroundMs == totalMs). Return that
        // directly -- it is the correct value, not a missing-data case.
        // Only return -1 when we truly have no permission or no UsageStats access.
        val finalScreenOn = if (!hasScreenEvents) -1 else screenOnMs

        return SessionDurations(foregroundMs, finalScreenOn)
    }

    companion object {
        // UsageEvents.Event.SCREEN_INTERACTIVE / SCREEN_NON_INTERACTIVE (API 29+)
        private const val SCREEN_INTERACTIVE = 15
        private const val SCREEN_NON_INTERACTIVE = 16
    }
}
