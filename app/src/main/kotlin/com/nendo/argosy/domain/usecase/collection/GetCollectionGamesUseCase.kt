package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCollectionGamesUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    operator fun invoke(collectionId: Long): Flow<List<GameEntity>> {
        return collectionDao.observeGamesInCollection(collectionId)
    }
}
