package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import javax.inject.Inject

class IsPinnedUseCase @Inject constructor(
    private val pinnedCollectionDao: PinnedCollectionDao
) {
    suspend fun isRegularPinned(collectionId: Long): Boolean {
        return pinnedCollectionDao.getPinnedByCollectionId(collectionId) != null
    }

    suspend fun isVirtualPinned(type: CategoryType, name: String): Boolean {
        return pinnedCollectionDao.getPinnedVirtual(type.name.lowercase(), name) != null
    }
}
