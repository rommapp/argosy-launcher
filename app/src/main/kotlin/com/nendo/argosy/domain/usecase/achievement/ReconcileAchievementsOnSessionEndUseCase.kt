package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import javax.inject.Inject

/**
 * Session-close safety net: reconciles the local achievement records for a game
 * against RA's authoritative unlock list, so unlocks the in-game listener missed
 * (suspend/resume teardown, a stalled foreground monitor, a dropped social push)
 * are still captured and pushed via [AchievementUpdateBus]. Cheap no-op for games
 * without an RA id or when RA is not logged in.
 */
class ReconcileAchievementsOnSessionEndUseCase @Inject constructor(
    private val raRepository: RetroAchievementsRepository,
    private val achievementDao: AchievementDao,
    private val gameDao: GameDao,
    private val overlayWriter: com.nendo.argosy.data.repository.GameUserOverlayWriter,
    private val achievementUpdateBus: AchievementUpdateBus
) {
    suspend operator fun invoke(gameId: Long) {
        if (!raRepository.isLoggedIn()) return
        val game = gameDao.getById(gameId) ?: return
        val gameRaId = game.effectiveRaId ?: game.raId ?: return

        val existing = achievementDao.getByGameId(gameId)
        if (existing.isEmpty()) return

        val fresh = raRepository.fetchUnlocksFresh(gameRaId, forceRefresh = true) ?: return

        val now = System.currentTimeMillis()
        var changed = 0
        for (achievement in existing) {
            if (achievement.raId in fresh.unlockedIds && achievement.unlockedAt == null) {
                achievementDao.markUnlocked(gameId, achievement.raId, now)
                changed++
            }
            if (achievement.raId in fresh.hardcoreUnlockedIds && achievement.unlockedHardcoreAt == null) {
                achievementDao.markUnlockedHardcore(gameId, achievement.raId, now)
                changed++
            }
        }
        if (changed == 0) return

        val total = achievementDao.countByGameId(gameId)
        val earned = achievementDao.countUnlockedByGameId(gameId)
        overlayWriter.updateAchievementCount(gameId, total, earned)
        achievementUpdateBus.emit(AchievementUpdateBus.AchievementUpdate(gameId, total, earned))
        Logger.info(TAG, "Session-end reconcile: recovered $changed unlock(s) for gameId=$gameId raId=$gameRaId")
    }

    companion object {
        private const val TAG = "AchievementReconcile"
    }
}
