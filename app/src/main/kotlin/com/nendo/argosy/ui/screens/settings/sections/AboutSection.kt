package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.SegmentedPreference
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
import com.nendo.argosy.ui.screens.settings.AboutUpdateAction
import com.nendo.argosy.data.update.BuildChannel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.dialogs.LicensesDialog
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.LogLevel

internal data class AboutLayoutState(val hasLogPath: Boolean, val hasChangelog: Boolean) {
    companion object {
        fun from(state: SettingsUiState) = AboutLayoutState(
            hasLogPath = state.fileLoggingPath != null,
            hasChangelog = aboutHasChangelog(state.updateCheck)
        )
    }
}

internal sealed class AboutItem(
    val key: String,
    val section: String,
    val visibleWhen: (AboutLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : AboutItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (AboutLayoutState) -> Boolean = { true }
    ) : AboutItem(key, section, visibleWhen), SearchableSetting

    data object VersionInfo : AboutItem("versionInfo", "version")
    data object CheckUpdates : Row(
        key = "checkUpdates",
        section = "version",
        labelRes = R.string.settings_about_update_title_check,
        visibleWhen = { BuildChannel.selfUpdates }
    )
    data object ChangelogPreview : Row(
        key = "changelogPreview",
        section = "version",
        labelRes = R.string.settings_search_label_about_changelog,
        visibleWhen = { it.hasChangelog }
    )
    data object BetaUpdates : Row(
        key = "betaUpdates",
        section = "version",
        labelRes = R.string.settings_about_beta_updates_title,
        visibleWhen = { BuildChannel.selfUpdates }
    )
    data object BackupSpacer : AboutItem("backupSpacer", "backup")
    data object ExportSettings : Row("exportSettings", "backup", R.string.settings_about_export_title)
    data object ImportSettings : Row("importSettings", "backup", R.string.settings_about_import_title)
    data object SystemSpacer : AboutItem("systemSpacer", "system")
    data object SystemizeHelper :
        Row("systemizeHelper", "system", R.string.settings_about_systemize_title)
    data object RestartApp : Row("restartApp", "system", R.string.settings_about_restart_title)
    data object SectionSpacer : AboutItem("spacer", "debug")
    data object ControllerTest : Row("controllerTest", "debug", R.string.about_tools_controller_title)
    data object DisplayInfo : Row("displayInfo", "debug", R.string.about_tools_display_title)
    data object ExportDiagnostics : Row("exportDiagnostics", "debug", R.string.about_tools_export_title)
    data object RunSetupAgain : Row(
        key = "runSetupAgain",
        section = "debug",
        labelRes = R.string.about_tools_setup_title,
        visibleWhen = { BuildChannel.isPrereleaseBuild }
    )
    data object ShowUpdatePrompt : Row(
        key = "showUpdatePrompt",
        section = "debug",
        labelRes = R.string.about_tools_prompt_title,
        visibleWhen = { BuildChannel.isPrereleaseBuild }
    )
    data object ResetNotices : Row(
        key = "resetNotices",
        section = "debug",
        labelRes = R.string.about_tools_reset_title,
        visibleWhen = { BuildChannel.isPrereleaseBuild }
    )
    data object DebugUpdateChecks : Row(
        key = "debugUpdateChecks",
        section = "debug",
        labelRes = R.string.settings_about_debug_update_checks_title,
        visibleWhen = { com.nendo.argosy.BuildConfig.DEBUG }
    )
    data object FileLogging : Row("fileLogging", "debug", R.string.settings_about_file_logging_title)
    data object LogLevel : Row(
        key = "logLevel",
        section = "debug",
        labelRes = R.string.settings_about_log_level_title,
        visibleWhen = { it.hasLogPath }
    )
    data object SaveDebugLogging : Row(
        key = "saveDebugLogging",
        section = "debug",
        labelRes = R.string.settings_about_save_debug_title,
        visibleWhen = { it.hasLogPath }
    )

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
                SectionSpacer, DebugHeader,
                ControllerTest, DisplayInfo, ExportDiagnostics, RunSetupAgain, ShowUpdatePrompt, ResetNotices,
                DebugUpdateChecks, FileLogging, LogLevel, SaveDebugLogging
            )
    }
}

internal val aboutLayout = SettingsLayout<AboutItem, AboutLayoutState>(
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
    updateCheck.latest?.notes != null

internal fun aboutUpdateActions(updateCheck: UpdateCheckState): List<AboutUpdateAction> = buildList {
    add(AboutUpdateAction.PRIMARY)
    if (updateCheck.updateAvailable && updateCheck.isStored && !updateCheck.isDownloading) {
        add(AboutUpdateAction.REDOWNLOAD)
    }
    add(AboutUpdateAction.CHANGELOG)
}

@Composable
fun AboutSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val updateCheck = uiState.updateCheck
    val isDebug = com.nendo.argosy.BuildConfig.DEBUG
    val isOnBetaVersion = BuildChannel.isPrereleaseBuild
    val tools by viewModel.aboutTools.state.collectAsState()
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
        if (!updateCheck.isChecking && !updateCheck.isDownloading) {
            viewModel.checkForUpdates()
        }
    }

    fun isFocused(item: AboutItem): Boolean =
        uiState.focusedIndex == aboutLayout.focusIndexOf(item, layoutState)


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

                is AboutItem.VersionInfo -> VersionInfoRow(
                    argosyVersion = uiState.appVersion,
                    rommVersion = uiState.server.rommVersion,
                    onLicensesClick = { showLicensesDialog = true }
                )

                is AboutItem.CheckUpdates -> {
                    val checkTitle = stringResource(item.labelRes)
                    val latestVersion = updateCheck.latest?.displayVersion.orEmpty()
                    val (title, subtitle) = when {
                        isDebug && !updateCheck.updateAvailable -> checkTitle to
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
                        updateCheck.updateAvailable && updateCheck.isSkipped ->
                            stringResource(R.string.settings_about_update_title_available) to
                                stringResource(R.string.settings_about_update_subtitle_skipped, latestVersion)
                        updateCheck.updateAvailable && updateCheck.isStored ->
                            stringResource(R.string.settings_about_update_title_available) to
                                stringResource(R.string.settings_about_update_subtitle_stored, latestVersion)
                        updateCheck.updateAvailable ->
                            stringResource(R.string.settings_about_update_title_available) to
                                stringResource(R.string.settings_about_update_subtitle_available, latestVersion)
                        updateCheck.hasChecked && isOnBetaVersion -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_current_prerelease)
                        updateCheck.hasChecked -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_current)
                        isOnBetaVersion -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_on_prerelease)
                        else -> checkTitle to
                            stringResource(R.string.settings_about_update_subtitle_idle)
                    }
                    val primaryLabel = if (updateCheck.updateAvailable) {
                        stringResource(R.string.settings_about_update_action_install)
                    } else {
                        stringResource(R.string.settings_about_update_action_check)
                    }
                    val redownloadLabel = stringResource(R.string.settings_about_update_action_redownload)
                    val changelogLabel = stringResource(R.string.settings_about_update_action_changelog)
                    val busy = updateCheck.isChecking || updateCheck.isDownloading
                    UpdateActionsRow(
                        title = title,
                        subtitle = subtitle,
                        actions = aboutUpdateActions(updateCheck),
                        labelFor = { action ->
                            when (action) {
                                AboutUpdateAction.PRIMARY -> primaryLabel
                                AboutUpdateAction.REDOWNLOAD -> redownloadLabel
                                AboutUpdateAction.CHANGELOG -> changelogLabel
                            }
                        },
                        enabledFor = { action ->
                            when (action) {
                                AboutUpdateAction.PRIMARY -> !busy && (updateCheck.updateAvailable || !isDebug)
                                AboutUpdateAction.REDOWNLOAD -> !busy
                                AboutUpdateAction.CHANGELOG -> true
                            }
                        },
                        isFocused = isFocused(item),
                        actionIndex = uiState.aboutUpdateActionIndex,
                        onAction = { viewModel.runUpdateAction(it) }
                    )
                }

                is AboutItem.ChangelogPreview -> ChangelogPreviewRow(
                    version = updateCheck.latest?.tag.orEmpty(),
                    body = updateCheck.latest?.notes.orEmpty(),
                    isFocused = isFocused(item),
                    onClick = { viewModel.openChangelog() }
                )

                is AboutItem.BetaUpdates -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = if (uiState.betaUpdatesEnabled) {
                        stringResource(R.string.settings_about_beta_updates_subtitle_on)
                    } else {
                        stringResource(R.string.settings_about_beta_updates_subtitle_off)
                    },
                    isEnabled = uiState.betaUpdatesEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setBetaUpdatesEnabled(it) }
                )

                is AboutItem.BackupSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                is AboutItem.ExportSettings -> ActionPreference(
                    icon = Icons.Outlined.Upload,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(
                        R.string.settings_about_export_subtitle,
                        SettingsBackupRepository.FILE_NAME
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.exportSettings() }
                )

                is AboutItem.ImportSettings -> ActionPreference(
                    icon = Icons.Outlined.Download,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_about_import_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.requestImportSettings() }
                )

                is AboutItem.SystemSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                is AboutItem.SystemizeHelper -> {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val systemState = remember(context) { com.nendo.argosy.util.SystemAppStatus.state(context) }
                    ActionPreference(
                        icon = Icons.Default.HealthAndSafety,
                        title = stringResource(
                            when (systemState) {
                                com.nendo.argosy.util.SystemAppState.SYSTEMIZED -> R.string.settings_about_unsystemize_title
                                else -> item.labelRes
                            }
                        ),
                        subtitle = stringResource(
                            when (systemState) {
                                com.nendo.argosy.util.SystemAppState.ORDINARY -> R.string.settings_about_systemize_subtitle
                                com.nendo.argosy.util.SystemAppState.SYSTEMIZED -> R.string.settings_about_unsystemize_subtitle
                                com.nendo.argosy.util.SystemAppState.PART_OF_SYSTEM -> R.string.settings_about_system_image_subtitle
                            }
                        ),
                        isFocused = isFocused(item),
                        onClick = {
                            com.nendo.argosy.util.SystemAppStatus.systemAppScript(context)?.let(viewModel::applyRootScript)
                        }
                    )
                }

                is AboutItem.RestartApp -> ActionPreference(
                    icon = Icons.Outlined.RestartAlt,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_about_restart_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.restartApp() }
                )

                is AboutItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

                is AboutItem.ControllerTest -> ActionPreference(
                    icon = Icons.Default.SportsEsports,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.about_tools_controller_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.aboutTools.openControllerTest() }
                )

                is AboutItem.DisplayInfo -> ActionPreference(
                    icon = Icons.Outlined.Monitor,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.about_tools_display_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.aboutTools.openDisplayInfo() }
                )

                is AboutItem.ExportDiagnostics -> ActionPreference(
                    icon = Icons.Default.Share,
                    title = stringResource(item.labelRes),
                    subtitle = if (tools.exporting) {
                        stringResource(R.string.about_tools_export_subtitle_busy)
                    } else {
                        stringResource(R.string.about_tools_export_subtitle)
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.aboutTools.exportDiagnostics() }
                )

                is AboutItem.RunSetupAgain -> ActionPreference(
                    icon = Icons.Default.Replay,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.about_tools_setup_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.runSetupAgain() }
                )

                is AboutItem.ShowUpdatePrompt -> ActionPreference(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(item.labelRes),
                    subtitle = if (tools.promptLoading) {
                        stringResource(R.string.about_tools_prompt_subtitle_busy)
                    } else {
                        stringResource(R.string.about_tools_prompt_subtitle)
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.aboutTools.showUpdatePromptNow() }
                )

                is AboutItem.ResetNotices -> ActionPreference(
                    icon = Icons.Default.Restore,
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.about_tools_reset_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.aboutTools.requestResetNotices() }
                )

                is AboutItem.DebugUpdateChecks -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = if (uiState.debugUpdateChecksEnabled) {
                        stringResource(R.string.settings_about_debug_update_checks_subtitle_on)
                    } else {
                        stringResource(R.string.settings_about_debug_update_checks_subtitle_off)
                    },
                    isEnabled = uiState.debugUpdateChecksEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setDebugUpdateChecksEnabled(it) }
                )

                is AboutItem.FileLogging -> {
                    if (uiState.fileLoggingPath != null) {
                        SwitchPreference(
                            icon = Icons.Default.Description,
                            title = stringResource(item.labelRes),
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

                is AboutItem.LogLevel -> SegmentedPreference(
                    title = stringResource(item.labelRes),
                    options = LogLevel.entries.map { stringResource(it.labelRes) },
                    selectedIndex = LogLevel.entries.indexOf(uiState.fileLogLevel),
                    isFocused = isFocused(item),
                    onSelect = { viewModel.setFileLogLevel(LogLevel.entries[it]) }
                )

                is AboutItem.SaveDebugLogging -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = if (uiState.saveDebugLoggingEnabled) {
                        stringResource(R.string.settings_about_save_debug_subtitle_on)
                    } else {
                        stringResource(R.string.settings_about_save_debug_subtitle_off)
                    },
                    isEnabled = uiState.saveDebugLoggingEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setSaveDebugLoggingEnabled(it) }
                )
            }
    }
}

@Composable
private fun UpdateActionsRow(
    title: String,
    subtitle: String,
    actions: List<AboutUpdateAction>,
    labelFor: (AboutUpdateAction) -> String,
    enabledFor: (AboutUpdateAction) -> Boolean,
    isFocused: Boolean,
    actionIndex: Int,
    onAction: (AboutUpdateAction) -> Unit
) {
    val focusedAction = actionIndex.coerceIn(0, actions.lastIndex)
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
        actions.forEachIndexed { index, action ->
            if (index > 0) Spacer(modifier = Modifier.width(Dimens.spacingSm))
            ActionButton(
                label = labelFor(action),
                onClick = { onAction(action) },
                focused = isFocused && focusedAction == index,
                primary = action == AboutUpdateAction.PRIMARY,
                enabled = enabledFor(action)
            )
        }
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
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            modifier = Modifier
                .clickableNoFocus(onClick = onLicensesClick)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
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
                modifier = Modifier.size(Dimens.iconSm)
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
