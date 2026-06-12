package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

enum class CategoryType {
    GENRE,
    GAME_MODE,
    SERIES
}

class GetGamesByCategoryUseCase @Inject constructor(
    private val collectionDao: CollectionDao,
    private val gameDao: GameDao
) {
    operator fun invoke(categoryType: CategoryType, categoryName: String): Flow<List<GameEntity>> {
        val collectionType = when (categoryType) {
            CategoryType.GENRE -> CollectionType.GENRE
            CategoryType.GAME_MODE -> CollectionType.GAME_MODE
            CategoryType.SERIES -> CollectionType.SERIES
        }
        return collectionDao.observeGameIdsByTypeAndName(collectionType, categoryName)
            .map { ids ->
                val byId = gameDao.getByIdsChunked(ids).associateBy { it.id }
                ids.mapNotNull { byId[it] }
            }
            .onStart { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }
}
