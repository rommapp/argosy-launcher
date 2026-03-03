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

    @Query("DELETE FROM play_sessions WHERE gameId = :gameId")
    suspend fun deleteByGame(gameId: Long)

    @Query("""
        SELECT * FROM play_sessions
        WHERE userId IS NOT NULL
        AND endTime > :since
        ORDER BY endTime ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedSessions(since: Instant, limit: Int = 100): List<PlaySessionEntity>

    @Query("""
        SELECT * FROM play_sessions
        WHERE userId IS NOT NULL
        ORDER BY endTime ASC
        LIMIT :limit
    """)
    suspend fun getAllUnsyncedSessions(limit: Int = 100): List<PlaySessionEntity>
}

data class PlayTimeSummary(
    val igdbId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val totalMinutes: Double
)
