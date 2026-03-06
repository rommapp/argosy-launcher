package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AchievementCounts(
    val total: Int,
    val earned: Int
)

class FetchAchievementsUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val raRepository: RetroAchievementsRepository,
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao,
    private val imageCacheManager: ImageCacheManager
) {
    suspend operator fun invoke(gameId: Long, rommId: Long? = null, raId: Long? = null): AchievementCounts? {
        if (rommId == null && raId == null) return null

        if (raId != null && raRepository.isLoggedIn()) {
            val result = fetchFromRA(raId, gameId)
            if (result != null) return result
        }

        if (rommId != null) {
            return fetchFromRomM(rommId, gameId)
        }

        return null
    }

    private suspend fun fetchFromRA(raId: Long, gameId: Long): AchievementCounts? {
        val raData = raRepository.getGameAchievementsWithProgress(raId) ?: return null

        val entities = raData.achievements.map { achievement ->
            val badgeUrl = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/$it.png" }
            val badgeUrlLock = achievement.badgeName?.let { "https://media.retroachievements.org/Badge/${it}_lock.png" }

            AchievementEntity(
                gameId = gameId,
                raId = achievement.id,
                title = achievement.title,
                description = achievement.description ?: "",
                points = achievement.points,
                type = achievement.type,
                badgeUrl = badgeUrl,
                badgeUrlLock = badgeUrlLock,
                unlockedAt = raData.unlockedTimestamps[achievement.id],
                unlockedHardcoreAt = raData.hardcoreUnlockedTimestamps[achievement.id]
            )
        }
        achievementDao.replaceForGame(gameId, entities)
        gameDao.updateAchievementCount(gameId, raData.totalCount, raData.earnedCount)
        queueBadgeCaching(gameId)

        return AchievementCounts(total = raData.totalCount, earned = raData.earnedCount)
    }

    private suspend fun fetchFromRomM(rommId: Long, gameId: Long): AchievementCounts? {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements
                if (apiAchievements.isNullOrEmpty()) return null

                romMRepository.refreshRAProgressionIfNeeded()
                val earnedAchievements = rom.raId?.let { romMRepository.getEarnedAchievements(it) } ?: emptyList()
                val earnedByBadgeId = earnedAchievements.associateBy { it.id }

                val entities = apiAchievements.map { achievement ->
                    val earned = earnedByBadgeId[achievement.badgeId]
                    val unlockedAt = earned?.date?.let { parseTimestamp(it) }
                    val unlockedHardcoreAt = earned?.dateHardcore?.let { parseTimestamp(it) }

                    AchievementEntity(
                        gameId = gameId,
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = achievement.badgeUrl,
                        badgeUrlLock = achievement.badgeUrlLock,
                        unlockedAt = unlockedAt,
                        unlockedHardcoreAt = unlockedHardcoreAt
                    )
                }
                achievementDao.replaceForGame(gameId, entities)

                val earnedCount = entities.count { it.isUnlocked }
                gameDao.updateAchievementCount(gameId, entities.size, earnedCount)
                queueBadgeCaching(gameId)

                AchievementCounts(total = entities.size, earned = earnedCount)
            }
            is RomMResult.Error -> null
        }
    }

    private suspend fun queueBadgeCaching(gameId: Long) {
        achievementDao.getByGameId(gameId).forEach { achievement ->
            if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                imageCacheManager.queueBadgeCache(
                    achievement.id,
                    achievement.badgeUrl,
                    achievement.badgeUrlLock
                )
            }
        }
    }

    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(timestamp).toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
