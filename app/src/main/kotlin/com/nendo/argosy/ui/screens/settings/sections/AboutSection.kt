package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun AboutSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val updateCheck = uiState.updateCheck
    val isDebug = com.nendo.argosy.BuildConfig.DEBUG
    val isOnBetaVersion = com.nendo.argosy.BuildConfig.VERSION_NAME.contains("-")
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            InfoPreference(
                title = "Version",
                value = uiState.appVersion,
                isFocused = uiState.focusedIndex == 0
            )
        }
        item {
            InfoPreference(
                title = "Installed Emulators",
                value = "${uiState.emulators.installedEmulators.size} detected",
                isFocused = uiState.focusedIndex == 1
            )
        }
        item {
            InfoPreference(
                title = "Argosy",
                value = "Emulation-focused launcher for Android handhelds",
                isFocused = uiState.focusedIndex == 2
            )
        }
        item {
            val (title, subtitle) = when {
                isDebug -> "Check for Updates" to "Disabled in debug builds"
                updateCheck.isDownloading -> "Downloading..." to "${updateCheck.downloadProgress}%"
                updateCheck.isChecking -> "Check for Updates" to "Checking..."
                updateCheck.error != null -> "Check for Updates" to "Error: ${updateCheck.error}"
                updateCheck.updateAvailable -> "Install Update" to "Tap to download ${updateCheck.latestVersion}"
                updateCheck.hasChecked && isOnBetaVersion -> "Check for Updates" to "Up to date (pre-release)"
                updateCheck.hasChecked -> "Check for Updates" to "Up to date"
                isOnBetaVersion -> "Check for Updates" to "Running pre-release build"
                else -> "Check for Updates" to "Check for new versions"
            }
            ActionPreference(
                icon = Icons.Default.Sync,
                title = title,
                subtitle = subtitle,
                isFocused = uiState.focusedIndex == 3,
                isEnabled = !isDebug && !updateCheck.isChecking && !updateCheck.isDownloading,
                onClick = {
                    if (updateCheck.updateAvailable) {
                        viewModel.downloadAndInstallUpdate(context)
                    } else {
                        viewModel.checkForUpdates()
                    }
                }
            )
        }
        item {
            SwitchPreference(
                title = "Beta Updates",
                subtitle = if (uiState.betaUpdatesEnabled)
                    "Receiving pre-release builds"
                else
                    "Stable releases only",
                isEnabled = uiState.betaUpdatesEnabled,
                isFocused = uiState.focusedIndex == 4,
                onToggle = { viewModel.setBetaUpdatesEnabled(it) }
            )
        }
    }
}
