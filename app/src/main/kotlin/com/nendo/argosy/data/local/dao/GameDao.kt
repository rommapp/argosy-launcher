package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.GameCategoryInfo
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class PlatformGameCount(
    val platformId: Long,
    val gameCount: Int
)

/**
 * Hiding is per account and lives in `user_roms_hidden`, so every list, count and filter here
 * carries the owner it is being run for and tests row existence rather than a column.
 *
 * A row with a null owner is an unattributed hide from an install that predates accounts, and
 * counts for whoever is signed in; the alternative is a user's hidden roms all reappearing the
 * first time they sign in to RomM. `save_cache` reads its unattributed rows the same way.
 */
@Dao
interface GameDao {

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeByPlatform(platformId: Long, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
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
    fun observeByPlatformSorted(platformId: Long, ownerUserId: Long?, limit: Int = 20): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
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
    suspend fun getByPlatformSorted(platformId: Long, ownerUserId: Long?, limit: Int = 20): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeAll(ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getAllSortedByTitle(ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT id FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getAllSortedByTitleIds(ownerUserId: Long?): List<Long>

    @Query("""
        SELECT * FROM games
        WHERE EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getHiddenSortedByTitle(ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT id FROM games
        WHERE EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getHiddenSortedByTitleIds(ownerUserId: Long?): List<Long>

    @Query("""
        SELECT * FROM games
        WHERE source = :source
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeBySource(source: GameSource, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    fun observeFavorites(ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeAllList(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeByPlatformList(platformId: Long, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE source = :source
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeBySourceList(source: GameSource, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        AND (source != 'STEAM' OR localPath IS NOT NULL OR (steamLauncher IS NOT NULL AND steamLauncher != 'native'))
        ORDER BY sortTitle ASC
    """)
    fun observePlayableList(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    fun observeFavoritesList(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        ORDER BY sortTitle ASC
    """)
    fun observeAllListIncludingHidden(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT g.id, g.platformId, g.platformSlug, g.title, g.sortTitle, g.localPath, g.source, g.coverPath, g.isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = g.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               g.isMultiDisc, g.rommId, g.steamAppId, g.packageName, g.steamLauncher, g.playCount, g.playTimeMinutes,
               g.lastPlayed, g.genre, g.gameModes, g.rating, g.userRating, g.userDifficulty, g.releaseYear, g.addedAt
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE p.syncEnabled = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = g.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY g.sortTitle ASC
    """)
    fun observeSyncEnabledGames(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT g.*
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE p.syncEnabled = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = g.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY g.sortTitle ASC
    """)
    fun observeSyncEnabledGamesFull(ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT g.id, g.genre, g.gameModes
        FROM games g
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE p.syncEnabled = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = g.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getSyncEnabledGamesForCategories(ownerUserId: Long?): List<GameCategoryInfo>

    @Query("""
        SELECT id, genre, gameModes FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    fun observeAllCategoryInfo(ownerUserId: Long?): Flow<List<GameCategoryInfo>>

    @Query("""
        SELECT id, genre, gameModes FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getAllCategoryInfo(ownerUserId: Long?): List<GameCategoryInfo>

    @Query("SELECT id, platformId, platformSlug, source, localPath FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPathInfo(): List<GameLocalPathInfo>

    @Query("""
        SELECT id, platformId, localPath FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getAllStorageInfo(ownerUserId: Long?): List<GameStorageInfo>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE platformId = :platformId
        ORDER BY sortTitle ASC
    """)
    fun observeByPlatformListIncludingHidden(platformId: Long, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE isFavorite = 1
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    fun observeFavoritesListIncludingHidden(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeHiddenList(ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE platformId = :platformId
        AND EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun observeHiddenByPlatformList(platformId: Long, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE platformId = :platformId AND isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    fun observeFavoritesByPlatformList(platformId: Long, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND (source = 'LOCAL_ONLY' OR source = 'ROMM_SYNCED' OR source = 'STEAM' OR source = 'ANDROID_APP')
        AND (source != 'STEAM' OR localPath IS NOT NULL OR (steamLauncher IS NOT NULL AND steamLauncher != 'native'))
        ORDER BY sortTitle ASC
    """)
    fun observePlayableByPlatformList(platformId: Long, ownerUserId: Long?): Flow<List<GameListItem>>

    @Query("""
        SELECT * FROM games
        WHERE isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    suspend fun getFavorites(ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT id FROM games
        WHERE isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY (source = 'ROMM_REMOTE') ASC, sortTitle ASC
    """)
    suspend fun getFavoriteIds(ownerUserId: Long?): List<Long>

    @Query("SELECT rommId FROM games WHERE isFavorite = 1 AND rommId IS NOT NULL")
    suspend fun getFavoriteRommIds(): List<Long>

    @Query("""
        SELECT * FROM games
        WHERE lastPlayed IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY lastPlayed DESC LIMIT :limit
    """)
    fun observeRecentlyPlayed(ownerUserId: Long?, limit: Int = 20): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE lastPlayed IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY lastPlayed DESC LIMIT :limit
    """)
    suspend fun getRecentlyPlayed(ownerUserId: Long?, limit: Int = 20): List<GameEntity>

    /**
     * Newly added games, narrowed to what can be launched only when [installedOnly] asks for it.
     * The predicate is a parameter rather than a second query so the two cannot drift; a caller
     * that always wanted playable games passes true.
     */
    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND lastPlayed IS NULL
        AND addedAt > :threshold
        AND (:installedOnly = 0
            OR localPath IS NOT NULL OR source = 'ANDROID_APP'
            OR (source = 'STEAM' AND steamLauncher IS NOT NULL AND steamLauncher != 'native'))
        ORDER BY addedAt DESC
        LIMIT :limit
    """)
    suspend fun getNewlyAdded(
        threshold: Instant,
        ownerUserId: Long?,
        installedOnly: Boolean,
        limit: Int = 20
    ): List<GameEntity>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getById(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE rommId = :rommId")
    suspend fun getByRommId(rommId: Long): GameEntity?

    @Query("SELECT id, rommId FROM games WHERE rommId IS NOT NULL")
    suspend fun getRommIdMappings(): List<RommIdMapping>

    @Query("SELECT * FROM games WHERE igdbId = :igdbId")
    suspend fun getByIgdbId(igdbId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId")
    suspend fun getAllByIgdbId(igdbId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getByIgdbIdAndPlatform(igdbId: Long, platformId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId AND platformId = :platformId")
    suspend fun getAllByIgdbIdAndPlatform(igdbId: Long, platformId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE steamAppId = :steamAppId")
    suspend fun getBySteamAppId(steamAppId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE steamAppId IS NOT NULL")
    suspend fun getAllWithSteamAppId(): List<GameEntity>

    @Query("UPDATE games SET igdbId = :igdbId WHERE id = :gameId")
    suspend fun updateIgdbId(gameId: Long, igdbId: Long)

    @Query("SELECT * FROM games WHERE source = 'STEAM' AND igdbId IS NULL AND steamAppId IS NOT NULL")
    suspend fun getUnresolvedSteamGames(): List<GameEntity>

    @Query("SELECT * FROM games WHERE localPath = :path")
    suspend fun getByPath(path: String): GameEntity?

    @Query("SELECT * FROM games WHERE sortTitle = :sortTitle AND platformId = :platformId LIMIT 1")
    suspend fun getBySortTitleAndPlatform(sortTitle: String, platformId: Long): GameEntity?

    @Query("""
        SELECT * FROM games
        WHERE searchTitle LIKE '%' || :query || '%'
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    fun search(query: String, ownerUserId: Long?): Flow<List<GameEntity>>

    @Upsert
    suspend fun insert(game: GameEntity): Long

    @Upsert
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET perGameSettingsEnabled = :enabled WHERE id = :gameId")
    suspend fun setPerGameSettingsEnabled(gameId: Long, enabled: Boolean)

    @Query("UPDATE games SET perGameControlsEnabled = :enabled WHERE id = :gameId")
    suspend fun setPerGameControlsEnabled(gameId: Long, enabled: Boolean)

    @Query("UPDATE games SET steamLauncher = :launcherPackage WHERE id = :gameId")
    suspend fun setSteamLauncher(gameId: Long, launcherPackage: String?)

    @Query("UPDATE games SET localPath = :path, source = :source, addedAt = :addedAt WHERE id = :gameId")
    suspend fun updateLocalPath(gameId: Long, path: String?, source: GameSource, addedAt: Instant = Instant.now())

    /**
     * Repoints a game at the same content in a new location, leaving source and addedAt intact.
     */
    @Query("UPDATE games SET localPath = :path WHERE id = :gameId")
    suspend fun relocateLocalPath(gameId: Long, path: String)

    @Query("DELETE FROM games WHERE id = :gameId")
    suspend fun delete(gameId: Long)

    @Query("DELETE FROM games WHERE platformId = :platformId")
    suspend fun deleteByPlatform(platformId: Long)

    @Query("SELECT * FROM games WHERE source IN (:sources) AND localPath IS NOT NULL")
    suspend fun getDownloadedBySources(sources: List<GameSource>): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId AND localPath IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getDownloadedGamesByPlatform(platformId: Long, ownerUserId: Long?): List<GameEntity>

    @Query("DELETE FROM games WHERE source IN (:sources)")
    suspend fun deleteBySources(sources: List<GameSource>)

    @Query("""
        SELECT COUNT(*) FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun countByPlatform(platformId: Long, ownerUserId: Long?): Int

    /**
     * Every platform's game count in one pass, carrying the same owner-scoped hidden predicate as
     * [countByPlatform] and [observeByPlatformList] so a count and the list it labels can never
     * disagree. Platforms with no games are absent rather than zero.
     */
    @Query("""
        SELECT platformId, COUNT(*) AS gameCount FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        GROUP BY platformId
    """)
    suspend fun countsByPlatform(ownerUserId: Long?): List<PlatformGameCount>

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId AND localPath IS NOT NULL")
    suspend fun countDownloadedByPlatform(platformId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM games
        WHERE platformId = :platformId AND isFavorite = 1
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun countFavoritesByPlatform(platformId: Long, ownerUserId: Long?): Int

    @Query("SELECT * FROM games WHERE platformId = :platformId AND localPath IS NOT NULL")
    suspend fun getDownloadedByPlatform(platformId: Long): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getByPlatform(platformId: Long, ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE platformId = :platformId
        AND EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY sortTitle ASC
    """)
    suspend fun getHiddenByPlatform(platformId: Long, ownerUserId: Long?): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun countAll(): Int

    @Query("SELECT * FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPath(): List<GameEntity>

    @Query("SELECT id FROM games WHERE localPath IS NOT NULL")
    suspend fun getGamesWithLocalPathIds(): List<Long>

    @Query("SELECT id FROM games WHERE localPath IS NOT NULL AND rommId IS NOT NULL")
    suspend fun getDownloadedRommGameIds(): List<Long>

    @Query("SELECT id FROM games")
    suspend fun getAllGameIds(): List<Long>

    @Query("SELECT id, coverPath, backgroundPath, cachedScreenshotPaths FROM games")
    suspend fun getAllImageCacheInfo(): List<GameImageCacheInfo>

    @Query("SELECT * FROM games WHERE rommId IS NOT NULL AND localPath IS NULL")
    suspend fun getGamesWithRommIdButNoPath(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = :source")
    suspend fun getBySource(source: GameSource): List<GameEntity>

    @Query("SELECT * FROM games WHERE source IN (:sources) AND platformId = :platformId")
    suspend fun getBySources(sources: List<GameSource>, platformId: Long): List<GameEntity>

    /**
     * Same set, minus the rows this account is masked off from. Duplicate cleanup and realignment
     * pick a survivor and delete the rest, which must never be decided from rows the account
     * cannot see.
     */
    @Query(
        """
        SELECT * FROM games
        WHERE source IN (:sources) AND platformId = :platformId
          AND id NOT IN (
              SELECT gameId FROM game_user_overlay
              WHERE ownerUserId = :ownerUserId AND isMember = 0
          )
        """
    )
    suspend fun getBySourcesForOwner(
        sources: List<GameSource>,
        platformId: Long,
        ownerUserId: Long?
    ): List<GameEntity>

    /**
     * Identity of every row this account still believes is a live server rom, across all
     * platforms. A projection rather than the entities: the caller compares a whole library
     * against a server id set and only needs the full row for the few that fail. Negative ids are
     * the marker for a rom that already left the library, so they are excluded rather than
     * re-examined.
     */
    @Query(
        """
        SELECT id, rommId, platformId FROM games
        WHERE source IN (:sources) AND rommId IS NOT NULL AND rommId > 0
          AND id NOT IN (
              SELECT gameId FROM game_user_overlay
              WHERE ownerUserId = :ownerUserId AND isMember = 0
          )
        """
    )
    suspend fun getServerBackedIdsForOwner(
        sources: List<GameSource>,
        ownerUserId: Long?
    ): List<ServerBackedGameRef>

    @Query(
        """
        SELECT * FROM games
        WHERE rommFileName = :fileName AND platformId = :platformId
          AND syncDirty = 0 AND rommId IS NOT NULL
          AND id NOT IN (
              SELECT gameId FROM game_user_overlay
              WHERE ownerUserId = :ownerUserId AND isMember = 0
          )
        """
    )
    suspend fun getCleanSyncedByFileNameAndPlatformForOwner(
        fileName: String,
        platformId: Long,
        ownerUserId: Long?
    ): List<GameEntity>

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

    @Query(
        "UPDATE games SET coverPath = :path, gradientColors = NULL, coverAspectRatio = NULL " +
            "WHERE id = :gameId"
    )
    suspend fun updateCoverPath(gameId: Long, path: String)

    @Query(
        "UPDATE games SET coverPath = NULL, gradientColors = NULL, coverAspectRatio = NULL " +
            "WHERE id = :gameId"
    )
    suspend fun clearCoverPath(gameId: Long)

    @Query(
        """
        UPDATE games SET
            originalCoverPath = CASE WHEN coverSetManually = 1 THEN originalCoverPath ELSE coverPath END,
            coverPath = :path,
            coverSetManually = 1,
            gradientColors = NULL,
            coverAspectRatio = NULL
        WHERE id = :gameId
        """
    )
    suspend fun setManualCover(gameId: Long, path: String)

    @Query(
        """
        UPDATE games SET
            coverPath = originalCoverPath,
            originalCoverPath = NULL,
            coverSetManually = 0,
            gradientColors = NULL,
            coverAspectRatio = NULL
        WHERE id = :gameId AND coverSetManually = 1
        """
    )
    suspend fun resetManualCover(gameId: Long)

    @Query("UPDATE games SET gradientColors = :json WHERE id = :gameId")
    suspend fun updateGradientColors(gameId: Long, json: String)

    @Query("""
        SELECT id, coverPath FROM games
        WHERE coverPath LIKE '/%' AND gradientColors IS NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY lastPlayed DESC
    """)
    suspend fun getLocalGamesNeedingGradients(ownerUserId: Long?): List<GradientExtractionCandidate>

    @Query("SELECT * FROM games WHERE coverPath LIKE 'http%'")
    suspend fun getGamesWithUncachedCovers(): List<GameEntity>

    @Query("UPDATE games SET boxBackPath = :path WHERE id = :gameId")
    suspend fun updateBoxBackPath(gameId: Long, path: String)

    /**
     * How many games can actually be drawn as a box. The renderer needs a front and a spine
     * together, so a library with covers but no spine art renders every game flat however the
     * setting is left.
     */
    @Query(
        """
        SELECT COUNT(*) FROM games
        WHERE boxSpinePath IS NOT NULL AND boxSpinePath != ''
          AND coverPath IS NOT NULL AND coverPath != ''
        """
    )
    suspend fun countBoxArtCapable(): Int

    @Query("UPDATE games SET boxSpinePath = :path WHERE id = :gameId")
    suspend fun updateBoxSpinePath(gameId: Long, path: String)

    @Query("SELECT * FROM games WHERE boxBackPath LIKE 'http%' OR boxSpinePath LIKE 'http%'")
    suspend fun getGamesWithUncachedBoxFaces(): List<GameEntity>

    @Query("SELECT * FROM games WHERE coverPath IS NULL OR coverPath = ''")
    suspend fun getGamesWithMissingCovers(): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games WHERE coverPath IS NOT NULL AND rommId IS NOT NULL")
    suspend fun countGamesWithCovers(): Int

    @Query("SELECT COUNT(*) FROM games WHERE coverPath LIKE '/%' AND rommId IS NOT NULL")
    suspend fun countGamesWithCachedCovers(): Int

    @Query("""
        SELECT DISTINCT regions FROM games
        WHERE regions IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getDistinctRegions(ownerUserId: Long?): List<String>

    @Query("""
        SELECT DISTINCT genre FROM games
        WHERE genre IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getDistinctGenres(ownerUserId: Long?): List<String>

    @Query("""
        SELECT DISTINCT franchises FROM games
        WHERE franchises IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getDistinctFranchises(ownerUserId: Long?): List<String>

    @Query("""
        SELECT DISTINCT gameModes FROM games
        WHERE gameModes IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getDistinctGameModes(ownerUserId: Long?): List<String>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND regions LIKE '%' || :region || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByRegion(region: String, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND genre = :genre
        ORDER BY sortTitle ASC
    """)
    fun observeByGenre(genre: String, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND franchises LIKE '%' || :franchise || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByFranchise(franchise: String, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND gameModes LIKE '%' || :gameMode || '%'
        ORDER BY sortTitle ASC
    """)
    fun observeByGameMode(gameMode: String, ownerUserId: Long?): Flow<List<GameEntity>>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND id != :excludeGameId
        AND collections LIKE '%' || :token || '%'
        ORDER BY releaseYear ASC
        LIMIT :limit
    """)
    suspend fun getRelatedByCollection(
        token: String,
        excludeGameId: Long,
        ownerUserId: Long?,
        limit: Int
    ): List<GameListItem>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND id != :excludeGameId
        AND franchises LIKE '%' || :token || '%'
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getRelatedByFranchise(
        token: String,
        excludeGameId: Long,
        ownerUserId: Long?,
        limit: Int
    ): List<GameListItem>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher, playCount, playTimeMinutes,
               lastPlayed, genre, gameModes, rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND id != :excludeGameId
        AND releaseYear BETWEEN :yearLo AND :yearHi
        AND (genres LIKE '%' || :token || '%' OR (genres IS NULL AND genre = :token))
        ORDER BY rating DESC
        LIMIT :limit
    """)
    suspend fun getRelatedByGenreAndYear(
        token: String,
        yearLo: Int,
        yearHi: Int,
        excludeGameId: Long,
        ownerUserId: Long?,
        limit: Int
    ): List<GameListItem>

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

    @Query("UPDATE games SET lastPlayedDiscId = :discId WHERE id = :gameId")
    suspend fun updateLastPlayedDisc(gameId: Long, discId: Long)

    @Query("UPDATE games SET m3uPath = :path WHERE id = :gameId")
    suspend fun updateM3uPath(gameId: Long, path: String?)

    @Query("UPDATE games SET activeVariantFileId = :fileId WHERE id = :gameId")
    suspend fun updateActiveVariantFileId(gameId: Long, fileId: Long?)

    @Query("UPDATE games SET lastPlayedFileId = :fileId WHERE id = :gameId")
    suspend fun updateLastPlayedFileId(gameId: Long, fileId: Long?)

    @Query("UPDATE games SET achievementsFetchedAt = :timestamp WHERE id = :gameId")
    suspend fun updateAchievementsFetchedAt(gameId: Long, timestamp: Long)

    @Query("SELECT achievementsFetchedAt FROM games WHERE id = :gameId")
    suspend fun getAchievementsFetchedAt(gameId: Long): Long?

    @Query("UPDATE games SET achievementsFetchedAt = NULL")
    suspend fun clearAllAchievementsFetchedAt()

    @Query("UPDATE games SET fileSizeBytes = :sizeBytes WHERE id = :gameId")
    suspend fun updateFileSize(gameId: Long, sizeBytes: Long)

    @Query("UPDATE games SET romHash = :hash WHERE id = :gameId")
    suspend fun updateRomHash(gameId: Long, hash: String)

    @Query("SELECT romHash FROM games WHERE id = :gameId")
    suspend fun getRomHash(gameId: Long): String?

    @Query("UPDATE games SET verifiedRaId = :raId, raIdVerified = 1 WHERE id = :gameId")
    suspend fun updateVerifiedRaId(gameId: Long, raId: Long?)

    @Query("UPDATE games SET titleId = :titleId WHERE id = :gameId AND titleIdLocked = 0")
    suspend fun updateTitleId(gameId: Long, titleId: String?)

    @Query("UPDATE games SET titleId = :titleId, titleIdLocked = :locked WHERE id = :gameId")
    suspend fun setTitleIdWithLock(gameId: Long, titleId: String?, locked: Boolean)

    @Query("UPDATE games SET titleId = :titleId, saveId = :saveId, titleIdLocked = :locked WHERE id = :gameId")
    suspend fun setTitleAndSaveIdWithLock(gameId: Long, titleId: String?, saveId: String?, locked: Boolean)

    @Query("UPDATE games SET saveId = :saveId WHERE id = :gameId")
    suspend fun setSaveId(gameId: Long, saveId: String?)

    @Query("SELECT saveId FROM games WHERE id = :gameId")
    suspend fun getSaveId(gameId: Long): String?

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

    @Query("""
        SELECT * FROM games
        WHERE playCount > 0
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY playTimeMinutes DESC
    """)
    suspend fun getPlayedGames(ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE (playCount = 0 OR playCount IS NULL)
        AND (completion = 0 OR completion IS NULL)
        AND localPath IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getUnplayedInstalledGames(ownerUserId: Long?): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE (playCount = 0 OR playCount IS NULL)
        AND (completion = 0 OR completion IS NULL)
        AND localPath IS NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
    """)
    suspend fun getUnplayedUndownloadedGames(ownerUserId: Long?): List<GameEntity>

    @Query("SELECT * FROM games WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<GameEntity>

    @Query("UPDATE games SET platformId = :newPlatformId, platformSlug = :newPlatformSlug WHERE platformId = :oldPlatformId")
    suspend fun migratePlatform(oldPlatformId: Long, newPlatformId: Long, newPlatformSlug: String)

    @Query("UPDATE games SET steamLauncher = :launcher, launcherSetManually = :setManually WHERE id = :gameId")
    suspend fun updateSteamLauncher(gameId: Long, launcher: String?, setManually: Boolean)

    @Query("SELECT * FROM games")
    suspend fun getAllGames(): List<GameEntity>

    @Query("UPDATE games SET coverPath = :coverPath, backgroundPath = :backgroundPath, cachedScreenshotPaths = :cachedScreenshotPaths, gradientColors = NULL, coverAspectRatio = NULL WHERE id = :gameId")
    suspend fun updateImagePaths(gameId: Long, coverPath: String?, backgroundPath: String?, cachedScreenshotPaths: String?)

    @Query("""
        SELECT * FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND (status IS NULL OR status NOT IN ('retired', 'never_playing'))
        ORDER BY RANDOM()
        LIMIT 1
    """)
    suspend fun getRandomGame(ownerUserId: Long?): GameEntity?

    @Query("""
        SELECT * FROM games
        WHERE searchTitle LIKE '%' || :query || '%'
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY
            CASE WHEN searchTitle LIKE :query || '%' THEN 0 ELSE 1 END,
            CASE WHEN rating IS NULL THEN 1 ELSE 0 END,
            rating DESC,
            sortTitle ASC
        LIMIT :limit
    """)
    fun searchForQuickMenu(query: String, ownerUserId: Long?, limit: Int = 10): Flow<List<GameEntity>>

    /**
     * Search narrowed to games that could be on this device, so the limit applies to candidates the
     * caller can actually use. Whether the file is still on disk is settled afterwards; everything
     * that was never downloaded is excluded here.
     */
    @Query("""
        SELECT * FROM games
        WHERE searchTitle LIKE '%' || :query || '%'
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND (
            source = 'ANDROID_APP'
            OR localPath IS NOT NULL
            OR (steamLauncher IS NOT NULL AND steamLauncher != 'native')
        )
        ORDER BY
            CASE WHEN searchTitle LIKE :query || '%' THEN 0 ELSE 1 END,
            CASE WHEN rating IS NULL THEN 1 ELSE 0 END,
            rating DESC,
            sortTitle ASC
        LIMIT :limit
    """)
    fun searchInstalled(query: String, ownerUserId: Long?, limit: Int): Flow<List<GameEntity>>

    @Query(
        "SELECT coverPath FROM games WHERE platformSlug = :platformSlug " +
            "AND coverSetManually = 1 AND coverPath IS NOT NULL"
    )
    suspend fun getManualCoverPathsForPlatform(platformSlug: String): List<String>

    /**
     * Forgets where cached art was kept for a platform, so the next sync fetches it again. Artwork
     * the user chose themselves is left alone; it is their work, not a cache.
     */
    @Query(
        """
        UPDATE games SET coverPath = NULL, backgroundPath = NULL, cachedScreenshotPaths = NULL
        WHERE platformSlug = :platformSlug AND coverSetManually = 0
        """
    )
    suspend fun clearCachedArtForPlatform(platformSlug: String)

    @Query("SELECT coverAspectRatio FROM games WHERE id = :gameId")
    suspend fun getCoverAspectRatio(gameId: Long): Float?

    @Query("UPDATE games SET coverAspectRatio = :ratio WHERE id = :gameId")
    suspend fun updateCoverAspectRatio(gameId: Long, ratio: Float)

    @Query("""
        SELECT id, title, rating FROM games
        WHERE NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        AND (status IS NULL OR status NOT IN ('retired', 'never_playing'))
    """)
    suspend fun getSearchCandidates(ownerUserId: Long?): List<SearchCandidate>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes,
               rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE coverPath LIKE '/%'
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        LIMIT 1
    """)
    suspend fun getFirstGameWithCover(ownerUserId: Long?): GameListItem?

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes,
               rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE coverPath LIKE '/%' AND lastPlayed IS NOT NULL AND localPath IS NOT NULL
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY lastPlayed DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedWithCovers(ownerUserId: Long?, limit: Int = 10): List<GameListItem>

    @Query("""
        SELECT id, platformId, platformSlug, title, sortTitle, localPath, source, coverPath, isFavorite,
               EXISTS(SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId)) AS isHidden,
               isMultiDisc, rommId, steamAppId, packageName, steamLauncher,
               playCount, playTimeMinutes, lastPlayed, genre, gameModes,
               rating, userRating, userDifficulty, releaseYear, addedAt
        FROM games
        WHERE coverPath LIKE '/%' AND lastPlayed IS NOT NULL
              AND localPath IS NOT NULL AND platformSlug IN (:platformSlugs)
        AND NOT EXISTS (SELECT 1 FROM user_roms_hidden h WHERE h.gameId = games.id AND (h.ownerUserId IS NULL OR h.ownerUserId IS :ownerUserId))
        ORDER BY lastPlayed DESC
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedOnPlatforms(
        platformSlugs: List<String>,
        ownerUserId: Long?,
        limit: Int = 10
    ): List<GameListItem>

    @Query("UPDATE games SET cheatsFetched = :fetched WHERE id = :gameId")
    suspend fun updateCheatsFetched(gameId: Long, fetched: Boolean)

    @Query("UPDATE games SET cheatsFetchedAt = :timestamp WHERE id = :gameId")
    suspend fun updateCheatsFetchedAt(gameId: Long, timestamp: Long)

    @Query("UPDATE games SET cheatsSelectedRegion = :region, cheatsSelectedVersion = :version WHERE id = :gameId")
    suspend fun updateCheatsSelectedVariant(gameId: Long, region: String, version: String)

    @Query("SELECT * FROM games WHERE cheatsFetched = 0 AND localPath IS NOT NULL LIMIT :limit")
    suspend fun getGamesWithoutCheats(limit: Int = 50): List<GameEntity>

    @Query("""
        SELECT * FROM games
        WHERE localPath IS NOT NULL
          AND (cheatsFetchedAt IS NULL OR cheatsFetchedAt < :staleThreshold)
        LIMIT :limit
    """)
    suspend fun getGamesNeedingCheatSync(staleThreshold: Long, limit: Int = 50): List<GameEntity>

    /**
     * Marks this account's rows on a platform as unseen, ahead of a sync pass that clears the
     * flag for everything the server still returns. Rows another account holds and this one is
     * masked off are left alone, or the pass would treat them as orphans of a library it was
     * never shown.
     */
    @Query(
        """
        UPDATE games SET syncDirty = 1
        WHERE platformId = :platformId AND source IN (:sources)
          AND id NOT IN (
              SELECT gameId FROM game_user_overlay
              WHERE ownerUserId = :ownerUserId AND isMember = 0
          )
        """
    )
    suspend fun markSyncDirtyForOwner(platformId: Long, sources: List<GameSource>, ownerUserId: Long?)

    @Query("UPDATE games SET syncDirty = 0 WHERE platformId = :platformId AND source IN (:sources)")
    suspend fun clearSyncDirty(platformId: Long, sources: List<GameSource>)

    @Query("SELECT * FROM games WHERE platformId = :platformId AND syncDirty = 1 AND source IN (:sources)")
    suspend fun getSyncDirtyGames(platformId: Long, sources: List<GameSource>): List<GameEntity>

    @Query(
        """
        UPDATE games SET syncDirty = 0
        WHERE id NOT IN (
            SELECT gameId FROM game_user_overlay
            WHERE ownerUserId = :ownerUserId AND isMember = 0
        )
        """
    )
    suspend fun clearAllSyncDirtyForOwner(ownerUserId: Long?)

    @Query("SELECT * FROM games WHERE platformId = :platformId AND localPath IS NOT NULL")
    suspend fun getGamesWithLocalPathByPlatform(platformId: Long): List<GameEntity>

    @Query("SELECT * FROM games WHERE platformId = :platformId AND rommId IS NOT NULL AND localPath IS NULL")
    suspend fun getGamesWithRommIdButNoPathByPlatform(platformId: Long): List<GameEntity>

    @Query("UPDATE games SET storeEnrichStatus = :status WHERE id = :gameId")
    suspend fun updateStoreEnrichStatus(gameId: Long, status: Int)

    @Query("UPDATE games SET storeEnrichStatus = 0 WHERE source = 'STEAM' AND storeEnrichStatus = 2")
    suspend fun resetFailedStoreEnrichment()

    @Query("SELECT * FROM games WHERE source = 'STEAM' AND steamAppId IS NOT NULL")
    suspend fun getAllSteamGamesForResolve(): List<GameEntity>

    @Query("SELECT * FROM games WHERE source = 'STEAM' AND localPath IS NOT NULL")
    suspend fun getInstalledSteamGames(): List<GameEntity>

}

data class SearchCandidate(
    val id: Long,
    val title: String,
    val rating: Float?
)

data class GradientExtractionCandidate(
    val id: Long,
    val coverPath: String?
)

data class GameLocalPathInfo(
    val id: Long,
    val platformId: Long,
    val platformSlug: String,
    val source: GameSource,
    val localPath: String?
)

data class GameStorageInfo(
    val id: Long,
    val platformId: Long,
    val localPath: String?
)

data class GameImageCacheInfo(
    val id: Long,
    val coverPath: String?,
    val backgroundPath: String?,
    val cachedScreenshotPaths: String?
)

data class RommIdMapping(
    val id: Long,
    val rommId: Long
)

data class ServerBackedGameRef(
    val id: Long,
    val rommId: Long,
    val platformId: Long
)

private const val ID_FETCH_BATCH_SIZE = 100

suspend fun GameDao.getByIdsChunked(ids: List<Long>): List<GameEntity> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(ID_FETCH_BATCH_SIZE).flatMap { getByIds(it) }
}
