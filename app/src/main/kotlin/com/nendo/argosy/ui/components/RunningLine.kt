package com.nendo.argosy.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.LocalRunningBreath
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

private val Line = ComponentDefaults.RunningLine

private const val GLOW_FADE_STOP = 0.7f

/**
 * Where a running line sits on a cover: along the bottom inner edge, or the top one when the
 * bottom edge is taken by a platform tab or spine. [frameInset] is the border it sits inside.
 */
data class RunningLinePlacement(
    val atTop: Boolean,
    val frameInset: Dp,
    val cornerRadius: Dp
)

/**
 * Draws [mark]'s running line over the content: a short green line inside one edge with a glow
 * into the cover. It breathes with the shared clock, holds at its peak while [holdAtPeak], and
 * turns dashed and still while the game is closing. No mark draws nothing.
 */
@Composable
fun Modifier.runningLine(
    mark: RunningGameUi?,
    placement: RunningLinePlacement,
    holdAtPeak: Boolean
): Modifier {
    if (mark == null) return this
    val breath = LocalRunningBreath.current
    val color = LocalLauncherTheme.current.semanticColors.success
    val closing = mark.isClosing
    val description = stringResource(
        if (closing) R.string.ui_game_card_saving else R.string.ui_game_card_running
    )
    return this
        .semantics { contentDescription = description }
        .drawWithContent {
            drawContent()
            val level = if (holdAtPeak || closing) 1f else breath.value
            drawRunningLine(color, placement, level, closing)
        }
}

private fun DrawScope.drawRunningLine(
    color: Color,
    placement: RunningLinePlacement,
    level: Float,
    closing: Boolean
) {
    val frame = placement.frameInset.toPx()
    val endInset = frame + maxOf(placement.cornerRadius.toPx(), Line.minEndInsetDp.dp.toPx())
    val left = endInset
    val right = size.width - endInset
    if (right <= left) return
    val height = Line.heightDp.dp.toPx()
    val gap = Line.edgeGapDp.dp.toPx()
    val lineTop = if (placement.atTop) frame + gap else size.height - frame - gap - height
    val glowEdge = if (placement.atTop) lineTop + height else lineTop
    val glowAlpha = if (closing) {
        Line.savingGlowAlpha
    } else {
        breathe(Line.glowTroughAlpha, Line.glowPeakAlpha, level)
    }
    drawGlow(color.copy(alpha = glowAlpha), left, right, glowEdge, placement.atTop)
    val centerY = lineTop + height / 2f
    val lineAlpha = if (closing) Line.linePeakAlpha else breathe(Line.lineTroughAlpha, Line.linePeakAlpha, level)
    if (closing) {
        drawLine(
            color = color.copy(alpha = lineAlpha),
            start = Offset(left, centerY),
            end = Offset(right, centerY),
            strokeWidth = height,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(Line.savingDashDp.dp.toPx(), Line.savingGapDp.dp.toPx())
            )
        )
    } else {
        drawLine(
            color = color.copy(alpha = lineAlpha),
            start = Offset(left + height / 2f, centerY),
            end = Offset(right - height / 2f, centerY),
            strokeWidth = height,
            cap = StrokeCap.Round
        )
    }
}

private fun breathe(trough: Float, peak: Float, level: Float): Float = trough + (peak - trough) * level

private fun DrawScope.drawGlow(color: Color, left: Float, right: Float, edgeY: Float, downward: Boolean) {
    val glowHeight = Line.glowHeightDp.dp.toPx()
    val halfWidth = (right - left) / 2f * (1f + Line.glowSpreadRatio * 2f)
    val center = Offset((left + right) / 2f, edgeY)
    val top = if (downward) edgeY else edgeY - glowHeight
    clipRect(left = center.x - halfWidth, top = top, right = center.x + halfWidth, bottom = top + glowHeight) {
        scale(scaleX = halfWidth / glowHeight, scaleY = 1f, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color,
                    GLOW_FADE_STOP to Color.Transparent,
                    center = center,
                    radius = glowHeight
                ),
                radius = glowHeight,
                center = center
            )
        }
    }
}
