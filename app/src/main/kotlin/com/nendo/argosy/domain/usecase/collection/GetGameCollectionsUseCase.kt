package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGameCollectionsUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    operator fun invoke(gameId: Long): Flow<List<Long>> {
        return collectionDao.observeCollectionIdsForGame(gameId)
    }
}
