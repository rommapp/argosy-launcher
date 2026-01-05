package com.nendo.argosy.domain.usecase.collection

import android.util.Log
import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.CollectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
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
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<CollectionWithCount>> {
        return collectionDao.observeByType(CollectionType.REGULAR)
            .onStart { Log.d("GetCollectionsUC", "observeByType(REGULAR): starting") }
            .flatMapLatest { collections ->
                val filtered = collections.filter {
                    it.name.isNotBlank() && it.name.lowercase() != "favorites"
                }
                Log.d("GetCollectionsUC", "flatMapLatest: ${filtered.size} collections")

                if (filtered.isEmpty()) {
                    return@flatMapLatest flowOf(emptyList())
                }

                val collectionFlows = filtered.map { collection ->
                    combine(
                        collectionDao.observeLocalGameCountInCollection(collection.id)
                            .onStart { emit(0) },
                        collectionDao.observeLocalCollectionCoverPaths(collection.id)
                            .onStart { emit(emptyList()) }
                    ) { count, covers ->
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

                combine(collectionFlows) { it.toList() }
            }
            .onStart { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }
}
