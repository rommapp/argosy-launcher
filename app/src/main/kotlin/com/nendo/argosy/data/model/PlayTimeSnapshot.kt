package com.nendo.argosy.data.model

import com.nendo.argosy.data.local.dao.DevicePlayTotal
import com.nendo.argosy.data.local.dao.GamePlayTotal
import com.nendo.argosy.data.local.dao.PlatformPlayTotal
import com.nendo.argosy.data.local.dao.PlaySessionSyncCounts
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class PlayDay(
    val date: LocalDate,
    val activeMs: Long
)

data class PlayStreaks(
    val current: Int,
    val longest: Int
)

/**
 * The dominant readings of a range. [weekday] and [hour] are the busiest row and column of the
 * weekday-by-hour matrix; [topPlatformSlug] and [topDeviceId] carry the most active time.
 */
data class PlayTimeSummary(
    val totalActiveMs: Long,
    val platformCount: Int,
    val topPlatformSlug: String,
    val weekday: DayOfWeek,
    val hour: Int,
    val topDeviceId: String
)

/**
 * Everything the Play Time hub shows, read in one pass so every section describes the same
 * sessions. Only [streaks] and [syncCounts] reach past the window. [weekHourMs] runs Monday to
 * Sunday, each row 0 to 23; [rangeSessions] is oldest first.
 */
data class PlayTimeSnapshot(
    val days: List<PlayDay>,
    val streaks: PlayStreaks,
    val weekHourMs: List<List<Long>>,
    val rangeSessions: List<PlaySessionEntity>,
    val summary: PlayTimeSummary?,
    val platforms: List<PlatformPlayTotal>,
    val devices: List<DevicePlayTotal>,
    val games: List<GamePlayTotal>,
    val localDeviceId: String,
    val sessionCount: Int,
    val syncCounts: PlaySessionSyncCounts,
    val lastUploadAt: Instant?,
    val lastPullAt: Instant?
) {
    val isEmpty: Boolean get() = sessionCount == 0
}
