package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository
import com.nendo.argosy.data.remote.github.FetchReleaseResult
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import com.nendo.argosy.ui.screens.settings.VariantOption
import com.nendo.argosy.ui.screens.settings.VariantPickerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SteamSettingsDelegate @Inject constructor(
    private val steamRepository: SteamRepository,
    private val notificationManager: NotificationManager,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val emulatorUpdateRepository: EmulatorUpdateRepository
) {
    private val _state = MutableStateFlow(SteamSettingsState())
    val state: StateFlow<SteamSettingsState> = _state.asStateFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    val downloadProgress = emulatorDownloadManager.downloadProgress

    fun updateState(newState: SteamSettingsState) {
        _state.value = newState
    }

    fun loadSteamSettings(context: Context, scope: CoroutineScope) {
        scope.launch {
            val hasPermission = checkStoragePermission()
            val installedLaunchers = SteamLaunchers.getInstalled(context).map { launcher ->
                InstalledSteamLauncher(
                    packageName = launcher.packageName,
                    displayName = launcher.displayName,
                    gameCount = 0,
                    supportsScanning = launcher.supportsScanning,
                    scanMayIncludeUninstalled = launcher.scanMayIncludeUninstalled
                )
            }

            val installedPackages = installedLaunchers.map { it.packageName }.toSet()
            val notInstalledLaunchers = EmulatorRegistry.getForPlatform("steam")
                .filter { it.packageName !in installedPackages }
                .map { def ->
                    NotInstalledSteamLauncher(
                        emulatorId = def.id,
                        displayName = def.displayName,
                        hasDirectDownload = def.githubRepo != null
                    )
                }

            _state.update {
                it.copy(
                    hasStoragePermission = hasPermission,
                    installedLaunchers = installedLaunchers,
                    notInstalledLaunchers = notInstalledLaunchers,
                    launcherActionIndex = 0
                )
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun installSteamLauncher(emulatorId: String, scope: CoroutineScope) {
        val def = EmulatorRegistry.getById(emulatorId) ?: return

        if (def.githubRepo == null) {
            scope.launch { def.downloadUrl?.let { _openUrlEvent.emit(it) } }
            return
        }

        if (!emulatorDownloadManager.canInstallPackages()) {
            emulatorDownloadManager.openInstallPermissionSettings()
            return
        }

        scope.launch {
            _state.update {
                it.copy(downloadingLauncherId = emulatorId, downloadProgress = 0f)
            }

            when (val result = emulatorUpdateRepository.fetchLatestRelease(def)) {
                is FetchReleaseResult.Success -> {
                    emulatorDownloadManager.downloadAndInstall(
                        emulatorId = emulatorId,
                        downloadUrl = result.downloadUrl,
                        assetName = result.assetName,
                        variant = result.variant
                    )
                }
                is FetchReleaseResult.MultipleVariants -> {
                    _state.update {
                        it.copy(
                            downloadingLauncherId = null,
                            downloadProgress = null,
                            variantPickerInfo = VariantPickerInfo(
                                emulatorId = emulatorId,
                                emulatorName = def.displayName,
                                variants = result.variants.map { v ->
                                    VariantOption(
                                        assetName = v.assetName,
                                        downloadUrl = v.downloadUrl,
                                        fileSize = v.assetSize,
                                        variant = v.variant
                                    )
                                }
                            ),
                            variantPickerFocusIndex = 0
                        )
                    }
                }
                is FetchReleaseResult.Error -> {
                    _state.update {
                        it.copy(downloadingLauncherId = null, downloadProgress = null)
                    }
                    notificationManager.showError("Download failed: ${result.message}")
                }
            }
        }
    }

    fun moveVariantPickerFocus(delta: Int) {
        val info = _state.value.variantPickerInfo ?: return
        val newIndex = (_state.value.variantPickerFocusIndex + delta)
            .coerceIn(0, info.variants.size - 1)
        _state.update { it.copy(variantPickerFocusIndex = newIndex) }
    }

    fun confirmVariantSelection() {
        val info = _state.value.variantPickerInfo ?: return
        val selected = info.variants.getOrNull(_state.value.variantPickerFocusIndex) ?: return
        _state.update {
            it.copy(
                variantPickerInfo = null,
                downloadingLauncherId = info.emulatorId,
                downloadProgress = 0f
            )
        }
        emulatorDownloadManager.downloadAndInstall(
            emulatorId = info.emulatorId,
            downloadUrl = selected.downloadUrl,
            assetName = selected.assetName,
            variant = selected.variant
        )
    }

    fun dismissVariantPicker() {
        _state.update { it.copy(variantPickerInfo = null) }
    }

    fun handleVariantPickerItemTap(index: Int) {
        _state.update { it.copy(variantPickerFocusIndex = index) }
    }

    fun moveLauncherActionFocus(delta: Int, launcherIndex: Int) {
        if (launcherIndex < 0 || launcherIndex >= _state.value.installedLaunchers.size) return

        val launcher = _state.value.installedLaunchers[launcherIndex]
        val maxIndex = if (launcher.supportsScanning) 1 else 0
        val newIndex = (_state.value.launcherActionIndex + delta).coerceIn(0, maxIndex)

        _state.update { it.copy(launcherActionIndex = newIndex) }
    }

    fun confirmLauncherAction(context: Context, scope: CoroutineScope, launcherIndex: Int) {
        if (launcherIndex < 0 || launcherIndex >= _state.value.installedLaunchers.size) return

        val launcher = _state.value.installedLaunchers[launcherIndex]

        if (launcher.supportsScanning && _state.value.launcherActionIndex == 0) {
            scanSteamLauncher(context, scope, launcher.packageName)
        } else {
            showAddSteamGameDialog(launcher.packageName)
        }
    }

    fun scanSteamLauncher(context: Context, scope: CoroutineScope, packageName: String) {
        val launcher = _state.value.installedLaunchers.find { it.packageName == packageName }
        if (launcher == null) return

        val steamLauncher = SteamLaunchers.getByPackage(packageName)

        scope.launch {
            _state.update {
                it.copy(isSyncing = true, syncingLauncher = packageName)
            }

            notificationManager.show("Scanning ${launcher.displayName}...")

            val scannedGames = if (steamLauncher?.supportsScanning == true) {
                withContext(Dispatchers.IO) {
                    steamLauncher.scan(context)
                }
            } else {
                emptyList()
            }

            if (scannedGames.isEmpty()) {
                _state.update {
                    it.copy(isSyncing = false, syncingLauncher = null)
                }
                notificationManager.show("No games found")
                return@launch
            }

            var addedCount = 0
            var skippedCount = 0

            for (game in scannedGames) {
                when (steamRepository.addGame(game.appId, packageName)) {
                    is SteamResult.Success -> addedCount++
                    is SteamResult.Error -> skippedCount++
                }
            }

            _state.update {
                it.copy(isSyncing = false, syncingLauncher = null)
            }

            val message = when {
                addedCount > 0 && skippedCount > 0 -> "Added $addedCount games, $skippedCount already existed"
                addedCount > 0 -> "Added $addedCount games"
                else -> "All ${scannedGames.size} games already in library"
            }
            notificationManager.show(message)
            loadSteamSettings(context, scope)
        }
    }

    fun refreshSteamMetadata(context: Context, scope: CoroutineScope) {
        scope.launch {
            _state.update {
                it.copy(isSyncing = true, syncingLauncher = "refresh")
            }

            when (val result = steamRepository.refreshAllMetadata()) {
                is SteamResult.Success -> {
                    notificationManager.show("Refreshed metadata for ${result.data} games")
                }
                is SteamResult.Error -> {
                    notificationManager.showError("Failed to refresh: ${result.message}")
                }
            }

            _state.update {
                it.copy(isSyncing = false, syncingLauncher = null)
            }
            loadSteamSettings(context, scope)
        }
    }

    fun showAddSteamGameDialog(launcherPackage: String? = null) {
        _state.update {
            it.copy(
                showAddGameDialog = true,
                addGameAppId = "",
                addGameError = null,
                isAddingGame = false,
                selectedLauncherPackage = launcherPackage
            )
        }
    }

    fun dismissAddSteamGameDialog() {
        _state.update {
            it.copy(
                showAddGameDialog = false,
                addGameAppId = "",
                addGameError = null,
                isAddingGame = false,
                selectedLauncherPackage = null
            )
        }
    }

    fun setAddGameAppId(appId: String) {
        _state.update {
            it.copy(addGameAppId = appId, addGameError = null)
        }
    }

    fun confirmAddSteamGame(context: Context, scope: CoroutineScope) {
        val steamState = _state.value
        val appIdStr = steamState.addGameAppId.trim()
        val appId = appIdStr.toLongOrNull()

        if (appId == null || appId <= 0) {
            _state.update {
                it.copy(addGameError = "Please enter a valid Steam App ID")
            }
            return
        }

        val launcherPackage = steamState.selectedLauncherPackage
            ?: SteamLaunchers.getPreferred(context)?.packageName
        if (launcherPackage == null) {
            _state.update {
                it.copy(addGameError = "No Steam launcher installed")
            }
            return
        }

        scope.launch {
            _state.update {
                it.copy(isAddingGame = true, addGameError = null)
            }

            when (val result = steamRepository.addGame(appId, launcherPackage)) {
                is SteamResult.Success -> {
                    notificationManager.show("Added: ${result.data.title}")
                    dismissAddSteamGameDialog()
                    loadSteamSettings(context, scope)
                }
                is SteamResult.Error -> {
                    _state.update {
                        it.copy(
                            isAddingGame = false,
                            addGameError = result.message
                        )
                    }
                }
            }
        }
    }

    fun requestStoragePermission(scope: CoroutineScope) {
        scope.launch {
            _requestStoragePermissionEvent.emit(Unit)
        }
    }
}
