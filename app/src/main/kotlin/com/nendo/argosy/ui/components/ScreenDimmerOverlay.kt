package com.nendo.argosy.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * How long a screen that last saw activity at [lastActivityAtMs] has left before the dimmer
 * should cover it, given [timeoutMs] of allowed idleness; zero once the timeout has already
 * passed. Both timestamps share one clock.
 */
fun screenDimmerRemainingMs(lastActivityAtMs: Long, nowMs: Long, timeoutMs: Long): Long =
    (timeoutMs - (nowMs - lastActivityAtMs)).coerceAtLeast(0L)

/**
 * Covers [content] with a black scrim once [timeoutMs] has passed since [lastActivityAtMs],
 * an [SystemClock.elapsedRealtime] stamp owned by whoever tracks user activity for the app. A
 * tap on the scrim calls [onWake], whose job is to move that stamp forward; the scrim lifts when
 * it does, and it never lifts on its own.
 */
@Composable
fun ScreenDimmerOverlay(
    enabled: Boolean,
    timeoutMs: Long,
    dimLevel: Float,
    lastActivityAtMs: Long,
    onWake: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDimmed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, timeoutMs, lastActivityAtMs) {
        isDimmed = false
        if (!enabled) return@LaunchedEffect
        delay(screenDimmerRemainingMs(lastActivityAtMs, SystemClock.elapsedRealtime(), timeoutMs))
        isDimmed = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(
            visible = isDimmed,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimLevel))
                    .pointerInput(Unit) {
                        detectTapGestures { onWake() }
                    }
            )
        }
    }
}
