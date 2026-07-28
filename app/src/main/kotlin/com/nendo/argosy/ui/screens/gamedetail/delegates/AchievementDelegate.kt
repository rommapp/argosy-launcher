package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.core.game.toAchievementUi
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.repository.RetroAchievementsRepository.Companion.toAchievementDefinition
import com.nendo.argosy.data.repository.RetroAchievementsRepository.Companion.toRommEarnedByBadgeId
import com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.core.game.AchievementUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class AchievementDelegate @Inject constructor(
    private val achievementDao: AchievementDao,
    private val gameRepository: GameRepository,
    private val raRepository: RetroAchievementsRepository,
    private val romMRepository: RomMRepository,
    private val imageCacheManager: ImageCacheManager,
    private val achievementUpdateBus: AchievementUpdateBus,
    private val verifyRAGameIdUseCase: VerifyRAGameIdUseCase
) {
    private val _achievements = MutableStateFlow<List<AchievementUi>>(emptyList())
    val achievements: StateFlow<List<AchievementUi>> = _achievements.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun loadCached(gameId: Long, hasAchievementSource: Boolean) {
        val cached = achievementDao.getByGameId(gameId, raRepository.activeOwnerUserId())
        if (!hasAchievementSource && cached.isEmpty()) {
            _achievements.value = emptyList()
            return
        }
        _achievements.value = cached.map { it.toAchievementUi() }
    }

    fun refresh(scope: CoroutineScope, gameId: Long, rommId: Long?) {
        scope.launch {
            _isLoading.value = true
            val fresh = fetchAndCacheAchievements(rommId, gameId)
            if (fresh.isNotEmpty()) {
                _achievements.value = fresh
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchAndCacheAchievements(rommId: Long?, gameId: Long): List<AchievementUi> {
        if (rommId != null) {
            val rommResults = fetchAchievementsFromRomM(rommId, gameId)
            if (rommResults.isNotEmpty()) return rommResults
        }
        return emptyList()
    }

    private suspend fun fetchAchievementsFromRomM(rommId: Long, gameId: Long): List<AchievementUi> {
        return when (val result = romMRepository.getRom(rommId)) {
            is RomMResult.Success -> {
                val rom = result.data
                val apiAchievements = rom.raMetadata?.achievements ?: emptyList()
                if (apiAchievements.isEmpty()) return emptyList()

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
                ) ?: return emptyList()

                gameRepository.updateAchievementsFetchedAt(gameId, System.currentTimeMillis())
                gameRepository.updateAchievementCount(gameId, counts.total, counts.earned)
                achievementUpdateBus.emit(
                    AchievementUpdateBus.AchievementUpdate(gameId, counts.total, counts.earned)
                )

                val savedAchievements =
                    achievementDao.getByGameId(gameId, raRepository.activeOwnerUserId())
                savedAchievements.forEach { achievement ->
                    if (achievement.cachedBadgeUrl == null && achievement.badgeUrl != null) {
                        imageCacheManager.queueBadgeCache(achievement.id, achievement.badgeUrl, achievement.badgeUrlLock)
                    }
                }

                savedAchievements.map { it.toAchievementUi() }
            }
            is RomMResult.Error -> emptyList()
        }
    }
}
