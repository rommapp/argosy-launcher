package com.nendo.argosy.ui.screens.settings.delegates

import android.os.Build
import android.os.Environment
import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.ManagedStorageAccessor
import com.nendo.argosy.domain.usecase.MigratePlatformStorageUseCase
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.domain.usecase.PurgePlatformUseCase
import com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.nendo.argosy.ui.screens.settings.EmulatorSavePathInfo
import com.nendo.argosy.ui.screens.settings.PlatformMigrationInfo
import com.nendo.argosy.ui.screens.settings.PlatformStorageConfig
import com.nendo.argosy.ui.screens.settings.StorageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "StorageSettingsDelegate"

class StorageSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val migrateStorageUseCase: MigrateStorageUseCase,
    private val migratePlatformStorageUseCase: MigratePlatformStorageUseCase,
    private val purgePlatformUseCase: PurgePlatformUseCase,
    private val syncPlatformUseCase: SyncPlatformUseCase,
    private val database: ALauncherDatabase,
    private val imageCacheManager: ImageCacheManager,
    private val managedStorageAccessor: ManagedStorageAccessor
) {
    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()

    private val _launchFolderPicker = MutableStateFlow(false)
    val launchFolderPicker: StateFlow<Boolean> = _launchFolderPicker.asStateFlow()

    private val _launchSavePathPicker = MutableSharedFlow<Long>()
    val launchSavePathPicker: SharedFlow<Long> = _launchSavePathPicker.asSharedFlow()

    private val _resetSavePathEvent = MutableSharedFlow<Long>()
    val resetSavePathEvent: SharedFlow<Long> = _resetSavePathEvent.asSharedFlow()

    private val _launchStatePathPicker = MutableSharedFlow<Long>()
    val launchStatePathPicker: SharedFlow<Long> = _launchStatePathPicker.asSharedFlow()

    private val _resetStatePathEvent = MutableSharedFlow<Long>()
    val resetStatePathEvent: SharedFlow<Long> = _resetStatePathEvent.asSharedFlow()

    private val _showMigrationDialog = MutableStateFlow(false)
    val showMigrationDialog: StateFlow<Boolean> = _showMigrationDialog.asStateFlow()

    private val _pendingStoragePath = MutableStateFlow<String?>(null)
    val pendingStoragePath: StateFlow<String?> = _pendingStoragePath.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating.asStateFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    fun updateState(newState: StorageState) {
        _state.value = newState
    }

    fun checkAllFilesAccess() {
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        _state.update { it.copy(hasAllFilesAccess = hasAccess) }
    }

    fun requestAllFilesAccess(scope: CoroutineScope) {
        scope.launch {
            _requestStoragePermissionEvent.emit(Unit)
        }
    }

    fun cycleMaxConcurrentDownloads(scope: CoroutineScope) {
        scope.launch {
            val current = _state.value.maxConcurrentDownloads
            val next = if (current >= 5) 1 else current + 1
            preferencesRepository.setMaxConcurrentDownloads(next)
            _state.update { it.copy(maxConcurrentDownloads = next) }
        }
    }

    fun adjustMaxConcurrentDownloads(scope: CoroutineScope, delta: Int) {
        scope.launch {
            val current = _state.value.maxConcurrentDownloads
            val next = (current + delta).coerceIn(1, 5)
            if (next != current) {
                preferencesRepository.setMaxConcurrentDownloads(next)
                _state.update { it.copy(maxConcurrentDownloads = next) }
            }
        }
    }

    fun cycleInstantDownloadThreshold(scope: CoroutineScope) {
        scope.launch {
            val thresholds = listOf(50, 100, 250, 500)
            val current = _state.value.instantDownloadThresholdMb
            val currentIndex = thresholds.indexOf(current).coerceAtLeast(0)
            val next = thresholds[(currentIndex + 1).mod(thresholds.size)]
            preferencesRepository.setInstantDownloadThresholdMb(next)
            _state.update { it.copy(instantDownloadThresholdMb = next) }
        }
    }

    fun openFolderPicker() {
        _launchFolderPicker.value = true
    }

    fun clearFolderPickerFlag() {
        _launchFolderPicker.value = false
    }

    fun setStoragePath(uriString: String) {
        val currentState = _state.value
        if (currentState.downloadedGamesCount > 0 && currentState.romStoragePath.isNotBlank()) {
            _showMigrationDialog.value = true
            _pendingStoragePath.value = uriString
        } else {
            applyStoragePath(uriString)
        }
    }

    fun confirmMigration(scope: CoroutineScope) {
        val pendingPath = _pendingStoragePath.value ?: return
        _showMigrationDialog.value = false
        migrateDownloads(scope, pendingPath)
    }

    fun cancelMigration() {
        _showMigrationDialog.value = false
        _pendingStoragePath.value = null
    }

    fun skipMigration() {
        val pendingPath = _pendingStoragePath.value ?: return
        _showMigrationDialog.value = false
        applyStoragePath(pendingPath)
    }

    private fun applyStoragePath(uriString: String, scope: CoroutineScope? = null) {
        val applyScope = scope ?: kotlinx.coroutines.GlobalScope
        applyScope.launch {
            preferencesRepository.setRomStoragePath(uriString)
            val availableSpace = gameRepository.getAvailableStorageBytes()
            _state.update {
                it.copy(
                    romStoragePath = uriString,
                    availableSpace = availableSpace
                )
            }
            _pendingStoragePath.value = null
        }
    }

    private fun migrateDownloads(scope: CoroutineScope, newPath: String) {
        scope.launch {
            val oldPath = _state.value.romStoragePath
            _isMigrating.value = true
            _pendingStoragePath.value = null

            migrateStorageUseCase(oldPath, newPath)

            _state.update { it.copy(romStoragePath = newPath) }
            _isMigrating.value = false
            refreshCollectionStats(scope)
        }
    }

    fun refreshCollectionStats(scope: CoroutineScope) {
        scope.launch {
            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()
            _state.update {
                it.copy(
                    downloadedGamesSize = downloadedSize,
                    downloadedGamesCount = downloadedCount
                )
            }
        }
    }

    fun setRomStoragePath(scope: CoroutineScope, path: String) {
        scope.launch {
            preferencesRepository.setRomStoragePath(path)
            _state.update { it.copy(romStoragePath = path) }
        }
    }

    private val _launchPlatformFolderPicker = MutableSharedFlow<Long>()
    val launchPlatformFolderPicker: SharedFlow<Long> = _launchPlatformFolderPicker.asSharedFlow()

    private var pendingPlatformId: Long? = null

    private var pendingEmulatorInfo: Map<Long, PlatformEmulatorInfo>? = null

    fun loadPlatformConfigs(scope: CoroutineScope) {
        scope.launch {
            val globalPath = _state.value.romStoragePath
            val platforms = platformDao.observeConfigurablePlatforms().first()
            val emulatorInfo = pendingEmulatorInfo

            val configs = platforms.map { platform ->
                val effectivePath = platform.customRomPath ?: "$globalPath/${platform.slug}"
                val downloadedCount = gameDao.countDownloadedByPlatform(platform.id)
                val info = emulatorInfo?.get(platform.id)
                PlatformStorageConfig(
                    platformId = platform.id,
                    platformName = platform.name,
                    gameCount = platform.gameCount,
                    downloadedCount = downloadedCount,
                    syncEnabled = platform.syncEnabled,
                    customRomPath = platform.customRomPath,
                    effectivePath = effectivePath,
                    supportsStatePath = info?.supportsStatePath ?: false,
                    emulatorId = info?.emulatorId,
                    effectiveSavePath = info?.effectiveSavePath,
                    isUserSavePathOverride = info?.isUserSavePathOverride ?: false,
                    effectiveStatePath = info?.effectiveStatePath,
                    isUserStatePathOverride = info?.isUserStatePathOverride ?: false
                )
            }

            _state.update { it.copy(platformConfigs = configs) }
        }
    }

    fun setPendingEmulatorInfo(infoMap: Map<Long, PlatformEmulatorInfo>) {
        pendingEmulatorInfo = infoMap
    }

    fun updatePlatformSavePath(platformId: Long, savePath: String?, isUserOverride: Boolean) {
        _state.update { current ->
            current.copy(
                platformConfigs = current.platformConfigs.map { config ->
                    if (config.platformId == platformId) {
                        config.copy(
                            effectiveSavePath = savePath,
                            isUserSavePathOverride = isUserOverride
                        )
                    } else config
                },
                platformSettingsButtonIndex = if (!isUserOverride) 0 else current.platformSettingsButtonIndex
            )
        }
    }

    fun updatePlatformStatePath(platformId: Long, statePath: String?, isUserOverride: Boolean) {
        _state.update { current ->
            current.copy(
                platformConfigs = current.platformConfigs.map { config ->
                    if (config.platformId == platformId) {
                        config.copy(
                            effectiveStatePath = statePath,
                            isUserStatePathOverride = isUserOverride
                        )
                    } else config
                },
                platformSettingsButtonIndex = if (!isUserOverride) 0 else current.platformSettingsButtonIndex
            )
        }
    }

    fun togglePlatformSync(scope: CoroutineScope, platformId: Long, enabled: Boolean) {
        scope.launch {
            platformDao.updateSyncEnabled(platformId, enabled)
            updatePlatformConfigInState(platformId) { it.copy(syncEnabled = enabled) }
        }
    }

    private fun updatePlatformConfigInState(
        platformId: Long,
        update: (PlatformStorageConfig) -> PlatformStorageConfig
    ) {
        _state.update { current ->
            current.copy(
                platformConfigs = current.platformConfigs.map { config ->
                    if (config.platformId == platformId) update(config) else config
                }
            )
        }
    }

    fun openPlatformFolderPicker(scope: CoroutineScope, platformId: Long) {
        pendingPlatformId = platformId
        scope.launch {
            _launchPlatformFolderPicker.emit(platformId)
        }
    }

    fun setPlatformPath(scope: CoroutineScope, platformId: Long, newPath: String) {
        scope.launch {
            val platform = platformDao.getById(platformId) ?: return@launch
            val globalPath = _state.value.romStoragePath
            val oldPath = platform.customRomPath ?: "$globalPath/${platform.slug}"

            val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
            if (gamesWithPaths.isNotEmpty()) {
                _state.update {
                    it.copy(
                        showMigratePlatformConfirm = PlatformMigrationInfo(
                            platformId = platformId,
                            platformName = platform.name,
                            oldPath = oldPath,
                            newPath = newPath,
                            isResetToGlobal = false
                        )
                    )
                }
            } else {
                platformDao.updateCustomRomPath(platformId, newPath)
                updatePlatformConfigInState(platformId) {
                    it.copy(customRomPath = newPath, effectivePath = newPath)
                }
            }
        }
        pendingPlatformId = null
    }

    fun resetPlatformToGlobal(scope: CoroutineScope, platformId: Long) {
        scope.launch {
            val platform = platformDao.getById(platformId) ?: return@launch
            val customPath = platform.customRomPath ?: return@launch
            val globalPath = _state.value.romStoragePath
            val newPath = "$globalPath/${platform.slug}"

            val gamesWithPaths = gameRepository.getGamesWithLocalPathsForPlatform(platformId)
            if (gamesWithPaths.isNotEmpty()) {
                _state.update {
                    it.copy(
                        showMigratePlatformConfirm = PlatformMigrationInfo(
                            platformId = platformId,
                            platformName = platform.name,
                            oldPath = customPath,
                            newPath = newPath,
                            isResetToGlobal = true
                        )
                    )
                }
            } else {
                platformDao.updateCustomRomPath(platformId, null)
                updatePlatformConfigInState(platformId) {
                    it.copy(customRomPath = null, effectivePath = newPath)
                }
            }
        }
    }

    fun confirmPlatformMigration(scope: CoroutineScope) {
        val info = _state.value.showMigratePlatformConfirm ?: return
        _state.update { it.copy(showMigratePlatformConfirm = null) }

        scope.launch {
            migratePlatformStorageUseCase(
                platformId = info.platformId,
                oldPath = info.oldPath,
                newPath = info.newPath,
                isResetToGlobal = info.isResetToGlobal
            )
            loadPlatformConfigs(scope)
            refreshCollectionStats(scope)
        }
    }

    fun cancelPlatformMigration() {
        _state.update { it.copy(showMigratePlatformConfirm = null) }
    }

    fun skipPlatformMigration(scope: CoroutineScope) {
        val info = _state.value.showMigratePlatformConfirm ?: return
        _state.update { it.copy(showMigratePlatformConfirm = null) }

        scope.launch {
            if (info.isResetToGlobal) {
                platformDao.updateCustomRomPath(info.platformId, null)
                updatePlatformConfigInState(info.platformId) {
                    it.copy(customRomPath = null, effectivePath = info.newPath)
                }
            } else {
                platformDao.updateCustomRomPath(info.platformId, info.newPath)
                updatePlatformConfigInState(info.platformId) {
                    it.copy(customRomPath = info.newPath, effectivePath = info.newPath)
                }
            }
        }
    }

    fun requestPurgePlatform(platformId: Long) {
        _state.update { it.copy(showPurgePlatformConfirm = platformId) }
    }

    fun confirmPurgePlatform(scope: CoroutineScope) {
        val platformId = _state.value.showPurgePlatformConfirm ?: return
        _state.update { it.copy(showPurgePlatformConfirm = null) }

        scope.launch {
            purgePlatformUseCase(platformId, deleteLocalFiles = true)
            loadPlatformConfigs(scope)
            refreshCollectionStats(scope)
        }
    }

    fun cancelPurgePlatform() {
        _state.update { it.copy(showPurgePlatformConfirm = null) }
    }

    fun openPlatformSettingsModal(platformId: Long) {
        _state.update {
            it.copy(
                platformSettingsModalId = platformId,
                platformSettingsFocusIndex = 0
            )
        }
    }

    fun closePlatformSettingsModal() {
        _state.update {
            it.copy(
                platformSettingsModalId = null,
                platformSettingsFocusIndex = 0
            )
        }
    }

    fun movePlatformSettingsFocus(delta: Int) {
        _state.update { state ->
            val config = state.platformConfigs.find { it.platformId == state.platformSettingsModalId }
            val maxIndex = if (config?.supportsStatePath == true) 5 else 4
            val newIndex = (state.platformSettingsFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(platformSettingsFocusIndex = newIndex, platformSettingsButtonIndex = 0)
        }
    }

    fun selectPlatformSettingsOption(scope: CoroutineScope) {
        val platformId = _state.value.platformSettingsModalId ?: return
        val config = getCurrentModalConfig() ?: return
        val focusIndex = _state.value.platformSettingsFocusIndex
        val buttonIndex = _state.value.platformSettingsButtonIndex

        val resyncIndex = if (config.supportsStatePath) 4 else 3
        val purgeIndex = if (config.supportsStatePath) 5 else 4

        when (focusIndex) {
            0 -> togglePlatformSync(scope, platformId, !config.syncEnabled)
            1 -> {
                if (buttonIndex == 0) {
                    openPlatformFolderPicker(scope, platformId)
                } else if (buttonIndex == 1 && config.customRomPath != null) {
                    resetPlatformToGlobal(scope, platformId)
                }
            }
            2 -> {
                if (buttonIndex == 0) {
                    openPlatformSavePathPicker(scope, platformId)
                } else if (buttonIndex == 1 && config.isUserSavePathOverride) {
                    resetPlatformSavePath(scope, platformId)
                }
            }
            3 -> {
                if (config.supportsStatePath) {
                    if (buttonIndex == 0) {
                        openPlatformStatePathPicker(scope, platformId)
                    } else if (buttonIndex == 1 && config.isUserStatePathOverride) {
                        resetPlatformStatePath(scope, platformId)
                    }
                } else {
                    closePlatformSettingsModal()
                    syncPlatform(scope, platformId, config.platformName)
                }
            }
            resyncIndex -> {
                closePlatformSettingsModal()
                syncPlatform(scope, platformId, config.platformName)
            }
            purgeIndex -> {
                closePlatformSettingsModal()
                requestPurgePlatform(platformId)
            }
        }
    }

    private fun openPlatformSavePathPicker(scope: CoroutineScope, platformId: Long) {
        scope.launch { _launchSavePathPicker.emit(platformId) }
    }

    fun emitSavePathPicker(scope: CoroutineScope, platformId: Long) {
        scope.launch { _launchSavePathPicker.emit(platformId) }
    }

    private fun resetPlatformSavePath(scope: CoroutineScope, platformId: Long) {
        scope.launch { _resetSavePathEvent.emit(platformId) }
    }

    private fun openPlatformStatePathPicker(scope: CoroutineScope, platformId: Long) {
        scope.launch { _launchStatePathPicker.emit(platformId) }
    }

    private fun resetPlatformStatePath(scope: CoroutineScope, platformId: Long) {
        scope.launch { _resetStatePathEvent.emit(platformId) }
    }

    private fun getCurrentModalConfig(): PlatformStorageConfig? {
        val platformId = _state.value.platformSettingsModalId ?: return null
        return _state.value.platformConfigs.find { it.platformId == platformId }
    }

    fun syncPlatform(scope: CoroutineScope, platformId: Long, platformName: String) {
        scope.launch {
            syncPlatformUseCase(platformId, platformName)
            loadPlatformConfigs(scope)
            refreshCollectionStats(scope)
        }
    }

    fun togglePlatformsExpanded() {
        _state.update { it.copy(platformsExpanded = !it.platformsExpanded) }
    }

    data class PlatformEmulatorInfo(
        val supportsStatePath: Boolean,
        val emulatorId: String?,
        val effectiveSavePath: String? = null,
        val isUserSavePathOverride: Boolean = false,
        val effectiveStatePath: String? = null,
        val isUserStatePathOverride: Boolean = false
    )

    fun updatePlatformEmulatorInfo(platformInfoMap: Map<Long, PlatformEmulatorInfo>) {
        _state.update { current ->
            current.copy(
                platformConfigs = current.platformConfigs.map { config ->
                    val info = platformInfoMap[config.platformId]
                    config.copy(
                        supportsStatePath = info?.supportsStatePath ?: false,
                        emulatorId = info?.emulatorId
                    )
                }
            )
        }
    }

    fun movePlatformSettingsButtonFocus(delta: Int) {
        _state.update { state ->
            val config = state.platformConfigs.find { it.platformId == state.platformSettingsModalId }
            val focusIndex = state.platformSettingsFocusIndex
            val hasReset = when {
                focusIndex == 1 -> config?.customRomPath != null
                focusIndex == 2 -> config?.isUserSavePathOverride == true
                focusIndex == 3 && config?.supportsStatePath == true -> config.isUserStatePathOverride
                else -> false
            }
            val maxIndex = if (hasReset) 1 else 0
            val newIndex = (state.platformSettingsButtonIndex + delta).coerceIn(0, maxIndex)
            state.copy(platformSettingsButtonIndex = newIndex)
        }
    }

    fun toggleScreenDimmer(scope: CoroutineScope) {
        scope.launch {
            val current = _state.value.screenDimmerEnabled
            val next = !current
            preferencesRepository.setScreenDimmerEnabled(next)
            _state.update { it.copy(screenDimmerEnabled = next) }
        }
    }

    fun cycleScreenDimmerTimeout(scope: CoroutineScope) {
        scope.launch {
            val current = _state.value.screenDimmerTimeoutMinutes
            val next = if (current >= 5) 1 else current + 1
            preferencesRepository.setScreenDimmerTimeoutMinutes(next)
            _state.update { it.copy(screenDimmerTimeoutMinutes = next) }
        }
    }

    fun cycleScreenDimmerLevel(scope: CoroutineScope) {
        scope.launch {
            val levels = listOf(40, 50, 60, 70)
            val current = _state.value.screenDimmerLevel
            val currentIndex = levels.indexOf(current).coerceAtLeast(0)
            val next = levels[(currentIndex + 1).mod(levels.size)]
            preferencesRepository.setScreenDimmerLevel(next)
            _state.update { it.copy(screenDimmerLevel = next) }
        }
    }

    fun adjustScreenDimmerTimeout(scope: CoroutineScope, delta: Int) {
        scope.launch {
            val current = _state.value.screenDimmerTimeoutMinutes
            val next = (current + delta).coerceIn(1, 5)
            if (next != current) {
                preferencesRepository.setScreenDimmerTimeoutMinutes(next)
                _state.update { it.copy(screenDimmerTimeoutMinutes = next) }
            }
        }
    }

    fun adjustScreenDimmerLevel(scope: CoroutineScope, delta: Int) {
        scope.launch {
            val current = _state.value.screenDimmerLevel
            val next = (current + delta * 10).coerceIn(40, 70)
            if (next != current) {
                preferencesRepository.setScreenDimmerLevel(next)
                _state.update { it.copy(screenDimmerLevel = next) }
            }
        }
    }

    fun requestPurgeAll() {
        _state.update { it.copy(showPurgeAllConfirm = true) }
    }

    fun cancelPurgeAll() {
        _state.update { it.copy(showPurgeAllConfirm = false) }
    }

    fun confirmPurgeAll(scope: CoroutineScope) {
        _state.update { it.copy(showPurgeAllConfirm = false, isPurgingAll = true) }
        scope.launch {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                imageCacheManager.clearCache()
            }
            _state.update { it.copy(isPurgingAll = false, platformConfigs = emptyList()) }
            refreshCollectionStats(scope)
        }
    }

    fun testManagedStorageAccess(scope: CoroutineScope) {
        scope.launch {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "=== Testing ManagedStorageAccessor ===")
                Log.i(TAG, "Managed access supported: ${managedStorageAccessor.isManagedAccessSupported()}")

                val testPackages = listOf(
                    "dev.eden.eden_emulator",
                    "com.retroarch",
                    "com.retroarch.aarch64"
                )

                for (pkg in testPackages) {
                    Log.i(TAG, "--- Testing package: $pkg ---")

                    val exists = managedStorageAccessor.exists(pkg)
                    Log.i(TAG, "  exists(): $exists")

                    if (exists) {
                        val files = managedStorageAccessor.listAndroidDataFiles(pkg)
                        if (files != null) {
                            Log.i(TAG, "  listAndroidDataFiles(): ${files.size} files")
                            files.take(5).forEach { file ->
                                Log.i(TAG, "    - ${file.displayName} (dir=${file.isDirectory}, size=${file.size})")
                            }
                            if (files.size > 5) {
                                Log.i(TAG, "    ... and ${files.size - 5} more")
                            }
                        } else {
                            Log.i(TAG, "  listAndroidDataFiles(): NULL (access denied or not found)")
                        }

                        val filesSubPath = managedStorageAccessor.listAndroidDataFiles(pkg, "files")
                        if (filesSubPath != null) {
                            Log.i(TAG, "  listAndroidDataFiles(files/): ${filesSubPath.size} files")
                            filesSubPath.take(5).forEach { file ->
                                Log.i(TAG, "    - ${file.displayName} (dir=${file.isDirectory})")
                            }
                        } else {
                            Log.i(TAG, "  listAndroidDataFiles(files/): NULL")
                        }
                    }
                }

                Log.i(TAG, "=== Test Complete ===")
            }
        }
    }
}
