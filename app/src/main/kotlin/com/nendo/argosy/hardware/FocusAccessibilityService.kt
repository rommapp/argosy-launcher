package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.ArgosyHomeIntent
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.HomeKeys
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.libretro.slot.ResumedGameRouter
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
import com.nendo.argosy.ui.screens.running.ResumeOverlay
import com.nendo.argosy.ui.screens.player.PlayerActivity
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.SecondaryHomeComponent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var playSessionTracker: PlaySessionTracker
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var quickPanelOverlay: QuickPanelOverlay
    @Inject lateinit var resumeOverlay: ResumeOverlay
    @Inject lateinit var backgroundGames: BackgroundGames

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var quickPanelEnabled = false
    @Volatile private var homeButtonInGames = true
    @Volatile private var systemHotkeys = SystemHotkeyConfig.DISABLED
    private var homeRoutedToGame = false
    private var homeClosedResume = false
    private var homeTakenFromGame = false
    private var homeHoldFired = false
    private val homeHold = Runnable {
        homeHoldFired = true
        openRunningScreen()
    }
    private val foreground by lazy {
        ForegroundAppTracker(
            ownPackage = packageName,
            inputSurfaces = ARGOSY_INPUT_SURFACES,
            imePackage = ::currentImePackage
        )
    }
    private val keyFilter = SystemKeyFilter(
        backGate = BackHoldGate(
            filtering = ::filtersBack,
            scheduleHold = { handler.postDelayed(it, BACK_HOLD_MS) },
            cancelHold = handler::removeCallbacks,
            onTap = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onHold = { quickPanelOverlay.show(gameDisplayId(), resultOwner = null) }
        ),
        hotkeys = SystemHotkeyDetector(
            config = { systemHotkeys },
            foreignAppInFront = {
                foreground.otherAppInFront && !quickPanelOverlay.isOpen.value && !resumeOverlay.isOpen.value
            },
            scheduleHold = { task, delayMs -> handler.postDelayed(task, delayMs) },
            cancelHold = handler::removeCallbacks,
            perform = ::performSystemHotkey,
            quickPanelAvailable = ::filtersBack,
            openQuickPanel = { quickPanelOverlay.show(gameDisplayId(), resultOwner = null) },
            openRunning = ::openRunningScreen
        )
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val source = event.packageName?.toString() ?: return
        foreground.onWindowStateChanged(source, event.className?.toString())
        Logger.info(
            TAG,
            "window $source/${event.className} foreground=${foreground.foregroundPackage} " +
                "otherAppInFront=${foreground.otherAppInFront}"
        )
    }

    private fun currentImePackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scope.launch {
            preferencesRepository.userPreferences
                .map { it.quickPanelInEmulators }
                .distinctUntilChanged()
                .collect { quickPanelEnabled = it }
        }
        scope.launch {
            preferencesRepository.userPreferences
                .map { it.homeButtonInGames }
                .distinctUntilChanged()
                .collect { homeButtonInGames = it }
        }
        scope.launch {
            preferencesRepository.userPreferences
                .map { prefs ->
                    SystemHotkeyConfig.resolve(
                        enabled = prefs.systemHotkeysEnabled,
                        customHome = prefs.systemHomeCombo,
                        customBack = prefs.systemBackCombo,
                        swapAB = prefs.swapAB,
                        swapStartSelect = prefs.swapStartSelect
                    )
                }
                .distinctUntilChanged()
                .collect { systemHotkeys = it }
        }
        Logger.info(TAG, "Accessibility service connected in $packageName")
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(homeHold)
        keyFilter.reset()
        scope.cancel()
        Logger.info(TAG, "Accessibility service destroyed in $packageName")
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val systemHome = HomeKeys.isSystemHome(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (systemHome || event.keyCode == KeyEvent.KEYCODE_BACK)
        ) {
            Logger.info(TAG, "gates ${gateSnapshot()}")
        }
        val consumedBy = when {
            systemHome && closesResumeOverlay(event) -> "resumeOverlay"
            systemHome && routesHomeToGame(event) -> "builtInGame"
            systemHome && takesHomeFromExternalGame(event) -> "externalGame"
            keyFilter.onKey(event.keyCode, event.action, event.repeatCount) -> "keyFilter"
            else -> null
        }
        if (event.repeatCount == 0 && logsKey(event, systemHome, consumedBy)) {
            Logger.info(
                TAG,
                "key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                    "${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"} " +
                    "device=${event.device?.name} systemHome=$systemHome consumedBy=${consumedBy ?: "none"}"
            )
        }
        return consumedBy != null
    }

    private fun logsKey(event: KeyEvent, systemHome: Boolean, consumedBy: String?): Boolean =
        systemHome || consumedBy != null || event.keyCode == KeyEvent.KEYCODE_BACK ||
            KeyEvent.isGamepadButton(event.keyCode)

    private fun gateSnapshot(): String {
        val session = playSessionTracker.activeSession.value
        return "package=$packageName root=${RootShell.isAvailable} " +
            "defaultHome=${SecondaryHomeComponent.isDefaultHome(this)} " +
            "otherAppInFront=${foreground.otherAppInFront} foreground=${foreground.foregroundPackage} " +
            "session=${session?.emulatorPackage} sessionInFront=${externalSessionInFront()} " +
            "gameDisplay=${gameDisplayId()} " +
            "keyguard=${getSystemService(KeyguardManager::class.java)?.isKeyguardLocked} " +
            "quickPanelInEmulators=$quickPanelEnabled homeButtonInGames=$homeButtonInGames quickPanelOpen=${quickPanelOverlay.isOpen.value} " +
            "resumeOpen=${resumeOverlay.isOpen.value} systemHotkeys=$systemHotkeys"
    }

    private fun takesHomeFromExternalGame(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) {
                homeTakenFromGame = externalGameHoldsHome()
                homeHoldFired = false
                handler.removeCallbacks(homeHold)
                if (homeTakenFromGame) {
                    handler.postDelayed(homeHold, SystemHotkeyDetector.GUIDE_HOLD_MS)
                    scope.launch { backgroundGames.withTransitionsOff {} }
                }
            }
            homeTakenFromGame
        }
        KeyEvent.ACTION_UP -> (homeTakenFromGame && homeHoldFired).also {
            homeTakenFromGame = false
            handler.removeCallbacks(homeHold)
            homeHoldFired = false
        }
        else -> false
    }

    private fun externalGameHoldsHome(): Boolean {
        if (!homeButtonInGames || !RootShell.isAvailable) return false
        if (!foreground.otherAppInFront || !externalSessionInFront()) return false
        if (gameDisplayId() != Display.DEFAULT_DISPLAY) return false
        if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false) return false
        return SecondaryHomeComponent.isDefaultHome(this)
    }

    private fun showArgosyWithoutTransition() {
        scope.launch {
            val shown = backgroundGames.withTransitionsOff { startArgosy(openRunning = false) }
            if (!shown) performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun startArgosy(openRunning: Boolean): Boolean =
        runCatching { startActivity(ArgosyHomeIntent.create(this, openRunning)) }
            .onFailure { Log.w(TAG, "Argosy could not be shown: ${it.message}") }
            .isSuccess

    private fun closesResumeOverlay(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0 && resumeOverlay.isOpen.value) {
                homeClosedResume = true
                resumeOverlay.hide()
            }
            homeClosedResume || (resumeOverlay.isOpen.value && !homeRoutedToGame)
        }
        KeyEvent.ACTION_UP -> homeClosedResume.also { homeClosedResume = false }
        else -> false
    }

    private fun routesHomeToGame(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) homeRoutedToGame = ResumedGameRouter.deliverGuideKey(pressed = true)
            homeRoutedToGame
        }
        KeyEvent.ACTION_UP -> homeRoutedToGame.also { routed ->
            homeRoutedToGame = false
            if (routed) ResumedGameRouter.deliverGuideKey(pressed = false)
        }
        else -> false
    }

    private fun performSystemHotkey(action: SystemHotkeyAction) {
        Logger.info(TAG, "system hotkey $action")
        when (action) {
            SystemHotkeyAction.HOME ->
                if (externalGameHoldsHome()) showArgosyWithoutTransition() else performGlobalAction(GLOBAL_ACTION_HOME)
            SystemHotkeyAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun openRunningScreen() {
        if (resumeOverlay.show(gameDisplayId())) return
        scope.launch { backgroundGames.withTransitionsOff { startArgosy(openRunning = true) } }
    }

    private fun filtersBack(): Boolean = quickPanelEnabled && externalSessionInFront()

    private fun externalSessionInFront(): Boolean {
        if (quickPanelOverlay.isOpen.value || resumeOverlay.isOpen.value) return false
        val session = playSessionTracker.activeSession.value ?: return false
        if (session.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return false
        return foreground.foregroundPackage == session.emulatorPackage
    }

    private fun gameDisplayId(): Int =
        DualScreenManagerHolder.instance?.emulatorDisplayId ?: Display.DEFAULT_DISPLAY

    @SuppressLint("NewApi")
    fun tapOnDisplay(displayId: Int) {
        val path = Path().apply { moveTo(1f, 1f) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .setDisplayId(displayId)
            .build()
        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Focus tap completed on display $displayId")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Focus tap cancelled on display $displayId")
            }
        }, null)
        Log.d(TAG, "Focus tap dispatched=$dispatched on display $displayId")
    }

    /**
     * A silent capture of [displayId], with no flash, sound or consent prompt, as a software
     * bitmap; null before Android 11 or when the system refuses, such as within its rate limit.
     */
    suspend fun captureDisplay(displayId: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(displayId, Dispatchers.IO.asExecutor(), object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = runCatching {
                        screenshot.hardwareBuffer.use { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { wrapped ->
                                wrapped.copy(Bitmap.Config.ARGB_8888, false).also { wrapped.recycle() }
                            }
                        }
                    }.onFailure { Log.w(TAG, "Screenshot of display $displayId unreadable", it) }.getOrNull()
                    continuation.resume(bitmap) { bitmap?.recycle() }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot of display $displayId refused: $errorCode")
                    continuation.resume(null)
                }
            })
        }
    }

    companion object {
        private const val TAG = "FocusA11y"
        private const val BACK_HOLD_MS = 500L

        private val ARGOSY_INPUT_SURFACES = setOf(
            ArgosyActivity::class.java.name,
            LibretroActivity::class.java.name,
            PlayerActivity::class.java.name
        )

        var instance: FocusAccessibilityService? = null
            private set

        /**
         * Whether Argosy's accessibility service is switched on in the system accessibility
         * settings, whether or not the system has bound it yet.
         */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val component = ComponentName(context, FocusAccessibilityService::class.java)
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == component
            }
        }
    }
}
