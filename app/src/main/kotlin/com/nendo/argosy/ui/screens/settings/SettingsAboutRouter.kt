package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.AppLanguage
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.data.preferences.SettingsExportResult
import com.nendo.argosy.data.preferences.SettingsImportResult
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "SettingsAboutRouter"

internal fun routeSetBetaUpdatesEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setBetaUpdatesEnabled(enabled)
        vm._uiState.update { it.copy(betaUpdatesEnabled = enabled) }
    }
}

internal fun routeSetAppAffinityEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setAppAffinityEnabled(enabled)
        vm._uiState.update { it.copy(appAffinityEnabled = enabled) }
    }
}

/**
 * Persists the launcher's display language, mirrors it into SessionStateStore so every Activity
 * and foreground service can read it synchronously from attachBaseContext, applies the framework
 * per-app language on API 33+ as a visible-in-system-settings bonus, and notifies the dual-screen
 * companion so both screens recreate with the new locale. The DataStore write and the
 * SessionStateStore mirror happen before the notify, since a recreate that raced ahead of either
 * would read the language it is replacing.
 */
internal fun routeSetAppLanguage(vm: SettingsViewModel, tag: String) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setAppLanguage(tag)
        com.nendo.argosy.data.preferences.SessionStateStore(vm.context).setAppLanguage(tag)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val localeManager = vm.context.getSystemService(android.app.LocaleManager::class.java)
            localeManager?.applicationLocales = if (tag == AppLanguage.SYSTEM.tag) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(tag)
            }
        }
        vm._uiState.update { it.copy(appLanguage = AppLanguage.fromString(tag)) }
        com.nendo.argosy.DualScreenManagerHolder.instance?.notifyLocaleChanged()
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

internal fun routeCycleFileLogLevel(vm: SettingsViewModel, direction: Int = 1) {
    val currentLevel = vm._uiState.value.fileLogLevel
    val newLevel = if (direction > 0) currentLevel.next() else currentLevel.prev()
    vm.setFileLogLevel(newLevel)
}

internal fun routeSetSaveDebugLoggingEnabled(vm: SettingsViewModel, enabled: Boolean) {
    vm.viewModelScope.launch {
        vm.preferencesRepository.setSaveDebugLoggingEnabled(enabled)
    }
    vm._uiState.update { it.copy(saveDebugLoggingEnabled = enabled) }
}

internal fun routeCheckForUpdates(vm: SettingsViewModel) {
    if (com.nendo.argosy.BuildConfig.DEBUG) return

    vm.viewModelScope.launch {
        vm._uiState.update { it.copy(updateCheck = it.updateCheck.copy(isChecking = true, error = null)) }

        when (val state = vm.updateRepository.checkForUpdates()) {
            is UpdateState.UpdateAvailable -> {
                vm._uiState.update {
                    it.copy(
                        updateCheck = UpdateCheckState(
                            isChecking = false,
                            updateAvailable = true,
                            latestVersion = state.release.tagName,
                            latestName = state.release.name,
                            latestBody = state.release.body,
                            downloadUrl = state.apkAsset.downloadUrl
                        )
                    )
                }
            }
            is UpdateState.UpToDate -> {
                vm._uiState.update {
                    it.copy(updateCheck = UpdateCheckState(isChecking = false, hasChecked = true, updateAvailable = false))
                }
            }
            is UpdateState.Error -> {
                vm._uiState.update {
                    it.copy(updateCheck = UpdateCheckState(isChecking = false, error = state.message))
                }
            }
            else -> {
                vm._uiState.update { it.copy(updateCheck = UpdateCheckState(isChecking = false)) }
            }
        }
    }
}

internal fun routeMoveUpdateActionFocus(vm: SettingsViewModel, delta: Int) {
    vm._uiState.update {
        it.copy(aboutUpdateActionIndex = (it.aboutUpdateActionIndex + delta).coerceIn(0, 1))
    }
}

internal fun routeOpenChangelog(vm: SettingsViewModel) {
    val check = vm._uiState.value.updateCheck
    val seed = if (check.updateAvailable && check.latestVersion != null) {
        listOf(
            ChangelogRelease(
                tag = check.latestVersion,
                name = check.latestName ?: check.latestVersion,
                body = check.latestBody,
                prerelease = check.latestVersion.contains("-"),
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

internal fun routeDownloadAndInstallUpdate(vm: SettingsViewModel, context: android.content.Context) {
    val state = vm._uiState.value.updateCheck
    val url = state.downloadUrl ?: return
    val version = state.latestVersion ?: return

    if (state.isDownloading) return

    vm.viewModelScope.launch {
        vm._uiState.update { it.copy(updateCheck = it.updateCheck.copy(isDownloading = true, downloadProgress = 0, error = null)) }

        try {
            val apkFile = withContext(Dispatchers.IO) {
                routeDownloadApk(vm, context, url, version) { progress ->
                    vm._uiState.update { it.copy(updateCheck = it.updateCheck.copy(downloadProgress = progress)) }
                }
            }

            vm._uiState.update {
                it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, readyToInstall = true))
            }

            vm.appInstaller.installApk(context, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download update", e)
            vm._uiState.update {
                it.copy(updateCheck = it.updateCheck.copy(isDownloading = false, error = e.message ?: "Download failed"))
            }
        }
    }
}

private fun routeDownloadApk(
    vm: SettingsViewModel,
    context: android.content.Context,
    url: String,
    version: String,
    onProgress: (Int) -> Unit
): File {
    val client = OkHttpClient.Builder().build()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()

    if (!response.isSuccessful) {
        throw Exception("Download failed: ${response.code}")
    }

    val body = response.body ?: throw Exception("Empty response")
    val contentLength = body.contentLength()
    val apkFile = vm.appInstaller.getApkCacheFile(context, version)

    apkFile.outputStream().use { output ->
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Long = 0
            var read: Int

            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesRead += read
                if (contentLength > 0) {
                    val progress = ((bytesRead * 100) / contentLength).toInt()
                    onProgress(progress)
                }
            }
        }
    }

    return apkFile
}
