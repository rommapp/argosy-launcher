package com.nendo.argosy.util

import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnAttach

/**
 * Takes the status and gesture bars off this window, and asks the system to bring them back only
 * for a deliberate swipe from the edge.
 *
 * The request does not survive the window losing and regaining the screen, so a window that wants
 * to stay bare has to ask again on resume and on focus gain. [installImmersiveMode] covers the
 * rest.
 */
fun Window.hideSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).let { controller ->
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * Makes a window immersive and keeps it that way.
 *
 * Three separate things bring the bars back and each needs its own answer. The first hide waits for
 * decor attach because a call from onCreate races initial layout on a cold start, no-ops, and
 * leaves the bars drawn until the first focus change. The watchdog re-hides on any insets pass that
 * reports them visible, which is what retracts a transient reveal that the swipe behaviour alone
 * leaves on screen - it hides inline rather than posting, because a post lands a tick later and the
 * bars are drawn in between. Resume and focus gain are the caller's, through [hideSystemBars].
 *
 * The watchdog returns the decor view's own inset handling rather than the insets it was given: it
 * replaces that handling, and skipping it stops insets reaching the content view, which leaves
 * Compose reading zero for the IME and every text field unusable.
 */
fun ComponentActivity.installImmersiveMode() {
    enableEdgeToEdge()
    ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
        if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        ViewCompat.onApplyWindowInsets(view, insets)
    }
    ViewCompat.requestApplyInsets(window.decorView)
    window.decorView.doOnAttach { window.hideSystemBars() }
}
