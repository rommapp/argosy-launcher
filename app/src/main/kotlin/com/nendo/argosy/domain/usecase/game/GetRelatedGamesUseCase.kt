package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import javax.inject.Inject

private const val MIN_RESULTS = 5
private const val MAX_RESULTS = 15
private const val PER_QUERY_LIMIT = 15
private const val YEAR_WINDOW = 3

class GetRelatedGamesUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val syncPreferencesRepository: SyncPreferencesRepository
) {
    suspend operator fun invoke(game: GameEntity): List<GameListItem> {
        val results = LinkedHashMap<Long, GameListItem>()
        val ownerUserId = syncPreferencesRepository.getRommUserId()

        tokensOf(game.collections).forEach { token ->
            gameDao.getRelatedByCollection(token, game.id, ownerUserId, PER_QUERY_LIMIT)
                .forEach { results.putIfAbsent(it.id, it) }
        }

        tokensOf(game.franchises).forEach { token ->
            gameDao.getRelatedByFranchise(token, game.id, ownerUserId, PER_QUERY_LIMIT)
                .forEach { results.putIfAbsent(it.id, it) }
        }

        val releaseYear = game.releaseYear
        if (results.size < MIN_RESULTS && releaseYear != null) {
            val genreTokens = tokensOf(game.genres).ifEmpty { tokensOf(game.genre) }
            genreTokens.forEach { token ->
                gameDao.getRelatedByGenreAndYear(
                    token,
                    releaseYear - YEAR_WINDOW,
                    releaseYear + YEAR_WINDOW,
                    game.id,
                    ownerUserId,
                    PER_QUERY_LIMIT
                ).forEach { results.putIfAbsent(it.id, it) }
            }
        }

        return results.values.take(MAX_RESULTS)
    }

    private fun tokensOf(joined: String?): List<String> =
        joined?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
