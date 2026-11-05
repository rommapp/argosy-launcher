package com.nendo.argosy.data.emulator.background

import android.app.Application
import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a game stays open in the background once the player leaves it: always for the built-in
 * emulator, and for an external emulator only on root, where Argosy can find, resume and stop it.
 * Whenever the player closes emulators on returning to Argosy, games are closed.
 */
@Singleton
class KeepAlivePolicy @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val emulatorResolver: EmulatorResolver,
    private val permissionHelper: PermissionHelper
) {
    val isSupported: Boolean get() = RootShell.isAvailable

    private suspend fun keepsGamesAlive(): Boolean =
        !preferencesRepository.preferences.first().closeEmulatorOnSessionEnd

    /**
     * Never true for Argosy itself or an emulator that has to be stopped before every launch.
     */
    suspend fun keepsAlive(emulatorPackage: String): Boolean {
        if (emulatorPackage == application.packageName) return false
        if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return keepsGamesAlive()
        val emulator = emulatorResolver.resolveEmulatorId(emulatorPackage)?.let { EmulatorRegistry.getById(it) }
        if (emulator?.launchConfig?.requiresEmulatorKill == true) return false
        return isSupported && keepsGamesAlive()
    }

    /**
     * [flags] as a fresh launch of [emulatorPackage] sends them: an emulator that stays alive drops
     * FLAG_ACTIVITY_NO_HISTORY, which would finish the game the moment the player leaves it.
     */
    suspend fun launchFlags(emulatorPackage: String, flags: Int): Int =
        if (keepsAlive(emulatorPackage)) flags and Intent.FLAG_ACTIVITY_NO_HISTORY.inv() else flags

    /**
     * Grants Argosy Usage Access through the root shell, so play time on root is measured the same
     * way as on a device where the player granted it.
     */
    fun grantUsageAccessOnRoot() {
        if (!RootShell.isAvailable || permissionHelper.hasUsageStatsPermission(application)) return
        RootShell.execute("appops set ${application.packageName} GET_USAGE_STATS allow")
            .onFailure { Logger.warn(TAG, "Usage Access grant through root failed", it) }
    }

    private companion object {
        const val TAG = "KeepAlivePolicy"
    }
}
