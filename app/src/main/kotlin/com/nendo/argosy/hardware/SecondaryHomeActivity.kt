package com.nendo.argosy.hardware

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.ui.theme.CustomFontFamilies
import com.nendo.argosy.ui.theme.CustomFontLoader
import com.nendo.argosy.ui.theme.ThemeState
import com.nendo.argosy.ui.theme.toThemeState
import com.nendo.argosy.ui.toScreenDimmerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.ScreenDimmerPreferences
import com.nendo.argosy.ui.components.ScreenDimmerOverlay
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.CompanionDetailScreen
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SelectModifier
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.hideSystemBars
import com.nendo.argosy.util.installImmersiveMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@dagger.hilt.android.AndroidEntryPoint
class SecondaryHomeActivity :
    ComponentActivity(),
    DualScreenManager.CompanionHost {

    private lateinit var dsm: DualScreenManager

    @javax.inject.Inject
    lateinit var gamepadInputHandler: com.nendo.argosy.ui.input.GamepadInputHandler

    private var languageTag: String = com.nendo.argosy.core.locale.LocaleHelper.SYSTEM_LANGUAGE_TAG

    override fun attachBaseContext(newBase: android.content.Context) {
        languageTag = SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(
            com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, languageTag)
        )
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        super.applyOverrideConfiguration(
            com.nendo.argosy.core.locale.LocaleHelper.overrideConfiguration(
                overrideConfiguration,
                languageTag
            )
        )
    }

    private var launchedExternalApp = false

    private var isInitialized by mutableStateOf(false)
    var isArgosyForeground by mutableStateOf(false)
        private set
    var isGameActive by mutableStateOf(false)
        private set
    private var isWizardActive by mutableStateOf(false)
    private var currentChannelName by mutableStateOf<String?>(null)
    private var isHardcore by mutableStateOf(false)
    var homeApps by mutableStateOf<List<String>>(emptyList())
        private set
    private var companionAchievements by mutableStateOf<List<AchievementUi>>(emptyList())
    private var companionSessionTimer: CompanionSessionTimer? = null
    private var homeRestoreSettled = false

    private var confirmHoldJob: kotlinx.coroutines.Job? = null
    private var confirmHoldFired = false
    private var mediaDimCollectJob: kotlinx.coroutines.Job? = null

    private var isMediaPanelVisible by mutableStateOf(false)
    var isShowcaseRole by mutableStateOf(false)
        private set

    var swapAB = false; private set
    var swapXY = false; private set
    var swapStartSelect = false; private set

    private val selectModifier = SelectModifier()

    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installImmersiveMode()
        keepAwakeWhileUserActive()

        if (!SessionStateStore(applicationContext).isDualScreenEnabled()) {
            android.util.Log.d("SecondaryHome", "dualScreenEnabled=false, finishing")
            finish()
            return
        }

        if (releaseUnsupportedDisplay()) return

        val existing = DualScreenManagerHolder.instance
        if (existing != null) {
            dsm = existing
            initializeCompanion()
        } else if (!com.nendo.argosy.util.SecondaryHomeComponent.isDefaultHome(this)) {
            android.util.Log.i("SecondaryHome", "Respawned without a running Argosy and not default home, releasing secondary display")
            com.nendo.argosy.util.SecondaryHomeComponent.setEnabled(this, false)
            CompanionGuardService.stop(this)
            finish()
            return
        } else if (sessionHoldsThisDisplay()) {
            android.util.Log.i(
                "SecondaryHome",
                "Respawned behind a running session on this display, waiting for it to end"
            )
            awaitSessionEnd()
        } else {
            recoverThroughMainActivity()
        }

        setContent {
            val themeState = remember { mutableStateOf(ThemeState()) }
            val customFonts = remember { mutableStateOf(CustomFontFamilies()) }
            val screenDimmerPrefs = remember { mutableStateOf(ScreenDimmerPreferences()) }
            val statusBarItems = remember {
                mutableStateOf(com.nendo.argosy.ui.components.StatusBarItems())
            }
            LaunchedEffect(isInitialized) {
                if (!isInitialized) return@LaunchedEffect
                dsm.preferencesRepository.userPreferences.collect { prefs ->
                    themeState.value = prefs.toThemeState()
                    customFonts.value = resolveCustomFonts(prefs.displayFontPath, prefs.bodyFontPath)
                    screenDimmerPrefs.value = prefs.toScreenDimmerPreferences()
                    statusBarItems.value = com.nendo.argosy.ui.components.statusBarItemsOf(prefs)
                }
            }
            LaunchedEffect(isInitialized) {
                if (!isInitialized) return@LaunchedEffect
                dsm.localeChangeToken.drop(1).collect { recreate() }
            }
            SecondaryHomeTheme(themeState = themeState.value, fonts = customFonts.value) {
                if (!isInitialized) return@SecondaryHomeTheme
                val scrapingArtwork by dsm.imageCacheManager.progress
                    .collectAsState()
                    .let { state ->
                        androidx.compose.runtime.remember {
                            androidx.compose.runtime.derivedStateOf { state.value.isProcessing }
                        }
                    }
                val lastUserActivityAtMs by dsm.lastUserActivityAtMs.collectAsState()
                val mediaPlayback by dsm.mediaPlayback.collectAsState()
                val dimmerPrefs = screenDimmerPrefs.value
                val dimmerEnabled = dimmerPrefs.enabled &&
                    !isGameActive && !isWizardActive && mediaPlayback == null
                androidx.compose.runtime.CompositionLocalProvider(
                    com.nendo.argosy.ui.components.LocalArtworkScraping provides scrapingArtwork,
                    com.nendo.argosy.ui.components.LocalStatusBarItems provides statusBarItems.value
                ) {
                    com.nendo.argosy.ui.input.ProvideButtonGlyphs(dsm.preferencesRepository.userPreferences) {
                        ScreenDimmerOverlay(
                            enabled = dimmerEnabled,
                            timeoutMs = dimmerPrefs.timeoutMinutes * 60_000L,
                            dimLevel = dimmerPrefs.level / 100f,
                            lastActivityAtMs = lastUserActivityAtMs,
                            onWake = { dsm.notifyUserActivity("companionDimmerTap") }
                        ) {
                            CompanionRoleContent()
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun CompanionRoleContent() {
        val gameActive by dsm.swappedIsGameActive.collectAsState()
        val hereDisplayId = androidx.core.content.ContextCompat.getDisplayOrDefault(this).displayId
        val showsDashboard = gameActive && dsm.primaryShowsDashboard(hereDisplayId)
        if (!isShowcaseRole && !showsDashboard) {
            com.nendo.argosy.ui.ArgosyApp(
                onStartupComplete = { dsm.stopStartupGuard() }
            )
            return
        }

        val slot by dsm.presentationSlot.collectAsState()
        val presentationSink = androidx.compose.runtime.remember {
            androidx.compose.ui.focus.FocusRequester()
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { presentationSink.requestFocus() }
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .focusRequester(presentationSink)
                .focusable()
        ) {
            com.nendo.argosy.ui.dualscreen.PresentationSlotContent(slot)
        }
    }

    override fun onResume() {
        super.onResume()
        if (releaseUnsupportedDisplay()) return
        window.hideSystemBars()
        if (!::dsm.isInitialized) return
        val currentDsm = DualScreenManagerHolder.instance
        if (currentDsm != null && dsm !== currentDsm) {
            android.util.Log.w("SecondaryHome", "DSM stale, reconnecting to new instance")
            dsm = currentDsm
            initializeCompanion()
        }
        launchedExternalApp = false
        syncFromSessionStore()
        dsm.onCompanionResumed()
        endSessionIfEmulatorGone()
    }

    private fun syncFromSessionStore() {
        val store = dsm.sessionStateStore
        store.setForeignAppOnSecondary(false)
        isGameActive = store.hasActiveSession()
        isHardcore = store.isHardcore()
        currentChannelName = store.getChannelName()
    }

    /**
     * Ends a session whose emulator has left this display. The companion getting its display back is
     * the one event that says a game running on it is over, and it is the only one there is: nothing
     * else observes an emulator the launcher does not own.
     */
    private fun endSessionIfEmulatorGone() {
        if (!isGameActive || dsm.isLaunchingGame) return
        val emulatorDisplay = dsm.emulatorDisplayId ?: return
        val ownDisplay = window.decorView.display?.displayId ?: return
        if (emulatorDisplay != ownDisplay) return
        if (dsm.isEmulatorStillOnScreen(this)) return
        android.util.Log.d("SecondaryHome", "Companion resumed and the emulator is gone, ending session")
        dsm.emulatorDisplayId = null
        dsm.playSessionTracker.endSessionInBackground()
        dsm.broadcastSessionCleared()
    }

    override fun onStop() {
        super.onStop()
        gamepadInputHandler.resetStickMotion()
        if (::dsm.isInitialized) dsm.onCompanionPaused()
    }

    override fun finishCompanion() {
        runOnUiThread { finish() }
    }

    override fun onDestroy() {
        if (::dsm.isInitialized) dsm.unregisterCompanionHost(hostDisplayId(), this)
        displayListener?.let {
            getSystemService(DisplayManager::class.java)
                .unregisterDisplayListener(it)
        }
        displayListener = null
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
        super.onDestroy()
    }

    private var mediaDimSwallowsTouch = false

    /**
     * A gesture that begins while the media dim holds this window, at either stage, is a wake and
     * nothing else: it is swallowed through its final up or cancel so the content underneath never
     * sees it. The swallowed up still runs the refocus handoff, because the touch has already
     * moved window focus here and key focus must land where any other tap would leave it.
     */
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
            val dimmed = isMediaDimmed()
            if (::dsm.isInitialized) {
                dsm.notifyUserActivity("companionTouchDown")
            }
            if (dimmed) {
                mediaDimSwallowsTouch = true
                Logger.debug(
                    DualScreenManager.MEDIA_DIM_LOG_TAG,
                    "wakeTouch swallowed window=companion " +
                        "brightness=${window.attributes.screenBrightness}"
                )
            }
        }
        if (mediaDimSwallowsTouch) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_UP -> {
                    mediaDimSwallowsTouch = false
                    refocusAfterTouch()
                }
                android.view.MotionEvent.ACTION_CANCEL -> mediaDimSwallowsTouch = false
            }
            return true
        }
        val result = super.dispatchTouchEvent(event)
        if (event.action == android.view.MotionEvent.ACTION_UP) {
            refocusAfterTouch()
        }
        return result
    }

    private fun refocusAfterTouch() {
        if (isGameActive && ::dsm.isInitialized) {
            window.decorView.post { dsm.refocusSession() }
        } else if (isShowcaseRole) {
            window.decorView.post { dsm.onRefocusUpper() }
        }
    }

    /**
     * The media dim is the sole writer of this window's brightness override, so an override in
     * place is exactly "this window is currently dimmed", at the partial stage as well as the dark
     * one.
     */
    private fun isMediaDimmed(): Boolean =
        window.attributes.screenBrightness !=
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

    /**
     * Gamepad keys are taken before the view hierarchy sees them, exactly as the primary activity
     * does it.
     *
     * onKeyDown is the fallback Android calls only once every view has declined the key, so a single
     * focused composable anywhere on this display silently owns the d-pad - after closing the app
     * drawer its grid held focus, and eight presses went into walking out of it before the carousel
     * saw one. The launcher decides selection itself, so the window has no business consulting focus
     * first; typing is the sole exception, and it says so.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (yieldsKeysToMediaPlayer()) {
            val forward = dsm.mediaPlayerKeyDispatcher
            if (forward != null) {
                if (forward(event)) return true
                return super.dispatchKeyEvent(event)
            }
        }
        if (::dsm.isInitialized && !dsm.companionHoldsPrimary.value) {
            handBackToPrimaryScreen()
            return true
        }
        if (::dsm.isInitialized && !dsm.claimInput(event)) return true
        if (event.keyCode == android.view.KeyEvent.KEYCODE_HOME ||
            event.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_MODE
        ) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                gamepadInputHandler.emitHomeEvent()
            }
            return true
        }
        if (gamepadInputHandler.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /**
     * The companion window normally has no use for generic motion, but while it holds input focus
     * during a playback the trigger axes land here instead of on the player, and L2/R2 would go
     * dead. The raw event is forwarded so the player's own axis-to-key conversion runs - the same
     * one a directly-delivered motion event gets.
     */
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val isJoystick = event.isFromSource(android.view.InputDevice.SOURCE_CLASS_JOYSTICK)
        if (isJoystick && ::dsm.isInitialized && !dsm.companionHoldsPrimary.value) {
            gamepadInputHandler.resetStickMotion()
            handBackToPrimaryScreen()
            return true
        }
        if (isJoystick && ::dsm.isInitialized && !dsm.claimInput(event)) {
            android.util.Log.d("SecondaryHome", "Joystick motion already handled by the primary display, dropped")
            return true
        }
        if (yieldsKeysToMediaPlayer()) {
            val forward = dsm.mediaPlayerMotionDispatcher
            if (forward != null && forward(event)) {
                gamepadInputHandler.resetStickMotion()
                return true
            }
        }
        if (gamepadInputHandler.processStickMotion(event, ::deliverStickEvent)) return true
        if (gamepadInputHandler.handleMotionEvent(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun deliverStickEvent(stickEvent: GamepadEvent, isRepeat: Boolean) {
        gamepadInputHandler.injectEvent(stickEvent, isRepeat)
    }

    private var lastHandBackAtMs = 0L

    private fun handBackToPrimaryScreen() {
        if (dsm.hasLiveSession()) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastHandBackAtMs < HAND_BACK_THROTTLE_MS) return
        lastHandBackAtMs = now
        dsm.onRefocusUpper()
    }

    /**
     * Whether the pad belongs to the video player right now. The yield happens only while the
     * media panel is the surface this display is showing, decided by the same predicate the
     * renderer asks, so any other screen the companion draws keeps its own controller input.
     * While the panel is up over a live playback the panel is touch-only, and every key and
     * trigger this window receives is handed to the player's own dispatch instead of being
     * interpreted here. The claim is the player's to make: claiming before forwarding would make
     * the player's copy of the same physical press look like a duplicate and get dropped. The
     * conflict overlays and the app drawer keep their priority because this yields nothing while
     * one is up. A locked player is the deliberate inversion: the viewer asked for the film to run
     * untouched, so the pad stays here and drives the panel until the lock is released.
     */
    private fun yieldsKeysToMediaPlayer(): Boolean {
        if (!::dsm.isInitialized) return false
        if (dsm.mediaPlayerControlsLocked.value) return false
        if (!mediaPanelIsSurfaceNow()) return false
        if (dsm.mediaPlayback.value == null) return false
        if (dsm.dualSyncOverlay.value != null || dsm.dualSaveConflict.value != null) return false
        return true
    }

    private fun mediaPanelIsSurfaceNow(): Boolean = false

    override fun onForegroundChanged(isForeground: Boolean) {
        isArgosyForeground = isForeground
        if (isForeground && isGameActive && !sessionKeepsItsScreen()) onSessionEnded()
        isInitialized = true
    }

    /**
     * Whether the game keeps the screen it was on as the launcher UI comes forward. Only one on the
     * other display does: with a single pair of panels, the launcher arriving is the game losing the
     * one it had. The launcher UI lives on the default display, so a game anywhere else is untouched
     * by it - which is also the exemption the launcher side applies before it tears a session down,
     * and the two have to agree or the companion drops the in-game panel out from under a live game.
     */
    private fun sessionKeepsItsScreen(): Boolean {
        if (!dsm.hasLiveSession()) return false
        if (isShowcaseRole) return true
        return dsm.emulatorDisplayId?.let { it != Display.DEFAULT_DISPLAY } ?: false
    }

    override fun onWizardStateChanged(isActive: Boolean) {
        isWizardActive = isActive
    }

    override fun onSessionStarted(
        gameId: Long, isHardcore: Boolean, channelName: String?
    ) {
        isGameActive = true
        this.isHardcore = isHardcore
        currentChannelName = channelName
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = CompanionSessionTimer().also {
            it.start(applicationContext)
        }
        isInitialized = true
    }

    override fun onSessionHardcoreChanged(isHardcore: Boolean, channelName: String?) {
        runOnUiThread {
            this.isHardcore = isHardcore
            currentChannelName = channelName
        }
    }

    override fun onSessionEnded() {
        isGameActive = false
        isHardcore = false
        currentChannelName = null
        companionSessionTimer?.stop(applicationContext)
        companionSessionTimer = null
        isInitialized = true
    }

    override fun onHomeAppsChanged(apps: List<String>) {
        homeApps = apps
    }

    override fun onLibraryRefresh() = Unit

    override fun onAccountSwitched() = Unit

    override fun onOverlayRequested(eventName: String) = Unit

    override fun onRoleSwapped(isSwapped: Boolean) {
        isShowcaseRole = isSwapped
    }

    override fun onOverlayClosed() = Unit

    override fun onBackgroundForward() = Unit

    /**
     * Keys the primary display forwarded keep the media-player yield the directly-delivered ones
     * get: while the panel fronts a live playback they are rebuilt as key events and handed to the
     * player's dispatch. The rebuild carries fresh timestamps because the source event was already
     * claimed by the forwarding activity, and the player's own claim would otherwise read the copy
     * as a duplicate and drop it. With no player dispatch registered the key falls through to the
     * companion's own routing instead of vanishing.
     */
    @android.annotation.SuppressLint("RestrictedApi")
    override fun onForwardKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        swapAB: Boolean,
        swapXY: Boolean,
        swapStartSelect: Boolean
    ) {
        if (yieldsKeysToMediaPlayer()) {
            val forward = dsm.mediaPlayerKeyDispatcher
            if (forward != null) {
                val time = android.os.SystemClock.uptimeMillis()
                forward(android.view.KeyEvent(time, time, action, keyCode, repeatCount))
                return
            }
        }
        val time = android.os.SystemClock.uptimeMillis()
        val forwarded = android.view.KeyEvent(time, time, action, keyCode, repeatCount)
        if (gamepadInputHandler.handleKeyEvent(forwarded)) return
        super.dispatchKeyEvent(forwarded)
    }

    /**
     * Holds this window awake while the person is using either screen, for the reason the primary
     * does: activity is credited only to the display an event landed on. Like the primary it also
     * holds for the whole of a playback, because the launcher's own dim ramp needs this display
     * powered to land its dark stage and to receive the touch that wakes it.
     */
    private fun keepAwakeWhileUserActive() {
        lifecycleScope.launch {
            val manager = DualScreenManagerHolder.instance ?: return@launch
            kotlinx.coroutines.flow.combine(
                manager.userActive,
                manager.mediaPlayback
            ) { active, playback -> active || playback != null }
                .collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }
    }

    /**
     * Fades this window towards dark while a playback on another display runs unattended, for the
     * reason the primary does: it should neither distract from the film nor burn battery, and any
     * input restores it in full. Launched from [initializeCompanion] so a stale-DSM reconnect
     * replaces the collector rather than leaving one bound to the dead instance.
     */
    private fun dimWhileMediaIdle() {
        mediaDimCollectJob?.cancel()
        mediaDimCollectJob = lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                dsm.mediaPlayerDisplay,
                dsm.mediaDimBrightness,
                dsm.mediaDimCoverAlpha
            ) { playerDisplayId, dim, coverAlpha -> Triple(playerDisplayId, dim, coverAlpha) }
                .collect { (playerDisplayId, dim, coverAlpha) ->
                    applyMediaDim(playerDisplayId, dim, coverAlpha)
                }
        }
    }

    /**
     * A window brightness of zero drives the panel to its MINIMUM backlight, not off - the
     * platform has no attribute value that blanks a panel. The manager therefore fades an opaque
     * black cover over the window across the ramp's second leg, and this window renders it as a
     * foreground drawable: it draws over everything and consumes no input itself. The waking tap
     * is swallowed in dispatchTouchEvent for its whole gesture, so it clears the brightness
     * override and starts the cover's fade-out without reaching the content underneath. The cover
     * follows the display split rather than a live dim stage, because the wake fade runs after
     * the override has already been released and the manager's alpha is the sole authority on how
     * much cover remains.
     *
     * The player's display is the window's own report when one is current, otherwise the display
     * the relocation rules place the player on: the report is cleared when a playback closes, and
     * an episode switch reuses the window without restarting it, so a missing report during a live
     * playback means stale bookkeeping, never "no player".
     */
    private fun applyMediaDim(playerDisplayId: Int?, dim: Float?, coverAlpha: Float) {
        val ownDisplayId = window.decorView.display?.displayId
        val resolvedPlayerId = playerDisplayId ?: dsm.mediaPlayerRelocationDisplayId()
        val isOtherDisplay = resolvedPlayerId != null &&
            ownDisplayId != null && ownDisplayId != resolvedPlayerId
        val dimsThisWindow = dim != null && isOtherDisplay
        logMediaDimDecline(dimsThisWindow, ownDisplayId, playerDisplayId, resolvedPlayerId, dim)
        val target = if (dimsThisWindow && dim != null) {
            dim
        } else {
            android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        applyMediaDimCover(if (isOtherDisplay) coverAlpha else 0f)
        val attributes = window.attributes
        if (attributes.screenBrightness == target) return
        Logger.debug(
            DualScreenManager.MEDIA_DIM_LOG_TAG,
            "apply window=companion display=$ownDisplayId player=$resolvedPlayerId brightness=$target"
        )
        attributes.screenBrightness = target
        window.attributes = attributes
    }

    private var mediaDimDeclineKey: String? = null

    /**
     * Logs a dim stage this window received and chose not to apply, once per stage and reason, so
     * a ramp whose output is being discarded shows up in the MediaDimRamp channel instead of
     * failing silently. Declining because this display shows the playback is the expected case and
     * is still logged - it is the positive proof of which window abstained and why.
     */
    private fun logMediaDimDecline(
        dimsThisWindow: Boolean,
        ownDisplayId: Int?,
        reportedPlayerId: Int?,
        resolvedPlayerId: Int?,
        dim: Float?
    ) {
        val reason = when {
            dim == null || dimsThisWindow -> null
            ownDisplayId == null -> "ownDisplayUnknown"
            resolvedPlayerId == null -> "playerDisplayUnresolved"
            else -> "ownDisplayShowsPlayback"
        }
        val key = reason?.let { "$it dim=$dim" }
        if (key != null && key != mediaDimDeclineKey) {
            Logger.debug(
                DualScreenManager.MEDIA_DIM_LOG_TAG,
                "declined window=companion display=$ownDisplayId playerReported=$reportedPlayerId " +
                    "playerResolved=$resolvedPlayerId brightness=$dim reason=$reason"
            )
        }
        mediaDimDeclineKey = key
    }

    private var mediaDimCover: android.graphics.drawable.ColorDrawable? = null

    private fun applyMediaDimCover(alpha: Float) {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        if (alphaInt <= 0) {
            if (mediaDimCover != null) {
                mediaDimCover = null
                window.decorView.foreground = null
            }
            return
        }
        val cover = mediaDimCover
            ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK).also {
                it.alpha = 0
                mediaDimCover = it
                window.decorView.foreground = it
            }
        cover.alpha = alphaInt
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (::dsm.isInitialized) dsm.notifyUserActivity("companionUserInteraction")
    }

    override fun refocusSelf() = startActivity(
        Intent(this, SecondaryHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    )

    override fun onDownloadCompleted(gameId: Long) {
        onLibraryRefresh()
    }

    private val fontFamilyCache = mutableMapOf<String, FontFamily>()

    private suspend fun resolveCustomFonts(displayPath: String?, bodyPath: String?): CustomFontFamilies =
        withContext(Dispatchers.IO) {
            CustomFontFamilies(
                display = resolveFontFamily(displayPath),
                body = resolveFontFamily(bodyPath)
            )
        }

    private fun resolveFontFamily(path: String?): FontFamily? {
        if (path == null) return null
        fontFamilyCache[path]?.let { return it }
        val family = CustomFontLoader.loadFamily(path)
        if (family != null) fontFamilyCache[path] = family
        return family
    }

    private fun hostDisplayId(): Int =
        androidx.core.content.ContextCompat.getDisplayOrDefault(this).displayId

    private fun initializeCompanion() {
        registerDisplayListener()
        loadInitialState()
        if (!isShowcaseRole) dsm.clearMediaInfoRequest()
        dsm.registerCompanionHost(hostDisplayId(), this)
        lifecycleScope.launch { dsm.companionAchievements.collect { companionAchievements = it } }
        lifecycleScope.launch {
            dsm.companionMediaVisible.collect { visible -> isMediaPanelVisible = visible }
        }
        lifecycleScope.launch {
            dsm.preferencesRepository.userPreferences.collect { prefs ->
                selectModifier.comboMap =
                    SelectModifier.comboMapFrom(prefs.selectLCombo, prefs.selectRCombo)
            }
        }
        dimWhileMediaIdle()
    }


    private fun loadInitialState() {
        val store = dsm.sessionStateStore
        isShowcaseRole = dsm.isRolesSwapped.value
        isArgosyForeground = store.isArgosyForeground()
        isGameActive = store.hasActiveSession()
        isWizardActive = store.isWizardActive() || !store.isFirstRunComplete()
        currentChannelName = store.getChannelName()
        isHardcore = store.isHardcore()

        if (isGameActive) {
            companionSessionTimer = CompanionSessionTimer().also {
                it.start(applicationContext)
            }
        }
        isInitialized = true
    }

    /**
     * Whether the session written to disk is still being played on this display.
     *
     * The companion is pinned as this display's home, so the OS recreates it the moment the launcher
     * process dies - underneath the game, which knows nothing about any of it. Every in-memory signal
     * that a game is running died with the process; the record on disk and the emulator's own windows
     * are what is left, and together they say a restart happened rather than a player coming back.
     * Silence from usage access answers false, which is the old behaviour: recover and let the
     * launcher come forward.
     */
    private fun sessionHoldsThisDisplay(): Boolean {
        val store = SessionStateStore(applicationContext)
        if (!store.hasActiveSession()) return false
        val emulatorPackage = store.getEmulatorPackage() ?: return false
        val emulatorDisplay = store.getEmulatorDisplayId()
        val ownDisplay = try {
            window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId
        } catch (_: Exception) { null }
        if (emulatorDisplay != null && ownDisplay != null && emulatorDisplay != ownDisplay) return false
        return PermissionHelper().isPackageOnScreenOrRecent(this, emulatorPackage)
    }

    /**
     * Holds the companion still until the game on this display is done with it, then recovers.
     *
     * Polled rather than driven by this activity's own lifecycle: a companion recreated underneath a
     * game may never be resumed, and one the OS chose to bring forward is resumed while the game is
     * still there, so neither event answers on its own. The poll runs only in this one state - no
     * manager, a session on this display - and ends the moment it recovers.
     */
    private fun awaitSessionEnd() {
        lifecycleScope.launch {
            while (sessionHoldsThisDisplay()) {
                kotlinx.coroutines.delay(SESSION_END_POLL_MS)
            }
            recoverThroughMainActivity()
        }
    }

    /**
     * Brings the launcher process back up and attaches to the manager it creates. Only ever called
     * when nothing is being played on this display: it puts an activity on the default display and
     * finishes this one on failure, and the OS respawning a pinned home activity turns that into a
     * loop if a game is what it keeps landing behind.
     */
    private fun recoverThroughMainActivity() {
        android.util.Log.w("SecondaryHome", "DSM not available, launching MainActivity")
        startActivity(
            Intent(this, com.nendo.argosy.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            android.app.ActivityOptions.makeBasic()
                .setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle()
        )
        lifecycleScope.launch {
            var attempts = 0
            while (DualScreenManagerHolder.instance == null && attempts < 50) {
                kotlinx.coroutines.delay(100)
                attempts++
            }
            val holder = DualScreenManagerHolder.instance
            if (holder == null) {
                android.util.Log.e("SecondaryHome", "DSM still null after waiting, finishing")
                finish()
                return@launch
            }
            dsm = holder
            initializeCompanion()
            syncFromSessionStore()
            dsm.onCompanionResumed()
            endSessionIfEmulatorGone()
        }
    }

    /**
     * The companion is only ever launched onto the secondary display, so landing on the default
     * one means the OS refused that placement - it does not permit a home activity there. Left
     * running it would cover the launcher and be relaunched in a loop, so release the component
     * and drop to single-screen instead. Returns true when the activity has been finished.
     */
    private fun releaseUnsupportedDisplay(): Boolean {
        val ownDisplayId = try {
            window.decorView.display?.displayId ?: windowManager.defaultDisplay.displayId
        } catch (_: Exception) { return false }
        if (ownDisplayId != android.view.Display.DEFAULT_DISPLAY) return false

        android.util.Log.w(
            "SecondaryHome",
            "Companion placed on the default display; this OS does not allow a secondary home"
        )
        SessionStateStore(applicationContext).setSecondaryDisplayUsable(false)
        DualScreenManagerHolder.instance?.fallbackToSingleScreen(persistent = true)
        com.nendo.argosy.util.SecondaryHomeComponent.setEnabled(this, false)
        CompanionGuardService.stop(this)
        finish()
        return true
    }

    private fun registerDisplayListener() {
        val displayManager = getSystemService(DisplayManager::class.java)
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {
                val myDisplayId = try {
                    windowManager.defaultDisplay.displayId
                } catch (_: Exception) { -1 }
                if (displayId == myDisplayId ||
                    displayManager.displays.size <= 1
                ) {
                    android.util.Log.w(
                        "SecondaryHome",
                        "Display removed, finishing companion activity"
                    )
                    finish()
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.hideSystemBars() else gamepadInputHandler.resetStickMotion()
    }
}

private const val CONFIRM_HOLD_MS = 500L
private const val HAND_BACK_THROTTLE_MS = 1500L

/**
 * How long this screen waits to settle on the restored position before it takes the driven role.
 *
 * Bounded for the reason the primary's wait is: a restore can defer while its sections load, and a
 * swap that never completes is worse than one that lands a beat late.
 */
private const val SWAP_PREPARE_TIMEOUT_MS = 400L
private const val SESSION_END_POLL_MS = 3000L
