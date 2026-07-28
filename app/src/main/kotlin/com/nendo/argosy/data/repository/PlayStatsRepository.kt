package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.PlayTimeSummary
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings UI facade over [PlaySessionDao] for play-time statistics
 * (per-game, per-platform, and aggregate session queries).
 */
@Singleton
class PlayStatsRepository @Inject constructor(
    private val playSessionDao: PlaySessionDao
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
}
