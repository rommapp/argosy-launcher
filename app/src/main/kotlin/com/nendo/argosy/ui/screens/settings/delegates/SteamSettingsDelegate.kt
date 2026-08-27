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
import com.nendo.argosy.data.launcher.GameNativeStoreSync
import com.nendo.argosy.data.launcher.GameNativeSyncFolder
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.launcher.StoreScanResult
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.SteamIgdbResolver
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository
import com.nendo.argosy.data.remote.github.FetchReleaseResult
import com.nendo.argosy.data.repository.SteamRepository
import com.nendo.argosy.data.repository.SteamResult
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.QrAuthState
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.data.steam.SteamLibraryManager
import com.nendo.argosy.data.steam.SteamPathResolver
import com.nendo.argosy.data.steam.SteamService
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
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

class SteamSettingsDelegate @Inject constructor(
    private val steamRepository: SteamRepository,
    private val steamAuthManager: SteamAuthManager,
    private val steamLibraryManager: SteamLibraryManager,
    private val steamContentManager: SteamContentManager,
    private val androidDataAccessor: AndroidDataAccessor,
    private val notificationManager: NotificationManager,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val emulatorUpdateRepository: EmulatorUpdateRepository,
    private val steamIgdbResolver: SteamIgdbResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val steamPathResolver: SteamPathResolver,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val storagePrefs: com.nendo.argosy.data.preferences.StoragePreferencesRepository,
    private val gameNativeStoreSync: GameNativeStoreSync
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

    private val _openStoreSyncDirPicker = MutableSharedFlow<GameNativeSyncFolder>()
    val openStoreSyncDirPicker: SharedFlow<GameNativeSyncFolder> = _openStoreSyncDirPicker.asSharedFlow()

    /**
     * True only when the index actually moved, so a caller can hand an unmoved press back to the
     * global fallback instead of swallowing it.
     */
    fun moveGameNativeActionFocus(delta: Int): Boolean {
        if (_state.value.gameNativeSyncDirs.isEmpty()) return false
        val next = (_state.value.gameNativeActionIndex + delta).coerceIn(0, 1)
        if (next == _state.value.gameNativeActionIndex) return false
        _state.update { it.copy(gameNativeActionIndex = next) }
        return true
    }

    fun openGameNativeFoldersModal() {
        _state.update {
            it.copy(
                showGameNativeFoldersModal = true,
                gameNativeFoldersFocusIndex = 0,
                gameNativeFoldersActionIndex = 0
            )
        }
    }

    fun dismissGameNativeFoldersModal() {
        _state.update { it.copy(showGameNativeFoldersModal = false) }
    }

    fun moveGameNativeFoldersFocus(delta: Int) {
        val folders = GameNativeSyncFolder.entries
        val next = (_state.value.gameNativeFoldersFocusIndex + delta).mod(folders.size)
        _state.update {
            it.copy(
                gameNativeFoldersFocusIndex = next,
                gameNativeFoldersActionIndex = 0
            )
        }
    }

    fun moveGameNativeFoldersActionFocus(delta: Int): Boolean {
        val folder = focusedSyncFolder() ?: return false
        if (_state.value.gameNativeSyncDirs[folder] == null) return false
        val next = (_state.value.gameNativeFoldersActionIndex + delta).coerceIn(0, 1)
        if (next == _state.value.gameNativeFoldersActionIndex) return false
        _state.update { it.copy(gameNativeFoldersActionIndex = next) }
        return true
    }

    fun confirmGameNativeFoldersRow(scope: CoroutineScope) {
        val folder = focusedSyncFolder() ?: return
        if (_state.value.gameNativeFoldersActionIndex == 1) {
            clearStoreSyncDir(scope, folder)
        } else {
            openStoreSyncDirPicker(scope, folder)
        }
    }

    fun openStoreSyncDirPicker(scope: CoroutineScope, folder: GameNativeSyncFolder) {
        focusSyncFolderRow(folder)
        scope.launch { _openStoreSyncDirPicker.emit(folder) }
    }

    /**
     * Records the folder without scanning; running the whole pass is the Scan button's job.
     */
    fun setStoreSyncDir(scope: CoroutineScope, folder: GameNativeSyncFolder, path: String) {
        scope.launch {
            storagePrefs.setGameNativeSyncDir(folder, path)
            _state.update {
                it.copy(
                    gameNativeSyncDirs = it.gameNativeSyncDirs + (folder to path),
                    gameNativeMissingDirs = it.gameNativeMissingDirs - folder
                )
            }
        }
    }

    fun clearStoreSyncDir(scope: CoroutineScope, folder: GameNativeSyncFolder) {
        focusSyncFolderRow(folder)
        scope.launch {
            storagePrefs.setGameNativeSyncDir(folder, null)
            _state.update {
                val remaining = it.gameNativeSyncDirs - folder
                it.copy(
                    gameNativeSyncDirs = remaining,
                    gameNativeMissingDirs = it.gameNativeMissingDirs - folder,
                    gameNativeFoldersActionIndex = 0,
                    gameNativeActionIndex = if (remaining.isEmpty()) 0 else it.gameNativeActionIndex
                )
            }
        }
    }

    private fun focusSyncFolderRow(folder: GameNativeSyncFolder) {
        _state.update {
            it.copy(
                gameNativeFoldersFocusIndex = folder.ordinal,
                gameNativeFoldersActionIndex = 0
            )
        }
    }

    fun rescanStoreSync(scope: CoroutineScope) {
        if (_state.value.isGameNativeScanning) return
        scope.launch {
            _state.update { it.copy(isGameNativeScanning = true) }
            try {
                val summary = withContext(Dispatchers.IO) { gameNativeStoreSync.scan() }
                if (!summary.configured) {
                    notificationManager.showError(NotificationText.Res(R.string.notif_steam_settings_gamenative_unconfigured))
                    return@launch
                }
                _state.update { it.copy(gameNativeMissingDirs = missingFolders(summary)) }
                notificationManager.show(
                    title = NotificationText.Res(R.string.notif_steam_settings_gamenative_scan_title),
                    subtitle = NotificationText.Raw(formatScanSummary(summary))
                )
            } finally {
                _state.update { it.copy(isGameNativeScanning = false) }
            }
        }
    }

    private fun focusedSyncFolder(): GameNativeSyncFolder? =
        GameNativeSyncFolder.entries.getOrNull(_state.value.gameNativeFoldersFocusIndex)

    private fun missingFolders(summary: GameNativeStoreSync.ScanSummary): Set<GameNativeSyncFolder> =
        summary.results.filterValues { it is StoreScanResult.FolderMissing }.keys

    private fun formatScanSummary(summary: GameNativeStoreSync.ScanSummary): String =
        summary.results.entries.joinToString(", ") { (folder, result) ->
            when (result) {
                is StoreScanResult.FolderMissing -> "${folder.displayName}: folder missing"
                is StoreScanResult.Library ->
                    "${folder.displayName}: ${result.markers} markers, " +
                        "${result.added} added, ${result.removed} removed"
                is StoreScanResult.InstallState ->
                    "${folder.displayName}: ${result.markers} markers, ${result.matched} matched, " +
                        "${result.notInLibrary} not in library"
            }
        }
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
            val gnStoragePath = withContext(Dispatchers.IO) { steamPathResolver.findGnStoragePath() }
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true

            val installedLaunchers = SteamLaunchers.getInstalled(context).map { launcher ->
                InstalledSteamLauncher(
                    packageName = launcher.packageName,
                    displayName = launcher.displayName,
                    gameCount = 0,
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
            val storeSyncDirs = withContext(Dispatchers.IO) {
                storagePrefs.migrateLegacyGameNativeSyncDir()
                storagePrefs.preferences.first().gameNativeSyncDirs
            }
            val missingSyncDirs = withContext(Dispatchers.IO) {
                storeSyncDirs.filterValues { !File(it).isDirectory }.keys
            }
            val volumes = withContext(Dispatchers.IO) { steamPathResolver.getAvailableVolumes() }
            val installedByVolume = withContext(Dispatchers.IO) { loadInstalledSteamSummary(volumes, context) }
            val resolvedSteamPath = withContext(Dispatchers.IO) { steamPathResolver.getResolvedSteamBase() }
            val steamPlatform = withContext(Dispatchers.IO) {
                platformRepository.getById(com.nendo.argosy.data.platform.LocalPlatformIds.STEAM)
            }
            val customRomPath = steamPlatform?.customRomPath

            _state.update {
                it.copy(
                    gnInstalled = gnInstalled,
                    gnStoragePath = gnStoragePath,
                    hasStoragePermission = hasPermission,
                    installedLaunchers = installedLaunchers,
                    notInstalledLaunchers = notInstalledLaunchers,
                    steamInstallVolume = prefs.steamInstallVolume,
                    availableVolumes = volumes,
                    installedGamesByVolume = installedByVolume,
                    steamInstallPath = resolvedSteamPath,
                    steamInstallPathIsCustom = !customRomPath.isNullOrBlank(),
                    gameNativeSyncDirs = storeSyncDirs,
                    gameNativeMissingDirs = missingSyncDirs,
                    gameNativeActionIndex = if (storeSyncDirs.isEmpty()) 0 else it.gameNativeActionIndex
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

            if (gnStoragePath != null) {
                withContext(Dispatchers.IO) { steamContentManager.discoverLocalSteamGames() }
            }

            if (!bound) {
                tryBindService(context)
            }
        }
    }

    fun cycleSteamInstallVolume(scope: CoroutineScope, direction: Int = 1) {
        val current = _state.value.steamInstallVolume
        val values = listOf<String?>(null) + _state.value.availableVolumes.map { it.target.toPreferenceValue() }
        if (values.size <= 1) return

        val currentIndex = values.indexOf(current).coerceAtLeast(0)
        val step = if (direction >= 0) 1 else -1
        val nextIndex = ((currentIndex + step) % values.size + values.size) % values.size
        val nextVolume = values[nextIndex]

        scope.launch {
            val active = steamContentManager.hasActiveSteamDownload()
            val queued = steamContentManager.downloadQueue.value.size
            preferencesRepository.setSteamInstallVolume(nextVolume)
            _state.update { it.copy(steamInstallVolume = nextVolume) }
            val inFlight = (if (active) 1 else 0) + queued
            if (inFlight > 0) {
                notificationManager.show(
                    title = NotificationText.Res(R.string.notif_steam_settings_install_path_changed_title),
                    subtitle = NotificationText.Plural(
                        R.plurals.notif_steam_settings_install_path_changed_subtitle,
                        inFlight,
                        listOf(inFlight)
                    )
                )
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
                    notificationManager.showError(
                        NotificationText.Res(R.string.notif_steam_settings_launcher_download_failed, listOf(result.message))
                    )
                }
            }
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
                    _state.update {
                        it.copy(
                            syncState = LibrarySyncState.Error(
                                messageRes = R.string.notif_steam_sync_error_could_not_connect_sync,
                                message = context.getString(R.string.notif_steam_sync_error_could_not_connect_sync)
                            )
                        )
                    }
                    return@launch
                }
            }

            steamLibraryManager.forceSync()
        }
    }

    fun forceSyncLibraryWithOverwrite(context: Context, scope: CoroutineScope) {
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
                    _state.update {
                        it.copy(
                            syncState = LibrarySyncState.Error(
                                messageRes = R.string.notif_steam_sync_error_could_not_connect_overwrite,
                                message = context.getString(R.string.notif_steam_sync_error_could_not_connect_overwrite)
                            )
                        )
                    }
                    return@launch
                }
            }

            steamLibraryManager.forceSyncWithOverwrite()
        }
    }

    fun disconnectSteam(context: Context, scope: CoroutineScope) {
        scope.launch {
            if (steamContentManager.hasActiveSteamDownload()) {
                steamContentManager.cancelDownload()
            }
            for (queued in steamContentManager.downloadQueue.value) {
                steamContentManager.cancelQueuedDownload(queued.appId)
            }
            serviceRef?.disconnect()
            steamAuthManager.logout()
            unbindService(context)
            context.stopService(Intent(context, SteamService::class.java))
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
            notificationManager.show(NotificationText.Res(R.string.notif_steam_settings_library_reset, listOf(count)))
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

    /**
     * Adds a Steam game by App ID under a specific managing launcher. Called from each
     * per-launcher Settings section so the resulting GameEntity is linked to that
     * launcher (e.g. GameNative's section passes GameNativeLauncher.packageName). The
     * launcher string is stored in GameEntity.steamLauncher and drives the launch
     * intent as well as the Unlink-from-X affordance.
     */
    fun confirmAddSteamGame(
        context: Context,
        scope: CoroutineScope,
        launcherPackage: String = com.nendo.argosy.data.launcher.GameNativeLauncher.packageName
    ) {
        val appIdStr = _state.value.addGameAppId.trim()
        val appId = appIdStr.toLongOrNull()

        if (appId == null || appId <= 0) {
            _state.update {
                it.copy(addGameError = context.getString(R.string.settings_steam_delegate_invalid_app_id))
            }
            return
        }

        scope.launch {
            _state.update { it.copy(isAddingGame = true, addGameError = null) }

            when (val result = steamRepository.addGame(appId, launcherPackage)) {
                is SteamResult.Success -> {
                    notificationManager.show(
                        NotificationText.Res(R.string.notif_steam_settings_game_added, listOf(result.data.title))
                    )
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
                    notificationManager.show(
                        NotificationText.Res(R.string.notif_steam_settings_library_synced, listOf(syncState.gamesAdded))
                    )
                } else if (syncState is LibrarySyncState.Error) {
                    val text = syncState.messageRes?.let { NotificationText.Res(it) }
                        ?: NotificationText.Raw(syncState.message)
                    notificationManager.showError(text)
                }
            }
        }
    }

    private suspend fun loadInstalledSteamSummary(
        volumes: List<com.nendo.argosy.data.steam.SteamInstallVolume>,
        context: Context
    ): Map<String, Int> {
        val games = gameRepository.getInstalledSteamGames()
        if (games.isEmpty()) return emptyMap()
        val buckets = linkedMapOf<String, Int>()
        val volumesByPath = volumes.sortedByDescending { it.path.length }
        val otherLabel = context.getString(R.string.settings_steam_delegate_volume_other_label)
        for (game in games) {
            val path = game.localPath ?: continue
            val label = volumesByPath.firstOrNull { path.startsWith(it.path) }?.label ?: otherLabel
            buckets[label] = (buckets[label] ?: 0) + 1
        }
        return buckets
    }

    private fun isGnInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GN_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

}
