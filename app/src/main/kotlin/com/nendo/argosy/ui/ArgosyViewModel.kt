package com.nendo.argosy.ui

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.annotation.StringRes
import com.nendo.argosy.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.DownloadQueueState
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.netplay.NetplayPreflightChecker
import com.nendo.argosy.data.netplay.NetplayPreflightResult
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.NetplayInvitePayload
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.domain.usecase.game.LaunchGameUseCase
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.EmulatorUpdateManager
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.screens.common.GameLaunchRequest
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.DisplayRefreshController
import com.nendo.argosy.hardware.DevicePerformanceResolver
import com.nendo.argosy.hardware.VolumeController
import com.nendo.argosy.ui.components.QuickSettingsController
import com.nendo.argosy.ui.components.friends.QuickFriendsController
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.ui.input.InputDispatcher.Companion.computeWrappedIndex
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.buttonGlyphSwaps
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.core.notification.DownloadNotificationObserver
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.SyncNotificationObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RECONNECT_SETTLE_MS = 5_000L

data class ArgosyUiState(
    val isFirstRun: Boolean = true,
    val isLoading: Boolean = true,
    @StringRes val startupStatusRes: Int? = null,
    val showStatusClock: Boolean = true,
    val showStatusBattery: Boolean = true,
    val showStatusNetwork: Boolean = false,
    val abIconsSwapped: Boolean = false,
    val xyIconsSwapped: Boolean = false,
    val swapStartSelect: Boolean = false,
    val menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP
)

/**
 * Focus index of the drawer's account row, which sits above the [DrawerItem] list and is not one
 * of them. [DRAWER_NAV_ITEM_OFFSET] converts between a drawerItems index and a drawer focus index.
 */
internal const val DRAWER_ACCOUNT_ROW_INDEX = 0
internal const val DRAWER_NAV_ITEM_OFFSET = 1
internal const val ACCOUNTS_SECTION_NAME = "ACCOUNTS"

data class DrawerState(
    val rommConnected: Boolean = false,
    val rommConnecting: Boolean = false,
    val localUser: SocialUser? = null,
    val localAvatarDoodle: String? = null,
    val rommUsername: String? = null,
    val rommAvatarUrl: String? = null,
    val downloadCount: Int = 0,
    val saveSyncAttentionCount: Int = 0,
    val pendingUpdateCount: Int = 0,
    val navFocusIndex: Int = 0
) {
    fun badgeCountFor(route: String): Int? = when (route) {
        Screen.Downloads.route -> downloadCount
        Screen.SaveSync.route -> saveSyncAttentionCount
        Screen.Settings.route -> pendingUpdateCount
        else -> 0
    }.takeIf { it > 0 }
}

data class ScreenDimmerPreferences(
    val enabled: Boolean = true,
    val timeoutMinutes: Int = 2,
    val level: Int = 30
)

fun UserPreferences.toScreenDimmerPreferences() = ScreenDimmerPreferences(
    enabled = screenDimmerEnabled,
    timeoutMinutes = screenDimmerTimeoutMinutes,
    level = screenDimmerLevel
)

data class DrawerItem(
    val route: String,
    @StringRes val labelRes: Int
)

data class NavRingState(
    val destinations: List<DrawerItem> = emptyList(),
    val pages: List<DrawerItem> = emptyList(),
    val homeAppBarConfigured: Boolean = false,
    val isBarVisible: Boolean = false
)

private const val NAV_BAR_AUTO_HIDE_MS = 5_000L

private fun isNavRouteAvailable(route: String, socialConnected: Boolean, mediaSignedIn: Boolean): Boolean =
    when (route) {
        Screen.Social.route -> socialConnected
        Screen.MediaLibrary.route -> mediaSignedIn
        else -> true
    }

@HiltViewModel
class ArgosyViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val quickNavigationSource: com.nendo.argosy.data.preferences.QuickNavigationSource,
    val gamepadInputHandler: GamepadInputHandler,
    val hapticManager: HapticFeedbackManager,
    val soundManager: SoundFeedbackManager,
    val imageCacheManager: com.nendo.argosy.data.cache.ImageCacheManager,
    val notificationManager: NotificationManager,
    downloadNotificationObserver: DownloadNotificationObserver,
    syncNotificationObserver: SyncNotificationObserver,
    private val gameRepository: GameRepository,
    private val romMRepository: RomMRepository,
    private val downloadManager: DownloadManager,
    private val modalResetSignal: ModalResetSignal,
    private val playSessionTracker: PlaySessionTracker,
    private val launcherStartup: com.nendo.argosy.ui.startup.LauncherStartupCoordinator,
    sessionEndCoordinator: com.nendo.argosy.ui.screens.common.SessionEndCoordinator,
    private val emulatorUpdateManager: EmulatorUpdateManager,
    private val coreVersionRepository: com.nendo.argosy.data.repository.CoreVersionRepository,
    private val syncCoordinator: com.nendo.argosy.data.sync.SyncCoordinator,
    private val syncConflictNotifier: com.nendo.argosy.data.sync.SyncConflictNotifier,
    private val socialSyncCoordinator: com.nendo.argosy.data.sync.SocialSyncCoordinator,
    private val brightnessController: BrightnessController,
    private val volumeController: VolumeController,
    private val performanceResolver: DevicePerformanceResolver,
    private val displayRefreshController: DisplayRefreshController,
    private val platformSyncQueue: com.nendo.argosy.data.sync.PlatformSyncQueue,
    private val socialRepository: SocialRepository,
    private val steamContentManager: com.nendo.argosy.data.steam.SteamContentManager,
    private val mediaDownloadManager: com.nendo.argosy.data.download.MediaDownloadManager,
    val steamDownloadPromptController: com.nendo.argosy.data.steam.SteamDownloadPromptController,
    val coreCrashController: com.nendo.argosy.libretro.CoreCrashController,
    private val netplayPreflightChecker: NetplayPreflightChecker,
    private val netplayJoinService: com.nendo.argosy.data.netplay.NetplayJoinService,
    private val launchGameUseCase: LaunchGameUseCase,
    private val pendingConflictDao: com.nendo.argosy.data.local.dao.PendingConflictDao,
    val saveConflicts: com.nendo.argosy.ui.conflict.SaveConflictPrompts,
    private val deepLinkLaunchCoordinator: com.nendo.argosy.ui.deeplink.DeepLinkLaunchCoordinator,
    private val emulatorLaunchTargetResolver:
        com.nendo.argosy.ui.screens.common.EmulatorLaunchTargetResolver
) : ViewModel() {

    suspend fun resolveDeepLinkLaunch(
        request: com.nendo.argosy.domain.model.DeepLinkRequest
    ): com.nendo.argosy.ui.deeplink.DeepLinkLaunch = deepLinkLaunchCoordinator.resolve(request)

    suspend fun awaitDeepLinkSyncReady() = deepLinkLaunchCoordinator.awaitConnectionIfSyncing()

    val netplayJoinState: StateFlow<com.nendo.argosy.data.netplay.NetplayJoinState> get() = netplayJoinService.state

    fun cancelNetplayJoin() = netplayJoinService.cancel()
    fun resetNetplayJoin() = netplayJoinService.reset()
    fun netplayJoinService(): com.nendo.argosy.data.netplay.NetplayJoinService = netplayJoinService

    private val contentResolver get() = application.contentResolver

    val sessionEndOverlay: StateFlow<com.nendo.argosy.ui.screens.common.SyncOverlayState?> =
        sessionEndCoordinator.syncOverlayState

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            quickSettings.refreshDisplayLevels()
        }
    }

    private val _detectedLayout = MutableStateFlow(ControllerDetector.detectFromActiveGamepad().layout)

    private val inputManager = application.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshControllerDetection()
        override fun onInputDeviceChanged(deviceId: Int) = refreshControllerDetection()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshControllerDetection()
    }


    init {
        downloadNotificationObserver.observe(viewModelScope)
        syncNotificationObserver.observe(viewModelScope)
        launcherStartup.start()
        observeFeedbackSettings(preferencesRepository)
        downloadManager.clearCompleted()
        initControllerDetection()
        saveConflicts.start(viewModelScope)
        observeConnectionForSync()
        observeSocialConnectionForSync()
        observeNetplayInvites()
        registerSettingsObserver()
    }

    override fun onCleared() {
        super.onCleared()
        application.contentResolver.unregisterContentObserver(settingsObserver)
        inputManager.unregisterInputDeviceListener(inputDeviceListener)
    }

    private fun registerSettingsObserver() {
        application.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            settingsObserver
        )
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeConnectionForSync() {
        syncConflictNotifier.start()
        viewModelScope.launch {
            romMRepository.connectionState
                .map { it is ConnectionState.Connected }
                .distinctUntilChanged()
                .debounce(RECONNECT_SETTLE_MS)
                .filter { it }
                .collect { syncCoordinator.reconcileAfterReconnect() }
        }
    }

    private fun observeSocialConnectionForSync() {
        viewModelScope.launch {
            var wasConnected = false
            socialRepository.connectionState.collect { state ->
                val isConnected = state is SocialConnectionState.Connected
                if (isConnected && !wasConnected) {
                    socialSyncCoordinator.processQueue()
                }
                wasConnected = isConnected
            }
        }
    }

    private fun initControllerDetection() {
        // One-time detection at startup
        refreshControllerDetection()
        // Register listener for device changes (connect/disconnect/mode change)
        inputManager.registerInputDeviceListener(inputDeviceListener, null)
    }

    fun refreshControllerDetection() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = ControllerDetector.detectFromActiveGamepad()
            if (_detectedLayout.value != result.layout) {
                Log.d("ArgosyVM", "Layout changed: ${_detectedLayout.value} -> ${result.layout} (${result.source})")
                _detectedLayout.value = result.layout
            }
        }
    }

    fun triggerPostWizardSync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            romMRepository.initialize()
            if (romMRepository.isConnected()) {
                platformSyncQueue.enqueueLibrary(initializeFirst = false)
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun ownedConflictCount() = preferencesRepository.userPreferences
        .map { it.rommUserId }
        .distinctUntilChanged()
        .flatMapLatest {
            pendingConflictDao.getOpenCountFlow(
                com.nendo.argosy.data.local.entity.PendingConflictEntity.ownerScope(it)
            )
        }

    private fun observeFeedbackSettings(preferencesRepository: UserPreferencesRepository) {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                hapticManager.setEnabled(prefs.hapticEnabled)
                hapticManager.setStrength(prefs.hapticStrength)
                soundManager.setEnabled(prefs.soundEnabled)
                soundManager.setVolume(prefs.soundVolume)
                soundManager.setSoundConfigs(prefs.soundConfigs)
                _isMediaSignedIn = prefs.isJellyfinSignedIn
            }
        }
    }

    val uiState: StateFlow<ArgosyUiState> = combine(
        preferencesRepository.userPreferences,
        _detectedLayout,
        launcherStartup.complete,
        launcherStartup.status
    ) { prefs, detectedLayout, startupDone, status ->
        val glyphSwaps = prefs.buttonGlyphSwaps(detectedLayout)
        val hasExistingConfig = prefs.rommBaseUrl != null || prefs.romStoragePath != null
        ArgosyUiState(
            isFirstRun = !prefs.firstRunComplete && !hasExistingConfig,
            isLoading = !startupDone,
            startupStatusRes = status,
            showStatusClock = prefs.showStatusClock,
            showStatusBattery = prefs.showStatusBattery,
            showStatusNetwork = prefs.showStatusNetwork,
            abIconsSwapped = glyphSwaps.ab,
            xyIconsSwapped = glyphSwaps.xy,
            swapStartSelect = glyphSwaps.startSelect,
            menuWrapMode = prefs.menuWrapMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ArgosyUiState()
    )

    private val _navFocusIndex = MutableStateFlow(0)

    val quickFriends = QuickFriendsController(socialRepository, preferencesRepository, viewModelScope)

    val quickSettings = QuickSettingsController(
        preferencesRepository = preferencesRepository,
        brightnessController = brightnessController,
        volumeController = volumeController,
        performanceResolver = performanceResolver,
        refreshController = displayRefreshController,
        socialRepository = socialRepository,
        hapticManager = hapticManager,
        soundManager = soundManager,
        quickFriends = quickFriends,
        wrapMode = { uiState.value.menuWrapMode },
        scope = viewModelScope
    )

    val drawerUiState: StateFlow<DrawerState> = combine(
        listOf(
            romMRepository.connectionState,
            downloadManager.state,
            combine(
                emulatorUpdateManager.assignedUpdateCount,
                coreVersionRepository.observeUpdateCount()
            ) { emulators, cores -> emulators + cores },
            _navFocusIndex,
            socialRepository.connectionState,
            steamContentManager.activeDownload,
            steamContentManager.downloadQueue,
            ownedConflictCount(),
            preferencesRepository.userPreferences,
            mediaDownloadManager.activeDownload,
            mediaDownloadManager.downloadQueue
        )
    ) { values ->
        val connection = values[0] as ConnectionState
        val downloads = values[1] as DownloadQueueState
        val pendingUpdateCount = values[2] as Int
        val navIndex = values[3] as Int
        val socialConnection = values[4] as SocialConnectionState
        val steamActiveDownload = values[5] as com.nendo.argosy.data.steam.SteamDownloadProgress?
        @Suppress("UNCHECKED_CAST")
        val steamQueue = values[6] as List<com.nendo.argosy.data.steam.QueuedSteamDownload>
        val saveSyncAttentionCount = values[7] as Int
        val userPrefs = values[8] as com.nendo.argosy.data.preferences.UserPreferences
        val mediaActiveDownload = values[9] as com.nendo.argosy.data.download.MediaDownloadProgress?
        @Suppress("UNCHECKED_CAST")
        val mediaQueue = values[10] as List<com.nendo.argosy.data.download.QueuedMediaDownload>

        val steamActive = steamActiveDownload != null
        val steamQueued = steamQueue.size
        val downloadCount = downloads.activeDownloads.size + downloads.queue.size +
            (if (steamActive) 1 else 0) + steamQueued +
            (if (mediaActiveDownload != null) 1 else 0) + mediaQueue.size
        DrawerState(
            rommConnected = connection is ConnectionState.Connected,
            rommConnecting = connection is ConnectionState.Connecting,
            localUser = (socialConnection as? SocialConnectionState.Connected)?.user,
            localAvatarDoodle = userPrefs.socialAvatarDoodle.takeIf { userPrefs.socialAvatarUseDoodle },
            rommUsername = userPrefs.rommUsername?.takeIf { it.isNotBlank() },
            rommAvatarUrl = rommAvatarUrl(userPrefs),
            downloadCount = downloadCount,
            saveSyncAttentionCount = saveSyncAttentionCount,
            pendingUpdateCount = pendingUpdateCount,
            navFocusIndex = navIndex
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrawerState()
    )

    private val allDrawerItems = NavRing.PAGES

    private var _isMediaSignedIn = false

    val drawerItems: List<DrawerItem>
        get() {
            val socialConnected = socialRepository.connectionState.value is SocialConnectionState.Connected
            return allDrawerItems.filter { isNavRouteAvailable(it.route, socialConnected, _isMediaSignedIn) }
        }

    private val _navBarVisible = MutableStateFlow(false)
    private var navBarHideJob: kotlinx.coroutines.Job? = null

    val navRingState: StateFlow<NavRingState> = combine(
        socialRepository.connectionState,
        preferencesRepository.userPreferences,
        quickNavigationSource.enabled,
        _navBarVisible
    ) { social, prefs, quickNavigation, barVisible ->
        val socialConnected = social is SocialConnectionState.Connected
        val pages = allDrawerItems.filter {
            isNavRouteAvailable(it.route, socialConnected, prefs.isJellyfinSignedIn)
        }
        val ring = if (quickNavigation) {
            NavRing.resolve(prefs.navRingRoutes).mapNotNull { token ->
                pages.firstOrNull { NavRing.token(it.route) == token }
            }
        } else {
            emptyList()
        }
        NavRingState(
            destinations = ring,
            pages = pages,
            homeAppBarConfigured = prefs.secondaryHomeApps.isNotEmpty(),
            isBarVisible = barVisible
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NavRingState()
    )

    fun isNavRingRoute(route: String?): Boolean =
        navRingState.value.destinations.any { NavRing.routeMatches(it.route, route) }

    fun navRingRouteFrom(currentRoute: String?, delta: Int): String? {
        val state = navRingState.value
        return NavRing.routeFrom(state.destinations, state.pages, currentRoute, delta)
    }

    fun showNavBar() {
        if (navRingState.value.destinations.isEmpty()) return
        navBarHideJob?.cancel()
        _navBarVisible.update { true }
        navBarHideJob = viewModelScope.launch {
            delay(NAV_BAR_AUTO_HIDE_MS)
            _navBarVisible.update { false }
        }
    }

    fun hideNavBar() {
        navBarHideJob?.cancel()
        navBarHideJob = null
        _navBarVisible.update { false }
    }

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    fun setDrawerOpen(open: Boolean) {
        _isDrawerOpen.value = open
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
    }

    fun resetAllModals() {
        _isDrawerOpen.value = false
        quickSettings.setOpen(false)
        modalResetSignal.emit()
    }

    /**
     * The drawer's navigation column carries one row that is not a [DrawerItem]: the account row
     * at index 0. Every nav item therefore sits at `drawerItems index + 1`, and this is the only
     * place that offset is defined.
     */
    private val drawerNavLastIndex: Int get() = drawerItems.size

    fun initDrawerFocus(currentRoute: String?, parentRoute: String? = null) {
        var index = drawerItems.indexOfFirst { NavRing.routeMatches(it.route, currentRoute) }
        if (index < 0 && parentRoute != null) {
            index = drawerItems.indexOfFirst { NavRing.routeMatches(it.route, parentRoute) }
        }
        if (index < 0) {
            index = drawerItems.indexOfFirst { it.route == Screen.Home.route }
        }
        _navFocusIndex.value = if (index >= 0) index + DRAWER_NAV_ITEM_OFFSET else 0
    }

    private fun moveWrappedFocus(
        focusFlow: MutableStateFlow<Int>,
        delta: Int,
        maxIndex: Int,
        wrapMode: MenuWrapMode
    ): InputResult {
        val next = computeWrappedIndex(focusFlow.value, delta, maxIndex, wrapMode)
        val moved = next != focusFlow.value
        focusFlow.value = next
        return if (moved) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
    }

    fun createDrawerInputHandler(
        onNavigate: (String) -> Unit,
        onDismiss: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            moveWrappedFocus(_navFocusIndex, -1, drawerNavLastIndex, uiState.value.menuWrapMode)

        override fun onDown(): InputResult =
            moveWrappedFocus(_navFocusIndex, 1, drawerNavLastIndex, uiState.value.menuWrapMode)

        override fun onConfirm(): InputResult {
            val currentIndex = _navFocusIndex.value
            if (currentIndex == DRAWER_ACCOUNT_ROW_INDEX) {
                onNavigate(Screen.Settings.createRoute(section = ACCOUNTS_SECTION_NAME))
                return InputResult.HANDLED
            }
            val itemIndex = currentIndex - DRAWER_NAV_ITEM_OFFSET
            if (itemIndex in drawerItems.indices) {
                Log.d("ArgosyViewModel", "Navigating to drawer item: ${drawerItems[itemIndex].route}")
                onNavigate(drawerItems[itemIndex].route)
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }

        override fun onRight(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }

        override fun onMenu(): InputResult {
            onDismiss()
            return InputResult.handled(SoundType.CLOSE_MODAL)
        }
    }

    fun onDrawerOpened() {
        viewModelScope.launch {
            romMRepository.checkConnection()
            syncCoordinator.processQueue()
            downloadManager.recheckStorageAndResume()
        }
    }

    val screenDimmerPreferences: StateFlow<ScreenDimmerPreferences> = preferencesRepository.userPreferences
        .map { it.toScreenDimmerPreferences() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ScreenDimmerPreferences()
        )

    val isEmulatorRunning: StateFlow<Boolean> = playSessionTracker.activeSession
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _netplayInvitePrompt = MutableStateFlow<NetplayInvitePayload?>(null)
    val netplayInvitePrompt: StateFlow<NetplayInvitePayload?> = _netplayInvitePrompt.asStateFlow()

    private val _netplayInviteFocusIndex = MutableStateFlow(0)
    val netplayInviteFocusIndex: StateFlow<Int> = _netplayInviteFocusIndex.asStateFlow()

    private val _netplayInviteLaunch =
        kotlinx.coroutines.flow.MutableSharedFlow<GameLaunchRequest>(extraBufferCapacity = 4)
    val netplayInviteLaunch: kotlinx.coroutines.flow.SharedFlow<GameLaunchRequest> = _netplayInviteLaunch

    private val _coreCrashLaunch =
        kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val coreCrashLaunch: kotlinx.coroutines.flow.SharedFlow<Long> = _coreCrashLaunch

    suspend fun launchOptionsFor(gameId: Long): android.os.Bundle? =
        emulatorLaunchTargetResolver.launchOptionsFor(gameId)

    fun launchFromCoreCrash() {
        val gameId = coreCrashController.prompt.value?.gameId ?: return
        coreCrashController.dismiss()
        _coreCrashLaunch.tryEmit(gameId)
    }

    private fun observeNetplayInvites() {
        viewModelScope.launch {
            socialRepository.netplayInvites.collect { payload ->
                _netplayInvitePrompt.value = payload
                _netplayInviteFocusIndex.value = 0
            }
        }
    }

    fun moveNetplayInviteFocus(direction: Int) {
        val newIndex = (_netplayInviteFocusIndex.value + direction).coerceIn(0, 1)
        _netplayInviteFocusIndex.value = newIndex
    }

    fun dismissNetplayInvite() {
        _netplayInvitePrompt.value = null
        _netplayInviteFocusIndex.value = 0
    }

    @Suppress("unused")
    private fun legacyJoinFriendNetplaySession(friend: Friend) {
        val session = friend.currentGame?.netplaySession ?: return
        if (!session.joinable) return
        viewModelScope.launch {
            notificationManager.show(
                title = NotificationText.Raw("Joining ${session.gameTitle}"),
                subtitle = NotificationText.Raw("Checking compatibility..."),
                duration = NotificationDuration.LONG
            )
            val preflight = netplayPreflightChecker.check(session)
            if (preflight !is NetplayPreflightResult.Joinable) {
                val reason = when (preflight) {
                    NetplayPreflightResult.RomNotFound -> "ROM not found"
                    NetplayPreflightResult.RomVersionMismatch -> "Different ROM version"
                    NetplayPreflightResult.CoreVersionMismatch -> "Different core version"
                    NetplayPreflightResult.CoreNotSupported -> "Core unsupported"
                    is NetplayPreflightResult.Joinable -> return@launch
                }
                notificationManager.show(
                    title = NotificationText.Raw("Can't join ${session.gameTitle}"),
                    subtitle = NotificationText.Raw(reason),
                    type = NotificationType.ERROR,
                    duration = NotificationDuration.MEDIUM
                )
                return@launch
            }
            val gameId = preflight.gameId ?: run {
                val igdbId = session.gameIgdbId?.toLong() ?: run {
                    notificationManager.show(
                        title = NotificationText.Raw("Can't join ${session.gameTitle}"),
                        subtitle = NotificationText.Raw("Missing game id for session"),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                    return@launch
                }
                gameRepository.getByIgdbId(igdbId)?.id ?: run {
                    notificationManager.show(
                        title = NotificationText.Raw("Can't join ${session.gameTitle}"),
                        subtitle = NotificationText.Raw("Local game not found"),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                    return@launch
                }
            }
            when (val result = launchGameUseCase(gameId = gameId, allowVariantPrompt = false)) {
                is LaunchResult.Success -> {
                    val decorated = android.content.Intent(result.intent).apply {
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_SESSION_ID, session.sessionId)
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_HOST_USER_ID, friend.id)
                        if (preflight.resolvedCorePath != null) {
                            putExtra(LibretroActivity.EXTRA_CORE_PATH, preflight.resolvedCorePath)
                        }
                    }
                    _netplayInviteLaunch.tryEmit(
                        GameLaunchRequest(decorated, emulatorLaunchTargetResolver.launchOptionsFor(gameId))
                    )
                }
                is LaunchResult.Error -> {
                    notificationManager.show(
                        title = NotificationText.Raw("Can't join ${session.gameTitle}"),
                        subtitle = NotificationText.Raw(result.message),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                }
                LaunchResult.Cancelled -> Unit
                else -> {
                    notificationManager.show(
                        title = NotificationText.Raw("Can't join ${session.gameTitle}"),
                        subtitle = NotificationText.Raw("Couldn't launch game"),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                }
            }
        }
    }

    fun acceptNetplayInvite() {
        val invite = _netplayInvitePrompt.value ?: return
        _netplayInvitePrompt.value = null
        _netplayInviteFocusIndex.value = 0
        viewModelScope.launch {
            val session = NetplaySession(
                sessionId = invite.sessionId,
                gameIgdbId = invite.gameIgdbId,
                gameTitle = invite.gameTitle,
                coreId = invite.coreId,
                romHashPrefix = invite.romHashPrefix,
                coreHash = invite.coreHash,
                joinable = true,
                protocolVersion = invite.protocolVersion
            )
            val preflight = netplayPreflightChecker.check(session)
            if (preflight !is NetplayPreflightResult.Joinable) {
                val reason = when (preflight) {
                    NetplayPreflightResult.RomNotFound ->
                        application.getString(R.string.ui_netplay_invite_reason_rom_missing)
                    NetplayPreflightResult.RomVersionMismatch ->
                        application.getString(R.string.ui_netplay_invite_reason_rom_version)
                    NetplayPreflightResult.CoreVersionMismatch ->
                        application.getString(R.string.ui_netplay_invite_reason_core_version)
                    NetplayPreflightResult.CoreNotSupported ->
                        application.getString(R.string.ui_netplay_invite_reason_core_unsupported)
                    is NetplayPreflightResult.Joinable -> return@launch
                }
                notificationManager.show(
                    title = NotificationText.Res(
                        R.string.ui_netplay_invite_failed_title,
                        listOf(invite.gameTitle)
                    ),
                    subtitle = NotificationText.Raw(reason),
                    type = NotificationType.ERROR,
                    duration = NotificationDuration.MEDIUM
                )
                return@launch
            }
            val igdbId = invite.gameIgdbId?.toLong() ?: run {
                notificationManager.show(
                    title = NotificationText.Res(
                        R.string.ui_netplay_invite_failed_title,
                        listOf(invite.gameTitle)
                    ),
                    subtitle = NotificationText.Res(
                        R.string.ui_netplay_invite_reason_missing_game_id
                    ),
                    type = NotificationType.ERROR,
                    duration = NotificationDuration.MEDIUM
                )
                return@launch
            }
            val game = gameRepository.getByIgdbId(igdbId) ?: run {
                notificationManager.show(
                    title = NotificationText.Res(
                        R.string.ui_netplay_invite_failed_title,
                        listOf(invite.gameTitle)
                    ),
                    subtitle = NotificationText.Res(
                        R.string.ui_netplay_invite_reason_game_not_local
                    ),
                    type = NotificationType.ERROR,
                    duration = NotificationDuration.MEDIUM
                )
                return@launch
            }
            when (val result = launchGameUseCase(gameId = game.id, allowVariantPrompt = false)) {
                is LaunchResult.Success -> {
                    val decorated = android.content.Intent(result.intent).apply {
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_SESSION_ID, invite.sessionId)
                        putExtra(LibretroActivity.EXTRA_NETPLAY_JOIN_HOST_USER_ID, invite.hostUserId)
                        if (preflight.resolvedCorePath != null) {
                            putExtra(LibretroActivity.EXTRA_CORE_PATH, preflight.resolvedCorePath)
                        }
                    }
                    _netplayInviteLaunch.tryEmit(
                        GameLaunchRequest(decorated, emulatorLaunchTargetResolver.launchOptionsFor(game.id))
                    )
                }
                is LaunchResult.Error -> {
                    notificationManager.show(
                        title = NotificationText.Res(
                            R.string.ui_netplay_invite_failed_title,
                            listOf(invite.gameTitle)
                        ),
                        subtitle = NotificationText.Raw(result.message),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                }
                LaunchResult.Cancelled -> Unit
                else -> {
                    notificationManager.show(
                        title = NotificationText.Res(
                            R.string.ui_netplay_invite_failed_title,
                            listOf(invite.gameTitle)
                        ),
                        subtitle = NotificationText.Res(
                            R.string.ui_netplay_invite_reason_launch_failed
                        ),
                        type = NotificationType.ERROR,
                        duration = NotificationDuration.MEDIUM
                    )
                }
            }
        }
    }

    data class PendingLaunch(
        val gameId: Long,
        val channelName: String? = null,
        val discId: Long? = null,
        val origin: LaunchOrigin = LaunchOrigin.INTERNAL
    )

    private val _pendingLaunch = MutableStateFlow<PendingLaunch?>(null)
    val pendingLaunch: StateFlow<PendingLaunch?> = _pendingLaunch.asStateFlow()

    fun initiateGameLaunch(
        gameId: Long,
        channelName: String? = null,
        discId: Long? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL
    ) {
        _pendingLaunch.value = PendingLaunch(gameId, channelName, discId, origin)
    }

    fun consumePendingLaunch(): PendingLaunch? {
        val launch = _pendingLaunch.value
        _pendingLaunch.value = null
        return launch
    }
}

private fun rommAvatarUrl(prefs: UserPreferences): String? {
    if (prefs.rommAvatarPath.isNullOrBlank()) return null
    val baseUrl = prefs.rommBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
    val userId = prefs.rommUserId ?: return null
    return "$baseUrl/api/users/$userId/avatar"
}
