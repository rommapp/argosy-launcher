package com.nendo.argosy.data.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.nendo.argosy.data.access.PrivilegedAccess
import com.nendo.argosy.data.emulator.background.KeepAlivePolicy
import com.nendo.argosy.data.model.PrivilegedReach
import com.nendo.argosy.data.wallpaper.LockScreenScenes
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.hardware.FanController
import com.nendo.argosy.hardware.FocusAccessibilityService
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.util.NotificationAccess
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.StorageAccess
import com.nendo.argosy.util.SystemSettings
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PermissionGrant {
    data class Screen(val intent: Intent) : PermissionGrant
    data object StorageRequest : PermissionGrant
    data object NotificationRequest : PermissionGrant
    data object ScreenCaptureRequest : PermissionGrant
    data object RootGrant : PermissionGrant
    data object PrivilegedEnable : PermissionGrant
    data object None : PermissionGrant
}

/**
 * The live state of every [ArgosyPermission] and the action that grants it. Reads package
 * manager and system settings, and the first read may probe a root shell: call it off the main
 * thread.
 */
@Singleton
class PermissionRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionHelper: PermissionHelper,
    private val screenCaptureManager: ScreenCaptureManager,
    private val ledController: LEDController,
    private val fanController: FanController,
    private val systemSettings: SystemSettings,
    private val privilegedAccess: Lazy<PrivilegedAccess>,
    private val keepAlivePolicy: Lazy<KeepAlivePolicy>
) {
    private val packageUri: Uri get() = Uri.parse("package:${context.packageName}")

    fun snapshot(lockScreenArt: Boolean = true): PermissionSnapshot = PermissionSnapshot(
        availability = ArgosyPermission.entries.associateWith(::availabilityOf),
        relevant = ArgosyPermission.entries.filter { isRelevant(it, lockScreenArt) }.toSet()
    )

    fun availabilityOf(permission: ArgosyPermission): PermissionAvailability = when (permission) {
        ArgosyPermission.ALL_FILES_ACCESS ->
            PermissionAvailability.of(StorageAccess.isGranted(context), grantable = true)
        ArgosyPermission.USAGE_ACCESS -> PermissionAvailability.of(
            permissionHelper.hasUsageStatsPermission(context),
            grantable = usageScreen() != null || RootShell.isAvailable
        )
        ArgosyPermission.NOTIFICATIONS ->
            PermissionAvailability.of(NotificationAccess.isGranted(context), grantable = true)
        ArgosyPermission.DISPLAY_OVERLAY ->
            PermissionAvailability.of(Settings.canDrawOverlays(context), grantable = overlayScreen() != null)
        ArgosyPermission.ACCESSIBILITY -> PermissionAvailability.of(
            FocusAccessibilityService.isEnabled(context),
            grantable = accessibilityScreen() != null
        )
        ArgosyPermission.BATTERY_UNRESTRICTED -> PermissionAvailability.of(
            isIgnoringBatteryOptimizations(),
            grantable = batteryRequestScreen() != null || batterySettingsScreen() != null
        )
        ArgosyPermission.WRITE_SETTINGS -> PermissionAvailability.of(
            Settings.System.canWrite(context),
            grantable = writeSettingsScreen() != null
        )
        ArgosyPermission.SCREEN_CAPTURE ->
            PermissionAvailability.of(screenCaptureManager.hasPermission.value, grantable = true)
        ArgosyPermission.LIVE_WALLPAPER ->
            PermissionAvailability.of(LockScreenScenes.isLiveActive(context), grantable = true)
        ArgosyPermission.PRIVILEGED_ACCESS -> when (val reach = privilegedAccess.get().state.value.reach) {
            PrivilegedReach.CHECKING -> PermissionAvailability.CHECKING
            else -> PermissionAvailability.of(reach.isReady, grantable = true)
        }
    }

    fun isRelevant(permission: ArgosyPermission, lockScreenArt: Boolean = true): Boolean = when (permission) {
        ArgosyPermission.WRITE_SETTINGS ->
            fanController.isAvailable() || systemSettings.brightnessUsesWriteSettings
        ArgosyPermission.SCREEN_CAPTURE -> ledController.isAvailable
        ArgosyPermission.LIVE_WALLPAPER -> LockScreenScenes.canOffer(context, lockScreenArt)
        ArgosyPermission.PRIVILEGED_ACCESS -> privilegedAccess.get().state.value.route != null
        else -> true
    }

    /**
     * What acting on [permission] does in its [availability]: a missing permission opens the way
     * to grant it, a granted one opens the Android screen where it can be turned off.
     */
    fun grantFor(permission: ArgosyPermission, availability: PermissionAvailability): PermissionGrant {
        val granted = availability == PermissionAvailability.GRANTED
        return when (permission) {
            ArgosyPermission.ALL_FILES_ACCESS -> PermissionGrant.StorageRequest
            ArgosyPermission.USAGE_ACCESS -> usageScreen()?.let(PermissionGrant::Screen)
                ?: if (!granted && RootShell.isAvailable) PermissionGrant.RootGrant else PermissionGrant.None
            ArgosyPermission.NOTIFICATIONS ->
                if (!granted && NotificationAccess.needsRuntimePrompt) {
                    PermissionGrant.NotificationRequest
                } else {
                    screenOrNone(appNotificationScreen())
                }
            ArgosyPermission.DISPLAY_OVERLAY -> screenOrNone(overlayScreen())
            ArgosyPermission.ACCESSIBILITY -> screenOrNone(accessibilityScreen())
            ArgosyPermission.BATTERY_UNRESTRICTED -> screenOrNone(
                if (granted) batterySettingsScreen() else batteryRequestScreen() ?: batterySettingsScreen()
            )
            ArgosyPermission.WRITE_SETTINGS -> screenOrNone(writeSettingsScreen())
            ArgosyPermission.SCREEN_CAPTURE -> PermissionGrant.ScreenCaptureRequest
            ArgosyPermission.LIVE_WALLPAPER -> PermissionGrant.Screen(LockScreenScenes.pickerIntent(context))
            ArgosyPermission.PRIVILEGED_ACCESS -> PermissionGrant.PrivilegedEnable
        }
    }

    /**
     * Grants [permission] through the root shell where Argosy has a root command for it. Blocks on
     * the shell.
     */
    fun grantOnRoot(permission: ArgosyPermission) {
        if (permission == ArgosyPermission.USAGE_ACCESS) keepAlivePolicy.get().grantUsageAccessOnRoot()
    }

    fun accessibilityScreen(): Intent? = resolving(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun screenOrNone(intent: Intent?): PermissionGrant =
        intent?.let(PermissionGrant::Screen) ?: PermissionGrant.None

    private fun usageScreen(): Intent? =
        resolving(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri))
            ?: Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .takeIf { permissionHelper.canRequestUsageStats(context) }

    private fun appNotificationScreen(): Intent? = resolving(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )

    private fun overlayScreen(): Intent? =
        resolving(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
            ?: resolving(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))

    private fun batteryRequestScreen(): Intent? =
        resolving(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))

    private fun batterySettingsScreen(): Intent? =
        resolving(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun writeSettingsScreen(): Intent? =
        resolving(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, packageUri))

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun resolving(intent: Intent): Intent? =
        intent.takeIf { it.resolveActivity(context.packageManager) != null }
}
