package com.nendo.argosy.ui.screens.settings.delegates

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PermissionsSettingsDelegate @Inject constructor(
    private val application: Application,
    private val permissionHelper: PermissionHelper,
    private val screenCaptureManager: ScreenCaptureManager,
    private val ledController: LEDController
) {
    private val _state = MutableStateFlow(PermissionsState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    private val fanSpeedFile = java.io.File("/sys/class/gpio5_pwm2/speed")

    fun refreshPermissions() {
        val hasStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        val hasUsageStats = permissionHelper.hasUsageStatsPermission(application)
        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val hasWriteSettings = Settings.System.canWrite(application)
        val isDeviceWithFanControl = fanSpeedFile.exists()
        val hasScreenCapture = screenCaptureManager.hasPermission.value
        val isLedAvailable = ledController.isAvailable
        val hasDisplayOverlay = Settings.canDrawOverlays(application)

        _state.update {
            it.copy(
                hasStorageAccess = hasStorage,
                hasUsageStats = hasUsageStats,
                hasNotificationPermission = hasNotification,
                hasWriteSettings = hasWriteSettings,
                isWriteSettingsRelevant = isDeviceWithFanControl,
                hasScreenCapture = hasScreenCapture,
                isScreenCaptureRelevant = isLedAvailable,
                hasDisplayOverlay = hasDisplayOverlay
            )
        }
    }

    fun observeScreenCapturePermission(scope: CoroutineScope) {
        scope.launch {
            screenCaptureManager.hasPermission.collect { hasPermission ->
                _state.update { it.copy(hasScreenCapture = hasPermission) }
            }
        }
    }

    fun hasScreenCapturePermission(): Boolean = screenCaptureManager.hasPermission.value

    fun isScreenCaptureRelevant(): Boolean = ledController.isAvailable

    fun openStorageSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
        }
    }

    fun openUsageStatsSettings() {
        permissionHelper.openUsageStatsSettings(application)
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, application.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        application.startActivity(intent)
    }

    fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        application.startActivity(intent)
    }

    fun openDisplayOverlaySettings() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${application.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        application.startActivity(intent)
    }

    fun hasWriteSettingsPermission(): Boolean = Settings.System.canWrite(application)

    fun isDeviceSettingsSupported(): Boolean = fanSpeedFile.exists()
}
