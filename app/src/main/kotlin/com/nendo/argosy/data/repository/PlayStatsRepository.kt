package com.nendo.argosy.data.repository

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.data.local.dao.PlayRecordDeletionDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.PlayTimeSummary
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.model.GamePlayActivity
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.data.model.PlayTimeSnapshot
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val GAME_TOTALS_LIMIT = 300

const val MIN_DISPLAY_MS = 60_000L

/**
 * Settings UI facade over [PlaySessionDao] for play-time statistics
 * (per-game, per-platform, and aggregate session queries).
 */
@Singleton
class PlayStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playSessionDao: PlaySessionDao,
    private val playRecordDeletionDao: PlayRecordDeletionDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend fun insert(session: PlaySessionEntity): Long =
        playSessionDao.insert(session)

    fun observeByGame(gameId: Long): Flow<List<PlaySessionEntity>> =
        playSessionDao.observeByGame(gameId)

    suspend fun getSessionsSince(since: Instant): List<PlaySessionEntity> =
        playSessionDao.getSessionsSince(since)

    /**
     * Active play time this device recorded for [gameId] in sessions that started at or after
     * [since].
     */
    suspend fun activePlayMsSince(gameId: Long, since: Instant): Long = withContext(Dispatchers.IO) {
        val device = localDeviceId()
        playSessionDao.getSessionsSince(since)
            .filter { it.gameId == gameId && it.deviceId == device }
            .sumOf { it.activePlayMs }
    }

    /**
     * The signed-in account's active play time per game id over sessions that started at or after
     * [since], private games excluded.
     */
    suspend fun activeMsPerGameSince(since: Instant): Map<Long, Long> = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val hidden = syncPreferencesRepository.privateIgdbIds()
        playSessionDao.getActiveMsPerGame(ownerUserId, since, MIN_DISPLAY_MS, GAME_TOTALS_LIMIT, hidden)
            .associate { it.gameId to it.activeMs }
    }

    /**
     * The signed-in account's active play time per game id over sessions that started in
     * [start, end), private games excluded, most played first.
     */
    suspend fun activeMsPerGameInRange(start: Instant, end: Instant, limit: Int): Map<Long, Long> =
        withContext(Dispatchers.IO) {
            val ownerUserId = syncPreferencesRepository.getRommUserId()
            val hidden = syncPreferencesRepository.privateIgdbIds()
            playSessionDao.getActiveMsPerGameInRange(ownerUserId, start, end, MIN_DISPLAY_MS, limit, hidden)
                .associate { it.gameId to it.activeMs }
        }

    /**
     * Active play time of each game's most recent session for the signed-in account, keyed by
     * game id. Sessions under [MIN_DISPLAY_MS] and games with no session are left out.
     */
    suspend fun lastSessionActiveMs(gameIds: Collection<Long>): Map<Long, Long> = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        gameIds.mapNotNull { gameId ->
            val activeMs = playSessionDao.getLatestForGameAndOwner(gameId, ownerUserId)?.activePlayMs
            activeMs?.takeIf { it >= MIN_DISPLAY_MS }?.let { gameId to it }
        }.toMap()
    }

    /**
     * The signed-in account's play of [gameId] over the [GamePlayActivityCalculator.WEEKS] weeks
     * ending at [now], with the end and device of its latest session that counts toward those weeks.
     */
    suspend fun gamePlayActivity(
        gameId: Long,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): GamePlayActivity = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val start = GamePlayActivityCalculator.windowStart(now, zone)
        val sessions = playSessionDao.getSessionsForGameAndOwnerInRange(gameId, ownerUserId, start, now)
        val weekMs = GamePlayActivityCalculator.weekActiveMs(sessions, now, zone, minWeekMs = MIN_DISPLAY_MS)
        val latest = GamePlayActivityCalculator.latestCountedSession(
            windowSessions = sessions,
            weekMs = weekMs,
            beforeWindow = playSessionDao.getLatestForGameAndOwnerBefore(gameId, ownerUserId, start),
            now = now,
            zone = zone
        )
        GamePlayActivity(
            weekActiveMs = weekMs,
            lastPlayedAt = latest?.endTime,
            lastPlayedDevice = latest?.let(GamePlayActivityCalculator::deviceName)
        )
    }

    suspend fun getSessionsInRange(start: Instant, end: Instant): List<PlaySessionEntity> =
        playSessionDao.getSessionsInRange(start, end)

    suspend fun getByIgdbId(igdbId: Long): List<PlaySessionEntity> =
        playSessionDao.getByIgdbId(igdbId)

    suspend fun getTopPlayedSince(since: Instant, limit: Int): List<PlayTimeSummary> =
        playSessionDao.getTopPlayedSince(since, limit)

    suspend fun getCount(): Int =
        playSessionDao.getCount()

    suspend fun getTotalActivePlayMsByPlatform(platformSlug: String): Long =
        playSessionDao.getTotalActivePlayMsByPlatform(platformSlug)

    /**
     * Deletes the signed-in account's play history for [gameId] on this device and blocks its
     * sessions from that instant back from being pulled or sent again.
     */
    suspend fun deletePlayRecords(gameId: Long) = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        playRecordDeletionDao.deleteRecords(gameId, ownerUserId, Instant.now())
    }

    /**
     * Every Play Time figure over the last [rangeDays] days. Streaks and the RomM sync
     * counts are the only all-time readings.
     */
    suspend fun loadSnapshot(rangeDays: Int): PlayTimeSnapshot = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val hidden = syncPreferencesRepository.privateIgdbIds()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays((rangeDays - 1).toLong())
        val since = firstDay.atStartOfDay(zone).toInstant()
        val until = today.plusDays(1).atStartOfDay(zone).toInstant()

        val dayTotals = playSessionDao.getActiveMsPerDay(ownerUserId, since, until, MIN_DISPLAY_MS, hidden)
            .associate { it.day to it.activeMs }
        val days = (0 until rangeDays).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            PlayDay(date = date, activeMs = dayTotals[date.toString()] ?: 0L)
        }
        val playDays = playSessionDao.getPlayDays(ownerUserId, MIN_DISPLAY_MS, hidden).mapNotNull { day ->
            runCatching { LocalDate.parse(day) }.getOrNull()
        }
        val rangeSessions = playSessionDao.getSessionsForOwnerSince(ownerUserId, since, hidden)
        val weekHourMs = PlayWeekHourMatrix.build(rangeSessions, zone)
        val shape = playSessionDao.getSessionShape(ownerUserId, since, hidden)
        val localDeviceId = localDeviceId()
        PlayTimeSnapshot(
            days = days,
            streaks = PlayStreakCalculator.compute(playDays, today),
            weekHourMs = weekHourMs,
            rangeSessions = rangeSessions,
            summary = PlayTimeSummaryCalculator.compute(rangeSessions, weekHourMs),
            platforms = playSessionDao.getActiveMsPerPlatform(ownerUserId, since, MIN_DISPLAY_MS, hidden),
            devices = playSessionDao.getActiveMsPerDevice(ownerUserId, since, MIN_DISPLAY_MS, hidden),
            games = playSessionDao.getActiveMsPerGame(ownerUserId, since, MIN_DISPLAY_MS, GAME_TOTALS_LIMIT, hidden),
            localDeviceId = localDeviceId,
            sessionCount = shape.sessionCount,
            syncCounts = playSessionDao.getRommSyncCounts(ownerUserId, localDeviceId),
            lastUploadAt = syncPreferencesRepository.getRommPlaySessionLastUpload(),
            lastPullAt = syncPreferencesRepository.getRommPlaySessionLastPull()
        )
    }

    private fun localDeviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
}
