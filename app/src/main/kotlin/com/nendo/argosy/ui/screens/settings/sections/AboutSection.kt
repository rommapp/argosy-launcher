package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun AboutSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val updateCheck = uiState.updateCheck
    val isDebug = com.nendo.argosy.BuildConfig.DEBUG
    val isOnBetaVersion = com.nendo.argosy.BuildConfig.VERSION_NAME.contains("-")
    val context = LocalContext.current
    val hasLogLevel = uiState.fileLoggingPath != null
    val debugListEnd = if (hasLogLevel) 6 else 5
    val debugFocusEnd = if (hasLogLevel) 4 else 3

    val sections = listOf(
        ListSection(listStartIndex = 0, listEndIndex = 3, focusStartIndex = 0, focusEndIndex = 2),
        ListSection(listStartIndex = 4, listEndIndex = debugListEnd, focusStartIndex = 3, focusEndIndex = debugFocusEnd)
    )

    val focusToListIndex: (Int) -> Int = { focus ->
        if (focus <= 2) focus else focus + 2
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = focusToListIndex,
        sections = sections
    )

    LazyColumn(
        state = listState,
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
                isFocused = uiState.focusedIndex == 1,
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
                isFocused = uiState.focusedIndex == 2,
                onToggle = { viewModel.setBetaUpdatesEnabled(it) }
            )
        }
        item { Spacer(modifier = Modifier.height(Dimens.spacingMd)) }
        item { SectionHeader("DEBUG") }
        item(key = "fileLogging-${uiState.fileLoggingPath}") {
            if (uiState.fileLoggingPath != null) {
                SwitchPreference(
                    icon = Icons.Default.Description,
                    title = "File Logging",
                    subtitle = formatLoggingPath(uiState.fileLoggingPath),
                    isEnabled = uiState.fileLoggingEnabled,
                    isFocused = uiState.focusedIndex == 3,
                    onToggle = { viewModel.toggleFileLogging(it) },
                    onLabelClick = { viewModel.openLogFolderPicker() }
                )
            } else {
                ActionPreference(
                    icon = Icons.Default.Description,
                    title = "Enable File Logging",
                    subtitle = "Write logs to a file for debugging",
                    isFocused = uiState.focusedIndex == 3,
                    onClick = { viewModel.openLogFolderPicker() }
                )
            }
        }
        if (uiState.fileLoggingPath != null) {
            item(key = "logLevel") {
                CyclePreference(
                    title = "Log Level",
                    value = uiState.fileLogLevel.name,
                    isFocused = uiState.focusedIndex == 4,
                    onClick = { viewModel.cycleFileLogLevel() }
                )
            }
        }
    }
}

private fun formatLoggingPath(rawPath: String): String {
    return when {
        rawPath.startsWith("/storage/emulated/0") ->
            rawPath.replace("/storage/emulated/0", "Internal")
        rawPath.startsWith("/storage/") -> {
            val parts = rawPath.removePrefix("/storage/").split("/", limit = 2)
            if (parts.size == 2) "SD Card/${parts[1]}" else rawPath
        }
        else -> rawPath
    }
}
