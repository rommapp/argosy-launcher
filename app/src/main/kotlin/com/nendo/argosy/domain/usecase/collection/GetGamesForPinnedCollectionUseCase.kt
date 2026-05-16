package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.model.PinnedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val FETCH_BATCH_SIZE = 100

class GetGamesForPinnedCollectionUseCase @Inject constructor(
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao
) {
    operator fun invoke(pinned: PinnedCollection): Flow<List<GameEntity>> {
        return when (pinned) {
            is PinnedCollection.Regular -> {
                collectionDao.observeGameIdsInCollection(pinned.collectionId).map { ids ->
                    val games = fetchByIds(ids)
                    val byId = games.associateBy { it.id }
                    ids.mapNotNull { byId[it] }
                }
            }
            is PinnedCollection.Virtual -> {
                gameDao.observeAllCategoryInfo().map { items ->
                    val matchingIds = items.filter { info ->
                        val field = when (pinned.type) {
                            CategoryType.GENRE -> info.genre
                            CategoryType.GAME_MODE -> info.gameModes
                        }
                        field?.split(",")?.any {
                            it.trim().equals(pinned.categoryName, ignoreCase = true)
                        } == true
                    }.map { it.id }
                    fetchByIds(matchingIds).sortedBy { it.sortTitle }
                }
            }
        }
    }

    private suspend fun fetchByIds(ids: List<Long>): List<GameEntity> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(FETCH_BATCH_SIZE).flatMap { gameDao.getByIds(it) }
    }
}
