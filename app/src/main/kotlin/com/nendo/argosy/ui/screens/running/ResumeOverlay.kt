package com.nendo.argosy.ui.screens.running

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nendo.argosy.ArgosyHomeIntent
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ComposeWindowOwner
import com.nendo.argosy.ui.common.LocalImageCacheManager
import com.nendo.argosy.ui.components.FooterHost
import com.nendo.argosy.ui.components.FooterHostController
import com.nendo.argosy.ui.components.LocalFooterHost
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.GamepadInput
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.LONG_PRESS_THRESHOLD_MS
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalModalPresence
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.buttonGlyphSwaps
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.ThemeViewModel
import com.nendo.argosy.util.hideWindowSystemBars
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.graphics.Bitmap
import com.nendo.argosy.hardware.FocusAccessibilityService
import com.nendo.argosy.data.emulator.background.TaskSnapshots
import com.nendo.argosy.ui.common.BlurredBackdrop
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ResumeOverlay"
private const val THEME_WAIT_MS = 750L
private const val WARM_FRAMES = 2
private const val CAPTURE_WAIT_MS = 400L
private const val FRONT_TASK_WAIT_MS = 5_000L

/**
 * Argosy's Resume list drawn in a window of its own over a running game, so the player can switch
 * games without bringing Argosy forward. Main process only.
 */
@Singleton
class ResumeOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controllerFactory: RunningAppsController.Factory,
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientLedManager: AmbientLedManager,
    private val controllerTracker: ConnectedControllerTracker,
    private val hapticManager: HapticFeedbackManager,
    private val soundManager: SoundFeedbackManager,
    private val imageCacheManager: ImageCacheManager
) {
    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private var session: Session? = null
    private val launchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Opens the overlay over [displayId], or joins the one already open. Main thread only.
     * True: [onClosed] runs once, when the overlay closes. False: the draw-over-apps permission is
     * missing or the window was refused, and [onClosed] never runs.
     */
    fun show(displayId: Int, onClosed: () -> Unit = {}): Boolean {
        session?.let { open ->
            open.closeListeners += onClosed
            return true
        }
        if (!Settings.canDrawOverlays(context)) return false
        val windowContext = windowContextFor(displayId) ?: return false
        val next = Session(windowContext, displayId)
        if (!next.attach()) {
            next.detach()
            return false
        }
        next.closeListeners += onClosed
        session = next
        _isOpen.value = true
        soundManager.play(SoundType.OPEN_MODAL)
        return true
    }

    fun hide() {
        session?.let(::close)
    }

    private fun close(target: Session) {
        if (session !== target) return
        session = null
        _isOpen.value = false
        soundManager.play(SoundType.CLOSE_MODAL)
        target.leave(instant = target.isSwitching()) {
            target.detach()
            target.closeListeners.forEach { it() }
        }
    }

    private fun windowContextFor(displayId: Int): Context? {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
        val display = displayManager.getDisplay(displayId)
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        val displayContext = context.createDisplayContext(display)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
    }

    private inner class Session(private val windowContext: Context, private val displayId: Int) {
        val closeListeners = mutableListOf<() -> Unit>()
        private val screenBehind = MutableStateFlow<Bitmap?>(null)
        private var captureJob: Job? = null
        private val leaving = MutableStateFlow(false)
        private var onLeft: (() -> Unit)? = null

        fun isSwitching(): Boolean = controller.uiState.value.resumingTaskId != null

        fun leave(instant: Boolean, then: () -> Unit) {
            if (instant || !shown.value) {
                then()
                return
            }
            onLeft = then
            leaving.value = true
        }

        private fun finishLeaving() {
            val then = onLeft ?: return
            onLeft = null
            then()
        }
        private val owner = ComposeWindowOwner()
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val prefs: StateFlow<UserPreferences> =
            preferencesRepository.userPreferences.stateIn(scope, SharingStarted.Eagerly, UserPreferences())
        private val dispatcher = InputDispatcher(hapticManager, soundManager)
        private val footerHost = FooterHostController()
        private val controller = controllerFactory.create(
            scope = scope,
            launchScope = launchScope,
            dismissOverlay = { close(this@Session) }
        )
        private val handler = controller.createInputHandler(onBack = { close(this@Session) })
        private val theme = ViewModelProvider(owner.viewModelStore, ThemeFactory())[ThemeViewModel::class.java]
        private val shown = MutableStateFlow(false)
        private val root = KeyRoot(windowContext)
        private val windowManager = windowContext.getSystemService(WindowManager::class.java)
        private var confirmHold: Job? = null
        private var confirmHeld = false

        fun attach(): Boolean {
            captureJob = scope.launch {
                val shot = captureScreenBehind() ?: return@launch
                screenBehind.value = shot
                controller.showFrontFrame(shot)
                scope.launch { keepAsFrontFrame(shot) }
            }
            owner.start()
            dispatcher.subscribeView(handler)
            controller.onVisible()
            scope.launch { controller.openGame.collect { gameId -> openInArgosy(gameId) } }
            val view = ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setContent { OverlayContent() }
            }
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            root.addView(view)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            return runCatching {
                windowManager?.addView(root, params) ?: return false
                root.requestFocus()
                root.hideWindowSystemBars()
                true
            }.onFailure { Log.w(TAG, "addView failed", it) }.getOrDefault(false)
        }

        fun detach() {
            controller.onHidden()
            confirmHold?.cancel()
            runCatching { if (root.isAttachedToWindow) windowManager?.removeView(root) }
                .onFailure { Log.w(TAG, "removeView failed", it) }
            owner.stop()
            scope.cancel()
        }

        private suspend fun keepAsFrontFrame(shot: Bitmap) {
            val taskId = withTimeoutOrNull(FRONT_TASK_WAIT_MS) {
                controller.uiState.first { !it.isLoading }.frontTaskId
            } ?: return
            withContext(Dispatchers.IO) { TaskSnapshots.saveGameFrame(context, taskId, shot) }
        }

        private suspend fun captureScreenBehind(): Bitmap? {
            val full = FocusAccessibilityService.instance?.captureDisplay(displayId) ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return full
            return withContext(Dispatchers.IO) {
                val scale = ComponentDefaults.ResumeOverlay.backdropDownscale.toInt()
                val small = Bitmap.createScaledBitmap(
                    full,
                    (full.width / scale).coerceAtLeast(1),
                    (full.height / scale).coerceAtLeast(1),
                    true
                )
                if (small !== full) full.recycle()
                small
            }
        }

        private fun openInArgosy(gameId: Long) {
            close(this)
            runCatching { context.startActivity(ArgosyHomeIntent.gameDetail(context, gameId)) }
                .onFailure { Log.w(TAG, "Argosy could not open game $gameId: ${it.message}") }
        }

        private fun onConfirmKey(event: KeyEvent): Boolean {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                    confirmHeld = true
                    confirmHold?.cancel()
                    confirmHold = scope.launch {
                        delay(LONG_PRESS_THRESHOLD_MS)
                        if (!confirmHeld) return@launch
                        confirmHeld = false
                        dispatcher.dispatch(GamepadInput(GamepadEvent.LongConfirm))
                    }
                }
                KeyEvent.ACTION_UP -> if (confirmHeld) {
                    confirmHeld = false
                    confirmHold?.cancel()
                    dispatcher.dispatch(GamepadInput(GamepadEvent.Confirm))
                }
            }
            return true
        }

        @Composable
        private fun OverlayContent() {
            val preferences by prefs.collectAsState()
            val activeLayout by controllerTracker.activeLayout.collectAsState()
            val swaps = remember(preferences, activeLayout) { preferences.buttonGlyphSwaps(activeLayout) }
            val isShown by shown.collectAsState()
            val isLeaving by leaving.collectAsState()
            val behind by screenBehind.collectAsState()
            val reduced = LocalMotionTier.current == MotionTier.Reduced
            val presence by animateFloatAsState(
                targetValue = if (isShown && !isLeaving) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (reduced) Motion.durationContent else MotionTokens.Tween.drawerMs,
                    easing = Motion.argosyEase
                ),
                label = "resumeOverlayPresence",
                finishedListener = { value -> if (value == 0f && leaving.value) finishLeaving() }
            )
            LaunchedEffect(Unit) {
                withTimeoutOrNull(THEME_WAIT_MS) { theme.themeState.drop(1).first() }
                withTimeoutOrNull(CAPTURE_WAIT_MS) { captureJob?.join() }
                repeat(WARM_FRAMES) { withFrameNanos { } }
                shown.value = true
            }

            ALauncherTheme(viewModel = theme) {
                CompositionLocalProvider(
                    LocalInputDispatcher provides dispatcher,
                    LocalModalPresence provides dispatcher,
                    LocalABIconsSwapped provides swaps.ab,
                    LocalXYIconsSwapped provides swaps.xy,
                    LocalSwapStartSelect provides swaps.startSelect,
                    LocalFooterHost provides footerHost,
                    LocalImageCacheManager provides imageCacheManager
                ) {
                    Box(modifier = Modifier.fillMaxSize().alpha(if (isShown) 1f else 0f)) {
                        if (behind != null) {
                            BlurredBackdrop(model = behind, strength = presence)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(presence)
                                    .background(LocalArgosyTheme.current.surfaceBase)
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize().alpha(presence)) {
                            RunningAppsContent(controller = controller, showsGameBackdrop = false)
                            FooterHost(controller = footerHost, modifier = Modifier.align(Alignment.BottomCenter))
                        }
                    }
                }
            }
        }

        private inner class KeyRoot(context: Context) : FrameLayout(context) {
            init {
                isFocusable = true
                isFocusableInTouchMode = true
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) hideWindowSystemBars()
            }

            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (!shown.value || leaving.value) return true
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) close(this@Session)
                    return true
                }
                val swaps = prefs.value
                val gamepadEvent = mapKeycodeToGamepadEvent(
                    event.keyCode,
                    swaps.swapAB,
                    swaps.swapXY,
                    swaps.swapStartSelect
                ) ?: return super.dispatchKeyEvent(event)
                if (gamepadEvent == GamepadEvent.Confirm) return onConfirmKey(event)
                if (event.action != KeyEvent.ACTION_DOWN) return true
                if (gamepadEvent == GamepadEvent.Back && event.repeatCount > 0) return true
                dispatcher.dispatch(GamepadInput(gamepadEvent, isRepeat = event.repeatCount > 0))
                return true
            }
        }
    }

    private inner class ThemeFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            ThemeViewModel::class.java -> ThemeViewModel(
                preferencesRepository = preferencesRepository,
                ambientLedManager = ambientLedManager,
                connectedControllerTracker = controllerTracker
            )
            else -> throw IllegalArgumentException("No resume overlay factory for ${modelClass.name}")
        } as T
    }
}
