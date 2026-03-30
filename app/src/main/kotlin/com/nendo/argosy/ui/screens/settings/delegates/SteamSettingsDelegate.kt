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
import com.nendo.argosy.data.preferences.UserPreferencesRepository
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
import com.nendo.argosy.data.steam.SteamPathResolver
import com.nendo.argosy.data.steam.SteamService
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    private val steamIgdbResolver: SteamIgdbResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamPathResolver: SteamPathResolver
) {
    private val _state = MutableStateFlow(SteamSettingsState())
    val state: StateFlow<SteamSettingsState> = _state.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val account = steamAuthManager.getActiveAccount()
            if (account != null) {
                _state.update {
                    it.copy(
                        connectionState = SteamConnectionState.LOGGED_IN,
                        username = account.username
                    )
                }
            }
        }
    }

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
    private var pendingSync = false

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

            val prefs = preferencesRepository.userPreferences.first()
            val volumes = withContext(Dispatchers.IO) { steamPathResolver.getAvailableVolumes() }

            _state.update {
                it.copy(
                    gnInstalled = gnInstalled,
                    gnStoragePath = gnStoragePath,
                    hasStoragePermission = hasPermission,
                    installedLaunchers = installedLaunchers,
                    notInstalledLaunchers = notInstalledLaunchers,
                    steamInstallVolume = prefs.steamInstallVolume,
                    availableVolumes = volumes
                )
            }

            val savedAccount = withContext(Dispatchers.IO) {
                steamAuthManager.getActiveAccount()
            }
            if (savedAccount != null) {
                _state.update {
                    it.copy(
                        connectionState = SteamConnectionState.LOGGED_IN,
                        username = savedAccount.username
                    )
                }
            }

            if (!bound) {
                tryBindService(context)
            }
        }
    }

    fun cycleSteamInstallVolume(scope: CoroutineScope) {
        val current = _state.value.steamInstallVolume
        val volumes = _state.value.availableVolumes.filter { it.hasGnPath }
        if (volumes.isEmpty()) return

        val paths = listOf<String?>(null) + volumes.map { it.path }
        val currentIndex = paths.indexOf(current)
        val nextIndex = (currentIndex + 1) % paths.size
        val nextVolume = paths[nextIndex]

        scope.launch {
            preferencesRepository.setSteamInstallVolume(nextVolume)
            _state.update { it.copy(steamInstallVolume = nextVolume) }
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
                withContext(Dispatchers.IO) { steamLauncher.scan(context, androidDataAccessor) }
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

    fun connectToSteam(context: Context, scope: CoroutineScope) {
        bindScope = scope
        pendingQrAuth = true
        // Optimistically move to CONNECTING so the UI shows a spinner
        // immediately rather than flickering back to the connect button
        // while the service starts up.
        _state.update {
            it.copy(
                connectionState = SteamConnectionState.CONNECTING,
                error = null
            )
        }
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
        // If connectToSteam was called, pendingQrAuth is already set and
        // observeServiceState will trigger startQrAuth once the client is
        // CONNECTED.  Calling steamAuthManager.startQrAuth() before the
        // client exists produces QrAuthState.Error which flashes the error
        // screen during a normal connect flow.
        if (pendingQrAuth) return
        steamAuthManager.startQrAuth()
    }

    fun cancelQrAuth() {
        pendingQrAuth = false
        steamAuthManager.cancelQrAuth()
    }

    fun syncLibrary(context: Context, scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(syncState = LibrarySyncState.SyncingLicenses) }

            val service = serviceRef
            val isConnected = service?.state?.value?.connectionState == SteamConnectionState.LOGGED_IN

            if (!isConnected) {
                pendingSync = true
                val intent = Intent(context, SteamService::class.java).apply {
                    putExtra(SteamService.EXTRA_FORCE_CONNECT, true)
                }
                context.startService(intent)
                if (!bound) tryBindService(context)

                val deadline = System.currentTimeMillis() + 30_000L
                while (System.currentTimeMillis() < deadline) {
                    val current = serviceRef?.state?.value?.connectionState
                    if (current == SteamConnectionState.LOGGED_IN) break
                    kotlinx.coroutines.delay(500)
                }
                pendingSync = false

                val finalState = serviceRef?.state?.value?.connectionState
                if (finalState != SteamConnectionState.LOGGED_IN) {
                    _state.update { it.copy(syncState = LibrarySyncState.Error("Could not connect to Steam")) }
                    return@launch
                }
            }

            steamLibraryManager.forceSync()
        }
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
                    // Suppress connection state changes that would flash the login screen:
                    // 1. During QR auth flow (old client teardown)
                    // 2. When we have a saved account and service is still connecting
                    // 3. During sync-initiated reconnect (hold LOGGED_IN until complete)
                    val suppressState = pendingQrAuth || pendingSync ||
                        (serviceState.connectionState == SteamConnectionState.DISCONNECTED &&
                            it.connectionState == SteamConnectionState.LOGGED_IN)

                    val effectiveConnection = if (suppressState)
                        it.connectionState
                    else
                        serviceState.connectionState

                    val showError = !suppressState &&
                        serviceState.error != null &&
                        (it.authPolling || it.qrUrl != null)

                    it.copy(
                        connectionState = effectiveConnection,
                        username = serviceState.username ?: it.username,
                        error = if (showError) serviceState.error else null
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
                        is QrAuthState.Idle -> it.copy(qrUrl = null, authPolling = false, error = null)
                        is QrAuthState.Starting -> it.copy(qrUrl = null, authPolling = true, error = null)
                        is QrAuthState.WaitingForScan -> it.copy(
                            qrUrl = authState.challengeUrl,
                            authPolling = true,
                            error = null
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

                if (syncState is LibrarySyncState.Complete && syncState.gamesAdded > 0) {
                    notificationManager.show("Added ${syncState.gamesAdded} Steam games")
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
