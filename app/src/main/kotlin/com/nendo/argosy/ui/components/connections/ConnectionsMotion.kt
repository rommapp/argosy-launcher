package com.nendo.argosy.ui.components.connections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlinx.coroutines.delay

@Composable
internal fun reducedMotion(): Boolean = LocalMotionTier.current == MotionTier.Reduced

@Composable
internal fun SearchPulseIcon(active: Boolean, content: @Composable () -> Unit) {
    val pulsing = active && !reducedMotion()
    Box(
        modifier = if (pulsing) Modifier.pulseRings(LocalArgosyTheme.current.focusAccent) else Modifier,
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun Modifier.pulseRings(color: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "connections-pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionTokens.Tween.longMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connections-pulse-phase"
    )
    val offset = MotionTokens.Tween.noticeProgressMs.toFloat() / MotionTokens.Tween.longMs
    val strokeDp = Dimens.borderMedium
    return drawBehind {
        val stroke = strokeDp.toPx()
        listOf(phase, (phase + offset).mod(1f)).forEach { ringPhase ->
            val eased = LinearOutSlowInEasing.transform(ringPhase)
            val scale = 1f + (ComponentDefaults.Connections.pulseRingScale - 1f) * eased
            drawCircle(
                color = color.copy(alpha = ComponentDefaults.Connections.pulseRingAlpha * (1f - eased)),
                radius = size.minDimension / 2f * scale,
                style = Stroke(width = stroke)
            )
        }
    }
}

@Composable
internal fun ProgressRingIcon(active: Boolean, content: @Composable () -> Unit) {
    val modifier = when {
        !active -> Modifier
        reducedMotion() -> Modifier.staticRing(
            LocalArgosyTheme.current.focusAccent.copy(alpha = ComponentDefaults.Connections.reducedRingAlpha)
        )
        else -> Modifier.orbitingRing(LocalArgosyTheme.current.hairlineHigh, LocalArgosyTheme.current.focusAccent)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun Modifier.staticRing(color: Color): Modifier {
    val strokeDp = Dimens.borderMedium
    return drawBehind {
        val stroke = strokeDp.toPx()
        drawCircle(color = color, radius = size.minDimension / 2f + stroke, style = Stroke(width = stroke))
    }
}

@Composable
private fun Modifier.orbitingRing(trackColor: Color, arcColor: Color): Modifier {
    val transition = rememberInfiniteTransition(label = "connections-ring")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(ComponentDefaults.ProgressBar.sweepMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "connections-ring-rotation"
    )
    val strokeDp = Dimens.borderMedium
    return drawBehind {
        val stroke = strokeDp.toPx()
        val radius = size.minDimension / 2f + stroke
        val topLeft = Offset(center.x - radius, center.y - radius)
        drawCircle(color = trackColor, radius = radius, style = Stroke(width = stroke))
        drawArc(
            color = arcColor,
            startAngle = rotation - QUARTER_TURN_DEGREES,
            sweepAngle = ComponentDefaults.Connections.pairArcDegrees.toFloat(),
            useCenter = false,
            topLeft = topLeft,
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
internal fun Modifier.successTint(active: Boolean): Modifier {
    val strength = remember { Animatable(0f) }
    val reduced = reducedMotion()
    LaunchedEffect(active, reduced) {
        if (!active) {
            strength.snapTo(0f)
            return@LaunchedEffect
        }
        strength.snapTo(1f)
        if (reduced) {
            delay(MotionTokens.Tween.longMs.toLong())
            strength.snapTo(0f)
        } else {
            strength.animateTo(0f, tween(MotionTokens.Tween.longMs, easing = Motion.argosyEase))
        }
    }
    val color = LocalLauncherTheme.current.semanticColors.success
    val inset = Dimens.spacingSm
    val radius = Dimens.radiusControl
    return drawBehind {
        val alpha = ComponentDefaults.Connections.successTintAlpha * strength.value
        if (alpha <= 0f) return@drawBehind
        val insetPx = inset.toPx()
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(insetPx, 0f),
            size = Size((size.width - insetPx * 2f).coerceAtLeast(0f), size.height),
            cornerRadius = CornerRadius(radius.toPx())
        )
    }
}

@Composable
internal fun Modifier.arrivalEntrance(staggerOrder: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(staggerOrder.toLong() * ComponentDefaults.AutoGrid.entryStepMs)
        progress.animateTo(1f, tween(MotionTokens.Tween.contentMs, easing = Motion.argosyEase))
    }
    val rise = ComponentDefaults.Connections.foundRiseDp.dp
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.toPx()
    }
}

private const val FULL_TURN_DEGREES = 360f
private const val QUARTER_TURN_DEGREES = 90f
