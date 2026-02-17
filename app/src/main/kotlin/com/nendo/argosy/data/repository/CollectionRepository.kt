package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao
) {
    fun observeCollectionById(id: Long): Flow<CollectionEntity?> =
        collectionDao.observeCollectionById(id)

    fun observeGamesInCollection(collectionId: Long): Flow<List<GameEntity>> =
        collectionDao.observeGamesInCollection(collectionId)

    suspend fun getAllCollections(): List<CollectionEntity> =
        collectionDao.getAllCollections()

    suspend fun getAllByType(type: CollectionType): List<CollectionEntity> =
        collectionDao.getAllByType(type)

    suspend fun getGamesInCollection(collectionId: Long): List<GameEntity> =
        collectionDao.getGamesInCollection(collectionId)

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
