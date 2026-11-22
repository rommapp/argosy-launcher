package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.domain.model.PinnedCollection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetGamesForPinnedCollectionUseCase @Inject constructor(
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    /**
     * The collection's games in its own order. A [limit] stops a regular collection from loading
     * games past it; a virtual one is ordered by title, so it loads whole and then takes [limit].
     */
    operator fun invoke(pinned: PinnedCollection, limit: Int = Int.MAX_VALUE): Flow<List<GameEntity>> =
        games(pinned, limit).map { it.take(limit) }

    private fun games(pinned: PinnedCollection, limit: Int): Flow<List<GameEntity>> {
        return when (pinned) {
            is PinnedCollection.Regular -> {
                collectionDao.observeGameIdsInCollection(pinned.collectionId).map { all ->
                    val ids = all.take(limit)
                    val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
                    ids.mapNotNull { byId[it] }
                }
            }
            is PinnedCollection.Virtual -> {
                if (pinned.type == CategoryType.SERIES) {
                    collectionDao.observeGameIdsByTypeAndName(CollectionType.SERIES, pinned.categoryName).map { ids ->
                        val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
                        ids.mapNotNull { byId[it] }.sortedBy { it.sortTitle }
                    }
                } else {
                    flow {
                        val owner = syncPreferencesRepository.getRommUserId()
                        emitAll(
                            gameDao.observeAllCategoryInfo(owner).map { items ->
                                val matchingIds = items.filter { info ->
                                    val field = when (pinned.type) {
                                        CategoryType.GENRE -> info.genre
                                        CategoryType.GAME_MODE -> info.gameModes
                                        CategoryType.SERIES -> null
                                    }
                                    field?.split(",")?.any {
                                        it.trim().equals(pinned.categoryName, ignoreCase = true)
                                    } == true
                                }.map { it.id }
                                gameDao.getByIdsChunked(matchingIds).sortedBy { it.sortTitle }
                            }
                        )
                    }
                }
            }
        }
    }
}
