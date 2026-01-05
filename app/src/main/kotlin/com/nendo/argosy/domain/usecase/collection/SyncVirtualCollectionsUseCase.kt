package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.CollectionType
import javax.inject.Inject

class SyncVirtualCollectionsUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val collectionDao: CollectionDao
) {
    suspend operator fun invoke() {
        val games = gameDao.getSyncEnabledGamesForCategories()

        val genreMap = mutableMapOf<String, MutableList<Long>>()
        val modeMap = mutableMapOf<String, MutableList<Long>>()

        games.forEach { game ->
            game.genre?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { genre ->
                genreMap.getOrPut(genre) { mutableListOf() }.add(game.id)
            }
            game.gameModes?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { mode ->
                modeMap.getOrPut(mode) { mutableListOf() }.add(game.id)
            }
        }

        syncCollectionType(CollectionType.GENRE, genreMap)
        syncCollectionType(CollectionType.GAME_MODE, modeMap)
    }

    private suspend fun syncCollectionType(
        type: CollectionType,
        categoryMap: Map<String, List<Long>>
    ) {
        val existingCollections = collectionDao.getAllByType(type)
        val existingByName = existingCollections.associateBy { it.name }

        for ((name, gameIds) in categoryMap) {
            val existing = existingByName[name]
            val collectionId = if (existing != null) {
                existing.id
            } else {
                collectionDao.insertCollection(
                    CollectionEntity(
                        name = name,
                        type = type,
                        isUserCreated = false
                    )
                )
            }

            val currentGameIds = collectionDao.getGameIdsInCollection(collectionId).toSet()
            val newGameIds = gameIds.toSet()

            for (gameId in currentGameIds - newGameIds) {
                collectionDao.removeGameFromCollection(collectionId, gameId)
            }

            for (gameId in newGameIds - currentGameIds) {
                collectionDao.addGameToCollection(CollectionGameEntity(collectionId, gameId))
            }
        }

        for (existing in existingCollections) {
            if (existing.name !in categoryMap) {
                collectionDao.deleteCollection(existing)
            }
        }
    }
}
