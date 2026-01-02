package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val description: String?,
    val gameCount: Int,
    val coverPaths: List<String>,
    val isUserCreated: Boolean,
    val rommId: Long?
)

class GetCollectionsUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    operator fun invoke(): Flow<List<CollectionWithCount>> {
        return collectionDao.observeAllCollections().map { collections ->
            collections.map { collection ->
                val count = collectionDao.getGameCountInCollection(collection.id)
                val covers = collectionDao.getCollectionCoverPaths(collection.id)
                CollectionWithCount(
                    id = collection.id,
                    name = collection.name,
                    description = collection.description,
                    gameCount = count,
                    coverPaths = covers,
                    isUserCreated = collection.isUserCreated,
                    rommId = collection.rommId
                )
            }
        }
    }
}
