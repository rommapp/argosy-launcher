package com.nendo.argosy.ui.screens.settings.delegates

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem
import com.nendo.argosy.ui.screens.settings.SyncSettingsState
import com.nendo.argosy.ui.components.PLATFORM_HEADER_COUNT
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SEARCH
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SORT
import com.nendo.argosy.util.PlatformFilterLogic
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
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val saveCacheRepository: SaveCacheRepository,
    private val stateCacheManager: StateCacheManager,
    private val syncCoordinator: com.nendo.argosy.data.sync.SyncCoordinator,
    private val platformRepository: PlatformRepository,
    private val rommRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager,
    private val notificationManager: NotificationManager,
    private val permissionHelper: com.nendo.argosy.util.PermissionHelper,
    private val attributionRepository: StorageAttributionRepository
) {
    private val _state = MutableStateFlow(SyncSettingsState())
    val state: StateFlow<SyncSettingsState> = _state.asStateFlow()

    private val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    private val _requestNotificationPermissionEvent = MutableSharedFlow<Unit>()
    val requestNotificationPermissionEvent: SharedFlow<Unit> = _requestNotificationPermissionEvent.asSharedFlow()

    private val _requestMediaPermissionEvent = MutableSharedFlow<Unit>()
    val requestMediaPermissionEvent: SharedFlow<Unit> = _requestMediaPermissionEvent.asSharedFlow()

    private val _openImageCachePickerEvent = MutableSharedFlow<Unit>()
    val openImageCachePickerEvent: SharedFlow<Unit> = _openImageCachePickerEvent.asSharedFlow()


    private var isSyncing = false

    fun updateState(newState: SyncSettingsState) {
        _state.value = newState
    }

    fun setDownloadCategoryDefault(scope: CoroutineScope, categoryKey: String, include: Boolean) {
        _state.update {
            it.copy(downloadDefaults = it.downloadDefaults + (categoryKey to include))
        }
        scope.launch {
            preferencesRepository.setDownloadCategoryDefault(categoryKey, include)
        }
    }

    fun loadLibrarySettings(scope: CoroutineScope) {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            val hasStoragePermission = checkStoragePermission()
            val hasNotificationPermission = checkNotificationPermission()
            val pendingCounts = saveCacheRepository.getPendingSyncCounts()
            val enabledPlatformCount = platformRepository.getEnabledPlatformCount()
            val totalPlatformCount = platformRepository.getTotalPlatformCount()
            val cacheCounts = saveCacheRepository.getCounts()
            val downloadDefaults = preferencesRepository.getGlobalDownloadDefaults()
            _state.update {
                it.copy(
                    downloadDefaults = downloadDefaults,
                    syncFilters = prefs.syncFilters,
                    saveSyncEnabled = prefs.saveSyncEnabled,
                    stateCacheEnabled = prefs.stateCacheEnabled,
                    saveCacheLimit = prefs.saveCacheLimit,
                    hasStoragePermission = hasStoragePermission,
                    hasNotificationPermission = hasNotificationPermission,
                    pendingUploadsCount = pendingCounts.pendingUploads,
                    imageCachePath = prefs.imageCachePath,
                    defaultImageCachePath = imageCacheManager.getDefaultCachePath(),
                    enabledPlatformCount = enabledPlatformCount,
                    totalPlatforms = totalPlatformCount,
                    saveCacheCount = cacheCounts.saveCacheCount,
                    stateCacheCount = cacheCounts.stateCacheCount,
                    pathCacheCount = cacheCounts.pathCacheCount
                )
            }
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)
        }
    }

    fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showSyncFiltersModal() {
        _state.update { it.copy(showSyncFiltersModal = true, syncFiltersModalFocusIndex = 0) }
    }

    fun dismissSyncFiltersModal() {
        _state.update { it.copy(showSyncFiltersModal = false, syncFiltersModalFocusIndex = 0) }
    }

    fun moveSyncFiltersModalFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = 6
            val newIndex = (state.syncFiltersModalFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(syncFiltersModalFocusIndex = newIndex)
        }
    }

    fun confirmSyncFiltersModalSelection(scope: CoroutineScope) {
        val state = _state.value
        when (state.syncFiltersModalFocusIndex) {
            0 -> showRegionPicker()
            1 -> toggleRegionMode(scope)
            2 -> setExcludeBeta(scope, !state.syncFilters.excludeBeta)
            3 -> setExcludePrototype(scope, !state.syncFilters.excludePrototype)
            4 -> setExcludeDemo(scope, !state.syncFilters.excludeDemo)
            5 -> setExcludeHack(scope, !state.syncFilters.excludeHack)
            6 -> setDeleteOrphans(scope, !state.syncFilters.deleteOrphans)
        }
    }

    fun showRegionPicker() {
        _state.update { it.copy(showRegionPicker = true, regionPickerFocusIndex = 0) }
    }

    fun dismissRegionPicker() {
        val state = _state.value
        if (state.regionPickerHeldRegion != null) {
            cancelRegionHold()
            return
        }
        _state.update {
            it.copy(showRegionPicker = false, regionPickerFocusIndex = 0)
        }
    }

    fun moveRegionPickerFocus(delta: Int) {
        val state = _state.value
        if (state.regionPickerHeldRegion != null) {
            moveHeldRegion(delta)
            return
        }
        _state.update { s ->
            val maxIndex = SyncFilterPreferences.ALL_KNOWN_REGIONS.size - 1
            val newIndex = (s.regionPickerFocusIndex + delta).coerceIn(0, maxIndex)
            s.copy(regionPickerFocusIndex = newIndex)
        }
    }

    fun confirmRegionPickerSelection(scope: CoroutineScope) {
        val state = _state.value
        if (state.regionPickerHeldRegion != null) {
            dropHeldRegion(scope)
            return
        }
        val region = state.syncFilters.pickerDisplayOrder.getOrNull(state.regionPickerFocusIndex) ?: return
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

    fun liftRegion() {
        _state.update { state ->
            if (state.syncFilters.regionMode != RegionFilterMode.INCLUDE) return@update state
            val region = state.syncFilters.pickerDisplayOrder
                .getOrNull(state.regionPickerFocusIndex)
                ?.takeIf { it in state.syncFilters.enabledRegions }
                ?: return@update state
            state.copy(
                regionPickerHeldRegion = region,
                regionPickerOrderBackup = state.syncFilters.enabledRegions
            )
        }
    }

    fun liftRegionAt(region: String) {
        _state.update { state ->
            if (state.syncFilters.regionMode != RegionFilterMode.INCLUDE) return@update state
            if (region !in state.syncFilters.enabledRegions) return@update state
            state.copy(
                regionPickerHeldRegion = region,
                regionPickerOrderBackup = state.syncFilters.enabledRegions,
                regionPickerFocusIndex = state.syncFilters.enabledRegions.indexOf(region)
            )
        }
    }

    private fun moveHeldRegion(delta: Int) {
        _state.update { state ->
            val region = state.regionPickerHeldRegion ?: return@update state
            val order = state.syncFilters.enabledRegions.toMutableList()
            val from = order.indexOf(region)
            if (from == -1) return@update state
            val to = (from + delta).coerceIn(0, order.size - 1)
            if (to == from) return@update state
            order.removeAt(from)
            order.add(to, region)
            state.copy(
                syncFilters = state.syncFilters.copy(enabledRegions = order),
                regionPickerFocusIndex = to
            )
        }
    }

    fun moveRegionTo(region: String, targetIndex: Int) {
        _state.update { state ->
            if (state.regionPickerHeldRegion != region) return@update state
            val order = state.syncFilters.enabledRegions.toMutableList()
            val from = order.indexOf(region)
            if (from == -1) return@update state
            val to = targetIndex.coerceIn(0, order.size - 1)
            if (to == from) return@update state
            order.removeAt(from)
            order.add(to, region)
            state.copy(
                syncFilters = state.syncFilters.copy(enabledRegions = order),
                regionPickerFocusIndex = to
            )
        }
    }

    fun dropHeldRegion(scope: CoroutineScope) {
        val order = _state.value.syncFilters.enabledRegions
        _state.update {
            it.copy(regionPickerHeldRegion = null, regionPickerOrderBackup = null)
        }
        scope.launch { preferencesRepository.setSyncFilterRegions(order) }
    }

    fun cancelRegionHold() {
        _state.update { state ->
            val backup = state.regionPickerOrderBackup
            state.copy(
                syncFilters = if (backup != null) state.syncFilters.copy(enabledRegions = backup) else state.syncFilters,
                regionPickerHeldRegion = null,
                regionPickerOrderBackup = null
            )
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

    fun toggleBoxArtCache(scope: CoroutineScope, currentValue: Boolean) {
        scope.launch {
            val newValue = !currentValue
            preferencesRepository.setBoxArtCacheEnabled(newValue)
            if (newValue) {
                imageCacheManager.resumePendingBoxFaceCache()
            }
        }
    }

    fun toggleUploadScreenshots(scope: CoroutineScope, currentValue: Boolean, onChanged: (Boolean) -> Unit) {
        scope.launch {
            val newValue = !currentValue
            if (newValue && !checkMediaPermission()) {
                _requestMediaPermissionEvent.emit(Unit)
                return@launch
            }
            preferencesRepository.setUploadScreenshotsEnabled(newValue)
            onChanged(newValue)
            if (newValue) promptUsageAccessIfMissing()
        }
    }

    fun onMediaPermissionResult(scope: CoroutineScope, granted: Boolean, onChanged: (Boolean) -> Unit) {
        if (!granted) return
        scope.launch {
            preferencesRepository.setUploadScreenshotsEnabled(true)
            onChanged(true)
            promptUsageAccessIfMissing()
        }
    }

    private fun promptUsageAccessIfMissing() {
        if (!permissionHelper.hasUsageStatsPermission(application)) {
            permissionHelper.openUsageStatsSettings(application)
        }
    }

    fun checkMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                application,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun enableSaveSync(scope: CoroutineScope) {
        scope.launch {
            val currentState = _state.value
            if (!currentState.hasStoragePermission) {
                _requestStoragePermissionEvent.emit(Unit)
                return@launch
            }
            if (!currentState.hasNotificationPermission) {
                _requestNotificationPermissionEvent.emit(Unit)
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

            if (newValue && !currentState.hasNotificationPermission) {
                _requestNotificationPermissionEvent.emit(Unit)
                return@launch
            }

            preferencesRepository.setSaveSyncEnabled(newValue)
            _state.update { it.copy(saveSyncEnabled = newValue) }

            if (newValue) {
                runSaveSyncNow(scope)
            }
        }
    }

    companion object {
        val SAVE_CACHE_LIMIT_VALUES = listOf(5, 7, 10, 15, 20)
    }

    fun cycleSaveCacheLimit(scope: CoroutineScope, direction: Int = 1) {
        scope.launch {
            val newLimit = cycleInList(_state.value.saveCacheLimit, SAVE_CACHE_LIMIT_VALUES, direction)
            preferencesRepository.setSaveCacheLimit(newLimit)
            _state.update { it.copy(saveCacheLimit = newLimit) }
        }
    }

    fun setSaveCacheLimit(scope: CoroutineScope, limit: Int) {
        scope.launch {
            preferencesRepository.setSaveCacheLimit(limit)
            _state.update { it.copy(saveCacheLimit = limit) }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onStoragePermissionResult(scope: CoroutineScope, granted: Boolean, _currentSection: Any?) {
        scope.launch {
            _state.update { it.copy(hasStoragePermission = granted) }
            if (granted) {
                enableSaveSync(scope)
            }
        }
    }

    fun onNotificationPermissionResult(scope: CoroutineScope, granted: Boolean) {
        scope.launch {
            _state.update { it.copy(hasNotificationPermission = granted) }
            if (granted && _state.value.hasStoragePermission) {
                preferencesRepository.setSaveSyncEnabled(true)
                _state.update { it.copy(saveSyncEnabled = true) }
                runSaveSyncNow(scope)
            }
        }
    }

    fun refreshNotificationPermission() {
        val hasPermission = checkNotificationPermission()
        _state.update { it.copy(hasNotificationPermission = hasPermission) }
    }

    fun runSaveSyncNow(scope: CoroutineScope) {
        if (isSyncing) return
        isSyncing = true
        _state.update { it.copy(isSyncing = true) }

        scope.launch {
            try {
                notificationManager.show("Syncing saves...")
                saveSyncRepository.checkForAllServerUpdates()
                val result = syncCoordinator.processQueue()
                val pendingCounts = saveCacheRepository.getPendingSyncCounts()
                _state.update { it.copy(pendingUploadsCount = pendingCounts.pendingUploads) }

                val message = when (result) {
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.NotConnected ->
                        "Not connected"
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.Completed ->
                        if (result.processed == 0) "Saves are up to date"
                        else "Synced ${result.processed} item(s)" +
                            (if (result.failed > 0) " (${result.failed} failed)" else "")
                }
                notificationManager.show(message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationManager.showError("Save sync failed: ${e.message}")
            } finally {
                isSyncing = false
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

    fun openImageCachePicker(scope: CoroutineScope) {
        scope.launch {
            _openImageCachePickerEvent.emit(Unit)
        }
    }

    fun moveImageCacheActionFocus(delta: Int) {
        val hasCustomPath = _state.value.imageCachePath != null
        val maxIndex = if (hasCustomPath) 1 else 0
        val current = _state.value.imageCacheActionIndex
        val newIndex = (current + delta).coerceIn(0, maxIndex)
        _state.update { it.copy(imageCacheActionIndex = newIndex) }
    }

    fun onImageCachePathSelected(scope: CoroutineScope, newPath: String) {
        scope.launch {
            val currentPath = _state.value.imageCachePath ?: imageCacheManager.getDefaultCachePath()

            // Update state and preferences immediately
            preferencesRepository.setImageCachePath(newPath)
            imageCacheManager.setCustomCachePath(newPath)
            _state.update { it.copy(imageCachePath = newPath) }

            // Migrate in background if there are existing files
            val hasExistingFiles = imageCacheManager.getCacheFileCountForBasePath(currentPath) > 0
            if (hasExistingFiles) {
                _state.update { it.copy(isImageCacheMigrating = true) }
                notificationManager.show("Moving cached images...")
                try {
                    val success = imageCacheManager.migrateCache(currentPath, newPath)
                    if (success) {
                        notificationManager.show("Images moved successfully")
                    } else {
                        notificationManager.showError("Failed to move some images")
                    }
                    attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
                } finally {
                    _state.update { it.copy(isImageCacheMigrating = false) }
                }
            }
        }
    }

    fun resetImageCacheToDefault(scope: CoroutineScope) {
        scope.launch {
            val currentPath = _state.value.imageCachePath ?: return@launch
            val defaultPath = imageCacheManager.getDefaultCachePath()

            // Update state and preferences immediately
            preferencesRepository.setImageCachePath(null)
            imageCacheManager.setCustomCachePath(null)
            _state.update { it.copy(imageCachePath = null, imageCacheActionIndex = 0) }

            // Migrate in background if there are existing files
            val hasExistingFiles = imageCacheManager.getCacheFileCountForBasePath(currentPath) > 0
            if (hasExistingFiles) {
                _state.update { it.copy(isImageCacheMigrating = true) }
                notificationManager.show("Moving cached images...")
                try {
                    val success = imageCacheManager.migrateCache(currentPath, defaultPath)
                    if (success) {
                        notificationManager.show("Images moved successfully")
                    } else {
                        notificationManager.showError("Failed to move some images")
                    }
                    attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
                } finally {
                    _state.update { it.copy(isImageCacheMigrating = false) }
                }
            }
        }
    }

    fun showPlatformFiltersModal(scope: CoroutineScope) {
        scope.launch {
            _state.update {
                it.copy(
                    showPlatformFiltersModal = true,
                    isLoadingPlatforms = true,
                    platformFiltersHeaderFocused = false,
                    platformFiltersHeaderIndex = 0,
                    platformFiltersSearchActive = false,
                    platformFiltersSortMenuOpen = false
                )
            }

            val result = rommRepository.syncPlatformsOnly()
            if (result.isFailure) {
                notificationManager.showError("Failed to fetch platforms: ${result.exceptionOrNull()?.message}")
            }

            val allPlatforms = platformRepository.getAllPlatformsOrdered().map { entity ->
                PlatformFilterItem(
                    id = entity.id,
                    name = entity.name,
                    slug = entity.slug,
                    romCount = entity.gameCount,
                    syncEnabled = entity.syncEnabled
                )
            }
            val filtered = PlatformFilterLogic.filterAndSortPlatformFilterItems(
                items = allPlatforms,
                searchQuery = _state.value.platformFilterSearchQuery,
                filterMode = _state.value.platformFilterMode,
                sortMode = _state.value.platformFilterSortMode
            )
            val enabledCount = allPlatforms.count { it.syncEnabled }
            _state.update {
                it.copy(
                    platformFiltersAllPlatforms = allPlatforms,
                    platformFiltersList = filtered,
                    isLoadingPlatforms = false,
                    platformFiltersModalFocusIndex = 0,
                    enabledPlatformCount = enabledCount,
                    totalPlatforms = allPlatforms.size
                )
            }
        }
    }

    fun dismissPlatformFiltersModal() {
        _state.update {
            it.copy(
                showPlatformFiltersModal = false,
                platformFiltersSearchActive = false,
                platformFiltersSortMenuOpen = false,
                platformFiltersHeaderFocused = false
            )
        }
    }

    fun applyPlatformFilters(resetFocus: Boolean = false) {
        _state.update { state ->
            val filtered = PlatformFilterLogic.filterAndSortPlatformFilterItems(
                items = state.platformFiltersAllPlatforms,
                searchQuery = state.platformFilterSearchQuery,
                filterMode = state.platformFilterMode,
                sortMode = state.platformFilterSortMode
            )
            state.copy(
                platformFiltersList = filtered,
                platformFiltersModalFocusIndex = if (resetFocus) 0 else state.platformFiltersModalFocusIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun setPlatformFilterSortMode(mode: PlatformFilterLogic.SortMode) {
        _state.update { it.copy(platformFilterSortMode = mode) }
        applyPlatformFilters(resetFocus = true)
    }

    fun setPlatformFilterSearchQuery(query: String) {
        _state.update { it.copy(platformFilterSearchQuery = query) }
        applyPlatformFilters(resetFocus = true)
    }

    fun cyclePlatformFilterMode() {
        _state.update {
            val nextMode = when (it.platformFilterMode) {
                PlatformFilterLogic.FilterMode.ALL -> PlatformFilterLogic.FilterMode.HAS_GAMES
                PlatformFilterLogic.FilterMode.HAS_GAMES -> PlatformFilterLogic.FilterMode.ENABLED
                PlatformFilterLogic.FilterMode.ENABLED -> PlatformFilterLogic.FilterMode.ALL
            }
            it.copy(platformFilterMode = nextMode)
        }
        applyPlatformFilters(resetFocus = true)
    }

    fun platformFiltersUp() {
        _state.update { s ->
            when {
                s.platformFiltersSortMenuOpen -> s.copy(
                    platformFiltersSortMenuIndex = (s.platformFiltersSortMenuIndex - 1).mod(PlatformFilterLogic.SortMode.entries.size)
                )
                s.platformFiltersSearchActive -> s
                s.platformFiltersHeaderFocused -> s
                s.platformFiltersModalFocusIndex <= 0 -> s.copy(platformFiltersHeaderFocused = true)
                else -> s.copy(platformFiltersModalFocusIndex = s.platformFiltersModalFocusIndex - 1)
            }
        }
    }

    fun platformFiltersDown() {
        _state.update { s ->
            when {
                s.platformFiltersSortMenuOpen -> s.copy(
                    platformFiltersSortMenuIndex = (s.platformFiltersSortMenuIndex + 1).mod(PlatformFilterLogic.SortMode.entries.size)
                )
                s.platformFiltersSearchActive -> s
                s.platformFiltersHeaderFocused -> s.copy(platformFiltersHeaderFocused = false, platformFiltersModalFocusIndex = 0)
                else -> {
                    val maxIndex = (s.platformFiltersList.size - 1).coerceAtLeast(0)
                    s.copy(platformFiltersModalFocusIndex = (s.platformFiltersModalFocusIndex + 1).coerceAtMost(maxIndex))
                }
            }
        }
    }

    fun platformFiltersLeft() = movePlatformFiltersHeader(-1)

    fun platformFiltersRight() = movePlatformFiltersHeader(1)

    private fun movePlatformFiltersHeader(delta: Int) {
        _state.update { s ->
            if (s.platformFiltersHeaderFocused && !s.platformFiltersSortMenuOpen && !s.platformFiltersSearchActive) {
                s.copy(platformFiltersHeaderIndex = (s.platformFiltersHeaderIndex + delta).mod(PLATFORM_HEADER_COUNT))
            } else {
                s
            }
        }
    }

    fun platformFiltersConfirm(scope: CoroutineScope) {
        val s = _state.value
        when {
            s.platformFiltersSortMenuOpen -> {
                val mode = PlatformFilterLogic.SortMode.entries[s.platformFiltersSortMenuIndex]
                _state.update { it.copy(platformFiltersSortMenuOpen = false) }
                setPlatformFilterSortMode(mode)
            }
            s.platformFiltersSearchActive -> _state.update { it.copy(platformFiltersSearchActive = false) }
            s.platformFiltersHeaderFocused -> when (s.platformFiltersHeaderIndex) {
                PLATFORM_HEADER_SEARCH -> _state.update { it.copy(platformFiltersSearchActive = true) }
                PLATFORM_HEADER_SORT -> _state.update {
                    it.copy(
                        platformFiltersSortMenuOpen = true,
                        platformFiltersSortMenuIndex = PlatformFilterLogic.SortMode.entries
                            .indexOf(it.platformFilterSortMode).coerceAtLeast(0)
                    )
                }
                else -> cyclePlatformFilterMode()
            }
            else -> {
                val platform = s.platformFiltersList.getOrNull(s.platformFiltersModalFocusIndex) ?: return
                togglePlatformSyncEnabled(scope, platform.id)
            }
        }
    }

    fun platformFiltersBack() {
        _state.update { s ->
            when {
                s.platformFiltersSortMenuOpen -> s.copy(platformFiltersSortMenuOpen = false)
                s.platformFiltersSearchActive -> s.copy(platformFiltersSearchActive = false)
                else -> s.copy(
                    showPlatformFiltersModal = false,
                    platformFiltersHeaderFocused = false
                )
            }
        }
    }

    fun openPlatformSearch() = _state.update { it.copy(platformFiltersHeaderFocused = true, platformFiltersHeaderIndex = PLATFORM_HEADER_SEARCH, platformFiltersSearchActive = true) }

    fun closePlatformSearch() = _state.update { it.copy(platformFiltersSearchActive = false) }

    fun openPlatformSortMenu() = _state.update {
        it.copy(
            platformFiltersHeaderFocused = true,
            platformFiltersHeaderIndex = PLATFORM_HEADER_SORT,
            platformFiltersSortMenuOpen = true,
            platformFiltersSortMenuIndex = PlatformFilterLogic.SortMode.entries.indexOf(it.platformFilterSortMode).coerceAtLeast(0)
        )
    }

    fun closePlatformSortMenu() = _state.update { it.copy(platformFiltersSortMenuOpen = false) }

    fun togglePlatformSyncEnabled(scope: CoroutineScope, platformId: Long) {
        scope.launch {
            val platform = platformRepository.getById(platformId) ?: return@launch
            val newEnabled = !platform.syncEnabled
            platformRepository.updateSyncEnabled(platformId, newEnabled)

            _state.update { state ->
                val updatedAllPlatforms = state.platformFiltersAllPlatforms.map { item ->
                    if (item.id == platformId) item.copy(syncEnabled = newEnabled) else item
                }
                val enabledCount = updatedAllPlatforms.count { it.syncEnabled }

                state.copy(
                    platformFiltersAllPlatforms = updatedAllPlatforms,
                    enabledPlatformCount = enabledCount
                )
            }
            applyPlatformFilters()
        }
    }

    fun requestResetSaveCache(scope: CoroutineScope) {
        scope.launch {
            val pendingUploads = saveCacheRepository.getPendingSyncCounts().pendingUploads
            _state.update {
                it.copy(
                    pendingUploadsCount = pendingUploads,
                    showResetSaveCacheConfirm = pendingUploads == 0
                )
            }
        }
    }

    fun cancelResetSaveCache() {
        _state.update { it.copy(showResetSaveCacheConfirm = false) }
    }

    fun confirmResetSaveCache(scope: CoroutineScope) {
        _state.update { it.copy(showResetSaveCacheConfirm = false, isResettingSaveCache = true) }
        scope.launch {
            val performed = saveCacheRepository.resetSaveCache()
            if (performed) {
                _state.update { it.copy(isResettingSaveCache = false, saveCacheCount = 0, stateCacheCount = 0) }
                attributionRepository.refreshOnOpen()
            } else {
                notificationManager.showError("Cannot reset save cache while a game is running")
                _state.update { it.copy(isResettingSaveCache = false) }
            }
        }
    }

    fun toggleStateCache(scope: CoroutineScope) {
        scope.launch {
            val newValue = !_state.value.stateCacheEnabled
            preferencesRepository.setStateCacheEnabled(newValue)
            _state.update { it.copy(stateCacheEnabled = newValue) }
        }
    }

    fun requestClearStateCache(scope: CoroutineScope) {
        scope.launch {
            val pendingUploads = saveCacheRepository.getPendingSyncCounts().pendingUploads
            _state.update {
                it.copy(
                    pendingUploadsCount = pendingUploads,
                    showClearStateCacheConfirm = pendingUploads == 0
                )
            }
        }
    }

    fun cancelClearStateCache() {
        _state.update { it.copy(showClearStateCacheConfirm = false) }
    }

    fun confirmClearStateCache(scope: CoroutineScope) {
        _state.update { it.copy(showClearStateCacheConfirm = false, isClearingStateCache = true) }
        scope.launch {
            val performed = stateCacheManager.clearAllCache()
            if (performed) {
                _state.update { it.copy(isClearingStateCache = false, stateCacheCount = 0) }
                attributionRepository.refreshOnOpen()
            } else {
                notificationManager.showError("Cannot clear state cache while a game is running")
                _state.update { it.copy(isClearingStateCache = false) }
            }
        }
    }

    fun requestClearPathCache(scope: CoroutineScope) {
        scope.launch {
            val pendingUploads = saveCacheRepository.getPendingSyncCounts().pendingUploads
            _state.update {
                it.copy(
                    pendingUploadsCount = pendingUploads,
                    showClearPathCacheConfirm = pendingUploads == 0
                )
            }
        }
    }

    fun cancelClearPathCache() {
        _state.update { it.copy(showClearPathCacheConfirm = false) }
    }

    fun confirmClearPathCache(scope: CoroutineScope) {
        _state.update { it.copy(showClearPathCacheConfirm = false, isClearingPathCache = true) }
        scope.launch {
            saveCacheRepository.clearPathCache()
            _state.update { it.copy(isClearingPathCache = false, pathCacheCount = 0) }
        }
    }

    fun requestSyncSaves() {
        _state.update { it.copy(showForceSyncConfirm = true, syncConfirmButtonIndex = 0) }
    }

    fun cancelSyncSaves() {
        _state.update { it.copy(showForceSyncConfirm = false) }
    }

    fun moveSyncConfirmFocus(delta: Int) {
        _state.update { state ->
            val newIndex = (state.syncConfirmButtonIndex + delta).coerceIn(0, 1)
            state.copy(syncConfirmButtonIndex = newIndex)
        }
    }

    fun confirmSyncSaves(scope: CoroutineScope) {
        _state.update { it.copy(showForceSyncConfirm = false) }
        runSaveSyncWithLocalScan(scope)
    }

    private fun runSaveSyncWithLocalScan(scope: CoroutineScope) {
        if (isSyncing) return
        isSyncing = true
        _state.update { it.copy(isSyncing = true) }

        scope.launch {
            try {
                notificationManager.show("Scanning local saves...")
                val queued = saveSyncRepository.scanAndQueueLocalChanges()
                if (queued > 0) {
                    notificationManager.show("Found $queued local saves to sync")
                }

                notificationManager.show("Syncing saves...")
                saveSyncRepository.checkForAllServerUpdates()
                val result = syncCoordinator.processQueue()
                val pendingCounts = saveCacheRepository.getPendingSyncCounts()
                _state.update { it.copy(pendingUploadsCount = pendingCounts.pendingUploads) }

                val message = when (result) {
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.NotConnected ->
                        "Not connected"
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.Completed ->
                        if (result.processed == 0) "Saves are up to date"
                        else "Synced ${result.processed} item(s)" +
                            (if (result.failed > 0) " (${result.failed} failed)" else "")
                }
                notificationManager.show(message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationManager.showError("Save sync failed: ${e.message}")
            } finally {
                isSyncing = false
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

}
