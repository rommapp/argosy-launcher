package com.nendo.argosy.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.nendo.argosy.data.emulator.PresenceEvent
import com.nendo.argosy.data.emulator.PresenceEventKind
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

    fun lastForegroundTimestamp(context: Context, packageName: String): Long? =
        lastForegroundTimestamps(context, setOf(packageName))[packageName]

    /**
     * Last foreground time for each of [packageNames] that has any, from a single usage-stats
     * query. Returns an empty map without the permission, which callers must read as "no answer"
     * rather than "nothing was running".
     */
    fun lastForegroundTimestamps(
        context: Context,
        packageNames: Set<String>,
        lookbackMs: Long = 24 * 60 * 60 * 1000L
    ): Map<String, Long> {
        if (packageNames.isEmpty()) return emptyMap()
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - lookbackMs,
            now
        ) ?: return emptyMap()
        val result = HashMap<String, Long>()
        for (entry in stats) {
            if (entry.packageName !in packageNames) continue
            val last = entry.lastTimeUsed
            if (last <= 0) continue
            val previous = result[entry.packageName]
            if (previous == null || last > previous) result[entry.packageName] = last
        }
        return result
    }

    /**
     * Whether [packageName] still has an activity on a screen, focused or not, or null when the
     * question cannot be answered and the caller must not read the silence as "gone".
     *
     * A second display keeps its app resumed while the launcher holds focus on the other one, so
     * recency of use reports a perfectly live emulator as backgrounded the moment it stops being
     * the focused app. The lifecycle stream does not: ACTIVITY_STOPPED is the only event that means
     * no longer visible, and a paused-but-visible activity never reaches it. Each activity is
     * tracked separately because a package hands off between its own activities, and the one being
     * torn down can report STOPPED after its successor has already resumed.
     */
    fun isPackageOnScreen(context: Context, packageName: String): Boolean? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        return ON_SCREEN_LOOKBACKS_MS.firstNotNullOfOrNull { lookbackMs ->
            lastLifecycleOnScreen(usm, packageName, lookbackMs)
        }
    }

    private fun lastLifecycleOnScreen(usm: UsageStatsManager, packageName: String, lookbackMs: Long): Boolean? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - lookbackMs, now)
        val event = UsageEvents.Event()
        val lastByActivity = HashMap<String, Pair<Long, Int>>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            val type = event.eventType
            if (type != UsageEvents.Event.ACTIVITY_RESUMED &&
                type != UsageEvents.Event.ACTIVITY_PAUSED &&
                type != UsageEvents.Event.ACTIVITY_STOPPED
            ) continue
            val key = event.className ?: continue
            val previous = lastByActivity[key]
            if (previous == null || event.timeStamp >= previous.first) {
                lastByActivity[key] = event.timeStamp to type
            }
        }
        if (lastByActivity.isEmpty()) return null
        return lastByActivity.values.any { it.second != UsageEvents.Event.ACTIVITY_STOPPED }
    }

    /**
     * Whether [packageName] still holds a screen, falling back to how recently it was used when the
     * window-level answer is unavailable. Anything asking "is that game still up" wants both halves:
     * [isPackageOnScreen] is the accurate answer and frequently has none, while recency alone reports
     * a perfectly live app on a second display as gone. Without usage access neither half can answer
     * and the result is false, so a caller that must not act on silence checks the permission itself.
     */
    fun isPackageOnScreenOrRecent(
        context: Context,
        packageName: String,
        recencyMs: Long = RECENT_USE_WINDOW_MS
    ): Boolean {
        isPackageOnScreen(context, packageName)?.let { return it }
        return isPackageInForeground(context, packageName, recencyMs)
    }

    /**
     * Screen, keyguard and activity lifecycle events between [fromMs] and [toMs]. Empty without
     * usage access, which the caller must read as "no answer" rather than "nothing happened".
     */
    fun presenceEvents(context: Context, fromMs: Long, toMs: Long): List<PresenceEvent> {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return emptyList()
        if (!hasUsageStatsPermission(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()
        val result = ArrayList<PresenceEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> PresenceEventKind.SCREEN_INTERACTIVE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> PresenceEventKind.SCREEN_NON_INTERACTIVE
                UsageEvents.Event.KEYGUARD_HIDDEN -> PresenceEventKind.KEYGUARD_HIDDEN
                UsageEvents.Event.ACTIVITY_RESUMED -> PresenceEventKind.ACTIVITY_RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED -> PresenceEventKind.ACTIVITY_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> PresenceEventKind.ACTIVITY_STOPPED
                else -> continue
            }
            result.add(PresenceEvent(event.timeStamp, kind, event.packageName, event.className))
        }
        return result
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

    /**
     * The spans between [fromMs] and [toMs] when at least one activity of [packageName] was resumed,
     * counting only [activityClass] when it is given. Null without usage access.
     */
    fun foregroundSpans(
        context: Context,
        packageName: String,
        activityClass: String?,
        fromMs: Long,
        toMs: Long
    ): List<LongRange>? {
        if (!hasUsageStatsPermission(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(fromMs - SPAN_SEED_LOOKBACK_MS, toMs)
        val event = UsageEvents.Event()
        val transitions = ArrayList<ActivityTransition>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != packageName) continue
            val className = event.className ?: continue
            if (activityClass != null && className != activityClass) continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> transitions += ActivityTransition(event.timeStamp, className, true)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> transitions += ActivityTransition(event.timeStamp, className, false)
            }
        }
        return spansFromTransitions(transitions, fromMs, toMs)
    }

    data class ActivityTransition(val timestamp: Long, val className: String, val resumed: Boolean)

    fun getSessionDurations(context: Context, packageName: String, fromMs: Long, toMs: Long): SessionDurations {
        if (!hasUsageStatsPermission(context)) return SessionDurations(-1, -1)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()

        var foregroundMs = 0L
        var lastForegroundTime: Long? = fromMs
        var hasAppEvents = false

        var screenOnMs = 0L
        var lastScreenOnTime: Long? = fromMs
        var hasScreenEvents = false

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

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

        if (lastForegroundTime != null) {
            foregroundMs += (toMs - lastForegroundTime).coerceAtLeast(0)
        }
        if (lastScreenOnTime != null) {
            screenOnMs += (toMs - lastScreenOnTime).coerceAtLeast(0)
        }

        val finalScreenOn = if (!hasScreenEvents) -1 else screenOnMs

        return SessionDurations(foregroundMs, finalScreenOn)
    }

    companion object {
        const val RECENT_USE_WINDOW_MS = 15_000L
        private const val SCREEN_INTERACTIVE = 15
        private const val SCREEN_NON_INTERACTIVE = 16
        private val ON_SCREEN_LOOKBACKS_MS = listOf(10 * 60 * 1000L, 12 * 60 * 60 * 1000L)

        private const val SPAN_SEED_LOOKBACK_MS = 12 * 60 * 60 * 1000L

        /**
         * Spans within [fromMs]..[toMs] when at least one activity was resumed, from time-ordered
         * [transitions] that may start before [fromMs] so the resumed set is known at [fromMs].
         */
        fun spansFromTransitions(transitions: List<ActivityTransition>, fromMs: Long, toMs: Long): List<LongRange> {
            val resumed = HashSet<String>()
            val spans = ArrayList<LongRange>()
            var openedAt: Long? = null
            fun close(at: Long) {
                val start = openedAt ?: return
                openedAt = null
                val clippedStart = maxOf(start, fromMs)
                val clippedEnd = minOf(at, toMs)
                if (clippedStart <= clippedEnd) spans += clippedStart..clippedEnd
            }
            for (transition in transitions) {
                if (transition.resumed) {
                    if (resumed.isEmpty()) openedAt = transition.timestamp
                    resumed += transition.className
                } else if (resumed.remove(transition.className) && resumed.isEmpty()) {
                    close(transition.timestamp)
                }
            }
            close(toMs)
            return spans
        }
    }
}
