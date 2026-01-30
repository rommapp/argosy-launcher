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
import com.nendo.argosy.ui.ArgosyApp
import com.nendo.argosy.hardware.AmbientLedContext
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ScreenCaptureManager
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadInputHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.nendo.argosy.ui.theme.ALauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            preferencesRepository.preferences.collect { prefs ->
                Logger.configure(
                    versionName = BuildConfig.VERSION_NAME,
                    logDirectory = prefs.fileLoggingPath,
                    enabled = prefs.fileLoggingEnabled,
                    level = prefs.fileLogLevel
                )
                ambientAudioManager.setEnabled(prefs.ambientAudioEnabled)
                ambientAudioManager.setVolume(prefs.ambientAudioVolume)
                ambientAudioManager.setAudioUri(prefs.ambientAudioUri)
                if (prefs.ambientAudioEnabled && prefs.ambientAudioUri != null && hasWindowFocus()) {
                    ambientAudioManager.fadeIn()
                }
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

    override fun onResume() {
        super.onResume()
        if (hasResumedBefore) {
            romMRepository.onAppResumed()
            activityScope.launch {
                romMRepository.initialize()
            }
            ambientAudioManager.fadeIn()
        }
        hasResumedBefore = true
    }

    override fun onPause() {
        super.onPause()
        ambientAudioManager.suspend()
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
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            ambientAudioManager.resumeFromSuspend()
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            window.decorView.requestFocus()
            launchRetryTracker.onFocusGained()
            ambientAudioManager.fadeIn()
            ambientLedManager.setContext(AmbientLedContext.ARGOSY_UI)
            ambientLedManager.clearInGameColors()
            gamepadInputHandler.blockInputFor(200)
        } else {
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
