package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

private const val SQL_PARAM_CHUNK = 500

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections ORDER BY name ASC")
    suspend fun getAllCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: Long): CollectionEntity?

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observeCollectionById(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collections WHERE rommId = :rommId")
    suspend fun getCollectionByRommId(rommId: Long): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionById(id: Long)

    @Query("DELETE FROM collections WHERE rommId IS NOT NULL")
    suspend fun deleteRomMSynced()

    @Query("DELETE FROM collections")
    suspend fun deleteAllCollections()

    @Query("""
        SELECT g.* FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        WHERE cg.collectionId = :collectionId
        ORDER BY cg.addedAt DESC
    """)
    fun observeGamesInCollection(collectionId: Long): Flow<List<GameEntity>>

    @Query("""
        SELECT g.* FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        WHERE cg.collectionId = :collectionId
        ORDER BY cg.addedAt DESC
    """)
    suspend fun getGamesInCollection(collectionId: Long): List<GameEntity>

    @Query("SELECT COUNT(*) FROM collection_games WHERE collectionId = :collectionId")
    fun observeGameCountInCollection(collectionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM collection_games WHERE collectionId = :collectionId")
    suspend fun getGameCountInCollection(collectionId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGameToCollection(collectionGame: CollectionGameEntity)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId = :gameId")
    suspend fun removeGameFromCollection(collectionId: Long, gameId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGamesToCollection(collectionGames: List<CollectionGameEntity>)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId IN (:gameIds)")
    suspend fun removeGamesFromCollection(collectionId: Long, gameIds: List<Long>)

    @Transaction
    suspend fun setCollectionGames(collectionId: Long, gameIds: Set<Long>) {
        val current = getGameIdsInCollection(collectionId).toSet()
        val added = gameIds - current
        if (added.isNotEmpty()) {
            addGamesToCollection(added.map { CollectionGameEntity(collectionId = collectionId, gameId = it) })
        }
        (current - gameIds).chunked(SQL_PARAM_CHUNK).forEach { removeGamesFromCollection(collectionId, it) }
    }

    @Transaction
    suspend fun replaceCollectionsOfType(type: CollectionType, gamesByName: Map<String, Set<Long>>) {
        val existing = getAllByType(type)
        val existingByName = existing.associateBy { it.name }
        for ((name, gameIds) in gamesByName) {
            val collectionId = existingByName[name]?.id
                ?: insertCollection(CollectionEntity(name = name, type = type, isUserCreated = false))
            setCollectionGames(collectionId, gameIds)
        }
        existing.filter { it.name !in gamesByName }.forEach { deleteCollection(it) }
    }

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun observeCollectionIdsForGame(gameId: Long): Flow<List<Long>>

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    suspend fun getCollectionIdsForGame(gameId: Long): List<Long>

    @Query("SELECT gameId FROM collection_games WHERE collectionId = :collectionId")
    suspend fun getGameIdsInCollection(collectionId: Long): List<Long>

    @Query("SELECT gameId FROM collection_games WHERE collectionId = :collectionId ORDER BY addedAt DESC")
    fun observeGameIdsInCollection(collectionId: Long): Flow<List<Long>>

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId")
    suspend fun clearCollectionGames(collectionId: Long)

    @Query("""
        SELECT $RESOLVED_ART_SQL FROM collection_games cg
        INNER JOIN game_art art ON art.gameId = cg.gameId AND art.slot = 'COVER'
        WHERE cg.collectionId = :collectionId AND $RESOLVED_ART_SQL IS NOT NULL
        ORDER BY cg.addedAt DESC
        LIMIT 4
    """)
    suspend fun getCollectionCoverPaths(collectionId: Long): List<String>

    @Query("""
        SELECT COUNT(*) FROM collection_games cg
        INNER JOIN games g ON cg.gameId = g.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE cg.collectionId = :collectionId AND p.syncEnabled = 1
    """)
    fun observeLocalGameCountInCollection(collectionId: Long): Flow<Int>

    @Query("""
        SELECT cg.collectionId AS collectionId,
            COUNT(*) AS gameCount,
            SUM($INSTALLED_SQL) AS installedCount,
            SUM(g.earnedAchievementCount) AS earnedAchievements,
            SUM(g.achievementCount) AS totalAchievements,
            SUM(g.playTimeMinutes) AS playTimeMinutes
        FROM collection_games cg
        INNER JOIN games g ON cg.gameId = g.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE p.syncEnabled = 1
        GROUP BY cg.collectionId
    """)
    fun observeLocalCollectionStats(): Flow<List<CollectionStats>>

    @Query("""
        SELECT c.id AS collectionId, cg.gameId AS gameId
        FROM collections c
        INNER JOIN collection_games cg ON cg.rowid IN (
            SELECT cg2.rowid FROM collection_games cg2
            INNER JOIN games g2 ON g2.id = cg2.gameId
            INNER JOIN platforms p2 ON p2.id = g2.platformId
            WHERE cg2.collectionId = c.id
                AND p2.syncEnabled = 1
            ORDER BY cg2.addedAt DESC
            LIMIT :perCollection
        )
        ORDER BY c.id ASC, cg.addedAt DESC
    """)
    fun observeLocalCoverGameIds(perCollection: Int): Flow<List<CollectionCoverGame>>

    @Query("SELECT * FROM collections WHERE type = :type ORDER BY name ASC")
    fun observeByType(type: CollectionType): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE type IN (:types) ORDER BY name ASC")
    fun observeByTypes(types: List<CollectionType>): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE type = :type ORDER BY name ASC")
    suspend fun getAllByType(type: CollectionType): List<CollectionEntity>

    @Query("SELECT COUNT(*) FROM collections WHERE type = :type")
    suspend fun countByType(type: CollectionType): Int

    @Query("SELECT * FROM collections WHERE type = :type AND name = :name LIMIT 1")
    suspend fun getByTypeAndName(type: CollectionType, name: String): CollectionEntity?

    @Query("""
        SELECT g.* FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        INNER JOIN collections c ON cg.collectionId = c.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE c.type = :type AND c.name = :name AND p.syncEnabled = 1
        ORDER BY g.sortTitle ASC
    """)
    fun observeGamesByTypeAndName(type: CollectionType, name: String): Flow<List<GameEntity>>

    @Query("""
        SELECT g.id FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        INNER JOIN collections c ON cg.collectionId = c.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE c.type = :type AND c.name = :name AND p.syncEnabled = 1
        ORDER BY g.sortTitle ASC
    """)
    fun observeGameIdsByTypeAndName(type: CollectionType, name: String): Flow<List<Long>>

    @Query("""
        SELECT DISTINCT g.id FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        INNER JOIN collections c ON cg.collectionId = c.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE c.type = :type AND c.name IN (:names) AND p.syncEnabled = 1
    """)
    fun observeGameIdsByTypeAndNames(type: CollectionType, names: List<String>): Flow<List<Long>>

    @Query("""
        SELECT DISTINCT g.id FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        INNER JOIN collections c ON cg.collectionId = c.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE c.type = :type AND c.name = :name AND p.syncEnabled = 1
    """)
    suspend fun virtualGameIds(type: CollectionType, name: String): List<Long>

    @Query("""
        SELECT DISTINCT c.name FROM collections c
        INNER JOIN collection_games cg ON c.id = cg.collectionId
        INNER JOIN games g ON cg.gameId = g.id
        INNER JOIN platforms p ON g.platformId = p.id
        WHERE c.type = :type AND p.syncEnabled = 1
        ORDER BY c.name ASC
    """)
    suspend fun getNamesWithGamesByType(type: CollectionType): List<String>

    @Query("""
        SELECT DISTINCT c.name FROM collections c
        INNER JOIN collection_games cg ON c.id = cg.collectionId
        WHERE c.type = :type AND cg.gameId IN (:gameIds)
    """)
    suspend fun getNamesByTypeForGames(type: CollectionType, gameIds: List<Long>): List<String>
}

data class CollectionStats(
    val collectionId: Long,
    val gameCount: Int,
    val installedCount: Int,
    val earnedAchievements: Int,
    val totalAchievements: Int,
    val playTimeMinutes: Int
)

data class CollectionCoverGame(val collectionId: Long, val gameId: Long)
