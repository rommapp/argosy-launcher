package com.nendo.argosy.ui.screens.settings.delegates

import android.app.Application
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.permissions.SaveSyncGate
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.SaveCacheRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem
import com.nendo.argosy.ui.screens.settings.SyncSettingsState
import com.nendo.argosy.ui.components.ListReorder
import com.nendo.argosy.ui.components.PLATFORM_HEADER_COUNT
import com.nendo.argosy.ui.components.ReorderStep
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SEARCH
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SORT
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PlatformFilterLogic
import com.nendo.argosy.util.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val attributionRepository: StorageAttributionRepository,
    private val retroAchievementsRepository: RetroAchievementsRepository,
    private val saveSyncGate: SaveSyncGate
) {
    private val _state = MutableStateFlow(SyncSettingsState())
    val state: StateFlow<SyncSettingsState> = _state.asStateFlow()

    private val _openImageCachePickerEvent = MutableSharedFlow<Unit>()
    val openImageCachePickerEvent: SharedFlow<Unit> = _openImageCachePickerEvent.asSharedFlow()


    private var isSyncing = false

    fun updateState(newState: SyncSettingsState) {
        _state.update { current ->
            newState.copy(
                saveSyncEnabled = current.saveSyncEnabled,
                platformFiltersAllPlatforms = current.platformFiltersAllPlatforms,
                platformFiltersList = current.platformFiltersList,
                enabledPlatformCount = current.enabledPlatformCount,
                totalPlatforms = current.totalPlatforms,
                totalGames = current.totalGames
            )
        }
    }

    fun observePlatforms(scope: CoroutineScope) {
        scope.launch {
            platformRepository.observeAllPlatforms().distinctUntilChanged().collect { platforms ->
                val items = platforms.map { entity ->
                    PlatformFilterItem(
                        id = entity.id,
                        name = entity.name,
                        slug = entity.slug,
                        romCount = entity.gameCount,
                        syncEnabled = entity.syncEnabled
                    )
                }
                _state.update {
                    it.copy(
                        platformFiltersAllPlatforms = items,
                        enabledPlatformCount = items.count { item -> item.syncEnabled },
                        totalPlatforms = items.size,
                        totalGames = platforms.sumOf { entity -> entity.gameCount }
                    )
                }
                applyPlatformFilters()
            }
        }
    }

    fun observeSaveSync(scope: CoroutineScope) {
        scope.launch {
            preferencesRepository.preferences
                .map { it.saveSyncEnabled }
                .distinctUntilChanged()
                .collect { enabled -> _state.update { it.copy(saveSyncEnabled = enabled) } }
        }
    }

    fun setDownloadCategoryDefault(scope: CoroutineScope, categoryKey: String, include: Boolean) {
        _state.update {
            it.copy(downloadDefaults = it.downloadDefaults + (categoryKey to include))
        }
        scope.launch {
            preferencesRepository.setDownloadCategoryDefault(categoryKey, include)
        }
    }

    fun refreshPlatformsFromServer(scope: CoroutineScope) {
        scope.launch {
            rommRepository.syncPlatformsOnly().onFailure {
                Logger.debug(TAG, "refreshPlatformsFromServer: skipped | ${it.message}")
            }
        }
    }

    fun loadLibrarySettings(scope: CoroutineScope) {
        scope.launch {
            val prefs = preferencesRepository.preferences.first()
            val hasStoragePermission = checkStoragePermission()
            val pendingCounts = saveCacheRepository.getPendingSyncCounts()
            val cacheCounts = saveCacheRepository.getCounts()
            val downloadDefaults = preferencesRepository.getGlobalDownloadDefaults()
            _state.update {
                it.copy(
                    downloadDefaults = downloadDefaults,
                    syncFilters = prefs.syncFilters,
                    regionPriority = prefs.regionPriority,
                    secureSaves = prefs.secureSaves,
                    stateCacheEnabled = prefs.stateCacheEnabled,
                    saveCacheLimit = prefs.saveCacheLimit,
                    hasStoragePermission = hasStoragePermission,
                    pendingUploadsCount = pendingCounts.pendingUploads,
                    imageCachePath = prefs.imageCachePath,
                    defaultImageCachePath = imageCacheManager.getDefaultCachePath(),
                    saveCacheCount = cacheCounts.saveCacheCount,
                    stateCacheCount = cacheCounts.stateCacheCount,
                    pathCacheCount = cacheCounts.pathCacheCount
                )
            }
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)
        }
    }

    fun checkStoragePermission(): Boolean = StorageAccess.isGranted(application)

    fun showSyncFiltersModal() {
        _state.update { it.copy(showSyncFiltersModal = true, syncFiltersModalFocusIndex = 0) }
    }

    fun dismissSyncFiltersModal() {
        _state.update { it.copy(showSyncFiltersModal = false, syncFiltersModalFocusIndex = 0) }
    }

    fun moveSyncFiltersModalFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = 7
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
            6 -> setExcludeUnofficial(scope, !state.syncFilters.excludeUnofficial)
            7 -> setDeleteOrphans(scope, !state.syncFilters.deleteOrphans)
        }
    }

    fun showRegionPicker() {
        _state.update { it.copy(showRegionPicker = true, regionPickerFocusIndex = 0) }
    }

    fun dismissRegionPicker() {
        _state.update {
            it.copy(showRegionPicker = false, regionPickerFocusIndex = 0)
        }
    }

    fun moveRegionPickerFocus(delta: Int) {
        _state.update { s ->
            val maxIndex = SyncFilterPreferences.ALL_KNOWN_REGIONS.size - 1
            val newIndex = (s.regionPickerFocusIndex + delta).coerceIn(0, maxIndex)
            s.copy(regionPickerFocusIndex = newIndex)
        }
    }

    fun confirmRegionPickerSelection(scope: CoroutineScope) {
        val region = SyncFilterPreferences.ALL_KNOWN_REGIONS
            .getOrNull(_state.value.regionPickerFocusIndex) ?: return
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

    fun showRegionPriority() {
        _state.update {
            it.copy(
                showRegionPriority = true,
                regionPriorityFocusIndex = 0,
                regionPriorityReorder = null
            )
        }
    }

    fun dismissRegionPriority() {
        _state.update { state ->
            state.copy(
                regionPriority = state.regionPriorityReorder?.backup ?: state.regionPriority,
                showRegionPriority = false,
                regionPriorityFocusIndex = 0,
                regionPriorityReorder = null
            )
        }
    }

    fun isHoldingRegionPriority(): Boolean = _state.value.regionPriorityReorder != null

    fun focusRegionPriority(index: Int) {
        _state.update { state ->
            if (state.regionPriorityReorder != null || index !in state.regionPriority.indices) {
                state
            } else {
                state.copy(regionPriorityFocusIndex = index)
            }
        }
    }

    fun moveRegionPriorityFocus(delta: Int) {
        _state.update { state ->
            val reorder = state.regionPriorityReorder
            when {
                state.regionPriority.isEmpty() -> state
                reorder != null -> applyRegionStep(state, reorder.moveBy(state.regionPriority, delta))
                else -> state.copy(
                    regionPriorityFocusIndex = (state.regionPriorityFocusIndex + delta)
                        .mod(state.regionPriority.size)
                )
            }
        }
    }

    fun liftRegionPriority() {
        _state.update { state ->
            if (state.regionPriorityReorder != null) return@update state
            val reorder = ListReorder.lift(state.regionPriority, state.regionPriorityFocusIndex)
                ?: return@update state
            state.copy(regionPriorityReorder = reorder)
        }
    }

    fun liftRegionPriorityAt(region: String) {
        _state.update { state ->
            val index = state.regionPriority.indexOf(region)
            val reorder = ListReorder.liftOrRegrab(state.regionPriorityReorder, state.regionPriority, index)
                ?: return@update state
            state.copy(regionPriorityReorder = reorder, regionPriorityFocusIndex = reorder.heldIndex)
        }
    }

    fun moveRegionPriorityTo(region: String, targetIndex: Int) {
        _state.update { state ->
            val reorder = state.regionPriorityReorder
            if (reorder == null || state.regionPriorityHeld != region) {
                state
            } else {
                applyRegionStep(state, reorder.moveTo(state.regionPriority, targetIndex))
            }
        }
    }

    fun dropRegionPriority(scope: CoroutineScope) {
        var committed: List<String>? = null
        _state.update { state ->
            val reorder = state.regionPriorityReorder
            committed = reorder?.changedOrder(state.regionPriority)
            if (reorder == null) state else state.copy(regionPriorityReorder = null)
        }
        val order = committed ?: return
        scope.launch { preferencesRepository.setRegionPriority(order) }
    }

    fun cancelRegionPriorityHold() {
        _state.update { state ->
            val reorder = state.regionPriorityReorder ?: return@update state
            state.copy(
                regionPriority = reorder.backup,
                regionPriorityReorder = null,
                regionPriorityFocusIndex = reorder.originIndex
            )
        }
    }

    private fun applyRegionStep(state: SyncSettingsState, step: ReorderStep<String>): SyncSettingsState =
        state.copy(
            regionPriority = step.items,
            regionPriorityReorder = step.reorder,
            regionPriorityFocusIndex = step.reorder.heldIndex
        )

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

    fun setExcludeUnofficial(scope: CoroutineScope, exclude: Boolean) {
        scope.launch {
            preferencesRepository.setSyncFilterExcludeUnofficial(exclude)
            _state.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeUnofficial = exclude))
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
                imageCacheManager.resumePendingArt(includeBoxFaces = true)
            }
        }
    }

    fun toggleUploadScreenshots(scope: CoroutineScope, currentValue: Boolean, onChanged: (Boolean) -> Unit) {
        scope.launch {
            val newValue = !currentValue
            preferencesRepository.setUploadScreenshotsEnabled(newValue)
            onChanged(newValue)
        }
    }

    fun setNotOnDeviceAction(
        scope: CoroutineScope,
        action: com.nendo.argosy.data.preferences.NotOnDeviceAction,
        onChanged: (com.nendo.argosy.data.preferences.NotOnDeviceAction) -> Unit
    ) {
        scope.launch {
            preferencesRepository.setNotOnDeviceAction(action)
            onChanged(action)
        }
    }

    fun enableSaveSync(scope: CoroutineScope) {
        scope.launch { turnSaveSyncOn(scope) }
    }

    fun toggleSaveSync(scope: CoroutineScope) {
        scope.launch {
            if (!_state.value.saveSyncEnabled) {
                turnSaveSyncOn(scope)
                return@launch
            }
            preferencesRepository.setSaveSyncEnabled(false)
        }
    }

    fun turnSaveSyncOff(scope: CoroutineScope) {
        scope.launch { preferencesRepository.setSaveSyncEnabled(false) }
    }

    private suspend fun turnSaveSyncOn(scope: CoroutineScope) {
        if (!saveSyncGate.clearToEnable()) return
        preferencesRepository.setSaveSyncEnabled(true)
        runSaveSyncNow(scope)
    }

    fun toggleSecureSaves(scope: CoroutineScope) {
        scope.launch {
            val newValue = !_state.value.secureSaves
            val hardcorePref = preferencesRepository.getBuiltinEmulatorSettings().first().defaultToHardcore
            if (!newValue && hardcorePref != "casual" && retroAchievementsRepository.isLoggedIn()) {
                _state.update { it.copy(showSecureSavesConfirm = true) }
                return@launch
            }
            preferencesRepository.setSecureSaves(newValue)
            _state.update { it.copy(secureSaves = newValue) }
        }
    }

    fun confirmDisableSecureSaves(scope: CoroutineScope) {
        scope.launch {
            preferencesRepository.setSecureSaves(false)
            _state.update { it.copy(secureSaves = false, showSecureSavesConfirm = false) }
        }
    }

    fun cancelDisableSecureSaves() {
        _state.update { it.copy(showSecureSavesConfirm = false) }
    }

    companion object {
        val SAVE_CACHE_LIMIT_VALUES = listOf(5, 7, 10, 15, 20)
        private const val TAG = "SyncSettingsDelegate"
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

    fun runSaveSyncNow(scope: CoroutineScope) {
        if (isSyncing) return
        isSyncing = true
        _state.update { it.copy(isSyncing = true) }

        scope.launch {
            try {
                notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_manual_syncing))
                saveSyncRepository.checkForAllServerUpdates()
                val result = syncCoordinator.processQueue()
                val pendingCounts = saveCacheRepository.getPendingSyncCounts()
                _state.update { it.copy(pendingUploadsCount = pendingCounts.pendingUploads) }

                val message: NotificationText = when (result) {
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.NotConnected ->
                        NotificationText.Res(R.string.notif_sync_settings_manual_not_connected)
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.Completed ->
                        if (result.processed == 0) {
                            NotificationText.Res(R.string.notif_sync_settings_manual_up_to_date)
                        } else if (result.failed > 0) {
                            NotificationText.Res(
                                R.string.notif_sync_settings_manual_synced_with_failures,
                                listOf(result.processed, result.failed)
                            )
                        } else {
                            NotificationText.Res(
                                R.string.notif_sync_settings_manual_synced,
                                listOf(result.processed)
                            )
                        }
                }
                notificationManager.show(message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationManager.showError(
                    NotificationText.Res(R.string.notif_sync_settings_manual_failed, listOf(e.message ?: "null"))
                )
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

            preferencesRepository.setImageCachePath(newPath)
            imageCacheManager.setCustomCachePath(newPath)
            _state.update { it.copy(imageCachePath = newPath) }

            val hasExistingFiles = imageCacheManager.getCacheFileCountForBasePath(currentPath) > 0
            if (hasExistingFiles) {
                _state.update { it.copy(isImageCacheMigrating = true) }
                notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_imagecache_moving_custom))
                try {
                    val success = imageCacheManager.migrateCache(currentPath, newPath)
                    if (success) {
                        notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_imagecache_moved_custom))
                    } else {
                        notificationManager.showError(NotificationText.Res(R.string.notif_sync_settings_imagecache_move_failed_custom))
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

            preferencesRepository.setImageCachePath(null)
            imageCacheManager.setCustomCachePath(null)
            _state.update { it.copy(imageCachePath = null, imageCacheActionIndex = 0) }

            val hasExistingFiles = imageCacheManager.getCacheFileCountForBasePath(currentPath) > 0
            if (hasExistingFiles) {
                _state.update { it.copy(isImageCacheMigrating = true) }
                notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_imagecache_moving_reset))
                try {
                    val success = imageCacheManager.migrateCache(currentPath, defaultPath)
                    if (success) {
                        notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_imagecache_moved_reset))
                    } else {
                        notificationManager.showError(NotificationText.Res(R.string.notif_sync_settings_imagecache_move_failed_reset))
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
                notificationManager.showError(
                    NotificationText.Res(
                        R.string.notif_sync_settings_platforms_fetch_failed,
                        listOf(result.exceptionOrNull()?.message ?: "null")
                    )
                )
            }

            _state.update { it.copy(isLoadingPlatforms = false) }
            applyPlatformFilters(resetFocus = true)
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
            platformRepository.updateSyncEnabled(platformId, !platform.syncEnabled)
        }
    }

    suspend fun pendingUploadCount(): Int = saveCacheRepository.getPendingSyncCounts().pendingUploads

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
                notificationManager.showError(NotificationText.Res(R.string.notif_sync_settings_reset_save_cache_blocked))
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
                notificationManager.showError(NotificationText.Res(R.string.notif_sync_settings_clear_state_cache_blocked))
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
                notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_scan_scanning))
                val secureSaves = preferencesRepository.userPreferences.first().secureSaves
                val queued = saveSyncRepository.scanAndQueueLocalChanges(secureSaves)
                if (queued > 0) {
                    notificationManager.show(
                        NotificationText.Res(R.string.notif_sync_settings_scan_found, listOf(queued))
                    )
                }

                notificationManager.show(NotificationText.Res(R.string.notif_sync_settings_scan_syncing))
                saveSyncRepository.checkForAllServerUpdates()
                val result = syncCoordinator.processQueue()
                val pendingCounts = saveCacheRepository.getPendingSyncCounts()
                _state.update { it.copy(pendingUploadsCount = pendingCounts.pendingUploads) }

                val message: NotificationText = when (result) {
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.NotConnected ->
                        NotificationText.Res(R.string.notif_sync_settings_scan_not_connected)
                    is com.nendo.argosy.data.sync.SyncCoordinator.ProcessResult.Completed ->
                        if (result.processed == 0) {
                            NotificationText.Res(R.string.notif_sync_settings_scan_up_to_date)
                        } else if (result.failed > 0) {
                            NotificationText.Res(
                                R.string.notif_sync_settings_scan_synced_with_failures,
                                listOf(result.processed, result.failed)
                            )
                        } else {
                            NotificationText.Res(
                                R.string.notif_sync_settings_scan_synced,
                                listOf(result.processed)
                            )
                        }
                }
                notificationManager.show(message)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                notificationManager.showError(
                    NotificationText.Res(R.string.notif_sync_settings_scan_failed, listOf(e.message ?: "null"))
                )
            } finally {
                isSyncing = false
                _state.update { it.copy(isSyncing = false) }
            }
        }
    }

}
