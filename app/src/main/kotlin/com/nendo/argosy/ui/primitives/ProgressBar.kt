package com.nendo.argosy.ui.primitives

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.trackGradientEnd
import androidx.compose.ui.graphics.Brush

enum class ProgressBarStyle { Active, Paused, Working }

/** V2 progress bar: 6dp rounded track on surfaceElevated; Working = striped semantic.progress fill. */
@Composable
fun ArgosyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    style: ProgressBarStyle = ProgressBarStyle.Active,
    tint: Color? = null,
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val fillColor = when (style) {
        ProgressBarStyle.Active -> tint ?: theme.focusAccent
        ProgressBarStyle.Paused -> theme.textMute
        ProgressBarStyle.Working ->
            if (theme.isDark) ColorTokens.Semantic.Dark.progress else ColorTokens.Semantic.Light.progress
    }
    val trackColor = theme.surfaceElevated
    val stripePhase = if (style == ProgressBarStyle.Working && !reducedMotion()) {
        rememberInfiniteTransition(label = "stripe-drift").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Motion.durationDrawer * 2, easing = LinearEasing),
            ),
            label = "stripe-phase",
        ).value
    } else 0f
    Canvas(modifier = modifier.fillMaxWidth().height((ComponentDefaults.ProgressBar.height * s).dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val fraction = progress?.coerceIn(0f, 1f) ?: 1f
        val fillWidth = size.width * fraction
        if (fillWidth <= 0f) return@Canvas
        val fillClip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, fillWidth, size.height, radius))
        }
        clipPath(fillClip) {
            if (style == ProgressBarStyle.Working) {
                drawRect(color = fillColor.copy(alpha = 0.35f), size = Size(fillWidth, size.height))
                val stripe = (ComponentDefaults.ProgressBar.stripeWidth * s).dp.toPx()
                val gap = (ComponentDefaults.ProgressBar.stripeGap * s).dp.toPx()
                val period = stripe + gap
                var x = -size.height - period + stripePhase * period
                while (x < fillWidth) {
                    drawLine(
                        color = fillColor,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = stripe,
                    )
                    x += period
                }
            } else {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            fillColor,
                            trackGradientEnd(fillColor, theme.isDark, ComponentDefaults.ProgressBar.gradientShiftRatio)
                        ),
                        startX = 0f,
                        endX = fillWidth.coerceAtLeast(1f),
                    ),
                    size = Size(fillWidth, size.height),
                )
                if (progress != null && fraction > 0f && fraction < 1f) {
                    val capWidth = size.height / 2f
                    drawRect(
                        color = lerp(fillColor, Color.White, 0.3f),
                        topLeft = Offset((fillWidth - capWidth).coerceAtLeast(0f), 0f),
                        size = Size(capWidth.coerceAtMost(fillWidth), size.height),
                    )
                }
            }
        }
    }
}

/**
 * Indeterminate twin of [ArgosyProgressBar]: a segment [ComponentDefaults.ProgressBar.sweepWidthRatio]
 * of the track wide sweeps across it every [sweepMs]. It rests centred under reduced motion or a zero
 * [sweepMs].
 */
@Composable
fun ArgosyIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    sweepMs: Int = ComponentDefaults.ProgressBar.sweepMs,
    tint: Color? = null,
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val fillColor = tint ?: theme.focusAccent
    val trackColor = theme.surfaceElevated
    val moving = sweepMs > 0 && !reducedMotion()
    val sweep: State<Float> = if (moving) {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = sweepMs, easing = Motion.argosyEase),
            ),
            label = "sweep-phase",
        )
    } else {
        remember { mutableFloatStateOf(RESTING_SWEEP_PHASE) }
    }
    Canvas(modifier = modifier.fillMaxWidth().height((ComponentDefaults.ProgressBar.height * s).dp)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val segment = size.width * ComponentDefaults.ProgressBar.sweepWidthRatio
        val start = -segment + sweep.value * (size.width + segment)
        val trackClip = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
        }
        clipPath(trackClip) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        fillColor,
                        trackGradientEnd(fillColor, theme.isDark, ComponentDefaults.ProgressBar.gradientShiftRatio)
                    ),
                    startX = start,
                    endX = (start + segment).coerceAtLeast(start + 1f),
                ),
                topLeft = Offset(start, 0f),
                size = Size(segment, size.height),
                cornerRadius = radius,
            )
        }
    }
}

private const val RESTING_SWEEP_PHASE = 0.5f
