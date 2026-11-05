package com.nendo.argosy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.roundToInt

private const val METER_SCALE = 1000L
private const val SPARKLINE_MIN_RANGE_C = 10f

@Composable
internal fun HardwareStatsBlock(stats: HardwareStatsUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        if (stats.hasCpu) {
            StatTile(
                value = stats.cpuMhz?.let { clockLabel(it) },
                label = stringResource(R.string.ui_quick_settings_stats_cpu),
                tempC = stats.cpuTempC,
                load = stats.cpuLoad,
                history = stats.cpuTempHistory,
                modifier = tile
            )
        }
        if (stats.hasGpu) {
            StatTile(
                value = stats.gpuMhz?.let { clockLabel(it) },
                label = stringResource(R.string.ui_quick_settings_stats_gpu),
                tempC = stats.gpuTempC,
                load = stats.gpuLoad,
                history = stats.gpuTempHistory,
                modifier = tile
            )
        }
        if (stats.hasCooling) {
            CoolingTile(stats = stats, modifier = tile)
        }
    }
}

@Composable
private fun CoolingTile(stats: HardwareStatsUi, modifier: Modifier) {
    val rpm = stats.fanRpm
    if (rpm == null) {
        StatTile(
            value = stats.batteryTempC?.let { temperatureLabel(it) },
            label = stringResource(R.string.ui_quick_settings_stats_battery),
            tempC = null,
            load = null,
            history = null,
            modifier = modifier,
            valueColor = heatColor(stats.batteryTempC)
        )
        return
    }
    StatTile(
        value = fanSpeedLabel(rpm),
        label = stringResource(R.string.ui_quick_settings_stats_fan),
        tempC = stats.batteryTempC,
        load = stats.fanLoad,
        history = null,
        modifier = modifier,
        tempIsBattery = true
    )
}

@Composable
private fun StatTile(
    value: String?,
    label: String,
    tempC: Float?,
    load: Float?,
    history: List<Float>?,
    modifier: Modifier,
    valueColor: Color = LocalArgosyTheme.current.textPrimary,
    tempIsBattery: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = modifier
            .background(theme.surfaceRaised.copy(alpha = ComponentDefaults.QuickPanel.statsTileFillAlpha), shape)
            .padding(Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (tempC != null) {
                if (tempIsBattery) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = stringResource(R.string.ui_quick_settings_stats_battery),
                        tint = heatColor(tempC),
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Spacer(Modifier.width(Dimens.spacingXs))
                }
                Text(
                    text = temperatureLabel(tempC),
                    style = MaterialTheme.typography.labelMedium,
                    color = heatColor(tempC),
                    maxLines = 1
                )
            }
        }
        Text(
            text = value ?: "",
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            maxLines = 1
        )
        if (load != null) {
            SegmentedMeterBar(
                totalBytes = METER_SCALE,
                segments = listOf(MaterialTheme.colorScheme.primary to (load * METER_SCALE).toLong()),
                trackColor = theme.hairlineLow
            )
        }
        if (history != null && tempC != null) {
            TempSparkline(history = history, color = heatColor(tempC))
        }
    }
}

@Composable
private fun TempSparkline(history: List<Float>, color: Color) {
    val scale = LocalUiScale.current.scale
    val height = (ComponentDefaults.QuickPanel.statsSparklineHeightDp * scale).dp
    val strokeWidth = (ComponentDefaults.QuickPanel.statsSparklineStrokeDp * scale).dp
    val lineColor = color.copy(alpha = ComponentDefaults.QuickPanel.statsSparklineAlpha)
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        if (history.size < 2) return@Canvas
        val low = history.min()
        val range = maxOf(history.max() - low, SPARKLINE_MIN_RANGE_C)
        val stroke = strokeWidth.toPx()
        val usable = size.height - stroke
        val step = size.width / (HardwareStatsUi.HISTORY_SIZE - 1)
        val start = size.width - step * (history.size - 1)
        val path = Path()
        history.forEachIndexed { index, temp ->
            val x = start + step * index
            val y = stroke / 2 + usable * (1f - (temp - low) / range)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun clockLabel(mhz: Int): String =
    if (mhz >= 1000) {
        stringResource(R.string.ui_quick_settings_stats_ghz, mhz / 1000f)
    } else {
        stringResource(R.string.ui_quick_settings_stats_mhz, mhz)
    }

@Composable
private fun fanSpeedLabel(rpm: Int): String =
    if (rpm >= 10_000) {
        stringResource(R.string.ui_quick_settings_stats_krpm, rpm / 1000f)
    } else {
        stringResource(R.string.ui_quick_settings_stats_rpm, rpm)
    }

@Composable
private fun temperatureLabel(tempC: Float): String =
    stringResource(R.string.ui_quick_settings_stats_celsius, tempC.roundToInt())

@Composable
private fun heatColor(tempC: Float?): Color = when {
    tempC == null -> LocalArgosyTheme.current.textDim
    tempC >= ComponentDefaults.QuickPanel.statsTempHotC -> MaterialTheme.colorScheme.error
    tempC >= ComponentDefaults.QuickPanel.statsTempWarmC -> LocalLauncherTheme.current.semanticColors.warning
    else -> LocalArgosyTheme.current.textPrimary
}
