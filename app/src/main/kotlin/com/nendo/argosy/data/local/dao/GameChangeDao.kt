package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import com.nendo.argosy.data.local.entity.GameCategoryInfo
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.local.entity.GameRegionInfo
import kotlinx.coroutines.flow.Flow

private const val TALLY_COLUMNS =
    "COUNT(*) AS rowCount, IFNULL(SUM(id), 0) AS idSum, IFNULL(SUM(id * id), 0) AS idSquareSum"

private const val CHANGED_SINCE = "FROM games WHERE changeSeq > :seq"

/**
 * Size and id checksums of the rows a scope admits. Equal tallies prove two id sets equal whenever
 * each holds at most two ids the other lacks.
 */
data class GameScopeTally(
    val rowCount: Int,
    val idSum: Long,
    val idSquareSum: Long
)

data class GameListChange(
    @Embedded val item: GameListItem,
    val inScope: Boolean
)

data class GameRegionChange(
    val id: Long,
    val regions: String?,
    val inScope: Boolean
)

data class GameCategoryChange(
    val id: Long,
    val genre: String?,
    val gameModes: String?,
    val inScope: Boolean
)

data class GameListChanges(
    val rows: List<GameListChange>,
    val tally: GameScopeTally,
    val maxSeq: Long
)

data class GameRegionChanges(
    val rows: List<GameRegionChange>,
    val tally: GameScopeTally,
    val maxSeq: Long
)

data class GameCategoryChanges(
    val rows: List<GameCategoryChange>,
    val tally: GameScopeTally,
    val maxSeq: Long
)

/**
 * Reads `games` rows stamped after a `changeSeq`. Every changed-rows query computes `inScope` from
 * the same predicate constant its scope's full query filters on.
 */
@Dao
interface GameChangeDao {

    @Query(
        "SELECT (SELECT IFNULL(MAX(changeSeq), 0) FROM games) + " +
            "(SELECT COUNT(*) FROM user_roms_hidden) + (SELECT COUNT(*) FROM platforms)"
    )
    fun observeScopeTick(): Flow<Long>

    @Query("SELECT IFNULL(MAX(changeSeq), 0) FROM games")
    suspend fun maxChangeSeq(): Long

    @Query("SELECT id FROM games")
    suspend fun getAllIds(): List<Long>

    @Query("SELECT $GAME_LIST_COLUMNS, CASE WHEN $ALL_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope $CHANGED_SINCE")
    suspend fun allListChangedSince(seq: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $ALL_LIST_SCOPE_SQL")
    suspend fun allListTally(ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun allListChanges(seq: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(allListChangedSince(seq, ownerUserId), allListTally(ownerUserId), maxChangeSeq())

    @Query("SELECT $GAME_LIST_COLUMNS, CASE WHEN $PLAYABLE_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope $CHANGED_SINCE")
    suspend fun playableListChangedSince(seq: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $PLAYABLE_LIST_SCOPE_SQL")
    suspend fun playableListTally(ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun playableListChanges(seq: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(playableListChangedSince(seq, ownerUserId), playableListTally(ownerUserId), maxChangeSeq())

    @Query("SELECT $GAME_LIST_COLUMNS, CASE WHEN $FAVORITES_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope $CHANGED_SINCE")
    suspend fun favoritesListChangedSince(seq: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $FAVORITES_LIST_SCOPE_SQL")
    suspend fun favoritesListTally(ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun favoritesListChanges(seq: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(favoritesListChangedSince(seq, ownerUserId), favoritesListTally(ownerUserId), maxChangeSeq())

    @Query("SELECT $GAME_LIST_COLUMNS, CASE WHEN $PLATFORM_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope $CHANGED_SINCE")
    suspend fun platformListChangedSince(seq: Long, platformId: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $PLATFORM_LIST_SCOPE_SQL")
    suspend fun platformListTally(platformId: Long, ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun platformListChanges(seq: Long, platformId: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(
            platformListChangedSince(seq, platformId, ownerUserId),
            platformListTally(platformId, ownerUserId),
            maxChangeSeq()
        )

    @Query(
        "SELECT $GAME_LIST_COLUMNS, CASE WHEN $PLATFORM_PLAYABLE_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope " +
            CHANGED_SINCE
    )
    suspend fun platformPlayableListChangedSince(seq: Long, platformId: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $PLATFORM_PLAYABLE_LIST_SCOPE_SQL")
    suspend fun platformPlayableListTally(platformId: Long, ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun platformPlayableListChanges(seq: Long, platformId: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(
            platformPlayableListChangedSince(seq, platformId, ownerUserId),
            platformPlayableListTally(platformId, ownerUserId),
            maxChangeSeq()
        )

    @Query(
        "SELECT $GAME_LIST_COLUMNS, CASE WHEN $PLATFORM_FAVORITES_LIST_SCOPE_SQL THEN 1 ELSE 0 END AS inScope " +
            CHANGED_SINCE
    )
    suspend fun platformFavoritesListChangedSince(seq: Long, platformId: Long, ownerUserId: Long?): List<GameListChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $PLATFORM_FAVORITES_LIST_SCOPE_SQL")
    suspend fun platformFavoritesListTally(platformId: Long, ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun platformFavoritesListChanges(seq: Long, platformId: Long, ownerUserId: Long?): GameListChanges =
        GameListChanges(
            platformFavoritesListChangedSince(seq, platformId, ownerUserId),
            platformFavoritesListTally(platformId, ownerUserId),
            maxChangeSeq()
        )

    @Query("SELECT id, regions FROM games WHERE $REGION_SCOPE_SQL")
    suspend fun getRegionInfo(): List<GameRegionInfo>

    @Query("SELECT id, regions, CASE WHEN $REGION_SCOPE_SQL THEN 1 ELSE 0 END AS inScope $CHANGED_SINCE")
    suspend fun regionChangedSince(seq: Long): List<GameRegionChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $REGION_SCOPE_SQL")
    suspend fun regionTally(): GameScopeTally

    @Transaction
    suspend fun regionChanges(seq: Long): GameRegionChanges =
        GameRegionChanges(regionChangedSince(seq), regionTally(), maxChangeSeq())

    @Query("SELECT id, genre, gameModes FROM games WHERE $CATEGORY_SCOPE_SQL")
    suspend fun getCategoryInfo(ownerUserId: Long?): List<GameCategoryInfo>

    @Query(
        "SELECT id, genre, gameModes, CASE WHEN $CATEGORY_SCOPE_SQL THEN 1 ELSE 0 END AS inScope " +
            CHANGED_SINCE
    )
    suspend fun categoryChangedSince(seq: Long, ownerUserId: Long?): List<GameCategoryChange>

    @Query("SELECT $TALLY_COLUMNS FROM games WHERE $CATEGORY_SCOPE_SQL")
    suspend fun categoryTally(ownerUserId: Long?): GameScopeTally

    @Transaction
    suspend fun categoryChanges(seq: Long, ownerUserId: Long?): GameCategoryChanges =
        GameCategoryChanges(categoryChangedSince(seq, ownerUserId), categoryTally(ownerUserId), maxChangeSeq())
}
