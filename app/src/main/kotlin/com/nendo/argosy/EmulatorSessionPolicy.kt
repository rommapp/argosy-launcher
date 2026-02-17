package com.nendo.argosy

import android.content.Context
import android.content.Intent
import android.util.Log
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
                    " - clearing session"
            )
            runBlocking { preferencesRepository.clearActiveSession() }
            sessionStateStore.clearSession()
            return false
        }
        return true
    }

    fun shouldYieldOnResume(
        hasResumedBefore: Boolean,
        focusLostTime: Long
    ): Boolean {
        if (!hasResumedBefore) return false
        runBlocking {
            preferencesRepository.getPersistedSession()
        } ?: return false
        val timeSinceFocusLost =
            System.currentTimeMillis() - focusLostTime
        return focusLostTime > 0 && timeSinceFocusLost < 2000
    }

    fun clearStaleSession(
        context: Context,
        dualScreenManager: DualScreenManager
    ) {
        val session = runBlocking {
            preferencesRepository.getPersistedSession()
        } ?: return
        val emulatorInForeground = permissionHelper.isPackageInForeground(
            context, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(
                TAG,
                "Emulator ${session.emulatorPackage} not in foreground" +
                    " - clearing stale session"
            )
            runBlocking { preferencesRepository.clearActiveSession() }
            sessionStateStore.clearSession()
            dualScreenManager.broadcastSessionCleared()

            if (displayAffinityHelper.hasSecondaryDisplay) {
                RecoveryDisplayService.stop(context)
            }
        }
    }
}
