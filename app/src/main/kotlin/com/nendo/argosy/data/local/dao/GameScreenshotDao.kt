package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.local.entity.planScreenshotRows

data class PendingScreenshot(
    val gameId: Long,
    val position: Int,
    val sourceUrl: String,
    val cachedPath: String?,
    val cachedFromUrl: String?,
    val rommId: Long?,
    val title: String
)

@Dao
interface GameScreenshotDao {

    @Query("SELECT * FROM game_screenshots WHERE gameId = :gameId ORDER BY position")
    suspend fun getForGame(gameId: Long): List<GameScreenshotEntity>

    @Query("SELECT * FROM game_screenshots WHERE gameId IN (:gameIds) ORDER BY gameId, position")
    suspend fun getForGames(gameIds: List<Long>): List<GameScreenshotEntity>

    @Query("SELECT * FROM game_screenshots WHERE cachedPath IS NOT NULL")
    suspend fun getCached(): List<GameScreenshotEntity>

    @Query("DELETE FROM game_screenshots WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GameScreenshotEntity>)

    @Query(
        "UPDATE game_screenshots SET cachedPath = :path, cachedFromUrl = :fromUrl " +
            "WHERE gameId = :gameId AND position = :position AND sourceUrl = :fromUrl"
    )
    suspend fun setCached(gameId: Long, position: Int, path: String, fromUrl: String)

    @Query(
        "UPDATE game_screenshots SET cachedFromUrl = :fromUrl " +
            "WHERE gameId = :gameId AND position = :position AND cachedPath = :path " +
            "AND sourceUrl = :fromUrl AND cachedFromUrl IS NULL"
    )
    suspend fun backfillCachedFromUrl(gameId: Long, position: Int, path: String, fromUrl: String)

    @Query("UPDATE game_screenshots SET cachedPath = :newPath WHERE gameId = :gameId AND position = :position AND cachedPath = :oldPath")
    suspend fun relocateCachedPath(gameId: Long, position: Int, oldPath: String, newPath: String)

    @Query("UPDATE game_screenshots SET cachedPath = NULL, cachedFromUrl = NULL WHERE gameId = :gameId")
    suspend fun clearCachedForGame(gameId: Long)

    @Query("UPDATE game_screenshots SET cachedPath = NULL, cachedFromUrl = NULL WHERE cachedPath IN (:paths)")
    suspend fun clearCachedPaths(paths: List<String>)

    @Query(
        """
        UPDATE game_screenshots SET cachedPath = NULL, cachedFromUrl = NULL
        WHERE cachedPath IS NOT NULL
          AND gameId IN (SELECT id FROM games WHERE platformSlug = :platformSlug)
        """
    )
    suspend fun clearCachedForPlatform(platformSlug: String)

    @Query(
        """
        SELECT ss.gameId, ss.position, ss.sourceUrl, ss.cachedPath, ss.cachedFromUrl,
               games.rommId, games.title
        FROM game_screenshots ss
        INNER JOIN games ON games.id = ss.gameId
        WHERE ss.cachedPath IS NULL OR ss.cachedFromUrl IS NOT ss.sourceUrl
        """
    )
    suspend fun getPending(): List<PendingScreenshot>

    @Query("SELECT COUNT(*) FROM game_screenshots")
    suspend fun countWithSource(): Int

    @Query("SELECT COUNT(*) FROM game_screenshots WHERE cachedPath IS NOT NULL")
    suspend fun countCached(): Int

    @Transaction
    suspend fun replaceSources(gameId: Long, urls: List<String>?): List<GameScreenshotEntity> {
        val existing = getForGame(gameId)
        val rows = planScreenshotRows(gameId, existing, urls)
        if (rows == existing) return rows
        deleteForGame(gameId)
        if (rows.isNotEmpty()) insertAll(rows)
        return rows
    }

    @Transaction
    suspend fun backfillCachedFromUrls(rows: List<PendingScreenshot>) {
        rows.forEach { row ->
            val path = row.cachedPath ?: return@forEach
            backfillCachedFromUrl(row.gameId, row.position, path, row.sourceUrl)
        }
    }
}

private const val SCREENSHOT_SQL_CHUNK = 900

suspend fun GameScreenshotDao.forGames(gameIds: Collection<Long>): Map<Long, List<GameScreenshotEntity>> {
    if (gameIds.isEmpty()) return emptyMap()
    return gameIds.distinct().chunked(SCREENSHOT_SQL_CHUNK).flatMap { getForGames(it) }.groupBy { it.gameId }
}

suspend fun GameScreenshotDao.clearCachedPathsChunked(paths: Collection<String>) {
    paths.chunked(SCREENSHOT_SQL_CHUNK).forEach { clearCachedPaths(it) }
}
