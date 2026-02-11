package com.nendo.argosy

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SecondaryHomeActivity : ComponentActivity() {

    @Inject
    lateinit var displayAffinityHelper: DisplayAffinityHelper

    @Inject
    lateinit var playSessionTracker: PlaySessionTracker

    private val viewModel: SecondaryHomeViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private var aButtonHoldRunnable: Runnable? = null
    private var aButtonActionTriggered = false
    private val HOLD_DELAY_MS = 750L
    private var justLaunchedOnPrimary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - this is the home screen
            }
        })

        setContent {
            ALauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SecondaryHomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.moveFocusUp()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.moveFocusDown()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.moveFocusLeft()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.moveFocusRight()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_L1 -> {
                    viewModel.previousSection()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_R1 -> {
                    viewModel.nextSection()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    aButtonActionTriggered = false
                    viewModel.startHoldingFocusedGame()
                    aButtonHoldRunnable = Runnable {
                        aButtonActionTriggered = true
                    }
                    handler.postDelayed(aButtonHoldRunnable!!, HOLD_DELAY_MS)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_X -> {
                    launchFocusedGame()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                aButtonHoldRunnable?.let { handler.removeCallbacks(it) }
                aButtonHoldRunnable = null
                viewModel.stopHoldingFocusedGame()
                if (aButtonActionTriggered) {
                    handler.post { launchFocusedGame() }
                } else {
                    selectFocusedGame()
                }
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun selectFocusedGame() {
        val (intent, options) = viewModel.selectFocusedGame() ?: return
        justLaunchedOnPrimary = true
        if (options != null) {
            startActivity(intent, options)
        } else {
            startActivity(intent)
        }
    }

    private fun launchFocusedGame() {
        val result = viewModel.launchFocusedGame()
        if (result != null) {
            val (intent, options) = result
            intent?.let {
                justLaunchedOnPrimary = true
                if (options != null) {
                    startActivity(it, options)
                } else {
                    startActivity(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (justLaunchedOnPrimary) {
            justLaunchedOnPrimary = false
            return
        }
        bringPrimaryHomeToFront()
    }

    private fun bringPrimaryHomeToFront() {
        // Don't cover an active game/emulator on the primary display
        if (playSessionTracker.activeSession.value != null) return

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val options = displayAffinityHelper.getActivityOptions(forEmulator = true)
        if (options != null) {
            startActivity(intent, options)
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
