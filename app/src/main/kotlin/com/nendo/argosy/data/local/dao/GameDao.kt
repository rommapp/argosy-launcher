package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface GameDao {

    @Query("SELECT * FROM games WHERE platformId = :platformId AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeByPlatform(platformId: String): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId AND isHidden = 0
        ORDER BY
            CASE
                WHEN localPath IS NOT NULL AND isFavorite = 1 THEN 0
                WHEN localPath IS NOT NULL THEN 1
                WHEN isFavorite = 1 THEN 2
                ELSE 3
            END,
            CASE WHEN lastPlayed IS NULL THEN 1 ELSE 0 END,
            lastPlayed DESC,
            CASE WHEN rating IS NULL THEN 1 ELSE 0 END,
            rating DESC,
            sortTitle ASC
        LIMIT :limit
    """)
    fun observeByPlatformSorted(platformId: String, limit: Int = 20): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId AND isHidden = 0
        ORDER BY
            CASE
                WHEN localPath IS NOT NULL AND isFavorite = 1 THEN 0
                WHEN localPath IS NOT NULL THEN 1
                WHEN isFavorite = 1 THEN 2
                ELSE 3
            END,
            CASE WHEN lastPlayed IS NULL THEN 1 ELSE 0 END,
            lastPlayed DESC,
            CASE WHEN rating IS NULL THEN 1 ELSE 0 END,
            rating DESC,
            sortTitle ASC
        LIMIT :limit
    """)
    suspend fun getByPlatformSorted(platformId: String, limit: Int = 20): List<GameEntity>

    @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY sortTitle ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE source = :source AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeBySource(source: GameSource): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM')
        ORDER BY sortTitle ASC
    """)
    fun observePlayable(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY sortTitle ASC")
    suspend fun getFavorites(): List<GameEntity>

    @Query("SELECT * FROM games WHERE isHidden = 0 AND lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isHidden = 0 AND lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 20): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE rommId = :rommId")
    suspend fun getByRommId(rommId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId")
    suspend fun getByIgdbId(igdbId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getByIgdbIdAndPlatform(igdbId: Long, platformId: String): GameEntity?

    @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
    suspend fun getBySteamAppId(steamAppId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE localPath = :path")
    suspend fun getByPath(path: String): GameEntity?

    @Query("SELECT * FROM games WHERE sortTitle = :sortTitle AND platformId = :platformId LIMIT 1")
    suspend fun getBySortTitleAndPlatform(sortTitle: String, platformId: String): GameEntity?

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' AND isHidden = 0 ORDER BY sortTitle ASC")
    fun search(query: String): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: GameEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET isFavorite = :favorite WHERE id = :gameId")
    suspend fun updateFavorite(gameId: Long, favorite: Boolean)

    @Query("UPDATE games SET isHidden = :hidden WHERE id = :gameId")
    suspend fun updateHidden(gameId: Long, hidden: Boolean)

    @Query("UPDATE games SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE id = :gameId")
    suspend fun recordPlayStart(gameId: Long, timestamp: Instant)

    @Query("UPDATE games SET playTimeMinutes = playTimeMinutes + :minutes WHERE id = :gameId")
    suspend fun addPlayTime(gameId: Long, minutes: Int)

    @Query("UPDATE games SET localPath = :path, source = :source WHERE id = :gameId")
    suspend fun updateLocalPath(gameId: Long, path: String?, source: GameSource)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun delete(gameId: Long)

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId AND isHidden = 0")
    suspend fun countByPlatform(platformId: String): Int

    @Query("SELECT * FROM games WHERE platformId = :platformId AND isHidden = 0 ORDER BY sortTitle ASC")
    suspend fun getByPlatform(platformId: String): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun countAll(): Int

    @Query("SELECT * FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE rommId IS NOT NULL AND localPath IS NULL")
    suspend fun getGamesWithRommIdButNoPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = :source")
    suspend fun getBySource(source: GameSource): List<GameEntity>

    @Query("UPDATE games SET localPath = NULL, source = 'ROMM_REMOTE' WHERE id = :gameId")
    suspend fun clearLocalPath(gameId: Long)

    @Query("UPDATE games SET backgroundPath = :path WHERE id = :gameId")
    suspend fun updateBackgroundPath(gameId: Long, path: String)

    @Query("SELECT * FROM games WHERE backgroundPath LIKE 'http%' AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun getGamesWithUncachedBackgrounds(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath IS NOT NULL AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun countGamesWithBackgrounds(): Int

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath LIKE '/%' AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun countGamesWithCachedBackgrounds(): Int

    @Query("UPDATE games SET coverPath = :path WHERE id = :gameId")
    suspend fun updateCoverPath(gameId: Long, path: String)

    @Query("SELECT * FROM games WHERE coverPath LIKE 'http%' AND rommId IS NOT NULL")
    suspend fun getGamesWithUncachedCovers(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE coverPath IS NOT NULL AND rommId IS NOT NULL")
    suspend fun countGamesWithCovers(): Int

    @Query("SELECT COUNT(*) FROM games WHERE coverPath LIKE '/%' AND rommId IS NOT NULL")
    suspend fun countGamesWithCachedCovers(): Int

    @Query("SELECT DISTINCT regions FROM games WHERE regions IS NOT NULL AND isHidden = 0")
    suspend fun getDistinctRegions(): List<String>

    @Query("SELECT DISTINCT genre FROM games WHERE genre IS NOT NULL AND isHidden = 0")
    suspend fun getDistinctGenres(): List<String>

    @Query("SELECT DISTINCT franchises FROM games WHERE franchises IS NOT NULL AND isHidden = 0")
    suspend fun getDistinctFranchises(): List<String>

    @Query("SELECT DISTINCT gameModes FROM games WHERE gameModes IS NOT NULL AND isHidden = 0")
    suspend fun getDistinctGameModes(): List<String>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND regions LIKE '%' || :region || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByRegion(region: String): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND genre = :genre
        ORDER BY sortTitle ASC
    """)
    fun observeByGenre(genre: String): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND franchises LIKE '%' || :franchise || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByFranchise(franchise: String): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND gameModes LIKE '%' || :gameMode || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByGameMode(gameMode: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE screenshotPaths IS NOT NULL AND cachedScreenshotPaths IS NULL AND rommId IS NOT NULL")
    suspend fun getGamesWithUncachedScreenshots(): List<GameEntity>

    @Query("UPDATE games SET cachedScreenshotPaths = :paths WHERE id = :gameId")
    suspend fun updateCachedScreenshotPaths(gameId: Long, paths: String)

    @Query("SELECT COUNT(*) FROM games WHERE screenshotPaths IS NOT NULL AND rommId IS NOT NULL")
    suspend fun countGamesWithScreenshots(): Int

    @Query("SELECT COUNT(*) FROM games WHERE cachedScreenshotPaths IS NOT NULL AND rommId IS NOT NULL")
    suspend fun countGamesWithCachedScreenshots(): Int

    @Query("UPDATE games SET userRating = :rating WHERE id = :gameId")
    suspend fun updateUserRating(gameId: Long, rating: Int)

    @Query("UPDATE games SET userDifficulty = :difficulty WHERE id = :gameId")
    suspend fun updateUserDifficulty(gameId: Long, difficulty: Int)

    @Query("UPDATE games SET completion = :completion WHERE id = :gameId")
    suspend fun updateCompletion(gameId: Long, completion: Int)

    @Query("UPDATE games SET backlogged = :backlogged WHERE id = :gameId")
    suspend fun updateBacklogged(gameId: Long, backlogged: Boolean)

    @Query("UPDATE games SET nowPlaying = :nowPlaying WHERE id = :gameId")
    suspend fun updateNowPlaying(gameId: Long, nowPlaying: Boolean)

    @Query("UPDATE games SET lastPlayedDiscId = :discId WHERE id = :gameId")
    suspend fun updateLastPlayedDisc(gameId: Long, discId: Long)

    @Query("UPDATE games SET achievementCount = :count, earnedAchievementCount = :earnedCount WHERE id = :gameId")
    suspend fun updateAchievementCount(gameId: Long, count: Int, earnedCount: Int = 0)
}
