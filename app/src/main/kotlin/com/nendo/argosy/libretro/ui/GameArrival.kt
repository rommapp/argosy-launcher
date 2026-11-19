package com.nendo.argosy.libretro.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion

/**
 * The arrival cover's current opacity, 1 black to 0 clear, for tests that step the clock frame
 * by frame.
 */
val ArrivalAlpha = SemanticsPropertyKey<Float>("ArrivalAlpha")

private var SemanticsPropertyReceiver.arrivalAlpha by ArrivalAlpha

/**
 * A full-size black cover over the game, opaque until [revealed] and then fading off over
 * [ComponentDefaults.LaunchOverlay.arrivalMs] at an even rate per frame, linear like LaunchVeil.
 * The game's SurfaceView ignores View alpha, so the game fades in by this cover fading out.
 * Reduced motion reveals at once. It takes no input and draws nothing once clear.
 */
@Composable
fun GameArrival(revealed: Boolean, modifier: Modifier = Modifier) {
    val reduced = reducedMotion()
    val target = if (revealed) 0f else 1f
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = if (reduced) 0 else ComponentDefaults.LaunchOverlay.arrivalMs,
            easing = LinearEasing
        ),
        label = "gameArrival"
    )
    val alpha = if (reduced) target else animated
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { arrivalAlpha = alpha }
            .drawBehind { if (alpha > 0f) drawRect(Color.Black, alpha = alpha) }
    )
}
