package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import androidx.compose.ui.graphics.compositeOver
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.dualscreen.PlayShareRow
import com.nendo.argosy.ui.dualscreen.PlayTimeSlotGame
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.TimelineDot
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.ui.screens.settings.PlayTimeEntryUi
import com.nendo.argosy.ui.screens.settings.PlayTimeScrub
import com.nendo.argosy.ui.screens.settings.PlayTimeState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.util.formatPlayTime
import java.time.ZoneId

private const val MS_PER_MIN = 60_000L
private const val PRESENTED_ROWS = 12
private const val PERCENT = 100L
private const val TOP_GAMES_PER_ROW = 2
private const val PRESENTED_SHARE_ROWS = 4
private const val PRESENTED_GAME_ROWS = 6

/**
 * Hands the other display what the cursor is resting on: the run of games behind the focused
 * section, or the day the calendar is scrubbed to. A section with nothing to show publishes
 * nothing, which leaves the other screen on its fallback.
 */
@Composable
internal fun PlayTimePresentation(
    uiState: SettingsUiState,
    visibleItems: List<PlayTimeItem>,
    layoutState: PlayTimeLayoutState
) {
    val active = DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    if (!active) return

    val context = LocalContext.current
    val playTime = uiState.playTime
    val focused = playTimeLayout.itemAtFocusIndex(uiState.focusedIndex, layoutState)
        ?: visibleItems.firstOrNull { it.isFocusable }
    val slot = when {
        focused == null -> PresentationSlot.Fallback
        focused.section == "activity" -> playTime.timelineSlot(context)
        focused.section == "platforms" -> playTime.shareSlot(
            kind = ShareKind.PLATFORM,
            title = stringResource(R.string.settings_play_time_section_platforms),
            subtitle = pluralStringResource(
                R.plurals.settings_play_time_platforms_tile_count,
                playTime.platforms.size,
                playTime.platforms.size
            ),
            entries = playTime.platforms,
            context = context
        )
        focused.section == "where" -> playTime.shareSlot(
            kind = ShareKind.DEVICE,
            title = stringResource(R.string.settings_play_time_section_where),
            subtitle = pluralStringResource(
                R.plurals.settings_play_time_devices_tile_count,
                playTime.devices.size,
                playTime.devices.size
            ),
            entries = playTime.devices,
            context = context
        )
        focused.section == "what" -> playTime.shareSlot(
            kind = ShareKind.GAME,
            title = stringResource(R.string.settings_play_time_section_what),
            subtitle = pluralStringResource(
                R.plurals.settings_play_time_games_tile_count,
                playTime.games.size,
                playTime.games.size
            ),
            entries = playTime.games,
            context = context
        )
        else -> PresentationSlot.Fallback
    }

    if (slot is PresentationSlot.PlayTime && slot.games.isEmpty()) return
    if (slot == PresentationSlot.Fallback) return
    PresentOnCompanion(SlotOwner("settings.playTime"), slot)
}

private enum class ShareKind { PLATFORM, DEVICE, GAME }

private fun shareRowsFor(kind: ShareKind) =
    if (kind == ShareKind.GAME) PRESENTED_GAME_ROWS else PRESENTED_SHARE_ROWS

@Composable
private fun PlayTimeState.shareSlot(
    kind: ShareKind,
    title: String,
    subtitle: String,
    entries: List<PlayTimeEntryUi>,
    context: android.content.Context
): PresentationSlot {
    if (entries.isEmpty()) return PresentationSlot.Fallback
    val theme = LocalArgosyTheme.current
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val total = entries.sumOf { it.activeMs }
    val topMs = entries.first().activeMs.toFloat().coerceAtLeast(1f)
    val gamesByPlatform = remember(games) { games.groupBy { it.platformSlug } }
    val gamesByDevice = remember(sessions, coverPaths) { topGamesByDevice(context) }
    return PresentationSlot.PlayShare(
        title = title,
        totalLabel = formatPlayTime(context, (total / MS_PER_MIN).toInt()),
        subtitle = subtitle,
        rows = entries.take(shareRowsFor(kind)).mapIndexed { index, entry ->
            PlayShareRow(
                label = entry.nameRes?.let(context::getString) ?: entry.name,
                valueLabel = formatPlayTime(context, (entry.activeMs / MS_PER_MIN).toInt()),
                shareLabel = context.getString(
                    R.string.settings_play_time_share_percent,
                    ((entry.activeMs * PERCENT) / total.coerceAtLeast(1L)).toInt()
                ),
                fraction = entry.activeMs / topMs,
                color = series[index],
                iconModel = entry.key
                    .takeIf { kind == ShareKind.PLATFORM }
                    ?.let { PlatformIconAssets.resolveAssetUri(context, it) },
                showsCover = kind == ShareKind.GAME,
                coverPath = entry.coverPath,
                topGames = when (kind) {
                    ShareKind.PLATFORM -> gamesByPlatform[entry.key].orEmpty().toSlotGames(context)
                    ShareKind.DEVICE -> gamesByDevice[entry.name].orEmpty()
                    ShareKind.GAME -> emptyList()
                }
            )
        }
    )
}

private fun PlayTimeState.topGamesByDevice(
    context: android.content.Context
): Map<String, List<PlayTimeSlotGame>> =
    sessions
        .groupBy { it.deviceName }
        .mapValues { (_, deviceSessions) ->
            deviceSessions
                .groupBy { it.gameId }
                .map { (gameId, played) ->
                    PlayTimeSlotGame(
                        gameId = gameId,
                        title = played.first().gameTitle,
                        coverPath = coverPaths[gameId],
                        detail = formatPlayTime(
                            context,
                            (played.sumOf { it.activeMs } / MS_PER_MIN).toInt()
                        )
                    )
                }
                .sortedByDescending { it.detail }
                .take(TOP_GAMES_PER_ROW)
        }

private fun List<PlayTimeEntryUi>.toSlotGames(context: android.content.Context) =
    take(TOP_GAMES_PER_ROW).map { entry ->
        PlayTimeSlotGame(
            gameId = entry.key.toLongOrNull() ?: entry.name.hashCode().toLong(),
            title = entry.name,
            coverPath = entry.coverPath,
            detail = formatPlayTime(context, (entry.activeMs / MS_PER_MIN).toInt())
        )
    }

@Composable
private fun PlayTimeState.timelineSlot(context: android.content.Context): PresentationSlot {
    if (days.isEmpty()) return PresentationSlot.Fallback
    val zone = remember { ZoneId.systemDefault() }
    val theme = LocalArgosyTheme.current
    val series = remember(theme.isDark) { ChartPalette.series(theme.isDark) }
    val slots = ComponentDefaults.PlayTimeChart.seriesSlots
    val slotOfSlug = remember(platforms, slots) {
        platforms.take(slots).withIndex().associate { (index, entry) -> entry.key to index }
    }
    val sessionsByDate = remember(sessions) { sessionsByDate(zone) }
    val daySlots = remember(days, sessionsByDate, slotOfSlug) {
        dayPlatformSlots(sessionsByDate, slotOfSlug)
    }
    val selectedIndex = playTimeScrubIndex(PlayTimeScrub.CALENDAR, this)
        ?.coerceIn(days.indices)
        ?: days.lastIndex
    val selectedDay = days[selectedIndex]
    return PresentationSlot.PlayTimeline(
        dots = days.mapIndexed { index, day ->
            TimelineDot(
                hasActivity = day.activeMs > 0L,
                color = daySlots[index]?.let { series[it] }
                    ?: theme.textMute.compositeOver(theme.surfaceBase),
                label = day.date
                    .takeIf { it.dayOfMonth == 1 }
                    ?.month
                    ?.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
            )
        },
        selectedIndex = selectedIndex,
        dayLabel = formatMonthDay(selectedDay.date.atStartOfDay(zone).toInstant()),
        dayTotal = selectedDay.activeMs
            .takeIf { it > 0L }
            ?.let { formatPlayTime(context, (it / MS_PER_MIN).toInt()) },
        games = gamesPlayedOn(selectedDay.date, context)
    )
}

private fun PlayTimeState.gamesPlayedOn(
    date: java.time.LocalDate,
    context: android.content.Context
): List<PlayTimeSlotGame> {
    val zone = ZoneId.systemDefault()
    return sessions
        .filter { it.startTime.atZone(zone).toLocalDate() == date }
        .groupBy { it.gameId }
        .filterValues { played -> played.sumOf { it.activeMs } >= MIN_DISPLAY_MS }
        .map { (gameId, played) ->
            PlayTimeSlotGame(
                gameId = gameId,
                title = played.first().gameTitle,
                coverPath = coverPaths[gameId],
                detail = formatPlayTime(context, (played.sumOf { it.activeMs } / MS_PER_MIN).toInt()),
                subtitle = played.first().platformName
            )
        }
        .sortedByDescending { it.detail }
        .take(PRESENTED_ROWS)
}
