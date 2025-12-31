package com.nendo.argosy.ui.components

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

@Stable
class ScreenDimmerState {
    private val _lastActivityTime = mutableLongStateOf(System.currentTimeMillis())
    val lastActivityTime: Long get() = _lastActivityTime.longValue

    fun recordActivity() {
        _lastActivityTime.longValue = System.currentTimeMillis()
    }
}

@Composable
fun rememberScreenDimmerState(): ScreenDimmerState = remember { ScreenDimmerState() }

@Composable
fun ScreenDimmerOverlay(
    enabled: Boolean,
    timeoutMs: Long,
    dimLevel: Float,
    dimmerState: ScreenDimmerState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isDimmed by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, timeoutMs, dimmerState.lastActivityTime) {
        if (!enabled) {
            isDimmed = false
            return@LaunchedEffect
        }

        isDimmed = false
        delay(timeoutMs)

        val elapsed = System.currentTimeMillis() - dimmerState.lastActivityTime
        if (elapsed >= timeoutMs) {
            isDimmed = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    dimmerState.recordActivity()
                }
            }
    ) {
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
                        detectTapGestures {
                            dimmerState.recordActivity()
                        }
                    }
            )
        }
    }
}
