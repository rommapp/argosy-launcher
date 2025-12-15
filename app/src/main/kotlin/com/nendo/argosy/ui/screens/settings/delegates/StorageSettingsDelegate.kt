package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.usecase.MigrateStorageUseCase
import com.nendo.argosy.ui.screens.settings.StorageState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class StorageSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val migrateStorageUseCase: MigrateStorageUseCase
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

    fun updateState(newState: StorageState) {
        _state.value = newState
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
}
