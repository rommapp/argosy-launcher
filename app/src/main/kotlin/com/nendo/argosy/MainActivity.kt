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
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.screens.common.GameActionsDelegate
import com.nendo.argosy.ui.screens.common.GameLaunchDelegate
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.DisplayRoleResolver
import com.nendo.argosy.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val sessionStateStore by lazy {
        com.nendo.argosy.data.preferences.SessionStateStore(this)
    }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var emulatorSessionPolicy: EmulatorSessionPolicy
    private lateinit var preferencesObserver: MainActivityPreferencesObserver

    lateinit var dualScreenManager: DualScreenManager
        private set

    private val _pendingDeepLink = MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: StateFlow<android.net.Uri?> = _pendingDeepLink

    var isRolesSwapped = false
        private set
    private var dualScreenInputFocus = "AUTO"

    // --- Delegated properties for external consumers (ArgosyApp.kt) ---

    var isOverlayFocused: Boolean
        get() = dualScreenManager.isOverlayFocused
        set(value) { dualScreenManager.isOverlayFocused = value }

    val dualScreenShowcase get() = dualScreenManager.dualScreenShowcase
    val dualGameDetailState get() = dualScreenManager.dualGameDetailState
    val isCompanionActive get() = dualScreenManager.isCompanionActive
    val dualViewMode get() = dualScreenManager.dualViewMode
    val dualAppBarFocused get() = dualScreenManager.dualAppBarFocused
    val dualDrawerOpen get() = dualScreenManager.dualDrawerOpen
    val dualCollectionShowcase get() = dualScreenManager.dualCollectionShowcase
    val pendingOverlayEvent get() = dualScreenManager.pendingOverlayEvent
    val swappedDualHomeViewModel get() = dualScreenManager.swappedDualHomeViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        emulatorSessionPolicy = EmulatorSessionPolicy(
            preferencesRepository = preferencesRepository,
            permissionHelper = permissionHelper,
            sessionStateStore = sessionStateStore,
            displayAffinityHelper = displayAffinityHelper
        )

        if (emulatorSessionPolicy.shouldYieldToEmulator(this, intent)) {
            Log.d(TAG, "Persisted session found - finishing to avoid stealing focus")
            finish()
            return
        }

        enableEdgeToEdge()
        hideSystemUI()

        val resolver = DisplayRoleResolver(displayAffinityHelper, sessionStateStore)
        isRolesSwapped = resolver.isSwapped
        sessionStateStore.setRolesSwapped(isRolesSwapped)
        dualScreenInputFocus = sessionStateStore.getDualScreenInputFocus()

        dualScreenManager = DualScreenManager(
            context = this,
            scope = activityScope,
            gameDao = gameDao,
            platformRepository = platformRepository,
            collectionRepository = collectionRepository,
            downloadQueueDao = downloadQueueDao,
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
            isRolesSwapped = isRolesSwapped
        )

        if (isRolesSwapped) {
            dualScreenManager.initSwappedViewModel()
        }

        dualScreenManager.registerReceivers()
        initCacheAndPreferences()
        collectLaunchRetryEvents()

        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)

        preferencesObserver = MainActivityPreferencesObserver(
            preferencesRepository = preferencesRepository,
            ambientAudioManager = ambientAudioManager,
            sessionStateStore = sessionStateStore,
            dualScreenManager = dualScreenManager,
            hasWindowFocus = ::hasWindowFocus
        )
        preferencesObserver.collectIn(activityScope) { focus ->
            dualScreenInputFocus = focus
        }

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArgosyApp(
                        isDualScreenDevice = displayAffinityHelper.hasSecondaryDisplay,
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

    override fun onResume() {
        super.onResume()

        dualScreenManager.broadcastForegroundState(true)

        if (emulatorSessionPolicy.shouldYieldOnResume(
                hasResumedBefore, focusLostTime
            )
        ) {
            Log.d(TAG, "Persisted session found on resume - yielding to emulator")
            moveTaskToBack(true)
            return
        }

        emulatorSessionPolicy.clearStaleSession(this, dualScreenManager)

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch { romMRepository.initialize() }
            ambientAudioManager.fadeIn()
        } else {
            if (displayAffinityHelper.hasSecondaryDisplay && !isRolesSwapped) {
                window.decorView.postDelayed({
                    sendBroadcast(
                        Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
                            .setPackage(packageName)
                    )
                }, 500)
            }
        }
        hasResumedBefore = true
    }

    override fun onPause() {
        super.onPause()
        ambientAudioManager.suspend()
        dualScreenManager.broadcastForegroundState(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager.stopCapture()
        activityScope.cancel()
        dualScreenManager.unregisterReceivers()
    }

    // --- Input Dispatch ---

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dualScreenManager.isCompanionActive.value &&
            !isRolesSwapped &&
            !isOverlayFocused &&
            dualScreenInputFocus == "TOP"
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_FORWARD_KEY).apply {
                        setPackage(packageName)
                        putExtra(DualScreenBroadcasts.EXTRA_KEY_CODE, event.keyCode)
                    }
                )
            }
            return true
        }
        if (dualScreenInputFocus == "BOTTOM") return super.dispatchKeyEvent(event)
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
            if (dualScreenManager.isCompanionActive.value &&
                !isOverlayFocused &&
                !isRolesSwapped
            ) {
                sendBroadcast(
                    Intent(DualScreenBroadcasts.ACTION_REFOCUS_LOWER)
                        .setPackage(packageName)
                )
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadInputHandler.handleMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // --- Window Focus ---

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
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
        }
    }

    // --- Private Helpers ---

    private fun initCacheAndPreferences() {
        activityScope.launch {
            val prefs = preferencesRepository.preferences.first()
            imageCacheManager.setCustomCachePath(prefs.imageCachePath)

            val validationResult = imageCacheManager.validateAndCleanCache()
            if (validationResult.deletedFiles > 0 || validationResult.clearedPaths > 0) {
                Log.i(TAG, "Cache validation: ${validationResult.deletedFiles} files deleted, ${validationResult.clearedPaths} paths cleared")
            }

            imageCacheManager.resumePendingCache()
            imageCacheManager.resumePendingCoverCache()
            imageCacheManager.resumePendingLogoCache()
            imageCacheManager.resumePendingBadgeCache()

            if (prefs.ambientLedEnabled &&
                !screenCaptureManager.hasPermission.value &&
                !screenCapturePromptedThisSession
            ) {
                screenCapturePromptedThisSession = true
                screenCaptureManager.requestPermission(
                    this@MainActivity, screenCaptureLauncher
                )
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
