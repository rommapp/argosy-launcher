package com.nendo.argosy.ui.screens.settings.delegates

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.repository.SteamIgdbResolver
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository
import com.nendo.argosy.data.remote.github.FetchReleaseResult
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.QrAuthState
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.data.steam.SteamLibraryManager
import com.nendo.argosy.data.steam.SteamService
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

private const val GN_PACKAGE = "app.gamenative"
private const val GN_STEAM_SUBPATH = "Android/data/$GN_PACKAGE/files/Steam/steamapps"

class SteamSettingsDelegate @Inject constructor(
    private val steamRepository: SteamRepository,
    private val steamAuthManager: SteamAuthManager,
    private val steamLibraryManager: SteamLibraryManager,
    private val androidDataAccessor: AndroidDataAccessor,
    private val notificationManager: NotificationManager,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val emulatorUpdateRepository: EmulatorUpdateRepository,
    private val steamIgdbResolver: SteamIgdbResolver
) {
    private val _state = MutableStateFlow(SteamSettingsState())
    val state: StateFlow<SteamSettingsState> = _state.asStateFlow()

    // Legacy stubs for routers that still reference these
    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()
    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()
    val downloadProgress = flowOf<com.nendo.argosy.data.emulator.EmulatorDownloadProgress?>(null)

    private var serviceRef: SteamService? = null
    private var observingService = false
    private var bound = false
    private var bindScope: CoroutineScope? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? SteamService.LocalBinder)?.getService() ?: return
            bindService(service, bindScope ?: return)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceRef = null
            observingService = false
        }
    }

    fun updateState(newState: SteamSettingsState) {
        _state.value = newState
    }

    fun bindService(service: SteamService, scope: CoroutineScope) {
        serviceRef = service
        if (!observingService) {
            observingService = true
            observeServiceState(scope)
            observeAuthState(scope)
            observeSyncState(scope)
        }
    }

    fun loadSteamSettings(context: Context, scope: CoroutineScope) {
        bindScope = scope
        scope.launch {
            val gnInstalled = isGnInstalled(context)
            val gnStoragePath = withContext(Dispatchers.IO) { findGnStoragePath() }
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true

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
                        hasDirectDownload = def.releaseSource != null
                    )
                }

            _state.update {
                it.copy(
                    gnInstalled = gnInstalled,
                    gnStoragePath = gnStoragePath,
                    hasStoragePermission = hasPermission,
                    installedLaunchers = installedLaunchers,
                    notInstalledLaunchers = notInstalledLaunchers
                )
            }

            // Auto-bind if service is already running
            if (!bound) {
                tryBindService(context)
            }
        }
    }

    fun installSteamLauncher(emulatorId: String, scope: CoroutineScope) {
        val def = EmulatorRegistry.getById(emulatorId) ?: return

        if (def.releaseSource == null) {
            scope.launch { def.downloadUrl?.let { _openUrlEvent.emit(it) } }
            return
        }

        if (!emulatorDownloadManager.canInstallPackages()) {
            emulatorDownloadManager.openInstallPermissionSettings()
            return
        }

        scope.launch {
            _state.update { it.copy(downloadingLauncherId = emulatorId, downloadProgress = 0f) }

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
                            variantPickerInfo = com.nendo.argosy.ui.screens.settings.VariantPickerInfo(
                                emulatorId = emulatorId,
                                emulatorName = def.displayName,
                                variants = result.variants.map { v ->
                                    com.nendo.argosy.ui.screens.settings.VariantOption(
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
                    _state.update { it.copy(downloadingLauncherId = null, downloadProgress = null) }
                    notificationManager.showError("Download failed: ${result.message}")
                }
            }
        }
    }

    fun scanSteamLauncher(context: Context, scope: CoroutineScope, packageName: String) {
        val launcher = _state.value.installedLaunchers.find { it.packageName == packageName } ?: return
        val steamLauncher = SteamLaunchers.getByPackage(packageName)

        scope.launch {
            _state.update { it.copy(isSyncing = true, syncingLauncher = packageName) }
            notificationManager.show("Scanning ${launcher.displayName}...")

            val scannedGames = if (steamLauncher?.supportsScanning == true) {
                withContext(Dispatchers.IO) { steamLauncher.scan(context) }
            } else {
                emptyList()
            }

            if (scannedGames.isEmpty()) {
                _state.update { it.copy(isSyncing = false, syncingLauncher = null) }
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

            steamIgdbResolver.requestResolutionForUnresolved()

            _state.update { it.copy(isSyncing = false, syncingLauncher = null) }

            val message = when {
                addedCount > 0 && skippedCount > 0 -> "Added $addedCount games, $skippedCount already existed"
                addedCount > 0 -> "Added $addedCount games"
                else -> "All ${scannedGames.size} games already in library"
            }
            notificationManager.show(message)
            loadSteamSettings(context, scope)
        }
    }

    private var pendingQrAuth = false

    fun connectToSteam(context: Context) {
        pendingQrAuth = true
        val intent = Intent(context, SteamService::class.java).apply {
            putExtra(SteamService.EXTRA_CONNECT_FOR_AUTH, true)
        }
        context.startService(intent)
        tryBindService(context)
    }

    private fun tryBindService(context: Context) {
        if (bound) return
        val intent = Intent(context, SteamService::class.java)
        context.startService(intent)
        bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }
    }

    fun startQrAuth() {
        steamAuthManager.startQrAuth()
    }

    fun cancelQrAuth() {
        steamAuthManager.cancelQrAuth()
    }

    fun syncLibrary() {
        steamLibraryManager.requestLibrarySync()
    }

    fun disconnectSteam(scope: CoroutineScope) {
        scope.launch {
            steamAuthManager.logout()
            _state.update {
                it.copy(
                    connectionState = SteamConnectionState.DISCONNECTED,
                    username = null,
                    qrUrl = null,
                    error = null
                )
            }
        }
    }

    fun resetLibrary(scope: CoroutineScope) {
        scope.launch {
            val count = steamLibraryManager.resetLibrary()
            notificationManager.show("Removed $count Steam games")
        }
    }

    fun showAddSteamGameDialog() {
        _state.update {
            it.copy(
                showAddGameDialog = true,
                addGameAppId = "",
                addGameError = null,
                isAddingGame = false
            )
        }
    }

    fun dismissAddSteamGameDialog() {
        _state.update {
            it.copy(
                showAddGameDialog = false,
                addGameAppId = "",
                addGameError = null,
                isAddingGame = false
            )
        }
    }

    fun setAddGameAppId(appId: String) {
        _state.update { it.copy(addGameAppId = appId, addGameError = null) }
    }

    fun confirmAddSteamGame(context: Context, scope: CoroutineScope) {
        val appIdStr = _state.value.addGameAppId.trim()
        val appId = appIdStr.toLongOrNull()

        if (appId == null || appId <= 0) {
            _state.update { it.copy(addGameError = "Please enter a valid Steam App ID") }
            return
        }

        scope.launch {
            _state.update { it.copy(isAddingGame = true, addGameError = null) }

            when (val result = steamRepository.addGame(appId, "native")) {
                is SteamResult.Success -> {
                    notificationManager.show("Added: ${result.data.title}")
                    dismissAddSteamGameDialog()
                }
                is SteamResult.Error -> {
                    _state.update {
                        it.copy(isAddingGame = false, addGameError = result.message)
                    }
                }
            }
        }
    }

    private fun observeServiceState(scope: CoroutineScope) {
        scope.launch {
            serviceRef?.state?.collect { serviceState ->
                _state.update {
                    it.copy(
                        connectionState = serviceState.connectionState,
                        username = serviceState.username ?: it.username,
                        error = serviceState.error
                    )
                }

                if (serviceState.connectionState == SteamConnectionState.CONNECTED && pendingQrAuth) {
                    pendingQrAuth = false
                    steamAuthManager.startQrAuth()
                }
            }
        }
    }

    private fun observeAuthState(scope: CoroutineScope) {
        scope.launch {
            steamAuthManager.qrAuthState.collect { authState ->
                _state.update {
                    when (authState) {
                        is QrAuthState.Idle -> it.copy(qrUrl = null, authPolling = false)
                        is QrAuthState.Starting -> it.copy(qrUrl = null, authPolling = true)
                        is QrAuthState.WaitingForScan -> it.copy(
                            qrUrl = authState.challengeUrl,
                            authPolling = true
                        )
                        is QrAuthState.Polling -> it.copy(authPolling = true)
                        is QrAuthState.Success -> it.copy(
                            username = authState.username,
                            qrUrl = null,
                            authPolling = false
                        )
                        is QrAuthState.Error -> it.copy(
                            error = authState.message,
                            qrUrl = null,
                            authPolling = false
                        )
                    }
                }
            }
        }
    }

    private fun observeSyncState(scope: CoroutineScope) {
        scope.launch {
            steamLibraryManager.syncState.collect { syncState ->
                _state.update { it.copy(syncState = syncState) }

                if (syncState is LibrarySyncState.Complete) {
                    notificationManager.show(
                        "Added ${syncState.gamesAdded}, updated ${syncState.gamesUpdated} games"
                    )
                } else if (syncState is LibrarySyncState.Error) {
                    notificationManager.showError(syncState.message)
                }
            }
        }
    }

    private fun isGnInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GN_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun findGnStoragePath(): String? {
        val volumes = mutableListOf<String>()

        // Check internal storage
        val internal = android.os.Environment.getExternalStorageDirectory().absolutePath
        volumes.add(internal)

        // Check SD card / removable volumes
        try {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name != "emulated" && vol.name != "self") {
                    volumes.add(vol.absolutePath)
                }
            }
        } catch (_: Exception) {}

        for (root in volumes) {
            val path = "$root/$GN_STEAM_SUBPATH"
            if (androidDataAccessor.exists(path) || File(path).exists()) {
                return "$root/Android/data/$GN_PACKAGE/files"
            }
        }
        return null
    }
}
