package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.HapticIntensity
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundPreset
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.BuildConfig

enum class SettingsSection {
    MAIN,
    SERVER,
    SYNC_SETTINGS,
    SYNC_FILTERS,
    STEAM_SETTINGS,
    STORAGE,
    DISPLAY,
    BOX_ART,
    HOME_SCREEN,
    CONTROLS,
    SOUNDS,
    EMULATORS,
    ABOUT
}

enum class BoxArtPreviewRatio(val ratio: Float, val displayName: String) {
    VERTICAL_2_3(2f / 3f, "2:3"),
    VERTICAL_3_4(3f / 4f, "3:4"),
    SQUARE_1_1(1f, "1:1")
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
    val effectiveEmulatorName: String? = null,
    val effectiveSavePath: String? = null,
    val isUserSavePathOverride: Boolean = false,
    val showSavePath: Boolean = false
) {
    val hasInstalledEmulators: Boolean get() = availableEmulators.isNotEmpty()
    val isRetroArchSelected: Boolean get() = selectedEmulatorPackage?.startsWith("com.retroarch") == true
    val showCoreSelection: Boolean get() = effectiveEmulatorIsRetroArch && availableCores.isNotEmpty()
}

data class EmulatorPickerInfo(
    val platformId: String,
    val platformSlug: String,
    val platformName: String,
    val installedEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef>,
    val selectedEmulatorName: String?
)

data class CorePickerInfo(
    val platformId: String,
    val platformSlug: String,
    val platformName: String,
    val availableCores: List<RetroArchCore>,
    val selectedCoreId: String?
)

data class DisplayState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val useAccentColorFooter: Boolean = false,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val defaultView: DefaultView = DefaultView.SHOWCASE
)

data class ControlsState(
    val hapticEnabled: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val abIconLayout: String = "auto",
    val detectedLayout: String? = null,
    val swapStartSelect: Boolean = false
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
    val savePathModalButtonIndex: Int = 0
)

data class SavePathModalInfo(
    val emulatorId: String,
    val emulatorName: String,
    val platformName: String,
    val savePath: String?,
    val isUserOverride: Boolean
)

data class PlatformStorageConfig(
    val platformId: String,
    val platformName: String,
    val gameCount: Int,
    val downloadedCount: Int = 0,
    val syncEnabled: Boolean,
    val customRomPath: String?,
    val effectivePath: String,
    val isExpanded: Boolean = false
)

data class StorageState(
    val romStoragePath: String = "",
    val downloadedGamesSize: Long = 0,
    val downloadedGamesCount: Int = 0,
    val maxConcurrentDownloads: Int = 1,
    val availableSpace: Long = 0,
    val hasAllFilesAccess: Boolean = false,
    val platformConfigs: List<PlatformStorageConfig> = emptyList(),
    val showPurgePlatformConfirm: String? = null,
    val showMigratePlatformConfirm: PlatformMigrationInfo? = null,
    val platformSettingsModalId: String? = null,
    val platformSettingsFocusIndex: Int = 0
)

data class PlatformMigrationInfo(
    val platformId: String,
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

data class SyncSettingsState(
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val showRegionPicker: Boolean = false,
    val regionPickerFocusIndex: Int = 0,
    val totalGames: Int = 0,
    val totalPlatforms: Int = 0,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val saveCacheLimit: Int = 10,
    val pendingUploadsCount: Int = 0,
    val hasStoragePermission: Boolean = false,
    val isSyncing: Boolean = false
)

data class InstalledSteamLauncher(
    val packageName: String,
    val displayName: String,
    val gameCount: Int = 0,
    val supportsScanning: Boolean = false
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

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.MAIN,
    val focusedIndex: Int = 0,
    val colorFocusIndex: Int = 0,
    val display: DisplayState = DisplayState(),
    val controls: ControlsState = ControlsState(),
    val sounds: SoundState = SoundState(),
    val emulators: EmulatorState = EmulatorState(),
    val server: ServerState = ServerState(),
    val storage: StorageState = StorageState(),
    val syncSettings: SyncSettingsState = SyncSettingsState(),
    val steam: SteamSettingsState = SteamSettingsState(),
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
    val boxArtPreviewRatio: BoxArtPreviewRatio = BoxArtPreviewRatio.VERTICAL_3_4
)
