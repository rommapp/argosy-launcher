package com.nendo.argosy.ui.screens.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LaunchConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.screens.settings.delegates.StorageSettingsDelegate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun routeObserveDelegateStates(vm: SettingsViewModel) {
    vm.displayDelegate.state.onEach { display ->
        vm._uiState.update { current ->
            val newConfig = if (display.gradientPreset != GradientPreset.CUSTOM &&
                current.display.gradientPreset != display.gradientPreset) {
                display.gradientPreset.toConfig()
            } else {
                current.gradientConfig
            }
            current.copy(
                display = display,
                colorFocusIndex = vm.displayDelegate.colorFocusIndex,
                gradientConfig = newConfig
            )
        }
    }.launchIn(vm.viewModelScope)

    vm.displayDelegate.previewGame.onEach { previewGame ->
        vm._uiState.update { it.copy(previewGame = previewGame) }
    }.launchIn(vm.viewModelScope)

    vm.controlsDelegate.state.onEach { controls ->
        vm._uiState.update { it.copy(controls = controls) }
    }.launchIn(vm.viewModelScope)

    vm.soundsDelegate.state.onEach { sounds ->
        vm._uiState.update { it.copy(sounds = sounds) }
    }.launchIn(vm.viewModelScope)

    vm.ambientAudioDelegate.state.onEach { ambientAudio ->
        vm._uiState.update { it.copy(ambientAudio = ambientAudio) }
    }.launchIn(vm.viewModelScope)
    vm.ambientAudioDelegate.initFlowCollection(vm.viewModelScope)

    vm.emulatorDelegate.state.onEach { emulators ->
        vm._uiState.update { it.copy(emulators = emulators) }
    }.launchIn(vm.viewModelScope)

    vm.emulatorDelegate.observeCoreUpdateCount().onEach { count ->
        vm.emulatorDelegate.updateCoreUpdatesAvailable(count)
    }.launchIn(vm.viewModelScope)

    vm.emulatorDelegate.observeEmulatorUpdateCount().onEach { count ->
        vm.emulatorDelegate.updateEmulatorUpdatesAvailable(count)
    }.launchIn(vm.viewModelScope)

    vm.emulatorDelegate.observePlatformUpdateCounts().onEach { platformCounts ->
        vm.emulatorDelegate.updatePlatformUpdatesAvailable(platformCounts)
    }.launchIn(vm.viewModelScope)

    vm.emulatorDelegate.observeDownloadProgress().onEach { progress ->
        if (progress != null) {
            vm.emulatorDelegate.updatePickerDownloadState(progress.emulatorId, progress.state)
        } else {
            vm.emulatorDelegate.updatePickerDownloadState(null, EmulatorDownloadState.Idle)
        }
    }.launchIn(vm.viewModelScope)

    vm.serverDelegate.state.onEach { server ->
        vm._uiState.update { it.copy(server = server) }
    }.launchIn(vm.viewModelScope)

    vm.storageDelegate.state.onEach { storage ->
        vm._uiState.update { it.copy(storage = storage) }
    }.launchIn(vm.viewModelScope)

    vm.storageDelegate.launchFolderPicker.onEach { launch ->
        vm._uiState.update { it.copy(launchFolderPicker = launch) }
    }.launchIn(vm.viewModelScope)

    vm.storageDelegate.showMigrationDialog.onEach { show ->
        vm._uiState.update { it.copy(showMigrationDialog = show) }
    }.launchIn(vm.viewModelScope)

    vm.storageDelegate.pendingStoragePath.onEach { path ->
        vm._uiState.update { it.copy(pendingStoragePath = path) }
    }.launchIn(vm.viewModelScope)

    vm.storageDelegate.isMigrating.onEach { migrating ->
        vm._uiState.update { it.copy(isMigrating = migrating) }
    }.launchIn(vm.viewModelScope)

    vm.syncDelegate.state.onEach { syncSettings ->
        vm._uiState.update { it.copy(syncSettings = syncSettings) }
    }.launchIn(vm.viewModelScope)

    vm.steamDelegate.state.onEach { steam ->
        vm._uiState.update { it.copy(steam = steam) }
    }.launchIn(vm.viewModelScope)

    vm.raDelegate.state.onEach { ra ->
        vm._uiState.update { it.copy(retroAchievements = ra) }
    }.launchIn(vm.viewModelScope)

    vm.androidGameScanner.progress.onEach { progress ->
        vm._uiState.update {
            it.copy(
                android = AndroidSettingsState(
                    isScanning = progress.isScanning,
                    scanProgressPercent = progress.progressPercent,
                    currentApp = progress.currentApp,
                    gamesFound = progress.gamesFound,
                    lastScanGamesAdded = it.android.lastScanGamesAdded
                )
            )
        }
    }.launchIn(vm.viewModelScope)

    vm.permissionsDelegate.state.onEach { permissions ->
        vm._uiState.update { it.copy(permissions = permissions) }
    }.launchIn(vm.viewModelScope)

    vm.biosDelegate.state.onEach { bios ->
        vm._uiState.update { it.copy(bios = bios) }
    }.launchIn(vm.viewModelScope)
}

internal fun routeObserveDelegateEvents(vm: SettingsViewModel) {
    merge(
        vm.syncDelegate.requestStoragePermissionEvent,
        vm.steamDelegate.requestStoragePermissionEvent,
        vm.storageDelegate.requestStoragePermissionEvent
    ).onEach {
        vm._requestStoragePermissionEvent.emit(Unit)
    }.launchIn(vm.viewModelScope)

    vm.syncDelegate.requestNotificationPermissionEvent.onEach {
        vm._requestNotificationPermissionEvent.emit(Unit)
    }.launchIn(vm.viewModelScope)

    vm.emulatorDelegate.openUrlEvent.onEach { url ->
        vm._openUrlEvent.emit(url)
    }.launchIn(vm.viewModelScope)

    vm.steamDelegate.openUrlEvent.onEach { url ->
        vm._openUrlEvent.emit(url)
    }.launchIn(vm.viewModelScope)

    vm.steamDelegate.downloadProgress.onEach { progress ->
        val steamState = vm.steamDelegate.state.value
        if (progress != null && steamState.downloadingLauncherId == progress.emulatorId) {
            val dlProgress = when (val state = progress.state) {
                is EmulatorDownloadState.Downloading -> state.progress
                is EmulatorDownloadState.WaitingForInstall -> null
                is EmulatorDownloadState.Installed -> null
                is EmulatorDownloadState.Failed -> null
                is EmulatorDownloadState.Idle -> null
            }
            val stillDownloading = progress.state is EmulatorDownloadState.Downloading ||
                progress.state is EmulatorDownloadState.WaitingForInstall
            vm.steamDelegate.updateState(
                steamState.copy(
                    downloadingLauncherId = if (stillDownloading) progress.emulatorId else null,
                    downloadProgress = dlProgress
                )
            )
            if (progress.state is EmulatorDownloadState.Installed) {
                vm.steamDelegate.loadSteamSettings(vm.context, vm.viewModelScope)
            }
        } else if (progress == null && steamState.downloadingLauncherId != null) {
            vm.steamDelegate.updateState(
                steamState.copy(downloadingLauncherId = null, downloadProgress = null)
            )
        }
    }.launchIn(vm.viewModelScope)
}

internal fun routeObserveConnectionState(vm: SettingsViewModel) {
    vm.romMRepository.connectionState.onEach { connectionState ->
        val status = when (connectionState) {
            is ConnectionState.Connected -> ConnectionStatus.ONLINE
            else -> {
                val prefs = vm.preferencesRepository.userPreferences.first()
                if (prefs.rommBaseUrl.isNullOrBlank()) ConnectionStatus.NOT_CONFIGURED
                else ConnectionStatus.OFFLINE
            }
        }
        val version = (connectionState as? ConnectionState.Connected)?.version
        vm.serverDelegate.updateState(vm._uiState.value.server.copy(
            connectionStatus = status,
            rommVersion = version
        ))
    }.launchIn(vm.viewModelScope)
}

internal fun routeObservePlatformLibretroSettings(vm: SettingsViewModel) {
    vm.platformLibretroSettingsDao.observeAll().onEach { settingsList ->
        val settingsMap = settingsList.associateBy { it.platformId }
        vm._uiState.update { current ->
            current.copy(
                platformLibretro = current.platformLibretro.copy(
                    platformSettings = settingsMap
                )
            )
        }
    }.launchIn(vm.viewModelScope)
}

internal fun routeLoadAvailablePlatformsForLibretro(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val platforms = vm.platformRepository.getAllPlatformsOrdered()
            .filter { it.syncEnabled && LibretroCoreRegistry.isPlatformSupported(it.slug) }
            .map { PlatformContext(it.id, it.name, it.slug) }
        vm._uiState.update {
            it.copy(builtinVideo = it.builtinVideo.copy(availablePlatforms = platforms))
        }
    }
}

internal fun routeStartControllerDetectionPolling(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        while (true) {
            delay(1000)
            vm.controlsDelegate.refreshDetectedLayout()
        }
    }
}

internal fun routeObserveModalResetSignal(vm: SettingsViewModel) {
    vm.modalResetSignal.signal.onEach {
        vm.emulatorDelegate.dismissEmulatorPicker()
        vm.emulatorDelegate.dismissSavePathModal()
        vm.storageDelegate.closePlatformSettingsModal()
        vm.soundsDelegate.dismissSoundPicker()
        vm.syncDelegate.dismissRegionPicker()
        vm.steamDelegate.dismissAddSteamGameDialog()
    }.launchIn(vm.viewModelScope)
}

internal fun routeLoadSettings(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val prefs = vm.preferencesRepository.preferences.first()
        val installedEmulators = vm.emulatorDetector.detectEmulators()
        val platforms = vm.platformRepository.observePlatformsWithGames().first()

        val installedPackages = installedEmulators.map { it.def.packageName }.toSet()

        val platformConfigs = platforms
            .map { platform ->
            val defaultConfig = vm.emulatorConfigDao.getDefaultForPlatform(platform.id)
            val available = installedEmulators.filter { platform.slug in it.def.supportedPlatforms }
            val isUserConfigured = defaultConfig != null

            val recommended = EmulatorRegistry.getRecommendedEmulators()[platform.slug] ?: emptyList()
            val downloadable = recommended
                .mapNotNull { EmulatorRegistry.getById(it) }
                .filter { it.packageName !in installedPackages && it.downloadUrl != null }

            val rawSelectedEmulatorDef = defaultConfig?.packageName?.let { vm.emulatorDetector.getByPackage(it) }
            val selectedEmulatorDef = if (!prefs.builtinLibretroEnabled && rawSelectedEmulatorDef?.id == "builtin") {
                null
            } else {
                rawSelectedEmulatorDef
            }
            val autoResolvedEmulator = vm.emulatorDetector.getPreferredEmulator(platform.slug, prefs.builtinLibretroEnabled)?.def
            val effectiveEmulatorDef = selectedEmulatorDef ?: autoResolvedEmulator
            val isRetroArch = effectiveEmulatorDef?.launchConfig is LaunchConfig.RetroArch
            val availableCores = if (isRetroArch) {
                EmulatorRegistry.getCoresForPlatform(platform.slug)
            } else {
                emptyList()
            }

            val storedCore = defaultConfig?.coreName
            val defaultCore = EmulatorRegistry.getDefaultCore(platform.slug)?.id
            val selectedCore = when {
                !isRetroArch -> null
                storedCore != null && availableCores.any { it.id == storedCore } -> storedCore
                else -> defaultCore ?: availableCores.firstOrNull()?.id
            }

            val emulatorId = effectiveEmulatorDef?.id
            val emulatorPackage = effectiveEmulatorDef?.packageName
            val savePathConfig = emulatorPackage?.let { SavePathRegistry.getConfigByPackage(it) }
                ?: emulatorId?.let { SavePathRegistry.getConfig(it) }
            val showSavePath = savePathConfig != null
            val effectiveSaveConfigId = savePathConfig?.emulatorId

            val computedSavePath = when {
                savePathConfig == null -> null
                isRetroArch -> {
                    vm.retroArchConfigParser.resolveSavePaths(
                        packageName = emulatorPackage ?: "com.retroarch",
                        systemName = platform.slug,
                        coreName = selectedCore
                    ).firstOrNull()
                }
                else -> savePathConfig.defaultPaths.firstOrNull()
            }

            val userSaveConfig = effectiveSaveConfigId?.let { vm.emulatorDelegate.getEmulatorSaveConfig(it) }
            val isUserSavePathOverride = userSaveConfig?.isUserOverride == true
            val effectiveSavePath = when {
                !isUserSavePathOverride -> computedSavePath
                isRetroArch && effectiveEmulatorDef != null -> {
                    vm.retroArchConfigParser.resolveSavePaths(
                        packageName = effectiveEmulatorDef.packageName,
                        systemName = platform.slug,
                        coreName = selectedCore,
                        basePathOverride = userSaveConfig?.savePathPattern
                    ).firstOrNull()
                }
                else -> userSaveConfig?.savePathPattern
            }

            val extensionOptions = EmulatorRegistry.getExtensionOptionsForPlatform(platform.slug)
            val selectedExtension = vm.emulatorDelegate.getPreferredExtension(platform.id)

            PlatformEmulatorConfig(
                platform = platform,
                selectedEmulator = defaultConfig?.displayName,
                selectedEmulatorPackage = defaultConfig?.packageName,
                selectedCore = selectedCore,
                isUserConfigured = isUserConfigured,
                availableEmulators = available,
                downloadableEmulators = downloadable,
                availableCores = availableCores,
                effectiveEmulatorIsRetroArch = isRetroArch,
                effectiveEmulatorId = emulatorId,
                effectiveEmulatorPackage = effectiveEmulatorDef?.packageName,
                effectiveEmulatorName = effectiveEmulatorDef?.displayName,
                effectiveSavePath = effectiveSavePath,
                isUserSavePathOverride = isUserSavePathOverride,
                showSavePath = showSavePath,
                extensionOptions = extensionOptions,
                selectedExtension = selectedExtension
            )
        }

        val canAutoAssign = platformConfigs.any { !it.isUserConfigured && it.availableEmulators.isNotEmpty() }

        val connectionState = vm.romMRepository.connectionState.value
        val connectionStatus = when {
            prefs.rommBaseUrl.isNullOrBlank() -> ConnectionStatus.NOT_CONFIGURED
            connectionState is ConnectionState.Connected -> ConnectionStatus.ONLINE
            else -> ConnectionStatus.OFFLINE
        }
        val rommVersion = (connectionState as? ConnectionState.Connected)?.version

        val downloadedSize = vm.gameRepository.getDownloadedGamesSize()
        val downloadedCount = vm.gameRepository.getDownloadedGamesCount()
        val availableSpace = vm.gameRepository.getAvailableStorageBytes()

        vm.displayDelegate.updateState(DisplayState(
            themeMode = prefs.themeMode,
            primaryColor = prefs.primaryColor,
            secondaryColor = prefs.secondaryColor,
            gridDensity = prefs.gridDensity,
            backgroundBlur = prefs.backgroundBlur,
            backgroundSaturation = prefs.backgroundSaturation,
            backgroundOpacity = prefs.backgroundOpacity,
            useGameBackground = prefs.useGameBackground,
            customBackgroundPath = prefs.customBackgroundPath,
            useAccentColorFooter = prefs.useAccentColorFooter,
            boxArtShape = prefs.boxArtShape,
            boxArtCornerRadius = prefs.boxArtCornerRadius,
            boxArtBorderThickness = prefs.boxArtBorderThickness,
            boxArtBorderStyle = prefs.boxArtBorderStyle,
            glassBorderTint = prefs.glassBorderTint,
            boxArtGlowStrength = prefs.boxArtGlowStrength,
            boxArtOuterEffect = prefs.boxArtOuterEffect,
            boxArtOuterEffectThickness = prefs.boxArtOuterEffectThickness,
            glowColorMode = prefs.glowColorMode,
            boxArtInnerEffect = prefs.boxArtInnerEffect,
            boxArtInnerEffectThickness = prefs.boxArtInnerEffectThickness,
            gradientPreset = prefs.gradientPreset,
            gradientAdvancedMode = prefs.gradientAdvancedMode,
            systemIconPosition = prefs.systemIconPosition,
            systemIconPadding = prefs.systemIconPadding,
            defaultView = prefs.defaultView,
            videoWallpaperEnabled = prefs.videoWallpaperEnabled,
            videoWallpaperDelaySeconds = prefs.videoWallpaperDelaySeconds,
            videoWallpaperMuted = prefs.videoWallpaperMuted,
            uiScale = prefs.uiScale,
            ambientLedEnabled = prefs.ambientLedEnabled,
            ambientLedBrightness = prefs.ambientLedBrightness,
            ambientLedAudioBrightness = prefs.ambientLedAudioBrightness,
            ambientLedAudioColors = prefs.ambientLedAudioColors,
            ambientLedColorMode = prefs.ambientLedColorMode,
            ambientLedAvailable = vm.displayDelegate.isAmbientLedAvailable(),
            hasScreenCapturePermission = vm.displayDelegate.hasScreenCapturePermission(),
            hasSecondaryDisplay = vm.displayAffinityHelper.hasSecondaryDisplay,
            displayRoleOverride = prefs.displayRoleOverride,
            dualScreenInputFocus = prefs.dualScreenInputFocus
        ))

        val detectionResult = ControllerDetector.detectFromActiveGamepad()
        val detectedLayoutName = when (detectionResult.layout) {
            DetectedLayout.XBOX -> "Xbox"
            DetectedLayout.NINTENDO -> "Nintendo"
            null -> null
        }
        vm.controlsDelegate.updateState(ControlsState(
            hapticEnabled = prefs.hapticEnabled,
            vibrationStrength = vm.controlsDelegate.getVibrationStrength(),
            vibrationSupported = vm.controlsDelegate.supportsSystemVibration,
            controllerLayout = prefs.controllerLayout,
            detectedLayout = detectedLayoutName,
            detectedDeviceName = detectionResult.deviceName,
            swapAB = prefs.swapAB,
            swapXY = prefs.swapXY,
            swapStartSelect = prefs.swapStartSelect,
            accuratePlayTimeEnabled = prefs.accuratePlayTimeEnabled,
            hasSecondaryDisplay = vm.displayAffinityHelper.hasSecondaryDisplay,
            dualScreenInputFocus = prefs.dualScreenInputFocus
        ))
        vm.controlsDelegate.refreshUsageStatsPermission()

        vm.soundsDelegate.updateState(SoundState(
            enabled = prefs.soundEnabled,
            volume = prefs.soundVolume,
            soundConfigs = prefs.soundConfigs
        ))

        val ambientUri = prefs.ambientAudioUri
        val isAmbientFolder = ambientUri?.let { uri ->
            uri.startsWith("/") && java.io.File(uri).isDirectory
        } ?: false
        val ambientFileName = ambientUri?.let { uri ->
            if (uri.startsWith("/")) {
                uri.substringAfterLast("/")
            } else {
                try {
                    android.net.Uri.parse(uri).let { parsedUri ->
                        vm.context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) cursor.getString(nameIndex) else null
                            } else null
                        }
                    }
                } catch (e: Exception) {
                    uri.substringAfterLast("/").substringBefore("?")
                }
            }
        }
        vm.ambientAudioDelegate.updateState(AmbientAudioState(
            enabled = prefs.ambientAudioEnabled,
            volume = prefs.ambientAudioVolume,
            audioUri = ambientUri,
            audioFileName = ambientFileName,
            isFolder = isAmbientFolder,
            shuffle = prefs.ambientAudioShuffle
        ))

        val currentEmulatorState = vm.emulatorDelegate.state.value
        vm.emulatorDelegate.updateState(EmulatorState(
            platforms = platformConfigs,
            installedEmulators = installedEmulators,
            canAutoAssign = canAutoAssign,
            platformSubFocusIndex = currentEmulatorState.platformSubFocusIndex,
            builtinLibretroEnabled = prefs.builtinLibretroEnabled
        ))
        vm.emulatorDelegate.updateCoreCounts()

        vm.serverDelegate.updateState(ServerState(
            connectionStatus = connectionStatus,
            rommUrl = prefs.rommBaseUrl ?: "",
            rommUsername = prefs.rommUsername ?: "",
            rommVersion = rommVersion,
            lastRommSync = prefs.lastRommSync,
            syncScreenshotsEnabled = prefs.syncScreenshotsEnabled
        ))

        vm.storageDelegate.updateState(StorageState(
            romStoragePath = prefs.romStoragePath ?: "",
            downloadedGamesSize = downloadedSize,
            downloadedGamesCount = downloadedCount,
            maxConcurrentDownloads = prefs.maxConcurrentDownloads,
            instantDownloadThresholdMb = prefs.instantDownloadThresholdMb,
            availableSpace = availableSpace,
            screenDimmerEnabled = prefs.screenDimmerEnabled,
            screenDimmerTimeoutMinutes = prefs.screenDimmerTimeoutMinutes,
            screenDimmerLevel = prefs.screenDimmerLevel
        ))
        vm.storageDelegate.checkAllFilesAccess()
        val platformEmulatorInfoMap = platformConfigs.associate { config ->
            val statePath = if (config.effectiveEmulatorIsRetroArch) {
                config.effectiveEmulatorPackage?.let { pkg ->
                    vm.retroArchConfigParser.resolveStatePaths(
                        packageName = pkg,
                        coreName = config.selectedCore
                    ).firstOrNull()
                }
            } else null

            config.platform.id to StorageSettingsDelegate.PlatformEmulatorInfo(
                supportsStatePath = config.effectiveEmulatorIsRetroArch,
                emulatorId = config.effectiveEmulatorId,
                effectiveSavePath = config.effectiveSavePath,
                isUserSavePathOverride = config.isUserSavePathOverride,
                effectiveStatePath = statePath,
                isUserStatePathOverride = false
            )
        }
        vm.storageDelegate.setPendingEmulatorInfo(platformEmulatorInfoMap)
        vm.storageDelegate.loadPlatformConfigs(vm.viewModelScope)

        vm.syncDelegate.updateState(SyncSettingsState(
            syncFilters = prefs.syncFilters,
            totalPlatforms = platforms.count { it.gameCount > 0 },
            totalGames = platforms.sumOf { it.gameCount },
            saveSyncEnabled = prefs.saveSyncEnabled,
            experimentalFolderSaveSync = prefs.experimentalFolderSaveSync,
            saveCacheLimit = prefs.saveCacheLimit,
            pendingUploadsCount = vm.saveCacheDao.countNeedingRemoteSync(),
            imageCachePath = prefs.imageCachePath,
            defaultImageCachePath = vm.imageCacheManager.getDefaultCachePath()
        ))

        val builtinSettings = vm.preferencesRepository.getBuiltinEmulatorSettings().first()
        val displayManager = vm.context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        val refreshRate = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
        vm._uiState.update {
            it.copy(
                betaUpdatesEnabled = prefs.betaUpdatesEnabled,
                fileLoggingEnabled = prefs.fileLoggingEnabled,
                fileLoggingPath = prefs.fileLoggingPath,
                fileLogLevel = prefs.fileLogLevel,
                saveDebugLoggingEnabled = prefs.saveDebugLoggingEnabled,
                appAffinityEnabled = prefs.appAffinityEnabled,
                builtinVideo = it.builtinVideo.copy(
                    shader = builtinSettings.shader,
                    shaderChainJson = builtinSettings.shaderChainJson,
                    filter = builtinSettings.filter,
                    aspectRatio = builtinSettings.aspectRatio,
                    skipDuplicateFrames = builtinSettings.skipDuplicateFrames,
                    blackFrameInsertion = builtinSettings.blackFrameInsertion,
                    displayRefreshRate = refreshRate,
                    fastForwardSpeed = builtinSettings.fastForwardSpeedDisplay,
                    rotation = builtinSettings.rotationDisplay,
                    overscanCrop = builtinSettings.overscanCropDisplay,
                    lowLatencyAudio = builtinSettings.lowLatencyAudio,
                    forceSoftwareTiming = builtinSettings.forceSoftwareTiming,
                    rewindEnabled = builtinSettings.rewindEnabled
                ),
                builtinControls = BuiltinControlsState(
                    rumbleEnabled = builtinSettings.rumbleEnabled,
                    limitHotkeysToPlayer1 = builtinSettings.limitHotkeysToPlayer1,
                    analogAsDpad = builtinSettings.analogAsDpad,
                    dpadAsAnalog = builtinSettings.dpadAsAnalog
                )
            )
        }

        vm.soundManager.setVolume(prefs.soundVolume)

        vm.permissionsDelegate.refreshPermissions()
        vm.biosDelegate.init(vm.viewModelScope)
    }
}
