package com.nendo.argosy.ui.primitives

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.trackGradientEnd
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.abs

private const val PHASE_OFF = 0f
private const val PHASE_COLLAPSED = 1f
private const val PHASE_ON = 2f
private const val THUMB_LAG = 2f

/**
 * Progress of a switched control between off (0) and on (2). Turning off collapses the fill
 * (2 to 1) and then dims the track (1 to 0); turning on runs the same two phases in reverse. A
 * change mid-run continues from the current point.
 */
@Composable
fun rememberSwitchedPhase(on: Boolean): Float {
    val reduced = reducedMotion()
    val phase = remember { Animatable(if (on) PHASE_ON else PHASE_OFF) }
    LaunchedEffect(on, reduced) {
        val target = if (on) PHASE_ON else PHASE_OFF
        if (reduced) {
            phase.animateTo(target, tween(Motion.durationMicro, easing = Motion.argosyEase))
            return@LaunchedEffect
        }
        val crossesMidpoint = if (on) phase.value < PHASE_COLLAPSED else phase.value > PHASE_COLLAPSED
        if (crossesMidpoint) {
            phase.animateTo(PHASE_COLLAPSED, phaseTween(abs(phase.value - PHASE_COLLAPSED)))
        }
        phase.animateTo(target, phaseTween(abs(phase.value - target)))
    }
    return phase.value
}

private fun phaseTween(distance: Float) =
    tween<Float>((Motion.durationMicro * distance.coerceIn(0f, 1f)).toInt(), easing = Motion.argosyEase)

@Composable
fun SwitchedTrackSlider(
    value: Float,
    phase: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val trackHeight = ComponentDefaults.TrackSlider.trackHeight * s
    val trackRadius = ComponentDefaults.TrackSlider.trackRadius * s
    val thumbSize = ComponentDefaults.TrackSlider.thumbSize * s
    val fillColor = if (focused) lerp(theme.focusAccent, Color.White, 0.25f) else theme.focusAccent
    val trackAlpha = ComponentDefaults.SwitchedSlider.offTrackAlpha +
        (1f - ComponentDefaults.SwitchedSlider.offTrackAlpha) * phase.coerceIn(PHASE_OFF, PHASE_COLLAPSED)
    val fillScale = (phase - PHASE_COLLAPSED).coerceIn(0f, 1f)
    val thumbScale = ((phase - PHASE_COLLAPSED) * THUMB_LAG - 1f).coerceIn(0f, 1f)
    val interactive = phase >= PHASE_ON
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((thumbSize * 2).dp)
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { offset -> onValueChange((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val fraction = value.coerceIn(0f, 1f)
        val trackPx = trackHeight.dp.toPx()
        val thumbPx = thumbSize.dp.toPx()
        val centerY = size.height / 2f
        val radius = CornerRadius(trackRadius.dp.toPx(), trackRadius.dp.toPx())
        val fillWidth = size.width * fraction
        drawRoundRect(
            color = theme.surfaceElevated.copy(alpha = theme.surfaceElevated.alpha * trackAlpha),
            topLeft = Offset(0f, centerY - trackPx / 2f),
            size = Size(size.width, trackPx),
            cornerRadius = radius,
        )
        if (fillScale < 1f && fillWidth > 0f) {
            drawRoundRect(
                color = theme.hairlineHigh.copy(alpha = theme.hairlineHigh.alpha * (1f - fillScale)),
                topLeft = Offset(0f, centerY - trackPx / 2f),
                size = Size(fillWidth, trackPx),
                cornerRadius = radius,
                style = Stroke(width = Dimens.borderThin.toPx()),
            )
        }
        if (fillScale > 0f) {
            val fillHeight = trackPx * fillScale
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        fillColor,
                        trackGradientEnd(fillColor, theme.isDark, ComponentDefaults.TrackSlider.gradientShiftRatio)
                    ),
                    startX = 0f,
                    endX = fillWidth.coerceAtLeast(1f),
                ),
                topLeft = Offset(0f, centerY - fillHeight / 2f),
                size = Size(fillWidth, fillHeight),
                cornerRadius = radius,
            )
        }
        if (thumbScale > 0f) {
            val thumbHeight = thumbPx * thumbScale
            drawRoundRect(
                color = theme.textPrimary,
                topLeft = Offset(
                    (fillWidth - thumbPx / 2f).coerceIn(0f, size.width - thumbPx),
                    centerY - thumbHeight / 2f
                ),
                size = Size(thumbPx, thumbHeight),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * Leading square of a switched row: accent-filled with [icon] while [on], neutral with the icon
 * struck through while off. Tapping it calls [onToggle].
 */
@Composable
fun SwitchChip(
    icon: ImageVector,
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val shape = RoundedCornerShape((ComponentDefaults.SwitchedSlider.chipRadiusDp * s).dp)
    val fill by animateColorAsState(
        targetValue = if (on) theme.focusAccent else theme.surfaceElevated,
        animationSpec = tween(Motion.durationMicro, easing = Motion.argosyEase),
        label = "switch-chip-fill",
    )
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier
            .size((ComponentDefaults.SwitchedSlider.chipSizeDp * s).dp)
            .clip(shape)
            .background(fill)
            .clickableNoFocus(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = on,
            animationSpec = tween(Motion.durationMicro, easing = Motion.argosyEase),
            label = "switch-chip-icon",
        ) { isOn ->
            val tint = if (isOn) onAccent else theme.textDim
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(Dimens.iconSm)
                    .then(if (isOn) Modifier else Modifier.strikeThrough(tint)),
            )
        }
    }
}

private fun Modifier.strikeThrough(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = Dimens.borderThick.toPx(),
    )
}
