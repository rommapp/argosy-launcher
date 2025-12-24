package com.nendo.argosy.domain.usecase.recommendation

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private const val TOTAL_RECOMMENDATIONS = 16
private const val UNDOWNLOADED_TARGET = 12
private const val NEW_PENALTY = 0.9f
private const val DECAY_AMOUNT = 0.1f
private const val FAVORITE_PENALTY = 0.5f

class GenerateRecommendationsUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(forceRegenerate: Boolean = false): List<Long> {
        val prefs = preferencesRepository.preferences.first()
        val currentWeekKey = getCurrentWeekKey()

        var penalties = prefs.recommendationPenalties.toMutableMap()
        val lastDecayWeek = prefs.lastPenaltyDecayWeek

        if (lastDecayWeek != null && lastDecayWeek != currentWeekKey) {
            val weeksPassed = calculateWeeksPassed(lastDecayWeek, currentWeekKey)
            penalties = decayPenalties(penalties, weeksPassed)
        }

        val playedGames = gameDao.getPlayedGames()
        if (playedGames.isEmpty()) return emptyList()

        val genreWeights = calculateGenreWeights(playedGames)
        val platformWeights = calculatePlatformWeights(playedGames)
        val playTimeBoost = calculatePlayTimeBoost(playedGames)

        val undownloadedGames = gameDao.getUnplayedUndownloadedGames()
        val installedUnplayed = gameDao.getUnplayedInstalledGames()

        if (undownloadedGames.isEmpty() && installedUnplayed.isEmpty()) return emptyList()

        val usedIds = mutableSetOf<Long>()
        val recommendations = mutableListOf<Long>()

        val undownloadedPicks = weightedRandomSelect(
            undownloadedGames,
            UNDOWNLOADED_TARGET,
            genreWeights,
            platformWeights,
            penalties,
            playTimeBoost
        )

        recommendations.addAll(undownloadedPicks)
        usedIds.addAll(undownloadedPicks)

        val remainingSlots = TOTAL_RECOMMENDATIONS - recommendations.size

        val installedPicks = weightedRandomSelect(
            installedUnplayed.filter { it.id !in usedIds },
            remainingSlots,
            genreWeights,
            platformWeights,
            penalties,
            playTimeBoost
        )

        recommendations.addAll(installedPicks)
        usedIds.addAll(installedPicks)

        if (recommendations.size < TOTAL_RECOMMENDATIONS) {
            val additionalUndownloaded = weightedRandomSelect(
                undownloadedGames.filter { it.id !in usedIds },
                TOTAL_RECOMMENDATIONS - recommendations.size,
                genreWeights,
                platformWeights,
                penalties,
                playTimeBoost
            )
            recommendations.addAll(additionalUndownloaded)
        }

        val finalList = recommendations.take(TOTAL_RECOMMENDATIONS)

        preferencesRepository.setRecommendations(finalList, Instant.now())
        preferencesRepository.setRecommendationPenalties(penalties, currentWeekKey)

        return finalList
    }

    private fun getCurrentWeekKey(): String {
        val today = LocalDate.now()
        val saturday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
        return saturday.toString()
    }

    private fun calculateWeeksPassed(lastWeekKey: String, currentWeekKey: String): Int {
        return try {
            val lastDate = LocalDate.parse(lastWeekKey)
            val currentDate = LocalDate.parse(currentWeekKey)
            ((currentDate.toEpochDay() - lastDate.toEpochDay()) / 7).toInt().coerceAtLeast(0)
        } catch (e: Exception) {
            1
        }
    }

    private fun decayPenalties(penalties: MutableMap<Long, Float>, weeks: Int): MutableMap<Long, Float> {
        val decayTotal = DECAY_AMOUNT * weeks
        val result = mutableMapOf<Long, Float>()
        for ((gameId, penalty) in penalties) {
            val newPenalty = (penalty - decayTotal).coerceAtLeast(0f)
            if (newPenalty > 0f) {
                result[gameId] = newPenalty
            }
        }
        return result
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

    private fun calculatePlatformWeights(playedGames: List<GameEntity>): Map<String, Double> {
        val weights = mutableMapOf<String, Double>()
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
        platformWeights: Map<String, Double>,
        penalties: Map<Long, Float>,
        playTimeBoost: Double
    ): Double {
        val genreScore = game.genre?.let { genreWeights[it] } ?: 0.0
        val platformScore = platformWeights[game.platformId] ?: 0.0
        val ratingScore = (game.rating ?: 0f).toDouble()

        val baseScore = (genreScore * 0.5) + (platformScore * 0.3) + (ratingScore * 0.2)
        val boostedScore = baseScore * (1.0 + playTimeBoost)

        val penalty = penalties[game.id] ?: 0f
        val favoritePenalty = if (game.isFavorite) FAVORITE_PENALTY else 0f
        val penaltyMultiplier = 1.0 - penalty - favoritePenalty

        return boostedScore * penaltyMultiplier
    }

    private fun weightedRandomSelect(
        games: List<GameEntity>,
        count: Int,
        genreWeights: Map<String, Double>,
        platformWeights: Map<String, Double>,
        penalties: Map<Long, Float>,
        playTimeBoost: Double
    ): List<Long> {
        if (games.isEmpty()) return emptyList()
        if (games.size <= count) return games.map { it.id }

        val scored = games.map { game ->
            game to calculatePreferenceScore(game, genreWeights, platformWeights, penalties, playTimeBoost)
        }

        val candidatePoolSize = (count * 4).coerceAtMost(games.size)
        val candidatePool = scored
            .sortedByDescending { it.second }
            .take(candidatePoolSize)
            .map { it.first }
            .shuffled()

        return candidatePool.take(count).map { it.id }
    }
}
