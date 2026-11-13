package com.nendo.argosy.ui.screens.savesync

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.model.GameActivitySnapshot
import com.nendo.argosy.domain.model.DeviceKind
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.common.playSessionDeviceLabelRes
import com.nendo.argosy.ui.components.playtime.playHourLabel
import com.nendo.argosy.ui.components.playtime.waveformPeak
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SaveGameDevice
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTimeVerbose
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private const val MS_PER_MINUTE = 60_000L
private const val PERCENT = 100L
private const val PRESENTED_DEVICES = 6

@Composable
internal fun SaveSyncPresentation(viewModel: SaveSyncViewModel) {
    val active = DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    LaunchedEffect(active) { viewModel.setPresentationActive(active) }
    if (!active) return
    val activity by viewModel.gameActivity.collectAsState()
    val snapshot = activity ?: return
    PresentOnCompanion(SlotOwner("savesync.game"), snapshot.toSlot())
}

@Composable
private fun GameActivitySnapshot.toSlot(): PresentationSlot.SaveGame {
    val context = LocalContext.current
    val isDark = LocalArgosyTheme.current.isDark
    val configuration = LocalConfiguration.current
    return remember(this, isDark, configuration) { buildSlot(context, isDark) }
}

private fun GameActivitySnapshot.buildSlot(context: Context, isDark: Boolean): PresentationSlot.SaveGame {
    val res = context.resources
    val series = ChartPalette.series(isDark)
    val subtitle = context.joined(
        buildList {
            platformName.takeIf { it.isNotBlank() }?.let(::add)
            if (sessionCount > 0) {
                add(res.getQuantityString(R.plurals.savesync_presentation_sessions, sessionCount, sessionCount))
                add(context.getString(R.string.savesync_presentation_average, context.minutesLabel(totalActiveMs / sessionCount)))
                add(context.getString(R.string.savesync_presentation_longest, context.minutesLabel(longestSessionMs)))
            }
            lastPlayed?.let {
                add(context.getString(R.string.savesync_presentation_last_played, formatRelativeTimeVerbose(context, it)))
            }
            if (versionCount > 0) {
                add(res.getQuantityString(R.plurals.savesync_presentation_versions, versionCount, versionCount))
            }
        }
    )
    val peakLabel = waveformPeak(weekHourMs)?.let { (row, hour) ->
        context.getString(
            R.string.savesync_presentation_peak,
            DayOfWeek.of(row + 1).getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            playHourLabel(context, hour)
        )
    }
    val totalMs = devices.sumOf { it.activeMs }.coerceAtLeast(1L)
    val presented = devices.take(PRESENTED_DEVICES).mapIndexed { index, device ->
        val labelRes = playSessionDeviceLabelRes(device.deviceId)
        SaveGameDevice(
            label = labelRes?.let(context::getString)
                ?: DeviceKind.withoutRepeatedManufacturer(device.deviceName),
            kind = if (labelRes != null) DeviceKind.UNKNOWN else DeviceKind.classify(device.deviceName, "android", null),
            activeMs = device.activeMs,
            valueLabel = context.minutesLabel(device.activeMs),
            shareLabel = context.getString(R.string.savesync_presentation_share_percent, (device.activeMs * PERCENT / totalMs).toInt()),
            detail = context.joined(
                listOf(
                    res.getQuantityString(R.plurals.savesync_presentation_device_sessions, device.sessionCount, device.sessionCount),
                    context.getString(R.string.savesync_presentation_device_last_played, formatRelativeTimeVerbose(context, device.lastPlayed))
                )
            ),
            color = series[index % series.size],
            isThisDevice = device.isThisDevice
        )
    }
    return PresentationSlot.SaveGame(
        title = title,
        subtitle = subtitle,
        totalLabel = totalActiveMs.takeIf { it >= MS_PER_MINUTE }?.let { context.minutesLabel(it) },
        coverPath = coverPath,
        backgroundPath = backgroundPath,
        days = days,
        saveDayIndices = days.indices.filter { days[it].date in saveDates }.toSet(),
        weekHourMs = weekHourMs,
        peakLabel = peakLabel,
        devices = presented,
        totalDeviceMs = presented.sumOf { it.activeMs },
        deviceSegments = presented.map { it.color to it.activeMs }
    )
}

private fun Context.joined(parts: List<String>): String =
    parts.reduceOrNull { acc, part -> getString(R.string.savesync_presentation_joined, acc, part) }.orEmpty()

private fun Context.minutesLabel(ms: Long): String =
    formatPlayTime(this, (ms / MS_PER_MINUTE).toInt())
