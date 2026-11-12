package com.nendo.argosy.util

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach
import com.nendo.argosy.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Which of Android's system bars a window keeps off the screen. The status flag also covers the
 * caption bar a freeform window draws along its top edge.
 */
data class SystemBarVisibility(
    val hideStatusBar: Boolean,
    val hideNavigationBar: Boolean
) {
    val hiddenTypes: Int
        get() = typesFor(hideStatusBar, hideNavigationBar)

    val shownTypes: Int
        get() = typesFor(!hideStatusBar, !hideNavigationBar)

    companion object {
        val IMMERSIVE = SystemBarVisibility(hideStatusBar = true, hideNavigationBar = true)

        /**
         * The launcher's bars from the stored preferences. [statusHeldHidden] keeps the status bar
         * off whatever the preference says, for a screen that draws its own clock and battery.
         */
        fun forLauncher(prefs: UserPreferences, statusHeldHidden: Boolean = false): SystemBarVisibility =
            SystemBarVisibility(
                hideStatusBar = prefs.hideSystemStatusBar || statusHeldHidden,
                hideNavigationBar = prefs.hideSystemNavigationBar
            )

        private fun typesFor(status: Boolean, navigation: Boolean): Int {
            val top = if (status) {
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
            } else {
                0
            }
            val bottom = if (navigation) WindowInsetsCompat.Type.navigationBars() else 0
            return top or bottom
        }
    }
}

/**
 * Hides and shows the status and navigation bars independently, and asks the system to bring a
 * hidden bar back only for a deliberate swipe from the edge.
 *
 * The request does not survive the window losing and regaining the screen, so a window has to ask
 * again on resume and on focus gain. [installImmersiveMode] covers the rest.
 */
fun Window.applySystemBars(visibility: SystemBarVisibility) {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).let { controller ->
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visibility.hiddenTypes != 0) controller.hide(visibility.hiddenTypes)
        if (visibility.shownTypes != 0) controller.show(visibility.shownTypes)
    }
}

fun Window.hideSystemBars() = applySystemBars(SystemBarVisibility.IMMERSIVE)

/**
 * Hides both system bars for the window holding this view, for windows added straight through
 * WindowManager that have no [Window] of their own.
 */
fun View.hideWindowSystemBars() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowInsetsController?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsets.Type.systemBars())
        }
    } else {
        @Suppress("DEPRECATION")
        systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }
}

/**
 * Keeps a launcher window's system bars matching the user's hide-status-bar and
 * hide-navigation-bar preferences. [visibility] starts fully hidden until [follow] delivers the
 * stored preferences.
 */
class LauncherSystemBars(private val window: Window) {

    var visibility: SystemBarVisibility = SystemBarVisibility.IMMERSIVE
        private set

    private var followJob: Job? = null
    private val statusHeldHidden = MutableStateFlow(false)

    fun apply() = window.applySystemBars(visibility)

    fun holdStatusBarHidden(hold: Boolean) {
        statusHeldHidden.value = hold
    }

    fun follow(scope: CoroutineScope, preferences: Flow<UserPreferences>) {
        followJob?.cancel()
        followJob = scope.launch {
            combine(preferences, statusHeldHidden) { prefs, held -> SystemBarVisibility.forLauncher(prefs, held) }
                .distinctUntilChanged()
                .collect { next ->
                    visibility = next
                    apply()
                }
        }
    }
}

/**
 * Makes a window edge-to-edge and keeps the bars that [visibility] hides off the screen: the first
 * apply on decor attach, then an insets watchdog that re-hides any hidden bar an insets pass reports
 * visible. Resume and focus gain stay with the caller, through [applySystemBars].
 *
 * The watchdog hands the insets on to the decor view's own handling, which keeps them reaching the
 * content view and Compose reading the IME.
 */
fun ComponentActivity.installImmersiveMode(
    visibility: () -> SystemBarVisibility = { SystemBarVisibility.IMMERSIVE }
) {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
        val reappeared = visibleSystemBarTypes(insets) and visibility().hiddenTypes
        if (reappeared != 0) {
            WindowInsetsControllerCompat(window, window.decorView).hide(reappeared)
        }
        ViewCompat.onApplyWindowInsets(view, insets)
    }
    ViewCompat.requestApplyInsets(window.decorView)
    window.decorView.doOnAttach { window.applySystemBars(visibility()) }
}

private val SYSTEM_BAR_TYPES = listOf(
    WindowInsetsCompat.Type.statusBars(),
    WindowInsetsCompat.Type.captionBar(),
    WindowInsetsCompat.Type.navigationBars()
)

private fun visibleSystemBarTypes(insets: WindowInsetsCompat): Int =
    SYSTEM_BAR_TYPES.filter { insets.isVisible(it) }.fold(0) { mask, type -> mask or type }
