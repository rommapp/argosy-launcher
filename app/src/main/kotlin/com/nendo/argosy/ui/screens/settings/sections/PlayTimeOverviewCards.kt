package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import com.nendo.argosy.data.repository.PlayStreakCalculator
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.PlayStatTile
import com.nendo.argosy.ui.components.playtime.PlayCalendar
import com.nendo.argosy.ui.components.playtime.PlayCoverMosaic
import com.nendo.argosy.ui.components.playtime.PlayFigureCard
import com.nendo.argosy.ui.components.playtime.PlayFigureHeader
import com.nendo.argosy.ui.components.playtime.PlayShareBand
import com.nendo.argosy.ui.components.playtime.PlayWaveform
import com.nendo.argosy.ui.components.playtime.ShareSegment
import com.nendo.argosy.ui.components.playtime.waveformPeak
import com.nendo.argosy.ui.screens.settings.PlayTimeEntryUi
import com.nendo.argosy.ui.screens.settings.PlayTimeScrub
import com.nendo.argosy.ui.screens.settings.PlayTimeState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatClockTime
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.util.formatRelativeTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private const val HOURS_IN_DAY = 24
private const val HOUR_LABEL_STEP = 6
private const val PERCENT = 100
private const val CARD_FILL_ALPHA = 0.3f

private fun percentOf(part: Long, whole: Long): Int =
    if (whole <= 0L) 0 else ((part * PERCENT) / whole).toInt()

private fun hourLabel(context: android.content.Context, hour: Int): String {
    val millis = LocalDate.now().atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return formatClockTime(context, millis)
}

private fun weekdayNames(style: TextStyle): List<String> =
    DayOfWeek.entries.map { it.getDisplayName(style, Locale.getDefault()) }

@Composable
internal fun PlayTimeMessageCard(title: String, message: String, isWorking: Boolean) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_FILL_ALPHA))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        if (isWorking) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconSm),
                strokeWidth = Dimens.borderMedium,
                color = theme.textMute
            )
        }
    }
}

internal data class DayGame(
    val title: String,
    val platformName: String,
    val activeMs: Long,
    val sessionCount: Int,
    val coverPath: String?
)


@Composable
internal fun PlayTimeCalendarCard(
    state: PlayTimeState,
    isFocused: Boolean,
    isEngaged: Boolean,
    onFocus: () -> Unit,
    onCellTap: (Int) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val othersColor = theme.textMute
    val slots = ComponentDefaults.PlayTimeChart.seriesSlots
    val slotOfSlug = remember(state.platforms, slots) {
        state.platforms.take(slots).withIndex().associate { (index, entry) -> entry.key to index }
    }
    val sessionsByDate = remember(state.sessions) {
        state.sessions.groupBy { it.startTime.atZone(zone).toLocalDate() }
    }
    val daySlots = remember(state.days, sessionsByDate, slotOfSlug) {
        state.days.map { day ->
            sessionsByDate[day.date]
                ?.groupBy { it.platformSlug }
                ?.maxByOrNull { (_, rows) -> rows.sumOf { it.activeMs } }
                ?.key
                ?.let { slotOfSlug[it] }
        }
    }
    val legend = remember(daySlots, state.platforms, series) {
        val present = daySlots.filterNotNull().toSet()
        state.platforms.take(slots).mapIndexedNotNull { index, entry ->
            if (index in present) Triple(entry.key, entry.name, series[index]) else null
        }
    }
    val hasOthers = remember(daySlots, state.days) {
        state.days.indices.any { daySlots[it] == null && state.days[it].activeMs > 0L }
    }
    val selected = playTimeScrubIndex(PlayTimeScrub.CALENDAR, state)
    val selectedDay = selected?.let { state.days.getOrNull(it) }
    val dayGames = remember(sessionsByDate, state.coverPaths, selectedDay?.date) {
        val date = selectedDay?.date ?: return@remember emptyList()
        sessionsByDate[date].orEmpty()
            .groupBy { it.gameId }
            .map { (gameId, rows) ->
                DayGame(
                    title = rows.first().gameTitle,
                    platformName = rows.first().platformName,
                    activeMs = rows.sumOf { it.activeMs },
                    sessionCount = rows.size,
                    coverPath = state.coverPaths[gameId]
                )
            }
            .filter { it.activeMs >= MIN_DISPLAY_MS }
            .sortedByDescending { it.activeMs }
    }
    PlayFigureCard(isFocused = isFocused, isEngaged = isEngaged, onFocus = onFocus) {
        PlayFigureHeader(
            title = selectedDay?.let {
                formatMonthDay(it.date.atStartOfDay(zone).toInstant())
            } ?: stringResource(R.string.settings_play_time_activity_empty),
            value = selectedDay?.let { playTimeLabel(it.activeMs) },
            subtitle = if (isEngaged) {
                pluralStringResource(
                    R.plurals.settings_play_time_day_game_count,
                    dayGames.size,
                    dayGames.size
                )
            } else {
                null
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                PlayCalendar(
                    days = state.days,
                    daySlots = daySlots,
                    seriesColors = series,
                    othersColor = othersColor,
                    selectedIndex = selected,
                    showSelection = isEngaged,
                    onCellTap = onCellTap
                )
                if (legend.isNotEmpty() || hasOthers) {
                    PlatformLegend(
                        entries = legend,
                        othersLabel = if (hasOthers) stringResource(R.string.settings_play_time_platform_others) else null,
                        othersColor = othersColor
                    )
                }
            }
            Crossfade(
                targetState = isEngaged,
                label = "playTimeCalendarPanel",
                modifier = Modifier.width((ComponentDefaults.PlayTimeChart.calendarPanelWidth * s).dp)
            ) { engaged ->
                if (engaged) {
                    DayBreakdown(games = dayGames)
                } else {
                    WindowSummary(state = state)
                }
            }
        }
    }
}

@Composable
private fun WindowSummary(state: PlayTimeState) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val summary = state.summary
    if (summary == null) {
        Text(
            text = stringResource(R.string.settings_play_time_summary_empty),
            style = MaterialTheme.typography.bodySmall,
            color = theme.textMute
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        PlayStatTile(
            label = pluralStringResource(
                R.plurals.settings_play_time_summary_window,
                state.days.size,
                state.days.size
            ),
            value = playTimeLabel(summary.totalActiveMs)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            SummaryFact(
                label = stringResource(R.string.settings_play_time_summary_platforms),
                value = summary.platformCount.toString(),
                detail = summary.platformName
            )
            SummaryFact(
                label = stringResource(R.string.settings_play_time_summary_peak),
                value = stringResource(
                    R.string.settings_play_time_summary_peak_value,
                    summary.weekday.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    hourLabel(context, summary.hour)
                ),
                detail = null
            )
        }
        SummaryFact(
            label = stringResource(R.string.settings_play_time_summary_device),
            value = if (summary.isThisDevice) {
                stringResource(R.string.settings_play_time_this_device)
            } else {
                summary.deviceName
            },
            detail = null
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
            SummaryFact(
                label = stringResource(R.string.settings_play_time_streak_current),
                value = pluralStringResource(
                    R.plurals.settings_play_time_streak_days,
                    state.currentStreak,
                    state.currentStreak
                ),
                detail = null
            )
            SummaryFact(
                label = stringResource(R.string.settings_play_time_streak_longest),
                value = pluralStringResource(
                    R.plurals.settings_play_time_streak_days,
                    state.longestStreak,
                    state.longestStreak
                ),
                detail = null
            )
        }
    }
}

@Composable
internal fun PlayTimeFactsCard(state: PlayTimeState) {
    val zone = remember { ZoneId.systemDefault() }
    val lengths = remember(state.sessions) { state.sessions.map { it.activeMs } }
    val average = remember(lengths) {
        PlayStreakCalculator.trimmedMean(lengths, ComponentDefaults.PlayTimeChart.factsTrimRatio)
    }
    val longest = remember(lengths) { lengths.maxOrNull() ?: 0L }
    val activeDays = remember(state.days) { state.days.count { it.activeMs > 0L } }
    val perActiveDay = remember(state.days, activeDays) {
        if (activeDays == 0) 0L else state.days.sumOf { it.activeMs } / activeDays
    }
    val busiestDay = remember(state.days) { state.days.maxByOrNull { it.activeMs }?.takeIf { it.activeMs > 0L } }
    val facts = listOf(
        stringResource(R.string.settings_play_time_facts_average) to playTimeLabel(average),
        stringResource(R.string.settings_play_time_facts_longest) to playTimeLabel(longest),
        stringResource(R.string.settings_play_time_facts_sessions) to state.sessions.size.toString(),
        stringResource(R.string.settings_play_time_facts_days_played) to stringResource(
            R.string.settings_play_time_facts_days_played_value,
            activeDays,
            state.days.size
        ),
        stringResource(R.string.settings_play_time_facts_per_active_day) to playTimeLabel(perActiveDay),
        stringResource(R.string.settings_play_time_facts_busiest_day) to (
            busiestDay?.let { formatMonthDay(it.date.atStartOfDay(zone).toInstant()) }
                ?: stringResource(R.string.settings_play_time_facts_none)
            )
    )
    val columns = ComponentDefaults.PlayTimeChart.factsColumns
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_FILL_ALPHA))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        facts.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                row.forEach { (label, value) ->
                    SummaryFact(
                        label = label,
                        value = value,
                        detail = null,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SummaryFact(label: String, value: String, detail: String?, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMute,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DayBreakdown(games: List<DayGame>, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val maxRows = ComponentDefaults.PlayTimeChart.calendarDetailRows
    val cover = (ComponentDefaults.PlayTimeChart.calendarDetailCover * s).dp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        if (games.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_play_time_day_empty),
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMute
            )
            return@Column
        }
        games.take(maxRows).forEach { game ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                DayGameCover(coverPath = game.coverPath, size = cover)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title.ifBlank { stringResource(R.string.settings_play_time_unknown_game) },
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = game.platformName,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textMute,
                        maxLines = 1
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = playTimeLabel(game.activeMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textPrimary,
                        maxLines = 1
                    )
                    if (game.sessionCount > 1) {
                        Text(
                            text = stringResource(
                                R.string.settings_play_time_day_session_count,
                                game.sessionCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textMute,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        if (games.size > maxRows) {
            Text(
                text = pluralStringResource(
                    R.plurals.settings_play_time_day_more,
                    games.size - maxRows,
                    games.size - maxRows
                ),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute
            )
        }
    }
}

@Composable
private fun DayGameCover(coverPath: String?, size: Dp) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val fallback = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_FILL_ALPHA)
    val model = coverPath?.let { rememberFileImageModel(it) }
    Box(
        modifier = Modifier
            .size(width = size, height = size * COVER_ASPECT)
            .clip(shape)
            .background(fallback)
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private const val COVER_ASPECT = 1.4f

@Composable
internal fun PlayTimeWaveformCard(
    state: PlayTimeState,
    isFocused: Boolean,
    isEngaged: Boolean,
    onFocus: () -> Unit,
    onCellTap: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val weekday = playTimeScrubIndex(PlayTimeScrub.WEEKDAY, state)
    val hour = playTimeScrubIndex(PlayTimeScrub.HOUR, state)
    val cellMs = if (weekday != null && hour != null) {
        state.weekHourMs.getOrNull(weekday)?.getOrNull(hour) ?: 0L
    } else {
        null
    }
    val peak = remember(state.weekHourMs) { waveformPeak(state.weekHourMs) }
    val shortNames = remember { weekdayNames(TextStyle.SHORT) }
    val fullNames = remember { weekdayNames(TextStyle.FULL) }
    val hourLabels = remember(context) {
        (0 until HOURS_IN_DAY step HOUR_LABEL_STEP).map { hourLabel(context, it) }
    }
    PlayFigureCard(isFocused = isFocused, isEngaged = isEngaged, onFocus = onFocus) {
        PlayFigureHeader(
            title = if (weekday != null && hour != null && peak != null) {
                stringResource(R.string.settings_play_time_wave_header, fullNames[weekday], hourLabel(context, hour))
            } else {
                stringResource(R.string.settings_play_time_wave_empty)
            },
            value = cellMs?.takeIf { peak != null }?.let { playTimeLabel(it) }
        )
        PlayWaveform(
            weekHourMs = state.weekHourMs,
            weekdayLabels = shortNames,
            hourLabels = hourLabels,
            peakLabel = peak?.let { (row, col) ->
                stringResource(R.string.settings_play_time_wave_peak, shortNames[row], hourLabel(context, col))
            },
            selectedWeekday = weekday.takeIf { peak != null && isEngaged },
            selectedHour = hour.takeIf { peak != null && isEngaged },
            onCellTap = onCellTap
        )
    }
}

@Composable
private fun PlatformLegend(
    entries: List<Triple<String, String, Color>>,
    othersLabel: String?,
    othersColor: Color
) {
    val s = LocalUiScale.current.scale
    val swatch = (ComponentDefaults.PlayTimeChart.legendSwatch * s).dp
    val perColumn = ComponentDefaults.PlayTimeChart.legendRowsPerColumn
    val all = remember(entries, othersLabel, othersColor) {
        entries + listOfNotNull(othersLabel?.let { Triple("", it, othersColor) })
    }
    val columns = remember(all, perColumn) {
        val count = (all.size + perColumn - 1) / perColumn
        List(count.coerceAtLeast(1)) { column ->
            all.drop(column * perColumn).take(perColumn)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        columns.forEach { column ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                column.forEach { (slug, name, color) ->
                    PlatformLegendEntry(
                        slug = slug.ifBlank { null },
                        name = name,
                        color = color,
                        swatch = swatch
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformLegendEntry(slug: String?, name: String, color: Color, swatch: Dp) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val glyph = slug?.let { remember(it) { PlatformIconAssets.resolveAssetUri(context, it) } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Box(modifier = Modifier.size(swatch).clip(CircleShape).background(color))
        if (glyph != null) {
            AsyncImage(
                model = glyph,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconXs)
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun shareSegments(
    entries: List<PlayTimeEntryUi>,
    othersLabel: String,
    withGlyph: Boolean
): List<ShareSegment> {
    val theme = LocalArgosyTheme.current
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val total = entries.sumOf { it.activeMs }
    val slots = ComponentDefaults.PlayTimeChart.seriesSlots
    val named = entries.take(slots).mapIndexed { index, entry ->
        ShareSegment(
            label = entry.name,
            valueMs = entry.activeMs,
            color = series[index],
            valueLabel = playTimeLabel(entry.activeMs),
            shareLabel = stringResource(R.string.settings_play_time_share_percent, percentOf(entry.activeMs, total)),
            platformSlug = entry.key.takeIf { withGlyph },
            badge = if (entry.isThisDevice) stringResource(R.string.settings_play_time_this_device) else null
        )
    }
    val othersMs = entries.drop(slots).sumOf { it.activeMs }
    if (othersMs <= 0L) return named
    return named + ShareSegment(
        label = othersLabel,
        valueMs = othersMs,
        color = theme.textMute,
        valueLabel = playTimeLabel(othersMs),
        shareLabel = stringResource(R.string.settings_play_time_share_percent, percentOf(othersMs, total))
    )
}

@Composable
internal fun PlayTimePlatformBand(state: PlayTimeState) {
    PlayShareBand(
        title = stringResource(R.string.settings_play_time_platform_band_title),
        segments = shareSegments(
            entries = state.platforms,
            othersLabel = stringResource(R.string.settings_play_time_platform_others),
            withGlyph = true
        ),
        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
internal fun PlayTimeDeviceBand(state: PlayTimeState) {
    PlayShareBand(
        title = stringResource(R.string.settings_play_time_device_band_title),
        segments = shareSegments(
            entries = state.devices,
            othersLabel = stringResource(R.string.settings_play_time_device_others),
            withGlyph = false
        ),
        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
internal fun PlayTimeMosaicCard(
    state: PlayTimeState,
    isFocused: Boolean,
    isEngaged: Boolean,
    onFocus: () -> Unit,
    onTileTap: (Int) -> Unit
) {
    val context = LocalContext.current
    val tiles = remember(state.games, state.coverPaths, state.mosaicFolded) { playTimeMosaicTiles(state) }
    val selected = playTimeScrubIndex(playTimeMosaicScrub(state), state)
    val tile = selected?.let { tiles.getOrNull(it) }
    val othersLabel = stringResource(R.string.settings_play_time_mosaic_others)
    val othersCountLabel = tiles.firstOrNull { it.gameId == null }?.let {
        pluralStringResource(R.plurals.settings_play_time_mosaic_others_count, it.foldedCount, it.foldedCount)
    }
    PlayFigureCard(isFocused = isFocused, isEngaged = isEngaged, onFocus = onFocus) {
        PlayFigureHeader(
            title = when {
                tile == null -> stringResource(R.string.settings_play_time_mosaic_empty)
                tile.gameId == null -> othersLabel
                else -> tile.title.ifBlank { stringResource(R.string.settings_play_time_unknown_game) }
            },
            value = tile?.let { playTimeLabel(it.activeMs) },
            subtitle = when {
                state.mosaicFolded -> pluralStringResource(
                    R.plurals.settings_play_time_mosaic_others_count,
                    tiles.size,
                    tiles.size
                )
                tile == null -> null
                tile.gameId == null -> othersCountLabel
                else -> tile.lastPlayed?.let { last ->
                    stringResource(R.string.settings_play_time_entry_last_played, formatRelativeTime(context, last))
                }
            }
        )
        PlayCoverMosaic(
            tiles = tiles,
            othersLabel = othersLabel,
            othersCountLabel = othersCountLabel,
            selectedIndex = selected,
            isEngaged = isEngaged,
            onTileTap = onTileTap
        )
    }
}

