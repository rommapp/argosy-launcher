package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.core.emulator.EmulatorDownloadState
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.ui.common.GradientExtractionResult
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.ExtensionOption
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
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
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.ui.input.SoundPreset
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.util.PlatformFilterLogic
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.social.discord.DiscordPresenceState

enum class BuiltinPathType { SAVE, STATE }

data class BuiltinPathMigration(
    val pathType: BuiltinPathType,
    val oldPath: String,
    val newPath: String,
    val existingFileCount: Int
)

enum class SettingsSection {
    MAIN,
    SERVER,
    SYNC_SETTINGS,
    STEAM_SETTINGS,
    RETRO_ACHIEVEMENTS,
    STORAGE,
    BIOS,
    INTERFACE,
    BOX_ART,
    HOME_SCREEN,
    AMBIENT_LED,
    CONTROLS,
    PLATFORMS,
    BUILTIN_EMULATOR,
    BUILTIN_VIDEO,
    BUILTIN_CONTROLS,
    SHADER_STACK,
    FRAME_PICKER,
    CORE_MANAGEMENT,
    CORE_OPTIONS,
    PLATFORM_DETAIL,
    SOCIAL,
    PERMISSIONS,
    DRIVERS,
    ABOUT
}

enum class ConnectionStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    NOT_CONFIGURED
}

enum class RomMAuthMethod {
    PAIRING_CODE,
    PASSWORD
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
    val selectedExtension: String? = null,
    val useFileUri: Boolean = false,
    val displayTarget: EmulatorDisplayTarget = EmulatorDisplayTarget.TOP,
    val hasSecondaryDisplay: Boolean = false
) {
    val hasInstalledEmulators: Boolean get() = availableEmulators.isNotEmpty()
    val isRetroArchSelected: Boolean get() = selectedEmulatorPackage?.startsWith("com.retroarch") == true
    val showCoreSelection: Boolean get() = (effectiveEmulatorIsRetroArch || effectiveEmulatorId == "builtin") && availableCores.isNotEmpty()
    val showExtensionSelection: Boolean get() = extensionOptions.isNotEmpty()
    val showLegacyModeOption: Boolean get() = effectiveEmulatorId == "drastic"
    val showDisplayTargetOption: Boolean get() = hasSecondaryDisplay
}

data class EmulatorUpdateInfo(
    val emulatorId: String,
    val currentVersion: String?,
    val latestVersion: String,
    val downloadUrl: String,
    val assetName: String,
    val assetSize: Long,
    val installedVariant: String? = null
)

data class EmulatorPickerInfo(
    val platformId: Long,
    val platformSlug: String,
    val platformName: String,
    val installedEmulators: List<InstalledEmulator>,
    val downloadableEmulators: List<EmulatorDef>,
    val selectedEmulatorName: String?,
    val updates: Map<String, EmulatorUpdateInfo> = emptyMap(),
    val downloadState: EmulatorDownloadState = EmulatorDownloadState.Idle,
    val downloadingEmulatorId: String? = null
)

data class VariantOption(
    val assetName: String,
    val downloadUrl: String,
    val fileSize: Long,
    val variant: String
)

data class VariantPickerInfo(
    val emulatorId: String,
    val emulatorName: String,
    val variants: List<VariantOption>
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
    val boxArtNativeAspectRatio: Boolean = false,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.SOLID,
    val glassBorderTint: GlassBorderTint = GlassBorderTint.OFF,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.MEDIUM,
    val glowColorMode: GlowColorMode = GlowColorMode.AUTO,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.SHADOW,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.MEDIUM,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val gradientAdvancedMode: Boolean = false,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val platformIndicatorStyle: PlatformIndicatorStyle = PlatformIndicatorStyle.TAB,
    val platformIndicatorContent: PlatformIndicatorContent = PlatformIndicatorContent.NAME,
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
    val ambientLedCoverArtEnabled: Boolean = true,
    val ambientLedCustomColor: Boolean = false,
    val ambientLedCustomColorHue: Int = 200,
    val ambientLedTransitionMs: Int = 250,
    val ambientLedScreenEnabled: Boolean = false,
    val ambientLedAvailable: Boolean = false,
    val hasScreenCapturePermission: Boolean = true,
    val hasSecondaryDisplay: Boolean = false,
    val hasPhysicalSecondaryDisplay: Boolean = false,
    val dualScreenEnabled: Boolean = false,
    val displayRoleOverride: DisplayRoleOverride = DisplayRoleOverride.AUTO,
    val installedOnlyHome: Boolean = false
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
    val selectLCombo: String = "quick_menu",
    val selectRCombo: String = "quick_settings",
    val accuratePlayTimeEnabled: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val hasSecondaryDisplay: Boolean = false,
    val menuWrapMode: com.nendo.argosy.data.preferences.MenuWrapMode = com.nendo.argosy.data.preferences.MenuWrapMode.HARD_STOP
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
    val audioFileName: String? = null,
    val isFolder: Boolean = false,
    val shuffle: Boolean = false,
    val currentTrackName: String? = null
)

data class EmulatorState(
    val platforms: List<PlatformEmulatorConfig> = emptyList(),
    val installedEmulators: List<InstalledEmulator> = emptyList(),
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
    val coreUpdatesAvailable: Int = 0,
    val builtinLibretroEnabled: Boolean = true,
    val architectureDisplay: String = "",
    val emulatorUpdateVersions: Map<String, String> = emptyMap(),
    val showVariantPicker: Boolean = false,
    val variantPickerInfo: VariantPickerInfo? = null,
    val variantPickerFocusIndex: Int = 0,
    val updateModal: EmulatorUpdateModal? = null,
    val updateModalFocusIndex: Int = 0,
    val showLaunchArgsModal: Boolean = false,
    val launchArgsModalState: LaunchArgsModalState? = null,
    val showAppPickerModal: Boolean = false,
    val appPickerModalState: AppPickerModalState? = null,
    val showMemcardPicker: Boolean = false,
    val memcardPickerInfo: MemcardPickerInfo? = null,
    val memcardPickerFocusIndex: Int = 0
) {
    val assignedUpdatesAvailable: Int
        get() = platforms.count { config ->
            config.effectiveEmulatorId != null && config.effectiveEmulatorId in emulatorUpdateVersions
        }
}

data class EmulatorUpdateModal(
    val emulatorId: String,
    val emulatorName: String,
    val state: UpdateModalState = UpdateModalState.Fetching
)

data class AppPickerModalState(
    val platformId: Long,
    val platformName: String,
    val platformSlug: String,
    val apps: List<com.nendo.argosy.data.platform.LaunchableApp> = emptyList(),
    val focusIndex: Int = 0
)

data class LaunchArgsModalState(
    val platformId: Long,
    val platformName: String,
    val emulatorId: String,
    val emulatorName: String,
    val focusIndex: Int = 0,
    val override: com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity? = null,
    val defaultLaunchMethod: String = "INTENT",
    val defaultFlagsMask: Int = 0,
    val defaultMimeType: String? = null,
    val defaultDataBinding: String = "None",
    val defaultExtraBinding: String = "None",
    val defaultClipDataBinding: String = "None",
    /** Opaque data binding (scheme URI, game ID) -- not user-cycleable. */
    val dataBindingLocked: Boolean = false,
    /** Non-path extras (title ID array) -- not user-cycleable. */
    val extraBindingLocked: Boolean = false,
    val showCustomExtrasInput: Boolean = false
)

sealed class UpdateModalState {
    data object Fetching : UpdateModalState()
    data class SelectVariant(val variants: List<VariantOption>) : UpdateModalState()
    data class Downloading(val progress: Float) : UpdateModalState()
    data object WaitingForInstall : UpdateModalState()
    data object Installed : UpdateModalState()
    data class Failed(val message: String) : UpdateModalState()
}

data class PlatformContext(
    val platformId: Long,
    val platformName: String,
    val platformSlug: String
)

data class PlatformDetailState(
    val platformIndex: Int = 0,
    val builtinEnteredFromPlatform: Boolean = false,
    val enteredExternally: Boolean = false,
    val showRemoveConfirm: Boolean = false,
    val totalGames: Int = 0,
    val downloadedGames: Int = 0,
    val favorites: Int = 0,
    val totalPlayTimeMs: Long = 0,
    val packagePathAccessible: Boolean? = null,
    val biosTotal: Int = 0,
    val biosDownloaded: Int = 0,
    val hasBiosRequirements: Boolean = false,
    val effectiveRomPath: String? = null,
    val customRomPath: String? = null,
    val effectiveSavePath: String? = null,
    val isUserSavePathOverride: Boolean = false,
    val effectiveStatePath: String? = null,
    val isUserStatePathOverride: Boolean = false,
    val supportsStatePath: Boolean = false,
    val isScanning: Boolean = false,
    val downloadedSizeBytes: Long = 0
) {
    val playTimeFormatted: String get() {
        if (totalPlayTimeMs <= 0) return "--"
        val totalMinutes = totalPlayTimeMs / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

data class BuiltinVideoState(
    val shader: String = "None",
    val shaderChainJson: String = "",
    val filter: String = "Auto",
    val aspectRatio: String = "Core Provided",
    val skipDuplicateFrames: Boolean = false,
    val blackFrameInsertion: Boolean = false,
    val displayRefreshRate: Float = 60f,
    val fastForwardEnabled: Boolean = true,
    val fastForwardSpeed: String = "4x",
    val rotation: String = "Auto",
    val overscanCrop: String = "Off",
    val framesEnabled: Boolean = false,
    val lowLatencyAudio: Boolean = true,
    val vsync: Boolean = true,
    val rewindEnabled: Boolean = true,
    val rewindSpeed: String = "1x",
    val rewindBufferDuration: String = "15s",
    val autoSaveState: Boolean = true,
    val autoRestoreState: Boolean = true,
    val savePath: String = "",
    val statePath: String = "",
    val isCustomSavePath: Boolean = false,
    val isCustomStatePath: Boolean = false,
    val platformContextIndex: Int = 0,
    val availablePlatforms: List<PlatformContext> = emptyList()
) {
    val canEnableBlackFrameInsertion: Boolean get() = displayRefreshRate >= 120f
    val isGlobalContext: Boolean get() = platformContextIndex == 0
    val currentPlatformContext: PlatformContext? get() =
        if (platformContextIndex > 0 && platformContextIndex <= availablePlatforms.size)
            availablePlatforms[platformContextIndex - 1]
        else null
    val shaderDisplayValue: String get() = shader
}

data class ShaderStackState(
    val entries: List<ShaderStackEntry> = emptyList(),
    val selectedIndex: Int = 0,
    val paramFocusIndex: Int = 0,
    val selectedShaderParams: List<ShaderParamDef> = emptyList(),
    val showShaderPicker: Boolean = false,
    val shaderPickerFocusIndex: Int = 0,
    val shaderPickerCategory: String? = null,
    val downloadingShaderId: String? = null,
    val preInstallsSynced: Boolean = false
) {
    val isEmpty: Boolean get() = entries.isEmpty()
    val selectedEntry: ShaderStackEntry? get() = entries.getOrNull(selectedIndex)
    val maxParamFocusIndex: Int get() = (selectedShaderParams.size - 1).coerceAtLeast(0)
}

data class ShaderParamDef(
    val name: String,
    val description: String,
    val initial: Float,
    val min: Float,
    val max: Float,
    val step: Float
)

data class ShaderStackEntry(
    val shaderId: String,
    val displayName: String,
    val params: Map<String, String> = emptyMap()
)

data class BuiltinControlsState(
    val rumbleEnabled: Boolean = true,
    val limitHotkeysToPlayer1: Boolean = true,
    val fastForwardMode: com.nendo.argosy.data.local.entity.FastForwardMode = com.nendo.argosy.data.local.entity.FastForwardMode.HOLD,
    val fastForwardPreservePitch: Boolean = false,
    val analogAsDpad: Boolean = false,
    val dpadAsAnalog: Boolean = false,
    val showControllerOrderModal: Boolean = false,
    val showInputMappingModal: Boolean = false,
    val showHotkeysModal: Boolean = false,
    val controllerOrderCount: Int = 0,
    val showStickMappings: Boolean = false,
    val showDpadAsAnalog: Boolean = false,
    val showRumble: Boolean = true,
    val showResetAll: Boolean = false,
    val touchEnabled: Boolean = true,
    val touchOpacityLandscape: Float = 0.45f,
    val touchOpacityPortrait: Float = 1.0f,
    val touchSizeScale: Float = 1.0f,
    val touchHaptic: Boolean = true,
    val touchFadeOnIdle: Boolean = false,
    val touchSwapHanded: Boolean = false,
    val touchLockOrientation: Boolean = false,
    val touchMirror180: Boolean = false,
    val touchColouredFaceButtons: Boolean = false,
    val touchGenesis6Button: Boolean = false,
    val showTouchLayoutEditorModal: Boolean = false
)

enum class CoreChipStatus {
    OFFLINE_NOT_DOWNLOADED,
    ONLINE_NOT_DOWNLOADED,
    DOWNLOADED,
    ACTIVE
}

data class CoreChipState(
    val coreId: String,
    val displayName: String,
    val isInstalled: Boolean,
    val isActive: Boolean,
    val netplaySupported: Boolean = false,
    val updateAvailable: Boolean = false
)

data class PlatformCoreRow(
    val platformSlug: String,
    val platformName: String,
    val cores: List<CoreChipState>
) {
    val activeCoreIndex: Int get() = cores.indexOfFirst { it.isActive }.coerceAtLeast(0)
}

data class CoreManagementState(
    val platforms: List<PlatformCoreRow> = emptyList(),
    val focusedPlatformIndex: Int = 0,
    val focusedCoreIndex: Int = 0,
    val isOnline: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadingCoreId: String? = null
) {
    val focusedPlatform: PlatformCoreRow? get() = platforms.getOrNull(focusedPlatformIndex)
    val focusedCore: CoreChipState? get() = focusedPlatform?.cores?.getOrNull(focusedCoreIndex)
}

data class CoreOptionsCoreContext(
    val coreId: String,
    val displayName: String,
    val isInstalled: Boolean
)

data class CoreOptionViewItem(
    val key: String,
    val displayName: String,
    val description: String?,
    val values: List<String>,
    val currentValue: String,
    val isOverridden: Boolean,
    val valueLabels: Map<String, String> = emptyMap()
) {
    val displayValue: String get() = valueLabels[currentValue] ?: currentValue
    fun displayValueFor(value: String): String = valueLabels[value] ?: value
}

data class CoreOptionsState(
    val platformContextIndex: Int = 0,
    val availablePlatforms: List<PlatformContext> = emptyList(),
    val coresForCurrentPlatform: List<CoreOptionsCoreContext> = emptyList(),
    val selectedCoreIndex: Int = 0,
    val options: List<CoreOptionViewItem> = emptyList(),
    val overrides: Map<String, String> = emptyMap()
) {
    val currentPlatformContext: PlatformContext? get() =
        if (platformContextIndex in availablePlatforms.indices)
            availablePlatforms[platformContextIndex]
        else null
    val selectedCore: CoreOptionsCoreContext? get() =
        coresForCurrentPlatform.getOrNull(selectedCoreIndex)
}

data class SavePathModalInfo(
    val emulatorId: String,
    val emulatorName: String,
    val platformName: String,
    val savePath: String?,
    val isUserOverride: Boolean,
    val savesBesideRom: Boolean = false,
    val besideRomSupported: Boolean = false
)

data class MemcardPickerInfo(
    val emulatorId: String,
    val emulatorName: String,
    val platformName: String,
    val cards: List<com.nendo.argosy.data.sync.platform.MemcardInfo>,
    val selectedCardPath: String?
)

data class PlatformStorageConfig(
    val platformId: Long,
    val platformName: String,
    val platformSlug: String,
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
    val supportsStatePath: Boolean = false,
    val folderMemcardCount: Int = -1,
    val selectedMemcardPath: String? = null
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
    val isValidatingDownloads: Boolean = false,
    val showPurgeAllConfirm: Boolean = false,
    val isPurgingAll: Boolean = false,
    val weeklyIntegrityCheckEnabled: Boolean = true,
    val busyPlatformIds: Set<Long> = emptySet(),
    val isLibrarySyncing: Boolean = false
)

data class PlatformMigrationInfo(
    val platformId: Long,
    val platformName: String,
    val oldPath: String,
    val newPath: String,
    val isResetToGlobal: Boolean
)

data class PlatformLibretroState(
    val platformSettings: Map<Long, PlatformLibretroSettingsEntity> = emptyMap()
)

data class ServerState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONFIGURED,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommVersion: String? = null,
    val lastRommSync: java.time.Instant? = null,
    val rommConfiguring: Boolean = false,
    val rommAuthMethod: RomMAuthMethod = RomMAuthMethod.PAIRING_CODE,
    val rommConfigUrl: String = "",
    val rommConfigUsername: String = "",
    val rommConfigPassword: String = "",
    val rommConfigPairingCode: String = "",
    val rommShowScanner: Boolean = false,
    val rommHasCamera: Boolean = false,
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
    val platformFiltersAllPlatforms: List<PlatformFilterItem> = emptyList(),
    val platformFilterSortMode: PlatformFilterLogic.SortMode = PlatformFilterLogic.SortMode.DEFAULT,
    val platformFilterMode: PlatformFilterLogic.FilterMode = PlatformFilterLogic.FilterMode.ALL,
    val platformFilterSearchQuery: String = "",
    val platformFiltersHeaderFocused: Boolean = false,
    val platformFiltersHeaderIndex: Int = 0,
    val platformFiltersSearchActive: Boolean = false,
    val platformFiltersSortMenuOpen: Boolean = false,
    val platformFiltersSortMenuIndex: Int = 0,
    val isLoadingPlatforms: Boolean = false,
    val enabledPlatformCount: Int = 0,
    val totalGames: Int = 0,
    val totalPlatforms: Int = 0,
    val saveSyncEnabled: Boolean = false,
    val saveCacheLimit: Int = 10,
    val pendingUploadsCount: Int = 0,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val imageCachePath: String? = null,
    val defaultImageCachePath: String? = null,
    val imageCacheActionIndex: Int = 0,
    val isImageCacheMigrating: Boolean = false,
    val showResetSaveCacheConfirm: Boolean = false,
    val isResettingSaveCache: Boolean = false,
    val showClearPathCacheConfirm: Boolean = false,
    val isClearingPathCache: Boolean = false,
    val showForceSyncConfirm: Boolean = false,
    val syncConfirmButtonIndex: Int = 1,
    val saveCacheCount: Int = 0,
    val stateCacheCount: Int = 0,
    val pathCacheCount: Int = 0
)

data class InstalledSteamLauncher(
    val packageName: String,
    val displayName: String,
    val gameCount: Int = 0,
    val supportsScanning: Boolean = false,
    val scanMayIncludeUninstalled: Boolean = false
)

data class NotInstalledSteamLauncher(
    val emulatorId: String,
    val displayName: String,
    val hasDirectDownload: Boolean
)

data class SteamSettingsState(
    // GameNative
    val gnInstalled: Boolean = false,
    val gnStoragePath: String? = null,

    // Install volume
    val steamInstallVolume: String? = null,
    val availableVolumes: List<com.nendo.argosy.data.steam.SteamInstallVolume> = emptyList(),
    val installedGamesByVolume: Map<String, Int> = emptyMap(),

    // Steam install path (resolved from Steam platform's customRomPath, or default <romsRoot>/steam)
    val steamInstallPath: String? = null,
    // True when the Steam platform row has a non-null customRomPath (user-overridden)
    val steamInstallPathIsCustom: Boolean = false,

    // Steam connection
    val connectionState: com.nendo.argosy.data.steam.SteamConnectionState =
        com.nendo.argosy.data.steam.SteamConnectionState.DISCONNECTED,
    val username: String? = null,
    val error: String? = null,

    // QR auth
    val qrUrl: String? = null,
    val authPolling: Boolean = false,

    // Library sync
    val syncState: com.nendo.argosy.data.steam.LibrarySyncState =
        com.nendo.argosy.data.steam.LibrarySyncState.Idle,

    // Manual add
    val showAddGameDialog: Boolean = false,
    val addGameAppId: String = "",
    val addGameError: String? = null,
    val isAddingGame: Boolean = false,

    // Legacy fields (used by GameDataSection/routers -- remove when those are reworked)
    val hasStoragePermission: Boolean = false,
    val installedLaunchers: List<InstalledSteamLauncher> = emptyList(),
    val notInstalledLaunchers: List<NotInstalledSteamLauncher> = emptyList(),
    val downloadingLauncherId: String? = null,
    val downloadProgress: Float? = null,
    val isSyncing: Boolean = false,
    val syncingLauncher: String? = null,
    val selectedLauncherPackage: String? = null,
    val launcherActionIndex: Int = 0,
    val variantPickerInfo: VariantPickerInfo? = null,
    val variantPickerFocusIndex: Int = 0
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
    val isScreenCaptureRelevant: Boolean = false,
    val hasDisplayOverlay: Boolean = false
) {
    val allGranted: Boolean get() = hasStorageAccess && hasUsageStats && hasNotificationPermission &&
        (!isWriteSettingsRelevant || hasWriteSettings) &&
        (!isScreenCaptureRelevant || hasScreenCapture) &&
        hasDisplayOverlay
    val grantedCount: Int get() = listOf(
        hasStorageAccess,
        hasUsageStats,
        hasNotificationPermission,
        if (isWriteSettingsRelevant) hasWriteSettings else null,
        if (isScreenCaptureRelevant) hasScreenCapture else null,
        hasDisplayOverlay
    ).count { it == true }
    val totalCount: Int get() {
        var count = 4
        if (isWriteSettingsRelevant) count++
        if (isScreenCaptureRelevant) count++
        return count
    }
}

const val RA_PROXY_TOGGLE_INDEX = 1
const val RA_PROXY_FIELD_INDEX = 2

data class RASettingsState(
    val isLoggedIn: Boolean = false,
    val username: String? = null,
    val isLoggingIn: Boolean = false,
    val isLoggingOut: Boolean = false,
    val showLoginForm: Boolean = false,
    val loginUsername: String = "",
    val loginPassword: String = "",
    val loginError: String? = null,
    val focusField: Int? = null,
    val pendingAchievementsCount: Int = 0,
    val proxyEnabled: Boolean = false,
    val proxyAddress: String = ""
)

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

data class GpuDriverInfo(
    val name: String,
    val version: String,
    val isInstalling: Boolean = false,
    val installProgress: Float = 0f
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
    val isBiosMigrating: Boolean = false,
    val expandedPlatformIndex: Int = -1,
    val platformSubFocusIndex: Int = 0,
    val actionIndex: Int = 0,
    val biosPathActionIndex: Int = 0,
    val showGpuDriverPrompt: Boolean = false,
    val gpuDriverInfo: GpuDriverInfo? = null,
    val gpuDriverPromptFocusIndex: Int = 0,
    val hasGpuDriverInstalled: Boolean = false,
    val deviceGpuName: String? = null
) {
    val missingFiles: Int get() = totalFiles - downloadedFiles
    val isComplete: Boolean get() = totalFiles > 0 && downloadedFiles == totalFiles
    val summaryText: String get() = when {
        totalFiles == 0 -> "No BIOS files found"
        isComplete -> "All $totalFiles files downloaded"
        else -> "$downloadedFiles of $totalFiles downloaded"
    }
}

data class DriverGroupUi(
    val name: String,
    val repoPath: String,
    val sort: Int,
    val useTagName: Boolean,
    val releases: List<DriverReleaseUi>,
    val error: String? = null,
    val expanded: Boolean = false
)

data class DriverReleaseUi(
    val title: String,
    val tagName: String,
    val body: String,
    val prerelease: Boolean,
    val isLatestStable: Boolean,
    val artifacts: List<DriverArtifactUi>
)

data class DriverArtifactUi(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

data class DriversState(
    val groups: List<DriverGroupUi> = emptyList(),
    val isLoading: Boolean = false,
    val gpuModel: String? = null,
    val recommendedDriver: String = "Unsupported",
    val activeDownload: DriverDownloadState? = null,
    val downloadedFiles: List<String> = emptyList(),
    val pickerGroupIndex: Int? = null,
    val pickerReleaseFocusIndex: Int = 0
) {
    val summary: String get() = when {
        isLoading && groups.isEmpty() -> "Checking drivers..."
        gpuModel.isNullOrBlank() -> "GPU not detected"
        else -> "Adreno drivers for ${gpuModel.orEmpty()}"
    }
}

data class DriverDownloadState(
    val artifactName: String,
    val downloaded: Long,
    val total: Long,
    val isComplete: Boolean = false,
    val error: String? = null
)

enum class SocialAuthStatus {
    NOT_LINKED,
    AWAITING_AUTH,
    CONNECTED,
    CONNECTING,
    ERROR
}

data class SocialState(
    val authStatus: SocialAuthStatus = SocialAuthStatus.NOT_LINKED,
    val qrUrl: String? = null,
    val loginCode: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatarColor: String? = null,
    val errorMessage: String? = null,
    val onlineStatusEnabled: Boolean = true,
    val showNowPlaying: Boolean = true,
    val notifyFriendOnline: Boolean = true,
    val notifyFriendPlaying: Boolean = true,
    val suppressNotificationsInGame: Boolean = false,
    val discordLinked: Boolean = false,
    val discordUsername: String? = null,
    val discordRichPresenceEnabled: Boolean = true,
    val discordPresenceState: DiscordPresenceState = DiscordPresenceState.Disconnected
)

data class SettingsUiState(
    val currentSection: SettingsSection = SettingsSection.MAIN,
    val focusedIndex: Int = 0,
    val parentFocusIndex: Int = 0,
    val systemizeResult: com.nendo.argosy.util.SystemizeWriteResult? = null,
    val colorFocusIndex: Int = 0,
    val display: DisplayState = DisplayState(),
    val controls: ControlsState = ControlsState(),
    val sounds: SoundState = SoundState(),
    val ambientAudio: AmbientAudioState = AmbientAudioState(),
    val emulators: EmulatorState = EmulatorState(),
    val platformDetail: PlatformDetailState = PlatformDetailState(),
    val builtinVideo: BuiltinVideoState = BuiltinVideoState(),
    val builtinControls: BuiltinControlsState = BuiltinControlsState(),
    val coreManagement: CoreManagementState = CoreManagementState(),
    val coreOptions: CoreOptionsState = CoreOptionsState(),
    val server: ServerState = ServerState(),
    val storage: StorageState = StorageState(),
    val platformLibretro: PlatformLibretroState = PlatformLibretroState(),
    val syncSettings: SyncSettingsState = SyncSettingsState(),
    val steam: SteamSettingsState = SteamSettingsState(),
    val retroAchievements: RASettingsState = RASettingsState(),
    val android: AndroidSettingsState = AndroidSettingsState(),
    val bios: BiosState = BiosState(),
    val drivers: DriversState = DriversState(),
    val social: SocialState = SocialState(),
    val permissions: PermissionsState = PermissionsState(),
    val launchFolderPicker: Boolean = false,
    val showMigrationDialog: Boolean = false,
    val pendingStoragePath: String? = null,
    val isMigrating: Boolean = false,
    val showBuiltinPathMigrationDialog: Boolean = false,
    val pendingBuiltinPathMigration: BuiltinPathMigration? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val updateCheck: UpdateCheckState = UpdateCheckState(),
    val betaUpdatesEnabled: Boolean = false,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val saveDebugLoggingEnabled: Boolean = false,
    val previewGame: GameListItem? = null,
    val previewGames: List<GameListItem> = emptyList(),
    val previewGameIndex: Int = 0,
    val gradientConfig: GradientExtractionConfig = GradientExtractionConfig(),
    val gradientExtractionResult: GradientExtractionResult? = null,
    val frameDownloadingId: String? = null,
    val frameInstalledRefresh: Int = 0,
    val appAffinityEnabled: Boolean = false
)
