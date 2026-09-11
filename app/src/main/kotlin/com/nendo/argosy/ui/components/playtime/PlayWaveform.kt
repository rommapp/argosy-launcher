package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

private const val HOURS = 24
private const val DAYS = 7
private const val HALF_HOUR = 0.5f
private const val PEAK_LABEL_FLIP_HOUR = 12

data class WaveformGeometry(
    val rowPaths: List<Path>,
    val rowHeightPx: Float,
    val rowGapPx: Float,
    val peakRow: Int?,
    val peakHour: Int?
) {
    fun rowTop(row: Int): Float = row * (rowHeightPx + rowGapPx)
    fun hourCenterX(hour: Int, widthPx: Float): Float = (hour + HALF_HOUR) / HOURS * widthPx
}

/**
 * The seven weekday waveforms, computed once per data and size: each row is a monotone cubic
 * through the 24 hourly totals, mirrored around the row centre and sharing one amplitude scale
 * across the week. A row with no play keeps a hairline so the weekday is visibly empty.
 */
fun buildWaveformGeometry(
    weekHourMs: List<List<Long>>,
    widthPx: Float,
    rowHeightPx: Float,
    rowGapPx: Float,
    minAmplitudePx: Float,
    samplesPerHour: Int
): WaveformGeometry {
    val rows = List(DAYS) { row ->
        val source = weekHourMs.getOrNull(row)
        List(HOURS) { hour -> source?.getOrElse(hour) { 0L } ?: 0L }
    }
    val max = rows.maxOf { it.max() }
    val peak = waveformPeak(rows)
    val halfRow = rowHeightPx / 2f
    val sampleCount = HOURS * samplesPerHour.coerceAtLeast(1) + 1
    val xs = FloatArray(HOURS + 2) { k -> k - HALF_HOUR }
    val paths = rows.mapIndexed { row, values ->
        val ys = FloatArray(HOURS + 2) { k ->
            val hour = (k - 1).mod(HOURS)
            val v = values[hour]
            if (max <= 0L) 0f else (v.toDouble() / max).toFloat()
        }
        val samples = MonotoneCubic.sample(xs, ys, sampleCount, 0f, HOURS.toFloat())
        val centerY = row * (rowHeightPx + rowGapPx) + halfRow
        val amplitudes = FloatArray(sampleCount) { i ->
            (samples[i].coerceIn(0f, 1f) * (halfRow - minAmplitudePx)).coerceAtLeast(0f) + minAmplitudePx
        }
        val path = Path()
        amplitudes.forEachIndexed { i, amplitude ->
            val x = widthPx * i / (sampleCount - 1)
            if (i == 0) path.moveTo(x, centerY - amplitude) else path.lineTo(x, centerY - amplitude)
        }
        for (i in amplitudes.indices.reversed()) {
            path.lineTo(widthPx * i / (sampleCount - 1), centerY + amplitudes[i])
        }
        path.close()
        path
    }
    return WaveformGeometry(paths, rowHeightPx, rowGapPx, peak?.first, peak?.second)
}

@Composable
fun PlayWaveform(
    weekHourMs: List<List<Long>>,
    weekdayLabels: List<String>,
    hourLabels: List<String>,
    peakLabel: String?,
    selectedWeekday: Int?,
    selectedHour: Int?,
    onCellTap: (weekday: Int, hour: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val rowHeight = (ComponentDefaults.PlayTimeChart.waveRowHeight * s).dp
    val rowGap = (ComponentDefaults.PlayTimeChart.waveRowGap * s).dp
    val labelWidth = (ComponentDefaults.PlayTimeChart.waveLabelWidth * s).dp
    val minAmplitude = (ComponentDefaults.PlayTimeChart.waveMinAmplitude * s).dp
    val restAlpha = ComponentDefaults.PlayTimeChart.waveRestAlpha
    val markerWidth = Dimens.borderThin
    val plotHeight = rowHeight * DAYS + rowGap * (DAYS - 1)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.width(labelWidth), verticalArrangement = Arrangement.spacedBy(rowGap)) {
                weekdayLabels.take(DAYS).forEachIndexed { index, label ->
                    Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == selectedWeekday) theme.textPrimary else theme.textMute,
                            maxLines = 1
                        )
                    }
                }
            }
            BoxWithConstraints(modifier = Modifier.weight(1f).height(plotHeight)) {
                val widthPx = with(density) { maxWidth.toPx() }
                val rowHeightPx = with(density) { rowHeight.toPx() }
                val rowGapPx = with(density) { rowGap.toPx() }
                val minAmplitudePx = with(density) { minAmplitude.toPx() }
                val geometry = remember(weekHourMs, widthPx, rowHeightPx, rowGapPx, minAmplitudePx) {
                    buildWaveformGeometry(
                        weekHourMs = weekHourMs,
                        widthPx = widthPx,
                        rowHeightPx = rowHeightPx,
                        rowGapPx = rowGapPx,
                        minAmplitudePx = minAmplitudePx,
                        samplesPerHour = ComponentDefaults.PlayTimeChart.waveSamplesPerHour
                    )
                }
                val markerColor = theme.textPrimary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(plotHeight)
                        .pointerInput(geometry) {
                            detectTapGestures { offset ->
                                val row = (offset.y / (rowHeightPx + rowGapPx)).toInt().coerceIn(0, DAYS - 1)
                                val hour = (offset.x / size.width * HOURS).toInt().coerceIn(0, HOURS - 1)
                                onCellTap(row, hour)
                            }
                        }
                ) {
                    geometry.rowPaths.forEachIndexed { row, path ->
                        val top = geometry.rowTop(row)
                        val brush = Brush.verticalGradient(
                            colors = listOf(accent, accent.copy(alpha = ComponentDefaults.PlayTimeChart.waveEdgeAlpha)),
                            startY = top,
                            endY = top + rowHeightPx
                        )
                        val alpha = if (selectedWeekday == null || selectedWeekday == row) 1f else restAlpha
                        drawPath(path = path, brush = brush, alpha = alpha)
                    }
                    if (selectedWeekday != null && selectedHour != null) {
                        val x = geometry.hourCenterX(selectedHour, size.width)
                        val top = geometry.rowTop(selectedWeekday)
                        drawLine(
                            color = markerColor,
                            start = Offset(x, top),
                            end = Offset(x, top + rowHeightPx),
                            strokeWidth = markerWidth.toPx()
                        )
                    }
                }
                val peakRow = geometry.peakRow
                val peakHour = geometry.peakHour
                if (peakLabel != null && peakRow != null && peakHour != null) {
                    val peakX = with(density) { geometry.hourCenterX(peakHour, widthPx).toDp() }
                    val peakY = with(density) { geometry.rowTop(peakRow).toDp() }
                    val flip = peakHour >= PEAK_LABEL_FLIP_HOUR
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(plotHeight)
                            .padding(
                                top = peakY,
                                start = if (flip) 0.dp else peakX + Dimens.spacingXs,
                                end = if (flip) maxWidth - peakX + Dimens.spacingXs else 0.dp
                            ),
                        contentAlignment = if (flip) Alignment.TopEnd else Alignment.TopStart
                    ) {
                        Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.Center) {
                            Text(
                                text = peakLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Row(modifier = Modifier.fillMaxWidth().padding(start = labelWidth)) {
            hourLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textMute,
                    maxLines = 1,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

fun waveformPeak(weekHourMs: List<List<Long>>): Pair<Int, Int>? {
    var best: Pair<Int, Int>? = null
    var bestValue = 0L
    weekHourMs.forEachIndexed { row, values ->
        values.forEachIndexed { hour, value ->
            if (value > bestValue) {
                bestValue = value
                best = row to hour
            }
        }
    }
    return best
}
