package com.nendo.argosy.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.AnimationSpeed
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SettingsViewModel"

enum class SettingsSection {
    MAIN,
    APPEARANCE,
    EMULATORS,
    COLLECTION,
    SYNC_FILTERS,
    LIBRARY_BREAKDOWN,
    ABOUT
}

data class PlatformBreakdown(
    val platformName: String,
    val totalGames: Int,
    val downloadedGames: Int,
    val downloadedSize: Long
)

enum class ConnectionStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    NOT_CONFIGURED
}

data class PlatformEmulatorConfig(
    val platform: PlatformEntity,
    val selectedEmulator: String?,
    val isUserConfigured: Boolean,
    val availableEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef> = emptyList()
) {
    val hasInstalledEmulators: Boolean get() = availableEmulators.isNotEmpty()
}

data class EmulatorPickerInfo(
    val platformId: String,
    val platformName: String,
    val installedEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef>,
    val selectedEmulatorName: String?
)

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.MAIN,
    val focusedIndex: Int = 0,
    val colorFocusIndex: Int = 0,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val hapticEnabled: Boolean = true,
    val nintendoButtonLayout: Boolean = false,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,

    val platforms: List<PlatformEmulatorConfig> = emptyList(),
    val installedEmulators: List<InstalledEmulator> = emptyList(),

    val romStoragePath: String = "",
    val totalGames: Int = 0,
    val totalPlatforms: Int = 0,
    val downloadedGamesSize: Long = 0,
    val downloadedGamesCount: Int = 0,
    val platformBreakdowns: List<PlatformBreakdown> = emptyList(),

    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommVersion: String? = null,
    val lastRommSync: java.time.Instant? = null,

    val rommConfiguring: Boolean = false,
    val rommConfigUrl: String = "",
    val rommConfigUsername: String = "",
    val rommConfigPassword: String = "",
    val rommConnecting: Boolean = false,
    val rommConfigError: String? = null,
    val rommFocusField: Int? = null,

    val launchFolderPicker: Boolean = false,
    val showMigrationDialog: Boolean = false,
    val pendingStoragePath: String? = null,
    val isMigrating: Boolean = false,

    val appVersion: String = "0.1.0",
    val canAutoAssign: Boolean = false,

    val showEmulatorPicker: Boolean = false,
    val emulatorPickerInfo: EmulatorPickerInfo? = null,
    val emulatorPickerFocusIndex: Int = 0,

    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val showRegionPicker: Boolean = false,
    val regionPickerFocusIndex: Int = 0,
    val syncScreenshotsEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformDao: PlatformDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val gameRepository: GameRepository,
    private val imageCacheManager: ImageCacheManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    val imageCacheProgress: StateFlow<ImageCacheProgress> = imageCacheManager.progress

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            val installedEmulators = emulatorDetector.detectEmulators()
            val platforms = platformDao.observePlatformsWithGames().first()

            val installedPackages = installedEmulators.map { it.def.packageName }.toSet()

            val platformConfigs = platforms.map { platform ->
                val defaultConfig = emulatorConfigDao.getDefaultForPlatform(platform.id)
                val available = installedEmulators.filter { platform.id in it.def.supportedPlatforms }
                val isUserConfigured = defaultConfig != null

                val recommended = EmulatorRegistry.getRecommendedEmulators()[platform.id] ?: emptyList()
                val downloadable = recommended
                    .mapNotNull { EmulatorRegistry.getById(it) }
                    .filter { it.packageName !in installedPackages && it.downloadUrl != null }

                PlatformEmulatorConfig(
                    platform = platform,
                    selectedEmulator = defaultConfig?.displayName,
                    isUserConfigured = isUserConfigured,
                    availableEmulators = available,
                    downloadableEmulators = downloadable
                )
            }

            val canAutoAssign = platformConfigs.any { !it.isUserConfigured && it.availableEmulators.isNotEmpty() }

            val connectionState = romMRepository.connectionState.value
            val connectionStatus = when {
                prefs.rommBaseUrl.isNullOrBlank() -> ConnectionStatus.NOT_CONFIGURED
                connectionState is RomMRepository.ConnectionState.Connected -> ConnectionStatus.ONLINE
                else -> ConnectionStatus.OFFLINE
            }
            val rommVersion = (connectionState as? RomMRepository.ConnectionState.Connected)?.version

            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()

            _uiState.update { state ->
                state.copy(
                    themeMode = prefs.themeMode,
                    primaryColor = prefs.primaryColor,
                    hapticEnabled = prefs.hapticEnabled,
                    nintendoButtonLayout = prefs.nintendoButtonLayout,
                    animationSpeed = prefs.animationSpeed,
                    platforms = platformConfigs,
                    installedEmulators = installedEmulators,
                    romStoragePath = prefs.romStoragePath ?: "",
                    totalPlatforms = platforms.count { it.gameCount > 0 },
                    totalGames = platforms.sumOf { it.gameCount },
                    downloadedGamesSize = downloadedSize,
                    downloadedGamesCount = downloadedCount,
                    connectionStatus = connectionStatus,
                    rommUrl = prefs.rommBaseUrl ?: "",
                    rommUsername = prefs.rommUsername ?: "",
                    rommVersion = rommVersion,
                    lastRommSync = prefs.lastRommSync,
                    canAutoAssign = canAutoAssign,
                    syncFilters = prefs.syncFilters,
                    syncScreenshotsEnabled = prefs.syncScreenshotsEnabled
                )
            }
        }
    }

    fun autoAssignAllEmulators() {
        viewModelScope.launch {
            for (config in _uiState.value.platforms) {
                if (!config.isUserConfigured && config.availableEmulators.isNotEmpty()) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.id)
                    if (preferred != null) {
                        setPlatformEmulator(config.platform.id, preferred)
                    }
                }
            }
        }
    }

    fun refreshEmulators() {
        viewModelScope.launch {
            val previousCounts = _uiState.value.platforms.associate {
                it.platform.id to it.availableEmulators.size
            }

            loadSettings()

            val currentPlatforms = _uiState.value.platforms
            for (config in currentPlatforms) {
                val prevCount = previousCounts[config.platform.id] ?: 0
                val currentCount = config.availableEmulators.size

                if (prevCount == 0 && currentCount > 0 && !config.isUserConfigured) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.id)
                    if (preferred != null) {
                        setPlatformEmulator(config.platform.id, preferred)
                    }
                }
            }
        }
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig) {
        if (config.availableEmulators.isEmpty() && config.downloadableEmulators.isEmpty()) return
        _uiState.update {
            it.copy(
                showEmulatorPicker = true,
                emulatorPickerInfo = EmulatorPickerInfo(
                    platformId = config.platform.id,
                    platformName = config.platform.name,
                    installedEmulators = config.availableEmulators,
                    downloadableEmulators = config.downloadableEmulators,
                    selectedEmulatorName = config.selectedEmulator
                ),
                emulatorPickerFocusIndex = 0
            )
        }
    }

    fun dismissEmulatorPicker() {
        _uiState.update {
            it.copy(
                showEmulatorPicker = false,
                emulatorPickerInfo = null,
                emulatorPickerFocusIndex = 0
            )
        }
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _uiState.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            val hasInstalled = info.installedEmulators.isNotEmpty()
            // Items: "Auto" (only if installed) + installed + downloadable
            val totalItems = (if (hasInstalled) 1 else 0) + info.installedEmulators.size + info.downloadableEmulators.size
            val maxIndex = (totalItems - 1).coerceAtLeast(0)
            val newIndex = (state.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulatorPickerFocusIndex = newIndex)
        }
    }

    fun confirmEmulatorPickerSelection() {
        viewModelScope.launch {
            val state = _uiState.value
            val info = state.emulatorPickerInfo ?: return@launch
            val index = state.emulatorPickerFocusIndex
            val hasInstalled = info.installedEmulators.isNotEmpty()

            if (hasInstalled) {
                // With installed emulators: Auto at 0, installed at 1..N, downloadable at N+1..
                when {
                    index == 0 -> {
                        // "Auto" selected - clear custom config
                        setPlatformEmulator(info.platformId, null)
                        dismissEmulatorPicker()
                    }
                    index <= info.installedEmulators.size -> {
                        // Installed emulator selected
                        val emulator = info.installedEmulators[index - 1]
                        setPlatformEmulator(info.platformId, emulator)
                        dismissEmulatorPicker()
                    }
                    else -> {
                        // Downloadable emulator selected - open URL
                        val downloadIndex = index - 1 - info.installedEmulators.size
                        val emulator = info.downloadableEmulators.getOrNull(downloadIndex)
                        emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
                    }
                }
            } else {
                // No installed emulators: downloadable starts at 0
                val emulator = info.downloadableEmulators.getOrNull(index)
                emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
            }
        }
    }

    fun navigateToSection(section: SettingsSection) {
        _uiState.update { it.copy(currentSection = section, focusedIndex = 0) }
        when (section) {
            SettingsSection.EMULATORS -> refreshEmulators()
            SettingsSection.COLLECTION -> checkRommConnection()
            SettingsSection.SYNC_FILTERS -> loadSyncFilters()
            SettingsSection.LIBRARY_BREAKDOWN -> loadPlatformBreakdowns()
            else -> {}
        }
    }

    private fun loadSyncFilters() {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            _uiState.update { it.copy(syncFilters = prefs.syncFilters) }
        }
    }

    private fun loadPlatformBreakdowns() {
        viewModelScope.launch {
            val breakdowns = gameRepository.getPlatformBreakdowns()
            _uiState.update { state ->
                state.copy(
                    platformBreakdowns = breakdowns.map { stats ->
                        PlatformBreakdown(
                            platformName = stats.platformName,
                            totalGames = stats.totalGames,
                            downloadedGames = stats.downloadedGames,
                            downloadedSize = stats.downloadedSize
                        )
                    }
                )
            }
        }
    }

    fun checkRommConnection() {
        val url = _uiState.value.rommUrl
        if (url.isBlank()) {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.NOT_CONFIGURED) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.CHECKING) }
            try {
                val result = romMRepository.getLibrarySummary()
                val status = if (result is RomMResult.Success) {
                    ConnectionStatus.ONLINE
                } else {
                    ConnectionStatus.OFFLINE
                }
                _uiState.update { it.copy(connectionStatus = status) }
            } catch (e: Exception) {
                Log.e(TAG, "checkRommConnection: failed", e)
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.OFFLINE) }
            }
        }
    }

    fun navigateBack(): Boolean {
        val state = _uiState.value
        return when {
            state.showRegionPicker -> {
                dismissRegionPicker()
                true
            }
            state.showEmulatorPicker -> {
                dismissEmulatorPicker()
                true
            }
            state.rommConfiguring -> {
                cancelRommConfig()
                true
            }
            state.currentSection == SettingsSection.LIBRARY_BREAKDOWN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.COLLECTION, focusedIndex = 0) }
                true
            }
            state.currentSection == SettingsSection.SYNC_FILTERS -> {
                _uiState.update { it.copy(currentSection = SettingsSection.COLLECTION, focusedIndex = 0) }
                true
            }
            state.currentSection != SettingsSection.MAIN -> {
                _uiState.update { it.copy(currentSection = SettingsSection.MAIN, focusedIndex = 0) }
                true
            }
            else -> false
        }
    }

    private val colorCount = 7

    fun moveFocus(delta: Int) {
        if (_uiState.value.showRegionPicker) {
            moveRegionPickerFocus(delta)
            return
        }
        if (_uiState.value.showEmulatorPicker) {
            moveEmulatorPickerFocus(delta)
            return
        }
        _uiState.update { state ->
            val maxIndex = when (state.currentSection) {
                SettingsSection.MAIN -> 3
                SettingsSection.APPEARANCE -> 4
                SettingsSection.EMULATORS -> {
                    val platformCount = state.platforms.size
                    val autoAssignOffset = if (state.canAutoAssign) 1 else 0
                    (platformCount + autoAssignOffset - 1).coerceAtLeast(0)
                }
                SettingsSection.COLLECTION -> when {
                    state.rommConfiguring -> 4
                    state.connectionStatus == ConnectionStatus.ONLINE ||
                    state.connectionStatus == ConnectionStatus.OFFLINE -> 6
                    else -> 2
                }
                SettingsSection.SYNC_FILTERS -> 5
                SettingsSection.LIBRARY_BREAKDOWN -> (state.platformBreakdowns.size - 1).coerceAtLeast(0)
                SettingsSection.ABOUT -> 2
            }
            state.copy(focusedIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex))
        }
    }

    fun moveColorFocus(delta: Int) {
        _uiState.update { state ->
            state.copy(colorFocusIndex = (state.colorFocusIndex + delta).coerceIn(0, colorCount - 1))
        }
    }

    fun selectFocusedColor() {
        val colors = listOf<Int?>(
            null,
            0xFF9575CD.toInt(),
            0xFF4DB6AC.toInt(),
            0xFFFFB74D.toInt(),
            0xFF81C784.toInt(),
            0xFFF06292.toInt(),
            0xFF64B5F6.toInt()
        )
        val color = colors.getOrNull(_uiState.value.colorFocusIndex)
        setPrimaryColor(color)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setPrimaryColor(color: Int?) {
        viewModelScope.launch {
            preferencesRepository.setCustomColors(color, null, null)
            _uiState.update { it.copy(primaryColor = color) }
        }
    }

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticEnabled(enabled)
            _uiState.update { it.copy(hapticEnabled = enabled) }
        }
    }

    fun setNintendoButtonLayout(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNintendoButtonLayout(enabled)
            _uiState.update { it.copy(nintendoButtonLayout = enabled) }
        }
    }

    fun setAnimationSpeed(speed: AnimationSpeed) {
        viewModelScope.launch {
            preferencesRepository.setAnimationSpeed(speed)
            _uiState.update { it.copy(animationSpeed = speed) }
        }
    }

    fun showRegionPicker() {
        _uiState.update { it.copy(showRegionPicker = true, regionPickerFocusIndex = 0) }
    }

    fun dismissRegionPicker() {
        _uiState.update { it.copy(showRegionPicker = false, regionPickerFocusIndex = 0) }
    }

    fun moveRegionPickerFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = SyncFilterPreferences.ALL_KNOWN_REGIONS.size - 1
            state.copy(regionPickerFocusIndex = (state.regionPickerFocusIndex + delta).coerceIn(0, maxIndex))
        }
    }

    fun confirmRegionPickerSelection() {
        val state = _uiState.value
        val region = SyncFilterPreferences.ALL_KNOWN_REGIONS.getOrNull(state.regionPickerFocusIndex) ?: return
        toggleRegion(region)
    }

    fun toggleRegion(region: String) {
        viewModelScope.launch {
            val current = _uiState.value.syncFilters.enabledRegions
            val updated = if (region in current) current - region else current + region
            preferencesRepository.setSyncFilterRegions(updated)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(enabledRegions = updated))
            }
        }
    }

    fun toggleRegionMode() {
        viewModelScope.launch {
            val current = _uiState.value.syncFilters.regionMode
            val next = when (current) {
                RegionFilterMode.INCLUDE -> RegionFilterMode.EXCLUDE
                RegionFilterMode.EXCLUDE -> RegionFilterMode.INCLUDE
            }
            preferencesRepository.setSyncFilterRegionMode(next)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(regionMode = next))
            }
        }
    }

    fun setExcludeBeta(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludeBeta(exclude)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeBeta = exclude))
            }
        }
    }

    fun setExcludePrototype(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludePrototype(exclude)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(excludePrototype = exclude))
            }
        }
    }

    fun setExcludeDemo(exclude: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterExcludeDemo(exclude)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(excludeDemo = exclude))
            }
        }
    }

    fun setDeleteOrphans(delete: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSyncFilterDeleteOrphans(delete)
            _uiState.update {
                it.copy(syncFilters = it.syncFilters.copy(deleteOrphans = delete))
            }
        }
    }

    fun toggleSyncScreenshots() {
        viewModelScope.launch {
            val newValue = !_uiState.value.syncScreenshotsEnabled
            preferencesRepository.setSyncScreenshotsEnabled(newValue)
            _uiState.update { it.copy(syncScreenshotsEnabled = newValue) }
            if (newValue) {
                imageCacheManager.resumePendingScreenshotCache()
            }
        }
    }

    fun openFolderPicker() {
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun clearFolderPickerFlag() {
        _uiState.update { it.copy(launchFolderPicker = false) }
    }

    fun setStoragePath(uriString: String) {
        val currentState = _uiState.value
        if (currentState.downloadedGamesCount > 0 && currentState.romStoragePath.isNotBlank()) {
            _uiState.update {
                it.copy(
                    showMigrationDialog = true,
                    pendingStoragePath = uriString
                )
            }
        } else {
            applyStoragePath(uriString)
        }
    }

    fun confirmMigration() {
        val pendingPath = _uiState.value.pendingStoragePath ?: return
        _uiState.update { it.copy(showMigrationDialog = false) }
        migrateDownloads(pendingPath)
    }

    fun cancelMigration() {
        _uiState.update {
            it.copy(
                showMigrationDialog = false,
                pendingStoragePath = null
            )
        }
    }

    fun skipMigration() {
        val pendingPath = _uiState.value.pendingStoragePath ?: return
        _uiState.update { it.copy(showMigrationDialog = false) }
        applyStoragePath(pendingPath)
    }

    private fun applyStoragePath(uriString: String) {
        viewModelScope.launch {
            preferencesRepository.setRomStoragePath(uriString)
            _uiState.update {
                it.copy(
                    romStoragePath = uriString,
                    pendingStoragePath = null
                )
            }
        }
    }

    private fun migrateDownloads(newPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isMigrating = true, pendingStoragePath = null) }

            val oldPath = _uiState.value.romStoragePath
            val gamesWithPaths = gameRepository.getGamesWithLocalPaths()
            val totalGames = gamesWithPaths.size

            notificationManager.showPersistent(
                key = "migration",
                title = "Moving games",
                subtitle = "0 / $totalGames",
                progress = NotificationProgress(0, totalGames)
            )

            var migrated = 0
            var failed = 0

            var skipped = 0
            Log.d(TAG, "Migration: starting, oldPath=$oldPath, newPath=$newPath, games=${gamesWithPaths.size}")
            gamesWithPaths.forEachIndexed { index, game ->
                Log.d(TAG, "Migration: processing ${index + 1}/$totalGames - ${game.title}")
                Log.d(TAG, "Migration: localPath=${game.localPath}")

                notificationManager.updatePersistent(
                    key = "migration",
                    subtitle = "${index + 1}/$totalGames: ${game.title}",
                    progress = NotificationProgress(index, totalGames)
                )

                try {
                    val oldFile = java.io.File(game.localPath!!)
                    Log.d(TAG, "Migration: oldFile exists=${oldFile.exists()}, size=${if (oldFile.exists()) oldFile.length() else 0}")

                    if (oldFile.exists()) {
                        val relativePath = if (oldFile.absolutePath.startsWith(oldPath)) {
                            oldFile.absolutePath.removePrefix(oldPath).trimStart('/')
                        } else {
                            "${game.platformId}/${oldFile.name}"
                        }
                        val newFile = java.io.File(newPath, relativePath)
                        Log.d(TAG, "Migration: relativePath=$relativePath, newFile=${newFile.absolutePath}")

                        newFile.parentFile?.mkdirs()
                        Log.d(TAG, "Migration: starting copy...")

                        oldFile.copyTo(newFile, overwrite = true)
                        Log.d(TAG, "Migration: copy complete, updating DB...")

                        gameRepository.updateLocalPath(game.id, newFile.absolutePath)
                        oldFile.delete()
                        migrated++
                        Log.d(TAG, "Migration: success for ${game.title}")
                    } else {
                        Log.d(TAG, "Migration: file missing, clearing localPath")
                        gameRepository.clearLocalPath(game.id)
                        skipped++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Migration: FAILED for ${game.title}", e)
                    failed++
                }
            }
            Log.d(TAG, "Migration: complete - migrated=$migrated, skipped=$skipped, failed=$failed")

            preferencesRepository.setRomStoragePath(newPath)
            _uiState.update {
                it.copy(
                    romStoragePath = newPath,
                    isMigrating = false
                )
            }

            val message = buildString {
                append("Moved $migrated")
                if (skipped > 0) append(", $skipped missing")
                if (failed > 0) append(", $failed failed")
            }
            notificationManager.completePersistent(
                key = "migration",
                title = "Migration complete",
                subtitle = message,
                type = if (failed > 0) NotificationType.WARNING else NotificationType.SUCCESS
            )

            refreshCollectionStats()
        }
    }

    private fun refreshCollectionStats() {
        viewModelScope.launch {
            val downloadedSize = gameRepository.getDownloadedGamesSize()
            val downloadedCount = gameRepository.getDownloadedGamesCount()
            _uiState.update {
                it.copy(
                    downloadedGamesSize = downloadedSize,
                    downloadedGamesCount = downloadedCount
                )
            }
        }
    }

    fun setPlatformEmulator(platformId: String, emulator: InstalledEmulator?) {
        viewModelScope.launch {
            emulatorConfigDao.clearPlatformDefaults(platformId)

            if (emulator != null) {
                val config = EmulatorConfigEntity(
                    platformId = platformId,
                    gameId = null,
                    packageName = emulator.def.packageName,
                    displayName = emulator.def.displayName,
                    coreName = EmulatorRegistry.getRetroArchCores()[platformId],
                    isDefault = true
                )
                emulatorConfigDao.insert(config)
            }

            loadSettings()
        }
    }

    fun setRomStoragePath(path: String) {
        viewModelScope.launch {
            preferencesRepository.setRomStoragePath(path)
            _uiState.update { it.copy(romStoragePath = path) }
        }
    }

    fun syncRomm() {
        viewModelScope.launch {
            Log.d(TAG, "syncRomm: starting")
            if (!romMRepository.isConnected()) {
                Log.d(TAG, "syncRomm: not connected")
                notificationManager.show(
                    title = "Error",
                    subtitle = "RomM not connected",
                    type = NotificationType.ERROR
                )
                return@launch
            }

            Log.d(TAG, "syncRomm: fetching summary")
            when (val summary = romMRepository.getLibrarySummary()) {
                is RomMResult.Error -> {
                    Log.e(TAG, "syncRomm: summary error: ${summary.message}")
                    notificationManager.show(
                        title = "Error",
                        subtitle = summary.message,
                        type = NotificationType.ERROR
                    )
                    return@launch
                }
                is RomMResult.Success -> {
                    val (platformCount, _) = summary.data
                    Log.d(TAG, "syncRomm: got $platformCount platforms, showing persistent")
                    notificationManager.showPersistent(
                        title = "Syncing Library",
                        subtitle = "Starting...",
                        key = "romm-sync",
                        progress = NotificationProgress(0, platformCount)
                    )

                    try {
                        withContext(NonCancellable) {
                            Log.d(TAG, "syncRomm: calling syncLibrary")
                            val result = romMRepository.syncLibrary { current, total, platform ->
                                Log.d(TAG, "syncRomm: progress $current/$total - $platform")
                                notificationManager.updatePersistent(
                                    key = "romm-sync",
                                    subtitle = platform,
                                    progress = NotificationProgress(current, total)
                                )
                            }

                            Log.d(TAG, "syncRomm: syncLibrary returned - added=${result.gamesAdded}, updated=${result.gamesUpdated}, errors=${result.errors}")
                            Log.d(TAG, "syncRomm: loading settings")
                            loadSettings()

                            if (result.errors.isEmpty()) {
                                Log.d(TAG, "syncRomm: completing with success")
                                val subtitle = buildString {
                                    append("${result.gamesAdded} added, ${result.gamesUpdated} updated")
                                    if (result.gamesDeleted > 0) {
                                        append(", ${result.gamesDeleted} removed")
                                    }
                                }
                                notificationManager.completePersistent(
                                    key = "romm-sync",
                                    title = "Sync complete",
                                    subtitle = subtitle,
                                    type = NotificationType.SUCCESS
                                )
                            } else {
                                Log.d(TAG, "syncRomm: completing with errors")
                                notificationManager.completePersistent(
                                    key = "romm-sync",
                                    title = "Sync completed with errors",
                                    subtitle = "${result.errors.size} platform(s) failed",
                                    type = NotificationType.ERROR
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "syncRomm: exception", e)
                        withContext(NonCancellable) {
                            notificationManager.completePersistent(
                                key = "romm-sync",
                                title = "Sync failed",
                                subtitle = e.message,
                                type = NotificationType.ERROR
                            )
                        }
                    }
                }
            }
            Log.d(TAG, "syncRomm: done")
        }
    }

    fun startRommConfig() {
        _uiState.update {
            it.copy(
                rommConfiguring = true,
                rommConfigUrl = it.rommUrl,
                rommConfigUsername = it.rommUsername,
                rommConfigPassword = "",
                rommConfigError = null,
                focusedIndex = 0
            )
        }
    }

    fun cancelRommConfig() {
        _uiState.update {
            it.copy(
                rommConfiguring = false,
                rommConfigUrl = "",
                rommConfigUsername = "",
                rommConfigPassword = "",
                rommConfigError = null,
                focusedIndex = 0
            )
        }
    }

    fun setRommConfigUrl(url: String) {
        _uiState.update { it.copy(rommConfigUrl = url) }
    }

    fun setRommConfigUsername(username: String) {
        _uiState.update { it.copy(rommConfigUsername = username) }
    }

    fun setRommConfigPassword(password: String) {
        _uiState.update { it.copy(rommConfigPassword = password) }
    }

    fun clearRommFocusField() {
        _uiState.update { it.copy(rommFocusField = null) }
    }

    fun connectToRomm() {
        val state = _uiState.value
        if (state.rommConfigUrl.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(rommConnecting = true, rommConfigError = null) }

            when (val result = romMRepository.connect(state.rommConfigUrl)) {
                is RomMResult.Success -> {
                    if (state.rommConfigUsername.isNotBlank() && state.rommConfigPassword.isNotBlank()) {
                        when (val loginResult = romMRepository.login(state.rommConfigUsername, state.rommConfigPassword)) {
                            is RomMResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        rommConnecting = false,
                                        rommConfiguring = false,
                                        connectionStatus = ConnectionStatus.ONLINE,
                                        rommUrl = state.rommConfigUrl,
                                        rommUsername = state.rommConfigUsername
                                    )
                                }
                                loadSettings()
                            }
                            is RomMResult.Error -> {
                                _uiState.update {
                                    it.copy(rommConnecting = false, rommConfigError = loginResult.message)
                                }
                            }
                        }
                    } else {
                        _uiState.update {
                            it.copy(rommConnecting = false, rommConfigError = "Username and password required")
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update {
                        it.copy(rommConnecting = false, rommConfigError = result.message)
                    }
                }
            }
        }
    }

    fun handleConfirm() {
        val state = _uiState.value
        when (state.currentSection) {
            SettingsSection.MAIN -> {
                val section = when (state.focusedIndex) {
                    0 -> SettingsSection.APPEARANCE
                    1 -> SettingsSection.EMULATORS
                    2 -> SettingsSection.COLLECTION
                    3 -> SettingsSection.ABOUT
                    else -> null
                }
                section?.let { navigateToSection(it) }
            }
            SettingsSection.APPEARANCE -> {
                when (state.focusedIndex) {
                    0 -> {
                        val next = when (state.themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                        setThemeMode(next)
                    }
                    2 -> setHapticEnabled(!state.hapticEnabled)
                    3 -> setNintendoButtonLayout(!state.nintendoButtonLayout)
                    4 -> {
                        val next = when (state.animationSpeed) {
                            AnimationSpeed.SLOW -> AnimationSpeed.NORMAL
                            AnimationSpeed.NORMAL -> AnimationSpeed.FAST
                            AnimationSpeed.FAST -> AnimationSpeed.OFF
                            AnimationSpeed.OFF -> AnimationSpeed.SLOW
                        }
                        setAnimationSpeed(next)
                    }
                }
            }
            SettingsSection.EMULATORS -> {
                val focusOffset = if (state.canAutoAssign) 1 else 0
                if (state.canAutoAssign && state.focusedIndex == 0) {
                    autoAssignAllEmulators()
                } else {
                    val platformIndex = state.focusedIndex - focusOffset
                    val config = state.platforms.getOrNull(platformIndex)
                    if (config != null) {
                        showEmulatorPicker(config)
                    }
                }
            }
            SettingsSection.COLLECTION -> {
                when {
                    state.rommConfiguring -> when (state.focusedIndex) {
                        0, 1, 2 -> _uiState.update { it.copy(rommFocusField = state.focusedIndex) }
                        3 -> connectToRomm()
                        4 -> cancelRommConfig()
                    }
                    else -> when (state.focusedIndex) {
                        0 -> navigateToSection(SettingsSection.LIBRARY_BREAKDOWN)
                        1 -> openFolderPicker()
                        2 -> startRommConfig()
                        3 -> navigateToSection(SettingsSection.SYNC_FILTERS)
                        4 -> {} // Sync Images - info only
                        5 -> toggleSyncScreenshots()
                        6 -> if (state.connectionStatus == ConnectionStatus.ONLINE) syncRomm()
                    }
                }
            }
            SettingsSection.SYNC_FILTERS -> {
                when (state.focusedIndex) {
                    0 -> showRegionPicker()
                    1 -> toggleRegionMode()
                    2 -> setExcludeBeta(!state.syncFilters.excludeBeta)
                    3 -> setExcludePrototype(!state.syncFilters.excludePrototype)
                    4 -> setExcludeDemo(!state.syncFilters.excludeDemo)
                    5 -> setDeleteOrphans(!state.syncFilters.deleteOrphans)
                }
            }
            else -> {}
        }
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            moveFocus(-1)
            return true
        }

        override fun onDown(): Boolean {
            moveFocus(1)
            return true
        }

        override fun onLeft(): Boolean {
            val state = _uiState.value
            if (state.currentSection == SettingsSection.APPEARANCE && state.focusedIndex == 1) {
                moveColorFocus(-1)
                return true
            }
            return false
        }

        override fun onRight(): Boolean {
            val state = _uiState.value
            if (state.currentSection == SettingsSection.APPEARANCE && state.focusedIndex == 1) {
                moveColorFocus(1)
                return true
            }
            return false
        }

        override fun onConfirm(): Boolean {
            val state = _uiState.value
            if (state.showRegionPicker) {
                confirmRegionPickerSelection()
                return true
            }
            if (state.showEmulatorPicker) {
                confirmEmulatorPickerSelection()
                return true
            }
            if (state.currentSection == SettingsSection.APPEARANCE && state.focusedIndex == 1) {
                selectFocusedColor()
                return true
            }
            handleConfirm()
            return true
        }

        override fun onBack(): Boolean {
            return if (!navigateBack()) {
                onBack()
                true
            } else {
                true
            }
        }

        override fun onMenu(): Boolean = false
    }
}
