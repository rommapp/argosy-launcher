package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.model.PinnedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetGamesForPinnedCollectionUseCase @Inject constructor(
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao
) {
    operator fun invoke(pinned: PinnedCollection): Flow<List<GameEntity>> {
        return when (pinned) {
            is PinnedCollection.Regular -> {
                collectionDao.observeGamesInCollection(pinned.collectionId)
            }
            is PinnedCollection.Virtual -> {
                gameDao.observeAll().map { games ->
                    games.filter { game ->
                        when (pinned.type) {
                            CategoryType.GENRE -> {
                                game.genre?.split(",")?.any {
                                    it.trim().equals(pinned.categoryName, ignoreCase = true)
                                } == true
                            }
                            CategoryType.GAME_MODE -> {
                                game.gameModes?.split(",")?.any {
                                    it.trim().equals(pinned.categoryName, ignoreCase = true)
                                } == true
                            }
                        }
                    }.sortedBy { it.sortTitle }
                }
            }
        }
    }
}
