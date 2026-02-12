package com.nendo.argosy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
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
import com.nendo.argosy.data.emulator.LaunchRetryTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import com.nendo.argosy.ui.ArgosyApp
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.hardware.RecoveryDisplayService
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.nendo.argosy.ui.theme.ALauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _pendingDeepLink = kotlinx.coroutines.flow.MutableStateFlow<android.net.Uri?>(null)
    val pendingDeepLink: kotlinx.coroutines.flow.StateFlow<android.net.Uri?> = _pendingDeepLink

    @Inject
    lateinit var gamepadInputHandler: GamepadInputHandler

    @Inject
    lateinit var imageCacheManager: ImageCacheManager

    @Inject
    lateinit var romMRepository: RomMRepository

    @Inject
    lateinit var launchRetryTracker: LaunchRetryTracker

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var ambientAudioManager: AmbientAudioManager

    @Inject
    lateinit var ambientLedManager: AmbientLedManager

    @Inject
    lateinit var screenCaptureManager: ScreenCaptureManager

    @Inject
    lateinit var displayAffinityHelper: DisplayAffinityHelper

    @Inject
    lateinit var permissionHelper: PermissionHelper

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(this) }

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var screenCapturePromptedThisSession = false
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenCaptureManager.onPermissionResult(result.resultCode, result.data)
        if (screenCaptureManager.hasPermission.value) {
            screenCaptureManager.startCapture()
        }
    }
    private var hasResumedBefore = false
    private var hadFocusBefore = false
    private var focusLostTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shouldYieldToEmulator()) {
            Log.d(TAG, "Persisted session found - finishing to avoid stealing focus")
            finish()
            return
        }

        enableEdgeToEdge()
        hideSystemUI()

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

            if (prefs.ambientLedEnabled && !screenCaptureManager.hasPermission.value && !screenCapturePromptedThisSession) {
                screenCapturePromptedThisSession = true
                screenCaptureManager.requestPermission(this@MainActivity, screenCaptureLauncher)
            }
        }

        activityScope.launch {
            launchRetryTracker.retryEvents.collect { intent ->
                Log.d("MainActivity", "Retrying launch intent after quick return")
                startActivity(intent)
            }
        }

        // Schedule pending RA achievement submission if any are queued
        com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this)


        activityScope.launch {
            var previousHomeApps: Set<String>? = null
            var previousPrimaryColor: Int? = null
            preferencesRepository.preferences.collect { prefs ->
                Logger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.fileLoggingEnabled,
                    level = prefs.fileLogLevel
                )
                SaveDebugLogger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.saveDebugLoggingEnabled
                )
                ambientAudioManager.setEnabled(prefs.ambientAudioEnabled)
                ambientAudioManager.setVolume(prefs.ambientAudioVolume)
                ambientAudioManager.setShuffle(prefs.ambientAudioShuffle)
                ambientAudioManager.setAudioSource(prefs.ambientAudioUri)
                if (prefs.ambientAudioEnabled && prefs.ambientAudioUri != null && hasWindowFocus()) {
                    ambientAudioManager.fadeIn()
                }

                // Update home apps for companion process
                if (previousHomeApps != null && prefs.secondaryHomeApps != previousHomeApps) {
                    updateHomeApps(prefs.secondaryHomeApps)
                }
                previousHomeApps = prefs.secondaryHomeApps

                // Update primary color for companion process
                if (prefs.primaryColor != previousPrimaryColor) {
                    sessionStateStore.setPrimaryColor(prefs.primaryColor)
                }
                previousPrimaryColor = prefs.primaryColor
            }
        }

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArgosyApp()
                }
            }
        }

    }

    private fun shouldYieldToEmulator(): Boolean {
        // Don't yield if user explicitly navigated here
        if (intent.data != null || intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            return false
        }
        // Check if a persisted session exists
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return false

        // Only yield if emulator is still in foreground (used within last 15 seconds)
        // If emulator isn't in foreground, the game has ended - clear session and proceed
        val emulatorInForeground = permissionHelper.isPackageInForeground(
            this, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(TAG, "Emulator ${session.emulatorPackage} not in foreground - clearing session")
            runBlocking { preferencesRepository.clearActiveSession() }
            return false
        }

        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!handleDeepLink(intent)) {
            handleHomeIntent(intent)
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

    override fun onResume() {
        super.onResume()

        // Notify secondary display that Argosy is in foreground
        broadcastForegroundState(true)

        // Check if we should yield to a running emulator
        if (shouldYieldOnResume()) {
            Log.d(TAG, "Persisted session found on resume - yielding to emulator")
            moveTaskToBack(true)
            return
        }

        // Clear stale session if emulator is no longer running
        clearStaleSession()

        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch {
                romMRepository.initialize()
            }
            ambientAudioManager.fadeIn()
        }
        hasResumedBefore = true
    }

    private fun clearStaleSession() {
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return
        val emulatorInForeground = permissionHelper.isPackageInForeground(
            this, session.emulatorPackage, withinMs = 15_000
        )
        if (!emulatorInForeground) {
            Log.d(TAG, "Emulator ${session.emulatorPackage} not in foreground - clearing stale session")
            runBlocking { preferencesRepository.clearActiveSession() }
            broadcastSessionCleared()

            // Stop recovery service if running (SECONDARY_HOME handles display now)
            if (displayAffinityHelper.hasSecondaryDisplay) {
                RecoveryDisplayService.stop(this)
            }
        }
    }

    private fun broadcastSessionCleared() {
        val intent = Intent("com.nendo.argosy.SESSION_CHANGED").apply {
            setPackage(packageName)
            putExtra("game_id", -1L)
        }
        sendBroadcast(intent)
    }

    private fun shouldYieldOnResume(): Boolean {
        // Don't yield if this is the first resume (onCreate just ran)
        if (!hasResumedBefore) return false
        // Check if a game session is active
        val session = runBlocking { preferencesRepository.getPersistedSession() } ?: return false
        // Only yield if we regained focus very quickly (< 2 sec) - indicates OOM recovery
        val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
        return focusLostTime > 0 && timeSinceFocusLost < 2000
    }

    private fun handleHomeIntent(intent: Intent): Boolean {
        if (intent.hasCategory(Intent.CATEGORY_HOME) && hasResumedBefore) {
            gamepadInputHandler.emitHomeEvent()
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        ambientAudioManager.suspend()
        // Notify secondary display that Argosy is going to background
        broadcastForegroundState(false)
    }

    private fun broadcastForegroundState(isForeground: Boolean) {
        // Write to SharedPreferences for companion process to read on startup
        sessionStateStore.setArgosyForeground(isForeground)

        val action = if (isForeground) {
            "com.nendo.argosy.FOREGROUND"
        } else {
            "com.nendo.argosy.BACKGROUND"
        }
        val intent = Intent(action).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)

        // Also update home apps when going to foreground
        if (isForeground) {
            activityScope.launch {
                val prefs = preferencesRepository.preferences.first()
                updateHomeApps(prefs.secondaryHomeApps)
            }
        }
    }

    private fun updateHomeApps(homeApps: Set<String>) {
        // Write to SharedPreferences for companion process to read on startup
        sessionStateStore.setHomeApps(homeApps)

        // Broadcast for live updates
        val intent = Intent("com.nendo.argosy.HOME_APPS_CHANGED").apply {
            setPackage(packageName)
            putStringArrayListExtra("home_apps", ArrayList(homeApps))
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureManager.stopCapture()
        activityScope.cancel()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        if (gamepadInputHandler.handleKeyEvent(event)) {
            return true
        }
        // Only handle Home key when not in emulator (gamepad handler didn't consume it)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_HOME) {
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
        if (gamepadInputHandler.handleMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val timeSinceFocusLost = System.currentTimeMillis() - focusLostTime
            // Only emit if we had focus before and lost it briefly (< 1 second = Home while visible)
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
            gamepadInputHandler.blockInputFor(200)
        } else {
            focusLostTime = System.currentTimeMillis()
            launchRetryTracker.onFocusLost()
            ambientAudioManager.fadeOut()
            ambientLedManager.setContext(AmbientLedContext.IN_GAME)
        }
    }

    fun requestScreenCapturePermission() {
        screenCaptureManager.requestPermission(this, screenCaptureLauncher)
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
