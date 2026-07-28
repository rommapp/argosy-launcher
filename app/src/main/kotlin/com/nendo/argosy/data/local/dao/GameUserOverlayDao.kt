package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.nendo.argosy.data.local.entity.GameUserOverlayEntity
import java.time.Instant

/**
 * Per-account game state, with every write mirrored onto the matching `games` column.
 *
 * The overlay row is the record and the `games` column is a materialised copy of the active
 * account, so reads keep going through the existing `FROM games` queries. Writes go the other
 * way round in one transaction: overlay first, then the mirror, never the mirror alone.
 *
 * A missing overlay row is deliberately not an error. It means this account has never written
 * anything about that game, in which case the `games` value already is its value, so the row
 * is seeded from `games` on first write and reads fall back to `games` until then.
 */
@Dao
interface GameUserOverlayDao {

    @Upsert
    suspend fun upsert(entity: GameUserOverlayEntity)

    @Query("SELECT * FROM game_user_overlay WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun get(ownerUserId: Long, gameId: Long): GameUserOverlayEntity?

    @Query("SELECT COUNT(*) FROM game_user_overlay WHERE ownerUserId = :ownerUserId")
    suspend fun countForOwner(ownerUserId: Long): Int

    @Query("SELECT COUNT(*) FROM game_user_overlay")
    suspend fun countAll(): Int

    @Query(
        """
        INSERT OR IGNORE INTO game_user_overlay (
            ownerUserId, gameId, isMember, serverHidden, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        )
        SELECT :ownerUserId, id, 1, 0, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        FROM games WHERE id = :gameId
        """
    )
    suspend fun ensureRow(ownerUserId: Long, gameId: Long)

    @Query(
        """
        INSERT OR IGNORE INTO game_user_overlay (
            ownerUserId, gameId, isMember, serverHidden, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        )
        SELECT :ownerUserId, id, 1, 0, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        FROM games WHERE rommId IN (:rommIds)
        """
    )
    suspend fun ensureRowsByRommIds(ownerUserId: Long, rommIds: List<Long>)

    @Query(
        """
        INSERT OR IGNORE INTO game_user_overlay (
            ownerUserId, gameId, isMember, serverHidden, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        )
        SELECT :ownerUserId, id, 1, 0, isFavorite, userRating, userDifficulty,
            completion, status, backlogged, nowPlaying, playCount, playTimeMinutes, lastPlayed,
            earnedAchievementCount
        FROM games
        """
    )
    suspend fun adoptWholeLibrary(ownerUserId: Long)

    @Query("UPDATE game_user_overlay SET isMember = :member WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeMembership(ownerUserId: Long, gameId: Long, member: Boolean)

    @Query("UPDATE game_user_overlay SET serverHidden = :hidden WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeServerHidden(ownerUserId: Long, gameId: Long, hidden: Boolean)

    /**
     * Drops one account's claim on a game without touching the row every other account shares.
     * [serverHidden] separates "the server hides this from me" from "I was never given it".
     */
    @Transaction
    suspend fun dropMembership(ownerUserId: Long, gameId: Long, serverHidden: Boolean) {
        ensureRow(ownerUserId, gameId)
        writeMembership(ownerUserId, gameId, false)
        writeServerHidden(ownerUserId, gameId, serverHidden)
    }

    @Transaction
    suspend fun grantMembership(ownerUserId: Long, gameId: Long) {
        ensureRow(ownerUserId, gameId)
        writeMembership(ownerUserId, gameId, true)
        writeServerHidden(ownerUserId, gameId, false)
    }

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM game_user_overlay
            WHERE ownerUserId = :ownerUserId AND gameId = :gameId AND isMember = 0
        )
        """
    )
    suspend fun isMasked(ownerUserId: Long, gameId: Long): Boolean

    @Query("SELECT gameId FROM game_user_overlay WHERE ownerUserId = :ownerUserId AND isMember = 0")
    suspend fun getMaskedGameIds(ownerUserId: Long): List<Long>

    @Query(
        """
        SELECT g.rommId FROM games g
        LEFT JOIN game_user_overlay o ON o.gameId = g.id AND o.ownerUserId = :ownerUserId
        WHERE g.rommId IS NOT NULL
          AND IFNULL(o.isMember, 1) = 1
          AND (CASE WHEN o.id IS NULL THEN g.isFavorite ELSE o.isFavorite END) = 1
        """
    )
    suspend fun getFavoriteRommIdsForOwner(ownerUserId: Long): List<Long>

    @Query("UPDATE game_user_overlay SET isFavorite = :favorite WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeFavorite(ownerUserId: Long, gameId: Long, favorite: Boolean)

    @Query("UPDATE games SET isFavorite = :favorite WHERE id = :gameId")
    suspend fun mirrorFavorite(gameId: Long, favorite: Boolean)

    @Transaction
    suspend fun setFavorite(ownerUserId: Long?, gameId: Long, favorite: Boolean) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeFavorite(ownerUserId, gameId, favorite)
        }
        mirrorFavorite(gameId, favorite)
    }

    @Query(
        """
        UPDATE game_user_overlay SET isFavorite = 1
        WHERE ownerUserId = :ownerUserId
          AND gameId IN (SELECT id FROM games WHERE rommId IN (:rommIds))
        """
    )
    suspend fun writeFavoritesByRommIds(ownerUserId: Long, rommIds: List<Long>)

    @Query("UPDATE games SET isFavorite = 1 WHERE rommId IN (:rommIds)")
    suspend fun mirrorFavoritesByRommIds(rommIds: List<Long>)

    @Transaction
    suspend fun setFavoritesByRommIds(ownerUserId: Long?, rommIds: List<Long>) {
        if (rommIds.isEmpty()) return
        if (ownerUserId != null) {
            ensureRowsByRommIds(ownerUserId, rommIds)
            writeFavoritesByRommIds(ownerUserId, rommIds)
        }
        mirrorFavoritesByRommIds(rommIds)
    }

    @Query(
        """
        UPDATE game_user_overlay SET isFavorite = :favorite
        WHERE ownerUserId = :ownerUserId
          AND gameId IN (SELECT id FROM games WHERE rommId = :rommId)
        """
    )
    suspend fun writeFavoriteByRommId(ownerUserId: Long, rommId: Long, favorite: Boolean)

    @Query("UPDATE games SET isFavorite = :favorite WHERE rommId = :rommId")
    suspend fun mirrorFavoriteByRommId(rommId: Long, favorite: Boolean)

    @Transaction
    suspend fun setFavoriteByRommId(ownerUserId: Long?, rommId: Long, favorite: Boolean) {
        if (ownerUserId != null) {
            ensureRowsByRommIds(ownerUserId, listOf(rommId))
            writeFavoriteByRommId(ownerUserId, rommId, favorite)
        }
        mirrorFavoriteByRommId(rommId, favorite)
    }

    @Query("UPDATE game_user_overlay SET userRating = :rating WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeUserRating(ownerUserId: Long, gameId: Long, rating: Int)

    @Query("UPDATE games SET userRating = :rating WHERE id = :gameId")
    suspend fun mirrorUserRating(gameId: Long, rating: Int)

    @Transaction
    suspend fun setUserRating(ownerUserId: Long?, gameId: Long, rating: Int) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeUserRating(ownerUserId, gameId, rating)
        }
        mirrorUserRating(gameId, rating)
    }

    @Query("UPDATE game_user_overlay SET userDifficulty = :difficulty WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeUserDifficulty(ownerUserId: Long, gameId: Long, difficulty: Int)

    @Query("UPDATE games SET userDifficulty = :difficulty WHERE id = :gameId")
    suspend fun mirrorUserDifficulty(gameId: Long, difficulty: Int)

    @Transaction
    suspend fun setUserDifficulty(ownerUserId: Long?, gameId: Long, difficulty: Int) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeUserDifficulty(ownerUserId, gameId, difficulty)
        }
        mirrorUserDifficulty(gameId, difficulty)
    }

    @Query("UPDATE game_user_overlay SET completion = :completion WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeCompletion(ownerUserId: Long, gameId: Long, completion: Int)

    @Query("UPDATE games SET completion = :completion WHERE id = :gameId")
    suspend fun mirrorCompletion(gameId: Long, completion: Int)

    @Transaction
    suspend fun setCompletion(ownerUserId: Long?, gameId: Long, completion: Int) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeCompletion(ownerUserId, gameId, completion)
        }
        mirrorCompletion(gameId, completion)
    }

    @Query("UPDATE game_user_overlay SET status = :status WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeStatus(ownerUserId: Long, gameId: Long, status: String?)

    @Query("UPDATE games SET status = :status WHERE id = :gameId")
    suspend fun mirrorStatus(gameId: Long, status: String?)

    @Transaction
    suspend fun setStatus(ownerUserId: Long?, gameId: Long, status: String?) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeStatus(ownerUserId, gameId, status)
        }
        mirrorStatus(gameId, status)
    }

    @Query("UPDATE game_user_overlay SET backlogged = :backlogged WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeBacklogged(ownerUserId: Long, gameId: Long, backlogged: Boolean)

    @Query("UPDATE games SET backlogged = :backlogged WHERE id = :gameId")
    suspend fun mirrorBacklogged(gameId: Long, backlogged: Boolean)

    @Transaction
    suspend fun setBacklogged(ownerUserId: Long?, gameId: Long, backlogged: Boolean) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeBacklogged(ownerUserId, gameId, backlogged)
        }
        mirrorBacklogged(gameId, backlogged)
    }

    @Query("UPDATE game_user_overlay SET nowPlaying = :nowPlaying WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeNowPlaying(ownerUserId: Long, gameId: Long, nowPlaying: Boolean)

    @Query("UPDATE games SET nowPlaying = :nowPlaying WHERE id = :gameId")
    suspend fun mirrorNowPlaying(gameId: Long, nowPlaying: Boolean)

    @Transaction
    suspend fun setNowPlaying(ownerUserId: Long?, gameId: Long, nowPlaying: Boolean) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeNowPlaying(ownerUserId, gameId, nowPlaying)
        }
        mirrorNowPlaying(gameId, nowPlaying)
    }

    @Query(
        """
        UPDATE game_user_overlay SET lastPlayed = :timestamp, playCount = playCount + 1
        WHERE ownerUserId = :ownerUserId AND gameId = :gameId
        """
    )
    suspend fun writePlayStart(ownerUserId: Long, gameId: Long, timestamp: Instant)

    @Query("UPDATE games SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE id = :gameId")
    suspend fun mirrorPlayStart(gameId: Long, timestamp: Instant)

    @Transaction
    suspend fun recordPlayStart(ownerUserId: Long?, gameId: Long, timestamp: Instant) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writePlayStart(ownerUserId, gameId, timestamp)
        }
        mirrorPlayStart(gameId, timestamp)
    }

    @Query(
        """
        UPDATE game_user_overlay SET playTimeMinutes = playTimeMinutes + :minutes
        WHERE ownerUserId = :ownerUserId AND gameId = :gameId
        """
    )
    suspend fun writeAddPlayTime(ownerUserId: Long, gameId: Long, minutes: Int)

    @Query("UPDATE games SET playTimeMinutes = playTimeMinutes + :minutes WHERE id = :gameId")
    suspend fun mirrorAddPlayTime(gameId: Long, minutes: Int)

    @Transaction
    suspend fun addPlayTime(ownerUserId: Long?, gameId: Long, minutes: Int) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeAddPlayTime(ownerUserId, gameId, minutes)
        }
        mirrorAddPlayTime(gameId, minutes)
    }

    @Query(
        """
        UPDATE game_user_overlay
        SET playCount = :playCount, playTimeMinutes = :playTimeMinutes, lastPlayed = :lastPlayed
        WHERE ownerUserId = :ownerUserId AND gameId = :gameId
        """
    )
    suspend fun writeMergedPlayTotals(
        ownerUserId: Long,
        gameId: Long,
        playCount: Int,
        playTimeMinutes: Int,
        lastPlayed: Instant?
    )

    @Query(
        """
        UPDATE games
        SET playCount = :playCount, playTimeMinutes = :playTimeMinutes, lastPlayed = :lastPlayed
        WHERE id = :gameId
        """
    )
    suspend fun mirrorMergedPlayTotals(
        gameId: Long,
        playCount: Int,
        playTimeMinutes: Int,
        lastPlayed: Instant?
    )

    /**
     * Absolute totals rather than increments, for multi-disc consolidation where the figures are
     * summed across the rows being folded together.
     */
    @Transaction
    suspend fun setMergedPlayTotals(
        ownerUserId: Long?,
        gameId: Long,
        playCount: Int,
        playTimeMinutes: Int,
        lastPlayed: Instant?
    ) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeMergedPlayTotals(ownerUserId, gameId, playCount, playTimeMinutes, lastPlayed)
        }
        mirrorMergedPlayTotals(gameId, playCount, playTimeMinutes, lastPlayed)
    }

    @Query("UPDATE game_user_overlay SET earnedAchievementCount = :earned WHERE ownerUserId = :ownerUserId AND gameId = :gameId")
    suspend fun writeEarnedAchievementCount(ownerUserId: Long, gameId: Long, earned: Int)

    @Query("UPDATE games SET achievementCount = :count, earnedAchievementCount = :earned WHERE id = :gameId")
    suspend fun mirrorAchievementCounts(gameId: Long, count: Int, earned: Int)

    /**
     * The set size belongs to the ROM and stays on `games`; only the earned tally is per account.
     */
    @Transaction
    suspend fun setAchievementCounts(ownerUserId: Long?, gameId: Long, count: Int, earned: Int) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeEarnedAchievementCount(ownerUserId, gameId, earned)
        }
        mirrorAchievementCounts(gameId, count, earned)
    }

    @Query(
        """
        UPDATE game_user_overlay SET earnedAchievementCount = earnedAchievementCount + 1
        WHERE ownerUserId = :ownerUserId AND gameId = :gameId
        """
    )
    suspend fun writeIncrementEarned(ownerUserId: Long, gameId: Long)

    @Query("UPDATE games SET earnedAchievementCount = earnedAchievementCount + 1 WHERE id = :gameId")
    suspend fun mirrorIncrementEarned(gameId: Long)

    @Transaction
    suspend fun incrementEarnedAchievementCount(ownerUserId: Long?, gameId: Long) {
        if (ownerUserId != null) {
            ensureRow(ownerUserId, gameId)
            writeIncrementEarned(ownerUserId, gameId)
        }
        mirrorIncrementEarned(gameId)
    }

    @Query(
        """
        UPDATE games SET
            isFavorite = 0, userRating = 0, userDifficulty = 0, completion = 0, status = NULL,
            backlogged = 0, nowPlaying = 0, playCount = 0, playTimeMinutes = 0, lastPlayed = NULL,
            earnedAchievementCount = 0
        """
    )
    suspend fun clearMaterialisedColumns()

    @Query(
        """
        UPDATE games SET
            isFavorite = (SELECT o.isFavorite FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            userRating = (SELECT o.userRating FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            userDifficulty = (SELECT o.userDifficulty FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            completion = (SELECT o.completion FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            status = (SELECT o.status FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            backlogged = (SELECT o.backlogged FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            nowPlaying = (SELECT o.nowPlaying FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            playCount = (SELECT o.playCount FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            playTimeMinutes = (SELECT o.playTimeMinutes FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            lastPlayed = (SELECT o.lastPlayed FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId),
            earnedAchievementCount = (SELECT o.earnedAchievementCount FROM game_user_overlay o WHERE o.gameId = games.id AND o.ownerUserId = :ownerUserId)
        WHERE id IN (SELECT gameId FROM game_user_overlay WHERE ownerUserId = :ownerUserId)
        """
    )
    suspend fun applyOverlayToGames(ownerUserId: Long)

    /**
     * Points the materialised `games` columns at [ownerUserId]. The clear runs first so a game
     * the incoming account has no row for shows that account's absence of state rather than the
     * outgoing account's. Never writes back from `games` to the overlay: the overlay is already
     * current, and a write-back would let a half-materialised table poison the record.
     */
    @Transaction
    suspend fun materialiseForOwner(ownerUserId: Long) {
        clearMaterialisedColumns()
        applyOverlayToGames(ownerUserId)
    }

    @Query("DELETE FROM game_user_overlay WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForOwner(ownerUserId: Long)
}
