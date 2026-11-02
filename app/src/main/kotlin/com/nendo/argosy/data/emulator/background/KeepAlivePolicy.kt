package com.nendo.argosy.data.emulator.background

import android.app.Application
import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether external emulators stay alive between sessions: on a device with a root shell, unless
 * the player closes emulators when a session ends.
 */
@Singleton
class KeepAlivePolicy @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val emulatorResolver: EmulatorResolver
) {
    val isSupported: Boolean get() = RootShell.isAvailable

    suspend fun keepsGamesAlive(): Boolean =
        isSupported && !preferencesRepository.preferences.first().closeEmulatorOnSessionEnd

    /**
     * Never true for the built-in emulator, Argosy itself, or an emulator that has to be stopped
     * before every launch.
     */
    suspend fun keepsAlive(emulatorPackage: String): Boolean {
        if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return false
        if (emulatorPackage == application.packageName) return false
        val emulator = emulatorResolver.resolveEmulatorId(emulatorPackage)?.let { EmulatorRegistry.getById(it) }
        if (emulator?.launchConfig?.requiresEmulatorKill == true) return false
        return keepsGamesAlive()
    }

    /**
     * [flags] as a fresh launch of [emulatorPackage] sends them: an emulator that stays alive drops
     * FLAG_ACTIVITY_NO_HISTORY, which would finish the game the moment the player leaves it.
     */
    suspend fun launchFlags(emulatorPackage: String, flags: Int): Int =
        if (keepsAlive(emulatorPackage)) flags and Intent.FLAG_ACTIVITY_NO_HISTORY.inv() else flags
}
