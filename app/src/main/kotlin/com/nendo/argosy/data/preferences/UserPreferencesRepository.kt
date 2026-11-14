package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.ScreenLayouts
import com.nendo.argosy.util.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private data class PreferencesSources(
    val display: DisplayPreferences,
    val sync: SyncPreferences,
    val controls: ControlsPreferences,
    val storage: StoragePreferences,
    val app: AppPreferences
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val displayPrefs: DisplayPreferencesRepository,
    private val syncPrefs: SyncPreferencesRepository,
    private val controlsPrefs: ControlsPreferencesRepository,
    private val storagePrefs: StoragePreferencesRepository,
    private val appPrefs: AppPreferencesRepository,
    private val builtinPrefs: BuiltinEmulatorPreferencesRepository,
    private val sessionPrefs: SessionPreferencesRepository,
    private val jellyfinPrefs: JellyfinPreferencesRepository
) {
    private val shareScope = com.nendo.argosy.util.SafeCoroutineScope(Dispatchers.Default, "UserPreferences")

    private val preferencesSource: Flow<UserPreferences> = combine(
        combine(
            displayPrefs.preferences,
            syncPrefs.preferences,
            controlsPrefs.preferences,
            storagePrefs.preferences,
            appPrefs.preferences
        ) { display, sync, controls, storage, app ->
            PreferencesSources(display, sync, controls, storage, app)
        },
        builtinPrefs.isBuiltinLibretroEnabled(),
        jellyfinPrefs.preferences
    ) { sources, builtinEnabled, jellyfin ->
        val (display, sync, controls, storage, app) = sources
        UserPreferences(
            rommBaseUrl = sync.rommBaseUrl,
            rommUsername = sync.rommUsername,
            rommUserId = sync.rommUserId,
            rommAvatarPath = sync.rommAvatarPath,
            rommProfileRevision = sync.rommProfileRevision,
            rommToken = sync.rommToken,
            rommDeviceId = sync.rommDeviceId,
            rommDeviceClientVersion = sync.rommDeviceClientVersion,
            raUsername = sync.raUsername,
            raToken = sync.raToken,
            raWebApiKey = sync.raWebApiKey,
            raProxyEnabled = sync.raProxyEnabled,
            raProxyAddress = sync.raProxyAddress,
            romStoragePath = storage.romStoragePath,
            showStatusClock = display.showStatusClock,
            showStatusBattery = display.showStatusBattery,
            showStatusNetwork = display.showStatusNetwork,
            themeMode = display.themeMode,
            primaryColor = display.primaryColor,
            secondaryColor = display.secondaryColor,
            tertiaryColor = display.tertiaryColor,
            surfaceTintBleed = display.surfaceTintBleed,
            backdropEnabled = display.backdropEnabled,
            backdropPreset = display.backdropPreset,
            backdropCellSize = display.backdropCellSize,
            backdropScatter = display.backdropScatter,
            backdropScaleJitter = display.backdropScaleJitter,
            backdropStrength = display.backdropStrength,
            backdropEdgeStyle = display.backdropEdgeStyle,
            backdropVertexIcons = display.backdropVertexIcons,
            backdropSeed = display.backdropSeed,
            backdropMotion = display.backdropMotion,
            backdropMotionSpeed = display.backdropMotionSpeed,
            backdropDriftAngle = display.backdropDriftAngle,
            displayFontPath = display.displayFontPath,
            displayFontName = display.displayFontName,
            bodyFontPath = display.bodyFontPath,
            bodyFontName = display.bodyFontName,
            displayFontScale = display.displayFontScale,
            bodyFontScale = display.bodyFontScale,
            hapticEnabled = controls.hapticEnabled,
            hapticStrength = controls.hapticStrength,
            soundEnabled = controls.soundEnabled,
            soundVolume = controls.soundVolume,
            swapAB = controls.swapAB,
            swapXY = controls.swapXY,
            controllerLayout = controls.controllerLayout,
            swapStartSelect = controls.swapStartSelect,
            selectLCombo = controls.selectLCombo,
            selectRCombo = controls.selectRCombo,
            menuWrapMode = controls.menuWrapMode,
            selectSwapMode = controls.selectSwapMode,
            openNavigationKey = controls.openNavigationKey,
            openQuickPanelKey = controls.openQuickPanelKey,
            quickPanelInEmulators = controls.quickPanelInEmulators,
            navRingRoutes = controls.navRingRoutes,
            quickNavigation = controls.quickNavigation,
            navBarAutoHide = controls.navBarAutoHide,
            ignoreControllerTouchpad = controls.ignoreControllerTouchpad,
            systemHotkeysEnabled = controls.systemHotkeysEnabled,
            homeButtonInGames = controls.homeButtonInGames,
            systemHomeCombo = controls.systemHomeCombo,
            systemBackCombo = controls.systemBackCombo,
            lastRommSync = sync.lastRommSync,
            rommChangesWatermark = sync.rommChangesWatermark,
            lastFavoritesSync = sync.lastFavoritesSync,
            lastFavoritesCheck = sync.lastFavoritesCheck,
            syncFilters = sync.syncFilters,
            regionPriority = sync.regionPriority,
            syncScreenshotsEnabled = sync.syncScreenshotsEnabled,
            uploadScreenshotsEnabled = sync.uploadScreenshotsEnabled,
            boxArtCacheEnabled = sync.boxArtCacheEnabled,
            backgroundBlur = display.backgroundBlur,
            backgroundSaturation = display.backgroundSaturation,
            backgroundOpacity = display.backgroundOpacity,
            useGameBackground = display.useGameBackground,
            customBackgroundPath = display.customBackgroundPath,
            homeBackgroundMode = display.homeBackgroundMode,
            homeLayout = display.homeLayout,
            presentationStyle = display.presentationStyle,
            presentationBackgroundBlur = display.presentationBackgroundBlur,
            useAccentColorFooter = display.useAccentColorFooter,
            compactFooter = display.compactFooter,
            hideSystemNavigationBar = display.hideSystemNavigationBar,
            hideSystemStatusBar = display.hideSystemStatusBar,
            screenOrientation = display.screenOrientation,
            lockScreenArt = display.lockScreenArt,
            homeAppAvailable = display.homeAppAvailable,
            iconPack = display.iconPack,
            hiddenApps = app.hiddenApps,
            secondaryHomeApps = app.secondaryHomeApps,
            visibleSystemApps = app.visibleSystemApps,
            appOrder = app.appOrder,
            maxConcurrentDownloads = storage.maxConcurrentDownloads,
            allowRemoteInstalls = storage.allowRemoteInstalls,
            autoStartDownloads = storage.autoStartDownloads,
            instantDownloadThresholdMb = storage.instantDownloadThresholdMb,
            stageDownloadsInternally = storage.stageDownloadsInternally,
            folderNameFromRom = storage.folderNameFromRom,
            gridDensity = display.gridDensity,
            libraryLayout = display.libraryLayout,
            libraryListStyle = display.libraryListStyle,
            libraryBoxArt3d = display.libraryBoxArt3d,
            libraryDefaultSort = display.libraryDefaultSort,
            libraryDefaultSortDescending = display.libraryDefaultSortDescending,
            sortInstalledFirst = display.sortInstalledFirst,
            sortFavoritesFirst = display.sortFavoritesFirst,
            libraryDefaultSource = display.libraryDefaultSource,
            libraryDefaultPlatformId = display.libraryDefaultPlatformId,
            libraryDefaultRegions = display.libraryDefaultRegions,
            libraryDefaultPlayers = display.libraryDefaultPlayers,
            soundConfigs = controls.soundConfigs,
            betaUpdatesEnabled = app.betaUpdatesEnabled,
            debugUpdateChecksEnabled = app.debugUpdateChecksEnabled,
            closeEmulatorOnSessionEnd = app.closeEmulatorOnSessionEnd,
            appLanguage = app.appLanguage,
            preLaunchRecapsEnabled = app.preLaunchRecapsEnabled,
            monthlyRecapEnabled = app.monthlyRecapEnabled,
            lastMonthlyRecapShown = app.lastMonthlyRecapShown,
            preLaunchRecapBaseline = app.preLaunchRecapBaseline,
            notificationsEnabled = app.notificationsEnabled,
            notifyDownloadsFinished = app.notifyDownloadsFinished,
            notifyBattery = app.notifyBattery,
            notifyCharging = app.notifyCharging,
            notifyControllers = app.notifyControllers,
            saveSyncEnabled = sync.saveSyncEnabled,
            notOnDeviceAction = sync.notOnDeviceAction,
            secureSaves = sync.secureSaves,
            stateCacheEnabled = sync.stateCacheEnabled,
            saveCacheLimit = sync.saveCacheLimit,
            fileLoggingEnabled = app.fileLoggingEnabled,
            fileLoggingPath = app.fileLoggingPath,
            fileLogLevel = app.fileLogLevel,
            saveDebugLoggingEnabled = sync.saveDebugLoggingEnabled,
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
            platformIndicatorStyle = display.platformIndicatorStyle,
            platformIndicatorContent = display.platformIndicatorContent,
            defaultView = display.defaultView,
            recommendedGameIds = app.recommendedGameIds,
            lastRecommendationGeneration = app.lastRecommendationGeneration,
            recommendationPenalties = app.recommendationPenalties,
            lastPenaltyDecayWeek = app.lastPenaltyDecayWeek,
            lastSeenVersion = app.lastSeenVersion,
            libraryRecentSearches = app.libraryRecentSearches,
            ambientAudioEnabled = controls.ambientAudioEnabled,
            ambientAudioVolume = controls.ambientAudioVolume,
            ambientAudioUri = controls.ambientAudioUri,
            ambientAudioShuffle = controls.ambientAudioShuffle,
            ambientAudioPlayInBackground = controls.ambientAudioPlayInBackground,
            gameDetailThemeEnabled = controls.gameDetailThemeEnabled,
            imageCachePath = sync.imageCachePath,
            screenDimmerEnabled = display.screenDimmerEnabled,
            screenDimmerTimeoutMinutes = display.screenDimmerTimeoutMinutes,
            screenDimmerLevel = display.screenDimmerLevel,
            customBiosPath = storage.customBiosPath,
            weeklyIntegrityCheckEnabled = storage.weeklyIntegrityCheckEnabled,
            lastIntegrityCheckTime = storage.lastIntegrityCheckTime,
            videoWallpaperEnabled = display.videoWallpaperEnabled,
            videoWallpaperDelaySeconds = display.videoWallpaperDelaySeconds,
            videoWallpaperMuted = display.videoWallpaperMuted,
            uiScale = display.uiScale,
            gripReserveMode = display.gripReserveMode,
            gripReservePercent = display.gripReservePercent,
            gripAutoControllers = display.gripAutoControllers,
            ambientLedEnabled = display.ambientLedEnabled,
            ambientLedBrightness = display.ambientLedBrightness,
            ambientLedAudioBrightness = display.ambientLedAudioBrightness,
            ambientLedAudioColors = display.ambientLedAudioColors,
            ambientLedColorMode = display.ambientLedColorMode,
            ambientLedCoverArtEnabled = display.ambientLedCoverArtEnabled,
            ambientLedCustomColor = display.ambientLedCustomColor,
            ambientLedCustomColorHue = display.ambientLedCustomColorHue,
            ambientLedScreenEnabled = display.ambientLedScreenEnabled,
            ambientLedTransitionMs = display.ambientLedTransitionMs,
            ambientLedAchievementFlash = display.ambientLedAchievementFlash,
            builtinLibretroEnabled = builtinEnabled,
            appDisplayTargets = app.appDisplayTargets,
            lidSleepEnabled = display.lidSleepEnabled,
            dualScreenEnabled = display.dualScreenEnabled,
            displayRoleOverride = display.displayRoleOverride,
            gamesDisplayTarget = display.gamesDisplayTarget,
            appsDisplayTarget = display.appsDisplayTarget,
            dualScreenInputFocus = display.dualScreenInputFocus,
            screenLayouts = display.screenLayouts,
            installedOnlyHome = display.installedOnlyHome,
            recentNewGamesLimit = display.recentNewGamesLimit,
            newBadgePlacement = display.newBadgePlacement,
            socialSessionToken = sync.socialSessionToken,
            socialUserId = sync.socialUserId,
            socialUsername = sync.socialUsername,
            socialDisplayName = sync.socialDisplayName,
            socialAvatarColor = sync.socialAvatarColor,
            socialAvatarDoodle = sync.socialAvatarDoodle,
            socialAvatarUseDoodle = sync.socialAvatarUseDoodle,
            socialOnlineStatusEnabled = sync.socialOnlineStatusEnabled,
            socialShowNowPlaying = sync.socialShowNowPlaying,
            socialNotifyFriendOnline = sync.socialNotifyFriendOnline,
            socialNotifyFriendPlaying = sync.socialNotifyFriendPlaying,
            socialSuppressNotificationsInGame = sync.socialSuppressNotificationsInGame,
            discordRichPresenceEnabled = sync.discordRichPresenceEnabled,
            lastPlaySessionSync = sync.lastPlaySessionSync,
            lastStateValidation = sync.lastStateValidation,
            quayPassEnabled = sync.quayPassEnabled,
            quayPassAvatarSyncPending = sync.quayPassAvatarSyncPending,
            quayPassMessageSyncPending = sync.quayPassMessageSyncPending,
            quayPassGreeting = sync.quayPassGreeting,
            quayPassTicketBalance = sync.quayPassTicketBalance,
            mediaStoragePath = storage.mediaStoragePath,
            jellyfinServerUrl = jellyfin.serverUrl,
            jellyfinDeviceId = jellyfin.deviceId,
            jellyfinAccessToken = jellyfin.accessToken,
            jellyfinUserId = jellyfin.userId,
            jellyfinUserName = jellyfin.userName,
            mediaDownloadQuality = jellyfin.downloadQuality,
            mediaStreamingQuality = jellyfin.streamingQuality,
            mediaAudioLanguage = jellyfin.audioLanguage,
            mediaSubtitleMode = jellyfin.subtitleMode,
            mediaSubtitleLanguage = jellyfin.subtitleLanguage,
            mediaBurnInImageSubtitles = jellyfin.burnInImageSubtitles,
            mediaConfirmPlayerExit = jellyfin.confirmPlayerExit,
            shareMediaPresence = jellyfin.shareMediaPresence
        )
    }

    val userPreferences: Flow<UserPreferences> = preferencesSource

    val preferences: Flow<UserPreferences> = userPreferences

    private val hotPreferences: SharedFlow<UserPreferences> =
        preferencesSource.shareIn(shareScope, SharingStarted.Eagerly, replay = 1)

    /**
     * The most recent preferences without suspending, or null before the first DataStore read.
     * A seed for a screen's first frame; reads that must see a write just made use [userPreferences].
     */
    val latest: UserPreferences? get() = hotPreferences.replayCache.firstOrNull()

    val menuWrapMode: MenuWrapMode get() = (latest ?: UserPreferences()).menuWrapMode

    // --- Display delegates ---

    suspend fun setThemeMode(mode: ThemeMode) = displayPrefs.setThemeMode(mode)
    suspend fun setCustomColors(primary: Int?, secondary: Int?, tertiary: Int?) = displayPrefs.setCustomColors(primary, secondary, tertiary)

    /**
     * Sets the accent colour, or the theme default when [color] is null. The secondary and
     * tertiary colours reset with it.
     */
    suspend fun setPrimaryColor(color: Int?) = displayPrefs.setCustomColors(color, null, null)
    suspend fun setSecondaryColor(color: Int?) = displayPrefs.setSecondaryColor(color)
    suspend fun setSurfaceTintBleed(bleed: Int) = displayPrefs.setSurfaceTintBleed(bleed)
    suspend fun setBackdropEnabled(enabled: Boolean) = displayPrefs.setBackdropEnabled(enabled)
    suspend fun setBackdropPreset(preset: BackdropPreset, edgeStyle: BackdropEdgeStyle, vertexIcons: BackdropVertexIcon) =
        displayPrefs.setBackdropPreset(preset, edgeStyle, vertexIcons)
    suspend fun setBackdropCellSize(sizeDp: Int) = displayPrefs.setBackdropCellSize(sizeDp)
    suspend fun setBackdropScatter(scatter: Int) = displayPrefs.setBackdropScatter(scatter)
    suspend fun setBackdropScaleJitter(jitter: Int) = displayPrefs.setBackdropScaleJitter(jitter)
    suspend fun setBackdropStrength(strength: Int) = displayPrefs.setBackdropStrength(strength)
    suspend fun setBackdropEdgeStyle(style: BackdropEdgeStyle) = displayPrefs.setBackdropEdgeStyle(style)
    suspend fun setBackdropVertexIcons(icons: BackdropVertexIcon) = displayPrefs.setBackdropVertexIcons(icons)
    suspend fun setBackdropSeed(seed: Long) = displayPrefs.setBackdropSeed(seed)
    suspend fun setBackdropMotion(motion: BackdropMotion) = displayPrefs.setBackdropMotion(motion)
    suspend fun setBackdropMotionSpeed(speed: Int) = displayPrefs.setBackdropMotionSpeed(speed)
    suspend fun setBackdropDriftAngle(angle: Float) = displayPrefs.setBackdropDriftAngle(angle)
    suspend fun setCustomFont(slot: FontSlot, path: String?, name: String?) = displayPrefs.setCustomFont(slot, path, name)
    suspend fun setFontScale(slot: FontSlot, scale: Int) = displayPrefs.setFontScale(slot, scale)
    suspend fun setGridDensity(density: GridDensity) = displayPrefs.setGridDensity(density)
    suspend fun setLibraryLayout(layout: LibraryLayout) = displayPrefs.setLibraryLayout(layout)

    suspend fun setLibraryListStyle(style: LibraryListStyle) = displayPrefs.setLibraryListStyle(style)

    suspend fun setLibraryBoxArt3d(enabled: Boolean) = displayPrefs.setLibraryBoxArt3d(enabled)

    suspend fun setLibraryDefaultSort(option: String, descending: Boolean) =
        displayPrefs.setLibraryDefaultSort(option, descending)

    suspend fun setSortInstalledFirst(enabled: Boolean) = displayPrefs.setSortInstalledFirst(enabled)

    suspend fun setSortFavoritesFirst(enabled: Boolean) = displayPrefs.setSortFavoritesFirst(enabled)

    suspend fun setLibraryDefaultSource(source: String) = displayPrefs.setLibraryDefaultSource(source)

    suspend fun setLibraryDefaultPlatformId(platformId: Long?) =
        displayPrefs.setLibraryDefaultPlatformId(platformId)

    suspend fun setLibraryDefaultRegions(regions: Set<String>) = displayPrefs.setLibraryDefaultRegions(regions)

    suspend fun setLibraryDefaultPlayers(bucket: com.nendo.argosy.domain.model.PlayerCountBucket?) =
        displayPrefs.setLibraryDefaultPlayers(bucket)

    suspend fun migrateLegacyDefaultPlatform(idForDisplayName: (String) -> Long?) =
        displayPrefs.migrateLegacyDefaultPlatform(idForDisplayName)
    suspend fun setUiScale(scale: Int) = displayPrefs.setUiScale(scale)
    suspend fun setGripReserveEnabled(enabled: Boolean) = displayPrefs.setGripReserveEnabled(enabled)
    suspend fun setGripReservePercent(percent: Int) = displayPrefs.setGripReservePercent(percent)
    suspend fun setBackgroundBlur(blur: Int) = displayPrefs.setBackgroundBlur(blur)
    suspend fun setBackgroundSaturation(saturation: Int) = displayPrefs.setBackgroundSaturation(saturation)
    suspend fun setBackgroundOpacity(opacity: Int) = displayPrefs.setBackgroundOpacity(opacity)
    suspend fun setUseGameBackground(use: Boolean) = displayPrefs.setUseGameBackground(use)
    suspend fun setCustomBackgroundPath(path: String?) = displayPrefs.setCustomBackgroundPath(path)
    suspend fun setHomeBackgroundMode(mode: HomeBackgroundMode) = displayPrefs.setHomeBackgroundMode(mode)
    suspend fun setHomeLayout(settings: com.nendo.argosy.domain.model.HomeLayoutSettings) =
        displayPrefs.setHomeLayout(settings)
    suspend fun setPresentationStyle(style: com.nendo.argosy.domain.model.PresentationStyle) =
        displayPrefs.setPresentationStyle(style)
    suspend fun setPresentationBackgroundBlur(blur: Int) = displayPrefs.setPresentationBackgroundBlur(blur)
    suspend fun setUseAccentColorFooter(use: Boolean) = displayPrefs.setUseAccentColorFooter(use)
    suspend fun setCompactFooter(enabled: Boolean) = displayPrefs.setCompactFooter(enabled)
    suspend fun setHideSystemNavigationBar(hide: Boolean) = displayPrefs.setHideSystemNavigationBar(hide)
    suspend fun setHideSystemStatusBar(hide: Boolean) = displayPrefs.setHideSystemStatusBar(hide)
    suspend fun setScreenOrientation(mode: ScreenOrientationMode) = displayPrefs.setScreenOrientation(mode)
    suspend fun setLockScreenArt(enabled: Boolean) = displayPrefs.setLockScreenArt(enabled)
    suspend fun setIconPack(packageName: String?) = displayPrefs.setIconPack(packageName)
    suspend fun setGripAutoControllers(
        controllers: com.nendo.argosy.domain.model.GripAutoControllers
    ) = displayPrefs.setGripAutoControllers(controllers)
    suspend fun setGripReserveMode(mode: GripReserveMode) = displayPrefs.setGripReserveMode(mode)
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
    suspend fun setPlatformIndicatorStyle(style: PlatformIndicatorStyle) = displayPrefs.setPlatformIndicatorStyle(style)
    suspend fun setPlatformIndicatorContent(content: PlatformIndicatorContent) = displayPrefs.setPlatformIndicatorContent(content)
    suspend fun setDefaultView(view: DefaultView) = displayPrefs.setDefaultView(view)
    suspend fun setVideoWallpaperEnabled(enabled: Boolean) = displayPrefs.setVideoWallpaperEnabled(enabled)
    suspend fun setVideoWallpaperDelaySeconds(seconds: Int) = displayPrefs.setVideoWallpaperDelaySeconds(seconds)
    suspend fun setVideoWallpaperMuted(muted: Boolean) = displayPrefs.setVideoWallpaperMuted(muted)
    suspend fun setAmbientLedEnabled(enabled: Boolean) = displayPrefs.setAmbientLedEnabled(enabled)
    suspend fun setAmbientLedBrightness(brightness: Int) = displayPrefs.setAmbientLedBrightness(brightness)
    suspend fun setAmbientLedAudioBrightness(enabled: Boolean) = displayPrefs.setAmbientLedAudioBrightness(enabled)
    suspend fun setAmbientLedAudioColors(enabled: Boolean) = displayPrefs.setAmbientLedAudioColors(enabled)
    suspend fun setAmbientLedColorMode(mode: AmbientLedColorMode) = displayPrefs.setAmbientLedColorMode(mode)
    suspend fun setAmbientLedCoverArtEnabled(enabled: Boolean) = displayPrefs.setAmbientLedCoverArtEnabled(enabled)
    suspend fun setAmbientLedCustomColor(enabled: Boolean) = displayPrefs.setAmbientLedCustomColor(enabled)
    suspend fun setAmbientLedCustomColorHue(hue: Int) = displayPrefs.setAmbientLedCustomColorHue(hue)
    suspend fun setAmbientLedTransitionMs(ms: Int) = displayPrefs.setAmbientLedTransitionMs(ms)
    suspend fun setAmbientLedScreenEnabled(enabled: Boolean) = displayPrefs.setAmbientLedScreenEnabled(enabled)
    suspend fun setAmbientLedAchievementFlash(enabled: Boolean) = displayPrefs.setAmbientLedAchievementFlash(enabled)
    suspend fun setScreenDimmerEnabled(enabled: Boolean) = displayPrefs.setScreenDimmerEnabled(enabled)
    suspend fun setScreenDimmerTimeoutMinutes(minutes: Int) = displayPrefs.setScreenDimmerTimeoutMinutes(minutes)
    suspend fun setScreenDimmerLevel(level: Int) = displayPrefs.setScreenDimmerLevel(level)
    suspend fun setLidSleepEnabled(enabled: Boolean) = displayPrefs.setLidSleepEnabled(enabled)
    suspend fun setDualScreenEnabled(enabled: Boolean) = displayPrefs.setDualScreenEnabled(enabled)
    suspend fun setDisplayRoleOverride(override: DisplayRoleOverride) = displayPrefs.setDisplayRoleOverride(override)
    suspend fun setGamesDisplayTarget(target: EmulatorDisplayTarget) = displayPrefs.setGamesDisplayTarget(target)
    suspend fun setAppsDisplayTarget(target: EmulatorDisplayTarget) = displayPrefs.setAppsDisplayTarget(target)
    suspend fun setScreenLayouts(layouts: ScreenLayouts) = displayPrefs.setScreenLayouts(layouts)
    suspend fun setDualScreenInputFocus(focus: DualScreenInputFocus) = displayPrefs.setDualScreenInputFocus(focus)
    suspend fun setInstalledOnlyHome(enabled: Boolean) = displayPrefs.setInstalledOnlyHome(enabled)
    suspend fun setRecentNewGamesLimit(limit: Int) = displayPrefs.setRecentNewGamesLimit(limit)
    suspend fun setNewBadgePlacement(placement: NewBadgePlacement) = displayPrefs.setNewBadgePlacement(placement)

    suspend fun setShowStatusClock(show: Boolean) = displayPrefs.setShowStatusClock(show)

    suspend fun setShowStatusBattery(show: Boolean) = displayPrefs.setShowStatusBattery(show)

    suspend fun setShowStatusNetwork(show: Boolean) = displayPrefs.setShowStatusNetwork(show)

    // --- Sync delegates ---

    suspend fun setRommConfig(url: String?, username: String?) = syncPrefs.setRommConfig(url, username)

    suspend fun privateIgdbIds(): List<Long> = syncPrefs.privateIgdbIds()

    suspend fun claimRaSignInNotice(): Boolean = syncPrefs.claimRaSignInNotice()
    suspend fun setDownloadCategoryDefault(categoryKey: String, include: Boolean) =
        syncPrefs.setDownloadCategoryDefault(categoryKey, include)
    suspend fun setDownloadCategoryPlatformOverride(platformSlug: String, categoryKey: String, include: Boolean?) =
        syncPrefs.setDownloadCategoryPlatformOverride(platformSlug, categoryKey, include)
    suspend fun getGlobalDownloadDefaults(): Map<String, Boolean> =
        DownloadDefaults.FACTORY + syncPrefs.downloadCategoryDefaults.first()
    suspend fun getDownloadPlatformOverrides(platformSlug: String): Map<String, Boolean> =
        syncPrefs.downloadCategoryPlatformOverrides.first()[platformSlug] ?: emptyMap()
    suspend fun getEffectiveDownloadDefaults(platformSlug: String): Map<String, Boolean> =
        syncPrefs.getEffectiveDownloadDefaults(platformSlug)
    suspend fun setRomMCredentials(baseUrl: String, token: String, username: String? = null, userId: Long? = null, avatarPath: String? = null) =
        syncPrefs.setRomMCredentials(baseUrl, token, username, userId, avatarPath)
    suspend fun refreshRomMProfile(token: String, expectedUserId: Long?, expectedBaseUrl: String?, username: String, avatarPath: String?, newBaseUrl: String? = null) =
        syncPrefs.refreshRomMProfile(token, expectedUserId, expectedBaseUrl, username, avatarPath, newBaseUrl)
    suspend fun clearRomMCredentials() = syncPrefs.clearRomMCredentials()
    suspend fun setRommDeviceId(deviceId: String, clientVersion: String) = syncPrefs.setRommDeviceId(deviceId, clientVersion)
    suspend fun clearRommDeviceId() = syncPrefs.clearRommDeviceId()
    suspend fun getRommSnapshotSupport(serverKey: String): Boolean? = syncPrefs.getRommSnapshotSupport(serverKey)
    suspend fun setRommSnapshotSupport(serverKey: String, supported: Boolean) =
        syncPrefs.setRommSnapshotSupport(serverKey, supported)
    suspend fun setRACredentials(username: String, token: String) = syncPrefs.setRACredentials(username, token)
    suspend fun clearRACredentials() = syncPrefs.clearRACredentials()
    suspend fun setRAWebApiKey(key: String?) = syncPrefs.setRAWebApiKey(key)
    suspend fun setRAProxy(enabled: Boolean, address: String) = syncPrefs.setRAProxy(enabled, address)
    suspend fun setLastRommSyncTime(time: Instant) = syncPrefs.setLastRommSyncTime(time)

    suspend fun setRommChangesWatermark(time: Instant) = syncPrefs.setRommChangesWatermark(time)
    suspend fun setLastRommFullSyncTime(time: Instant) = syncPrefs.setLastRommFullSyncTime(time)
    suspend fun getSyncResumeGeneration(): Instant? = syncPrefs.getSyncResumeGeneration()
    suspend fun getSyncResumeCompletedPlatformIds(): Set<Long> = syncPrefs.getSyncResumeCompletedPlatformIds()
    suspend fun startSyncGeneration(time: Instant) = syncPrefs.startSyncGeneration(time)
    suspend fun addSyncResumeCompletedPlatform(platformId: Long) = syncPrefs.addSyncResumeCompletedPlatform(platformId)
    suspend fun clearSyncResume() = syncPrefs.clearSyncResume()
    suspend fun setLastFavoritesSyncTime(time: Instant) = syncPrefs.setLastFavoritesSyncTime(time)
    suspend fun setLastFavoritesCheckTime(time: Instant) = syncPrefs.setLastFavoritesCheckTime(time)
    suspend fun setSyncFilterRegions(regions: List<String>) = syncPrefs.setSyncFilterRegions(regions)
    suspend fun setSyncFilterRegionMode(mode: RegionFilterMode) = syncPrefs.setSyncFilterRegionMode(mode)
    suspend fun setRegionPriority(order: List<String>) = syncPrefs.setRegionPriority(order)
    suspend fun setSyncFilterExcludeBeta(exclude: Boolean) = syncPrefs.setSyncFilterExcludeBeta(exclude)
    suspend fun setSyncFilterExcludePrototype(exclude: Boolean) = syncPrefs.setSyncFilterExcludePrototype(exclude)
    suspend fun setSyncFilterExcludeDemo(exclude: Boolean) = syncPrefs.setSyncFilterExcludeDemo(exclude)
    suspend fun setSyncFilterExcludeHack(exclude: Boolean) = syncPrefs.setSyncFilterExcludeHack(exclude)
    suspend fun setSyncFilterExcludeUnofficial(exclude: Boolean) =
        syncPrefs.setSyncFilterExcludeUnofficial(exclude)
    suspend fun setSyncFilterDeleteOrphans(delete: Boolean) = syncPrefs.setSyncFilterDeleteOrphans(delete)
    suspend fun setSyncScreenshotsEnabled(enabled: Boolean) = syncPrefs.setSyncScreenshotsEnabled(enabled)
    suspend fun setUploadScreenshotsEnabled(enabled: Boolean) = syncPrefs.setUploadScreenshotsEnabled(enabled)
    suspend fun setBoxArtCacheEnabled(enabled: Boolean) = syncPrefs.setBoxArtCacheEnabled(enabled)
    suspend fun setSaveSyncEnabled(enabled: Boolean) = syncPrefs.setSaveSyncEnabled(enabled)
    suspend fun setNotOnDeviceAction(action: NotOnDeviceAction) = syncPrefs.setNotOnDeviceAction(action)
    suspend fun setSecureSaves(enabled: Boolean) = syncPrefs.setSecureSaves(enabled)
    suspend fun setStateCacheEnabled(enabled: Boolean) = syncPrefs.setStateCacheEnabled(enabled)
    suspend fun setSaveCacheLimit(limit: Int) = syncPrefs.setSaveCacheLimit(limit)
    suspend fun setSaveDebugLoggingEnabled(enabled: Boolean) = syncPrefs.setSaveDebugLoggingEnabled(enabled)
    suspend fun setImageCachePath(path: String?) = syncPrefs.setImageCachePath(path)

    // --- Social delegates ---

    suspend fun setSocialCredentials(
        sessionToken: String,
        userId: String,
        username: String,
        displayName: String?,
        avatarColor: String?
    ) = syncPrefs.setSocialCredentials(sessionToken, userId, username, displayName, avatarColor)
    suspend fun clearSocialCredentials() = syncPrefs.clearSocialCredentials()
    suspend fun setSocialAvatarUseDoodle(enabled: Boolean) = syncPrefs.setSocialAvatarUseDoodle(enabled)
    suspend fun setSocialOnlineStatusEnabled(enabled: Boolean) = syncPrefs.setSocialOnlineStatusEnabled(enabled)
    suspend fun setSocialShowNowPlaying(enabled: Boolean) = syncPrefs.setSocialShowNowPlaying(enabled)
    suspend fun setSocialNotifyFriendOnline(enabled: Boolean) = syncPrefs.setSocialNotifyFriendOnline(enabled)
    suspend fun setSocialNotifyFriendPlaying(enabled: Boolean) = syncPrefs.setSocialNotifyFriendPlaying(enabled)
    suspend fun setSocialSuppressNotificationsInGame(enabled: Boolean) = syncPrefs.setSocialSuppressNotificationsInGame(enabled)
    suspend fun setDiscordRichPresenceEnabled(enabled: Boolean) = syncPrefs.setDiscordRichPresenceEnabled(enabled)
    suspend fun setLastPlaySessionSyncTime(time: Instant) = syncPrefs.setLastPlaySessionSyncTime(time)
    suspend fun setQuayPassEnabled(enabled: Boolean) = syncPrefs.setQuayPassEnabled(enabled)
    suspend fun setQuayPassAvatarSyncPending(pending: Boolean) = syncPrefs.setQuayPassAvatarSyncPending(pending)
    suspend fun setQuayPassMessageSyncPending(pending: Boolean) = syncPrefs.setQuayPassMessageSyncPending(pending)
    suspend fun setQuayPassGreetingFromServer(greeting: String) = syncPrefs.setQuayPassGreetingFromServer(greeting)
    suspend fun setSocialAvatarFromServer(doodle: String?) = syncPrefs.setSocialAvatarFromServer(doodle)
    suspend fun setQuayPassGreeting(greeting: String) = syncPrefs.setQuayPassGreeting(greeting)
    suspend fun setQuayPassTicketBalance(balance: Int) = syncPrefs.setQuayPassTicketBalance(balance)

    // --- Controls delegates ---

    suspend fun setHapticEnabled(enabled: Boolean) = controlsPrefs.setHapticEnabled(enabled)
    suspend fun setHapticStrength(strength: Float) = controlsPrefs.setHapticStrength(strength)
    suspend fun adjustHapticStrength(delta: Float): Float = controlsPrefs.adjustHapticStrength(delta)
    suspend fun setSoundEnabled(enabled: Boolean) = controlsPrefs.setSoundEnabled(enabled)
    suspend fun setSoundVolume(volume: Int) = controlsPrefs.setSoundVolume(volume)
    suspend fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) = controlsPrefs.setSoundConfigs(configs)
    suspend fun setSoundConfig(type: SoundType, config: SoundConfig?) = controlsPrefs.setSoundConfig(type, config)
    suspend fun setSwapAB(enabled: Boolean) = controlsPrefs.setSwapAB(enabled)
    suspend fun setSwapXY(enabled: Boolean) = controlsPrefs.setSwapXY(enabled)
    suspend fun setControllerLayout(layout: String) = controlsPrefs.setControllerLayout(layout)
    suspend fun setSwapStartSelect(enabled: Boolean) = controlsPrefs.setSwapStartSelect(enabled)
    suspend fun setSelectLCombo(value: String) = controlsPrefs.setSelectLCombo(value)
    suspend fun setSelectRCombo(value: String) = controlsPrefs.setSelectRCombo(value)
    suspend fun setAmbientAudioEnabled(enabled: Boolean) = controlsPrefs.setAmbientAudioEnabled(enabled)
    suspend fun setAmbientAudioVolume(volume: Int) = controlsPrefs.setAmbientAudioVolume(volume)
    suspend fun setAmbientAudioUri(uri: String?) = controlsPrefs.setAmbientAudioUri(uri)
    suspend fun setAmbientAudioShuffle(shuffle: Boolean) = controlsPrefs.setAmbientAudioShuffle(shuffle)
    suspend fun setGameDetailThemeEnabled(enabled: Boolean) = controlsPrefs.setGameDetailThemeEnabled(enabled)
    suspend fun setMenuWrapMode(mode: MenuWrapMode) = controlsPrefs.setMenuWrapMode(mode)
    suspend fun setSelectSwapMode(mode: SelectSwapMode) = controlsPrefs.setSelectSwapMode(mode)
    suspend fun setOpenNavigationKey(keyCode: Int) = controlsPrefs.setOpenNavigationKey(keyCode)
    suspend fun setOpenQuickPanelKey(keyCode: Int) = controlsPrefs.setOpenQuickPanelKey(keyCode)
    suspend fun setQuickPanelInEmulators(enabled: Boolean) = controlsPrefs.setQuickPanelInEmulators(enabled)
    suspend fun setNavRingRoutes(routes: List<String>?) = controlsPrefs.setNavRingRoutes(routes)
    suspend fun setQuickNavigation(enabled: Boolean) = controlsPrefs.setQuickNavigation(enabled)
    suspend fun setNavBarAutoHide(enabled: Boolean) = controlsPrefs.setNavBarAutoHide(enabled)
    suspend fun setIgnoreControllerTouchpad(enabled: Boolean) = controlsPrefs.setIgnoreControllerTouchpad(enabled)
    suspend fun setSystemHotkeysEnabled(enabled: Boolean) = controlsPrefs.setSystemHotkeysEnabled(enabled)
    suspend fun setHomeButtonInGames(enabled: Boolean) = controlsPrefs.setHomeButtonInGames(enabled)
    suspend fun setSystemHomeCombo(keyCodes: List<Int>?) = controlsPrefs.setSystemHomeCombo(keyCodes)
    suspend fun setSystemBackCombo(keyCodes: List<Int>?) = controlsPrefs.setSystemBackCombo(keyCodes)


    // --- Storage delegates ---

    suspend fun setRomStoragePath(path: String) = storagePrefs.setRomStoragePath(path)
    suspend fun setMaxConcurrentDownloads(count: Int) = storagePrefs.setMaxConcurrentDownloads(count)
    suspend fun setAllowRemoteInstalls(enabled: Boolean) = storagePrefs.setAllowRemoteInstalls(enabled)
    suspend fun setAutoStartDownloads(enabled: Boolean) = storagePrefs.setAutoStartDownloads(enabled)
    suspend fun setInstantDownloadThresholdMb(value: Int) = storagePrefs.setInstantDownloadThresholdMb(value)
    suspend fun setStageDownloadsInternally(enabled: Boolean) = storagePrefs.setStageDownloadsInternally(enabled)

    suspend fun setFolderNameFromRom(enabled: Boolean) = storagePrefs.setFolderNameFromRom(enabled)
    suspend fun setCustomBiosPath(path: String?) = storagePrefs.setCustomBiosPath(path)
    suspend fun setWeeklyIntegrityCheckEnabled(enabled: Boolean) = storagePrefs.setWeeklyIntegrityCheckEnabled(enabled)
    suspend fun setLastIntegrityCheckTime(timeMs: Long) = storagePrefs.setLastIntegrityCheckTime(timeMs)
    suspend fun setMediaStoragePath(path: String?) = storagePrefs.setMediaStoragePath(path)

    suspend fun setJellyfinServerUrl(url: String?) = jellyfinPrefs.setServerUrl(url)
    suspend fun setJellyfinDeviceId(deviceId: String) = jellyfinPrefs.setDeviceId(deviceId)
    suspend fun setJellyfinCredentials(accessToken: String, userId: String, userName: String?) =
        jellyfinPrefs.setCredentials(accessToken, userId, userName)
    suspend fun clearJellyfinCredentials() = jellyfinPrefs.clearCredentials()
    suspend fun setMediaDownloadQuality(quality: MediaDownloadQuality) =
        jellyfinPrefs.setDownloadQuality(quality)
    suspend fun setMediaStreamingQuality(quality: MediaStreamingQuality) =
        jellyfinPrefs.setStreamingQuality(quality)
    suspend fun setMediaAudioLanguage(language: MediaAudioLanguage) =
        jellyfinPrefs.setAudioLanguage(language)
    suspend fun setMediaSubtitleMode(mode: MediaSubtitleMode) = jellyfinPrefs.setSubtitleMode(mode)
    suspend fun setMediaSubtitleLanguage(language: MediaSubtitleLanguage) =
        jellyfinPrefs.setSubtitleLanguage(language)
    suspend fun setMediaBurnInImageSubtitles(enabled: Boolean) =
        jellyfinPrefs.setBurnInImageSubtitles(enabled)
    suspend fun setMediaConfirmPlayerExit(enabled: Boolean) =
        jellyfinPrefs.setConfirmPlayerExit(enabled)
    suspend fun setShareMediaPresence(enabled: Boolean) = jellyfinPrefs.setShareMediaPresence(enabled)

    // --- App delegates ---

    suspend fun setBetaUpdatesEnabled(enabled: Boolean) = appPrefs.setBetaUpdatesEnabled(enabled)
    suspend fun setDebugUpdateChecksEnabled(enabled: Boolean) = appPrefs.setDebugUpdateChecksEnabled(enabled)
    suspend fun setCloseEmulatorOnSessionEnd(enabled: Boolean) = appPrefs.setCloseEmulatorOnSessionEnd(enabled)
    suspend fun setHiddenApps(apps: Set<String>) = appPrefs.setHiddenApps(apps)
    suspend fun toggleDockPin(packageName: String) = appPrefs.toggleDockPin(packageName)
    suspend fun reorderDockPins(expected: List<String>, order: List<String>) =
        appPrefs.reorderDockPins(expected, order)
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
    suspend fun setAppDisplayTarget(packageName: String, screenKey: String?) =
        appPrefs.setAppDisplayTarget(packageName, screenKey)
    suspend fun setAppLanguage(tag: String) = appPrefs.setAppLanguage(tag)
    suspend fun setPreLaunchRecapsEnabled(enabled: Boolean) = appPrefs.setPreLaunchRecapsEnabled(enabled)
    suspend fun setMonthlyRecapEnabled(enabled: Boolean) = appPrefs.setMonthlyRecapEnabled(enabled)
    suspend fun setLastMonthlyRecapShown(month: java.time.YearMonth) = appPrefs.setLastMonthlyRecapShown(month)
    suspend fun setNotificationsEnabled(enabled: Boolean) = appPrefs.setNotificationsEnabled(enabled)
    suspend fun setNotifyDownloadsFinished(enabled: Boolean) = appPrefs.setNotifyDownloadsFinished(enabled)
    suspend fun setNotifyBattery(enabled: Boolean) = appPrefs.setNotifyBattery(enabled)
    suspend fun setNotifyCharging(enabled: Boolean) = appPrefs.setNotifyCharging(enabled)
    suspend fun setNotifyControllers(enabled: Boolean) = appPrefs.setNotifyControllers(enabled)

    suspend fun setPreLaunchRecapBaseline(at: java.time.Instant) = appPrefs.setPreLaunchRecapBaseline(at)

    // --- Builtin emulator delegates ---

    suspend fun setBuiltinShader(shader: String) = builtinPrefs.setBuiltinShader(shader)
    suspend fun setBuiltinShaderChain(chainJson: String) = builtinPrefs.setBuiltinShaderChain(chainJson)
    suspend fun setBuiltinFilter(filter: String) = builtinPrefs.setBuiltinFilter(filter)
    suspend fun setBuiltinLibretroEnabled(enabled: Boolean) = builtinPrefs.setBuiltinLibretroEnabled(enabled)
    suspend fun setBuiltinAspectRatio(aspectRatio: String) = builtinPrefs.setBuiltinAspectRatio(aspectRatio)
    suspend fun setBuiltinPortraitPosition(value: String) = builtinPrefs.setBuiltinPortraitPosition(value)
    suspend fun setBuiltinSkipDuplicateFrames(enabled: Boolean) = builtinPrefs.setBuiltinSkipDuplicateFrames(enabled)
    suspend fun setBuiltinLowLatencyAudio(enabled: Boolean) = builtinPrefs.setBuiltinLowLatencyAudio(enabled)
    suspend fun setBuiltinForceSoftwareTiming(enabled: Boolean) = builtinPrefs.setBuiltinForceSoftwareTiming(enabled)
    suspend fun setBuiltinRumbleEnabled(enabled: Boolean) = builtinPrefs.setBuiltinRumbleEnabled(enabled)
    suspend fun setBuiltinMotionEnabled(enabled: Boolean) = builtinPrefs.setBuiltinMotionEnabled(enabled)
    suspend fun setBuiltinGyroAimInvertX(enabled: Boolean) = builtinPrefs.setBuiltinGyroAimInvertX(enabled)
    suspend fun setBuiltinGyroAimInvertY(enabled: Boolean) = builtinPrefs.setBuiltinGyroAimInvertY(enabled)
    suspend fun setBuiltinGyroAimSensitivity(percent: Int) = builtinPrefs.setBuiltinGyroAimSensitivity(percent)
    suspend fun setBuiltinBlackFrameInsertion(enabled: Boolean) = builtinPrefs.setBuiltinBlackFrameInsertion(enabled)
    suspend fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) = builtinPrefs.setBuiltinLimitHotkeysToPlayer1(enabled)
    suspend fun setBuiltinAnalogAsDpad(enabled: Boolean) = builtinPrefs.setBuiltinAnalogAsDpad(enabled)
    suspend fun setBuiltinDpadAsAnalog(enabled: Boolean) = builtinPrefs.setBuiltinDpadAsAnalog(enabled)
    suspend fun setBuiltinCoreForPlatform(platformSlug: String, coreId: String) = builtinPrefs.setBuiltinCoreForPlatform(platformSlug, coreId)
    suspend fun setBuiltinFastForwardEnabled(enabled: Boolean) = builtinPrefs.setBuiltinFastForwardEnabled(enabled)
    suspend fun setBuiltinFastForwardSpeed(speed: Int) = builtinPrefs.setBuiltinFastForwardSpeed(speed)
    suspend fun setBuiltinFastForwardMode(mode: com.nendo.argosy.data.local.entity.FastForwardMode) = builtinPrefs.setBuiltinFastForwardMode(mode)
    suspend fun setBuiltinSelectAsBack(mode: com.nendo.argosy.data.local.entity.SelectAsBackMode) = builtinPrefs.setBuiltinSelectAsBack(mode)
    suspend fun setBuiltinDualScreenMatchSize(enabled: Boolean) = builtinPrefs.setBuiltinDualScreenMatchSize(enabled)
    suspend fun setBuiltinFastForwardPreservePitch(enabled: Boolean) = builtinPrefs.setBuiltinFastForwardPreservePitch(enabled)
    suspend fun setBuiltinRotation(rotation: Int) = builtinPrefs.setBuiltinRotation(rotation)
    suspend fun setBuiltinOverscanCrop(crop: Int) = builtinPrefs.setBuiltinOverscanCrop(crop)
    suspend fun setBuiltinRewindEnabled(enabled: Boolean) = builtinPrefs.setBuiltinRewindEnabled(enabled)
    suspend fun setBuiltinRewindSpeed(speed: Int) = builtinPrefs.setBuiltinRewindSpeed(speed)
    suspend fun setBuiltinRewindBufferDuration(duration: Int) = builtinPrefs.setBuiltinRewindBufferDuration(duration)
    suspend fun setBuiltinFramesEnabled(enabled: Boolean) = builtinPrefs.setBuiltinFramesEnabled(enabled)
    suspend fun setBuiltinAutoSaveState(enabled: Boolean) = builtinPrefs.setBuiltinAutoSaveState(enabled)
    suspend fun setBuiltinAutoRestoreState(enabled: Boolean) = builtinPrefs.setBuiltinAutoRestoreState(enabled)
    suspend fun setBuiltinAutoRestoreStateMode(mode: String) = builtinPrefs.setBuiltinAutoRestoreStateMode(mode)
    suspend fun setBuiltinPreferNewerServerSave(enabled: Boolean) = builtinPrefs.setBuiltinPreferNewerServerSave(enabled)
    suspend fun setBuiltinCustomSavePath(path: String?) = builtinPrefs.setBuiltinCustomSavePath(path)
    suspend fun setBuiltinCustomStatePath(path: String?) = builtinPrefs.setBuiltinCustomStatePath(path)
    suspend fun setBuiltinMigrationComplete() = builtinPrefs.setBuiltinMigrationComplete()
    suspend fun setArchitectureOverride(abi: String?) = builtinPrefs.setArchitectureOverride(abi)
    suspend fun setTouchControlsShowWhenNoGamepad(enabled: Boolean) = builtinPrefs.setTouchControlsShowWhenNoGamepad(enabled)
    suspend fun setTouchControlsOpacityLandscape(opacity: Float) = builtinPrefs.setTouchControlsOpacityLandscape(opacity)
    suspend fun setTouchControlsOpacityPortrait(opacity: Float) = builtinPrefs.setTouchControlsOpacityPortrait(opacity)
    suspend fun setTouchControlsSizeScale(scale: Float) = builtinPrefs.setTouchControlsSizeScale(scale)
    suspend fun setTouchControlsHaptic(enabled: Boolean) = builtinPrefs.setTouchControlsHaptic(enabled)
    suspend fun setTouchControlsFadeOnIdle(enabled: Boolean) = builtinPrefs.setTouchControlsFadeOnIdle(enabled)
    suspend fun setTouchControlsSwapHanded(enabled: Boolean) = builtinPrefs.setTouchControlsSwapHanded(enabled)
    suspend fun setTouchControlsLockOrientation(enabled: Boolean) = builtinPrefs.setTouchControlsLockOrientation(enabled)
    suspend fun setTouchControlsMirror180(enabled: Boolean) = builtinPrefs.setTouchControlsMirror180(enabled)
    suspend fun setTouchControlsAllowLongPressEdit(enabled: Boolean) = builtinPrefs.setTouchControlsAllowLongPressEdit(enabled)
    suspend fun setTouchControlsColouredFaceButtons(enabled: Boolean) = builtinPrefs.setTouchControlsColouredFaceButtons(enabled)
    suspend fun setTouchControlsGenesis6Button(enabled: Boolean) = builtinPrefs.setTouchControlsGenesis6Button(enabled)

    suspend fun setHudEnabled(enabled: Boolean) = builtinPrefs.setHudEnabled(enabled)

    suspend fun setHudCorner(corner: String) = builtinPrefs.setHudCorner(corner)

    suspend fun setHudShowBattery(enabled: Boolean) = builtinPrefs.setHudShowBattery(enabled)

    suspend fun setHudShowClock(enabled: Boolean) = builtinPrefs.setHudShowClock(enabled)

    suspend fun setHudShowPlaytime(enabled: Boolean) = builtinPrefs.setHudShowPlaytime(enabled)

    suspend fun setHudShowFps(enabled: Boolean) = builtinPrefs.setHudShowFps(enabled)

    suspend fun setHudShowLastSave(enabled: Boolean) = builtinPrefs.setHudShowLastSave(enabled)
    fun getArchitectureOverride(): Flow<String?> = builtinPrefs.getArchitectureOverride()
    fun getBuiltinEmulatorSettings(): Flow<BuiltinEmulatorSettings> = builtinPrefs.getBuiltinEmulatorSettings()
    fun getBuiltinCoreSelections(): Flow<Map<String, String>> = builtinPrefs.getBuiltinCoreSelections()
    suspend fun removeBuiltinCoreSelectionsByCore(coreId: String) = builtinPrefs.removeBuiltinCoreSelectionsByCore(coreId)
    fun isBuiltinMigrationComplete(): Flow<Boolean> = builtinPrefs.isBuiltinMigrationComplete()
    fun isPs2StandalonePinDone(): Flow<Boolean> = builtinPrefs.isPs2StandalonePinDone()
    suspend fun setPs2StandalonePinDone() = builtinPrefs.setPs2StandalonePinDone()
    fun isBuiltinPriorityPinDone(): Flow<Boolean> = builtinPrefs.isBuiltinPriorityPinDone()
    suspend fun setBuiltinPriorityPinDone() = builtinPrefs.setBuiltinPriorityPinDone()
    fun isBuiltinMicPermissionAsked(): Flow<Boolean> = builtinPrefs.isBuiltinMicPermissionAsked()
    suspend fun setBuiltinMicPermissionAsked() = builtinPrefs.setBuiltinMicPermissionAsked()

    // --- Session delegates ---

    val activeSessionFlow: Flow<PersistedSession?> = sessionPrefs.activeSessionFlow

    suspend fun persistActiveSession(
        gameId: Long,
        emulatorPackage: String,
        startTime: Instant,
        coreName: String?,
        isHardcore: Boolean,
        channelName: String? = null,
        variantFileId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        isNetplayGuest: Boolean = false,
        streamClaim: com.nendo.argosy.data.streaming.StreamClaim? = null
    ) = sessionPrefs.persistActiveSession(
        gameId, emulatorPackage, startTime, coreName, isHardcore, channelName, variantFileId, origin, isNetplayGuest, streamClaim
    )

    suspend fun clearActiveSession(startedAt: Instant? = null) = sessionPrefs.clearActiveSession(startedAt)

    suspend fun getPersistedSession(): PersistedSession? = sessionPrefs.getPersistedSession()

    suspend fun keepSessionForRetry(maxAttempts: Int, startedAt: Instant? = null): Boolean =
        sessionPrefs.keepSessionForRetry(maxAttempts, startedAt)
}

/**
 * Gyro aim sensitivity as a percentage: 100 reaches the right stick's full range at the default
 * turn, 200 at half that turn.
 */
object GyroAimSensitivity {
    const val DEFAULT = 100
    const val MIN = 25
    const val MAX = 300
    const val STEP = 25

    fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)

    fun step(percent: Int, direction: Int): Int {
        val next = percent + direction * STEP
        return when {
            next > MAX -> MIN
            next < MIN -> MAX
            else -> next
        }
    }
}

data class BuiltinEmulatorSettings(
    val shader: String = "None",
    val shaderChainJson: String = "",
    val filter: String = "Auto",
    val aspectRatio: String = "Core Provided",
    val portraitPosition: String = "Auto",
    val skipDuplicateFrames: Boolean = false,
    val lowLatencyAudio: Boolean = true,
    val audioVolume: Int = 100,
    val forceSoftwareTiming: Boolean = false,
    val rumbleEnabled: Boolean = true,
    val motionEnabled: Boolean = true,
    val gyroAimInvertX: Boolean = false,
    val gyroAimInvertY: Boolean = false,
    val gyroAimSensitivity: Int = GyroAimSensitivity.DEFAULT,
    val blackFrameInsertion: Boolean = false,
    val framesEnabled: Boolean = false,
    val frame: String? = null,
    val limitHotkeysToPlayer1: Boolean = true,
    val confirmReturnHome: Boolean = true,
    val analogAsDpad: Boolean = false,
    val dpadAsAnalog: Boolean = false,
    val fastForwardEnabled: Boolean = true,
    val fastForwardSpeed: Int = 4,
    val fastForwardMode: com.nendo.argosy.data.local.entity.FastForwardMode = com.nendo.argosy.data.local.entity.FastForwardMode.HOLD,
    val selectAsBack: com.nendo.argosy.data.local.entity.SelectAsBackMode = com.nendo.argosy.data.local.entity.SelectAsBackMode.AUTO,
    val dualScreenMatchSize: Boolean = false,
    val fastForwardPreservePitch: Boolean = false,
    val rotation: Int = -1,
    val overscanCrop: Int = 0,
    val rewindEnabled: Boolean = true,
    val rewindSpeed: Int = 1,
    val rewindBufferDuration: Int = 15,
    val autoSaveState: Boolean = true,
    val autoRestoreState: Boolean = true,
    val autoRestoreStateMode: String = "restore",
    val preferNewerServerSave: Boolean = true,
    val hwCoreSaveStatesEnabled: Boolean = false,
    val defaultToHardcore: String = "ask",
    val customSavePath: String? = null,
    val customStatePath: String? = null,
    val architectureOverride: String? = null,
    val gpuDriver: String = com.nendo.argosy.data.emulator.GpuDriverToken.SYSTEM,
    val showTouchControlsWhenNoGamepad: Boolean = true,
    val touchControlsOpacityLandscape: Float = 0.45f,
    val touchControlsOpacityPortrait: Float = 1.0f,
    val touchControlsSizeScale: Float = 1.0f,
    val touchControlsHaptic: Boolean = true,
    val touchControlsFadeOnIdle: Boolean = false,
    val touchControlsSwapHanded: Boolean = false,
    val touchControlsLockOrientation: Boolean = false,
    val touchControlsMirror180: Boolean = false,
    val touchControlsAllowLongPressEdit: Boolean = false,
    val touchControlsColouredFaceButtons: Boolean = false,
    val touchControlsGenesis6Button: Boolean = false,
    val speedrunStartOnReset: Boolean = true,
    val speedrunPanelSide: String = "Right",
    val speedrunPanelWidthPercent: Int = 30,
    val ingameMenuTwoColumn: Boolean = false,
    val hudEnabled: Boolean = false,
    val hudCorner: String = "Top Left",
    val hudShowBattery: Boolean = true,
    val hudShowClock: Boolean = true,
    val hudShowPlaytime: Boolean = false,
    val hudShowFps: Boolean = false,
    val hudShowLastSave: Boolean = false
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

    val audioVolumeGain: Float
        get() = audioVolume / 100f

    val audioVolumeDisplay: String
        get() = "${audioVolume}%"

    val fastForwardSpeedDisplay: String
        get() = "${fastForwardSpeed}x"

    val rewindSpeedDisplay: String
        get() = "${rewindSpeed}x"

    val rewindBufferDurationDisplay: String
        get() = "${rewindBufferDuration}s"

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
    val rommBaseUrl: String? = null,
    val rommUsername: String? = null,
    val rommUserId: Long? = null,
    val rommAvatarPath: String? = null,
    val rommProfileRevision: Long = 0,
    val rommToken: String? = null,
    val rommDeviceId: String? = null,
    val rommDeviceClientVersion: String? = null,
    val raUsername: String? = null,
    val raToken: String? = null,
    val raWebApiKey: String? = null,
    val raProxyEnabled: Boolean = false,
    val raProxyAddress: String = "",
    val romStoragePath: String? = null,
    val showStatusClock: Boolean = true,
    val showStatusBattery: Boolean = true,
    val showStatusNetwork: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val tertiaryColor: Int? = null,
    val surfaceTintBleed: Int = 0,
    val backdropEnabled: Boolean = false,
    val backdropPreset: BackdropPreset = BackdropPreset.DOTS,
    val backdropCellSize: Int = ComponentDefaults.SurfaceBackdrop.cellSizeDefaultDp,
    val backdropScatter: Int = 200,
    val backdropScaleJitter: Int = 100,
    val backdropStrength: Int = 20,
    val backdropEdgeStyle: BackdropEdgeStyle = BackdropEdgeStyle.NONE,
    val backdropVertexIcons: BackdropVertexIcon = BackdropVertexIcon.NONE,
    val backdropSeed: Long = 0L,
    val backdropMotion: BackdropMotion = BackdropMotion.OFF,
    val backdropMotionSpeed: Int = 75,
    val backdropDriftAngle: Float = 45f,
    val displayFontPath: String? = null,
    val displayFontName: String? = null,
    val bodyFontPath: String? = null,
    val bodyFontName: String? = null,
    val displayFontScale: Int = 100,
    val bodyFontScale: Int = 100,
    val hapticEnabled: Boolean = true,
    val hapticStrength: Float = ControlsPreferences.DEFAULT_HAPTIC_STRENGTH,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val controllerLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val selectLCombo: String = "quick_menu",
    val selectRCombo: String = "quick_settings",
    val menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP,
    val selectSwapMode: SelectSwapMode = SelectSwapMode.HOLD,
    val openNavigationKey: Int = ControlsPreferences.DEFAULT_OPEN_NAVIGATION_KEY,
    val openQuickPanelKey: Int = ControlsPreferences.DEFAULT_OPEN_QUICK_PANEL_KEY,
    val quickPanelInEmulators: Boolean = false,
    val navRingRoutes: List<String>? = null,
    val quickNavigation: Boolean = true,
    val navBarAutoHide: Boolean = true,
    val ignoreControllerTouchpad: Boolean = true,
    val systemHotkeysEnabled: Boolean = true,
    val homeButtonInGames: Boolean = true,
    val systemHomeCombo: List<Int>? = null,
    val systemBackCombo: List<Int>? = null,
    val lastRommSync: Instant? = null,
    val rommChangesWatermark: Instant? = null,
    val lastFavoritesSync: Instant? = null,
    val lastFavoritesCheck: Instant? = null,
    val syncFilters: SyncFilterPreferences = SyncFilterPreferences(),
    val regionPriority: List<String> = SyncFilterPreferences.ALL_KNOWN_REGIONS,
    val syncScreenshotsEnabled: Boolean = false,
    val uploadScreenshotsEnabled: Boolean = true,
    val boxArtCacheEnabled: Boolean = true,
    val hiddenApps: Set<String> = emptySet(),
    val secondaryHomeApps: List<String> = emptyList(),
    val visibleSystemApps: Set<String> = emptySet(),
    val appOrder: List<String> = emptyList(),
    val maxConcurrentDownloads: Int = 1,
    val allowRemoteInstalls: Boolean = true,
    val autoStartDownloads: Boolean = true,
    val instantDownloadThresholdMb: Int = 50,
    val stageDownloadsInternally: Boolean = true,
    val folderNameFromRom: Boolean = false,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val libraryListStyle: LibraryListStyle = LibraryListStyle.RELAXED,
    val libraryBoxArt3d: Boolean = false,
    val libraryDefaultSort: String = "TITLE",
    val libraryDefaultSortDescending: Boolean? = null,
    val sortInstalledFirst: Boolean = false,
    val sortFavoritesFirst: Boolean = false,
    val libraryDefaultSource: String = "ALL",
    val libraryDefaultPlatformId: Long? = null,
    val libraryDefaultRegions: Set<String> = emptySet(),
    val libraryDefaultPlayers: com.nendo.argosy.domain.model.PlayerCountBucket? = null,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val betaUpdatesEnabled: Boolean = false,
    val debugUpdateChecksEnabled: Boolean = false,
    val closeEmulatorOnSessionEnd: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val preLaunchRecapsEnabled: Boolean = true,
    val monthlyRecapEnabled: Boolean = true,
    val lastMonthlyRecapShown: java.time.YearMonth? = null,
    val preLaunchRecapBaseline: java.time.Instant? = null,
    val notificationsEnabled: Boolean = true,
    val notifyDownloadsFinished: Boolean = true,
    val notifyBattery: Boolean = true,
    val notifyCharging: Boolean = true,
    val notifyControllers: Boolean = true,
    val saveSyncEnabled: Boolean = false,
    val notOnDeviceAction: NotOnDeviceAction = NotOnDeviceAction.DOWNLOAD,
    val secureSaves: Boolean = true,
    val stateCacheEnabled: Boolean = true,
    val saveCacheLimit: Int = 10,
    val backgroundBlur: Int = 40,
    val backgroundSaturation: Int = 100,
    val backgroundOpacity: Int = 100,
    val useGameBackground: Boolean = true,
    val customBackgroundPath: String? = null,
    val homeBackgroundMode: HomeBackgroundMode = HomeBackgroundMode.GAME_ART,
    val homeLayout: com.nendo.argosy.domain.model.HomeLayoutSettings =
        com.nendo.argosy.domain.model.HomeLayoutSettings(),
    val presentationStyle: com.nendo.argosy.domain.model.PresentationStyle =
        com.nendo.argosy.domain.model.PresentationStyle(),
    val presentationBackgroundBlur: Int = 0,
    val useAccentColorFooter: Boolean = false,
    val compactFooter: Boolean = false,
    val hideSystemNavigationBar: Boolean = true,
    val hideSystemStatusBar: Boolean = true,
    val screenOrientation: ScreenOrientationMode = ScreenOrientationMode.AUTO,
    val lockScreenArt: Boolean = true,
    val homeAppAvailable: Boolean = true,
    val iconPack: String? = null,
    val fileLoggingEnabled: Boolean = false,
    val fileLoggingPath: String? = null,
    val fileLogLevel: LogLevel = LogLevel.INFO,
    val saveDebugLoggingEnabled: Boolean = false,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD,
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
    val recommendedGameIds: List<Long> = emptyList(),
    val lastRecommendationGeneration: Instant? = null,
    val recommendationPenalties: Map<Long, Float> = emptyMap(),
    val lastPenaltyDecayWeek: String? = null,
    val lastSeenVersion: String? = null,
    val libraryRecentSearches: List<String> = emptyList(),
    val ambientAudioEnabled: Boolean = false,
    val ambientAudioVolume: Int = 50,
    val ambientAudioUri: String? = null,
    val ambientAudioShuffle: Boolean = false,
    val ambientAudioPlayInBackground: Boolean = false,
    val gameDetailThemeEnabled: Boolean = false,
    val imageCachePath: String? = null,
    val screenDimmerEnabled: Boolean = true,
    val screenDimmerTimeoutMinutes: Int = 2,
    val screenDimmerLevel: Int = 50,
    val customBiosPath: String? = null,
    val videoWallpaperEnabled: Boolean = false,
    val videoWallpaperDelaySeconds: Int = 3,
    val videoWallpaperMuted: Boolean = false,
    val uiScale: Int = 100,
    val gripReserveMode: GripReserveMode = GripReserveMode.OFF,
    val gripReservePercent: Int = 35,
    val gripAutoControllers: com.nendo.argosy.domain.model.GripAutoControllers =
        com.nendo.argosy.domain.model.GripAutoControllers(),
    val ambientLedEnabled: Boolean = false,
    val ambientLedBrightness: Int = 100,
    val ambientLedAudioBrightness: Boolean = true,
    val ambientLedAudioColors: Boolean = false,
    val ambientLedColorMode: AmbientLedColorMode = AmbientLedColorMode.DOMINANT_3,
    val ambientLedCoverArtEnabled: Boolean = true,
    val ambientLedCustomColor: Boolean = false,
    val ambientLedCustomColorHue: Int = 200,
    val ambientLedScreenEnabled: Boolean = false,
    val ambientLedTransitionMs: Int = 250,
    val ambientLedAchievementFlash: Boolean = true,
    val builtinLibretroEnabled: Boolean = true,
    val appDisplayTargets: Map<String, String> = emptyMap(),
    val lidSleepEnabled: Boolean = true,
    val dualScreenEnabled: Boolean = false,
    val displayRoleOverride: DisplayRoleOverride = DisplayRoleOverride.AUTO,
    val gamesDisplayTarget: EmulatorDisplayTarget = EmulatorDisplayTarget.PRESENTATION,
    val appsDisplayTarget: EmulatorDisplayTarget = EmulatorDisplayTarget.DEFAULT,
    val dualScreenInputFocus: DualScreenInputFocus = DualScreenInputFocus.AUTO,
    val screenLayouts: ScreenLayouts = ScreenLayouts(),
    val installedOnlyHome: Boolean = false,
    val recentNewGamesLimit: Int = RecentNewGamesLimit.DEFAULT,
    val newBadgePlacement: NewBadgePlacement = NewBadgePlacement.RIGHT,
    val socialSessionToken: String? = null,
    val socialUserId: String? = null,
    val socialUsername: String? = null,
    val socialDisplayName: String? = null,
    val socialAvatarColor: String? = null,
    val socialAvatarDoodle: String? = null,
    val socialAvatarUseDoodle: Boolean = false,
    val socialOnlineStatusEnabled: Boolean = true,
    val socialShowNowPlaying: Boolean = true,
    val socialNotifyFriendOnline: Boolean = true,
    val socialNotifyFriendPlaying: Boolean = true,
    val socialSuppressNotificationsInGame: Boolean = false,
    val discordRichPresenceEnabled: Boolean = true,
    val lastPlaySessionSync: Instant? = null,
    val lastStateValidation: Instant? = null,
    val weeklyIntegrityCheckEnabled: Boolean = true,
    val lastIntegrityCheckTime: Long? = null,
    val quayPassEnabled: Boolean = false,
    val quayPassAvatarSyncPending: Boolean = false,
    val quayPassMessageSyncPending: Boolean = false,
    val quayPassGreeting: String? = null,
    val quayPassTicketBalance: Int = 0,
    val mediaStoragePath: String? = null,
    val jellyfinServerUrl: String? = null,
    val jellyfinDeviceId: String? = null,
    val jellyfinAccessToken: String? = null,
    val jellyfinUserId: String? = null,
    val jellyfinUserName: String? = null,
    val mediaDownloadQuality: MediaDownloadQuality = MediaDownloadQuality.ORIGINAL,
    val mediaStreamingQuality: MediaStreamingQuality = MediaStreamingQuality.AUTO,
    val mediaAudioLanguage: MediaAudioLanguage = MediaAudioLanguage.ENGLISH,
    val mediaSubtitleMode: MediaSubtitleMode = MediaSubtitleMode.PREFERRED,
    val mediaSubtitleLanguage: MediaSubtitleLanguage = MediaSubtitleLanguage.ENGLISH,
    val mediaBurnInImageSubtitles: Boolean = false,
    val mediaConfirmPlayerExit: Boolean = false,
    val shareMediaPresence: Boolean = true
) {
    val isSocialLinked: Boolean get() = socialSessionToken != null

    val isJellyfinSignedIn: Boolean get() = !jellyfinAccessToken.isNullOrBlank()
}

enum class GripReserveMode(val displayName: String) {
    OFF("Off"),
    ON("On"),
    AUTO("Auto");

    companion object {
        fun fromString(value: String?): GripReserveMode =
            entries.firstOrNull { it.name == value } ?: OFF
    }
}

enum class ThemeMode(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromString(value: String?): ThemeMode =
            entries.find { it.name == value } ?: SYSTEM
    }
}

enum class ScreenOrientationMode {
    AUTO, LANDSCAPE, PORTRAIT;

    companion object {
        fun fromString(value: String?): ScreenOrientationMode =
            entries.find { it.name == value } ?: AUTO
    }
}

enum class GridDensity {
    COMPACT, NORMAL, SPACIOUS;

    companion object {
        fun fromString(value: String?): GridDensity =
            entries.find { it.name == value } ?: NORMAL
    }
}

enum class LibraryLayout {
    GRID, LIST;

    companion object {
        fun fromString(value: String?): LibraryLayout =
            entries.find { it.name == value } ?: GRID
    }
}

enum class LibraryListStyle {
    COMPACT, RELAXED;

    companion object {
        fun fromString(value: String?): LibraryListStyle =
            entries.find { it.name == value } ?: RELAXED
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

enum class NewBadgePlacement {
    RIGHT, LEFT, HIDDEN;

    companion object {
        fun fromString(value: String?): NewBadgePlacement =
            entries.find { it.name == value } ?: RIGHT
    }
}

enum class SystemIconPosition {
    OFF, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    companion object {
        // Cycleable corner positions only. OFF is reserved as an internal sentinel
        // (BoxArtShapes carve-out gating) and is reached via PlatformIndicatorStyle.OFF.
        val CORNERS: List<SystemIconPosition> = listOf(TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT)

        fun fromString(value: String?): SystemIconPosition =
            entries.find { it.name == value } ?: TOP_LEFT
    }
}

enum class PlatformIndicatorStyle {
    OFF, TAB, SPINE;

    companion object {
        fun fromString(value: String?): PlatformIndicatorStyle =
            entries.find { it.name == value } ?: TAB
    }
}

enum class PlatformIndicatorContent {
    NAME, ICON, NAME_AND_ICON;

    companion object {
        fun fromString(value: String?): PlatformIndicatorContent =
            entries.find { it.name == value } ?: NAME
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
    SQUARE(1f, "1:1"),
    NATIVE(3f / 4f, "Native");

    /** Whether covers should use their real image proportions instead of [aspectRatio]. */
    val isNative: Boolean get() = this == NATIVE

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

    /**
     * The override to record once a screen layout puts the roles at [rolesSwapped]. AUTO stays
     * AUTO; an explicit choice follows the layout, so the two never name different arrangements.
     */
    fun followingLayout(rolesSwapped: Boolean): DisplayRoleOverride =
        if (this == AUTO) AUTO else matching(rolesSwapped)

    companion object {
        fun fromString(value: String?): DisplayRoleOverride =
            entries.find { it.name == value } ?: AUTO

        fun matching(rolesSwapped: Boolean): DisplayRoleOverride =
            if (rolesSwapped) SWAPPED else STANDARD
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

enum class EmulatorDisplayTarget {
    DEFAULT,
    PRESENTATION,
    PRIMARY,
    APP_SCREEN;

    companion object {
        fun fromString(value: String?): EmulatorDisplayTarget = when (value) {
            LEGACY_TOP -> DEFAULT
            LEGACY_HERO -> PRESENTATION
            LEGACY_BOTTOM, LEGACY_LIBRARY -> PRIMARY
            else -> entries.find { it.name == value } ?: DEFAULT
        }

        /**
         * The global Games target. Never [DEFAULT]: a missing or unknown token reads as
         * [PRESENTATION].
         */
        fun gamesDefaultFrom(value: String?): EmulatorDisplayTarget =
            fromString(value).takeUnless { it == DEFAULT } ?: PRESENTATION

        /**
         * The global Apps target. [DEFAULT] means the App Screen while the layout has one, else
         * the Presentation screen.
         */
        fun appsDefaultFrom(value: String?): EmulatorDisplayTarget = fromString(value)

        private const val LEGACY_TOP = "TOP"
        private const val LEGACY_BOTTOM = "BOTTOM"
        private const val LEGACY_HERO = "HERO"
        private const val LEGACY_LIBRARY = "LIBRARY"
    }
}
