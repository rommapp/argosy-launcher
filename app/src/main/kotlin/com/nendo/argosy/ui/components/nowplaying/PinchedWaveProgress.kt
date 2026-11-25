package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import kotlin.math.PI

private const val NANOS_PER_MS = 1_000_000f
private const val TWO_PI = (2.0 * PI).toFloat()
private const val SAMPLE_STEP_PX = 2f

private class WaveMetrics(
    val height: Dp,
    val amplitude: Dp,
    val pinchRadius: Dp,
    val playedStroke: Dp,
    val thumbHeight: Dp
)

private val Defaults = ComponentDefaults.NowPlaying

private val RegularMetrics = WaveMetrics(
    height = Defaults.waveHeightDp.dp,
    amplitude = Defaults.waveAmplitudeDp.dp,
    pinchRadius = Defaults.pinchRadiusDp.dp,
    playedStroke = Defaults.playedStrokeDp.dp,
    thumbHeight = Defaults.thumbHeightDp.dp
)

private val CompactMetrics = WaveMetrics(
    height = Defaults.waveHeightCompactDp.dp,
    amplitude = Defaults.waveAmplitudeCompactDp.dp,
    pinchRadius = Defaults.pinchRadiusCompactDp.dp,
    playedStroke = Defaults.playedStrokeCompactDp.dp,
    thumbHeight = Defaults.thumbHeightCompactDp.dp
)

@Composable
fun PinchedWaveProgress(
    progress: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    analysis: TrackAnalysis? = null,
    durationMs: Long = 0L
) {
    val theme = LocalArgosyTheme.current
    val reduced = reducedMotion()
    val metrics = if (compact) CompactMetrics else RegularMetrics
    val density = LocalDensity.current
    var widthPx by remember { mutableIntStateOf(0) }
    val columnCount = with(density) {
        ((widthPx - Defaults.thumbWidthDp.dp.toPx()) / Defaults.waveBarPitchDp.dp.toPx()).toInt().coerceAtLeast(0)
    }
    val envelope = remember(analysis, durationMs, columnCount) {
        analysis?.let { waveEnvelope(it, durationMs, columnCount) }
    }
    val hasEnvelope = envelope != null
    val realAlpha = remember { Animatable(0f) }
    LaunchedEffect(hasEnvelope, reduced) {
        when {
            !hasEnvelope -> realAlpha.snapTo(0f)
            reduced -> realAlpha.snapTo(1f)
            else -> realAlpha.animateTo(1f, tween(Defaults.waveFadeInMs, easing = Motion.argosyEase))
        }
    }
    val clamped = progress.coerceIn(0f, 1f)
    val amplitudeFraction by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(Defaults.waveRelaxMs, easing = Motion.argosyEase),
        label = "pinchedWaveAmplitude"
    )
    val loudness by animateFloatAsState(
        targetValue = loudnessAt(envelope, clamped, Defaults.waveLoudnessFloor),
        animationSpec = if (reduced) snap() else tween(Defaults.waveLoudnessSettleMs, easing = Motion.argosyEase),
        label = "pinchedWaveLoudness"
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, reduced) {
        if (!playing || reduced) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val elapsedMs = (now - last) / NANOS_PER_MS
                last = now
                phase = (phase + elapsedMs / Defaults.wavePhaseCycleMs * TWO_PI).mod(TWO_PI)
            }
        }
    }
    val playedPath = remember { Path() }
    val restPath = remember { Path() }
    val playedColor = theme.focusAccent
    val restColor = theme.textDim.copy(alpha = Defaults.restAlpha)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.height)
            .onSizeChanged { widthPx = it.width }
    ) {
        val wave = WaveGeometry(
            thumbWidth = Defaults.thumbWidthDp.dp.toPx(),
            amplitude = metrics.amplitude.toPx() * amplitudeFraction * loudness,
            pinchRadius = metrics.pinchRadius.toPx(),
            wavelength = Defaults.waveWavelengthDp.dp.toPx(),
            phase = phase
        )
        val start = wave.thumbWidth / 2f
        val end = size.width - wave.thumbWidth / 2f
        val thumbX = start + (end - start) * clamped
        val shown = realAlpha.value
        if (envelope != null && shown > 0f) {
            drawEnvelope(envelope, start, end, thumbX, playedColor, theme.textDim, shown * Defaults.waveBarAlpha)
        }
        traceWave(restPath, wave, from = thumbX, to = end, thumbX = thumbX)
        drawPath(restPath, restColor, style = Stroke(Defaults.restStrokeDp.dp.toPx(), cap = StrokeCap.Round))
        traceWave(playedPath, wave, from = start, to = thumbX, thumbX = thumbX)
        drawPath(playedPath, playedColor, style = Stroke(metrics.playedStroke.toPx(), cap = StrokeCap.Round))
        drawThumb(thumbX, metrics.thumbHeight.toPx(), wave.thumbWidth, playedColor)
    }
}

@Composable
fun NowPlayingProgress(
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    analysis: TrackAnalysis? = null
) {
    val theme = LocalArgosyTheme.current
    val timeStyle = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium
    Column(modifier = modifier.fillMaxWidth()) {
        PinchedWaveProgress(
            progress = progressFraction(positionMs, durationMs),
            playing = playing,
            compact = compact,
            analysis = analysis,
            durationMs = durationMs
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatPlaybackTime(positionMs), style = timeStyle, color = theme.textDim)
            Text(text = formatPlaybackTime(durationMs), style = timeStyle, color = theme.textDim)
        }
    }
}

private class WaveGeometry(
    val thumbWidth: Float,
    val amplitude: Float,
    val pinchRadius: Float,
    val wavelength: Float,
    val phase: Float
)

private fun DrawScope.traceWave(path: Path, wave: WaveGeometry, from: Float, to: Float, thumbX: Float) {
    path.reset()
    val mid = size.height / 2f
    fun yAt(x: Float) = mid + pinchedWaveOffset(x, thumbX, wave.amplitude, wave.pinchRadius, wave.wavelength, wave.phase)
    path.moveTo(from, yAt(from))
    var x = from + SAMPLE_STEP_PX
    while (x < to) {
        path.lineTo(x, yAt(x))
        x += SAMPLE_STEP_PX
    }
    path.lineTo(to, yAt(to))
}

private fun DrawScope.drawEnvelope(
    envelope: FloatArray,
    start: Float,
    end: Float,
    thumbX: Float,
    playedColor: Color,
    restColor: Color,
    alpha: Float
) {
    if (envelope.isEmpty()) return
    val barWidth = Defaults.waveBarWidthDp.dp.toPx()
    val mid = size.height / 2f
    val maxHalf = (mid - barWidth / 2f).coerceAtLeast(0f)
    val pitch = (end - start) / envelope.size
    for (column in envelope.indices) {
        val x = start + (column + 0.5f) * pitch
        val half = maxHalf * envelope[column]
        drawLine(
            color = if (x <= thumbX) playedColor else restColor,
            start = Offset(x, mid - half),
            end = Offset(x, mid + half),
            strokeWidth = barWidth,
            cap = StrokeCap.Round,
            alpha = alpha
        )
    }
}

private fun DrawScope.drawThumb(x: Float, height: Float, width: Float, color: Color) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x - width / 2f, (size.height - height) / 2f),
        size = Size(width, height),
        cornerRadius = CornerRadius(width / 2f, width / 2f)
    )
}
