package com.nendo.argosy

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.input.GamepadInputHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
        }

        activityScope.launch {
            launchRetryTracker.retryEvents.collect { intent ->
                Log.d("MainActivity", "Retrying launch intent after quick return")
                startActivity(intent)
            }
        }

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

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadInputHandler.handleKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
            window.decorView.requestFocus()
            launchRetryTracker.onFocusGained()
            ambientAudioManager.fadeIn()
        } else {
            launchRetryTracker.onFocusLost()
            ambientAudioManager.fadeOut()
        }
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
