package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao
) {
    fun observeCollectionById(id: Long): Flow<CollectionEntity?> =
        collectionDao.observeCollectionById(id)

    fun observeGamesInCollection(collectionId: Long): Flow<List<GameEntity>> =
        collectionDao.observeGameIdsInCollection(collectionId).map { ids ->
            val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }

    suspend fun getAllCollections(): List<CollectionEntity> =
        collectionDao.getAllCollections()

    suspend fun getAllByType(type: CollectionType): List<CollectionEntity> =
        collectionDao.getAllByType(type)

    fun observeGameIdsByTypeAndNames(type: CollectionType, names: List<String>): Flow<List<Long>> =
        collectionDao.observeGameIdsByTypeAndNames(type, names)

    suspend fun getNamesWithGamesByType(type: CollectionType): List<String> =
        collectionDao.getNamesWithGamesByType(type)

    suspend fun getGamesInCollection(collectionId: Long): List<GameEntity> {
        val ids = collectionDao.getGameIdsInCollection(collectionId)
        val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    suspend fun getCollectionIdsForGame(gameId: Long): List<Long> =
        collectionDao.getCollectionIdsForGame(gameId)

    suspend fun getCollectionCoverPaths(collectionId: Long): List<String> =
        collectionDao.getCollectionCoverPaths(collectionId)

    suspend fun insertCollection(collection: CollectionEntity): Long =
        collectionDao.insertCollection(collection)

    suspend fun addGameToCollection(collectionGame: CollectionGameEntity) =
        collectionDao.addGameToCollection(collectionGame)

    suspend fun removeGameFromCollection(collectionId: Long, gameId: Long) =
        collectionDao.removeGameFromCollection(collectionId, gameId)
}
