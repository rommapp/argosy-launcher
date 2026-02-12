package com.nendo.argosy.hardware

import android.content.Context
import android.app.Presentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.ui.theme.ThemeViewModel

class SecondaryDisplayPresentation(
    context: Context,
    display: Display,
    private val viewModel: SecondaryHomeViewModel,
    private val themeViewModel: ThemeViewModel,
    private val onKeyEvent: (KeyEvent) -> Boolean
) : Presentation(context, display), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val handler = Handler(Looper.getMainLooper())
    private var aButtonHoldRunnable: Runnable? = null
    private var aButtonActionTriggered = false
    private val HOLD_DELAY_MS = 750L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedStateRegistryController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@SecondaryDisplayPresentation)
            setViewTreeSavedStateRegistryOwner(this@SecondaryDisplayPresentation)
            setViewTreeViewModelStoreOwner(this@SecondaryDisplayPresentation)

            setContent {
                ALauncherTheme(viewModel = themeViewModel) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        SecondaryHomeScreen(viewModel = viewModel)
                    }
                }
            }
        }

        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onPause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onStop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        _viewModelStore.clear()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onKeyEvent(event)) {
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
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

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
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
        }

        return super.dispatchKeyEvent(event)
    }

    private fun selectFocusedGame() {
        val (intent, options) = viewModel.selectFocusedGame() ?: return
        if (options != null) {
            context.startActivity(intent, options)
        } else {
            context.startActivity(intent)
        }
    }

    private fun launchFocusedGame() {
        val result = viewModel.launchFocusedGame()
        if (result != null) {
            val (intent, options) = result
            intent?.let {
                if (options != null) {
                    context.startActivity(it, options)
                } else {
                    context.startActivity(it)
                }
            }
        }
    }
}
