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

    @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY sortTitle ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE source = :source AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeBySource(source: GameSource): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED')
        ORDER BY sortTitle ASC
    """)
    fun observePlayable(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY lastPlayed DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE rommId = :rommId")
    suspend fun getByRommId(rommId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId")
    suspend fun getByIgdbId(igdbId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getByIgdbIdAndPlatform(igdbId: Long, platformId: String): GameEntity?

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

    @Query("SELECT COUNT(*) FROM games")
    suspend fun countAll(): Int

    @Query("SELECT * FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = :source")
    suspend fun getBySource(source: GameSource): List<GameEntity>

    @Query("UPDATE games SET localPath = NULL, source = 'ROMM_REMOTE' WHERE id = :gameId")
    suspend fun clearLocalPath(gameId: Long)

    @Query("UPDATE games SET backgroundPath = :path WHERE id = :gameId")
    suspend fun updateBackgroundPath(gameId: Long, path: String)

    @Query("SELECT * FROM games WHERE backgroundPath LIKE 'http%' AND rommId IS NOT NULL")
    suspend fun getGamesWithUncachedBackgrounds(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath IS NOT NULL AND rommId IS NOT NULL")
    suspend fun countGamesWithBackgrounds(): Int

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath LIKE '/%' AND rommId IS NOT NULL")
    suspend fun countGamesWithCachedBackgrounds(): Int

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
}
