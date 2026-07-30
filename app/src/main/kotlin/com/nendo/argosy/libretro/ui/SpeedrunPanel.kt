package com.nendo.argosy.libretro.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.libretro.speedrun.SpeedrunPhase
import com.nendo.argosy.libretro.speedrun.SpeedrunRunState
import com.nendo.argosy.ui.theme.LocalLauncherTheme

private const val REFERENCE_PANEL_WIDTH_DP = 280f

@Composable
fun SpeedrunPanel(
    state: SpeedrunRunState,
    modifier: Modifier = Modifier
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val elapsedMs by produceState(0L, state) {
        value = state.elapsedAt(SystemClock.elapsedRealtime())
        if (state.phase == SpeedrunPhase.RUNNING) {
            while (true) {
                withFrameMillis { value = state.elapsedAt(SystemClock.elapsedRealtime()) }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        val scale = (maxWidth.value / REFERENCE_PANEL_WIDTH_DP).coerceIn(0.6f, 1.4f)
        val listState = rememberLazyListState()

        LaunchedEffect(state.currentIndex) {
            if (state.phase == SpeedrunPhase.RUNNING) {
                listState.animateScrollToItem((state.currentIndex - 2).coerceAtLeast(0))
            }
        }

        Column(modifier = Modifier.padding((12 * scale).dp)) {
            Text(
                text = state.categoryName,
                style = MaterialTheme.typography.labelSmall,
                fontSize = (11 * scale).sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = formatRunTime(elapsedMs),
                fontSize = (34 * scale).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = when (state.phase) {
                    SpeedrunPhase.FINISHED -> MaterialTheme.colorScheme.primary
                    SpeedrunPhase.PAUSED -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (state.phase == SpeedrunPhase.PAUSED) {
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = (10 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.padding(vertical = (4 * scale).dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy((2 * scale).dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(state.segments, key = { index, _ -> index }) { index, name ->
                    val timeMs = state.splitTimesMs.getOrNull(index)
                    val pbMs = state.pbSplitTimesMs.getOrNull(index)
                    val segmentDuration = timeMs?.let { t ->
                        val previous = if (index == 0) 0L else state.splitTimesMs.getOrNull(index - 1)
                        previous?.let { t - it }
                    }
                    val best = state.bestSegmentDurationsMs.getOrNull(index)
                    SegmentRow(
                        name = name,
                        timeMs = timeMs,
                        deltaMs = if (timeMs != null && pbMs != null) timeMs - pbMs else null,
                        isGold = segmentDuration != null && best != null && segmentDuration < best,
                        isCurrent = state.phase != SpeedrunPhase.IDLE &&
                            state.phase != SpeedrunPhase.FINISHED &&
                            index == state.currentIndex,
                        isSkipped = index < state.currentIndex && timeMs == null,
                        scale = scale
                    )
                }
            }
            PanelFooter(state = state, scale = scale)
        }
    }
}

@Composable
private fun SegmentRow(
    name: String,
    timeMs: Long?,
    deltaMs: Long?,
    isGold: Boolean,
    isCurrent: Boolean,
    isSkipped: Boolean,
    scale: Float
) {
    val semanticColors = LocalLauncherTheme.current.semanticColors
    val rowColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        timeMs != null -> MaterialTheme.colorScheme.onSurface
        isSkipped -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0f)
            )
            .padding(horizontal = (4 * scale).dp, vertical = (3 * scale).dp)
    ) {
        Text(
            text = name,
            fontSize = (13 * scale).sp,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = rowColor,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        if (deltaMs != null) {
            Text(
                text = formatDelta(deltaMs),
                fontSize = (12 * scale).sp,
                fontFamily = FontFamily.Monospace,
                color = when {
                    isGold -> semanticColors.warning
                    deltaMs < 0 -> semanticColors.success
                    else -> MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(end = (6 * scale).dp)
            )
        }
        Text(
            text = when {
                timeMs != null -> formatRunTime(timeMs)
                isSkipped -> "-"
                else -> ""
            },
            fontSize = (13 * scale).sp,
            fontFamily = FontFamily.Monospace,
            color = rowColor
        )
    }
}

@Composable
private fun PanelFooter(state: SpeedrunRunState, scale: Float) {
    Column(modifier = Modifier.padding(top = (6 * scale).dp)) {
        state.pbTimeMs?.let { FooterRow("Personal Best", formatRunTime(it), scale) }
        state.sessionBestMs?.let { FooterRow("Session Best", formatRunTime(it), scale) }
        state.sumOfBestMs?.let { FooterRow("Sum of Best", formatRunTime(it), scale) }
        if (state.attemptCount > 0) {
            Text(
                text = "Attempt ${state.attemptCount}",
                fontSize = (10 * scale).sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun FooterRow(label: String, value: String, scale: Float) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = (11 * scale).sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = (11 * scale).sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

private fun formatRunTime(ms: Long): String {
    val totalTenths = ms / 100
    val tenths = totalTenths % 10
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
    } else {
        "%d:%02d.%d".format(minutes, seconds, tenths)
    }
}

private fun formatDelta(ms: Long): String {
    val sign = if (ms < 0) "−" else "+"
    val abs = kotlin.math.abs(ms)
    val totalSeconds = abs / 1000
    val tenths = (abs / 100) % 10
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return if (minutes > 0) {
        "%s%d:%02d.%d".format(sign, minutes, seconds, tenths)
    } else {
        "%s%d.%d".format(sign, seconds, tenths)
    }
}
