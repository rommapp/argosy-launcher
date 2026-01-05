package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.domain.model.PinnedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPinnedCollectionsUseCase @Inject constructor(
    private val pinnedCollectionDao: PinnedCollectionDao,
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao
) {
    operator fun invoke(): Flow<List<PinnedCollection>> {
        return pinnedCollectionDao.observeAllPinned().map { pinnedEntities ->
            pinnedEntities.mapNotNull { entity ->
                when {
                    entity.collectionId != null -> {
                        val collection = collectionDao.getCollectionById(entity.collectionId)
                        if (collection != null) {
                            val gameCount = collectionDao.getGameCountInCollection(entity.collectionId)
                            PinnedCollection.Regular(
                                id = entity.id,
                                collectionId = entity.collectionId,
                                displayName = collection.name,
                                displayOrder = entity.displayOrder,
                                gameCount = gameCount
                            )
                        } else {
                            pinnedCollectionDao.deleteById(entity.id)
                            null
                        }
                    }
                    entity.virtualType != null && entity.virtualName != null -> {
                        val type = CategoryType.entries.find {
                            it.name.equals(entity.virtualType, ignoreCase = true)
                        } ?: return@mapNotNull null

                        val gameCount = countGamesInCategory(type, entity.virtualName)
                        PinnedCollection.Virtual(
                            id = entity.id,
                            type = type,
                            categoryName = entity.virtualName,
                            displayOrder = entity.displayOrder,
                            gameCount = gameCount
                        )
                    }
                    else -> null
                }
            }
        }
    }

    private suspend fun countGamesInCategory(type: CategoryType, name: String): Int {
        val games = gameDao.getAllGames()
        return games.count { game ->
            when (type) {
                CategoryType.GENRE -> {
                    game.genre?.split(",")?.any { it.trim().equals(name, ignoreCase = true) } == true
                }
                CategoryType.GAME_MODE -> {
                    game.gameModes?.split(",")?.any { it.trim().equals(name, ignoreCase = true) } == true
                }
            }
        }
    }
}
