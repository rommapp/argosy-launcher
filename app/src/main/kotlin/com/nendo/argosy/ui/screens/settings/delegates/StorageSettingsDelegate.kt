package com.nendo.argosy.ui.screens.settings.delegates

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.MigratePlatformStorageUseCase
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.domain.usecase.PurgePlatformUseCase
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

class StorageSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val migrateStorageUseCase: MigrateStorageUseCase,
    private val migratePlatformStorageUseCase: MigratePlatformStorageUseCase,
    private val purgePlatformUseCase: PurgePlatformUseCase
) {
    private val _state = MutableStateFlow(StorageState())
    val state: StateFlow<StorageState> = _state.asStateFlow()

    private val _launchFolderPicker = MutableStateFlow(false)
    val launchFolderPicker: StateFlow<Boolean> = _launchFolderPicker.asStateFlow()

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

    private val _launchPlatformFolderPicker = MutableSharedFlow<String>()
    val launchPlatformFolderPicker: SharedFlow<String> = _launchPlatformFolderPicker.asSharedFlow()

    private var pendingPlatformId: String? = null

    fun loadPlatformConfigs(scope: CoroutineScope) {
        scope.launch {
            val globalPath = _state.value.romStoragePath
            val platforms = platformDao.observeConfigurablePlatforms().first()

            val configs = platforms.map { platform ->
                val effectivePath = platform.customRomPath ?: "$globalPath/${platform.id}"
                val downloadedCount = gameDao.countDownloadedByPlatform(platform.id)
                PlatformStorageConfig(
                    platformId = platform.id,
                    platformName = platform.name,
                    gameCount = platform.gameCount,
                    downloadedCount = downloadedCount,
                    syncEnabled = platform.syncEnabled,
                    customRomPath = platform.customRomPath,
                    effectivePath = effectivePath
                )
            }

            _state.update { it.copy(platformConfigs = configs) }
        }
    }

    fun togglePlatformSync(scope: CoroutineScope, platformId: String, enabled: Boolean) {
        scope.launch {
            platformDao.updateSyncEnabled(platformId, enabled)
            _state.update { current ->
                current.copy(
                    platformConfigs = current.platformConfigs.map { config ->
                        if (config.platformId == platformId) {
                            config.copy(syncEnabled = enabled)
                        } else config
                    }
                )
            }
        }
    }

    fun openPlatformFolderPicker(scope: CoroutineScope, platformId: String) {
        pendingPlatformId = platformId
        scope.launch {
            _launchPlatformFolderPicker.emit(platformId)
        }
    }

    fun setPlatformPath(scope: CoroutineScope, platformId: String, newPath: String) {
        scope.launch {
            val platform = platformDao.getById(platformId) ?: return@launch
            val globalPath = _state.value.romStoragePath
            val oldPath = platform.customRomPath ?: "$globalPath/${platform.id}"

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
                loadPlatformConfigs(scope)
            }
        }
        pendingPlatformId = null
    }

    fun resetPlatformToGlobal(scope: CoroutineScope, platformId: String) {
        scope.launch {
            val platform = platformDao.getById(platformId) ?: return@launch
            val customPath = platform.customRomPath ?: return@launch
            val globalPath = _state.value.romStoragePath
            val newPath = "$globalPath/${platform.id}"

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
                loadPlatformConfigs(scope)
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
            } else {
                platformDao.updateCustomRomPath(info.platformId, info.newPath)
            }
            loadPlatformConfigs(scope)
        }
    }

    fun requestPurgePlatform(platformId: String) {
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

    fun openPlatformSettingsModal(platformId: String) {
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
        val config = getCurrentModalConfig() ?: return
        val maxIndex = if (config.customRomPath != null) 3 else 2
        _state.update {
            val newIndex = (it.platformSettingsFocusIndex + delta).coerceIn(0, maxIndex)
            it.copy(platformSettingsFocusIndex = newIndex)
        }
    }

    fun selectPlatformSettingsOption(scope: CoroutineScope) {
        val platformId = _state.value.platformSettingsModalId ?: return
        val config = getCurrentModalConfig() ?: return
        val focusIndex = _state.value.platformSettingsFocusIndex

        val hasCustomPath = config.customRomPath != null
        when (focusIndex) {
            0 -> {
                togglePlatformSync(scope, platformId, !config.syncEnabled)
            }
            1 -> {
                closePlatformSettingsModal()
                openPlatformFolderPicker(scope, platformId)
            }
            2 -> {
                if (hasCustomPath) {
                    closePlatformSettingsModal()
                    resetPlatformToGlobal(scope, platformId)
                } else {
                    closePlatformSettingsModal()
                    requestPurgePlatform(platformId)
                }
            }
            3 -> {
                if (hasCustomPath) {
                    closePlatformSettingsModal()
                    requestPurgePlatform(platformId)
                }
            }
        }
    }

    private fun getCurrentModalConfig(): PlatformStorageConfig? {
        val platformId = _state.value.platformSettingsModalId ?: return null
        return _state.value.platformConfigs.find { it.platformId == platformId }
    }
}
