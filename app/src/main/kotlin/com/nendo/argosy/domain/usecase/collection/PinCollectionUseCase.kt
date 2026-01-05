package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.data.local.entity.PinnedCollectionEntity
import javax.inject.Inject

class PinCollectionUseCase @Inject constructor(
    private val pinnedCollectionDao: PinnedCollectionDao
) {
    suspend fun pinRegular(collectionId: Long): Long {
        val existing = pinnedCollectionDao.getPinnedByCollectionId(collectionId)
        if (existing != null) return existing.id

        val maxOrder = pinnedCollectionDao.getMaxDisplayOrder() ?: -1
        val entity = PinnedCollectionEntity(
            collectionId = collectionId,
            displayOrder = maxOrder + 1
        )
        return pinnedCollectionDao.insert(entity)
    }

    suspend fun pinVirtual(type: CategoryType, name: String): Long {
        val typeString = type.name.lowercase()
        val existing = pinnedCollectionDao.getPinnedVirtual(typeString, name)
        if (existing != null) return existing.id

        val maxOrder = pinnedCollectionDao.getMaxDisplayOrder() ?: -1
        val entity = PinnedCollectionEntity(
            virtualType = typeString,
            virtualName = name,
            displayOrder = maxOrder + 1
        )
        return pinnedCollectionDao.insert(entity)
    }
}
