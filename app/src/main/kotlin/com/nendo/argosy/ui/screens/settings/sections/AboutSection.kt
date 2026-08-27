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
import androidx.compose.foundation.layout.width
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.data.preferences.SettingsBackupRepository
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.UpdateCheckState
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.dialogs.LicensesDialog
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.LogLevel

internal data class AboutLayoutState(val hasLogPath: Boolean, val hasChangelog: Boolean)

internal sealed class AboutItem(
    val key: String,
    val section: String,
    val visibleWhen: (AboutLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, VersionInfo, SectionSpacer, SystemSpacer, BackupSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int) : AboutItem(key, section)
    data object VersionInfo : AboutItem("versionInfo", "version")
    data object CheckUpdates : AboutItem("checkUpdates", "version")
    data object ChangelogPreview : AboutItem(
        key = "changelogPreview",
        section = "version",
        visibleWhen = { it.hasChangelog }
    )
    data object BetaUpdates : AboutItem("betaUpdates", "version")
    data object BackupSpacer : AboutItem("backupSpacer", "backup")
    data object ExportSettings : AboutItem("exportSettings", "backup")
    data object ImportSettings : AboutItem("importSettings", "backup")
    data object SystemSpacer : AboutItem("systemSpacer", "system")
    data object SystemizeHelper : AboutItem("systemizeHelper", "system")
    data object RestartApp : AboutItem("restartApp", "system")
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
        private val VersionHeader = Header("versionHeader", "version", R.string.settings_about_section_version)
        private val BackupHeader = Header("backupHeader", "backup", R.string.settings_about_section_backup)
        private val SystemHeader = Header("systemHeader", "system", R.string.settings_about_section_system_app)
        private val DebugHeader = Header("debugHeader", "debug", R.string.settings_about_section_debug)

        val ALL: List<AboutItem>
            get() = listOf(
                VersionHeader, VersionInfo, CheckUpdates, ChangelogPreview, BetaUpdates,
                BackupSpacer, BackupHeader, ExportSettings, ImportSettings,
                SystemSpacer, SystemHeader, SystemizeHelper, RestartApp,
                SectionSpacer, DebugHeader, FileLogging, LogLevel, SaveDebugLogging
            )
    }
}

private val aboutLayout = SettingsLayout<AboutItem, AboutLayoutState>(
    allItems = AboutItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "version" -> R.string.settings_about_section_version
            "backup" -> R.string.settings_about_section_backup
            "system" -> R.string.settings_about_section_system_app
            "debug" -> R.string.settings_about_section_debug
            else -> null
        }
    }
)

internal fun aboutSections(hasLogPath: Boolean, hasChangelog: Boolean) =
    aboutLayout.buildSections(AboutLayoutState(hasLogPath, hasChangelog))

internal fun aboutMaxFocusIndex(hasLogPath: Boolean, hasChangelog: Boolean): Int =
    aboutLayout.maxFocusIndex(AboutLayoutState(hasLogPath, hasChangelog))

internal fun aboutItemAtFocusIndex(focusIndex: Int, hasLogPath: Boolean, hasChangelog: Boolean): AboutItem? =
    aboutLayout.itemAtFocusIndex(focusIndex, AboutLayoutState(hasLogPath, hasChangelog))

internal fun aboutHasChangelog(updateCheck: UpdateCheckState): Boolean =
    updateCheck.updateAvailable && updateCheck.latestBody != null

@Composable
fun AboutSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val updateCheck = uiState.updateCheck
    val isDebug = com.nendo.argosy.BuildConfig.DEBUG
    val isOnBetaVersion = com.nendo.argosy.BuildConfig.VERSION_NAME.contains("-")
    val context = LocalContext.current
    val hasLogPath = uiState.fileLoggingPath != null
    val hasChangelog = aboutHasChangelog(updateCheck)
    var showLicensesDialog by remember { mutableStateOf(false) }

    val layoutState = remember(hasLogPath, hasChangelog) { AboutLayoutState(hasLogPath, hasChangelog) }
    val visibleItems = remember(hasLogPath, hasChangelog) { aboutLayout.visibleItems(layoutState) }
    val sections = remember(hasLogPath, hasChangelog, context) {
        aboutLayout.buildSections(layoutState, context)
    }

    LaunchedEffect(Unit) {
        if (!isDebug && !updateCheck.isChecking && !updateCheck.isDownloading && !updateCheck.readyToInstall) {
            viewModel.checkForUpdates()
        }
    }

    fun isFocused(item: AboutItem): Boolean =
        uiState.focusedIndex == aboutLayout.focusIndexOf(item, layoutState)

    fun pickerToken(item: AboutItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }


    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { aboutLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it == AboutItem.SectionSpacer },
        isHeader = { it is AboutItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is AboutItem.Header -> SectionHeader(stringResource(item.titleRes))

                AboutItem.VersionInfo -> VersionInfoRow(
                    argosyVersion = uiState.appVersion,
                    rommVersion = uiState.server.rommVersion,
                    onLicensesClick = { showLicensesDialog = true }
                )

                AboutItem.CheckUpdates -> {
                    val checkTitle = stringResource(R.string.settings_about_update_title_check)
                    val (title, subtitle) = when {
                        isDebug -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_debug)
                        updateCheck.isDownloading ->
                            stringResource(R.string.settings_about_update_title_downloading) to
                                stringResource(
                                    R.string.settings_about_update_subtitle_progress,
                                    updateCheck.downloadProgress
                                )
                        updateCheck.isChecking -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_checking)
                        updateCheck.error != null -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_error, updateCheck.error)
                        updateCheck.updateAvailable ->
                            stringResource(R.string.settings_about_update_title_available) to
                                stringResource(
                                    R.string.settings_about_update_subtitle_available,
                                    updateCheck.latestVersion.orEmpty()
                                )
                        updateCheck.hasChecked && isOnBetaVersion -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_current_prerelease)
                        updateCheck.hasChecked -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_current)
                        isOnBetaVersion -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_on_prerelease)
                        else -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_idle)
                    }
                    UpdateActionsRow(
                        title = title,
                        subtitle = subtitle,
                        primaryLabel = if (updateCheck.updateAvailable) {
                            stringResource(R.string.settings_about_update_action_download)
                        } else {
                            stringResource(R.string.settings_about_update_action_check)
                        },
                        primaryEnabled = !isDebug && !updateCheck.isChecking && !updateCheck.isDownloading,
                        isFocused = isFocused(item),
                        actionIndex = uiState.aboutUpdateActionIndex,
                        onPrimary = {
                            if (updateCheck.updateAvailable) {
                                viewModel.downloadAndInstallUpdate(context)
                            } else {
                                viewModel.checkForUpdates()
                            }
                        },
                        onChangelog = { viewModel.openChangelog() }
                    )
                }

                AboutItem.ChangelogPreview -> ChangelogPreviewRow(
                    version = updateCheck.latestVersion.orEmpty(),
                    body = updateCheck.latestBody.orEmpty(),
                    isFocused = isFocused(item),
                    onClick = { viewModel.openChangelog() }
                )

                AboutItem.BetaUpdates -> SwitchPreference(
                    title = stringResource(R.string.settings_about_beta_updates_title),
                    subtitle = if (uiState.betaUpdatesEnabled) {
                        stringResource(R.string.settings_about_beta_updates_subtitle_on)
                    } else {
                        stringResource(R.string.settings_about_beta_updates_subtitle_off)
                    },
                    isEnabled = uiState.betaUpdatesEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBetaUpdatesEnabled(it) }
                )

                AboutItem.BackupSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                AboutItem.ExportSettings -> ActionPreference(
                    icon = Icons.Outlined.Upload,
                    title = stringResource(R.string.settings_about_export_title),
                    subtitle = stringResource(
                        R.string.settings_about_export_subtitle,
                        SettingsBackupRepository.FILE_NAME
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.exportSettings() }
                )

                AboutItem.ImportSettings -> ActionPreference(
                    icon = Icons.Outlined.Download,
                    title = stringResource(R.string.settings_about_import_title),
                    subtitle = stringResource(R.string.settings_about_import_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.requestImportSettings() }
                )

                AboutItem.SystemSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                AboutItem.SystemizeHelper -> ActionPreference(
                    icon = Icons.Default.HealthAndSafety,
                    title = stringResource(R.string.settings_about_systemize_title),
                    subtitle = stringResource(R.string.settings_about_systemize_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.writeSystemizeScript() }
                )

                AboutItem.RestartApp -> ActionPreference(
                    icon = Icons.Outlined.RestartAlt,
                    title = stringResource(R.string.settings_about_restart_title),
                    subtitle = stringResource(R.string.settings_about_restart_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.restartApp() }
                )

                AboutItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                AboutItem.FileLogging -> {
                    if (uiState.fileLoggingPath != null) {
                        SwitchPreference(
                            icon = Icons.Default.Description,
                            title = stringResource(R.string.settings_about_file_logging_title),
                            subtitle = formatLoggingPath(context, uiState.fileLoggingPath),
                            isEnabled = uiState.fileLoggingEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.toggleFileLogging(it) },
                            onLabelClick = { viewModel.openLogFolderPicker() }
                        )
                    } else {
                        ActionPreference(
                            icon = Icons.Default.Description,
                            title = stringResource(R.string.settings_about_file_logging_enable_title),
                            subtitle = stringResource(R.string.settings_about_file_logging_enable_subtitle),
                            isFocused = isFocused(item),
                            onClick = { viewModel.openLogFolderPicker() }
                        )
                    }
                }

                AboutItem.LogLevel -> CyclePreference(
                    title = stringResource(R.string.settings_about_log_level_title),
                    value = uiState.fileLogLevel.name,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleFileLogLevel() },
                    onPrev = { viewModel.cycleFileLogLevel(-1) },
                    options = remember { LogLevel.entries.map { it.name } },
                    onSelect = { viewModel.setFileLogLevel(LogLevel.entries[it]) },
                    pickerRequestToken = pickerToken(item)
                )

                AboutItem.SaveDebugLogging -> SwitchPreference(
                    title = stringResource(R.string.settings_about_save_debug_title),
                    subtitle = if (uiState.saveDebugLoggingEnabled) {
                        stringResource(R.string.settings_about_save_debug_subtitle_on)
                    } else {
                        stringResource(R.string.settings_about_save_debug_subtitle_off)
                    },
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

@Composable
private fun UpdateActionsRow(
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    isFocused: Boolean,
    actionIndex: Int,
    onPrimary: () -> Unit,
    onChangelog: () -> Unit
) {
    Row(
        modifier = preferenceModifier(isFocused),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = preferenceContentColor(isFocused)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = preferenceSecondaryColor(isFocused)
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        ActionButton(
            label = primaryLabel,
            onClick = onPrimary,
            focused = isFocused && actionIndex == 0,
            primary = true,
            enabled = primaryEnabled
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        ActionButton(
            label = stringResource(R.string.settings_about_update_action_changelog),
            onClick = onChangelog,
            focused = isFocused && actionIndex == 1
        )
    }
}

@Composable
private fun ChangelogPreviewRow(
    version: String,
    body: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = preferenceModifier(isFocused, onClick = onClick)) {
        Text(
            text = stringResource(R.string.settings_about_changelog_preview_title, version),
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = if (isFocused) preferenceSecondaryColor(true) else theme.textDim,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
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
                    text = stringResource(R.string.settings_about_version_argosy_label),
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
                        text = stringResource(R.string.settings_about_version_romm_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.settings_about_version_romm_value, rommVersion),
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
                text = stringResource(R.string.settings_about_licenses_label),
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

private fun formatLoggingPath(context: android.content.Context, rawPath: String): String {
    val primaryRoot = com.nendo.argosy.data.storage.StoragePathUtils.primaryExternalRoot
    return when {
        rawPath.startsWith(primaryRoot) ->
            rawPath.replaceFirst(primaryRoot, context.getString(R.string.settings_about_log_path_internal))
        rawPath.startsWith("/storage/") -> {
            val parts = rawPath.removePrefix("/storage/").split("/", limit = 2)
            if (parts.size == 2) {
                context.getString(R.string.settings_about_log_path_sd_card, parts[1])
            } else {
                rawPath
            }
        }
        else -> rawPath
    }
}
