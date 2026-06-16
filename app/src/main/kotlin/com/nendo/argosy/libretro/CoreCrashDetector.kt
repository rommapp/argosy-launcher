package com.nendo.argosy.libretro

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.preferences.SessionPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CoreCrashInfo(
    val coreId: String,
    val gameId: Long,
    val reason: Int,
    val exitTimestamp: Long
)

@Singleton
class CoreCrashDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionPreferences: SessionPreferencesRepository
) {
    suspend fun detect(): CoreCrashInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val session = sessionPreferences.getPersistedSession() ?: return null
        if (session.emulatorPackage != EmulatorRegistry.BUILTIN_PACKAGE) return null
        val coreId = session.coreName ?: return null
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exits = runCatching {
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
        }.getOrNull().orEmpty()
        val crash = exits.firstOrNull { exit ->
            (exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                exit.reason == ApplicationExitInfo.REASON_SIGNALED) &&
                exit.timestamp >= session.startTime.toEpochMilli()
        } ?: return null
        return CoreCrashInfo(coreId, session.gameId, crash.reason, crash.timestamp)
    }
}
