package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.GameArtEntity
import com.nendo.argosy.data.local.entity.toResolvedArt
import com.nendo.argosy.data.local.entity.toResolvedArtByGame
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.data.model.ResolvedGameArt
import kotlinx.coroutines.flow.Flow

internal const val RESOLVED_ART_SQL = "COALESCE(art.overridePath, art.cachedPath, art.sourceUrl)"

internal const val COVER_ART_JOIN =
    "LEFT JOIN game_art art ON art.gameId = games.id AND art.slot = 'COVER'"

private const val COVER_DERIVED_RESET_ON_CACHE_CHANGE =
    "gradientColors = CASE WHEN overridePath IS NULL AND cachedPath IS NOT :path THEN NULL ELSE gradientColors END, " +
        "coverAspectRatio = CASE WHEN overridePath IS NULL AND cachedPath IS NOT :path THEN NULL ELSE coverAspectRatio END"

data class PendingArt(
    val gameId: Long,
    val slot: String,
    val sourceUrl: String,
    val cachedPath: String?,
    val cachedFromUrl: String?,
    val rommId: Long?,
    val steamAppId: Long?,
    val title: String
)

data class GradientCandidate(
    val gameId: Long,
    val coverPath: String
)

data class AppIconTarget(
    val gameId: Long,
    val packageName: String
)

data class GameArtChanges(
    val rows: List<GameArtEntity>,
    val totalRows: Int
)

data class GameArtLocalPaths(
    val gameId: Long,
    val slot: String,
    val cachedPath: String?,
    val overridePath: String?
)

@Dao
interface GameArtDao {

    @Query("SELECT COUNT(*) FROM game_art")
    fun observeRowCount(): Flow<Int>

    @Query("SELECT * FROM game_art")
    suspend fun getAll(): List<GameArtEntity>

    @Query("SELECT COUNT(*) FROM game_art")
    suspend fun countRows(): Int

    @Query("SELECT DISTINCT gameId FROM game_art")
    suspend fun getGameIds(): List<Long>

    @Query(
        "SELECT * FROM game_art WHERE gameId IN " +
            "(SELECT DISTINCT gameId FROM game_art WHERE changeSeq > :seq)"
    )
    suspend fun changedSince(seq: Long): List<GameArtEntity>

    @Transaction
    suspend fun changesSince(seq: Long): GameArtChanges = GameArtChanges(changedSince(seq), countRows())

    @Query("SELECT * FROM game_art WHERE gameId = :gameId")
    suspend fun getForGame(gameId: Long): List<GameArtEntity>

    @Query("SELECT * FROM game_art WHERE gameId IN (:gameIds)")
    suspend fun getForGames(gameIds: List<Long>): List<GameArtEntity>

    @Query("SELECT * FROM game_art WHERE gameId = :gameId AND slot = :slot")
    suspend fun get(gameId: Long, slot: String): GameArtEntity?

    @Query("INSERT OR IGNORE INTO game_art (gameId, slot) VALUES (:gameId, :slot)")
    suspend fun insertSlotIfAbsent(gameId: Long, slot: String)

    @Query(
        "UPDATE game_art SET sourceUrl = :url " +
            "WHERE gameId = :gameId AND slot = :slot AND sourceUrl IS NOT :url"
    )
    suspend fun updateSourceUrl(gameId: Long, slot: String, url: String?)

    @Query(
        "UPDATE game_art SET cachedPath = :path, cachedFromUrl = :fromUrl, $COVER_DERIVED_RESET_ON_CACHE_CHANGE " +
            "WHERE gameId = :gameId AND slot = :slot " +
            "AND (cachedPath IS NOT :path OR cachedFromUrl IS NOT :fromUrl)"
    )
    suspend fun updateCached(gameId: Long, slot: String, path: String?, fromUrl: String?)

    @Query(
        "UPDATE game_art SET cachedFromUrl = :fromUrl " +
            "WHERE gameId = :gameId AND slot = :slot AND cachedPath = :path AND cachedFromUrl IS NOT :fromUrl"
    )
    suspend fun backfillCachedFromUrl(gameId: Long, slot: String, path: String, fromUrl: String)

    @Query("UPDATE game_art SET cachedPath = :newPath WHERE gameId = :gameId AND slot = :slot AND cachedPath = :oldPath")
    suspend fun relocateCachedPath(gameId: Long, slot: String, oldPath: String, newPath: String)

    @Query(
        "UPDATE game_art SET overridePath = :path, gradientColors = NULL, coverAspectRatio = NULL " +
            "WHERE gameId = :gameId AND slot = :slot AND overridePath IS NOT :path"
    )
    suspend fun updateOverride(gameId: Long, slot: String, path: String?)

    @Query("UPDATE game_art SET overridePath = :newPath WHERE gameId = :gameId AND slot = :slot AND overridePath = :oldPath")
    suspend fun relocateOverridePath(gameId: Long, slot: String, oldPath: String, newPath: String)

    @Query("UPDATE game_art SET gradientColors = :json WHERE gameId = :gameId AND slot = 'COVER'")
    suspend fun updateGradientColors(gameId: Long, json: String)

    @Query("UPDATE game_art SET coverAspectRatio = :ratio WHERE gameId = :gameId AND slot = 'COVER'")
    suspend fun updateCoverAspectRatio(gameId: Long, ratio: Float)

    @Query("SELECT coverAspectRatio FROM game_art WHERE gameId = :gameId AND slot = 'COVER'")
    suspend fun getCoverAspectRatio(gameId: Long): Float?

    @Query("SELECT gameId, slot, cachedPath, overridePath FROM game_art WHERE cachedPath IS NOT NULL OR overridePath IS NOT NULL")
    suspend fun getLocalPaths(): List<GameArtLocalPaths>

    @Query("SELECT cachedPath FROM game_art WHERE cachedPath IS NOT NULL")
    suspend fun getAllCachedPaths(): List<String>

    @Query("SELECT overridePath FROM game_art WHERE overridePath IS NOT NULL")
    suspend fun getAllOverridePaths(): List<String>

    @Query(
        "UPDATE game_art SET cachedPath = NULL, cachedFromUrl = NULL, " +
            "gradientColors = CASE WHEN overridePath IS NULL THEN NULL ELSE gradientColors END, " +
            "coverAspectRatio = CASE WHEN overridePath IS NULL THEN NULL ELSE coverAspectRatio END " +
            "WHERE cachedPath IN (:paths)"
    )
    suspend fun clearCachedPaths(paths: List<String>)

    @Query("UPDATE game_art SET overridePath = NULL, gradientColors = NULL, coverAspectRatio = NULL WHERE overridePath IN (:paths)")
    suspend fun clearOverridePaths(paths: List<String>)

    @Query(
        """
        SELECT art.overridePath FROM game_art art
        INNER JOIN games ON games.id = art.gameId
        WHERE games.platformSlug = :platformSlug AND art.overridePath IS NOT NULL
        """
    )
    suspend fun getOverridePathsForPlatform(platformSlug: String): List<String>

    @Query(
        """
        UPDATE game_art SET cachedPath = NULL, cachedFromUrl = NULL,
            gradientColors = CASE WHEN overridePath IS NULL THEN NULL ELSE gradientColors END,
            coverAspectRatio = CASE WHEN overridePath IS NULL THEN NULL ELSE coverAspectRatio END
        WHERE cachedPath IS NOT NULL
          AND gameId IN (SELECT id FROM games WHERE platformSlug = :platformSlug)
        """
    )
    suspend fun clearCachedForPlatform(platformSlug: String)

    @Query(
        """
        SELECT art.gameId, art.slot, art.sourceUrl, art.cachedPath, art.cachedFromUrl,
               games.rommId, games.steamAppId, games.title
        FROM game_art art
        INNER JOIN games ON games.id = art.gameId
        WHERE art.sourceUrl IS NOT NULL
          AND (art.cachedPath IS NULL OR art.cachedFromUrl IS NOT art.sourceUrl)
        """
    )
    suspend fun getPending(): List<PendingArt>

    @Query(
        """
        SELECT art.gameId AS gameId, $RESOLVED_ART_SQL AS coverPath FROM game_art art
        INNER JOIN games ON games.id = art.gameId
        WHERE art.slot = 'COVER' AND $RESOLVED_ART_SQL LIKE '/%' AND art.gradientColors IS NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY games.lastPlayed DESC
        """
    )
    suspend fun getGradientCandidates(ownerUserId: Long?): List<GradientCandidate>

    @Query("SELECT COUNT(*) FROM game_art WHERE slot = :slot AND sourceUrl IS NOT NULL")
    suspend fun countWithSource(slot: String): Int

    @Query("SELECT COUNT(*) FROM game_art WHERE slot = :slot AND sourceUrl IS NOT NULL AND cachedPath IS NOT NULL")
    suspend fun countCached(slot: String): Int

    @Query(
        """
        SELECT games.id AS gameId, games.packageName AS packageName FROM games
        LEFT JOIN game_art art ON art.gameId = games.id AND art.slot = 'COVER'
        WHERE games.source = 'ANDROID_APP' AND games.packageName IS NOT NULL
          AND art.sourceUrl IS NULL AND art.cachedPath IS NULL AND art.overridePath IS NULL
        """
    )
    suspend fun getAndroidGamesWithoutCover(): List<AppIconTarget>

    @Query(
        "INSERT OR IGNORE INTO game_art (gameId, slot) " +
            "SELECT :targetGameId, slot FROM game_art WHERE gameId = :sourceGameId"
    )
    suspend fun insertSlotsOf(targetGameId: Long, sourceGameId: Long)

    @Query(
        """
        UPDATE game_art SET
            sourceUrl = COALESCE(sourceUrl,
                (SELECT s.sourceUrl FROM game_art s WHERE s.gameId = :sourceGameId AND s.slot = game_art.slot)),
            cachedFromUrl = CASE WHEN cachedPath IS NULL
                THEN (SELECT s.cachedFromUrl FROM game_art s WHERE s.gameId = :sourceGameId AND s.slot = game_art.slot)
                ELSE cachedFromUrl END,
            cachedPath = COALESCE(cachedPath,
                (SELECT s.cachedPath FROM game_art s WHERE s.gameId = :sourceGameId AND s.slot = game_art.slot)),
            overridePath = COALESCE(overridePath,
                (SELECT s.overridePath FROM game_art s WHERE s.gameId = :sourceGameId AND s.slot = game_art.slot))
        WHERE gameId = :targetGameId
          AND slot IN (SELECT slot FROM game_art WHERE gameId = :sourceGameId)
        """
    )
    suspend fun updateMissingFrom(targetGameId: Long, sourceGameId: Long)

    @Transaction
    suspend fun fillMissingFrom(targetGameId: Long, sourceGameId: Long) {
        insertSlotsOf(targetGameId, sourceGameId)
        updateMissingFrom(targetGameId, sourceGameId)
    }

    @Transaction
    suspend fun backfillCachedFromUrls(rows: List<PendingArt>) {
        rows.forEach { row ->
            val path = row.cachedPath ?: return@forEach
            backfillCachedFromUrl(row.gameId, row.slot, path, row.sourceUrl)
        }
    }

    @Transaction
    suspend fun setSourceUrl(gameId: Long, slot: ArtSlot, url: String?) {
        if (url == null && get(gameId, slot.name) == null) return
        insertSlotIfAbsent(gameId, slot.name)
        updateSourceUrl(gameId, slot.name, url)
    }

    @Transaction
    suspend fun setCached(gameId: Long, slot: ArtSlot, path: String, fromUrl: String?) {
        insertSlotIfAbsent(gameId, slot.name)
        updateCached(gameId, slot.name, path, fromUrl)
    }

    @Transaction
    suspend fun setOverride(gameId: Long, slot: ArtSlot, path: String) {
        insertSlotIfAbsent(gameId, slot.name)
        updateOverride(gameId, slot.name, path)
    }

    @Transaction
    suspend fun setGradientColors(gameId: Long, json: String) {
        insertSlotIfAbsent(gameId, ArtSlot.COVER.name)
        updateGradientColors(gameId, json)
    }

    @Transaction
    suspend fun setCoverAspectRatio(gameId: Long, ratio: Float) {
        insertSlotIfAbsent(gameId, ArtSlot.COVER.name)
        updateCoverAspectRatio(gameId, ratio)
    }
}

private const val ART_ID_CHUNK = 900

suspend fun GameArtDao.resolved(gameId: Long): ResolvedGameArt = getForGame(gameId).toResolvedArt()

suspend fun GameArtDao.resolvedFor(gameIds: Collection<Long>): Map<Long, ResolvedGameArt> {
    if (gameIds.isEmpty()) return emptyMap()
    return gameIds.distinct().chunked(ART_ID_CHUNK).flatMap { getForGames(it) }.toResolvedArtByGame()
}

suspend fun GameArtDao.clearCached(gameId: Long, slot: ArtSlot) =
    updateCached(gameId, slot.name, null, null)

suspend fun GameArtDao.clearOverride(gameId: Long, slot: ArtSlot) =
    updateOverride(gameId, slot.name, null)

suspend fun GameArtDao.clearCachedPathsChunked(paths: Collection<String>) {
    paths.chunked(ART_ID_CHUNK).forEach { clearCachedPaths(it) }
}
