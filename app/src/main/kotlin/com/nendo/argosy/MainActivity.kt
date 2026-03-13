package com.nendo.argosy

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.LaunchRetryTracker
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.GameDao
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
import com.nendo.argosy.util.DisplayRoleResolver
import com.nendo.argosy.util.PermissionHelper
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var platformRepository: PlatformRepository
    @Inject lateinit var collectionRepository: CollectionRepository
    @Inject lateinit var downloadQueueDao: DownloadQueueDao
    @Inject lateinit var gamepadInputHandler: GamepadInputHandler
    @Inject lateinit var imageCacheManager: ImageCacheManager
    @Inject lateinit var romMRepository: RomMRepository
    @Inject lateinit var launchRetryTracker: LaunchRetryTracker
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository
    @Inject lateinit var ambientAudioManager: AmbientAudioManager
    @Inject lateinit var ambientLedManager: AmbientLedManager
    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var displayAffinityHelper: DisplayAffinityHelper
    @Inject lateinit var permissionHelper: PermissionHelper
    @Inject lateinit var gameActionsDelegate: GameActionsDelegate
    @Inject lateinit var gameLaunchDelegate: GameLaunchDelegate
    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var getUnifiedSavesUseCase: GetUnifiedSavesUseCase
    @Inject lateinit var restoreCachedSaveUseCase: RestoreCachedSaveUseCase
    @Inject lateinit var emulatorResolver: EmulatorResolver
    @Inject lateinit var fetchAchievementsUseCase: FetchAchievementsUseCase
    @Inject lateinit var gameFileDao: com.nendo.argosy.data.local.dao.GameFileDao
    @Inject lateinit var downloadManagerInstance: com.nendo.argosy.data.download.DownloadManager
    @Inject lateinit var edenContentManager: com.nendo.argosy.data.emulator.EdenContentManager
    @Inject lateinit var notificationManager: com.nendo.argosy.ui.notification.NotificationManager
    @Inject lateinit var emulatorConfigDao: com.nendo.argosy.data.local.dao.EmulatorConfigDao
    @Inject lateinit var playSessionTracker: com.nendo.argosy.data.emulator.PlaySessionTracker
    @Inject lateinit var repairImageCacheUseCase: com.nendo.argosy.domain.usecase.cache.RepairImageCacheUseCase
    @Inject lateinit var presenceManager: com.nendo.argosy.data.social.PresenceManager
    @Inject lateinit var discordPresenceManager: com.nendo.argosy.data.social.discord.DiscordPresenceManager

    private val sessionStateStore by lazy {
        com.nendo.argosy.data.preferences.SessionStateStore(this)
    }
    private val activityScope = SafeCoroutineScope(Dispatchers.Main, "MainActivity")

    private lateinit var emulatorSessionPolicy: EmulatorSessionPolicy
    private lateinit var preferencesObserver: MainActivityPreferencesObserver


    lateinit var dualScreenManager: DualScreenManager
        private set

    private val _pendingDeepLink = MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: StateFlow<android.net.Uri?> = _pendingDeepLink

    var isRolesSwapped by mutableStateOf(false)
        private set
    var isDualScreenDevice by mutableStateOf(false)
        private set
    var isOnHomeScreen = false

    // --- Delegated properties for external consumers (ArgosyApp.kt) ---

    var isOverlayFocused: Boolean
        get() = dualScreenManager.isOverlayFocused
        set(value) {
            dualScreenManager.isOverlayFocused = value
            updateWindowFocusability()
        }

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
    fun moveDualCollectionFocus(delta: Int) = dualScreenManager.moveDualCollectionFocus(delta)
    fun toggleDualCollectionAtFocus() = dualScreenManager.toggleDualCollectionAtFocus()
    fun showDualCollectionCreateDialog() = dualScreenManager.showDualCollectionCreateDialog()
    fun dismissDualCollectionCreateDialog() = dualScreenManager.dismissDualCollectionCreateDialog()
    fun confirmDualCollectionCreate(name: String) = dualScreenManager.confirmDualCollectionCreate(name)
    fun updateDualSaveNameText(text: String) = dualScreenManager.updateDualSaveNameText(text)
    fun confirmDualSaveName() = dualScreenManager.confirmDualSaveName()

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

    // --- Lifecycle ---

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (display != null && display!!.displayId != Display.DEFAULT_DISPLAY) {
            val companionIntent = Intent(this, SecondaryHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(companionIntent)
            finish()
            return
        }

        emulatorSessionPolicy = EmulatorSessionPolicy(
            preferencesRepository = preferencesRepository,
            permissionHelper = permissionHelper,
            sessionStateStore = sessionStateStore,
            displayAffinityHelper = displayAffinityHelper,
            playSessionTracker = playSessionTracker
        )

        if (emulatorSessionPolicy.shouldYieldToEmulator(this, intent)) {
            Log.d(TAG, "Persisted session found - finishing to avoid stealing focus")
            finish()
            return
        }

        enableEdgeToEdge()
        hideSystemUI()

        discordPresenceManager.init(this)

        displayAffinityHelper.dualScreenEnabled = sessionStateStore.isDualScreenEnabled()
        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        isRolesSwapped = resolver.isSwapped
        isDualScreenDevice = displayAffinityHelper.hasSecondaryDisplay
        sessionStateStore.setRolesSwapped(isRolesSwapped)

        val existingDsm = DualScreenManagerHolder.instance
        if (existingDsm != null) {
            dualScreenManager = existingDsm
            dualScreenManager.rebind(this, activityScope)
            dualScreenManager.isRolesSwapped = isRolesSwapped
        } else {
            dualScreenManager = DualScreenManager(
                context = this,
                scope = activityScope,
                gameDao = gameDao,
                platformRepository = platformRepository,
                collectionRepository = collectionRepository,
                downloadQueueDao = downloadQueueDao,
                gameFileDao = gameFileDao,
                downloadManager = downloadManagerInstance,
                gameActionsDelegate = gameActionsDelegate,
                gameLaunchDelegate = gameLaunchDelegate,
                saveCacheManager = saveCacheManager,
                getUnifiedSavesUseCase = getUnifiedSavesUseCase,
                restoreCachedSaveUseCase = restoreCachedSaveUseCase,
                emulatorResolver = emulatorResolver,
                fetchAchievementsUseCase = fetchAchievementsUseCase,
                displayAffinityHelper = displayAffinityHelper,
                sessionStateStore = sessionStateStore,
                preferencesRepository = preferencesRepository,
                syncPreferencesRepository = syncPreferencesRepository,
                edenContentManager = edenContentManager,
                notificationManager = notificationManager,
                emulatorConfigDao = emulatorConfigDao,
                playSessionTracker = playSessionTracker,
                repairImageCacheUseCase = repairImageCacheUseCase,
                isRolesSwapped = isRolesSwapped
            )
            DualScreenManagerHolder.instance = dualScreenManager
        }

        if (isRolesSwapped) {
            dualScreenManager.initSwappedViewModel()
        }

        dualScreenManager.onRoleSwapped = { swapped ->
            isRolesSwapped = swapped
            if (swapped && dualScreenManager.swappedDualHomeViewModel == null) {
                dualScreenManager.initSwappedViewModel()
            }
            updateWindowFocusability()
        }
        dualScreenManager.onDisplayChanged = { hasDisplay ->
            isDualScreenDevice = hasDisplay
        }
        dualScreenManager.onOverlayFocusChanged = { _ ->
            updateWindowFocusability()
        }
        dualScreenManager.onEmulatorDispatcherChanged = {
            updateWindowFocusability()
        }
        dualScreenManager.registerReceivers()
        dualScreenManager.ensureCompanionLaunched()
        initCacheAndPreferences()
        collectLaunchRetryEvents()
        collectSwappedGameState()

        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)

        preferencesObserver = MainActivityPreferencesObserver(
            preferencesRepository = preferencesRepository,
            ambientAudioManager = ambientAudioManager,
            sessionStateStore = sessionStateStore,
            dualScreenManager = dualScreenManager,
            displayAffinityHelper = displayAffinityHelper,
            onDualScreenChanged = { isDualScreenDevice = it },
            hasWindowFocus = ::hasWindowFocus
        )
        preferencesObserver.collectIn(activityScope)

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArgosyApp(
                        isDualScreenDevice = isDualScreenDevice,
                        isRolesSwapped = isRolesSwapped,
                        isCompanionActive = isCompanionActive,
                        dualScreenShowcase = dualScreenShowcase,
                        dualGameDetailState = dualGameDetailState,
                        dualViewMode = dualViewMode,
                        dualCollectionShowcase = dualCollectionShowcase,
                        dualAppBarFocused = dualAppBarFocused,
                        dualDrawerOpen = dualDrawerOpen
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
        Log.d(TAG, "onResume: swapped=$isRolesSwapped gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"} hasResumedBefore=$hasResumedBefore")

        dualScreenManager.broadcastForegroundState(true)

        if (emulatorSessionPolicy.shouldYieldOnResume(
                hasResumedBefore, focusLostTime, packageName
            )
        ) {
            Log.d(TAG, "Persisted session found on resume - yielding to emulator")
            moveTaskToBack(true)
            return
        }

        emulatorSessionPolicy.clearStaleSession(this, dualScreenManager)
        updateWindowFocusability()

        if (::dualScreenManager.isInitialized) {
            val emulatorDisplay = dualScreenManager.emulatorDisplayId
            if (emulatorDisplay != null && emulatorDisplay != display?.displayId) {
                dualScreenManager.restoreEmulatorFocus()
            }
        }

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch { romMRepository.initialize() }
            ambientAudioManager.fadeIn()
        } else {
            if (displayAffinityHelper.hasSecondaryDisplay && !isRolesSwapped) {
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
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "dispatchKeyEvent: key=${event.keyCode} isHome=$isOnHomeScreen swapped=$isRolesSwapped gameOnSecondary=${dualScreenManager.swappedIsGameActive.value} companion=${dualScreenManager.isCompanionActive.value} overlay=$isOverlayFocused")
        }

        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused) {
            val emulatorDispatcher = dualScreenManager.emulatorKeyDispatcher
            if (emulatorDispatcher != null) {
                Log.d(TAG, "dispatchKeyEvent: FORWARDING key=${event.keyCode} to emulator")
                return emulatorDispatcher(event)
            }
            return true
        }

        if (!isRolesSwapped &&
            isOnHomeScreen &&
            dualScreenManager.isCompanionActive.value &&
            !isOverlayFocused
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.d(TAG, "dispatchKeyEvent: FORWARDING key=${event.keyCode} to companion")
                dualScreenManager.companionHost?.onForwardKey(
                    event.keyCode,
                    sessionStateStore.getSwapAB(),
                    sessionStateStore.getSwapXY(),
                    sessionStateStore.getSwapStartSelect()
                )
            }
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "dispatchKeyEvent: LOCAL handling key=${event.keyCode}")
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
        if (dualScreenManager.swappedIsGameActive.value && !isOverlayFocused) {
            val emulatorDispatcher = dualScreenManager.emulatorMotionDispatcher
            if (emulatorDispatcher != null) {
                return emulatorDispatcher(event)
            }
            return true
        }

        val stickEvent = gamepadInputHandler.processStickMotion(event)
        if (stickEvent != null) {
            if (!isRolesSwapped &&
                isOnHomeScreen &&
                dualScreenManager.isCompanionActive.value &&
                !isOverlayFocused
            ) {
                val keyCode = gamepadEventToKeyCode(stickEvent)
                if (keyCode != null) {
                    dualScreenManager.companionHost?.onForwardKey(
                        keyCode,
                        sessionStateStore.getSwapAB(),
                        sessionStateStore.getSwapXY(),
                        sessionStateStore.getSwapStartSelect()
                    )
                }
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
        Log.d(TAG, "onWindowFocusChanged: hasFocus=$hasFocus swapped=$isRolesSwapped gameActive=${if (::dualScreenManager.isInitialized) dualScreenManager.swappedIsGameActive.value else "N/A"}")
        if (hasFocus &&
            ::dualScreenManager.isInitialized &&
            dualScreenManager.swappedIsGameActive.value &&
            !isOverlayFocused
        ) {
            updateWindowFocusability()
            return
        }
        if (hasFocus) {
            val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
            if (hadFocusBefore && focusLostTime > 0 && timeSinceFocusLost < 1000) {
                gamepadInputHandler.emitHomeEvent()
            }
            hadFocusBefore = true
            focusLostTime = 0L
            hideSystemUI()
            window.decorView.requestFocus()
            launchRetryTracker.onFocusGained()
            ambientAudioManager.fadeIn()
            ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
            ambientLedManager.clearInGameColors()
            if (!isOverlayFocused) {
                gamepadInputHandler.blockInputFor(200)
            }
        } else {
            focusLostTime = System.currentTimeMillis()
            launchRetryTracker.onFocusLost()
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

    private fun initCacheAndPreferences() {
        activityScope.launch {
            val prefs = preferencesRepository.preferences.first()
            displayAffinityHelper.dualScreenEnabled = prefs.dualScreenEnabled
            sessionStateStore.setDualScreenEnabled(prefs.dualScreenEnabled)
            isDualScreenDevice = displayAffinityHelper.hasSecondaryDisplay
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)

            val validationResult = imageCacheManager.validateAndCleanCache()
            if (validationResult.deletedFiles > 0 || validationResult.clearedPaths > 0) {
                Log.i(TAG, "Cache validation: ${validationResult.deletedFiles} files deleted, ${validationResult.clearedPaths} paths cleared")
            }

            imageCacheManager.resumePendingCache()
            imageCacheManager.resumePendingCoverCache()
            imageCacheManager.resumePendingLogoCache()
            imageCacheManager.resumePendingBadgeCache()

            if (prefs.ambientLedEnabled) {
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

    private fun collectLaunchRetryEvents() {
        activityScope.launch {
            launchRetryTracker.retryEvents.collect { intent ->
                Log.d(TAG, "Retrying launch intent after quick return")
                startActivity(intent)
            }
        }
    }

    private fun collectSwappedGameState() {
        activityScope.launch {
            dualScreenManager.swappedIsGameActive.collect {
                updateWindowFocusability()
            }
        }
    }

    private fun updateWindowFocusability() {
        val gameActive = dualScreenManager.swappedIsGameActive.value &&
            !isOverlayFocused
        val hasEmulatorDispatcher =
            dualScreenManager.emulatorKeyDispatcher != null
        val shouldBlock = gameActive && !hasEmulatorDispatcher
        Log.d(TAG, "updateWindowFocusability: shouldBlock=$shouldBlock (gameActive=$gameActive hasDispatcher=$hasEmulatorDispatcher)")
        if (shouldBlock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
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

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
