package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientExtractionResult
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.ExtensionOption
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.GlassBorderTint
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundPreset
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.BuildConfig

enum class SettingsSection {
    MAIN,
    SERVER,
    SYNC_SETTINGS,
    STEAM_SETTINGS,
    STORAGE,
    BIOS,
    INTERFACE,
    BOX_ART,
    HOME_SCREEN,
    CONTROLS,
    EMULATORS,
    PERMISSIONS,
    ABOUT
}

enum class ConnectionStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    NOT_CONFIGURED
}

data class PlatformEmulatorConfig(
    val platform: PlatformEntity,
    val selectedEmulator: String?,
    val selectedEmulatorPackage: String? = null,
    val selectedCore: String? = null,
    val isUserConfigured: Boolean,
    val availableEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef> = emptyList(),
    val availableCores: List<RetroArchCore> = emptyList(),
    val effectiveEmulatorIsRetroArch: Boolean = false,
    val effectiveEmulatorId: String? = null,
    val effectiveEmulatorPackage: String? = null,
    val effectiveEmulatorName: String? = null,
    val effectiveSavePath: String? = null,
    val isUserSavePathOverride: Boolean = false,
    val showSavePath: Boolean = false,
    val extensionOptions: List<ExtensionOption> = emptyList(),
    val selectedExtension: String? = null
) {
    val hasInstalledEmulators: Boolean get() = availableEmulators.isNotEmpty()
    val isRetroArchSelected: Boolean get() = selectedEmulatorPackage?.startsWith("com.retroarch") == true
    val showCoreSelection: Boolean get() = effectiveEmulatorIsRetroArch && availableCores.isNotEmpty()
    val showExtensionSelection: Boolean get() = extensionOptions.isNotEmpty()
}

data class EmulatorPickerInfo(
    val platformId: Long,
    val platformSlug: String,
    val platformName: String,
    val installedEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef>,
    val selectedEmulatorName: String?
)

data class CorePickerInfo(
    val platformId: Long,
    val platformSlug: String,
    val platformName: String,
    val availableCores: List<RetroArchCore>,
    val selectedCoreId: String?
)

data class DisplayState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val useAccentColorFooter: Boolean = false,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID,
    val glassBorderTint: GlassBorderTint = GlassBorderTint.OFF,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.MEDIUM,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.SHADOW,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.MEDIUM,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val gradientAdvancedMode: Boolean = false,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val defaultView: DefaultView = DefaultView.HOME,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelaySeconds: Int = 3,
    val videoWallpaperMuted: Boolean = false,
    val uiScale: Int = 100,
    val ambientLedEnabled: Boolean = false,
    val ambientLedBrightness: Int = 100,
    val ambientLedAudioBrightness: Boolean = true,
    val ambientLedAudioColors: Boolean = false,
    val ambientLedColorMode: AmbientLedColorMode = AmbientLedColorMode.DOMINANT_3,
    val ambientLedAvailable: Boolean = false,
    val hasScreenCapturePermission: Boolean = true
)

data class ControlsState(
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = 0.5f,
    val vibrationSupported: Boolean = false,
    val controllerLayout: String = "auto",
    val detectedLayout: String? = null,
    val detectedDeviceName: String? = null,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val swapStartSelect: Boolean = false,
    val accuratePlayTimeEnabled: Boolean = false,
    val hasUsageStatsPermission: Boolean = false
)

data class SoundState(
    val enabled: Boolean = false,
    val volume: Int = 40,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val showSoundPicker: Boolean = false,
    val soundPickerType: SoundType? = null,
    val soundPickerFocusIndex: Int = 0
) {
    val presets: List<SoundPreset> get() = SoundPreset.entries.toList()

    fun getCurrentPresetForType(type: SoundType): SoundPreset? {
        val config = soundConfigs[type] ?: return null
        return SoundPreset.entries.find { it.name == config.presetName }
    }

    fun getDisplayNameForType(type: SoundType): String {
        val config = soundConfigs[type] ?: return "Default"
        if (config.customFilePath != null) return "Custom"
        return SoundPreset.entries.find { it.name == config.presetName }?.displayName ?: "Default"
    }
}

data class AmbientAudioState(
    val enabled: Boolean = false,
    val volume: Int = 50,
    val audioUri: String? = null,
    val audioFileName: String? = null
)

data class EmulatorState(
    val platforms: List<PlatformEmulatorConfig> = emptyList(),
    val installedEmulators: List<InstalledEmulator> = emptyList(),
    val canAutoAssign: Boolean = false,
    val platformSubFocusIndex: Int = 0,
    val showEmulatorPicker: Boolean = false,
    val emulatorPickerInfo: EmulatorPickerInfo? = null,
    val emulatorPickerFocusIndex: Int = 0,
    val emulatorPickerSelectedIndex: Int? = null,
    val showSavePathModal: Boolean = false,
    val savePathModalInfo: SavePathModalInfo? = null,
    val savePathModalFocusIndex: Int = 0,
    val savePathModalButtonIndex: Int = 0,
    val installedCoreCount: Int = 0,
    val totalCoreCount: Int = 0,
    val coreUpdatesAvailable: Int = 0
)

data class SavePathModalInfo(
    val emulatorId: String,
    val emulatorName: String,
    val platformName: String,
    val savePath: String?,
    val isUserOverride: Boolean
)

data class PlatformStorageConfig(
    val platformId: Long,
    val platformName: String,
    val gameCount: Int,
    val downloadedCount: Int = 0,
    val syncEnabled: Boolean,
    val customRomPath: String?,
    val effectivePath: String,
    val isExpanded: Boolean = false,
    val emulatorId: String? = null,
    val emulatorName: String? = null,
    val effectiveSavePath: String? = null,
    val customSavePath: String? = null,
    val isUserSavePathOverride: Boolean = false,
    val effectiveStatePath: String? = null,
    val customStatePath: String? = null,
    val isUserStatePathOverride: Boolean = false,
    val supportsStatePath: Boolean = false
)

data class EmulatorSavePathInfo(
    val emulatorId: String,
    val emulatorName: String,
    val savePath: String?,
    val isCustom: Boolean
)

data class StorageState(
    val romStoragePath: String = "",
    val downloadedGamesSize: Long = 0,
    val downloadedGamesCount: Int = 0,
    val maxConcurrentDownloads: Int = 1,
    val instantDownloadThresholdMb: Int = 50,
    val availableSpace: Long = 0,
    val hasAllFilesAccess: Boolean = false,
    val platformConfigs: List<PlatformStorageConfig> = emptyList(),
    val showPurgePlatformConfirm: Long? = null,
    val showMigratePlatformConfirm: PlatformMigrationInfo? = null,
    val platformSettingsModalId: Long? = null,
    val platformSettingsFocusIndex: Int = 0,
    val platformSettingsButtonIndex: Int = 0,
    val platformsExpanded: Boolean = false,
    val screenDimmerEnabled: Boolean = true,
    val screenDimmerTimeoutMinutes: Int = 2,
    val screenDimmerLevel: Int = 30,
    val isValidatingCache: Boolean = false,
    val showPurgeAllConfirm: Boolean = false,
    val isPurgingAll: Boolean = false
) {
    val customPlatformCount: Int get() = platformConfigs.count { it.customRomPath != null }
}

data class PlatformMigrationInfo(
    val platformId: Long,
    val platformName: String,
    val oldPath: String,
    val newPath: String,
    val isResetToGlobal: Boolean
)

data class ServerState(
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
    val syncScreenshotsEnabled: Boolean = false
)

data class PlatformFilterItem(
    val id: Long,
    val name: String,
    val slug: String,
    val romCount: Int,
    val syncEnabled: Boolean
)

data class SyncSettingsState(
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val showSyncFiltersModal: Boolean = false,
    val syncFiltersModalFocusIndex: Int = 0,
    val showRegionPicker: Boolean = false,
    val regionPickerFocusIndex: Int = 0,
    val showPlatformFiltersModal: Boolean = false,
    val platformFiltersModalFocusIndex: Int = 0,
    val platformFiltersList: List<PlatformFilterItem> = emptyList(),
    val isLoadingPlatforms: Boolean = false,
    val enabledPlatformCount: Int = 0,
    val totalGames: Int = 0,
    val totalPlatforms: Int = 0,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val saveCacheLimit: Int = 10,
    val pendingUploadsCount: Int = 0,
    val hasStoragePermission: Boolean = false,
    val isSyncing: Boolean = false,
    val imageCachePath: String? = null,
    val defaultImageCachePath: String? = null,
    val imageCacheActionIndex: Int = 0,
    val isImageCacheMigrating: Boolean = false
)

data class InstalledSteamLauncher(
    val packageName: String,
    val displayName: String,
    val gameCount: Int = 0,
    val supportsScanning: Boolean = false,
    val scanMayIncludeUninstalled: Boolean = false
)

data class SteamSettingsState(
    val hasStoragePermission: Boolean = false,
    val installedLaunchers: List<InstalledSteamLauncher> = emptyList(),
    val isSyncing: Boolean = false,
    val syncingLauncher: String? = null,
    val showAddGameDialog: Boolean = false,
    val addGameAppId: String = "",
    val addGameError: String? = null,
    val isAddingGame: Boolean = false,
    val selectedLauncherPackage: String? = null,
    val launcherActionIndex: Int = 0
)

data class AndroidSettingsState(
    val isScanning: Boolean = false,
    val scanProgressPercent: Int = 0,
    val currentApp: String = "",
    val gamesFound: Int = 0,
    val lastScanGamesAdded: Int? = null
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val hasChecked: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val readyToInstall: Boolean = false
)

data class PermissionsState(
    val hasStorageAccess: Boolean = false,
    val hasUsageStats: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasWriteSettings: Boolean = false,
    val isWriteSettingsRelevant: Boolean = false,
    val hasScreenCapture: Boolean = false,
    val isScreenCaptureRelevant: Boolean = false
) {
    val allGranted: Boolean get() = hasStorageAccess && hasUsageStats && hasNotificationPermission &&
        (!isWriteSettingsRelevant || hasWriteSettings) &&
        (!isScreenCaptureRelevant || hasScreenCapture)
    val grantedCount: Int get() = listOf(
        hasStorageAccess,
        hasUsageStats,
        hasNotificationPermission,
        if (isWriteSettingsRelevant) hasWriteSettings else null,
        if (isScreenCaptureRelevant) hasScreenCapture else null
    ).count { it == true }
    val totalCount: Int get() {
        var count = 3
        if (isWriteSettingsRelevant) count++
        if (isScreenCaptureRelevant) count++
        return count
    }
}

data class BiosFirmwareItem(
    val id: Long,
    val rommId: Long,
    val platformSlug: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val isDownloaded: Boolean,
    val localPath: String? = null
)

data class BiosPlatformGroup(
    val platformSlug: String,
    val platformName: String,
    val totalFiles: Int,
    val downloadedFiles: Int,
    val firmwareItems: List<BiosFirmwareItem>,
    val isExpanded: Boolean = false
) {
    val isComplete: Boolean get() = downloadedFiles == totalFiles
    val statusText: String get() = "$downloadedFiles / $totalFiles"
}

data class DistributeResultItem(
    val emulatorId: String,
    val emulatorName: String,
    val platformResults: List<PlatformDistributeResult>
)

data class PlatformDistributeResult(
    val platformSlug: String,
    val platformName: String,
    val filesCopied: Int
)

data class BiosState(
    val platformGroups: List<BiosPlatformGroup> = emptyList(),
    val totalFiles: Int = 0,
    val downloadedFiles: Int = 0,
    val isDownloading: Boolean = false,
    val downloadingFileName: String? = null,
    val downloadProgress: Float = 0f,
    val isDistributing: Boolean = false,
    val showDistributeResultModal: Boolean = false,
    val distributeResults: List<DistributeResultItem> = emptyList(),
    val customBiosPath: String? = null,
    val expandedPlatformIndex: Int = -1,
    val platformSubFocusIndex: Int = 0,
    val actionIndex: Int = 0
) {
    val missingFiles: Int get() = totalFiles - downloadedFiles
    val isComplete: Boolean get() = totalFiles > 0 && downloadedFiles == totalFiles
    val summaryText: String get() = when {
        totalFiles == 0 -> "No BIOS files found"
        isComplete -> "All $totalFiles files downloaded"
        else -> "$downloadedFiles of $totalFiles downloaded"
    }
}

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.MAIN,
    val focusedIndex: Int = 0,
    val colorFocusIndex: Int = 0,
    val display: DisplayState = DisplayState(),
    val controls: ControlsState = ControlsState(),
    val sounds: SoundState = SoundState(),
    val ambientAudio: AmbientAudioState = AmbientAudioState(),
    val emulators: EmulatorState = EmulatorState(),
    val server: ServerState = ServerState(),
    val storage: StorageState = StorageState(),
    val syncSettings: SyncSettingsState = SyncSettingsState(),
    val steam: SteamSettingsState = SteamSettingsState(),
    val android: AndroidSettingsState = AndroidSettingsState(),
    val bios: BiosState = BiosState(),
    val permissions: PermissionsState = PermissionsState(),
    val launchFolderPicker: Boolean = false,
    val showMigrationDialog: Boolean = false,
    val pendingStoragePath: String? = null,
    val isMigrating: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val updateCheck: UpdateCheckState = UpdateCheckState(),
    val betaUpdatesEnabled: Boolean = false,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val previewGame: GameListItem? = null,
    val previewGames: List<GameListItem> = emptyList(),
    val previewGameIndex: Int = 0,
    val gradientConfig: GradientExtractionConfig = GradientExtractionConfig(),
    val gradientExtractionResult: GradientExtractionResult? = null
)
