package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.GameCategoryInfo
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface GameDao {

    @Query("SELECT * FROM games WHERE platformId = :platformId AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeByPlatform(platformId: Long): Flow<List<GameEntity>>

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
    fun observeByPlatformSorted(platformId: Long, limit: Int = 20): Flow<List<GameEntity>>

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
    suspend fun getByPlatformSorted(platformId: Long, limit: Int = 20): List<GameEntity>

    @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY sortTitle ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isHidden = 0 ORDER BY sortTitle ASC")
    suspend fun getAllSortedByTitle(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = :source AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeBySource(source: GameSource): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY sortTitle ASC
    """)
    fun observePlayable(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC")
    fun observeFavorites(): Flow<List<GameEntity>>

    // Lightweight list projections (avoid CursorWindow overflow)
    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE isHidden = 0 ORDER BY sortTitle ASC")
    fun observeAllList(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE platformId = :platformId AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeByPlatformList(platformId: Long): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE source = :source AND isHidden = 0 ORDER BY sortTitle ASC")
    fun observeBySourceList(source: GameSource): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games
        WHERE isHidden = 0
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY sortTitle ASC
    """)
    fun observePlayableList(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC")
    fun observeFavoritesList(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games ORDER BY sortTitle ASC")
    fun observeAllListIncludingHidden(): Flow<List<GameListItem>>

    @Query("""
        SELECT g.id, g.platformId, g.platformSlug, g.title, g.sortTitle, g.localPath, g.source, g.coverPath, g.isFavorite, g.isHidden, g.isMultiDisc, g.rommId, g.steamAppId, g.packageName, g.playCount, g.playTimeMinutes, g.lastPlayed, g.genre, g.gameModes
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE g.isHidden = 0 AND p.syncEnabled = 1
        ORDER BY g.sortTitle ASC
    """)
    fun observeSyncEnabledGames(): Flow<List<GameListItem>>

    @Query("""
        SELECT g.*
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE g.isHidden = 0 AND p.syncEnabled = 1
        ORDER BY g.sortTitle ASC
    """)
    fun observeSyncEnabledGamesFull(): Flow<List<GameEntity>>

    @Query("""
        SELECT g.id, g.genre, g.gameModes
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE g.isHidden = 0 AND p.syncEnabled = 1
    """)
    suspend fun getSyncEnabledGamesForCategories(): List<GameCategoryInfo>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE platformId = :platformId ORDER BY sortTitle ASC")
    fun observeByPlatformListIncludingHidden(platformId: Long): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games
        WHERE (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY sortTitle ASC
    """)
    fun observePlayableListIncludingHidden(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE isFavorite = 1 ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC")
    fun observeFavoritesListIncludingHidden(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE isHidden = 1 ORDER BY sortTitle ASC")
    fun observeHiddenList(): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE platformId = :platformId AND isHidden = 1 ORDER BY sortTitle ASC")
    fun observeHiddenByPlatformList(platformId: Long): Flow<List<GameListItem>>

    @Query("SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes FROM games WHERE platformId = :platformId AND isFavorite = 1 AND isHidden = 0 ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC")
    fun observeFavoritesByPlatformList(platformId: Long): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName, playCount, playTimeMinutes, lastPlayed, genre, gameModes
        FROM games
        WHERE platformId = :platformId
        AND isHidden = 0
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY sortTitle ASC
    """)
    fun observePlayableByPlatformList(platformId: Long): Flow<List<GameListItem>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND isHidden = 0 ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC")
    suspend fun getFavorites(): List<GameEntity>

    @Query("SELECT rommId FROM games WHERE isFavorite = 1 AND rommId IS NOT NULL")
    suspend fun getFavoriteRommIds(): List<Long>

    @Query("UPDATE games SET isFavorite = 1 WHERE rommId IN (:rommIds)")
    suspend fun setFavoritesByRommIds(rommIds: List<Long>)

    @Query("UPDATE games SET isFavorite = 0 WHERE rommId IS NOT NULL AND rommId NOT IN (:rommIds)")
    suspend fun clearFavoritesNotInRommIds(rommIds: List<Long>)

    @Query("UPDATE games SET isFavorite = 1 WHERE rommId = :rommId")
    suspend fun setFavoriteByRommId(rommId: Long)

    @Query("UPDATE games SET isFavorite = 0 WHERE rommId = :rommId")
    suspend fun clearFavoriteByRommId(rommId: Long)

    @Query("SELECT * FROM games WHERE isHidden = 0 AND lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int = 20): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE isHidden = 0 AND lastPlayed IS NOT NULL ORDER BY lastPlayed DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 20): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND lastPlayed IS NULL
        AND addedAt > :threshold
        AND (localPath IS NOT NULL OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY addedAt DESC
        LIMIT :limit
    """)
    fun observeNewlyAddedPlayable(threshold: Instant, limit: Int = 20): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND lastPlayed IS NULL
        AND addedAt > :threshold
        AND (localPath IS NOT NULL OR source = 'STEAM' OR source = 'ANDROID_APP')
        ORDER BY addedAt DESC
        LIMIT :limit
    """)
    suspend fun getNewlyAddedPlayable(threshold: Instant, limit: Int = 20): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE rommId = :rommId")
    suspend fun getByRommId(rommId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId")
    suspend fun getByIgdbId(igdbId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getByIgdbIdAndPlatform(igdbId: Long, platformId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getAllByIgdbIdAndPlatform(igdbId: Long, platformId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
    suspend fun getBySteamAppId(steamAppId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE localPath = :path")
    suspend fun getByPath(path: String): GameEntity?

    @Query("SELECT * FROM games WHERE sortTitle = :sortTitle AND platformId = :platformId LIMIT 1")
    suspend fun getBySortTitleAndPlatform(sortTitle: String, platformId: Long): GameEntity?

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

    @Query("UPDATE games SET localPath = :path, source = :source, addedAt = :addedAt WHERE id = :gameId")
    suspend fun updateLocalPath(gameId: Long, path: String?, source: GameSource, addedAt: Instant = Instant.now())

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun delete(gameId: Long)

    @Query("DELETE FROM games WHERE platformId = :platformId")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId AND isHidden = 0")
    suspend fun countByPlatform(platformId: Long): Int

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId AND localPath IS NOT NULL")
    suspend fun countDownloadedByPlatform(platformId: Long): Int

    @Query("SELECT * FROM games WHERE platformId = :platformId AND localPath IS NOT NULL")
    suspend fun getDownloadedByPlatform(platformId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE platformId = :platformId AND isHidden = 0 ORDER BY sortTitle ASC")
    suspend fun getByPlatform(platformId: Long): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun countAll(): Int

    @Query("SELECT * FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE rommId IS NOT NULL AND localPath IS NULL")
    suspend fun getGamesWithRommIdButNoPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = :source")
    suspend fun getBySource(source: GameSource): List<GameEntity>

    @Query("SELECT * FROM games WHERE packageName = :packageName")
    suspend fun getByPackageName(packageName: String): GameEntity?

    @Delete
    suspend fun delete(game: GameEntity)

    @Query("UPDATE games SET localPath = NULL, source = 'ROMM_REMOTE' WHERE id = :gameId")
    suspend fun clearLocalPath(gameId: Long)

    @Query("UPDATE games SET backgroundPath = :path WHERE id = :gameId")
    suspend fun updateBackgroundPath(gameId: Long, path: String)

    @Query("UPDATE games SET backgroundPath = NULL WHERE id = :gameId")
    suspend fun clearBackgroundPath(gameId: Long)

    @Query("SELECT * FROM games WHERE backgroundPath LIKE 'http%' AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun getGamesWithUncachedBackgrounds(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath IS NOT NULL AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun countGamesWithBackgrounds(): Int

    @Query("SELECT COUNT(*) FROM games WHERE backgroundPath LIKE '/%' AND (rommId IS NOT NULL OR steamAppId IS NOT NULL)")
    suspend fun countGamesWithCachedBackgrounds(): Int

    @Query("UPDATE games SET coverPath = :path WHERE id = :gameId")
    suspend fun updateCoverPath(gameId: Long, path: String)

    @Query("UPDATE games SET coverPath = NULL WHERE id = :gameId")
    suspend fun clearCoverPath(gameId: Long)

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

    @Query("SELECT cachedScreenshotPaths FROM games WHERE id = :gameId")
    suspend fun getCachedScreenshotPaths(gameId: Long): String?

    @Query("SELECT screenshotPaths FROM games WHERE id = :gameId")
    suspend fun getScreenshotPaths(gameId: Long): String?

    @Query("UPDATE games SET cachedScreenshotPaths = :paths WHERE id = :gameId")
    suspend fun updateCachedScreenshotPaths(gameId: Long, paths: String)

    @Query("UPDATE games SET cachedScreenshotPaths = NULL WHERE id = :gameId")
    suspend fun clearCachedScreenshotPaths(gameId: Long)

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

    @Query("UPDATE games SET status = :status WHERE id = :gameId")
    suspend fun updateStatus(gameId: Long, status: String?)

    @Query("UPDATE games SET backlogged = :backlogged WHERE id = :gameId")
    suspend fun updateBacklogged(gameId: Long, backlogged: Boolean)

    @Query("UPDATE games SET nowPlaying = :nowPlaying WHERE id = :gameId")
    suspend fun updateNowPlaying(gameId: Long, nowPlaying: Boolean)

    @Query("UPDATE games SET lastPlayedDiscId = :discId WHERE id = :gameId")
    suspend fun updateLastPlayedDisc(gameId: Long, discId: Long)

    @Query("UPDATE games SET m3uPath = :path WHERE id = :gameId")
    suspend fun updateM3uPath(gameId: Long, path: String?)

    @Query("UPDATE games SET achievementCount = :count, earnedAchievementCount = :earnedCount WHERE id = :gameId")
    suspend fun updateAchievementCount(gameId: Long, count: Int, earnedCount: Int = 0)

    @Query("UPDATE games SET achievementsFetchedAt = :timestamp WHERE id = :gameId")
    suspend fun updateAchievementsFetchedAt(gameId: Long, timestamp: Long)

    @Query("SELECT achievementsFetchedAt FROM games WHERE id = :gameId")
    suspend fun getAchievementsFetchedAt(gameId: Long): Long?

    @Query("UPDATE games SET fileSizeBytes = :sizeBytes WHERE id = :gameId")
    suspend fun updateFileSize(gameId: Long, sizeBytes: Long)

    @Query("UPDATE games SET romHash = :hash WHERE id = :gameId")
    suspend fun updateRomHash(gameId: Long, hash: String)

    @Query("SELECT romHash FROM games WHERE id = :gameId")
    suspend fun getRomHash(gameId: Long): String?

    @Query("UPDATE games SET earnedAchievementCount = earnedAchievementCount + 1 WHERE id = :gameId")
    suspend fun incrementEarnedAchievementCount(gameId: Long)

    @Query("UPDATE games SET activeSaveChannel = :channelName WHERE id = :gameId")
    suspend fun updateActiveSaveChannel(gameId: Long, channelName: String?)

    @Query("SELECT activeSaveChannel FROM games WHERE id = :gameId")
    suspend fun getActiveSaveChannel(gameId: Long): String?

    @Query("UPDATE games SET activeSaveTimestamp = :timestamp WHERE id = :gameId")
    suspend fun updateActiveSaveTimestamp(gameId: Long, timestamp: Long?)

    @Query("SELECT activeSaveTimestamp FROM games WHERE id = :gameId")
    suspend fun getActiveSaveTimestamp(gameId: Long): Long?

    @Query("UPDATE games SET activeSaveApplied = :applied WHERE id = :gameId")
    suspend fun updateActiveSaveApplied(gameId: Long, applied: Boolean)

    @Query("UPDATE games SET activeSaveApplied = 0 WHERE activeSaveApplied = 1")
    suspend fun resetAllActiveSaveApplied()

    @Query("SELECT activeSaveApplied FROM games WHERE id = :gameId")
    suspend fun getActiveSaveApplied(gameId: Long): Boolean

    @Query("UPDATE games SET titleId = :titleId WHERE id = :gameId AND titleIdLocked = 0")
    suspend fun updateTitleId(gameId: Long, titleId: String?)

    @Query("UPDATE games SET titleId = :titleId, titleIdLocked = :locked WHERE id = :gameId")
    suspend fun setTitleIdWithLock(gameId: Long, titleId: String?, locked: Boolean)

    @Query("SELECT titleIdLocked FROM games WHERE id = :gameId")
    suspend fun isTitleIdLocked(gameId: Long): Boolean

    @Query("UPDATE games SET titleId = NULL, titleIdLocked = 0 WHERE id = :gameId")
    suspend fun clearTitleId(gameId: Long)

    @Query("SELECT * FROM games WHERE titleId = :titleId AND platformId = :platformId LIMIT 1")
    suspend fun getByTitleIdAndPlatform(titleId: String, platformId: Long): GameEntity?

    @Query("SELECT titleId FROM games WHERE id = :gameId")
    suspend fun getTitleId(gameId: Long): String?

    @Query("UPDATE games SET titleIdCandidates = :candidates WHERE id = :gameId")
    suspend fun updateTitleIdCandidates(gameId: Long, candidates: String?)

    @Query("SELECT titleIdCandidates FROM games WHERE id = :gameId")
    suspend fun getTitleIdCandidates(gameId: Long): String?

    @Query("SELECT * FROM games WHERE playCount > 0 AND isHidden = 0 ORDER BY playTimeMinutes DESC")
    suspend fun getPlayedGames(): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE (playCount = 0 OR playCount IS NULL)
        AND (completion = 0 OR completion IS NULL)
        AND localPath IS NOT NULL
        AND isHidden = 0
    """)
    suspend fun getUnplayedInstalledGames(): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE (playCount = 0 OR playCount IS NULL)
        AND (completion = 0 OR completion IS NULL)
        AND localPath IS NULL
        AND isHidden = 0
    """)
    suspend fun getUnplayedUndownloadedGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<GameEntity>

    @Query("UPDATE games SET platformId = :newPlatformId WHERE platformId = :oldPlatformId")
    suspend fun migratePlatform(oldPlatformId: Long, newPlatformId: Long)

    @Query("UPDATE games SET steamLauncher = :launcher, launcherSetManually = :setManually WHERE id = :gameId")
    suspend fun updateSteamLauncher(gameId: Long, launcher: String?, setManually: Boolean)

    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<GameEntity>

    @Query("UPDATE games SET coverPath = :coverPath, backgroundPath = :backgroundPath, cachedScreenshotPaths = :cachedScreenshotPaths WHERE id = :gameId")
    suspend fun updateImagePaths(gameId: Long, coverPath: String?, backgroundPath: String?, cachedScreenshotPaths: String?)

    @Query("""
        SELECT * FROM games
        WHERE isHidden = 0
        AND (status IS NULL OR status NOT IN ('retired', 'never_playing'))
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomGame(): GameEntity?

    @Query("""
        SELECT * FROM games
        WHERE title LIKE '%' || :query || '%'
        AND isHidden = 0
        ORDER BY
            CASE WHEN title LIKE :query || '%' THEN 0 ELSE 1 END,
            CASE WHEN rating IS NULL THEN 1 ELSE 0 END,
            rating DESC,
            sortTitle ASC
        LIMIT :limit
    """)
    fun searchForQuickMenu(query: String, limit: Int = 10): Flow<List<GameEntity>>

    @Query("""
        SELECT id, title, rating FROM games
        WHERE isHidden = 0
        AND (status IS NULL OR status NOT IN ('retired', 'never_playing'))
    """)
    suspend fun getSearchCandidates(): List<SearchCandidate>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath,
               isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes
        FROM games
        WHERE coverPath LIKE '/%' AND isHidden = 0
        LIMIT 1
    """)
    suspend fun getFirstGameWithCover(): GameListItem?

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath,
               isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes
        FROM games
        WHERE coverPath LIKE '/%' AND isHidden = 0 AND lastPlayed IS NOT NULL AND localPath IS NOT NULL
        ORDER BY lastPlayed DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedWithCovers(limit: Int = 10): List<GameListItem>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath,
               isFavorite, isHidden, isMultiDisc, rommId, steamAppId, packageName,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes
        FROM games
        WHERE coverPath LIKE '/%' AND isHidden = 0 AND lastPlayed IS NOT NULL
              AND localPath IS NOT NULL AND platformSlug IN (:platformSlugs)
        ORDER BY lastPlayed DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedOnPlatforms(platformSlugs: List<String>, limit: Int = 10): List<GameListItem>

    @Query("UPDATE games SET cheatsFetched = :fetched WHERE id = :gameId")
    suspend fun updateCheatsFetched(gameId: Long, fetched: Boolean)

    @Query("SELECT * FROM games WHERE cheatsFetched = 0 AND localPath IS NOT NULL LIMIT :limit")
    suspend fun getGamesWithoutCheats(limit: Int = 50): List<GameEntity>
}

data class SearchCandidate(
    val id: Long,
    val title: String,
    val rating: Float?
)
