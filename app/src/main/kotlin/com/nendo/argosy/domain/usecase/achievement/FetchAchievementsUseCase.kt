package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
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

                val earnedBadgeIds = rom.raId?.let { romMRepository.getEarnedBadgeIds(it) } ?: emptySet()

                val entities = apiAchievements.map { achievement ->
                    AchievementEntity(
                        gameId = gameId,
                        raId = achievement.raId,
                        title = achievement.title,
                        description = achievement.description,
                        points = achievement.points,
                        type = achievement.type,
                        badgeUrl = achievement.badgeUrl,
                        badgeUrlLock = achievement.badgeUrlLock,
                        isUnlocked = achievement.badgeId in earnedBadgeIds
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
}
