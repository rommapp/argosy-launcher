package com.nendo.argosy.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.ui.common.GradientColorExtractor
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.emulator.BuiltinCoreResolver
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.repository.CoreOptionsRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.LibretroSettingsRepository
import com.nendo.argosy.data.repository.PlatformRepository
import android.net.Uri
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinConnectionManager
import com.nendo.argosy.data.remote.jellyfin.JellyfinSignInCallbacks
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.social.SocialAuthManager
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.discord.DiscordPresenceManager
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.domain.usecase.sync.SyncLibraryUseCase
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.ui.ModalResetSignal
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.settings.components.ScopedMapping
import com.nendo.argosy.ui.screens.settings.delegates.AccountsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.AmbientAudioSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.BiosSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.EmulatorSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.JellyfinPasswordSignInRequest
import com.nendo.argosy.ui.screens.settings.delegates.PermissionsSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.RASettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ServerSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SoundSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SteamSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.StorageAttributionDelegate
import com.nendo.argosy.ui.screens.settings.delegates.StorageSettingsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.util.PlatformFilterLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val preferencesRepository: UserPreferencesRepository,
    internal val hapticManager: HapticFeedbackManager,
    internal val platformRepository: PlatformRepository,
    internal val appPreferencesRepository:
        com.nendo.argosy.data.preferences.AppPreferencesRepository,
    internal val libretroSettingsRepo: LibretroSettingsRepository,
    internal val touchLayoutRepository: com.nendo.argosy.data.repository.TouchLayoutRepository,
    internal val launchArgsRepo: com.nendo.argosy.data.repository.LaunchArgsRepository,
    internal val installedAppResolver: com.nendo.argosy.data.platform.InstalledAppResolver,
    internal val emulatorConfigRepo: EmulatorConfigRepository,
    internal val emulatorDetector: EmulatorDetector,
    internal val builtinCoreResolver: BuiltinCoreResolver,
    internal val romMRepository: RomMRepository,
    internal val notificationManager: NotificationManager,
    internal val settingsBackupRepository:
        com.nendo.argosy.data.preferences.SettingsBackupRepository,
    internal val gameRepository: GameRepository,
    private val extContentOrganizer: com.nendo.argosy.data.download.ExtContentOrganizer,
    private val androidGameScanner: com.nendo.argosy.data.scanner.AndroidGameScanner,
    internal val imageCacheManager: ImageCacheManager,
    internal val syncLibraryUseCase: SyncLibraryUseCase,
    internal val platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue,
    internal val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    internal val updateRepository: UpdateRepository,
    internal val appInstaller: AppInstaller,
    internal val soundManager: SoundFeedbackManager,
    internal val saveCacheDao: SaveCacheDao,
    internal val retroArchConfigParser: RetroArchConfigParser,
    internal val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
    internal val savePathAuthority: com.nendo.argosy.data.emulator.savepath.SavePathAuthority,
    val displayDelegate: DisplaySettingsDelegate,
    val controlsDelegate: ControlsSettingsDelegate,
    val soundsDelegate: SoundSettingsDelegate,
    val ambientAudioDelegate: AmbientAudioSettingsDelegate,
    val emulatorDelegate: EmulatorSettingsDelegate,
    val serverDelegate: ServerSettingsDelegate,
    val accountsDelegate: AccountsSettingsDelegate,
    val storageDelegate: StorageSettingsDelegate,
    val attributionDelegate: StorageAttributionDelegate,
    val storagePlatformGamesDelegate: com.nendo.argosy.ui.screens.settings.delegates.StoragePlatformGamesDelegate,
    val storageCachesDelegate: com.nendo.argosy.ui.screens.settings.delegates.StorageCachesDelegate,
    val syncDelegate: SyncSettingsDelegate,
    val steamDelegate: SteamSettingsDelegate,
    val jellyfinDelegate: com.nendo.argosy.ui.screens.settings.delegates.JellyfinSettingsDelegate,
    val raDelegate: RASettingsDelegate,
    val permissionsDelegate: PermissionsSettingsDelegate,
    val biosDelegate: BiosSettingsDelegate,
    val driversDelegate: com.nendo.argosy.ui.screens.settings.delegates.DriversSettingsDelegate,
    internal val modalResetSignal: ModalResetSignal,
    internal val gradientColorExtractor: GradientColorExtractor,
    internal val coreManager: LibretroCoreManager,
    internal val inputConfigRepository: com.nendo.argosy.data.repository.InputConfigRepository,
    internal val frameRegistry: com.nendo.argosy.libretro.frame.FrameRegistry,
    internal val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    internal val socialRepository: SocialRepository,
    internal val discordPresenceManager: DiscordPresenceManager,
    internal val coreOptionsRepo: CoreOptionsRepository,
    private val playStatsRepo: com.nendo.argosy.data.repository.PlayStatsRepository,
    private val biosRepository: com.nendo.argosy.data.repository.BiosRepository,
    private val savePathValidator: com.nendo.argosy.data.emulator.SavePathValidator,
    internal val jellyfinConnectionManager: JellyfinConnectionManager
) : ViewModel() {

    private var jellyfinSignInJob: Job? = null

    internal val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    internal val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    internal val _downloadUpdateEvent = MutableSharedFlow<Unit>()
    val downloadUpdateEvent: SharedFlow<Unit> = _downloadUpdateEvent.asSharedFlow()

    internal val _requestStoragePermissionEvent = MutableSharedFlow<Unit>()
    val requestStoragePermissionEvent: SharedFlow<Unit> = _requestStoragePermissionEvent.asSharedFlow()

    internal val _requestNotificationPermissionEvent = MutableSharedFlow<Unit>()
    val requestNotificationPermissionEvent: SharedFlow<Unit> = _requestNotificationPermissionEvent.asSharedFlow()

    internal val _requestBlePermissionEvent = MutableSharedFlow<Unit>()
    val requestBlePermissionEvent: SharedFlow<Unit> = _requestBlePermissionEvent.asSharedFlow()

    internal val _requestScreenCapturePermissionEvent = MutableSharedFlow<Unit>()
    val requestScreenCapturePermissionEvent: SharedFlow<Unit> = _requestScreenCapturePermissionEvent.asSharedFlow()

    internal val _requestMediaPermissionEvent = MutableSharedFlow<Unit>()
    val requestMediaPermissionEvent: SharedFlow<Unit> = _requestMediaPermissionEvent.asSharedFlow()

    internal val _navigationEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 4)
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    data class NavigationEvent(val route: String)

    val imageCacheProgress: StateFlow<ImageCacheProgress> = imageCacheManager.progress

    val openBackgroundPickerEvent: SharedFlow<Unit> = displayDelegate.openBackgroundPickerEvent
    val openCustomSoundPickerEvent: SharedFlow<SoundType> = soundsDelegate.openCustomSoundPickerEvent
    val openBgmPlaylistManagerEvent: SharedFlow<Unit> = ambientAudioDelegate.openPlaylistManagerEvent
    val openBgmAddMusicBrowserEvent: SharedFlow<Unit> = ambientAudioDelegate.openAddMusicBrowserEvent
    val openMusicBrowserBgmEvent: SharedFlow<Unit> = ambientAudioDelegate.openMusicBrowserEvent
    val openMusicLocationPickerEvent: SharedFlow<Unit> = ambientAudioDelegate.openMusicLocationPickerEvent
    val openMediaLocationPickerEvent: SharedFlow<Unit> = jellyfinDelegate.openMediaLocationPickerEvent
    val jellyfinQuickConnectRequestEvent: SharedFlow<String> = jellyfinDelegate.quickConnectRequestEvent
    val jellyfinPasswordSignInRequestEvent: SharedFlow<JellyfinPasswordSignInRequest> =
        jellyfinDelegate.passwordSignInRequestEvent
    val openMusicBrowserSfxEvent: SharedFlow<SoundType> = soundsDelegate.openMusicBrowserSfxEvent
    val launchPlatformFolderPicker: SharedFlow<Long> = storageDelegate.launchPlatformFolderPicker
    val launchSavePathPicker: SharedFlow<Unit> = emulatorDelegate.launchSavePathPicker
    val builtinNavigationEvent = emulatorDelegate.builtinNavigationEvent

    private val _avatarEditorEvent = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val avatarEditorEvent: kotlinx.coroutines.flow.SharedFlow<Unit> = _avatarEditorEvent
    val launchPlatformSavePathPicker: SharedFlow<Long> = storageDelegate.launchSavePathPicker
    val resetPlatformSavePathEvent: SharedFlow<Long> = storageDelegate.resetSavePathEvent
    val launchPlatformStatePathPicker: SharedFlow<Long> = storageDelegate.launchStatePathPicker
    private val _launchBuiltinSavePathPicker = MutableSharedFlow<Unit>()
    val launchBuiltinSavePathPicker: SharedFlow<Unit> = _launchBuiltinSavePathPicker
    private val _launchBuiltinStatePathPicker = MutableSharedFlow<Unit>()
    val launchBuiltinStatePathPicker: SharedFlow<Unit> = _launchBuiltinStatePathPicker
    private val _launchPlatformBuiltinSavePathPicker = MutableSharedFlow<Long>()
    val launchPlatformBuiltinSavePathPicker: SharedFlow<Long> = _launchPlatformBuiltinSavePathPicker
    private val _launchPlatformBuiltinStatePathPicker = MutableSharedFlow<Long>()
    val launchPlatformBuiltinStatePathPicker: SharedFlow<Long> = _launchPlatformBuiltinStatePathPicker

    fun openBuiltinSavePathBrowser() { viewModelScope.launch { _launchBuiltinSavePathPicker.emit(Unit) } }
    fun openBuiltinStatePathBrowser() { viewModelScope.launch { _launchBuiltinStatePathPicker.emit(Unit) } }
    fun openPlatformBuiltinSavePathBrowser(platformId: Long) { viewModelScope.launch { _launchPlatformBuiltinSavePathPicker.emit(platformId) } }
    fun openPlatformBuiltinStatePathBrowser(platformId: Long) { viewModelScope.launch { _launchPlatformBuiltinStatePathPicker.emit(platformId) } }
    val resetPlatformStatePathEvent: SharedFlow<Long> = storageDelegate.resetStatePathEvent
    val openImageCachePickerEvent: SharedFlow<Unit> = syncDelegate.openImageCachePickerEvent
    val hardResetCompletedEvent: SharedFlow<Unit> = storageDelegate.hardResetCompletedEvent
    val launchBiosFolderPicker: SharedFlow<Unit> = biosDelegate.launchFolderPicker
    val launchGpuDriverFilePicker: SharedFlow<Unit> = biosDelegate.launchGpuDriverFilePicker

    internal val _openFontPickerEvent = MutableSharedFlow<FontSlot>()
    val openFontPickerEvent: SharedFlow<FontSlot> = _openFontPickerEvent.asSharedFlow()

    internal val _openLogFolderPickerEvent = MutableSharedFlow<Unit>()
    val openLogFolderPickerEvent: SharedFlow<Unit> = _openLogFolderPickerEvent.asSharedFlow()

    internal val _openSettingsBackupPickerEvent = MutableSharedFlow<Unit>()
    val openSettingsBackupPickerEvent: SharedFlow<Unit> =
        _openSettingsBackupPickerEvent.asSharedFlow()

    internal val _openCertificatePickerEvent = MutableSharedFlow<Unit>()
    val openCertificatePickerEvent: SharedFlow<Unit> =
        _openCertificatePickerEvent.asSharedFlow()

    internal val _openDeviceSettingsEvent = MutableSharedFlow<Unit>()
    val openDeviceSettingsEvent: SharedFlow<Unit> = _openDeviceSettingsEvent.asSharedFlow()

    init {
        routeObserveDelegateStates(this)
        routeObserveDelegateEvents(this)
        routeObserveModalResetSignal(this)
        routeObserveConnectionState(this)
        observeSocialConnectionState()
        observeAvatarPreferences()
        routeObservePlatformLibretroSettings(this)
        routeLoadAvailablePlatformsForLibretro(this)
        loadSettings()
        driversDelegate.loadGpuInfo()
        raDelegate.initialize(viewModelScope)
        displayDelegate.loadPreviewGame(viewModelScope)
        displayDelegate.observeScreenCapturePermission(viewModelScope)
        routeStartControllerDetectionPolling(this)

        // TODO: Remove after testing manage=true Android/data access
        storageDelegate.testManagedStorageAccess(viewModelScope)
    }

    fun cyclePlatformContext(direction: Int) = routeCyclePlatformContext(this, direction)

    internal fun loadSettings() = routeLoadSettings(this)

    fun refreshEmulators() {
        emulatorDelegate.refreshEmulators()
        loadSettings()
    }

    fun checkStoragePermission() = storageDelegate.checkAllFilesAccess()
    fun requestStoragePermission() = storageDelegate.requestAllFilesAccess(viewModelScope)

    fun reloadDrivers(force: Boolean = false) = driversDelegate.loadDrivers(viewModelScope, force)
    fun downloadDriverArtifact(artifact: com.nendo.argosy.ui.screens.settings.DriverArtifactUi) =
        driversDelegate.downloadArtifact(viewModelScope, artifact)
    fun openDriverPicker(index: Int) = driversDelegate.openPicker(index)
    fun dismissDriverPicker() = driversDelegate.dismissPicker()
    fun moveDriverPickerFocus(delta: Int) = driversDelegate.movePickerReleaseFocus(delta)
    fun downloadSelectedDriverRelease() = driversDelegate.downloadFocusedPickerRelease(viewModelScope)
    fun dismissDriverDownload() = driversDelegate.dismissActiveDownload()

    fun requestAccountSwitch(accountId: Long) = accountsDelegate.requestSwitch(accountId)
    fun requestAccountRemoval(accountId: Long) =
        accountsDelegate.requestRemoval(viewModelScope, accountId)
    fun cancelAccountRemoval() = accountsDelegate.cancelRemoval()
    fun confirmAccountRemoval(policy: com.nendo.argosy.data.sync.UnflushedQueuePolicy) =
        accountsDelegate.confirmRemoval(viewModelScope, policy)
    fun startAddAccount() = accountsDelegate.requestAddAccount()
    fun retryAddAccountPairing() = accountsDelegate.startPairing(viewModelScope)
    fun cancelAddAccount() = accountsDelegate.cancelPairing()
    fun confirmAccountExitPrompt() = accountsDelegate.confirmExitPrompt(viewModelScope)
    fun cancelAccountExitPrompt() = accountsDelegate.cancelExitPrompt()
    fun retryInterruptedAccountSwitch() = accountsDelegate.retryInterruptedSwitch(viewModelScope)
    fun dismissAccountNotice() = accountsDelegate.dismissNotice()

    fun setAccountRowAction(account: AccountUi, action: AccountRowAction) {
        val index = _uiState.value.accounts.actionsFor(account).indexOf(action)
        if (index >= 0) accountsDelegate.setRowActionIndex(index)
    }

    fun moveAccountRowAction(direction: Int): Boolean {
        val state = _uiState.value
        val item = com.nendo.argosy.ui.screens.settings.sections
            .accountsItemAtFocusIndex(state.focusedIndex, state.accounts)
        val account = (item as? com.nendo.argosy.ui.screens.settings.sections.AccountsItem.Account)
            ?.account ?: return false
        return accountsDelegate.moveRowActionFocus(
            direction,
            state.accounts.actionsFor(account).size
        )
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig) = routeShowEmulatorPicker(this, config)

    fun dismissEmulatorPicker() = emulatorDelegate.dismissEmulatorPicker()

    fun handleVariantPickerItemTap(index: Int) = routeHandleVariantPickerItemTap(this, index)

    fun moveVariantPickerFocus(delta: Int) = emulatorDelegate.moveVariantPickerFocus(delta)
    fun selectVariant() = emulatorDelegate.selectVariant()
    fun confirmVariantSelection() = emulatorDelegate.selectVariant()
    fun dismissVariantPicker() = emulatorDelegate.dismissVariantPicker()
    fun navigateToBuiltinVideo() = emulatorDelegate.navigateToBuiltinVideo(viewModelScope)
    fun navigateToBuiltinControls() = emulatorDelegate.navigateToBuiltinControls(viewModelScope)
    fun navigateToCoreManagement() = emulatorDelegate.navigateToCoreManagement(viewModelScope)
    fun navigateToCoreOptions() = emulatorDelegate.navigateToCoreOptions(viewModelScope)
    fun navigateToCoreOptionsForPlatform() {
        val s = _uiState.value
        val currentSlug = s.emulators.platforms.getOrNull(s.platformDetail.platformIndex)?.platform?.slug
        val targetIndex = s.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformSlug == currentSlug }
            .takeIf { it >= 0 } ?: s.coreOptions.platformContextIndex
        _uiState.update {
            it.copy(
                coreOptions = it.coreOptions.copy(
                    platformContextIndex = targetIndex,
                    selectedCoreIndex = 0
                )
            )
        }
        emulatorDelegate.navigateToCoreOptions(viewModelScope)
    }
    fun openPlatformDetailById(platformId: Long) {
        viewModelScope.launch {
            val platforms = kotlinx.coroutines.withTimeoutOrNull(5000) {
                uiState.map { it.emulators.platforms }.first { it.isNotEmpty() }
            } ?: return@launch
            val index = platforms.indexOfFirst { it.platform.id == platformId }
            if (index < 0) {
                startAtSection(SettingsSection.PLATFORMS)
                return@launch
            }
            _uiState.update {
                it.copy(platformDetail = it.platformDetail.copy(platformIndex = index))
            }
            startAtSection(SettingsSection.PLATFORM_DETAIL)
            loadPlatformDetailStats(index)
        }
    }

    fun navigateToPlatformDetail(platformIndex: Int) {
        _uiState.update {
            it.copy(platformDetail = it.platformDetail.copy(platformIndex = platformIndex))
        }
        routePushSection(this, SettingsSection.PLATFORM_DETAIL)
        loadPlatformDetailStats(platformIndex)
    }

    fun cyclePlatformDetail(direction: Int) {
        val platforms = _uiState.value.emulators.platforms
        if (platforms.isEmpty()) return
        val currentIndex = _uiState.value.platformDetail.platformIndex
        val newIndex = (currentIndex + direction).coerceIn(0, platforms.size - 1)
        if (newIndex == currentIndex) return
        _uiState.update { it.copy(
            focusedIndex = 0,
            platformDetail = it.platformDetail.copy(platformIndex = newIndex)
        ) }
        loadPlatformDetailStats(newIndex)
    }

    internal fun loadPlatformDetailStats(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        viewModelScope.launch {
            val platformId = config.platform.id
            val platformSlug = config.platform.slug
            val downloaded = gameRepository.countDownloadedByPlatform(platformId)
            val favorites = gameRepository.countFavoritesByPlatform(platformId)
            val totalPlayTimeMs = playStatsRepo.getTotalActivePlayMsByPlatform(platformSlug)
            val allBiosStatus = biosRepository.getStatusByPlatform()
            val biosStatus = allBiosStatus.find { it.platformSlug == platformSlug }
            val packagePathAccessible = if (config.effectiveEmulatorId == "builtin") {
                true
            } else {
                config.effectiveEmulatorPackage?.let { pkg ->
                    val emulatorId = config.effectiveEmulatorId ?: return@let null
                    savePathValidator.isPackageDataAccessible(emulatorId, pkg)
                }
            }

            val globalDefaults = preferencesRepository.getGlobalDownloadDefaults()
            val overrides = preferencesRepository.getDownloadPlatformOverrides(platformSlug)

            _uiState.update { it.copy(
                platformDetail = it.platformDetail.copy(
                    totalGames = config.platform.gameCount,
                    downloadedGames = downloaded,
                    favorites = favorites,
                    totalPlayTimeMs = totalPlayTimeMs,
                    packagePathAccessible = packagePathAccessible,
                    biosTotal = biosStatus?.totalFiles ?: 0,
                    biosDownloaded = biosStatus?.downloadedFiles ?: 0,
                    hasBiosRequirements = (biosStatus?.totalFiles ?: 0) > 0,
                    downloadOverrides = overrides,
                    globalDownloadDefaults = globalDefaults
                )
            ) }
        }
    }

    val librarySyncProgress get() = romMRepository.syncProgress

    /**
     * Moves a platform one place in the order every platform list follows. Marks the order as the
     * user's from the first successful move, so it is not overwritten on the next launch.
     */
    fun movePlatformOrder(platformId: Long, delta: Int) {
        viewModelScope.launch {
            if (!platformRepository.movePlatform(platformId, delta)) return@launch
            appPreferencesRepository.setPlatformOrderCustomised()
            val moved = uiState
                .map { state -> state.emulators.platforms.indexOfFirst { it.platform.id == platformId } }
                .first { it >= 0 }
            _uiState.update {
                it.copy(platformDetail = it.platformDetail.copy(platformIndex = moved))
            }
        }
    }

    fun clearPlatformArtCache(platformSlug: String) {
        viewModelScope.launch {
            val reclaimed = imageCacheManager.clearPlatformCache(platformSlug)
            notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_artwork_cache_title),
                subtitle = if (reclaimed > 0) {
                    NotificationText.Res(
                        R.string.settings_shell_vm_artwork_cleared_template,
                        listOf(com.nendo.argosy.util.formatBytes(reclaimed))
                    )
                } else {
                    NotificationText.Res(R.string.settings_shell_vm_artwork_nothing_cached)
                },
                type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }

    fun scanFilesForPlatform(platformId: Long) {
        val platformIndex = _uiState.value.platformDetail.platformIndex
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex)
        val platformName = config?.platform?.name ?: context.getString(R.string.settings_shell_vm_platform_fallback)
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = true)) }
        viewModelScope.launch {
            val invalidated = gameRepository.validateLocalFilesForPlatform(platformId)
            val discovered = gameRepository.discoverLocalFilesForPlatform(platformId)
            _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = false)) }
            loadPlatformDetailStats(platformIndex)

            val parts = mutableListOf<String>()
            if (discovered > 0) {
                parts.add(
                    context.resources.getQuantityString(
                        R.plurals.settings_shell_vm_scan_found_template,
                        discovered,
                        discovered
                    )
                )
            }
            if (invalidated > 0) {
                parts.add(
                    context.resources.getQuantityString(
                        R.plurals.settings_shell_vm_scan_removed_template,
                        invalidated,
                        invalidated
                    )
                )
            }
            if (parts.isEmpty()) parts.add(context.getString(R.string.settings_shell_vm_scan_no_changes))

            notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_scan_complete_title),
                subtitle = NotificationText.Res(
                    R.string.settings_shell_vm_scan_complete_subtitle_template,
                    listOf(platformName, parts.joinToString(", "))
                ),
                type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }

    fun scanInstalledAndroidGames() {
        val platformIndex = _uiState.value.platformDetail.platformIndex
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = true)) }
        viewModelScope.launch {
            val result = runCatching { androidGameScanner.scanInstalledGames() }
                .getOrDefault(com.nendo.argosy.data.scanner.AndroidScanResult())
            _uiState.update { it.copy(platformDetail = it.platformDetail.copy(isScanning = false)) }
            loadPlatformDetailStats(platformIndex)

            val parts = buildList {
                if (result.added > 0) {
                    add(
                        context.resources.getQuantityString(
                            R.plurals.settings_shell_vm_android_added_template,
                            result.added,
                            result.added
                        )
                    )
                }
                if (result.enriched > 0) {
                    add(context.getString(R.string.settings_shell_vm_android_updated_template, result.enriched))
                }
            }
            notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_scan_complete_title2),
                subtitle = if (parts.isNotEmpty()) {
                    NotificationText.Res(
                        R.string.settings_shell_vm_android_scan_result_template,
                        listOf(parts.joinToString(", "))
                    )
                } else {
                    NotificationText.Res(R.string.settings_shell_vm_android_scan_nothing_new)
                },
                type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }

    fun navigateToBuiltinVideoForPlatform(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        val ctxIndex = _uiState.value.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformId == config.platform.id }
        if (ctxIndex >= 0) {
            _uiState.update {
                it.copy(builtinVideo = it.builtinVideo.copy(platformContextIndex = ctxIndex + 1))
            }
        }
        emulatorDelegate.navigateToBuiltinVideo(viewModelScope)
    }

    fun navigateToBuiltinControlsForPlatform(platformIndex: Int) {
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        val ctxIndex = _uiState.value.builtinVideo.availablePlatforms
            .indexOfFirst { it.platformId == config.platform.id }
        if (ctxIndex >= 0) {
            _uiState.update {
                it.copy(builtinVideo = it.builtinVideo.copy(platformContextIndex = ctxIndex + 1))
            }
        }
        emulatorDelegate.navigateToBuiltinControls(viewModelScope)
    }

    fun getInstalledCoreIds(): Set<String> =
        coreManager.getInstalledCores().map { it.coreId }.toSet()

    fun downloadCore(coreId: String) = routeDownloadCore(this, coreId)
    fun downloadCoreWithNotification(coreId: String) = routeDownloadCoreWithNotification(this, coreId)
    fun deleteCore(coreId: String) = routeDeleteCore(this, coreId)

    fun requestDeleteCore(coreId: String) = _uiState.update {
        it.copy(coreOptions = it.coreOptions.copy(pendingDeleteCoreId = coreId))
    }

    fun cancelDeleteCore() = _uiState.update {
        it.copy(coreOptions = it.coreOptions.copy(pendingDeleteCoreId = null))
    }

    fun confirmDeleteCore() {
        val coreId = _uiState.value.coreOptions.pendingDeleteCoreId ?: return
        cancelDeleteCore()
        deleteCore(coreId)
    }

    fun cycleBuiltinArchitecture(direction: Int) = routeCycleBuiltinArchitecture(this, direction)
    fun setBuiltinShader(value: String) = routeSetBuiltinShader(this, value)
    fun setBuiltinFramesEnabled(enabled: Boolean) = routeSetBuiltinFramesEnabled(this, enabled)
    fun setBuiltinLibretroEnabled(enabled: Boolean) = routeSetBuiltinLibretroEnabled(this, enabled)
    fun setIngameMenuTwoColumn(enabled: Boolean) = routeSetIngameMenuTwoColumn(this, enabled)
    fun setHudEnabled(enabled: Boolean) = routeSetHudEnabled(this, enabled)
    fun cycleHudCorner(forward: Boolean) = routeCycleHudCorner(this, forward)
    fun setHudCorner(corner: String) = routeSetHudCorner(this, corner)
    fun setHudShowBattery(enabled: Boolean) = routeSetHudShowBattery(this, enabled)
    fun setHudShowClock(enabled: Boolean) = routeSetHudShowClock(this, enabled)
    fun setHudShowPlaytime(enabled: Boolean) = routeSetHudShowPlaytime(this, enabled)
    fun setHudShowFps(enabled: Boolean) = routeSetHudShowFps(this, enabled)
    fun setHudShowLastSave(enabled: Boolean) = routeSetHudShowLastSave(this, enabled)
    fun setBuiltinFilter(value: String) = routeSetBuiltinFilter(this, value)
    fun setBuiltinAspectRatio(value: String) = routeSetBuiltinAspectRatio(this, value)
    fun setBuiltinPortraitPosition(value: String) = routeSetBuiltinPortraitPosition(this, value)
    fun setBuiltinSkipDuplicateFrames(enabled: Boolean) = routeSetBuiltinSkipDuplicateFrames(this, enabled)
    fun setBuiltinLowLatencyAudio(enabled: Boolean) = routeSetBuiltinLowLatencyAudio(this, enabled)
    fun setBuiltinVSync(enabled: Boolean) = routeSetBuiltinVSync(this, enabled)
    fun setBuiltinFastForwardEnabled(enabled: Boolean) = routeSetBuiltinFastForwardEnabled(this, enabled)
    fun setBuiltinRewindEnabled(enabled: Boolean) = routeSetBuiltinRewindEnabled(this, enabled)
    fun setBuiltinAutoSaveState(enabled: Boolean) = routeSetBuiltinAutoSaveState(this, enabled)
    fun setBuiltinAutoRestoreState(enabled: Boolean) = routeSetBuiltinAutoRestoreState(this, enabled)
    fun setBuiltinHwCoreSaveStates(enabled: Boolean) = routeSetBuiltinHwCoreSaveStates(this, enabled)
    fun setBuiltinDefaultToHardcore(mode: String) = routeSetBuiltinDefaultToHardcore(this, mode)
    fun cycleRADefaultMode(direction: Int) {
        val options = listOf("ask", "casual", "hardcore")
        val current = _uiState.value.retroAchievements.defaultToHardcore
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        setBuiltinDefaultToHardcore(options[nextIndex])
    }
    fun setBuiltinSavePath(path: String) = routeSetBuiltinSavePath(this, path)
    fun resetBuiltinSavePath() = routeResetBuiltinSavePath(this)
    fun setBuiltinStatePath(path: String) = routeSetBuiltinStatePath(this, path)
    fun resetBuiltinStatePath() = routeResetBuiltinStatePath(this)
    fun setPlatformBuiltinSavePath(platformId: Long, path: String) = routeSetPlatformBuiltinSavePath(this, platformId, path)
    fun resetPlatformBuiltinSavePath(platformId: Long) = routeResetPlatformBuiltinSavePath(this, platformId)
    fun setPlatformBuiltinStatePath(platformId: Long, path: String) = routeSetPlatformBuiltinStatePath(this, platformId, path)
    fun resetPlatformBuiltinStatePath(platformId: Long) = routeResetPlatformBuiltinStatePath(this, platformId)

    fun openLaunchArgsModal(platformId: Long) = routeOpenLaunchArgsModal(this, platformId)
    fun closeLaunchArgsModal() = routeCloseLaunchArgsModal(this)
    fun moveLaunchArgsFocus(delta: Int) = routeMoveLaunchArgsFocus(this, delta)
    fun cycleLaunchArgsMethod() = routeCycleLaunchArgsMethod(this)
    fun cycleLaunchArgsDataBinding(direction: Int = 1) = routeCycleLaunchArgsDataBinding(this, direction)
    fun cycleLaunchArgsExtraBinding(direction: Int = 1) = routeCycleLaunchArgsExtraBinding(this, direction)
    fun cycleLaunchArgsClipDataBinding(direction: Int = 1) = routeCycleLaunchArgsClipDataBinding(this, direction)
    fun toggleLaunchArgsFlag(flagBit: Int) = routeToggleLaunchArgsFlag(this, flagBit)
    fun cycleLaunchArgsMimeType(direction: Int = 1) = routeCycleLaunchArgsMimeType(this, direction)
    fun resetLaunchArgsFocused() = routeResetLaunchArgsFocused(this)
    fun resetAllLaunchArgs() = routeResetAllLaunchArgs(this)
    fun openLaunchArgsCustomExtras() = routeOpenLaunchArgsCustomExtras(this)
    fun closeLaunchArgsCustomExtras() = routeCloseLaunchArgsCustomExtras(this)
    fun saveLaunchArgsCustomExtras(raw: String) = routeSaveLaunchArgsCustomExtras(this, raw)

    fun openAppPickerModal(platformId: Long) = routeOpenAppPickerModal(this, platformId)
    fun closeAppPickerModal() = routeCloseAppPickerModal(this)
    fun moveAppPickerFocus(delta: Int) = routeMoveAppPickerFocus(this, delta)
    fun confirmAppPickerSelection() = routeConfirmAppPickerSelection(this)
    fun setBuiltinRumbleEnabled(enabled: Boolean) = routeSetBuiltinRumbleEnabled(this, enabled)
    fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) = routeSetBuiltinLimitHotkeysToPlayer1(this, enabled)
    fun setSpeedrunStartOnReset(enabled: Boolean) = routeSetSpeedrunStartOnReset(this, enabled)
    fun setSpeedrunPanelSide(side: String) = routeSetSpeedrunPanelSide(this, side)
    fun adjustSpeedrunPanelWidth(delta: Int) = routeAdjustSpeedrunPanelWidth(this, delta)
    fun setBuiltinFastForwardMode(mode: com.nendo.argosy.data.local.entity.FastForwardMode) = routeSetBuiltinFastForwardMode(this, mode)
    fun setBuiltinFastForwardPreservePitch(enabled: Boolean) = routeSetBuiltinFastForwardPreservePitch(this, enabled)
    fun setBuiltinAnalogAsDpad(enabled: Boolean) = routeSetBuiltinAnalogAsDpad(this, enabled)
    fun setBuiltinDpadAsAnalog(enabled: Boolean) = routeSetBuiltinDpadAsAnalog(this, enabled)
    fun showControllerOrderModal() = routeShowControllerOrderModal(this)
    fun hideControllerOrderModal() = routeHideControllerOrderModal(this)
    fun assignControllerToPort(port: Int, device: android.view.InputDevice) = routeAssignControllerToPort(this, port, device)
    fun clearControllerOrder() = routeClearControllerOrder(this)
    fun getControllerOrder() = inputConfigRepository.observeControllerOrder()
    fun showInputMappingModal() = routeShowInputMappingModal(this)
    fun hideInputMappingModal() = routeHideInputMappingModal(this)
    fun setTouchEnabled(enabled: Boolean) = routeSetTouchEnabled(this, enabled)
    fun setTouchOpacityLandscape(value: Float) = routeSetTouchOpacityLandscape(this, value)
    fun setTouchOpacityPortrait(value: Float) = routeSetTouchOpacityPortrait(this, value)
    fun setTouchSizeScale(value: Float) = routeSetTouchSizeScale(this, value)
    fun setTouchHaptic(enabled: Boolean) = routeSetTouchHaptic(this, enabled)
    fun setTouchFadeOnIdle(enabled: Boolean) = routeSetTouchFadeOnIdle(this, enabled)
    fun setTouchSwapHanded(enabled: Boolean) = routeSetTouchSwapHanded(this, enabled)
    fun setTouchLockOrientation(enabled: Boolean) = routeSetTouchLockOrientation(this, enabled)
    fun setTouchMirror180(enabled: Boolean) = routeSetTouchMirror180(this, enabled)
    fun setTouchColouredFaceButtons(enabled: Boolean) = routeSetTouchColouredFaceButtons(this, enabled)
    fun setTouchGenesis6Button(enabled: Boolean) = routeSetTouchGenesis6Button(this, enabled)
    fun showTouchLayoutEditor() = routeShowTouchLayoutEditor(this)
    fun hideTouchLayoutEditor() = routeHideTouchLayoutEditor(this)
    fun getConnectedControllers() = inputConfigRepository.getConnectedControllers()

    suspend fun getControllerMapping(
        controller: com.nendo.argosy.data.repository.ControllerInfo,
        platformId: String? = null
    ): ScopedMapping {
        val device = android.view.InputDevice.getDevice(controller.deviceId)
            ?: return ScopedMapping()
        return ScopedMapping(
            mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(device, platformId),
            inherited = inputConfigRepository.getInheritedExtendedMappingForDevice(device, platformId)
        )
    }

    suspend fun saveControllerMapping(
        controller: com.nendo.argosy.data.repository.ControllerInfo,
        mapping: Map<com.nendo.argosy.data.repository.InputSource, Int>,
        presetName: String?,
        isAutoDetected: Boolean,
        platformId: String? = null
    ) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.saveExtendedMapping(device, mapping, presetName, isAutoDetected, platformId)
    }

    suspend fun applyControllerPreset(controller: com.nendo.argosy.data.repository.ControllerInfo, presetName: String) {
        val device = android.view.InputDevice.getDevice(controller.deviceId) ?: return
        inputConfigRepository.applyPreset(device, presetName)
    }

    fun showHotkeysModal() = routeShowHotkeysModal(this)
    fun hideHotkeysModal() = routeHideHotkeysModal(this)
    fun observeHotkeys() = inputConfigRepository.observeHotkeys()

    suspend fun saveHotkey(
        action: com.nendo.argosy.data.local.entity.HotkeyAction,
        keyCodes: List<Int>,
        scopeType: com.nendo.argosy.data.local.entity.HotkeyScopeType,
        scopeKey: String?
    ) {
        inputConfigRepository.setHotkey(action, keyCodes, scopeType = scopeType, scopeKey = scopeKey)
    }

    suspend fun clearHotkey(
        action: com.nendo.argosy.data.local.entity.HotkeyAction,
        scopeType: com.nendo.argosy.data.local.entity.HotkeyScopeType,
        scopeKey: String?
    ) {
        inputConfigRepository.clearScopedHotkey(action, scopeType, scopeKey)
    }

    suspend fun setHotkeyHoldMs(
        action: com.nendo.argosy.data.local.entity.HotkeyAction,
        holdMs: Long,
        scopeType: com.nendo.argosy.data.local.entity.HotkeyScopeType,
        scopeKey: String?
    ) {
        inputConfigRepository.setHotkeyHoldMs(action, holdMs, scopeType = scopeType, scopeKey = scopeKey)
    }

    suspend fun saveCoreControlHotkey(
        coreId: String,
        retropadId: Int,
        mode: com.nendo.argosy.data.local.entity.CoreInputMode,
        keyCodes: List<Int>
    ) {
        inputConfigRepository.setCoreControlHotkey(
            id = null,
            keyCodes = keyCodes,
            retropadId = retropadId,
            mode = mode,
            coreId = coreId
        )
    }

    suspend fun deleteCoreBind(id: Long) {
        inputConfigRepository.deleteHotkeyById(id)
    }

    fun setBuiltinBlackFrameInsertion(enabled: Boolean) = routeSetBuiltinBlackFrameInsertion(this, enabled)
    fun cycleBuiltinShader(direction: Int) = routeCycleBuiltinShader(this, direction)

    internal val _shaderRegistry by lazy {
        com.nendo.argosy.libretro.shader.ShaderRegistry(context)
    }
    internal val _shaderDownloader by lazy {
        com.nendo.argosy.libretro.shader.ShaderDownloader(_shaderRegistry.getCatalogDir())
    }

    fun getFrameRegistry(): com.nendo.argosy.libretro.frame.FrameRegistry = frameRegistry

    val shaderChainManager by lazy { routeInitShaderChainManager(this) }

    fun getShaderRegistry(): com.nendo.argosy.libretro.shader.ShaderRegistry = _shaderRegistry
    fun openShaderChainConfig() = routeOpenShaderChainConfig(this)
    fun openFrameConfig() = routeOpenFrameConfig(this)
    fun downloadAndSelectFrame(frameId: String) = routeDownloadAndSelectFrame(this, frameId)

    fun addShaderToStack(id: String, name: String) = shaderChainManager.addShaderToStack(id, name)
    fun removeShaderFromStack() = shaderChainManager.removeShaderFromStack()
    fun reorderShaderInStack(direction: Int) = shaderChainManager.reorderShaderInStack(direction)
    fun selectShaderInStack(index: Int) = shaderChainManager.selectShaderInStack(index)
    fun cycleShaderTab(direction: Int) = shaderChainManager.cycleShaderTab(direction)
    fun showShaderPicker() = shaderChainManager.showShaderPicker()
    fun dismissShaderPicker() = shaderChainManager.dismissShaderPicker()
    fun setShaderPickerFocusIndex(index: Int) = shaderChainManager.setShaderPickerFocusIndex(index)
    fun moveShaderPickerFocus(delta: Int) = shaderChainManager.moveShaderPickerFocus(delta)
    fun jumpShaderPickerSection(direction: Int) = shaderChainManager.jumpShaderPickerSection(direction)
    fun confirmShaderPickerSelection() = shaderChainManager.confirmShaderPickerSelection()
    fun moveShaderParamFocus(delta: Int) = shaderChainManager.moveShaderParamFocus(delta)
    fun adjustShaderParam(direction: Int) = shaderChainManager.adjustShaderParam(direction)
    fun resetShaderParam() = shaderChainManager.resetShaderParam()

    fun cycleBuiltinFilter(direction: Int) = routeCycleBuiltinFilter(this, direction)
    fun cycleBuiltinAspectRatio(direction: Int) = routeCycleBuiltinAspectRatio(this, direction)
    fun cycleBuiltinPortraitPosition(direction: Int) = routeCycleBuiltinPortraitPosition(this, direction)
    fun cycleBuiltinFastForwardSpeed(direction: Int) = routeCycleBuiltinFastForwardSpeed(this, direction)
    fun cycleBuiltinAudioVolume(direction: Int) = routeCycleBuiltinAudioVolume(this, direction)
    fun cycleBuiltinRotation(direction: Int) = routeCycleBuiltinRotation(this, direction)
    fun cycleBuiltinOverscanCrop(direction: Int) = routeCycleBuiltinOverscanCrop(this, direction)
    fun cycleBuiltinRewindSpeed(direction: Int) = routeCycleBuiltinRewindSpeed(this, direction)
    fun cycleBuiltinRewindBufferDuration(direction: Int) = routeCycleBuiltinRewindBufferDuration(this, direction)
    fun updatePlatformLibretroSetting(setting: LibretroSettingDef, value: String?) = routeUpdatePlatformLibretroSetting(this, setting, value)
    fun resetAllPlatformLibretroSettings() = routeResetAllPlatformLibretroSettings(this)
    fun updatePlatformControlSetting(field: String, value: Boolean?) = routeUpdatePlatformControlSetting(this, field, value)
    fun resetAllPlatformControlSettings() = routeResetAllPlatformControlSettings(this)
    fun loadCoreManagementState(preserveFocus: Boolean = false) = routeLoadCoreManagementState(this, preserveFocus)
    fun moveCoreManagementPlatformFocus(delta: Int): Boolean = routeMoveCoreManagementPlatformFocus(this, delta)
    fun moveCoreManagementCoreFocus(delta: Int): Boolean = routeMoveCoreManagementCoreFocus(this, delta)
    fun selectCoreForPlatform() = routeSelectCoreForPlatform(this)
    fun selectCoreAt(platformIndex: Int, coreIndex: Int) = routeSelectCoreAt(this, platformIndex, coreIndex)

    fun loadCoreOptionsState() = routeLoadCoreOptionsState(this)
    fun cycleCoreOptionsPlatformContext(direction: Int) = routeCycleCoreOptionsPlatformContext(this, direction)
    fun cycleCoreSelector(direction: Int) = routeCycleCoreSelector(this, direction)
    fun cycleCoreOptionValue(optionKey: String, direction: Int) = routeCycleCoreOptionValue(this, optionKey, direction)
    fun resetCoreOption(optionKey: String) = routeResetCoreOption(this, optionKey)
    fun resetAllCoreOptions() = routeResetAllCoreOptions(this)

    fun movePlatformSubFocus(delta: Int, maxIndex: Int): Boolean =
        emulatorDelegate.movePlatformSubFocus(delta, maxIndex)
    fun resetPlatformSubFocus() = emulatorDelegate.resetPlatformSubFocus()
    fun cycleCoreForPlatform(config: PlatformEmulatorConfig, direction: Int) =
        emulatorDelegate.cycleCoreForPlatform(viewModelScope, config, direction) { loadSettings() }
    fun changeExtensionForPlatform(config: PlatformEmulatorConfig, extension: String) =
        emulatorDelegate.changeExtensionForPlatform(viewModelScope, config.platform.id, extension) { loadSettings() }
    fun toggleLegacyMode(config: PlatformEmulatorConfig) =
        emulatorDelegate.toggleLegacyMode(viewModelScope, config) { loadSettings() }
    fun cycleDisplayTarget(config: PlatformEmulatorConfig, direction: Int) =
        emulatorDelegate.cycleDisplayTarget(viewModelScope, config, direction) { loadSettings() }

    fun cycleExtensionForPlatform(config: PlatformEmulatorConfig, direction: Int) =
        routeCycleExtensionForPlatform(this, config, direction)

    fun moveEmulatorPickerFocus(delta: Int) = emulatorDelegate.moveEmulatorPickerFocus(delta)

    fun confirmEmulatorPickerSelection() = routeConfirmEmulatorPickerSelection(this)
    fun handleEmulatorPickerItemTap(index: Int) = routeHandleEmulatorPickerItemTap(this, index)

    fun setEmulatorSavePath(emulatorId: String, path: String) =
        emulatorDelegate.setEmulatorSavePath(viewModelScope, emulatorId, path) { loadSettings() }
    fun resetEmulatorSavePath(emulatorId: String) =
        emulatorDelegate.resetEmulatorSavePath(viewModelScope, emulatorId) { loadSettings() }

    fun showSavePathModal(config: PlatformEmulatorConfig) = routeShowSavePathModal(this, config)

    fun dismissSavePathModal() = emulatorDelegate.dismissSavePathModal()
    fun toggleSavesBesideRom() = emulatorDelegate.toggleSavesBesideRom(viewModelScope)
    fun moveSavePathModalFocus(delta: Int) = emulatorDelegate.moveSavePathModalFocus(delta)
    fun moveSavePathModalButtonFocus(delta: Int) = emulatorDelegate.moveSavePathModalButtonFocus(delta)

    fun confirmSavePathModalSelection() = routeConfirmSavePathModalSelection(this)

    fun openMemcardPicker(config: PlatformEmulatorConfig) {
        val emulatorId = config.effectiveEmulatorId ?: return
        emulatorDelegate.showMemcardPicker(
            scope = viewModelScope,
            emulatorId = emulatorId,
            emulatorName = config.effectiveEmulatorName ?: emulatorId,
            emulatorPackage = config.effectiveEmulatorPackage,
            platformName = config.platform.name
        )
    }
    fun dismissMemcardPicker() = emulatorDelegate.dismissMemcardPicker()
    fun moveMemcardPickerFocus(delta: Int) = emulatorDelegate.moveMemcardPickerFocus(delta)
    fun confirmMemcardSelection(cardPath: String) =
        emulatorDelegate.confirmMemcardSelection(viewModelScope, cardPath) { loadSettings() }
    fun handleMemcardPickerItemTap(index: Int) {
        val info = uiState.value.emulators.memcardPickerInfo ?: return
        val card = info.cards.getOrNull(index) ?: return
        confirmMemcardSelection(card.path)
    }
    fun resetMemcardSelection(emulatorId: String) =
        emulatorDelegate.clearMemcardSelection(viewModelScope, emulatorId) { loadSettings() }
    fun forceCheckEmulatorUpdates() = routeForceCheckEmulatorUpdates(this)
    fun triggerEmulatorUpdate(emulatorId: String) = emulatorDelegate.triggerUpdateForEmulator(emulatorId, viewModelScope)
    fun selectUpdateModalVariant() = emulatorDelegate.selectUpdateModalVariant()
    fun moveUpdateModalFocus(delta: Int) = emulatorDelegate.moveUpdateModalFocus(delta)
    fun dismissUpdateModal() = emulatorDelegate.dismissUpdateModal()
    fun handlePlatformItemTap(index: Int) = routeHandlePlatformItemTap(this, index)

    fun navigateToSection(section: SettingsSection) = routeNavigateToSection(this, section)

    fun setFocusIndex(index: Int) { _uiState.update { it.copy(focusedIndex = index) } }

    fun requestEnumPicker(key: String) {
        _uiState.update { it.copy(enumPickerKey = key, enumPickerToken = it.enumPickerToken + 1) }
    }

    fun moveFocusWrapped(delta: Int, maxIndex: Int): Boolean {
        var moved = false
        _uiState.update {
            val newIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                it.focusedIndex, delta, maxIndex, it.controls.menuWrapMode
            )
            moved = newIndex != it.focusedIndex
            it.copy(focusedIndex = newIndex)
        }
        return moved
    }

    fun refreshSteamSettings() = steamDelegate.loadSteamSettings(context, viewModelScope)

    fun confirmLauncherAction() = routeConfirmLauncherAction(this)


    // Steam integration (new flow)
    fun connectToSteam() = steamDelegate.connectToSteam(context, viewModelScope)
    fun startSteamQrAuth() = steamDelegate.startQrAuth()
    fun cancelSteamQrAuth() = steamDelegate.cancelQrAuth()
    fun syncSteamLibrary() = steamDelegate.syncLibrary(context, viewModelScope)
    fun forceSyncSteamLibrary() = steamDelegate.forceSyncLibraryWithOverwrite(context, viewModelScope)
    fun disconnectSteam() = steamDelegate.disconnectSteam(context, viewModelScope)
    fun resetSteamLibrary() = steamDelegate.resetLibrary(viewModelScope)
    fun showAddSteamGameDialog() = steamDelegate.showAddSteamGameDialog()
    fun openGameNativeSyncDirPicker(folder: com.nendo.argosy.data.launcher.GameNativeSyncFolder) =
        steamDelegate.openStoreSyncDirPicker(viewModelScope, folder)
    fun setGameNativeSyncDir(folder: com.nendo.argosy.data.launcher.GameNativeSyncFolder, path: String) =
        steamDelegate.setStoreSyncDir(viewModelScope, folder, path)
    fun clearGameNativeSyncDir(folder: com.nendo.argosy.data.launcher.GameNativeSyncFolder) =
        steamDelegate.clearStoreSyncDir(viewModelScope, folder)
    fun rescanGameNativeStores() = steamDelegate.rescanStoreSync(viewModelScope)
    fun moveGameNativeActionFocus(delta: Int): Boolean {
        val moved = steamDelegate.moveGameNativeActionFocus(delta)
        if (!moved && _uiState.value.steam.gameNativeSyncDirs.isNotEmpty()) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
        return moved
    }
    fun openGameNativeFoldersModal() = steamDelegate.openGameNativeFoldersModal()
    fun dismissGameNativeFoldersModal() = steamDelegate.dismissGameNativeFoldersModal()
    fun moveGameNativeFoldersFocus(delta: Int) = steamDelegate.moveGameNativeFoldersFocus(delta)
    fun moveGameNativeFoldersActionFocus(delta: Int) {
        val folder = com.nendo.argosy.data.launcher.GameNativeSyncFolder.entries
            .getOrNull(_uiState.value.steam.gameNativeFoldersFocusIndex)
        val hasSecondAction = folder != null && _uiState.value.steam.gameNativeSyncDirs[folder] != null
        if (!steamDelegate.moveGameNativeFoldersActionFocus(delta) && hasSecondAction) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
        }
    }
    fun confirmGameNativeFoldersRow() = steamDelegate.confirmGameNativeFoldersRow(viewModelScope)
    val openGameNativeSyncDirPickerEvent get() = steamDelegate.openStoreSyncDirPicker
    @Suppress("UNUSED_PARAMETER")
    fun showAddSteamGameDialog(launcherPackage: String?) = steamDelegate.showAddSteamGameDialog()
    fun dismissAddSteamGameDialog() = steamDelegate.dismissAddSteamGameDialog()
    fun setAddGameAppId(appId: String) = steamDelegate.setAddGameAppId(appId)
    fun confirmAddSteamGame() = steamDelegate.confirmAddSteamGame(context, viewModelScope)
    fun cycleSteamInstallVolume(direction: Int = 1) = steamDelegate.cycleSteamInstallVolume(viewModelScope, direction)
    fun openSteamInstallPathPicker() =
        storageDelegate.openPlatformFolderPicker(viewModelScope, com.nendo.argosy.data.platform.LocalPlatformIds.STEAM)
    fun resetSteamInstallPath() =
        storageDelegate.resetPlatformToGlobal(viewModelScope, com.nendo.argosy.data.platform.LocalPlatformIds.STEAM)

    fun installSteamLauncher(emulatorId: String) = steamDelegate.installSteamLauncher(emulatorId, viewModelScope)
    fun refreshSteamMetadata() {}
    fun moveSteamVariantFocus(delta: Int) {}
    fun confirmSteamVariantSelection() {}
    fun dismissSteamVariantPicker() {}
    @Suppress("UNUSED_PARAMETER")
    fun handleSteamVariantItemTap(index: Int) {}
    fun checkRommConnection() = serverDelegate.checkRommConnection(viewModelScope)

    fun navigateBack(): Boolean = routeNavigateBack(this)

    fun moveFocus(delta: Int) = routeMoveFocus(this, delta)

    fun moveColorFocus(delta: Int) = routeMoveColorFocus(this, delta)

    fun selectFocusedColor() = displayDelegate.selectFocusedColor(viewModelScope)
    fun setThemeMode(mode: com.nendo.argosy.data.preferences.ThemeMode) = displayDelegate.setThemeMode(viewModelScope, mode)

    fun cycleThemeMode(direction: Int = 1) = routeCycleThemeMode(this, direction)

    fun setPrimaryColor(color: Int?) = displayDelegate.setPrimaryColor(viewModelScope, color)
    fun adjustHue(delta: Float) = displayDelegate.adjustHue(viewModelScope, delta)
    fun resetToDefaultColor() = displayDelegate.resetToDefaultColor(viewModelScope)
    fun setSecondaryColor(color: Int?) = displayDelegate.setSecondaryColor(viewModelScope, color)
    fun adjustSecondaryHue(delta: Float) = displayDelegate.adjustSecondaryHue(viewModelScope, delta)
    fun resetToDefaultSecondaryColor() = displayDelegate.resetToDefaultSecondaryColor(viewModelScope)
    fun adjustSurfaceTintBleed(delta: Int) = displayDelegate.adjustSurfaceTintBleed(viewModelScope, delta)
    fun cycleSurfaceTintBleed() = displayDelegate.cycleSurfaceTintBleed(viewModelScope)
    fun setBackdropEnabled(enabled: Boolean) = displayDelegate.setBackdropEnabled(viewModelScope, enabled)
    fun setBackdropPreset(preset: com.nendo.argosy.data.preferences.BackdropPreset) = displayDelegate.setBackdropPreset(viewModelScope, preset)
    fun cycleBackdropPreset(direction: Int = 1) = displayDelegate.cycleBackdropPreset(viewModelScope, direction)
    fun adjustBackdropCellSize(delta: Int) = displayDelegate.adjustBackdropCellSize(viewModelScope, delta)
    fun cycleBackdropCellSize() = displayDelegate.cycleBackdropCellSize(viewModelScope)
    fun adjustBackdropScatter(delta: Int) = displayDelegate.adjustBackdropScatter(viewModelScope, delta)
    fun cycleBackdropScatter() = displayDelegate.cycleBackdropScatter(viewModelScope)
    fun adjustBackdropScaleJitter(delta: Int) = displayDelegate.adjustBackdropScaleJitter(viewModelScope, delta)
    fun cycleBackdropScaleJitter() = displayDelegate.cycleBackdropScaleJitter(viewModelScope)
    fun adjustBackdropStrength(delta: Int) = displayDelegate.adjustBackdropStrength(viewModelScope, delta)
    fun cycleBackdropStrength() = displayDelegate.cycleBackdropStrength(viewModelScope)
    fun setBackdropEdgeStyle(style: com.nendo.argosy.data.preferences.BackdropEdgeStyle) = displayDelegate.setBackdropEdgeStyle(viewModelScope, style)
    fun cycleBackdropEdgeStyle(direction: Int = 1) = displayDelegate.cycleBackdropEdgeStyle(viewModelScope, direction)
    fun setBackdropVertexIcons(icons: com.nendo.argosy.data.preferences.BackdropVertexIcon) = displayDelegate.setBackdropVertexIcons(viewModelScope, icons)
    fun cycleBackdropVertexIcons(direction: Int = 1) = displayDelegate.cycleBackdropVertexIcons(viewModelScope, direction)
    fun setBackdropMotion(motion: com.nendo.argosy.data.preferences.BackdropMotion) = displayDelegate.setBackdropMotion(viewModelScope, motion)
    fun cycleBackdropMotion(direction: Int = 1) = displayDelegate.cycleBackdropMotion(viewModelScope, direction)
    fun adjustBackdropMotionSpeed(delta: Int) = displayDelegate.adjustBackdropMotionSpeed(viewModelScope, delta)
    fun cycleBackdropMotionSpeed() = displayDelegate.cycleBackdropMotionSpeed(viewModelScope)
    fun setBackdropDriftAngle(angle: Float) = displayDelegate.setBackdropDriftAngle(viewModelScope, angle)
    fun adjustBackdropDriftAngle(deltaDegrees: Float) = displayDelegate.adjustBackdropDriftAngle(viewModelScope, deltaDegrees)
    fun reshuffleBackdropSeed() = displayDelegate.reshuffleBackdropSeed(viewModelScope)
    fun adjustFontScale(slot: FontSlot, delta: Int) = displayDelegate.adjustFontScale(viewModelScope, slot, delta)
    fun cycleFontScale(slot: FontSlot) = displayDelegate.cycleFontScale(viewModelScope, slot)
    fun setGridDensity(density: GridDensity) = displayDelegate.setGridDensity(viewModelScope, density)

    fun cycleGridDensity(direction: Int = 1) = routeCycleGridDensity(this, direction)

    fun setUiScale(scale: Int) = displayDelegate.setUiScale(viewModelScope, scale)

    fun adjustUiScale(delta: Int) = routeAdjustUiScale(this, delta)

    fun adjustGripReservePercent(delta: Int) = routeAdjustGripReservePercent(this, delta)

    fun adjustBackgroundBlur(delta: Int) = routeAdjustBackgroundBlur(this, delta)
    fun adjustBackgroundSaturation(delta: Int) = routeAdjustBackgroundSaturation(this, delta)
    fun adjustBackgroundOpacity(delta: Int) = routeAdjustBackgroundOpacity(this, delta)
    fun cycleBackgroundBlur() = routeCycleBackgroundBlur(this)
    fun cycleBackgroundSaturation() = routeCycleBackgroundSaturation(this)
    fun cycleBackgroundOpacity() = routeCycleBackgroundOpacity(this)

    fun setUseGameBackground(use: Boolean) = displayDelegate.setUseGameBackground(viewModelScope, use)
    fun setHomeBackgroundMode(mode: HomeBackgroundMode) = displayDelegate.setHomeBackgroundMode(viewModelScope, mode)
    fun setHomeLayout(settings: com.nendo.argosy.domain.model.HomeLayoutSettings) =
        displayDelegate.setHomeLayout(viewModelScope, settings)
    fun cycleHomeBackgroundMode(direction: Int = 1) = displayDelegate.cycleHomeBackgroundMode(viewModelScope, direction)
    fun setUseAccentColorFooter(use: Boolean) = displayDelegate.setUseAccentColorFooter(viewModelScope, use)
    fun setCompactFooter(enabled: Boolean) = displayDelegate.setCompactFooter(viewModelScope, enabled)

    fun showGripControllerModal() = displayDelegate.showGripControllerModal()

    fun hideGripControllerModal() = displayDelegate.hideGripControllerModal()

    fun setGripReserveMode(mode: com.nendo.argosy.data.preferences.GripReserveMode) =
        displayDelegate.setGripReserveMode(viewModelScope, mode)

    fun cycleGripReserveMode(direction: Int) =
        displayDelegate.cycleGripReserveMode(viewModelScope, direction)

    fun addGripAutoController(controllerId: String, controllerName: String) =
        displayDelegate.addGripAutoController(viewModelScope, controllerId, controllerName)

    fun removeGripAutoController(controllerId: String) =
        displayDelegate.removeGripAutoController(viewModelScope, controllerId)
    fun setCustomBackgroundPath(path: String?) = displayDelegate.setCustomBackgroundPath(viewModelScope, path)
    fun openBackgroundPicker() = displayDelegate.openBackgroundPicker(viewModelScope)

    fun navigateToBoxArt() = routeNavigateToBoxArt(this)
    fun navigateToControllerGrip() = routeNavigateToControllerGrip(this)

    fun navigateToHomeScreen() = routeNavigateToHomeScreen(this)
    fun navigateToAmbientLed() = routeNavigateToAmbientLed(this)
    fun navigateToThemeSounds() = routeNavigateToThemeSounds(this)
    fun navigateToThemeMusic() = routeNavigateToThemeMusic(this)
    fun navigateToThemeFonts() = routeNavigateToThemeFonts(this)
    fun navigateToThemeBackdrop() = routeNavigateToThemeBackdrop(this)
    fun navigateToStorageGames() = routeNavigateToStorageGames(this)
    fun navigateToStorageMedia() = routeNavigateToStorageMedia(this)
    fun navigateToStorageCaches() = routeNavigateToStorageCaches(this, CACHES_ENTRY_TOP)
    fun navigateToStorageCachesForSteam() = routeNavigateToStorageCaches(this, CACHES_ENTRY_STEAM)

    fun navigateToSaveSyncScreen() {
        viewModelScope.launch {
            _navigationEvents.emit(
                NavigationEvent(com.nendo.argosy.ui.navigation.Screen.SaveSync.route)
            )
        }
    }

    /**
     * Deep links (drawer, notifications) land mid-tree with no parent screen behind them, so
     * Back has to leave settings rather than surface a screen the user never opened.
     */
    fun startAtSection(section: SettingsSection) = routeStartAtSection(this, section)
    fun refreshStorageAttribution(deep: Boolean = false) = attributionDelegate.refresh(force = true, deep = deep)

    fun toggleStateCache() = syncDelegate.toggleStateCache(viewModelScope)
    fun requestClearStateCache() = syncDelegate.requestClearStateCache(viewModelScope)
    fun cancelClearStateCache() = syncDelegate.cancelClearStateCache()
    fun confirmClearStateCache() = syncDelegate.confirmClearStateCache(viewModelScope)

    fun requestCachesClear(target: CachesClearTarget) {
        val driverBusy = _uiState.value.drivers.activeDownload
            ?.let { it.error == null && !it.isComplete } == true
        storageCachesDelegate.requestClear(target, driverDownloadActive = driverBusy)
    }

    fun cancelCachesClear() = storageCachesDelegate.cancelClear()

    fun confirmCachesClear() = storageCachesDelegate.confirmClear(viewModelScope) { target ->
        if (target == CachesClearTarget.SHADERS_CATALOG) {
            getShaderRegistry().invalidateInstalledCache()
        }
    }

    fun toggleGamesSortMode() = attributionDelegate.setGamesSortMode(
        when (_uiState.value.attribution.gamesSortMode) {
            StorageGamesSortMode.PLATFORM -> StorageGamesSortMode.SIZE
            StorageGamesSortMode.SIZE -> StorageGamesSortMode.PLATFORM
        }
    )

    fun openStoragePlatformGames(platformId: Long) {
        val name = _uiState.value.attribution.snapshot?.gamesPerPlatform
            ?.firstOrNull { it.platformId == platformId }?.name ?: ""
        routePushSection(this, SettingsSection.STORAGE_PLATFORM_GAMES)
        storagePlatformGamesDelegate.open(platformId, name, viewModelScope)
    }

    fun resetStoragePlatformHighlightedCategory() =
        storagePlatformGamesDelegate.setHighlightedCategory(0)

    fun setStoragePlatformHighlightedCategory(index: Int) =
        storagePlatformGamesDelegate.setHighlightedCategory(index)

    fun onStoragePlatformCoverTap(gameIndex: Int, gameId: Long) {
        setFocusIndex(gameIndex)
        storagePlatformGamesDelegate.setHighlightedCategory(0)
        storagePlatformGamesDelegate.requestDeleteConfirm(gameId)
    }

    fun onStoragePlatformCategoryTap(
        gameIndex: Int,
        categoryIndex: Int,
        gameId: Long,
        bucket: com.nendo.argosy.domain.usecase.storage.GameStorageBucket
    ) {
        setFocusIndex(gameIndex)
        storagePlatformGamesDelegate.setHighlightedCategory(categoryIndex)
        if (bucket == com.nendo.argosy.domain.usecase.storage.GameStorageBucket.BASE) {
            storagePlatformGamesDelegate.requestDeleteConfirm(gameId)
        } else {
            storagePlatformGamesDelegate.requestCategoryDeleteConfirm(gameId, bucket)
        }
    }

    fun requestStoragePlatformCategoryDelete(
        gameId: Long,
        bucket: com.nendo.argosy.domain.usecase.storage.GameStorageBucket
    ) = storagePlatformGamesDelegate.requestCategoryDeleteConfirm(gameId, bucket)

    fun dismissStoragePlatformCategoryDelete() =
        storagePlatformGamesDelegate.dismissCategoryDeleteConfirm()

    fun confirmStoragePlatformCategoryDelete(
        gameId: Long,
        bucket: com.nendo.argosy.domain.usecase.storage.GameStorageBucket
    ) = storagePlatformGamesDelegate.deleteCategory(gameId, bucket, viewModelScope) {
        backToStorageGamesFromPlatform()
    }

    fun requestStoragePlatformGameDelete(gameId: Long) =
        storagePlatformGamesDelegate.requestDeleteConfirm(gameId)

    fun dismissStoragePlatformGameDelete() = storagePlatformGamesDelegate.dismissDeleteConfirm()

    fun confirmStoragePlatformGameDelete(gameId: Long, withSoundtrack: Boolean) =
        storagePlatformGamesDelegate.confirmDeleteGame(gameId, withSoundtrack, viewModelScope) {
            backToStorageGamesFromPlatform()
        }

    private fun backToStorageGamesFromPlatform() {
        routePopSection(this)
    }

    fun openFontPicker(slot: FontSlot) {
        viewModelScope.launch { _openFontPickerEvent.emit(slot) }
    }

    fun importFont(slot: FontSlot, uri: Uri) = routeImportFont(this, slot, uri)
    fun revertFont(slot: FontSlot) = routeRevertFont(this, slot)

    internal fun updateFontNameState(slot: FontSlot, name: String?) {
        val display = _uiState.value.display
        displayDelegate.updateState(when (slot) {
            FontSlot.DISPLAY -> display.copy(displayFontName = name)
            FontSlot.BODY -> display.copy(bodyFontName = name)
        })
    }

    fun cycleBoxArtShape(direction: Int = 1) = displayDelegate.cycleBoxArtShape(viewModelScope, direction)
    fun cycleBoxArtCornerRadius(direction: Int = 1) = displayDelegate.cycleBoxArtCornerRadius(viewModelScope, direction)
    fun cycleBoxArtBorderThickness(direction: Int = 1) = displayDelegate.cycleBoxArtBorderThickness(viewModelScope, direction)
    fun cycleBoxArtBorderStyle(direction: Int = 1) = displayDelegate.cycleBoxArtBorderStyle(viewModelScope, direction)
    fun cycleGlassBorderTint(direction: Int = 1) = displayDelegate.cycleGlassBorderTint(viewModelScope, direction)

    fun cycleGradientPreset(direction: Int = 1) = routeCycleGradientPreset(this, direction)
    fun setGradientPreset(preset: com.nendo.argosy.data.cache.GradientPreset) = routeSetGradientPreset(this, preset)
    fun toggleGradientAdvancedMode() = routeToggleGradientAdvancedMode(this)

    fun cycleBoxArtGlowStrength(direction: Int = 1) = displayDelegate.cycleBoxArtGlowStrength(viewModelScope, direction)
    fun cycleBoxArtOuterEffect(direction: Int = 1) = displayDelegate.cycleBoxArtOuterEffect(viewModelScope, direction)
    fun cycleBoxArtOuterEffectThickness(direction: Int = 1) = displayDelegate.cycleBoxArtOuterEffectThickness(viewModelScope, direction)
    /**
     * Cycle the glow source, refreshing the preview card's sampled colours with it.
     *
     * Only the gradient-preset and sampling routes used to trigger extraction, and every control
     * that reaches them is hidden unless the border style is Gradient. Picking Cover under any
     * other border style therefore left the card showing whatever was sampled last, or nothing.
     */
    fun cycleGlowColorMode(direction: Int = 1) {
        displayDelegate.cycleGlowColorMode(viewModelScope, direction)
        extractGradientForPreview()
    }
    fun cycleSystemIconPosition(direction: Int = 1) = displayDelegate.cycleSystemIconPosition(viewModelScope, direction)
    fun cycleSystemIconPadding(direction: Int = 1) = displayDelegate.cycleSystemIconPadding(viewModelScope, direction)
    fun cyclePlatformIndicatorStyle(direction: Int = 1) = displayDelegate.cyclePlatformIndicatorStyle(viewModelScope, direction)
    fun cyclePlatformIndicatorContent(direction: Int = 1) = displayDelegate.cyclePlatformIndicatorContent(viewModelScope, direction)
    fun cycleBoxArtInnerEffect(direction: Int = 1) = displayDelegate.cycleBoxArtInnerEffect(viewModelScope, direction)
    fun cycleBoxArtInnerEffectThickness(direction: Int = 1) = displayDelegate.cycleBoxArtInnerEffectThickness(viewModelScope, direction)
    fun setLibraryDefaultSortIndex(index: Int) = displayDelegate.setLibraryDefaultSortIndex(viewModelScope, index)
    fun cycleLibraryDefaultSort(direction: Int) = displayDelegate.cycleLibraryDefaultSort(viewModelScope, direction)
    fun setSortInstalledFirst(enabled: Boolean) = displayDelegate.setSortInstalledFirst(viewModelScope, enabled)
    fun setSortFavoritesFirst(enabled: Boolean) = displayDelegate.setSortFavoritesFirst(viewModelScope, enabled)
    fun setLibraryDefaultSource(source: String) = displayDelegate.setLibraryDefaultSource(viewModelScope, source)
    fun cycleLibraryDefaultSource(direction: Int) = displayDelegate.cycleLibraryDefaultSource(viewModelScope, direction)
    fun setLibraryDefaultPlatform(name: String) = displayDelegate.setLibraryDefaultPlatform(viewModelScope, name)
    fun cycleLibraryDefaultPlatform(direction: Int, options: List<String>) =
        displayDelegate.cycleLibraryDefaultPlatform(viewModelScope, direction, options)
    fun navigateToLibraryView() = routeNavigateToLibraryView(this)
    fun setVideoWallpaperEnabled(enabled: Boolean) = displayDelegate.setVideoWallpaperEnabled(viewModelScope, enabled)
    fun cycleVideoWallpaperDelay(direction: Int = 1) = displayDelegate.cycleVideoWallpaperDelay(viewModelScope, direction)
    fun setVideoWallpaperMuted(muted: Boolean) = displayDelegate.setVideoWallpaperMuted(viewModelScope, muted)
    fun setAmbientLedEnabled(enabled: Boolean) = displayDelegate.setAmbientLedEnabled(viewModelScope, enabled)
    fun setAmbientLedBrightness(brightness: Int) = displayDelegate.setAmbientLedBrightness(viewModelScope, brightness)
    fun adjustAmbientLedBrightness(delta: Int) = displayDelegate.adjustAmbientLedBrightness(viewModelScope, delta)
    fun cycleAmbientLedBrightness() = displayDelegate.cycleAmbientLedBrightness(viewModelScope)
    fun setAmbientLedAudioBrightness(enabled: Boolean) = displayDelegate.setAmbientLedAudioBrightness(viewModelScope, enabled)
    fun setAmbientLedAudioColors(enabled: Boolean) = displayDelegate.setAmbientLedAudioColors(viewModelScope, enabled)
    fun cycleAmbientLedColorMode(direction: Int = 1) = displayDelegate.cycleAmbientLedColorMode(viewModelScope, direction)
    fun setAmbientLedCoverArtEnabled(enabled: Boolean) = displayDelegate.setAmbientLedCoverArtEnabled(viewModelScope, enabled)
    fun setAmbientLedCustomColor(enabled: Boolean) = displayDelegate.setAmbientLedCustomColor(viewModelScope, enabled)
    fun setAmbientLedCustomColorHue(hue: Int) = displayDelegate.setAmbientLedCustomColorHue(viewModelScope, hue)
    fun adjustAmbientLedCustomColorHue(delta: Int) = displayDelegate.adjustAmbientLedCustomColorHue(viewModelScope, delta)
    fun cycleAmbientLedTransitionMs(direction: Int) = displayDelegate.cycleAmbientLedTransitionMs(viewModelScope, direction)
    fun cycleAmbientLedTransitionMsWrap() = displayDelegate.cycleAmbientLedTransitionMsWrap(viewModelScope)
    fun setAmbientLedTransitionMs(ms: Int) = displayDelegate.setAmbientLedTransitionMs(viewModelScope, ms)
    fun setAmbientLedScreenEnabled(enabled: Boolean) = displayDelegate.setAmbientLedScreenEnabled(viewModelScope, enabled)
    fun setAmbientLedAchievementFlash(enabled: Boolean) = displayDelegate.setAmbientLedAchievementFlash(viewModelScope, enabled)
    fun setInstalledOnlyHome(enabled: Boolean) = displayDelegate.setInstalledOnlyHome(viewModelScope, enabled)

    fun loadPreviewGames() = routeLoadPreviewGames(this)
    fun cyclePrevPreviewGame() = routeCyclePrevPreviewGame(this)
    fun cycleNextPreviewGame() = routeCycleNextPreviewGame(this)
    fun extractGradientForPreview() = routeExtractGradientForPreview(this)

    fun cycleGradientSampleGrid(direction: Int) = routeCycleGradientSampleGrid(this, direction)
    fun cycleGradientRadius(direction: Int) = routeCycleGradientRadius(this, direction)
    fun cycleGradientMinSaturation(direction: Int) = routeCycleGradientMinSaturation(this, direction)
    fun cycleGradientMinValue(direction: Int) = routeCycleGradientMinValue(this, direction)
    fun cycleGradientHueDistance(direction: Int) = routeCycleGradientHueDistance(this, direction)
    fun cycleGradientSaturationBump(direction: Int) = routeCycleGradientSaturationBump(this, direction)
    fun cycleGradientValueClamp(direction: Int) = routeCycleGradientValueClamp(this, direction)

    fun setHapticEnabled(enabled: Boolean) = controlsDelegate.setHapticEnabled(viewModelScope, enabled)

    fun cycleVibrationStrength() = controlsDelegate.adjustVibrationStrength(0.1f)

    fun adjustVibrationStrength(delta: Float) = routeAdjustVibrationStrength(this, delta)

    fun setSoundEnabled(enabled: Boolean) = soundsDelegate.setSoundEnabled(viewModelScope, enabled)

    fun setBetaUpdatesEnabled(enabled: Boolean) = routeSetBetaUpdatesEnabled(this, enabled)
    fun setAppAffinityEnabled(enabled: Boolean) = routeSetAppAffinityEnabled(this, enabled)
    fun setAppLanguage(tag: String) = routeSetAppLanguage(this, tag)
    fun cycleAppLanguage(direction: Int = 1) = routeCycleAppLanguage(this, direction)

    fun setDualScreenEnabled(enabled: Boolean) = routeSetDualScreenEnabled(this, enabled)

    fun cycleDisplayRoleOverride(direction: Int = 1) = routeCycleDisplayRoleOverride(this, direction)

    fun setDisplayRoleOverride(value: com.nendo.argosy.data.preferences.DisplayRoleOverride) =
        routeSetDisplayRoleOverride(this, value)

    fun setSoundVolume(volume: Int) = soundsDelegate.setSoundVolume(viewModelScope, volume)

    fun adjustSoundVolume(delta: Int) = routeAdjustSoundVolume(this, delta)
    fun cycleSoundVolume() = routeCycleSoundVolume(this)

    fun showSoundPicker(type: SoundType) = soundsDelegate.showSoundPicker(type)
    fun dismissSoundPicker() = soundsDelegate.dismissSoundPicker()
    fun moveSoundPickerFocus(delta: Int) = soundsDelegate.moveSoundPickerFocus(delta)
    fun resetSoundToDefault(type: SoundType) = soundsDelegate.resetSoundToDefault(viewModelScope, type)
    fun confirmSoundPickerSelection() = soundsDelegate.confirmSoundPickerSelection(viewModelScope)
    fun confirmSoundPickerSelectionAt(index: Int) = soundsDelegate.confirmSoundPickerSelectionAt(viewModelScope, index)
    fun setCustomSoundFile(type: SoundType, filePath: String, fromRomm: Boolean = false) =
        soundsDelegate.setCustomSoundFile(viewModelScope, type, filePath, fromRomm)
    fun setAmbientAudioEnabled(enabled: Boolean) = ambientAudioDelegate.setEnabled(viewModelScope, enabled)
    fun setAmbientAudioVolume(volume: Int) = ambientAudioDelegate.setVolume(viewModelScope, volume)

    fun adjustAmbientAudioVolume(delta: Int) = routeAdjustAmbientAudioVolume(this, delta)
    fun cycleAmbientAudioVolume() = routeCycleAmbientAudioVolume(this)

    fun setAmbientAudioShuffle(shuffle: Boolean) = ambientAudioDelegate.setShuffle(viewModelScope, shuffle)
    fun setGameDetailThemeEnabled(enabled: Boolean) = ambientAudioDelegate.setGameDetailTheme(viewModelScope, enabled)
    fun addBgmPlaylistEntry(path: String) = ambientAudioDelegate.addPlaylistEntry(viewModelScope, path)
    fun openBgmPlaylistManager() = ambientAudioDelegate.openPlaylistManager(viewModelScope)
    fun openBgmAddMusicBrowser() = ambientAudioDelegate.openAddMusicBrowser(viewModelScope)
    fun openMusicBrowserBgm() = ambientAudioDelegate.openMusicBrowser(viewModelScope)
    fun openMusicLocationPicker() = ambientAudioDelegate.openMusicLocationPicker(viewModelScope)
    fun onMusicLocationSelected(path: String) = ambientAudioDelegate.onMusicLocationSelected(viewModelScope, path)
    fun confirmMusicRelocation() = ambientAudioDelegate.confirmMusicRelocation(viewModelScope)
    fun skipMusicRelocation() = ambientAudioDelegate.skipMusicRelocation(viewModelScope)
    fun cancelMusicRelocation() = ambientAudioDelegate.cancelMusicRelocation()
    fun setSwapAB(enabled: Boolean) = controlsDelegate.setSwapAB(viewModelScope, enabled)
    fun setSwapXY(enabled: Boolean) = controlsDelegate.setSwapXY(viewModelScope, enabled)
    fun cycleControllerLayout(direction: Int = 1) = controlsDelegate.cycleControllerLayout(viewModelScope, direction)
    fun setControllerLayout(layout: String) = controlsDelegate.setControllerLayout(viewModelScope, layout)
    fun refreshDetectedLayout() = controlsDelegate.refreshDetectedLayout()
    fun setSwapStartSelect(enabled: Boolean) = controlsDelegate.setSwapStartSelect(viewModelScope, enabled)
    fun cycleSelectLCombo(direction: Int = 1) = controlsDelegate.cycleSelectLCombo(viewModelScope, direction)
    fun cycleSelectRCombo(direction: Int = 1) = controlsDelegate.cycleSelectRCombo(viewModelScope, direction)
    fun setSelectLCombo(value: String) = controlsDelegate.setSelectLCombo(viewModelScope, value)
    fun setSelectRCombo(value: String) = controlsDelegate.setSelectRCombo(viewModelScope, value)
    fun cycleMenuWrapMode(direction: Int = 1) = controlsDelegate.cycleMenuWrapMode(viewModelScope, direction)
    fun setMenuWrapMode(mode: com.nendo.argosy.data.preferences.MenuWrapMode) = controlsDelegate.setMenuWrapMode(viewModelScope, mode)
    fun refreshUsageStatsPermission() = controlsDelegate.refreshUsageStatsPermission()
    fun openUsageStatsSettings() = controlsDelegate.openUsageStatsSettings()
    fun openStorageSettings() = permissionsDelegate.openStorageSettings()
    fun openNotificationSettings() = permissionsDelegate.openNotificationSettings()
    fun openWriteSettings() = permissionsDelegate.openWriteSettings()
    fun openDisplayOverlaySettings() = permissionsDelegate.openDisplayOverlaySettings()

    fun requestScreenCapturePermission() = routeRequestScreenCapturePermission(this)
    fun refreshPermissions() = permissionsDelegate.refreshPermissions()

    fun showSyncFiltersModal() = routeShowSyncFiltersModal(this)
    fun dismissSyncFiltersModal() = routeDismissSyncFiltersModal(this)

    fun moveSyncFiltersModalFocus(delta: Int) = syncDelegate.moveSyncFiltersModalFocus(delta)
    fun confirmSyncFiltersModalSelection() = syncDelegate.confirmSyncFiltersModalSelection(viewModelScope)

    fun showPlatformFiltersModal() = routeShowPlatformFiltersModal(this)
    fun dismissPlatformFiltersModal() = routeDismissPlatformFiltersModal(this)

    fun platformFiltersUp() = syncDelegate.platformFiltersUp()
    fun platformFiltersDown() = syncDelegate.platformFiltersDown()
    fun platformFiltersLeft() = syncDelegate.platformFiltersLeft()
    fun platformFiltersRight() = syncDelegate.platformFiltersRight()
    fun platformFiltersConfirm() = syncDelegate.platformFiltersConfirm(viewModelScope)
    fun platformFiltersBack() = syncDelegate.platformFiltersBack()
    fun openPlatformSearch() = syncDelegate.openPlatformSearch()
    fun closePlatformSearch() = syncDelegate.closePlatformSearch()
    fun openPlatformSortMenu() = syncDelegate.openPlatformSortMenu()
    fun closePlatformSortMenu() = syncDelegate.closePlatformSortMenu()
    fun togglePlatformSyncEnabled(platformId: Long) = syncDelegate.togglePlatformSyncEnabled(viewModelScope, platformId)

    fun setPlatformFilterSortMode(mode: PlatformFilterLogic.SortMode) = syncDelegate.setPlatformFilterSortMode(mode)
    fun setPlatformFilterSearchQuery(query: String) = syncDelegate.setPlatformFilterSearchQuery(query)
    fun cyclePlatformFilterMode() = syncDelegate.cyclePlatformFilterMode()

    fun showRegionPicker() = routeShowRegionPicker(this)
    fun dismissRegionPicker() = routeDismissRegionPicker(this)

    fun moveRegionPickerFocus(delta: Int) = syncDelegate.moveRegionPickerFocus(delta)
    fun confirmRegionPickerSelection() = syncDelegate.confirmRegionPickerSelection(viewModelScope)
    fun toggleRegion(region: String) = syncDelegate.toggleRegion(viewModelScope, region)
    fun liftRegion() = syncDelegate.liftRegion()
    fun liftRegionAt(region: String) = syncDelegate.liftRegionAt(region)
    fun moveRegionTo(region: String, targetIndex: Int) = syncDelegate.moveRegionTo(region, targetIndex)
    fun dropHeldRegion() = syncDelegate.dropHeldRegion(viewModelScope)
    fun cancelRegionHold() = syncDelegate.cancelRegionHold()
    fun toggleRegionMode() = syncDelegate.toggleRegionMode(viewModelScope)
    fun setExcludeBeta(exclude: Boolean) = syncDelegate.setExcludeBeta(viewModelScope, exclude)
    fun setExcludePrototype(exclude: Boolean) = syncDelegate.setExcludePrototype(viewModelScope, exclude)
    fun setExcludeDemo(exclude: Boolean) = syncDelegate.setExcludeDemo(viewModelScope, exclude)
    fun setExcludeHack(exclude: Boolean) = syncDelegate.setExcludeHack(viewModelScope, exclude)
    fun setExcludeUnofficial(exclude: Boolean) =
        syncDelegate.setExcludeUnofficial(viewModelScope, exclude)
    fun setDeleteOrphans(delete: Boolean) = syncDelegate.setDeleteOrphans(viewModelScope, delete)

    fun toggleSyncScreenshots() = routeToggleSyncScreenshots(this)
    fun toggleUploadScreenshots() = routeToggleUploadScreenshots(this)
    fun toggleBoxArtCache() = routeToggleBoxArtCache(this)
    fun setDownloadCategoryDefault(categoryKey: String, include: Boolean) =
        syncDelegate.setDownloadCategoryDefault(viewModelScope, categoryKey, include)

    fun openPlatformDownloadDefaults(platformSlug: String) {
        _uiState.update {
            it.copy(platformDetail = it.platformDetail.copy(
                showDownloadDefaults = true,
                downloadDefaultsFocusIndex = 0,
                downloadDefaultsSlug = platformSlug
            ))
        }
    }

    fun dismissPlatformDownloadDefaults() {
        _uiState.update {
            it.copy(platformDetail = it.platformDetail.copy(showDownloadDefaults = false))
        }
    }

    fun movePlatformDownloadDefaultsFocus(delta: Int) {
        _uiState.update { st ->
            val maxIndex = com.nendo.argosy.data.preferences.DownloadDefaults.CONFIGURABLE_KEYS.size
            val newIndex = com.nendo.argosy.ui.input.InputDispatcher.computeWrappedIndex(
                st.platformDetail.downloadDefaultsFocusIndex, delta, maxIndex, st.controls.menuWrapMode
            )
            st.copy(platformDetail = st.platformDetail.copy(downloadDefaultsFocusIndex = newIndex))
        }
    }

    fun setPlatformDownloadDefault(categoryKey: String, include: Boolean) {
        val slug = _uiState.value.platformDetail.downloadDefaultsSlug ?: return
        _uiState.update { st ->
            val overrides = st.platformDetail.downloadOverrides + (categoryKey to include)
            st.copy(platformDetail = st.platformDetail.copy(downloadOverrides = overrides))
        }
        viewModelScope.launch {
            preferencesRepository.setDownloadCategoryPlatformOverride(slug, categoryKey, include)
        }
    }

    fun setFocusedPlatformDownloadDefault(include: Boolean) {
        val keys = com.nendo.argosy.data.preferences.DownloadDefaults.CONFIGURABLE_KEYS
        val key = keys.getOrNull(_uiState.value.platformDetail.downloadDefaultsFocusIndex) ?: return
        setPlatformDownloadDefault(key, include)
    }

    fun activatePlatformDownloadDefaultsRow() {
        val detail = _uiState.value.platformDetail
        val keys = com.nendo.argosy.data.preferences.DownloadDefaults.CONFIGURABLE_KEYS
        val idx = detail.downloadDefaultsFocusIndex
        if (idx < keys.size) {
            val key = keys[idx]
            val effective = detail.downloadOverrides[key]
                ?: detail.globalDownloadDefaults[key]
                ?: (com.nendo.argosy.data.preferences.DownloadDefaults.FACTORY[key] ?: false)
            setPlatformDownloadDefault(key, !effective)
        } else {
            resetPlatformDownloadDefaults()
        }
    }

    fun resetPlatformDownloadDefaults() {
        val detail = _uiState.value.platformDetail
        val slug = detail.downloadDefaultsSlug ?: return
        val keys = detail.downloadOverrides.keys.toList()
        _uiState.update { st ->
            st.copy(platformDetail = st.platformDetail.copy(downloadOverrides = emptyMap()))
        }
        viewModelScope.launch {
            keys.forEach { preferencesRepository.setDownloadCategoryPlatformOverride(slug, it, null) }
        }
    }

    fun enableSaveSync() = syncDelegate.enableSaveSync(viewModelScope)
    fun toggleSaveSync() = syncDelegate.toggleSaveSync(viewModelScope)
    fun toggleSecureSaves() = syncDelegate.toggleSecureSaves(viewModelScope)
    fun confirmDisableSecureSaves() = syncDelegate.confirmDisableSecureSaves(viewModelScope)
    fun cancelDisableSecureSaves() = syncDelegate.cancelDisableSecureSaves()
    fun cycleSaveCacheLimit(direction: Int = 1) = syncDelegate.cycleSaveCacheLimit(viewModelScope, direction)
    fun setSaveCacheLimit(limit: Int) = syncDelegate.setSaveCacheLimit(viewModelScope, limit)

    fun onStoragePermissionResult(granted: Boolean) = routeOnStoragePermissionResult(this, granted)

    fun onNotificationPermissionResult(granted: Boolean) = syncDelegate.onNotificationPermissionResult(viewModelScope, granted)
    fun onMediaPermissionResult(granted: Boolean) = routeOnMediaPermissionResult(this, granted)
    fun runSaveSyncNow() = syncDelegate.runSaveSyncNow(viewModelScope)

    fun requestResetSaveCache() = syncDelegate.requestResetSaveCache(viewModelScope)
    fun confirmResetSaveCache() = syncDelegate.confirmResetSaveCache(viewModelScope)
    fun cancelResetSaveCache() = syncDelegate.cancelResetSaveCache()

    fun requestClearPathCache() = syncDelegate.requestClearPathCache(viewModelScope)
    fun confirmClearPathCache() = syncDelegate.confirmClearPathCache(viewModelScope)
    fun cancelClearPathCache() = syncDelegate.cancelClearPathCache()

    fun requestSyncSaves() = syncDelegate.requestSyncSaves()
    fun confirmSyncSaves() = syncDelegate.confirmSyncSaves(viewModelScope)
    fun cancelSyncSaves() = syncDelegate.cancelSyncSaves()
    fun moveSyncConfirmFocus(delta: Int) = syncDelegate.moveSyncConfirmFocus(delta)

    fun openImageCachePicker() = syncDelegate.openImageCachePicker(viewModelScope)
    fun moveImageCacheActionFocus(delta: Int) = syncDelegate.moveImageCacheActionFocus(delta)
    fun setImageCachePath(path: String) = syncDelegate.onImageCachePathSelected(viewModelScope, path)
    fun resetImageCacheToDefault() = syncDelegate.resetImageCacheToDefault(viewModelScope)

    fun validateImageCache() = routeValidateImageCache(this)
    fun validateDownloads() = routeValidateDownloads(this)

    fun toggleWeeklyIntegrityCheck(enabled: Boolean) =
        storageDelegate.toggleWeeklyIntegrityCheck(viewModelScope, enabled)

    fun cycleMaxConcurrentDownloads() = storageDelegate.cycleMaxConcurrentDownloads(viewModelScope)

    fun adjustMaxConcurrentDownloads(delta: Int) = routeAdjustMaxConcurrentDownloads(this, delta)

    fun cycleInstantDownloadThreshold(direction: Int = 1) = storageDelegate.cycleInstantDownloadThreshold(viewModelScope, direction)

    fun toggleStageDownloadsInternally() = storageDelegate.toggleStageDownloadsInternally(viewModelScope)

    fun toggleScreenDimmer() = storageDelegate.toggleScreenDimmer(viewModelScope)
    fun cycleScreenDimmerTimeout() = storageDelegate.cycleScreenDimmerTimeout(viewModelScope)

    fun adjustScreenDimmerTimeout(delta: Int) = routeAdjustScreenDimmerTimeout(this, delta)

    fun cycleScreenDimmerLevel() = storageDelegate.cycleScreenDimmerLevel(viewModelScope)

    fun adjustScreenDimmerLevel(delta: Int) = routeAdjustScreenDimmerLevel(this, delta)

    fun openFolderPicker() = storageDelegate.openFolderPicker()
    fun clearFolderPickerFlag() = storageDelegate.clearFolderPickerFlag()
    fun setStoragePath(uriString: String) = storageDelegate.setStoragePath(viewModelScope, uriString)
    fun confirmMigration() = storageDelegate.confirmMigration(viewModelScope)
    fun cancelMigration() = storageDelegate.cancelMigration()
    fun skipMigration() = storageDelegate.skipMigration(viewModelScope)
    fun confirmBuiltinPathMigration() = routeConfirmBuiltinPathMigration(this)
    fun cancelBuiltinPathMigration() = routeCancelBuiltinPathMigration(this)
    fun skipBuiltinPathMigration() = routeSkipBuiltinPathMigration(this)
    fun togglePlatformSync(platformId: Long, enabled: Boolean) {
        storageDelegate.togglePlatformSync(viewModelScope, platformId, enabled)
        _uiState.update { state ->
            state.copy(
                emulators = state.emulators.copy(
                    platforms = state.emulators.platforms.map { cfg ->
                        if (cfg.platform.id == platformId) {
                            cfg.copy(platform = cfg.platform.copy(syncEnabled = enabled))
                        } else {
                            cfg
                        }
                    }
                )
            )
        }
    }

    fun togglePlatformCombineContent(platformId: Long, enabled: Boolean) {
        storageDelegate.togglePlatformCombineContent(viewModelScope, platformId, enabled)
        if (!enabled) offerCombineRestore(platformId)
        _uiState.update { state ->
            state.copy(
                emulators = state.emulators.copy(
                    platforms = state.emulators.platforms.map { cfg ->
                        if (cfg.platform.id == platformId) {
                            cfg.copy(platform = cfg.platform.copy(combineContent = enabled))
                        } else {
                            cfg
                        }
                    }
                )
            )
        }
    }

    /**
     * Turning Combine Content off stops enforcing the flat layout but never moves anything on its
     * own, so the games it is still holding flat are offered back to their own folders here. Games
     * with no updates or DLC of their own have nothing to gain from a folder and are not counted.
     */
    private fun offerCombineRestore(platformId: Long) {
        viewModelScope.launch {
            val platformDir = gameRepository.getDownloadDirForPlatformId(platformId)
            val holding = extContentOrganizer.gamesHoldingCombinedLayout(platformId, platformDir)
            if (holding.isEmpty()) return@launch
            _uiState.update {
                it.copy(platformDetail = it.platformDetail.copy(combineRestoreCount = holding.size))
            }
        }
    }

    fun dismissCombineRestore() {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(combineRestoreCount = 0)) }
    }

    fun confirmCombineRestore(platformId: Long) {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(combineRestoreCount = 0)) }
        viewModelScope.launch {
            val platformDir = gameRepository.getDownloadDirForPlatformId(platformId)
            val holding = extContentOrganizer.gamesHoldingCombinedLayout(platformId, platformDir)
            val restored = holding.count { extContentOrganizer.restoreFromCombinedLayout(it, platformDir) > 0 }
            notificationManager.show(
                title = NotificationText.Res(
                    if (restored > 0) {
                        R.string.settings_shell_vm_folders_restored_title
                    } else {
                        R.string.settings_shell_vm_nothing_moved_title
                    }
                ),
                subtitle = if (restored > 0) {
                    NotificationText.Plural(
                        R.plurals.settings_shell_vm_games_moved_back, restored, listOf(restored)
                    )
                } else {
                    NotificationText.Res(R.string.settings_shell_vm_no_games_moved_back)
                },
                type = if (restored > 0) {
                    com.nendo.argosy.core.notification.NotificationType.SUCCESS
                } else {
                    com.nendo.argosy.core.notification.NotificationType.WARNING
                },
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }

    fun focusIndexForPlatform(platformId: Long?): Int {
        val focusable = com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem
            .buildItems(_uiState.value.emulators.platforms)
            .filter { it.isFocusable }
        return focusable.indexOfFirst {
            it is com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem.PlatformItem &&
                it.config.platform.id == platformId
        }.coerceAtLeast(0)
    }

    fun platformArrayIndexAtFocus(focusedIndex: Int): Int? {
        val item = com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem
            .buildItems(_uiState.value.emulators.platforms)
            .filter { it.isFocusable }
            .getOrNull(focusedIndex)
        return (item as? com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem.PlatformItem)?.index
    }
    fun enablePlatformAndReload(platformId: Long) {
        storageDelegate.togglePlatformSync(viewModelScope, platformId, true)
        viewModelScope.launch {
            kotlinx.coroutines.delay(100)
            loadSettings()
        }
    }
    fun openPlatformFolderPicker(platformId: Long) = storageDelegate.openPlatformFolderPicker(viewModelScope, platformId)
    fun setPlatformPath(platformId: Long, path: String) =
        storageDelegate.setPlatformPath(viewModelScope, platformId, path)
    fun resetPlatformToGlobal(platformId: Long) = storageDelegate.resetPlatformToGlobal(viewModelScope, platformId)
    fun syncPlatform(platformId: Long, platformName: String) = storageDelegate.syncPlatform(viewModelScope, platformId, platformName)
    fun openPlatformSavePathPicker(platformId: Long) = storageDelegate.emitSavePathPicker(viewModelScope, platformId)

    fun setPlatformSavePath(platformId: Long, basePath: String) = routeSetPlatformSavePath(this, platformId, basePath)
    fun resetPlatformSavePath(platformId: Long) = routeResetPlatformSavePath(this, platformId)
    fun setPlatformStatePath(platformId: Long, basePath: String) = routeSetPlatformStatePath(this, platformId, basePath)
    fun resetPlatformStatePath(platformId: Long) = routeResetPlatformStatePath(this, platformId)

    fun togglePlatformsExpanded() = storageDelegate.togglePlatformsExpanded()

    fun jumpToNextSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean =
        routeJumpToNextSection(this, sections)
    fun jumpToPrevSection(sections: List<com.nendo.argosy.ui.components.ListSection>): Boolean =
        routeJumpToPrevSection(this, sections)

    fun requestPurgePlatform(platformId: Long) = storageDelegate.requestPurgePlatform(platformId)
    fun confirmPurgePlatform() = storageDelegate.confirmPurgePlatform(viewModelScope)
    fun cancelPurgePlatform() = storageDelegate.cancelPurgePlatform()
    fun requestPurgeAll() = storageDelegate.requestPurgeAll(viewModelScope)
    fun confirmPurgeAll() = storageDelegate.confirmPurgeAll(viewModelScope)
    fun cancelPurgeAll() = storageDelegate.cancelPurgeAll()
    fun requestHardReset() = storageDelegate.requestHardReset(viewModelScope)
    fun cancelHardReset() = storageDelegate.cancelHardReset()
    fun hardResetHoldStarted() = hapticManager.vibrate(HapticPattern.SELECTION)
    fun confirmHardReset() {
        hapticManager.vibrate(HapticPattern.ERROR)
        storageDelegate.confirmHardReset(viewModelScope)
    }
    fun confirmPlatformMigration() = storageDelegate.confirmPlatformMigration(viewModelScope)
    fun cancelPlatformMigration() = storageDelegate.cancelPlatformMigration()
    fun skipPlatformMigration() = storageDelegate.skipPlatformMigration(viewModelScope)
    fun openPlatformSettingsModal(platformId: Long) = storageDelegate.openPlatformSettingsModal(platformId)
    fun closePlatformSettingsModal() = storageDelegate.closePlatformSettingsModal()
    fun movePlatformSettingsFocus(delta: Int) = storageDelegate.movePlatformSettingsFocus(delta)
    fun movePlatformSettingsButtonFocus(delta: Int) = storageDelegate.movePlatformSettingsButtonFocus(delta)
    fun selectPlatformSettingsOption() = storageDelegate.selectPlatformSettingsOption(viewModelScope)

    fun openLogFolderPicker() = routeOpenLogFolderPicker(this)
    fun setFileLoggingPath(path: String) = routeSetFileLoggingPath(this, path)
    fun toggleFileLogging(enabled: Boolean) = routeToggleFileLogging(this, enabled)
    fun setFileLogLevel(level: LogLevel) = routeSetFileLogLevel(this, level)
    fun cycleFileLogLevel(direction: Int = 1) = routeCycleFileLogLevel(this, direction)
    fun setSaveDebugLoggingEnabled(enabled: Boolean) = routeSetSaveDebugLoggingEnabled(this, enabled)

    fun setPlatformEmulator(platformId: Long, platformSlug: String, emulator: InstalledEmulator?) =
        routeSetPlatformEmulator(this, platformId, platformSlug, emulator)

    fun setRomStoragePath(path: String) = storageDelegate.setRomStoragePath(viewModelScope, path)

    fun syncRomm() = routeSyncRomm(this)

    fun checkForUpdates() = routeCheckForUpdates(this)
    fun downloadAndInstallUpdate(context: android.content.Context) = routeDownloadAndInstallUpdate(this, context)
    fun moveUpdateActionFocus(delta: Int) = routeMoveUpdateActionFocus(this, delta)
    fun openChangelog() = routeOpenChangelog(this)
    fun closeChangelog() = routeCloseChangelog(this)
    fun loadChangelogPage() = routeLoadChangelogPage(this)

    fun exportSettings() = routeExportSettings(this)
    fun requestImportSettings() = routeRequestImportSettings(this)
    fun confirmImportSettings() = routeConfirmImportSettings(this)
    fun cancelImportSettings() = routeCancelImportSettings(this)
    fun importSettingsFrom(path: String) = routeImportSettingsFrom(this, path)

    fun writeSystemizeScript() {
        _uiState.update { it.copy(systemizeResult = com.nendo.argosy.util.SystemizeScript.write(context)) }
    }

    /**
     * Relaunches the app through a fresh task so the process is replaced rather than resumed. As a
     * home launcher Argosy is otherwise awkward to restart by hand.
     */
    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }

    fun dismissSystemizeDialog() {
        _uiState.update { it.copy(systemizeResult = null) }
    }

    fun startRommConfig() = routeStartRommConfig(this)
    fun cancelRommConfig() = routeCancelRommConfig(this)

    fun setRommConfigUrl(url: String) = serverDelegate.setRommConfigUrl(url)
    fun commitRommUrl() = serverDelegate.commitRommUrl(viewModelScope)
    fun setRommConfigPairingCode(code: String) = serverDelegate.setRommConfigPairingCode(code)
    fun setRommAuthMethod(method: RomMAuthMethod) = serverDelegate.setRommAuthMethod(method)
    fun requestCertificatePicker() {
        viewModelScope.launch { _openCertificatePickerEvent.emit(Unit) }
    }

    fun importCertificate(path: String) = serverDelegate.importCertificate(viewModelScope, path)

    fun showRommScanner() = serverDelegate.showScanner()
    fun dismissRommScanner() = serverDelegate.dismissScanner()
    fun handleRommScanResult(origin: String, code: String) = serverDelegate.handleScanResult(origin, code, viewModelScope) { loadSettings() }
    fun clearRommFocusField() = serverDelegate.clearRommFocusField()

    fun requestRommSignOut() =
        serverDelegate.requestRommSignOut(viewModelScope) { syncDelegate.pendingUploadCount() }
    fun cancelRommSignOut() = serverDelegate.cancelRommSignOut()
    fun confirmRommSignOut() = serverDelegate.confirmRommSignOut(viewModelScope) { loadSettings() }

    fun connectToRomm() = routeConnectToRomm(this)
    fun showRALoginForm() = routeShowRALoginForm(this)
    fun hideRALoginForm() = routeHideRALoginForm(this)

    fun setRALoginUsername(username: String) = raDelegate.setLoginUsername(username)
    fun setRALoginPassword(password: String) = raDelegate.setLoginPassword(password)
    fun clearRAFocusField() = raDelegate.clearFocusField()

    fun loginToRA() = routeLoginToRA(this)
    fun logoutFromRA() = routeLogoutFromRA(this)
    fun pushRACredentialsToRetroArch() = raDelegate.pushToRetroArch(viewModelScope) { count ->
        when {
            count > 0 -> notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_pushed_to_retroarch_title),
                subtitle = NotificationText.Plural(
                    R.plurals.settings_shell_vm_retroarch_configs_updated, count, listOf(count)
                ),
                type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
            count == 0 -> notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_retroarch_not_found_title),
                subtitle = NotificationText.Res(R.string.settings_shell_vm_retroarch_not_found_subtitle),
                type = com.nendo.argosy.core.notification.NotificationType.ERROR,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
            else -> notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_vm_not_signed_in_title),
                subtitle = NotificationText.Res(R.string.settings_shell_vm_not_signed_in_subtitle),
                type = com.nendo.argosy.core.notification.NotificationType.ERROR,
                duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
            )
        }
    }
    fun setRAProxyEnabled(enabled: Boolean) = routeSetRAProxyEnabled(this, enabled)
    fun setRAProxyAddress(address: String) = raDelegate.setProxyAddress(viewModelScope, address)

    fun handleConfirm(): InputResult = routeConfirm(this)

    fun downloadAllBios() = biosDelegate.downloadAllBios(viewModelScope)
    fun distributeAllBios() = biosDelegate.distributeAllBios(viewModelScope)

    fun openBiosFolderPicker() = biosDelegate.openFolderPicker(viewModelScope)
    fun toggleBiosPlatformExpanded(index: Int) = biosDelegate.togglePlatformExpanded(index)
    fun downloadBiosForPlatform(platformSlug: String) =
        biosDelegate.downloadBiosForPlatform(platformSlug, viewModelScope) {
            loadPlatformDetailStats(_uiState.value.platformDetail.platformIndex)
        }
    fun downloadSingleBios(rommId: Long) = biosDelegate.downloadSingleBios(rommId, viewModelScope)
    fun onBiosFolderSelected(path: String) = biosDelegate.onBiosFolderSelected(path, viewModelScope)

    private var pendingBiosCopyPlatformSlug: String? = null
    val hasPendingBiosCopy: Boolean get() = pendingBiosCopyPlatformSlug != null

    internal var shaderChainPersistJob: kotlinx.coroutines.Job? = null

    fun requestRemoveLocalFiles() {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = true)) }
    }

    fun dismissRemoveConfirm() {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = false)) }
    }

    fun confirmRemoveLocalFiles(platformId: Long) {
        _uiState.update { it.copy(platformDetail = it.platformDetail.copy(showRemoveConfirm = false)) }
        val platformIndex = _uiState.value.platformDetail.platformIndex
        val config = _uiState.value.emulators.platforms.getOrNull(platformIndex) ?: return
        viewModelScope.launch {
            val deleted = gameRepository.deleteLocalFilesForPlatform(platformId)
            if (deleted > 0) {
                notificationManager.show(
                    title = NotificationText.Res(R.string.settings_shell_vm_files_removed_title),
                    subtitle = NotificationText.Plural(
                        R.plurals.settings_shell_vm_files_removed_subtitle,
                        deleted,
                        listOf(deleted, config.platform.name)
                    ),
                    type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                    duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
                )
            }
            loadPlatformDetailStats(platformIndex)
        }
    }

    fun resetPlatformRomPath(platformId: Long) =
        storageDelegate.resetPlatformToGlobal(viewModelScope, platformId)

    fun launchSavePathPicker(platformId: Long) {
        storageDelegate.emitSavePathPicker(viewModelScope, platformId)
    }

    fun launchStatePathPicker(platformId: Long) {
        storageDelegate.emitStatePathPicker(viewModelScope, platformId)
    }

    fun launchBiosCopyPicker(platformSlug: String) {
        pendingBiosCopyPlatformSlug = platformSlug
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun onBiosCopyFolderSelected(path: String) {
        val slug = pendingBiosCopyPlatformSlug ?: return
        pendingBiosCopyPlatformSlug = null
        viewModelScope.launch {
            val copied = biosRepository.copyBiosForPlatformTo(slug, path)
            if (copied > 0) {
                notificationManager.show(
                    title = NotificationText.Res(R.string.settings_shell_vm_bios_copied_title),
                    subtitle = NotificationText.Plural(
                        R.plurals.settings_shell_vm_bios_copied_subtitle, copied, listOf(copied)
                    ),
                    type = com.nendo.argosy.core.notification.NotificationType.SUCCESS,
                    duration = com.nendo.argosy.core.notification.NotificationDuration.MEDIUM
                )
            }
        }
    }

    fun resetBiosToDefault() = biosDelegate.resetBiosToDefault(viewModelScope)
    fun moveBiosActionFocus(delta: Int) = biosDelegate.moveActionFocus(delta)

    fun moveBiosPathActionFocus(delta: Int): Boolean = routeMoveBiosPathActionFocus(this, delta)

    fun resetBiosPathActionFocus() = biosDelegate.resetBiosPathActionFocus()

    fun moveBiosPlatformSubFocus(delta: Int): Boolean = routeMoveBiosPlatformSubFocus(this, delta)

    fun resetBiosPlatformSubFocus() = biosDelegate.resetPlatformSubFocus()
    fun dismissDistributeResultModal() = biosDelegate.dismissDistributeResultModal()

    fun dismissDownloadFailureModal() = biosDelegate.dismissDownloadFailureModal()
    fun installGpuDriver() = biosDelegate.installGpuDriver(viewModelScope)
    fun openGpuDriverFilePicker() = biosDelegate.openGpuDriverFilePicker(viewModelScope)
    fun installGpuDriverFromFile(filePath: String) = biosDelegate.installGpuDriverFromFile(filePath, viewModelScope)
    fun dismissGpuDriverPrompt() = biosDelegate.dismissGpuDriverPrompt()
    fun moveGpuDriverPromptFocus(delta: Int) = biosDelegate.moveGpuDriverPromptFocus(delta)

    override fun onCleared() {
        super.onCleared()
        shaderChainManager.destroy()
    }

    fun createInputHandler(onBack: () -> Unit): InputHandler =
        SettingsInputHandler(this, onBack)

    // --- Social ---

    private fun observeSocialConnectionState() {
        socialRepository.connectionState.onEach { state ->
            when (state) {
                is SocialConnectionState.Disconnected -> {
                    val prefs = preferencesRepository.userPreferences.first()
                    if (prefs.isSocialLinked) {
                        _uiState.update { it.copy(social = it.social.copy(
                            authStatus = SocialAuthStatus.CONNECTING
                        )) }
                    } else {
                        _uiState.update { it.copy(social = SocialState(
                            authStatus = SocialAuthStatus.NOT_LINKED
                        )) }
                    }
                }
                is SocialConnectionState.Connecting -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.CONNECTING
                    )) }
                }
                is SocialConnectionState.Connected -> {
                    val prefs = preferencesRepository.userPreferences.first()
                    _uiState.update { it.copy(social = SocialState(
                        authStatus = SocialAuthStatus.CONNECTED,
                        username = state.user.username,
                        displayName = state.user.displayName,
                        avatarColor = state.user.avatarColor,
                        avatarDoodle = prefs.socialAvatarDoodle,
                        avatarUseDoodle = prefs.socialAvatarUseDoodle,
                        onlineStatusEnabled = prefs.socialOnlineStatusEnabled,
                        showNowPlaying = prefs.socialShowNowPlaying,
                        notifyFriendOnline = prefs.socialNotifyFriendOnline,
                        notifyFriendPlaying = prefs.socialNotifyFriendPlaying,
                        suppressNotificationsInGame = prefs.socialSuppressNotificationsInGame,
                        discordLinked = socialRepository.discordLinked.value,
                        discordUsername = socialRepository.discordUsername.value,
                        discordRichPresenceEnabled = prefs.discordRichPresenceEnabled,
                        discordPresenceState = discordPresenceManager.state.value,
                        quayPassEnabled = prefs.quayPassEnabled
                    )) }
                }
                is SocialConnectionState.AwaitingAuth -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.AWAITING_AUTH,
                        qrUrl = state.qrUrl,
                        loginCode = state.loginCode
                    )) }
                }
                is SocialConnectionState.Failed -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.ERROR,
                        errorMessage = state.reason
                    )) }
                }
            }
        }.launchIn(viewModelScope)

        socialRepository.discordLinked.onEach { linked ->
            _uiState.update { it.copy(social = it.social.copy(
                discordLinked = linked,
                discordUsername = socialRepository.discordUsername.value
            )) }
        }.launchIn(viewModelScope)

        discordPresenceManager.state.onEach { presenceState ->
            _uiState.update { it.copy(social = it.social.copy(
                discordPresenceState = presenceState
            )) }
        }.launchIn(viewModelScope)

        preferencesRepository.userPreferences
            .map { it.quayPassEnabled }
            .distinctUntilChanged()
            .onEach { enabled ->
                _uiState.update { it.copy(social = it.social.copy(
                    quayPassEnabled = enabled
                )) }
            }
            .launchIn(viewModelScope)
    }

    internal fun handleSocialConfirm(state: SettingsUiState): InputResult {
        return when (state.social.authStatus) {
            SocialAuthStatus.NOT_LINKED -> {
                startSocialAuth()
                InputResult.HANDLED
            }
            SocialAuthStatus.AWAITING_AUTH -> {
                cancelSocialAuth()
                InputResult.HANDLED
            }
            SocialAuthStatus.CONNECTED -> {
                val layoutState = com.nendo.argosy.ui.screens.settings.sections.SocialLayoutState(
                    isConnected = true,
                    hasAvatarDoodle = state.social.avatarDoodle != null
                )
                when (com.nendo.argosy.ui.screens.settings.sections.socialItemAtFocusIndex(state.focusedIndex, layoutState)) {
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.EditAvatar -> {
                        openAvatarEditor()
                        InputResult.HANDLED
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.UseDoodleAvatar -> {
                        setSocialAvatarUseDoodle(!state.social.avatarUseDoodle)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.OnlineStatus -> {
                        setSocialOnlineStatus(!state.social.onlineStatusEnabled)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.ShowNowPlaying -> {
                        if (state.social.onlineStatusEnabled) setSocialShowNowPlaying(!state.social.showNowPlaying)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.NotifyFriendOnline -> {
                        if (state.social.onlineStatusEnabled) setSocialNotifyFriendOnline(!state.social.notifyFriendOnline)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.NotifyFriendPlaying -> {
                        if (state.social.onlineStatusEnabled) setSocialNotifyFriendPlaying(!state.social.notifyFriendPlaying)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.SuppressInGame -> {
                        if (state.social.onlineStatusEnabled) setSocialSuppressNotificationsInGame(!state.social.suppressNotificationsInGame)
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.QuayPassEnabled -> {
                        if (state.social.quayPassEnabled) setQuayPassEnabled(false)
                        else requestEnableQuayPass()
                        InputResult.handled(SoundType.TOGGLE)
                    }
                    is com.nendo.argosy.ui.screens.settings.sections.SocialItem.Unlink -> {
                        logoutSocial()
                        InputResult.HANDLED
                    }
                    else -> InputResult.HANDLED
                }
            }
            else -> InputResult.UNHANDLED
        }
    }

    fun startSocialAuth() {
        viewModelScope.launch {
            _uiState.update { it.copy(social = it.social.copy(
                authStatus = SocialAuthStatus.CONNECTING
            )) }

            val result = socialRepository.startAuth()

            when (result) {
                is SocialAuthManager.AuthResult.Success -> {
                    _uiState.update { it.copy(social = SocialState(
                        authStatus = SocialAuthStatus.CONNECTED,
                        username = result.user.username,
                        displayName = result.user.displayName,
                        avatarColor = result.user.avatarColor,
                        onlineStatusEnabled = true,
                        showNowPlaying = true,
                        discordRichPresenceEnabled = true
                    )) }
                }
                is SocialAuthManager.AuthResult.Error -> {
                    _uiState.update { it.copy(social = it.social.copy(
                        authStatus = SocialAuthStatus.ERROR,
                        errorMessage = result.message
                    )) }
                }
            }
        }

        viewModelScope.launch {
            socialRepository.authState.collect { state ->
                when (state) {
                    is SocialAuthManager.AuthState.AwaitingLogin -> {
                        _uiState.update { it.copy(social = it.social.copy(
                            authStatus = SocialAuthStatus.AWAITING_AUTH,
                            qrUrl = state.qrUrl,
                            loginCode = state.loginCode
                        )) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelSocialAuth() {
        socialRepository.cancelAuth()
        _uiState.update { it.copy(social = SocialState(
            authStatus = SocialAuthStatus.NOT_LINKED
        )) }
    }

    fun logoutSocial() {
        viewModelScope.launch {
            socialRepository.logout()
            _uiState.update { it.copy(social = SocialState(
                authStatus = SocialAuthStatus.NOT_LINKED
            )) }
        }
    }

    private fun observeAvatarPreferences() {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _uiState.update {
                    if (it.social.avatarDoodle == prefs.socialAvatarDoodle &&
                        it.social.avatarUseDoodle == prefs.socialAvatarUseDoodle
                    ) it
                    else it.copy(social = it.social.copy(
                        avatarDoodle = prefs.socialAvatarDoodle,
                        avatarUseDoodle = prefs.socialAvatarUseDoodle
                    ))
                }
            }
        }
    }

    fun setSocialAvatarUseDoodle(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialAvatarUseDoodle(enabled)
            _uiState.update { it.copy(social = it.social.copy(avatarUseDoodle = enabled)) }
        }
    }

    fun openAvatarEditor() {
        viewModelScope.launch { _avatarEditorEvent.emit(Unit) }
    }

    fun setSocialOnlineStatus(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialOnlineStatusEnabled(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                onlineStatusEnabled = enabled
            )) }
        }
    }

    fun startJellyfinConfig() = jellyfinDelegate.startServerConfig(::resetFocusIndex)
    fun setJellyfinConfigUrl(url: String) = jellyfinDelegate.setConfigUrl(url)
    fun setJellyfinConfigFocusField(field: Int?) = jellyfinDelegate.setConfigFocusField(field)
    fun clearJellyfinConfigFocusField() = jellyfinDelegate.clearConfigFocusField()
    fun commitJellyfinConfig() = jellyfinDelegate.commitServerConfig(
        viewModelScope,
        ::resetFocusIndex,
        ::refreshJellyfinConnection
    )
    fun cancelJellyfinConfig() = jellyfinDelegate.cancelServerConfig(::resetFocusIndex)

    private fun resetFocusIndex() {
        _uiState.update { it.copy(focusedIndex = 0) }
    }

    /**
     * Reconnects to the stored server so the section knows what it is dealing with before the user
     * asks to sign in - which sign-in paths the server offers is a live answer, not an assumption.
     */
    internal fun refreshJellyfinConnection() {
        viewModelScope.launch { jellyfinConnectionManager.initialize() }
    }

    fun requestJellyfinSignIn() = jellyfinDelegate.requestSignIn(viewModelScope, ::resetFocusIndex)
    fun requestJellyfinSignOut() = jellyfinDelegate.requestSignOut()
    fun cancelJellyfinSignOut() = jellyfinDelegate.cancelSignOut()
    fun confirmJellyfinSignOut() {
        cancelJellyfinSignIn()
        jellyfinDelegate.confirmSignOut(viewModelScope) { jellyfinConnectionManager.signOut() }
    }

    fun showJellyfinLoginForm() = jellyfinDelegate.showLoginForm(::resetFocusIndex)
    fun hideJellyfinLoginForm() = jellyfinDelegate.hideLoginForm(::resetFocusIndex)
    fun setJellyfinLoginUsername(username: String) = jellyfinDelegate.setLoginUsername(username)
    fun setJellyfinLoginPassword(password: String) = jellyfinDelegate.setLoginPassword(password)
    fun setJellyfinLoginFocusField(field: Int?) = jellyfinDelegate.setLoginFocusField(field)
    fun clearJellyfinLoginFocusField() = jellyfinDelegate.clearLoginFocusField()
    fun submitJellyfinPasswordSignIn() = jellyfinDelegate.submitPasswordSignIn(viewModelScope)

    /**
     * Runs the Quick Connect exchange the section asked for and reports each step back to the
     * delegate. The connection layer persists the credentials it wins through
     * [com.nendo.argosy.data.preferences.UserPreferencesRepository.setJellyfinCredentials], so this
     * carries presentation only.
     */
    internal fun startJellyfinQuickConnect(serverUrl: String) {
        jellyfinSignInJob?.cancel()
        jellyfinSignInJob = viewModelScope.launch {
            jellyfinConnectionManager.signInWithQuickConnect(serverUrl, jellyfinSignInCallbacks())
        }
    }

    internal fun startJellyfinPasswordSignIn(request: JellyfinPasswordSignInRequest) {
        jellyfinSignInJob?.cancel()
        jellyfinSignInJob = viewModelScope.launch {
            jellyfinConnectionManager.signInWithPassword(
                request.serverUrl,
                request.username,
                request.password,
                jellyfinSignInCallbacks()
            )
        }
    }

    fun cancelJellyfinSignIn() {
        jellyfinSignInJob?.cancel()
        jellyfinSignInJob = null
        jellyfinConnectionManager.clearQuickConnectState()
        jellyfinDelegate.cancelSignIn()
    }

    private fun jellyfinSignInCallbacks() = JellyfinSignInCallbacks(
        onCodeIssued = { code -> jellyfinDelegate.onQuickConnectStarted(code) },
        onSignedIn = { userName -> jellyfinDelegate.onSignedIn(userName) },
        onFailed = { reason -> jellyfinDelegate.onSignInFailed(reason, ::resetFocusIndex) }
    )

    fun cycleJellyfinDownloadQuality(direction: Int) =
        jellyfinDelegate.cycleDownloadQuality(viewModelScope, direction)
    fun setJellyfinDownloadQuality(quality: com.nendo.argosy.data.preferences.MediaDownloadQuality) =
        jellyfinDelegate.setDownloadQuality(viewModelScope, quality)
    fun cycleJellyfinStreamingQuality(direction: Int) =
        jellyfinDelegate.cycleStreamingQuality(viewModelScope, direction)
    fun setJellyfinStreamingQuality(quality: com.nendo.argosy.data.preferences.MediaStreamingQuality) =
        jellyfinDelegate.setStreamingQuality(viewModelScope, quality)
    fun cycleJellyfinAudioLanguage(direction: Int) =
        jellyfinDelegate.cycleAudioLanguage(viewModelScope, direction)
    fun setJellyfinAudioLanguage(language: com.nendo.argosy.data.preferences.MediaAudioLanguage) =
        jellyfinDelegate.setAudioLanguage(viewModelScope, language)
    fun cycleJellyfinSubtitleMode(direction: Int) =
        jellyfinDelegate.cycleSubtitleMode(viewModelScope, direction)
    fun setJellyfinSubtitleMode(mode: com.nendo.argosy.data.preferences.MediaSubtitleMode) =
        jellyfinDelegate.setSubtitleMode(viewModelScope, mode)
    fun cycleJellyfinSubtitleLanguage(direction: Int) =
        jellyfinDelegate.cycleSubtitleLanguage(viewModelScope, direction)
    fun setJellyfinSubtitleLanguage(language: com.nendo.argosy.data.preferences.MediaSubtitleLanguage) =
        jellyfinDelegate.setSubtitleLanguage(viewModelScope, language)
    fun setJellyfinBurnInImageSubtitles(enabled: Boolean) =
        jellyfinDelegate.setBurnInImageSubtitles(viewModelScope, enabled)
    fun setJellyfinConfirmPlayerExit(enabled: Boolean) =
        jellyfinDelegate.setConfirmPlayerExit(viewModelScope, enabled)
    fun setJellyfinSharePresence(enabled: Boolean) =
        jellyfinDelegate.setSharePresence(viewModelScope, enabled)

    val mediaSyncProgress get() = jellyfinDelegate.librarySyncProgress
    fun syncJellyfinLibrary() = jellyfinDelegate.syncLibrary(viewModelScope)

    fun openMediaLocationPicker() = jellyfinDelegate.openMediaLocationPicker(viewModelScope)
    fun onMediaLocationSelected(path: String) =
        jellyfinDelegate.onMediaLocationSelected(viewModelScope, path)
    fun confirmMediaRelocation() = jellyfinDelegate.confirmMediaRelocation(viewModelScope)
    fun skipMediaRelocation() = jellyfinDelegate.skipMediaRelocation(viewModelScope)
    fun cancelMediaRelocation() = jellyfinDelegate.cancelMediaRelocation()

    fun setSocialShowNowPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialShowNowPlaying(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                showNowPlaying = enabled
            )) }
        }
    }

    fun setSocialNotifyFriendOnline(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendOnline(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                notifyFriendOnline = enabled
            )) }
        }
    }

    fun setSocialNotifyFriendPlaying(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialNotifyFriendPlaying(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                notifyFriendPlaying = enabled
            )) }
        }
    }

    fun setSocialSuppressNotificationsInGame(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSocialSuppressNotificationsInGame(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                suppressNotificationsInGame = enabled
            )) }
        }
    }

    fun setQuayPassEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setQuayPassEnabled(enabled)
        }
    }

    /**
     * Begins enabling QuayPass: requests BLE runtime permissions first (the
     * service cannot scan/advertise without them on Android 12+).
     */
    fun requestEnableQuayPass() {
        viewModelScope.launch { _requestBlePermissionEvent.emit(Unit) }
    }

    fun onBlePermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch { preferencesRepository.setQuayPassEnabled(true) }
        }
    }

    fun setDiscordRichPresence(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDiscordRichPresenceEnabled(enabled)
            _uiState.update { it.copy(social = it.social.copy(
                discordRichPresenceEnabled = enabled
            )) }
        }
    }
}
