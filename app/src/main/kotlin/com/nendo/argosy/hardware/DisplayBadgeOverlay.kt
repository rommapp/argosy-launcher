package com.nendo.argosy.hardware

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.nendo.argosy.ui.components.ScreenNumberBadge
import com.nendo.argosy.ui.components.ScreenNumberChip
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

enum class DisplayBadgeSize { LARGE, SMALL }

/**
 * Badges drawn above whatever each display is showing, including another app's window. One window
 * per display, added and removed together.
 */
class DisplayBadgeOverlay(private val context: Context) {

    private val shown = mutableMapOf<Int, View>()
    private var owner: ComposeWindowOwner? = null

    fun show(numbers: Map<Int, Int>, size: DisplayBadgeSize) {
        hide()
        if (numbers.isEmpty()) return
        val lifecycleOwner = ComposeWindowOwner().also { owner = it }
        lifecycleOwner.start()
        numbers.forEach { (displayId, number) ->
            addBadge(displayId, number, size, lifecycleOwner)?.let { shown[displayId] = it }
        }
    }

    fun hide() {
        shown.forEach { (displayId, view) ->
            runCatching { windowManagerFor(displayId)?.removeView(view) }
        }
        shown.clear()
        owner?.stop()
        owner = null
    }

    private fun addBadge(
        displayId: Int,
        number: Int,
        size: DisplayBadgeSize,
        lifecycleOwner: ComposeWindowOwner
    ): View? {
        val displayContext = badgeWindowContext(displayId) ?: return null
        val windowManager = displayContext.getSystemService(WindowManager::class.java) ?: return null
        val view = ComposeView(displayContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (size) {
                        DisplayBadgeSize.LARGE -> ScreenNumberBadge(number = number)
                        DisplayBadgeSize.SMALL -> ScreenNumberChip(number = number)
                    }
                }
            }
        }
        val density = displayContext.resources.displayMetrics.density
        val width = ((if (size == DisplayBadgeSize.LARGE) LARGE_WIDTH_DP else SMALL_WIDTH_DP) * density).toInt()
        val height = ((if (size == DisplayBadgeSize.LARGE) LARGE_HEIGHT_DP else SMALL_HEIGHT_DP) * density).toInt()
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = ((if (size == DisplayBadgeSize.LARGE) LARGE_MARGIN_DP else SMALL_MARGIN_DP) * density).toInt()
            y = (STATUS_CAPSULE_CLEARANCE_DP * density).toInt()
        }
        return runCatching {
            windowManager.addView(view, params)
            view
        }.onFailure {
            android.util.Log.w("DisplayBadge", "addView failed on display $displayId", it)
        }.getOrNull()
    }

    private val displayManager
        get() = context.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager

    private fun windowManagerFor(displayId: Int): WindowManager? =
        badgeWindowContext(displayId)?.getSystemService(WindowManager::class.java)

    private fun badgeWindowContext(displayId: Int): Context? {
        val display = displayManager.getDisplay(displayId) ?: return null
        val displayContext = context.createDisplayContext(display)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null
            )
        } else {
            displayContext
        }
    }

    private companion object {
        const val LARGE_MARGIN_DP = 16
        const val SMALL_MARGIN_DP = 12
        const val STATUS_CAPSULE_CLEARANCE_DP = 64
        const val LARGE_WIDTH_DP = 96
        const val LARGE_HEIGHT_DP = 88
        const val SMALL_WIDTH_DP = 56
        const val SMALL_HEIGHT_DP = 44
    }
}

internal class ComposeWindowOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
