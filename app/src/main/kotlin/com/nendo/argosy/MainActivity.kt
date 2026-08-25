package com.nendo.argosy

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.domain.usecase.achievement.FetchAchievementsUseCase
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.ui.ArgosyApp
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.gamepadEventToKeyCode
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.theme.ALauncherTheme
import android.view.Display
import com.nendo.argosy.hardware.SecondaryHomeActivity
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.hideSystemBars
import com.nendo.argosy.util.installImmersiveMode
import com.nendo.argosy.util.DisplayRoleResolver
import dagger.hilt.android.AndroidEntryPoint
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainActivity"
internal fun shouldInitializeScreenCapture(prefs: UserPreferences): Boolean =
    prefs.ambientLedEnabled && prefs.ambientLedScreenEnabled

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var gameRepository: com.nendo.argosy.data.repository.GameRepository
    @Inject lateinit var activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository
    @Inject lateinit var configureEmulatorUseCase: com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
    @Inject lateinit var platformRepository: PlatformRepository
    @Inject lateinit var collectionRepository: CollectionRepository
    @Inject lateinit var downloadQueueDao: DownloadQueueDao
    @Inject lateinit var downloadQueueRepository: com.nendo.argosy.data.repository.DownloadQueueRepository
    @Inject lateinit var gamepadInputHandler: GamepadInputHandler
    @Inject lateinit var triggerAxisKeyEmitter: com.nendo.argosy.ui.input.TriggerAxisKeyEmitter
    @Inject lateinit var imageCacheManager: ImageCacheManager
    @Inject lateinit var resolveGameEmulatorContext:
        com.nendo.argosy.domain.usecase.emulator.ResolveGameEmulatorContextUseCase
    @Inject lateinit var hapticFeedbackManager: com.nendo.argosy.ui.input.HapticFeedbackManager
    @Inject lateinit var soundFeedbackManager: com.nendo.argosy.ui.input.SoundFeedbackManager
    @Inject lateinit var androidGameScanner: com.nendo.argosy.data.scanner.AndroidGameScanner
    @Inject lateinit var gameNativeStoreSync: com.nendo.argosy.data.launcher.GameNativeStoreSync
    @Inject lateinit var romMRepository: RomMRepository
    @Inject lateinit var jellyfinConnectionManager: com.nendo.argosy.data.remote.jellyfin.JellyfinConnectionManager
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
    @Inject lateinit var homeTileRepository: com.nendo.argosy.data.repository.HomeTileRepository
    @Inject lateinit var homeTilePromptQueue: com.nendo.argosy.data.repository.HomeTilePromptQueue
    @Inject lateinit var appsRepository: com.nendo.argosy.data.repository.AppsRepository
    @Inject lateinit var ambientAudioManager: AmbientAudioManager
    @Inject lateinit var bgmPlaylistCoordinator: com.nendo.argosy.ui.audio.BgmPlaylistCoordinator
    @Inject lateinit var ambientLedManager: AmbientLedManager
    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var displayAffinityHelper: DisplayAffinityHelper
    @Inject lateinit var permissionHelper: com.nendo.argosy.util.PermissionHelper
    @Inject lateinit var gameActionsDelegate: GameActionsDelegate
    @Inject lateinit var syncPlatformUseCase: com.nendo.argosy.domain.usecase.sync.SyncPlatformUseCase
    @Inject lateinit var gameThemeAudioCoordinator: com.nendo.argosy.ui.audio.GameThemeAudioCoordinator
    @Inject lateinit var getPinnedCollectionsUseCase: com.nendo.argosy.domain.usecase.collection.GetPinnedCollectionsUseCase
    @Inject lateinit var advanceCollectionFocusUseCase: com.nendo.argosy.domain.usecase.collection.AdvanceCollectionFocusUseCase
    @Inject lateinit var prepareCollectionQueueUseCase: com.nendo.argosy.domain.usecase.collection.PrepareCollectionQueueUseCase
    @Inject lateinit var getGamesForPinnedCollectionUseCase: com.nendo.argosy.domain.usecase.collection.GetGamesForPinnedCollectionUseCase
    @Inject lateinit var gameLaunchDelegate: GameLaunchDelegate
    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var getUnifiedSavesUseCase: GetUnifiedSavesUseCase
    @Inject lateinit var getUnifiedStatesUseCase:
        com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase
    @Inject lateinit var stateCacheManager: com.nendo.argosy.data.repository.StateCacheManager
    @Inject lateinit var restoreCachedSaveUseCase: RestoreCachedSaveUseCase
    @Inject lateinit var activateSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.ActivateSaveChannelUseCase
    @Inject lateinit var restoreSaveChannelPointUseCase:
        com.nendo.argosy.domain.usecase.savechannel.RestoreSaveChannelPointUseCase
    @Inject lateinit var createSaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.CreateSaveChannelUseCase
    @Inject lateinit var copySaveChannelUseCase:
        com.nendo.argosy.domain.usecase.savechannel.CopySaveChannelUseCase
    @Inject lateinit var restoreStateUseCase:
        com.nendo.argosy.domain.usecase.state.RestoreStateUseCase
    @Inject lateinit var prefetchGameSaveDataUseCase:
        com.nendo.argosy.domain.usecase.sync.PrefetchGameSaveDataUseCase
    @Inject lateinit var emulatorResolver: EmulatorResolver
    @Inject lateinit var coreVersionExtractor: com.nendo.argosy.data.emulator.CoreVersionExtractor
    @Inject lateinit var fetchAchievementsUseCase: FetchAchievementsUseCase
    @Inject lateinit var gameFileDao: com.nendo.argosy.data.local.dao.GameFileDao
    @Inject lateinit var downloadManagerInstance: com.nendo.argosy.data.download.DownloadManager
    @Inject lateinit var notificationManager: com.nendo.argosy.core.notification.NotificationManager
    @Inject lateinit var titleIdDownloadObserver: com.nendo.argosy.data.emulator.TitleIdDownloadObserver
    @Inject lateinit var homeGridPageRepository: com.nendo.argosy.data.repository.HomeGridPageRepository
    @Inject lateinit var pageChooserEntrySource: com.nendo.argosy.ui.home.grid.PageChooserEntrySource
    @Inject lateinit var emulatorConfigDao: com.nendo.argosy.data.local.dao.EmulatorConfigDao
    @Inject lateinit var builtinCoreResolver: com.nendo.argosy.data.emulator.BuiltinCoreResolver
    @Inject lateinit var saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
    @Inject lateinit var steamDownloadQueueDao: com.nendo.argosy.data.local.dao.SteamDownloadQueueDao
    @Inject lateinit var steamRepository: com.nendo.argosy.data.repository.SteamRepository
    @Inject lateinit var playSessionTracker: com.nendo.argosy.data.emulator.PlaySessionTracker
    @Inject lateinit var repairImageCacheUseCase: com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
    @Inject lateinit var downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository
    @Inject lateinit var steamContentManager: com.nendo.argosy.data.steam.SteamContentManager
    @Inject lateinit var presenceManager: com.nendo.argosy.data.social.PresenceManager
    @Inject lateinit var discordPresenceManager: com.nendo.argosy.data.social.discord.DiscordPresenceManager
    @Inject lateinit var gradientExtractionDelegate: com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
    @Inject lateinit var filePickerFlowUseCase: com.nendo.argosy.domain.usecase.download.FilePickerFlowUseCase
    @Inject lateinit var mediaRepository: com.nendo.argosy.data.repository.MediaRepository
    @Inject lateinit var getRelatedMediaUseCase:
        com.nendo.argosy.domain.usecase.media.GetRelatedMediaUseCase
    @Inject lateinit var resolveMediaPlayTargetUseCase:
        com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase
    @Inject lateinit var mediaPlaybackTracker: com.nendo.argosy.data.media.MediaPlaybackTracker

    private val sessionStateStore by lazy {
        com.nendo.argosy.data.preferences.SessionStateStore(this)
    }
    private val activityScope = SafeCoroutineScope(Dispatchers.Main, "MainActivity")

    private lateinit var preferencesObserver: MainActivityPreferencesObserver


    lateinit var dualScreenManager: DualScreenManager
        private set

    private val _pendingDeepLink = MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: StateFlow<android.net.Uri?> = _pendingDeepLink

    var isOnHomeScreen = false

    // --- Delegated properties for external consumers (ArgosyApp.kt) ---

    var isOverlayFocused: Boolean
        get() = dualScreenManager.isOverlayFocused
        set(value) {
            dualScreenManager.isOverlayFocused = value
        }

    var onDimmerActivity: (() -> Unit)? = null

    val dualScreenShowcase get() = dualScreenManager.dualScreenShowcase
    val dualGameDetailState get() = dualScreenManager.dualGameDetailState
    val isCompanionActive get() = dualScreenManager.isCompanionActive
    val dualViewMode get() = dualScreenManager.dualViewMode
    val dualAppBarFocused get() = dualScreenManager.dualAppBarFocused
    val dualDrawerOpen get() = dualScreenManager.dualDrawerOpen
    val dualCollectionShowcase get() = dualScreenManager.dualCollectionShowcase
    val pendingOverlayEvent get() = dualScreenManager.pendingOverlayEvent
    val swappedDualHomeViewModel get() = dualScreenManager.swappedDualHomeViewModel
    val swappedCurrentScreen get() = dualScreenManager.swappedCurrentScreen
    val swappedGameDetailViewModel get() = dualScreenManager.swappedGameDetailViewModel
    val homeAppsList get() = dualScreenManager.homeAppsList

    fun clearPendingOverlay() = dualScreenManager.clearPendingOverlay()
    fun adjustDualModalRating(delta: Int) = dualScreenManager.adjustDualModalRating(delta)
    fun setDualModalRating(value: Int) = dualScreenManager.setDualModalRating(value)
    fun moveDualModalStatus(delta: Int) = dualScreenManager.moveDualModalStatus(delta)
    fun setDualModalStatus(value: String) = dualScreenManager.setDualModalStatus(value)
    fun confirmDualModal() = dualScreenManager.confirmDualModal()
    fun dismissDualModal() = dualScreenManager.dismissDualModal()
    fun setDualEmulatorFocus(index: Int) = dualScreenManager.setDualEmulatorFocus(index)
    fun setDualCollectionFocus(index: Int) = dualScreenManager.setDualCollectionFocus(index)
    fun moveDualEmulatorFocus(delta: Int) = dualScreenManager.moveDualEmulatorFocus(delta)
    fun confirmDualEmulatorSelection() = dualScreenManager.confirmDualEmulatorSelection()
    fun setDualCoreFocus(index: Int) = dualScreenManager.setDualCoreFocus(index)
    fun moveDualCoreFocus(delta: Int) = dualScreenManager.moveDualCoreFocus(delta)
    fun confirmDualCoreSelection() = dualScreenManager.confirmDualCoreSelection()
    fun setDualSavePathFocus(index: Int) = dualScreenManager.setDualSavePathFocus(index)
    fun moveDualSavePathFocus(delta: Int) = dualScreenManager.moveDualSavePathFocus(delta)
    fun confirmDualSavePathSelection() = dualScreenManager.confirmDualSavePathSelection()
    fun setDualDisplayTargetFocus(index: Int) = dualScreenManager.setDualDisplayTargetFocus(index)
    fun moveDualDisplayTargetFocus(delta: Int) = dualScreenManager.moveDualDisplayTargetFocus(delta)
    fun confirmDualDisplayTargetSelection() = dualScreenManager.confirmDualDisplayTargetSelection()
    fun setDualMemoryCardFocus(index: Int) = dualScreenManager.setDualMemoryCardFocus(index)
    fun moveDualMemoryCardFocus(delta: Int) = dualScreenManager.moveDualMemoryCardFocus(delta)
    fun confirmDualMemoryCardSelection() = dualScreenManager.confirmDualMemoryCardSelection()
    fun setDualVariantFocus(index: Int) = dualScreenManager.setDualVariantFocus(index)
    fun moveDualVariantFocus(delta: Int) = dualScreenManager.moveDualVariantFocus(delta)
    fun confirmDualVariantSelection() = dualScreenManager.confirmDualVariantSelection()
    fun moveDualFilePickerFocus(delta: Int) = dualScreenManager.moveDualFilePickerFocus(delta)
    fun jumpDualFilePickerGroup(direction: Int) = dualScreenManager.jumpDualFilePickerGroup(direction)
    fun toggleDualFilePickerRow(row: com.nendo.argosy.data.model.FilePickerRow? = null) =
        dualScreenManager.toggleDualFilePickerRow(row)
    fun setDualFilePickerGroupCollapsed(collapse: Boolean) =
        dualScreenManager.setDualFocusedFilePickerGroupCollapsed(collapse)
    fun toggleDualFilePickerGroupCollapse(groupKey: String) =
        dualScreenManager.toggleDualFilePickerGroupCollapse(groupKey)
    fun moveDualFilePickerButtonFocus(delta: Int) = dualScreenManager.moveDualFilePickerButtonFocus(delta)
    fun activateDualFilePickerFocused() = dualScreenManager.activateDualFilePickerFocused()
    fun confirmDualFilePicker() = dualScreenManager.confirmDualFilePicker()
    fun moveDualCollectionFocus(delta: Int) = dualScreenManager.moveDualCollectionFocus(delta)
    fun toggleDualCollectionAtFocus() = dualScreenManager.toggleDualCollectionAtFocus()
    fun showDualCollectionCreateDialog() = dualScreenManager.showDualCollectionCreateDialog()
    fun dismissDualCollectionCreateDialog() = dualScreenManager.dismissDualCollectionCreateDialog()
    fun confirmDualCollectionCreate(name: String) = dualScreenManager.confirmDualCollectionCreate(name)
    fun updateDualSaveNameText(text: String) = dualScreenManager.updateDualSaveNameText(text)
    fun confirmDualSaveName() = dualScreenManager.confirmDualSaveName()
    fun selectDualDisc(index: Int) = dualScreenManager.selectDualDisc(index)
    fun setDualSteamInstallFocus(index: Int) = dualScreenManager.setDualSteamInstallFocus(index)
    fun moveDualSteamInstallFocus(delta: Int) = dualScreenManager.moveDualSteamInstallFocus(delta)
    fun confirmDualSteamInstallSelection() = dualScreenManager.confirmDualSteamInstallSelection()

    // --- Screen Capture ---

    private var screenCapturePromptedThisSession = false
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureManager.onPermissionResult(result.resultCode, result.data)
        if (screenCaptureManager.hasPermission.value) {
            screenCaptureManager.startCapture()
        }
    }

    fun requestScreenCapturePermission() {
        screenCaptureManager.requestPermission(this, screenCaptureLauncher)
    }

    // --- Lifecycle State ---

    private var hasResumedBefore = false
    private var hadFocusBefore = false
    private var focusLostTime = 0L
    private var yieldedFocusToGame = false

    // --- Lifecycle ---

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (display != null && display!!.displayId != Display.DEFAULT_DISPLAY) {
            if (!sessionStateStore.isDualScreenEnabled()) {
                Log.d(TAG, "MainActivity on non-default display with dual-screen disabled, finishing")
                finish()
                return
            }
            val companionIntent = Intent(this, SecondaryHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val options = android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(display!!.displayId)
                .toBundle()
            try {
                startActivity(companionIntent, options)
            } catch (e: android.content.ActivityNotFoundException) {
                Log.w(TAG, "Companion activity unavailable on this install, yielding display", e)
            }
            finish()
            return
        }

        installImmersiveMode()
        keepAwakeWhileUserActive()

        discordPresenceManager.init(this)

        displayAffinityHelper.dualScreenEnabled = sessionStateStore.isDualScreenEnabled()
        displayAffinityHelper.secondaryDisplayUsable = sessionStateStore.isSecondaryDisplayUsable()
        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        val initialSwapped = resolver.isSwapped
        sessionStateStore.setRolesSwapped(initialSwapped)

        val existingDsm = DualScreenManagerHolder.instance
        if (existingDsm != null) {
            dualScreenManager = existingDsm
            dualScreenManager.rebind(this, activityScope)
            dualScreenManager.setRolesSwapped(initialSwapped)
        } else {
            dualScreenManager = DualScreenManager(
                context = this,
                scope = activityScope,
                gameDao = gameDao,
                gameRepository = gameRepository,
                activeSaveRepository = activeSaveRepository,
                platformRepository = platformRepository,
                collectionRepository = collectionRepository,
                downloadQueueDao = downloadQueueDao,
                downloadQueueRepository = downloadQueueRepository,
                gameFileDao = gameFileDao,
                downloadManager = downloadManagerInstance,
                gameActionsDelegate = gameActionsDelegate,
                syncPlatformUseCase = syncPlatformUseCase,
                gameLaunchDelegate = gameLaunchDelegate,
                saveCacheManager = saveCacheManager,
                getUnifiedSavesUseCase = getUnifiedSavesUseCase,
                getUnifiedStatesUseCase = getUnifiedStatesUseCase,
                stateCacheManager = stateCacheManager,
                restoreCachedSaveUseCase = restoreCachedSaveUseCase,
                activateSaveChannelUseCase = activateSaveChannelUseCase,
                restoreSaveChannelPointUseCase = restoreSaveChannelPointUseCase,
                createSaveChannelUseCase = createSaveChannelUseCase,
                copySaveChannelUseCase = copySaveChannelUseCase,
                restoreStateUseCase = restoreStateUseCase,
                prefetchGameSaveDataUseCase = prefetchGameSaveDataUseCase,
                emulatorResolver = emulatorResolver,
                coreVersionExtractor = coreVersionExtractor,
                fetchAchievementsUseCase = fetchAchievementsUseCase,
                displayAffinityHelper = displayAffinityHelper,
                sessionStateStore = sessionStateStore,
                preferencesRepository = preferencesRepository,
                syncPreferencesRepository = syncPreferencesRepository,
                homeTileRepository = homeTileRepository,
                homeTilePromptQueue = homeTilePromptQueue,
                appsRepository = appsRepository,
                notificationManager = notificationManager,
                titleIdDownloadObserver = titleIdDownloadObserver,
                homeGridPageRepository = homeGridPageRepository,
                pageChooserEntrySource = pageChooserEntrySource,
                ambientAudioManager = ambientAudioManager,
                emulatorConfigDao = emulatorConfigDao,
                configureEmulatorUseCase = configureEmulatorUseCase,
                builtinCoreResolver = builtinCoreResolver,
                saveHandlerRegistry = saveHandlerRegistry,
                steamDownloadQueueDao = steamDownloadQueueDao,
                steamRepository = steamRepository,
                playSessionTracker = playSessionTracker,
                permissionHelper = permissionHelper,
                steamContentManager = steamContentManager,
                repairImageCacheUseCase = repairImageCacheUseCase,
                downloadFileStatusRepository = downloadFileStatusRepository,
                gradientExtractionDelegate = gradientExtractionDelegate,
                filePickerFlow = filePickerFlowUseCase,
                gameThemeAudioCoordinator = gameThemeAudioCoordinator,
                getPinnedCollectionsUseCase = getPinnedCollectionsUseCase,
                getGamesForPinnedCollectionUseCase = getGamesForPinnedCollectionUseCase,
                advanceCollectionFocusUseCase = advanceCollectionFocusUseCase,
                prepareCollectionQueueUseCase = prepareCollectionQueueUseCase,
                mediaRepository = mediaRepository,
                getRelatedMediaUseCase = getRelatedMediaUseCase,
                resolveMediaPlayTargetUseCase = resolveMediaPlayTargetUseCase,
                mediaPlaybackTracker = mediaPlaybackTracker,
                imageCacheManager = imageCacheManager,
                resolveGameEmulatorContext = resolveGameEmulatorContext,
                hapticManager = hapticFeedbackManager,
                soundManager = soundFeedbackManager,
                initialRolesSwapped = initialSwapped
            )
            DualScreenManagerHolder.instance = dualScreenManager
        }

        if (initialSwapped) {
            dualScreenManager.initSwappedViewModel()
        }

        dualScreenManager.onRoleSwapped = { swapped ->
            if (swapped) {
                if (dualScreenManager.swappedDualHomeViewModel == null) {
                    dualScreenManager.initSwappedViewModel()
                }
                refocusSelf()
            }
        }
        dualScreenManager.onOverlayFocusChanged = { _ -> }
        dualScreenManager.onEmulatorDispatcherChanged = { }
        dualScreenManager.registerReceivers()
        dualScreenManager.ensureCompanionLaunched()
        dualScreenManager.startStartupGuard()
        initCacheAndPreferences()
        activityScope.launch { jellyfinConnectionManager.initialize() }


        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)

        preferencesObserver = MainActivityPreferencesObserver(
            preferencesRepository = preferencesRepository,
            ambientAudioManager = ambientAudioManager,
            bgmPlaylistCoordinator = bgmPlaylistCoordinator,
            sessionStateStore = sessionStateStore,
            dualScreenManager = dualScreenManager,
            displayAffinityHelper = displayAffinityHelper,
            onDualScreenChanged = { dualScreenManager.setDualScreenDevice(it) },
            hasWindowFocus = ::hasWindowFocus
        )
        preferencesObserver.collectIn(activityScope)

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val rolesSwappedState = dualScreenManager.isRolesSwapped.collectAsState()
                    val dualScreenDeviceState = dualScreenManager.isDualScreenDevice.collectAsState()
                    ArgosyApp(
                        isDualScreenDevice = dualScreenDeviceState.value,
                        isRolesSwapped = rolesSwappedState.value,
                        isCompanionActive = isCompanionActive,
                        dualScreenShowcase = dualScreenShowcase,
                        dualGameDetailState = dualGameDetailState,
                        dualViewMode = dualViewMode,
                        dualCollectionShowcase = dualCollectionShowcase,
                        dualAppBarFocused = dualAppBarFocused,
                        dualDrawerOpen = dualDrawerOpen,
                        onStartupComplete = { dualScreenManager.stopStartupGuard() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) {
            handleHomeIntent(intent)
        }
    }

    @SuppressLint("NewApi")
    override fun onResume() {
        super.onResume()
        window.hideSystemBars()
        onDimmerActivity?.invoke()
        Log.d(TAG, "onResume: swapped=${dualScreenManager.isRolesSwapped.value} gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"} hasResumedBefore=$hasResumedBefore")

        dualScreenManager.broadcastForegroundState(true)

        cleanupStaleSession()
        revalidateDownloadedFiles()

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch { romMRepository.initialize() }
            activityScope.launch { jellyfinConnectionManager.initialize() }
            ambientAudioManager.fadeIn()
        } else {
            if (displayAffinityHelper.hasSecondaryDisplay && !dualScreenManager.isRolesSwapped.value) {
                window.decorView.postDelayed({
                    dualScreenManager.companionHost?.refocusSelf()
                }, 500)
            }
        }
        hasResumedBefore = true
    }

    override fun onPause() {
        super.onPause()
        ambientAudioManager.suspend()
        if (!dualScreenManager.isCompanionActive.value) {
            dualScreenManager.broadcastForegroundState(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::screenCaptureManager.isInitialized) screenCaptureManager.stopCapture()
        activityScope.cancel()
        if (isFinishing && ::dualScreenManager.isInitialized) {
            dualScreenManager.unregisterReceivers()
            DualScreenManagerHolder.instance = null
        }
    }

    // --- Input Dispatch ---

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!dualScreenManager.claimInput(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Logger.verbose(TAG) { "dispatchKeyEvent: key=${event.keyCode} isHome=$isOnHomeScreen swapped=${dualScreenManager.isRolesSwapped.value} gameOnSecondary=${dualScreenManager.swappedIsGameActive.value} companion=${dualScreenManager.isCompanionActive.value} overlay=$isOverlayFocused" }
            if (dualScreenManager.handleConflictInput(
                    event.keyCode,
                    sessionStateStore.getSwapAB(),
                    sessionStateStore.getSwapXY(),
                    sessionStateStore.getSwapStartSelect()
                )
            ) return true
        }

        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused && isGameOnOtherDisplay()) {
            val emulatorDispatcher = dualScreenManager.emulatorKeyDispatcher
            if (emulatorDispatcher != null) {
                Logger.verbose(TAG) { "dispatchKeyEvent: FORWARDING key=${event.keyCode} to emulator" }
                return emulatorDispatcher(event)
            }
            return true
        }

        if (!dualScreenManager.isRolesSwapped.value &&
            isOnHomeScreen &&
            dualScreenManager.isCompanionActive.value &&
            !isOverlayFocused
        ) {
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    Logger.verbose(TAG) { "dispatchKeyEvent: FORWARDING key=${event.keyCode} to companion" }
                    onDimmerActivity?.invoke()
                }
                dualScreenManager.companionHost?.onForwardKey(
                    event.keyCode,
                    event.action,
                    event.repeatCount,
                    sessionStateStore.getSwapAB(),
                    sessionStateStore.getSwapXY(),
                    sessionStateStore.getSwapStartSelect()
                )
            }
            return true
        }

        if (!dualScreenManager.isRolesSwapped.value &&
            isOnHomeScreen &&
            !isOverlayFocused &&
            !dualScreenManager.isCompanionActive.value &&
            !dualScreenManager.swappedIsGameActive.value &&
            !sessionStateStore.isForeignAppOnSecondary() &&
            displayAffinityHelper.hasSecondaryDisplay
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "dispatchKeyEvent: companion link stale, relinking key=${event.keyCode}")
                reassertCompanionForwarding()
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Logger.verbose(TAG) { "dispatchKeyEvent: LOCAL handling key=${event.keyCode}" }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        if (gamepadInputHandler.handleKeyEvent(event)) {
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_HOME
        ) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        triggerAxisKeyEmitter.emit(event).forEach { dispatchKeyEvent(it) }

        if (!dualScreenManager.claimInput(event)) return true
        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused && isGameOnOtherDisplay()) {
            val emulatorDispatcher = dualScreenManager.emulatorMotionDispatcher
            if (emulatorDispatcher != null) {
                return emulatorDispatcher(event)
            }
            return true
        }

        val stickEvent = gamepadInputHandler.processStickMotion(event)
        if (stickEvent != null) {
            if (!dualScreenManager.isRolesSwapped.value &&
                isOnHomeScreen &&
                dualScreenManager.isCompanionActive.value &&
                !isOverlayFocused
            ) {
                val keyCode = gamepadEventToKeyCode(stickEvent)
                if (keyCode != null) {
                    dualScreenManager.companionHost?.onForwardKey(
                        keyCode,
                        KeyEvent.ACTION_DOWN,
                        0,
                        sessionStateStore.getSwapAB(),
                        sessionStateStore.getSwapXY(),
                        sessionStateStore.getSwapStartSelect()
                    )
                }
                return true
            }

            if (!dualScreenManager.isRolesSwapped.value &&
                isOnHomeScreen &&
                !isOverlayFocused &&
                !dualScreenManager.isCompanionActive.value &&
                !dualScreenManager.swappedIsGameActive.value &&
                !sessionStateStore.isForeignAppOnSecondary() &&
                displayAffinityHelper.hasSecondaryDisplay
            ) {
                reassertCompanionForwarding()
                return true
            }

            gamepadInputHandler.injectEvent(stickEvent)
            return true
        }

        if (gamepadInputHandler.handleMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // --- Window Focus ---

    @SuppressLint("NewApi")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus swapped=${if (::dualScreenManager.isInitialized) dualScreenManager.isRolesSwapped.value else "N/A"} gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"}")
        if (hasFocus) {
            val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
            if (hadFocusBefore && focusLostTime > 0 && timeSinceFocusLost < 1000) {
                gamepadInputHandler.emitHomeEvent()
            }
            hadFocusBefore = true
            focusLostTime = 0L
            if (yieldedFocusToGame) {
                yieldedFocusToGame = false
                reassertCompanionForwarding()
            }
            onDimmerActivity?.invoke()
            window.hideSystemBars()
            window.decorView.requestFocus()
            ambientAudioManager.fadeIn()
            ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
            ambientLedManager.clearInGameColors()
            if (!isOverlayFocused) {
                gamepadInputHandler.blockInputFor(200)
            }
        } else {
            focusLostTime = System.currentTimeMillis()
            if (::dualScreenManager.isInitialized &&
                displayAffinityHelper.hasSecondaryDisplay &&
                !dualScreenManager.isRolesSwapped.value
            ) {
                yieldedFocusToGame = true
            }
            if (::dualScreenManager.isInitialized) {
                dualScreenManager.onFocusLostToEmulator()
            }
            ambientAudioManager.fadeOut()
            ambientLedManager.setContext(AmbientLedContext.IN_GAME)
            if (::dualScreenManager.isInitialized) {
                val emulatorDisplay = dualScreenManager.emulatorDisplayId
                if (emulatorDisplay != null && emulatorDisplay != display?.displayId) {
                    dualScreenManager.restoreEmulatorFocus()
                }
            }
        }
    }

    // --- Private Helpers ---

    /** True only when a session's emulator runs on a different display than this activity; a same-display session cannot have focus while we do, so input must never be deferred to it. */
    private fun isGameOnOtherDisplay(): Boolean {
        val emulatorDisplay = dualScreenManager.emulatorDisplayId ?: return false
        val ownDisplay = window.decorView.display?.displayId ?: return false
        return emulatorDisplay != ownDisplay
    }

    /** Relinks companion input forwarding when input arrives on home but the link is stale (companion marked inactive or overlay focus latched) after a game, sleep/wake, or a foreground app yielding the secondary display. */
    private fun reassertCompanionForwarding() {
        if (!::dualScreenManager.isInitialized) return
        if (!displayAffinityHelper.hasSecondaryDisplay) return
        if (dualScreenManager.isRolesSwapped.value) return
        if (dualScreenManager.swappedIsGameActive.value) return
        isOverlayFocused = false
        if (dualScreenManager.isCompanionActive.value) {
            dualScreenManager.companionHost?.refocusSelf()
        } else {
            dualScreenManager.ensureCompanionLaunched()
        }
    }

    /**
     * Re-checks localPath for every downloaded game whenever the user returns to
     * the launcher. Catches manual deletions performed outside the app (e.g. via
     * a file manager) so stale "installed" badges clear without requiring a cold
     * restart. Cost is one File.exists per downloaded game; acceptable on resume.
     */
    private fun revalidateDownloadedFiles() {
        activityScope.launch(Dispatchers.IO) {
            runCatching { gameRepository.validateLocalFiles() }
                .onFailure { Log.w(TAG, "revalidateDownloadedFiles failed", it) }
        }
    }

    /**
     * The launcher UI returning to the foreground ends the session only once the emulator is
     * actually gone. An emulator that hands off between its own activities drops the launcher
     * in front for an instant, and a two-display device leaves the game running unfocused on the
     * other panel, so neither the launcher resuming nor the display it resumed on says anything
     * about whether the game is still there. Only a session on a different display survives
     * outright, since the game and the launcher UI legitimately coexist.
     */
    private fun cleanupStaleSession() {
        activityScope.launch {
            if (!::dualScreenManager.isInitialized) return@launch
            if (dualScreenManager.isLaunchingGame) return@launch
            val emulatorDisplay = dualScreenManager.emulatorDisplayId
            val ownDisplay = window.decorView.display?.displayId
            if (emulatorDisplay != null && ownDisplay != null && emulatorDisplay != ownDisplay) return@launch
            if (dualScreenManager.isEmulatorStillOnScreen(this@MainActivity)) return@launch
            if (playSessionTracker.activeSession.value == null &&
                preferencesRepository.getPersistedSession() == null
            ) return@launch

            dualScreenManager.emulatorDisplayId = null
            sessionStateStore.clearSession()
            playSessionTracker.endSessionInBackground()
            dualScreenManager.broadcastSessionCleared()
            if (displayAffinityHelper.hasSecondaryDisplay) {
                com.nendo.argosy.hardware.RecoveryDisplayService.stop(this@MainActivity)
            }
        }
    }

    private fun initCacheAndPreferences() {
        activityScope.launch {
            val prefs = preferencesRepository.preferences.first()
            displayAffinityHelper.dualScreenEnabled = prefs.dualScreenEnabled
            sessionStateStore.setDualScreenEnabled(prefs.dualScreenEnabled)
            sessionStateStore.setSaveSyncEnabled(prefs.saveSyncEnabled)
            dualScreenManager.setDualScreenDevice(displayAffinityHelper.hasSecondaryDisplay)
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)

            if (imageCacheManager.needsLegacyCacheDirsMigration()) {
                Log.i(TAG, "Migrating cover cache out of purgeable cacheDir to persistent storage")
                imageCacheManager.migrateLegacyCacheDirs()
            }

            if (imageCacheManager.needsFlatToShardedMigration()) {
                Log.i(TAG, "Migrating flat image cache to sharded directories")
                imageCacheManager.migrateFlatToSharded()
            }

            imageCacheManager.resumePendingCache()
            imageCacheManager.resumePendingCoverCache()
            if (preferencesRepository.preferences.first().boxArtCacheEnabled) {
                imageCacheManager.resumePendingBoxFaceCache()
            }
            imageCacheManager.resumePendingLogoCache()
            imageCacheManager.resumePendingBadgeCache()

            val validationResult = imageCacheManager.validateAndCleanCache()
            if (validationResult.deletedFiles > 0 || validationResult.clearedPaths > 0) {
                Log.i(TAG, "Cache validation: ${validationResult.deletedFiles} files deleted, ${validationResult.clearedPaths} paths cleared")
            }

            imageCacheManager.recoverMissingCovers()

            androidGameScanner.ensureAndroidPlatformExists()

            val relinked = androidGameScanner.relinkInstalledRommAndroidApps()
            if (relinked > 0) {
                Log.i(TAG, "Relinked $relinked installed RomM Android games to their packages")
            }

            val storeSync = gameNativeStoreSync.scan()
            if (storeSync.configured) {
                Log.i(TAG, "GameNative store sync: ${storeSync.results}")
            }

            if (shouldInitializeScreenCapture(prefs)) {
                if (screenCaptureManager.hasPermission.value && !screenCaptureManager.isCapturing.value) {
                    screenCaptureManager.startCapture()
                } else if (!screenCaptureManager.hasPermission.value && !screenCapturePromptedThisSession) {
                    screenCapturePromptedThisSession = true
                    screenCaptureManager.requestPermission(
                        this@MainActivity, screenCaptureLauncher
                    )
                }
            }
        }
    }



    private fun handleDeepLink(intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme == "argosy") {
            Log.d(TAG, "Received deep link: $uri")
            _pendingDeepLink.value = uri
            return true
        }
        return false
    }

    fun clearPendingDeepLink() {
        _pendingDeepLink.value = null
    }

    private fun handleHomeIntent(intent: Intent): Boolean {
        if (intent.hasCategory(Intent.CATEGORY_HOME) && hasResumedBefore) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return false
    }

    /**
     * Holds this window awake while the person is using either screen.
     *
     * Input only counts as activity on the display it landed on, so without this the screen that
     * is not being driven dims underneath a session the user is very much still in.
     */
    private fun keepAwakeWhileUserActive() {
        activityScope.launch {
            dualScreenManager.userActive.collect { active ->
                if (active) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Takes key focus back after a swap has made this the screen being driven.
     *
     * The companion does the same for itself when the roles go the other way. Without the matching
     * move, a swap into this role leaves focus on the window that just became the showcase, and the
     * pad reads as dead until a touch hands focus over.
     */
    private fun refocusSelf() = startActivity(
        Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )
}
