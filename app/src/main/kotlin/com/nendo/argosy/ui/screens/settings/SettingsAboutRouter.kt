package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.AppLanguage
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.preferences.SettingsExportResult
import com.nendo.argosy.data.preferences.SettingsImportResult
import com.nendo.argosy.data.model.AppUpdateDownload
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.ui.screens.settings.sections.aboutUpdateActions
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun routeSetBetaUpdatesEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setBetaUpdatesEnabled(enabled)
        vm._uiState.update { it.copy(betaUpdatesEnabled = enabled) }
    }
}

/**
 * Persists the launcher's display language and mirrors it into SessionStateStore, so every
 * Activity and foreground service can read it synchronously from attachBaseContext. The writes
 * happen first: a restart that raced ahead of either would read the language it is replacing.
 *
 * Exactly one restart mechanism runs, never both. On API 33+ the framework per-app language API
 * restarts the activities itself, so asking the dual-screen manager to recreate as well would put
 * two teardowns on the same activity and leave the surviving composition holding an InputDispatcher
 * nothing is subscribed to: gamepad input dies while touch, which never goes through the
 * dispatcher, keeps working. Below 33 there is no framework API, so the notify is the only restart.
 */
internal fun routeSetAppLanguage(vm: SettingsViewModel, tag: String) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setAppLanguage(tag)
        com.nendo.argosy.data.preferences.SessionStateStore(vm.context).setAppLanguage(tag)
        vm._uiState.update { it.copy(appLanguage = AppLanguage.fromString(tag)) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = vm.context.getSystemService(android.app.LocaleManager::class.java)
            localeManager?.applicationLocales = if (tag == AppLanguage.SYSTEM.tag) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(tag)
            }
        } else {
            com.nendo.argosy.DualScreenManagerHolder.instance?.notifyLocaleChanged()
        }
    }
}

internal fun routeCycleAppLanguage(vm: SettingsViewModel, direction: Int) {
    val entries = AppLanguage.entries
    val current = vm._uiState.value.appLanguage
    val next = entries[(current.ordinal + direction).mod(entries.size)]
    routeSetAppLanguage(vm, next.tag)
}

internal fun routeOpenLogFolderPicker(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        vm._openLogFolderPickerEvent.emit(Unit)
    }
}

internal fun routeExportSettings(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        when (val result = vm.settingsBackupRepository.exportToFile()) {
            is SettingsExportResult.Success -> vm.notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_router_settings_exported_title),
                subtitle = NotificationText.Res(
                    R.string.settings_shell_router_settings_exported_subtitle,
                    listOf(result.count, result.path)
                ),
                type = NotificationType.SUCCESS,
                duration = NotificationDuration.MEDIUM
            )
            is SettingsExportResult.Error -> vm.notificationManager.showError(NotificationText.Raw(result.message))
        }
    }
}

internal fun routeRequestImportSettings(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(showImportSettingsConfirm = true) }
}

internal fun routeCancelImportSettings(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(showImportSettingsConfirm = false) }
}

internal fun routeConfirmImportSettings(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(showImportSettingsConfirm = false) }
    vm.viewModelScope.launch { vm._openSettingsBackupPickerEvent.emit(Unit) }
}

internal fun routeImportSettingsFrom(vm: SettingsViewModel, path: String) {
    vm.viewModelScope.launch {
        when (val result = vm.settingsBackupRepository.importFromFile(path)) {
            is SettingsImportResult.Success -> {
                vm.loadSettings()
                vm.notificationManager.show(
                    title = NotificationText.Res(R.string.settings_shell_router_settings_imported_title),
                    subtitle = importSubtitle(result),
                    type = NotificationType.SUCCESS,
                    duration = NotificationDuration.MEDIUM
                )
            }
            is SettingsImportResult.Error -> vm.notificationManager.showError(NotificationText.Raw(result.message))
        }
    }
}

private fun importSubtitle(result: SettingsImportResult.Success): NotificationText = when {
    result.skipped > 0 -> NotificationText.Res(
        R.string.settings_shell_router_import_subtitle_with_skipped, listOf(result.applied, result.skipped)
    )
    else -> NotificationText.Res(R.string.settings_shell_router_import_subtitle, listOf(result.applied))
}

internal fun routeSetFileLoggingPath(vm: SettingsViewModel, path: String) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setFileLoggingPath(path)
        vm.preferencesRepository.setFileLoggingEnabled(true)
    }
    vm._uiState.update { it.copy(fileLoggingEnabled = true, fileLoggingPath = path) }
}

internal fun routeToggleFileLogging(vm: SettingsViewModel, enabled: Boolean) {
    if (enabled && vm._uiState.value.fileLoggingPath == null) {
        vm.openLogFolderPicker()
    } else {
        vm.viewModelScope.launch {
            vm.preferencesRepository.setFileLoggingEnabled(enabled)
        }
        vm._uiState.update { it.copy(fileLoggingEnabled = enabled) }
    }
}

internal fun routeSetFileLogLevel(vm: SettingsViewModel, level: LogLevel) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setFileLogLevel(level)
    }
    vm._uiState.update { it.copy(fileLogLevel = level) }
}

internal fun routeSetSaveDebugLoggingEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setSaveDebugLoggingEnabled(enabled)
    }
    vm._uiState.update { it.copy(saveDebugLoggingEnabled = enabled) }
}

internal fun routeSetDebugUpdateChecksEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setDebugUpdateChecksEnabled(enabled)
        vm._uiState.update { it.copy(debugUpdateChecksEnabled = enabled) }
    }
}

internal fun routeObserveAppUpdate(vm: SettingsViewModel) {
    val manager = vm.appUpdateManager
    vm.viewModelScope.launch {
        combine(manager.stored, manager.download) { stored, download -> stored to download }
            .collect { (stored, download) ->
                val latest = manager.availableIn(stored)
                val isStored = latest != null && manager.isStored(latest)
                val tag = latest?.tag
                vm._uiState.update { state ->
                    state.copy(
                        updateCheck = state.updateCheck.copy(
                            latest = latest,
                            isSkipped = tag != null && tag == stored.skippedTag,
                            isStored = isStored,
                            isDownloading = download is AppUpdateDownload.Downloading && download.tag == tag,
                            downloadProgress = (download as? AppUpdateDownload.Downloading)
                                ?.takeIf { it.tag == tag }?.progress ?: 0,
                            error = when (download) {
                                is AppUpdateDownload.Failed -> download.message.takeIf { download.tag == tag }
                                is AppUpdateDownload.Downloading, is AppUpdateDownload.HandedOff -> null
                                AppUpdateDownload.Idle -> state.updateCheck.error
                            }
                        )
                    )
                }
            }
    }
}

internal fun routeCheckForUpdates(vm: SettingsViewModel) {
    if (com.nendo.argosy.BuildConfig.DEBUG || vm._uiState.value.updateCheck.isChecking) return
    vm.viewModelScope.launch {
        vm._uiState.update { it.copy(updateCheck = it.updateCheck.copy(isChecking = true, error = null)) }
        val result = vm.appUpdateManager.check()
        vm._uiState.update { state ->
            state.copy(
                updateCheck = state.updateCheck.copy(
                    isChecking = false,
                    hasChecked = result is UpdateState.UpdateAvailable || result is UpdateState.UpToDate,
                    error = (result as? UpdateState.Error)?.message
                )
            )
        }
    }
}

internal fun routeMoveUpdateActionFocus(vm: SettingsViewModel, delta: Int) {
    vm._uiState.update {
        val last = (aboutUpdateActions(it.updateCheck).size - 1).coerceAtLeast(0)
        it.copy(aboutUpdateActionIndex = (it.aboutUpdateActionIndex + delta).coerceIn(0, last))
    }
}

internal fun routeOpenChangelog(vm: SettingsViewModel) {
    val latest = vm._uiState.value.updateCheck.latest
    val seed = if (latest != null) {
        listOf(
            ChangelogRelease(
                tag = latest.tag,
                name = latest.name.ifBlank { latest.tag },
                body = latest.notes,
                prerelease = latest.tag.contains("-"),
                publishedAt = null
            )
        )
    } else {
        emptyList()
    }
    vm._uiState.update { it.copy(changelog = ChangelogState(visible = true, releases = seed)) }
    routeLoadChangelogPage(vm)
}

internal fun routeCloseChangelog(vm: SettingsViewModel) {
    vm._uiState.update { it.copy(changelog = ChangelogState()) }
}

internal fun routeLoadChangelogPage(vm: SettingsViewModel) {
    val changelog = vm._uiState.value.changelog
    if (changelog.isLoading) return
    val nextPage = changelog.page + 1
    vm._uiState.update { it.copy(changelog = it.changelog.copy(isLoading = true)) }

    vm.viewModelScope.launch {
        vm.updateRepository.listReleases(nextPage).fold(
            onSuccess = { pageResult ->
                vm._uiState.update { state ->
                    val existing = if (nextPage == 1) emptyList() else state.changelog.releases
                    val incoming = pageResult.releases.map { release ->
                        ChangelogRelease(
                            tag = release.tagName,
                            name = release.name,
                            body = release.body,
                            prerelease = release.prerelease,
                            publishedAt = release.publishedAt
                        )
                    }
                    state.copy(
                        changelog = state.changelog.copy(
                            releases = (existing + incoming).distinctBy { it.tag },
                            page = nextPage,
                            canLoadMore = pageResult.hasMore,
                            isLoading = false
                        )
                    )
                }
            },
            onFailure = {
                vm._uiState.update { state ->
                    state.copy(changelog = state.changelog.copy(isLoading = false))
                }
            }
        )
    }
}

internal fun routeInstallUpdate(vm: SettingsViewModel, fresh: Boolean) {
    val state = vm._uiState.value.updateCheck
    val latest = state.latest ?: return
    if (state.isDownloading) return
    vm.appUpdateManager.install(latest, fresh = fresh)
}

internal fun routeUpdateAction(vm: SettingsViewModel, action: AboutUpdateAction) {
    val check = vm._uiState.value.updateCheck
    when (action) {
        AboutUpdateAction.PRIMARY -> if (check.updateAvailable) routeInstallUpdate(vm, fresh = false) else routeCheckForUpdates(vm)
        AboutUpdateAction.REDOWNLOAD -> routeInstallUpdate(vm, fresh = true)
        AboutUpdateAction.CHANGELOG -> routeOpenChangelog(vm)
    }
}
