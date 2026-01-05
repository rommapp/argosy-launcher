package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.CollectionDao
import com.nendo.argosy.data.local.entity.CollectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

data class CategoryWithCount(
    val name: String,
    val gameCount: Int,
    val coverPaths: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class GetVirtualCollectionCategoriesUseCase @Inject constructor(
    private val collectionDao: CollectionDao
) {
    fun getGenres(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GENRE)
    }

    fun getGameModes(): Flow<List<CategoryWithCount>> {
        return getCategoriesByType(CollectionType.GAME_MODE)
    }

    private fun getCategoriesByType(type: CollectionType): Flow<List<CategoryWithCount>> {
        return collectionDao.observeByType(type)
            .flatMapLatest { collections ->
                if (collections.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val flows = collections.map { collection ->
                        combine(
                            collectionDao.observeLocalGameCountInCollection(collection.id),
                            collectionDao.observeLocalCollectionCoverPaths(collection.id)
                        ) { count, covers ->
                            CategoryWithCount(
                                name = collection.name,
                                gameCount = count,
                                coverPaths = covers
                            )
                        }
                    }
                    combine(flows) { it.toList().filter { cat -> cat.gameCount > 0 } }
                }
            }
            .map { categories -> categories.sortedBy { it.name } }
            .onStart { emit(emptyList()) }
            .flowOn(Dispatchers.IO)
    }
}
