package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.dialogs.LicensesDialog
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

private data class AboutLayoutState(val hasLogPath: Boolean)

private sealed class AboutItem(
    val key: String,
    val section: String,
    val visibleWhen: (AboutLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, VersionInfo, SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String) : AboutItem(key, section)
    data object VersionInfo : AboutItem("versionInfo", "version")
    data object CheckUpdates : AboutItem("checkUpdates", "version")
    data object BetaUpdates : AboutItem("betaUpdates", "version")
    data object SectionSpacer : AboutItem("spacer", "debug")
    data object FileLogging : AboutItem("fileLogging", "debug")
    data object LogLevel : AboutItem(
        key = "logLevel",
        section = "debug",
        visibleWhen = { it.hasLogPath }
    )
    data object SaveDebugLogging : AboutItem(
        key = "saveDebugLogging",
        section = "debug",
        visibleWhen = { it.hasLogPath }
    )
    data object AppAffinity : AboutItem("appAffinity", "debug")

    companion object {
        private val VersionHeader = Header("versionHeader", "version", "VERSION")
        private val DebugHeader = Header("debugHeader", "debug", "DEBUG")

        val ALL: List<AboutItem> = listOf(
            VersionHeader, VersionInfo, CheckUpdates, BetaUpdates,
            SectionSpacer, DebugHeader, FileLogging, LogLevel, SaveDebugLogging, AppAffinity
        )
    }
}

private val aboutLayout = SettingsLayout<AboutItem, AboutLayoutState>(
    allItems = AboutItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun aboutMaxFocusIndex(hasLogPath: Boolean): Int =
    aboutLayout.maxFocusIndex(AboutLayoutState(hasLogPath))

@Composable
fun AboutSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val updateCheck = uiState.updateCheck
    val isDebug = com.nendo.argosy.BuildConfig.DEBUG
    val isOnBetaVersion = com.nendo.argosy.BuildConfig.VERSION_NAME.contains("-")
    val context = LocalContext.current
    val hasLogPath = uiState.fileLoggingPath != null
    var showLicensesDialog by remember { mutableStateOf(false) }

    val layoutState = remember(hasLogPath) { AboutLayoutState(hasLogPath) }
    val visibleItems = remember(hasLogPath) { aboutLayout.visibleItems(layoutState) }
    val sections = remember(hasLogPath) { aboutLayout.buildSections(layoutState) }

    fun isFocused(item: AboutItem): Boolean =
        uiState.focusedIndex == aboutLayout.focusIndexOf(item, layoutState)

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { aboutLayout.focusToListIndex(it, layoutState) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is AboutItem.Header -> SectionHeader(item.title)

                AboutItem.VersionInfo -> VersionInfoRow(
                    argosyVersion = uiState.appVersion,
                    rommVersion = uiState.server.rommVersion,
                    onLicensesClick = { showLicensesDialog = true }
                )

                AboutItem.CheckUpdates -> {
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
                        isFocused = isFocused(item),
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

                AboutItem.BetaUpdates -> SwitchPreference(
                    title = "Beta Updates",
                    subtitle = if (uiState.betaUpdatesEnabled)
                        "Receiving pre-release builds"
                    else
                        "Stable releases only",
                    isEnabled = uiState.betaUpdatesEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBetaUpdatesEnabled(it) }
                )

                AboutItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                AboutItem.FileLogging -> {
                    if (uiState.fileLoggingPath != null) {
                        SwitchPreference(
                            icon = Icons.Default.Description,
                            title = "File Logging",
                            subtitle = formatLoggingPath(uiState.fileLoggingPath),
                            isEnabled = uiState.fileLoggingEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.toggleFileLogging(it) },
                            onLabelClick = { viewModel.openLogFolderPicker() }
                        )
                    } else {
                        ActionPreference(
                            icon = Icons.Default.Description,
                            title = "Enable File Logging",
                            subtitle = "Write logs to a file for debugging",
                            isFocused = isFocused(item),
                            onClick = { viewModel.openLogFolderPicker() }
                        )
                    }
                }

                AboutItem.LogLevel -> CyclePreference(
                    title = "Log Level",
                    value = uiState.fileLogLevel.name,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleFileLogLevel() }
                )

                AboutItem.SaveDebugLogging -> SwitchPreference(
                    title = "Save Debug Logging",
                    subtitle = if (uiState.saveDebugLoggingEnabled)
                        "Detailed save operations logged"
                    else
                        "Log save sync, cache, and channel events",
                    isEnabled = uiState.saveDebugLoggingEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSaveDebugLoggingEnabled(it) }
                )

                AboutItem.AppAffinity -> SwitchPreference(
                    title = "App Display Affinity",
                    subtitle = if (uiState.appAffinityEnabled)
                        "Emulators primary, apps secondary"
                    else
                        "Default display behavior",
                    isEnabled = uiState.appAffinityEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setAppAffinityEnabled(it) }
                )
            }
        }
    }
}

@Composable
private fun VersionInfoRow(
    argosyVersion: String,
    rommVersion: String?,
    onLicensesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
        ) {
            Column {
                Text(
                    text = "Argosy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = argosyVersion,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (rommVersion != null) {
                Column {
                    Text(
                        text = "RomM API",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "v$rommVersion",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable(onClick = onLicensesClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Licenses",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Outlined.Article,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
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
