package com.nendo.argosy.domain.usecase.quickmenu

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class GetTopUnplayedUseCase @Inject constructor(
    private val gameDao: GameDao
) {
    operator fun invoke(limit: Int = 20): Flow<List<GameEntity>> = flow {
        val playedGames = gameDao.getPlayedGames()

        if (playedGames.isEmpty()) {
            val unplayedByRating = (gameDao.getUnplayedInstalledGames() +
                gameDao.getUnplayedUndownloadedGames())
                .filter { it.rating != null }
                .sortedByDescending { it.rating }
                .take(limit)
            emit(unplayedByRating)
            return@flow
        }

        val genreWeights = calculateGenreWeights(playedGames)
        val platformWeights = calculatePlatformWeights(playedGames)
        val playTimeBoost = calculatePlayTimeBoost(playedGames)

        val unplayedGames = gameDao.getUnplayedInstalledGames() +
            gameDao.getUnplayedUndownloadedGames()

        if (unplayedGames.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val scored = unplayedGames.map { game ->
            game to calculatePreferenceScore(game, genreWeights, platformWeights, playTimeBoost)
        }

        val topUnplayed = scored
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

        emit(topUnplayed)
    }

    private fun calculateRecencyFactor(lastPlayed: Instant?): Double {
        if (lastPlayed == null) return 0.1
        val daysSincePlay = Duration.between(lastPlayed, Instant.now()).toDays()
        return 1.0 / (1.0 + (daysSincePlay / 30.0))
    }

    private fun calculateGenreWeights(playedGames: List<GameEntity>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
        for (game in playedGames) {
            val genre = game.genre ?: continue
            val playCount = game.playCount.coerceAtLeast(1)
            val playTimeHours = game.playTimeMinutes / 60.0
            val recencyFactor = calculateRecencyFactor(game.lastPlayed)
            val weight = (playCount + playTimeHours) * recencyFactor
            weights[genre] = weights.getOrDefault(genre, 0.0) + weight
        }
        return weights
    }

    private fun calculatePlatformWeights(playedGames: List<GameEntity>): Map<Long, Double> {
        val weights = mutableMapOf<Long, Double>()
        for (game in playedGames) {
            val playCount = game.playCount.coerceAtLeast(1)
            val playTimeHours = game.playTimeMinutes / 60.0
            val recencyFactor = calculateRecencyFactor(game.lastPlayed)
            val weight = (playCount + playTimeHours) * recencyFactor
            weights[game.platformId] = weights.getOrDefault(game.platformId, 0.0) + weight
        }
        return weights
    }

    private fun calculatePlayTimeBoost(playedGames: List<GameEntity>): Double {
        val effectivePlayTimeHours = playedGames.sumOf { game ->
            val playTimeHours = game.playTimeMinutes / 60.0
            val recencyFactor = calculateRecencyFactor(game.lastPlayed)
            playTimeHours * recencyFactor
        }
        return (effectivePlayTimeHours / 10.0).coerceIn(0.0, 0.25)
    }

    private fun calculatePreferenceScore(
        game: GameEntity,
        genreWeights: Map<String, Double>,
        platformWeights: Map<Long, Double>,
        playTimeBoost: Double
    ): Double {
        val genreScore = game.genre?.let { genreWeights[it] } ?: 0.0
        val platformScore = platformWeights[game.platformId] ?: 0.0
        val ratingScore = (game.rating ?: 0f).toDouble()

        val baseScore = (genreScore * 0.5) + (platformScore * 0.3) + (ratingScore * 0.2)
        return baseScore * (1.0 + playTimeBoost)
    }
}
