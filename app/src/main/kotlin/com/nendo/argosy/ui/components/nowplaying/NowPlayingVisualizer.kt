package com.nendo.argosy.ui.components.nowplaying

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion

private const val NANOS_PER_SECOND = 1_000_000_000f

private val Defaults = ComponentDefaults.NowPlaying

/**
 * A full-bleed backdrop of soft accent bars across the lower part of the screen, one per band of
 * [analysis] at the playback position. [positionMs] is a polled sample; between samples the
 * position advances with frame time while [playing]. Paused bars fall to rest. Draws nothing
 * without an analysis or with reduced motion.
 */
@Composable
fun NowPlayingVisualizer(
    analysis: TrackAnalysis?,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    if (analysis == null || reducedMotion()) return
    val accent = LocalArgosyTheme.current.focusAccent
    val currentAnalysis by rememberUpdatedState(analysis)
    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val settled = remember { FloatArray(TrackAnalysis.BAND_COUNT) }
    var levels by remember { mutableStateOf(FloatArray(TrackAnalysis.BAND_COUNT)) }

    LaunchedEffect(playing) {
        val fresh = FloatArray(TrackAnalysis.BAND_COUNT)
        var sampledMs = currentPosition
        var sampledAt = withFrameNanos { it }
        var last = sampledAt
        while (true) {
            val now = withFrameNanos { it }
            if (currentPosition != sampledMs) {
                sampledMs = currentPosition
                sampledAt = now
            }
            if (playing) {
                currentAnalysis.bandsAt(interpolatedPositionMs(sampledMs, now - sampledAt, currentDuration), fresh)
            } else {
                fresh.fill(0f)
            }
            settleLevels(settled, fresh, Defaults.visualizerFallRatioPerSecond, (now - last) / NANOS_PER_SECOND)
            last = now
            levels = settled.copyOf()
            if (!playing && settled.all { it == 0f }) break
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val gap = Defaults.visualizerBarGapDp.dp.toPx()
                val glowRadius = Defaults.visualizerGlowDp.dp.toPx()
                val count = TrackAnalysis.BAND_COUNT
                val barWidth = ((size.width - gap * (count + 1)) / count).coerceAtLeast(0f)
                val maxHeight = size.height * Defaults.visualizerHeightRatio
                val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
                val glowPaint = Paint().apply {
                    asFrameworkPaint().apply {
                        isAntiAlias = true
                        color = accent.copy(alpha = Defaults.visualizerGlowAlpha).toArgb()
                        maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
                    }
                }
                val fill = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = Defaults.visualizerBarAlpha), accent.copy(alpha = 0f)),
                    startY = size.height - maxHeight,
                    endY = size.height
                )
                onDrawBehind {
                    val shown = levels
                    drawIntoCanvas { canvas ->
                        for (band in 0 until count) {
                            val height = maxHeight * shown[band]
                            if (height <= 0f) continue
                            val left = gap + band * (barWidth + gap)
                            val top = size.height - height
                            canvas.drawRoundRect(
                                left, top, left + barWidth, size.height + corner.x,
                                corner.x, corner.y, glowPaint
                            )
                        }
                    }
                    for (band in 0 until count) {
                        val height = maxHeight * shown[band]
                        if (height <= 0f) continue
                        val left = gap + band * (barWidth + gap)
                        drawRoundRect(
                            brush = fill,
                            topLeft = Offset(left, size.height - height),
                            size = Size(barWidth, height + corner.y),
                            cornerRadius = corner
                        )
                    }
                }
            }
    )
}
