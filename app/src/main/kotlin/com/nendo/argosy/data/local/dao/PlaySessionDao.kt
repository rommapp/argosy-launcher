package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
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

    @Query("UPDATE play_sessions SET gameId = :toGameId WHERE gameId = :fromGameId")
    suspend fun moveToGame(fromGameId: Long, toGameId: Long): Int

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

    @Query("""
        SELECT ps.*, g.rommId AS rommId FROM play_sessions ps
        INNER JOIN games g ON g.id = ps.gameId
        WHERE ps.rommSessionId IS NULL
          AND ps.ownerUserId IS :ownerUserId
          AND g.rommId > 0
          AND (ps.endTime / 1000) > (ps.startTime / 1000)
          AND ps.id > :afterId
        ORDER BY ps.id ASC
        LIMIT :limit
    """)
    suspend fun getPendingForRomM(
        ownerUserId: Long?,
        afterId: Long,
        limit: Int
    ): List<PendingRomMPlaySession>

    @Query("UPDATE play_sessions SET rommSessionId = :rommSessionId WHERE id = :id")
    suspend fun setRommSessionId(id: Long, rommSessionId: Long)

    @Query("UPDATE play_sessions SET richPresence = :richPresence, richPresenceAt = :richPresenceAt WHERE id = :id")
    suspend fun setRichPresence(id: Long, richPresence: String, richPresenceAt: Instant)

    @Query("UPDATE play_sessions SET raSnapshotUnlocks = :encoded WHERE id = :id")
    suspend fun setRaSnapshotUnlocks(id: Long, encoded: String)

    @Query("""
        SELECT * FROM play_sessions
        WHERE gameId = :gameId AND ownerUserId IS :ownerUserId
        ORDER BY endTime DESC
        LIMIT 1
    """)
    suspend fun getLatestForGameAndOwner(gameId: Long, ownerUserId: Long?): PlaySessionEntity?

    @Query("""
        SELECT * FROM play_sessions
        WHERE gameId = :gameId AND ownerUserId IS :ownerUserId AND startTime < :before
        ORDER BY endTime DESC
        LIMIT 1
    """)
    suspend fun getLatestForGameAndOwnerBefore(gameId: Long, ownerUserId: Long?, before: Instant): PlaySessionEntity?

    @Query("""
        SELECT * FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :start AND startTime < :end
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsForOwnerInRange(
        ownerUserId: Long?,
        start: Instant,
        end: Instant,
        hiddenIgdbIds: List<Long>
    ): List<PlaySessionEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM play_sessions
            WHERE ownerUserId IS :ownerUserId AND startTime >= :start AND startTime < :end
              AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        )
    """)
    suspend fun hasSessionsForOwnerInRange(
        ownerUserId: Long?,
        start: Instant,
        end: Instant,
        hiddenIgdbIds: List<Long>
    ): Boolean

    @Query("""
        SELECT gameId, MIN(startTime) AS firstPlayed FROM play_sessions
        WHERE ownerUserId IS :ownerUserId
        GROUP BY gameId
    """)
    suspend fun getFirstPlayedPerGame(ownerUserId: Long?): List<GameFirstPlayed>

    @Query("""
        UPDATE play_sessions SET gameId = (
            SELECT g.id FROM games g
            WHERE g.igdbId = play_sessions.igdbId AND g.platformSlug = play_sessions.platformSlug
        )
        WHERE ownerUserId IS :ownerUserId
          AND igdbId IS NOT NULL
          AND NOT EXISTS (SELECT 1 FROM games WHERE games.id = play_sessions.gameId)
          AND (
            SELECT COUNT(*) FROM games g
            WHERE g.igdbId = play_sessions.igdbId AND g.platformSlug = play_sessions.platformSlug
          ) = 1
    """)
    suspend fun relinkOrphans(ownerUserId: Long?): Int

    @Query("""
        SELECT gameId, SUM((activePlayMs + 30000) / 60000) AS minutes FROM play_sessions
        WHERE ownerUserId = :ownerUserId AND foldedIntoTotal = 0 AND deviceId != :localDeviceId
          AND rommSessionId IS NOT NULL
          AND EXISTS (SELECT 1 FROM games WHERE games.id = play_sessions.gameId)
        GROUP BY gameId
    """)
    suspend fun getUnfoldedRemoteMinutes(ownerUserId: Long, localDeviceId: String): List<GameMinutes>

    @Query("""
        UPDATE play_sessions SET foldedIntoTotal = 1
        WHERE ownerUserId = :ownerUserId AND foldedIntoTotal = 0 AND deviceId != :localDeviceId
          AND rommSessionId IS NOT NULL
          AND EXISTS (SELECT 1 FROM games WHERE games.id = play_sessions.gameId)
    """)
    suspend fun markRemoteFolded(ownerUserId: Long, localDeviceId: String): Int

    @Query("DELETE FROM play_sessions WHERE ownerUserId = :ownerUserId")
    suspend fun deleteByOwner(ownerUserId: Long)

    @Query("SELECT COUNT(*) FROM play_sessions WHERE ownerUserId IS NULL")
    suspend fun countUnowned(): Int

    @Query("UPDATE play_sessions SET ownerUserId = :ownerUserId WHERE ownerUserId IS NULL")
    suspend fun adoptUnowned(ownerUserId: Long)

    @Query("""
        SELECT MAX(startTime) FROM play_sessions
        WHERE deviceId = :deviceId AND ownerUserId IS :ownerUserId AND rommSessionId IS NOT NULL
    """)
    suspend fun getLatestRommStartForDevice(deviceId: String, ownerUserId: Long?): Instant?

    @Query("SELECT rommSessionId FROM play_sessions WHERE rommSessionId IN (:rommSessionIds)")
    suspend fun getHeldRommSessionIds(rommSessionIds: List<Long>): List<Long>

    @Query("""
        SELECT day, SUM(gameMs) AS activeMs FROM (
            SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day,
                   gameId, SUM(activePlayMs) AS gameMs
            FROM play_sessions
            WHERE ownerUserId IS :ownerUserId AND startTime >= :since AND startTime < :until
              AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
            GROUP BY day, gameId
            HAVING gameMs >= :minActiveMs
        )
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getActiveMsPerDay(
        ownerUserId: Long?,
        since: Instant,
        until: Instant,
        minActiveMs: Long,
        hiddenIgdbIds: List<Long>
    ): List<PlayDayTotal>

    @Query("""
        SELECT day FROM (
            SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day,
                   gameId, SUM(activePlayMs) AS gameMs
            FROM play_sessions
            WHERE ownerUserId IS :ownerUserId
              AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
            GROUP BY day, gameId
            HAVING gameMs >= :minActiveMs
        )
        GROUP BY day
        ORDER BY day ASC
    """)
    suspend fun getPlayDays(ownerUserId: Long?, minActiveMs: Long, hiddenIgdbIds: List<Long>): List<String>

    @Query("""
        SELECT * FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :since
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsForOwnerSince(
        ownerUserId: Long?,
        since: Instant,
        hiddenIgdbIds: List<Long>
    ): List<PlaySessionEntity>

    @Query("""
        SELECT * FROM play_sessions
        WHERE gameId = :gameId AND ownerUserId IS :ownerUserId
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsForGameAndOwner(gameId: Long, ownerUserId: Long?): List<PlaySessionEntity>

    @Query("""
        SELECT * FROM play_sessions
        WHERE gameId = :gameId AND ownerUserId IS :ownerUserId AND startTime >= :start AND startTime < :end
        ORDER BY startTime ASC
    """)
    suspend fun getSessionsForGameAndOwnerInRange(
        gameId: Long,
        ownerUserId: Long?,
        start: Instant,
        end: Instant
    ): List<PlaySessionEntity>

    @Query("""
        SELECT platformSlug, SUM(activePlayMs) AS activeMs, COUNT(*) AS sessionCount,
               MAX(startTime) AS lastPlayed
        FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :since
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        GROUP BY platformSlug
        HAVING activeMs >= :minActiveMs
        ORDER BY activeMs DESC
    """)
    suspend fun getActiveMsPerPlatform(
        ownerUserId: Long?,
        since: Instant,
        minActiveMs: Long,
        hiddenIgdbIds: List<Long>
    ): List<PlatformPlayTotal>

    @Query("""
        SELECT deviceId, MAX(deviceManufacturer) AS deviceManufacturer, MAX(deviceModel) AS deviceModel,
               SUM(activePlayMs) AS activeMs, COUNT(*) AS sessionCount, MAX(startTime) AS lastPlayed
        FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :since
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        GROUP BY deviceId
        HAVING activeMs >= :minActiveMs
        ORDER BY activeMs DESC
    """)
    suspend fun getActiveMsPerDevice(
        ownerUserId: Long?,
        since: Instant,
        minActiveMs: Long,
        hiddenIgdbIds: List<Long>
    ): List<DevicePlayTotal>

    @Query("""
        SELECT gameId, MAX(gameTitle) AS gameTitle, MAX(platformSlug) AS platformSlug, MAX(igdbId) AS igdbId,
               SUM(activePlayMs) AS activeMs, COUNT(*) AS sessionCount, MAX(startTime) AS lastPlayed
        FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :since
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        GROUP BY gameId
        HAVING activeMs >= :minActiveMs
        ORDER BY activeMs DESC
        LIMIT :limit
    """)
    suspend fun getActiveMsPerGame(
        ownerUserId: Long?,
        since: Instant,
        minActiveMs: Long,
        limit: Int,
        hiddenIgdbIds: List<Long>
    ): List<GamePlayTotal>

    @Query("""
        SELECT gameId, MAX(gameTitle) AS gameTitle, MAX(platformSlug) AS platformSlug, MAX(igdbId) AS igdbId,
               SUM(activePlayMs) AS activeMs, COUNT(*) AS sessionCount, MAX(startTime) AS lastPlayed
        FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :start AND startTime < :end
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
        GROUP BY gameId
        HAVING activeMs >= :minActiveMs
        ORDER BY activeMs DESC
        LIMIT :limit
    """)
    suspend fun getActiveMsPerGameInRange(
        ownerUserId: Long?,
        start: Instant,
        end: Instant,
        minActiveMs: Long,
        limit: Int,
        hiddenIgdbIds: List<Long>
    ): List<GamePlayTotal>

    @Query("""
        SELECT COUNT(*) AS sessionCount
        FROM play_sessions
        WHERE ownerUserId IS :ownerUserId AND startTime >= :since
          AND (igdbId IS NULL OR igdbId NOT IN (:hiddenIgdbIds))
    """)
    suspend fun getSessionShape(ownerUserId: Long?, since: Instant, hiddenIgdbIds: List<Long>): PlaySessionShape

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN ps.rommSessionId IS NULL
                                  AND (ps.endTime / 1000) > (ps.startTime / 1000)
                                  AND EXISTS (SELECT 1 FROM games g WHERE g.id = ps.gameId AND g.rommId > 0)
                                 THEN 1 ELSE 0 END), 0) AS pending,
               COALESCE(SUM(CASE WHEN ps.rommSessionId IS NULL
                                  AND NOT EXISTS (SELECT 1 FROM games g WHERE g.id = ps.gameId AND g.rommId > 0)
                                 THEN 1 ELSE 0 END), 0) AS unlinked,
               COALESCE(SUM(CASE WHEN ps.rommSessionId IS NOT NULL THEN 1 ELSE 0 END), 0) AS onRomm,
               COALESCE(SUM(CASE WHEN ps.deviceId != :localDeviceId THEN 1 ELSE 0 END), 0) AS fromOtherDevices
        FROM play_sessions ps
        WHERE ps.ownerUserId IS :ownerUserId
    """)
    suspend fun getRommSyncCounts(ownerUserId: Long?, localDeviceId: String): PlaySessionSyncCounts
}

data class PlayDayTotal(
    val day: String,
    val activeMs: Long
)

data class PlatformPlayTotal(
    val platformSlug: String,
    val activeMs: Long,
    val sessionCount: Int,
    val lastPlayed: Instant
)

data class DevicePlayTotal(
    val deviceId: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val activeMs: Long,
    val sessionCount: Int,
    val lastPlayed: Instant
)

data class GamePlayTotal(
    val gameId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val igdbId: Long?,
    val activeMs: Long,
    val sessionCount: Int,
    val lastPlayed: Instant
)

data class GameMinutes(
    val gameId: Long,
    val minutes: Int
)

data class GameFirstPlayed(
    val gameId: Long,
    val firstPlayed: Instant
)

data class PlaySessionShape(
    val sessionCount: Int
)

/**
 * [pending] rows are exactly what [PlaySessionDao.getPendingForRomM] would upload; [unlinked]
 * rows are held back because no current games row with a RomM id backs them.
 */
data class PlaySessionSyncCounts(
    val pending: Int,
    val unlinked: Int,
    val onRomm: Int,
    val fromOtherDevices: Int
)

data class PlayTimeSummary(
    val igdbId: Long,
    val gameTitle: String,
    val platformSlug: String,
    val totalMinutes: Double
)

data class PendingRomMPlaySession(
    @Embedded val session: PlaySessionEntity,
    val rommId: Long
)
