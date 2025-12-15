package com.nendo.argosy.ui.screens.settings.delegates

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.PendingSaveSyncDao
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.settings.SyncSettingsState
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

class SyncSettingsDelegate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val pendingSaveSyncDao: PendingSaveSyncDao,
    private val imageCacheManager: ImageCacheManager,
    private val notificationManager: NotificationManager
) {
    private val _state = MutableStateFlow(SyncSettingsState())
    val state: StateFlow<SyncSettingsState> = _state.asStateFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    fun updateState(newState: SyncSettingsState) {
        _state.value = newState
    }

    fun loadLibrarySettings(scope: CoroutineScope) {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            val hasPermission = checkStoragePermission()
            val pendingCount = pendingSaveSyncDao.getCount()
            _state.update {
                it.copy(
                    syncFilters = prefs.syncFilters,
                    saveSyncEnabled = prefs.saveSyncEnabled,
                    experimentalFolderSaveSync = prefs.experimentalFolderSaveSync,
                    saveCacheLimit = prefs.saveCacheLimit,
                    hasStoragePermission = hasPermission,
                    pendingUploadsCount = pendingCount
                )
            }
        }
    }

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun showRegionPicker() {
        _state.update { it.copy(showRegionPicker = true, regionPickerFocusIndex = 0) }
    }

    fun dismissRegionPicker() {
        _state.update { it.copy(showRegionPicker = false, regionPickerFocusIndex = 0) }
    }

    fun moveRegionPickerFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = SyncFilterPreferences.ALL_KNOWN_REGIONS.size - 1
            val newIndex = (state.regionPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(regionPickerFocusIndex = newIndex)
        }
    }

    fun confirmRegionPickerSelection(scope: CoroutineScope) {
        val state = _state.value
        val region = SyncFilterPreferences.ALL_KNOWN_REGIONS.getOrNull(state.regionPickerFocusIndex) ?: return
        toggleRegion(scope, region)
    }

    fun toggleRegion(scope: CoroutineScope, region: String) {
        scope.launch {
            val current = _state.value.syncFilters.enabledRegions
            val updated = if (region in current) current - region else current + region
            preferencesRepository.setSyncFilterRegions(updated)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(enabledRegions = updated))
            }
        }
    }

    fun toggleRegionMode(scope: CoroutineScope) {
        scope.launch {
            val current = _state.value.syncFilters.regionMode
            val next = when (current) {
                RegionFilterMode.INCLUDE -> RegionFilterMode.EXCLUDE
                RegionFilterMode.EXCLUDE -> RegionFilterMode.INCLUDE
            }
            preferencesRepository.setSyncFilterRegionMode(next)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(regionMode = next))
            }
        }
    }

    fun setExcludeBeta(scope: CoroutineScope, exclude: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterExcludeBeta(exclude)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeBeta = exclude))
            }
        }
    }

    fun setExcludePrototype(scope: CoroutineScope, exclude: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterExcludePrototype(exclude)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(excludePrototype = exclude))
            }
        }
    }

    fun setExcludeDemo(scope: CoroutineScope, exclude: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterExcludeDemo(exclude)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeDemo = exclude))
            }
        }
    }

    fun setExcludeHack(scope: CoroutineScope, exclude: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterExcludeHack(exclude)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeHack = exclude))
            }
        }
    }

    fun setDeleteOrphans(scope: CoroutineScope, delete: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterDeleteOrphans(delete)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(deleteOrphans = delete))
            }
        }
    }

    fun toggleSyncScreenshots(scope: CoroutineScope, currentValue: Boolean) {
        scope.launch {
            val newValue = !currentValue
            preferencesRepository.setSyncScreenshotsEnabled(newValue)
            if (newValue) {
                imageCacheManager.resumePendingScreenshotCache()
            }
        }
    }

    fun enableSaveSync(scope: CoroutineScope) {
        scope.launch {
            val currentState = _state.value
            if (!currentState.hasStoragePermission) {
                _requestStoragePermissionEvent.emit(Unit)
                return@launch
            }

            preferencesRepository.setSaveSyncEnabled(true)
            _state.update { it.copy(saveSyncEnabled = true) }
            runSaveSyncNow(scope)
        }
    }

    fun toggleSaveSync(scope: CoroutineScope) {
        scope.launch {
            val currentState = _state.value
            val newValue = !currentState.saveSyncEnabled

            preferencesRepository.setSaveSyncEnabled(newValue)
            _state.update { it.copy(saveSyncEnabled = newValue) }

            if (newValue) {
                runSaveSyncNow(scope)
            }
        }
    }

    fun toggleExperimentalFolderSaveSync(scope: CoroutineScope) {
        scope.launch {
            val currentState = _state.value
            val newValue = !currentState.experimentalFolderSaveSync

            preferencesRepository.setExperimentalFolderSaveSync(newValue)
            _state.update { it.copy(experimentalFolderSaveSync = newValue) }
        }
    }

    fun cycleSaveCacheLimit(scope: CoroutineScope) {
        scope.launch {
            val currentLimit = _state.value.saveCacheLimit
            val values = listOf(5, 7, 10, 15, 20)
            val currentIndex = values.indexOf(currentLimit).takeIf { it >= 0 } ?: 2
            val newLimit = values[(currentIndex + 1) % values.size]

            preferencesRepository.setSaveCacheLimit(newLimit)
            _state.update { it.copy(saveCacheLimit = newLimit) }
        }
    }

    fun onStoragePermissionResult(scope: CoroutineScope, granted: Boolean, currentSection: Any?) {
        scope.launch {
            _state.update { it.copy(hasStoragePermission = granted) }
            // Note: Section check should be done by caller
            if (granted) {
                preferencesRepository.setSaveSyncEnabled(true)
                _state.update { it.copy(saveSyncEnabled = true) }
                runSaveSyncNow(scope)
            }
        }
    }

    fun runSaveSyncNow(scope: CoroutineScope) {
        scope.launch {
            notificationManager.show("Checking for save updates...")
            try {
                saveSyncRepository.checkForAllServerUpdates()
                val uploaded = saveSyncRepository.processPendingUploads()
                val pendingCount = pendingSaveSyncDao.getCount()
                _state.update { it.copy(pendingUploadsCount = pendingCount) }
                if (uploaded > 0) {
                    notificationManager.show("Uploaded $uploaded saves")
                } else {
                    notificationManager.show("Saves are up to date")
                }
            } catch (e: Exception) {
                notificationManager.showError("Save sync failed: ${e.message}")
            }
        }
    }
}
