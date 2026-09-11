package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val DAYS_IN_WEEK = 7
private const val LABELLED_ROW_STEP = 2

data class CalendarGrid(
    val weeks: Int,
    val rows: List<List<Int?>>,
    val monthStarts: Map<Int, YearMonth>
)

fun buildCalendarGrid(days: List<PlayDay>): CalendarGrid {
    if (days.isEmpty()) return CalendarGrid(0, List(DAYS_IN_WEEK) { emptyList() }, emptyMap())
    val first = days.first().date
    val gridStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val span = ChronoUnit.DAYS.between(gridStart, days.last().date).toInt()
    val weeks = span / DAYS_IN_WEEK + 1
    val indexByOffset = days.indices.associateBy { index ->
        ChronoUnit.DAYS.between(gridStart, days[index].date).toInt()
    }
    val rows = (0 until DAYS_IN_WEEK).map { row ->
        (0 until weeks).map { week -> indexByOffset[week * DAYS_IN_WEEK + row] }
    }
    val monthStarts = buildMap {
        var seen: YearMonth? = null
        (0 until weeks).forEach { week ->
            val date = gridStart.plusDays((week * DAYS_IN_WEEK).toLong())
            val month = YearMonth.from(date.plusDays((DAYS_IN_WEEK - 1).toLong()))
            if (month != seen) {
                seen = month
                put(week, month)
            }
        }
    }
    return CalendarGrid(weeks = weeks, rows = rows, monthStarts = monthStarts)
}

@Composable
fun PlayCalendar(
    days: List<PlayDay>,
    daySlots: List<Int?>,
    seriesColors: List<Color>,
    othersColor: Color,
    selectedIndex: Int?,
    showSelection: Boolean,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ComponentDefaults.PlayTimeChart.calendarEmptyAlpha)
    val levels = ComponentDefaults.PlayTimeChart.calendarLevels
    val floor = ComponentDefaults.PlayTimeChart.calendarRampFloor
    val gap = (ComponentDefaults.PlayTimeChart.surfaceGap * s).dp
    val labelWidth = (ComponentDefaults.PlayTimeChart.calendarLabelWidth * s).dp
    val cellSize = (ComponentDefaults.PlayTimeChart.calendarCell * s).dp
    val shape = RoundedCornerShape(gap * 2)
    val grid = remember(days) { buildCalendarGrid(days) }
    val cellLevels = remember(days) {
        val max = days.maxOfOrNull { it.activeMs } ?: 0L
        days.map { ChartPalette.levelOf(it.activeMs, max, levels) }
    }
    fun cellColor(index: Int): Color {
        val level = cellLevels.getOrNull(index) ?: 0
        if (level <= 0) return track
        val hue = ChartPalette.slotColor(seriesColors, daySlots.getOrNull(index), othersColor)
        return ChartPalette.sequential(track, hue, level, levels, floor)
    }
    val locale = Locale.getDefault()
    val weekdayLabels = remember(locale) {
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    val labelStyle = MaterialTheme.typography.labelSmall
    Row(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(modifier = Modifier.height(labelRowHeight(labelStyle) + gap))
            grid.rows.indices.forEach { row ->
                Box(modifier = Modifier.width(labelWidth).height(cellSize), contentAlignment = Alignment.CenterStart) {
                    if (row % LABELLED_ROW_STEP == 0) {
                        Text(
                            text = weekdayLabels[row],
                            style = labelStyle,
                            color = theme.textMute,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f).clipToBounds()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    (0 until grid.weeks).forEach { week ->
                        Box(modifier = Modifier.width(cellSize).height(labelRowHeight(labelStyle))) {
                            grid.monthStarts[week]?.let { month ->
                                Text(
                                    text = month.month.getDisplayName(TextStyle.SHORT, locale),
                                    style = labelStyle,
                                    color = theme.textDim,
                                    maxLines = 1,
                                    modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
                                )
                            }
                        }
                    }
                }
                grid.rows.forEach { cells ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        cells.forEach { index ->
                            val selected = showSelection && index != null && index == selectedIndex
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clip(shape)
                                    .background(if (index == null) track.copy(alpha = 0f) else cellColor(index))
                                    .then(
                                        if (selected) Modifier.border(Dimens.borderMedium, theme.textPrimary, shape) else Modifier
                                    )
                                    .then(
                                        if (index != null) Modifier.clickableNoFocus { onCellTap(index) } else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun labelRowHeight(style: androidx.compose.ui.text.TextStyle): Dp =
    with(LocalDensity.current) { style.lineHeight.toDp() }
