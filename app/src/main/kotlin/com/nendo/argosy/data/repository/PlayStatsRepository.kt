package com.nendo.argosy.data.repository

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.PlayTimeSummary
import com.nendo.argosy.data.local.entity.PlaySessionEntity
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
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend fun insert(session: PlaySessionEntity): Long =
        playSessionDao.insert(session)

    fun observeByGame(gameId: Long): Flow<List<PlaySessionEntity>> =
        playSessionDao.observeByGame(gameId)

    suspend fun getSessionsSince(since: Instant): List<PlaySessionEntity> =
        playSessionDao.getSessionsSince(since)

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

    suspend fun deleteByGame(gameId: Long) =
        playSessionDao.deleteByGame(gameId)

    /**
     * Every Play Time figure over the last [rangeDays] days. Streaks and the RomM sync
     * counts are the only all-time readings.
     */
    suspend fun loadSnapshot(rangeDays: Int): PlayTimeSnapshot = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val firstDay = today.minusDays((rangeDays - 1).toLong())
        val since = firstDay.atStartOfDay(zone).toInstant()

        val dayTotals = playSessionDao.getActiveMsPerDay(ownerUserId, since, MIN_DISPLAY_MS)
            .associate { it.day to it.activeMs }
        val days = (0 until rangeDays).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            PlayDay(date = date, activeMs = dayTotals[date.toString()] ?: 0L)
        }
        val playDays = playSessionDao.getPlayDays(ownerUserId, MIN_DISPLAY_MS).mapNotNull { day ->
            runCatching { LocalDate.parse(day) }.getOrNull()
        }
        val rangeSessions = playSessionDao.getSessionsForOwnerSince(ownerUserId, since)
        val weekHourMs = PlayWeekHourMatrix.build(rangeSessions, zone)
        val shape = playSessionDao.getSessionShape(ownerUserId, since)
        val localDeviceId = localDeviceId()
        PlayTimeSnapshot(
            days = days,
            streaks = PlayStreakCalculator.compute(playDays, today),
            weekHourMs = weekHourMs,
            rangeSessions = rangeSessions,
            summary = PlayTimeSummaryCalculator.compute(rangeSessions, weekHourMs),
            platforms = playSessionDao.getActiveMsPerPlatform(ownerUserId, since, MIN_DISPLAY_MS),
            devices = playSessionDao.getActiveMsPerDevice(ownerUserId, since, MIN_DISPLAY_MS),
            games = playSessionDao.getActiveMsPerGame(ownerUserId, since, MIN_DISPLAY_MS, GAME_TOTALS_LIMIT),
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
