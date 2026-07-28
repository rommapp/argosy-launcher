package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.RetroAchievementsRepository.Companion.toAchievementDefinition
import com.nendo.argosy.data.repository.RetroAchievementsRepository.Companion.toRommEarnedByBadgeId
import javax.inject.Inject

data class AchievementCounts(
    val total: Int,
    val earned: Int
)

class FetchAchievementsUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val raRepository: RetroAchievementsRepository,
    private val verifyRAGameIdUseCase: VerifyRAGameIdUseCase,
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val imageCacheManager: ImageCacheManager
) {
    suspend operator fun invoke(gameId: Long, rommId: Long? = null, raId: Long? = null): AchievementCounts? {
        // Fetch RA data from RomM (which syncs with RA server-side using its API key).
        // Never call RA directly for UI display -- that's only for in-game sessions.
        if (rommId != null) {
            return fetchFromRomM(rommId, gameId)
        }

        return null
    }

    private suspend fun fetchFromRomM(rommId: Long, gameId: Long): AchievementCounts? {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements
                if (apiAchievements.isNullOrEmpty()) return null

                val rommEarnedByBadgeId = if (rom.raId != null) {
                    romMRepository.refreshRAProgressionIfNeeded(force = true)
                    romMRepository.getEarnedAchievements(rom.raId).toRommEarnedByBadgeId()
                } else {
                    emptyMap()
                }

                val counts = raRepository.syncAchievementsForGame(
                    gameId = gameId,
                    gameRaId = rom.raId,
                    definitions = apiAchievements.map { it.toAchievementDefinition() },
                    rommEarnedById = rommEarnedByBadgeId
                ) ?: return null

                gameDao.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())
                overlayWriter.updateAchievementCount(gameId, counts.total, counts.earned)
                queueBadgeCaching(gameId)

                AchievementCounts(total = counts.total, earned = counts.earned)
            }
            is RomMResult.Error -> null
        }
    }

    private suspend fun queueBadgeCaching(gameId: Long) {
        achievementDao.getAllForGame(gameId).forEach { achievement ->
            if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                imageCacheManager.queueBadgeCache(
                    achievement.id,
                    achievement.badgeUrl,
                    achievement.badgeUrlLock
                )
            }
        }
    }
}
