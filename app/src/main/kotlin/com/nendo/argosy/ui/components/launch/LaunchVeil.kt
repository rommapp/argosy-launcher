package com.nendo.argosy.ui.components.launch

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The veil's current opacity, 0 clear to 1 black, for tests that step the clock frame by frame.
 */
val VeilAlpha = SemanticsPropertyKey<Float>("VeilAlpha")

private var SemanticsPropertyReceiver.veilAlpha by VeilAlpha

/**
 * A full-size black layer that fades in over [ComponentDefaults.LaunchOverlay.veilMs] while
 * [veiled] holds and out when it drops. First drawn while [veiled], it starts where a fade begun
 * at [veiledSinceMs] (elapsed realtime) would stand. Reduced motion cuts at once; it takes no
 * input and draws nothing while clear.
 */
@Composable
fun LaunchVeil(veiled: Boolean, modifier: Modifier = Modifier, veiledSinceMs: Long? = null) {
    val reduced = reducedMotion()
    val veilMs = ComponentDefaults.LaunchOverlay.veilMs
    val target = if (veiled) 1f else 0f
    val animated = remember {
        val elapsedMs = veiledSinceMs?.let { SystemClock.elapsedRealtime() - it }
        Animatable(veilStartAlpha(veiled, elapsedMs, veilMs, reduced))
    }
    LaunchedEffect(target, reduced) {
        animated.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = veilRemainingMs(animated.value, target, veilMs, reduced),
                easing = LinearEasing
            )
        )
    }
    val alpha = if (reduced) target else animated.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { veilAlpha = alpha }
            .drawBehind { if (alpha > 0f) drawRect(Color.Black, alpha = alpha) }
    )
}
