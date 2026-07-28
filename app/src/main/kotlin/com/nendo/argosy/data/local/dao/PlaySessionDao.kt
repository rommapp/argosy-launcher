package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PlaySessionDao {
    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM play_sessions WHERE gameId = :gameId AND startTime = :startTime)")
    suspend fun existsByGameAndStart(gameId: Long, startTime: Instant): Boolean

    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId ORDER BY startTime DESC")
    fun observeByGame(gameId: Long): Flow<List<PlaySessionEntity>>

    @Query("SELECT * FROM play_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    suspend fun getSessionsSince(since: Instant): List<PlaySessionEntity>

    @Query("SELECT * FROM play_sessions WHERE startTime >= :start AND startTime < :end ORDER BY startTime DESC")
    suspend fun getSessionsInRange(start: Instant, end: Instant): List<PlaySessionEntity>

    @Query("SELECT * FROM play_sessions WHERE igdbId = :igdbId ORDER BY startTime DESC")
    suspend fun getByIgdbId(igdbId: Long): List<PlaySessionEntity>

    @Query("""
        SELECT igdbId, gameTitle, platformSlug, SUM((julianday(endTime) - julianday(startTime)) * 24 * 60) as totalMinutes
        FROM play_sessions
        WHERE startTime >= :since AND igdbId IS NOT NULL
        GROUP BY igdbId
        ORDER BY totalMinutes DESC
        LIMIT :limit
    """)
    suspend fun getTopPlayedSince(since: Instant, limit: Int): List<PlayTimeSummary>

    @Query("SELECT COUNT(*) FROM play_sessions")
    suspend fun getCount(): Int

    @Query("SELECT COALESCE(SUM(activePlayMs), 0) FROM play_sessions WHERE platformSlug = :platformSlug")
    suspend fun getTotalActivePlayMsByPlatform(platformSlug: String): Long

    @Query("DELETE FROM play_sessions WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("DELETE FROM play_sessions WHERE gameId IN (SELECT id FROM games WHERE source IN (:sourceNames))")
    suspend fun deleteByGameSources(sourceNames: List<String>)

    /**
     * Sessions the social upload has not seen. Gated on [PlaySessionEntity.userId] because the
     * social server keys on that id and a session recorded while unlinked has nothing to send.
     */
    @Query("""
        SELECT * FROM play_sessions
        WHERE userId IS NOT NULL
          AND (:since IS NULL OR endTime > :since)
          AND ownerUserId IS :ownerUserId
        ORDER BY endTime ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedForSocial(
        since: Instant?,
        ownerUserId: Long?,
        limit: Int = 100
    ): List<PlaySessionEntity>

    /**
     * Sessions the RomM ingest has not seen. Deliberately not gated on the social id: RomM
     * attribution is [PlaySessionEntity.ownerUserId], and a device with no social link still has
     * play time to report.
     */
    @Query("""
        SELECT * FROM play_sessions
        WHERE (:since IS NULL OR endTime > :since)
          AND ownerUserId IS :ownerUserId
        ORDER BY endTime ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedForRomM(
        since: Instant?,
        ownerUserId: Long?,
        limit: Int = 100
    ): List<PlaySessionEntity>

    @Query("DELETE FROM play_sessions WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)
}

data class PlayTimeSummary(
    val igdbId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val totalMinutes: Double
)
