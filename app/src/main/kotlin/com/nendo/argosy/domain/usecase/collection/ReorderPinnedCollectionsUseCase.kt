package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.PinnedCollectionDao
import com.nendo.argosy.domain.model.PinnedCollection
import javax.inject.Inject

class ReorderPinnedCollectionsUseCase @Inject constructor(
    private val pinnedCollectionDao: PinnedCollectionDao
) {
    suspend operator fun invoke(orderedPins: List<PinnedCollection>) {
        orderedPins.forEachIndexed { index, pin ->
            pinnedCollectionDao.updateOrder(pin.id, index)
        }
    }
}
