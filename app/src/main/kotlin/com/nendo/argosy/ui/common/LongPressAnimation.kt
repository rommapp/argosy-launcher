package com.nendo.argosy.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class LongPressAnimationConfig(
    val targetScale: Float = 1.3f,
    val tapThreshold: Float = 1.1f,
    val holdDelayMs: Long = 250L,
    val scaleUpDurationMs: Int = 500,
    val withFadeEffect: Boolean = true,
)

@Stable
class LongPressAnimationState(
    private val config: LongPressAnimationConfig,
    private val scope: CoroutineScope,
) {
    val scale = Animatable(1f)
    val alpha = Animatable(1f)
    val whiteOverlay = Animatable(0f)

    var isAnimating by mutableStateOf(false)
        private set

    private var touchAnimationJob: Job? = null
    private var gamepadAnimationJob: Job? = null
    private var actionTriggered = false

    fun handleGamepadHold(isHolding: Boolean) {
        if (isHolding) {
            isAnimating = true
            gamepadAnimationJob = scope.launch {
                delay(config.holdDelayMs)
                scale.animateTo(
                    targetValue = config.targetScale,
                    animationSpec = tween(
                        durationMillis = config.scaleUpDurationMs,
                        easing = EaseIn,
                    ),
                )
            }
        } else if (gamepadAnimationJob != null) {
            gamepadAnimationJob?.cancel()
            gamepadAnimationJob = null
            scope.launch {
                launch { scale.animateTo(1f, tween(150)) }
                if (config.withFadeEffect) {
                    launch { alpha.animateTo(1f, tween(150)) }
                    launch { whiteOverlay.animateTo(0f, tween(150)) }
                }
            }
            isAnimating = false
        }
    }

    internal fun onGestureDown(
        onLongPress: () -> Unit,
    ) {
        actionTriggered = false
        touchAnimationJob = scope.launch {
            if (config.withFadeEffect) isAnimating = true

            delay(config.holdDelayMs)

            scale.animateTo(
                targetValue = config.targetScale,
                animationSpec = tween(
                    durationMillis = config.scaleUpDurationMs,
                    easing = EaseIn,
                ),
            )

            actionTriggered = true
            onLongPress()

            if (config.withFadeEffect) {
                launch {
                    scale.animateTo(
                        2f,
                        tween(durationMillis = 100, easing = EaseOut),
                    )
                }
                launch {
                    alpha.animateTo(
                        0f,
                        tween(durationMillis = 100, easing = EaseOut),
                    )
                }
                launch {
                    whiteOverlay.animateTo(
                        1f,
                        tween(durationMillis = 100, easing = EaseOut),
                    )
                }
                delay(100)
                scale.snapTo(1f)
                alpha.snapTo(1f)
                whiteOverlay.snapTo(0f)
                isAnimating = false
            } else {
                scale.animateTo(1f, tween(150))
            }
        }
    }

    internal fun onGestureUp(
        wasReleased: Boolean,
        onClick: () -> Unit,
    ) {
        if (actionTriggered) return

        touchAnimationJob?.cancel()
        touchAnimationJob = null

        scope.launch {
            launch { scale.animateTo(1f, tween(150)) }
            if (config.withFadeEffect) {
                launch { alpha.animateTo(1f, tween(150)) }
                launch { whiteOverlay.animateTo(0f, tween(150)) }
                isAnimating = false
            }
        }

        if (wasReleased && scale.value < config.tapThreshold) {
            onClick()
        }
    }
}

@Composable
fun rememberLongPressAnimationState(
    config: LongPressAnimationConfig = LongPressAnimationConfig(),
): LongPressAnimationState {
    val scope = rememberCoroutineScope()
    return remember(config) {
        LongPressAnimationState(config, scope)
    }
}

@Composable
fun GamepadLongPressEffect(
    isHolding: Boolean,
    state: LongPressAnimationState,
) {
    LaunchedEffect(isHolding) {
        state.handleGamepadHold(isHolding)
    }
}

fun Modifier.longPressGraphicsLayer(
    state: LongPressAnimationState,
    applyAlpha: Boolean = true,
): Modifier = this.graphicsLayer {
    scaleX = state.scale.value
    scaleY = state.scale.value
    if (applyAlpha) {
        alpha = state.alpha.value
    }
}

fun Modifier.longPressGesture(
    key: Any?,
    state: LongPressAnimationState,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = this.pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        state.onGestureDown(onLongPress)
        val up = waitForUpOrCancellation()
        state.onGestureUp(
            wasReleased = up != null,
            onClick = onClick,
        )
    }
}
