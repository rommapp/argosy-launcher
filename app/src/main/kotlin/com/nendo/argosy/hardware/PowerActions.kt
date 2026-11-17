package com.nendo.argosy.hardware

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.background.RunningGames
import com.nendo.argosy.data.emulator.background.RunningGamesSnapshot
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootScriptRunner
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the Power page reaches the device: a root shell, Argosy's accessibility service, or
 * neither. A root shell wins when both are present.
 */
enum class PowerRoute {
    ROOT,
    ACCESSIBILITY,
    NONE;

    companion object {
        fun select(rootAvailable: Boolean, accessibilityBound: Boolean): PowerRoute = when {
            rootAvailable -> ROOT
            accessibilityBound -> ACCESSIBILITY
            else -> NONE
        }

        fun sleepAvailable(route: PowerRoute, sdkInt: Int): Boolean = when (route) {
            ROOT -> true
            ACCESSIBILITY -> sdkInt >= Build.VERSION_CODES.P
            NONE -> false
        }
    }
}

/**
 * Sleep, restart and power off for the quick panel's Power page. Restart and power off push the
 * pending cached saves first when a network is up and leave every open game running. Each action
 * posts its own failure notice and runs in this holder's scope, outliving the panel that asked.
 */
@Singleton
class PowerActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runningGames: RunningGames,
    private val gameRepository: GameRepository,
    private val appsRepository: AppsRepository,
    private val syncCoordinator: dagger.Lazy<SyncCoordinator>,
    private val permissionRegistry: PermissionRegistry,
    private val notificationManager: NotificationManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)

    val running: StateFlow<RunningGamesSnapshot> get() = runningGames.state

    suspend fun watchRunning() = runningGames.watch()

    suspend fun currentRoute(): PowerRoute = withContext(Dispatchers.IO) {
        PowerRoute.select(
            rootAvailable = RootShell.isAvailable,
            accessibilityBound = FocusAccessibilityService.instance != null
        )
    }

    suspend fun gameTitle(gameId: Long): String? = withContext(Dispatchers.IO) { gameRepository.getById(gameId)?.title }

    suspend fun appLabel(packageName: String): String? = withContext(Dispatchers.IO) {
        appsRepository.getAppLabel(packageName) ?: EmulatorRegistry.getByPackage(packageName)?.displayName
    }

    fun sleep(route: PowerRoute): Deferred<Boolean> = scope.async {
        val slept = when (route) {
            PowerRoute.ROOT -> RootShell.run(context, LidSleepGuard.SLEEP_SCRIPT)?.succeeded == true
            PowerRoute.ACCESSIBILITY -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                globalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            PowerRoute.NONE -> false
        }
        if (!slept) notifyFailure(R.string.ui_quick_settings_power_failed_sleep)
        slept
    }

    fun restart(): Deferred<Boolean> = scope.async {
        runAfterSavePush(R.string.ui_quick_settings_power_failed_restart) { RootScriptRunner.reboot() }
    }

    fun powerOff(): Deferred<Boolean> = scope.async {
        runAfterSavePush(R.string.ui_quick_settings_power_failed_off) { RootScriptRunner.shutdown() }
    }

    /**
     * Saves and closes every built-in game, then opens the system power menu through the
     * accessibility service.
     */
    fun openSystemPowerMenu(): Deferred<Boolean> = scope.async {
        val builtIn = runningGames.refresh()
            .filter { it.packageName == EmulatorRegistry.BUILTIN_PACKAGE }
            .map { it.taskId }
        if (!runningGames.close(builtIn)) Logger.warn(TAG, "a built-in game outlived its close before the power menu")
        val opened = globalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
        if (!opened) notifyFailure(R.string.ui_quick_settings_power_failed_menu)
        opened
    }

    fun openAccessibilitySettings() {
        val intent = permissionRegistry.accessibilityScreen() ?: return
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Logger.warn(TAG, "Accessibility settings did not open", e)
        }
    }

    private suspend fun runAfterSavePush(@StringRes failure: Int, command: () -> Boolean): Boolean {
        pushPendingSaves()
        val ran = withContext(Dispatchers.IO) { command() }
        if (!ran) notifyFailure(failure)
        return ran
    }

    private suspend fun pushPendingSaves() {
        if (!hasNetwork()) return
        try {
            val result = withTimeoutOrNull(SAVE_PUSH_TIMEOUT_MS) { syncCoordinator.get().processQueue() }
            Logger.info(TAG, "pending saves pushed before the power command: ${result ?: "timed out"}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "pending save push failed before the power command", e)
        }
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun globalAction(action: Int): Boolean = withContext(Dispatchers.Main) {
        FocusAccessibilityService.instance?.performGlobalAction(action) == true
    }

    private fun notifyFailure(@StringRes message: Int) {
        notificationManager.show(
            title = NotificationText.Res(message),
            type = NotificationType.ERROR,
            immediate = true
        )
    }

    private companion object {
        const val TAG = "PowerActions"
        const val SAVE_PUSH_TIMEOUT_MS = 60_000L
    }
}
