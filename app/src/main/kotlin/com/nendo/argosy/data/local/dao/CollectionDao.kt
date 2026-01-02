package com.nendo.argosy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun observeCollectionIdsForGame(gameId: Long): Flow<List<Long>>

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    suspend fun getCollectionIdsForGame(gameId: Long): List<Long>

    @Query("SELECT gameId FROM collection_games WHERE collectionId = :collectionId")
    suspend fun getGameIdsInCollection(collectionId: Long): List<Long>

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId")
    suspend fun clearCollectionGames(collectionId: Long)

    @Query("""
        SELECT g.coverPath FROM games g
        INNER JOIN collection_games cg ON g.id = cg.gameId
        WHERE cg.collectionId = :collectionId AND g.coverPath IS NOT NULL
        ORDER BY cg.addedAt DESC
        LIMIT 4
    """)
    suspend fun getCollectionCoverPaths(collectionId: Long): List<String>
}
