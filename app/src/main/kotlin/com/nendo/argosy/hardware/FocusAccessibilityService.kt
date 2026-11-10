package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
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
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.libretro.slot.GuideKeyRouter
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
import com.nendo.argosy.ui.screens.player.PlayerActivity
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var quickPanelEnabled = false
    @Volatile private var systemHotkeys = SystemHotkeyConfig.DISABLED
    private var homeRoutedToGame = false
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
            foreignAppInFront = { foreground.otherAppInFront && !quickPanelOverlay.isOpen.value },
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
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        keyFilter.reset()
        scope.cancel()
        Log.d(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in HOME_KEYS && routesHomeToGame(event)) return true
        return keyFilter.onKey(event.keyCode, event.action, event.repeatCount)
    }

    private fun routesHomeToGame(event: KeyEvent): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) homeRoutedToGame = GuideKeyRouter.deliver(pressed = true)
            homeRoutedToGame
        }
        KeyEvent.ACTION_UP -> homeRoutedToGame.also { routed ->
            homeRoutedToGame = false
            if (routed) GuideKeyRouter.deliver(pressed = false)
        }
        else -> false
    }

    private fun performSystemHotkey(action: SystemHotkeyAction) {
        val globalAction = when (action) {
            SystemHotkeyAction.HOME -> GLOBAL_ACTION_HOME
            SystemHotkeyAction.BACK -> GLOBAL_ACTION_BACK
        }
        performGlobalAction(globalAction)
    }

    private fun openRunningScreen() {
        runCatching { startActivity(ArgosyHomeIntent.create(this, openRunning = true)) }
            .onFailure { Log.w(TAG, "Argosy running screen could not be shown: ${it.message}") }
    }

    private fun filtersBack(): Boolean {
        if (!quickPanelEnabled || quickPanelOverlay.isOpen.value) return false
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
        private val HOME_KEYS = setOf(KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_MOVE_HOME)

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
