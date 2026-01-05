package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import javax.inject.Inject

class UnpinCollectionUseCase @Inject constructor(
    private val pinnedCollectionDao: PinnedCollectionDao
) {
    suspend fun unpinRegular(collectionId: Long) {
        pinnedCollectionDao.deleteByCollectionId(collectionId)
    }

    suspend fun unpinVirtual(type: CategoryType, name: String) {
        pinnedCollectionDao.deleteVirtual(type.name.lowercase(), name)
    }

    suspend fun unpinById(pinId: Long) {
        pinnedCollectionDao.deleteById(pinId)
    }
}
