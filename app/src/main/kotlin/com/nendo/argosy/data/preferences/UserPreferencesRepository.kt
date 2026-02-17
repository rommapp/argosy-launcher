package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.util.LogLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val displayPrefs: DisplayPreferencesRepository,
    private val syncPrefs: SyncPreferencesRepository,
    private val controlsPrefs: ControlsPreferencesRepository,
    private val storagePrefs: StoragePreferencesRepository,
    private val appPrefs: AppPreferencesRepository,
    private val builtinPrefs: BuiltinEmulatorPreferencesRepository,
    private val sessionPrefs: SessionPreferencesRepository
) {
    val userPreferences: Flow<UserPreferences> = combine(
        displayPrefs.preferences,
        syncPrefs.preferences,
        controlsPrefs.preferences,
        storagePrefs.preferences,
        appPrefs.preferences
    ) { display, sync, controls, storage, app ->
        UserPreferences(
            firstRunComplete = app.firstRunComplete,
            rommBaseUrl = sync.rommBaseUrl,
            rommUsername = sync.rommUsername,
            rommToken = sync.rommToken,
            rommDeviceId = sync.rommDeviceId,
            rommDeviceClientVersion = sync.rommDeviceClientVersion,
            raUsername = sync.raUsername,
            raToken = sync.raToken,
            romStoragePath = storage.romStoragePath,
            themeMode = display.themeMode,
            primaryColor = display.primaryColor,
            secondaryColor = display.secondaryColor,
            tertiaryColor = display.tertiaryColor,
            hapticEnabled = controls.hapticEnabled,
            soundEnabled = controls.soundEnabled,
            soundVolume = controls.soundVolume,
            swapAB = controls.swapAB,
            swapXY = controls.swapXY,
            controllerLayout = controls.controllerLayout,
            swapStartSelect = controls.swapStartSelect,
            lastRommSync = sync.lastRommSync,
            lastFavoritesSync = sync.lastFavoritesSync,
            lastFavoritesCheck = sync.lastFavoritesCheck,
            syncFilters = sync.syncFilters,
            syncScreenshotsEnabled = sync.syncScreenshotsEnabled,
            backgroundBlur = display.backgroundBlur,
            backgroundSaturation = display.backgroundSaturation,
            backgroundOpacity = display.backgroundOpacity,
            useGameBackground = display.useGameBackground,
            customBackgroundPath = display.customBackgroundPath,
            useAccentColorFooter = display.useAccentColorFooter,
            hiddenApps = app.hiddenApps,
            secondaryHomeApps = app.secondaryHomeApps,
            visibleSystemApps = app.visibleSystemApps,
            appOrder = app.appOrder,
            maxConcurrentDownloads = storage.maxConcurrentDownloads,
            instantDownloadThresholdMb = storage.instantDownloadThresholdMb,
            gridDensity = display.gridDensity,
            soundConfigs = controls.soundConfigs,
            betaUpdatesEnabled = app.betaUpdatesEnabled,
            saveSyncEnabled = sync.saveSyncEnabled,
            experimentalFolderSaveSync = sync.experimentalFolderSaveSync,
            stateCacheEnabled = sync.stateCacheEnabled,
            saveCacheLimit = sync.saveCacheLimit,
            fileLoggingEnabled = app.fileLoggingEnabled,
            fileLoggingPath = app.fileLoggingPath,
            fileLogLevel = app.fileLogLevel,
            saveDebugLoggingEnabled = sync.saveDebugLoggingEnabled,
            saveWatcherEnabled = sync.saveWatcherEnabled,
            boxArtShape = display.boxArtShape,
            boxArtCornerRadius = display.boxArtCornerRadius,
            boxArtBorderThickness = display.boxArtBorderThickness,
            boxArtBorderStyle = display.boxArtBorderStyle,
            glassBorderTint = display.glassBorderTint,
            boxArtGlowStrength = display.boxArtGlowStrength,
            boxArtOuterEffect = display.boxArtOuterEffect,
            boxArtOuterEffectThickness = display.boxArtOuterEffectThickness,
            glowColorMode = display.glowColorMode,
            boxArtInnerEffect = display.boxArtInnerEffect,
            boxArtInnerEffectThickness = display.boxArtInnerEffectThickness,
            gradientPreset = display.gradientPreset,
            gradientAdvancedMode = display.gradientAdvancedMode,
            systemIconPosition = display.systemIconPosition,
            systemIconPadding = display.systemIconPadding,
            defaultView = display.defaultView,
            recommendedGameIds = app.recommendedGameIds,
            lastRecommendationGeneration = app.lastRecommendationGeneration,
            recommendationPenalties = app.recommendationPenalties,
            lastPenaltyDecayWeek = app.lastPenaltyDecayWeek,
            lastSeenVersion = app.lastSeenVersion,
            libraryRecentSearches = app.libraryRecentSearches,
            accuratePlayTimeEnabled = controls.accuratePlayTimeEnabled,
            ambientAudioEnabled = controls.ambientAudioEnabled,
            ambientAudioVolume = controls.ambientAudioVolume,
            ambientAudioUri = controls.ambientAudioUri,
            ambientAudioShuffle = controls.ambientAudioShuffle,
            imageCachePath = sync.imageCachePath,
            screenDimmerEnabled = display.screenDimmerEnabled,
            screenDimmerTimeoutMinutes = display.screenDimmerTimeoutMinutes,
            screenDimmerLevel = display.screenDimmerLevel,
            customBiosPath = storage.customBiosPath,
            videoWallpaperEnabled = display.videoWallpaperEnabled,
            videoWallpaperDelaySeconds = display.videoWallpaperDelaySeconds,
            videoWallpaperMuted = display.videoWallpaperMuted,
            uiScale = display.uiScale,
            ambientLedEnabled = display.ambientLedEnabled,
            ambientLedBrightness = display.ambientLedBrightness,
            ambientLedAudioBrightness = display.ambientLedAudioBrightness,
            ambientLedAudioColors = display.ambientLedAudioColors,
            ambientLedColorMode = display.ambientLedColorMode,
            androidDataSafUri = sync.androidDataSafUri,
            builtinLibretroEnabled = true,
            appAffinityEnabled = app.appAffinityEnabled,
            displayRoleOverride = display.displayRoleOverride,
            dualScreenInputFocus = display.dualScreenInputFocus
        )
    }

    val preferences: Flow<UserPreferences> = userPreferences

    // --- Display delegates ---

    suspend fun setThemeMode(mode: ThemeMode) = displayPrefs.setThemeMode(mode)
    suspend fun setCustomColors(primary: Int?, secondary: Int?, tertiary: Int?) = displayPrefs.setCustomColors(primary, secondary, tertiary)
    suspend fun setSecondaryColor(color: Int?) = displayPrefs.setSecondaryColor(color)
    suspend fun setGridDensity(density: GridDensity) = displayPrefs.setGridDensity(density)
    suspend fun setUiScale(scale: Int) = displayPrefs.setUiScale(scale)
    suspend fun setBackgroundBlur(blur: Int) = displayPrefs.setBackgroundBlur(blur)
    suspend fun setBackgroundSaturation(saturation: Int) = displayPrefs.setBackgroundSaturation(saturation)
    suspend fun setBackgroundOpacity(opacity: Int) = displayPrefs.setBackgroundOpacity(opacity)
    suspend fun setUseGameBackground(use: Boolean) = displayPrefs.setUseGameBackground(use)
    suspend fun setCustomBackgroundPath(path: String?) = displayPrefs.setCustomBackgroundPath(path)
    suspend fun setUseAccentColorFooter(use: Boolean) = displayPrefs.setUseAccentColorFooter(use)
    suspend fun setBoxArtShape(shape: BoxArtShape) = displayPrefs.setBoxArtShape(shape)
    suspend fun setBoxArtCornerRadius(radius: BoxArtCornerRadius) = displayPrefs.setBoxArtCornerRadius(radius)
    suspend fun setBoxArtBorderThickness(thickness: BoxArtBorderThickness) = displayPrefs.setBoxArtBorderThickness(thickness)
    suspend fun setBoxArtBorderStyle(style: BoxArtBorderStyle) = displayPrefs.setBoxArtBorderStyle(style)
    suspend fun setGlassBorderTint(tint: GlassBorderTint) = displayPrefs.setGlassBorderTint(tint)
    suspend fun setBoxArtGlowStrength(strength: BoxArtGlowStrength) = displayPrefs.setBoxArtGlowStrength(strength)
    suspend fun setBoxArtOuterEffect(effect: BoxArtOuterEffect) = displayPrefs.setBoxArtOuterEffect(effect)
    suspend fun setBoxArtOuterEffectThickness(thickness: BoxArtOuterEffectThickness) = displayPrefs.setBoxArtOuterEffectThickness(thickness)
    suspend fun setGlowColorMode(mode: GlowColorMode) = displayPrefs.setGlowColorMode(mode)
    suspend fun setBoxArtInnerEffect(effect: BoxArtInnerEffect) = displayPrefs.setBoxArtInnerEffect(effect)
    suspend fun setBoxArtInnerEffectThickness(thickness: BoxArtInnerEffectThickness) = displayPrefs.setBoxArtInnerEffectThickness(thickness)
    suspend fun setGradientPreset(preset: GradientPreset) = displayPrefs.setGradientPreset(preset)
    suspend fun setGradientAdvancedMode(enabled: Boolean) = displayPrefs.setGradientAdvancedMode(enabled)
    suspend fun setSystemIconPosition(position: SystemIconPosition) = displayPrefs.setSystemIconPosition(position)
    suspend fun setSystemIconPadding(padding: SystemIconPadding) = displayPrefs.setSystemIconPadding(padding)
    suspend fun setDefaultView(view: DefaultView) = displayPrefs.setDefaultView(view)
    suspend fun setVideoWallpaperEnabled(enabled: Boolean) = displayPrefs.setVideoWallpaperEnabled(enabled)
    suspend fun setVideoWallpaperDelaySeconds(seconds: Int) = displayPrefs.setVideoWallpaperDelaySeconds(seconds)
    suspend fun setVideoWallpaperMuted(muted: Boolean) = displayPrefs.setVideoWallpaperMuted(muted)
    suspend fun setAmbientLedEnabled(enabled: Boolean) = displayPrefs.setAmbientLedEnabled(enabled)
    suspend fun setAmbientLedBrightness(brightness: Int) = displayPrefs.setAmbientLedBrightness(brightness)
    suspend fun setAmbientLedAudioBrightness(enabled: Boolean) = displayPrefs.setAmbientLedAudioBrightness(enabled)
    suspend fun setAmbientLedAudioColors(enabled: Boolean) = displayPrefs.setAmbientLedAudioColors(enabled)
    suspend fun setAmbientLedColorMode(mode: AmbientLedColorMode) = displayPrefs.setAmbientLedColorMode(mode)
    suspend fun setScreenDimmerEnabled(enabled: Boolean) = displayPrefs.setScreenDimmerEnabled(enabled)
    suspend fun setScreenDimmerTimeoutMinutes(minutes: Int) = displayPrefs.setScreenDimmerTimeoutMinutes(minutes)
    suspend fun setScreenDimmerLevel(level: Int) = displayPrefs.setScreenDimmerLevel(level)
    suspend fun setDisplayRoleOverride(override: DisplayRoleOverride) = displayPrefs.setDisplayRoleOverride(override)
    suspend fun setDualScreenInputFocus(focus: DualScreenInputFocus) = displayPrefs.setDualScreenInputFocus(focus)

    // --- Sync delegates ---

    suspend fun setRommConfig(url: String?, username: String?) = syncPrefs.setRommConfig(url, username)
    suspend fun setRomMCredentials(baseUrl: String, token: String, username: String? = null) = syncPrefs.setRomMCredentials(baseUrl, token, username)
    suspend fun clearRomMCredentials() = syncPrefs.clearRomMCredentials()
    suspend fun setRommDeviceId(deviceId: String, clientVersion: String) = syncPrefs.setRommDeviceId(deviceId, clientVersion)
    suspend fun clearRommDeviceId() = syncPrefs.clearRommDeviceId()
    suspend fun setRACredentials(username: String, token: String) = syncPrefs.setRACredentials(username, token)
    suspend fun clearRACredentials() = syncPrefs.clearRACredentials()
    suspend fun setLastRommSyncTime(time: Instant) = syncPrefs.setLastRommSyncTime(time)
    suspend fun setLastFavoritesSyncTime(time: Instant) = syncPrefs.setLastFavoritesSyncTime(time)
    suspend fun setLastFavoritesCheckTime(time: Instant) = syncPrefs.setLastFavoritesCheckTime(time)
    suspend fun setSyncFilterRegions(regions: Set<String>) = syncPrefs.setSyncFilterRegions(regions)
    suspend fun setSyncFilterRegionMode(mode: RegionFilterMode) = syncPrefs.setSyncFilterRegionMode(mode)
    suspend fun setSyncFilterExcludeBeta(exclude: Boolean) = syncPrefs.setSyncFilterExcludeBeta(exclude)
    suspend fun setSyncFilterExcludePrototype(exclude: Boolean) = syncPrefs.setSyncFilterExcludePrototype(exclude)
    suspend fun setSyncFilterExcludeDemo(exclude: Boolean) = syncPrefs.setSyncFilterExcludeDemo(exclude)
    suspend fun setSyncFilterExcludeHack(exclude: Boolean) = syncPrefs.setSyncFilterExcludeHack(exclude)
    suspend fun setSyncFilterDeleteOrphans(delete: Boolean) = syncPrefs.setSyncFilterDeleteOrphans(delete)
    suspend fun setSyncScreenshotsEnabled(enabled: Boolean) = syncPrefs.setSyncScreenshotsEnabled(enabled)
    suspend fun setSaveSyncEnabled(enabled: Boolean) = syncPrefs.setSaveSyncEnabled(enabled)
    suspend fun setExperimentalFolderSaveSync(enabled: Boolean) = syncPrefs.setExperimentalFolderSaveSync(enabled)
    suspend fun setSaveCacheLimit(limit: Int) = syncPrefs.setSaveCacheLimit(limit)
    suspend fun setSaveDebugLoggingEnabled(enabled: Boolean) = syncPrefs.setSaveDebugLoggingEnabled(enabled)
    suspend fun setImageCachePath(path: String?) = syncPrefs.setImageCachePath(path)
    suspend fun setAndroidDataSafUri(uri: String?) = syncPrefs.setAndroidDataSafUri(uri)
    fun saveWatcherEnabled(): Flow<Boolean> = syncPrefs.saveWatcherEnabled()
    suspend fun setSaveWatcherEnabled(enabled: Boolean) = syncPrefs.setSaveWatcherEnabled(enabled)

    // --- Controls delegates ---

    suspend fun setHapticEnabled(enabled: Boolean) = controlsPrefs.setHapticEnabled(enabled)
    suspend fun setSoundEnabled(enabled: Boolean) = controlsPrefs.setSoundEnabled(enabled)
    suspend fun setSoundVolume(volume: Int) = controlsPrefs.setSoundVolume(volume)
    suspend fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) = controlsPrefs.setSoundConfigs(configs)
    suspend fun setSoundConfig(type: SoundType, config: SoundConfig?) = controlsPrefs.setSoundConfig(type, config)
    suspend fun setSwapAB(enabled: Boolean) = controlsPrefs.setSwapAB(enabled)
    suspend fun setSwapXY(enabled: Boolean) = controlsPrefs.setSwapXY(enabled)
    suspend fun setControllerLayout(layout: String) = controlsPrefs.setControllerLayout(layout)
    suspend fun setSwapStartSelect(enabled: Boolean) = controlsPrefs.setSwapStartSelect(enabled)
    suspend fun setAccuratePlayTimeEnabled(enabled: Boolean) = controlsPrefs.setAccuratePlayTimeEnabled(enabled)
    suspend fun setAmbientAudioEnabled(enabled: Boolean) = controlsPrefs.setAmbientAudioEnabled(enabled)
    suspend fun setAmbientAudioVolume(volume: Int) = controlsPrefs.setAmbientAudioVolume(volume)
    suspend fun setAmbientAudioUri(uri: String?) = controlsPrefs.setAmbientAudioUri(uri)
    suspend fun setAmbientAudioShuffle(shuffle: Boolean) = controlsPrefs.setAmbientAudioShuffle(shuffle)

    // --- Storage delegates ---

    suspend fun setRomStoragePath(path: String) = storagePrefs.setRomStoragePath(path)
    suspend fun setMaxConcurrentDownloads(count: Int) = storagePrefs.setMaxConcurrentDownloads(count)
    suspend fun setInstantDownloadThresholdMb(value: Int) = storagePrefs.setInstantDownloadThresholdMb(value)
    suspend fun setCustomBiosPath(path: String?) = storagePrefs.setCustomBiosPath(path)

    // --- App delegates ---

    suspend fun setFirstRunComplete() = appPrefs.setFirstRunComplete()
    suspend fun setBetaUpdatesEnabled(enabled: Boolean) = appPrefs.setBetaUpdatesEnabled(enabled)
    suspend fun setHiddenApps(apps: Set<String>) = appPrefs.setHiddenApps(apps)
    suspend fun setSecondaryHomeApps(apps: Set<String>) = appPrefs.setSecondaryHomeApps(apps)
    suspend fun setVisibleSystemApps(apps: Set<String>) = appPrefs.setVisibleSystemApps(apps)
    suspend fun setAppOrder(order: List<String>) = appPrefs.setAppOrder(order)
    suspend fun setLastSeenVersion(version: String) = appPrefs.setLastSeenVersion(version)
    suspend fun addLibraryRecentSearch(query: String) = appPrefs.addLibraryRecentSearch(query)
    suspend fun setRecommendations(gameIds: List<Long>, timestamp: Instant) = appPrefs.setRecommendations(gameIds, timestamp)
    suspend fun clearRecommendations() = appPrefs.clearRecommendations()
    suspend fun setRecommendationPenalties(penalties: Map<Long, Float>, weekKey: String) = appPrefs.setRecommendationPenalties(penalties, weekKey)
    suspend fun setFileLoggingEnabled(enabled: Boolean) = appPrefs.setFileLoggingEnabled(enabled)
    suspend fun setFileLoggingPath(path: String?) = appPrefs.setFileLoggingPath(path)
    suspend fun setFileLogLevel(level: LogLevel) = appPrefs.setFileLogLevel(level)
    suspend fun setAppAffinityEnabled(enabled: Boolean) = appPrefs.setAppAffinityEnabled(enabled)

    // --- Builtin emulator delegates ---

    suspend fun setBuiltinShader(shader: String) = builtinPrefs.setBuiltinShader(shader)
    suspend fun setBuiltinShaderChain(chainJson: String) = builtinPrefs.setBuiltinShaderChain(chainJson)
    suspend fun setBuiltinFilter(filter: String) = builtinPrefs.setBuiltinFilter(filter)
    suspend fun setBuiltinLibretroEnabled(enabled: Boolean) = builtinPrefs.setBuiltinLibretroEnabled(enabled)
    suspend fun setBuiltinAspectRatio(aspectRatio: String) = builtinPrefs.setBuiltinAspectRatio(aspectRatio)
    suspend fun setBuiltinSkipDuplicateFrames(enabled: Boolean) = builtinPrefs.setBuiltinSkipDuplicateFrames(enabled)
    suspend fun setBuiltinLowLatencyAudio(enabled: Boolean) = builtinPrefs.setBuiltinLowLatencyAudio(enabled)
    suspend fun setBuiltinForceSoftwareTiming(enabled: Boolean) = builtinPrefs.setBuiltinForceSoftwareTiming(enabled)
    suspend fun setBuiltinRumbleEnabled(enabled: Boolean) = builtinPrefs.setBuiltinRumbleEnabled(enabled)
    suspend fun setBuiltinBlackFrameInsertion(enabled: Boolean) = builtinPrefs.setBuiltinBlackFrameInsertion(enabled)
    suspend fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) = builtinPrefs.setBuiltinLimitHotkeysToPlayer1(enabled)
    suspend fun setBuiltinAnalogAsDpad(enabled: Boolean) = builtinPrefs.setBuiltinAnalogAsDpad(enabled)
    suspend fun setBuiltinDpadAsAnalog(enabled: Boolean) = builtinPrefs.setBuiltinDpadAsAnalog(enabled)
    suspend fun setBuiltinCoreForPlatform(platformSlug: String, coreId: String) = builtinPrefs.setBuiltinCoreForPlatform(platformSlug, coreId)
    suspend fun setBuiltinFastForwardSpeed(speed: Int) = builtinPrefs.setBuiltinFastForwardSpeed(speed)
    suspend fun setBuiltinRotation(rotation: Int) = builtinPrefs.setBuiltinRotation(rotation)
    suspend fun setBuiltinOverscanCrop(crop: Int) = builtinPrefs.setBuiltinOverscanCrop(crop)
    suspend fun setBuiltinRewindEnabled(enabled: Boolean) = builtinPrefs.setBuiltinRewindEnabled(enabled)
    suspend fun setBuiltinFramesEnabled(enabled: Boolean) = builtinPrefs.setBuiltinFramesEnabled(enabled)
    suspend fun setBuiltinMigrationComplete() = builtinPrefs.setBuiltinMigrationComplete()
    fun getBuiltinEmulatorSettings(): Flow<BuiltinEmulatorSettings> = builtinPrefs.getBuiltinEmulatorSettings()
    fun getBuiltinCoreSelections(): Flow<Map<String, String>> = builtinPrefs.getBuiltinCoreSelections()
    fun isBuiltinMigrationComplete(): Flow<Boolean> = builtinPrefs.isBuiltinMigrationComplete()

    // --- Session delegates ---

    data class PersistedSession(
        val gameId: Long,
        val emulatorPackage: String,
        val startTime: Instant,
        val coreName: String?,
        val isHardcore: Boolean,
        val channelName: String? = null
    )

    val activeSessionFlow: Flow<PersistedSession?> = sessionPrefs.activeSessionFlow.map { session ->
        session?.let {
            PersistedSession(
                gameId = it.gameId,
                emulatorPackage = it.emulatorPackage,
                startTime = it.startTime,
                coreName = it.coreName,
                isHardcore = it.isHardcore,
                channelName = it.channelName
            )
        }
    }

    suspend fun persistActiveSession(
        gameId: Long,
        emulatorPackage: String,
        startTime: Instant,
        coreName: String?,
        isHardcore: Boolean,
        channelName: String? = null
    ) = sessionPrefs.persistActiveSession(gameId, emulatorPackage, startTime, coreName, isHardcore, channelName)

    suspend fun clearActiveSession() = sessionPrefs.clearActiveSession()

    suspend fun getPersistedSession(): PersistedSession? {
        val session = sessionPrefs.getPersistedSession() ?: return null
        return PersistedSession(
            gameId = session.gameId,
            emulatorPackage = session.emulatorPackage,
            startTime = session.startTime,
            coreName = session.coreName,
            isHardcore = session.isHardcore,
            channelName = session.channelName
        )
    }
}

data class BuiltinEmulatorSettings(
    val shader: String = "None",
    val shaderChainJson: String = "",
    val filter: String = "Auto",
    val aspectRatio: String = "Core Provided",
    val skipDuplicateFrames: Boolean = false,
    val lowLatencyAudio: Boolean = true,
    val forceSoftwareTiming: Boolean = false,
    val rumbleEnabled: Boolean = true,
    val blackFrameInsertion: Boolean = false,
    val framesEnabled: Boolean = false,
    val frame: String? = null,
    val limitHotkeysToPlayer1: Boolean = true,
    val analogAsDpad: Boolean = false,
    val dpadAsAnalog: Boolean = false,
    val fastForwardSpeed: Int = 4,
    val rotation: Int = -1,
    val overscanCrop: Int = 0,
    val rewindEnabled: Boolean = true
) {
    val shaderConfig: com.swordfish.libretrodroid.ShaderConfig
        get() = when (shader) {
            "CRT" -> com.swordfish.libretrodroid.ShaderConfig.CRT
            "LCD" -> com.swordfish.libretrodroid.ShaderConfig.LCD
            "Sharp" -> com.swordfish.libretrodroid.ShaderConfig.Sharp
            "CUT" -> com.swordfish.libretrodroid.ShaderConfig.CUT()
            "CUT2" -> com.swordfish.libretrodroid.ShaderConfig.CUT2()
            "CUT3" -> com.swordfish.libretrodroid.ShaderConfig.CUT3()
            "Custom" -> com.swordfish.libretrodroid.ShaderConfig.Default
            else -> com.swordfish.libretrodroid.ShaderConfig.Default
        }

    val shaderChainConfig: com.nendo.argosy.libretro.shader.ShaderChainConfig
        get() = com.nendo.argosy.libretro.shader.ShaderChainConfig.fromJson(shaderChainJson)

    val filterMode: Int
        get() = when (filter) {
            "Nearest" -> 0
            "Bilinear" -> 1
            else -> -1
        }

    val isIntegerScaling: Boolean
        get() = aspectRatio == "Integer"

    val fastForwardSpeedDisplay: String
        get() = "${fastForwardSpeed}x"

    val rotationDisplay: String
        get() = when (rotation) {
            -1 -> "Auto"
            0 -> "0°"
            90 -> "90°"
            180 -> "180°"
            270 -> "270°"
            else -> "Auto"
        }

    val overscanCropDisplay: String
        get() = when (overscanCrop) {
            0 -> "Off"
            else -> "${overscanCrop}px"
        }
}

data class UserPreferences(
    val firstRunComplete: Boolean = false,
    val rommBaseUrl: String? = null,
    val rommUsername: String? = null,
    val rommToken: String? = null,
    val rommDeviceId: String? = null,
    val rommDeviceClientVersion: String? = null,
    val raUsername: String? = null,
    val raToken: String? = null,
    val romStoragePath: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val controllerLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val lastRommSync: Instant? = null,
    val lastFavoritesSync: Instant? = null,
    val lastFavoritesCheck: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val syncScreenshotsEnabled: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    val secondaryHomeApps: Set<String> = emptySet(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList(),
    val maxConcurrentDownloads: Int = 1,
    val instantDownloadThresholdMb: Int = 50,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val betaUpdatesEnabled: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val experimentalFolderSaveSync: Boolean = false,
    val stateCacheEnabled: Boolean = true,
    val saveCacheLimit: Int = 10,
    val backgroundBlur: Int = 0,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val useAccentColorFooter: Boolean = false,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val saveDebugLoggingEnabled: Boolean = false,
    val saveWatcherEnabled: Boolean = false,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
    val boxArtCornerRadius: BoxArtCornerRadius = BoxArtCornerRadius.MEDIUM,
    val boxArtBorderThickness: BoxArtBorderThickness = BoxArtBorderThickness.MEDIUM,
    val boxArtBorderStyle: BoxArtBorderStyle = BoxArtBorderStyle.GLASS,
    val glassBorderTint: GlassBorderTint = GlassBorderTint.TINT_20,
    val boxArtGlowStrength: BoxArtGlowStrength = BoxArtGlowStrength.MEDIUM,
    val boxArtOuterEffect: BoxArtOuterEffect = BoxArtOuterEffect.GLOW,
    val boxArtOuterEffectThickness: BoxArtOuterEffectThickness = BoxArtOuterEffectThickness.THIN,
    val glowColorMode: GlowColorMode = GlowColorMode.AUTO,
    val boxArtInnerEffect: BoxArtInnerEffect = BoxArtInnerEffect.GLASS,
    val boxArtInnerEffectThickness: BoxArtInnerEffectThickness = BoxArtInnerEffectThickness.THICK,
    val gradientPreset: GradientPreset = GradientPreset.BALANCED,
    val gradientAdvancedMode: Boolean = false,
    val systemIconPosition: SystemIconPosition = SystemIconPosition.TOP_LEFT,
    val systemIconPadding: SystemIconPadding = SystemIconPadding.MEDIUM,
    val defaultView: DefaultView = DefaultView.HOME,
    val recommendedGameIds: List<Long> = emptyList(),
    val lastRecommendationGeneration: Instant? = null,
    val recommendationPenalties: Map<Long, Float> = emptyMap(),
    val lastPenaltyDecayWeek: String? = null,
    val lastSeenVersion: String? = null,
    val libraryRecentSearches: List<String> = emptyList(),
    val accuratePlayTimeEnabled: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val ambientAudioVolume: Int = 50,
    val ambientAudioUri: String? = null,
    val ambientAudioShuffle: Boolean = false,
    val imageCachePath: String? = null,
    val screenDimmerEnabled: Boolean = true,
    val screenDimmerTimeoutMinutes: Int = 2,
    val screenDimmerLevel: Int = 50,
    val customBiosPath: String? = null,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelaySeconds: Int = 3,
    val videoWallpaperMuted: Boolean = false,
    val uiScale: Int = 100,
    val ambientLedEnabled: Boolean = false,
    val ambientLedBrightness: Int = 100,
    val ambientLedAudioBrightness: Boolean = true,
    val ambientLedAudioColors: Boolean = false,
    val ambientLedColorMode: AmbientLedColorMode = AmbientLedColorMode.DOMINANT_3,
    val androidDataSafUri: String? = null,
    val builtinLibretroEnabled: Boolean = true,
    val appAffinityEnabled: Boolean = false,
    val displayRoleOverride: DisplayRoleOverride = DisplayRoleOverride.AUTO,
    val dualScreenInputFocus: DualScreenInputFocus = DualScreenInputFocus.AUTO
)

enum class ThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

enum class GridDensity {
    COMPACT, NORMAL, SPACIOUS;

    companion object {
        fun fromString(value: String?): GridDensity =
            entries.find { it.name == value } ?: NORMAL
    }
}

enum class BoxArtCornerRadius(val dp: Int) {
    NONE(0), SMALL(4), MEDIUM(8), LARGE(12), EXTRA_LARGE(16);

    companion object {
        fun fromString(value: String?): BoxArtCornerRadius =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtBorderThickness(val dp: Int) {
    NONE(0), THIN(1), MEDIUM(2), THICK(4);

    companion object {
        fun fromString(value: String?): BoxArtBorderThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtBorderStyle {
    SOLID, GLASS, GRADIENT;

    companion object {
        fun fromString(value: String?): BoxArtBorderStyle =
            entries.find { it.name == value } ?: SOLID
    }
}

enum class GlassBorderTint(val alpha: Float) {
    OFF(0f), TINT_5(0.05f), TINT_10(0.10f), TINT_15(0.15f), TINT_20(0.20f), TINT_25(0.25f);

    companion object {
        fun fromString(value: String?): GlassBorderTint =
            entries.find { it.name == value } ?: OFF
    }
}

enum class BoxArtGlowStrength(val alpha: Float, val isShadow: Boolean = false) {
    OFF(0f),
    LOW(0.2f),
    MEDIUM(0.4f),
    HIGH(0.6f),
    SHADOW_SMALL(0.15f, isShadow = true),
    SHADOW_LARGE(0.25f, isShadow = true);

    companion object {
        fun fromString(value: String?): BoxArtGlowStrength =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class SystemIconPosition {
    OFF, TOP_LEFT, TOP_RIGHT;

    companion object {
        fun fromString(value: String?): SystemIconPosition =
            entries.find { it.name == value } ?: TOP_LEFT
    }
}

enum class SystemIconPadding(val dp: Int) {
    SMALL(4), MEDIUM(8), LARGE(12);

    companion object {
        fun fromString(value: String?): SystemIconPadding =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class DefaultView {
    HOME, LIBRARY;

    companion object {
        fun fromString(value: String?): DefaultView = when (value) {
            "HOME", "SHOWCASE" -> HOME
            "LIBRARY" -> LIBRARY
            else -> HOME
        }
    }
}

enum class BoxArtInnerEffect {
    OFF, GLOW, SHADOW, GLASS, SHINE;

    companion object {
        fun fromString(value: String?): BoxArtInnerEffect =
            entries.find { it.name == value } ?: SHADOW
    }
}

enum class BoxArtInnerEffectThickness(val px: Float) {
    THIN(6f), MEDIUM(16f), THICK(24f);

    companion object {
        fun fromString(value: String?): BoxArtInnerEffectThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class BoxArtOuterEffect {
    OFF, GLOW, SHADOW, SHINE;

    companion object {
        fun fromString(value: String?): BoxArtOuterEffect =
            entries.find { it.name == value } ?: GLOW
    }
}

enum class BoxArtOuterEffectThickness(val px: Float) {
    THIN(8f), MEDIUM(16f), THICK(24f);

    companion object {
        fun fromString(value: String?): BoxArtOuterEffectThickness =
            entries.find { it.name == value } ?: MEDIUM
    }
}

enum class GlowColorMode {
    AUTO, ACCENT, ACCENT_GRADIENT, COVER;

    companion object {
        fun fromString(value: String?): GlowColorMode =
            entries.find { it.name == value } ?: AUTO
    }
}

enum class BoxArtShape(val aspectRatio: Float, val displayName: String) {
    TALL(2f / 3f, "2:3"),
    STANDARD(3f / 4f, "3:4"),
    SQUARE(1f, "1:1");

    companion object {
        fun fromString(value: String?): BoxArtShape =
            entries.find { it.name == value } ?: STANDARD
    }
}

enum class AmbientLedColorMode(val displayName: String) {
    DOMINANT_3("Dominant Colors"),
    VIBRANT_MUTED("Vibrant & Muted"),
    HUE_FAMILIES("Hue Families");

    companion object {
        fun fromString(value: String?): AmbientLedColorMode =
            entries.find { it.name == value } ?: DOMINANT_3
    }
}

enum class DisplayRoleOverride(val displayName: String) {
    AUTO("Auto"),
    STANDARD("Standard"),
    SWAPPED("Swapped");

    companion object {
        fun fromString(value: String?): DisplayRoleOverride =
            entries.find { it.name == value } ?: AUTO
    }
}

enum class DualScreenInputFocus(val displayName: String) {
    AUTO("Auto"),
    TOP("Top Screen"),
    BOTTOM("Bottom Screen");

    companion object {
        fun fromString(value: String?): DualScreenInputFocus =
            entries.find { it.name == value } ?: AUTO
    }
}
