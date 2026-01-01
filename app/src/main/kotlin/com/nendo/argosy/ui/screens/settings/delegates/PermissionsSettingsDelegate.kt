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
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PermissionsSettingsDelegate @Inject constructor(
    private val application: Application,
    private val permissionHelper: PermissionHelper,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PermissionsState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        scope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _state.update { it.copy(trustUserCertificates = prefs.trustUserCertificates) }
            }
        }
    }

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

        _state.update {
            it.copy(
                hasStorageAccess = hasStorage,
                hasUsageStats = hasUsageStats,
                hasNotificationPermission = hasNotification
            )
        }
    }

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

    fun setTrustUserCertificates(enabled: Boolean) {
        scope.launch {
            userPreferencesRepository.setTrustUserCertificates(enabled)
        }
    }

    fun setTrustUserCertificatesAndRestart(enabled: Boolean) {
        _state.update { it.copy(showRestartDialog = false) }
        scope.launch {
            userPreferencesRepository.setTrustUserCertificates(enabled)
            restartApp()
        }
    }

    fun showRestartDialog() {
        _state.update { it.copy(showRestartDialog = true) }
    }

    fun dismissRestartDialog() {
        _state.update { it.copy(showRestartDialog = false) }
    }

    fun toggleTrustUserCertificates() {
        if (_state.value.trustUserCertificates) {
            setTrustUserCertificates(false)
        } else {
            showRestartDialog()
        }
    }

    fun restartApp() {
        val intent = application.packageManager.getLaunchIntentForPackage(application.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        application.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
