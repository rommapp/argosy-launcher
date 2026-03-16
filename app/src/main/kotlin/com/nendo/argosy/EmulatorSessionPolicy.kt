package com.nendo.argosy

import android.content.Context
import android.content.Intent
import android.util.Log
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.RecoveryDisplayService
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.runBlocking

private const val TAG = "EmulatorSessionPolicy"

class EmulatorSessionPolicy(
    private val preferencesRepository: UserPreferencesRepository,
    private val permissionHelper: PermissionHelper,
    private val sessionStateStore: SessionStateStore,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val playSessionTracker: PlaySessionTracker,
) {

    fun shouldYieldToEmulator(
        context: Context,
        intent: Intent
    ): Boolean {
        if (intent.data != null ||
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        ) {
            return false
        }
        if (sessionStateStore.isRolesSwapped()) return false
        val session = runBlocking {
            preferencesRepository.getPersistedSession()
        } ?: return false

        val emulatorInForeground = permissionHelper.isPackageInForeground(
            context, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(
                TAG,
                "Emulator ${session.emulatorPackage} not in foreground" +
                    " - ending session"
            )
            playSessionTracker.endSessionInBackground()
            return false
        }
        return true
    }

    fun shouldYieldOnResume(
        context: Context,
        hasResumedBefore: Boolean,
        focusLostTime: Long,
        appPackage: String
    ): Boolean {
        if (!hasResumedBefore) return false
        if (sessionStateStore.isRolesSwapped()) return false
        val session = runBlocking {
            preferencesRepository.getPersistedSession()
        } ?: return false
        if (session.emulatorPackage == appPackage) return false
        val timeSinceFocusLost =
            System.currentTimeMillis() - focusLostTime
        if (focusLostTime <= 0 || timeSinceFocusLost >= 2000) return false
        return permissionHelper.isPackageInForeground(
            context, session.emulatorPackage, withinMs = 5_000
        )
    }

    fun clearStaleSession(
        context: Context,
        dualScreenManager: DualScreenManager
    ) {
        if (dualScreenManager.isLaunchingGame) return
        if (dualScreenManager.emulatorDisplayId != null) {
            val emulatorPkg = sessionStateStore.getEmulatorPackage() ?: return
            if (permissionHelper.isPackageInForeground(
                    context, emulatorPkg, withinMs = 15_000
                )
            ) return
        }

        runBlocking {
            preferencesRepository.getPersistedSession()
        } ?: return

        Log.d(TAG, "Ending stale session on resume")
        dualScreenManager.emulatorDisplayId = null
        playSessionTracker.endSessionInBackground()
        dualScreenManager.broadcastSessionCleared()

        if (displayAffinityHelper.hasSecondaryDisplay) {
            RecoveryDisplayService.stop(context)
        }
    }
}
