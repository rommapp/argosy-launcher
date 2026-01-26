package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
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
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao,
    private val imageCacheManager: ImageCacheManager
) {
    suspend operator fun invoke(rommId: Long, gameId: Long): AchievementCounts? {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements
                if (apiAchievements.isNullOrEmpty()) return null

                val earnedAchievements = rom.raId?.let { romMRepository.getEarnedAchievements(it) } ?: emptyList()
                val earnedByBadgeId = earnedAchievements.associateBy { it.id }

                val entities = apiAchievements.map { achievement ->
                    val earned = earnedByBadgeId[achievement.badgeId]
                    val unlockedAt = earned?.date?.let { parseTimestamp(it) }
                    val unlockedHardcoreAt = earned?.dateHardcore?.let { parseTimestamp(it) }
                    val isUnlocked = earned != null

                    AchievementEntity(
                        gameId = gameId,
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = achievement.badgeUrl,
                        badgeUrlLock = achievement.badgeUrlLock,
                        isUnlocked = isUnlocked,
                        unlockedAt = unlockedAt,
                        unlockedHardcoreAt = unlockedHardcoreAt
                    )
                }
                achievementDao.replaceForGame(gameId, entities)

                val earnedCount = entities.count { it.isUnlocked }
                gameDao.updateAchievementCount(gameId, entities.size, earnedCount)

                val savedAchievements = achievementDao.getByGameId(gameId)
                savedAchievements.forEach { achievement ->
                    if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                        imageCacheManager.queueBadgeCache(
                            achievement.id,
                            achievement.badgeUrl,
                            achievement.badgeUrlLock
                        )
                    }
                }

                AchievementCounts(total = entities.size, earned = earnedCount)
            }
            is RomMResult.Error -> null
        }
    }

    private fun parseTimestamp(timestamp: String): Long? {
        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(timestamp).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
