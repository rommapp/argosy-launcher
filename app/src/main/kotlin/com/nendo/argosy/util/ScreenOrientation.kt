package com.nendo.argosy.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.Display
import androidx.core.content.ContextCompat
import com.nendo.argosy.data.preferences.ScreenOrientationMode
import com.nendo.argosy.data.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The orientation request for a fixed [ScreenOrientationMode], or null for AUTO, which leaves the
 * window to its own default. Both fixed modes follow the sensor within their axis, so either
 * landscape or either portrait direction works with the system rotation lock on.
 */
fun ScreenOrientationMode.fixedRequest(): Int? = when (this) {
    ScreenOrientationMode.AUTO -> null
    ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
}

val Activity.isOnMainDisplay: Boolean
    get() = ContextCompat.getDisplayOrDefault(this).displayId == Display.DEFAULT_DISPLAY

/**
 * Keeps a launcher activity in the orientation the user picked while it sits on the main display.
 * Another display keeps the activity's own default.
 */
fun Activity.followScreenOrientation(scope: CoroutineScope, preferences: Flow<UserPreferences>) {
    scope.launch {
        preferences
            .map { it.screenOrientation }
            .distinctUntilChanged()
            .collect { mode ->
                if (isOnMainDisplay) {
                    requestedOrientation = mode.fixedRequest() ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
    }
}
