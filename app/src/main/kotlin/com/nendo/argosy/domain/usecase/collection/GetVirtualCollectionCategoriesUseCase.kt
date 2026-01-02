package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.GameDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CategoryWithCount(
    val name: String,
    val gameCount: Int
)

class GetVirtualCollectionCategoriesUseCase @Inject constructor(
    private val gameDao: GameDao
) {
    fun getGenres(): Flow<List<CategoryWithCount>> {
        return gameDao.observeAll().map { games ->
            games
                .mapNotNull { it.genre }
                .flatMap { it.split(",").map { g -> g.trim() } }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .map { (name, count) -> CategoryWithCount(name, count) }
                .sortedBy { it.name }
        }
    }

    fun getGameModes(): Flow<List<CategoryWithCount>> {
        return gameDao.observeAll().map { games ->
            games
                .mapNotNull { it.gameModes }
                .flatMap { it.split(",").map { m -> m.trim() } }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .map { (name, count) -> CategoryWithCount(name, count) }
                .sortedBy { it.name }
        }
    }
}
