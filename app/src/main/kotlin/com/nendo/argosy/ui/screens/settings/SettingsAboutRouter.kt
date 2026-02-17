package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.remote.github.UpdateState
import com.nendo.argosy.util.LogLevel
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

internal fun routeOpenLogFolderPicker(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        vm._openLogFolderPickerEvent.emit(Unit)
    }
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
