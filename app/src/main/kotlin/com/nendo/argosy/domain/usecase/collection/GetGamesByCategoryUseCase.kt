package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

enum class CategoryType {
    GENRE,
    GAME_MODE
}

class GetGamesByCategoryUseCase @Inject constructor(
    private val gameDao: GameDao
) {
    operator fun invoke(categoryType: CategoryType, categoryName: String): Flow<List<GameEntity>> {
        return gameDao.observeAll().map { games ->
            games.filter { game ->
                when (categoryType) {
                    CategoryType.GENRE -> {
                        game.genre?.split(",")?.any { it.trim().equals(categoryName, ignoreCase = true) } == true
                    }
                    CategoryType.GAME_MODE -> {
                        game.gameModes?.split(",")?.any { it.trim().equals(categoryName, ignoreCase = true) } == true
                    }
                }
            }.sortedBy { it.sortTitle }
        }
    }
}
