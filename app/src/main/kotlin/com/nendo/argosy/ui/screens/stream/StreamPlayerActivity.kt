package com.nendo.argosy.ui.screens.stream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.streaming.StreamEndReason
import com.nendo.argosy.data.streaming.StreamPhase
import com.nendo.argosy.data.streaming.StreamSession
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import com.nendo.argosy.ui.dualscreen.dashboard.SessionControls
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.util.hideSystemBars
import com.nendo.argosy.util.installImmersiveMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class StreamPlayerActivity : ComponentActivity() {

    private var languageTag: String = com.nendo.argosy.core.locale.LocaleHelper.SYSTEM_LANGUAGE_TAG

    override fun attachBaseContext(newBase: Context) {
        languageTag = com.nendo.argosy.data.preferences.SessionStateStore(newBase).getAppLanguage()
        super.attachBaseContext(com.nendo.argosy.core.locale.LocaleHelper.wrap(newBase, languageTag))
    }

    override fun applyOverrideConfiguration(overrideConfiguration: android.content.res.Configuration?) {
        super.applyOverrideConfiguration(
            com.nendo.argosy.core.locale.LocaleHelper.overrideConfiguration(overrideConfiguration, languageTag)
        )
    }

    @Inject
    lateinit var sessions: StreamSessionManager

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private lateinit var webView: WebView
    private lateinit var overlay: ComposeView
    private val uiState = MutableStateFlow(StreamPlayerUiState())
    private var loadedRoom: String? = null
    private var roomOrigin: String? = null
    private var swapAB by mutableStateOf(false)
    private var swapXY by mutableStateOf(false)
    private var swapStartSelect by mutableStateOf(false)
    private var backHoldJob: Job? = null
    private var noticeJob: Job? = null
    private var ownsCompanion = false
    private var pendingPermission: PermissionRequest? = null

    private val forwardedKeyDispatcher: (KeyEvent) -> Boolean = { event -> dispatchKeyEvent(event) }
    private val forwardedMotionDispatcher: (MotionEvent) -> Boolean = { event -> dispatchGenericMotionEvent(event) }

    private val microphoneRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingPermission ?: return@registerForActivityResult
            pendingPermission = null
            answer(request, microphoneGranted = granted)
        }

    private val quickActions = object : DualScreenManager.SessionQuickActions {
        override fun quickSave() {
            runControl(StreamControlRow.SAVE_STATE)
        }

        override fun loadState(slotNumber: Int, savedAtMillis: Long) {
            runControl(StreamControlRow.LOAD_STATE)
        }

        override fun screenshot() = Unit

        override fun openCheats() = Unit

        override fun openGameSettings() {
            openControls()
        }

        override fun quit() {
            runControl(StreamControlRow.SAVE_AND_EXIT)
        }
    }

    private val dashboardActions = object : DualScreenManager.StreamDashboardActions {
        override fun nextDisc() {
            runControl(StreamControlRow.NEXT_DISC)
        }

        override fun toggleMute() {
            runControl(StreamControlRow.MUTE)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (sessions.session.value == null) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        installImmersiveMode()
        webView = buildWebView()
        overlay = ComposeView(this).apply {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setContent {
                ALauncherTheme {
                    CompositionLocalProvider(
                        LocalABIconsSwapped provides swapAB,
                        LocalXYIconsSwapped provides swapXY,
                        LocalSwapStartSelect provides swapStartSelect
                    ) {
                        val state by uiState.collectAsState()
                        StreamPlayerOverlay(
                            state = state,
                            onRow = ::runControl,
                            onCloseControls = ::closeControls,
                            onLeave = ::leaveFromOverlay
                        )
                    }
                }
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(webView, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(this@StreamPlayerActivity.overlay, FrameLayout.LayoutParams(MATCH, MATCH))
            }
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (uiState.value.controlsOpen) closeControls() else openControls()
            }
        })
        sessions.attachTask(taskId)
        observeSession()
        observeOverlay()
        readButtonSwaps()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView = WebView(this).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                onPagePermission(request)
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermission === request) pendingPermission = null
            }
        }
    }

    private fun onPagePermission(request: PermissionRequest) {
        if (request.origin?.let(::originOf) != roomOrigin) {
            request.deny()
            return
        }
        val wantsMicrophone = PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources
        val held = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (wantsMicrophone && !held) {
            pendingPermission?.deny()
            pendingPermission = request
            microphoneRequest.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        answer(request, microphoneGranted = held)
    }

    private fun answer(request: PermissionRequest, microphoneGranted: Boolean) {
        val grantable = request.resources.filter { resource ->
            resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE && microphoneGranted
        }
        if (grantable.isEmpty()) request.deny() else request.grant(grantable.toTypedArray())
    }

    private fun observeSession() {
        lifecycleScope.launch {
            sessions.session.collect { session ->
                if (session == null) {
                    finish()
                    return@collect
                }
                applySession(session)
            }
        }
    }

    private fun applySession(session: StreamSession) {
        val stage = StreamPlayerUiState.stageOf(session.phase)
        uiState.update {
            it.copy(
                title = session.target.title,
                containerLabel = session.target.containerLabel,
                stage = stage,
                preparing = session.extractionPhase != null,
                launchFailed = session.endReason == StreamEndReason.LAUNCH_FAILED,
                rows = StreamPlayerUiState.rowsFor(session),
                muted = session.muted,
                controlsOpen = it.controlsOpen && stage == StreamPlayerStage.PLAYING
            )
        }
        val room = session.roomUrl
        if (session.phase == StreamPhase.PLAYING && room != null && room != loadedRoom) {
            loadedRoom = room
            roomOrigin = originOf(Uri.parse(room))
            webView.loadUrl(room)
        }
        if (session.phase == StreamPhase.ENDED && session.endReason in CLOSING_REASONS) {
            sessions.clearEnded()
            return
        }
        if (ownsCompanion) publishControls(session)
    }

    private fun observeOverlay() {
        lifecycleScope.launch {
            uiState.map { it.capturesInput }.distinctUntilChanged().collect { captures ->
                overlay.visibility = if (captures) View.VISIBLE else View.GONE
                if (!captures) webView.requestFocus()
            }
        }
    }

    private fun readButtonSwaps() {
        lifecycleScope.launch {
            userPreferencesRepository.preferences.collectLatest { prefs ->
                swapAB = prefs.swapAB
                swapXY = prefs.swapXY
                swapStartSelect = prefs.swapStartSelect
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        sessions.onPlayerShown()
        claimCompanion()
    }

    override fun onResume() {
        super.onResume()
        window.hideSystemBars()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onStop() {
        backHoldJob?.cancel()
        backHoldJob = null
        if (!isChangingConfigurations && !isFinishing) sessions.onPlayerHidden()
        releaseCompanion()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.hideSystemBars()
    }

    override fun onDestroy() {
        val dsm = DualScreenManagerHolder.instance
        if (dsm != null) {
            if (dsm.emulatorKeyDispatcher === forwardedKeyDispatcher) dsm.emulatorKeyDispatcher = null
            if (dsm.emulatorMotionDispatcher === forwardedMotionDispatcher) dsm.emulatorMotionDispatcher = null
        }
        pendingPermission?.deny()
        pendingPermission = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun claimCompanion() {
        val dsm = DualScreenManagerHolder.instance ?: return
        dsm.emulatorKeyDispatcher = forwardedKeyDispatcher
        dsm.emulatorMotionDispatcher = forwardedMotionDispatcher
        val session = sessions.session.value?.takeIf { it.phase != StreamPhase.ENDED } ?: return
        if (ownsCompanion) return
        window.decorView.display?.displayId?.let(dsm::setEmulatorDisplay)
        if (session.isHost) {
            dsm.sessionQuickActions = quickActions
            dsm.streamDashboardActions = dashboardActions
        }
        dsm.onSessionChanged(session.target.gameId)
        ownsCompanion = true
        publishControls(session)
    }

    private fun releaseCompanion() {
        if (!ownsCompanion) return
        ownsCompanion = false
        val dsm = DualScreenManagerHolder.instance ?: return
        if (dsm.sessionQuickActions === quickActions) dsm.sessionQuickActions = null
        if (dsm.streamDashboardActions === dashboardActions) dsm.streamDashboardActions = null
        dsm.onSessionChanged(-1L)
    }

    private fun publishControls(session: StreamSession) {
        val dsm = DualScreenManagerHolder.instance ?: return
        if (dsm.sessionQuickActions !== quickActions) return
        dsm.updateSessionControls(
            SessionControls(
                quickStates = session.lastStateSavedAtMillis
                    ?.takeIf { session.supportsStates }
                    ?.let { listOf(DashboardQuickState(session.stateSlot, it, null)) }
                    .orEmpty(),
                cheatsAvailable = false,
                settingsAvailable = true,
                screenshotAvailable = false,
                discSwapAvailable = session.canSwapDisc,
                muteAvailable = session.isHost,
                isMuted = session.muted
            )
        )
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dsm = DualScreenManagerHolder.instance
        if (dsm != null && !dsm.claimInput(event)) return true
        if (event.keyCode in SYSTEM_KEYS) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK && !uiState.value.capturesInput) return onBackKey(event)
        if (uiState.value.capturesInput) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                mapKeycodeToGamepadEvent(event.keyCode, swapAB, swapXY, swapStartSelect)?.let(::routeOverlay)
            }
            return true
        }
        webView.dispatchKeyEvent(event)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (uiState.value.capturesInput) return true
        webView.dispatchGenericMotionEvent(event)
        return true
    }

    private fun onBackKey(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                backHoldJob?.cancel()
                backHoldJob = lifecycleScope.launch {
                    delay(BACK_HOLD_MS)
                    openControls()
                }
            }
            KeyEvent.ACTION_UP -> {
                backHoldJob?.cancel()
                backHoldJob = null
            }
        }
        return true
    }

    private fun routeOverlay(event: GamepadEvent) {
        val state = uiState.value
        if (state.stage != StreamPlayerStage.PLAYING) {
            if (event == GamepadEvent.Confirm || event == GamepadEvent.Back) leaveFromOverlay()
            return
        }
        when (event) {
            GamepadEvent.Up -> moveControlsFocus(-1)
            GamepadEvent.Down -> moveControlsFocus(1)
            GamepadEvent.Confirm -> state.focusedRow?.let(::runControl)
            GamepadEvent.Back -> closeControls()
            else -> Unit
        }
    }

    private fun moveControlsFocus(delta: Int) {
        uiState.update { state ->
            if (state.rows.isEmpty()) state
            else state.copy(controlsFocusIndex = (state.controlsFocusIndex + delta).mod(state.rows.size))
        }
    }

    private fun openControls() {
        if (uiState.value.stage != StreamPlayerStage.PLAYING) return
        uiState.update { it.copy(controlsOpen = true, controlsFocusIndex = 0, notice = null) }
    }

    private fun closeControls() {
        uiState.update { it.copy(controlsOpen = false, notice = null) }
    }

    private fun runControl(row: StreamControlRow) {
        when (row) {
            StreamControlRow.RESUME -> closeControls()
            StreamControlRow.SAVE_AND_EXIT -> {
                closeControls()
                sessions.saveAndExit()
            }
            StreamControlRow.LEAVE -> {
                closeControls()
                sessions.leave()
            }
            StreamControlRow.SAVE_STATE -> control(R.string.stream_controls_notice_state_saved) { sessions.saveState() }
            StreamControlRow.LOAD_STATE -> control(R.string.stream_controls_notice_state_loaded) { sessions.loadState() }
            StreamControlRow.NEXT_DISC -> control(R.string.stream_controls_notice_disc_swapped) { sessions.nextDisc() }
            StreamControlRow.MUTE -> control(null) { sessions.toggleMute() }
        }
    }

    private fun control(@StringRes doneNotice: Int?, block: suspend () -> Boolean) {
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            val done = block()
            val notice = if (done) doneNotice else R.string.stream_controls_notice_failed
            uiState.update { it.copy(notice = notice) }
            if (notice != null) {
                delay(NOTICE_MS)
                uiState.update { it.copy(notice = null) }
            }
        }
    }

    private fun leaveFromOverlay() {
        val session = sessions.session.value
        if (session == null || session.phase == StreamPhase.ENDED) {
            sessions.clearEnded()
            finish()
            return
        }
        sessions.leave()
    }

    private fun originOf(uri: Uri): String? {
        val scheme = uri.scheme ?: return null
        val authority = uri.authority ?: return null
        return "$scheme://$authority"
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val BACK_HOLD_MS = 500L
        private const val NOTICE_MS = 2_500L
        private val SYSTEM_KEYS = setOf(
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_APP_SWITCH
        )
        private val CLOSING_REASONS = setOf(
            StreamEndReason.SAVED_AND_EXITED,
            StreamEndReason.GRACE_EXPIRED,
            StreamEndReason.LEFT
        )

        /**
         * Opens the stream window, or raises the one already open. [options] is the placement
         * the built-in emulator's launch resolves.
         */
        fun start(context: Context, options: Bundle?) {
            context.startActivity(
                Intent(context, StreamPlayerActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
                options
            )
        }
    }
}
